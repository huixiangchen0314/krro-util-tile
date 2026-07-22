package top.kzre.krro.util.tile;

import java.util.Map;
import java.util.function.Consumer;

public interface Canvas {
    int getTileSize();
    int getMinTileX();
    int getMaxTileX();
    int getMinTileY();
    int getMaxTileY();
    float[] getDefaultPixel();
    Tile getTile(int tx, int ty);

    void forEachTile(TileVisitor visitor);

    int tileCount();

    // ---------- 公开的像素读写 ----------
    void getPixel(int x, int y, float[] out);

    void setPixel(int x, int y, float r, float g, float b, float a);

    // ---------- 批量读写 ----------
    void readBytes(float[] dest, int destOffset, int x, int y, int w, int h, int destRowStride);

    void writeBytes(float[] src, int srcOffset, int x, int y, int w, int h, int srcRowStride);

    // ---------- 填充 ----------
    void fillRect(int x, int y, int w, int h, float[] color);

    // ---------- 清空 ----------
    void clear();

    void getBounds(int[] out);

    SequentialIterator createSequentialIterator(int x, int y, int w, int h,
                                                boolean writable,
                                                ScanOrder order);

    SequentialIterator createSequentialIterator(int x, int y, int w, int h,
                                                boolean writable);

    RandomAccessIterator createRandomAccessIterator(boolean writable);

    void readTiles(Consumer<Map<Long, float[]>> consumer);

    void writeTiles(Map<Long, float[]> newTiles);
}
