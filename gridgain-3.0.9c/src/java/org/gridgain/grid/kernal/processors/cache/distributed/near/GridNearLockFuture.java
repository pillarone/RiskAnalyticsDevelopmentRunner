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
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Cache lock future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearLockFuture<K, V> extends GridCompoundIdentityFuture<Boolean>
    implements GridCacheMvccLockFuture<K, V, Boolean> {
    /** Cache registry. */
    @GridToStringExclude
    private GridCacheContext<K, V> cctx;

    /** Lock owner thread. */
    @GridToStringInclude
    private long threadId;

    /** Keys to lock. */
    private Collection<? extends K> keys;

    /** Keys locked so far. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private List<GridDistributedCacheEntry<K, V>> entries;

    /** Future ID. */
    private GridUuid futId;

    /** Lock version. */
    private GridCacheVersion lockVer;

    /** Read flag. */
    private boolean read;

    /** Flag to return value. */
    private boolean retval;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Timed out flag. */
    private volatile boolean timedOut;

    /** Timeout object. */
    @GridToStringExclude
    private LockTimeoutObject timeoutObj;

    /** Lock timeout. */
    private long timeout;

    /** Logger. */
    @GridToStringExclude
    private GridLogger log;

    /** Filter. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

    /** Transaction. */
    @GridToStringExclude
    private GridNearTxLocal<K, V> tx;

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearLockFuture() {
        // No-op.
    }

    /**
     * @param cctx Registry.
     * @param keys Keys to lock.
     * @param tx Transaction.
     * @param read Read flag.
     * @param retval Flag to return value or not.
     * @param timeout Lock acquisition timeout.
     * @param filter Filter.
     */
    public GridNearLockFuture(
        GridCacheContext<K, V> cctx,
        Collection<? extends K> keys,
        @Nullable GridNearTxLocal<K, V> tx,
        boolean read,
        boolean retval,
        long timeout,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        super(cctx.kernalContext(), CU.boolReducer());
        assert cctx != null;
        assert keys != null;

        this.cctx = cctx;
        this.keys = keys;
        this.tx = tx;
        this.read = read;
        this.retval = retval;
        this.timeout = timeout;
        this.filter = filter;

        threadId = tx == null ? Thread.currentThread().getId() : tx.threadId();

        lockVer = tx != null ? tx.xidVersion() : cctx.versions().next();

        futId = GridUuid.randomUuid();

        entries = new ArrayList<GridDistributedCacheEntry<K, V>>(keys.size());

        log = cctx.logger(getClass());

        if (timeout > 0) {
            timeoutObj = new LockTimeoutObject();

            cctx.time().addTimeoutObject(timeoutObj);
        }
    }

    /** {@inheritDoc} */
    @Override public UUID nodeId() {
        return ctx.localNodeId();
    }

    /**
     * @return Participating nodes.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return
            F.viewReadOnly(futures(), new GridClosure<GridFuture<?>, GridRichNode>() {
                @Nullable @Override public GridRichNode apply(GridFuture<?> f) {
                    if (isMini(f))
                        return ((MiniFuture)f).node();

                    return cctx.rich().rich(cctx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return lockVer;
    }

    /**
     * @return Entries.
     */
    public List<GridDistributedCacheEntry<K, V>> entriesCopy() {
        synchronized (mux) {
            return new ArrayList<GridDistributedCacheEntry<K, V>>(entries);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<? extends K> keys() {
        return keys;
    }

    /**
     * @return Future ID.
     */
    @Override public GridUuid futureId() {
        return futId;
    }

    /**
     * @return {@code True} if transaction is not {@code null}.
     */
    private boolean inTx() {
        return tx != null;
    }

    /**
     * @return {@code True} if transaction is not {@code null} and in EC mode.
     */
    private boolean ec() {
        return tx != null && tx.ec();
    }

    /**
     * @return {@code True} if transaction is not {@code null} and has invalidate flag set.
     */
    private boolean isInvalidate() {
        return tx != null && tx.isInvalidate();
    }

    /**
     * @return {@code True} if commit is synchronous.
     */
    private boolean syncCommit() {
        return tx != null && tx.syncCommit();
    }

    /**
     * @return {@code True} if rollback is synchronous.
     */
    private boolean syncRollback() {
        return tx != null && tx.syncRollback();
    }

    /**
     * @return Transaction isolation or {@code null} if no transaction.
     */
    @Nullable private GridCacheTxIsolation isolation() {
        return tx == null ? null : tx.isolation();
    }

    /**
     * @return {@code true} if related transaction is implicit.
     */
    private boolean implicitTx() {
        return tx != null && tx.implicit();
    }

    /**
     * @param cached Entry.
     * @return {@code True} if locked.
     * @throws GridCacheEntryRemovedException If removed.
     */
    private boolean locked(GridCacheEntryEx<K, V> cached) throws GridCacheEntryRemovedException {
        // Reentry-aware check.
        return (cached.lockedLocally(lockVer.id()) || cached.lockedByThread(threadId)) &&
            filter(cached); // If filter failed, lock is failed.
    }

    /**
     * @param cached Entry.
     * @param owner Lock owner.
     * @return {@code True} if locked.
     */
    private boolean locked(GridCacheEntryEx<K, V> cached, GridCacheMvccCandidate<K> owner) {
        // Reentry-aware check (if filter failed, lock is failed).
        return owner != null && owner.matches(lockVer, cctx.nodeId(), threadId) && filter(cached);
    }

    /**
     * Adds entry to future.
     *
     * @param entry Entry to add.
     * @return Lock candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable private GridCacheMvccCandidate<K> addEntry(GridDistributedCacheEntry<K, V> entry)
        throws GridCacheEntryRemovedException {
        // Check if lock acquisition is timed out.
        if (timedOut)
            return null;

        // Add local lock first, as it may throw GridCacheEntryRemovedException.
        GridCacheMvccCandidate<K> c = entry.addLocal(threadId, lockVer, timeout, !inTx(), ec(), inTx());

        synchronized (mux) {
            entries.add(entry);
        }

        if (c == null && timeout < 0) {
            if (log.isDebugEnabled())
                log.debug("Failed to acquire lock with negative timeout: " + entry);

            onFailed(false);

            return null;
        }

        // Double check if lock acquisition has already timed out.
        if (timedOut) {
            entry.removeLock(lockVer);

            return null;
        }

        return c;
    }

    /**
     * Undoes all locks.
     *
     * @param dist If {@code true}, then remove locks from remote nodes as well.
     */
    private void undoLocks(boolean dist) {
        // Transactions will undo during rollback.
        if (dist && tx == null)
            cctx.near().removeLocks(lockVer, keys);
        else {
            if (tx != null) {
                if (tx.setRollbackOnly()) {
                    if (log.isDebugEnabled())
                        log.debug("Marked transaction as rollback only because locks could not be acquired: " + tx);
                }
                else if (log.isDebugEnabled())
                    log.debug("Transaction was not marked rollback-only while locks were not acquired: " + tx);
            }

            for (GridCacheEntryEx<K, V> e : entriesCopy()) {
                try {
                    e.removeLock(lockVer);
                }
                catch (GridCacheEntryRemovedException ignored) {
                    while (true) {
                        try {
                            e = cctx.cache().peekEx(e.key());

                            if (e != null)
                                e.removeLock(lockVer);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Attempted to remove lock on removed entry (will retry) [ver=" +
                                    lockVer + ", entry=" + e + ']');
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * @param dist {@code True} if need to distribute lock release.
     */
    private void onFailed(boolean dist) {
        undoLocks(dist);

        complete(false);
    }

    /**
     * @param success Success flag.
     */
    public void complete(boolean success) {
        onComplete(success, true);
    }

    /**
     * @param nodeId Left node ID
     * @return {@code True} if node was in the list.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    f.onResult(new GridTopologyException("Remote node left grid (will retry): " + nodeId));

                    return true;
                }
            }

        return false;
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridNearLockResponse<K, V> res) {
        if (!isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received lock response from node [nodeId=" + nodeId + ", res=" + res + ", fut=" + this + ']');

            for (GridFuture<Boolean> fut : pending()) {
                if (isMini(fut)) {
                    MiniFuture mini = (MiniFuture)fut;

                    if (mini.futureId().equals(res.miniId())) {
                        assert mini.node().id().equals(nodeId);

                        if (log.isDebugEnabled())
                            log.debug("Found mini future for response [mini=" + mini + ", res=" + res + ']');

                        mini.onResult(res);

                        if (log.isDebugEnabled())
                            log.debug("Future after processed lock response [fut=" + this + ", mini=" + mini +
                                ", res=" + res + ']');

                        return;
                    }
                }
            }

            U.warn(log, "Failed to find mini future for response (perhaps due to stale message) [res=" + res +
                ", fut=" + this + ']');
        }
        else if (log.isDebugEnabled())
            log.debug("Ignoring lock response from node (future is done) [nodeId=" + nodeId + ", res=" + res +
                ", fut=" + this + ']');
    }

    /**
     * @param t Error.
     */
    public void onError(Throwable t) {
        if (err.compareAndSet(null, t instanceof GridCacheLockTimeoutException ? null : t))
            onComplete(false, true);
    }

    /**
     * @param cached Entry to check.
     * @return {@code True} if filter passed.
     */
    private boolean filter(GridCacheEntryEx<K, V> cached) {
        try {
            if (!cctx.isAll(cached, filter)) {
                if (log.isDebugEnabled())
                    log.debug("Filter didn't pass for entry (will fail lock): " + cached);

                onFailed(true);

                return false;
            }

            return true;
        }
        catch (GridException e) {
            onError(e);

            return false;
        }
    }

    /**
     * Callback for whenever entry lock ownership changes.
     *
     * @param entry Entry whose lock ownership changed.
     */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        if (owner != null && owner.version().equals(lockVer)) {
            onDone(true);

            return true;
        }

        return false;
    }

    /**
     * @return {@code True} if locks have been acquired.
     */
    private boolean checkLocks() {
        if (!isDone() && !hasPending()) {
            for (int i = 0; i < entries.size(); i++) {
                while (true) {
                    GridCacheEntryEx<K, V> cached = entries.get(i);

                    try {
                        if (!locked(cached)) {
                            if (log.isDebugEnabled())
                                log.debug("Lock is still not acquired for entry (will keep waiting) [entry=" +
                                    cached + ", fut=" + this + ']');

                            return false;
                        }

                        break;
                    }
                    // Possible in concurrent cases, when owner is changed after locks
                    // have been released or cancelled.
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in onOwnerChanged method (will retry): " + cached);

                        // Replace old entry with new one.
                        entries.set(i, (GridDistributedCacheEntry<K, V>)cctx.cache().entryEx(cached.key()));
                    }
                }
            }

            if (log.isDebugEnabled())
                log.debug("Local lock acquired for entries [fut=" + this + ", entries=" + entries + "]");

            onComplete(true, true);

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() {
        if (onCancelled())
            onComplete(false, true);

        return isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Boolean success, Throwable err) {
        if (log.isDebugEnabled())
            log.debug("Received onDone(..) callback [success=" + success + ", err=" + err + ", fut=" + this + ']');

        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && success && !ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err instanceof GridCacheLockTimeoutException ? null : err);

        if (err != null)
            success = false;

        return onComplete(success, true);
    }

    /**
     * Completeness callback.
     *
     * @param success {@code True} if lock was acquired.
     * @param distribute {@code True} if need to distribute lock removal in case of failure.
     * @return {@code True} if complete by this operation.
     */
    private boolean onComplete(boolean success, boolean distribute) {
        if (log.isDebugEnabled())
            log.debug("Received onComplete(..) callback [success=" + success + ", distribute=" + distribute +
                ", fut=" + this + ']');

        if (!success)
            undoLocks(distribute);

        if (tx != null)
            cctx.tm().txContext(tx);

        if (super.onDone(success, err.get())) {
            if (log.isDebugEnabled())
                log.debug("Completing future: " + this);

            // Clean up.
            cctx.mvcc().removeFuture(this);

            if (timeoutObj != null)
                cctx.time().removeTimeoutObject(timeoutObj);

            // TODO: Check if we still need this.
            wakeUpEntries();

            return true;
        }

        return false;
    }

    /**
     * Wake up all entries.
     */
    private void wakeUpEntries() {
        for (GridDistributedCacheEntry<K, V> entry : entriesCopy()) {
            try {
                entry.wakeUp();
            }
            catch (GridCacheEntryRemovedException ignore) {
                // At this point locks could have been removed, so we must ignore.
                if (log.isDebugEnabled())
                    log.debug("Ignoring 'wakeUp' call on removed entry: " + entry);
            }
        }
    }

    /**
     * Checks for errors.
     *
     * @throws GridException If execution failed.
     */
    private void checkError() throws GridException {
        if (err.get() != null)
            throw U.cast(err.get());
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return futId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearLockFuture.class, this, "inTx", inTx(), "super", super.toString());
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     *
     */
    void map() {
        map(keys, Collections.<GridRichNode, Collection<K>>emptyMap());

        markInitialized();
    }

    /**
     * @param keys Keys.
     * @param mapped Mappings to check for duplicates.
     */
    private void map(Iterable<? extends K> keys, Map<GridRichNode, Collection<K>> mapped) {
        Collection<GridRichNode> nodes = CU.allNodes(cctx);

        ConcurrentMap<GridRichNode, Collection<K>> mappings =
            new ConcurrentHashMap<GridRichNode, Collection<K>>(nodes.size());

        // Assign keys to primary nodes.
        for (K key : keys) {
            if (tx != null) {
                UUID nodeId = tx.mapping(key);

                // Do not remap if mapped already.
                if (nodeId != null) {
                    GridRichNode n = ctx.discovery().richNode(nodeId);

                    if (n != null) {
                        CU.getOrSet(mappings, n).add(key);

                        continue; // For loop.
                    }
                }
            }

            map(key, mappings, nodes, mapped);
        }

        if (isDone())
            return;

        if (tx != null)
            tx.addMapping(mappings);

        Collection<K> retries = new LinkedList<K>();

        // Create mini futures.
        for (Map.Entry<GridRichNode, Collection<K>> e : mappings.entrySet()) {
            final GridRichNode node = e.getKey();

            final Collection<K> mappedKeys = e.getValue();

            assert !mappedKeys.isEmpty();

            try {
                GridNearLockRequest<K, V> req = null;

                boolean distribute = false;

                boolean explicit = false;

                for (K key : mappedKeys) {
                    while (true) {
                        GridNearCacheEntry<K, V> entry = null;

                        try {
                            entry = cctx.near().entryExx(key);

                            if (!cctx.isAll(entry.wrap(false), filter)) {
                                if (log.isDebugEnabled())
                                    log.debug("Entry being locked did not pass filter (will not lock): " + entry);

                                onComplete(false, false);

                                return;
                            }

                            // Removed exception may be thrown here.
                            GridCacheMvccCandidate<K> cand = addEntry(entry);

                            if (cand != null) {
                                if (req == null)
                                    req = new GridNearLockRequest<K, V>(cctx.nodeId(), threadId, futId, lockVer,
                                        inTx(), implicitTx(), read, isolation(), isInvalidate(), timeout,
                                        syncCommit(), syncRollback(), mappedKeys.size());

                                req.addKeyBytes(
                                    key,
                                    cand.reentry() ? null : entry.getOrMarshalKeyBytes(),
                                    retval && entry.isNew(),
                                    Collections.<GridCacheMvccCandidate<K>>emptyList(),
                                    entry.dhtVersion(), // Include DHT version to match remote DHT entry.
                                    cctx);

                                distribute = !cand.reentry();

                                if (cand.reentry())
                                    explicit = tx != null && !entry.hasLockCandidate(tx.xidVersion());
                            }
                            else
                                // Ignore reentries within transactions.
                                explicit = tx != null && !entry.hasLockCandidate(tx.xidVersion());

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignored) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in lockAsync(..) method (will retry): " + entry);
                        }
                    }
                }

                // Mark mapping explicit lock flag.
                if (explicit) {
                    boolean marked = tx != null && tx.markExplicit(node.id());

                    assert tx == null || marked;
                }

                // If this is the primary node for the keys.
                if (distribute) {
                    if (filter != null && filter.length != 0)
                        req.filter(filter, cctx);

                    if (node.isLocal()) {
                        req.miniId(GridUuid.randomUuid());

                        GridDhtFuture<K, GridNearLockResponse<K, V>> fut = dht().lockAllAsync(cctx.localNode(), req,
                            mappedKeys, filter);

                        retries.addAll(fut.retries());

                        // Remap.
                        if (!retries.isEmpty())
                            map(retries, mappings);

                        // Add new future.
                        add(new GridEmbeddedFuture<Boolean, GridNearLockResponse<K, V>>(
                            cctx.kernalContext(),
                            fut,
                            new C2<GridNearLockResponse<K, V>, Exception, Boolean>() {
                                @Override public Boolean apply(GridNearLockResponse<K, V> res, Exception e) {
                                    if (CU.isLockTimeout(e) || (res != null && CU.isLockTimeout(res.error())))
                                        return false;

                                    if (e != null) {
                                        onError(e);

                                        return false;
                                    }

                                    if (res == null) {
                                        onError(new GridException("Lock response is null for future: " + this));

                                        return false;
                                    }

                                    if (res.error() != null) {
                                        onError(res.error());

                                        return false;
                                    }

                                    try {
                                        int i = 0;

                                        for (K k : mappedKeys) {
                                            while (true) {
                                                GridNearCacheEntry<K, V> entry = cctx.near().entryExx(k);

                                                try {
                                                    // Lock is held at this point, so we can set the
                                                    // returned value if any.
                                                    entry.resetFromPrimary(res.value(i), res.valueBytes(i),
                                                        lockVer, res.dhtVersion(i), node.id());

                                                    entry.doneRemote(lockVer, tx == null ? lockVer : tx.minVersion(),
                                                        res.pending(), res.committedVersions(),
                                                        res.rolledbackVersions());

                                                    if (ec())
                                                        entry.recheck();

                                                    if (log.isDebugEnabled())
                                                        log.debug("Processed response for entry [res=" + res +
                                                            ", entry=" + entry + ']');

                                                    break; // Inner while loop.
                                                }
                                                catch (GridCacheEntryRemovedException ignored) {
                                                    if (log.isDebugEnabled())
                                                        log.debug("Failed to add candidates because entry was " +
                                                            "removed (will renew).");

                                                    // Replace old entry with new one.
                                                    entries.set(i, (GridDistributedCacheEntry<K, V>)
                                                        cctx.cache().entryEx(entry.key()));
                                                }
                                            }

                                            i++; // Increment outside of while loop.
                                        }
                                    }
                                    catch (GridException ex) {
                                        onError(ex);
                                    }

                                    return true;
                                }
                            }
                        ));
                    }
                    else {
                        MiniFuture fut = new MiniFuture(node, mappedKeys);

                        req.miniId(fut.futureId());

                        add(fut); // Append new future.

                        try {
                            cctx.io().send(node, req);
                        }
                        catch (GridTopologyException ex) {
                            assert fut != null;

                            fut.onResult(ex);
                        }
                    }
                }
            }
            catch (GridException ex) {
                onError(ex);
            }
        }
    }

    /**
     * @param mappings Mappings.
     * @param key Key to map.
     * @param nodes Nodes.
     * @param mapped Previously mapped.
     */
    private void map(K key, ConcurrentMap<GridRichNode, Collection<K>> mappings, Collection<GridRichNode> nodes,
        Map<GridRichNode, Collection<K>> mapped) {
        GridRichNode primary = CU.primary0(cctx.affinity(key, nodes));

        Collection<K> keys = mapped.get(primary);

        if (keys != null && keys.contains(key)) {
            onDone(new GridException("Failed to remap key to a new node " +
                "(key got remapped to the same node) [key=" + key + ", node=" +
                U.toShortString(primary) + ']'));

            return;
        }

        CU.getOrSet(mappings, primary).add(key);
    }

    /**
     * @return DHT cache.
     */
    private GridDhtCache<K, V> dht() {
        return cctx.near().dht();
    }

    /**
     * Lock request timeout object.
     */
    private class LockTimeoutObject implements GridTimeoutObject {
        /** End time. */
        private final long endTime = System.currentTimeMillis() + timeout;

        /** {@inheritDoc} */
        @Override public UUID timeoutId() {
            return lockVer.id();
        }

        /** {@inheritDoc} */
        @Override public long endTime() {
            // Account for overflow.
            return endTime < 0 ? Long.MAX_VALUE : endTime;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
        @Override public void onTimeout() {
            if (log.isDebugEnabled())
                log.debug("Timed out waiting for lock response: " + this);

            timedOut = true;

            onComplete(false, true);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(LockTimeoutObject.class, this);
        }
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<Boolean> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Node ID. */
        @GridToStringExclude
        private GridRichNode node;

        /** Keys. */
        @GridToStringInclude
        private Collection<K> keys;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param node Node.
         * @param keys Keys.
         */
        MiniFuture(GridRichNode node, Collection<K> keys) {
            super(cctx.kernalContext());

            this.node = node;
            this.keys = keys;
        }

        /**
         * @return Future ID.
         */
        GridUuid futureId() {
            return futId;
        }

        /**
         * @return Node ID.
         */
        public GridRichNode node() {
            return node;
        }

        /**
         * @return Keys.
         */
        public Collection<K> keys() {
            return keys;
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (log.isDebugEnabled())
                log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

            // Fail.
            onDone(e);
        }

        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will retry): " + this);

            // Remap.
            map(keys, F.t(node, keys));

            onDone(true);
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearLockResponse<K, V> res) {
            if (res.error() != null) {
                if (log.isDebugEnabled())
                    log.debug("Finishing mini future with an error due to error in response [miniFut=" + this +
                        ", res=" + res + ']');

                // Fail.
                if (res.error() instanceof GridCacheLockTimeoutException)
                    onDone(false);
                else
                    onDone(res.error());

                return;
            }

            Collection<K> retries = res.retries();

            if (!F.isEmpty(retries)) {
                if (log.isDebugEnabled())
                    log.debug("Remapping mini get future [leftOvers=" + retries + ", fut=" + this + ']');

                // This will append new futures to compound list.
                map(retries, F.t(node, keys));
            }

            int i = 0;

            for (K k : keys) {
                while (true) {
                    GridNearCacheEntry<K, V> entry = cctx.near().entryExx(k);

                    try {
                        // Lock is held at this point, so we can set the
                        // returned value if any.
                        entry.resetFromPrimary(res.value(i), res.valueBytes(i), lockVer, res.dhtVersion(i), node.id());

                        entry.doneRemote(lockVer, tx == null ? lockVer : tx.minVersion(), res.pending(),
                            res.committedVersions(), res.rolledbackVersions());

                        if (ec())
                            entry.recheck();

                        if (log.isDebugEnabled())
                            log.debug("Processed response for entry [res=" + res + ", entry=" + entry + ']');

                        break; // Inner while loop.
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to add candidates because entry was removed (will renew).");

                        // Replace old entry with new one.
                        entries.set(i, (GridDistributedCacheEntry<K, V>)cctx.cache().entryEx(entry.key()));
                    }
                    catch (GridException e) {
                        onError(e);

                        return;
                    }
                }

                i++;
            }

            onDone(true);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "node", node.id(), "super", super.toString());
        }
    }
}
