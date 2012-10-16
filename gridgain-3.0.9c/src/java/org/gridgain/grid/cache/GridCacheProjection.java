// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * This interface provides a rich API for working with distributed caches. It includes the following
 * main functionality:
 * <ul>
 * <li>
 *  Various {@code 'get(..)'} methods to synchronously or asynchronously get values from cache.
 *  All {@code 'get(..)'} methods are transactional and will participate in an ongoing transaction
 *  if there is one.
 * </li>
 * <li>
 *  Various {@code 'put(..)'}, {@code 'putIfAbsent(..)'}, and {@code 'replace(..)'} methods to
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
 *  Various {@code 'contains(..)'} method to check if cache contains certain keys or values.
 * </li>
 * <li>
 *  Various {@code 'forEach(..)'}, {@code 'forAny(..)'}, and {@code 'reduce(..)'} methods to visit
 *  every cache entry within this projection.
 * </li>
 * <li>
 *  Various {@code flagsOn(..)'}, {@code 'flagsOff(..)'}, and {@code 'projection(..)'} methods to
 *  set specific flags and filters on a cache projection.
 * </li>
 * <li>
 *  Methods like {@code 'keySet(..)'}, {@code 'values(..)'}, and {@code 'entrySet(..)'} to provide
 *  views on cache keys, values, and entries.
 * </li>
 * <li>
 *  Various {@code 'peek(..)'} methods to peek at values in global or transactional memory, swap
 *  storage, or persistent storage.
 * </li>
 * <li>
 *  Various {@code 'reload(..)'} methods to reload latest values from persistent storage.
 * </li>
 * <li>
 *  Various {@code 'unswap(..)'} methods to load specified keys from swap storage into
 *  global cache memory.
 * </li>
 * <li>
 *  Various {@code 'invalidate(..)'} methods to set cached values to {@code null}.
 * <li>
 *  Various {@code 'lock(..)'}, {@code 'unlock(..)'}, and {@code 'isLocked(..)'} methods to acquire, release,
 *  and check on distributed locks on a single or multiple keys in cache. All locking methods
 *  are not transactional and will not enlist keys into ongoing transaction, if any.
 * </li>
 * <li>
 *  Various {@code 'clear(..)'} methods to clear elements from cache, and optionally from
 *  swap storage. All {@code 'clear(..)'} methods are not transactional and will not enlist cleared
 *  keys into ongoing transaction, if any.
 * </li>
 * <li>
 *  Various {@code 'evict(..)'} methods to evict elements from cache, and optionally store
 *  them in underlying swap storage for later access. All {@code 'evict(..)'} methods are not
 *  transactional and will not enlist evicted keys into ongoing transaction, if any.
 * </li>
 * <li>
 *  Various {@code 'txStart(..)'} and {@code 'inTx(..)'} methods to perform various cache
 *  operations within a transaction (see {@link GridCacheTx} for more information).
 * </li>
 * <li>
 *  Various {@code 'createXxxQuery(..)'} methods to query cache using either {@link GridCacheQueryType#SQL SQL},
 *  {@link GridCacheQueryType#LUCENE LUCENE} or {@link GridCacheQueryType#H2TEXT H2TEXT} text search, or
 *  {@link GridCacheQueryType#SCAN SCAN} for filter-based full scan (see {@link GridCacheQuery}
 *   for more information).
 * </li>
 * <li>
 *  Various {@code 'mapKeysToNodes(..)'} methods which provide node affinity mapping for
 *  given keys. All {@code 'mapKeysToNodes(..)'} methods are not transactional and will not enlist
 *  keys into ongoing transaction.
 * </li>
 * <li>
 *  Various {@code 'gridProjection(..)'} methods which provide {@link GridProjection} only
 *  for nodes on which given keys reside. All {@code 'gridProjection(..)'} methods are not
 *  transactional and will not enlist keys into ongoing transaction.
 * </li>
 * <li>Method {@link #toMap()} to convert this interface into standard Java {@link ConcurrentMap} interface.
 * </ul>
 * <h1 class="header">Extended Put And Remove Methods</h1>
 * All methods that end with {@code 'x'} provide the same functionality as their sibling
 * methods that don't end with {@code 'x'}, however instead of returning a previous value they
 * return a {@code boolean} flag indicating whether operation succeeded or not. Returning
 * a previous value may involve a network trip or a persistent store lookup and should be
 * avoided whenever not needed.
 * <h1 class="header">Predicate Filters</h1>
 * All filters passed into methods on this API are checked <b>atomically</b>. In other words the
 * value returned by the methods is guaranteed to be consistent with the filters passed in. Note
 * that filters are optional, and if not passed in, then methods will still work as is without
 * filter validation.
 * <h1 class="header">Transactions</h1>
 * Cache API supports distributed transactions. All {@code 'get(..)'}, {@code 'put(..)'}, {@code 'replace(..)'},
 * and {@code 'remove(..)'} operations are transactional and will participate in an ongoing transaction,
 * if any. Other methods like {@code 'peek(..)'} or various {@code 'contains(..)'} methods may
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
 */
public interface GridCacheProjection<K, V> extends Iterable<GridCacheEntry<K, V>>, GridMetadataAware {
    /**
     * Gets name of this cache ({@code null} for default cache).
     *
     * @return Cache name.
     */
    public String name();

    /**
     * Returns parent projection from which this projection was created.
     *
     * @param <K> Key type of parent projection.
     * @param <V> Value type of parent projection.
     * @return Parent projection from which this projection was created.
     */
    @Nullable public <K, V> GridCacheProjection<K, V> parent();

    /**
     * Gets base cache for this projection.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Base cache for this projection.
     */
    @SuppressWarnings({"ClassReferencesSubclass"})
    public <K, V> GridCache<K, V> cache();

    /**
     * @return Flags for this projection (empty set if no flags have been set).
     */
    public Set<GridCacheFlag> flags();

    /**
     * Gets key-value predicate on which this projection is based on or {@code null}
     * if key-value predicate is not defined.
     *
     * @return Key-value filter on which this projection is based on.
     */
    @Nullable public GridPredicate2<K, V> keyValuePredicate();

    /**
     * Gets entry predicate on which this projection is based on or {@code null}
     * if entry predicate is not defined.
     *
     * @return Entry filter on which this projection is based on.
     */
    @Nullable public GridPredicate<? super GridCacheEntry<K, V>> entryPredicate();

    /**
     * Gets cache projection only for given key and value type. Only {@code non-null} key-value
     * pairs that have matching key and value pairs will be used in this projection.
     * <h1 class="header">Cache Flags</h1>
     * The resulting projection will have flag {@link GridCacheFlag#STRICT} set on it.
     *
     * @param keyType Key type.
     * @param valType Value type.
     * @param <K1> Key type.
     * @param <V1> Value type.
     * @return Cache projection for given key and value types.
     */
    public <K1, V1> GridCacheProjection<K1, V1> projection(Class<?> keyType, Class<?> valType);

    /**
     * Gets cache projection based on given key-value predicate. Whenever makes sense,
     * this predicate will be used to pre-filter cache operations. If
     * operation passed pre-filtering, this filter will be passed through
     * to cache operations as well.
     * <p>
     * For example, for {@link #putAll(Map , GridPredicate[])} method only
     * elements that pass the filter will be given to {@code GridCache.putAll(m,filter)}
     * where it will be checked once again prior to put.
     * <h1 class="header">Cache Flags</h1>
     * The resulting projection will have flag {@link GridCacheFlag#STRICT} set on it.
     *
     * @param p Key-value predicate for this projection. If {@code null}, then the
     *      same projection is returned.
     * @return Projection for given key-value predicate.
     */
    public GridCacheProjection<K, V> projection(@Nullable GridPredicate2<K, V>... p);

    /**
     * Gets cache projection based on given entry filter. This filter will be simply passed through
     * to all cache operations on this projection. Unlike {@link #projection(GridPredicate2[])}
     * method, this filter will <b>not</b> be used for pre-filtering.
     *
     * @param filter Filter to be passed through to all cache operations. If {@code null}, then the
     *      same projection is returned.  If cache operation receives its own filter, then filters
     *      will be {@code 'anded'}.
     * @return Projection based on given filter.
     */
    public GridCacheProjection<K, V> projection(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Gets cache projection base on this one, but with the specified flags turned on.
     * <h1 class="header">Cache Flags</h1>
     * The resulting projection will inherit all the flags from this projection.
     *
     * @param flags Flags to turn on (if empty, then no-op).
     * @return New projection based on this one, but with the specified flags turned on.
     */
    public GridCacheProjection<K, V> flagsOn(@Nullable GridCacheFlag... flags);

    /**
     * Gets cache projection base on this but with the specified flags turned off.
     * <h1 class="header">Cache Flags</h1>
     * The resulting projection will inherit all the flags from this projection except for
     * the ones that were turned off.
     *
     * @param flags Flags to turn off (if empty, then all flags will be turned off).
     * @return New projection based on this one, but with the specified flags turned off.
     */
    public GridCacheProjection<K, V> flagsOff(@Nullable GridCacheFlag... flags);

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings.
     */
    public boolean isEmpty();

    /**
     * Returns {@code true} if this cache contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this map is to be tested.
     * @param filter Filter to test prior to checking containment.
     * @return {@code true} if this map contains a mapping for the specified key.
     * @throws NullPointerException if the key is {@code null}.
     */
    public boolean containsKey(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for all the keys.
     * If passed in keys are empty or {@code null}, then {@code true} is returned.
     *
     * @param keys Keys to check.
     * @param filter Filter to test prior to checking for containment.
     * @return {@code true} if this cache contains mappings for all given keys.
     */
    public boolean containsAllKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for all the keys.
     * If passed in keys are empty, then {@code true} is returned.
     *
     * @param keys Keys to check.
     * @return {@code true} if this cache contains mappings for all the keys.
     */
    public boolean containsAllKeys(@Nullable K... keys);

    /**
     * Returns {@code true} if this cache contains given value.
     *
     * @param val Value to check.
     * @param filter Filter to test prior to checking for containment.
     * @return {@code True} if given value is present in cache.
     * @throws NullPointerException if the value is {@code null}.
     */
    public boolean containsValue(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for all the keys
     * that pass through the filter. If cache is empty then {@code true} is returned.
     *
     * @param filter Key filter to check.
     * @return {@code True} if this filter returns {@code true} for all cache keys.
     */
    public boolean containsAllKeys(@Nullable GridPredicate<? super K>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for at least
     * one of the keys. If passed in keys are empty, then
     * {@code false} is returned.
     *
     * @param keys Keys to check.
     * @param filter Filter to test prior to checking containment.
     * @return {@code true} if this cache contains mappings for at least
     *      one of the keys.
     */
    public boolean containsAnyKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for at least
     * one of the keys. If passed in keys are empty, then
     * {@code false} is returned.
     *
     * @param keys Keys to check.
     * @return {@code true} if this cache contains mappings for at least
     *      one of the keys.
     */
    public boolean containsAnyKeys(@Nullable K... keys);

    /**
     * Returns {@code true} if this cache contains mappings for at least
     * one of the keys. If cache is empty, then {@code false} is returned.
     *
     * @param filter Key filter to check.
     * @return {@code true} if this cache contains mappings for at least
     *      one of the keys that passes through the filter.
     */
    public boolean containsAnyKeys(@Nullable GridPredicate<? super K>... filter);

    /**
     * Returns {@code true} if this cache contains all the values.
     * If cache is empty, then {@code true} is returned.
     *
     * @param vals Values to check.
     * @param filter Filter to test prior to checking containment.
     * @return {@code true} if this cache contains all the values.
     */
    public boolean containsAllValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains all the given values.
     * If cache is empty, then {@code true} is returned.
     *
     * @param vals Values to check.
     * @return {@code true} if this cache contains all the values.
     */
    public boolean containsAllValues(@Nullable V... vals);

    /**
     * Returns {@code true} if all cache values pass through the filter.
     * If cache is empty, then {@code true} is returned.
     *
     * @param filter Value filter to check.
     * @return {@code true} if all cache values pass through the filter.
     */
    public boolean containsAllValues(@Nullable GridPredicate<? super V>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for at least one of the values.
     * If cache is empty, then {@code false} is returned.
     *
     * @param vals Values to check.
     * @param filter Filter to test prior to checking containment.
     * @return {@code true} if this cache contains mappings for at least
     *      one of the values.
     */
    public boolean containsAnyValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if this cache contains mappings for at least one of the values.
     * If cache is empty, then {@code false} is returned.
     *
     * @param vals Values to check.
     * @return {@code true} if this cache contains mappings for at least
     *      one of the values.
     */
    public boolean containsAnyValues(@Nullable V... vals);

    /**
     * Returns {@code true} if at least one of cache values passes through the filter.
     * If cache is empty, then {@code false} is returned.
     *
     * @param filter Filter to check.
     * @return {@code true} if at least one of cache values passes through the filter.
     */
    public boolean containsAnyValues(@Nullable GridPredicate<? super V>... filter);

    /**
     * Returns {@code true} if passed in predicate evaluates to {@code true} for any of the cache entries.
     * If cache is empty, then {@code false} is returned.
     *
     * @param filter Entry filter to check.
     * @return {@code True} if passed in predicate evaluates to {@code true} for
     *      any of the cache entries, {@code false} otherwise.
     */
    public boolean containsAnyEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Returns {@code true} if all cache entries pass through the filter.
     * If cache is empty, then {@code true} is returned.
     *
     * @param filter Entry filter to check.
     * @return {@code True} if all cache entries pass through the filter.
     */
    public boolean containsAllEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     * @param keys Keys to visit.
     */
    public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis, @Nullable Collection<? extends K> keys);

    /**
     * Asynchronously executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys);

    /**
     * Executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     */
    public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis);

    /**
     * Asynchronously executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis);

    /**
     * Executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     * @param keys Keys to visit.
     */
    public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis, @Nullable K... keys);

    /**
     * Asynchronously executes visitor closure on each cache element.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Closure which will be invoked for each cache entry.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis, @Nullable K... keys);

    /**
     * Tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @return {@code True} if the given predicate holds for all visited entries, {@code false} otherwise.
     */
    public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis);

    /**
     * Asynchronously tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis);

    /**
     * Tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @param keys Keys to visit.
     * @return {@code True} if the given predicate holds for all visited entries, {@code false} otherwise.
     */
    public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable Collection<? extends K> keys);

    /**
     * Asynchronously tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys);

    /**
     * Tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @param keys Keys to visit.
     * @return {@code True} if the given predicate holds for all visited entries, {@code false} otherwise.
     */
    public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable K... keys);

    /**
     * Asynchronously tests whether the predicate holds for all entries. If cache is empty,
     * then {@code true} is returned.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param vis Predicate to test for each cache entry.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable K... keys);

    /**
     * Visits every cache entry and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @return Reduced value provided by reducer.
     */
    @Nullable public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc);

    /**
     * Asynchronously visits every cache entry and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @return Future which will complete whenever this operation completes.
     */
    public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc);

    /**
     * Visits specified cache entries and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @param keys Keys to visit.
     * @return Reduced value provided by reducer.
     */
    @Nullable public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys);

    /**
     * Asynchronously visits specified cache entries and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys);

    /**
     * Visits specified cache entries and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @param keys Keys to visit.
     * @return Reduced value provided by reducer.
     */
    @Nullable public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc, @Nullable K... keys);

    /**
     * Asynchronously visits specified cache entries and then reduces the result.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional and will not enlist keys into transaction simply
     * because they were visited. However, if you perform transactional operations on the
     * visited entries, those operations will enlist the entry into transaction.
     *
     * @param rdc Reducer which will be invoked for all keys-value pairs and
     *      eventually return a value at the end.
     * @param keys Keys to visit.
     * @return Future which will complete whenever this operation completes.
     */
    public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc, @Nullable K... keys);

    /**
     * Reloads a single key from persistent storage. This method
     * delegates to {@link GridCacheStore#load(String, GridCacheTx , Object)}
     * method.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param key Key to reload.
     * @param filter Optional filter. If provided, will be used to filter entries before
     *      loading and before they get set into cache.
     * @return Reloaded value or current value if entry was updated while reloading.
     * @throws GridException If reloading failed.
     */
    @Nullable public V reload(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously reloads a single key from persistent storage. This method
     * delegates to {@link GridCacheStore#load(String, GridCacheTx , Object)}
     * method.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param key Key to reload.
     * @param filter Optional filter. If provided, will be used to filter entries before
     *      loading and before they get set into cache.
     * @return Future to be completed whenever the entry is reloaded.
     */
    public GridFuture<V> reloadAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Reloads all currently cached keys form persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @throws GridException If reloading failed.
     */
    public void reloadAll() throws GridException;

    /**
     * Asynchronously reloads all specified entries from underlying
     * persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @return Future which will complete whenever {@code reload} completes.
     */
    public GridFuture<?> reloadAllAsync();

    /**
     * Reloads specified entries from underlying persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param keys Keys to reload.
     * @param filter Optional filter. If provided, will be used to filter key-value pairs
     *      to be put into cache.
     * @throws GridException if reloading failed.
     */
    public void reloadAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Asynchronously reloads all specified entries from underlying
     * persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param keys Keys to reload.
     * @param filter Optional filter. If provided, will be used to filter key-value pairs
     *      to be put into cache.
     * @return Future which will complete whenever {@code reload} completes.
     */
    public GridFuture<?> reloadAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Reloads specified entries from underlying persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param keys Keys to reload.
     * @throws GridException if reloading failed.
     */
    public void reloadAll(@Nullable K... keys) throws GridException;

    /**
     * Asynchronously reloads all specified entries from underlying
     * persistent storage.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param keys Keys to reload.
     * @return Future which will complete whenever {@code reload} completes.
     */
    public GridFuture<?> reloadAllAsync(@Nullable K... keys);

    /**
     * Reloads all currently cached entries which pass the passed in filter from the
     * underlying persistent storage. This method delegates to
     * {@link GridCacheStore#loadAll(String, GridCacheTx , Collection, GridInClosure2)}
     * method, and is different from {@link GridCache#loadCache(GridPredicate2 , long, Object...)}
     * in the sense that only all currently cached keys are reloaded.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param filter Optional filter. If provided, will be used to filter key-value pairs
     *      to be put into cache.
     * @throws GridException If reloading failed.
     */
    public void reloadAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously reloads all currently cached entries which pass the filter from the
     * underlying persistent storage. This method delegates to
     * {@link GridCacheStore#loadAll(String, GridCacheTx , Collection, GridInClosure2)}
     * method, and is different from {@link GridCache#loadCache(GridPredicate2 , long, Object...)}
     * in the sense that only all currently cached keys are reloaded.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in transactions, however it does not violate
     * cache integrity and can be used safely with or without transactions.
     *
     * @param filter Optional filter. If provided, will be used to filter key-value pairs
     *      to be put into cache.
     * @return Future which will complete whenever {@code reload} completes.
     */
    public GridFuture<?> reloadAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Peeks at in-memory cached value using default {@link GridCachePeekMode#SMART}
     * peek mode.
     * <p>
     * This method will not load value from any persistent store or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param key Entry key.
     * @return Peeked value.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public V peek(K key);

    /**
     * Peeks at in-memory cached value using default {@link GridCachePeekMode#SMART}
     * peek mode.
     * <p>
     * This method will not load values from any persistent storage or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param key Key to peek.
     * @param filter Optional filter. If entry does not pass the filter, {@code null} is returned.
     * @return Peeked value or {@code null} if entry does not have a value or did not
     *      pass the filter.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public V peek(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Peeks at in-memory cached values using default {@link GridCachePeekMode#SMART}
     * peek mode.
     * <p>
     * This method will not load values from any persistent storage or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param keys Keys to peek.
     * @return Map of key-value pairs.
     */
    public Map<K, V> peekAll(@Nullable Collection<? extends K> keys);

    /**
     * Peeks at in-memory cached value using default {@link GridCachePeekMode#SMART}
     * peek mode. Only values that pass the filter will be returned.
     * <p>
     * This method will not load values from any persistent storage or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param keys Keys to get.
     * @param filter Optional filter. If provided, will be used to filter entries prior to returning.
     * @return Map of key-value pairs.
     */
    public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Peeks at in-memory cached values using default {@link GridCachePeekMode#SMART}
     * peek mode.
     * <p>
     * This method will not load values from any persistent storage or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param keys Keys to get.
     * @return Map of key-value pairs.
     */
    public Map<K, V> peekAll(@Nullable K... keys);

    /**
     * Peeks at in-memory cached value using default {@link GridCachePeekMode#SMART}
     * peek mode. Only values that pass the filter will be returned.
     * <p>
     * This method will not load values from any persistent storage or from a remote node.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it will
     * peek at transactional value according to the {@link GridCachePeekMode#SMART} mode
     * semantics. If you need to look at global cached value even from within transaction,
     * you can use {@link GridCache#peek(Object, GridCachePeekMode...)} method.
     *
     * @param filter Only keys that pass through all provided filters will be retrieved.
     * @return Map of key-value pairs.
     */
    public Map<K, V> peekAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Peeks at cached value using optional set of peek modes. This method will sequentially
     * iterate over given peek modes in the order passed in, and try to peek at value using
     * each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)} implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param key Entry key.
     * @param modes Optional set of peek modes.
     * @return Peeked value.
     * @throws GridException If peek operation failed.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public V peek(K key, @Nullable GridCachePeekMode... modes) throws GridException;

    /**
     * Peeks at cached value using optional set of peek modes. This method will sequentially
     * iterate over given peek modes in the order passed in, and try to peek at value using
     * each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param key Entry key.
     * @param modes Optional set of peek modes.
     * @return Peeked value.
     * @throws GridException If peek operation failed.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public V peek(K key, @Nullable Collection<GridCachePeekMode> modes) throws GridException;

    /**
     * Asynchronously peeks at cached value using optional set of peek modes. This method will
     * sequentially iterate over given peek modes in the order passed in, and try to peek at value
     * using each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param key Entry key.
     * @param modes Optional set of peek modes.
     * @return Future for peeked entry value.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public GridFuture<V> peekAsync(K key, @Nullable GridCachePeekMode... modes);

    /**
     * Asynchronously peeks at cached value using optional set of peek modes. This method will
     * sequentially iterate over given peek modes in the order passed in, and try to peek at value
     * using each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param key Entry key.
     * @param modes Optional set of peek modes.
     * @return Future for peeked entry value.
     * @throws NullPointerException If key is {@code null}.
     */
    @Nullable public GridFuture<V> peekAsync(K key, @Nullable Collection<GridCachePeekMode> modes);

    /**
     * Peeks at cached values using optional set of peek modes. This method will sequentially
     * iterate over given peek modes in the order passed in, and try to peek at value using
     * each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peekAll(Collection)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param keys Keys to peek.
     * @param modes Peek modes.
     * @return Map of peeked key-value pairs.
     * @throws GridException If peek operation failed.
     */
    public Map<K, V> peekAll(@Nullable Collection<? extends K> keys, @Nullable GridCachePeekMode... modes)
        throws GridException;

    /**
     * Peeks at cached values using optional set of peek modes. This method will sequentially
     * iterate over given peek modes in the order passed in, and try to peek at value using
     * each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peekAll(Collection)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param keys Keys to peek.
     * @param modes Peek modes.
     * @return Map of peeked key-value pairs.
     * @throws GridException If peek operation failed.
     */
    public Map<K, V> peekAll(@Nullable Collection<? extends K> keys, @Nullable Collection<GridCachePeekMode> modes)
        throws GridException;

    /**
     * Asynchronously peeks at cached values using optional set of peek modes. This method will
     * sequentially iterate over given peek modes in the order passed in, and try to peek at value
     * using each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param keys Keys to peek.
     * @param modes Peek modes.
     * @return Future for map of peeked key-value pairs.
     */
    public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridCachePeekMode... modes);

    /**
     * Asynchronously peeks at cached values using optional set of peek modes. This method will
     * sequentially iterate over given peek modes in the order passed in, and try to peek at value
     * using each peek mode. Once a {@code non-null} value is found, it will be immediately returned.
     * <p>
     * Note that if modes are not provided this method works exactly the same way as
     * {@link #peek(Object)}, implicitly using {@link GridCachePeekMode#SMART} mode.
     * <h2 class="header">Transactions</h2>
     * This method does not participate in any transactions, however, it may
     * peek at transactional value depending on the peek modes used.
     *
     * @param keys Keys to peek.
     * @param modes Peek modes.
     * @return Future for map of peeked key-value pairs.
     */
    public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes);

    /**
     * Retrieves value mapped to the specified key from cache. Value will only be returned if
     * its entry passed the optional filter provided. Filter check is atomic, and therefore the
     * returned value is guaranteed to be consistent with the filter. The return value of {@code null}
     * means entry did not pass the provided filter or cache has no mapping for the
     * key.
     * <p>
     * If the value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disable, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#load(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param key Key to retrieve the value for.
     * @param filter Filter to check prior to getting the value. Note that filter check
     *      together with getting the value is an atomic operation.
     * @return Value for the given key.
     * @throws GridException If get operation failed.
     * @throws GridCacheFlagException If failed projection flags validation.
     * @throws NullPointerException if the key is {@code null}.
     */
    @Nullable public V get(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously retrieves value mapped to the specified key from cache. Value will only be returned if
     * its entry passed the optional filter provided. Filter check is atomic, and therefore the
     * returned value is guaranteed to be consistent with the filter. The return value of {@code null}
     * means entry did not pass the provided filter or cache has no mapping for the
     * key.
     * <p>
     * If the value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#load(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param key Key for the value to get.
     * @param filter Filter to check prior to getting the value. Note that filter check
     *      together with getting the value is an atomic operation.
     * @return Future for the get operation.
     * @throws NullPointerException if the key is {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<V> getAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Retrieves values mapped to the specified keys from cache. Value will only be returned if
     * its entry passed the optional filter provided. Filter check is atomic, and therefore the
     * returned value is guaranteed to be consistent with the filter. If requested key-value pair
     * is not present in the returned map, then it means that its entry did not pass the provided
     * filter or cache has no mapping for the key.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param keys Keys to get.
     * @param filter Only keys that pass through all provided filters will be retrieved.
     * @return Map of key-value pairs.
     * @throws GridException If get operation failed.
     * @throws GridCacheFlagException If failed projection flags validation.
     */
    public Map<K, V> getAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Asynchronously retrieves values mapped to the specified keys from cache. Value will only be returned if
     * its entry passed the optional filter provided. Filter check is atomic, and therefore the
     * returned value is guaranteed to be consistent with the filter. If requested key-value pair
     * is not present in the returned map, then it means that its entry did not pass the provided
     * filter or cache has no mapping for the key.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param keys Key for the value to get.
     * @param filter Only keys that pass through all provided filters will be retrieved.
     * @return Future for the get operation.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Retrieves values mapped to the specified keys from cache. If requested key-value pair
     * is not present in the returned map, then it means that cache has no mapping for the key.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param keys Keys to get.
     * @return Map of key-value pairs.
     * @throws GridException If get operation failed.
     * @throws GridCacheFlagException If failed projection flags validation.
     */
    public Map<K, V> getAll(@Nullable K... keys) throws GridException;

    /**
     * Asynchronously retrieves values mapped to the specified keys from cache. If requested key-value pair
     * is not present in the returned map, then it means that cache has no mapping for the key.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param keys Keys to get.
     * @return Map of key-value pairs.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Map<K, V>> getAllAsync(@Nullable K... keys);

    /**
     * Retrieves all values whose entries pass the provided filter. Note, that a new map
     * will be created, and therefore, if filter accepts all, or close to all, cache entries,
     * the created map may get very large and affect performance and memory consumption.
     * In this case it is more advisable to use {@link #projection(GridPredicate[])} based
     * on entry filter, as it is simply a view on the underlying cache and not a copy.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param filter Only keys that pass through all provided filters will be retrieved.
     * @return Map of key-value pairs.
     * @throws GridException If get operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public Map<K, V> getAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously retrieves all values whose entries pass the provided filter. Note, that a new map
     * will be created, and therefore, if filter accepts all, or close to all, cache entries,
     * the created map may get very large and affect performance and memory consumption.
     * In this case it is more advisable to use {@link #projection(GridPredicate[])} based
     * on entry filter, as it is simply a view on the underlying cache and not a copy.
     * <p>
     * If some value is not present in cache, then it will be looked up from swap storage. If
     * it's not present in swap, or if swap is disabled, and if read-through is allowed, value
     * will be loaded from {@link GridCacheStore} persistent storage via
     * {@link GridCacheStore#loadAll(String, GridCacheTx, Collection, GridInClosure2)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if {@link GridCacheFlag#LOCAL} flag is set on projection.
     *
     * @param filter Only keys that pass through all provided filters will be retrieved.
     * @return Future for the get operation.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Map<K, V>> getAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage,  {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #putx(Object, Object, GridPredicate[])} should
     * always be used instead of this one to avoid the overhead associated with returning of the previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @param filter Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Previous value associated with specified key, or {@code null}
     *  if entry did not pass the filter, or if there was no mapping for the key in swap
     *  or in persistent storage.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If put operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    @Nullable public V put(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage,  {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #putx(Object, Object, GridPredicate[])} should
     * always be used instead of this one to avoid the overhead associated with returning of the previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @param filter Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Future for the put operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<V> putAsync(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #put(Object, Object, GridPredicate[])} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning a value. It
     * should be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @param filter Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return {@code True} if value was stored in cache, {@code false} otherwise.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If put operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean putx(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #put(Object, Object, GridPredicate[])} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning of a value. It
     * should always be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @param filter Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Future for the put operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> putxAsync(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Stores given key-value pair in cache only if cache had no previous mapping for it. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #putxIfAbsent(Object, Object)} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Previously contained value regardless of whether put happened or not.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If put operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    @Nullable public V putIfAbsent(K key, V val) throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache only if cache had no previous mapping for it. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #putxIfAbsentAsync(Object, Object)} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Future of put operation which will provide previously contained value
     *   regardless of whether put happened or not.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<V> putIfAbsentAsync(K key, V val);

    /**
     * Stores given key-value pair in cache only if cache had no previous mapping for it.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #putIfAbsent(Object, Object)} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning of a value. It
     * should always be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return {@code true} if value is stored in cache and {@code false} otherwise.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If put operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean putxIfAbsent(K key, V val) throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache only if cache had no previous mapping for it.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #putIfAbsent(Object, Object)} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning of a value. It
     * should always be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Future for this put operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> putxIfAbsentAsync(K key, V val);

    /**
     * Stores given key-value pair in cache only if if there is a previous mapping for it. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #replacex(Object, Object)} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Previously contained value regardless of whether replace happened or not.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If replace operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    @Nullable public V replace(K key, V val) throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache only if if there is a previous mapping for it. If cache
     * previously contained value for the given key, then this value is returned. Otherwise,
     * in case of {@link GridCacheMode#REPLICATED} caches, the value will be loaded from swap
     * and, if it's not there, and read-through is allowed, from the underlying
     * {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED} caches,
     * the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #replacex(Object, Object)} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Future for replace operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<V> replaceAsync(K key, V val);

    /**
     * Stores given key-value pair in cache only if only if there is a previous mapping for it.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #replace(Object, Object)} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning of a value. It
     * should always be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return {@code True} if replace happened, {@code false} otherwise.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If replace operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean replacex(K key, V val) throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache only if only if there is a previous mapping for it.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * Unlike {@link #replaceAsync(Object, Object)} method, it does not return previous
     * value and, therefore, does not have any overhead associated with returning of a value. It
     * should always be used whenever return value is not required.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param val Value to be associated with the given key.
     * @return Future for the replace operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> replacexAsync(K key, V val);

    /**
     * Stores given key-value pair in cache only if only if the previous value is equal to the
     * {@code 'oldVal'} passed in.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param oldVal Old value to match.
     * @param newVal Value to be associated with the given key.
     * @return {@code True} if replace happened, {@code false} otherwise.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridException If replace operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean replace(K key, V oldVal, V newVal) throws GridException;

    /**
     * Asynchronously stores given key-value pair in cache only if only if the previous value is equal to the
     * {@code 'oldVal'} passed in.
     * <p>
     * This method will return {@code true} if value is stored in cache and {@code false} otherwise.
     * <p>
     * If write-through is enabled, the stored value will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#put(String, GridCacheTx, Object, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to store in cache.
     * @param oldVal Old value to match.
     * @param newVal Value to be associated with the given key.
     * @return Future for the replace operation.
     * @throws NullPointerException If either key or value are {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> replaceAsync(K key, V oldVal, V newVal);

    /**
     * Stores given key-value pairs in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored values will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#putAll(String, GridCacheTx, Map)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param m Key-value pairs to store in cache.
     * @param filter Optional entry filter. If provided, then entry will
     *      be stored only if the filter returned {@code true}.
     * @throws GridException If put operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public void putAll(@Nullable Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Asynchronously stores given key-value pairs in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored values will be persisted to {@link GridCacheStore}
     * via {@link GridCacheStore#putAll(String, GridCacheTx, Map)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param m Key-value pairs to store in cache.
     * @param filter Optional entry filter. If provided, then entry will
     *      be stored only if the filter returned {@code true}.
     * @return Future for putAll operation.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<?> putAllAsync(@Nullable Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Set of cached keys. Note that this set will contain mappings for all keys,
     * even if their values are {@code null} because they were invalidated. You can remove
     * elements from this set, but you cannot add elements to this set. All removal operation
     * will be reflected on the cache itself.
     * <p>
     * Iterator over this set will not fail if set was concurrently updated
     * by another thread. This means that iterator may or may not return latest
     * keys depending on whether they were added before or after current
     * iterator position.
     *
     * @return Key set for this cache.
     */
    public Set<K> keySet();

    /**
     * Set of cached keys. Note that this set will contain mappings for all keys,
     * even if their values are {@code null} because they were invalidated. You can remove
     * elements from this set, but you cannot add elements to this set. All removal operation
     * will be reflected on the cache itself.
     * <p>
     * If filter is provided, then only the keys for the entries that pass
     * provided filters are included in the set.
     * <p>
     * Iterator over this set will not fail if set was concurrently updated
     * by another thread. This means that iterator may or may not return latest
     * keys depending on whether they were added before or after current
     * iterator position.
     *
     * @param filter Filter for the key set. If not provided then key set for the
     *      whole cache is returned.
     * @return Key set for this cache.
     */
    public Set<K> keySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Set of cached keys. Note that this set will contain mappings for all cached keys,
     * even if their values are {@code null} because they were invalidated. You can
     * remove elements from this set, but you cannot add elements to this set. All
     * removal operation will be reflected on the cache itself.
     * <p>
     * If filter is provided, then only the keys for the entries that pass
     * provided filters are returned.
     * <p>
     * Iterator over this set will not fail if set was concurrently updated
     * by another thread. This means that iterator may or may not return latest
     * keys depending on whether they were added before or after current
     * iterator position.
     *
     * @param keys Only keys specified here will be returned in the set.
     * @param filter Filter for the key set. If not provided then key set for the
     *      whole cache is returned.
     * @return Key set for this cache.
     */
    public Set<K> keySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Set of cached keys. Note that this set will contain mappings for all cached keys,
     * even if their values are {@code null} because they were invalidated. You can
     * remove elements from this set, but you cannot add elements to this set. All
     * removal operation will be reflected on the cache itself.
     * <p>
     * Iterator over this set will not fail if set was concurrently updated
     * by another thread. This means that iterator may or may not return latest
     * keys depending on whether they were added before or after current
     * iterator position.
     *
     * @param keys Only keys specified here will be returned in the set.
     * @return Key set for this cache.
     */
    public Set<K> keySet(@Nullable K... keys);

    /**
     * Collection of cached values. Note that this collection will not contain
     * values that are {@code null} because they were invalided. You can remove
     * elements from this collection, but you cannot add elements to this collection.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * Iterator over this collection will not fail if collection was
     * concurrently updated by another thread. This means that iterator may or
     * may not return latest values depending on whether they were added before
     * or after current iterator position.
     *
     * @return Collection of cached values.
     */
    public Collection<V> values();

    /**
     * Collection of cached values. Note that this collection will not contain
     * values that are {@code null} because they were invalided. You can remove
     * elements from this collection, but you cannot add elements to this collection.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * If filter is provided, then only the keys for the entries that pass
     * provided filters are returned.
     * <p>
     * Iterator over this collection will not fail if collection was
     * concurrently updated by another thread. This means that iterator may or
     * may not return latest values depending on whether they were added before
     * or after current iterator position.
     *
     * @param filter Filter for the key set. If not provided then key set for the
     *      whole cache is returned.
     * @return Collection of cached values.
     */
    public Collection<V> values(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Collection of cached values for given keys. Note that this collection will not contain
     * values that are {@code null} because they were invalided. You can remove
     * elements from this collection, but you cannot add elements to this collection.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * If filter is provided, then only the keys for the entries that pass
     * provided filters are returned.
     * <p>
     * Iterator over this collection will not fail if collection was
     * concurrently updated by another thread. This means that iterator may or
     * may not return latest values depending on whether they were added before
     * or after current iterator position.
     *
     * @param keys Keys for which value pairs will be returned.
     * @param filter Filter for the values. If not provided then key set for the
     *      whole cache is returned.
     * @return Collection of cached values.
     */
    public Collection<V> values(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Collection of cached values for given keys. Note that this collection will not contain
     * values that are {@code null} because they were invalided. You can remove
     * elements from this collection, but you cannot add elements to this collection.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * If filter is provided, then only the keys for the entries that pass
     * provided filters are returned.
     * <p>
     * Iterator over this collection will not fail if collection was
     * concurrently updated by another thread. This means that iterator may or
     * may not return latest values depending on whether they were added before
     * or after current iterator position.
     *
     * @param keys Keys for which value pairs will be returned.
     * @return Collection of cached values.
     */
    public Collection<V> values(@Nullable K... keys);

    /**
     * Gets set containing all cache entries. You can remove
     * elements from this set, but you cannot add elements to this set.
     * All removal operation will be reflected on the cache itself.
     *
     * @return Set containing all current cache entries.
     */
    public Set<GridCacheEntry<K, V>> entrySet();

    /**
     * Gets set containing all cache entries for given keys. You can remove
     * elements from this set, but you cannot add elements to this set.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * If optional filters are provided, then only entries that successfully
     * pass the filters will be included in the resulting set.
     *
     * @param keys Keys to get entries for.
     * @param filter Filter for all entries in the set.
     * @return Cache entries for specified keys (possibly empty).
     */
    public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Gets set containing all cache entries for given keys. You can remove
     * elements from this set, but you cannot add elements to this set.
     * All removal operation will be reflected on the cache itself.
     *
     * @param keys Keys to get entries for.
     * @return Cache entries for specified keys (possibly empty).
     */
    public Set<GridCacheEntry<K, V>> entrySet(@Nullable K... keys);

    /**
     * Gets set containing all cache entries. You can remove
     * elements from this set, but you cannot add elements to this set.
     * All removal operation will be reflected on the cache itself.
     * <p>
     * If optional filters are provided, then only entries that successfully
     * pass the filters will be included in the resulting set.
     *
     * @param filter Key filter for entries.
     * @return Entries that pass through key filter.
     */
    public Set<GridCacheEntry<K, V>> entrySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Creates user's query without any parameters. For more information refer to
     * {@link GridCacheQuery} documentation.
     *
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery();

    /**
     * Creates user's query for given query type. For more information refer to
     * {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type);

    /**
     * Creates user's query for given query type, queried class, and query clause which is generally
     * a where clause. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable Class<?> cls, @Nullable String clause);

    /**
     * Creates user's query for given query type, queried class, and query clause which is generally
     * a where clause. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable String clsName, @Nullable String clause);

    /**
     * Creates user's transform query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery();

    /**
     * Creates user's transform query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type);

    /**
     * Creates user's transform query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, @Nullable Class<?> cls,
        @Nullable String clause);

    /**
     * Creates user's transform query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, @Nullable String clsName,
        @Nullable String clause);

    /**
     * Creates user's reduce query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery();

    /**
     * Creates user's reduce query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type);

    /**
     * Creates user's reduce query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable Class<?> cls,
        @Nullable String clause);

    /**
     * Creates user's reduce query. For more information refer to {@link GridCacheQuery} documentation.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause);

    /**
     * Starts transaction with default isolation, concurrency, timeout, and invalidation policy.
     * All defaults are set in {@link GridCacheConfiguration} at startup.
     *
     * @return New transaction
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public GridCacheTx txStart() throws IllegalStateException;

    /**
     * Starts new transaction with the specified timeout and default concurrency,
     * isolation, and invalidation policy.
     *
     * @param timeout Transaction timeout.
     * @return New transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public GridCacheTx txStart(long timeout);

    /**
     * Starts new transaction with the specified concurrency and isolation.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @return New transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation);

    /**
     * Starts transaction with specified isolation, concurrency, timeout, and invalidation flag.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @return New transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public GridCacheTx txStart(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate);

    /**
     * Gets transaction started by this thread or {@code null} if this thread does
     * not have a transaction.
     *
     * @return Transaction started by this thread or {@code null} if this thread
     *      does not have a transaction.
     */
    @Nullable public GridCacheTx tx();

    /**
     * Gets entry from cache with the specified key. The returned entry can
     * be used even after entry key has been removed from cache. In that
     * case, every operation on returned entry will result in creation of a
     * new entry.
     * <p>
     * Note that this method can return {@code null} if projection is configured as
     * pre-filtered and entry key and value don't pass key-value filter of the projection.
     *
     * @param key Entry key.
     * @return Cache entry or {@code null} if projection pre-filtering was not passed.
     */
    @Nullable public GridCacheEntry<K, V> entry(K key);

    /**
     * Evicts entry associated with given key from cache. Note, that entry will be evicted
     * only if it's not used (not participating in any locks or transactions).
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entry will
     * be swapped to disk.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param key Key to evict from cache.
     * @param filter Optional filter that entry should pass before it will be evicted.
     * @return {@code True} if entry could be evicted, {@code false} otherwise.
     */
    public boolean evict(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Attempts to evict all cache entries. Note, that entry will be
     * evicted only if it's not used (not participating in any locks or
     * transactions).
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * be swapped to disk.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     */
    public void evictAll();

    /**
     * Attempts to evict all cache entries. Note, that entry will be
     * evicted only if it's not used (not participating in any locks or
     * transactions).
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * be swapped to disk.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     * @param filter Optional filter that entry should pass before it will be evicted.
     */
    public void evictAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Attempts to evict all entries associated with keys. Note,
     * that entry will be evicted only if it's not used (not
     * participating in any locks or transactions).
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * be swapped to disk.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to evict.
     * @param filter Optional filter that entry should pass before it will be evicted.
     */
    public void evictAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Attempts to evict all entries associated with keys. Note,
     * that entry will be evicted only if it's not used (not
     * participating in any locks or transactions).
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * be swapped to disk.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to evict.
     */
    public void evictAll(@Nullable K... keys);

    /**
     * Clears all entries from this cache only if the entry is not
     * currently locked or participating in a transaction.
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * also be cleared from swap.
     * <p>
     * Note that this operation is local as it merely clears
     * entries from local cache. It does not remove entries from
     * remote caches or from underlying persistent storage.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     */
    public void clearAll();

    /**
     * Clears all entries from this cache only if the entry is not
     * currently locked or participating in a transaction.
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * also be cleared from swap.
     * <p>
     * Note that this operation is local as it merely clears
     * entries from local cache. It does not remove entries from
     * remote caches or from underlying persistent storage.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param filter Optional entry filter that entry should pass
     *      before it will be cleared.
     */
    public void clearAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Clears all entries from this cache only if the entry is not
     * currently locked, and is not participating in a transaction.
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * also be cleared from swap.
     * <p>
     * Note that this operation is local as it merely clears
     * entries from local cache. It does not remove entries from
     * remote caches or from underlying persistent storage.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to clear.
     * @param filter Optional entry filter that entry should pass
     *      before it will be cleared.
     */
    public void clearAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Clears all entries from this cache only if the entry is not
     * currently locked, is not participating in a transaction.
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * also be cleared from swap.
     * <p>
     * Note that this operation is local as it merely clears
     * entries from local cache. It does not remove entries from
     * remote caches or from underlying persistent storage.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to clear.
     */
    public void clearAll(@Nullable K... keys);

    /**
     * Clears an entry from this cache and swap storage only if the entry
     * is not currently locked, and is not participating in a transaction.
     * <p>
     * If {@link GridCacheConfiguration#isSwapEnabled()} is set to {@code true} and
     * {@link GridCacheFlag#SKIP_SWAP} is not enabled, the evicted entries will
     * also be cleared from swap.
     * <p>
     * Note that this operation is local as it merely clears
     * an entry from local cache. It does not remove entries from
     * remote caches or from underlying persistent storage.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param key Key to clear.
     * @param filter Optional entry filter that entry should pass before it will be evicted.
     * @return {@code True} if entry was successfully cleared from cache, {@code false}
     *      if entry was in use at the time of this method invocation and could not be
     *      cleared.
     */
    public boolean clear(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Invalidates (nullifies) an entry with given key. This operation is local and non-transactional.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param key Key to invalidate entry for.
     * @param filter Optional filter that entry should pass prior to invalidation.
     * @throws GridException In case of operation failure.
     * @return {@code true} if entry was invalidated, {@code false} otherwise.
     */
    public boolean invalidate(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Invalidates (nullifies) all entries. This operation is local and non-transactional.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to invalidate entries for.
     * @param filter Optional filter that entry should pass prior to invalidation.
     * @throws GridException In case of operation failure.
     */
    public void invalidateAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Invalidates (nullifies) all entries. This operation is local and non-transactional.
     *
     * @param keys Keys to invalidate entries for.
     * @throws GridException In case of operation failure.
     */
    public void invalidateAll(@Nullable K... keys) throws GridException;

    /**
     * Optimizes the size of an entry with given key. If entry value is {@code null}
     * at the time of the call then entry is removed locally.
     *
     * @param key Key to optimize entry for.
     * @param filter Optional filter that entry should pass prior to optimization.
     * @throws GridException If failed to compact.
     * @return {@code true} if entry was cleared from cache (if value was {@code null}).
     */
    public boolean compact(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Optimizes the size of entries with given keys. If value of single processed entry
     * is {@code null} at the time of the call then this entry is removed locally.
     *
     * @param keys Keys to optimize entries for.
     * @param filter Optional filter that entry should pass prior to optimization.
     * @throws GridException If failed to compact.
     */
    public void compactAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Optimizes the size of entries with given keys. If value of single processed entry
     * is {@code null} at the time of the call then this entry is removed locally.
     *
     * @param keys Keys to optimize entries for.
     * @throws GridException If failed to compact.
     */
    public void compactAll(@Nullable K... keys) throws GridException;

    /**
     * Removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of {@link GridCacheMode#REPLICATED} caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED}
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #removex(Object, GridPredicate[])} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key, or {@code null}
     *      if there was no value for this key.
     * @throws NullPointerException If key is {@code null}.
     * @throws GridException If remove operation failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    @Nullable public V remove(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of {@link GridCacheMode#REPLICATED} caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying {@link GridCacheStore} storage. In case of {@link GridCacheMode#PARTITIONED}
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, {@link GridCacheStore#load(String, GridCacheTx, Object)} method will be used.
     * <p>
     * If the returned value is not needed, method {@link #removex(Object, GridPredicate[])} should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Future for the remove operation.
     * @throws NullPointerException if the key is {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<V> removeAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Removes given key mapping from cache.
     * <p>
     * This method will return {@code true} if remove did occur, which means that all optionally
     * provided filters have passed and there was something to remove, {@code false} otherwise.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return {@code True} if filter passed validation and entry was removed, {@code false} otherwise.
     * @throws NullPointerException if the key is {@code null}.
     * @throws GridException If remove failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean removex(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously removes given key mapping from cache.
     * <p>
     * This method will return {@code true} if remove did occur, which means that all optionally
     * provided filters have passed and there was something to remove, {@code false} otherwise.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Future for the remove operation. The future will return {@code true}
     *      if optional filters passed validation and remove did occur, {@code false} otherwise.
     * @throws NullPointerException if the key is {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> removexAsync(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Removes given key mapping from cache if one exists and value is equal to the passed in value.
     * <p>
     * This method will return {@code true} if remove did occur, which means that all optionally
     * provided filters have passed and there was something to remove, {@code false} otherwise.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param val Value to match against currently cached value.
     * @return {@code True} if filter passed validation, {@code false} otherwise.
     * @throws NullPointerException if the key or value is {@code null}.
     * @throws GridException If remove failed.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public boolean remove(K key, V val) throws GridException;

    /**
     * Asynchronously removes given key mapping from cache if one exists and value is equal to the passed in value.
     * <p>
     * This method will return {@code true} if remove did occur, which means that all optionally
     * provided filters have passed and there was something to remove, {@code false} otherwise.
     * <p>
     * If write-through is enabled, the value will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#remove(String, GridCacheTx, Object)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key whose mapping is to be removed from cache.
     * @param val Value to match against currently cached value.
     * @return Future for the remove operation. The future will return {@code true}
     *      if currently cached value will match the passed in one.
     * @throws NullPointerException if the key or value is {@code null}.
     * @throws GridCacheFlagException If projection flags validation failed.
     */
    public GridFuture<Boolean> removeAsync(K key, V val);

    /**
     * Removes given key mappings from cache for entries for which the optionally passed in filters do
     * pass.
     * <p>
     * If write-through is enabled, the values will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#removeAll(String, GridCacheTx, Collection)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys whose mappings are to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @throws GridException If remove failed.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void removeAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Asynchronously removes given key mappings from cache for entries for which the optionally
     * passed in filters do pass.
     * <p>
     * If write-through is enabled, the values will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#removeAll(String, GridCacheTx, Collection)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys whose mappings are to be removed from cache.
     * @param filter Optional filter to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Future for the remove operation. The future will complete whenever
     *      remove operation completes.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<?> removeAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Removes mappings from cache for entries for which the optionally passed in filters do
     * pass. If passed in filters are {@code null}, then all entries in cache will be enrolled
     * into transaction.
     * <p>
     * <b>USE WITH CARE</b> - if your cache has many entries that pass through the filter or if filter
     * is empty, then transaction will quickly become very heavy and slow.
     * <p>
     * If write-through is enabled, the values will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#removeAll(String, GridCacheTx, Collection)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Filter used to supply keys for remove operation (if {@code null},
     *      then nothing will be removed).
     * @throws GridException If remove failed.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void removeAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously removes mappings from cache for entries for which the optionally passed in filters do
     * pass. If passed in filters are {@code null}, then all entries in cache will be enrolled
     * into transaction.
     * <p>
     * <b>USE WITH CARE</b> - if your cache has many entries that pass through the filter or if filter
     * is empty, then transaction will quickly become very heavy and slow.
     * <p>
     * If write-through is enabled, the values will be removed from {@link GridCacheStore}
     * via {@link GridCacheStore#removeAll(String, GridCacheTx, Collection)} method.
     * <h2 class="header">Transactions</h2>
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Filter used to supply keys for remove operation (if {@code null},
     *      then nothing will be removed).
     * @return Future for the remove operation. The future will complete whenever
     *      remove operation completes.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<?> removeAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Synchronously acquires lock on a cached object with given
     * key only if the passed in filter (if any) passes. This method
     * together with filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to lock.
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return {@code True} if all filters passed and lock was acquired,
     *      {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lock(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously acquires lock on a cached object with given
     * key only if the passed in filter (if any) passes. This method
     * together with filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to lock.
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return Future for the lock operation. The future will return {@code true}
     *      whenever lock was successfully acquired, {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * Synchronously acquires lock on a cached object with given
     * key only if the passed in filter (if any) passes. This method
     * together with filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return {@code True} if all filters passed and lock was acquired,
     *      {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lock(K key, long timeout, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Asynchronously acquires lock on a cached object with given
     * key only if the passed in filter (if any) passes. This method
     * together with filter check will be executed as one atomic operation.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter to validate prior to acquiring the lock.
     * @return Future for the lock operation. The future will return {@code true}
     *      whenever all filters pass and locks are acquired before timeout is expired,
     *      {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAsync(K key, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * All or nothing synchronous lock for passed in filter. The filter
     * will be used to supply keys to be locked. This method together with
     * filter check will be executed as one atomic operation. If
     * at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Filter to supply keys to be locked.
     * @return {@code True} if filters passed and all locks were acquired, {@code false}
     *      otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * All or nothing asynchronous lock for passed in filter. The filter
     * will be used to supply keys to be locked. This method together with
     * filter check will be executed as one atomic operation. If
     * at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Filter to supply keys to be locked.
     * @return Future for the collection of locks. The future will return
     *      {@code true} if filters passed and all locks were acquired,
     *      {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * All or nothing synchronous lock for passed in filter. The filter
     * will be used to supply keys to be locked. This method together with
     * filter check will be executed as one atomic operation. If
     * at least one filter validation failed, no locks will be acquired.
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
     * @param filter Filter to supply keys to be locked.
     * @return {@code True} if all filters passed and locks were acquired before
     *      timeout has expired, {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(long timeout, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * All or nothing asynchronous lock for passed in filter. The filter
     * will be used to supply keys to be locked. This method together with
     * filter check will be executed as one atomic operation. If
     * at least one filter validation failed, no locks will be acquired.
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
     * @param filter Filter to supply keys to be locked.
     * @return Future for the collection of locks. The future will return
     *      {@code true} if all filters passed and locks were acquired before
     *      timeout has expired, {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation. If
     * at least one filter validation failed, no locks will be acquired.
     *
     * @param keys Keys to lock.
     * @param filter Optional filter that needs to atomically pass in order for
     *      the locks to be acquired.
     * @return {@code True} if all locks were acquired.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @param filter Optional filter that needs to atomically pass in order for the
     *      locks to be acquired.
     * @return Future for the collection of locks. The future will return
     *      {@code true} if all filters passed and locks were acquired, {@code false}
     *      otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter that needs to atomically pass in order for the locks
     *      to be acquired.
     * @return {@code True} if all filters passed and locks were acquired before
     *      timeout has expired, {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @param filter Optional filter that needs to atomically pass in order for the locks
     *      to be acquired.
     * @return Future for the collection of locks. The future will return
     *      {@code true} if all filters passed and locks were acquired before
     *      timeout has expired, {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter);

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @return This method always returns {@code true} or throws and exception.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(@Nullable K... keys) throws GridException;

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @return Future for the collection of locks. The future will return
     *      {@code true} after all locks were acquired.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(@Nullable K... keys);

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @return {@code True} if all locks were acquired before timeout expired,
     *      {@code false} otherwise.
     * @throws GridException If lock acquisition resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public boolean lockAll(long timeout, @Nullable K... keys) throws GridException;

    /**
     * All or nothing synchronous lock for passed in keys. This method
     * together with filter check will be executed as one atomic operation.
     * If at least one filter validation failed, no locks will be acquired.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to lock.
     * @param timeout Timeout in milliseconds to wait for lock to be acquired
     *      ({@code '0'} for no expiration).
     * @return Future for the collection of locks. The future will return
     *      {@code true} if all locks were acquired before timeout expired,
     *      {@code false} otherwise.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public GridFuture<Boolean> lockAllAsync(long timeout, @Nullable K... keys);

    /**
     * Unlocks given key only if current thread owns the lock. If optional filter
     * will not pass, then unlock will not happen. If the key being unlocked was
     * never locked by current thread, then this method will do nothing.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to unlock.
     * @param filter Optional filter that needs to pass prior to unlock taking effect.
     * @throws GridException If unlock execution resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unlock(K key, GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Unlocks given keys only if current thread owns the locks. Only the keys
     * that have been locked by calling thread will be unlocked. If none of the
     * key locks is owned by current thread, then this method will do nothing.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to unlock.
     * @throws GridException If unlock execution resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unlockAll(@Nullable K... keys) throws GridException;

    /**
     * Unlocks given keys only if current thread owns the locks. Only the keys
     * that have been locked by calling thread and pass through the filter (if any)
     * will be unlocked. If none of the key locks is owned by current thread, then
     * this method will do nothing.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to unlock.
     * @param filter Optional filter which needs to pass for individual entries
     *      to be unlocked.
     * @throws GridException If unlock execution resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unlockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) throws GridException;

    /**
     * Unlock the keys supplied by the given filter only if current thread owns
     * the locks. If none of the key locks is owned by current thread, then
     * this method will do nothing.
     * <h2 class="header">Transactions</h2>
     * Locks are not transactional and should not be used from within transactions. If you do
     * need explicit locking within transaction, then you should use
     * {@link GridCacheTxConcurrency#PESSIMISTIC} concurrency control for transaction
     * which will acquire explicit locks for relevant cache operations.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}, {@link GridCacheFlag#READ}.
     *
     * @param filter Filter used to supply keys from cache (if {@code null},
     *      then no key will be unlocked).
     * @throws GridException If unlock execution resulted in error.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unlockAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter)
        throws GridException;

    /**
     * Checks if any node owns a lock for this key.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param key Key to check.
     * @return {@code True} if lock is owned by some node.
     */
    public boolean isLocked(K key);

    /**
     * Checks if every key provided by the filter is locked by some grid node.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param filter Key filter used to supply keys.
     * @return {@code True} if all keys accepted by the given filter are
     *      locked by some nodes.
     */
    public boolean isAllLocked(@Nullable GridPredicate<? super K>... filter);

    /**
     * Checks if every key provided is locked by some grid node.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param keys Keys to check.
     * @return {@code True} if all of the locks are currently locked
     *      by any of the grid nodes.
     */
    public boolean isAllLocked(Collection<? extends K> keys);

    /**
     * Checks if every key provided is locked by some grid node.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param keys Keys to check.
     * @return {@code True} if all of the locks are currently locked
     *      by any of the grid nodes.
     */
    public boolean isAllLocked(K... keys);

    /**
     * Checks if every key provided by the filter is locked by the current thread.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param filter Key filter used to supply keys.
     * @return {@code True} if all keys accepted by the given filter are
     *      locked by current thread.
     */
    public boolean isAllLockedByThread(@Nullable GridPredicate<? super K>... filter);

    /**
     * Checks if every key provided is locked by the current thread.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param keys Keys to check.
     * @return {@code True} if every key provided is locked by the current thread.
     */
    public boolean isAllLockedByThread(@Nullable Collection<? extends K> keys);

    /**
     * Checks if every key provided is locked by the current thread.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param keys Keys to check.
     * @return {@code True} if every key provided is locked by the current thread.
     */
    public boolean isAllLockedByThread(@Nullable K... keys);

    /**
     * Checks if current thread owns a lock on this key.
     * <p>
     * This is a local in-VM operation and does not involve any network trips
     * or access to persistent storage in any way.
     *
     * @param key Key to check.
     * @return {@code True} if key is locked by current thread.
     */
    public boolean isLockedByThread(K key);

    /**
     * Gets size of this cache projection. Note that this method will have to
     * iterate through the map entries to count all values that satisfy this
     * projection.
     * <p>
     * If you would like to find out how many entries are there in cache
     * altogether (including {@code nulls}), you should use
     * {@link #keySize()} method on base {@link GridCache} which will
     * return total size of internal hash table in constant time without iteration.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size();

    /**
     * Gets size of cache key set. This method is different from {@link #size()}
     * method in a way that {@link #size()} method will only provide size for {@code not-null}
     * values and has complexity of O(N), while {@code keySize()} will return the count of
     * all cache entries and has O(1) complexity on base cache projection. It is essentially the
     * size of cache key set and is semantically identical to {{@code GridCache.keySet().size()}.
     *
     * @return Size of cache key set.
     */
    public int keySize();

    /**
     * This method provides ability to detect which keys are mapped to which nodes.
     * Use it to determine which nodes are storing which keys prior to sending
     * jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>
     *
     * @param keys Keys to map to nodes.
     * @return Map of node IDs to keys.
     */
    public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable Collection<? extends K> keys);

    /**
     * This method provides ability to detect which keys are mapped to which nodes.
     * Use it to determine which nodes are storing which keys prior to sending
     * jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>
     *
     * @param filter Filter for keys to check.
     * @return Map of node IDs to keys.
     */
    public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable GridPredicate<? super K>... filter);

    /**
     * This method provides ability to detect which keys are mapped to which nodes.
     * Use it to determine which nodes are storing which keys prior to sending
     * jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>
     *
     * @param keys Keys to map to nodes.
     * @return Map of node IDs to keys.
     */
    public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable K... keys);

    /**
     * This method provides ability to detect to which primary node the given key
     * is mapped. Use it to determine which nodes are storing which keys prior to sending
     * jobs that access these keys.
     * <p>
     * This method works as following:
     * <ul>
     * <li>For local caches it returns only local node ID.</li>
     * <li>
     *      For fully replicated caches first node ID returned by {@link GridCacheAffinity}
     *      is returned.
     * </li>
     * <li>For partitioned caches, primary node for the given key is returned.</li>
     * </ul>
     *
     * @param key Keys to map to a node.
     * @return ID of primary node for the key.
     */
    public UUID mapKeyToNode(K key);

    /**
     * Provides grid projection for all nodes participating in this cache.
     *
     * @return Grid projection for all cache nodes.
     */
    public GridProjection gridProjection();

    /**
     * This method provides {@link GridProjection} for all nodes to which the
     * passed in keys are mapped. It works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>

     * @param keys Keys to map to nodes.
     * @return Grid projection for all nodes to which the passed in keys are mapped.
     */
    public GridProjection gridProjection(@Nullable Collection<? extends K> keys);

    /**
     * This method provides {@link GridProjection} for all nodes to which the
     * passed in keys are mapped. It works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>

     * @param keys Keys to map to nodes.
     * @return Grid projection for all nodes to which the passed in keys are mapped.
     */
    public GridProjection gridProjection(@Nullable K... keys);

    /**
     * This method provides {@link GridProjection} for all nodes to which all the
     * keys whose entries pass through the filter are mapped. It works as following:
     * <ul>
     * <li>For local caches it returns only local node mapped to all keys.</li>
     * <li>
     *      For fully replicated caches {@link GridCacheAffinity} is
     *      used to determine which keys are mapped to which nodes.
     * </li>
     * <li>For partitioned caches, the returned map represents node-to-key affinity.</li>
     * </ul>

     * @param filter Filter used to supply keys.
     * @return Grid projection for all nodes to which all keys that pass the filter are mapped.
     */
    public GridProjection gridProjection(@Nullable GridPredicate<? super K>... filter);

    /**
     * This method unswaps cache entry by given key, if any, from swap storage
     * into memory.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#SKIP_SWAP}, {@link GridCacheFlag#READ}.
     *
     * @param key Key to unswap entry for.
     * @return Unswapped entry value or {@code null} for non-existing key.
     * @throws GridException If unswap failed.
     * @throws GridCacheFlagException If flags validation failed.
     */
    @Nullable public V unswap(K key) throws GridException;

    /**
     * This method unswaps cache entries by given keys, if any, from swap storage
     * into memory.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#SKIP_SWAP}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to unswap entries for.
     * @throws GridException If unswap failed.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unswapAll(@Nullable Collection<? extends K> keys) throws GridException;

    /**
     * This method unswaps cache entries by given keys, if any, from swap storage
     * into memory.
     * <h2 class="header">Transactions</h2>
     * This method is not transactional.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#SKIP_SWAP}, {@link GridCacheFlag#READ}.
     *
     * @param keys Keys to unswap entries for.
     * @throws GridException If unswap failed.
     * @throws GridCacheFlagException If flags validation failed.
     */
    public void unswapAll(@Nullable K... keys) throws GridException;

    /**
     * Starts new transaction with default isolation, concurrency, and invalidation
     * policy and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param closures Closures to execute in transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public void inTx(GridInClosure<GridCacheProjection<K, V>>... closures) throws IllegalStateException, GridException;

    /**
     * Starts new transaction with default isolation, concurrency, and invalidation
     * policy and sequentially executes given closures within it. Unlike its sibling
     * {@link #inTx(GridInClosure[])} this method does not block and immediately returns
     * execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param closures Closures to execute in transaction.
     * @return Execution future.
     * @throws GridException If cache failure occurred.
     */
    public GridFuture<?> inTxAsync(GridInClosure<GridCacheProjection<K, V>>... closures) throws GridException;

    /**
     * Starts new transaction with the specified timeout, isolation, concurrency
     * and invalidation policy and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param timeout Transaction timeout.
     * @param closures Closures to execute in transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public void inTx(long timeout, GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts new transaction with the specified timeout, isolation, concurrency
     * and invalidation policy and sequentially executes given closures within it.
     * Unlike its sibling {@link #inTx(long, GridInClosure[])} this method does not
     * block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param timeout Transaction timeout.
     * @param closures Closures to execute in transaction.
     * @return Execution future.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public GridFuture<?> inTxAsync(long timeout, GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation and concurrency and sequentially
     * executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param closures Closures to execute in transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation and concurrency and sequentially executes
     * given closures within it. Unlike its sibling
     * {@link #inTx(GridCacheTxConcurrency, GridCacheTxIsolation, GridInClosure[])}
     * this method does not block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param closures Closures to execute in transaction.
     * @return Execution future.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation, concurrency, timeout, and invalidation flag
     * and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param closures Closures to execute in transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation, concurrency, timeout, and invalidation flag
     * and sequentially executes given closures within it. Unlike its sibling
     * {@link #inTx(GridCacheTxConcurrency, GridCacheTxIsolation, long, boolean, GridInClosure[])}
     * this method does not block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param closures Closures to execute in transaction.
     * @return Execution future.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridInClosure<GridCacheProjection<K, V>>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Collection of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> Collection<R> inTx(GridOutClosure<? super R>... closures) throws IllegalStateException, GridException;

    /**
     * Starts transaction and sequentially executes given closures within it. Unlike
     * its sibling {@link #inTx(GridOutClosure[])} this method does not block and
     * immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Future of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> GridFuture<Collection<R>> inTxAsync(GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts new transaction with the specified timeout, isolation, concurrency
     * and invalidation policy and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param timeout Transaction timeout.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Collection of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> Collection<R> inTx(long timeout, GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts new transaction with the specified timeout, isolation, concurrency
     * and invalidation policy and sequentially executes given closures within it.
     * Unlike its sibling {@link #inTx(long, GridOutClosure[])} this method does not
     * block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param timeout Transaction timeout.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Future of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> GridFuture<Collection<R>> inTxAsync(long timeout, GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation and concurrency and sequentially
     * executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Collection of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation and concurrency and sequentially executes
     * given closures within it. Unlike its sibling
     * {@link #inTx(GridCacheTxConcurrency, GridCacheTxIsolation, GridOutClosure[])}
     * this method does not block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Future of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation, concurrency, timeout, and invalidation flag
     * and sequentially executes given closures within it.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Collection of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Starts transaction with specified isolation, concurrency, timeout, and invalidation flag
     * and sequentially executes given closures within it. Unlike its sibling
     * {@link #inTx(GridCacheTxConcurrency, GridCacheTxIsolation, long, boolean, GridOutClosure[])}
     * this method does not block and immediately returns execution future.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#LOCAL}.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param closures Closures to execute in transaction.
     * @param <R> Type of out-closure return value.
     * @return Future of execution results.
     * @throws IllegalStateException If transaction is already started by this thread.
     * @throws GridException If cache failure occurred.
     */
    public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridOutClosure<? super R>... closures)
        throws IllegalStateException, GridException;

    /**
     * Converts this API into standard Java {@link ConcurrentMap} interface.
     *
     * @return {@link ConcurrentMap} representation of given cache.
     */
    public ConcurrentMap<K, V> toMap();
}
