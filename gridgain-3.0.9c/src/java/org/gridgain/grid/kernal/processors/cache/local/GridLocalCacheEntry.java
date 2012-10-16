// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.local;

import org.gridgain.grid.kernal.processors.cache.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Cache entry for local caches.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public class GridLocalCacheEntry<K, V> extends GridCacheMapEntry<K, V> {

    /**
     * @param ctx  Cache registry.
     * @param key  Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl  Time to live.
     */
    public GridLocalCacheEntry(GridCacheContext<K, V> ctx, K key, int hash, V val,
        GridCacheMapEntry<K, V> next, long ttl) {
        super(ctx, key, hash, val, next, ttl);
    }

    /**
     * Add local candidate.
     *
     * @param threadId Owning thread ID.
     * @param ver Lock version.
     * @param timeout Timeout to acquire lock.
     * @param reenter Reentry flag.
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @return New candidate.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable public GridCacheMvccCandidate<K> addLocal(long threadId, GridCacheVersion ver, long timeout,
        boolean reenter, boolean ec, boolean tx) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> cand;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.localOwner();

            cand = mvcc.addLocal(this, threadId, ver, timeout, reenter, ec, tx);

            owner = mvcc.localOwner();

            val = this.val;
        }

        if (cand != null) {
            if (!cand.reentry()) {
                cctx.mvcc().addNext(cand);
            }

            // Event notification.
            cctx.events().addEvent(partition(), key, cand.nodeId(), cand, EVT_CACHE_OBJECT_LOCKED, val, val);
        }

        checkOwnerChanged(prev, owner);

        return cand;
    }

    /**
     *
     * @param cand Candidate.
     * @return Current owner.
     */
    @Nullable public GridCacheMvccCandidate<K> readyLocal(GridCacheMvccCandidate<K> cand) {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        synchronized (mux) {
            prev = mvcc.localOwner();

            owner = mvcc.readyLocal(cand);

            if (owner != prev) {
                mux.notifyAll();
            }
        }

        checkOwnerChanged(prev, owner);

        return owner;
    }

    /**
     *
     * @param ver Candidate version.
     * @return Current owner.
     */
    @Nullable public GridCacheMvccCandidate<K> readyLocal(GridCacheVersion ver) {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        synchronized (mux) {
            prev = mvcc.localOwner();

            owner = mvcc.readyLocal(ver);

            if (owner != prev) {
                mux.notifyAll();
            }
        }

        checkOwnerChanged(prev, owner);

        return owner;
    }

    /** {@inheritDoc} */
    @Override public boolean tmLock(GridCacheTxEx<K, V> tx, long timeout) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> cand = addLocal(tx.threadId(), tx.xidVersion(), timeout, /*reenter*/false, tx.ec(),
            /*tx*/true);

        if (cand != null) {
            readyLocal(cand);

            return true;
        }

        return false;
    }

    /**
     * Rechecks if lock should be reassigned.
     *
     * @return Current owner.
     */
    @Nullable public GridCacheMvccCandidate<K> recheck() {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        synchronized (mux) {
            prev = mvcc.localOwner();

            owner = mvcc.recheck();

            if (owner != prev) {
                mux.notifyAll();
            }
        }

        checkOwnerChanged(prev, owner);

        return owner;
    }

    /**
     * @param prev Previous owner.
     * @param owner Current owner.
     */
    private void checkOwnerChanged(GridCacheMvccCandidate<K> prev, GridCacheMvccCandidate<K> owner) {
        assert !Thread.holdsLock(mux);

        if (owner != prev) {
            cctx.mvcc().callback().onOwnerChanged(this, prev, owner);

            if (owner != null) {
                checkThreadChain(owner);
            }
        }
    }

    /**
     * @param owner Starting candidate in the chain.
     */
    private void checkThreadChain(GridCacheMvccCandidate<K> owner) {
        assert !Thread.holdsLock(mux);

        assert owner != null;
        assert owner.owner() || owner.used() : "Neither owner or used flags are set on ready local candidate: " +
            owner;

        if (owner.next() != null) {
            for (GridCacheMvccCandidate<K> cand = owner.next(); cand != null; cand = cand.next()) {
                assert cand.local();

                // Allow next lock in the thread to proceed.
                if (!cand.used()) {
                    GridLocalCacheEntry<K, V> e =
                        (GridLocalCacheEntry<K, V>)cctx.cache().peekEx(cand.key());

                    // At this point candidate may have been removed and entry destroyed,
                    // so we check for null.
                    if (e != null) {
                        e.recheck();
                    }

                    break;
                }
            }
        }
    }

    /**
     * Unlocks lock if it is currently owned.
     *
     * @param lockId Lock ID to unlock.
     */
    public void unlock(UUID lockId) {
        GridCacheMvccCandidate<K> prev;

        GridCacheMvccCandidate<K> owner = null;

        V val;

        synchronized (mux) {
            prev = mvcc.localOwner();

            if (prev != null && prev.id().equals(lockId)) {
                owner = mvcc.releaseLocal();

                if (owner != prev) {
                    mux.notifyAll();
                }
            }

            val = this.val;
        }

        if (prev != null && prev.id().equals(lockId)) {
            checkThreadChain(prev);

            // Event notification.
            cctx.events().addEvent(partition(), key, prev.nodeId(), prev, EVT_CACHE_OBJECT_UNLOCKED, val, val);
        }

        checkOwnerChanged(prev, owner);
    }

    /**
     * Unlocks lock if it is currently owned.
     *
     * @param tx Transaction to unlock.
     */
    @Override public void txUnlock(GridCacheTxEx<K, V> tx) throws GridCacheEntryRemovedException {
        removeLock(tx.xidVersion());
    }

    /**
     * Releases local lock.
     */
    void releaseLocal() {
        releaseLocal(Thread.currentThread().getId());
    }

    /**
     * Releases local lock.
     *
     * @param threadId Thread ID.
     */
    void releaseLocal(long threadId) {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            prev = mvcc.localOwner();

            owner = mvcc.releaseLocal(threadId);

            if (owner != prev) {
                mux.notifyAll();
            }

            val = this.val;
        }

        if (prev != null && owner != prev) {
            checkThreadChain(prev);

            // Event notification.
            cctx.events().addEvent(partition(), key, prev.nodeId(), prev, EVT_CACHE_OBJECT_UNLOCKED, val, val);
        }

        checkOwnerChanged(prev, owner);
    }

    /**
     * Removes candidate regardless if it is owner or not.
     *
     * @param cand Candidate to remove.
     * @throws GridCacheEntryRemovedException If the entry was removed by version other
     *      than one passed in.
     */
    void removeLock(GridCacheMvccCandidate<K> cand) throws GridCacheEntryRemovedException {
        removeLock(cand.version());
    }

    /** {@inheritDoc} */
    @Override public boolean removeLock(GridCacheVersion ver) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev = null;
        GridCacheMvccCandidate<K> owner = null;

        GridCacheMvccCandidate<K> doomed;

        V val;

        synchronized (mux) {
            if (obsoleteVer != null && !obsoleteVer.equals(ver)) {
                checkObsolete();
            }

            doomed = mvcc.candidate(ver);

            if (doomed != null) {
                prev = mvcc.localOwner();

                owner = mvcc.remove(ver);

                if (owner != prev) {
                    mux.notifyAll();
                }
            }

            val = this.val;
        }

        if (doomed != null) {
            checkThreadChain(doomed);

            // Event notification.
            cctx.events().addEvent(partition(), key, doomed.nodeId(), doomed, EVT_CACHE_OBJECT_UNLOCKED, val, val);
        }

        checkOwnerChanged(prev, owner);

        return doomed != null;
    }
}
