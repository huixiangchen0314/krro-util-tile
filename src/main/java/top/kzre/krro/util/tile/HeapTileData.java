package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.pool.PoolManagers;

/**
 * JVM堆内存储的瓦片数据
 */
public final class HeapTileData extends AbstractTileData {

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
            FloatsPools.getPool(pixels.length).release(pixels);
            pixels = null;
        }
    }


    @Override
    public int getByteSize() {
        return size * 4;
    }

    @Override
    public TileData copy() {
        FloatsHolder holder = PoolManagers.floats().getHolder();
        float[] newPixels = holder.getPool(size).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, size);
        return new HeapTileData(newPixels);
    }


}
