package top.kzre.krro.util.tile;

import java.util.ArrayList;
import java.util.List;
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

    /** 获取指定像素的 Alpha 值（不透明程度）。 */
    default float getOpacity(int x, int y) {
        int c = getChannels();
        // 若画布包含 Alpha 通道（最后一个通道），直接读取
        if (c >= 2) {
            float[] pixel = new float[c];
            getPixel(x, y, pixel);
            return pixel[c - 1];
        }
        // 没有 Alpha 通道则视为完全不透明
        return 1.0f;
    }

    /** 设置指定像素的 Alpha 值，其他颜色通道保持不变。 */
    default void setOpacity(int x, int y, float alpha) {
        int c = getChannels();
        if (c >= 2) {
            float[] pixel = new float[c];
            getPixel(x, y, pixel);          // 先读取原有颜色
            pixel[c - 1] = alpha;           // 仅替换 Alpha
            setPixel(x, y, pixel);
        }
        // 若画布无法表示 Alpha，则静默忽略
    }


    /**
     * 创建一个子画布视图，限制到指定的像素矩形区域。
     * 所有读写操作将被限制在该区域内，区域外的操作会被忽略或返回默认像素。
     * 该视图与底层画布共享同一份像素存储，修改会直接反映到原画布。
     *
     * @param x 左上角像素坐标（世界坐标）
     * @param y 左上角像素坐标
     * @param w 宽度（像素）
     * @param h 高度（像素）
     * @return 一个实现 Canvas 接口的视图，其坐标相对于原画布
     */
    Canvas subCanvas(int x, int y, int w, int h);


    /**
     * 按瓦片跨度切分画布为子视图（抽象方法，由各实现类提供高效实现）。
     * @param tileSpan 每个子视图包含的瓦片数量（沿 X 和 Y 方向）
     * @return 子视图列表
     */
    List<Canvas> split(int tileSpan);

    /**
     * 按单个瓦片切分画布（每个子视图恰好为一个瓦片）。
     * 等价于 {@code splitByTiles(1)}。
     */
    List<Canvas> split();
}
