// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.typedef.internal.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Concurrent set implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridConcurrentHashSet<E> extends GridSetWrapper<E> {
    /**
     * Creates a new, empty set with a default initial capacity,
     * load factor, and concurrencyLevel.
     */
    public GridConcurrentHashSet() {
        super(new ConcurrentHashMap<E, Object>());
    }

    /**
     * Creates a new, empty set with the specified initial
     * capacity, and with default load factor and concurrencyLevel.
     *
     * @param initCap The initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     *      elements is negative.
     */
    public GridConcurrentHashSet(int initCap) {
        super(new ConcurrentHashMap<E, Object>(initCap));
    }

    /**
     * Creates a new, empty set with the specified initial
     * capacity, load factor, and concurrency level.
     *
     * @param initCap The initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @param loadFactor The load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @param conLevel The estimated number of concurrently
     *      updating threads. The implementation performs internal sizing
     *      to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     *      negative or the load factor or concurrency level are
     *      non-positive.
     */
    public GridConcurrentHashSet(int initCap, float loadFactor, int conLevel) {
        super(new ConcurrentHashMap<E, Object>(initCap, loadFactor, conLevel));
    }

    /**
     * Creates a new set with the same elements as the given collection. The
     * collection is created with a capacity of twice the number of mappings in
     * the given map or 11 (whichever is greater), and a default load factor
     * and concurrencyLevel.
     *
     * @param c Collection to add.
     */
    public GridConcurrentHashSet(Collection<E> c) {
        super(new ConcurrentHashMap<E, Object>(c.size()));

        addAll(c);
    }

    /** {@inheritDoc} */
    @Override public boolean add(E e) {
        ConcurrentMap<E, Object> m = (ConcurrentMap<E, Object>)map;

        return m.putIfAbsent(e, VAL) == null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridConcurrentHashSet.class, this, "elements", map().keySet());
    }
}
