package top.kzre.krro.util.tile;

/**
 * 瓦片句柄，持有对 TileData 的引用，支持 COW 写入。
 * 线程安全（所有公开方法使用 synchronized 保证可见性和原子性）。
 */
public abstract class Tile {

    /**
     * 替换当前数据（用于共享、或存储设施更新）。
     *
     * <p><b>幂等</b>：先 acquire 新数据、再 release 旧数据。当
     * {@code newData == data} 时，acquire 和 release 净效果为零，
     * 引用计数不变，不会触发瞬时的归零副作用。
     *
     * <p><b>调用者不需预先增加引用计数</b>——本方法内部完成。
     *
     * <p><b>线程契约</b>：方法本身 {@code synchronized}，可从任意线程调用。
     * 但替换后数据的可见性由调用方负责——如果其他线程持有旧引用并继续
     * 访问，需要外部同步。
     *
     * @param newData 新数据，不能为 null
     * @throws IllegalArgumentException newData 为 null
     */
    public abstract void replaceData(TileData newData) ;

    abstract TileData getDataRef();

    /**
     * 以类型 {@code T} 查询瓦片数据的能力。
     *
     * <p>如果内部数据实现了 {@code type}，返回该实例；否则返回
     * {@code null}。用于上层判断 tile 是否支持某种能力并拿到接口引用：
     * <pre>{@code
     * GLTile gpu = tile.queryData(GLTile.class);
     * if (gpu != null) {
     *     gpu.ensureUploaded();
     *     GLTileDescriptor d = gpu.getDescriptor();
     * }
     * }</pre>
     *
     * <p><b>拒绝 {@link TileData}</b>——{@code queryData(TileData.class)}
     * 抛 {@link IllegalArgumentException}。直接暴露内部数据会让上层绕过
     * 能力接口操作引用计数，破坏封装。上层应查询具体能力接口。
     *
     * @param type 查询的能力接口类型；不能为 {@code null} 或 {@link TileData}
     * @return 内部数据实现了 {@code type} 时返回该实例；否则 {@code null}
     * @throws IllegalArgumentException type 为 {@code null} 或 {@link TileData}
     */
    public <T> T queryData(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (type == TileData.class) {
            throw new IllegalArgumentException(
                    "Cannot query for TileData directly; query a specific "
                            + "capability interface (e.g. GLTile) instead");
        }
        TileData data = getDataRef();
        if (data == null) {
            return null;
        }
        return type.isInstance(data) ? type.cast(data) : null;
    }


    public abstract int tx();

    public abstract int ty();

    @Deprecated
    public abstract void setPixel(int localX, int localY, float r, float g, float b, float a, int tileSize);

    @Deprecated
    public abstract void getPixel(int localX, int localY, float[] out, int tileSize);

    public abstract void setPixel(int localX, int localY, float[] pixel,
                  int tileSize, int channels);

    public abstract void getPixel(int localX, int localY, float[] out,
                  int tileSize, int channels);

    public abstract float[] getPixelsSnapshot();

    public abstract float[] getPixelsForWrite();
}