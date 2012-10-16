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
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Cache proxy.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheProxyImpl<K, V> implements GridCacheProxy<K, V>, Externalizable {
    /** Context. */
    private GridCacheContext<K, V> ctx;

    /** Gateway. */
    private GridCacheGateway<K, V> gate;

    /** Cache. */
    @GridToStringInclude
    private GridCache<K, V> cache;

    /** Projection. */
    @GridToStringExclude
    private GridCacheProjectionImpl<K, V> prj;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheProxyImpl() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param prj Projection.
     */
    public GridCacheProxyImpl(GridCacheContext<K, V> ctx, GridCacheProjectionImpl<K, V> prj) {
        assert ctx != null;

        this.ctx = ctx;
        this.prj = prj;

        gate = ctx.gate();
        cache = ctx.cache();
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return cache.name();
    }

    /** {@inheritDoc} */
    @Nullable @Override public <K, V> GridCacheProjection<K, V> parent() {
        return cache.parent();
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCache<K, V> cache() {
        return cache.cache();
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        cache.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        cache.copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 addMeta(String name, V1 val) {
        return cache.addMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 putMetaIfAbsent(String name, V1 val) {
        return cache.putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 putMetaIfAbsent(String name, Callable<V1> c) {
        return cache.putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V1> V1 addMetaIfAbsent(String name, V1 val) {
        return cache.addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Nullable @Override public <V1> V1 addMetaIfAbsent(String name, @Nullable Callable<V1> c) {
        return cache.addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <V1> V1 meta(String name) {
        return cache.<V1>meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"RedundantTypeArguments"})
    @Override public <V1> V1 removeMeta(String name) {
        return cache.<V1>removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V1> boolean removeMeta(String name, V1 val) {
        return cache.removeMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V1> Map<String, V1> allMeta() {
        return cache.allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return cache.hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V1> boolean hasMeta(String name, V1 val) {
        return cache.hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V1> boolean replaceMeta(String name, V1 curVal, V1 newVal) {
        return cache.replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public GridCacheConfiguration configuration() {
        return cache.configuration();
    }

    /** {@inheritDoc} */
    @Override public void txSynchronize(@Nullable GridCacheTxSynchronization[] syncs) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.txSynchronize(syncs);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void txUnsynchronize(@Nullable GridCacheTxSynchronization[] syncs) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.txUnsynchronize(syncs);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxSynchronization> txSynchronizations() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.txSynchronizations();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.metrics();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheQueryMetrics> queryMetrics() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.queryMetrics();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public long overflowSize() throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.overflowSize();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void loadCache(GridPredicate2<K, V> p, long ttl, @Nullable Object[] args) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.loadCache(p, ttl, args);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> loadCacheAsync(GridPredicate2<K, V> p, long ttl, @Nullable Object[] args) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.loadCacheAsync(p, ttl, args);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheEntry<K, V> randomEntry() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.randomEntry();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public ConcurrentMap<K, V> toMap() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.toMap();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.flags();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridPredicate2<K, V> keyValuePredicate() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keyValuePredicate();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<? super GridCacheEntry<K, V>> entryPredicate() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entryPredicate();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <K1, V1> GridCacheProjection<K1, V1> projection(Class<?> keyType, Class<?> valType) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.projection(keyType, valType);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> projection(@Nullable GridPredicate2<K, V>[] p) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.projection(p);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> projection(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.projection(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOn(@Nullable GridCacheFlag[] flags) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.flagsOn(flags);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOff(@Nullable GridCacheFlag[] flags) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.flagsOff(flags);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isEmpty();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsKey(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllKeys(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllKeys(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsValue(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsValue(val, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllKeys(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyKeys(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyKeys(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyKeys(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllValues(vals, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable V[] vals) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllValues(vals);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable GridPredicate<? super V>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllValues(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyValues(vals, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable V[] vals) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyValues(vals);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable GridPredicate<? super V>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyValues(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAnyEntries(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.containsAllEntries(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.forEach(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forEachAsync(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.forEach(vis);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forEachAsync(vis);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis, @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.forEach(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis, @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forEachAsync(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAll(vis);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAllAsync(vis);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAll(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAllAsync(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAll(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.forAllAsync(vis, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduce(rdc);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduceAsync(rdc);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduce(rdc, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduceAsync(rdc, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc, @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduce(rdc, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reduceAsync(rdc, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V reload(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reload(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reloadAsync(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void reloadAll() throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.reloadAll();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reloadAllAsync();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.reloadAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reloadAllAsync(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.reloadAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reloadAllAsync(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.reloadAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.reloadAllAsync(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peek(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V get(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.get(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAsync(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAllAsync(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAllAsync(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.getAllAsync(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V put(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.put(key, val, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putAsync(K key, V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putAsync(key, val, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean putx(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putx(key, val, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxAsync(K key, V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putxAsync(key, val, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V putIfAbsent(K key, V val) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putIfAbsent(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putIfAbsentAsync(K key, V val) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putIfAbsentAsync(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean putxIfAbsent(K key, V val) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putxIfAbsent(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxIfAbsentAsync(K key, V val) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putxIfAbsentAsync(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V replace(K key, V val) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replace(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(K key, V val) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replaceAsync(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(K key, V val) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replacex(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(K key, V val) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replacexAsync(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean replace(K key, V oldVal, V newVal) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replace(key, oldVal, newVal);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(K key, V oldVal, V newVal) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.replaceAsync(key, oldVal, newVal);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void putAll(@Nullable Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.putAll(m, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> putAllAsync(@Nullable Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.putAllAsync(m, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keySet();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keySet(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keySet(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keySet(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.values();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.values(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.values(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.values(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entrySet();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entrySet(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entrySet(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entrySet(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createQuery();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createQuery(type);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable Class<?> cls,
        @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createQuery(type, cls, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable String clsName,
        @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createQuery(type, clsName, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createTransformQuery();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createTransformQuery(type);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createTransformQuery(type, cls, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createTransformQuery(type, clsName, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createReduceQuery();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createReduceQuery(type);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createReduceQuery(type, cls, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.createReduceQuery(type, clsName, clause);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart() throws IllegalStateException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.txStart();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(long timeout) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.txStart(timeout);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.txStart(concurrency, isolation);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.txStart(concurrency, isolation, timeout, invalidate);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx tx() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.tx();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(K key) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peek(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(K key, @Nullable GridCachePeekMode[] modes) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peek(key, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(K key, @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peek(key, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(K key, @Nullable GridCachePeekMode... modes) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAsync(key, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(K key, @Nullable Collection<GridCachePeekMode> modes) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAsync(key, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys, @Nullable GridCachePeekMode[] modes)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(keys, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAll(keys, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridCachePeekMode... modes) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAllAsync(keys, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.peekAllAsync(keys, modes);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheEntry<K, V> entry(K key) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.entry(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean evict(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.evict(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.evictAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.evictAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void evictAll() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.evictAll();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.evictAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.clearAll();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.clearAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.clearAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.clearAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean clear(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.clear(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.invalidate(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.invalidateAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.invalidateAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean compact(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.compact(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.compactAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.compactAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V remove(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.remove(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removeAsync(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean removex(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removex(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removexAsync(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean remove(K key, V val) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.remove(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(K key, V val) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removeAsync(key, val);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.removeAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removeAllAsync(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.removeAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.removeAllAsync(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lock(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAsync(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, long timeout, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lock(key, timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAsync(key, timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(keys, timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(keys, timeout, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout, @Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAll(timeout, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout, @Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.lockAllAsync(timeout, keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unlock(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unlock(key, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unlockAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unlockAll(keys, filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unlockAll(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked(K key) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isLocked(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLocked(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLocked(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLocked(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLockedByThread(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLockedByThread(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isAllLockedByThread(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread(K key) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.isLockedByThread(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public int size() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.size();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public int keySize() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.keySize();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.mapKeysToNodes(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.mapKeysToNodes(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.mapKeysToNodes(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public UUID mapKeyToNode(K key) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.mapKeyToNode(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.gridProjection();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable Collection<? extends K> keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.gridProjection(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable K[] keys) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.gridProjection(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable GridPredicate<? super K>[] filter) {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.gridProjection(filter);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V unswap(K key) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.unswap(key);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable Collection<? extends K> keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unswapAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable K[] keys) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.unswapAll(keys);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.inTx(closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void inTx(long timeout, GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.inTx(timeout, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(long timeout, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(timeout, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.inTx(concurrency, isolation, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(concurrency, isolation, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation, long timeout,
        boolean invalidate, GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            cache.inTx(concurrency, isolation, timeout, invalidate, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridInClosure<GridCacheProjection<K, V>>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(concurrency, isolation, timeout, invalidate, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridOutClosure<? super R>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTx(closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridOutClosure<? super R>[] closures)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(long timeout, GridOutClosure<? super R>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTx(timeout, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(long timeout, GridOutClosure<? super R>[] closures)
        throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(timeout, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridOutClosure<? super R>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTx(concurrency, isolation, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, GridOutClosure<? super R>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(concurrency, isolation, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridOutClosure<? super R>[] closures) throws GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTx(concurrency, isolation, timeout, invalidate, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate, GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.inTxAsync(concurrency, isolation, timeout, invalidate, closures);
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridCacheEntry<K, V>> iterator() {
        GridCacheProjectionImpl<K, V> prev = gate.enter(prj);

        try {
            return cache.iterator();
        }
        finally {
            gate.leave(prev);
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = (GridCacheContext<K, V>)in.readObject();

        gate = ctx.gate();
        cache = ctx.cache();
    }

    /**
     * @return Context.
     */
    public GridCacheContext<K, V> context() {
        return ctx;
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicSequence atomicSequence(String name) throws GridException {
        return ctx.dataStructures().sequence(name, 0L, false, true);
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicSequence atomicSequence(String name, long initVal, boolean persistent) throws GridException {
        return ctx.dataStructures().sequence(name, initVal, persistent, true);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicSequence(String name) throws GridException {
        return ctx.dataStructures().removeSequence(name);
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicLong atomicLong(String name) throws GridException {
        return ctx.dataStructures().atomicLong(name, 0L, false, true);
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicLong atomicLong(String name, long initVal, boolean persistent) throws GridException {
        return ctx.dataStructures().atomicLong(name, initVal, persistent, true);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicLong(String name) throws GridException {
        return ctx.dataStructures().removeAtomicLong(name);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheAtomicReference<T> atomicReference(String name) throws GridException {
        return ctx.dataStructures().atomicReference(name, null, false, true);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheAtomicReference<T> atomicReference(String name, T initVal, boolean persistent)
        throws GridException {
        return ctx.dataStructures().atomicReference(name, initVal, persistent, true);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicReference(String name) throws GridException {
        return ctx.dataStructures().removeAtomicReference(name);
    }

    /** {@inheritDoc} */
    @Override public <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name) throws GridException {
        return ctx.dataStructures().atomicStamped(name, null, null, true);
    }

    /** {@inheritDoc} */
    @Override public <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name, T initVal, S initStamp)
        throws GridException {
        return ctx.dataStructures().atomicStamped(name, initVal, initStamp, true);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAtomicStamped(String name) throws GridException {
        return ctx.dataStructures().removeAtomicStamped(name);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheQueue<T> queue(String name) throws GridException {
        return ctx.dataStructures().queue(name, GridCacheQueueType.FIFO, Integer.MAX_VALUE, true, true);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type) throws GridException {
        return ctx.dataStructures().queue(name, type, Integer.MAX_VALUE, true, true);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int capacity)
        throws GridException {
        return ctx.dataStructures().queue(name, type, capacity <= 0 ? Integer.MAX_VALUE : capacity, true, true);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int capacity,
        boolean collocated) throws GridException {
        return ctx.dataStructures().queue(name, type, capacity <= 0 ? Integer.MAX_VALUE : capacity, collocated, true);
    }

    /** {@inheritDoc} */
    @Override public boolean removeQueue(String name) throws GridException {
        return ctx.dataStructures().removeQueue(name);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheProxyImpl.class, this);
    }
}
