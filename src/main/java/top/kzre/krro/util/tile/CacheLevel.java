package top.kzre.krro.util.tile;

public enum CacheLevel {
    /**
     * JVM 堆内存
     */
    HEAP,
    /**
     * 堆外内存
     */
    DIRECT,
    /**
     * 临时交换文件，磁盘IO
     */
    DISK,
}
