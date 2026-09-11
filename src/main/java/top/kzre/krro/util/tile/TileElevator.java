package top.kzre.krro.util.tile;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 提升器：持有复数个层次，由 Monitor 线程统一驱动。
 *
 * <pre>
 *   外部线程:
 *     add(tile) ──→ pending 队列（不触碰 tiles 结构）
 *
 *   Monitor 线程:
 *     evict():
 *       1. drainPending()       // 把队列里的 tile 送进最顶层
 *       2. 对每层:
 *            demoter.evict()   // 先降级腾容量
 *            promoter.evict()  // 再提升
 * </pre>
 *
 * 线程约束：
 * <ul>
 *   <li>{@code add} 任意线程可调，仅入队；</li>
 *   <li>{@code evict} 只在 Monitor 线程执行，独占访问所有 tier 的结构；</li>
 *   <li>因此内部无需同步，{@code tiers} / 各层队列都不加锁。</li>
 * </ul>
 */
public final class TileElevator implements TileEvictor {

    /** 单轮最多处理的投递数量，避免一次 evict 被大量 add 拖长。 */
    private static final int DEFAULT_DRAIN_LIMIT = 256;

    @Getter
    private final List<TileTier> tiers;

    private final Queue<Tile> pending = new ConcurrentLinkedQueue<>();
    private final int drainLimit;

    public TileElevator(List<TileTier> tiers) {
        this(tiers, DEFAULT_DRAIN_LIMIT);
    }

    public TileElevator(List<TileTier> tiers, int drainLimit) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("tiers must not be empty");
        }
        if (drainLimit <= 0) {
            throw new IllegalArgumentException("drainLimit must be > 0");
        }
        this.tiers = Collections.unmodifiableList(new ArrayList<>(tiers));
        this.drainLimit = drainLimit;
    }

    public TileElevator(TileTier... tiers) {
        this(Arrays.asList(tiers));
    }

    /**
     * 任意线程调用，仅入队。
     * tile 会在 Monitor 线程下一次 {@link #evict()} 时被真正加入缓存。
     */
    @Override
    public void add(Tile tile) {
        pending.offer(tile);
    }

    /**
     * 只在 Monitor 线程调用。
     */
    @Override
    public void evict() {
        drainPending();

        for (TileTier tier : tiers) {
            tier.getDemoter().evict();
            tier.getPromoter().evict();
        }
    }

    /** 队列当前积压数量，便于监控 / 限流。 */
    public int pendingSize() {
        return pending.size();
    }

    private void drainPending() {
        TileEvictor top = tiers.get(0).getDemoter();
        for (int i = 0; i < drainLimit; i++) {
            Tile tile = pending.poll();
            if (tile == null) {
                return;
            }
            // 投递到 drain 之间可能已被释放，跳过失效的瓦片
            // 不强制投递固定次数，失效具有局部性，下次检查剩下的可能就也失效了
            if (tile.getDataRef().valid()){
                top.add(tile);
            }
        }
    }
}