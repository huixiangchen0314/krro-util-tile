package top.kzre.krro.util.tile;

/**
 * 抽象瓦片数据，不管是否是CPU 存储还是GPU 存储，交换文件还是什么
 */
public interface TileData {
    float[] getPixels();

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
