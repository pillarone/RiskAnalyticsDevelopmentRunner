// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Monadic map. This class can be optionally created with {@code null} implementation in
 * which case it will act as an empty map.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMapOpt<K, V> extends GridSerializableMap<K, V> {
    private final Map<K, V> impl;

    /**
     * Creates monadic map with optional implementation.
     *
     * @param impl If {@code null} - this will act as an empty map, otherwise
     *      it will use given implementation.
     */
    public GridMapOpt(@Nullable Map<K, V> impl) {
        this.impl = impl == null ? Collections.<K, V>emptyMap() : impl;
    }

    /**
     * Calls {@code this(null);}.
     */
    public GridMapOpt() {
        this(null);
    }

    /** {@inheritDoc} */
    @Override public int size() {
        assert impl != null;

        return impl.size();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        assert impl != null;

        return impl.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(Object key) {
        assert impl != null;

        return impl.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public boolean containsValue(Object val) {
        assert impl != null;

        return impl.containsValue(val);
    }

    /** {@inheritDoc} */
    @Override public V get(Object key) {
        assert impl != null;

        return impl.get(key);
    }

    /** {@inheritDoc} */
    @Override public V put(K key, V val) {
        assert impl != null;

        return impl.put(key, val);
    }

    /** {@inheritDoc} */
    @Override public V remove(Object key) {
        assert impl != null;

        return impl.remove(key);
    }

    /** {@inheritDoc} */
    @Override public void putAll(Map<? extends K, ? extends V> m) {
        assert impl != null;

        impl.putAll(m);
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        assert impl != null;

        impl.clear();
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet() {
        assert impl != null;

        return impl.keySet();
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values() {
        assert impl != null;

        return impl.values();
    }

    /** {@inheritDoc} */
    @Override public Set<Entry<K, V>> entrySet() {
        assert impl != null;

        return impl.entrySet();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return impl.toString();
    }
}
