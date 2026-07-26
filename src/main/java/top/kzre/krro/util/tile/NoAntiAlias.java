package top.kzre.krro.util.tile;

 final class NoAntiAlias extends AntiAlias {
    public static AntiAlias INSTANCE = new NoAntiAlias();
    private NoAntiAlias() {}

    @Override
    public void read(float[] dst, Canvas canvas, double x, double y, float[] color, CoveragePredicate predicate) {
        int channels = canvas.getChannels();
        if (channels >= 0) System.arraycopy(color, 0, dst, 0, channels);
    }
}
