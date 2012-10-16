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
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Transaction managed by cache ({@code 'Ex'} stands for external).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheTxEx<K, V> extends GridCacheTx, GridTimeoutObject {
    /**
     * Gets ID of additional node involved. For example, in DHT case, other node is
     * near node ID.
     *
     * @return Parent node IDs.
     */
    @Nullable public UUID otherNodeId();

    /**
     * @param entry Entry to check.
     * @return {@code True} if lock is owned.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public boolean ownsLock(GridCacheEntryEx<K, V> entry) throws GridCacheEntryRemovedException;

    /**
     * @param entry Entry to check.
     * @return {@code True} if lock is owned.
     */
    public boolean ownsLockUnsafe(GridCacheEntryEx<K, V> entry);

    /**
     * For Partitioned caches, this flag is {@code false} for remote DHT transactions
     * because serializability of transaction is enforced on primary node. All
     * other transaction types must enforce it.
     *
     * @return Enforce serializable flag.
     */
    public boolean enforceSerializable();

    /**
     * @return {@code True} if near transaction.
     */
    public boolean near();

    /**
     * @return {@code True} if transaction is local, {@code false} if it's remote.
     */
    public boolean local();

    /**
     * @return {@code True} if transaction is configured with synchronous commit flag.
     */
    public boolean syncCommit();

    /**
     * @return {@code True} if transaction is configured with synchronous rollback flag.
     */
    public boolean syncRollback();

    /**
     * @param key Key to check.
     * @return {@code True} if key is present.
     */
    public boolean hasReadKey(K key);

    /**
     * @param key Key to check.
     * @return {@code True} if key is present.
     */
    public boolean hasWriteKey(K key);

    /**
     * @return Read set.
     */
    public Set<K> readSet();

    /**
     * @return Write set.
     */
    public Set<K> writeSet();

    /**
     * @return All transaction entries.
     */
    public Collection<GridCacheTxEntry<K, V>> allEntries();

    /**
     * @return Write entries.
     */
    public Collection<GridCacheTxEntry<K, V>> writeEntries();

    /**
     * @return Read entries.
     */
    public Collection<GridCacheTxEntry<K, V>> readEntries();

    /**
     * Seals transaction for updates.
     */
    public void seal();

    /**
     * @param key Key for the entry.
     * @return Entry for the key (either from write set or read set).
     */
    @Nullable public GridCacheTxEntry<K, V> entry(K key);

    /**
     * @param failFast Fail-fast flag.
     * @param key Key to look up.
     * @param filter Filter to check.
     * @return Current value for the key within transaction.
     * @throws GridCacheFilterFailedException If filter failed and failFast is {@code true}.
     */
    @Nullable public V peek(boolean failFast, K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheFilterFailedException;

    /**
     * @return Start version.
     */
    public GridCacheVersion startVersion();

    /**
     * @return Transaction version.
     */
    public GridCacheVersion xidVersion();

    /**
     * @return Version created at commit time.
     */
    public GridCacheVersion commitVersion();

    /**
     * @param commitVer Commit version.
     * @return {@code True} if version was set.
     */
    public boolean commitVersion(GridCacheVersion commitVer);

    /**
     * @return End version (a.k.a. <tt>'tnc'</tt> or <tt>'transaction number counter'</tt>)
     *      assigned to this transaction at the end of write phase.
     */
    public GridCacheVersion endVersion();

    /**
     * Prepare state.
     *
     * @throws GridException If failed.
     */
    public void prepare() throws GridException;

    /**
     * Prepare stage.
     *
     * @return Future for prepare step.
     */
    public GridFuture<GridCacheTx> prepareAsync();

    /**
     * @param endVer End version (a.k.a. <tt>'tnc'</tt> or <tt>'transaction number counter'</tt>)
     *      assigned to this transaction at the end of write phase.
     */
    public void endVersion(GridCacheVersion endVer);

    /**
     * @param lsnr Transaction finish listener.
     */
    public void addFinishListener(GridInClosure<GridCacheTxEx<K, V>> lsnr);

    /**
     * @param state Transaction state.
     * @return {@code True} if transition was valid, {@code false} otherwise.
     */
    public boolean state(GridCacheTxState state);

    /**
     * @param invalidate Invalidate flag.
     */
    public void invalidate(boolean invalidate);

    /**
     * @param key Cache key.
     * @param cands Collection of lock candidates for that key.
     */
    public void addLocalCandidates(K key, Collection<GridCacheMvccCandidate<K>> cands);

    /**
     * @return All lock candidates.
     */
    public Map<K, Collection<GridCacheMvccCandidate<K>>> localCandidates();

    /**
     * Callback invoked whenever there is a lock that has been acquired
     * by this transaction for any of the participating entries.
     *
     * @param entry Cache entry.
     * @param owner Lock candidate that won ownership of the lock.
     * @return {@code True} if transaction cared about notification.
     */
    public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner);

    /**
     * @return {@code True} if transaction timed out.
     */
    public boolean timedOut();

    /**
     * @return {@code True} if transaction had completed successfully or unsuccessfully.
     */
    public boolean done();

    /**
     * @return {@code True} for EC transactions.
     */
    public boolean ec();

    /**
     * @return {@code True} for OPTIMISTIC transactions.
     */
    public boolean optimistic();

    /**
     * @return {@code True} for PESSIMISTIC transactions.
     */
    public boolean pessimistic();

    /**
     * @return {@code True} if read-committed.
     */
    public boolean readCommitted();

    /**
     * @return {@code True} if repeatable-read.
     */
    public boolean repeatableRead();

    /**
     * @return {@code True} if serializable.
     */
    public boolean serializable();

    /**
     * Checks whether given key has been removed within transaction.
     *
     * @param key Key to check.
     * @return {@code True} if key has been removed.
     */
    public boolean removed(K key);

    /**
     * Gets allowed remaining time for this transaction.
     *
     * @return Remaining time.
     * @throws GridCacheTxTimeoutException If transaction timed out.
     */
    public long remainingTime() throws GridCacheTxTimeoutException;

    /**
     * @return Alternate transaction versions.
     */
    public Collection<GridCacheVersion> alternateVersions();

    /**
     * @param base Base for committed versions.
     * @param committed Committed transactions relative to base.
     * @param rolledback Rolled back transactions relative to base.
     */
    public void completedVersions(GridCacheVersion base, Collection<GridCacheVersion> committed,
        Collection<GridCacheVersion> rolledback);

    /**
     * @return {@code True} if transaction has at least one internal entry.
     */
    public boolean internal();
}
