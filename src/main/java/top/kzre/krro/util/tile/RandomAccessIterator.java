package top.kzre.krro.util.tile;

/**
 * 随机访问迭代器，支持跳转到任意像素位置进行读写。
 * 轻量级，不持有需要显式释放的资源。
 */
public interface RandomAccessIterator {

    /**
     * 移动到指定的世界像素坐标。
     * @param x 世界像素 X 坐标
     * @param y 世界像素 Y 坐标
     */
    void moveTo(int x, int y);

    /**
     * 读取当前像素的 RGBA 值。
     * @param out 输出数组（长度至少 4）
     * @throws IllegalStateException 如果尚未调用 moveTo 或当前位置无效
     */
    void getPixel(float[] out);

    /**
     * 写入当前像素的 RGBA 值（仅当迭代器为可写模式）。
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     * @param a Alpha 分量
     * @throws UnsupportedOperationException 如果迭代器为只读模式
     * @throws IllegalStateException 如果尚未调用 moveTo 或当前位置无效
     */
    void setPixel(float r, float g, float b, float a);

    /**
     * 获取当前像素的 X 坐标。
     */
    int x();

    /**
     * 获取当前像素的 Y 坐标。
     */
    int y();

    /**
     * 检查迭代器是否可写。
     */
    boolean isWritable();
}