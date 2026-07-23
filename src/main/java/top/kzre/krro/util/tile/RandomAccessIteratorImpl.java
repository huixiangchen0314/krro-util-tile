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

    // 通用数组版写像素
    @Override
    public void setPixel(float[] pixel) {
        if (!writable) throw new UnsupportedOperationException("Read-only iterator");
        if (!hasCurrent) throw new IllegalStateException("No current position");
        canvas.setPixel(curX, curY, pixel);   // TiledCanvas 已支持 setPixel(x, y, float[])
    }

    // 四参数版本委托给数组版（标记为废弃以推荐使用数组版）
    @Override
    @Deprecated
    public void setPixel(float r, float g, float b, float a) {
        setPixel(new float[]{r, g, b, a});
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