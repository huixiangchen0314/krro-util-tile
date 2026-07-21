package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsPools;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 瓦片的像素数据，持有池化 RGBA float 数组，支持引用计数和写时拷贝（COW）。
 */
final class TileData {
    private final float[] pixels;
    private final AtomicInteger refCount;

    /**
     * 创建引用计数为 1 的 TileData，直接持有传入的池化数组。
     */
    TileData(float[] pixels) {
        this.pixels = pixels;
        this.refCount = new AtomicInteger(1);
    }

    /** 返回内部像素数组（只读，调用者不可修改）。 */
    float[] getPixels() {
        return pixels;
    }

    /** 增加引用计数，返回新计数值。 */
    int acquire() {
        return refCount.incrementAndGet();
    }

    /**
     * 减少引用计数，若归零则将像素数组归还池。
     * @return 剩余引用计数
     */
    int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            FloatsPools.getPool(pixels.length).release(pixels);
        }
        return remaining;
    }

    int refCount() {
        return refCount.get();
    }

    /**
     * 创建数据的独立副本，新引用计数为 1，新数组从池中分配。
     */
    TileData cloneData() {
        int len = pixels.length;
        float[] newPixels = FloatsPools.getPool(len).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, len);
        return new TileData(newPixels);
    }
}