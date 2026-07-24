package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;

final class SSAA extends AntiAlias {
    private final int scale;

    SSAA(int scale) {
        this.scale = scale;
    }

    @Override
    public void write(Canvas canvas, double x, double y, float[] color, CoveragePredicate predicate) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] original = pool.acquire();
        float[] blended = pool.acquire();
        try {
            canvas.getPixel(ix, iy, original);
            float coverage = computeCoverage(x, y, canvas, predicate);
            for (int c = 0; c < channels; c++) {
                blended[c] = original[c] + (color[c] - original[c]) * coverage;
            }
            canvas.setPixel(ix, iy, blended);
        } finally {
            pool.release(blended);
            pool.release(original);
        }
    }

    private float computeCoverage(double x, double y, Canvas canvas,
                                  CoveragePredicate predicate) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double step = 1.0 / scale;
        double start = step / 2.0;
        int inside = 0;
        int total = scale * scale;
        for (int sy = 0; sy < scale; sy++) {
            double sampleY = iy + start + sy * step;
            for (int sx = 0; sx < scale; sx++) {
                double sampleX = ix + start + sx * step;
                if (predicate.test((int) sampleX, (int) sampleY, canvas)) {
                    inside++;
                }
            }
        }
        return (float) inside / total;
    }
}
