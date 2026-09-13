package top.kzre.krro.util.tile;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 抽象瓦片数据，不管是否是CPU 存储还是GPU 存储，交换文件还是什么
 */
public interface TileData {

    @Deprecated
    float[] getPixels();

    /**
     * 返回底层像素的<b>活视图</b>。修改它直接影响底层数据。
     *
     * <p>约定：
     * <ul>
     *   <li>position = 0，limit = size（float 数量）</li>
     *   <li>每次调用返回新的视图对象；修改其 position 不影响内部状态</li>
     *   <li>字节序为 {@link ByteOrder#nativeOrder()}</li>
     * </ul>
     *
     */
    FloatBuffer floatBuffer();

    // 标记脏，默认什么也不做
    default void markDirty(){

    }

    int acquire();

    int release();

    int refCount();

    boolean valid();

    int acquireIfValid();

    int getByteSize();
    TileData copy();

}
