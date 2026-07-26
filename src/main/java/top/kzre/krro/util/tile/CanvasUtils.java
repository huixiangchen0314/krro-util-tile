package top.kzre.krro.util.tile;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;

public final class CanvasUtils {
    private CanvasUtils() {}

    /**
     * 对 Canvas 进行双线性插值采样，结果存入 out。
     * 临时数组可复用，避免重复分配。
     */
    public static void bilinearSample(Canvas src, float x, float y, float[] out,
                                      float[] s00, float[] s10, float[] s01, float[] s11) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float fx = x - x0;
        float fy = y - y0;

        src.getPixel(x0, y0, s00);
        src.getPixel(x1, y0, s10);
        src.getPixel(x0, y1, s01);
        src.getPixel(x1, y1, s11);

        int c = src.getChannels();
        for (int i = 0; i < c; i++) {
            float top = s00[i] + (s10[i] - s00[i]) * fx;
            float bot = s01[i] + (s11[i] - s01[i]) * fx;
            out[i] = top + (bot - top) * fy;
        }
    }

    public static void bilinearSample(Canvas src, float x, float y,float[] out) {
        int channels = src.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] sample00 = pool.acquire();
        float[] sample10 = pool.acquire();
        float[] sample01 = pool.acquire();
        float[] sample11 = pool.acquire();
        try{
            bilinearSample(src, x, y, out, sample00, sample10, sample01, sample11);
        }finally {
            pool.release(sample00);
            pool.release(sample10);
            pool.release(sample01);
            pool.release(sample11);
        }
    }
}
