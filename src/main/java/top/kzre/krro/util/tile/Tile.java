package top.kzre.krro.util.tile;

/**
 * 瓦片句柄，持有对 TileData 的引用，支持 COW 写入。
 * 线程安全（所有公开方法使用 synchronized 保证可见性和原子性）。
 */
public final class Tile {
    private final int tx, ty;
    private volatile TileData data;

    /**
     * 构造 Tile，直接持有传入的 data（假设 data 已经包含初始引用）。
     * 不再额外 acquire，由调用者确保 data 的引用计数已正确增加。
     */
    Tile(int tx, int ty, TileData data) {
        this.tx = tx;
        this.ty = ty;
        this.data = data;
    }

    public int tx() { return tx; }
    public int ty() { return ty; }

    /**
     * 确保数据可写：若引用计数 > 1，则克隆数据并替换。
     * 必须在持有锁时调用。
     */
    private void ensureWritable() {
        TileData current = data;
        if (current.refCount() > 1) {
            TileData newData = current.cloneData();
            current.release();          // 释放旧数据
            data = newData;             // 新数据引用计数为 1
        }
    }

    public synchronized void setPixel(int localX, int localY, float r, float g, float b, float a, int tileSize) {
        ensureWritable();
        float[] pix = data.getPixels();
        int idx = (localY * tileSize + localX) * 4;
        pix[idx] = r;
        pix[idx+1] = g;
        pix[idx+2] = b;
        pix[idx+3] = a;
    }

    public synchronized void getPixel(int localX, int localY, float[] out, int tileSize) {
        float[] pix = data.getPixels();
        int idx = (localY * tileSize + localX) * 4;
        out[0] = pix[idx];
        out[1] = pix[idx+1];
        out[2] = pix[idx+2];
        out[3] = pix[idx+3];
    }

    public synchronized float[] getPixelsSnapshot() {
        return data.getPixels();
    }

    public synchronized float[] getPixelsForWrite(int tileSize) {
        ensureWritable();
        return data.getPixels();
    }

    /**
     * 替换当前数据（用于共享）。调用者需保证新数据的引用计数已增加。
     */
    synchronized void replaceData(TileData newData) {
        data.release();
        data = newData;
        newData.acquire();  // 增加当前 Tile 的引用
    }

    /** 包内方法：获取当前数据引用（不增加引用计数） */
    synchronized TileData getDataRef() {
        return data;
    }
}