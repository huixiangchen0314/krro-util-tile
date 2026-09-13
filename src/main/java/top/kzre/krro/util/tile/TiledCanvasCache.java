package top.kzre.krro.util.tile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FIFO 缓存
 */
public final class TiledCanvasCache {

    private final LRUCache cache;
    private final Object lock = new Object();

    public TiledCanvasCache(int maxSize) {
        this.cache = new LRUCache(maxSize);
    }

    /** 获取缓存画布，不会增加引用计数（缓存自身持有引用） */
    public TiledCanvas get(Object key) {
        synchronized (lock) {
            return cache.get(key);
        }
    }

    /** 放入画布，如果已存在相同 key 则替换（旧画布会被释放） */
    public void put(Object key, TiledCanvas canvas) {
        synchronized (lock) {
            cache.put(key, canvas);
        }
    }

    /** 移除并释放指定 key 的画布 */
    public void remove(Object key) {
        synchronized (lock) {
            TiledCanvas canvas = cache.remove(key);
            if (canvas != null) {
                canvas.clear();
            }
        }
    }

    /** 清空所有缓存并释放画布 */
    public void clear() {
        synchronized (lock) {
            cache.clear();
        }
    }

    // ---- 内部 LRU 实现 ----
    private static class LRUCache extends LinkedHashMap<Object, TiledCanvas> {
        private final int maxSize;

        LRUCache(int maxSize) {
            super(16, 0.75f, true);   // accessOrder = true → LRU
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, TiledCanvas> eldest) {
            if (size() > maxSize) {
                eldest.getValue().clear(); // 释放数据
                return true;
            }
            return false;
        }

        @Override
        public TiledCanvas remove(Object key) {
            TiledCanvas canvas = super.remove(key);
            if (canvas != null) {
                canvas.clear();
            }
            return canvas;
        }

        @Override
        public void clear() {
            for (TiledCanvas canvas : values()) {
                canvas.clear();
            }
            super.clear();
        }
    }
}