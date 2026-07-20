package top.kzre.krro.util.tile;

final class CanvasViewImpl implements CanvasView {
    private final TiledCanvas canvas;
    private final int x, y, w, h;          // 世界像素坐标

    CanvasViewImpl(TiledCanvas canvas, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Width and height must be positive");
        this.canvas = canvas;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public int x() { return x; }
    @Override
    public int y() { return y; }
    @Override
    public int width() { return w; }
    @Override
    public int height() { return h; }

    @Override
    public SequentialIterator sequentialIterator(boolean writable) {
        return canvas.createSequentialIterator(x, y, w, h, writable, ScanOrder.ROW_MAJOR);
    }

    @Override
    public SequentialIterator sequentialIterator(boolean writable, ScanOrder order) {
        return canvas.createSequentialIterator(x, y, w, h, writable, order);
    }

    @Override
    public RandomAccessIterator randomAccessIterator(boolean writable) {
        return canvas.createRandomAccessIterator(writable);
    }

    @Override
    public void readBytes(float[] dest, int destOffset, int rowStride) {
        canvas.readBytes(dest, destOffset, x, y, w, h, rowStride);
    }

    @Override
    public void writeBytes(float[] src, int srcOffset, int rowStride) {
        canvas.writeBytes(src, srcOffset, x, y, w, h, rowStride);
    }

    @Override
    public void fill(float[] color) {
        canvas.fillRect(x, y, w, h, color);
    }

    @Override
    public CanvasView subView(int subX, int subY, int subW, int subH) {
        if (subW <= 0 || subH <= 0) throw new IllegalArgumentException("sub width/height must be positive");
        int worldX = x + subX;
        int worldY = y + subY;
        // 裁剪到当前视图范围
        int maxX = x + w;
        int maxY = y + h;
        if (worldX < x) worldX = x;
        if (worldY < y) worldY = y;
        if (worldX + subW > maxX) subW = maxX - worldX;
        if (worldY + subH > maxY) subH = maxY - worldY;
        if (subW <= 0 || subH <= 0)
            throw new IllegalArgumentException("Sub-view out of bounds or zero size");
        return new CanvasViewImpl(canvas, worldX, worldY, subW, subH);
    }

    @Override
    public void copyFrom(CanvasView src) {
        if (src.width() != this.w || src.height() != this.h)
            throw new IllegalArgumentException("Source view dimensions must match target");
        // 使用 readBytes + writeBytes 或直接使用画布的 copyFrom 但需考虑偏移
        // 更简单：通过读/写缓冲区实现
        int totalPixels = w * h;
        float[] buffer = new float[totalPixels * 4];
        src.readBytes(buffer, 0, w);
        this.writeBytes(buffer, 0, w);
    }

    @Override
    public void copyFrom(CanvasView src, int srcX, int srcY) {
        // 从源视图的 (srcX, srcY) 开始复制到本视图的 (0,0)，保持相同大小
        if (srcX < 0 || srcY < 0) throw new IllegalArgumentException("srcX/srcY must be non-negative");
        if (srcX + w > src.width() || srcY + h > src.height())
            throw new IllegalArgumentException("Source region exceeds source view bounds");
        // 实现：可先读取源区域到临时数组，再写入本视图
        int totalPixels = w * h;
        float[] buffer = new float[totalPixels * 4];
        src.readBytes(buffer, 0, w); // 但 readBytes 从源视图的 (0,0) 开始？需要调整
        // 我们可以使用视图的子视图来读取指定区域
        CanvasView srcSub = src.subView(srcX, srcY, w, h);
        srcSub.readBytes(buffer, 0, w);
        this.writeBytes(buffer, 0, w);
    }
}