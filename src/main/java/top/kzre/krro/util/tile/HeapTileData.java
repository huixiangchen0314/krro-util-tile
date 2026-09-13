package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.*;

/**
 * JVM堆内存储的瓦片数据
 */
public final class HeapTileData extends AbstractTileData {
    private static final FloatsHolder holder;
    static {
         holder = PoolManagers.floats().getHolder();
    }
    private final int size;

    private float[] pixels;

    public HeapTileData(float[] pixels) {
        this.pixels = pixels;
        this.size = pixels.length;
    }

    @Override
    public float[] getPixels() {
        return pixels;
    }


    @Override
    protected void onRelease() {
        if (pixels != null) {
            holder.getPool(pixels.length).release(pixels);
            pixels = null;
        }
    }


    @Override
    public int getByteSize() {
        return size * 4;
    }

    @Override
    public TileData copy() {
        float[] newPixels = holder.getPool(size).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, size);
        return new HeapTileData(newPixels);
    }


}
