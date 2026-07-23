package top.kzre.krro.util.tile;

import lombok.Getter;
import top.kzre.krro.util.pool.FloatsPools;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 瓦片画布 —— 高性能 RGBA float 图像存储，支持负索引和动态扩展。
 * <p>
 * 内部数据为 RGBA 四通道 float，排列为 [r, g, b, a, r, g, b, a, ...]。
 * 此类是线程安全的，内部使用 ConcurrentHashMap 和 Tile 的同步方法。
 * 范围管理（min/max）使用 synchronized 保护。
 * <p>
 * 撤销/重做功能由上层（Clojure）实现，本类不提供任何历史记录。
 */
public final class TiledCanvas implements Canvas {

    // ========== 瓦片坐标工具（私有静态） ==========
    public static long pack(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
    }

    public static int unpackTx(long key) {
        return (int) (key >> 32);
    }

    public static int unpackTy(long key) {
        return (int) key;
    }

    public static int tileX(int worldX, int tileSize) {
        return Math.floorDiv(worldX, tileSize);
    }

    public static int tileY(int worldY, int tileSize) {
        return Math.floorDiv(worldY, tileSize);
    }

    public static int localX(int worldX, int tileSize) {
        return Math.floorMod(worldX, tileSize);
    }

    public static int localY(int worldY, int tileSize) {
        return Math.floorMod(worldY, tileSize);
    }

    // ---------- 字段 ----------
    @Getter
    private final int tileSize;
    @Getter
    private final int channels;          // 实例变量，final，默认 4
    private final ConcurrentHashMap<Long, Tile> tiles;

    // 特殊暴露，外部不该直接修改
    @Getter
    private final float[] defaultPixel;
    // ---------- 范围查询 ----------
    @Getter
    private volatile int minTileX;
    @Getter
    private volatile int maxTileX;
    @Getter
    private volatile int minTileY;
    @Getter
    private volatile int maxTileY;

    // ---------- 构造器 ----------
    public TiledCanvas(int tileSize) {
        this(tileSize, new float[]{0f, 0f, 0f, 0f}, 4);
    }

    public TiledCanvas(int tileSize, float[] defaultPixel) {
        this(tileSize, defaultPixel, 4);   // 默认 4 通道
    }

    // 完整构造器
    public TiledCanvas(int tileSize, float[] defaultPixel, int channels) {
        if (tileSize <= 0) throw new IllegalArgumentException("tileSize must be positive");
        if (channels < 1 || channels > 10)  // 根据实际需要限定范围
            throw new IllegalArgumentException("channels must be between 1 and 10");
        if (defaultPixel == null || defaultPixel.length < channels)
            throw new IllegalArgumentException("defaultPixel must have length >= " + channels);

        this.tileSize = tileSize;
        this.channels = channels;
        this.tiles = new ConcurrentHashMap<>();
        this.defaultPixel = defaultPixel.clone();
        this.minTileX = Integer.MAX_VALUE;
        this.maxTileX = Integer.MIN_VALUE;
        this.minTileY = Integer.MAX_VALUE;
        this.maxTileY = Integer.MIN_VALUE;
    }

    /**
     * 获取指定瓦片的句柄。返回的 Tile 对象仅用于读取像素数据（通过 {@link Tile#getPixelsSnapshot()}），
     * 不应直接修改其内部数组。如果需要写入，请使用 {@link #setPixel}、{@link #writeBytes}、{@link #fillRect} 等公开 API。
     *
     * @param tx 瓦片 X 索引（支持负数）
     * @param ty 瓦片 Y 索引（支持负数）
     * @return 瓦片句柄，若瓦片不存在则返回 null
     */
    @Override
    public Tile getTile(int tx, int ty) {
        return tiles.get(pack(tx, ty));
    }

