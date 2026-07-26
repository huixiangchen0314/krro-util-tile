package top.kzre.krro.util.tile;

/**
 * 抗锯齿读取接口
 */

public abstract class AntiAlias {

    /**
     * 覆盖谓词，用于亚像素抗锯齿精度填充判断.
     */
    @FunctionalInterface
    public interface CoveragePredicate {
        boolean test(int x, int y, Canvas canvas);
    }

    public static CoveragePredicate alwaysTrue() {
        return AlwaysTrue.INSTANCE;
    }

    public abstract void read(float[] dst, Canvas canvas, double x, double y, float[] color, CoveragePredicate predicate);

    public void read(float[] dst, Canvas canvas, double x, double y, float[] color){
        read(dst, canvas, x, y, color, alwaysTrue());
    }

    public float[] read(double x, double y, Canvas canvas, float[] color){
        float[] dst = new float[canvas.getChannels()];
        read(dst, canvas, x, y, color);
        return dst;
    }


    public static AntiAlias noAntiAlias(){
        return NoAntiAlias.INSTANCE;
    }

    public static AntiAlias ssaa(int scale){
        return new SSAA(scale);
    }

    private static final class SSAA2x2Holder {
        static final AntiAlias SSAA2x2 = new SSAA(2);
    }

    public static AntiAlias ssaa2x2(){
        return SSAA2x2Holder.SSAA2x2;
    }

    private static final class AlwaysTrue implements CoveragePredicate {
        static final AlwaysTrue INSTANCE = new AlwaysTrue();
        public boolean test(int x, int y, Canvas canvas) { return true; }
    }
}
