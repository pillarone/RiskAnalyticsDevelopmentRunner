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
 * Monadic iterable. This class can be optionally created with {@code null} implementation in
 * which case it will return an empty iterator.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIterableOpt<T> implements GridSerializableIterable<T> {
    /** */
    private final Iterable<T> impl;

    /**
     * Creates monadic iterable with optional implementation.
     *
     * @param impl If {@code null} - this iterable will return an empty
     *      iterator, otherwise it will use given implementation.
     */
    public GridIterableOpt(@Nullable Iterable<T> impl) {
        this.impl = impl == null ? Collections.<T>emptyList() : impl;
    }

    /**
     * Calls {@code this(null);}.
     */
    public GridIterableOpt() {
        this(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override public Iterator<T> iterator() {
        assert impl != null;

        return impl.iterator();
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
