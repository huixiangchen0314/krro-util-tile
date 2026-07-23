package top.kzre.krro.util.tile;

import java.util.*;
import java.util.function.Consumer;

/**
 * 画布视图，基于瓦片坐标的矩形区域，实现 {@link Canvas} 接口。
 * 视图由一个起始瓦片索引 (minTx, minTy) 和瓦片数量 (tileW, tileH) 定义。
 * 所有读写操作均被限制在视图瓦片范围内，视图外的瓦片不可见且不会被修改。
 * 线程安全依赖于底层 {@link TiledCanvas}。
 */
final class CanvasView implements Canvas {
    private final Canvas canvas;
    private final int minTx;    // 视图左上角瓦片 X 索引（包含）
    private final int minTy;    // 视图左上角瓦片 Y 索引（包含）
    private final int tileW;    // 视图宽度（瓦片数）
    private final int tileH;    // 视图高度（瓦片数）

    CanvasView(Canvas  canvas, int minTx, int minTy, int tileW, int tileH) {
        if (tileW <= 0 || tileH <= 0) {
            throw new IllegalArgumentException("Tile width and height must be positive");
        }
        this.canvas = canvas;
        this.minTx = minTx;
        this.minTy = minTy;
        this.tileW = tileW;
        this.tileH = tileH;
    }

    // ───────── Canvas 接口实现 ─────────
    @Override
    public int getChannels() {
        return canvas.getChannels();
    }
    @Override
    public int getTileSize() {
        return canvas.getTileSize();
    }

    @Override
    public int getMinTileX() {
        return minTx;
    }

    @Override
    public int getMaxTileX() {
        return minTx + tileW - 1;
    }

    @Override
    public int getMinTileY() {
        return minTy;
    }

    @Override
    public int getMaxTileY() {
        return minTy + tileH - 1;
    }

    @Override
    public float[] getDefaultPixel() {
        return canvas.getDefaultPixel();
    }

    @Override
    public Tile getTile(int tx, int ty) {
        if (tx < minTx || tx > getMaxTileX() || ty < minTy || ty > getMaxTileY()) {
            return null;
        }
        return canvas.getTile(tx, ty);
    }

    @Override
    public void forEachTile(TileVisitor visitor) {
        for (int ty = minTy; ty <= getMaxTileY(); ty++) {
            for (int tx = minTx; tx <= getMaxTileX(); tx++) {
                Tile tile = canvas.getTile(tx, ty);
                if (tile != null) {
                    float[] data = tile.getPixelsSnapshot();
                    visitor.visit(tx, ty, data);
                }
            }
        }
    }