    /**
     * 遍历所有存在的瓦片，对每个瓦片调用回调函数。
     * 回调函数接收三个参数：瓦片索引 tx、ty 以及只读的像素数据数组。
     * <p>
     * 注意：像素数据数组是内部数组的直接引用，仅用于读取，不应修改。
     * 若需要写入，请使用其他公开 API。
     *
     * @param visitor 回调函数，接受 (tx, ty, data)
     */
    @Override
    public void forEachTile(TileVisitor visitor) {
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            long key = entry.getKey();
            int tx = unpackTx(key);
            int ty = unpackTy(key);
            float[] data = entry.getValue().getPixelsSnapshot();
            visitor.visit(tx, ty, data);
        }
    }

    // ---------- 包内可见的瓦片访问 ----------
    Tile ensureTile(int tx, int ty) {
        long key = pack(tx, ty);
        Tile tile = tiles.get(key);
        if (tile == null) {
            float[] raw = allocateTile();
            fillTileWithDefault(raw);
            TileData data = new TileData(raw);
            Tile newTile = new Tile(tx, ty, data);
            Tile existing = tiles.putIfAbsent(key, newTile);
            if (existing != null) {
                data.release();
                return existing;
            }
            // 注册到全局管理器
            TileStorageManager.instance().register(data);
            synchronized (this) {
                updateExtent(tx, ty);
            }
            return newTile;
        }
        return tile;
    }

    void deleteTile(int tx, int ty) {
        long key = pack(tx, ty);
        Tile removed = tiles.remove(key);
        if (removed != null) {
            removed.getDataRef().release();
            synchronized (this) {
                recomputeExtent();
            }
        }
    }

    @Override
    public int tileCount() {
        return this.tiles.size();
    }

    // ---------- 公开的像素读写 ----------
    @Override
    public void getPixel(int x, int y, float[] out) {
        if (out == null || out.length < channels)
            throw new IllegalArgumentException("out array must have length >= 4");
        int tx = tileX(x, tileSize);
        int ty = tileY(y, tileSize);
        Tile tile = getTile(tx, ty);
        if (tile == null) {
            System.arraycopy(defaultPixel, 0, out, 0, channels);
            return;
        }
        int lx = localX(x, tileSize);
        int ly = localY(y, tileSize);
        tile.getPixel(lx, ly, out, tileSize, channels);
    }

    @Deprecated
    @Override
    public void setPixel(int worldX, int worldY, float r, float g, float b, float a) {
        setPixel(worldX, worldY, new float[]{r, g, b, a});
    }

    @Override
    public void setPixel(int x, int y, float[] pixel) {
        if (pixel.length < channels) throw new IllegalArgumentException("pixel array too short");
        int tx = tileX(x, tileSize);
        int ty = tileY(y, tileSize);
        Tile tile = ensureTile(tx, ty);
        int lx = localX(x, tileSize);
        int ly = localY(y, tileSize);
        tile.setPixel(lx, ly, pixel, tileSize, channels);   // tile.setPixel 也需支持数组
    }

    // ---------- 批量读写 ----------
    @Override
    public void readBytes(float[] dest, int destOffset, int x, int y, int w, int h, int destRowStride) {
        if (dest == null) throw new IllegalArgumentException("dest cannot be null");
        if (w <= 0 || h <= 0) return;
        if (destRowStride <= 0) destRowStride = w;

        for (int row = 0; row < h; ) {
            int ty = tileY(y + row, tileSize);
            int rowsInTile = Math.min(tileSize - localY(y + row, tileSize), h - row);

            for (int col = 0; col < w; ) {
                int tx = tileX(x + col, tileSize);
                Tile tile = getTile(tx, ty);
                if (tile == null) {
                    int destRowBase = destOffset + row * destRowStride + col;
                    for (int r = 0; r < rowsInTile; r++) {
                        int rowStart = (destRowBase + r * destRowStride) * channels;
                        for (int c = 0; c < w - col; c++) {
                            int idx = rowStart + c * channels;
                            dest[idx] = defaultPixel[0];
                            dest[idx+1] = defaultPixel[1];
                            dest[idx+2] = defaultPixel[2];
                            dest[idx+3] = defaultPixel[3];
                        }
                    }
                } else {
                    float[] tileData = tile.getPixelsSnapshot();
                    int localX0 = localX(x + col, tileSize);
                    int localY0 = localY(y + row, tileSize);
                    int tileRowStride = tileSize * channels;
                    int srcOffset = (localY0 * tileSize + localX0) * channels;
                    int copyCols = Math.min(tileSize - localX0, w - col);
                    int bytesPerRow = copyCols * channels;

                    for (int r = 0; r < rowsInTile; r++) {
                        int destRowStart = destOffset + (row + r) * destRowStride + col;
                        System.arraycopy(tileData, srcOffset + r * tileRowStride,
                                dest, destRowStart * channels, bytesPerRow);
                    }
                }
                col += Math.min(tileSize - localX(x + col, tileSize), w - col);
            }
            row += rowsInTile;
        }
    }

    @Override
    public void writeBytes(float[] src, int srcOffset, int x, int y, int w, int h, int srcRowStride) {
        if (src == null) throw new IllegalArgumentException("src cannot be null");
        if (w <= 0 || h <= 0) return;
        if (srcRowStride <= 0) srcRowStride = w;

        for (int row = 0; row < h; ) {
            int ty = tileY(y + row, tileSize);
            int rowsInTile = Math.min(tileSize - localY(y + row, tileSize), h - row);

            for (int col = 0; col < w; ) {
                int tx = tileX(x + col, tileSize);
                Tile tile = ensureTile(tx, ty);
                float[] tileData = tile.getPixelsForWrite(tileSize);
                int localX0 = localX(x + col, tileSize);
                int localY0 = localY(y + row, tileSize);
                int tileRowStride = tileSize * channels;
                int dstOffset = (localY0 * tileSize + localX0) * channels;
                int copyCols = Math.min(tileSize - localX0, w - col);
                int bytesPerRow = copyCols * channels;

                for (int r = 0; r < rowsInTile; r++) {
                    int srcRowStart = srcOffset + (row + r) * srcRowStride + col;
                    System.arraycopy(src, srcRowStart * channels,
                            tileData, dstOffset + r * tileRowStride, bytesPerRow);
                }
                col += copyCols;
            }
            row += rowsInTile;
        }
    }

    // ---------- 填充 ----------
    @Override
    public void fillRect(int x, int y, int w, int h, float[] color) {
        if (color == null || color.length < channels)
            throw new IllegalArgumentException("color must be a float[4]");
        if (w <= 0 || h <= 0) return;

        // 判断填充颜色是否与默认像素完全相同
        boolean colorEqualsDefault = (color[0] == defaultPixel[0] && color[1] == defaultPixel[1] &&
                color[2] == defaultPixel[2] && color[3] == defaultPixel[3]);

        for (int row = 0; row < h; ) {
            int ty = tileY(y + row, tileSize);
            int rowsInTile = Math.min(tileSize - localY(y + row, tileSize), h - row);

            for (int col = 0; col < w; ) {
                int tx = tileX(x + col, tileSize);
                int localX0 = localX(x + col, tileSize);
                int localY0 = localY(y + row, tileSize);

                // 如果填充区域完整覆盖一个瓦片
                if (localX0 == 0 && localY0 == 0 && rowsInTile == tileSize &&
                        (col + tileSize <= w) && (row + tileSize <= h)) {

                    if (colorEqualsDefault) {
                        // 颜色等于默认像素：直接删除瓦片即可（相当于用默认填充）
                        deleteTile(tx, ty);
                    } else {
                        // 替换整个瓦片为填充颜色
                        deleteTile(tx, ty);
                        float[] raw = allocateTile();
                        fillTileWithColor(raw, color);
                        TileData data = new TileData(raw);
                        Tile newTile = new Tile(tx, ty, data);
                        Tile existing = tiles.putIfAbsent(pack(tx, ty), newTile);
                        if (existing != null) {
                            data.release();
                        } else {
                            synchronized (this) {
                                updateExtent(tx, ty);
                            }
                        }
                    }
                    col += tileSize;
                } else {
                    // 部分覆盖：获取可写瓦片，逐像素填充
                    Tile tile = ensureTile(tx, ty);
                    float[] tileData = tile.getPixelsForWrite(tileSize);
                    int copyCols = Math.min(tileSize - localX0, w - col);
                    int tileRowStride = tileSize * channels;
                    int dstOffset = (localY0 * tileSize + localX0) * channels;

                    for (int r = 0; r < rowsInTile; r++) {
                        int rowStart = dstOffset + r * tileRowStride;
                        for (int c = 0; c < copyCols; c++) {
                            int idx = rowStart + c * channels;
                            tileData[idx] = color[0];
                            tileData[idx+1] = color[1];
                            tileData[idx+2] = color[2];
                            tileData[idx+3] = color[3];
                        }
                    }
                    col += copyCols;
                }
            }
            row += rowsInTile;
        }
    }

    // ---------- 清空 ----------
    @Override
    public void clear() {
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            entry.getValue().getDataRef().release();
        }
        tiles.clear();
        synchronized (this) {
            minTileX = Integer.MAX_VALUE;
            maxTileX = Integer.MIN_VALUE;
            minTileY = Integer.MAX_VALUE;
            maxTileY = Integer.MIN_VALUE;
        }
    }

    // ---------- 复制 ----------
    public TiledCanvas copy() {
        TiledCanvas copy = new TiledCanvas(this.tileSize, this.defaultPixel);
        for (Map.Entry<Long, Tile> entry : this.tiles.entrySet()) {
            long key = entry.getKey();
            Tile srcTile = entry.getValue();
            float[] srcPixels = srcTile.getPixelsSnapshot();
            float[] dstPixels = copy.allocateTile();
            System.arraycopy(srcPixels, 0, dstPixels, 0, srcPixels.length);
            TileData data = new TileData(dstPixels);
            Tile dstTile = new Tile(srcTile.tx(), srcTile.ty(), data);
            copy.tiles.put(key, dstTile);
        }
        // 同步读取当前范围（保证一致性）
        synchronized (this) {
            copy.minTileX = this.minTileX;
            copy.maxTileX = this.maxTileX;
            copy.minTileY = this.minTileY;
            copy.maxTileY = this.maxTileY;
        }
        return copy;
    }

    /**
     * 将另一个画布的数据共享到当前画布（引用计数增加，轻量级快照）。
     * 当前画布的原有数据会被释放。
     */
    public void shareFrom(TiledCanvas src) {
        if (src.tileSize != this.tileSize)
            throw new IllegalArgumentException("tileSize mismatch");

        // 清除当前所有瓦片（释放引用）
        clear();

        // 复制源画布的瓦片引用
        for (Map.Entry<Long, Tile> entry : src.tiles.entrySet()) {
            long key = entry.getKey();
            Tile srcTile = entry.getValue();
            TileData data = srcTile.getDataRef();
            data.acquire(); // 增加引用计数
            Tile newTile = new Tile(srcTile.tx(), srcTile.ty(), data);
            tiles.put(key, newTile);
        }

        // 更新范围（直接读取 src 的 volatile 字段，然后同步更新 this）
        synchronized (this) {
            this.minTileX = src.minTileX;
            this.maxTileX = src.maxTileX;
            this.minTileY = src.minTileY;
            this.maxTileY = src.maxTileY;
        }
    }

    @Override
    public void getBounds(int[] out) {
        if (out == null || out.length < 4)
            throw new IllegalArgumentException("out array must have length >= 4");
        if (tiles.isEmpty()) {
            out[0] = out[1] = out[2] = out[3] = 0;
            return;
        }
        out[0] = minTileX * tileSize;
        out[1] = minTileY * tileSize;
        out[2] = (maxTileX + 1) * tileSize - 1;
        out[3] = (maxTileY + 1) * tileSize - 1;
    }


    public int totalSize() { return tiles.size(); }


    @Override
    public SequentialIterator createSequentialIterator(int x, int y, int w, int h,
                                                       boolean writable,
                                                       ScanOrder order) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("w and h must be positive");
        return new SeqIteratorImpl(this, x, y, w, h, writable, order);
    }

    @Override
    public SequentialIterator createSequentialIterator(int x, int y, int w, int h,
                                                       boolean writable) {
        return createSequentialIterator(x, y, w, h, writable, ScanOrder.ROW_MAJOR);
    }

    /**
     * 创建随机访问迭代器。
     * @param writable 是否可写
     */
    @Override
    public RandomAccessIterator createRandomAccessIterator(boolean writable) {
        return new RandomAccessIteratorImpl(this, writable);
    }


    // ---------- 内部辅助 ----------
    private float[] allocateTile() {
        int len = tileSize * tileSize * channels;
        return FloatsPools.getPool(len).acquire();
    }

    private void fillTileWithDefault(float[] tile) {
        int len = tile.length;
        for (int i = 0; i < len; i += channels) {
            tile[i] = defaultPixel[0];
            tile[i+1] = defaultPixel[1];
            tile[i+2] = defaultPixel[2];
            tile[i+3] = defaultPixel[3];
        }
    }

    private void fillTileWithColor(float[] tile, float[] color) {
        int len = tile.length;
        for (int i = 0; i < len; i += channels) {
            tile[i] = color[0];
            tile[i+1] = color[1];
            tile[i+2] = color[2];
            tile[i+3] = color[3];
        }
    }

    private  void updateExtent(int tx, int ty) {
        if (tx < minTileX) minTileX = tx;
        if (tx > maxTileX) maxTileX = tx;
        if (ty < minTileY) minTileY = ty;
        if (ty > maxTileY) maxTileY = ty;
    }

    private void recomputeExtent() {
        if (tiles.isEmpty()) {
            minTileX = Integer.MAX_VALUE;
            maxTileX = Integer.MIN_VALUE;
            minTileY = Integer.MAX_VALUE;
            maxTileY = Integer.MIN_VALUE;
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Long key : tiles.keySet()) {
            int tx = unpackTx(key);
            int ty = unpackTy(key);
            if (tx < minX) minX = tx;
            if (tx > maxX) maxX = tx;
            if (ty < minY) minY = ty;
            if (ty > maxY) maxY = ty;
        }
        minTileX = minX;
        maxTileX = maxX;
        minTileY = minY;
        maxTileY = maxY;
    }

    /**
     * 以只读方式遍历当前所有瓦片，将打包键和像素数组的映射传递给 consumer。
     * <p>
     * 像素数组是内部数据的直接引用，<b>仅在此回调期间有效</b>，调用者严禁：
     * <ul>
     *   <li>修改返回的 float[] 内容</li>
     *   <li>将数组或 Map 的引用保存到回调外部</li>
     * </ul>
     * 此设计避免了内存拷贝，同时通过引用计数和 COW 保证了读取期间数据不会被并发写入破坏。
     *
     * @param consumer 接收瓦片映射的回调，键为 pack(tx, ty)，值为瓦片像素数组（只读）
     */
    @Override
    public void readTiles(Consumer<Map<Long, float[]>> consumer) {
        Map<Long, float[]> snapshot = new HashMap<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getPixelsSnapshot());
        }
        consumer.accept(Collections.unmodifiableMap(snapshot));
    }

    @Override
    public void writeTiles(Map<Long, float[]> newTiles) {
        mergeTiles(newTiles);   // 复用已有的零拷贝合并方法
    }

    /**
     * 合并瓦片数据（零拷贝）。
     * <p>
     * 直接使用传入的像素数组，<b>不进行复制</b>。调用者必须满足：
     * <ul>
     *   <li>数组长度必须为 {@code tileSize * tileSize * channels}</li>
     *   <li>数组必须是从 {@link top.kzre.krro.util.pool.FloatsPools} 获取的，
     *       且调用者<b>转移所有权</b>给本画布，之后不得再修改或释放该数组</li>
     *   <li>若数组不符合池化要求（例如是普通 {@code new float[]}），
     *       将导致池污染和未定义行为，所有风险由调用者承担</li>
     * </ul>
     * <p>
     * 未出现在映射中的现有瓦片保持不变。若键已存在，则通过 {@link Tile#replaceData} 替换数据。
     *
     * @param newTiles 要合并的瓦片映射，键为 pack(tx, ty)，值为 RGBA float 数组（所有权转移）
     * @throws IllegalArgumentException 如果任意数组长度不匹配
     */
    public TiledCanvas mergeTiles(Map<Long, float[]> newTiles) {
        int expectedLen = tileSize * tileSize * channels;
        for (Map.Entry<Long, float[]> entry : newTiles.entrySet()) {
            long key = entry.getKey();
            float[] src = entry.getValue();

            if (src.length != expectedLen) {
                throw new IllegalArgumentException(
                        "Pixel array length mismatch: expected " + expectedLen + ", got " + src.length);
            }

            TileData newData = new TileData(src);    // 直接使用外部数组，引用计数 = 1

            Tile existingTile = tiles.get(key);
            if (existingTile != null) {
                existingTile.replaceData(newData);   // 内部管理引用计数
            } else {
                Tile newTile = new Tile(unpackTx(key), unpackTy(key), newData);
                tiles.put(key, newTile);
            }

            synchronized (this) {
                updateExtent(unpackTx(key), unpackTy(key));
            }
        }
        return this;
    }

    /**
     * 将另一个画布的所有瓦片合并到当前画布中。
     * 已存在的瓦片会被替换，未存在的会被添加。源画布的数据通过引用计数共享，
     * 后续修改会在 COW 机制下自动分离。
     *
     * @param canvas 源画布，其 tileSize 必须与当前画布相同
     * @throws IllegalArgumentException 如果 tileSize 不匹配
     */
    public TiledCanvas mergeCanvas(TiledCanvas canvas) {
        if (canvas.tileSize != this.tileSize) {
            throw new IllegalArgumentException("tileSize mismatch");
        }
        for (Map.Entry<Long, Tile> entry : canvas.tiles.entrySet()) {
            long key = entry.getKey();
            Tile srcTile = entry.getValue();
            TileData srcData = srcTile.getDataRef();
            // 增加引用计数，代表当前画布将持有一个引用
            srcData.acquire();

            Tile existingTile = this.tiles.get(key);
            if (existingTile != null) {
                // 释放目标画布原有的瓦片数据
                existingTile.getDataRef().release();
                // 直接替换为新的 Tile（使用同一个 TileData）
                this.tiles.put(key, new Tile(unpackTx(key), unpackTy(key), srcData));
            } else {
                this.tiles.put(key, new Tile(unpackTx(key), unpackTy(key), srcData));
                // 仅在新添加瓦片时更新范围
                synchronized (this) {
                    updateExtent(unpackTx(key), unpackTy(key));
                }
            }

        }
        return this;
    }

    public void deleteTiles(Collection<Long> keys) {
        for (Long key : keys) {
            Tile removed = tiles.remove(key);
            if(removed != null) {
                removed.getDataRef().release();
            }
        }
    }
}