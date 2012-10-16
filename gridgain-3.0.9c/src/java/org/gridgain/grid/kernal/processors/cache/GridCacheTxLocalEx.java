// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;

import java.util.*;

/**
 * Local transaction API.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheTxLocalEx<K, V> extends GridCacheTxEx<K, V> {
    /**
     * @return Minimum version involved in transaction.
     */
    public GridCacheVersion minVersion();
    
    /**
     * @return Future for this transaction.
     */
    public GridFuture<GridCacheTx> future();

    /**
     * @return Finish future.
     */
    public GridFuture<GridCacheTx> finishFuture();

    /**
     * @throws GridException If commit failed.
     */
    public void userCommit() throws GridException;

    /**
     * @return {@code True} if all entries have been committed as a result of this invocation.
     * @throws GridException If commit failed.
     */
    public boolean userCommitEC() throws GridException;

    /**
     * @throws GridException If rollback failed.
     */
    public void userRollback() throws GridException;

    /**
     * @param filter Entry filter.
     * @param key Key
     * @return Value
     * @throws GridException If get failed.
     */
    public V get(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     *
     * @param keys Keys to retrieve.
     * @param filter Entry filter.
     * @return Map of retrieved key-value pairs.
     * @throws GridException If get failed.
     */
    public Map<K, V> getAll(Collection<? extends K> keys, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException;

    /**
     * @param key Key.
     * @param filter Entry filter.
     * @return Completed value future.
     */
    public GridFuture<V> getAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @param keys Keys to get.
     * @param filter Entry filter.
     * @return Future for this get.
     */
    public GridFuture<Map<K, V>> getAllAsync(Collection<? extends K> keys, 
        GridPredicate<? super GridCacheEntry<K, V>>[] filter);


    /**
     *
     * @param key Key.
     * @param val Value.
     * @param filter Filter.
     * @return Old value.
     * @throws GridException If put failed.
     */
    public V put(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     *
     * @param key Key.
     * @param val Value.
     * @param filter Filter.
     * @return Old value.
     * @throws GridException If put failed.
     */
    public boolean putx(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * @param map Map to put.
     * @param filter Filter.
     * @return Old value for the first key in the map.
     * @throws GridException If put failed.
     */
    public GridCacheReturn<V> putAll(Map<? extends K, ? extends V> map,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * @param key Key.
     * @param val Value.
     * @param filter Filter.
     * @return Value future.
     */
    public GridFuture<V> putAsync(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter);


    /**
     * @param key Key.
     * @param val Value.
     * @param filter Filter.
     * @return Value future.
     */
    public GridFuture<Boolean> putxAsync(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     *
     * @param key Key.
     * @param filter Filter.
     * @return Value.
     * @throws GridException If remove failed.
     */
    public V remove(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     *
     * @param key Key.
     * @param filter Filter.
     * @return Value.
     * @throws GridException If remove failed.
     */
    public boolean removex(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * @param keys Keys to remove.
     * @param filter Filter.
     * @return Value of first removed key.
     * @throws GridException If remove failed.
     */
    public GridCacheReturn<V> removeAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * Asynchronous remove.
     *
     * @param key Key to remove.
     * @param filter Filter.
     * @return Removed value.
     */
    public GridFuture<V> removeAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * Asynchronous remove.
     *
     * @param key Key to remove.
     * @param filter Filter.
     * @return Removed value.
     */
    public GridFuture<Boolean> removexAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @param map Map to put.
     * @param retval Flag indicating whether a value should be returned.
     * @param filter Filter.
     * @return Future all the put operation which will return value as if
     *      {@link #put(Object, Object, GridPredicate[])} was called.
     */
    public GridFuture<GridCacheReturn<V>> putAllAsync(Map<? extends K, ? extends V> map,
        boolean retval, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @param keys Keys to remove.
     * @param retval Flag indicating whether a value should be returned.
     * @param implicit Allows to externally control how transaction handles implicit flags. 
     * @param filter Filter.
     * @return Future for asynchronous remove.
     */
    public GridFuture<GridCacheReturn<V>> removeAllAsync(Collection<? extends K> keys, boolean implicit,
        boolean retval, GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * Finishes transaction (either commit or rollback).
     *
     * @param commit {@code True} if commit, {@code false} if rollback.
     * @throws GridException If finish failed.
     */
    public void finish(boolean commit) throws GridException;

    /**
     * @param commit {@code True} if commit.
     * @return {@code True} if all entries have been committed.
     * @throws GridException If failed.
     */
    public boolean finishEC(boolean commit) throws GridException;

    /**
     * @param async if {@code True}, then loading will happen in a separate thread.
     * @param keys Keys.
     * @param closure Closure.
     * @return Future with {@code True} value if loading took place.
     */
    public GridFuture<Boolean> loadMissing(boolean async, Collection<? extends K> keys, GridInClosure2<K, V> closure);
}
