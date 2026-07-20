package top.kzre.krro.util.tile;

/**
 * 顺序迭代器，用于遍历画布的矩形区域（按行或列顺序）。
 * 迭代器不持有任何需要显式释放的资源，使用完毕后由垃圾回收器自动处理。
 */
public interface SequentialIterator {

    /**
     * 移动到下一个像素。
     * @return {@code true} 如果成功移动，{@code false} 如果已到达区域末尾。
     */
    boolean next();

    /**
     * 读取当前像素的 RGBA 值到输出数组。
     * @param out 输出数组（长度至少 4）
     * @throws IllegalStateException 如果迭代器已到达末尾或未正确初始化
     */
    void getPixel(float[] out);

    /**
     * 写入当前像素的 RGBA 值（仅当迭代器为可写模式）。
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     * @param a Alpha 分量
     * @throws UnsupportedOperationException 如果迭代器为只读模式
     * @throws IllegalStateException 如果迭代器已到达末尾或未正确初始化
     */
    void setPixel(float r, float g, float b, float a);

    /**
     * 获取当前像素的 X 坐标（相对于画布原点）。
     * @throws IllegalStateException 如果迭代器已到达末尾
     */
    int x();

    /**
     * 获取当前像素的 Y 坐标（相对于画布原点）。
     * @throws IllegalStateException 如果迭代器已到达末尾
     */
    int y();

    /**
     * 重置迭代器到起始位置（相当于重新开始遍历）。
     */
    void reset();
}