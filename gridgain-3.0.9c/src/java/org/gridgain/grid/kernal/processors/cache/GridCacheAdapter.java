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
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.jta.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import javax.transaction.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.Collections.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Adapter for different cache implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheAdapter<K, V> extends GridMetadataAwareAdapter implements GridCache<K, V>,
    Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<String, String>> stash = new ThreadLocal<GridTuple2<String, String>>() {
        @Override protected GridTuple2<String, String> initialValue() {
            return F.t2();
        }
    };

    /** Cache configuration. */
    @GridToStringExclude
    protected GridCacheContext<K, V> ctx;

    /** Local map. */
    @GridToStringExclude
    protected GridCacheMap<K, V> map;

    /** Local node ID. */
    @GridToStringExclude
    protected UUID locNodeId;

    /** Cache configuration. */
    @GridToStringExclude
    protected GridCacheConfigurationAdapter cacheCfg;

    /** Grid configuration. */
    @GridToStringExclude
    protected GridConfiguration gridCfg;

    /** Cache metrics. */
    protected final GridCacheMetricsAdapter metrics = new GridCacheMetricsAdapter();

    /** */
    private ThreadLocal<GridCacheXAResource> xaRsrc = new ThreadLocal<GridCacheXAResource>();

    /** */
    private TransactionManager jtaTm;

    /** Logger. */
    protected GridLogger log;

    /** {@inheritDoc} */
    @Override public String name() {
        return ctx.config().getName();
    }

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    protected GridCacheAdapter() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     * @param startSize Start size.
     */
    @SuppressWarnings({"OverriddenMethodCallDuringObjectConstruction"})
    protected GridCacheAdapter(GridCacheContext<K, V> ctx, int startSize) {
        assert ctx != null;

        this.ctx = ctx;

        gridCfg = ctx.gridConfig();
        cacheCfg = ctx.config();

        locNodeId = ctx.gridConfig().getNodeId();

        map = new GridCacheMap<K, V>(ctx, startSize, 0.75F);

        log = ctx.gridConfig().getGridLogger().getLogger(getClass());

        init();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public <K, V> GridCacheProjection<K, V> parent() {
        return null;
    }

    /**
     * @return Base map.
     */
    GridCacheMap<K, V> map() {
        return map;
    }

    /**
     * @return Context.
     */
    public GridCacheContext<K, V> context() {
        return ctx;
    }

    /**
     * @return Logger.
     */
    protected GridLogger log() {
        return log;
    }

    /**
     * @return Preloader.
     */
    protected abstract GridCachePreloader<K, V> preloader();

    /**
     * Read lock.
     */
    void readLock() {
        map.readLock();
    }

    /**
     * Read unlock.
     */
    void readUnlock() {
        map.readUnlock();
    }

    /**
     * Write lock.
     */
    void writeLock() {
        map.writeLock();
    }

    /**
     * Write unlock.
     */
    void writeUnlock() {
        map.writeUnlock();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCache<K, V> cache() {
        return (GridCache<K, V>)this;
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        return F.asSet(ctx.forcedFlags());
    }

    /** {@inheritDoc} */
    @Override public GridPredicate2<K, V> keyValuePredicate() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<? super GridCacheEntry<K, V>> entryPredicate() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOn(@Nullable GridCacheFlag[] flags) {
        if (F.isEmpty(flags)) {
            return this;
        }

        return new GridCacheProjectionImpl<K, V>(this, ctx, null, null, EnumSet.copyOf(F.asList(flags)));
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOff(@Nullable GridCacheFlag[] flags) {
        return this;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K1, V1> GridCacheProjection<K1, V1> projection(Class<?> keyType, Class<?> valType) {
        try {
            ctx.deploy().registerClasses(keyType, valType);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K1, V1>(
            (GridCacheProjection<K1, V1>)this,
            (GridCacheContext<K1, V1>)ctx,
            CU.<K1, V1>typeFilter(keyType, valType),
            null, null);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> projection(GridPredicate2<K, V>[] p) {
        if (F.isEmpty(p)) {
            return this;
        }

        try {
            ctx.deploy().registerClasses(p);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K, V>(this, ctx, p, null, null);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> projection(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(filter)) {
            return this;
        }

        try {
            ctx.deploy().registerClasses(filter);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K, V>(this, ctx, null, filter, null);
    }

    /** {@inheritDoc} */
    @Override public GridCacheConfiguration configuration() {
        return ctx.config();
    }

    /**
     *
     * @param keys Keys to lock.
     * @param timeout Lock timeout.
     * @param tx Transaction.
     * @param isRead {@code True} for read operations.
     * @param retval Flag to return value.
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param filter Optional filter.
     * @return Locks future.
     */
    public abstract GridFuture<Boolean> txLockAsync(
        Collection<? extends K> keys,
        long timeout,
        GridCacheTxLocalEx<K, V> tx,
        boolean isRead,
        boolean retval,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @param implicit {@code True} if transaction is implicit.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout transaction timeout.
     * @param invalidate Invalidation flag.
     * @param syncCommit Synchronous commit flag.
     * @param syncRollback Synchronous rollback flag.
     * @param swapEnabled If {@code true} then swap storage will be used.
     * @param storeEnabled if {@code true} then read/write through will be used.
     * @return New transaction.
     */
    public abstract GridCacheTxLocalAdapter<K, V> newTx(
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean swapEnabled,
        boolean storeEnabled);

    /**
     * Creates transaction with all defaults.
     *
     * @param implicit Implicit flag.
     * @return New transaction.
     */
    private GridCacheTxLocalAdapter<K, V> newTx(boolean implicit) {
        GridCacheConfigurationAdapter cfg = ctx.config();

        GridCacheTxConcurrency concurrency = implicit ? PESSIMISTIC : cfg.getDefaultTxConcurrency();
        GridCacheTxIsolation isolation = implicit ? READ_COMMITTED : cfg.getDefaultTxIsolation();

        return newTx(
            implicit,
            concurrency,
            isolation,
            cfg.getDefaultTxTimeout(),
            ctx.isInvalidate(),
            ctx.syncCommit(),
            ctx.syncRollback(),
            ctx.isSwapEnabled(),
            ctx.isStoreEnabled()
        );
    }

    /**
     * Post constructor initialization for subclasses.
     */
    protected void init() {
        // No-op.
    }

    /**
     * Starts this cache. Child classes should override this method
     * to provide custom start-up behavior.
     *
     * @throws GridException If start failed.
     */
    public void start() throws GridException {
        // No-op.
    }

    /**
     * Startup info.
     *
     * @return Startup info.
     */
    protected final String startInfo() {
        return "Cache started: " + ctx.config().getName();
    }

    /**
     * Stops this cache. Child classes should override this method
     * to provide custom stop behavior.
     */
    public void stop() {
        // No-op.
    }

    /**
     * Stop info.
     *
     * @return Stop info.
     */
    protected final String stopInfo() {
        return "Cache stopped: " + ctx.config().getName();
    }

    /**
     * Kernal start callback.
     *
     * @throws GridException If callback failed.
     */
    protected void onKernalStart() throws GridException {
        // No-op.
    }

    /**
     * Kernal stop callback.
     */
    public void onKernalStop() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return values().size();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        return values().isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(key, "key");

        GridCacheEntryEx<K, V> e = peekEx(key);

        try {
            return e != null && e.peek(SMART, filter) != null;
        }
        catch (GridCacheEntryRemovedException ignore) {
            if (log.isDebugEnabled())
                log.debug("Got removed entry during peek (will ignore): " + e);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys))
            return true;

        assert keys != null;

        for (K k : keys)
            if (!containsKey(k, filter))
                return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable K[] keys) {
        return F.isEmpty(keys) || containsAllKeys(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable GridPredicate<? super K>[] filter) {
        return F.isEmpty(filter) || forAll(F.<K, V>cacheKeys(filter));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable K[] keys) {
        return F.isEmpty(keys) || containsAnyKeys(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys)) {
            return true;
        }

        assert keys != null;

        for (K k : keys) {
            if (containsKey(k, filter)) {
                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable GridPredicate<? super K>[] filter) {
        return F.isEmpty(filter) || keySet(ctx.vararg(F.<K, V>cacheKeys(filter))).iterator().hasNext();
    }

    /** {@inheritDoc} */
    @Override public boolean containsValue(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(val, "val");

        return values(filter).contains(val);
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.isEmpty(vals) || values(filter).containsAll(vals);
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable V[] vals) {
        return F.isEmpty(vals) || containsAllValues(F.asList(vals));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable GridPredicate<? super V>[] filter) {
        return F.isEmpty(filter) || F.forAll(values(), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.isEmpty(vals) || !values(ctx.vararg(F.and(filter, F.<K, V>cacheContainsPeek(vals)))).isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable V[] vals) {
        return F.isEmpty(vals) || containsAnyValues(F.asList(vals));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable GridPredicate<? super V>[] filter) {
        return F.isEmpty(filter) || F.view(values(), filter).iterator().hasNext();
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.isEmpty(filter) || entrySet(filter).iterator().hasNext();
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.isEmpty(filter) || F.forAll(entrySet(), filter);
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable K[] keys) {
        return peekAll(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys) {
        return peekAll(keys, (GridPredicate<GridCacheEntry>[])null);
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return peekAll(keySet(), filter);
    }

    /** {@inheritDoc} */
    @Override public V peek(K key) {
        return peek(key, (GridPredicate<GridCacheEntry>[])null);
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable GridCachePeekMode[] modes) throws GridException {
        return F.isEmpty(modes) ? peek(key) : peek(key, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(K key, @Nullable GridCachePeekMode[] modes) {
        return peekAsync(key, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys, @Nullable GridCachePeekMode... modes)
        throws GridException {
        return F.isEmpty(modes) ? peekAll(keys) : peekAll(keys, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridCachePeekMode[] modes) {
        return peekAllAsync(keys, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        return peek0(key, modes, ctx.tm().<GridCacheTxEx<K, V>>tx());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(final K key, @Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() throws GridException {
                return peek0(key, modes, tx);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        return peekAll0(keys, modes, ctx.tm().<GridCacheTxEx<K, V>>tx(), null);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable final Collection<? extends K> keys,
        @Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Map<K, V>>() {
            @Nullable @Override public Map<K, V> call() throws GridException {
                return peekAll0(keys, modes, tx, null);
            }
        }), false);
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            return peek0(false, key, SMART, filter);
        }
        catch (GridCacheFilterFailedException e) {
            e.printStackTrace();

            assert false; // Should never happen.

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return peekAll0(keys, filter, null);
    }

    /**
     * @param failFast Fail fast flag.
     * @param key Key.
     * @param mode Peek mode.
     * @param filter Filter.
     * @return Peeked value.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @Nullable protected V peek0(boolean failFast, K key, GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridCacheFilterFailedException {
        GridCacheEntryEx<K, V> e = peekEx(key);

        try {
            if (e != null)
                return ctx.cloneOnFlag(failFast ? e.peekFailFast(mode, filter) : e.peek(mode, filter));

            GridCacheTxEx<K, V> tx = ctx.tm().tx();

            return tx != null ? ctx.cloneOnFlag(tx.peek(failFast, key, filter)) : null;
        }
        catch (GridCacheEntryRemovedException ignore) {
            if (log.isDebugEnabled())
                log.debug("Got removed entry during 'peek': " + e);

            return null;
        }
        catch (GridException ex) {
            throw new GridRuntimeException(ex);
        }
    }

    /**
     * @param keys Keys.
     * @param filter Filter.
     * @param skipped Skipped keys, possibly {@code null}.
     * @return Peeked map.
     */
    protected Map<K, V> peekAll0(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, @Nullable Collection<K> skipped) {
        if (F.isEmpty(keys))
            return Collections.emptyMap();

        assert keys != null;

        Map<K, V> ret = new HashMap<K, V>(keys.size(), 1.0f);

        for (K k : keys) {
            GridCacheEntryEx<K, V> e = peekEx(k);

            if (e != null)
                try {
                    ret.put(k, ctx.cloneOnFlag(e.peekFailFast(SMART, filter)));
                }
                catch (GridCacheEntryRemovedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry during 'peek' (will skip): " + e);
                }
                catch (GridCacheFilterFailedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Filter failed during peek (will skip): " + e);

                    if (skipped != null)
                        skipped.add(k);
                }
                catch (GridException ex) {
                    throw new GridRuntimeException(ex);
                }
        }

        return ret;
    }

    /**
     * @param key Key.
     * @param modes Peek modes.
     * @param tx Transaction to peek at (if modes contains TX value).
     * @return Peeked value.
     * @throws GridException In case of error.
     */
    @Nullable protected V peek0(K key, @Nullable Collection<GridCachePeekMode> modes, GridCacheTxEx<K, V> tx)
        throws GridException {
        try {
            return peek0(false, key, modes, tx);
        }
        catch (GridCacheFilterFailedException ex) {
            ex.printStackTrace();

            assert false; // Should never happen.

            return null;
        }
    }

    /**
     * @param failFast If {@code true}, then filter failure will result in exception.
     * @param key Key.
     * @param modes Peek modes.
     * @param tx Transaction to peek at (if modes contains TX value).
     * @return Peeked value.
     * @throws GridException In case of error.
     * @throws GridCacheFilterFailedException If filer validation failed.
     */
    @Nullable protected V peek0(boolean failFast, K key, @Nullable Collection<GridCachePeekMode> modes,
        GridCacheTxEx<K, V> tx) throws GridException, GridCacheFilterFailedException {
        if (F.isEmpty(modes))
            return peek(key, (GridPredicate<GridCacheEntry>[])null);

        assert modes != null;

        GridCacheEntryEx<K, V> e = peekEx(key);

        try {
            for (GridCachePeekMode m : modes) {
                V val = null;

                if (e != null)
                    val = e.peek0(failFast, m, null, tx);
                else if (m == TX || m == SMART)
                    val = tx != null ? tx.peek(failFast, key, null) : null;
                else if (m == SWAP)
                    val = peekSwap(key);
                else if (m == DB)
                    val = peekDb(key);

                if (val != null)
                    return ctx.cloneOnFlag(val);
            }
        }
        catch (GridCacheEntryRemovedException ignore) {
            if (log.isDebugEnabled())
                log.debug("Got removed entry during 'peek': " + e);
        }

        return null;
    }

    /**
     * @param key Key to read from swap storage.
     * @return Value from swap storage.
     * @throws GridException In case of any errors.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekSwap(K key) throws GridException {
        GridCacheSwapEntry<V> e = ctx.swap().read(key);

        return e != null ? e.value() : null;
    }

    /**
     * @param key Key to read from persistent store.
     * @return Value from persistent store.
     * @throws GridException In case of any errors.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekDb(K key) throws GridException {
        return (V)CU.loadFromStore(ctx, log, ctx.tm().<GridCacheTx>tx(), key);
    }

    /**
     * @param keys Keys.
     * @param modes Modes.
     * @param tx Transaction.
     * @param skipped Keys skipped during filter validation.
     * @return Peeked values.
     * @throws GridException If failed.
     */
    protected Map<K, V> peekAll0(@Nullable Collection<? extends K> keys, @Nullable Collection<GridCachePeekMode> modes,
        GridCacheTxEx<K, V> tx, @Nullable Collection<K> skipped) throws GridException {
        if (F.isEmpty(keys))
            return emptyMap();

        assert keys != null;

        Map<K, V> ret = new HashMap<K, V>(keys.size(), 1.0f);

        for (K k : keys) {
            try {
                V val = peek0(skipped != null, k, modes, tx);

                if (val != null)
                    ret.put(k, val);
            }
            catch (GridCacheFilterFailedException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Filter validation failed for key: " + k);

                if (skipped != null)
                    skipped.add(k);
            }
        }

        return ret;
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis) {
        A.notNull(vis, "vis");

        for (GridCacheEntry<K, V> e : entrySet())
            vis.apply(e);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(final GridInClosure<? super GridCacheEntry<K, V>> vis) {
        A.notNull(vis, "vis");

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().runLocalSafe(ctx.projectSafe(new GPR() {
            @Override public void run() {
                ctx.tm().txContext(tx);

                forEach(vis);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        A.notNull(vis, "vis");

        for (GridCacheEntry<K, V> e : entrySet(keys))
            vis.apply(e);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(final GridInClosure<? super GridCacheEntry<K, V>> vis,
        final Collection<? extends K> keys) {
        A.notNull(vis, "vis");

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().runLocalSafe(ctx.projectSafe(new GPR() {
            @Override public void run() {
                ctx.tm().txContext(tx);

                forEach(vis, keys);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis, K[] keys) {
        forEach(vis, F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis, K[] keys) {
        return forEachAsync(vis, F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis) {
        A.notNull(vis, "vis");

        for (GridCacheEntry<K, V> e : entrySet())
            if (!vis.apply(e))
                return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(final GridPredicate<? super GridCacheEntry<K, V>> vis) {
        A.notNull(vis, "vis");

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Boolean>() {
            @Override public Boolean call() {
                ctx.tm().txContext(tx);

                return forAll(vis);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        A.notNull(vis, "vis");

        for (GridCacheEntry<K, V> e : entrySet(keys))
            if (!vis.apply(e))
                return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(final GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable final Collection<? extends K> keys) {
        A.notNull(vis, "vis");

        if (F.isEmpty(keys))
            return new GridFinishedFuture<Boolean>(ctx.kernalContext(), true);

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Boolean>() {
            @Override public Boolean call() {
                ctx.tm().txContext(tx);

                return forAll(vis, keys);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable K[] keys) {
        return F.isEmpty(keys) || forAll(vis, F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable K[] keys) {
        A.notNull(vis, "vis");

        return F.isEmpty(keys) ? new GridFinishedFuture<Boolean>(ctx.kernalContext(), true) :
            forAllAsync(vis, F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        A.notNull(rdc, "rdc");

        for (GridCacheEntry<K, V> e : entrySet())
            if (!rdc.collect(e))
                break;

        return rdc.apply();
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(final GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        A.notNull(rdc, "rdc");

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<R>() {
            @Nullable @Override public R call() {
                ctx.tm().txContext(tx);

                return reduce(rdc);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc, Collection<? extends K> keys) {
        A.notNull(rdc, "rdc");

        for (GridCacheEntry<K, V> e : entrySet(keys))
            if (!rdc.collect(e))
                break;

        return rdc.apply();
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(final GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        final Collection<? extends K> keys) {
        A.notNull(rdc, "rdc");

        final GridCacheTxEx tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<R>() {
            @Nullable @Override public R call() {
                ctx.tm().txContext(tx);

                return reduce(rdc, keys);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc, K[] keys) {
        return reduce(rdc, F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc, K[] keys) {
        return reduceAsync(rdc, F.asList(keys));
    }

    /**
     * Undeploys and removes all entries for class loader.
     *
     * @param ldr Class loader to undeploy.
     */
    public void onUndeploy(ClassLoader ldr) {
        ctx.deploy().onUndeploy(ldr);
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheEntry<K, V> entry(K key) {
        return entryEx(key).wrap(true);
    }

    /**
     *
     * @param key Entry key.
     * @return Entry or <tt>null</tt>.
     */
    @Nullable public GridCacheEntryEx<K, V> peekEx(K key) {
        return entry0(key, false);
    }

    /**
     *
     * @param key Entry key.
     * @return Entry or <tt>null</tt>.
     */
    public GridCacheEntryEx<K, V> entryEx(K key) {
        GridCacheEntryEx<K, V> e = entry0(key, true);

        assert e != null;

        return e;
    }

    /**
     *
     * @param key Entry key.
     * @param create Flag to create entry if it does not exist.
     * @return Entry or <tt>null</tt>.
     */
    @SuppressWarnings({"TooBroadScope"})
    @Nullable private GridCacheEntryEx<K, V> entry0(K key, boolean create) {
        GridCacheEntryEx<K, V> entry = null;

        readLock();

        try {
            entry = map.getEntry(key);
        }
        finally {
            readUnlock();
        }

        if ((entry != null && entry.obsolete()) || (entry == null && create)) {
            writeLock();

            try {
                // Double check.
                entry = map.getEntry(key);

                GridCacheEntryEx<K, V> doomed = null;

                if (entry != null && entry.obsolete()) {
                    doomed = map.removeEntry(key);

                    entry = null;
                }

                boolean created = false;

                if (entry == null && create) {
                    entry = map.putEntry(key, null, ctx.config().getDefaultTimeToLive());

                    created = true;
                }

                if (doomed != null)
                    // Event notification.
                    ctx.events().addEvent(doomed.partition(), doomed.key(), locNodeId, (UUID)null, null,
                        EVT_CACHE_ENTRY_DESTROYED, null, null);

                if (created)
                    // Event notification.
                    ctx.events().addEvent(entry.partition(), entry.key(), locNodeId, (UUID)null, null,
                        EVT_CACHE_ENTRY_CREATED, null, null);
            }
            finally {
                writeUnlock();
            }
        }

        return entry;
    }

    /**
     * Same as {@link #entrySet()} but for internal use only to
     * avoid casting.
     *
     * @return Set of entry wrappers.
     */
    public Set<GridCacheEntryImpl<K, V>> wrappers() {
        return map.wrappers(CU.<K, V>empty());
    }

    /**
     * @return Set of internal cached entry representations.
     */
    public Set<GridCacheEntryEx<K, V>> entries() {
        return map.entries0(CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet() {
        return entrySet(CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return map.entries(filter);
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return entrySet(keys, null, filter);
    }

    /**
     * @param keys Keys.
     * @param keyFilter Key filter.
     * @param filter Entry filter.
     * @return Entry set.
     */
    public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super K> keyFilter, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys))
            return emptySet();

        return new GridCacheEntrySet<K, V>(
            ctx,
            F.<K, GridCacheEntry<K, V>>viewReadOnly(keys, CU.<K, V>cacheKey2Entry(ctx), keyFilter),
            filter);
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(K[] keys) {
        return entrySet(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet() {
        return keySet(CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return map.keySet(filter);
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys)) {
            return emptySet();
        }

        return new GridCacheKeySet<K, V>(
            ctx,
            F.viewReadOnly(keys, CU.<K, V>cacheKey2Entry(ctx)),
            filter);
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(K[] keys) {
        return keySet(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values() {
        return values(CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return map.values(filter);
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys)) {
            return emptySet();
        }

        return new GridCacheValueCollection<K, V>(
            ctx,
            F.viewReadOnly(keys, CU.<K, V>cacheKey2Entry(ctx)),
            filter);
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(K[] keys) {
        return values(F.asList(keys), CU.<K, V>empty());
    }

    /**
     *
     * @param key Entry key.
     */
    public void removeIfObsolete(K key) {
        writeLock();

        try {
            GridCacheEntryEx<K, V> entry = map.getEntry(key);

            if (entry != null) {
                if (!entry.obsolete()) {
                    if (log.isDebugEnabled())
                        log.debug("Remove will not be done (obsolete entry got replaced): " + entry);
                }
                else {
                    GridCacheEntryEx<K, V> doomed = map.removeEntry(key);

                    assert doomed != null;
                    assert doomed.obsolete() : "Removed non-obsolete entry: " + doomed;

                    if (log.isDebugEnabled())
                        log.debug("Removed entry from cache: " + doomed);

                    // Event notification.
                    ctx.events().addEvent(doomed.partition(), doomed.key(), locNodeId, (UUID)null, null,
                        EVT_CACHE_ENTRY_DESTROYED, null, null);
                }
            }
        }
        finally {
            writeUnlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll() {
        clearAll(CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        boolean swap = ctx.isSwapEnabled();

        GridCacheVersion obsoleteVer = null;

        // Note that the iterator is thread safe, so we don't have to synchronize.
        for (GridCacheEntryEx<K, V> e : map.entries0(CU.<K, V>empty())) {
            if (obsoleteVer == null)
                obsoleteVer = ctx.versions().next();

            try {
                if (e.clear(obsoleteVer, swap, filter))
                    removeIfObsolete(e.key());
                else
                    if (log.isDebugEnabled())
                        log.debug("Failed to remove entry: " + e);
            }
            catch (GridException ex) {
                U.error(log, "Failed to clear entry from swap storage (will continue to clear other entries): " + e, ex);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void clearAll(Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        if (F.isEmpty(keys)) {
            return;
        }

        GridCacheVersion obsoleteVer = ctx.versions().next();

        for (K k : keys) {
            clear(obsoleteVer, k, filter);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean clear(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        return clear(ctx.versions().next(), key, filter);
    }

    /**
     * Clears entry from cache.
     *
     * @param obsoleteVer Obsolete version to set.
     * @param key Key to clear.
     * @param filter Optional filter.
     * @return {@code True} if cleared.
     */
    private boolean clear(GridCacheVersion obsoleteVer, K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheEntryEx<K, V> e = peekEx(key);

        try {
            if (e != null && e.clear(obsoleteVer, ctx.isSwapEnabled(), filter)) {
                removeIfObsolete(key);

                return true;
            }
        }
        catch (GridException ex) {
            U.error(log, "Failed to clear entry from swap storage (will continue to clear other entries): " + e, ex);
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public void clearAll(K[] keys) {
        clearAll(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        ctx.denyOnFlag(READ);

        A.notNull(key, "key");

        GridCacheEntryEx<K, V> entry = peekEx(key);

        try {
            return entry != null && entry.invalidate(filter);
        }
        catch (GridCacheEntryRemovedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Got removed entry in invalidate(...): " + key);
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnFlag(READ);

        if (keys != null)
            for (K key : keys)
                invalidate(key, filter);
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable K[] keys) throws GridException {
        ctx.denyOnFlag(READ);

        if (!F.isEmpty(keys)) {
            invalidateAll(F.asList(keys), null);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean compact(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        ctx.denyOnFlag(READ);

        A.notNull(key, "key");

        GridCacheEntryEx<K, V> entry = peekEx(key);

        try {
            if (entry != null && entry.compact(filter)) {
                removeIfObsolete(key);

                return true;
            }
        }
        catch (GridCacheEntryRemovedException ignored) {
            if (log().isDebugEnabled())
                log().debug("Got removed entry in invalidate(...): " + key);
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnFlag(READ);

        if (keys != null)
            for (K key : keys)
                compact(key, filter);
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable K[] keys) throws GridException {
        ctx.denyOnFlag(READ);

        if (!F.isEmpty(keys))
            compactAll(F.asList(keys));
    }

    /**
     * @param entry Removes obsolete entry from cache.
     */
    void removeEntry(GridCacheEntryEx entry) {
        writeLock();

        try {
            if (map.getEntry(entry.key()) == entry)
                map.removeEntry(entry.key());
        }
        finally {
            writeUnlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void evictAll() {
        evictAll(keySet(), null);
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        evictAll(keySet(), filter);
    }

    /** {@inheritDoc} */
    @Override public void evictAll(K[] keys) {
        ctx.denyOnFlag(READ);

        evictAll(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean evict(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        return evictx(key, ctx.versions().next(), filter);
    }

    /** {@inheritDoc} */
    @Override public void evictAll(Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        if (F.isEmpty(keys))
            return;

        GridCacheVersion obsoleteVer = ctx.versions().next();

        for (K k : keys)
            evictx(k, obsoleteVer, filter);
    }

    /**
     * Evicts an entry from cache.
     *
     * @param key Key.
     * @param ver Version.
     * @param filter Filter.
     * @return {@code True} if entry was evicted.
     */
    private boolean evictx(K key, GridCacheVersion ver,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCacheEntryEx<K, V> entry = peekEx(key);

        if (entry == null)
            return true;

        try {
            return ctx.evicts().evict(entry, ver, filter);
        }
        catch (GridException ex) {
            U.error(log, "Failed to evict entry from cache: " + entry, ex);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V get(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return getAllAsync(F.asList(key), filter).get().get(key);
    }

    /** {@inheritDoc} */
    @Override public final GridFuture<V> getAsync(final K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(LOCAL);

        try {
            checkJta();
        }
        catch (GridException e) {
            return new GridFinishedFuture<V>(ctx.kernalContext(), e);
        }

        return new GridFutureWrapper<V, Map<K, V>>(getAllAsync(Collections.singletonList(key), filter),
            new C1<Map<K, V>, V>() {
                @Override public V apply(Map<K, V> map) {
                    return map.get(key);
                }
            }
        );
    }

    /** {@inheritDoc} */
    @Override public final Map<K, V> getAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnFlag(LOCAL);

        checkJta();

        return getAllAsync(keys, filter).get();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V reload(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        ctx.denyOnFlags(F.asList(LOCAL, READ));

        A.notNull(key, "key");

        while (true) {
            try {
                return ctx.cloneOnFlag(entryEx(key).innerReload(filter));
            }
            catch (GridCacheEntryRemovedException ignored) {
                if (log.isDebugEnabled()) {
                    log.debug("Attempted to reload a removed entry for key (will retry): " + key);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(final K key,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlags(F.asList(LOCAL, READ));

        return ctx.closures().callLocalSafe(ctx.projectSafe(new Callable<V>() {
            @Nullable @Override public V call() throws GridException {
                return reload(key, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public void reloadAll() throws GridException {
        ctx.denyOnFlags(F.asList(LOCAL, READ));

        reloadAll(keySet());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync() {
        ctx.denyOnFlags(F.asList(LOCAL, READ));

        return reloadAllAsync(keySet());
    }

    /**
     * @param keys Keys.
     * @param reload Reload flag.
     * @param tx Transaction.
     * @param filter Filter.
     * @param vis Visitor.
     * @return Future.
     */
    public GridFuture<Object> readThroughAllAsync(final Collection<? extends K> keys, boolean reload,
        @Nullable final GridCacheTx tx, GridPredicate<? super GridCacheEntry<K, V>>[] filter,
        final GridInClosure2<K, V> vis) {
        return ctx.closures().callLocalSafe(new GPC<Object>() {
                @Nullable @Override public Object call() {
                    try {
                        CU.loadAllFromStore(ctx, log, tx, keys, vis);
                    }
                    catch (GridException e) {
                        throw new GridClosureException(e);
                    }

                    return null;
                }
            }, true);
    }

    /**
     * @param keys Keys.
     * @param ret Return flag.
     * @param filter Optional filter.
     * @return Non-{@code null} map if return flag is {@code true}.
     * @throws GridException If failed.
     */
    @Nullable public Map<K, V> reloadAll(@Nullable Collection<? extends K> keys, boolean ret,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return reloadAllAsync(keys, ret, filter).get();
    }

    /**
     * @param keys Keys.
     * @param ret Return flag.
     * @param filter Filter.
     * @return Future.
     */
    public GridFuture<Map<K, V>> reloadAllAsync(@Nullable Collection<? extends K> keys, boolean ret,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        if (!F.isEmpty(keys)) {
            try {
                final String uid = CU.uuid(); // Get meta UUID for this thread.

                assert keys != null;

                for (K key : keys) {
                    while (true) {
                        if (key == null) {
                            continue;
                        }

                        GridCacheEntryEx<K, V> entry = entryEx(key);

                        try {
                            // Get version before checking filer.
                            GridCacheVersion ver = entry.version();

                            if (ctx.isAll(entry, filter)) {
                                // Tag entry with current version.
                                entry.addMeta(uid, ver);
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled()) {
                                log.debug("Got removed entry for reload (will retry): " + entry);
                            }
                        }
                    }
                }

                final Map<K, V> map = ret ? new HashMap<K, V>(keys.size()) : null;

                return new GridFutureWrapper<Map<K, V>, Object>(
                    readThroughAllAsync(F.view(keys, CU.keyHasMeta(ctx, uid)), true, null, filter, new CI2<K, V>() {
                        // Version for all loaded entries.
                        private GridCacheVersion nextVer = ctx.versions().next();

                        /** {@inheritDoc} */
                        @Override public void apply(K key, V val) {
                            GridCacheEntryEx<K, V> entry = peekEx(key);

                            if (entry != null) {
                                try {
                                    GridCacheVersion curVer = entry.removeMeta(uid);

                                    // If entry passed the filter.
                                    if (curVer != null) {
                                        boolean wasNew = entry.isNew();

                                        entry.unswap();

                                        boolean set = entry.versionedValue(val, curVer, nextVer);

                                        if (map != null) {
                                            if (set || wasNew) {
                                                map.put(key, val);
                                            }
                                            else {
                                                try {
                                                    V v = peek0(false, key, GLOBAL, filter);

                                                    if (v != null) {
                                                        map.put(key, val);
                                                    }
                                                }
                                                catch (GridCacheFilterFailedException ex) {
                                                    ex.printStackTrace();

                                                    assert false;
                                                }
                                            }
                                        }

                                        if (log.isDebugEnabled()) {
                                            log.debug("Set value loaded from store into entry [set=" + set + ", curVer=" +
                                                curVer + ", newVer=" + nextVer + ", entry=" + entry + ']');
                                        }
                                    }
                                    else {
                                        if (log.isDebugEnabled()) {
                                            log.debug("Current version was not found (either entry was removed or validation " +
                                                "was not passed: " + entry);
                                        }
                                    }
                                }
                                catch (GridCacheEntryRemovedException ignore) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Got removed entry for reload (will not store reloaded entry) [entry=" +
                                            entry + ']');
                                    }
                                }
                                catch (GridException e) {
                                    throw new GridRuntimeException(e);
                                }
                            }
                        }
                    }), new C1<Object, Map<K, V>>() {
                    @Nullable @Override public Map<K, V> apply(Object e) {
                        return map;
                    }
                });
            }
            catch (GridException e) {
                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), e);
            }
        }

        return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        reloadAll(keys, false, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return reloadAllAsync(keys, false, filter);
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable K[] keys) throws GridException {
        ctx.denyOnFlag(READ);

        if (!F.isEmpty(keys)) {
            reloadAll(F.asList(keys), CU.<K, V>empty());
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable K[] keys) {
        ctx.denyOnFlag(READ);

        return F.isEmpty(keys) ? new GridFinishedFuture(ctx.kernalContext()) :
            reloadAllAsync(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public final void reloadAll(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnFlag(READ);

        Set<K> keys = keySet();

        // Don't reload empty cache.
        if (!keys.isEmpty()) {
            reloadAll(keys, filter);
        }
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings({"unchecked"})
    public GridFuture<?> reloadAllAsync(@Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(READ);

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC() {
            @Nullable @Override public Object call() throws GridException {
                reloadAll(filter);

                return null;
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return getAllAsync(keys, true, filter);
    }

    /** {@inheritDoc} */
    protected GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys, boolean checkTx,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(LOCAL);

        if (F.isEmpty(keys))
            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());

        try {
            checkJta();
        }
        catch (GridException e) {
            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), e);
        }

        GridCacheTxLocalAdapter<K, V> tx = null;

        if (checkTx)
            tx = ctx.tm().tx();

        if (tx == null || tx.implicit()) {
            try {
                assert keys != null;

                final Map<K, V> map = new GridLeanMap<K, V>(keys.size());

                Map<K, GridCacheVersion> misses = null;

                for (K key : keys) {
                    // Ignore null keys.
                    if (key == null)
                        continue;

                    while (true) {
                        GridCacheEntryEx<K, V> entry = entryEx(key);

                        try {
                            V val = entry.innerGet(null, ctx.isSwapEnabled(),
                                /*don't read-through*/false, true, true, true, filter);

                            GridCacheVersion ver = entry.version();

                            if (val == null) {
                                if (misses == null)
                                    misses = new GridLeanMap<K, GridCacheVersion>();

                                misses.put(key, ver);
                            }
                            else {
                                map.put(key, ctx.cloneOnFlag(val));

                                if (keys.size() == 1)
                                    // Safe to return because no locks are required in READ_COMMITTED mode.
                                    return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), map);
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignored) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in getAllAsync(..) method (will retry): " + key);
                        }
                        catch (GridCacheFilterFailedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Filter validation failed for entry: " + entry);

                            break; // While loop.
                        }
                    }
                }

                if (misses != null && ctx.isStoreEnabled()) {
                    final Map<K, GridCacheVersion> loadKeys = misses;

                    final Collection<K> redos = new LinkedList<K>();

                    return new GridEmbeddedFuture<Map<K, V>, Map<K, V>>(
                        ctx.kernalContext(),
                        ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Map<K, V>>() {
                            @Override public Map<K, V> call() throws Exception {
                                CU.loadAllFromStore(ctx, log, null/*tx*/, loadKeys.keySet(), new CI2<K, V>() {
                                    // New version for all new entries.
                                    private GridCacheVersion nextVer;

                                    @Override public void apply(K key, V val) {
                                        GridCacheVersion ver = loadKeys.get(key);

                                        if (ver == null) {
                                            if (log.isDebugEnabled())
                                                log.debug("Value from storage was never asked for [key=" + key +
                                                    ", val=" + val + ']');

                                            return;
                                        }

                                        // Initialize next version.
                                        if (nextVer == null)
                                            nextVer = ctx.versions().next();

                                        GridCacheEntryEx<K, V> entry = entryEx(key);

                                        try {
                                            boolean set = entry.versionedValue(val, ver, nextVer);

                                            if (log.isDebugEnabled())
                                                log.debug("Set value loaded from store into entry [set=" + set +
                                                    ", curVer=" + ver + ", newVer=" + nextVer + ", entry=" + entry + ']');

                                            if (val == null)
                                                // Don't put key-value pair into result map if value is null.
                                                return;

                                            if (set || F.isEmpty(filter))
                                                map.put(key, ctx.cloneOnFlag(val));
                                            else
                                                // Try again, so we can return consistent values.
                                                redos.add(key);
                                        }
                                        catch (GridCacheEntryRemovedException ignore) {
                                            if (log.isDebugEnabled())
                                                log.debug("Got removed entry during getAllAsync (will ignore): " + entry);
                                        }
                                        catch (GridException e) {
                                            // Wrap errors (will be unwrapped).
                                            throw new GridRuntimeException("Failed to set cache value for entry: " +
                                                entry, e);
                                        }
                                    }
                                });

                                return map;
                            }
                        })),
                        new C2<Map<K, V>, Exception, GridFuture<Map<K, V>>>() {
                            @Override public GridFuture<Map<K, V>> apply(Map<K, V> map, Exception e) {
                                if (e != null)
                                    return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), e);

                                if (!redos.isEmpty())
                                    // Future recursion.
                                    return getAllAsync(redos, filter);

                                // There were no misses.
                                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());
                            }
                        },
                        new C2<Map<K, V>, Exception, Map<K, V>>() {
                            @Override public Map<K, V> apply(Map<K, V> loaded, Exception e) {
                                if (e == null)
                                    map.putAll(loaded);

                                return map;
                            }
                        }
                    );
                }

                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), map);
            }
            catch (GridException e) {
                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), e);
            }
        }
        else
            return ctx.wrapCloneMap(tx.getAllAsync(keys, filter));
    }

    /** {@inheritDoc} */
    @Nullable @Override public V put(final K key, final V val,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.cloneOnFlag(syncOp(new SyncOp<V>() {
            @Override public V op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.put(key, val, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putAsync(final K key, final V val,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.wrapClone(asyncOp(new AsyncOp<V>(key) {
            @Override public GridFuture<V> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putAsync(key, val, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean putx(final K key, final V val,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.putx(key, val, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxAsync(final K key, final V val,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putxAsync(key, val, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Nullable @Override public V putIfAbsent(final K key, final V val) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.cloneOnFlag(syncOp(new SyncOp<V>() {
            @Override public V op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.put(key, val, ctx.noPeekArray());
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putIfAbsentAsync(final K key, final V val) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.wrapClone(asyncOp(new AsyncOp<V>(key) {
            @Override public GridFuture<V> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putAsync(key, val, ctx.noPeekArray());
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean putxIfAbsent(final K key, final V val) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.putx(key, val, ctx.noPeekArray());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxIfAbsentAsync(final K key, final V val) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putxAsync(key, val, ctx.noPeekArray());
            }
        });
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V replace(final K key, final V val) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.cloneOnFlag(syncOp(new SyncOp<V>() {
            @Override public V op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.put(key, val, ctx.hasPeekArray());
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(final K key, final V val) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return ctx.wrapClone(asyncOp(new AsyncOp<V>(key) {
            @Override public GridFuture<V> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putAsync(key, val, ctx.hasPeekArray());
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(final K key, final V val) throws GridException {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.putx(key, val, ctx.hasPeekArray());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(final K key, final V val) {
        A.notNull(key, "key", val, "val");

        ctx.denyOnLocalRead();

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putxAsync(key, val, ctx.hasPeekArray());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean replace(final K key, final V oldVal, final V newVal) throws GridException {
        A.notNull(key, "key", oldVal, "oldVal", newVal, "newVal");

        ctx.denyOnLocalRead();

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                // Register before hiding in the filter.
                ctx.deploy().registerClass(oldVal);

                return tx.putx(key, newVal, ctx.equalsPeekArray(oldVal));
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(final K key, final V oldVal, final V newVal) {
        A.notNull(key, "key", oldVal, "oldVal", newVal, "newVal");

        ctx.denyOnLocalRead();

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                // Register before hiding in the filter.
                try {
                    ctx.deploy().registerClass(oldVal);
                }
                catch (GridException e) {
                    return new GridFinishedFuture<Boolean>(ctx.kernalContext(), e);
                }

                return tx.putxAsync(key, newVal, ctx.equalsPeekArray(oldVal));
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void putAll(final Map<? extends K, ? extends V> m,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnLocalRead();

        syncOp(new SyncInOp() {
            @Override public void inOp(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                tx.putAll(m, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> putAllAsync(final Map<? extends K, ? extends V> m,
        @Nullable final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnLocalRead();

        return asyncOp(new AsyncInOp(m.keySet()) {
            @Override public GridFuture<?> inOp(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.putAllAsync(m, false, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V remove(final K key, final GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        ctx.denyOnLocalRead();

        A.notNull(key, "key");

        return ctx.cloneOnFlag(syncOp(new SyncOp<V>() {
            @Override public V op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.remove(key, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(final K key,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnLocalRead();

        A.notNull(key, "key");

        return ctx.wrapClone(asyncOp(new AsyncOp<V>(key) {
            @Override public GridFuture<V> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.removeAsync(key, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public void removeAll(final Collection<? extends K> keys,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnLocalRead();

        syncOp(new SyncInOp() {
            @Override public void inOp(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                tx.removeAll(keys, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(final Collection<? extends K> keys,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnLocalRead();

        return asyncOp(new AsyncInOp(keys) {
            @Override public GridFuture<?> inOp(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.removeAllAsync(keys, tx.implicit(), false, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean removex(final K key, final GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        ctx.denyOnLocalRead();

        A.notNull(key, "key");

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                return tx.removex(key, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(final K key,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnLocalRead();

        A.notNull(key, "key");

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.removexAsync(key, filter);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean remove(final K key, final V val) throws GridException {
        ctx.denyOnLocalRead();

        A.notNull(key, "key", val, "val");

        return syncOp(new SyncOp<Boolean>() {
            @Override public Boolean op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                // Register before hiding in the filter.
                ctx.deploy().registerClass(val);

                return tx.removex(key, ctx.vararg(F.<K, V>cacheContainsPeek(val)));
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(final K key, final V val) {
        ctx.denyOnLocalRead();

        A.notNull(key, "key", val, "val");

        return asyncOp(new AsyncOp<Boolean>(key) {
            @Override public GridFuture<Boolean> op(GridCacheTxLocalAdapter<K, V> tx) {
                // Register before hiding in the filter.
                try {
                    ctx.deploy().registerClass(val);
                }
                catch (GridException e) {
                    return new GridFinishedFuture<Boolean>(ctx.kernalContext(), e);
                }

                return tx.removexAsync(key, ctx.vararg(F.<K, V>cacheContainsPeek(val)));
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void removeAll(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        ctx.denyOnLocalRead();

        if (F.isEmpty(filter))
            filter = ctx.trueArray();

        final GridPredicate<? super GridCacheEntry<K, V>>[] p = filter;

        syncOp(new SyncInOp() {
            @Override public void inOp(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
                tx.removeAll(keySet(p), CU.<K, V>empty());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnLocalRead();

        final Set<? extends K> keys = keySet(filter);

        return asyncOp(new AsyncInOp(keys) {
            @Override public GridFuture<?> inOp(GridCacheTxLocalAdapter<K, V> tx) {
                return tx.removeAllAsync(keys, tx.implicit(), false, CU.<K, V>empty());
            }
        });
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        return GridCacheMetricsAdapter.copyOf(metrics);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public Collection<GridCacheQueryMetrics> queryMetrics() {
        // Query manager can be null.
        GridCacheQueryManager qryMgr = ctx.queries();

        return qryMgr != null ? qryMgr.metrics() : null;
    }

    /**
     * @return Metrics.
     */
    public GridCacheMetricsAdapter metrics0() {
        return metrics;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheTx tx() {
        GridCacheTx tx = ctx.tm().tx();

        return tx == null ? null : new GridCacheTxProxyImpl(tx, ctx.gate());
    }

    /**
     *
     * @param key Key to lock.
     * @param timeout Lock acquisition timeout.
     * @param tx Transaction.
     * @param invalidate Invalidate flag.
     * @param isRead {@code True} for read operations.
     * @param retval Flag to return value.
     * @param isolation Transaction isolation.
     * @param filter Optional filter.
     * @return Lock future.
     */
    public GridFuture<Boolean> txLockAsync(K key, long timeout, GridCacheTxLocalEx<K, V> tx, boolean invalidate,
        boolean isRead, boolean retval, GridCacheTxIsolation isolation,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return txLockAsync(Collections.singletonList(key), timeout, tx, isRead, retval, isolation, invalidate, filter);
    }

    /**
     *
     * @param key Key.
     * @param timeout Lock timeout.
     * @param tx Transaction.
     * @param invalidate Invalidate flag.
     * @param isRead {@code True} for read operations.
     * @param retval Flag to return value.
     * @param isolation Transaction isolation.
     * @return {@code True} if lock was acquired within timeout.
     * @throws GridException IF failed.
     */
    public boolean txLock(
        K key,
        long timeout,
        GridCacheTxLocalEx<K, V> tx,
        boolean invalidate,
        boolean isRead,
        boolean retval,
        GridCacheTxIsolation isolation)
        throws GridException {
        return txLock(Collections.singletonList(key), timeout, tx, invalidate, isRead, retval, isolation);
    }

    /**
     *
     * @param keys Keys to lock.
     * @param timeout Lock timeout.
     * @param tx Transaction.
     * @param invalidate Invalidate flag.
     * @param isRead {@code True} for read operations.
     * @param retval Flag to return value.
     * @param isolation Transaction isolation.
     * @return Locked keys.
     * @throws GridException If lock failed.
     */
    public boolean txLock(Collection<? extends K> keys, long timeout, GridCacheTxLocalEx<K, V> tx, boolean invalidate,
        boolean isRead, boolean retval, GridCacheTxIsolation isolation) throws GridException {
        return F.isEmpty(keys) || txLockAsync(keys, timeout, tx, isRead, retval, isolation, invalidate, CU.<K, V>empty()).
            get();
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, long timeout,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return lockAll(Collections.singletonList(key), timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return F.isEmpty(keys) || lockAllAsync(keys, timeout, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return lockAllAsync(Collections.singletonList(key), timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return lockAllAsync(keys, ctx.config().getDefaultLockTimeout(), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return lock(key, ctx.config().getDefaultLockTimeout(), filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return lockAsync(key, ctx.config().getDefaultLockTimeout(), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return lockAll(keys, ctx.config().getDefaultLockTimeout(), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable K[] keys) throws GridException {
        return lockAll(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(K[] keys) {
        return lockAllAsync(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout, K[] keys) throws GridException {
        return lockAll(F.asList(keys), timeout, CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout, K[] keys) {
        return lockAllAsync(F.asList(keys), timeout, CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return lockAll(keySet(filter), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return lockAllAsync(keySet(filter), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return lockAll(keySet(filter), timeout, CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return lockAllAsync(keySet(filter), timeout, CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public void unlock(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        unlockAll(Collections.singletonList(key), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked(K key) {
        GridCacheEntryEx<K, V> entry = peekEx(key);

        return entry != null && entry.wrap(false).isLocked();
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(Collection<? extends K> keys) {
        A.notNull(keys, "keys");

        for (K key : keys) {
            GridCacheEntryEx<K, V> entry = peekEx(key);

            if (entry == null || entry.obsolete() || !entry.wrap(false).isLocked())
                return false;
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread(K key) {
        GridCacheEntryEx<K, V> entry = peekEx(key);

        return entry != null && entry.wrap(false).isLockedByThread();
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(Collection<? extends K> keys) {
        for (K key : keys) {
            GridCacheEntryEx<K, V> entry = peekEx(key);

            if (entry == null || entry.obsolete() || !entry.wrap(false).isLockedByThread())
                return false;
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart() throws IllegalStateException {
        GridCacheConfigurationAdapter cfg = ctx.config();

        return txStart(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            cfg.getDefaultTxTimeout(),
            cfg.isInvalidate()
        );
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(long timeout) {
        A.ensure(timeout >= 0, "timeout cannot be negative.");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return txStart(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            timeout,
            cfg.isInvalidate()
        );
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation) {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return txStart(
            concurrency,
            isolation,
            cfg.getDefaultTxTimeout(),
            cfg.isInvalidate()
        );
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate) throws IllegalStateException {
        if (concurrency == EVENTUALLY_CONSISTENT && isolation == SERIALIZABLE)
            throw new IllegalArgumentException("EVENTUALLY_CONSISTENT transactions cannot have SERIALIZABLE " +
                "isolation.");

        if (!ctx.isEnterprise() && concurrency == EVENTUALLY_CONSISTENT)
            throw new GridEnterpriseFeatureException("Eventually Consistent Transactions");

        GridCacheTx tx = ctx.tm().userTx();

        if (tx != null)
            throw new IllegalStateException("Failed to start new transaction " +
                "(current thread already has a transaction): " + tx);

        tx = ctx.tm().onCreated(
            newTx(
                false,
                concurrency,
                isolation,
                timeout,
                invalidate || ctx.hasFlag(INVALIDATE),
                ctx.syncCommit(),
                ctx.syncRollback(),
                ctx.isSwapEnabled(),
                ctx.isStoreEnabled()
            )
        );

        assert tx != null;

        // Wrap into proxy.
        return new GridCacheTxProxyImpl(tx, ctx.gate());
    }

    /** {@inheritDoc} */
    @Override public long overflowSize() throws GridException {
        return ctx.swap().swapSize();
    }

    /** {@inheritDoc} */
    @Override public ConcurrentMap<K, V> toMap() {
        return new GridCacheMapAdapter<K, V>(this);
    }

    /**
     * Checks if cache is working in JTA transaction and enlist cache as XAResource if necessary.
     *
     * @throws GridException In case of lookup error.
     */
    protected void checkJta() throws GridException {
        if (jtaTm == null) {
            if (cacheCfg.getTransactionManagerLookup() != null) {
                jtaTm = cacheCfg.getTransactionManagerLookup().getTm();
            }
        }

        if (jtaTm != null) {
            GridCacheXAResource rsrc = xaRsrc.get();

            if (rsrc == null || rsrc.isFinished()) {
                try {
                    Transaction jtaTx = jtaTm.getTransaction();

                    if (jtaTx != null) {
                        GridCacheTx tx = ctx.tm().userTx();

                        if (tx == null) {
                            // Start with default concurrency and isolation.
                            GridCacheConfigurationAdapter cfg = ctx.config();

                            tx = ctx.tm().onCreated(
                                newTx(
                                    false,
                                    cfg.getDefaultTxConcurrency(),
                                    cfg.getDefaultTxIsolation(),
                                    cfg.getDefaultTxTimeout(),
                                    cfg.isInvalidate() || ctx.hasFlag(INVALIDATE),
                                    ctx.syncCommit(),
                                    ctx.syncRollback(),
                                    ctx.isSwapEnabled(),
                                    ctx.isStoreEnabled()
                                )
                            );
                        }

                        rsrc = new GridCacheXAResource((GridCacheTxEx)tx, ctx);

                        if (!jtaTx.enlistResource(rsrc)) {
                            throw new GridException("Failed to enlist XA resource to JTA user transaction.");
                        }

                        xaRsrc.set(rsrc);
                    }
                }
                catch (SystemException e) {
                    throw new GridException("Failed to obtain JTA transaction.", e);
                }
                catch (RollbackException e) {
                    throw new GridException("Failed to enlist XAResource to JTA transaction.", e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void txSynchronize(GridCacheTxSynchronization[] syncs) {
        ctx.tm().addSynchronizations(syncs);
    }

    /** {@inheritDoc} */
    @Override public void txUnsynchronize(GridCacheTxSynchronization[] syncs) {
        ctx.tm().removeSynchronizations(syncs);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxSynchronization> txSynchronizations() {
        return ctx.tm().synchronizations();
    }

    /** {@inheritDoc} */
    @Override public void loadCache(final GridPredicate2<K, V> p, long ttl, Object[] args) throws GridException {
        CU.loadCache(ctx, log, new CI2<K, V>() {
                // Version for all loaded entries.
                private GridCacheVersion ver = ctx.versions().next();

                @Override public void apply(K key, V val) {
                    if (p != null && !p.apply(key, val)) {
                        return;
                    }

                    GridCacheEntryEx<K, V> entry = entryEx(key);

                    try {
                        entry.versionedValue(val, null, ver);
                    }
                    catch (GridException e) {
                        throw new GridRuntimeException("Failed to put cache value: " + entry, e);
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled()) {
                            log.debug("Got removed entry during loadCache (will ignore): " + entry);
                        }
                    }
                }
            }, args);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> loadCacheAsync(final GridPredicate2<K, V> p, final long ttl, final Object[] args) {
        return ctx.closures().callLocalSafe(
            ctx.projectSafe(new Callable<Object>() {
                @Nullable
                @Override public Object call() throws GridException {
                    loadCache(p, ttl, args);

                    return null;
                }
            }));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"TooBroadScope"})
    @Nullable @Override public GridCacheEntry<K, V> randomEntry() {
        GridCacheMapEntry<K, V> e;

        readLock();

        try {
            e = map.randomEntry();
        }
        finally {
            readUnlock();
        }

        return e == null || e.obsolete() ? null : e.wrap(true);
    }

    /** {@inheritDoc} */
    @Override public int keySize() {
        return map.publicSize();
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery() {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(null, flags());
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, null, flags());
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable Class<?> cls,
        @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, cls, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable String clsName,
        @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, clsName, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery() {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(null, flags());
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, cls, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, clsName, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery() {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(null, flags());
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, cls, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheQueryManager<K, V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, clsName, clause, null, flags());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheAdapter.class, this, "name", name(), "size", size());
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(K[] keys) throws GridException {
        return getAll(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(K[] keys) {
        return getAllAsync(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return getAll(keySet(filter), filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return getAllAsync(keySet(filter), filter);
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridCacheEntry<K, V>> iterator() {
        return entrySet().iterator();
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(K[] keys) throws GridException {
        unlockAll(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        unlockAll(keySet(filter), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(K[] keys) {
        return isAllLocked(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(K[] keys) {
        return isAllLockedByThread(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(GridPredicate<? super K>[] filter) {
        return isAllLocked(keySet(ctx.vararg(F.<K, V>cacheKeys(filter))));
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(GridPredicate<? super K>[] filter) {
        return isAllLockedByThread(keySet(ctx.vararg(F.<K, V>cacheKeys(filter))));
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(GridPredicate<? super K>[] filter) {
        return mapKeysToNodes(keySet(ctx.vararg(F.<K, V>cacheKeys(filter))));
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(K[] keys) {
        return mapKeysToNodes(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public UUID mapKeyToNode(K key) {
        UUID id = F.first(mapKeysToNodes(F.asList(key)).keySet());

        assert id != null;

        return id;
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(K[] keys) {
        return gridProjection(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(GridPredicate<? super K>[] filter) {
        return gridProjection(keySet(ctx.vararg(F.<K, V>cacheKeys(filter))));
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        return ctx.grid().projectionForNodes(CU.allNodes(ctx));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public V unswap(K key) throws GridException {
        ctx.denyOnFlags(F.asList(READ, SKIP_SWAP));

        A.notNull(key, "key");

        GridCacheSwapEntry<V> unswapped = ctx.swap().readAndRemove(key);

        if (unswapped == null)
            return null;

        GridCacheEntryEx<K, V> entry = entryEx(key);

        try {
            if (!entry.initialValue(key, unswapped))
                return null;
        }
        catch (GridCacheEntryRemovedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Entry has been concurrently removed.");

            return null;
        }

        // Event notification.
        ctx.events().addEvent(entry.partition(), key, ctx.discovery().localNode().id(), (UUID)null, null,
            EVT_CACHE_OBJECT_UNSWAPPED, null, null);

        return ctx.cloneOnFlag(unswapped.value());
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable Collection<? extends K> keys) throws GridException {
        ctx.denyOnFlags(F.asList(READ, SKIP_SWAP));

        if (keys != null)
            for (K k : keys)
                unswap(k);
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable K... keys) throws GridException {
        ctx.denyOnFlags(F.asList(READ, SKIP_SWAP));

        if (!F.isEmpty(keys))
            unswapAll(F.asList(keys));
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        A.ensure(!F.isEmpty(closures), "closures are empty");

        inTx(ctx.config().getDefaultTxTimeout(), closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(long timeout, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        inTx(cfg.getDefaultTxConcurrency(), cfg.getDefaultTxIsolation(), timeout, cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");

        GridCacheConfigurationAdapter cfg = ctx.config();

        inTx(concurrency, isolation, cfg.getDefaultTxTimeout(), cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation, long timeout,
        boolean invalidate, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        ctx.denyOnFlag(LOCAL);

        GridCacheTx tx = ctx.tm().userTx();

        if (tx != null)
            throw new IllegalStateException("Failed to start new transaction " +
                "(current thread already has a transaction): " + tx);

        tx = txStart(concurrency, isolation, timeout, invalidate);

        try {
            for (GridInClosure<GridCacheProjection<K, V>> c : closures)
                c.apply(this);

            tx.commit();
        }
        catch (Exception e) {
            tx.setRollbackOnly();

            throw new GridException("An exception was thrown by one of the given closures.", e);
        }
        finally {
            tx.end();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <R> Collection<R> inTx(GridOutClosure<? super R>[] closures) throws GridException {
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        return inTx(ctx.config().getDefaultTxTimeout(), closures);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(long timeout, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTx(cfg.getDefaultTxConcurrency(), cfg.getDefaultTxIsolation(), timeout, cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTx(concurrency, isolation, cfg.getDefaultTxTimeout(), cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheTx tx = ctx.tm().userTx();

        if (tx != null)
            throw new IllegalStateException("Failed to start new transaction " +
                "(current thread already has a transaction): " + tx);

        tx = txStart(concurrency, isolation, timeout, invalidate);

        Collection<R> res = new LinkedList<R>();

        try {
            for (GridOutClosure<? super R> c : closures)
                res.add((R)c.apply());

            tx.commit();
        }
        catch (Exception e) {
            tx.setRollbackOnly();

            throw new GridException("An exception was thrown by one of the given closures.", e);
        }
        finally {
            tx.end();
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws GridException {
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            cfg.getDefaultTxTimeout(),
            cfg.isInvalidate(),
            closures
        );
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(long timeout, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            timeout,
            cfg.isInvalidate(),
            closures
        );
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(concurrency, isolation, cfg.getDefaultTxTimeout(), cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(final GridCacheTxConcurrency concurrency,
        final GridCacheTxIsolation isolation, final long timeout, final boolean invalidate,
        final GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        return ctx.closures().runLocal(ctx.projectSafe(new CAX() {
            @Override public void applyx() throws GridException {
                inTx(concurrency, isolation, timeout, invalidate, closures);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            cfg.getDefaultTxTimeout(),
            cfg.isInvalidate(),
            closures
        );
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(long timeout, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(
            cfg.getDefaultTxConcurrency(),
            cfg.getDefaultTxIsolation(),
            timeout,
            cfg.isInvalidate(),
            closures
        );
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");

        GridCacheConfigurationAdapter cfg = ctx.config();

        return inTxAsync(concurrency, isolation, cfg.getDefaultTxTimeout(), cfg.isInvalidate(), closures);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(final GridCacheTxConcurrency concurrency,
        final GridCacheTxIsolation isolation, final long timeout, final boolean invalidate,
        final GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        A.notNull(concurrency, "concurrency");
        A.notNull(isolation, "isolation");
        A.ensure(timeout >= 0, "timeout cannot be negative");
        A.ensure(!F.isEmpty(closures), "closures cannot be empty");

        return ctx.closures().callLocal(ctx.projectSafe(new GPC<Collection<R>>() {
            @Override public Collection<R> call() throws GridException {
                return inTx(concurrency, isolation, timeout, invalidate, closures);
            }
        }));
    }

    /**
     * @param op Cache operation.
     * @param <T> Return type.
     * @return Operation result.
     * @throws GridException If operation failed.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    @Nullable private <T> T syncOp(SyncOp<T> op) throws GridException {
        checkJta();

        GridCacheTxLocalAdapter<K, V> tx = ctx.tm().tx();

        if (tx == null || tx.implicit()) {
            tx = ctx.tm().onCreated(newTx(true));

            assert tx != null;

            try {
                T t = op.op(tx);

                assert tx.done() : "Transaction is not done: " + tx;

                return t;
            }
            catch (GridCacheTxRollbackException e) {
                throw e;
            }
            catch (GridCacheTxHeuristicException e) {
                throw e;
            }
            catch (GridException e) {
                try {
                    tx.rollback();
                }
                catch (GridException e1) {
                    U.error(log, "Failed to rollback transaction (cache may contain stale locks): " + tx, e1);
                }

                throw e;
            }
        }
        else {
            return op.op(tx);
        }
    }

    /**
     * @param op Cache operation.
     * @param <T> Return type.
     * @return Future.
     */
    private <T> GridFuture<T> asyncOp(final AsyncOp<T> op) {
        try {
            checkJta();
        }
        catch (GridException e) {
            return new GridFinishedFuture<T>(ctx.kernalContext(), e);
        }

        GridCacheTxLocalAdapter<K, V> tx = ctx.tm().tx();

        if (tx == null) {
            tx = ctx.tm().onCreated(newTx(true));
        }
        // For implicit transactions, we chain them up.
        else if (tx.implicit()) {
            // This will register a new transaction for this thread.
            final GridCacheTxLocalAdapter<K, V> newTx = ctx.tm().onCreated(newTx(true));

            assert newTx != null;

            final GridFutureAdapter<Object> fut = new GridFutureAdapter<Object>(ctx.kernalContext());

            tx.addFinishListener(new CI1<GridCacheTxEx<K, V>>() {
                @Override public void apply(final GridCacheTxEx<K, V> tx) {
                    if (log.isDebugEnabled())
                        log.debug("Previous transaction has changed its status to final: " + tx);

                    // We can only grab finish future after transaction state was set to some final state.
                    // Otherwise, it would be possible for finish future to be null.
                    ((GridCacheTxLocalEx<K, V>)tx).finishFuture().listenAsync(new CI1<GridFuture<GridCacheTx>>() {
                        @Override public void apply(GridFuture<GridCacheTx> f) {
                            if (log.isDebugEnabled())
                                log.debug("Finished waiting for previous transaction: " + tx);

                            fut.onDone();
                        }
                    });
                }
            });

            // Start when previous transaction completes.
            return new GridEmbeddedFuture<T, Object>(fut, new C2<Object, Exception, GridFuture<T>>() {
                @Override public GridFuture<T> apply(Object o, Exception e) {
                    if (log.isDebugEnabled())
                        log.debug("Continuing with new transaction [keys=" + op.keys() + ", newTx=" + newTx + ']');

                    return op.op(newTx);
                }
            }, ctx.kernalContext());
        }

        return op.op(tx);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
        U.writeString(out, ctx.namex());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple2<String, String> t = stash.get();

        t.set1(U.readString(in));
        t.set2(U.readString(in));
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        GridTuple2<String, String> t = stash.get();

        try {
            return G.grid(t.get1()).cache(t.get2());
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicSequence atomicSequence(String name) throws GridException {
        return ctx.dataStructures().sequence(name, 0L, false, true);
    }

    /** {@inheritDoc} */
    @Override public GridCacheAtomicSequence atomicSequence(String name, long initVal, boolean persistent)
        throws GridException {
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
    @Override public GridCacheAtomicLong atomicLong(String name, long initVal, boolean persistent) throws
        GridException {
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

    /**
     * NOTE: this method is for testing purposes only!
     */
    public void clearMetrics() {
        metrics.clear();
    }

    /**
     * Cache operation.
     */
    private abstract class SyncOp<T> {
        /**
         * @param tx Transaction.
         * @return Operation return value.
         * @throws GridException If failed.
         */
        @Nullable public abstract T op(GridCacheTxLocalAdapter<K, V> tx) throws GridException;
    }

    /**
     * Cache operation.
     */
    private abstract class SyncInOp extends SyncOp<Object> {
        /** {@inheritDoc} */
        @Nullable @Override public final Object op(GridCacheTxLocalAdapter<K, V> tx) throws GridException {
            inOp(tx);

            return null;
        }

        /**
         * @param tx Transaction.
         * @throws GridException If failed.
         */
        public abstract void inOp(GridCacheTxLocalAdapter<K, V> tx) throws GridException;
    }

    /**
     * Cache operation.
     */
    private abstract class AsyncOp<T> {
        /** Keys. */
        private final Collection<? extends K> keys;

        /**
         * @param key Key.
         */
        protected AsyncOp(K key) {
            keys = Arrays.asList(key);
        }

        /**
         * @param keys Keys involved.
         */
        protected AsyncOp(Collection<? extends K> keys) {
            this.keys = keys;
        }

        /**
         * @return Keys.
         */
        Collection<? extends K> keys() {
            return keys;
        }

        /**
         * @param tx Transaction.
         * @return Operation return value.
         */
        public abstract GridFuture<T> op(GridCacheTxLocalAdapter<K, V> tx);
    }

    /**
     * Cache operation.
     */
    private abstract class AsyncInOp extends AsyncOp<Object> {
        /**
         * @param key Key.
         */
        protected AsyncInOp(K key) {
            super(key);
        }

        /**
         * @param keys Keys involved.
         */
        protected AsyncInOp(Collection<? extends K> keys) {
            super(keys);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public final GridFuture<Object> op(GridCacheTxLocalAdapter<K, V> tx) {
            return (GridFuture<Object>)inOp(tx);
        }

        /**
         * @param tx Transaction.
         * @return Operation return value.
         */
        public abstract GridFuture<?> inOp(GridCacheTxLocalAdapter<K, V> tx);
    }
}
