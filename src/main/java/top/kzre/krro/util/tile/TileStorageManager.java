package top.kzre.krro.util.tile;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class TileStorageManager {
    private static volatile TileStorageManager INSTANCE;

    public static TileStorageManager instance() {
        if (INSTANCE == null) {
            synchronized (TileStorageManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TileStorageManager();
                    // 注册 JVM 关闭钩子，确保后台线程被终止
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (INSTANCE != null) {
                            INSTANCE.shutdown();
                        }
                    }, "Krro-Tile-Shutdown"));
                }
            }
        }
        return INSTANCE;
    }

    private final StorageLevel heapLevel = new StorageLevel() {
        @Override
        protected boolean demote(TileData data) {
            return data.demoteToDirect();
        }
    };

    private final ScheduledExecutorService scheduler;

    private TileStorageManager() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Krro Tile Evictor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::evictIfNeeded, 3, 3, TimeUnit.SECONDS);
    }

    /** 注册一个 HEAP 瓦片（由 TiledCanvas 在 ensureTile 后调用） */
    public void register(TileData data) {
        heapLevel.register(data);
    }

    /** 移除瓦片记录（由 TileData.dispose 自动调用，也可手动调用） */
    public void remove(TileData data) {
        heapLevel.remove(data);
    }

    /** 当前堆内存占用（字节） */
    public long totalHeapMemory() {
        return heapLevel.totalMemory();
    }

    /** 手动触发淘汰（通常由后台定时执行） */
    public void evictIfNeeded() {
        heapLevel.evictIfNeeded();
    }

    /** 设置最大堆瓦片数 */
    public void setMaxHeapTiles(int max) {
        heapLevel.setMaxTiles(max);
    }

    /** 设置最大堆内存阈值（字节） */
    public void setMaxHeapMemoryBytes(long bytes) {
        heapLevel.setMaxMemoryBytes(bytes);
    }

    /** 关闭后台线程 */
    public void shutdown() {
        scheduler.shutdown();
    }
}