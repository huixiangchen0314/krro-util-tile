package top.kzre.krro.util.tile;

/**
 * {@link TileFactory} 的默认实现：创建 {@link DefaultTile}。
 *
 * <p>单例——无状态，全局共享一个实例。通过 {@link #INSTANCE} 获取。
 */
public final class DefaultTileFactory implements TileFactory {

    /** 全局单例。 */
    public static final DefaultTileFactory INSTANCE = new DefaultTileFactory();

    private DefaultTileFactory() {}

    @Override
    public Tile create(int tx, int ty, TileData data) {
        return new DefaultTile(tx, ty, data);
    }
}