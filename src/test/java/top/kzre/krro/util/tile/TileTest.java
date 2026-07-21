package top.kzre.krro.util.tile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileTest {
    private Tile tile;
    private TileData data;
    private static final int TILE_SIZE = 4; // 小尺寸便于验证
    private float[] pixels;

    @BeforeEach
    void setUp() {
        pixels = new float[TILE_SIZE * TILE_SIZE * 4];
        // 初始化唯一值用于识别
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i / 100.0f;
        }
        data = new TileData(pixels);
        tile = new Tile(2, -3, data); // 任意瓦片坐标
    }

    @Test
    void txAndTyShouldBeCorrect() {
        assertEquals(2, tile.tx());
        assertEquals(-3, tile.ty());
    }

    @Test
    void getPixelShouldReturnCorrectValues() {
        float[] out = new float[4];
        tile.getPixel(0, 0, out, TILE_SIZE);
        // 索引0对应像素(0,0)，四个通道应为pixels[0..3]
        assertEquals(pixels[0], out[0], 0.001f);
        assertEquals(pixels[1], out[1], 0.001f);
        assertEquals(pixels[2], out[2], 0.001f);
        assertEquals(pixels[3], out[3], 0.001f);
    }

    @Test
    void setPixelShouldModifyPixel() {
        tile.setPixel(1, 1, 0.1f, 0.2f, 0.3f, 0.4f, TILE_SIZE);
        float[] out = new float[4];
        tile.getPixel(1, 1, out, TILE_SIZE);
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f, 0.4f}, out, 0.001f);
    }

    @Test
    void getPixelsSnapshotShouldReturnOriginalReference() {
        float[] snap = tile.getPixelsSnapshot();
        assertSame(pixels, snap);
    }

    @Test
    void cowShouldActivateWhenRefCountGreaterThanOne() {
        // 模拟引用计数 > 1：通过 acquire 增加
        data.acquire(); // 现在 refCount = 2
        // 写入应触发 COW
        tile.setPixel(0, 0, 0.9f, 0.8f, 0.7f, 0.6f, TILE_SIZE);
        float[] newSnap = tile.getPixelsSnapshot();
        assertNotSame(pixels, newSnap, "COW should create a new array");
        // 原始数组内容不变
        assertEquals(0.0f, pixels[0], 0.001f); // 初始值 0/100.0 = 0.0
        // 新数组已修改
        assertEquals(0.9f, newSnap[0], 0.001f);
    }
}