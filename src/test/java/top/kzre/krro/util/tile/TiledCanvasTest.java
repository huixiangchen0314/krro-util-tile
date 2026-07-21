package top.kzre.krro.util.tile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TiledCanvasTest {
    private TiledCanvas canvas;
    private static final int TILE_SIZE = 64;
    private static final float[] DEFAULT_PIXEL = {0f, 0f, 0f, 0f};

    @BeforeEach
    void setUp() {
        canvas = new TiledCanvas(TILE_SIZE, DEFAULT_PIXEL);
    }

    // ── 坐标映射测试 ───────────────────────────────
    @Test
    void packUnpackShouldBeInverse() {
        int tx = -5, ty = 10;
        long packed = TiledCanvas.pack(tx, ty);
        assertEquals(tx, TiledCanvas.unpackTx(packed));
        assertEquals(ty, TiledCanvas.unpackTy(packed));
    }

    @Test
    void tileXAndLocalXShouldReconstructWorldX() {
        int worldX = 130;
        int tx = TiledCanvas.tileX(worldX, TILE_SIZE);
        int lx = TiledCanvas.localX(worldX, TILE_SIZE);
        assertEquals(worldX, tx * TILE_SIZE + lx);
        assertTrue(lx >= 0 && lx < TILE_SIZE);
    }

    @Test
    void negativeWorldXShouldMapCorrectly() {
        int worldX = -50;
        int tx = TiledCanvas.tileX(worldX, TILE_SIZE);
        int lx = TiledCanvas.localX(worldX, TILE_SIZE);
        // floorDiv(-50,64) = -1, floorMod(-50,64) = 14  (因为 -1*64 + 14 = -50)
        assertEquals(-1, tx);
        assertEquals(14, lx);
        assertEquals(worldX, tx * TILE_SIZE + lx);
    }

    @Test
    void tileYAndLocalYShouldWorkForNegative() {
        int worldY = -10;
        int ty = TiledCanvas.tileY(worldY, TILE_SIZE);
        int ly = TiledCanvas.localY(worldY, TILE_SIZE);
        // floorDiv(-10,64) = -1, floorMod(-10,64) = 54
        assertEquals(-1, ty);
        assertEquals(54, ly);
        assertEquals(worldY, ty * TILE_SIZE + ly);
    }

    // ── 像素读写与瓦片创建 ─────────────────────────
    @Test
    void setAndGetSinglePixelShouldBeConsistent() {
        canvas.setPixel(100, 200, 0.2f, 0.4f, 0.6f, 0.8f);
        float[] out = new float[4];
        canvas.getPixel(100, 200, out);
        assertArrayEquals(new float[]{0.2f, 0.4f, 0.6f, 0.8f}, out, 0.001f);
    }

    @Test
    void singlePixelShouldCreateOnlyOneTile() {
        canvas.setPixel(70, 80, 1, 1, 1, 1);
        assertEquals(1, canvas.tileCount());
        long expectedKey = TiledCanvas.pack(
                TiledCanvas.tileX(70, TILE_SIZE),
                TiledCanvas.tileY(80, TILE_SIZE));
        // 通过 readTiles 验证键存在
        List<Long> keys = new ArrayList<>();
        canvas.readTiles(m -> keys.addAll(m.keySet()));
        assertEquals(1, keys.size());
        assertEquals(expectedKey, (long) keys.get(0));
    }

    @Test
    void lineWithinOneTileShouldNotCreateMultipleTiles() {
        // 在 (10,10) 到 (20,10) 水平线，全部在同一个瓦片内 (tileX 0, tileY 0)
        for (int x = 10; x <= 20; x++) {
            canvas.setPixel(x, 10, 1, 1, 1, 1);
        }
        assertEquals(1, canvas.tileCount(), "Line confined to one tile should create exactly 1 tile");
    }

    @Test
    void diagonalLineShouldNotSplatterTilesIncorrectly() {
        // 从 (0,0) 到 (63,63) 对角线，仍在单个瓦片 (0,0) 内
        for (int i = 0; i <= 63; i++) {
            canvas.setPixel(i, i, 1, 1, 1, 1);
        }
        assertEquals(1, canvas.tileCount());
    }

    @Test
    void crossTileBoundaryShouldCreateMultipleTiles() {
        // 从 (0,0) 到 (64,0) 跨越两个瓦片 (tx=0 和 tx=1)
        for (int x = 0; x <= 64; x++) {
            canvas.setPixel(x, 0, 1, 1, 1, 1);
        }
        // 应有两个瓦片：(-1,0)? 不，x=0..64 覆盖两个瓦片：tx=0 和 tx=1
        assertEquals(2, canvas.tileCount());
        // 验证 tile (0,0) 和 (1,0) 存在
        assertNotNull(canvas.getTile(0, 0));
        assertNotNull(canvas.getTile(1, 0));
    }

    // ── 范围与边界测试 ────────────────────────────
    @Test
    void emptyCanvasBoundsShouldBeZero() {
        int[] bounds = new int[4];
        canvas.getBounds(bounds);
        assertEquals(0, bounds[0]);
        assertEquals(0, bounds[1]);
        assertEquals(0, bounds[2]);
        assertEquals(0, bounds[3]);
    }

    @Test
    void boundsShouldCoverPixelsInBothDirections() {
        canvas.setPixel(-100, -50, 1, 1, 1, 1);
        canvas.setPixel(200, 150, 1, 1, 1, 1);
        int[] bounds = new int[4];
        canvas.getBounds(bounds);
        // 瓦片坐标转换为世界像素范围
        int minTx = TiledCanvas.tileX(-100, TILE_SIZE);
        int maxTx = TiledCanvas.tileX(200, TILE_SIZE);
        int minTy = TiledCanvas.tileY(-50, TILE_SIZE);
        int maxTy = TiledCanvas.tileY(150, TILE_SIZE);
        assertEquals(minTx * TILE_SIZE, bounds[0]);
        assertEquals(minTy * TILE_SIZE, bounds[1]);
        assertEquals((maxTx + 1) * TILE_SIZE - 1, bounds[2]);
        assertEquals((maxTy + 1) * TILE_SIZE - 1, bounds[3]);
    }

    // ── 复制/共享/合并 ─────────────────────────────
    @Test
    void copyShouldNotShareData() {
        canvas.setPixel(10, 10, 1, 0, 0, 1);
        TiledCanvas copy = canvas.copy();
        copy.setPixel(10, 10, 0, 1, 0, 1);
        float[] orig = new float[4], copyPix = new float[4];
        canvas.getPixel(10, 10, orig);
        copy.getPixel(10, 10, copyPix);
        assertNotEquals(orig[0], copyPix[0]);
    }

    @Test
    void shareFromShouldUseReferenceCounting() {
        canvas.setPixel(50, 50, 0.5f, 0.5f, 0.5f, 1.0f);
        TiledCanvas shared = new TiledCanvas(TILE_SIZE);
        shared.shareFrom(canvas);
        // 修改共享画布不应影响原画布
        shared.setPixel(50, 50, 0.1f, 0.1f, 0.1f, 1.0f);
        float[] orig = new float[4];
        canvas.getPixel(50, 50, orig);
        assertEquals(0.5f, orig[0], 0.001f);
    }

    @Test
    void mergeTilesShouldReplaceSpecifiedTiles() {
        canvas.setPixel(30, 30, 1, 1, 1, 1);
        Map<Long, float[]> newTiles = new HashMap<>();
        long key = TiledCanvas.pack(TiledCanvas.tileX(30, TILE_SIZE), TiledCanvas.tileY(30, TILE_SIZE));
        float[] newData = new float[TILE_SIZE * TILE_SIZE * 4];
        Arrays.fill(newData, 0.2f);
        newTiles.put(key, newData);
        canvas.mergeTiles(newTiles);
        float[] out = new float[4];
        canvas.getPixel(30, 30, out);
        assertEquals(0.2f, out[0], 0.001f);
    }

    @Test
    void clearShouldWipeAllTiles() {
        canvas.setPixel(1, 1, 1, 1, 1, 1);
        canvas.clear();
        assertEquals(0, canvas.tileCount());
        float[] out = new float[4];
        canvas.getPixel(1, 1, out);
        assertArrayEquals(DEFAULT_PIXEL, out, 0.001f);
    }

    // ── 迭代器/视图测试（若需要可补充）───────────
    @Test
    void readTilesShouldExposeAllTiles() {
        canvas.setPixel(1, 2, 1, 1, 1, 1);
        canvas.setPixel(100, 200, 0, 0, 0, 1);
        Set<Long> keys = new HashSet<>();
        canvas.readTiles(m -> keys.addAll(m.keySet()));
        assertEquals(2, keys.size());
    }
}