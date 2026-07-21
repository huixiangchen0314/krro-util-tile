package top.kzre.krro.util.tile;


final class RandomAccessIteratorImpl implements RandomAccessIterator {
    private final TiledCanvas canvas;
    private final boolean writable;
    private final CacheEntry[] cache;
    private int cursor;

    private int curX, curY;
    private Tile currentTile;
    private float[] currentData;
    private int offset;
    private boolean hasCurrent;

    private static class CacheEntry {
        long key;
        Tile tile;
        int tx, ty;
    }

    RandomAccessIteratorImpl(TiledCanvas canvas, boolean writable) {
        this.canvas = canvas;
        this.writable = writable;
        this.cache = new CacheEntry[4];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = new CacheEntry();
        }
        this.cursor = 0;
        this.hasCurrent = false;
    }

    private Tile getTileFor(int tx, int ty) {
        long key = TiledCanvas.pack(tx, ty);
        // 查找缓存
        for (CacheEntry entry : cache) {
            if (entry.key == key && entry.tile != null) {
                return entry.tile;
            }
        }
        // 未命中：从画布获取
        Tile tile = writable ? canvas.ensureTile(tx, ty) : canvas.getTile(tx, ty);
        // 更新缓存（循环替换）
        CacheEntry evicted = cache[cursor];
        evicted.key = key;
        evicted.tile = tile;
        evicted.tx = tx;
        evicted.ty = ty;
        cursor = (cursor + 1) % cache.length;
        return tile;
    }

    @Override
    public void moveTo(int x, int y) {
        this.curX = x;
        this.curY = y;
        int tx = TiledCanvas.tileX(x, canvas.tileSize);
        int ty = TiledCanvas.tileY(y, canvas.tileSize);

        currentTile = getTileFor(tx, ty);
        if (currentTile != null) {
            if (writable) {
                currentData = currentTile.getPixelsForWrite(canvas.tileSize);
            } else {
                currentData = currentTile.getPixelsSnapshot();
            }
            int lx = TiledCanvas.localX(x, canvas.tileSize);
            int ly = TiledCanvas.localY(y, canvas.tileSize);
            offset = (ly * canvas.tileSize + lx) * TiledCanvas.CHANNELS;
            hasCurrent = true;
        } else {
            // 只读且瓦片不存在
            currentData = null;
            offset = 0;
            hasCurrent = true; // 仍然标记有效，getPixel 返回默认像素
        }
    }

    @Override
    public void getPixel(float[] out) {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        if (currentData != null) {
            out[0] = currentData[offset];
            out[1] = currentData[offset + 1];
            out[2] = currentData[offset + 2];
            out[3] = currentData[offset + 3];
        } else {
            System.arraycopy(canvas.getDefaultPixel(), 0, out, 0, TiledCanvas.CHANNELS);
        }
    }

    @Override
    public void setPixel(float r, float g, float b, float a) {
        if (!writable) throw new UnsupportedOperationException("Read-only iterator");
        if (!hasCurrent) throw new IllegalStateException("No current position");
        // 确保瓦片存在
        if (currentData == null) {
            // 重新获取（ensure 会创建）
            int tx = TiledCanvas.tileX(curX, canvas.tileSize);
            int ty = TiledCanvas.tileY(curY, canvas.tileSize);
            currentTile = canvas.ensureTile(tx, ty);
            currentData = currentTile.getPixelsForWrite(canvas.tileSize);
            int lx = TiledCanvas.localX(curX, canvas.tileSize);
            int ly = TiledCanvas.localY(curY, canvas.tileSize);
            offset = (ly * canvas.tileSize + lx) * TiledCanvas.CHANNELS;
            // 更新缓存（由于 getTileFor 已缓存，但只读时可能为 null）
            // 为了缓存一致性，直接更新缓存
            long key = TiledCanvas.pack(tx, ty);
            for (CacheEntry entry : cache) {
                if (entry.key == key) {
                    entry.tile = currentTile;
                    break;
                }
            }
        }
        currentData[offset] = r;
        currentData[offset + 1] = g;
        currentData[offset + 2] = b;
        currentData[offset + 3] = a;
    }

    @Override
    public int x() {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        return curX;
    }

    @Override
    public int y() {
        if (!hasCurrent) throw new IllegalStateException("No current position");
        return curY;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }
}