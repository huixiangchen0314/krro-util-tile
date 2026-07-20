package top.kzre.krro.util.tile;

/**
 * 瓦片访问回调接口，用于遍历瓦片时接收每个瓦片的索引和只读数据。
 */
@FunctionalInterface
public interface TileVisitor {
    /**
     * 访问一个瓦片。
     * @param tx   瓦片 X 索引
     * @param ty   瓦片 Y 索引
     * @param data 像素数据的只读快照（不可修改）
     */
    void visit(int tx, int ty, float[] data);
}