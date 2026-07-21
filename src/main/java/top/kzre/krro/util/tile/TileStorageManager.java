package top.kzre.krro.util.tile;


import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.WeakHashMap;

class TileStorageManager {
    private static volatile TileStorageManager INSTANCE;

    public static TileStorageManager instance() {
        if (INSTANCE == null) {
            synchronized (TileStorageManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TileStorageManager();
                }
            }
        }
        return INSTANCE;
    }

    @Getter
    private static class TileAccess {
        private final int size;
        @Setter
        private long lastAccessTime;

        TileAccess(int size, long lastAccessTime) {
            this.size = size;
            this.lastAccessTime = lastAccessTime;
        }

    }

    public static class Config {
        private long maxHeapMemoryBytes = 256L * 1024 * 1024;
        private long maxDirectMemoryBytes = 256L * 1024 * 1024;

        public synchronized void setMaxHeapMemoryBytes(long bytes) {
            this.maxHeapMemoryBytes = bytes;
        }

        public synchronized long getMaxHeapMemoryBytes() {
            return maxHeapMemoryBytes;
        }

        public synchronized void setMaxDirectMemoryBytes(long bytes) {
            this.maxDirectMemoryBytes = bytes;
        }

        public synchronized long getMaxDirectMemoryBytes() {
            return maxDirectMemoryBytes;
        }
    }

    @Getter
    private final Config config = new Config();
    private final WeakHashMap<TileData, TileAccess> tiles = new WeakHashMap<>();

    private TileStorageManager() {}

    public synchronized void report(TileData data) {
        TileAccess access = tiles.get(data);
        if (access == null) {
            tiles.put(data, new TileAccess(data.getByteSize(), System.nanoTime()));
            evictIfNeeded();      // 新瓦片首次报告，检查并触发淘汰
        } else {
            access.setLastAccessTime(System.nanoTime());
        }
    }

    public synchronized void remove(TileData data) {
        tiles.remove(data);
    }

    public synchronized long totalHeapMemory() {
        long total = 0;
        for (Map.Entry<TileData, TileAccess> entry : tiles.entrySet()) {
            if (entry.getKey().getLevel() == CacheLevel.HEAP) {
                total += entry.getValue().getSize();
            }
        }
        return total;
    }

    /**
     * 若堆内存超限，找到最久未使用的 HEAP 瓦片降级为 DIRECT，直到内存回到阈值以下。
     */
    public synchronized void evictIfNeeded() {
        long maxHeap = config.getMaxHeapMemoryBytes();
        while (totalHeapMemory() > maxHeap && !tiles.isEmpty()) {
            TileData candidate = findLRUHeapTile();
            if (candidate == null) {
                break;
            }
            if (!candidate.demoteToDirect()) {
                break;
            }
        }
    }

    /**
     * 从当前跟踪的瓦片中找出最久未访问且可降级（引用计数为1，状态为HEAP）的瓦片。
     * 必须在持有 TileStorageManager 对象锁时调用。
     * @return 候选瓦片，若没有符合条件者则返回 null
     */
    private TileData findLRUHeapTile() {
        TileData candidate = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<TileData, TileAccess> entry : tiles.entrySet()) {
            TileData data = entry.getKey();
            if (data.getLevel() != CacheLevel.HEAP || data.refCount() != 1) {
                continue;
            }
            TileAccess access = entry.getValue();
            if (access.getLastAccessTime() < oldestTime) {
                oldestTime = access.getLastAccessTime();
                candidate = data;
            }
        }
        return candidate;
    }

    // 仅供测试使用
    void reset() {
        tiles.clear();
        config.setMaxHeapMemoryBytes(256L * 1024 * 1024);
    }
}