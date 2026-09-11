package top.kzre.krro.util.tile;

/**
 * 抽象瓦片数据，不管是否是CPU 存储还是GPU 存储，交换文件还是什么
 */
public interface TileData {
    float[] getPixels();

    int acquire();

    int release();

    int refCount();

    boolean valid();

    int acquireIfValid();

    int getByteSize();
    TileData copy();

}
