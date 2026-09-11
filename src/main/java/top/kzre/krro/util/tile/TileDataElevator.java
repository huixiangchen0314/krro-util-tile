package top.kzre.krro.util.tile;

import java.lang.ref.WeakReference;
import java.util.function.Function;

/**
 * 瓦片数据提升器
 */
public class TileDataElevator {
    /**
     * 该级别缓存最大的容量
     */
    private final int maxByteSize;

    /**
     * 提升函数
     */
    private final Function<TileData, TileData> promoter;

    /**
     * 降级函数
     */
    private final Function<TileData, TileData> demoter;

    public TileDataElevator(int maxByteSize, Function<TileData, TileData> promoter, Function<TileData, TileData> demoter) {
        this.maxByteSize = maxByteSize;
        this.promoter = promoter;
        this.demoter = demoter;
    }

}
