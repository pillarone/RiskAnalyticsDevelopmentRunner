// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.jetbrains.annotations.*;

/**
 * Manager of data structures.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheDataStructuresManager<K, V> extends GridCacheManager<K, V> {
    /**
     * Will get a sequence from cache and create one if it's not cached.
     *
     * @param name Sequence name.
     * @param initVal Initial value for sequence. If sequence already cached, {@code initVal} will be ignored.
     * @param persistent Persistent flag.
     * @param create  If {@code true} sequence will be created in case it doesn't in cache.
     * @return Sequence.
     * @throws GridException If loading failed.
     */
    public abstract GridCacheAtomicSequence sequence(String name, long initVal, boolean persistent, boolean create)
        throws GridException;

    /**
     * Remove sequence from cache.
     *
     * @param name Sequence name.
     * @return Method returns {@code true} if sequence has been removed and {@code false} if it's not cached.
     * @throws GridException If removing failed.
     */
    public abstract boolean removeSequence(String name) throws GridException;

    /**
     * Will get an atomic long from cache and create one if it's not cached.
     *
     * @param name Name of atomic long.
     * @param initVal Initial value for atomic long. If atomic long already cached, {@code initVal}
     *        will be ignored.
     * @param persistent Persistent flag.
     * @param create If {@code true} atomic long will be created in case it doesn't in cache.
     * @return Atomic long.
     * @throws GridException If loading failed.
     */
    public abstract GridCacheAtomicLong atomicLong(String name, long initVal, boolean persistent, boolean create)
        throws GridException;

    /**
     * Remove atomic long from cache.
     *
     * @param name Atomic long name.
     * @return Method returns {@code true} if atomic long has been removed and {@code false} if it's not cached.
     * @throws GridException If removing failed.
     */
    public abstract boolean removeAtomicLong(String name) throws GridException;

    /**
     * Will get a atomic reference from cache and create one if it's not cached.
     *
     * @param name Name of atomic reference.
     * @param initVal Initial value for atomic reference. If atomic reference already cached, {@code initVal}
     *        will be ignored.
     * @param persistent Persistent flag.
     * @param create If {@code true} atomic reference will be created in case it doesn't in cache.
     * @return Atomic reference.
     * @throws GridException If loading failed.
     */
    public abstract <T> GridCacheAtomicReference<T> atomicReference(String name, @Nullable T initVal,
        boolean persistent, boolean create) throws GridException;

    /**
     * Remove atomic reference from cache.
     *
     * @param name Atomic reference name.
     * @return Method returns {@code true} if atomic reference has been removed and {@code false} if it's not cached.
     * @throws GridException If removing failed.
     */
    public abstract boolean removeAtomicReference(String name) throws GridException;

    /**
     * Will get a atomic stamped from cache and create one if it's not cached.
     *
     * @param name Name of atomic stamped.
     * @param initVal Initial value for atomic stamped. If atomic stamped already cached, {@code initVal}
     *        will be ignored.
     * @param initStamp Initial stamp for atomic stamped. If atomic stamped already cached, {@code initStamp}
     *        will be ignored.
     * @param create If {@code true} atomic stamped will be created in case it doesn't in cache.
     * @return Atomic stamped.
     * @throws GridException If loading failed.
     */
    public abstract <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name, @Nullable T initVal,
        @Nullable S initStamp, boolean create) throws GridException;

    /**
     * Remove atomic stamped from cache.
     *
     * @param name Atomic stamped name.
     * @return Method returns {@code true} if atomic stamped has been removed and {@code false} if it's not cached.
     * @throws GridException If removing failed.
     */
    public abstract boolean removeAtomicStamped(String name) throws GridException;

    /**
     * Will get a queue from cache and create one if it's not cached.
     *
     * @param name Name of queue.
     * @param type Type of queue.
     * @param capacity Max size of queue.
     * @param collocated Collocation flag.
     * @param create If {@code true} queue will be created in case it doesn't in cache.
     * @return Instance of queue.
     * @throws GridException If failed.
     */
    public abstract <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int capacity,
        boolean collocated, boolean create) throws GridException;

    /**
     * Remove queue from cache.
     *
     * @param name Queue name.
     * @return Method returns {@code true} if queue has been removed and {@code false} if it's not cached.
     * @throws GridException If removing failed.
     */
    public abstract boolean removeQueue(String name) throws GridException;

    /**
     * Transaction committed callback for transaction manager.
     *
     * @param tx Committed transaction.
     */
    public abstract void onTxCommitted(GridCacheTxEx<K, V> tx);

    /**
     *
     * @return Cache of annotated by {@link GridCacheQueuePriority} methods and fields.
     */
    public abstract GridCacheAnnotationHelper<GridCacheQueuePriority> priorityAnnotations();
}
