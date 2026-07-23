package top.kzre.krro.util.tile;

import java.util.Map;
import java.util.function.Consumer;

public interface Canvas {
    int getTileSize();
    int getChannels();
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

    @Deprecated
    void setPixel(int x, int y, float r, float g, float b, float a);

    void setPixel(int x, int y, float[] pixel);

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


    /**
     * 读取 RGBA 像素（4 通道）。
     * 若画布并非 4 通道，会进行合理转换（如灰度扩展、RGB 补 Alpha）。
     */
    default float[] getRGBA(int x, int y) {
        float[] rgba = new float[4];
        getPixel(x, y, rgba);
        return rgba;
    }

    /** 读取像素到已有 RGBA 数组（仅适用于 4 通道画布）。 */
    default void getRGBA(int x, int y, float[] out) {
        getPixel(x, y, out);
    }


    /** 设置 RGBA 像素（仅适用于 4 通道画布）。 */
    default void setRGBA(int x, int y, float r, float g, float b, float a) {
        setPixel(x, y, new float[]{r, g, b, a});
    }

    /** 获取灰度值（仅适用于 1 通道画布）。 */
    default float getGray(int x, int y) {
        float[] gray = new float[1];
        getPixel(x, y, gray);
        return gray[0];
    }

    /** 设置灰度值（仅适用于 1 通道画布）。 */
    default void setGray(int x, int y, float gray) {
        setPixel(x, y, new float[]{gray});
    }
}
