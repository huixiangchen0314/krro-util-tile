package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsPools;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 瓦片的像素数据，包含 RGBA float 数组和引用计数。
 * 不可变（通过 clone 创建新实例），线程安全（引用计数使用 AtomicInteger）。
 * 内部 float[] 从 FloatsPools 获取，释放时归还。
 */
final class TileData {
    private final float[] pixels;
    private final AtomicInteger refCount;

    /**
     * 创建 TileData，引用计数初始为 1。
     * 传入的 pixels 数组必须来自池或新分配，且不再被外部直接修改。
     */
    TileData(float[] pixels) {
        this.pixels = pixels;
        this.refCount = new AtomicInteger(1);
    }

    float[] getPixels() {
        return pixels;
    }

    /** 增加引用计数（返回新的计数值） */
    int acquire() {
        return refCount.incrementAndGet();
    }

    /**
     * 减少引用计数，若归零则将像素数组归还给 FloatsPools。
     * @return 剩余引用计数
     */
    int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            int len = pixels.length;
            FloatsPools.getPool(len).release(pixels);
        }
        return remaining;
    }

    int refCount() {
        return refCount.get();
    }

    /**
     * 克隆数据（生成新的独立副本，引用计数为 1）。
     * 新数组从池中获取。
     */
    TileData cloneData() {
        int len = pixels.length;
        float[] newPixels = FloatsPools.getPool(len).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, len);
        return new TileData(newPixels);
    }
}