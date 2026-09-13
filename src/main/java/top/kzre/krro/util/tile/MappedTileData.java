package top.kzre.krro.util.tile;

import lombok.Getter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 基于内存映射文件的瓦片数据。
 *
 * <p>用途：
 * <ul>
 *   <li>瓦片过多、内存吃紧时，把不活跃瓦片写盘，需要时 mmap 读回</li>
 *   <li>直接映射已存在的磁盘数据，避免一次性加载</li>
 * </ul>
 *
 * <p>相比 {@link DirectTileData}，本实现把数据放在文件系统而非匿名堆外内存，
 * 因此释放后数据可持久（若 {@code deleteOnRelease=false}）或由 OS 回收。
 */
public final class MappedTileData extends AbstractTileData {

    private final int size;              // float 数量
    @Getter
    private final Path path;             // 交换文件路径
    private final boolean deleteOnRelease;
    private volatile MappedByteBuffer mapping;

    // ── 构造 ────────────────────────────────────────

    /**
     * 从堆内数据创建交换瓦片（写入新建的临时文件）。
     *
     * @param src             初始像素数据
     * @param deleteOnRelease 释放时是否删除交换文件
     */
    public MappedTileData(float[] src, boolean deleteOnRelease) {
        if (src == null || src.length == 0) {
            throw new IllegalArgumentException("src must not be empty");
        }
        this.size = src.length;
        this.deleteOnRelease = deleteOnRelease;
        try {
            this.path = Files.createTempFile("krro-tile-", ".swap");
            this.path.toFile().deleteOnExit();   // 兜底清理
            long bytes = (long) size * Float.BYTES;
            try (FileChannel ch = FileChannel.open(path,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                this.mapping = ch.map(FileChannel.MapMode.READ_WRITE, 0, bytes);
            }
            this.mapping.asFloatBuffer().put(src);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 从已存在的交换文件绑定瓦片。
     *
     * @param path            交换文件路径
     * @param size            float 数量
     * @param deleteOnRelease 释放时是否删除文件
     */
    public MappedTileData(Path path, int size, boolean deleteOnRelease) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        this.size = size;
        this.path = path;
        this.deleteOnRelease = deleteOnRelease;
        try {
            long bytes = (long) size * Float.BYTES;
            long actual = Files.size(path);
            if (actual < bytes) {
                throw new IllegalArgumentException(
                        "swap file too small: expected " + bytes
                                + " bytes, got " + actual);
            }
            try (FileChannel ch = FileChannel.open(path,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                this.mapping = ch.map(FileChannel.MapMode.READ_WRITE, 0, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── TileData API ───────────────────────────────

    /**
     * 从交换文件读取全部像素到堆内数组。
     * 每次调用都分配新的 float[]（相比 HeapTileData，这是"回读"的代价）。
     */
    @Override
    public float[] getPixels() {
        float[] out = new float[size];
        floatBuffer().get(out);   // ← position=0，安全
        return out;
    }

    /**
     * 将堆内数据写回交换文件（覆盖）。
     * 不主动 force()，由操作系统决定刷盘时机。
     */
    public void put(float[] src) {
        if (src == null || src.length != size) {
            throw new IllegalArgumentException(
                    "size mismatch: expected " + size
                            + ", got " + (src == null ? "null" : src.length));
        }
        MappedByteBuffer m = requireMapping();
        m.asFloatBuffer().put(src);
    }

    /**
     * 内部映射引用。零包装。
     *
     * <p><b>警告</b>：任何对返回 buffer 的 position/limit 修改都会影响本实例的
     * 后续操作，包括 {@link #mapping()}、{@link #getPixels()} 等。
     * 除非明确知道自己在做什么，否则请用 {@link #mapping()}。
     */
    public MappedByteBuffer rawMapping() {
        return requireMapping();
    }

    /**
     * 安全视图。position=0，limit=byteSize，独立对象。
     * LWJGL 上传纹理时用这个，不会污染内部状态。
     */
    public MappedByteBuffer mapping() {
        MappedByteBuffer m = requireMapping();
        MappedByteBuffer dup = (MappedByteBuffer) m.duplicate();
        dup.position(0);
        dup.limit(size * Float.BYTES);
        return dup;
    }

    /**
     * 底层像素的活视图。position=0，limit=size（float 数量）。
     * 独立视图对象，修改其 position 不影响内部映射。
     */
    @Override
    public FloatBuffer floatBuffer() {
        return mapping().asFloatBuffer();
    }

    public int size() {
        return size;
    }

    @Override
    public int getByteSize() {
        return size * Float.BYTES;
    }

    @Override
    public TileData copy() {
        // 拷贝到新的交换文件（独立生命周期）
        return new MappedTileData(getPixels(), deleteOnRelease);
    }

    @Override
    protected void onRelease() {
        // 断开映射引用，让 Cleaner 有机会释放。
        mapping = null;
        if (deleteOnRelease) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // 不抛出：mmap 未完全释放时（Windows）删除可能失败，
                // 已通过 deleteOnExit 兜底。
                System.err.println(
                        "Failed to delete swap file: " + path + " - " + e);
            }
        }
    }

    // ── 内部 ───────────────────────────────────────

    private MappedByteBuffer requireMapping() {
        MappedByteBuffer m = mapping;
        if (m == null) {
            throw new IllegalStateException(
                    "MappedTileData has been released: " + path);
        }
        return m;
    }
}