package top.kzre.krro.util.tile;

public final class DefaultTile extends AbstractTile{
    /**
     * 构造 Tile，直接持有传入的 data（假设 data 已经包含初始引用）。
     * 不再额外 acquire，由调用者确保 data 的引用计数已正确增加。
     *
     * @param tx
     * @param ty
     * @param data
     */
    DefaultTile(int tx, int ty, TileData data) {
        super(tx, ty, data);
    }
}
