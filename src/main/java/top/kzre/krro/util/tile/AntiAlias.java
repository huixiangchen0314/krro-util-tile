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

    public abstract void write(Canvas canvas, double x, double y, float[] color, CoveragePredicate predicate);

    public void write(Canvas canvas, double x, double y, float[] color){
        write(canvas, x, y, color, alwaysTrue());
    }


    public static AntiAlias noAntiAlias(){
        return NoAntiAlias.INSTANCE;
    }

    public static AntiAlias ssaa(int scale){
        return new SSAA(scale);
    }

    public static AntiAlias ssaa2x2(){
        return new SSAA(2);
    }

    private static final class AlwaysTrue implements CoveragePredicate {
        static final AlwaysTrue INSTANCE = new AlwaysTrue();
        public boolean test(int x, int y, Canvas canvas) { return true; }
    }
}
