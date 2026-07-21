package top.kzre.krro.util.tile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileStorageManagerTest {

    private TileStorageManager manager;

    @BeforeEach
    void setUp() {
        manager = TileStorageManager.instance();
        manager.reset();   // 清空之前测试的影响
    }

    private TileData createHeapTile(int sizeInFloats) {
        float[] pixels = new float[sizeInFloats];
        return new TileData(pixels);
    }

    @Test
    void instanceShouldBeSingleton() {
        assertSame(manager, TileStorageManager.instance());
    }

    @Test
    void reportNewTileShouldIncreaseHeapMemory() {
        TileData tile = createHeapTile(64 * 64 * 4);
        assertEquals(CacheLevel.HEAP, tile.getLevel());
        long memoryBefore = manager.totalHeapMemory();
        manager.report(tile); // 首次报告
        long memoryAfter = manager.totalHeapMemory();
        assertEquals(tile.getByteSize(), memoryAfter - memoryBefore);
    }

    @Test
    void reportExistingTileShouldNotDuplicate() {
        TileData tile = createHeapTile(100);
        manager.report(tile);
        long memory = manager.totalHeapMemory();
        manager.report(tile);
        assertEquals(memory, manager.totalHeapMemory()); // 大小不变
    }

    @Test
    void removeShouldFreeHeapMemory() {
        TileData tile = createHeapTile(200);
        manager.report(tile);
        long memoryBefore = manager.totalHeapMemory();
        manager.remove(tile);
        long memoryAfter = manager.totalHeapMemory();
        assertEquals(0, memoryAfter);
        assertTrue(memoryBefore > memoryAfter);
    }

    @Test
    void totalHeapMemoryShouldSumAllHeapTiles() {
        TileData tile1 = createHeapTile(100);
        TileData tile2 = createHeapTile(200);
        manager.report(tile1);
        manager.report(tile2);
        long expected = tile1.getByteSize() + tile2.getByteSize();
        assertEquals(expected, manager.totalHeapMemory());
    }

    @Test
    void totalHeapMemoryShouldIgnoreDirectTiles() {
        TileData tile = createHeapTile(100);
        manager.report(tile);
        tile.demoteToDirect(); // 降级后不再计入堆内存
        assertEquals(0, manager.totalHeapMemory());
    }

    @Test
    void evictIfNeededShouldDemoteWhenOverLimit() {
        // 设置很小的堆内存限制
        manager.getConfig().setMaxHeapMemoryBytes(1000);
        List<TileData> tiles = new ArrayList<>();
        // 创建多个瓦片，每个占用 400 字节（100 floats * 4）
        for (int i = 0; i < 10; i++) {
            TileData tile = createHeapTile(100);
            manager.report(tile);   // 内部调用 evictIfNeeded
            tiles.add(tile);
        }
        // 堆内存总量不应超过限制太多，部分瓦片应已降级
        long heapMemory = manager.totalHeapMemory();
        assertTrue(heapMemory <= 1000 + 400); // 允许一次超出（最后加入时可能超限但还没来得及降级）
        // 至少有一个瓦片被降级
        boolean hasDirect = tiles.stream().anyMatch(t -> t.getLevel() == CacheLevel.DIRECT);
        assertTrue(hasDirect);
    }

    @Test
    void evictShouldNotDemoteSharedTile() {
        manager.getConfig().setMaxHeapMemoryBytes(100);
        TileData shared = createHeapTile(100);
        shared.acquire(); // refCount = 2
        manager.report(shared);
        // 再添加一个瓦片触发淘汰
        TileData another = createHeapTile(50);
        manager.report(another);
        // 共享瓦片不应被降级
        assertEquals(CacheLevel.HEAP, shared.getLevel());
    }

    @Test
    void evictShouldStopWhenNoEligibleTile() {
        manager.getConfig().setMaxHeapMemoryBytes(50);
        TileData tile = createHeapTile(100);
        tile.acquire(); // refCount = 2，不可降级
        manager.report(tile);
        // 添加另一个瓦片触发淘汰，但唯一可降级的目标是共享的，降级应失败
        TileData another = createHeapTile(50);
        manager.report(another); // 不应陷入死循环
        // 内存可能仍超限，但不应崩溃
        assertTrue(manager.totalHeapMemory() > 0);
    }
}