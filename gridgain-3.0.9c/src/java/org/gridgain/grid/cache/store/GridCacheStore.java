// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.store;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.hibernate.*;
import org.gridgain.grid.cache.store.jdbc.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * API for cache persistent storage for read-through and write-through behavior.
 * Persistent store is configured via {@link GridCacheConfiguration#getStore()}
 * configuration property. If not provided, values will be only kept in cache memory
 * or swap storage without ever being persisted to a persistent storage.
 * <p>
 * {@link GridCacheStoreAdapter} provides default implementation for bulk operations,
 * such as {@link #loadAll(String, GridCacheTx, Collection, GridInClosure2)},
 * {@link #putAll(String, GridCacheTx, Map)}, and {@link #removeAll(String, GridCacheTx, Collection)}
 * by sequentially calling corresponding {@link #load(String, GridCacheTx, Object)},
 * {@link #put(String, GridCacheTx, Object, Object)}, and {@link #remove(String, GridCacheTx, Object)}
 * operations. Use this adapter whenever such behaviour is acceptable. However
 * in many cases it maybe more preferable to take advantage of database batch update
 * functionality, and therefore default adapter implementation may not be the best option.
 * <p>
 * Provided implementations may be used for test purposes:
 * <ul>
 *     <li>{@link GridCacheHibernateBlobStore}</li>
 *     <li>{@link GridCacheJdbcBlobStore}</li>
 * </ul>
 * <p>
 * All transactional operations of this API are provided with ongoing {@link GridCacheTx},
 * if any. As transaction is {@link GridMetadataAware}, you can attach any metadata to
 * it, e.g. to recognize if several operations belong to the same transaction or not.
 * Here is an example of how attach a JDBC connection as transaction metadata:
 * <pre name="code" class="java">
 * Connection conn = tx.meta("some.name");
 *
 * if (conn == null) {
 *     conn = ...; // Get JDBC connection.
 *
 *     // Store connection in transaction metadata, so it can be accessed
 *     // for other operations on the same transaction.
 *     tx.addMeta("some.name", conn);
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheStore<K, V> {
    /**
     * Loads value for the key from underlying persistent storage.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param key Key to load.
     * @return Loaded value or {@code null} if value was not found.
     * @throws GridException If load failed.
     */
    @Nullable public V load(@Nullable String cacheName, @Nullable GridCacheTx tx, K key) throws GridException;

    /**
     * Loads all values from underlying persistent storage. Note that keys are not
     * passed, so it is up to implementation to figure out what to load. This method
     * is called whenever {@link GridCache#loadCache(GridPredicate2 , long, Object...)}
     * method is invoked which is usually to preload the cache from persistent storage.
     * <p>
     * This method is optional, and cache implementation does not depend on this
     * method to do anything. Default implementation of this method in
     * {@link GridCacheStoreAdapter} does nothing.
     * <p>
     * For every loaded value method {@link GridInClosure2#apply(Object, Object)}
     * should be called on the passed in closure. The closure will then make sure
     * that the loaded value is stored in cache.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param c Closure for loaded values.
     * @param args Arguments passes into
     *      {@link GridCache#loadCache(org.gridgain.grid.lang.GridPredicate2 , long, Object...)} method.
     * @throws GridException If loading failed.
     */
    public void loadAll(@Nullable String cacheName, GridInClosure2<K, V> c, @Nullable Object... args)
        throws GridException;

    /**
     * Loads all values for given keys and passes every value to the provided closure.
     * <p>
     * For every loaded value method {@link GridInClosure#apply(Object)} should be called on
     * the passed in closure. The closure will then make sure that the loaded value is stored
     * in cache.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param keys Collection of keys to load.
     * @param c Closure to call for every loaded element.
     * @throws GridException If load failed.
     */
    public void loadAll(@Nullable String cacheName, @Nullable GridCacheTx tx, @Nullable Collection<? extends K> keys,
        GridInClosure2<K, V> c) throws GridException;

    /**
     * Stores a given value in persistent storage. Note that cache transaction is implicitly created
     * even for a single put, so the passed in transaction can never be {@code null}.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param key Key to put.
     * @param val Value to put.
     * @throws GridException If put failed.
     */
    public void put(@Nullable String cacheName, GridCacheTx tx, K key, @Nullable V val) throws GridException;

    /**
     * Stores given key value pairs in persistent storage. Note that cache transaction is implicitly created
     * even for a single put, so the passed in transaction can never be {@code null}.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param map Values to store.
     * @throws GridException If store failed.
     */
    public void putAll(@Nullable String cacheName, GridCacheTx tx, @Nullable Map<? extends K, ? extends V> map)
        throws GridException;

    /**
     * Removes the value identified by given key from persistent storage. Note that cache transaction is
     * implicitly created even for a single put, so the passed in transaction can never be {@code null}.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param key Key to remove.
     * @throws GridException If remove failed.
     */
    public void remove(@Nullable String cacheName, GridCacheTx tx, K key) throws GridException;

    /**
     * Removes all vales identified by given keys from persistent storage. Note that cache transaction
     * is implicitly created even for a single put, so the passed in transaction can never be {@code null}.
     *
     * @param cacheName Cache name ({@code null} for default no-name cache).
     * @param tx Cache transaction.
     * @param keys Keys to remove.
     * @throws GridException If remove failed.
     */
    public void removeAll(@Nullable String cacheName, GridCacheTx tx, @Nullable Collection<? extends K> keys)
        throws GridException;

    /**
     * Tells store to commit or rollback a transaction depending on the value of the {@code 'commit'}
     * parameter.
     * <p>
     * Note that if explicit transactions are not used in code, then it is possible
     * to commit or rollback transactions directly in {@code 'put(..)'}, or {@code 'remove(..)'}
     * methods. In that case, this method should be left empty ({@link GridCacheStoreAdapter} provides
     * empty implementation of this method).
     *
     * @param cacheName Name of the cache ({@code null} for default no-name cache).
     * @param tx Cache transaction being ended.
     * @param commit {@code True} if transaction should commit, {@code false} for rollback.
     * @throws GridException If commit or rollback failed. Note that commit failure in some cases
     *      may bring cache transaction into {@link GridCacheTxState#UNKNOWN} which will
     *      consequently cause all transacted entries to be invalidated.
     */
    public void txEnd(@Nullable String cacheName, GridCacheTx tx, boolean commit) throws GridException;
}
