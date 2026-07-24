package top.kzre.krro.util.tile;

 final class NoAntiAlias extends AntiAlias {
    public static AntiAlias INSTANCE = new NoAntiAlias();
    private NoAntiAlias() {}

    @Override
    public void write(Canvas canvas, double x, double y, float[] color, CoveragePredicate predicate) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        canvas.setPixel(ix, iy, color);
    }
}
