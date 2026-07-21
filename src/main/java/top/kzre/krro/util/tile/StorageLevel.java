package top.kzre.krro.util.tile;

import lombok.Setter;

import java.util.Map;
import java.util.WeakHashMap;

abstract class StorageLevel {
    // 记录每个 HEAP TileData 的注册时间戳（nanoTime），用于 FIFO 淘汰
    private final WeakHashMap<TileData, Long> registry = new WeakHashMap<>();

    @Setter protected int maxTiles = 2048;
    @Setter protected long maxMemoryBytes = 256L * 1024 * 1024;

    /** 注册一个 HEAP 瓦片 */
    public synchronized void register(TileData data) {
        if (data.getLevel() != CacheLevel.HEAP) return;
        registry.putIfAbsent(data, System.nanoTime());
    }

    /** 移除瓦片记录（由 TileData.dispose 调用） */
    public synchronized void remove(TileData data) {
        registry.remove(data);
    }

    /** 当前 HEAP 内存占用（字节） */
    public synchronized long totalMemory() {
        long total = 0;
        for (TileData data : registry.keySet()) {
            if (data.getLevel() == CacheLevel.HEAP) {
                total += data.getByteSize();
            }
        }
        return total;
    }

    /** 按注册顺序（最旧先淘汰）降级可降级瓦片，直到满足阈值 */
    public synchronized void evictIfNeeded() {
        while (registry.size() > maxTiles || totalMemory() > maxMemoryBytes) {
            // 找出注册时间最早的（值最小）的 TileData
            TileData oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<TileData, Long> entry : registry.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) break;  // 没有可淘汰的瓦片

            // 只有引用计数为 1 且仍为 HEAP 时才降级
            if (oldest.getLevel() == CacheLevel.HEAP && oldest.refCount() == 1) {
                if (demote(oldest)) {
                    // 降级成功，瓦片不再占用 HEAP，从注册表移除
                    registry.remove(oldest);
                } else {
                    break;  // 降级失败，停止淘汰
                }
            } else {
                // 状态不符合，可能是被其他地方修改，直接移除记录
                registry.remove(oldest);
            }
        }
    }

    /** 子类实现具体降级策略 */
    protected abstract boolean demote(TileData data);
}