package top.kzre.krro.util.tile;


final class RandomAccessIteratorImpl implements RandomAccessIterator {
    private final TiledCanvas canvas;
    private final boolean writable;
    private int curX, curY;
    private boolean hasCurrent;

    RandomAccessIteratorImpl(TiledCanvas canvas, boolean writable) {
        this.canvas = canvas;
        this.writable = writable;
        this.hasCurrent = false;
    }

    @Override
    public void moveTo(int x, int y) {
        this.curX = x;
        this.curY = y;
        hasCurrent = true;
    }

    @Override
    public void getPixel(float[] out) {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        canvas.getPixel(curX, curY, out);
    }

    @Override
    public void setPixel(float r, float g, float b, float a) {
        if (!writable) throw new UnsupportedOperationException("Read-only iterator");
        if (!hasCurrent) throw new IllegalStateException("No current position");
        // 通过画布直接写入，确保使用最新的瓦片数据（自动处理 COW 和缺失瓦片）
        canvas.setPixel(curX, curY, r, g, b, a);
    }

    @Override
    public int x() {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        return curX;
    }

    @Override
    public int y() {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        return curY;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }
}