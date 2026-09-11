package top.kzre.krro.util.tile.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 基于弱引用的双向链表队列。
 * <p>
 * 元素按加入顺序排列，迭代顺序为：最新 -> 最旧。
 * 当元素被 GC 回收后，会在下一次 add/remove/cleanup/iterator 遍历时自动从链表中移除。
 */
public class WeakReferenceQueue<E> {

    private final ReferenceQueue<E> garbage = new ReferenceQueue<>();

    /** 哨兵节点，避免到处判空；referent 永远为 null，不会被 GC 回收（head 被强引用）。 */
    private final ListEntry<E> head = new ListEntry<>(null, garbage);

    private int size = 0;

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void add(E obj) {
        cleanup();
        size++;
        new ListEntry<>(obj, garbage).insert(head.prev);
    }

    /**
     * @return 是否真的移除了元素
     */
    public boolean remove(E obj) {
        cleanup();
        ListEntry<E> entry = head.next;
        while (entry != head) {
            if (entry.get() == obj) {
                size--;
                entry.remove();
                return true;
            }
            entry = entry.next;
        }
        return false;
    }

    /** 清理所有已被 GC 回收的节点。 */
    public void cleanup() {
        Reference<? extends E> ref;
        while ((ref = garbage.poll()) != null) {
            if (ref instanceof ListEntry) {
                ListEntry<?> entry = (ListEntry<?>) ref;
                size--;
                entry.remove();
            }
        }
    }


    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /** 下一个待检查的节点，从最新开始。 */
        private ListEntry<E> nextEntry = head.prev;
        /** 预取的下一个有效元素。 */
        private E nextElement;
        /** 最近一次 next() 返回的节点，用于 remove()。 */
        private ListEntry<E> lastReturned;

        @Override
        public boolean hasNext() {
            if (nextElement != null) {
                return true;
            }
            while (nextEntry != head) {
                E e = nextEntry.get();
                if (e != null) {
                    nextElement = e;
                    return true;
                }
                // 已被 GC 回收，顺手从链表中移除
                ListEntry<E> stale = nextEntry;
                nextEntry = nextEntry.prev;
                size--;
                stale.remove();
            }
            return false;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            lastReturned = nextEntry;
            E result = nextElement;
            nextEntry = nextEntry.prev;
            nextElement = null;
            return result;
        }

        @Override
        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException(
                        "next() has not been called, or remove() already called");
            }
            lastReturned.remove();
            size--;
            lastReturned = null;
        }
    }

    private static class ListEntry<T> extends WeakReference<T> {
        ListEntry<T> prev, next;

        ListEntry(T referent, ReferenceQueue<? super T> queue) {
            super(referent, queue);
            prev = this;
            next = this;
        }

        void insert(ListEntry<T> where) {
            prev = where;
            next = where.next;
            where.next = this;
            next.prev = this;
        }

        void remove() {
            prev.next = next;
            next.prev = prev;
            next = this;
            prev = this;
        }
    }
}