package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsPools;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

final class TileData {

    private final AtomicInteger refCount;

    private CacheLevel level;


    private final int size;

    private float[] pixels;
    private ByteBuffer directBuffer;

    TileData(float[] pixels) {
        this.pixels = pixels;
        this.size = pixels.length;
        this.refCount = new AtomicInteger(1);
        this.level = CacheLevel.HEAP;
    }

    float[] getPixels() {
        ensureHeap();
        return pixels;
    }

    int acquire() {
        return refCount.incrementAndGet();
    }

    int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            dispose();
        }
        return remaining;
    }

    synchronized CacheLevel getLevel() {
        return level;
    }

    private void dispose() {
        TileStorageManager.instance().remove(this);  // 从管理器注销
        switch (level) {
            case HEAP:
                if (pixels != null) {
                    FloatsPools.getPool(pixels.length).release(pixels);
                    pixels = null;
                }
                break;
            case DIRECT:
                directBuffer = null;
                break;
        }
    }

    int refCount() {
        return refCount.get();
    }

    TileData cloneData() {
        ensureHeap();
        float[] newPixels = FloatsPools.getPool(size).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, size);
        return new TileData(newPixels);
    }

    public int getByteSize() {
        return size * 4;
    }

    // ---------- 缓存层级切换（供管理器调用）----------
    synchronized boolean demoteToDirect() {
        if (level != CacheLevel.HEAP || refCount.get() != 1) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        buffer.asFloatBuffer().put(pixels);
        directBuffer = buffer;
        FloatsPools.getPool(pixels.length).release(pixels);
        pixels = null;
        level = CacheLevel.DIRECT;
        return true;
    }

    private synchronized void ensureHeap() {
        if (level == CacheLevel.HEAP) return;
        if (level == CacheLevel.DIRECT) {
            float[] newPixels = FloatsPools.getPool(size / 4).acquire();
            directBuffer.asFloatBuffer().get(newPixels);
            directBuffer = null;
            pixels = newPixels;
            level = CacheLevel.HEAP;
        }
    }
}