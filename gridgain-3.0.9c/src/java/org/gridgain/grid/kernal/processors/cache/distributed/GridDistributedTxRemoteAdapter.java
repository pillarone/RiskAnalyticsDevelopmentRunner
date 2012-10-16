// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction created by system implicitly on remote nodes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedTxRemoteAdapter<K, V> extends GridCacheTxAdapter<K, V>
    implements GridCacheTxRemoteEx<K, V> {
    /** Read set. */
    @GridToStringInclude
    protected Map<K, GridCacheTxEntry<K, V>> readMap;

    /** Write map. */
    @GridToStringInclude
    protected Map<K, GridCacheTxEntry<K, V>> writeMap;

    /** Remote thread ID. */
    @GridToStringInclude
    private long rmtThreadId;

    /** Map of lock candidates that need to be synced up. */
    @GridToStringInclude
    private Map<K, Collection<GridCacheMvccCandidate<K>>> cands;

    /** Explicit versions. */
    @GridToStringInclude
    private List<GridCacheVersion> explicitVers;

    /** Started flag. */
    @GridToStringInclude
    private boolean started;

    /** {@code True} only if all write entries are locked by this transaction. */
    @GridToStringInclude
    private AtomicBoolean commitAllowed = new AtomicBoolean(false);

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDistributedTxRemoteAdapter() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     * @param nodeId Node ID.
     * @param rmtThreadId Remote thread ID.
     * @param xidVer XID version.
     * @param commitVer Commit version.
     * @param concurrency Concurrency level (should be pessimistic).
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param timeout Timeout.
     */
    public GridDistributedTxRemoteAdapter(
        GridCacheContext<K, V> ctx,
        UUID nodeId,
        long rmtThreadId,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout) {
        super(ctx, nodeId, xidVer, ctx.versions().last(), Thread.currentThread().getId(), concurrency, isolation,
            timeout, invalidate, false, false);

        this.rmtThreadId = rmtThreadId;

        commitVersion(commitVer);

        // Must set started flag after concurrency and isolation.
        started = true;
    }

    /**
     * @return Checks if transaction has no entries.
     */
    public boolean empty() {
        return readMap.isEmpty() && writeMap.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean removed(K key) {
        GridCacheTxEntry e = writeMap.get(key);

        return e != null && e.op() == DELETE;
    }

    /** {@inheritDoc} */
    @Override public void invalidate(boolean invalidate) {
        this.invalidate = invalidate;
    }

    /** {@inheritDoc} */
    @Override public void seal() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public V peek(boolean failFast, K key,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridCacheFilterFailedException {
        assert false : "Method peek can only be called on user transaction: " + this;

        throw new IllegalStateException("Method peek can only be called on user transaction: " + this);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxEntry<K, V> entry(K key) {
        GridCacheTxEntry<K, V> e = writeMap == null ? null : writeMap.get(key);

        if (e == null)
            e = readMap == null ? null : readMap.get(key);

        return e;
    }

    /**
     * Clears entry from transaction as it never happened.
     *
     * @param key key to be removed.
     */
    public void clearEntry(K key) {
        readMap.remove(key);
        writeMap.remove(key);

        if (cands != null)
            cands.remove(key);
    }

    /** {@inheritDoc} */
    @Override public void addRemoteCandidates(
        Map<K, Collection<GridCacheMvccCandidate<K>>> cands,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        for (GridCacheTxEntry<K, V> txEntry : F.concat(false, writeEntries(), readEntries())) {
            while (true) {
                GridDistributedCacheEntry<K, V> entry = (GridDistributedCacheEntry<K, V>)txEntry.cached();

                try {
                    // Handle explicit locks.
                    GridCacheVersion base = txEntry.explicitVersion() != null ? txEntry.explicitVersion() : xidVer;

                    Collection<GridCacheMvccCandidate<K>> entryCands =
                        cands == null ? Collections.<GridCacheMvccCandidate<K>>emptyList() : cands.get(txEntry.key());

                    entry.addRemoteCandidates(entryCands, base, committedVers, rolledbackVers);

                    if (ec())
                        entry.recheck();

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    assert entry.obsoleteVersion() != null;

                    if (log.isDebugEnabled())
                        log.debug("Replacing obsolete entry in remote transaction [entry=" + entry +
                            ", tx=" + this + ']');

                    // Replace the entry.
                    txEntry.cached(ctx.cache().entryEx(txEntry.key()), entry.keyBytes());
                }
            }
        }
    }

    /**
     * @param baseVer Base version.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     */
    @Override public void doneRemote(GridCacheVersion baseVer, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        if (readMap != null && !readMap.isEmpty())
            for (GridCacheTxEntry<K, V> txEntry : readMap.values())
                doneRemote(txEntry, baseVer, committedVers, rolledbackVers);

        if (writeMap != null && !writeMap.isEmpty())
            for (GridCacheTxEntry<K, V> txEntry : writeMap.values())
                doneRemote(txEntry, baseVer, committedVers, rolledbackVers);
    }

    /**
     * Adds completed versions to an entry.
     *
     * @param txEntry Entry.
     * @param baseVer Base version for completed versions.
     * @param committedVers Completed versions relative to base version.
     * @param rolledbackVers Rolled back versions relative to base version.
     */
    private void doneRemote(
        GridCacheTxEntry<K, V> txEntry,
        GridCacheVersion baseVer,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        while (true) {
            GridDistributedCacheEntry<K, V> entry = (GridDistributedCacheEntry<K, V>)txEntry.cached();

            try {
                // Handle explicit locks.
                GridCacheVersion doneVer = txEntry.explicitVersion() != null ? txEntry.explicitVersion() : xidVer;

                entry.doneRemote(doneVer, baseVer, committedVers, rolledbackVers);

                break;
            }
            catch (GridCacheEntryRemovedException ignored) {
                assert entry.obsoleteVersion() != null;

                if (log.isDebugEnabled())
                    log.debug("Replacing obsolete entry in remote transaction [entry=" + entry + ", tx=" + this + ']');

                // Replace the entry.
                txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        try {
            if (hasWriteKey(entry.key())) {
                commitIfLocked();

                return true;
            }
        }
        catch (GridException e) {
            U.error(log, "Failed to commit remote transaction: " + this, e);
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isStarted() {
        return started;
    }

    /**
     * @return Remote node thread ID.
     */
    long getRemoteThreadId() {
        return rmtThreadId;
    }

    /**
     * @param key Key to add to read set.
     * @param keyBytes Key bytes.
     */
    public void addRead(K key, byte[] keyBytes) {
        checkInternal(key);

        GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(ctx, this, READ, null, 0,
            ctx.cache().entryEx(key));

        txEntry.keyBytes(keyBytes);

        readMap.put(key, txEntry);
    }

    /**
     * @param key Key to add to write set.
     * @param keyBytes Key bytes.
     */
    public void addWrite(K key, byte[] keyBytes) {
        checkInternal(key);

        GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(ctx, this, UPDATE, null, 0,
            ctx.cache().entryEx(key));

        txEntry.keyBytes(keyBytes);

        writeMap.put(key, txEntry);
    }

    /**
     * @param e Transaction entry to set.
     * @return {@code True} if value was set.
     */
    @Override public boolean setWriteValue(GridCacheTxEntry<K, V> e) {
        checkInternal(e.key());

        GridCacheTxEntry<K, V> entry = writeMap.get(e.key());

        if (entry == null) {
            GridCacheTxEntry<K, V> rmv = readMap.remove(e.key());

            if (rmv != null) {
                e.cached(rmv.cached(), rmv.keyBytes());

                writeMap.put(e.key(), e);
            }
            // If lock is explicit.
            else {
                e.cached(ctx.cache().entryEx(e.key()), null);

                // explicit lock.
                writeMap.put(e.key(), e);
            }
        }
        else {
            // Copy values.
            entry.value(e.value());
            entry.valueBytes(e.valueBytes());
            entry.op(e.op());
            entry.ttl(e.ttl());
            entry.expireTime(e.expireTime());
            entry.explicitVersion(e.explicitVersion());
        }

        addExplicit(e);

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean hasReadKey(K key) {
        return readMap.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public boolean hasWriteKey(K key) {
        return writeMap.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> prepareAsync() {
        assert false;
        return null;
    }

    /** {@inheritDoc} */
    @Override public Set<K> readSet() {
        return readMap.keySet();
    }

    /** {@inheritDoc} */
    @Override public Set<K> writeSet() {
        return writeMap.keySet();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> allEntries() {
        return F.concat(false, writeEntries(), readEntries());
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> writeEntries() {
        return writeMap.values();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheTxEntry<K, V>> readEntries() {
        return readMap.values();
    }

    /**
     * Prepare phase.
     *
     * @throws GridException If prepare failed.
     */
    @Override public void prepare() throws GridException {
        // If another thread is doing prepare or rollback.
        if (!state(PREPARING)) {
            if (log.isDebugEnabled())
                log.debug("Invalid transaction state for prepare: " + this);

            return;
        }

        try {
            ctx.tm().prepareTx(this);

            state(PREPARED);
        }
        catch (GridException e) {
            setRollbackOnly();

            throw e;
        }
    }

    /**
     * @return {@code True} if transaction should record events.
     */
    protected boolean isNotifyEvent() {
        return true;
    }

    /**
     * @throws GridException If commit failed.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void commitIfLocked() throws GridException {
        // Special handling for eventually consistent transactions.
        if (ec()) {
            commitEC();

            return;
        }

        if (state() == COMMITTING) {
            for (GridCacheTxEntry<K, V> txEntry : writeMap.values()) {
                while (true) {
                    GridCacheEntryEx<K, V> cacheEntry = txEntry.cached();

                    try {
                        GridCacheVersion ver = txEntry.explicitVersion() != null ? txEntry.explicitVersion() : xidVer;

                        // If locks haven't been acquired yet, keep waiting.
                        if (!cacheEntry.lockedBy(ver)) {
                            if (log.isDebugEnabled())
                                log.debug("Transaction does not own lock for entry (will wait) [entry=" + cacheEntry +
                                    ", tx=" + this + ']');

                            return;
                        }

                        break; // While.
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry while committing (will retry): " + txEntry);

                        txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                    }
                }
            }

            // Only one thread gets to commit.
            if (commitAllowed.compareAndSet(false, true)) {
                GridException err = null;

                if (!F.isEmpty(writeMap)) {
                    // Register this transaction as completed prior to write-phase to
                    // ensure proper lock ordering for removed entries.
                    ctx.tm().addCommittedTx(this);

                    // Node that for near transactions we grab all entries.
                    for (GridCacheTxEntry<K, V> txEntry : (near() ? allEntries() : writeEntries())) {
                        try {
                            while (true) {
                                try {
                                    GridCacheEntryEx<K, V> cached = txEntry.cached();

                                    if (cached == null)
                                        txEntry.cached(cached = ctx.cache().entryEx(txEntry.key()), null);

                                    GridNearCacheEntry<K, V> nearCached = null;

                                    if (ctx.isDht())
                                        nearCached = ctx.dht().near().peekExx(txEntry.key());

                                    if (txEntry.op() == CREATE || txEntry.op() == UPDATE) {
                                        // Invalidate only for near nodes (backups cannot be invalidated).
                                        if (isInvalidate() && !ctx.isDht())
                                            cached.innerRemove(this, nodeId, nodeId, false, isNotifyEvent(),
                                                txEntry.filters());
                                        else {
                                            cached.innerSet(this, nodeId, nodeId, txEntry.value(), txEntry.valueBytes(),
                                                false, txEntry.expireTime(), txEntry.ttl(), isNotifyEvent(),
                                                    txEntry.filters());

                                            // Keep near entry up to date.
                                            if (nearCached != null)
                                                nearCached.updateOrEvict(xidVer, cached.rawGet(), cached.valueBytes(),
                                                    cached.expireTime(), cached.ttl(), nodeId);
                                        }
                                    }
                                    else if (txEntry.op() == DELETE) {
                                        cached.innerRemove(this, nodeId, nodeId, false, isNotifyEvent(),
                                            txEntry.filters());

                                        // Keep near entry up to date.
                                        if (nearCached != null)
                                            nearCached.updateOrEvict(xidVer, null, null, 0, 0, nodeId);
                                    }
                                    else if (txEntry.op() == READ) {
                                        assert near();

                                        if (log.isDebugEnabled())
                                            log.debug("Ignoring READ entry when committing: " + txEntry);
                                    }
                                    else if (log.isDebugEnabled())
                                        log.debug("Ignoring NOOP entry when remotely committing: " + txEntry);

                                    // Assert after setting values as we want to make sure
                                    // that if we replaced removed entries.
                                    assert
                                        txEntry.op() == READ ||
                                            // If candidate is not there, then lock was explicit
                                            // and we simply allow the commit to proceed.
                                            !cached.hasLockCandidateUnsafe(xidVer) || cached.lockedByUnsafe(xidVer) :
                                        "Transaction does not own lock for commit [entry=" + cached +
                                            ", tx=" + this + ']';

                                    // Break out of while loop.
                                    break;
                                }
                                catch (GridCacheEntryRemovedException ignored) {
                                    if (log.isDebugEnabled())
                                        log.debug("Attempting to commit a removed entry (will retry): " + txEntry);

                                    // Renew cached entry.
                                    txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                                }
                            }
                        }
                        catch (Throwable ex) {
                            state(UNKNOWN);

                            // In case of error, we still make the best effort to commit,
                            // as there is no way to rollback at this point.
                            err = ex instanceof GridException ? (GridException)ex :
                                new GridException("Commit produced a runtime exception: " + this, ex);
                        }
                    }
                }

                if (err != null) {
                    state(UNKNOWN);

                    throw err;
                }

                ctx.tm().commitTx(this);

                state(COMMITTED);
            }
        }
    }

    /**
     * Commit eventually consistent transactions.
     *
     * @throws GridException If commit failed.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void commitEC() throws GridException {
        if (state() == COMMITTING) {
            GridException err = null;

            if (!F.isEmpty(writeMap)) {
                for (GridCacheTxEntry<K, V> txEntry : writeMap.values()) {
                    GridCacheVersion ver = txEntry.explicitVersion() != null ? txEntry.explicitVersion() : xidVer;

                    try {
                        while (true) {
                            GridCacheEntryEx<K, V> cacheEntry = txEntry.cached();

                            try {
                                if (cacheEntry == null)
                                    cacheEntry = ctx.cache().peekEx(txEntry.key());

                                // Commit only locked entries. If some entry is not locked, then
                                // we will arrive here again whenever lock will be acquired.
                                if (cacheEntry != null) {
                                    if (cacheEntry.lockedBy(ver)) {
                                        if (txEntry.markCommitting()) {
                                            if (txEntry.op() == CREATE || txEntry.op() == UPDATE) {
                                                if (isInvalidate())
                                                    cacheEntry.innerSet(this, nodeId, nodeId, null, null,
                                                        false, txEntry.expireTime(), txEntry.ttl(), true,
                                                        txEntry.filters());
                                                else
                                                    cacheEntry.innerSet(this, nodeId, nodeId, txEntry.value(),
                                                        txEntry.valueBytes(), false, txEntry.expireTime(),
                                                        txEntry.ttl(), true, txEntry.filters());
                                            }
                                            else {
                                                // EC transactions cannot have NOOP entries.
                                                assert txEntry.op() == DELETE;

                                                cacheEntry.innerRemove(this, nodeId, nodeId, false, true,
                                                    txEntry.filters());
                                            }

                                            // Remove lock to avoid double commit.
                                            cacheEntry.removeLock(ver);
                                        }
                                    }
                                }

                                break;
                            }
                            // Possible if entry cached within transaction is obsolete.
                            catch (GridCacheEntryRemovedException ignored) {
                                if (log.isDebugEnabled())
                                    log.debug("Got removed entry in commitEC method (will retry): " + txEntry);

                                txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                            }
                        }
                    }
                    catch (Throwable ex) {
                        // In case of error, we still make the best effort to commit,
                        // as there is no way to rollback at this point.
                        err = ex instanceof GridException ? (GridException)ex :
                            new GridException("Commit produced a runtime exception: " + this, ex);
                    }
                }

                for (GridCacheTxEntry<K, V> txEntry : writeMap.values()) {
                    GridCacheVersion ver = txEntry.explicitVersion() == null ? xidVer : txEntry.explicitVersion();

                    while (true) {
                        GridCacheEntryEx<K, V> cacheEntry = txEntry.cached();

                        try {
                            if (cacheEntry.candidate(ver) != null) {
                                if (log.isDebugEnabled())
                                    log.debug("EC transaction cannot be complete yet [txId=" + xidVer +
                                        ", entry=" + txEntry + ']');

                                return;
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry while committing EC (will retry): " + txEntry);

                            txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                        }
                    }
                }
            }

            if (commitAllowed.compareAndSet(false, true)) {
                ctx.tm().commitTx(this);

                if (err != null) {
                    state(UNKNOWN);

                    throw err;
                }

                state(COMMITTED);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void commit() throws GridException {
        if (!state(COMMITTING)) {
            GridCacheTxState state = state();

            // If other thread is doing commit, then no-op.
            if (state == COMMITTING || state == COMMITTED)
                return;

            setRollbackOnly();

            throw new GridException("Invalid transaction state for commit [state=" + state + ", tx=" + this + ']');
        }

        commitIfLocked();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> commitAsync() {
        assert false : "Asynchronous commit is not needed for remote replicated transactions: " + this;

        // Never reached.
        return null;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass"})
    @Override public void rollback() {
        try {
            // Note that we don't evict near entries here -
            // they will be deleted by their corrsesponding transactions.
            if (state(ROLLING_BACK)) {
                ctx.tm().rollbackTx(this);

                state(ROLLED_BACK);
            }
        }
        catch (RuntimeException e) {
            state(UNKNOWN);

            throw e;
        }
        catch (Error e) {
            state(UNKNOWN);

            throw e;
        }
    }

    /** {@inheritDoc} */
    @Override public void addLocalCandidates(K key, Collection<GridCacheMvccCandidate<K>> cands) {
        if (this.cands == null)
            this.cands = new HashMap<K, Collection<GridCacheMvccCandidate<K>>>();

        this.cands.put(key, cands);
    }

    /** {@inheritDoc} */
    @Override public Map<K, Collection<GridCacheMvccCandidate<K>>> localCandidates() {
        return cands == null ?
            Collections.<K, Collection<GridCacheMvccCandidate<K>>>emptyMap() :
            Collections.unmodifiableMap(cands);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheVersion> alternateVersions() {
        return explicitVers == null ? Collections.<GridCacheVersion>emptyList() : explicitVers;
    }

    /**
     * Adds explicit version if there is one.
     *
     * @param e Transaction entry.
     */
    protected void addExplicit(GridCacheTxEntry<K, V> e) {
        if (e.explicitVersion() != null) {
            if (explicitVers == null)
                explicitVers = new LinkedList<GridCacheVersion>();

            if (!explicitVers.contains(e.explicitVersion())) {
                explicitVers.add(e.explicitVersion());

                if (log.isDebugEnabled())
                    log.debug("Added explicit version to transaction [explicitVer=" + e.explicitVersion() +
                        ", tx=" + this + ']');

                // Register alternate version with TM.
                ctx.tm().addAlternateVersion(e.explicitVersion(), this);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDistributedTxRemoteAdapter.class, this, "super", super.toString());
    }
}
