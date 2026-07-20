package top.kzre.krro.util.tile;

/**
 * 画布视图，代表一个矩形子区域，提供独立的迭代器和批量操作。
 * 视图不可变，线程安全。
 */
public interface CanvasView {

    int x();
    int y();
    int width();
    int height();

    /**
     * 创建顺序迭代器（默认行优先）。
     * @param writable 是否可写
     */
    SequentialIterator sequentialIterator(boolean writable);

    /**
     * 创建指定扫描顺序的顺序迭代器。
     */
    SequentialIterator sequentialIterator(boolean writable, ScanOrder order);

    /**
     * 创建随机访问迭代器。
     */
    RandomAccessIterator randomAccessIterator(boolean writable);

    /**
     * 将视图区域的数据读入外部缓冲区（相对于视图坐标）。
     * @param dest 目标数组
     * @param destOffset 起始偏移（像素数）
     * @param rowStride 目标行步长（像素数，≤0 表示使用 width）
     */
    void readBytes(float[] dest, int destOffset, int rowStride);

    /**
     * 将外部数据写入视图区域。
     */
    void writeBytes(float[] src, int srcOffset, int rowStride);

    /**
     * 用指定颜色填充视图区域。
     */
    void fill(float[] color);

    /**
     * 创建子视图（相对于当前视图坐标）。
     * @param x 子视图左上角 X（相对）
     * @param y 子视图左上角 Y（相对）
     * @param w 宽度
     * @param h 高度
     */
    CanvasView subView(int x, int y, int w, int h);

    /**
     * 从另一个视图复制数据（覆盖本视图区域）。
     * 两个视图的尺寸必须相同，或源视图区域完全覆盖目标。
     * @param src 源视图
     */
    void copyFrom(CanvasView src);

    /**
     * 从另一个视图的指定位置复制数据到本视图的当前位置（保持尺寸一致）。
     */
    void copyFrom(CanvasView src, int srcX, int srcY);
}