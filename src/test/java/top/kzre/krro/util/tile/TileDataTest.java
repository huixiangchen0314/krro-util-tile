package top.kzre.krro.util.tile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class TileDataTest {
    private TileData data;
    private float[] originalPixels;
    private static final int PIXEL_COUNT = 64 * 64 * 4;

    @BeforeEach
    void setUp() {
        originalPixels = new float[PIXEL_COUNT];
        for (int i = 0; i < originalPixels.length; i++) {
            originalPixels[i] = i * 0.001f;
        }
        data = new TileData(originalPixels);
        // 确保每个测试前管理器状态干净
        TileStorageManager.instance().reset();
    }

    @Test
    void initialRefCountShouldBeOne() {
        assertEquals(1, data.refCount());
    }

    @Test
    void acquireShouldIncreaseRefCount() {
        int rc = data.acquire();
        assertEquals(2, rc);
        assertEquals(2, data.refCount());
    }

    @Test
    void releaseShouldDecreaseRefCount() {
        data.acquire(); // 2
        int rc = data.release(); // 1
        assertEquals(1, rc);
        assertNotEquals(0, data.refCount());
    }

    @Test
    void releaseToZeroShouldDispose() {
        assertEquals(1, data.refCount());
        data.release(); // 0
        assertEquals(0, data.refCount());
        // 再次调用 getPixels 会重新分配？但释放后对象不应再被使用，这里仅验证计数归零
    }

    @Test
    void getPixelsShouldReturnOriginalArray() {
        assertSame(originalPixels, data.getPixels());
    }

    @Test
    void byteSizeShouldBeCorrect() {
        assertEquals(PIXEL_COUNT * 4, data.getByteSize());
    }

    @Test
    void initialLevelShouldBeHeap() {
        assertEquals(CacheLevel.HEAP, data.getLevel());
    }

    @Test
    void demoteToDirectShouldSucceedWhenRefCountIsOne() {
        assertTrue(data.demoteToDirect());
        assertEquals(CacheLevel.DIRECT, data.getLevel());
    }

    @Test
    void demoteToDirectShouldFailWhenRefCountGreaterThanOne() {
        data.acquire(); // refCount = 2
        assertFalse(data.demoteToDirect());
        assertEquals(CacheLevel.HEAP, data.getLevel());
    }

    @Test
    void accessAfterDemoteShouldUpgradeBackToHeap() {
        data.demoteToDirect();
        assertEquals(CacheLevel.DIRECT, data.getLevel());
        float[] pixels = data.getPixels(); // 自动升级
        assertEquals(CacheLevel.HEAP, data.getLevel());
        assertNotNull(pixels);
        // 验证数据一致性
        assertArrayEquals(originalPixels, pixels, 0.001f);
    }

    @Test
    void cloneShouldWorkOnDirectData() {
        data.demoteToDirect();
        TileData clone = data.cloneData(); // cloneData 内部调用 ensureHeap
        assertEquals(CacheLevel.HEAP, clone.getLevel());
        assertArrayEquals(originalPixels, clone.getPixels(), 0.001f);
        // 原对象应该被升级回 HEAP
        assertEquals(CacheLevel.HEAP, data.getLevel());
    }

    @Test
    void cloneShouldCreateIndependentCopy() {
        TileData clone = data.cloneData();
        assertNotSame(data, clone);
        assertNotSame(originalPixels, clone.getPixels());
        assertArrayEquals(originalPixels, clone.getPixels(), 0.001f);
        clone.getPixels()[0] = 0.5f;
        assertNotEquals(0.5f, data.getPixels()[0], 0.001f);
    }

    @Test
    void cloneShouldHaveRefCountOne() {
        TileData clone = data.cloneData();
        assertEquals(1, clone.refCount());
    }

    @Test
    void reportShouldBeCalledOnGetPixels() {
        data.getPixels();  // 触发报告
        assertTrue(TileStorageManager.instance().totalHeapMemory() > 0);
    }
}