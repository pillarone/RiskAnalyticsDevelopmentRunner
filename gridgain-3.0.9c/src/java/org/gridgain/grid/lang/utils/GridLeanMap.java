// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.typedef.internal.*;

import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * Lean map implementation that keeps up to five entries in its fields.
 * {@code Null}-keys are not supported.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLeanMap<K, V> extends GridSerializableMap<K, V> implements Cloneable {
    /** Implementation used internally. */
    private Map<K, V> map;

    /** Counter for batch puts. */
    private int batchCntr;

    /**
     * Constructs empty lean map.
     */
    public GridLeanMap() {
        /* No-op. */
    }

    /**
     * Constructs lean map with initial size.
     * <p>
     * If given size is greater than zero then map activates batch mode for <tt>put()</tt>
     * operations. In this mode map allows to put up to <tt>size</tt> number of key-value
     * pairs without internal size optimization, i.e. each next <tt>put()</tt> in batch
     * doesn't switch backing implementation so that initial implementation is used as long
     * as its capacity allows.
     * <p>
     * Note that any removal operation either through iterator or map
     * itself turns batch mode off and map starts optimizing size after any modification.
     *
     * @param size Initial size.
     */
    @SuppressWarnings({"IfMayBeConditional"})
    public GridLeanMap(int size) {
        assert size >= 0;

        batchCntr = size;

        if (size == 0) {
            // No-op.
        }
        else if (size == 1) {
            map = new Map1<K, V>();
        }
        else if (size == 2) {
            map = new Map2<K, V>();
        }
        else if (size == 3) {
            map = new Map3<K, V>();
        }
        else if (size == 4) {
            map = new Map4<K, V>();
        }
        else if (size == 5) {
            map = new Map5<K, V>();
        }
        else {
            map = new HashMap<K, V>(size, 1.0f);
        }
    }

    /**
     * Constructs lean map.
     *
     * @param m Map to copy entries from.
     */
    public GridLeanMap(Map<K, V> m) {
        buildFrom(m);
    }

    /**
     * @param m Map to build from.
     */
    private void buildFrom(Map<K, V> m) {
        Iterator<Entry<K, V>> iter = m.entrySet().iterator();

        if (m.isEmpty()) {
            map = null;
        }
        else if (m.size() == 1) {
            Entry<K, V> e = iter.next();

            map = new Map1<K, V>(e.getKey(), e.getValue());
        }
        else if (m.size() == 2) {
            Entry<K, V> e1 = iter.next();
            Entry<K, V> e2 = iter.next();

            map = new Map2<K, V>(e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue());
        }
        else if (m.size() == 3) {
            Entry<K, V> e1 = iter.next();
            Entry<K, V> e2 = iter.next();
            Entry<K, V> e3 = iter.next();

            map = new Map3<K, V>(e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue(), e3.getKey(), e3.getValue());
        }
        else if (m.size() == 4) {
            Entry<K, V> e1 = iter.next();
            Entry<K, V> e2 = iter.next();
            Entry<K, V> e3 = iter.next();
            Entry<K, V> e4 = iter.next();

            map = new Map4<K, V>(e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue(), e3.getKey(), e3.getValue(),
                e4.getKey(), e4.getValue());
        }
        else if (m.size() == 5) {
            Entry<K, V> e1 = iter.next();
            Entry<K, V> e2 = iter.next();
            Entry<K, V> e3 = iter.next();
            Entry<K, V> e4 = iter.next();
            Entry<K, V> e5 = iter.next();

            map = new Map5<K, V>(e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue(), e3.getKey(), e3.getValue(),
                e4.getKey(), e4.getValue(), e5.getKey(), e5.getValue());
        }
        else {
            map = new HashMap<K, V>(m);
        }
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return map != null ? map.size() : 0;
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(Object key) {
        A.notNull(key, "key");

        return map != null && map.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public boolean containsValue(Object value) {
        return map != null && map.containsValue(value);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V get(Object key) {
        A.notNull(key, "key");

        return map != null ? map.get(key) : null;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V put(K key, V val) throws NullPointerException {
        A.notNull(key, "key");

        if (key == null) {
            throw new NullPointerException("Null-keys are not supported.");
        }

        if (map == null) {
            map = new Map1<K, V>(key, val);

            return null;
        }

        if (map.containsKey(key)) {
            V old = get(key);

            map.put(key, val);

            return old;
        }

        if (batchCntr > 0) {
            batchCntr--;

            V old = get(key);

            map.put(key, val);

            return old;
        }

        int size = map.size();

        // Switch implementation.
        if (size == 1) {
            Map1<K, V> m = (Map1<K, V>)map;

            map = new Map2<K, V>(m.k1, m.v1, key, val);
        }
        else if (size == 2) {
            Map2<K, V> m = (Map2<K, V>)map;

            map = new Map3<K, V>(m.k1, m.v1, m.k2, m.v2, key, val);
        }
        else if (size == 3) {
            Map3<K, V> m = (Map3<K, V>)map;

            map = new Map4<K, V>(m.k1, m.v1, m.k2, m.v2, m.k3, m.v3, key, val);
        }
        else if (size == 4) {
            Map4<K, V> m = (Map4<K, V>)map;

            map = new Map5<K, V>(m.k1, m.v1, m.k2, m.v2, m.k3, m.v3, m.k4, m.v4, key, val);
        }
        else if (size == 5) {
            Map<K, V> m = map;

            map = new HashMap<K, V>(6, 1.0f);

            map.putAll(m);

            map.put(key, val);
        }
        else {
            map.put(key, val);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public V remove(Object key) {
        A.notNull(key, "key");

        if (map == null) {
            return null;
        }

        Map<K, V> tmpMap = new HashMap<K, V>(map);

        V removed = tmpMap.remove(key);

        if (tmpMap.size() <= 5) {
            buildFrom(tmpMap);
        }
        else {
            // If removing results to hash map then do not recreate it.
            map.remove(key);
        }

        return removed;
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        map = null;
    }

    /** {@inheritDoc} */
    @Override public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked", "CloneDoesntDeclareCloneNotSupportedException"})
    @Override protected Object clone() {
        try {
            GridLeanMap<K, V> clone = (GridLeanMap<K, V>)super.clone();

            clone.buildFrom(this);

            return clone;
        }
        catch (CloneNotSupportedException ignore) {
            throw new InternalError();
        }
    }

    /**
     * Entry set.
     */
    private class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
                /** */
                private int idx = -1;

                /** */
                private Iterator<Entry<K, V>> mapIter;

                /** */
                private Entry<K, V> curEnt;

                /**
                 * @param forceNew If forced to create new instance.
                 * @return Iterator for internal map entry set.
                 */
                @SuppressWarnings({"IfMayBeConditional"})
                private Iterator<Entry<K, V>> getMapIterator(boolean forceNew) {
                    if (mapIter == null || forceNew) {
                        if (map != null) {
                            mapIter = map.entrySet().iterator();
                        }
                        else {
                            mapIter = new Iterator<Entry<K, V>>() {
                                @Override public boolean hasNext() { return false; }

                                @Override public Entry<K, V> next() { throw new NoSuchElementException(); }

                                @Override public void remove() { throw new IllegalStateException(); }
                            };
                        }
                    }

                    return mapIter;
                }

                @Override public boolean hasNext() { return map != null && getMapIterator(false).hasNext(); }

                @Override public Entry<K, V> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    idx++;

                    return curEnt = getMapIterator(false).next();
                }

                @Override public void remove() {
                    if (curEnt == null) {
                        throw new IllegalStateException();
                    }

                    GridLeanMap.this.remove(curEnt.getKey());

                    curEnt = null;

                    mapIter = getMapIterator(true);

                    for (int i = 0; i < idx && mapIter.hasNext(); i++) {
                        mapIter.next();
                    }

                    idx--;
                }
            };
        }

        @Override public int size() {
            return GridLeanMap.this.size();
        }
    }

    /**
     * Map for single entry.
     *
     * @param <K>
     * @param <V>
     */
    private static class Map1<K, V> extends AbstractMap<K, V> implements Serializable {
        /** */
        protected K k1;

        /** */
        protected V v1;

        /**
         * Constructs map.
         */
        Map1() {
            // No-op.
        }

        /**
         * Constructs map.
         *
         * @param k1 Key.
         * @param v1 Value.
         */
        Map1(K k1, V v1) {
            this.k1 = k1;
            this.v1 = v1;
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return k1 != null ? 1 : 0;
        }

        /** {@inheritDoc} */
        @Override public boolean isEmpty() {
            return size() == 0;
        }

        /** {@inheritDoc} */
        @Override public boolean containsKey(Object key) {
            return k1 != null && F.eq(key, k1);
        }

        /** {@inheritDoc} */
        @Override public boolean containsValue(Object value) {
            return k1 != null && F.eq(value, v1);
        }

        /** {@inheritDoc} */
        @Nullable
        @Override public V get(Object key) {
            return k1 != null && F.eq(key, k1) ? v1 : null;
        }

        /**
         * Puts key-value pair into map only if given key is already contained in the map
         * or there are free slots.
         * Note that this implementation of {@link Map#put(Object, Object)} does not match
         * general contract of {@link Map} interface and serves only for internal purposes.
         *
         * @param key Key.
         * @param value Value.
         * @return Previous value associated with given key.
         */
        @Nullable
        @Override public V put(K key, V value) {
            V oldVal = get(key);

            if (k1 == null || F.eq(k1, key)) {
                k1 = key;
                v1 = value;
            }

            return oldVal;
        }

        /**
         * @param keys Keys.
         * @return Key set.
         */
        protected Set<K> keySet(K... keys) {
            Set<K> set = new HashSet<K>(size(), 1.0f);

            for (K k : keys) {
                if (k != null) {
                    set.add(k);
                }
            }

            return set;
        }

        /**
         * @param key Key.
         * @param value Value.
         * @return New entry.
         */
        protected Entry<K, V> e(K key, V value) {
            return new SimpleImmutableEntry<K, V>(key, value);
        }

        /**
         * @param entries Entries.
         * @return Key set.
         */
        protected Set<Entry<K, V>> entrySet(Entry<K, V>... entries) {
            Set<Entry<K, V>> set = new HashSet<Entry<K, V>>(size(), 1.0f);

            for (Entry<K, V> e : entries) {
                if (e.getKey() != null) {
                    set.add(e);
                }
            }

            return set;
        }

        /**
         * @param entries Entries.
         * @return Values.
         */
        protected Collection<V> values(Entry<K, V>... entries) {
            Collection<V> set = new HashSet<V>(size(), 1.0f);

            for (Entry<K, V> e : entries) {
                if (e.getKey() != null) {
                    set.add(e.getValue());
                }
            }

            return set;
        }

        /** {@inheritDoc} */
        @Override public Set<K> keySet() {
            return keySet(k1);
        }

        /** {@inheritDoc} */
        @Override public Set<Entry<K, V>> entrySet() {
            return entrySet(e(k1, v1));
        }

        /** {@inheritDoc} */
        @Override public Collection<V> values() {
            return values(e(k1, v1));
        }
    }

    /**
     * Map for two entries.
     */
    private static class Map2<K, V> extends Map1<K, V> {
        /** */
        protected K k2;

        /** */
        protected V v2;

        /**
         * Constructs map.
         */
        Map2() {
            // No-op.
        }

        /**
         * Constructs map.
         *
         * @param k1 Key1.
         * @param v1 Value1.
         * @param k2 Key2.
         * @param v2 Value2.
         */
        Map2(K k1, V v1, K k2, V v2) {
            super(k1, v1);

            this.k2 = k2;
            this.v2 = v2;
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return super.size() + (k2 != null ? 1 : 0);
        }

        /** {@inheritDoc} */
        @Override public boolean containsKey(Object k) {
            return super.containsKey(k) || (k2 != null && F.eq(k, k2));
        }

        /** {@inheritDoc} */
        @Override public boolean containsValue(Object v) {
            return super.containsValue(v) || (k2 != null && F.eq(v, v2));
        }

        /** {@inheritDoc} */
        @Override public V get(Object k) {
            V v = super.get(k);

            return v != null ? v : (k2 != null && F.eq(k, k2)) ? v2 : null;
        }

        /**
         * Puts key-value pair into map only if given key is already contained in the map
         * or there are free slots.
         * Note that this implementation of {@link Map#put(Object, Object)} does not match
         * general contract of {@link Map} interface and serves only for internal purposes.
         *
         * @param key Key.
         * @param value Value.
         * @return Previous value associated with given key.
         */
        @Nullable
        @Override public V put(K key, V value) throws NullPointerException {
            V oldVal = get(key);

            if (k1 == null || F.eq(k1, key)) {
                k1 = key;
                v1 = value;
            }
            else if (k2 == null || F.eq(k2, key)) {
                k2 = key;
                v2 = value;
            }

            return oldVal;
        }

        /** {@inheritDoc} */
        @Override public Set<K> keySet() {
            return keySet(k1, k2);
        }

        /** {@inheritDoc} */
        @Override public Set<Entry<K, V>> entrySet() {
            return entrySet(e(k1, v1), e(k2, v2));
        }

        /** {@inheritDoc} */
        @Override public Collection<V> values() {
            return values(e(k1, v1), e(k2, v2));
        }
    }

    /**
     * Map for three entries.
     */
    private static class Map3<K, V> extends Map2<K, V> {
        /** */
        protected K k3;

        /** */
        protected V v3;

        /**
         * Constructs map.
         */
        Map3() {
            // No-op.
        }

        /**
         * Constructs map.
         *
         * @param k1 Key1.
         * @param v1 Value1.
         * @param k2 Key2.
         * @param v2 Value2.
         * @param k3 Key3.
         * @param v3 Value3.
         */
        Map3(K k1, V v1, K k2, V v2, K k3, V v3) {
            super(k1, v1, k2, v2);

            this.k3 = k3;
            this.v3 = v3;
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return super.size() + (k3 != null ? 1 : 0);
        }

        /** {@inheritDoc} */
        @Override public boolean containsKey(Object k) {
            return super.containsKey(k) || (k3 != null && F.eq(k, k3));
        }

        /** {@inheritDoc} */
        @Override public boolean containsValue(Object v) {
            return super.containsValue(v) || (k3 != null && F.eq(v, v3));
        }

        /** {@inheritDoc} */
        @Override public V get(Object k) {
            V v = super.get(k);

            return v != null ? v : (k3 != null && F.eq(k, k3)) ? v3 : null;
        }

        /**
         * Puts key-value pair into map only if given key is already contained in the map
         * or there are free slots.
         * Note that this implementation of {@link Map#put(Object, Object)} does not match
         * general contract of {@link Map} interface and serves only for internal purposes.
         *
         * @param key Key.
         * @param value Value.
         * @return Previous value associated with given key.
         */
        @Override public V put(K key, V value) throws NullPointerException {
            V oldVal = get(key);

            if (k1 == null || F.eq(k1, key)) {
                k1 = key;
                v1 = value;
            }
            else if (k2 == null || F.eq(k2, key)) {
                k2 = key;
                v2 = value;
            }
            else if (k3 == null || F.eq(k3, key)) {
                k3 = key;
                v3 = value;
            }

            return oldVal;
        }

        /** {@inheritDoc} */
        @Override public Set<K> keySet() {
            return keySet(k1, k2, k3);
        }

        /** {@inheritDoc} */
        @Override public Set<Entry<K, V>> entrySet() {
            return entrySet(e(k1, v1), e(k2, v2), e(k3, v3));
        }

        /** {@inheritDoc} */
        @Override public Collection<V> values() {
            return values(e(k1, v1), e(k2, v2), e(k3, v3));
        }
    }

    /**
     * Map for four entries.
     */
    private static class Map4<K, V> extends Map3<K, V> {
        /** */
        protected K k4;

        /** */
        protected V v4;

        /**
         * Constructs map.
         */
        Map4() {
            // No-op.
        }

        /**
         * Constructs map.
         *
         * @param k1 Key1.
         * @param v1 Value1.
         * @param k2 Key2.
         * @param v2 Value2.
         * @param k3 Key3.
         * @param v3 Value3.
         * @param k4 Key4.
         * @param v4 Value4.
         */
        Map4(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
            super(k1, v1, k2, v2, k3, v3);

            this.k4 = k4;
            this.v4 = v4;
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return super.size() + (k4 != null ? 1 : 0);
        }

        /** {@inheritDoc} */
        @Override public boolean containsKey(Object k) {
            return super.containsKey(k) || (k4 != null && F.eq(k, k4));
        }

        /** {@inheritDoc} */
        @Override public boolean containsValue(Object v) {
            return super.containsValue(v) || (k4 != null && F.eq(v, v4));
        }

        /** {@inheritDoc} */
        @Override public V get(Object k) {
            V v = super.get(k);

            return v != null ? v : (k4 != null && F.eq(k, k4)) ? v4 : null;
        }

        /**
         * Puts key-value pair into map only if given key is already contained in the map
         * or there are free slots.
         * Note that this implementation of {@link Map#put(Object, Object)} does not match
         * general contract of {@link Map} interface and serves only for internal purposes.
         *
         * @param key Key.
         * @param value Value.
         * @return Previous value associated with given key.
         */
        @Override public V put(K key, V value) throws NullPointerException {
            V oldVal = get(key);

            if (k1 == null || F.eq(k1, key)) {
                k1 = key;
                v1 = value;
            }
            else if (k2 == null || F.eq(k2, key)) {
                k2 = key;
                v2 = value;
            }
            else if (k3 == null || F.eq(k3, key)) {
                k3 = key;
                v3 = value;
            }
            else if (k4 == null || F.eq(k4, key)) {
                k4 = key;
                v4 = value;
            }

            return oldVal;
        }

        /** {@inheritDoc} */
        @Override public Set<K> keySet() {
            return keySet(k1, k2, k3, k4);
        }

        /** {@inheritDoc} */
        @Override public Set<Entry<K, V>> entrySet() {
            return entrySet(e(k1, v1), e(k2, v2), e(k3, v3), e(k4, v4));
        }

        /** {@inheritDoc} */
        @Override public Collection<V> values() {
            return values(e(k1, v1), e(k2, v2), e(k3, v3), e(k4, v4));
        }
    }

    /**
     * Map for five entries.
     */
    private static class Map5<K, V> extends Map4<K, V> {
        /** */
        private K k5;

        /** */
        private V v5;

        /**
         * Constructs map.
         */
        Map5() {
            // No-op.
        }

        /**
         * Constructs map.
         *
         * @param k1 Key1.
         * @param v1 Value1.
         * @param k2 Key2.
         * @param v2 Value2.
         * @param k3 Key3.
         * @param v3 Value3.
         * @param k4 Key4.
         * @param v4 Value4.
         * @param k5 Key5.
         * @param v5 Value5.
         */
        Map5(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
            super(k1, v1, k2, v2, k3, v3, k4, v4);

            this.k5 = k5;
            this.v5 = v5;
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return super.size() + (k5 != null ? 1 : 0);
        }

        /** {@inheritDoc} */
        @Override public boolean containsKey(Object k) {
            return super.containsKey(k) || (k5 != null && F.eq(k, k5));
        }

        /** {@inheritDoc} */
        @Override public boolean containsValue(Object v) {
            return super.containsValue(v) || (k5 != null && F.eq(v, v5));
        }

        /** {@inheritDoc} */
        @Override public V get(Object k) {
            V v = super.get(k);

            return v != null ? v : (k5 != null && F.eq(k, k5)) ? v5 : null;
        }

        /**
         * Puts key-value pair into map only if given key is already contained in the map
         * or there are free slots.
         * Note that this implementation of {@link Map#put(Object, Object)} does not match
         * general contract of {@link Map} interface and serves only for internal purposes.
         *
         * @param key Key.
         * @param value Value.
         * @return Previous value associated with given key.
         */
        @Override public V put(K key, V value) throws NullPointerException {
            V oldVal = get(key);

            if (k1 == null || F.eq(k1, key)) {
                k1 = key;
                v1 = value;
            }
            else if (k2 == null || F.eq(k2, key)) {
                k2 = key;
                v2 = value;
            }
            else if (k3 == null || F.eq(k3, key)) {
                k3 = key;
                v3 = value;
            }
            else if (k4 == null || F.eq(k4, key)) {
                k4 = key;
                v4 = value;
            }
            else if (k5 == null || F.eq(k5, key)) {
                k5 = key;
                v5 = value;
            }

            return oldVal;
        }

        /** {@inheritDoc} */
        @Override public Set<K> keySet() {
            return keySet(k1, k2, k3, k4, k5);
        }

        /** {@inheritDoc} */
        @Override public Set<Entry<K, V>> entrySet() {
            return entrySet(e(k1, v1), e(k2, v2), e(k3, v3), e(k4, v4), e(k5, v5));
        }

        /** {@inheritDoc} */
        @Override public Collection<V> values() {
            return values(e(k1, v1), e(k2, v2), e(k3, v3), e(k4, v4), e(k5, v5));
        }
    }
}