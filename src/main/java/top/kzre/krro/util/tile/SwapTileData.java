package top.kzre.krro.util.tile;

import lombok.Getter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 交换文件中保存的瓦片数据。
 *
 * <p>用途：瓦片数量过多、内存吃紧时，将不活跃瓦片写入磁盘，
 * 需要时通过 mmap 读回。使用内存映射实现惰性加载，避免一次性分配堆内存。
 *
 * <p>生命周期：
 * <ul>
 *   <li>引用计数由 {@link AbstractTileData} 管理</li>
 *   <li>{@code onRelease} 清空映射引用；若 {@code deleteOnRelease=true} 则删除文件</li>
 *   <li>无论 {@code deleteOnRelease} 如何，都会调用 {@link java.io.File#deleteOnExit()} 兜底</li>
 * </ul>
 */
public final class SwapTileData extends AbstractTileData {

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
    public SwapTileData(float[] src, boolean deleteOnRelease) {
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
    public SwapTileData(Path path, int size, boolean deleteOnRelease) {
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
        MappedByteBuffer m = requireMapping();
        float[] out = new float[size];
        // duplicate 不改变原 buffer 的 position，可并发调用
        m.asFloatBuffer().get(out);
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
     * 零拷贝只读视图。LWJGL 上传纹理时可用，避免回读到 float[]。
     * 调用方不得改变其 position/limit。
     */
    public MappedByteBuffer mapping() {
        return requireMapping();
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
        return new SwapTileData(getPixels(), deleteOnRelease);
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
                    "SwapTileData has been released: " + path);
        }
        return m;
    }
}