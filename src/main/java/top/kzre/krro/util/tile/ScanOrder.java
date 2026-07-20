package top.kzre.krro.util.tile;

/**
 * 顺序迭代器的扫描顺序。
 */
public enum ScanOrder {
    /** 行优先：从左到右，从上到下 */
    ROW_MAJOR,
    /** 列优先：从上到下，从左到右 */
    COLUMN_MAJOR,
    /** 蛇形：行交替方向（从左到右，下一行从右到左） */
    SNAKE
}