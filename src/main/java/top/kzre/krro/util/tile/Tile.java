package top.kzre.krro.util.tile;

/**
 * 瓦片句柄，持有对 TileData 的引用，支持 COW 写入。
 * 线程安全（所有公开方法使用 synchronized 保证可见性和原子性）。
 */
public abstract class Tile {
    abstract void replaceData(TileData newData);

    abstract TileData getDataRef();

    public abstract int tx();

    public abstract int ty();

    @Deprecated
    public abstract void setPixel(int localX, int localY, float r, float g, float b, float a, int tileSize);

    @Deprecated
    public abstract void getPixel(int localX, int localY, float[] out, int tileSize);

    public abstract void setPixel(int localX, int localY, float[] pixel,
                  int tileSize, int channels);

    public abstract void getPixel(int localX, int localY, float[] out,
                  int tileSize, int channels);

    public abstract float[] getPixelsSnapshot();

    public abstract float[] getPixelsForWrite();
}