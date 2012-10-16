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
 * Monadic collection. This class can be optionally created with {@code null} implementation in
 * which case it will act as an empty collection.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCollectionOpt<T> extends GridSerializableCollection<T> {
    /** */
    private final Collection<T> impl;

    /**
     * Creates monadic collection with optional implementation.
     *
     * @param impl If {@code null} - this will act as an empty collection, otherwise
     *      it will use given implementation.
     */
    public GridCollectionOpt(@Nullable Collection<T> impl) {
        this.impl = impl == null ? Collections.<T>emptyList() : impl;
    }

    /**
     * Calls {@code this(null);}.
     */
    public GridCollectionOpt() {
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
    @Override public boolean contains(Object o) {
        assert impl != null;

        return impl.contains(o);
    }

    /** {@inheritDoc} */
    @Override public Iterator<T> iterator() {
        assert impl != null;

        return impl.iterator();
    }

    /** {@inheritDoc} */
    @Override public Object[] toArray() {
        assert impl != null;

        return impl.toArray();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SuspiciousToArrayCall"})
    @Override public <T> T[] toArray(T[] a) {
        assert impl != null;

        return impl.toArray(a);
    }

    /** {@inheritDoc} */
    @Override public boolean add(T t) {
        assert impl != null;

        return impl.add(t);
    }

    /** {@inheritDoc} */
    @Override public boolean remove(Object o) {
        assert impl != null;

        return impl.remove(o);
    }

    /** {@inheritDoc} */
    @Override public boolean containsAll(Collection<?> c) {
        assert impl != null;

        return impl.containsAll(c);
    }

    /** {@inheritDoc} */
    @Override public boolean addAll(Collection<? extends T> c) {
        assert impl != null;

        return impl.addAll(c);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAll(Collection<?> c) {
        assert impl != null;

        return impl.removeAll(c);
    }

    /** {@inheritDoc} */
    @Override public boolean retainAll(Collection<?> c) {
        assert impl != null;

        return impl.retainAll(c);
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        assert impl != null;

        impl.clear();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        assert impl != null;

        return impl.equals(o);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        assert impl != null;

        return impl.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return impl.toString();
    }    
}
