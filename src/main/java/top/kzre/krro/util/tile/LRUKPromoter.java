package top.kzre.krro.util.tile;

import lombok.Getter;
import top.kzre.krro.util.tile.util.WeakReferenceQueue;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * LRU-K 提升器。
 * <p>
 * 本层不做容量淘汰，只负责统计访问次数：当某个 Tile 被访问 K 次后，
 * 通过 {@code promoter} 提升数据并交给 {@code high} 层。
 * 提升动作统一在 {@link #evict()} 中批量执行，避免占用外部调用路径。
 * 容量控制由 {@code high}（或调用方）负责。
 * <p>
 * 线程约束：
 * <ul>
 *   <li>{@code add} / {@code evict} 只在 Monitor 线程执行；</li>
 *   <li>{@code TileDataWrapper.acquire*} 可能被任意线程调用，
 *       因此 {@code hits} 使用 {@link AtomicInteger} 累加；</li>
 *   <li>{@code promoted} 只由 Monitor 线程写、任意线程读，
 *       用 {@code volatile} 保证可见性即可，无需 CAS。</li>
 * </ul>
 */
public final class LRUKPromoter implements TileEvictor {

    private final TileEvictor high;
    private final Function<TileData, TileData> promoter;
    private final int k;

    @Getter
    private final WeakReferenceQueue<Tile> tiles = new WeakReferenceQueue<>();

    public LRUKPromoter(TileEvictor high, Function<TileData, TileData> promoter, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.high = high;
        this.promoter = promoter;
        this.k = k;
    }

    @Override
    public void add(Tile tile) {
        TileData data = tile.getDataRef();
        if (!(data instanceof TileDataWrapper)) {
            tile.replaceData(new TileDataWrapper(data));
        }
        tiles.add(tile);
    }

    @Override
    public void evict() {
        tiles.cleanup();

        // 1. 收集本轮需要提升的 tile（Monitor 线程内，无并发写 tiles）
        List<Tile> toPromote = null;
        Iterator<Tile> it = tiles.iterator();
        while (it.hasNext()) {
            Tile tile = it.next();
            TileData data = tile.getDataRef();
            if (data instanceof TileDataWrapper) {
                TileDataWrapper wrapper = (TileDataWrapper) data;
                if (!wrapper.promoted && wrapper.hits.get() >= k) {
                    if (toPromote == null) {
                        toPromote = new ArrayList<Tile>();
                    }
                    toPromote.add(tile);
                }
            }
        }
        if (toPromote == null) {
            return;
        }

        // 2. 批量提升（只有 Monitor 线程会走到这里写 promoted）
        for (Tile tile : toPromote) {
            TileData data = tile.getDataRef();
            if (!(data instanceof TileDataWrapper)) {
                continue;
            }
            TileDataWrapper wrapper = (TileDataWrapper) data;
            if (wrapper.promoted || wrapper.hits.get() < k) {
                continue;
            }
            if (tile.getDataRef() != wrapper) {
                wrapper.promoted = true;
                continue;
            }

            wrapper.promoted = true;
            tiles.remove(tile);

            TileData lifted = promoter.apply(wrapper.delegate);
            tile.replaceData(lifted);
            high.add(tile);
        }
    }

    /**
     * 统计包装。
     * <p>
     * {@code hits} 可能被任意线程自增，使用 {@link AtomicInteger}；
     * {@code promoted} 只被 Monitor 线程写入，其他线程读，用 {@code volatile} 保证可见性。
     */
    private final class TileDataWrapper implements TileData {

        private final TileData delegate;
        private final AtomicInteger hits = new AtomicInteger(0);
        private volatile boolean promoted;

        private TileDataWrapper(TileData delegate) {
            this.delegate = delegate;
        }

        private void recordHit() {
            // best-effort 检查：读到过期的 false 会多计数，但不影响提升语义
            if (!promoted) {
                hits.incrementAndGet();
            }
        }

        @Override
        public float[] getPixels() {
            return delegate.getPixels();
        }

        @Override
        public FloatBuffer floatBuffer() {
            return delegate.floatBuffer();
        }

        @Override
        public int acquire() {
            int rc = delegate.acquire();
            recordHit();
            return rc;
        }

        @Override
        public int release() {
            return delegate.release();
        }

        @Override
        public int refCount() {
            return delegate.refCount();
        }

        @Override
        public boolean valid() {
            return delegate.valid();
        }

        @Override
        public int acquireIfValid() {
            int rc = delegate.acquireIfValid();
            if (rc > 0) {
                recordHit();
            }
            return rc;
        }

        @Override
        public int getByteSize() {
            return delegate.getByteSize();
        }

        @Override
        public TileData copy() {
            return delegate.copy();
        }
    }
}