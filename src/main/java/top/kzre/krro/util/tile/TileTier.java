package top.kzre.krro.util.tile;

import lombok.Getter;

import java.util.function.Function;


public final class TileTier {
    @Getter
    private final TileEvictor demoter;
    @Getter
    private final TileEvictor promoter;

    public TileTier(TileEvictor demoter, TileEvictor promoter) {
        this.demoter = demoter;
        this.promoter = promoter;
    }

    /**
     * 两层瓦片存储。
     *
     * <pre>
     *   add ──→ High: MaxSizeDemoter（容量上限）
     *                │ evict: 超出 maxByteSize → 降级最旧的
     *                ↓
     *              Low: LRUKPromoter（命中统计）
     *                │ evict: hits ≥ k → 提升到 High
     *                ↑
     *                └─── 回到 High
     * </pre>
     *
     * 线程约束：本类所有方法只在 Monitor 线程执行。

     * @param highMaxByteSize High 层的容量上限（字节）
     * @param demoter         High → Low 的数据转换（如：降采样 / 压缩）
     * @param promoter        Low → High 的数据转换（如：解码 / 解压）
     * @param k               Low 层提升阈值：命中 k 次即可提升
     */
    public static TileTier fifoLRUK(int highMaxByteSize,
                                    Function<TileData, TileData> demoter,
                                    Function<TileData, TileData> promoter,
                                    int k) {
        // High 先建，Low 用转发器延迟绑定
        Forwarder forwarder = new Forwarder();
        MaxSizeFIFODemoter  high = new MaxSizeFIFODemoter(highMaxByteSize, demoter, forwarder);
        LRUKPromoter low = new LRUKPromoter(high, promoter, k);
        forwarder.target = low;
        return new TileTier(high, low);
    }

    /**
     * 占位用的转发器，打破 High → Low 的循环构造依赖。
     * 只有构造期生效，之后所有调用都直达 low 实例。
     */
    private static final class Forwarder implements TileEvictor {
        TileEvictor target;

        @Override
        public void add(Tile tile) {
            target.add(tile);
        }

        @Override
        public void evict() {
            target.evict();
        }
    }
}