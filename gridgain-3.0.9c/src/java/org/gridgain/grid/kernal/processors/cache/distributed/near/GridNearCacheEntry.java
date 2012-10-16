// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Replicated cache entry.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public class GridNearCacheEntry<K, V> extends GridDistributedCacheEntry<K, V> {
    /** ID of primary node from which this entry was last read. */
    private volatile UUID primaryNodeId;

    /** DHT version which caused the last update. */
    private GridCacheVersion dhtVer;

    /**
     * @param ctx Cache context.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    public GridNearCacheEntry(GridCacheContext<K, V> ctx, K key, int hash, V val, GridCacheMapEntry<K, V> next,
        long ttl) {
        super(ctx, key, hash, val, next, ttl);
    }

    /** {@inheritDoc} */
    @Override public boolean valid() {
        UUID primaryNodeId = this.primaryNodeId;

        if (primaryNodeId == null)
            return false;

        if (cctx.discovery().node(primaryNodeId) == null) {
            this.primaryNodeId = null;

            return false;
        }

        // Make sure that primary node is alive before returning this value.
        GridRichNode primary = F.first(cctx.affinity(key(), CU.allNodes(cctx)));

        if (primary != null && primary.id().equals(primaryNodeId))
            return true;

        // Primary node changed.
        this.primaryNodeId = null;

        return false;
    }

    /**
     * @return {@code True} if this entry was initialized by this call.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     */
    public boolean initializeFromDht() throws GridCacheEntryRemovedException {
        while (true) {
            GridDhtCacheEntry<K, V> entry = cctx.near().dht().peekExx(key);

            if (entry != null) {
                GridCacheEntryInfo<K, V> e = entry.info();

                if (e != null) {
                    synchronized (mux) {
                        checkObsolete();

                        if (isNew()) {
                            // Version does not change for load ops.
                            update(e.value(), e.valueBytes(), e.expireTime(), e.ttl(), e.version(), e.metrics());

                            dhtVer = e.version();

                            return true;
                        }

                        return false;
                    }
                }
            }
            else
                return false;
        }
    }

    /**
     * This method should be called only when lock is owned on this entry.
     *
     * @param val Value.
     * @param valBytes Value bytes.
     * @param ver Version.
     * @param dhtVer DHT version.
     * @param primaryNodeId Primary node ID.
     * @return {@code True} if reset was done.
     * @throws GridCacheEntryRemovedException If obsolete.
     * @throws GridException If failed.
     */
    @SuppressWarnings( {"RedundantTypeArguments"})
    public boolean resetFromPrimary(V val, byte[] valBytes, GridCacheVersion ver, GridCacheVersion dhtVer,
        UUID primaryNodeId) throws GridCacheEntryRemovedException, GridException {
        assert dhtVer != null;

        cctx.versions().onReceived(primaryNodeId, dhtVer);

        if (valBytes != null && val == null) {
            GridCacheVersion curDhtVer = dhtVersion();

            if (!F.eq(dhtVer, curDhtVer))
                val = U.<V>unmarshal(cctx.marshaller(), new GridByteArrayList(valBytes), cctx.deploy().globalLoader());
        }

        synchronized (mux) {
            checkObsolete();

            this.primaryNodeId = primaryNodeId;

            if (!F.eq(this.dhtVer, dhtVer)) {
                this.val = val;
                this.valBytes = valBytes;
                this.ver = ver;
                this.dhtVer = dhtVer;

                return true;
            }
        }

        return false;
    }

    /**
     * This method should be called only when lock is owned on this entry.
     *
     * @param dhtVer DHT version.
     * @param val Value associated with version.
     * @param valBytes Value bytes.
     * @param expireTime Expire time.
     * @param ttl Time to live.
     * @param primaryNodeId Primary node ID.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public void updateOrEvict(GridCacheVersion dhtVer, V val, byte[] valBytes, long expireTime, long ttl,
        UUID primaryNodeId) throws GridCacheEntryRemovedException {
        assert dhtVer != null;

        cctx.versions().onReceived(primaryNodeId, dhtVer);

        synchronized (mux) {
            checkObsolete();

            // Don't set DHT version to null until we get a match from DHT remote transaction.
            if (F.eq(this.dhtVer, dhtVer))
                this.dhtVer = null;

            // If we are here, then we already tried to evict this entry.
            // If cannot evict, then update.
            if (this.dhtVer == null) {
                if (!markObsolete(dhtVer, true)) {
                    this.val = val;
                    this.valBytes = valBytes;
                    this.expireTime = expireTime;
                    this.ttl = ttl;
                    this.primaryNodeId = primaryNodeId;
                }
            }
        }
    }

    /**
     * @return DHT version for this entry.
     * @throws GridCacheEntryRemovedException If obsolete.
     */
    @Nullable public GridCacheVersion dhtVersion() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return dhtVer;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isNew() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return startVer == ver || !valid();
        }
    }

    /**
     * @return ID of primary node from which this value was loaded.
     */
    UUID nodeId() {
        return primaryNodeId;
    }

    /** {@inheritDoc} */
    @Override protected void recordNodeId(UUID primaryNodeId) {
        // Even though synchronization is held when this method is called,
        // we synchronize here again - overhead is minimal, but code is cleaner.
        synchronized (mux) {
            this.primaryNodeId = primaryNodeId;
        }
    }

    /**
     * This method should be called only when committing optimistic transactions.
     *
     * @param dhtVer DHT version to record.
     */
    public void recordDhtVersion(GridCacheVersion dhtVer) {
        // Version manager must be updated separately, when adding DHT version
        // to transaction entries.
        synchronized (mux) {
            this.dhtVer = dhtVer;
        }
    }

    /** {@inheritDoc} */
    @Override protected void refreshAhead(GridCacheTx tx, K key, GridCacheVersion matchVer) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override protected V readThrough(GridCacheTx tx, K key, boolean reload,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cctx.near().loadAsync(F.asList(key), reload, filter).get().get(key);
    }

    /**
     * @param tx Transaction.
     * @param primaryNodeId Primary node ID.
     * @param val New value.
     * @param valBytes Value bytes.
     * @param ver Version to use.
     * @param ttl Time to live.
     * @param expireTime Expiration time.
     * @param evt Event flag.
     * @return {@code True} if initial value was set.
     * @throws GridException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    public boolean loadedValue(@Nullable GridCacheTx tx, UUID primaryNodeId, V val, byte[] valBytes,
        GridCacheVersion ver, long ttl, long expireTime, boolean evt) throws GridException,
        GridCacheEntryRemovedException {
        if (valBytes != null && val == null && isNew())
            val = U.<V>unmarshal(cctx.marshaller(), new GridByteArrayList(valBytes), cctx.deploy().globalLoader());

        try {
            synchronized (mux) {
                checkObsolete();

                if (metrics == null)
                    metrics = new GridCacheMetricsAdapter();

                metrics.onRead(false);

                if (isNew() || !valid()) {
                    this.primaryNodeId = primaryNodeId;

                    isRefreshing = false;

                    // Version does not change for load ops.
                    update(val, valBytes, expireTime, ttl, ver, metrics);

                    updateIndex(val);

                    return true;
                }

                return false;
            }
        }
        finally {
            if (evt)
                cctx.events().addEvent(partition(), key, tx, null, EVT_CACHE_OBJECT_READ, val, null);
        }
    }

    /** {@inheritDoc} */
    @Override protected void updateIndex(V val) throws GridException {
        // No-op: queries are disabled for near cache.
    }

    /** {@inheritDoc} */
    @Override protected void clearIndex() throws GridException {
        // No-op: queries are disabled for near cache.
    }

    /** {@inheritDoc} */
    @Override public boolean hasLockCandidate(long threadId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.remoteCandidate(cctx.nodeId(), threadId) != null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == threadId;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThreadUnsafe(long threadId) {
        synchronized (mux) {
            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == threadId;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(GridCacheVersion exclude) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c!= null && c.threadId() == Thread.currentThread().getId() &&
                (exclude == null || !c.version().equals(exclude));
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localOwner() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            return mvcc.remoteOwner();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheMvccCandidate<K>> localCandidates(GridCacheVersion[] exclude)
        throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return remoteMvccSnapshot(exclude);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocally(UUID lockId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.version().id().equals(lockId);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lockedByThread(long threadId, GridCacheVersion exclude)
        throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.threadId() == threadId && (exclude == null || !c.version().equals(exclude));
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> candidate(UUID nodeId, long threadId)
        throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            return mvcc.remoteCandidate(nodeId, threadId);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> localCandidate(long threadId) throws GridCacheEntryRemovedException {
        return candidate(cctx.nodeId(), threadId);
    }

    /** {@inheritDoc} */
    @Override public boolean lockedLocallyUnsafe(UUID lockId) {
        synchronized (mux) {
            GridCacheMvccCandidate<K> c = mvcc.remoteOwner();

            return c != null && c.version().id().equals(lockId);
        }
    }

    /**
     * @param baseVer Base version.
     * @param owned Owned versions.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public void orderOwned(GridCacheVersion baseVer, Collection<GridCacheVersion> owned)
        throws GridCacheEntryRemovedException {
        if (!F.isEmpty(owned)) {
            GridCacheMvccCandidate<K> prev;
            GridCacheMvccCandidate<K> owner;

            V val;

            synchronized (mux) {
                checkObsolete();

                prev = mvcc.anyOwner();

                boolean emptyBefore = mvcc.isEmpty();

                owner = mvcc.orderOwned(baseVer, owned);

                boolean emptyAfter = mvcc.isEmpty();

                if (prev != owner)
                    mux.notifyAll();

                checkCallbacks(emptyBefore, emptyAfter);

                val = this.val;
            }

            // This call must be made outside of synchronization.
            checkOwnerChanged(prev, owner, val);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> addLocal(long threadId, GridCacheVersion ver, long timeout,
        boolean reenter, boolean ec, boolean tx) throws GridCacheEntryRemovedException {
        try {
            GridCacheMvccCandidate<K> prev;
            GridCacheMvccCandidate<K> owner;
            GridCacheMvccCandidate<K> cand;

            V val;

            UUID locId = cctx.nodeId();

            synchronized (mux) {
                // Check removed locks prior to obsolete flag.
                checkRemoved(ver);

                checkObsolete();

                GridCacheMvccCandidate<K> c = mvcc.remoteCandidate(locId, threadId);

                if (c != null)
                    return reenter ? c.reenter() : null;

                prev = mvcc.anyOwner();

                boolean emptyBefore = mvcc.isEmpty();

                // Lock could not be acquired.
                if (timeout < 0 && !emptyBefore)
                    return null;

                // Local lock for near cache is a remote lock.
                mvcc.addRemote(this, locId, null, threadId, ver, timeout, ec, tx, true);

                owner = mvcc.anyOwner();

                boolean emptyAfter = mvcc.isEmpty();

                if (prev != owner)
                    mux.notifyAll();

                checkCallbacks(emptyBefore, emptyAfter);

                val = this.val;

                refreshRemotes();

                cand = mvcc.candidate(ver);
            }

            // This call must be outside of synchronization.
            checkOwnerChanged(prev, owner, val);

            return cand;
        }
        catch (GridDistributedLockCancelledException e) {
            e.printStackTrace();

            assert false : "Local lock for near transaction got cancelled: " + this;

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMvccCandidate<K> readyLock(GridCacheMvccCandidate<K> cand)
        throws GridCacheEntryRemovedException {

        // Essentially no-op as locks are acquired on primary nodes.
        synchronized (mux) {
            checkObsolete();

            return mvcc.anyOwner();
        }
    }

    /**
     * Unlocks local lock.
     *
     * @return Removed candidate, or <tt>null</tt> if thread still holds the lock.
     */
    @Override @Nullable public GridCacheMvccCandidate<K> removeLock() {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        UUID locId = cctx.nodeId();

        synchronized (mux) {
            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            GridCacheMvccCandidate<K> cand = mvcc.remoteCandidate(locId, Thread.currentThread().getId());

            if (cand != null && cand.nearLocal() && cand.owner() && cand.used()) {
                // If a reentry, then release reentry. Otherwise, remove lock.
                GridCacheMvccCandidate<K> reentry = cand.unenter();

                if (reentry != null) {
                    assert reentry.reentry();

                    return reentry;
                }

                mvcc.remove(cand.version());

                owner = mvcc.anyOwner();

                refreshRemotes();
            }
            else
                return null;

            boolean emptyAfter = mvcc.isEmpty();

            if (owner != prev)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        assert owner != prev;

        if (log.isDebugEnabled())
            log.debug("Released local candidate from entry [owner=" + owner + ", prev=" + prev +
                ", entry=" + this + ']');

        if (prev != null && owner != prev)
            checkThreadChain(prev);

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner != prev ? prev : null;
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntry<K, V> wrap(boolean prjAware) {
        GridCacheProjectionImpl<K, V> prjPerCall = cctx.projectionPerCall();

        if (prjPerCall != null && prjAware)
            return new GridPartitionedCacheEntryImpl<K, V>(prjPerCall, cctx, key, this);

        GridCacheEntryImpl<K, V> wrapper = this.wrapper;

        if (wrapper == null)
            this.wrapper = wrapper = new GridPartitionedCacheEntryImpl<K, V>(null, cctx, key, this);

        return wrapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearCacheEntry.class, this, "super", super.toString());
    }
}
