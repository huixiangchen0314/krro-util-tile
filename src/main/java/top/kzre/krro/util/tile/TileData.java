package top.kzre.krro.util.tile;

import lombok.Getter;
import top.kzre.krro.util.pool.FloatsPools;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

final class TileData {

    private final AtomicInteger refCount;


    private CacheLevel level;

    // 构造时确定的堆内存字节数（像素数组长度×4）
    @Getter
    private final int byteSize;

    // HEAP 状态
    private float[] pixels;

    // DIRECT 状态
    private ByteBuffer directBuffer;

    /**
     * 创建 HEAP 状态的 TileData。
     */
    TileData(float[] pixels) {
        this.pixels = pixels;
        this.byteSize = pixels.length * 4;  // 固定值
        this.refCount = new AtomicInteger(1);
        this.level = CacheLevel.HEAP;
    }

    /** 返回只读像素数组，若当前为 DIRECT 则自动升级到 HEAP。 */
    float[] getPixels() {
        TileStorageManager.instance().report(this);
        ensureHeap();
        return pixels;
    }

    int acquire() {
        int rc = refCount.incrementAndGet();          // 先增加计数
        TileStorageManager.instance().report(this);   // 再报告
        return rc;
    }

    int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            TileStorageManager.instance().remove(this);
            dispose();
        }
        return remaining;
    }

    synchronized CacheLevel getLevel() {
        return level;
    }

    private void dispose() {
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

    /** 深拷贝，生成一个完全独立的 HEAP 副本。 */
    TileData cloneData() {
        ensureHeap();  // 确保数据在堆上
        float[] newPixels = FloatsPools.getPool(byteSize / 4).acquire();
        System.arraycopy(pixels, 0, newPixels, 0, byteSize / 4);
        return new TileData(newPixels);
    }

    // ---------- 缓存层级切换 ----------

    /** 仅由 TileStorageManager 调用，将 HEAP 降级为 DIRECT。 */
    synchronized boolean demoteToDirect() {
        if (level != CacheLevel.HEAP || refCount.get() != 1) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(byteSize);
        buffer.asFloatBuffer().put(pixels);
        directBuffer = buffer;
        FloatsPools.getPool(pixels.length).release(pixels);
        pixels = null;
        level = CacheLevel.DIRECT;
        TileStorageManager.instance().report(this); // 更新统计（此时sizeInBytes返回0）
        return true;
    }

    /** 确保数据在 HEAP，若当前为 DIRECT 则升级。 */
    private synchronized void ensureHeap() {
        if (level == CacheLevel.HEAP) return;
        if (level == CacheLevel.DIRECT) {
            float[] newPixels = FloatsPools.getPool(byteSize / 4).acquire();
            directBuffer.asFloatBuffer().get(newPixels);
            directBuffer = null;
            pixels = newPixels;
            level = CacheLevel.HEAP;
            TileStorageManager.instance().report(this); // 更新统计（此时sizeInBytes返回heapSizeBytes）
        }
    }
}