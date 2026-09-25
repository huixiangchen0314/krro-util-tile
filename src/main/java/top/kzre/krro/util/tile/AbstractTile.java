package top.kzre.krro.util.tile;

public class AbstractTile extends Tile {
    private final int tx, ty;
    private volatile TileData data;

    /**
     * 构造 Tile，直接持有传入的 data（假设 data 已经包含初始引用）。
     * 不再额外 acquire，由调用者确保 data 的引用计数已正确增加。
     */
    AbstractTile(int tx, int ty, TileData data) {
        this.tx = tx;
        this.ty = ty;
        this.data = data;
    }

    @Override
    public int tx() { return tx; }
    @Override
    public int ty() { return ty; }

    /**
     * 确保数据可写：若引用计数 > 1，则克隆数据并替换。
     * 必须在持有锁时调用。
     */
    private void ensureWritable() {
        ensureHeapTileData();
        TileData current = data;
        if (current.refCount() > 1) {
            TileData newData = current.copy();
            current.release();          // 释放旧数据
            data = newData;             // 新数据引用计数为 1
        }
        data.markDirty();
    }

    private void ensureHeapTileData(){
        TileData current = data;
        if (current instanceof HeapTileData) {
            return;    // 已是 HeapTileData，无需转换
        }

        // ── 降级路径埋点：谁在把 GPU 瓦片换回 CPU ──
        {
            StringBuilder sb = new StringBuilder("TileData lifted:");
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                sb.append("\n>>>>>at ").append(e);
            }
            System.out.println(sb);
        }


        int floatCount = current.getByteSize() / Float.BYTES;
        float[] pixels = new float[floatCount];
        current.floatBuffer().get(pixels);

        current.release();          // 释放当前引用（AbstractTile 持有的那一个）
        data = new HeapTileData(pixels);   // 新实例，refCount = 1
    }

    @Override
    public synchronized boolean compareAndReplaceData(Tile other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (other == this) return false;

        // 一次读源数据——它既是源，也是版本快照
        TileData src = other.getDataRef();
        if (src == null) return false;

        // 版本是 VersionedTile 的能力，不是 TileData 的——
        // 先判能力接口，再转型
        if (!(src instanceof VersionedTile)) return false;
        Object srcVer = ((VersionedTile) src).version();

        if (this.version() != srcVer) return false;

        src.acquire();
        data.release();
        data = src;
        return true;
    }

    // ────────── 旧版 RGBA 四通道接口（已废弃，请使用通用版本）──────────
    /** @deprecated 使用 {@link #setPixel(int, int, float[], int, int)} 替代 */
    @Deprecated
    @Override
    public synchronized void setPixel(int localX, int localY, float r, float g, float b, float a, int tileSize) {
        setPixel(localX, localY, new float[]{r, g, b, a}, tileSize, 4);
    }

    /** @deprecated 使用 {@link #getPixel(int, int, float[], int, int)} 替代 */
    @Deprecated
    @Override
    public synchronized void getPixel(int localX, int localY, float[] out, int tileSize) {
        getPixel(localX, localY, out, tileSize, 4);
    }

    // ────────── 通用多通道接口 ──────────
    /**
     * 以数组形式设置像素，通道数由 {@code channels} 指定。
     * @param pixel 长度至少为 channels
     */
    @Override
    public synchronized void setPixel(int localX, int localY, float[] pixel,
                                      int tileSize, int channels) {
        ensureWritable();
        float[] pix = data.getPixels();
        int idx = (localY * tileSize + localX) * channels;
        System.arraycopy(pixel, 0, pix, idx, channels);
    }

    /**
     * 读取像素到提供的数组中，写入 {@code channels} 个通道值。
     */
    @Override
    public synchronized void getPixel(int localX, int localY, float[] out,
                                      int tileSize, int channels) {
        float[] pix = data.getPixels();
        int idx = (localY * tileSize + localX) * channels;
        System.arraycopy(pix, idx, out, 0, channels);
    }

    @Override
    public synchronized float[] getPixelsSnapshot() {
        return data.getPixels();
    }

    @Override
    public synchronized float[] getPixelsForWrite() {
        ensureWritable();
        return data.getPixels();
    }

    /**
     * 替换当前数据（用于共享、或存储设施更新）。
     *
     * <p><b>幂等</b>：先 acquire 新数据、再 release 旧数据。当
     * {@code newData == data} 时，acquire 和 release 净效果为零，
     * 引用计数不变，不会触发瞬时的归零副作用。
     *
     * <p><b>调用者不需预先增加引用计数</b>——本方法内部完成。
     *
     * @throws IllegalArgumentException newData 为 null
     */
    @Override
    public synchronized void replaceData(TileData newData) {
        if (newData == null) {
            throw new IllegalArgumentException("newData must not be null");
        }
        newData.acquire();
        data.release();
        data = newData;
    }

    /**
     * 替换数据，**接管** newData 的引用——不 acquire。
     *
     * <p>调用方必须已经持有 newData 的一份引用并放弃它。
     * 与 {@link #replaceData} 的区别：后者 acquire 新数据、release 旧数据，
     * 净效果是"共享一份"；本方法直接换引用，净效果是"接管"。
     *
     * <p>调用方需保证 newData != null。
     */
     synchronized void replaceDataOwned(TileData newData) {
        TileData old = data;
        data = newData;
        old.release();
    }

    /** 包内方法：获取当前数据引用（不增加引用计数） */
    @Override
    synchronized TileData getDataRef() {
        return data;
    }


    @Override
    public String toString() {
        TileData d = data;
        return "Tile{"
                + "tx=" + tx
                + ", ty=" + ty
                + ", data=" + (d == null ? "null" : d.getClass().getSimpleName())
                + ", refs=" + (d == null ? 0 : d.refCount())
                + "}";
    }
}
