package top.kzre.krro.util.tile;

public  final class SeqIteratorImpl implements SequentialIterator {
    private final TiledCanvas canvas;
    private final int startX, startY, endX, endY;
    private final boolean writable;
    private final ScanOrder order;

    private int curX, curY;
    private Tile currentTile;
    private float[] curTileData;
    private int tileOffset;
    private boolean hasCurrent;

    SeqIteratorImpl(TiledCanvas canvas, int x, int y, int w, int h,
                    boolean writable, ScanOrder order) {
        this.canvas = canvas;
        this.startX = x;
        this.startY = y;
        this.endX = x + w - 1;
        this.endY = y + h - 1;
        this.writable = writable;
        this.order = order;
        this.curX = startX;
        this.curY = startY;
        this.hasCurrent = loadTileForCurrentPosition();
    }

    private boolean loadTileForCurrentPosition() {
        if (curX < startX || curX > endX || curY < startY || curY > endY) {
            return false;
        }
        int tx = TiledCanvas.tileX(curX, canvas.tileSize);
        int ty = TiledCanvas.tileY(curY, canvas.tileSize);

        if (writable) {
            currentTile = canvas.ensureTile(tx, ty);
            curTileData = currentTile.getPixelsForWrite(canvas.tileSize);
        } else {
            currentTile = canvas.getTile(tx, ty);
            if (currentTile != null) {
                curTileData = currentTile.getPixelsSnapshot();
            } else {
                curTileData = null;
            }
        }

        if (curTileData != null) {
            int lx = TiledCanvas.localX(curX, canvas.tileSize);
            int ly = TiledCanvas.localY(curY, canvas.tileSize);
            tileOffset = (ly * canvas.tileSize + lx) * TiledCanvas.CHANNELS;
        }
        return true;
    }

    @Override
    public boolean next() {
        if (!hasCurrent) return false;

        int nextX = curX, nextY = curY;
        boolean moved = false;

        switch (order) {
            case ROW_MAJOR:
                if (curX < endX) {
                    nextX = curX + 1;
                    moved = true;
                } else if (curY < endY) {
                    nextY = curY + 1;
                    nextX = startX;
                    moved = true;
                }
                break;
            case COLUMN_MAJOR:
                if (curY < endY) {
                    nextY = curY + 1;
                    moved = true;
                } else if (curX < endX) {
                    nextX = curX + 1;
                    nextY = startY;
                    moved = true;
                }
                break;
            case SNAKE:
                if ((curY - startY) % 2 == 0) {
                    if (curX < endX) {
                        nextX = curX + 1;
                        moved = true;
                    } else if (curY < endY) {
                        nextY = curY + 1;
                        nextX = endX;
                        moved = true;
                    }
                } else {
                    if (curX > startX) {
                        nextX = curX - 1;
                        moved = true;
                    } else if (curY < endY) {
                        nextY = curY + 1;
                        nextX = startX;
                        moved = true;
                    }
                }
                break;
        }

        if (!moved) {
            hasCurrent = false;
            return false;
        }

        curX = nextX;
        curY = nextY;
        hasCurrent = loadTileForCurrentPosition();
        return hasCurrent;
    }

    @Override
    public void getPixel(float[] out) {
        if (!hasCurrent) throw new IllegalStateException("Iterator exhausted or not started");
        if (curTileData != null) {
            out[0] = curTileData[tileOffset];
            out[1] = curTileData[tileOffset + 1];
            out[2] = curTileData[tileOffset + 2];
            out[3] = curTileData[tileOffset + 3];
        } else {
            System.arraycopy(canvas.defaultPixel, 0, out, 0, TiledCanvas.CHANNELS);
        }
    }

    @Override
    public void setPixel(float r, float g, float b, float a) {
        if (!writable) throw new UnsupportedOperationException("Read-only iterator");
        if (!hasCurrent) throw new IllegalStateException("Iterator exhausted or not started");

        if (curTileData == null) {
            int tx = TiledCanvas.tileX(curX, canvas.tileSize);
            int ty = TiledCanvas.tileY(curY, canvas.tileSize);
            currentTile = canvas.ensureTile(tx, ty);
            curTileData = currentTile.getPixelsForWrite(canvas.tileSize);
            int lx = TiledCanvas.localX(curX, canvas.tileSize);
            int ly = TiledCanvas.localY(curY, canvas.tileSize);
            tileOffset = (ly * canvas.tileSize + lx) * TiledCanvas.CHANNELS;
        }
        curTileData[tileOffset] = r;
        curTileData[tileOffset + 1] = g;
        curTileData[tileOffset + 2] = b;
        curTileData[tileOffset + 3] = a;
    }

    @Override
    public int x() {
        if (!hasCurrent) throw new IllegalStateException("Iterator exhausted or not started");
        return curX;
    }

    @Override
    public int y() {
        if (!hasCurrent) throw new IllegalStateException("Iterator exhausted or not started");
        return curY;
    }

    @Override
    public void reset() {
        curX = startX;
        curY = startY;
        hasCurrent = loadTileForCurrentPosition();
    }
}
