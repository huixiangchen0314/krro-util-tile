package top.kzre.krro.util.tile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 堆外内存存储的瓦片数据。
 *
 * <p>用途：与 LWJGL / GPU / NIO 交互时，避免 JVM 堆与本地内存之间的拷贝。
 * 通过 {@link #buffer()} 拿到 {@link FloatBuffer}，LWJGL 的 GL 调用可直接使用（零拷贝）。
 *
 * <p>生命周期：
 * <ul>
 *   <li>引用计数由 {@link AbstractTileData} 管理，与 {@link HeapTileData} 语义一致</li>
 *   <li>release 后清空引用，本地内存由 {@link ByteBuffer} 的内部 Cleaner 随 GC 释放</li>
 *   <li>高频复用场景请外部池化（{@link #reset} 允许重新绑定新数据）</li>
 * </ul>
 *
 * <p>注意：{@link #copy()} 会分配新的堆外内存；频繁 copy 时建议池化。
 */
public final class DirectTileData extends AbstractTileData {

    private final int size;          // float 数量
    private ByteBuffer buffer;
    private FloatBuffer floatView;

    /** 按 float 数量分配堆外内存。 */
    public DirectTileData(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        this.size = size;
        this.buffer = ByteBuffer.allocateDirect(size * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        this.floatView = buffer.asFloatBuffer();
    }

    /** 从堆内数组拷贝到堆外。 */
    public DirectTileData(float[] src) {
        this(src.length);
        this.floatView.put(src);
    }

    /**
     * 使用外部准备好的堆外内存。所有权移交给本实例。
     *
     * <p>约定：
     * <ul>
     *   <li>buffer 必须为 {@link ByteOrder#nativeOrder()} 字节序</li>
     *   <li>buffer 剩余可读字节数必须 ≥ size * {@link Float#BYTES}</li>
     *   <li>调用方移交 buffer 后不应再持有对它的引用</li>
     * </ul>
     */
    public DirectTileData(ByteBuffer externalBuffer, int size) {
        if (externalBuffer == null) {
            throw new IllegalArgumentException("externalBuffer must not be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (externalBuffer.order() != ByteOrder.nativeOrder()) {
            throw new IllegalArgumentException(
                    "externalBuffer must use native byte order");
        }
        if (externalBuffer.remaining() < size * Float.BYTES) {
            throw new IllegalArgumentException(
                    "externalBuffer too small: need " + (size * Float.BYTES)
                            + " bytes, got " + externalBuffer.remaining());
        }
        this.size = size;
        this.buffer = externalBuffer;
        this.floatView = externalBuffer.asFloatBuffer();
    }

    /**
     * 零拷贝视图。LWJGL 的所有 GL 调用可直接使用此 buffer。
     * 调用方不得改变其 position/limit，如需操作请 {@link FloatBuffer#duplicate()}。
     */
    public FloatBuffer buffer() {
        return floatView;
    }

    public int size() {
        return size;
    }

    /** 拷贝堆外数据到堆内数组。不改变内部 position。 */
    @Override
    public float[] getPixels() {
        float[] out = new float[size];
        FloatBuffer dup = floatView.duplicate();
        dup.position(0);
        dup.get(out);
        return out;
    }

    /** 从堆内数组覆盖写入。 */
    public void put(float[] src) {
        if (src.length != size) {
            throw new IllegalArgumentException(
                    "size mismatch: expected " + size + ", got " + src.length);
        }
        FloatBuffer dup = floatView.duplicate();
        dup.position(0);
        dup.put(src);
    }

    /** 从另一个 DirectTileData 拷贝（ByteBuffer 层次拷贝，最快）。 */
    public void copyFrom(DirectTileData src) {
        if (src.size != this.size) {
            throw new IllegalArgumentException("size mismatch");
        }
        ByteBuffer dstB = this.buffer.duplicate();
        ByteBuffer srcB = src.buffer.duplicate();
        dstB.clear();
        srcB.clear();
        dstB.put(srcB);
    }

    @Override
    protected void onRelease() {
        // 清空引用，本地内存由 ByteBuffer 的 Cleaner 随 GC 释放。
        buffer = null;
        floatView = null;
    }

    @Override
    public int getByteSize() {
        return size * Float.BYTES;
    }

    @Override
    public TileData copy() {
        DirectTileData copy = new DirectTileData(size);
        copy.copyFrom(this);
        return copy;
    }

    // ── 池化支持 ─────────────────────────────────

    /**
     * 重置此实例，绑定到新的堆内数据。
     * 仅在实例被池化复用时调用，调用前必须确保 refCount == 0。
     */
    void reset(float[] src) {
        if (src.length != size) {
            throw new IllegalArgumentException("size mismatch");
        }
        if (refCount() > 0) {
            throw new IllegalStateException(
                    "Cannot reset a tile with active references: " + refCount());
        }
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(size * Float.BYTES)
                    .order(ByteOrder.nativeOrder());
            floatView = buffer.asFloatBuffer();
        }
        FloatBuffer dup = floatView.duplicate();
        dup.position(0);
        dup.put(src);
    }
}