    @Override
    public int tileCount() {
        int count = 0;
        for (int ty = minTy; ty <= getMaxTileY(); ty++) {
            for (int tx = minTx; tx <= getMaxTileX(); tx++) {
                if (canvas.getTile(tx, ty) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    // ── 像素读写：将世界坐标转换为瓦片坐标，再委托给底层画布 ──
    @Override
    public void getPixel(int worldX, int worldY, float[] out) {
        int tileSize = getTileSize();
        int tx = TiledCanvas.tileX(worldX, tileSize);
        int ty = TiledCanvas.tileY(worldY, tileSize);
        if (tx < minTx || tx > getMaxTileX() || ty < minTy || ty > getMaxTileY()) {
            Arrays.fill(out, 0f);
            return;
        }
        canvas.getPixel(worldX, worldY, out);
    }

    @Deprecated
    @Override
    public void setPixel(int worldX, int worldY, float r, float g, float b, float a) {
        setPixel(worldX, worldY, new float[]{r, g, b, a});
    }

    // 通用数组版像素写入（带视图裁剪）
    @Override
    public void setPixel(int worldX, int worldY, float[] pixel) {
        int tileSize = getTileSize();
        int tx = TiledCanvas.tileX(worldX, tileSize);
        int ty = TiledCanvas.tileY(worldY, tileSize);
        if (tx < minTx || tx > getMaxTileX() || ty < minTy || ty > getMaxTileY()) {
            return;   // 视图外直接忽略
        }
        canvas.setPixel(worldX, worldY, pixel);
    }

    @Override
    public void readBytes(float[] dest, int destOffset, int worldX, int worldY,
                          int w, int h, int destRowStride) {
        int tileSize = getTileSize();
        int viewMinX = minTx * tileSize;
        int viewMinY = minTy * tileSize;
        int viewMaxX = (getMaxTileX() + 1) * tileSize;
        int viewMaxY = (getMaxTileY() + 1) * tileSize;

        int x0 = Math.max(worldX, viewMinX);
        int y0 = Math.max(worldY, viewMinY);
        int x1 = Math.min(worldX + w, viewMaxX);
        int y1 = Math.min(worldY + h, viewMaxY);
        if (x0 >= x1 || y0 >= y1) return;

        int offsetX = x0 - worldX;
        int offsetY = y0 - worldY;
        int stride = (destRowStride <= 0) ? w : destRowStride;
        int adjustedOffset = destOffset + (offsetY * stride + offsetX) * getChannels();

        canvas.readBytes(dest, adjustedOffset, x0, y0, x1 - x0, y1 - y0, stride);
    }

    @Override
    public void writeBytes(float[] src, int srcOffset, int worldX, int worldY,
                           int w, int h, int srcRowStride) {
        int tileSize = getTileSize();
        int viewMinX = minTx * tileSize;
        int viewMinY = minTy * tileSize;
        int viewMaxX = (getMaxTileX() + 1) * tileSize;
        int viewMaxY = (getMaxTileY() + 1) * tileSize;

        int x0 = Math.max(worldX, viewMinX);
        int y0 = Math.max(worldY, viewMinY);
        int x1 = Math.min(worldX + w, viewMaxX);
        int y1 = Math.min(worldY + h, viewMaxY);
        if (x0 >= x1 || y0 >= y1) return;

        int offsetX = x0 - worldX;
        int offsetY = y0 - worldY;
        int stride = (srcRowStride <= 0) ? w : srcRowStride;
        int adjustedOffset = srcOffset + (offsetY * stride + offsetX) * getChannels();

        canvas.writeBytes(src, adjustedOffset, x0, y0, x1 - x0, y1 - y0, stride);
    }

    @Override
    public void fillRect(int worldX, int worldY, int w, int h, float[] color) {
        int tileSize = getTileSize();
        int viewMinX = minTx * tileSize;
        int viewMinY = minTy * tileSize;
        int viewMaxX = (getMaxTileX() + 1) * tileSize;
        int viewMaxY = (getMaxTileY() + 1) * tileSize;

        int x0 = Math.max(worldX, viewMinX);
        int y0 = Math.max(worldY, viewMinY);
        int x1 = Math.min(worldX + w, viewMaxX);
        int y1 = Math.min(worldY + h, viewMaxY);
        if (x0 >= x1 || y0 >= y1) return;

        canvas.fillRect(x0, y0, x1 - x0, y1 - y0, color);
    }

    @Override
    public void clear() {
        canvas.fillRect(minTx * getTileSize(), minTy * getTileSize(),
                tileW * getTileSize(), tileH * getTileSize(),
                canvas.getDefaultPixel());
    }

    @Override
    public void getBounds(int[] out) {
        if (out == null || out.length < 4)
            throw new IllegalArgumentException("out array must have length >= 4");
        out[0] = minTx * getTileSize();
        out[1] = minTy * getTileSize();
        out[2] = (getMaxTileX() + 1) * getTileSize() - 1;
        out[3] = (getMaxTileY() + 1) * getTileSize() - 1;
    }

    @Override
    public SequentialIterator createSequentialIterator(int worldX, int worldY,
                                                       int w, int h,
                                                       boolean writable, ScanOrder order) {
        int tileSize = getTileSize();
        int viewMinX = minTx * tileSize;
        int viewMinY = minTy * tileSize;
        int viewMaxX = (getMaxTileX() + 1) * tileSize;
        int viewMaxY = (getMaxTileY() + 1) * tileSize;

        int x0 = Math.max(worldX, viewMinX);
        int y0 = Math.max(worldY, viewMinY);
        int x1 = Math.min(worldX + w, viewMaxX);
        int y1 = Math.min(worldY + h, viewMaxY);
        if (x0 >= x1 || y0 >= y1) {
            return new EmptySequentialIterator();
        }
        return canvas.createSequentialIterator(x0, y0, x1 - x0, y1 - y0, writable, order);
    }

    @Override
    public SequentialIterator createSequentialIterator(int worldX, int worldY,
                                                       int w, int h,
                                                       boolean writable) {
        return createSequentialIterator(worldX, worldY, w, h, writable, ScanOrder.ROW_MAJOR);
    }

    @Override
    public RandomAccessIterator createRandomAccessIterator(boolean writable) {
        return new ViewRandomAccessIterator(writable);
    }

    @Override
    public void readTiles(Consumer<Map<Long, float[]>> consumer) {
        Map<Long, float[]> snapshot = new HashMap<>();
        for (int ty = minTy; ty <= getMaxTileY(); ty++) {
            for (int tx = minTx; tx <= getMaxTileX(); tx++) {
                Tile tile = canvas.getTile(tx, ty);
                if (tile != null) {
                    snapshot.put(TiledCanvas.pack(tx, ty), tile.getPixelsSnapshot());
                }
            }
        }
        consumer.accept(Collections.unmodifiableMap(snapshot));
    }

    @Override
    public void writeTiles(Map<Long, float[]> newTiles) {
        // 过滤出视图范围内的瓦片键，其余忽略
        Map<Long, float[]> filtered = new HashMap<>();
        for (Map.Entry<Long, float[]> entry : newTiles.entrySet()) {
            long key = entry.getKey();
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);
            if (tx >= minTx && tx <= getMaxTileX() && ty >= minTy && ty <= getMaxTileY()) {
                filtered.put(key, entry.getValue());
            }
        }
        if (!filtered.isEmpty()) {
            canvas.writeTiles(filtered);   // 委托给底层画布（TiledCanvas）
        }
    }

    @Override
    public Canvas subCanvas(int x, int y, int w, int h) {
        int tileSize = getTileSize();
        int viewMinX = minTx * tileSize;
        int viewMinY = minTy * tileSize;
        int viewMaxX = (getMaxTileX() + 1) * tileSize - 1;
        int viewMaxY = (getMaxTileY() + 1) * tileSize - 1;

        // 裁剪到当前视图范围内
        int x0 = Math.max(x, viewMinX);
        int y0 = Math.max(y, viewMinY);
        int x1 = Math.min(x + w - 1, viewMaxX);
        int y1 = Math.min(y + h - 1, viewMaxY);
        if (x0 > x1 || y0 > y1) {
            // 调用者保证区域重叠，否则抛出异常。
            throw new IllegalArgumentException("Requested sub-region is outside the view");
        }

        int minTxNew = TiledCanvas.tileX(x0, tileSize);
        int minTyNew = TiledCanvas.tileY(y0, tileSize);
        int maxTxNew = TiledCanvas.tileX(x1, tileSize);
        int maxTyNew = TiledCanvas.tileY(y1, tileSize);
        int tileWNew = maxTxNew - minTxNew + 1;
        int tileHNew = maxTyNew - minTyNew + 1;

        return new CanvasView(canvas, minTxNew, minTyNew, tileWNew, tileHNew);
    }

    @Override
    public List<Canvas> split(int tileSpan) {
        if (tileSpan <= 0) throw new IllegalArgumentException("tileSpan must be positive");
        List<Canvas> tiles = new ArrayList<>();
        int ts = getTileSize();
        int minTx = getMinTileX();
        int maxTx = getMaxTileX();
        int minTy = getMinTileY();
        int maxTy = getMaxTileY();
        if (minTx > maxTx || minTy > maxTy) return tiles;

        for (int ty = minTy; ty <= maxTy; ty += tileSpan) {
            int curH = Math.min(tileSpan, maxTy - ty + 1);
            for (int tx = minTx; tx <= maxTx; tx += tileSpan) {
                int curW = Math.min(tileSpan, maxTx - tx + 1);
                tiles.add(subCanvas(tx * ts, ty * ts, curW * ts, curH * ts));
            }
        }
        return tiles;
    }

    @Override
    public List<Canvas> split() {
        return split(1);
    }

    // ───────── 视图感知的随机访问迭代器 ─────────
    private class ViewRandomAccessIterator implements RandomAccessIterator {
        private final RandomAccessIterator delegate;
        private final int minX, minY, maxX, maxY; // 视图像素范围

        ViewRandomAccessIterator(boolean writable) {
            int tileSize = getTileSize();
            this.minX = minTx * tileSize;
            this.minY = minTy * tileSize;
            this.maxX = (getMaxTileX() + 1) * tileSize - 1;
            this.maxY = (getMaxTileY() + 1) * tileSize - 1;
            this.delegate = canvas.createRandomAccessIterator(writable);
        }

        @Override
        public void moveTo(int x, int y) {
            delegate.moveTo(x, y);
        }

        @Override
        public void getPixel(float[] out) {
            int x = delegate.x();
            int y = delegate.y();
            if (x < minX || x > maxX || y < minY || y > maxY) {
                Arrays.fill(out, 0f);
                return;
            }
            delegate.getPixel(out);
        }

        @Override
        @Deprecated
        public void setPixel(float r, float g, float b, float a) {
            setPixel(new float[]{r, g, b, a});
        }

        // 通用数组版本，增加视图裁剪
        @Override
        public void setPixel(float[] pixel) {
            int x = delegate.x();
            int y = delegate.y();
            if (x < minX || x > maxX || y < minY || y > maxY) {
                return;   // 视图外忽略
            }
            delegate.setPixel(pixel);
        }

        @Override
        public int x() {
            return delegate.x();
        }

        @Override
        public int y() {
            return delegate.y();
        }

        @Override
        public boolean isWritable() {
            return delegate.isWritable();
        }
    }

    // 空顺序迭代器
    private static class EmptySequentialIterator implements SequentialIterator {

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public void getPixel(float[] out) {

        }

        @Override
        public void setPixel(float r, float g, float b, float a) {

        }

        @Override
        public int x() {
            return 0;
        }

        @Override
        public int y() {
            return 0;
        }

        @Override
        public void reset() {

        }
    }
}