package org.gridgain.scalar.pimps

// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

import org.gridgain.scalar._
import scalar._
import org.gridgain.grid.lang.GridPredicate
import org.gridgain.grid.cache._
import org.jetbrains.annotations.Nullable
import org.gridgain.grid._
import collection._
import JavaConversions._

/**
 * ==Overview==
 * Defines Scalar "pimp" for `GridCacheProjection` on Java side.
 *
 * Essentially this class extends Java `GridCacheProjection` interface with Scala specific
 * API adapters using primarily implicit conversions defined in `ScalarMixin` object. What
 * it means is that you can use functions defined in this class on object
 * of Java `GridCacheProjection` type. Scala will automatically (implicitly) convert it into
 * Scalar's pimp and replace the original call with a call on that pimp.
 *
 * Note that Scalar provide extensive library of implicit conversion between Java and
 * Scala GridGain counterparts in `ScalarMixin` object
 *
 * ==Suffix '$' In Names==
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Scala pimp is shadowing or with Java package name that your Scala code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Scala's side method with `$` suffix.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class ScalarCacheProjectionPimp[@specialized K, @specialized V](private val impl: GridCacheProjection[K, V]) extends
    Iterable[GridCacheEntry[K, V]] {
    if (impl == null)
        throw new GridException("Implementation is set to 'null'.")

    /** Type alias. */
    protected type EntryPred = (_ >: GridCacheEntry[K, V]) => Boolean

    /** Type alias. */
    protected type KvPred = (K, V) => Boolean

    /**
     * Gets iterator for cache entries.
     */
    def iterator =
        toScalaSeq(impl.iterator).iterator

    /**
     * Unwraps sequence of functions to sequence of GridGain predicates.
     */
    private def unwrap(@Nullable p: Seq[EntryPred]): Seq[GridPredicate[_ >: GridCacheEntry[K, V]]] =
        if (p == null)
            null
        else
            p map ((f: EntryPred) => toPredicate(f))

    /**
     * Retrieves value mapped to the specified key from cache. The return value of {@code null}
     * means entry did not pass the provided filter or cache has no mapping for the key.
     *
     * @param k Key to retrieve the value for.
     * @return Value for the given key.
     */
    def apply(k: K): V =
        impl.get(k)

    /**
     * Retrieves value mapped to the specified key from cache as an option. The return value
     * of `null` means entry did not pass the provided filter or cache has no mapping for the key.
     *
     * @param k Key to retrieve the value for.
     * @param p Filter to check prior to getting the value. Note that filter check
     *      together with getting the value is an atomic operation.
     * @return Value for the given key.
     * @see `org.gridgain.grid.cache.GridCacheProjection.get(...)`
     */
    def opt(k: K, p: EntryPred*): Option[V] =
        Option(impl.get(k, unwrap(p): _*))

    /**
     * Gets cache projection based on given key-value predicate. Whenever makes sense,
     * this predicate will be used to pre-filter cache operations. If
     * operation passed pre-filtering, this filter will be passed through
     * to cache operations as well.
     *
     * @param p Key-value predicate for this projection. If `null`, then the
     *      same projection is returned.
     * @return Projection for given key-value predicate.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByKv(@Nullable p: ((K, V) => Boolean)*): GridCacheProjection[K, V] =
        if (p == null || p.length == 0)
            impl
        else
            impl.projection(p.map((f: ((K, V) => Boolean)) => toPredicate2(f)): _*)

    /**
     * Gets cache projection based on given entry filter. This filter will be simply passed through
     * to all cache operations on this projection. Unlike `viewByKv` function, this filter
     * will '''not''' be used for pre-filtering.
     *
     * @param p Filter to be passed through to all cache operations. If `null`, then the
     *      same projection is returned.  If cache operation receives its own filter, then filters
     *      will be `anded`.
     * @return Projection based on given filter.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByEntry(@Nullable p: EntryPred*): GridCacheProjection[K, V] =
        if (p == null || p.length == 0)
            impl
        else
            impl.projection(unwrap(p): _*)

    /**
     * Gets cache projection only for given key and value type. Only `non-null` key-value
     * pairs that have matching key and value pairs will be used in this projection.
     *
     * ===Cache Flags===
     * The resulting projection will have flag `GridCacheFlag#STRICT` set on it.
     *
     * @param k Key type.
     * @param v Value type.
     * @return Cache projection for given key and value types.
     * @see `org.gridgain.grid.cache.GridCacheProjection.projection(...)`
     */
    def viewByType[A <: K, B <: V](k: Class[A], v: Class[B]):
        GridCacheProjection[A, B] = {
        assert(k != null && v != null)

        impl.projection(toJavaType(k), toJavaType(v));
    }

    /**
     * Converts given type of corresponding Java type, if Scala does
     * auto-conversion for given type. Only primitive types and Strings
     * are supported.
     *
     * @param c Type to convert.
     */
    private def toJavaType(c: Class[_]) = {
        assert(c != null)

        if (c == classOf[Int])
            classOf[java.lang.Integer]
        else if (c == classOf[Boolean])
            classOf[java.lang.Boolean]
        else if (c == classOf[String])
            classOf[java.lang.String]
        else if (c == classOf[Char])
            classOf[java.lang.Character]
        else if (c == classOf[Long])
            classOf[java.lang.Long]
        else if (c == classOf[Double])
            classOf[java.lang.Double]
        else if (c == classOf[Float])
            classOf[java.lang.Float]
        else if (c == classOf[Short])
            classOf[java.lang.Short]
        else if (c == classOf[Byte])
            classOf[java.lang.Byte]
        else
            c
    }

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return `True` if value was stored in cache, `false` otherwise.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putx(...)`
     */
    def putx$(kv: (K, V), @Nullable p: EntryPred*): Boolean =
        impl.putx(kv._1, kv._2, unwrap(p): _*)

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Previous value associated with specified key, or `null`
     *      if entry did not pass the filter, or if there was no mapping for the key in swap
     *      or in persistent storage.
     * @see `org.gridgain.grid.cache.GridCacheProjection#put(...)`
     */
    def put$(kv: (K, V), @Nullable p: EntryPred*): V =
        impl.put(kv._1, kv._2, unwrap(p): _*)

    /**
     * Stores given key-value pair in cache. If filters are provided, then entries will
     * be stored in cache only if they pass the filter. Note that filter check is atomic,
     * so value stored in cache is guaranteed to be consistent with the filters.
     * <p>
     * If write-through is enabled, the stored value will be persisted to `GridCacheStore`
     * via `GridCacheStore#put(String, GridCacheTx, Object, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return Previous value associated with specified key as an option.
     * @see `org.gridgain.grid.cache.GridCacheProjection#put(...)`
     */
    def putOpt$(kv: (K, V), @Nullable p: EntryPred*): Option[V] =
        Option(impl.put(kv._1, kv._2, unwrap(p): _*))

    /**
     * Operator alias for the same function `putx$`.
     *
     * @param kv Key-Value pair to store in cache.
     * @param p Optional filter to check prior to putting value in cache. Note
     *      that filter check is atomic with put operation.
     * @return `True` if value was stored in cache, `false` otherwise.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putx(...)`
     */
    def +=(kv: (K, V), @Nullable p: EntryPred*): Boolean =
        putx$(kv, p: _*)

    /**
     * Stores given key-value pairs in cache.
     *
     * If write-through is enabled, the stored values will be persisted to `GridCacheStore`
     * via `GridCacheStore#putAll(String, GridCacheTx, Map)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kv1 Key-value pair to store in cache.
     * @param kv2 Key-value pair to store in cache.
     * @param kvs Optional key-value pairs to store in cache.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def putAll$(kv1: (K, V), kv2: (K, V), @Nullable kvs: (K, V)*) {
        var m = mutable.Map.empty[K, V]

        m += (kv1, kv2)

        if (kvs != null)
            kvs foreach (m += _)

        impl.putAll(m)
    }

    /**
     * Stores given key-value pairs from the sequence in cache.
     *
     * If write-through is enabled, the stored values will be persisted to `GridCacheStore`
     * via `GridCacheStore#putAll(String, GridCacheTx, Map)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param kvs Key-value pairs to store in cache. If `null` this function is no-op.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def putAll$(@Nullable kvs: Seq[(K, V)]) {
        if (kvs != null)
            impl.putAll(mutable.Map(kvs: _*))
    }

    /**
     * Removes given key mappings from cache.
     *
     * If write-through is enabled, the values will be removed from `GridCacheStore`
     * via `GridCacheStore#removeAll(String, GridCacheTx, Collection)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param ks Sequence of additional keys to remove. If `null` - this function is no-op.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def removeAll$(@Nullable ks: Seq[K]) {
        if (ks != null)
            impl.removeAll(ks)
    }

    /**
     * Operator alias for the same function `putAll$`.
     *
     * @param kv1 Key-value pair to store in cache.
     * @param kv2 Key-value pair to store in cache.
     * @param kvs Optional key-value pairs to store in cache.
     * @see `org.gridgain.grid.cache.GridCacheProjection#putAll(...)`
     */
    def +=(kv1: (K, V), kv2: (K, V), @Nullable kvs: (K, V)*) {
        putAll$(kv1, kv2, kvs: _*)
    }

    /**
     * Removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of `GridCacheMode#REPLICATED` caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying `GridCacheStore` storage. In case of `GridCacheMode#PARTITIONED`
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, `GridCacheStore#load(String, GridCacheTx, Object)` method will be used.
     *
     * If the returned value is not needed, method `removex$(...)` should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     *
     * If write-through is enabled, the value will be removed from 'GridCacheStore'
     * via `GridCacheStore#remove(String, GridCacheTx, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key, or `null`
     *      if there was no value for this key.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def remove$(k: K, @Nullable p: EntryPred*): V =
        impl.remove(k, unwrap(p): _*)

    /**
     * Removes given key mapping from cache. If cache previously contained value for the given key,
     * then this value is returned. Otherwise, in case of `GridCacheMode#REPLICATED` caches,
     * the value will be loaded from swap and, if it's not there, and read-through is allowed,
     * from the underlying `GridCacheStore` storage. In case of `GridCacheMode#PARTITIONED`
     * caches, the value will be loaded from the primary node, which in its turn may load the value
     * from the swap storage, and consecutively, if it's not in swap and read-through is allowed,
     * from the underlying persistent storage. If value has to be loaded from persistent
     * storage, `GridCacheStore#load(String, GridCacheTx, Object)` method will be used.
     *
     * If the returned value is not needed, method `removex$(...)` should
     * always be used instead of this one to avoid the overhead associated with returning of the
     * previous value.
     *
     * If write-through is enabled, the value will be removed from 'GridCacheStore'
     * via `GridCacheStore#remove(String, GridCacheTx, Object)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key as an option.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def removeOpt$(k: K, @Nullable p: EntryPred*): Option[V] =
        Option(impl.remove(k, unwrap(p): _*))

    /**
     * Operator alias for the same function `remove$`.
     *
     * @param k Key whose mapping is to be removed from cache.
     * @param p Optional filters to check prior to removing value form cache. Note
     *      that filter is checked atomically together with remove operation.
     * @return Previous value associated with specified key, or `null`
     *      if there was no value for this key.
     * @see `org.gridgain.grid.cache.GridCacheProjection#remove(...)`
     */
    def -=(k: K, @Nullable p: EntryPred*): V =
        remove$(k, p: _*)

    /**
     * Removes given key mappings from cache.
     *
     * If write-through is enabled, the values will be removed from `GridCacheStore`
     * via `GridCacheStore#removeAll(String, GridCacheTx, Collection)` method.
     *
     * ===Transactions===
     * This method is transactional and will enlist the entry into ongoing transaction
     * if there is one.
     *
     * ===Cache Flags===
     * This method is not available if any of the following flags are set on projection:
     * `GridCacheFlag#LOCAL`, `GridCacheFlag#READ`.
     *
     * @param k1 1st key to remove.
     * @param k2 2nd key to remove.
     * @param ks Optional sequence of additional keys to remove.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def removeAll$(k1: K, k2: K, @Nullable ks: K*) {
        var s = new mutable.ArraySeq[K](2 + (if (ks == null) 0 else ks.length))

        k1 +: s
        k2 +: s

        if (ks != null)
            ks foreach (_ +: s)

        impl.removeAll(s)
    }

    /**
     * Operator alias for the same function `remove$`.
     *
     * @param k1 1st key to remove.
     * @param k2 2nd key to remove.
     * @param ks Optional sequence of additional keys to remove.
     * @see `org.gridgain.grid.cache.GridCacheProjection#removeAll(...)`
     */
    def -=(k1: K, k2: K, @Nullable ks: K*) {
        removeAll$(k1, k2, ks: _*)
    }
}
