package top.kzre.krro.util.tile;

/**
 * 瓦片句柄，持有对 TileData 的引用，支持 COW 写入。
 * 线程安全（所有公开方法使用 synchronized 保证可见性和原子性）。
 */
public abstract class Tile implements VersionedTile{
    @Override
    public Object version() {
        TileData data = getDataRef();
        if (data instanceof VersionedTile) {
            return ((VersionedTile) data).version();
        }
        return data;
    }

    /**
     * 将本瓦片附着到指定的画布。
     *
     * <p>建立瓦片与其所属画布之间的归属关系——附着后瓦片才参与
     * 画布级别的生命周期管理（COW 数据替换、失效通知、释放回调等）。
     *
     * <p><b>包级可见</b>——由 {@link TiledCanvas} 在内部调用。瓦片的
     * 归属由画布管理，上层调用方不应直接调用本方法。
     *
     * <p><b>线程契约</b>：由调用方（画布）负责在适当的同步上下文中
     * 调用，本方法自身不保证同步。
     *
     * @param canvas 所属画布，不能为 {@code null}
     */
    abstract void attach(TiledCanvas canvas);

    /**
     * 判断本瓦片当前是否有效。
     *
     * <p>抽象方法——具体判定标准由子类决定。常见失效场景包括：所属
     * 画布已关闭、数据引用已被释放或替换为空数据、或被显式标记为
     * 不再参与渲染。失效的瓦片不再具有稳定的像素语义——读写像素或
     * 将其纳入合成都会产生未定义结果。
     *
     * <p>调用方在访问瓦片像素数据或将其传递给渲染管线前，应先检查
     * 本方法返回 {@code true}。
     *
     * @return 瓦片仍然有效时返回 {@code true}
     */
    public abstract boolean isValid();

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
    abstract void replaceDataOwned(TileData newData);
    abstract TileData getDataRef();

    public abstract boolean compareAndReplaceData(Tile other);


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

    /**
     * 写入瓦片数据，自动转堆内存
     */
    public abstract void setPixel(int localX, int localY, float[] pixel,
                  int tileSize, int channels);

    /**
     * 读取瓦片数据，字段转堆内存
     */
    public abstract void getPixel(int localX, int localY, float[] out,
                  int tileSize, int channels);

    /**
     * 读取瓦片数组，自动转堆内存数据
     */
    public abstract float[] getPixelsSnapshot();

    /**
     * 写瓦片数组，自动转堆内存
     */
    public abstract float[] getPixelsForWrite();
}