package top.kzre.krro.util.tile;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractTileData implements TileData, VersionedTile{
    private final AtomicInteger refCount;
    public AbstractTileData() {
        this.refCount = new AtomicInteger(1);
    }

    @Override
    public int acquire() {
        return refCount.incrementAndGet();
    }


    @Override
    public int release() {
        int remaining = refCount.decrementAndGet();
        if(remaining < 0){
            throw new IllegalStateException("Tile was already released!");
        }
        if (remaining == 0) {
            onRelease();
        }
        return remaining;
    }

    @Override
    public int refCount() {
        return refCount.get();
    }

    @Override
    public boolean valid(){
        return refCount.get() > 0;
    }

    @Override
    public int acquireIfValid() {
        int current;
        do {
            current = refCount.get();
            if (current <= 0) {
                return 0;   // 已释放，无效
            }
        } while (!refCount.compareAndSet(current, current + 1));
        return current + 1;
    }

    protected abstract void onRelease();
}
