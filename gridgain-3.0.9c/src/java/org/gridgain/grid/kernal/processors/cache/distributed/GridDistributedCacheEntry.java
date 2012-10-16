// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Entry for distributed (replicated/partitioned) cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public class GridDistributedCacheEntry<K, V> extends GridCacheMapEntry<K, V> {
    /** Remote candidates snapshot. */
    private volatile List<GridCacheMvccCandidate<K>> rmts = Collections.emptyList();

    /** Partition. */
    private int part;

    /**
     * @param ctx Cache context.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    public GridDistributedCacheEntry(GridCacheContext<K, V> ctx, K key, int hash, V val,
        GridCacheMapEntry<K, V> next, long ttl) {
        super(ctx, key, hash, val, next, ttl);

        part = ctx.partition(key);
    }

    /** {@inheritDoc} */
    @Override public int partition() {
        return part;
    }

    /**
     *
     */
    protected void refreshRemotes() {
        rmts = mvcc.remoteCandidates();
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
        GridCacheMvccCandidate<K> cand;
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            cand = mvcc.addLocal(this, threadId, ver, timeout, reenter, ec, tx);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        // Don't link reentries.
        if (cand != null && !cand.reentry())
            // Link with other candidates in the same thread.
            cctx.mvcc().addNext(cand);

        checkOwnerChanged(prev, owner, val);

        return cand;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheMvccCandidate<K>> remoteMvccSnapshot(GridCacheVersion... exclude) {
        Collection<GridCacheMvccCandidate<K>> rmts = this.rmts;

        if (rmts.isEmpty() || F.isEmpty(exclude))
            return rmts;

        Collection<GridCacheMvccCandidate<K>> cands = new ArrayList<GridCacheMvccCandidate<K>>(rmts.size());

        for (GridCacheMvccCandidate<K> c : rmts) {
            assert !c.reentry();

            // Don't include reentries.
            if (!U.containsObjectArray(exclude, c.version()))
                cands.add(c);
        }

        return cands;
    }

    /**
     * Adds new lock candidate.
     *
     * @param nodeId Node ID.
     * @param otherNodeId Other node ID.
     * @param threadId Thread ID.
     * @param ver Lock version.
     * @param timeout Lock acquire timeout.
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @throws GridDistributedLockCancelledException If lock has been canceled.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     */
    public void addRemote(UUID nodeId, @Nullable UUID otherNodeId, long threadId, GridCacheVersion ver, long timeout,
        boolean ec, boolean tx) throws GridDistributedLockCancelledException, GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            // Check removed locks prior to obsolete flag.
            checkRemoved(ver);

            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            mvcc.addRemote(this, nodeId, otherNodeId, threadId, ver, timeout, ec, tx, false);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;

            refreshRemotes();
        }

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);
    }

    /**
     * Adds new lock candidate.
     *
     * @param cand Remote lock candidate.
     * @throws GridDistributedLockCancelledException If lock has been canceled.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     */
    public void addRemote(GridCacheMvccCandidate<K> cand) throws GridDistributedLockCancelledException,
        GridCacheEntryRemovedException {

        V val;

        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        synchronized (mux) {
            cand.parent(this);

            // Check removed locks prior to obsolete flag.
            checkRemoved(cand.version());

            checkObsolete();

            boolean emptyBefore = mvcc.isEmpty();

            prev = mvcc.anyOwner();

            mvcc.addRemote(cand);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;

            refreshRemotes();
        }

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);
    }

    /**
     * Removes all lock candidates for node.
     *
     * @param nodeId ID of node to remove locks from.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public void removeExplicitNodeLocks(UUID nodeId) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            owner = mvcc.removeExplicitNodeCandidates(nodeId);

            boolean emptyAfter = mvcc.isEmpty();

            if (owner != prev)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            refreshRemotes();
        }

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);
    }

    /**
     * Unlocks local lock.
     *
     * @return Removed candidate, or <tt>null</tt> if thread still holds the lock.
     */
    @Nullable public GridCacheMvccCandidate<K> removeLock() {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            owner = mvcc.releaseLocal();

            boolean emptyAfter = mvcc.isEmpty();

            if (owner != prev)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

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
    @Override public boolean removeLock(GridCacheVersion ver) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev = null;
        GridCacheMvccCandidate<K> owner = null;

        GridCacheMvccCandidate<K> doomed;

        V val;

        synchronized (mux) {
            addRemoved(ver);

            if (obsoleteVer != null && !obsoleteVer.equals(ver))
                checkObsolete();

            doomed = mvcc.candidate(ver);

            if (doomed != null) {
                prev = mvcc.anyOwner();

                boolean emptyBefore = mvcc.isEmpty();

                owner = mvcc.remove(doomed.version());

                boolean emptyAfter = mvcc.isEmpty();

                if (owner != prev)
                    mux.notifyAll();

                if (!doomed.local())
                    refreshRemotes();

                checkCallbacks(emptyBefore, emptyAfter);
            }

            val = this.val;
        }

        if (log.isDebugEnabled())
            log.debug("Removed lock candidate from entry [doomed=" + doomed + ", owner=" + owner + ", prev=" + prev +
                ", entry=" + this + ']');

        if (doomed != null)
            checkThreadChain(doomed);

        // This call must be outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return doomed != null;
    }

    /**
     *
     * @param ver Lock version.
     * @throws GridDistributedLockCancelledException If lock is cancelled.
     */
    protected void checkRemoved(GridCacheVersion ver) throws GridDistributedLockCancelledException {
        synchronized (mux) {
            if ((obsoleteVer != null && obsoleteVer.equals(ver)) || cctx.mvcc().isRemoved(ver))
                throw new GridDistributedLockCancelledException();
        }
    }

    /**
     * @param ver Lock version.
     */
    protected void addRemoved(GridCacheVersion ver) {
        synchronized (mux) {
            cctx.mvcc().addRemoved(ver);
        }
    }

    /**
     *
     * @param cand Candidate to acquire lock for.
     * @return Owner.
     * @throws GridCacheEntryRemovedException If entry is removed.
     */
    @Nullable public GridCacheMvccCandidate<K> readyLock(
        GridCacheMvccCandidate<K> cand) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            owner = mvcc.readyLocal(cand);

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        // This call must be made outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner;
    }

    /**
     *
     * @param ver Version of candidate to acquire lock for.
     * @return Owner.
     * @throws GridCacheEntryRemovedException If entry is removed.
     */
    @Nullable public GridCacheMvccCandidate<K> readyLock(GridCacheVersion ver)
        throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            owner = mvcc.readyLocal(ver);

            assert owner == null || owner.owner() : "Owner flag not set for owner: " + owner;

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        // This call must be made outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner;
    }

    /**
     * Reorders completed versions.
     *
     * @param baseVer Base version for reordering.
     * @param committedVers Completed versions.
     * @param rolledbackVers Rolled back versions.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public void orderCompleted(GridCacheVersion baseVer, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers)
        throws GridCacheEntryRemovedException {
        if (!F.isEmpty(committedVers) || !F.isEmpty(rolledbackVers)) {
            GridCacheMvccCandidate<K> prev;
            GridCacheMvccCandidate<K> owner;

            V val;

            synchronized (mux) {
                checkObsolete();

                prev = mvcc.anyOwner();

                boolean emptyBefore = mvcc.isEmpty();

                owner = mvcc.orderCompleted(baseVer, committedVers, rolledbackVers);

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

    /**
     *
     * @param lockVer Done version.
     * @param baseVer Base version.
     * @param committedVers Completed versions for reordering.
     * @param rolledbackVers Rolled back versions for reordering.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @return Owner.
     */
    @Nullable public GridCacheMvccCandidate<K> doneRemote(
        GridCacheVersion lockVer,
        GridCacheVersion baseVer,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) throws GridCacheEntryRemovedException {
        return doneRemote(lockVer, baseVer, Collections.<GridCacheVersion>emptySet(), committedVers, rolledbackVers);
    }

    /**
     *
     * @param lockVer Done version.
     * @param baseVer Base version.
     * @param pendingVers Pending versions that are less than lock version.
     * @param committedVers Completed versions for reordering.
     * @param rolledbackVers Rolled back versions for reordering.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @return Owner.
     */
    @Nullable public GridCacheMvccCandidate<K> doneRemote(
        GridCacheVersion lockVer,
        GridCacheVersion baseVer,
        Collection<GridCacheVersion> pendingVers,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) throws GridCacheEntryRemovedException {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            // Order completed versions.
            if (!F.isEmpty(committedVers) || !F.isEmpty(rolledbackVers)) {
                mvcc.orderCompleted(lockVer, committedVers, rolledbackVers);

                if (!baseVer.equals(lockVer))
                    mvcc.orderCompleted(baseVer, committedVers, rolledbackVers);
            }

            owner = mvcc.doneRemote(lockVer, pendingVers, committedVers, rolledbackVers);

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        // This call must be made outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner;
    }

    /**
     * Rechecks if lock should be reassigned.
     *
     * @return Current owner.
     */
    @Nullable public GridCacheMvccCandidate<K> recheck() {
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            owner = mvcc.recheck();

            boolean emptyAfter = mvcc.isEmpty();

            if (owner != prev)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = this.val;
        }

        // This call must be made outside of synchronization.
        checkOwnerChanged(prev, owner, val);

        return owner;
    }

    /** {@inheritDoc} */
    @Override public boolean tmLock(GridCacheTxEx<K, V> tx, long timeout)
        throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        if (tx.local())
            // Null is returned if timeout is negative and there is other lock owner.
            return addLocal(tx.threadId(), tx.xidVersion(), timeout, false, tx.ec(), true) != null;

        try {
            addRemote(tx.nodeId(), tx.otherNodeId(), tx.threadId(), tx.xidVersion(), tx.timeout(), tx.ec(), true);

            return true;
        }
        catch (GridDistributedLockCancelledException ignored) {
            if (log.isDebugEnabled())
                log.debug("Attempted to enter tx lock for cancelled ID (will ignore): " + tx);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public void txUnlock(GridCacheTxEx<K, V> tx) throws GridCacheEntryRemovedException {
        removeLock(tx.xidVersion());
    }

    /**
     * Adds remote candidates automatically filtering any local candidates.
     *
     * @param cands Candidates to add.
     * @param baseVer Base version for reordering.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     * @throws GridCacheEntryRemovedException If entry is removed.
     */
    public void addRemoteCandidates(
        Collection<GridCacheMvccCandidate<K>> cands,
        GridCacheVersion baseVer,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) throws GridCacheEntryRemovedException {
        if (cands != null && !cands.isEmpty())
            for (GridCacheMvccCandidate<K> cand : cands) {
                // Filter local nodes.
                if (!cand.nodeId().equals(cctx.discovery().localNode().id())) {
                    try {
                        addRemote(cand);
                    }
                    catch (GridDistributedLockCancelledException ignored) {
                        // Ignore.
                        if (log.isDebugEnabled())
                            log.debug("Will not sync up on candidate since lock was canceled: " + cand);
                    }
                }
            }

        orderCompleted(baseVer, committedVers, rolledbackVers);
    }

    /**
     * @param emptyBefore Empty flag before operation.
     * @param emptyAfter Empty flag after operation.
     */
    protected void checkCallbacks(boolean emptyBefore, boolean emptyAfter) {
        assert Thread.holdsLock(mux);

        if (emptyBefore != emptyAfter) {
            if (emptyBefore)
                cctx.mvcc().callback().onLocked(this);

            if (emptyAfter)
                cctx.mvcc().callback().onFreed(this);
        }
    }

    /**
     * @param prev Previous owner.
     * @param owner Current owner.
     * @param val Entry value.
     */
    protected void checkOwnerChanged(GridCacheMvccCandidate<K> prev, GridCacheMvccCandidate<K> owner, V val) {
        assert !Thread.holdsLock(mux);

        if (owner != prev) {
            cctx.mvcc().callback().onOwnerChanged(this, prev, owner);

            if (owner != null && owner.local())
                checkThreadChain(owner);

            if (prev != null)
                // Event notification.
                cctx.events().addEvent(partition(), key, prev.nodeId(), prev, EVT_CACHE_OBJECT_UNLOCKED, val, val);

            if (owner != null)
                // Event notification.
                cctx.events().addEvent(partition(), key, owner.nodeId(), owner, EVT_CACHE_OBJECT_LOCKED, val, val);
        }
    }

    /**
     * @param owner Starting candidate in the chain.
     */
    protected void checkThreadChain(GridCacheMvccCandidate<K> owner) {
        assert !Thread.holdsLock(mux);

        assert owner != null;
        assert owner.owner() || owner.used() : "Neither owner or used flags are set on ready local candidate: " +
            owner;

        if (owner.local() && owner.next() != null) {
            for (GridCacheMvccCandidate<K> cand = owner.next(); cand != null; cand = cand.next()) {
                assert cand.local() : "Remote candidate cannot be part of thread chain: " + cand;

                // Allow next lock in the thread to proceed.
                if (!cand.used()) {
                    GridDistributedCacheEntry<K, V> e =
                        (GridDistributedCacheEntry<K, V>)cctx.cache().peekEx(cand.key());

                    if (e != null)
                        e.recheck();

                    break;
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedCacheEntry.class, this, super.toString());
    }
}
