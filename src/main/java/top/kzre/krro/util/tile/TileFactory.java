package top.kzre.krro.util.tile;

/**
 * 瓦片工厂。{@link TiledCanvas} 创建或替换瓦片时通过它生产。
 *
 * <p>让上层控制 Tile 的实例化——默认是 {@link DefaultTile}，
 * 特殊场景（如 GPU 换页）可注入带额外能力的子类。
 *
 * <p><b>线程契约</b>：由 TiledCanvas 在创建瓦片时调用。具体线程
 * 由调用 TiledCanvas 的一方决定。
 */
@FunctionalInterface
public interface TileFactory {
    /**
     * @param tx   瓦片 x 坐标
     * @param ty   瓦片 y 坐标
     * @param data 瓦片的初始数据
     * @return 新的 Tile 实例
     */
    Tile create(int tx, int ty, TileData data);
}