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
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.GridCacheTxState.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction adapter for cache transactions.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheTxLocalAdapter<K, V> extends GridCacheTxAdapter<K, V>
    implements GridCacheTxLocalEx<K, V> {
    /** Generation for op IDs. */
    private static final AtomicInteger IDGEN = new AtomicInteger();

    /** Per-transaction read map. */
    @GridToStringInclude
    protected Map<K, GridCacheTxEntry<K, V>> txMap;

    /** Read view on transaction map. */
    @GridToStringExclude
    protected GridCacheTxMap<K, V> readView;

    /** Write view on transaction map. */
    @GridToStringExclude
    protected GridCacheTxMap<K, V> writeView;

    /** Minimal version encountered (either explicit lock or XID of this transaction). */
    protected GridCacheVersion minVer;

    /** Flag indicating with TM commit happened. */
    protected AtomicBoolean doneFlag = new AtomicBoolean(false);

    /** Committed versions, relative to base. */
    protected Collection<GridCacheVersion> committedVers = Collections.emptyList();

    /** Rolled back versions, relative to base. */
    protected Collection<GridCacheVersion> rolledbackVers = Collections.emptyList();

    /** Base for completed versions. */
    protected GridCacheVersion completedBase;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    protected GridCacheTxLocalAdapter() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     * @param xidVer Transaction ID.
     * @param implicit {@code True} if transaction was implicitly started by the system,
     *      {@code false} if it was started explicitly by user.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param swapEnabled Whether to use swap storage.
     * @param storeEnabled Whether to use read/write through.
     */
    protected GridCacheTxLocalAdapter(
        GridCacheContext<K, V> ctx,
        GridCacheVersion xidVer,
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean swapEnabled,
        boolean storeEnabled) {
        super(ctx, xidVer, implicit, true, concurrency, isolation, timeout, invalidate, swapEnabled, storeEnabled);

        minVer = xidVer;
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        assert false;
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isStarted() {
        return txMap != null;
    }

    /** {@inheritDoc} */
    @Override public boolean hasReadKey(K key) {
        return readView.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public boolean hasWriteKey(K key) {
        return writeView.containsKey(key);
    }

    /**
     * @return Transaction read set.
     */
    @Override public Set<K> readSet() {
        return txMap == null ? Collections.<K>emptySet() : readView.keySet();
    }

    /**
     * @return Transaction write set.
     */
    @Override public Set<K> writeSet() {
        return txMap == null ? Collections.<K>emptySet() : writeView.keySet();
    }

    /** {@inheritDoc} */
    @Override public boolean removed(K key) {
        if (txMap == null)
            return false;

        GridCacheTxEntry<K, V> e = txMap.get(key);

        return e != null && e.op() == DELETE;
    }

    /**
     * @return Read map.
     */
    public Map<K, GridCacheTxEntry<K, V>> readMap() {
        return readView == null ? Collections.<K, GridCacheTxEntry<K, V>>emptyMap() : readView;
    }

    /**
     * @return Write map.
     */
    public Map<K, GridCacheTxEntry<K, V>> writeMap() {
        return writeView == null ? Collections.<K, GridCacheTxEntry<K, V>>emptyMap() : writeView;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> allEntries() {
        return txMap == null ? Collections.<GridCacheTxEntry<K, V>>emptySet() : txMap.values();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> readEntries() {
        return readView == null ? Collections.<GridCacheTxEntry<K, V>>emptyList() : readView.values();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> writeEntries() {
        return writeView == null ? Collections.<GridCacheTxEntry<K, V>>emptyList() : writeView.values();
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridCacheTxEntry<K, V> entry(K key) {
        return txMap == null ? null : txMap.get(key);
    }

    /**
     * @param key Key.
     * @param opId Operation ID.
     * @return Entry for given key and operation ID.
     */
    @Nullable private GridCacheTxEntry<K, V> entry(K key, int opId) {
        GridCacheTxEntry<K, V> txEntry = entry(key);

        if (txEntry == null)
            return null;

        while (txEntry.opId() != opId)
            txEntry = txEntry.child();

        return txEntry.opId() == opId ? txEntry : null;
    }

    /** {@inheritDoc} */
    @Override public void seal() {
        if (readView != null)
            readView.seal();

        if (writeView != null)
            writeView.seal();
    }

    /**
     * @return New operation ID.
     */
    protected int newOpId() {
        return IDGEN.incrementAndGet();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable @Override public V peek(boolean failFast, K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheFilterFailedException {
        GridCacheTxEntry<K, V> e = txMap == null ? null : txMap.get(key);

        if (e != null && e.marked()) {
            if (!F.isAll(e.cached().wrap(false), filter))
                return CU.<V>failed(failFast, e.value());

            return e.value();
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> loadMissing(boolean async, final Collection<? extends K> keys,
        final GridInClosure2<K, V> closure) {
        if (!async) {
            try {
                return new GridFinishedFuture<Boolean>(ctx.kernalContext(),
                    CU.loadAllFromStore(ctx, log, this, keys, closure));
            }
            catch (GridException e) {
                return new GridFinishedFuture<Boolean>(ctx.kernalContext(), e);
            }
        }
        else {
            try {
                return ctx.closures().callLocal(new GPC<Boolean>() {
                        @Override public Boolean call() throws Exception {
                            return CU.loadAllFromStore(ctx, log, GridCacheTxLocalAdapter.this, keys, closure);
                        }
                    }, true);
            }
            catch (GridException e) {
                return new GridFinishedFuture<Boolean>(ctx.kernalContext(), e);
            }
        }
    }

    /**
     * Gets minimum version present in transaction.
     *
     * @return Minimum versions.
     */
    @Override public GridCacheVersion minVersion() {
        return minVer;
    }

    /**
     * @throws GridException If prepare step failed.
     */
    @SuppressWarnings({"CatchGenericClass"})
    protected void userPrepare() throws GridException {
        if (state() != PREPARING) {
            if (timedOut())
                throw new GridCacheTxTimeoutException("Transaction timed out: " + this);

            setRollbackOnly();

            throw new GridException("Invalid transaction state for prepare [state=" + state() + ", tx=" + this + ']');
        }

        checkValid(CU.<K, V>empty());

        try {
            ctx.tm().prepareTx(this);
        }
        catch (GridException e) {
            throw e;
        }
        catch (Throwable e) {
            setRollbackOnly();

            throw new GridException("Transaction validation produced a runtime exception: " + this, e);
        }
    }

    /** {@inheritDoc} */
    @Override public void commit() throws GridException {
        try {
            commitAsync().get();
        }
        finally {
            ctx.tm().txContextReset();

            if (ctx.isNear())
                ctx.near().dht().context().tm().txContextReset();
        }
    }

    /** {@inheritDoc} */
    @Override public void prepare() throws GridException {
        prepareAsync().get();
    }

    /**
     * Checks that locks are in proper state for commit.
     *
     * @param entry Cache entry to check.
     */
    private void checkCommitLocks(GridCacheEntryEx<K, V> entry) {
        assert ownsLockUnsafe(entry) : "Lock is not owned for commit in PESSIMISTIC mode [entry=" + entry +
            ", tx=" + this + ']';
    }

    /**
     * Uncommits transaction by invalidating all of its entries.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void uncommit() {
        for (GridCacheTxEntry<K, V> e : writeMap().values()) {
            try {
                GridCacheEntryEx<K, V> cacheEntry = e.cached();

                if (e.op() != NOOP)
                    cacheEntry.invalidate(null, xidVer);
            }
            catch (Throwable t) {
                log().error("Failed to invalidate transaction entries while reverting a commit.", t);

                break;
            }
        }
    }

    /**
     * Performs batch database operations. This commit must be called
     * before {@link #userCommit()}. This way if there is a DB failure,
     * cache transaction can still be rolled back.
     *
     * @param writeEntries Transaction write set.
     * @throws GridException If batch update failed.
     */
    @SuppressWarnings({"CatchGenericClass"})
    protected void batchStoreCommit(Iterable<GridCacheTxEntry<K, V>> writeEntries) throws GridException {
        GridCacheStore store = ctx.config().getStore();

        if (store != null && storeEnabled) {
            try {
                // Implicit transactions are always updated at the end.
                if (isBatchUpdate()) {
                    if (writeEntries != null) {
                        Map<K, V> putMap = null;
                        List<K> rmvCol = null;

                        /*
                         * Batch database processing.
                         */
                        for (GridCacheTxEntry<K, V> e : writeEntries) {
                            if (e.op() == CREATE || e.op() == UPDATE) {
                                // Batch-process all removes if needed.
                                if (rmvCol != null && !rmvCol.isEmpty()) {
                                    CU.removeAllFromStore(ctx, log(), this, rmvCol);

                                    // Reset.
                                    rmvCol.clear();
                                }

                                if (putMap == null)
                                    putMap = new LinkedHashMap<K, V>(writeMap().size(), 1.0f);

                                putMap.put(e.key(), e.value());
                            }
                            else if (e.op() == DELETE) {
                                // Batch-process all puts if needed.
                                if (putMap != null && !putMap.isEmpty()) {
                                    CU.putAllToStore(ctx, log(), this, putMap);

                                    // Reset.
                                    putMap.clear();
                                }

                                if (rmvCol == null)
                                    rmvCol = new LinkedList<K>();

                                rmvCol.add(e.key());
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Ignoring NOOP entry for batch store commit: " + e);
                        }

                        if (putMap != null && !putMap.isEmpty()) {
                            assert rmvCol == null || rmvCol.isEmpty();

                            // Batch put at the end of transaction.
                            CU.putAllToStore(ctx, log(), this, putMap);
                        }

                        if (rmvCol != null && !rmvCol.isEmpty()) {
                            assert putMap == null || putMap.isEmpty();

                            // Batch remove at the end of transaction.
                            CU.removeAllFromStore(ctx, log(), this, rmvCol);
                        }
                    }
                }

                // Commit while locks are held.
                store.txEnd(ctx.namex(), this, true);
            }
            catch (GridException ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (Throwable ex) {
                setRollbackOnly();

                throw new GridException("Failed to commit transaction to database: " + this, ex);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass"})
    @Override public void userCommit() throws GridException {
        GridCacheTxState state = state();

        if (state != COMMITTING) {
            if (timedOut())
                throw new GridCacheTxTimeoutException("Transaction timed out: " + this);

            setRollbackOnly();

            throw new GridException("Invalid transaction state for commit [state=" + state + ", tx=" + this + ']');
        }

        checkValid(CU.<K, V>empty());

        boolean empty = F.isEmpty(near() ? txMap : writeMap());

        if (!empty) {
            batchStoreCommit(writeMap().values());

            // Register this transaction as completed prior to write-phase to
            // ensure proper lock ordering for removed entries.
            ctx.tm().addCommittedTx(this);

            try {
                ctx.tm().txContext(this);

                /*
                 * Commit to cache. Note that for 'near' transaction we loop through all the entries.
                 */
                for (GridCacheTxEntry<K, V> txEntry : (near() ? allEntries() : writeEntries())) {
                    UUID nodeId = txEntry.nodeId() == null ? this.nodeId : txEntry.nodeId();

                    try {
                        while (true) {
                            try {
                                GridCacheEntryEx<K, V> cached = txEntry.cached();

                                // Must try to evict near entries before committing from
                                // transaction manager to make sure locks are held.
                                if (!evictNearEntry(txEntry, true)) {
                                    GridCacheEntryEx<K, V> nearCached = null;

                                    if (ctx.isDht())
                                        nearCached = ctx.dht().near().peekEx(txEntry.key());

                                    // For near local transactions we must record DHT version
                                    // in order to keep near entries on backup nodes until
                                    // backup remote transaction completes.
                                    if (near())
                                        ((GridNearCacheEntry<K, V>)cached).recordDhtVersion(txEntry.dhtVersion());

                                    if (txEntry.op() == CREATE || txEntry.op() == UPDATE) {
                                        T2<Boolean, V> t = cached.innerSet(this, nodeId, txEntry.nodeId(),
                                            txEntry.value(), txEntry.valueBytes(), false, txEntry.expireTime(),
                                            txEntry.ttl(), true, txEntry.filters());

                                        if (nearCached != null && t.get1())
                                            nearCached.innerSet(null, nodeId, nodeId, txEntry.value(),
                                                txEntry.valueBytes(), false, txEntry.expireTime(), txEntry.ttl(), false,
                                                CU.<K, V>empty());
                                    }
                                    else if (txEntry.op() == DELETE) {
                                        T2<Boolean, V> t = cached.innerRemove(this, nodeId, txEntry.nodeId(), false,
                                            true, txEntry.filters());

                                        if (nearCached != null && t.get1())
                                            nearCached.innerRemove(null, nodeId, nodeId, false, false,
                                                CU.<K, V>empty());
                                    }
                                    else if (txEntry.op() == READ) {
                                        assert near();

                                        if (log.isDebugEnabled())
                                            log.debug("Ignoring READ entry when committing: " + txEntry);
                                    }
                                    else if (log.isDebugEnabled())
                                        log.debug("Ignoring NOOP entry when committing: " + txEntry);
                                }

                                // Check commit locks after set, to make sure that
                                // we are not changing obsolete entries.
                                // (innerSet and innerRemove will throw an exception
                                // if an entry is obsolete).
                                if (txEntry.op() != READ)
                                    checkCommitLocks(cached);

                                // Break out of while loop.
                                break;
                            }
                            // If entry cached within transaction got removed.
                            catch (GridCacheEntryRemovedException ignored) {
                                if (log.isDebugEnabled())
                                    log.debug("Got removed entry during transaction commit (will retry): " + txEntry);

                                txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                            }
                        }
                    }
                    catch (Throwable ex) {
                        state(UNKNOWN);

                        try {
                            // Courtesy to minimize damage.
                            uncommit();
                        }
                        catch (Throwable ex1) {
                            U.error(log, "Failed to uncommit transaction: " + this, ex1);
                        }

                        throw new GridCacheTxHeuristicException("Failed to locally write to cache " +
                            "(all transaction entries will be invalidated, however there was a window when entries " +
                            "for this transaction were visible to others): " + this, ex);
                    }
                }
            }
            finally {
                ctx.tm().txContextReset();
            }
        }

        if (doneFlag.compareAndSet(false, true)) {
            // Unlock all locks.
            ctx.tm().commitTx(this);

            assert completedBase != null;
            assert committedVers != null;
            assert rolledbackVers != null;
        }
    }

    /** {@inheritDoc} */
    @Override public void completedVersions(
        GridCacheVersion completedBase,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        this.completedBase = completedBase;
        this.committedVers = committedVers;
        this.rolledbackVers = rolledbackVers;
    }

    /**
     * @return Completed base for ordering.
     */
    public GridCacheVersion completedBase() {
        return completedBase;
    }

    /**
     * @return Committed versions.
     */
    public Collection<GridCacheVersion> committedVersions() {
        return committedVers;
    }

    /**
     * @return Rolledback versions.s
     */
    public Collection<GridCacheVersion> rolledbackVersions() {
        return rolledbackVers;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass"})
    @Override public boolean userCommitEC() throws GridException {
        assert ec();

        GridCacheTxState state = state();

        if (state == COMMITTED) {
            if (log().isDebugEnabled())
                log().debug("Commit in EVENTUALLY_CONSISTENT mode will not proceed as another thread " +
                    "has already finished it: " + this);

            // Some other thread has finished the commit.
            return false;
        }

        if (state != COMMITTING) {
            if (timedOut())
                throw new GridCacheTxTimeoutException("Transaction timed out: " + this);

            setRollbackOnly();

            throw new GridException("Invalid transaction state for commit [state=" + state + ", tx=" + this + ']');
        }

        checkValid(CU.<K, V>empty());

        if (!F.isEmpty(writeMap())) {
            initCommitVersion();

            List<GridCacheTxEntry<K, V>> commitList = null;

            /*
             * Commit to cache.
             */
            for (GridCacheTxEntry<K, V> txEntry : writeMap().values()) {
                try {
                    while (true) {
                        GridCacheEntryEx<K, V> cached = txEntry.cached();

                        try {
                            GridCacheVersion ver = txEntry.explicitVersion() == null ? xidVer : txEntry.explicitVersion();

                            if (cached.lockedLocally(ver.id())) {
                                // Avoid double commits.
                                if (txEntry.markCommitting()) {
                                    if (isBatchUpdate()) {
                                        if (commitList == null)
                                            commitList = new LinkedList<GridCacheTxEntry<K, V>>();

                                        commitList.add(txEntry);
                                    }

                                    if (txEntry.op() == CREATE || txEntry.op() == UPDATE) {
                                        cached.innerSet(this, nodeId, txEntry.nodeId(), txEntry.value(),
                                            txEntry.valueBytes(), false, txEntry.expireTime(), txEntry.ttl(),
                                            true, txEntry.filters());
                                    }
                                    else {
                                        assert txEntry.op() == DELETE; // EC transactions cannot have NOOP entries.

                                        // If batch commit.
                                        if (commitList != null) {
                                            // Commit to DB before obsolete flag is set.
                                            batchStoreCommitEC(commitList);

                                            // Unlock all committed entries
                                            for (GridCacheTxEntry<K, V> e1 : commitList) {
                                                if (e1 != txEntry) {
                                                    GridCacheEntryEx<K, V> cacheEntry1 = e1.cached();

                                                    GridCacheVersion ver1 = e1.explicitVersion() == null ?
                                                        xidVer : e1.explicitVersion();

                                                    // Unlock.
                                                    cacheEntry1.removeLock(ver1);
                                                }
                                            }

                                            // Reset commit list.
                                            commitList = null;
                                        }

                                        cached.innerRemove(this, nodeId, txEntry.nodeId(), false, true,
                                            txEntry.filters());
                                    }

                                    // Deleted entry did not get unlocked during batch commit,
                                    // so we need to unlock it.
                                    if (!isBatchUpdate() || txEntry.op() == DELETE)
                                        // Unlock.
                                        cached.removeLock(ver);

                                    if (log.isDebugEnabled())
                                        log.debug("Committed EC entry for transaction [txId=" + xidVer +
                                            ", entry=" + txEntry + ']');
                                }
                            }

                            break; // While.
                        }
                        // If entry cached within transaction got removed before lock.
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in commitEC method (will retry): " + txEntry);

                            txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                        }
                    }
                }
                catch (Throwable ex) {
                    // Log error, and move the next entry to make the best effort to commit all values.
                    log().error("Failed to locally write a value to cache (best attempt will be made to commit " +
                        "all transaction entries) [failedEntry=" + txEntry + ", transaction=" + this + ']', ex);
                }
            }

            if (commitList != null) {
                // Batch commit all entries locked so far.
                batchStoreCommitEC(commitList);

                // Unlock all committed entries
                for (GridCacheTxEntry<K, V> txEntry : commitList) {
                    while (true) {
                        try {
                            GridCacheEntryEx<K, V> cacheEntry = txEntry.cached();

                            GridCacheVersion ver = txEntry.explicitVersion() == null ?
                                xidVer : txEntry.explicitVersion();

                            // Unlock.
                            cacheEntry.removeLock(ver);

                            break; // While.
                        }
                        // If entry cached within transaction got removed before lock.
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in commitEC method (will retry): " + txEntry);

                            txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                        }
                    }
                }
            }

            for (GridCacheTxEntry<K, V> txEntry : writeMap().values()) {
                GridCacheVersion ver = txEntry.explicitVersion() == null ? xidVer : txEntry.explicitVersion();

                while (true) {
                    try {
                        GridCacheEntryEx<K, V> cached = txEntry.cached();

                        if (cached.candidate(ver) != null) {
                            if (log.isDebugEnabled())
                                log.debug("EC transaction cannot be complete yet [txId=" + xidVer + ", entry=" +
                                    txEntry + ']');

                            return false;
                        }

                        break;
                    }
                    // If entry cached within transaction got removed before lock.
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in commitEC method (will retry): " + txEntry);

                        // Don't recreate, as entry may have been removed by this transaction.
                        GridCacheEntryEx<K, V> cached = ctx.cache().peekEx(txEntry.key());

                        if (cached == null)
                            break; // While.

                        txEntry.cached(cached, txEntry.keyBytes());
                    }
                }
            }
        }

        // Clean up.
        if (doneFlag.compareAndSet(false, true)) {
            ctx.tm().commitTx(this);

            return true;
        }

        return false;
    }

    /**
     * Commits entries in batch for EC transactions.
     *
     * @param commitList Commit list.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void batchStoreCommitEC(Iterable<GridCacheTxEntry<K, V>> commitList) {
        GridCacheStore store = ctx.config().getStore();

        if (commitList != null && store != null && storeEnabled) {
            assert isBatchUpdate();

            Map<K, V> putMap = null;

            K rmvKey = null;

            try {
                /*
                 * Batch database processing.
                 */
                for (GridCacheTxEntry<K, V> e : commitList) {
                    // Removed entry must be last in commitList.
                    assert rmvKey == null;

                    if (e.op() == CREATE || e.op() == UPDATE) {
                        if (putMap == null)
                            putMap = new LinkedHashMap<K, V>(writeMap().size(), 1.0f);

                        putMap.put(e.key(), e.value());
                    }
                    else {
                        assert e.op() == DELETE; // Cannot have NOOP entries in EC mode.

                        if (putMap != null && !putMap.isEmpty()) {
                            // Batch put at the end of transaction.
                            CU.putAllToStore(ctx, log(), this, putMap);

                            putMap = null;
                        }

                        rmvKey = e.key();

                        CU.removeFromStore(ctx, log(), this, rmvKey);
                    }
                }

                if (putMap != null && !putMap.isEmpty())
                    // Batch put at the end of transaction.
                    CU.putAllToStore(ctx, log(), this, putMap);
            }
            catch (Throwable ex) {
                // Throw rollback exception to signify that
                // transaction has been rolled back.
                U.error(log, "Failed to persist values to persistent store [putKeys=" +
                    (putMap != null ? putMap.keySet() : "") + ", rmvKey=" + rmvKey +
                    ", tx=" + this + ']', ex);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void userRollback() throws GridException {
        GridCacheTxState state = state();

        if (state != ROLLING_BACK && state != ROLLED_BACK) {
            setRollbackOnly();

            throw new GridException("Invalid transaction state for rollback [state=" + state + ", tx=" + this + ']');
        }

        if (doneFlag.compareAndSet(false, true)) {
            if (near())
                // Must evict near entries before rolling back from
                // transaction manager, so they will be removed from cache.
                for (GridCacheTxEntry<K, V> e : allEntries())
                    evictNearEntry(e, false);

            ctx.tm().rollbackTx(this);

            GridCacheStore store = ctx.config().getStore();

            if (isSingleUpdate() || isBatchUpdate())
                store.txEnd(ctx.namex(), this, false);
        }
    }

    /** {@inheritDoc} */
    @Override public V get(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return getAll(Collections.singletonList(key), filter).get(key);
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return getAllAsync(keys, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(final K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridFutureWrapper<V, Map<K, V>>(getAllAsync(Collections.singletonList(key), filter),
            new C1<Map<K, V>, V>() {
                @Override public V apply(Map<K, V> map) {
                    return map.get(key);
                }
            }
        );
    }

    /**
     * Checks if there is a cached or swapped value for {@link #getAllAsync(Collection, GridPredicate[])}
     * method.
     *
     * @param keys Key to enlist.
     * @param map Return map.
     * @param missed Map of missed keys.
     * @param opId Operation ID.
     * @param filter Filter to test.
     * @return Collection of skipped entries if any.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    private Set<K> enlistRead(Collection<? extends K> keys, Map<K, V> map, Map<K, GridCacheVersion> missed, int opId,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        assert !F.isEmpty(keys);

        Set<K> skipped = null;

        // In this loop we cover only read-committed or optimistic transactions.
        // Transactions that are pessimistic and not read-committed are covered
        // outside of this loop.
        for (K key : keys) {
            if (key == null)
                continue;

            // Check write map (always check writes first).
            GridCacheTxEntry<K, V> txEntry = entry(key);

            // Either non-read-committed or there was a previous write.
            if (txEntry != null) {
                // This if branch is just an optimization.
                if (txEntry.lastMarked()) {
                    // We can check filter even in pessimistic mode, as lock is held on marked entries.
                    if (ctx.isAll(txEntry.cached(), filter)) {
                        V val = txEntry.rootValue();

                        if (val != null)
                            map.put(key, val);
                    }
                    else
                        skipped = skip(skipped, key);
                }
                else {
                    // Handle misses.
                    while (true) {
                        GridCacheEntryEx<K, V> cached = txEntry.cached();

                        try {
                            txEntry = addEntry(READ, opId, null, cached, filter);

                            missed.put(txEntry.key(), cached.version());

                            break; // While.
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry while enlisting missed tx entry (will retry): " + cached);

                            // Reset.
                            txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                        }
                    }
                }
            }
            // First time access within transaction.
            else {
                while (true) {
                    GridCacheEntryEx<K, V> cached = ctx.cache().entryEx(key);

                    try {
                        GridCacheVersion ver = cached.version();

                        V val = null;

                        if (!pessimistic()) {
                            // This call will check for filter.
                            val = cached.innerGet(this, true, /*no read-through*/false, true, true, true, filter);

                            if (val != null)
                                map.put(key, val);
                            else
                                missed.put(key, ver);
                        }
                        else
                            // We must wait for the lock in pessimistic mode.
                            missed.put(key, ver);

                        if (!readCommitted()) {
                            txEntry = addEntry(READ, opId, val, cached, filter);

                            // As optimization, mark as checked immediately
                            // for non-pessimistic if value is not null.
                            if (val != null && !pessimistic())
                                txEntry.mark();
                        }

                        break; // While.
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in transaction getAllAsync(..) (will retry): " + key);
                    }
                    catch (GridCacheFilterFailedException e) {
                        if (log.isDebugEnabled())
                            log.debug("Filter validation failed for entry: " + cached);

                        if (!readCommitted()) {
                            // Value for which failure occurred.
                            V val = e.<V>value();

                            txEntry = addEntry(READ, opId, val, cached, filter);

                            // Mark as checked immediately for non-pessimistic.
                            if (val != null && !pessimistic())
                                txEntry.mark();
                        }

                        break; // While loop.
                    }
                }
            }
        }

        return skipped;
    }

    /**
     * Adds skipped key.
     *
     * @param skipped Skipped set (possibly {@code null}).
     * @param key Key to add.
     * @return Skipped set.
     */
    private Set<K> skip(Set<K> skipped, K key) {
        if (skipped == null)
            skipped = new GridLeanSet<K>();

        skipped.add(key);

        if (log.isDebugEnabled())
            log.debug("Added key to skipped set: " + key);

        return skipped;
    }

    /**
     * Loads all missed keys for {@link #getAllAsync(Collection, GridPredicate[])} method.
     *
     * @param map Return map.
     * @param missedMap Missed keys.
     * @param redos Keys to retry.
     * @param opId Operation ID.
     * @param filter Filter.
     * @return Loaded key-value pairs.
     */
    private GridFuture<Map<K, V>> checkMissed(final Map<K, V> map, final Map<K, GridCacheVersion> missedMap,
        final Collection<K> redos, final int opId, final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (log.isDebugEnabled())
            log.debug("Loading missed values for missed map: " + missedMap);

        return new GridEmbeddedFuture<Map<K, V>, Boolean>(ctx.kernalContext(),
            loadMissing(false, missedMap.keySet(), new CI2<K, V>() {
                private GridCacheVersion nextVer;

                @Override public void apply(K key, V val) {
                    if (isRollbackOnly()) {
                        if (log.isDebugEnabled())
                            log.debug("Ignoring loaded value for read because transaction was rolled back: " +
                                GridCacheTxLocalAdapter.this);

                        return;
                    }

                    GridCacheVersion ver = missedMap.get(key);

                    if (ver == null) {
                        if (log.isDebugEnabled())
                            log.debug("Value from storage was never asked for [key=" + key + ", val=" + val + ']');

                        return;
                    }

                    GridCacheTxEntry<K, V> txEntry = entry(key, opId);

                    // In pessimistic mode we hold the lock, so filter validation
                    // should always be valid.
                    if (pessimistic())
                        ver = null;

                    // Initialize next version.
                    if (nextVer == null)
                        nextVer = ctx.versions().next();

                    while (true) {
                        assert txEntry != null || readCommitted();

                        GridCacheEntryEx<K, V> e = txEntry == null ? ctx.cache().entryEx(key) : txEntry.cached();

                        try {
                            boolean pass = ctx.isAll(e, filter);

                            // Must initialize to true since even if filter didn't pass,
                            // we still record the transaction value.
                            boolean set = true;

                            if (pass) {
                                try {
                                    set = e.versionedValue(val, ver, nextVer);
                                }
                                catch (GridCacheEntryRemovedException ignore) {
                                    if (log.isDebugEnabled())
                                        log.debug("Got removed entry in transaction getAll method " +
                                            "(will try again): " + e);

                                    if (pessimistic() && !readCommitted() && !isRollbackOnly()) {
                                        U.error(log, "Inconsistent transaction state (entry got removed while " +
                                            "holding lock) [entry=" + e + ", tx=" + GridCacheTxLocalAdapter.this + "]");

                                        setRollbackOnly();

                                        return;
                                    }

                                    if (txEntry != null)
                                        txEntry.cached(ctx.cache().entryEx(key), txEntry.keyBytes());

                                    continue; // While loop.
                                }
                            }

                            // In pessimistic mode, we should always be able to set.
                            assert set || !pessimistic();

                            if (readCommitted()) {
                                if (pass && val != null)
                                    map.put(key, val);
                            }
                            else {
                                assert txEntry != null;

                                if (set || F.isEmpty(filter)) {
                                    txEntry.setAndMark(val);

                                    if (pass && val != null)
                                        map.put(key, val);
                                }
                                else {
                                    assert !pessimistic() : "Pessimistic transaction should not have to redo gets: " +
                                        this;

                                    if (log.isDebugEnabled())
                                        log.debug("Failed to set versioned value for entry (will redo): " + e);

                                    redos.add(key);
                                }
                            }

                            if (log.isDebugEnabled())
                                log.debug("Set value loaded from store into entry from transaction [set=" + set +
                                    ", matchVer=" + ver + ", newVer=" + nextVer + ", entry=" + e + ']');

                            break; // While loop.
                        }
                        catch (GridException ex) {
                            throw new GridRuntimeException("Failed to put value for cache entry: " + e, ex);
                        }
                    }
                }
            }),
            new C2<Boolean, Exception, Map<K, V>>() {
                @Override public Map<K, V> apply(Boolean b, Exception e) {
                    if (e != null) {
                        setRollbackOnly();

                        throw new GridClosureException(e);
                    }

                    if (!b && !readCommitted()) {
                        // There is no store - we must mark the entries.
                        for (K key : missedMap.keySet()) {
                            GridCacheTxEntry<K, V> txEntry = entry(key, opId);

                            if (txEntry != null)
                                txEntry.mark();
                        }
                    }

                    return map;
                }
            });
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(Collection<? extends K> keys,
        final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys))
            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());

        init();

        boolean single = keys.size() == 1;

        final int opId = newOpId();

        try {
            checkValid(CU.<K, V>empty());

            final Map<K, V> map = new GridLeanMap<K, V>(keys.size());

            final Map<K, GridCacheVersion> missed = new GridLeanMap<K, GridCacheVersion>(pessimistic() ? keys.size() : 0);

            Set<K> skipped = enlistRead(keys, map, missed, opId, filter);

            if (single && missed.isEmpty())
                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), map);

            // Handle locks.
            if (pessimistic() && !readCommitted()) {
                final Collection<? extends K> lockKeys = F.view(keys, F.notIn(skipped));

                GridFuture<Boolean> fut = ctx.cache().txLockAsync(lockKeys, lockTimeout(), this, true, true,
                    isolation, isInvalidate(), CU.<K, V>empty());

                return new GridEmbeddedFuture<Map<K, V>, Boolean>(
                    ctx.kernalContext(),
                    fut,
                    new PLC2<Map<K, V>>() {
                        @Override public GridFuture<Map<K, V>> postLock() throws GridException {
                            if (log.isDebugEnabled())
                                log.debug("Acquired transaction lock for read on keys: " + lockKeys);

                            // Load keys only after the locks have been acquired.
                            for (K key : lockKeys) {
                                if (map.containsKey(key))
                                    // We already have a return value.
                                    continue;

                                GridCacheTxEntry<K, V> txEntry = entry(key, opId);

                                assert txEntry != null;

                                if (!txEntry.isRoot()) {
                                    // Wait for parent and mark this entry.
                                    V val = txEntry.waitForParent(true);

                                    if (ctx.isAll(txEntry.cached(), filter) && val != null)
                                        map.put(key, val);

                                    missed.remove(key);
                                }
                                else {
                                    // Check if there is cached value.
                                    while (true) {
                                        GridCacheEntryEx<K, V> cached = txEntry.cached();

                                        try {
                                            V val = cached.innerGet(GridCacheTxLocalAdapter.this, swapEnabled,
                                                false, true, true, true, filter);

                                            // If value is in cache and passed the filter.
                                            if (val != null) {
                                                map.put(key, val);

                                                missed.remove(key);

                                                txEntry.setAndMark(val);
                                            }
                                            else if (ctx.isNear()) {
                                                // Value was fetched during lock acquisition.
                                                // If it's null, then it is not in cache or store.
                                                missed.remove(key);

                                                txEntry.setAndMark(val);
                                            }

                                            break; // While.
                                        }
                                        catch (GridCacheEntryRemovedException ignore) {
                                            if (log.isDebugEnabled())
                                                log.debug("Got removed exception in get postLock (will retry): " +
                                                    cached);

                                            txEntry.cached(ctx.cache().entryEx(key), txEntry.keyBytes());
                                        }
                                        catch (GridCacheFilterFailedException e) {
                                            // Failed value for the filter.
                                            @SuppressWarnings({"RedundantTypeArguments"}) // Idea wrongly warns here.
                                            V val = e.<V>value();

                                            if (val != null) {
                                                // If filter fails after lock is acquired, we don't reload,
                                                // regardless if value is null or not.
                                                missed.remove(key);

                                                txEntry.setAndMark(val);
                                            }

                                            break; // While.
                                        }
                                    }
                                }
                            }

                            if (!missed.isEmpty())
                                return checkMissed(map, missed, null, opId, filter);

                            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());
                        }
                    },
                    new FinishClosure<Map<K, V>>() {
                        @Override Map<K, V> finish(Map<K, V> loaded) {
                            map.putAll(loaded);

                            return map;
                        }
                    }
                );
            }
            else {
                assert optimistic() || ec() || readCommitted();

                final Collection<K> redos = new LinkedList<K>();

                if (!missed.isEmpty()) {
                    if (!readCommitted()) {
                        for (Iterator<K> it = missed.keySet().iterator(); it.hasNext(); ) {
                            K key = it.next();

                            if (map.containsKey(key)) {
                                it.remove();

                                // We already have a return value.
                                continue;
                            }

                            GridCacheTxEntry<K, V> txEntry = entry(key, opId);

                            assert txEntry != null;

                            if (!txEntry.isRoot()) {
                                // We choose to wait here and hold the thread,
                                // even though the root request may have to redo.
                                // We assume that if redo happened, then entry
                                // most likely has been populated with value and
                                // this wait will be instantaneous. In rare cases
                                // it is possible that entry will have to be reloaded
                                // on redo, but to handle it would require complex
                                // logic which we choose not to implement.
                                V val = txEntry.waitForParent(true);

                                if (ctx.isAll(txEntry.cached(), filter) && val != null)
                                    // Put value from root.
                                    map.put(key, val);

                                it.remove();
                            }
                        }
                    }

                    if (missed.isEmpty())
                        return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), map);

                    return new GridEmbeddedFuture<Map<K, V>, Map<K, V>>(
                        ctx.kernalContext(),
                        // First future.
                        checkMissed(map, missed, redos, opId, filter),
                        // Closure that returns another future, based on result from first.
                        new PMC<Map<K, V>>() {
                            @Override public GridFuture<Map<K, V>> postMiss(Map<K, V> map) {
                                if (redos.isEmpty())
                                    return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(),
                                        Collections.<K, V>emptyMap());

                                if (log.isDebugEnabled())
                                    log.debug("Starting to future-recursively get values for keys: " + redos);

                                // Future recursion.
                                return getAllAsync(redos, filter);
                            }
                        },
                        // Finalize.
                        new FinishClosure<Map<K, V>>() {
                            @Override Map<K, V> finish(Map<K, V> loaded) {
                                map.putAll(loaded);

                                return map;
                            }
                        }
                    );
                }

                return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), map);
            }
        }
        catch (GridException e) {
            setRollbackOnly();

            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public V put(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return putAllAsync(F.t(key, val), true, filter).get().value();
    }

    /** {@inheritDoc} */
    @Override public boolean putx(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return putAllAsync(F.t(key, val), false, filter).get().success();
    }

    /** {@inheritDoc} */
    @Override public GridCacheReturn<V> putAll(Map<? extends K, ? extends V> map,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return putAllAsync(map, false, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putAsync(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridFutureWrapper<V, GridCacheReturn<V>>(putAllAsync(F.t(key, val), true, filter),
            CU.<V>return2value());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxAsync(K key, V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridFutureWrapper<Boolean, GridCacheReturn<V>>(
            putAllAsync(F.t(key, val), false, filter), CU.<V>return2flag());
    }

    /** {@inheritDoc} */
    @Override public V remove(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return removeAllAsync(Collections.singletonList(key), implicit, true, filter).get().value();
    }

    /** {@inheritDoc} */
    @Override public boolean removex(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return removeAllAsync(Collections.singletonList(key), implicit, false, filter).get().success();
    }

    /** {@inheritDoc} */
    @Override public GridCacheReturn<V> removeAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return removeAllAsync(keys, implicit, false, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridFutureWrapper<V, GridCacheReturn<V>>(
            removeAllAsync(Collections.singletonList(key), implicit, true, filter),
            CU.<V>return2value());
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridFutureWrapper<Boolean, GridCacheReturn<V>>(
            removeAllAsync(Collections.singletonList(key), implicit, false, filter),
            CU.<V>return2flag());
    }

    /**
     * Checks filter for non-pessimistic transactions.
     *
     * @param cached Cached entry.
     * @param filter Filter to check.
     * @return {@code True} if passed or pessimistic.
     * @throws GridException If failed.
     */
    private boolean filter(GridCacheEntryEx<K, V> cached,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return pessimistic() || ctx.isAll(cached, filter);
    }

    /**
     * Internal routine for <tt>putAll(..)</tt>
     *
     * @param keys Keys to enlist.
     * @param implicit Implicit flag.
     * @param lookup Value lookup map ({@code null} for remove).
     * @param opId Operation ID.
     * @param retval Flag indicating whether a value should be returned.
     * @param lockOnly If {@code true}, then entry will be enlisted as noop.
     * @param filter User filters.
     * @param ret Return value.
     * @return Future with skipped keys (the ones that didn't pass filter for pessimistic transactions).
     */
    protected GridFuture<Set<K>> enlistWrite(Collection<? extends K> keys, boolean implicit,
        Map<? extends K, ? extends V> lookup, int opId, boolean retval, boolean lockOnly,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter, final GridCacheReturn<V> ret) {
        Set<K> skipped = null;

        boolean rmv = lookup == null;

        try {
            for (K key : keys) {
                V val = rmv ? null : lookup.get(key);

                if (key == null || (!rmv && val == null))
                    continue;

                GridCacheTxEntry<K, V> txEntry = entry(key);

                GridCacheEntryEx<K, V> cached;

                // First time access.
                if (txEntry == null) {
                    while (true) {
                        cached = ctx.cache().entryEx(key);

                        cached.unswap();

                        try {
                            // Check if lock is being explicitly acquired by the same thread.
                            if (!implicit && cached.lockedByThread(xidVer))
                                throw new GridException("Cannot access key within transaction if lock is " +
                                    "externally held [key=" + key + ", entry=" + cached + ']');

                            V old = cached.peek(GLOBAL, CU.<K, V>empty());

                            if (!filter(cached, filter)) {
                                skipped = skip(skipped, key);

                                ret.set(old, false);

                                if (!readCommitted() && old != null) {
                                    // Enlist failed filters as reads for non-read-committed mode,
                                    // so future ops will get the same values.
                                    txEntry = addEntry(READ, opId, old, cached, filter);

                                    txEntry.mark();
                                }

                                break; // While.
                            }

                            txEntry = addEntry(lockOnly ? NOOP : rmv ? DELETE : ret.hasValue() ? UPDATE : CREATE,
                                opId, val, cached, filter);

                            if (!pessimistic()) {
                                txEntry.mark();

                                if (old == null) {
                                    if (retval) {
                                        // If return value is required, then we know for sure that there is only
                                        // one key in the keys collection.
                                        assert keys.size() == 1;

                                        GridFuture<Boolean> fut = loadMissing(true, F.asList(key), new CI2<K, V>() {
                                            @Override public void apply(K k, V v) {
                                                if (log.isDebugEnabled())
                                                    log.debug("Loaded value from remote node [key=" + k + ", val=" +
                                                        v + ']');

                                                ret.set(v, true);
                                            }
                                        });

                                        return new GridEmbeddedFuture<Set<K>, Boolean>(
                                            ctx.kernalContext(),
                                            fut,
                                            new C2<Boolean, Exception, Set<K>>() {
                                                @Override public Set<K> apply(Boolean b, Exception e) {
                                                    if (e != null)
                                                        throw new GridClosureException(e);

                                                    return Collections.emptySet();
                                                }
                                            }
                                        );
                                    }
                                    else
                                        ret.set(null, !rmv);
                                }
                                else
                                    ret.set(old, true);
                            }
                            else
                                ret.set(old, true);

                            break; // While.
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in transaction putAll0 method: " + cached);
                        }
                    }
                }
                else {
                    cached = txEntry.cached();

                    V v = txEntry.value();

                    boolean del = txEntry.op() == DELETE && rmv;

                    if (!del) {
                        if (!filter(cached, filter)) {
                            skipped = skip(skipped, key);

                            ret.set(v, false);

                            continue;
                        }

                        txEntry = addEntry(rmv ? DELETE : v != null ? UPDATE : CREATE, opId, val, cached, filter);
                    }

                    if (!pessimistic()) {
                        txEntry.markAndCopyToRoot();

                        // Set tx entry and return values.
                        ret.set(v, !lockOnly && (v != null || !rmv));
                    }
                }
            }
        }
        catch (GridException e) {
            return new GridFinishedFuture<Set<K>>(ctx.kernalContext(), e);
        }

        return new GridFinishedFuture<Set<K>>(ctx.kernalContext(), skipped);
    }

    /**
     * Post lock processing for put or remove.
     *
     * @param keys Keys.
     * @param failed Collection of potentially failed keys (need to populate in this method).
     * @param ret Return value.
     * @param rmv {@code True} if remove.
     * @param opId Operation ID.
     * @param filter Filter.
     * @return Failed keys.
     * @throws GridException If error.
     */
    protected Set<K> postLockWrite(Iterable<? extends K> keys, Set<K> failed, GridCacheReturn<V> ret, boolean rmv,
        int opId, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        for (K k : keys) {
            GridCacheTxEntry<K, V> txEntry = entry(k, opId);

            if (txEntry == null)
                throw new GridException("Transaction entry is null (most likely collection of keys passed into cache " +
                    "operation was changed before operation completed) [missingKey=" + k + ", tx=" + this + ']');

            while (true) {
                GridCacheEntryEx<K, V> cached = txEntry.cached();

                try {
                    assert cached.lockedByThread(threadId) || isRollbackOnly() :
                        "Transaction lock is not acquired [entry=" + cached + ", tx=" + this +
                            ", nodeId=" + ctx.nodeId() + ']';

                    if (log.isDebugEnabled())
                        log.debug("Post lock write entry: " + cached);

                    V v;

                    if (!txEntry.isRoot())
                        // Note that this wait is instantaneous, that's why it's OK
                        // to wait here. We only do it to properly order all the
                        // filter checks.
                        ret.value(v = txEntry.waitForParent(/*don't mark*/false));
                    else {
                        ret.value(v = cached.peek(GLOBAL, CU.<K, V>empty()));

                        if (v == null && near())
                            ret.value(v = ctx.near().dht().peek(k, F.asList(GLOBAL)));
                    }

                    boolean pass = ctx.isAll(cached, filter);

                    // For remove operation we return true only if we are removing s/t,
                    // i.e. cached value is not null.
                    ret.success(pass && (v != null || !rmv));

                    if (pass) {
                        txEntry.markAndCopyToRoot();

                        if (log.isDebugEnabled())
                            log.debug("Filter passed in post lock for key: " + k);
                    }
                    else {
                        failed = skip(failed, k);

                        // Change to NOOP, so it will be unlocked.
                        txEntry.setAndMark(NOOP, ret.value());
                    }

                    break; // While.
                }
                // If entry cached within transaction got removed before lock.
                catch (GridCacheEntryRemovedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry in putAllAsync method (will retry): " + cached);

                    txEntry.cached(ctx.cache().entryEx(k), txEntry.keyBytes());
                }
            }
        }

        if (log.isDebugEnabled())
            log.debug("Entries that failed after lock filter check: " + failed);

        return failed;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheReturn<V>> putAllAsync(final Map<? extends K, ? extends V> map,
        boolean retval, final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (log.isDebugEnabled())
            log.debug("Called putAllAsync(...) [tx=" + this + ", map=" + map + ", retval=" + retval + "]");

        try {
            checkValid(filter);
        }
        catch (GridException e) {
            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
        }

        init();

        final int opId = newOpId();

        final GridCacheReturn<V> ret = new GridCacheReturn<V>(false);

        if (F.isEmpty(map)) {
            if (implicit())
                try {
                    commit();
                }
                catch (GridException e) {
                    return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
                }

            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), ret.success(true));
        }

        try {
            final GridFuture<Set<K>> loadFut = enlistWrite(map.keySet(), implicit, map, opId, retval, false, filter, ret);

            if (pessimistic()) {
                // Loose all previously skipped keys.
                final Collection<? extends K> keys = F.view(map.keySet(), F.notIn(loadFut.get()));

                if (log.isDebugEnabled())
                    log.debug("Before acquiring transaction lock for put on keys: " + keys);

                GridFuture<Boolean> fut = ctx.cache().txLockAsync(keys, lockTimeout(), this, false, retval, isolation,
                    isInvalidate(), CU.<K, V>empty());

                return new GridEmbeddedFuture<GridCacheReturn<V>, Boolean>(
                    ctx.kernalContext(),
                    fut,
                    new PLC1<GridCacheReturn<V>>() {
                        @Override public GridCacheReturn<V> postLock(GridCacheReturn<V> ret) throws GridException {
                            if (log.isDebugEnabled())
                                log.debug("Acquired transaction lock for put on keys: " + keys);

                            Set<K> failed = postLockWrite(keys, loadFut.get(), ret, /*remove*/false, opId, filter);

                            // Write-through.
                            if (isSingleUpdate())
                                CU.putAllToStore(ctx, log, GridCacheTxLocalAdapter.this, F.view(map, F.notIn(failed)));

                            return ret;
                        }
                    }, ret);
            }
            else {
                // Write-through (if there is cache-store, persist asynchronously).
                if (isSingleUpdate()) {
                    // Note that we can't have filter here, because if there was filter,
                    // it would not pass initial 'checkValid' validation for optimistic mode.
                    return new GridEmbeddedFuture<GridCacheReturn<V>, Set<K>>(
                        ctx.kernalContext(),
                        loadFut,
                        new C2<Set<K>, Exception, GridCacheReturn<V>>() {
                            @Override public GridCacheReturn<V> apply(Set<K> skipped, Exception e) {
                                try {
                                    CU.putAllToStore(ctx, log(), GridCacheTxLocalAdapter.this,
                                        F.view(map, F.not(F.contains(skipped))));
                                }
                                catch (GridException ex) {
                                    throw new GridClosureException(ex);
                                }

                                return ret;
                            }
                        });
                }

                return new GridFutureWrapper<GridCacheReturn<V>, Set<K>>(loadFut, new C1<Set<K>, GridCacheReturn<V>>() {
                    @Override public GridCacheReturn<V> apply(Set<K> e) {
                        return ret;
                    }
                });
            }
        }
        catch (GridException e) {
            setRollbackOnly();

            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheReturn<V>> removeAllAsync(Collection<? extends K> keys,
        boolean implicit, boolean retval, final GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        assert keys != null;

        if (log.isDebugEnabled())
            log.debug("Called removeAllAsync(...) [tx=" + this + ", keys=" + keys + ", implicit=" + implicit +
                ", retval=" + retval + "]");

        try {
            checkValid(filter);
        }
        catch (GridException e) {
            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
        }

        final GridCacheReturn<V> ret = new GridCacheReturn<V>(false);

        if (F.isEmpty(keys)) {
            if (implicit()) {
                try {
                    commit();
                }
                catch (GridException e) {
                    return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
                }
            }

            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), ret.success(true));
        }

        init();

        final int opId = newOpId();

        try {
            final GridFuture<Set<K>> loadFut = enlistWrite(keys, implicit, null, opId, retval, false, filter, ret);

            final Collection<K> opKeys = new GridCacheTxCollection<K, V, K>(txMap.values(), CU.<K, V>opId(opId),
                CU.<K, V>tx2key()).seal();

            if (log.isDebugEnabled())
                log.debug("Remove op keys [opId=" + opId + ", opKeys=" + opKeys + ']');

            // Acquire locks only after having added operation to the write set.
            // Otherwise, during rollback we will not know whether locks need
            // to be rolled back.
            if (pessimistic()) {
                // Loose all skipped and previously locked (we cannot reenter locks here).
                final Collection<? extends K> passedKeys = F.view(opKeys, F.notIn(loadFut.get()));

                if (log.isDebugEnabled())
                    log.debug("Before acquiring transaction lock for remove on keys: " + passedKeys);

                GridFuture<Boolean> fut = ctx.cache().txLockAsync(passedKeys, lockTimeout(), this, false, retval,
                    isolation, isInvalidate(), CU.<K, V>empty());

                return new GridEmbeddedFuture<GridCacheReturn<V>, Boolean>(
                    ctx.kernalContext(),
                    fut,
                    new PLC1<GridCacheReturn<V>>() {
                        @Override protected GridCacheReturn<V> postLock(GridCacheReturn<V> ret) throws GridException {
                            if (log.isDebugEnabled())
                                log.debug("Acquired transaction lock for remove on keys: " + passedKeys);

                            Set<K> failed = postLockWrite(passedKeys, loadFut.get(), ret, /*remove*/true, opId, filter);

                            // Write-through.
                            if (isSingleUpdate())
                                CU.removeAllFromStore(ctx, log, GridCacheTxLocalAdapter.this,
                                    F.view(passedKeys, F.notIn(failed)));

                            return ret;
                        }
                    },
                    ret);
            }
            else {
                // Write-through (if there is cache-store, persist asynchronously).
                if (isSingleUpdate()) {
                    // Note that we can't have filter here, because if there was filter,
                    // it would not pass initial 'checkValid' validation for optimistic mode.
                    return new GridEmbeddedFuture<GridCacheReturn<V>, Set<K>>(
                        ctx.kernalContext(),
                        loadFut,
                        new C2<Set<K>, Exception, GridCacheReturn<V>>() {
                            @Override public GridCacheReturn<V> apply(Set<K> skipped, Exception e) {
                                try {
                                    CU.removeAllFromStore(ctx, log(), GridCacheTxLocalAdapter.this,
                                        F.view(opKeys, F.not(F.contains(skipped))));
                                }
                                catch (GridException ex) {
                                    throw new GridClosureException(ex);
                                }

                                return ret;
                            }
                        });
                }

                return new GridFutureWrapper<GridCacheReturn<V>, Set<K>>(loadFut, new C1<Set<K>, GridCacheReturn<V>>() {
                    @Override public GridCacheReturn<V> apply(Set<K> e) {
                        return ret;
                    }
                });
            }
        }
        catch (GridException e) {
            setRollbackOnly();

            return new GridFinishedFuture<GridCacheReturn<V>>(ctx.kernalContext(), e);
        }
    }

    /**
     * Initializes read map.
     *
     * @return {@code True} if transaction was successfully  started.
     */
    public boolean init() {
        if (txMap == null) {
            txMap = Collections.synchronizedMap(new LinkedHashMap<K, GridCacheTxEntry<K, V>>());

            readView = new GridCacheTxMap<K, V>(txMap, CU.<K, V>reads());
            writeView = new GridCacheTxMap<K, V>(txMap, CU.<K, V>writes());

            return ctx.tm().onStarted(this);
        }

        return true;
    }

    /**
     * Checks transaction expiration.
     *
     * @param filter Optional array of filters to check for optimistic transactions.
     * @throws GridException If transaction failed.
     */
    protected void checkValid(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (optimistic() && !ctx.config().isBatchUpdateOnCommit() && !F.isEmpty(filter))
            throw new GridException("Operations that receive non-empty predicate filters cannot be used for " +
                "optimistic mode if 'batchUpdateOnCommit' configuration flag is set to 'false': " + this);

        if (isRollbackOnly()) {
            if (timedOut())
                throw new GridCacheTxTimeoutException("Cache transaction timed out: " + this);

            GridCacheTxState state = state();

            if (state == ROLLING_BACK || state == ROLLED_BACK)
                throw new GridCacheTxRollbackException("Cache transaction is marked as rollback-only " +
                    "(will be rolled back automatically): " + this);

            if (state == UNKNOWN)
                throw new GridCacheTxHeuristicException("Cache transaction is in unknown state " +
                    "(remote transactions will be invalidated): " + this);

            throw new GridException("Cache transaction marked as rollback-only: " + this);
        }

        if (remainingTime() == 0 && setRollbackOnly())
            throw new GridCacheTxTimeoutException("Cache transaction timed out " +
                "(was rolled back automatically): " + this);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheVersion> alternateVersions() {
        return Collections.emptyList();
    }

    /**
     * @param op Cache operation.
     * @param opId Operation ID.
     * @param val Value.
     * @param entry Cache entry.
     * @param filter Filter.
     * @return Transaction entry.
     */
    protected final GridCacheTxEntry<K, V> addEntry(GridCacheOperation op, int opId, V val,
        GridCacheEntryEx<K, V> entry, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        K key = entry.key();

        checkInternal(key);

        assert state() == GridCacheTxState.ACTIVE : "Invalid tx state for adding entry [op=" + op + ", opId=" + opId +
            ", val=" + val + ", entry=" + entry + ", filter=" + Arrays.toString(filter) +
            ", txCtx=" + ctx.tm().txContextVersion() + ", tx=" + this + ']';

        while (true) {
            try {

                GridCacheTxEntry<K, V> root = txMap.get(key);

                GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(ctx, this, opId, root, op, val, entry.ttl(),
                    entry, filter);

                // DHT local transactions don't have explicit locks.
                if (!ctx.isDht()) {
                    // All put operations must wait for async locks to complete,
                    // so it is safe to get acquired locks.
                    GridCacheMvccCandidate<K> explicitCand = entry.localOwner();

                    if (explicitCand != null) {
                        GridCacheVersion explicitVer = explicitCand.version();

                        if (!explicitVer.equals(xidVer) && explicitCand.threadId() == threadId) {
                            txEntry.explicitVersion(explicitVer);

                            if (explicitVer.isLess(minVer))
                                minVer = explicitVer;
                        }
                    }
                }

                if (root == null)
                    txMap.put(key, txEntry);

                if (log.isDebugEnabled())
                    log.debug("Created transaction entry: " + txEntry);

                return txEntry;
            }
            catch (GridCacheEntryRemovedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Got removed entry in transaction newEntry method (will retry): " + entry);

                entry = ctx.cache().entryEx(entry.key());
            }
        }
    }

    /**
     * @return {@code True} if updates should be batched up.
     */
    protected boolean isBatchUpdate() {
        return storeEnabled && (implicit() || ctx.config().getStore() != null && ctx.config().isBatchUpdateOnCommit());
    }

    /**
     * @return {@code True} if updates should be done individually.
     */
    protected boolean isSingleUpdate() {
        return storeEnabled && !implicit() && ctx.config().getStore() != null && !ctx.config().isBatchUpdateOnCommit();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridCacheTxLocalAdapter.class, this, super.toString());
    }

    /**
     * Post-lock closure alias.
     *
     * @param <T> Return type.
     */
    protected abstract class PLC1<T> extends PostLockClosure1<T> {
        // No-op.
    }

    /**
     * Post-lock closure alias.
     *
     * @param <T> Return type.
     */
    protected abstract class PLC2<T> extends PostLockClosure2<T> {
        // No-op.
    }

    /**
     * Post-lock closure alias.
     *
     * @param <T> Return type.
     */
    protected abstract class PMC<T> extends PostMissClosure<T> {
        // No-op.
    }

    /**
     * Async-callable alias.
     *
     * @param <R> Argument and return value type.
     */
    protected abstract class AC<R> extends AsyncCallable<R> {
        protected AC(R arg) {
            super(arg);
        }
    }

    /**
     * Post-lock closure.
     *
     * @param <T> Return type.
     */
    protected abstract class PostLockClosure1<T> extends GridClosure3<Boolean, T, Exception, T> {
        @Override public final T apply(Boolean locked, T t, Exception e) {
            try {
                if (e != null) {
                    setRollbackOnly();

                    throw e;
                }

                if (!locked) {
                    setRollbackOnly();

                    throw new GridCacheTxTimeoutException("Failed to acquire lock within provided timeout for " +
                        "transaction [timeout=" + lockTimeout() + ", tx=" + this + ']');
                }

                T r = postLock(t);

                // Commit implicit transactions.
                if (implicit())
                    commit();

                return r;
            }
            catch (Error ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (RuntimeException ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (Exception ex) {
                setRollbackOnly();

                throw new GridClosureException(ex);
            }
        }

        /**
         * Post lock callback.
         *
         * @param val Argument.
         * @return Future return value.
         * @throws GridException If operation failed.
         */
        protected abstract T postLock(T val) throws GridException;
    }

    /**
     * Post-lock closure.
     *
     * @param <T> Return type.
     */
    protected abstract class PostLockClosure2<T> extends GridClosure2<Boolean, Exception, GridFuture<T>> {
        @Override public final GridFuture<T> apply(Boolean locked, Exception e) {
            try {
                if (e != null) {
                    setRollbackOnly();

                    throw e;
                }

                if (!locked) {
                    setRollbackOnly();

                    throw new GridCacheTxTimeoutException("Failed to acquire lock within provided timeout for " +
                        "transaction [timeout=" + lockTimeout() + ", tx=" + this + ']');
                }

                return postLock();
            }
            catch (RuntimeException ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (Exception ex) {
                setRollbackOnly();

                throw new GridClosureException(ex);
            }
        }

        /**
         * Post lock callback.
         *
         * @return Future return value.
         * @throws GridException If operation failed.
         */
        protected abstract GridFuture<T> postLock() throws GridException;
    }

    /**
     * Post-lock closure.
     *
     * @param <T> Return type.
     */
    protected abstract class PostMissClosure<T> extends GridClosure2<T, Exception, GridFuture<T>> {
        @Override public final GridFuture<T> apply(T t, Exception e) {
            try {
                if (e != null)
                    throw e;

                return postMiss(t);
            }
            catch (RuntimeException ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (Exception ex) {
                setRollbackOnly();

                throw new GridClosureException(ex);
            }
        }

        /**
         * Post lock callback.
         *
         * @param t Post-miss parameter.
         * @return Future return value.
         * @throws GridException If operation failed.
         */
        protected abstract GridFuture<T> postMiss(T t) throws GridException;
    }

    /**
     * Post-lock closure.
     *
     * @param <T> Return type.
     */
    protected abstract class FinishClosure<T> extends GridClosure2<T, Exception, T> {
        /** {@inheritDoc} */
        @Override public final T apply(T t, Exception e) {
            try {
                if (e != null)
                    throw e;

                t = finish(t);

                // Commit implicit transactions.
                if (implicit())
                    commit();

                return t;
            }
            catch (RuntimeException ex) {
                setRollbackOnly();

                throw ex;
            }
            catch (Exception ex) {
                setRollbackOnly();

                throw new GridClosureException(ex);
            }
        }

        abstract T finish(T t) throws GridException;
    }

    /**
     * Async operation.
     *
     * @param <R> Return type.
     */
    protected abstract class AsyncCallable<R> implements Callable<R> {
        /** Op argument. */
        private final R arg;

        /**
         * @param arg Op argument.
         */
        protected AsyncCallable(R arg) {
            this.arg = arg;
        }

        @Override public final R call() throws GridException {
            try {
                return call(arg);
            }
            catch (GridException e) {
                setRollbackOnly();

                throw e;
            }
        }

        protected abstract R call(R arg) throws GridException;
    }
}
