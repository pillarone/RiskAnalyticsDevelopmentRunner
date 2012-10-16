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
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;
import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Adapter for cache entry.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public abstract class GridCacheMapEntry<K, V> extends GridMetadataAwareAdapter implements GridCacheEntryEx<K, V> {
    /** Cache registry. */
    @GridToStringExclude
    protected final GridCacheContext<K, V> cctx;

    /** Logger. */
    @GridToStringExclude
    protected final GridLogger log;

    /** Key. */
    @GridToStringInclude
    protected final K key;

    /** Value. */
    @GridToStringInclude
    protected V val;

    /** Start version. */
    @GridToStringInclude
    protected final GridCacheVersion startVer;

    /** Version. */
    @GridToStringInclude
    protected GridCacheVersion ver;

    /** Next entry in the linked list. */
    @GridToStringExclude
    private GridCacheMapEntry<K, V> next;

    /** Key hash code. */
    @GridToStringInclude
    private final int hash;

    /** Key bytes. */
    @GridToStringExclude
    private volatile byte[] keyBytes;

    /** Value bytes. */
    @GridToStringExclude
    protected byte[] valBytes;

    /** Time to live. */
    @GridToStringInclude
    protected long ttl;

    /** Expiration time. */
    @GridToStringInclude
    protected long expireTime;

    /** Removed flag. */
    @GridToStringInclude
    protected GridCacheVersion obsoleteVer;

    /** Metrics. */
    @GridToStringInclude
    protected GridCacheMetricsAdapter metrics;

    /** Lock owner. */
    @GridToStringInclude
    protected final GridCacheMvcc<K> mvcc;

    /** Refreshing flag. */
    protected boolean isRefreshing;

    /** Wrapper around entry. */
    @GridToStringExclude
    protected volatile GridCacheEntryImpl<K, V> wrapper;

    /** Read-write lock. */
    @GridToStringExclude
    protected final Object mux = new Object();

    /**
     * @param cctx Cache context.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    protected GridCacheMapEntry(GridCacheContext<K, V> cctx, K key, int hash, V val,
        GridCacheMapEntry<K, V> next, long ttl) {
        this.key = key;
        this.val = val;
        this.hash = hash;
        this.next = next;
        this.cctx = cctx;
        this.ttl = ttl;

        ver = startVer = cctx.versions().next();

        expireTime = toExpireTime(ttl);

        log = cctx.logger(getClass());

        metrics = new GridCacheMetricsAdapter(cctx.cache().metrics0());

        mvcc = new GridCacheMvcc<K>(cctx);
    }

    /** {@inheritDoc} */
    @Override public boolean isNew() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return ver == startVer;
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion startVersion() {
        return startVer;
    }

    /** {@inheritDoc} */
    @Override public boolean valid() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public int partition() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public boolean partitionValid() {
        return true;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheEntryInfo<K, V> info() {
        GridCacheEntryInfo<K, V> info = new GridCacheEntryInfo<K, V>();

        info.key(key);

        synchronized (mux) {
            if (obsolete())
                info = null;
            else {
                info.keyBytes(keyBytes);
                info.value(val);
                info.valueBytes(valBytes);
                info.ttl(ttl);
                info.expireTime(expireTime);
                info.version(ver);
                info.metrics(GridCacheMetricsAdapter.copyOf(metrics));
            }
        }

        return info;
    }

    /**
     * Unswaps an entry.
     *
     * @throws GridException If failed.
     */
    @Override public void unswap() throws GridException {
        if (cctx.isSwapEnabled())
            synchronized (mux) {
                if (startVer == ver) {
                    GridCacheSwapEntry<V> e = cctx.swap().readAndRemove(this);

                    if (log.isDebugEnabled())
                        log.debug("Read swap entry [swapEntry=" + e + ", cacheEntry=" + this + ']');

                    // If there is a value.
                    if (e != null) {
                        long delta = e.expireTime() == 0 ? 0 : e.expireTime() - System.currentTimeMillis();

                        if (delta >= 0) {
                            // Set unswapped value.
                            update(e.value(), e.valueBytes(), e.expireTime(), e.ttl(), e.version(), e.metrics());

                            cctx.events().addEvent(partition(), key, cctx.nodeId(), (UUID)null, null,
                                EVT_CACHE_OBJECT_UNSWAPPED, null, null);
                        }
                        else
                            clearIndex();
                    }
                }
            }
    }

    /**
     * @throws GridException If failed.
     */
    protected void swap() throws GridException {
        if (cctx.isSwapEnabled()) {
            // Even though we know that mutex is held, still synchronize here.
            // The overhead is minimal, but we get the benefit of IDE tracking
            // that the field is never accessed outside of synchronization.
            synchronized (mux) {
                if (expireTime > 0 && System.currentTimeMillis() >= expireTime)
                    // Don't swap entry if it's expired.
                    return;

                if (valBytes == null)
                    valBytes = CU.marshal(cctx, val).getEntireArray();

                UUID clsLdrId = null;

                if (val != null)
                    clsLdrId = cctx.deploy().getClassLoaderId(val.getClass().getClassLoader());

                cctx.swap().write(getOrMarshalKeyBytes(), valBytes, ver, ttl, expireTime, metrics, clsLdrId);

                cctx.events().addEvent(partition(), key, cctx.nodeId(), (UUID)null, null,
                    EVT_CACHE_OBJECT_SWAPPED, null, null);
            }

            if (log.isDebugEnabled())
                log.debug("Wrote swap entry: " + this);
        }
    }

    /**
     * @throws GridException If failed.
     */
    private void releaseSwap() throws GridException {
        if (cctx.isSwapEnabled()) {
            synchronized (mux) {
                cctx.swap().remove(getOrMarshalKeyBytes());
            }

            if (log.isDebugEnabled())
                log.debug("Removed swap entry [entry=" + this + ']');
        }
    }

    /**
     * @param tx Transaction.
     * @param key Key.
     * @param matchVer Version to match.
     */
    protected void refreshAhead(final GridCacheTx tx, final K key, final GridCacheVersion matchVer) {
        if (log.isDebugEnabled())
            log.debug("Scheduling asynchronous refresh for entry: " + this);

        // Asynchronous execution (we don't check filter here).
        cctx.closures().runLocalSafe(new GPR() {
                /** {@inheritDoc} */
                @SuppressWarnings({"unchecked"})
                @Override public void run() {
                    if (log.isDebugEnabled())
                        log.debug("Refreshing-ahead entry: " + GridCacheMapEntry.this);

                    synchronized (mux) {
                        // If there is a point to refresh.
                        if (!matchVer.equals(ver)) {
                            isRefreshing = false;

                            if (log.isDebugEnabled())
                                log.debug("Will not refresh value as entry has been recently updated: " +
                                    GridCacheMapEntry.this);

                            return;
                        }
                    }

                    V val = null;

                    try {
                        val = (V)CU.loadFromStore(cctx, log, tx, key);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to refresh-ahead entry: " + GridCacheMapEntry.this, e);
                    }
                    finally {
                        synchronized (mux) {
                            isRefreshing = false;

                            // If version matched, set value. Note that we don't update
                            // swap here, as asynchronous refresh happens only if
                            // value is already in memory.
                            if (val != null && matchVer.equals(ver)) {
                                // Don't change version for read-through.
                                update(val, null, toExpireTime(ttl), ttl, ver, metrics);

                                try {
                                    updateIndex(val);
                                }
                                catch (GridException e) {
                                    U.error(log, "Failed to update cache index: " + GridCacheMapEntry.this, e);
                                }
                            }
                        }
                    }
                }
            }, true);
    }

    /**
     * @param tx Transaction.
     * @param key Key.
     * @param reload flag.
     * @param filter Filter.
     * @return Read value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable protected V readThrough(GridCacheTx tx, K key, boolean reload,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        // NOTE: Do not remove explicit cast as it breaks ANT builds.
        return CU.<K, V>loadFromStore(cctx, log, tx, key);
    }

    /** {@inheritDoc} */
    @Nullable @Override public final V innerGet(GridCacheTx tx, boolean readSwap, boolean readThrough, boolean failFast,
        boolean updateMetrics, boolean evt, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheEntryRemovedException, GridCacheFilterFailedException {
        cctx.denyOnFlag(LOCAL);

        return innerGet0(tx, readSwap, readThrough, evt, failFast, updateMetrics, filter);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "RedundantTypeArguments"})
    private V innerGet0(GridCacheTx tx, boolean readSwap, boolean readThrough, boolean evt, boolean failFast,
        boolean updateMetrics, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheEntryRemovedException, GridCacheFilterFailedException {
        // Disable read-through if there is no store.
        if (cctx.config().getStore() == null)
            readThrough = false;

        GridCacheMvccCandidate<K> owner = null;

        V old = null;
        V ret = null;

        try {
            if (!cctx.isAll(this, filter))
                return CU.<V>failed(failFast);

            boolean asyncRefresh = false;

            GridCacheVersion startVer;

            boolean expired = false;

            V expiredVal = null;

            synchronized (mux) {
                checkObsolete();

                // Cache version for optimistic check.
                startVer = ver;

                owner = mvcc.anyOwner();

                double delta = Double.MAX_VALUE;

                if (expireTime > 0) {
                    delta = expireTime - System.currentTimeMillis();

                    if (log.isDebugEnabled())
                        log.debug("Checked expiration time for entry [timeLeft=" + delta + ", entry=" + this + ']');

                    if (delta <= 0)
                        expired = true;
                }

                // Attempt to load from swap.
                if (val == null && readSwap) {
                    // Only unswap when loading initial state.
                    if (isNew())
                        // If this entry is already expired (expiration time was too low),
                        // we simply remove from swap and clear index.
                        if (expired) {
                            releaseSwap();

                            clearIndex();
                        }
                        else {
                            // Read and remove swap entry.
                            unswap();

                            // Recalculate expiration after swap read.
                            if (expireTime > 0) {
                                delta = expireTime - System.currentTimeMillis();

                                if (delta <= 0)
                                    expired = true;
                            }
                        }
                }

                // Only calculate asynchronous refresh-ahead, if there is no other
                // one in progress and if not expired.
                if (delta > 0 && expireTime > 0 && !isRefreshing) {
                    double refreshRatio = cctx.config().getRefreshAheadRatio();

                    if (1 - delta / ttl >= refreshRatio)
                        asyncRefresh = true;
                }

                old = expired || !valid() ? null : val;

                if (expired)
                    expiredVal = val;

                if (old == null) {
                    asyncRefresh = false;

                    if (updateMetrics)
                        metrics.onRead(false);
                }
                else {
                    if (updateMetrics)
                        metrics.onRead(true);

                    // Set retVal here for event notification.
                    ret = old;

                    // Mark this entry as refreshing, so other threads won't schedule
                    // asynchronous refresh while this one is in progress.
                    if (asyncRefresh && readThrough)
                        isRefreshing = true;
                }
            }

            if (evt && expired)
                cctx.events().addEvent(partition(), key, tx, owner, EVT_CACHE_OBJECT_EXPIRED, null, expiredVal);

            if (asyncRefresh && readThrough) {
                assert ret != null;

                refreshAhead(tx, key, startVer);
            }

            // Check before load.
            if (!cctx.isAll(this, filter))
                return CU.<V>failed(failFast, ret);

            if (ret != null) {
                // If return value is consistent, then done.
                if (F.isEmpty(filter) || version().equals(startVer))
                    return ret;

                // Try again (recursion).
                return innerGet0(tx, readSwap, readThrough, false, failFast, updateMetrics, filter);
            }

            boolean loadedFromStore = false;

            if (ret == null && readThrough) {
                ret = readThrough(tx, key, false, filter);

                loadedFromStore = true;
            }

            boolean match = false;

            synchronized (mux) {
                // If version matched, set value.
                if (startVer.equals(ver)) {
                    match = true;

                    if (ret != null) {
                        GridCacheVersion nextVer = cctx.versions().next();

                        // Don't change version for read-through.
                        update(ret, null, toExpireTime(ttl), ttl, nextVer, metrics);
                    }

                    if (loadedFromStore)
                        // Update indexes.
                        updateIndex(ret);
                }
            }

            if (F.isEmpty(filter))
                return ret;
            else {
                if (!match)
                    // Try again (recursion).
                    return innerGet0(tx, readSwap, readThrough, false, failFast, updateMetrics, filter);

                return ret;
            }
        }
        finally {
            if (evt)
                cctx.events().addEvent(partition(), key, tx, owner, EVT_CACHE_OBJECT_READ, ret, old);

            // Touch entry right away for read-committed mode.
            if (tx == null || tx.isolation() == READ_COMMITTED)
                cctx.evicts().touch(this);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public final V innerReload(GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheEntryRemovedException {
        cctx.denyOnFlag(READ);

        CU.checkStore(cctx);

        GridCacheVersion startVer;

        boolean wasNew;

        synchronized (mux) {
            checkObsolete();

            // Cache version for optimistic check.
            startVer = ver;

            wasNew = isNew();
        }

        // Generate new version.
        GridCacheVersion nextVer = cctx.versions().next();

        // Check before load.
        if (cctx.isAll(this, filter)) {
            V ret = readThrough(null, key, true, filter);

            synchronized (mux) {
                // If entry was loaded during read step.
                if (wasNew && !isNew())
                    return ret;

                // If version matched, set value.
                if (startVer.equals(ver)) {
                    releaseSwap();

                    update(ret, null, toExpireTime(ttl), ttl, nextVer, metrics);

                    // Update indexes.
                    updateIndex(ret);

                    // If value was set - return, otherwise try again.
                    return ret;
                }
            }

            if (F.isEmpty(filter))
                return ret;

            // Recursion.
            return innerReload(filter);
        }

        // If filter didn't pass.
        return null;
    }

    /**
     * @param nodeId Node ID.
     */
    protected void recordNodeId(UUID nodeId) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public final T2<Boolean, V> innerSet(GridCacheTxEx<K, V> tx, UUID evtNodeId, UUID affNodeId, V val,
        byte[] valBytes, boolean writeThrough, long expireTime, long ttl, boolean evt,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException, GridCacheEntryRemovedException {
        V old = null;

        GridCacheVersion newVer = null;

        try {
            boolean valid = valid();

            // Lock should be held by now.
            if (!cctx.isAll(this, filter))
                return new T2<Boolean, V>(false, null);

            synchronized (mux) {
                checkObsolete();

                newVer = ver;

                assert tx == null || tx.ownsLock(this) : "Transaction does not own lock for update [entry=" + this +
                    ", tx=" + tx + ']';

                // For EVENTUALLY_CONSISTENT transactions change state only if the
                // version is higher.
                if (tx != null && tx.ec() && tx.commitVersion().compareTo(ver) < 0)
                    return new T2<Boolean, V>(true, this.val);

                // Load and remove from swap if is new.
                if (isNew())
                    unswap();

                old = this.val;

                newVer = tx == null ? cctx.versions().next() : tx.commitVersion();

                update(val, valBytes, expireTime, ttl, newVer, metrics);

                recordNodeId(affNodeId);

                metrics.onWrite();

                // Update index inside synchronization since it can be updated
                // in load methods without actually holding entry lock.
                if (val != null)
                    updateIndex(val);
            }

            if (log.isDebugEnabled())
                log.debug("Updated cache entry [val=" + val + ", old=" + old + ", entry=" + this + ']');

            // Persist outside of synchronization. The correctness of the
            // value will be handled by current transaction.
            if (writeThrough)
                CU.putToStore(cctx, log, tx, key, val);

            return valid ? new T2<Boolean, V>(true, old) : new T2<Boolean, V>(false, null);
        }
        finally {
            if (evt && newVer != null)
                cctx.events().addEvent(
                    partition(),
                    key,
                    evtNodeId,
                    tx == null ? null : tx.xid(),
                    newVer.id(),
                    EVT_CACHE_OBJECT_PUT,
                    val,
                    old);
        }
    }

    /** {@inheritDoc} */
    @Override public final T2<Boolean, V> innerRemove(GridCacheTxEx<K, V> tx, UUID evtNodeId, UUID affNodeId,
        boolean writeThrough, boolean evt, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheEntryRemovedException {
        V old = null;

        GridCacheVersion newVer = tx == null ? cctx.versions().next() : tx.commitVersion();

        try {
            boolean valid = valid();

            // Lock should be held by now.
            if (!cctx.isAll(this, filter))
                return new T2<Boolean, V>(false, null);

            GridCacheVersion obsoleteVer = null;

            synchronized (mux) {
                checkObsolete();

                assert tx == null || tx.ownsLock(this) : "Transaction does not own lock for remove [entry=" + this +
                    ", tx=" + tx + ']';

                // For EVENTUALLY_CONSISTENT transactions change state only if the
                // version is higher.
                if (tx != null && tx.ec() && tx.commitVersion().compareTo(ver) < 0)
                    return new T2<Boolean, V>(true, val);

                // Release swap if needed.
                if (isNew())
                    releaseSwap();

                // Clear indexes inside of synchronization since indexes
                // can be updated without actually holding entry lock.
                clearIndex();

                old = val;

                update(null, null, toExpireTime(ttl), ttl, newVer, metrics);

                metrics.onWrite();

                if (tx == null)
                    obsoleteVer = newVer;
                else
                    // Only delete entry if the lock is not explicit.
                    if (lockedBy(tx.xidVersion()))
                        obsoleteVer = tx.xidVersion();
                    else if (log.isDebugEnabled())
                        log.debug("Obsolete version was not set because lock was explicit: " + this);
            }

            // Persist outside of synchronization. The correctness of the
            // value will be handled by current transaction.
            if (writeThrough)
                CU.removeFromStore(cctx, log, tx, key);

            synchronized (mux) {
                // If entry is still removed.
                if (newVer == ver)
                    if (obsoleteVer == null || !markObsolete(obsoleteVer)) {
                        if (log.isDebugEnabled())
                            log.debug("Entry could not be marked obsolete (it is still used): " + this);
                    }
                    else {
                        recordNodeId(affNodeId);

                        // If entry was not marked obsolete, then removed lock
                        // will be registered whenever removeLock is called.
                        cctx.mvcc().addRemoved(obsoleteVer);

                        if (log.isDebugEnabled())
                            log.debug("Entry was marked obsolete: " + this);
                    }
            }

            return valid ? new T2<Boolean, V>(true, old) : new T2<Boolean, V>(false, null);
        }
        finally {
            if (evt && newVer != null)
                cctx.events().addEvent(partition(), key, evtNodeId, tx == null ? null : tx.xid(), newVer.id(),
                    EVT_CACHE_OBJECT_REMOVED, null, old);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean clearIfNew(GridCacheVersion ver) throws GridException {
        // Don't check flags here as this method is for internal use only.
        // ==============================================================

        synchronized (mux) {
            if (ver != startVer)
                return false;

            if (!markObsolete(ver)) {
                if (log.isDebugEnabled())
                    log.debug("Entry could not be marked obsolete (it is still used): " + this);

                return false;
            }

            if (log.isDebugEnabled())
                log.debug("Entry has been marked obsolete: " + this);

            // Give to GC.
            update(null, null, toExpireTime(ttl), ttl, ver, metrics);

            clearIndex();

            return true;
        }
    }

    /**
     * @return {@code true} if entry has readers. It makes sense only for dht entry.
     * @throws GridCacheEntryRemovedException If removed.
     */
    protected boolean hasReaders() throws GridCacheEntryRemovedException {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean clear(GridCacheVersion ver, boolean swap,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cctx.denyOnFlag(READ);

        // For optimistic check.
        GridCacheVersion startVer;

        synchronized (mux) {
            startVer = ver;
        }

        if (!cctx.isAll(this, filter))
            return false;

        synchronized (mux) {
            if (!startVer.equals(ver))
                // Version has changed since filter checking.
                return clear(ver, swap, filter);

            try {
                if (hasReaders() || !markObsolete(ver)) {
                    if (log.isDebugEnabled())
                        log.debug("Entry could not be marked obsolete (it is still used or has readers): " + this);

                    return false;
                }
            }
            catch (GridCacheEntryRemovedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Got removed entry when clearing (will simply return): " + this);

                return true;
            }

            if (log.isDebugEnabled())
                log.debug("Entry has been marked obsolete: " + this);

            // Give to GC.
            update(null, null, toExpireTime(ttl), ttl, ver, metrics);

            clearIndex();

            if (swap) {
                releaseSwap();

                if (log.isDebugEnabled())
                    log.debug("Entry has been cleared from swap storage: " + this);
            }

            return true;
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion obsoleteVersion() {
        synchronized (mux) {
            return obsoleteVer;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean markObsolete(GridCacheVersion ver) {
        return markObsolete(ver, false);
    }

    /** {@inheritDoc} */
    @Override public boolean markObsolete(GridCacheVersion ver, boolean clear) {
        if (ver != null) {
            synchronized (mux) {
                // If already obsolete, then do nothing.
                if (obsoleteVer != null)
                    return true;

                if (mvcc.isEmpty(ver)) {
                    obsoleteVer = ver;

                    if (clear) {
                        val = null;
                        valBytes = null;
                    }
                }

                return obsoleteVer != null;
            }
        }
        else {
            synchronized (mux) {
                return obsoleteVer != null;
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean obsolete() {
        synchronized (mux) {
            return obsoleteVer != null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean obsolete(GridCacheVersion exclude) {
        synchronized (mux) {
            return obsoleteVer != null && !obsoleteVer.equals(exclude);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(@Nullable GridCacheVersion curVer, GridCacheVersion newVer)
        throws GridException {
        assert newVer != null;

        synchronized (mux) {
            if (curVer == null || ver.equals(curVer)) {
                val = null;
                valBytes = null;

                ver = newVer;

                releaseSwap();

                clearIndex();
            }

            return obsoleteVer != null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridException {
        if (F.isEmpty(filter)) {
            synchronized (mux) {
                checkObsolete();

                invalidate(null, cctx.versions().next());

                return true;
            }
        }
        else {
            // For optimistic checking.
            GridCacheVersion startVer;

            synchronized (mux) {
                checkObsolete();

                startVer = ver;
            }

            if (!cctx.isAll(this, filter))
                return false;

            synchronized (mux) {
                checkObsolete();

                if (startVer.equals(ver)) {
                    invalidate(null, cctx.versions().next());

                    return true;
                }
            }

            // If version has changed then repeat the process.
            return invalidate(filter);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean compact(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridException {
        // For optimistic checking.
        GridCacheVersion startVer;

        synchronized (mux) {
            checkObsolete();

            startVer = ver;
        }

        if (!cctx.isAll(this, filter))
            return false;

        synchronized (mux) {
            checkObsolete();

            if (startVer.equals(ver))
                if (val != null) {
                    valBytes = null;

                    return false;
                }
                else
                    return clear(cctx.versions().next(), cctx.isSwapEnabled(), filter);
        }

        // If version has changed do it again.
        return compact(filter);
    }

    /**
     *
     * @param val New value.
     * @param valBytes New value bytes.
     * @param expireTime Expiration time.
     * @param ttl Time to live.
     * @param ver Update version.
     * @param metrics Metrics.
     */
    protected void update(V val, byte[] valBytes, long expireTime, long ttl, GridCacheVersion ver,
        GridCacheMetricsAdapter metrics) {
        assert ver != null;

        synchronized (mux) {
            this.val = val;
            this.valBytes = valBytes;
            this.ttl = ttl;
            this.expireTime = expireTime;
            this.ver = ver;

            if (metrics != null)
                this.metrics = metrics;
        }
    }

    /**
     * @param ttl Time to live.
     * @return Expiration time.
     */
    protected long toExpireTime(long ttl) {
        long expireTime = ttl == 0 ? 0 : System.currentTimeMillis() + ttl;

        // Account for overflow.
        if (expireTime < 0)
            expireTime = 0;

        return expireTime;
    }

    /**
     * @throws GridCacheEntryRemovedException If entry is obsolete.
     */
    protected void checkObsolete() throws GridCacheEntryRemovedException {
        // Even though we know that mutex is held, still synchronize here.
        // The overhead is minimal, but we get the benefit of IDE tracking
        // that the field is never accessed outside of synchronization.
        synchronized (mux) {
            if (obsoleteVer != null)
                throw new GridCacheEntryRemovedException();
        }
    }

    /** {@inheritDoc} */
    @Override public K key() {
        return key;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return ver;
        }
    }

    /**
     * Gets hash value for the entry key.
     *
     * @return Hash value.
     */
    int hash() {
        return hash;
    }

    /**
     * @return Next entry in the linked list.
     */
    GridCacheMapEntry<K, V> next() {
        synchronized (mux) {
            return next;
        }
    }

    /**
     * @param next Next entry in the linked list.
     */
    void next(GridCacheMapEntry<K, V> next) {
        synchronized (mux) {
            this.next = next;
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peek(GridCachePeekMode mode, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException {
        try {
            return peek0(false, mode, filter, cctx.tm().<GridCacheTxEx<K, V>>tx());
        }
        catch (GridCacheFilterFailedException ignore) {
            assert false;

            return null;
        }
        catch (GridException e) {
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public V peek(Collection<GridCachePeekMode> modes, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException {
        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peek(mode, filter);

            if (val != null)
                return val;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public V peekFailFast(GridCachePeekMode mode,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridCacheFilterFailedException {
        try {
            return peek0(true, mode, filter, cctx.tm().<GridCacheTxEx<K, V>>tx());
        }
        catch (GridException e) {
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /**
     * @param failFast Fail-fast flag.
     * @param mode Peek mode.
     * @param filter Filter.
     * @param tx Transaction to peek value at (if mode is TX value).
     * @return Peeked value.
     * @throws GridException In case of error.
     * @throws GridCacheEntryRemovedException If removed.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable @Override public V peek0(boolean failFast, GridCachePeekMode mode,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheTxEx<K, V> tx)
        throws GridCacheEntryRemovedException, GridCacheFilterFailedException, GridException {
        switch (mode) {
            case TX:
                return peekTx(failFast, filter, tx);

            case GLOBAL:
                return peekGlobal(failFast, filter);

            case SMART:
                /*
                * If there is no ongoing transaction, or transaction is NOT in ACTIVE state,
                * which means that it is either rolling back, preparing to commit, or committing,
                * then we only check the global cache storage because value has already been
                * validated against filter and enlisted into transaction and, therefore, second
                * validation against the same enlisted value will be invalid (it will always be false).
                *
                * However, in ACTIVE state, we must also validate against other values that
                * may have enlisted into the same transaction and that's why we pass 'true'
                * to 'e.peek(true)' method in this case.
                */
                return tx == null || tx.state() != ACTIVE ? peekGlobal(failFast, filter) :
                    peekTxThenGlobal(failFast, filter, tx);

            case SWAP:
                return peekSwap(failFast, filter);

            case DB:
                return peekDb(failFast, filter);

            default: // Should never be reached.
                assert false;

                return null;
        }
    }

    /**
     * @param failFast Fail fast flag.
     * @param filter Filter.
     * @param tx Transaction to peek value at (if mode is TX value).
     * @return Peeked value.
     * @throws GridCacheFilterFailedException If filter failed.
     * @throws GridCacheEntryRemovedException If entry got removed.
     * @throws GridException If unexpected cache failure occurred.
     */
    @Nullable private V peekTxThenGlobal(boolean failFast, GridPredicate<? super GridCacheEntry<K, V>>[] filter,
        GridCacheTxEx<K, V> tx) throws GridCacheFilterFailedException, GridCacheEntryRemovedException, GridException {
        V v = peekTx(failFast, filter, tx);

        return v == null ? peekGlobal(failFast, filter) : v;
    }

    /**
     * @param failFast Fail fast flag.
     * @param filter Filter.
     * @param tx Transaction to peek value at (if mode is TX value).
     * @return Peeked value.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @Nullable private V peekTx(boolean failFast, GridPredicate<? super GridCacheEntry<K, V>>[] filter,
        GridCacheTxEx<K, V> tx) throws GridCacheFilterFailedException {
        return tx == null ? null : tx.peek(failFast, key, filter);
    }

    /**
     * @param failFast Fail fast flag.
     * @param filter Filter.
     * @return Peeked value.
     * @throws GridCacheFilterFailedException If filter failed.
     * @throws GridCacheEntryRemovedException If entry got removed.
     * @throws GridException If unexpected cache failure occurred.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable private V peekGlobal(boolean failFast, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridCacheFilterFailedException, GridException {
        if (!valid())
            return null;

        while (true) {
            GridCacheVersion ver;
            V val;

            synchronized (mux) {
                checkObsolete();

                ver = this.ver;
                val = this.val;
            }

            if (!cctx.isAll(wrap(false), filter))
                return CU.<V>failed(failFast);

            if (F.isEmpty(filter) || ver.equals(version()))
                return val;
        }
    }

    /**
     * @param failFast Fail fast flag.
     * @param filter Filter.
     * @return Value from swap storage.
     * @throws GridException In case of any errors.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekSwap(boolean failFast, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheFilterFailedException {
        if (!cctx.isAll(wrap(false), filter))
            return (V)CU.failed(failFast);

        GridCacheSwapEntry<V> e = cctx.swap().read(this);

        return e != null ? e.value() : null;
    }

    /**
     * @param failFast Fail fast flag.
     * @param filter Filter.
     * @return Value from persistent store.
     * @throws GridException In case of any errors.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekDb(boolean failFast, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheFilterFailedException {
        if (!cctx.isAll(wrap(false), filter))
            return (V)CU.failed(failFast);

        return (V)CU.loadFromStore(cctx, log, cctx.tm().<GridCacheTx>tx(), key);
    }

    /**
     *
     * @return Value.
     */
    @Override public V rawGet() {
        synchronized (mux) {
            return val;
        }
    }

    /**
     *
     * @param val New value.
     * @param ttl Time to live.
     * @return Old value.
     */
    @Override public V rawPut(V val, long ttl) {
        synchronized (mux) {
            V old = this.val;

            update(val, null, toExpireTime(ttl), ttl, cctx.versions().next(), metrics);

            return old;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Override public boolean initialValue(V val, byte[] valBytes, GridCacheVersion ver, long ttl,
        long expireTime, GridCacheMetricsAdapter metrics) throws GridException, GridCacheEntryRemovedException {

        if (valBytes != null && val == null && isNew())
            val = U.<V>unmarshal(cctx.marshaller(), new GridByteArrayList(valBytes), cctx.deploy().globalLoader());

        synchronized (mux) {
            checkObsolete();

            if (isNew()) {
                // Version does not change for load ops.
                update(val, valBytes, expireTime, ttl, ver, metrics);

                if (val != null)
                    updateIndex(val);

                return true;
            }

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean initialValue(K key, GridCacheSwapEntry<V> unswapped) throws GridException,
        GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            if (isNew()) {
                // Version does not change for load ops.
                update(unswapped.value(),
                    unswapped.valueBytes(),
                    unswapped.expireTime(),
                    unswapped.ttl(),
                    unswapped.version(),
                    unswapped.metrics());

                return true;
            }

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean versionedValue(V val, GridCacheVersion curVer, GridCacheVersion newVer)
        throws GridException, GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            if (curVer == null || curVer.equals(ver)) {
                if (newVer == null)
                    newVer = cctx.versions().next();

                // Version does not change for load ops.
                update(val, null, toExpireTime(ttl), ttl, newVer, metrics);

                if (val != null)
                    updateIndex(val);

                return true;
            }

            return false;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"NakedNotify", "NotifyWithoutCorrespondingWait"})
    @Override public void wakeUp() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            mux.notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasLockCandidate(GridCacheVersion ver) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.hasCandidate(ver);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasLockCandidate(long threadId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.localCandidate(threadId) != null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByAny(GridCacheVersion[] exclude) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return !mvcc.isEmpty(exclude);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread() throws GridCacheEntryRemovedException {
        return lockedByThread(Thread.currentThread().getId());
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(GridCacheVersion exclude) throws GridCacheEntryRemovedException {
        return lockedByThread(Thread.currentThread().getId(), exclude);
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocally(UUID lockId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.isLocallyOwned(lockId);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId,
        GridCacheVersion exclude) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.isLocallyOwnedByThread(threadId, exclude);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.isLocallyOwnedByThread(threadId);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedBy(GridCacheVersion ver) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.isOwnedBy(ver);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThreadUnsafe(long threadId) {
        synchronized (mux) {
            return mvcc.isLocallyOwnedByThread(threadId);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByUnsafe(GridCacheVersion ver) {
        synchronized (mux) {
            return mvcc.isOwnedBy(ver);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocallyUnsafe(UUID lockId) {
        synchronized (mux) {
            return mvcc.isLocallyOwned(lockId);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasLockCandidateUnsafe(GridCacheVersion ver) {
        synchronized (mux) {
            return mvcc.hasCandidate(ver);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheMvccCandidate<K>> localCandidates(GridCacheVersion... exclude)
        throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.localCandidates(exclude);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheMvccCandidate<K>> remoteMvccSnapshot(GridCacheVersion... exclude) {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheMvccCandidate<K> candidate(
        GridCacheVersion ver) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.candidate(ver);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localCandidate(long threadId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.localCandidate(threadId);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> candidate(UUID nodeId, long threadId)
        throws GridCacheEntryRemovedException {
        boolean local = cctx.nodeId().equals(nodeId);

        synchronized (mux) {
            checkObsolete();

            return local ? mvcc.localCandidate(threadId) : mvcc.remoteCandidate(nodeId, threadId);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localOwner() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.localOwner();
        }
    }

    /** {@inheritDoc} */
    @Override public long expireTime() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return expireTime;
        }
    }

    /** {@inheritDoc} */
    @Override public long ttl() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return ttl;
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return metrics;
        }
    }

    /** {@inheritDoc} */
    @Override public void keyBytes(byte[] keyBytes) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            if (keyBytes != null)
                this.keyBytes = keyBytes;
        }
    }

    /** {@inheritDoc} */
    @Override public byte[] keyBytes() {
        synchronized (mux) {
            return keyBytes;
        }
    }

    /** {@inheritDoc} */
    @Override public byte[] getOrMarshalKeyBytes() throws GridException {
        byte[] bytes = keyBytes();

        if (bytes != null)
            return bytes;

        bytes = CU.marshal(cctx, key).getEntireArray();

        synchronized (mux) {
            keyBytes = bytes;
        }

        return bytes;
    }

    /** {@inheritDoc} */
    @Override public byte[] valueBytes() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return valBytes;
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public byte[] valueBytes(GridCacheVersion ver)
        throws GridException, GridCacheEntryRemovedException {
        V val = null;
        byte[] valBytes = null;

        synchronized (mux) {
            checkObsolete();

            if (ver == null || this.ver.equals(ver)) {
                val = this.val;
                ver = this.ver;
                valBytes = this.valBytes;
            }
            else
                ver = null;
        }

        if (valBytes == null) {
            if (val != null)
                valBytes = CU.marshal(cctx, val).getEntireArray();

            if (ver != null) {
                synchronized (mux) {
                    checkObsolete();

                    if (this.val == val)
                        this.valBytes = valBytes;
                }
            }
        }

        return valBytes;
    }

    /**
     * Updates cache index.
     *
     * @param val New value.
     * @throws GridException If update failed.
     */
    protected void updateIndex(V val) throws GridException {
        // Even though we know that mutex is held, still synchronize here.
        // The overhead is minimal, but we get the benefit of IDE tracking
        // that the field is never accessed outside of synchronization.
        synchronized (mux) {
            if (val == null)
                clearIndex();
            else {
                GridCacheQueryManager<K, V> qryMgr = cctx.queries();

                if (qryMgr != null)
                    qryMgr.store(key(), keyBytes(), val, ver);
            }
        }
    }

    /**
     * Clears index.
     *
     * @throws GridException If failed.
     */
    protected void clearIndex() throws GridException {
        // Even though we know that mutex is held, still synchronize here.
        // The overhead is minimal, but we get the benefit of IDE tracking
        // that the field is never accessed outside of synchronization.
        synchronized (mux) {
            GridCacheQueryManager<K, V> qryMgr = cctx.queries();

            if (qryMgr != null)
                qryMgr.remove(key(), keyBytes());
        }
    }

    /**
     * Wraps this map entry into cache entry.
     *
     * @param prjAware {@code true} if entry should inherit projection properties.
     * @return Wrapped entry.
     */
    @Override public GridCacheEntry<K, V> wrap(boolean prjAware) {
        GridCacheProjectionImpl<K, V> prjPerCall = cctx.projectionPerCall();

        if (prjPerCall != null && prjAware)
            return new GridCacheEntryImpl<K, V>(prjPerCall, cctx, key, this);

        GridCacheEntryImpl<K, V> wrapper = this.wrapper;

        if (wrapper == null)
            this.wrapper = wrapper = new GridCacheEntryImpl<K, V>(null, cctx, key, this);

        return wrapper;
    }

    /** {@inheritDoc} */
    @Override public boolean evictInternal(boolean swap, GridCacheVersion obsoleteVer,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        try {
            if (F.isEmpty(filter)) {
                synchronized (mux) {
                    if ((!hasReaders() || swap) && markObsolete(obsoleteVer)) {
                        if (swap)
                            if (startVer != ver)
                                try {
                                    // Write to swap.
                                    swap();
                                }
                                catch (GridException e) {
                                    U.error(log, "Failed to write entry to swap storage: " + this, e);
                                }

                        // Nullify value after swap.
                        val = null;

                        return true;
                    }
                }
            }
            else {
                // For optimistic check.
                GridCacheVersion v;

                synchronized (mux) {
                    v = ver;
                }

                if (!cctx.isAll(this, filter))
                    return false;

                synchronized (mux) {
                    if (!v.equals(ver))
                        // Version has changed since entry passed the filter. Do it again.
                        return evictInternal(swap, obsoleteVer, filter);

                    if ((!hasReaders() || swap) && markObsolete(obsoleteVer)) {
                        if (swap)
                            if (startVer != ver)
                                try {
                                    // Write to swap.
                                    swap();
                                }
                                catch (GridException e) {
                                    U.error(log, "Failed to write entry to swap storage: " + this, e);
                                }

                        // Nullify value after swap.
                        val = null;

                        return true;
                    }
                }
            }
        }
        catch (GridCacheEntryRemovedException ignore) {
            if (log.isDebugEnabled())
                log.debug("Got removed entry when evicting (will simply return): " + this);

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean visitable(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            if (!cctx.isAll(wrap(false), filter) || obsolete())
                return false;
        }
        catch (GridException e) {
            U.error(log, "An exception was thrown while filter checking.", e);

            return false;
        }

        GridCacheTxEx<K, V> tx = cctx.tm().tx();

        return tx == null || !tx.removed(key);
    }

    /** {@inheritDoc} */
    @Override public void ttl(long ttl) throws GridCacheEntryRemovedException {
        assert ttl >= 0;

        synchronized (mux) {
            checkObsolete();

            if (ttl == 0) {
                this.ttl = 0;

                expireTime = toExpireTime(0);
            }
            else if (this.ttl == 0) {
                this.ttl = ttl;

                expireTime = toExpireTime(ttl);
            }
            else {
                long delta = ttl - this.ttl;

                if (delta < 0)
                    expireTime = expireTime + delta < 0 ? System.currentTimeMillis() : expireTime + delta;
                else {
                    expireTime += ttl - this.ttl;

                    // Account for overflow.
                    if (expireTime < 0)
                        expireTime = 0;

                    this.ttl = ttl;
                }
            }

            if (log.isDebugEnabled())
                log.debug("Set ttl [ttl=" + this.ttl + ", expireTime=" + expireTime + ", timeLeft=" +
                    (expireTime - System.currentTimeMillis()) + ']');
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable public ClassLoader keyClassLoader() {
        return key == null ? null : key.getClass().getClassLoader();
    }

    /** {@inheritDoc} */
    @Override @Nullable public ClassLoader valueClassLoader() {
        synchronized (mux) {
            return val == null ? null : val.getClass().getClassLoader();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof GridCacheMapEntry))
            return false;

        GridCacheMapEntry<?, ?> e = (GridCacheMapEntry<?, ?>)o;

        if (hash != e.hash)
            return false;

        Object k1 = key;
        Object k2 = e.key;

        return F.eq(k1, k2) && F.eq(rawGet(), e.rawGet());
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return hash;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheMapEntry.class, this);
    }
}
