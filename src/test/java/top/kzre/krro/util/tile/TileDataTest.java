package top.kzre.krro.util.tile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileDataTest {
    private TileData data;
    private float[] originalPixels;

    @BeforeEach
    void setUp() {
        originalPixels = new float[64 * 64 * 4]; // 任意尺寸
        for (int i = 0; i < originalPixels.length; i++) {
            originalPixels[i] = i * 0.001f;
        }
        data = new TileData(originalPixels);
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
    void releaseToZeroShouldBeAllowed() {
        int rc = data.release(); // 0
        assertEquals(0, rc);
        // 再次释放可能抛出异常？取决于实现，这里仅验证计数正确
        assertEquals(0, data.refCount());
    }

    @Test
    void getPixelsShouldReturnOriginalArray() {
        assertSame(originalPixels, data.getPixels());
    }

    @Test
    void cloneDataShouldCreateIndependentCopy() {
        TileData clone = data.cloneData();
        assertNotSame(data, clone);
        assertNotSame(originalPixels, clone.getPixels());
        assertArrayEquals(originalPixels, clone.getPixels(), 0.001f);
        // 修改克隆不影响原数据
        clone.getPixels()[0] = 0.5f;
        assertNotEquals(0.5f, data.getPixels()[0], 0.001f);
    }

    @Test
    void cloneShouldHaveRefCountOne() {
        TileData clone = data.cloneData();
        assertEquals(1, clone.refCount());
    }
}