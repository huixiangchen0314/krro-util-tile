package top.kzre.krro.util.tile;

import top.kzre.krro.util.tile.util.WeakReferenceQueue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * 瓦片数据最大容量降级器
 */
public final class MaxSizeFIFODemoter implements TileEvictor {

    /**
     * 该级别缓存最大的容量
     */
    private final int maxByteSize;

    /**
     * 降级函数
     */
    private final Function<TileData, TileData> demoter;

    private final WeakReferenceQueue<Tile> tiles = new WeakReferenceQueue<>();

    /**
     * 被降级后的容器
     */
    private final TileEvictor low;

    public MaxSizeFIFODemoter(int maxByteSize, Function<TileData, TileData> demoter, TileEvictor low) {
        this.maxByteSize = maxByteSize;
        this.demoter = demoter;
        this.low = low;
    }

    @Override
    public synchronized void add(Tile tile){
        tiles.add(tile);
    }

    /**
     * FIFO 淘汰：淘汰最早加入的瓦片，直到总字节数 <= maxByteSize
     */
    @Override
    public synchronized void evict() {
        // 先清理已经被 GC 的弱引用
        tiles.cleanup();

        // 收集当前所有有效瓦片，并统计总字节大小
        List<Tile> wrappers = new ArrayList<>();
        int totalBytes = 0;

        Iterator<Tile> iterator = tiles.iterator();
        while (iterator.hasNext()) {
            Tile wrapper = iterator.next();
            TileData data = wrapper.getDataRef();
            if (data != null) {
                totalBytes += data.getByteSize(); // 假设 TileData 有 byteSize()
                wrappers.add(wrapper);
            }
        }
        for (int i = wrappers.size() - 1; i >= 0 && totalBytes > maxByteSize; i--) {
            Tile tile = wrappers.get(i);
            tiles.remove(tile);
            TileData data = tile.getDataRef();
            totalBytes -= data.getByteSize();
            if (data.refCount() > 0){
                TileData demoted = demoter.apply(data);
                tile.replaceData(demoted);
                low.add(tile);
            }

        }
    }


}
