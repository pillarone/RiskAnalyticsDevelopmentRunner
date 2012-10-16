// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This interface provides a rich API for working with individual cache entries. It
 * includes the following main functionality:
 * <ul>
 * <li>
 *  Various {@code 'get(..)'} methods to synchronously or asynchronously get values from cache.
 *  All {@code 'get(..)'} methods are transactional and will participate in an ongoing transaction
 *  if there is one.
 * </li>
 * <li>
 *  Various {@code 'set(..)'}, {@code 'setIfAbsent(..)'}, and {@code 'replace(..)'} methods to
 *  synchronously or asynchronously put single or multiple entries into cache.
 *  All these methods are transactional and will participate in an ongoing transaction
 *  if there is one.
 * </li>
 * <li>
 *  Various {@code 'remove(..)'} methods to synchronously or asynchronously remove single or multiple keys
 *  from cache. All {@code 'remove(..)'} methods are transactional and will participate in an ongoing transaction
 *  if there is one.
 * </li>
 * <li>
 *  Various {@code 'invalidate(..)'} methods to set cached values to {@code null}.
 * <li>
 * <li>
 *  Various {@code 'isLocked(..)'} methods to check on distributed locks on a single or multiple keys
 *  in cache. All locking methods are not transactional and will not enlist keys into ongoing transaction,
 *  if any.
 * </li>
 * <li>
 *  Various {@code 'peek(..)'} methods to peek at values in global or transactional memory, swap
 *  storage, or persistent storage.
 * </li>
 * <li>
 *  Various {@code 'reload(..)'} methods to reload latest values from persistent storage.
 * </li>
 * <li>
 *  Method {@link #evict(GridPredicate[])} to evict elements from cache, and optionally store
 *  them in underlying swap storage for later access. All {@code 'evict(..)'} methods are not
 *  transactional and will not enlist evicted keys into ongoing transaction, if any.
 * </li>
 * <li>
 *  Methods for {@link #timeToLive(long)} to change or lookup entry's time to live. 
 * </ul>
 * <h1 class="header">Extended Put And Remove Methods</h1>
 * All methods that end with {@code 'x'} provide the same functionality as their sibling
 * methods that don't end with {@code 'x'}, however instead of returning a previous value they
 * return a {@code boolean} flag indicating whether operation succeeded or not. Returning
 * a previous value may involve a network trip or a persistent store lookup and should be
 * avoided whenever not needed.
 * <h1 class="header">Predicate Filters</h1>
 * All filters passed into methods on this API are checked <b>atomically</b>. In other words the
 * value returned by the methods is guaranteed to be consistent with the filters passed in.
 * <h1 class="header">Transactions</h1>
 * Cache API supports distributed transactions. All {@code 'get(..)'}, {@code 'put(..)'}, {@code 'replace(..)'},
 * and {@code 'remove(..)'} operations are transactional and will participate in an ongoing transaction,
 * if any. Other methods like {@code 'peek(..)'} may
 * be transaction-aware, i.e. check in-transaction entries first, but will not affect the current
 * state of transaction. See {@link GridCacheTx} documentation for more information
 * about transactions.
 * <h1 class="header">Null Keys or Values</h1>
 * Neither {@code null} keys or values are allowed to be stored in cache. If a {@code null} value
 * happens to be in cache (e.g. after invalidation or remove), then cache will treat this case
 * as there is no value at all.
 * <p>
 * All API method with {@link Nullable @Nullable} annotation on method parameters
 * or return values either accept or may return a {@code null} value. Parameters that do not
 * have this annotation cannot be {@code null} and invoking method with a {@code null} parameter
 * in this case will result in {@link NullPointerException}.
 * <h1 class="header">Peer Class Loading</h1>
 * All classes passed into cache API will be automatically deployed to any participating grid nodes.
 * No explicit deployment step is required.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <K> Key type.
 * @param <V> Value type.
 */
public interface GridCacheEntry<K, V> extends Map.Entry<K, V>, GridMetadataAware {
    /**
     * @return Flags for this entry.
     */
    public Set<GridCacheFlag> flags();

    /**
     * Cache projection to which this entry belongs. Note that entry and its
     * parent projections have same flags and filters.
     *
     * @return Cache projection for the cache to which this entry belongs.
     */
    public GridCacheProjection<K, V> parent();

    /**
     * Transaction-aware check if cache has in-memory cached value. This method
     * is based on using default {@link GridCachePeekMode#SMART SMART} peek mode
     * and is equivalent to {@code 'hasValue(SMART)'} or {@code 'peek(SMART) != null'}
     * expression.
     * <p>
     * This method will not attempt to load entry from any persistent store or remote node.
     *
     * @param filter Filter that needs to pass prior to returning the value. Note that
     *      checking filter and returning value will be done as one <b>atomic</b> operation.
     * @return Value currently present in cache.
     */
    public boolean hasValue(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Check if cache has value for the entry. This method is equivalent to
     * {@code 'peek(mode, filter) != null'} expression.
     * <p>
     * Note that if mode is not provided (null) then
     * {@link GridCachePeekMode#SMART SMART} mode is used by default.
     *
     * @param mode Peek mode.
     *      See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @return {@code True} if filter passed and entry has a value either within transaction or
     *      globally, depending on {@code 'tx'} parameter.
     * @throws GridException If peek operation, such as swap or database read, failed.
     */
    public boolean hasValue(@Nullable GridCachePeekMode mode) throws GridException;

    /**
     * This method has the same semantic as {@link GridCacheProjection#peek(Object)} method.
     *
     * @return See {@link GridCacheProjection#peek(Object)}.
     */
    @Nullable public V peek();

    /**
     * This method has the same semantic as {@link GridCacheProjection#peek(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#peek(Object)}.
     * @return See {@link GridCacheProjection#peek(Object)}.
     */
    @Nullable public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as {@link GridCacheProjection#peek(Object, GridCachePeekMode...)} method.
     *
     * @param mode See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @return See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @throws GridException See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     */
    @Nullable public V peek(@Nullable GridCachePeekMode mode) throws GridException;

    /**
     * This method has the same semantic as {@link GridCacheProjection#peek(Object, GridCachePeekMode...)} method.
     *
     * @param modes See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @return See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @throws GridException See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     */
    @Nullable public V peek(@Nullable GridCachePeekMode... modes) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#peek(Object, Collection)} method.
     *
     * @param modes See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @return See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     * @throws GridException See {@link GridCacheProjection#peek(Object, GridCachePeekMode...)}.
     */
    @Nullable public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException;

    /**
     * This method has the same semantic as {@link GridCacheProjection#peekAsync(Object, GridCachePeekMode...)}
     * method.
     *
     * @param modes See {@link GridCacheProjection#peekAsync(Object, GridCachePeekMode...)}.
     * @return See {@link GridCacheProjection#peekAsync(Object, GridCachePeekMode...)}.
     */
    @Nullable public GridFuture<V> peekAsync(@Nullable GridCachePeekMode... modes);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#peekAsync(Object, Collection)} method.
     *
     * @param modes See {@link GridCacheProjection#peekAsync(Object, GridCachePeekMode...)}.
     * @return See {@link GridCacheProjection#peekAsync(Object, GridCachePeekMode...)}.
     */
    @Nullable public GridFuture<V> peekAsync(@Nullable Collection<GridCachePeekMode> modes);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#reload(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#reload(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#reload(Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#reload(Object, GridPredicate[])}.
     */
    @Nullable public V reload(GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#reloadAsync(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#reloadAsync(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#reloadAsync(Object, GridPredicate[])}.
     */
    public GridFuture<V> reloadAsync(GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#isLocked(Object)} method.
     *
     * @return See {@link GridCacheProjection#isLocked(Object)}.
     */
    public boolean isLocked();

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#isLockedByThread(Object)} method.
     *
     * @return See {@link GridCacheProjection#isLockedByThread(Object)}.
     */
    public boolean isLockedByThread();

    /**
     * Gets current version of this cache entry.
     *
     * @return Version of this cache entry.
     */
    public Object version();

    /**
     * Gets expiration time for this entry.
     *
     * @return Absolute time when this value expires.
     */
    public long expirationTime();

    /**
     * Gets time to live, i.e. maximum life time, of this entry in milliseconds.
     *
     * @return Time to live value for this entry.
     */
    public long timeToLive();

    /**
     * Sets time to live, i.e. maximum life time, of this entry in milliseconds.
     *
     * @param ttl Time to live value for this entry.
     */
    public void timeToLive(long ttl);

    /**
     * Metrics containing various statistics about this entry.
     * 
     * @return Cache entry metrics.
     */
    public GridCacheMetrics metrics();

    /**
     * Gets the flag indicating current node's primary ownership for this entry.
     *
     * @return {@code true} if current grid node is the primary owner for this entry.
     */
    public boolean primary();

    /**
     * Gets parent grid projection for this entry.
     *
     * @return Parent projection for this entry.
     * @see GridCacheProjection
     */
    public GridProjection gridProjection();

    /**
     * This method has the same semantic as {@link #get(GridPredicate[])} method, however it
     * wraps {@link GridException} into {@link GridRuntimeException} if failed in order to
     * comply with {@link java.util.Map.Entry} interface.
     *
     * @return See {@link #get(GridPredicate[])}
     */
    @Nullable @Override public V getValue();

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#get(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#get(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#get(Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#get(Object, GridPredicate[])}.
     */
    @Nullable public V get(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#getAsync(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#getAsync(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#getAsync(Object, GridPredicate[])}.
     */
    public GridFuture<V> getAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as {@link #set(Object, GridPredicate[])} method, however it
     * wraps {@link GridException} into {@link GridRuntimeException} if failed in order to
     * comply with {@link java.util.Map.Entry} interface.
     *
     * @return See {@link #set(Object, GridPredicate[])}
     */
    @Nullable @Override public V setValue(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#put(Object, Object, GridPredicate[])} method.
     *
     * @param val See {@link GridCacheProjection#put(Object, Object, GridPredicate[])}
     * @param filter See {@link GridCacheProjection#put(Object, Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#put(Object, Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#put(Object, Object, GridPredicate[])}.
     */
    @Nullable public V set(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putAsync(Object, Object, GridPredicate[])} method.
     *
     * @param val See {@link GridCacheProjection#putAsync(Object, Object, GridPredicate[])}
     * @param filter See {@link GridCacheProjection#putAsync(Object, Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#putAsync(Object, Object, GridPredicate[])}.
     */
    public GridFuture<V> setAsync(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putIfAbsent(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#putIfAbsent(Object, Object)}
     * @return See {@link GridCacheProjection#putIfAbsent(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#putIfAbsent(Object, Object)}.
     */
    @Nullable public V setIfAbsent(V val) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putIfAbsentAsync(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#putIfAbsentAsync(Object, Object)}
     * @return See {@link GridCacheProjection#putIfAbsentAsync(Object, Object)}.
     */
    public GridFuture<V> setIfAbsentAsync(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putx(Object, Object, GridPredicate[])} method.
     *
     * @param val See {@link GridCacheProjection#putx(Object, Object, GridPredicate[])}
     * @param filter See {@link GridCacheProjection#putx(Object, Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#putx(Object, Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#putx(Object, Object, GridPredicate[])}.
     */
    public boolean setx(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putxAsync(Object, Object, GridPredicate[])} method.
     *
     * @param val See {@link GridCacheProjection#putxAsync(Object, Object, GridPredicate[])}
     * @param filter See {@link GridCacheProjection#putxAsync(Object, Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#putxAsync(Object, Object, GridPredicate[])}.
     */
    public GridFuture<Boolean> setxAsync(V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putxIfAbsent(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#putxIfAbsent(Object, Object)}
     * @return See {@link GridCacheProjection#putxIfAbsent(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#putxIfAbsent(Object, Object)}.
     */
    public boolean setxIfAbsent(@Nullable V val) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#putxIfAbsentAsync(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#putxIfAbsentAsync(Object, Object)}
     * @return See {@link GridCacheProjection#putxIfAbsentAsync(Object, Object)}.
     */
    public GridFuture<Boolean> setxIfAbsentAsync(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replace(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#replace(Object, Object)}
     * @return See {@link GridCacheProjection#replace(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#replace(Object, Object)}.
     */
    @Nullable public V replace(V val) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replaceAsync(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#replaceAsync(Object, Object)}
     * @return See {@link GridCacheProjection#replaceAsync(Object, Object)}.
     */
    public GridFuture<V> replaceAsync(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replacex(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#replacex(Object, Object)}
     * @return See {@link GridCacheProjection#replacex(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#replacex(Object, Object)}.
     */
    public boolean replacex(V val) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replacexAsync(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#replacexAsync(Object, Object)}
     * @return See {@link GridCacheProjection#replacexAsync(Object, Object)}.
     */
    public GridFuture<Boolean> replacexAsync(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replace(Object, Object, Object)} method.
     *
     * @param oldVal See {@link GridCacheProjection#replace(Object, Object, Object)}
     * @param newVal See {@link GridCacheProjection#replace(Object, Object, Object)}
     * @return See {@link GridCacheProjection#replace(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#replace(Object, Object)}.
     */
    public boolean replace(V oldVal, V newVal) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#replaceAsync(Object, Object, Object)} method.
     *
     * @param oldVal See {@link GridCacheProjection#replaceAsync(Object, Object, Object)}
     * @param newVal See {@link GridCacheProjection#replaceAsync(Object, Object, Object)}
     * @return See {@link GridCacheProjection#replaceAsync(Object, Object)}.
     */
    public GridFuture<Boolean> replaceAsync(V oldVal, V newVal);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#remove(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#remove(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#remove(Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#remove(Object, GridPredicate[])}.
     */
    @Nullable public V remove(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#removeAsync(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#removeAsync(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#removeAsync(Object, GridPredicate[])}.
     */
    public GridFuture<V> removeAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#removex(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#removex(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#removex(Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#removex(Object, GridPredicate[])}.
     */
    public boolean removex(@Nullable GridPredicate<GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#removexAsync(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#removexAsync(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#removexAsync(Object, GridPredicate[])}.
     */
    public GridFuture<Boolean> removexAsync(@Nullable GridPredicate<GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#remove(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#remove(Object, Object)}.
     * @return See {@link GridCacheProjection#remove(Object, Object)}.
     * @throws GridException See {@link GridCacheProjection#remove(Object, Object)}.
     */
    public boolean remove(V val) throws GridException;

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#removeAsync(Object, Object)} method.
     *
     * @param val See {@link GridCacheProjection#removeAsync(Object, Object)}.
     * @return See {@link GridCacheProjection#removeAsync(Object, Object)}.
     */
    public GridFuture<Boolean> removeAsync(V val);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#evict(Object, GridPredicate[])} method.
     *
     * @param filter Optional filter.
     * @return See {@link GridCacheProjection#evict(Object, GridPredicate[])}.
     */
    public boolean evict(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#clear(Object, GridPredicate[])} method.
     *
     * @param filter Optional filter that entry should pass before
     *      it will be cleared from cache.
     * @return See {@link GridCacheProjection#clear(Object, GridPredicate[])}.
     */
    public boolean clear(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * This method has the same semantic as
     * {@link GridCacheProjection#invalidate(Object, GridPredicate[])} method.
     *
     * @param filter See {@link GridCacheProjection#invalidate(Object, GridPredicate[])}.
     * @return See {@link GridCacheProjection#invalidate(Object, GridPredicate[])}.
     * @throws GridException See {@link GridCacheProjection#invalidate(Object, GridPredicate[])}.
     */
    public boolean invalidate(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Optimizes the size of this entry. If entry value is {@code null} at the time
     * of the call then entry is removed locally.
     *
     * @param filter Optional filter that entry should pass prior to optimization.
     * @throws GridException If failed to compact.
     * @return {@code true} if entry was cleared from cache (if value was {@code null}).
     */
    public boolean compact(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Synchronously acquires lock on a cached object associated with this entry
     * only if the passed in filter (if any) passes. This method together with filter
     * check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return {@code True} if all filters passed and lock was acquired,
     *      {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lock(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously acquires lock on a cached object associated with this entry
     * only if the passed in filter (if any) passes. This method together with filter
     * check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return Future for the lock operation. The future will return {@code true}
     *      whenever lock was successfully acquired, {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Synchronously acquires lock on a cached object associated with this entry
     * only if the passed in filter (if any) passes. This method together with
     * filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions.
     * If you do need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return {@code True} if all filters passed and lock was acquired,
     *      {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lock(long timeout, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously acquires lock on a cached object associated with this entry
     * only if the passed in filter (if any) passes. This method together with
     * filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return Future for the lock operation. The future will return {@code true}
     *      whenever all filters pass and locks are acquired before timeout is expired,
     *      {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Unlocks this entry only if current thread owns the lock. If optional filter
     * will not pass, then unlock will not happen. If this entry was never locked by
     * current thread, then this method will do nothing.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Optional filter that needs to pass prior to unlock taking effect.
     * @throws GridException If unlock execution resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unlock(GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;
}
