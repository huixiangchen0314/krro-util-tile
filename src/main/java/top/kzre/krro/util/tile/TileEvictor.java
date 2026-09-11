package top.kzre.krro.util.tile;

public interface TileEvictor {
    void add(Tile tile);

    void evict();
}
