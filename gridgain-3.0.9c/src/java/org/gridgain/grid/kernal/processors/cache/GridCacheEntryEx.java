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
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Internal API for cache entry ({@code 'Ex'} stands for extended).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheEntryEx<K, V> extends GridMetadataAware {
    /**
     * @return Partition ID.
     */
    public int partition();

    /**
     * @return Key.
     */
    public K key();

    /**
     *
     * @return Value.
     */
    public V rawGet();

    /**
     *
     * @param val New value.
     * @param ttl Time to live.
     * @return Old value.
     */
    public V rawPut(V val, long ttl);

    /**
     * Wraps this map entry into cache entry.

     * @param prjAware {@code true} if entry should inherit projection properties.
     * @return Wrapped entry.
     */
    public GridCacheEntry<K, V> wrap(boolean prjAware);

    /**
     * @return Not-null version if entry is obsolete.
     */
    public GridCacheVersion obsoleteVersion();

    /**
     * @return {@code True} if entry is obsolete.
     */
    public boolean obsolete();

    /**
     * @param exclude Obsolete version to ignore.
     * @return {@code True} if obsolete version is not {@code null} and is not the
     *      passed in version.
     */
    public boolean obsolete(GridCacheVersion exclude);

    /**
     * @return Entry info.
     */
    public GridCacheEntryInfo<K, V> info();

    /**
     * Invalidates this entry.
     *
     * @param curVer Current version to match ({@code null} means always match).
     * @param newVer New version to set.
     * @return {@code true} if entry is obsolete.
     * @throws GridException If swap could not be released.
     */
    public boolean invalidate(@Nullable GridCacheVersion curVer, GridCacheVersion newVer) throws GridException;

    /**
     * Invalidates this entry if it passes given filter.
     *
     * @param filter Optional filter that entry should pass before invalidation.
     * @return {@code true} if entry was actually invalidated.
     * @throws GridException If swap could not be released.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean invalidate(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridException;

    /**
     * Optimizes the size of this entry.
     *
     * @param filter Optional filter that entry should pass before invalidation.
     * @throws GridCacheEntryRemovedException If entry was removed.
     * @throws GridException If operation failed.
     * @return {@code true} if entry was not being used and could be removed.
     */
    public boolean compact(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridException;

    /**
     * @param swap Swap flag.
     * @param obsoleteVer Version for eviction.
     * @param filter Optional filter.
     * @return {@code True} if entry could be evicted.
     * @throws GridException In case of error.
     */
    public boolean evictInternal(boolean swap, GridCacheVersion obsoleteVer,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * Checks if entry is new.
     *
     * @return {@code True} if entry is new.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean isNew() throws GridCacheEntryRemovedException;

    /**
     * @return Start version.
     */
    public GridCacheVersion startVersion();

    /**
     * @return Checks if value is valid.
     */
    public boolean valid();

    /**
     * @return {@code True} if partition is in valid.
     */
    public boolean partitionValid();

    /**
     * @param tx Ongoing transaction (possibly null).
     * @param readSwap Flag indicating whether to check swap memory.
     * @param readThrough Flag indicating whether to read through.
     * @param failFast If {@code true}, then throw {@link GridCacheFilterFailedException} if
     *      filter didn't pass.
     * @param filter Filter to check prior to getting the value. Note that filter check
     *      together with getting the value is an atomic operation.
     * @param updateMetrics If {@code true} then metrics should be updated.
     * @param evt Flag to signal event notification.
     * @return Cached value.
     * @throws GridException If loading value failed.
     * @throws GridCacheEntryRemovedException If entry was removed.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @Nullable public V innerGet(@Nullable GridCacheTx tx, boolean readSwap, boolean readThrough, boolean failFast,
        boolean updateMetrics, boolean evt, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException,
        GridCacheEntryRemovedException, GridCacheFilterFailedException;

    /**
     * Reloads entry from underlying storage.
     *
     * @param filter Filter for entries.
     * @return Reloaded value.
     * @throws GridException If reload failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable
    public V innerReload(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException,
        GridCacheEntryRemovedException;

    /**
     * @param tx Cache transaction.
     * @param evtNodeId ID of node responsible for this change.
     * @param affNodeId Partitioned node iD.
     * @param val Value to set.
     * @param valBytes Value bytes to set.
     * @param writeThrough If {@code true} then persist to storage.
     * @param expireTime Expiration time.
     * @param ttl Time to live.
     * @param evt Flag to signal event notification.
     * @param filter Filter.
     * @return Cached value.
     * @throws GridException If storing value failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public T2<Boolean, V> innerSet(GridCacheTxEx<K, V> tx, UUID evtNodeId, UUID affNodeId, V val,
        byte[] valBytes, boolean writeThrough, long expireTime, long ttl, boolean evt,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException, GridCacheEntryRemovedException;

    /**
     * @return Old value.
     * @param tx Cache transaction.
     * @param evtNodeId ID of node responsible for this change.
     * @param affNodeId Partitioned node iD.
     * @param writeThrough If {@code true}, persist to the storage.
     * @param evt Flag to signal event notification.
     * @param filter Filter.
     * @throws GridException If remove failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public T2<Boolean, V> innerRemove(GridCacheTxEx<K, V> tx, UUID evtNodeId, UUID affNodeId,
        boolean writeThrough, boolean evt, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException,
        GridCacheEntryRemovedException;

    /**
     * Marks entry as obsolete and, if possible or required, removes it
     * from swap storage.
     *
     * @param ver Obsolete version.
     * @param swap If {@code true} then remove from swap.
     * @param filter Optional entry filter.
     * @throws GridException If failed to remove from swap.
     * @return {@code True} if entry was not being used, passed the filter and could be removed.
     */
    public boolean clear(GridCacheVersion ver, boolean swap,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException;

    /**
     * Marks entry as obsolete and, if possible or required, removes it
     * from swap storage.
     *
     * @param ver Obsolete version.
     * @throws GridException If failed to remove from swap.
     * @return {@code True} if entry was not being used and could be removed.
     */
    public boolean clearIfNew(GridCacheVersion ver) throws GridException;

    /**
     * This locks is called by transaction manager during prepare step
     * for optimistic transactions.
     *
     * @param tx Cache transaction.
     * @param timeout Timeout for lock acquisition.
     * @return {@code True} if lock was acquired, {@code false} otherwise.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     * @throws GridDistributedLockCancelledException If lock has been cancelled.
     */
    public boolean tmLock(GridCacheTxEx<K, V> tx, long timeout) throws GridCacheEntryRemovedException,
        GridDistributedLockCancelledException;

    /**
     * Unlocks acquired lock.
     *
     * @param tx Cache transaction.
     * @throws GridCacheEntryRemovedException If this entry has been removed from cache.
     */
    public abstract void txUnlock(GridCacheTxEx<K, V> tx) throws GridCacheEntryRemovedException;

    /**
     * @param ver Removes lock.
     * @return {@code True} If lock has been removed.
     * @throws GridCacheEntryRemovedException If this entry has been removed from cache.
     */
    public boolean removeLock(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Sets obsolete flag if possible.
     *
     * @param ver Version to set as obsolete.
     * @return {@code True} if entry is obsolete, {@code false} if
     *      entry is still used by other threads or nodes.
     */
    public boolean markObsolete(GridCacheVersion ver);

    /**
     * Sets obsolete flag if possible.
     *
     * @param ver Version to set as obsolete.
     * @param clear If {@code true}, then value will be cleared as well.
     * @return {@code True} if entry is obsolete, {@code false} if
     *      entry is still used by other threads or nodes.
     */
    public boolean markObsolete(GridCacheVersion ver, boolean clear);

    /**
     * @param filter Entry filter.
     * @return {@code True} if entry is visitable.
     */
    public boolean visitable(GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @return Key bytes.
     */
    public byte[] keyBytes();

    /**
     * @return Key bytes.
     * @throws GridException If marshalling failed.
     */
    public byte[] getOrMarshalKeyBytes() throws GridException;

    /**
     * @return Version.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public GridCacheVersion version() throws GridCacheEntryRemovedException;

    /**
     * Peeks into entry without loading value or updating statistics.
     *
     * @param mode Peek mode.
     * @param filter Optional filter.
     * @return Value.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable public V peek(GridCachePeekMode mode, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException;

    /**
     * Peeks into entry without loading value or updating statistics.
     *
     * @param modes Peek modes.
     * @param filter Optional filter.
     * @return Value.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable public V peek(Collection<GridCachePeekMode> modes, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException;

    /**
     * Peeks into entry without loading value or updating statistics.
     *
     * @param mode Peek mode.
     * @param filter Optional filter.
     * @return Value.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @throws GridCacheFilterFailedException If {@code failFast} is {@code true} and
     *      filter didn't pass.
     */
    @Nullable public V peekFailFast(GridCachePeekMode mode, GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridCacheEntryRemovedException, GridCacheFilterFailedException;

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
    @Nullable public V peek0(boolean failFast, GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheTxEx<K, V> tx)
        throws GridCacheEntryRemovedException, GridCacheFilterFailedException, GridException;

    /**
     * Sets new value if current version is <tt>0</tt>
     *
     * @param val New value.
     * @param valBytes Value bytes.
     * @param ver Version to use.
     * @param ttl Time to live.
     * @param expireTime Expiration time.
     * @param metrics Metrics.
     * @return {@code True} if initial value was set.
     * @throws GridException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @SuppressWarnings({"unchecked"})
    public boolean initialValue(V val, byte[] valBytes, GridCacheVersion ver, long ttl, long expireTime,
        GridCacheMetricsAdapter metrics) throws GridException, GridCacheEntryRemovedException;

    /**
     * Sets new value if current version is <tt>0</tt> using swap entry data.
     * Note that this method does not update cache index.
     *
     * @param key Key.
     * @param unswapped Swap entry to set entry state from.
     * @return {@code True} if  initial value was set.
     * @throws GridException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @SuppressWarnings({"unchecked"})
    public boolean initialValue(K key, GridCacheSwapEntry<V> unswapped)
        throws GridException, GridCacheEntryRemovedException;

    /**
     * Sets new value if passed in version matches the current version
     * (used for read-through only).
     *
     * @param val New value.
     * @param curVer Version to match or {@code null} if match is not required.
     * @param newVer Version to set.
     * @return {@code True} if versioned matched.
     * @throws GridException If index could not be updated.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean versionedValue(V val, GridCacheVersion curVer, GridCacheVersion newVer)
        throws GridException, GridCacheEntryRemovedException;

    /**
     * Wakes up all threads waiting on the mux.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public void wakeUp() throws GridCacheEntryRemovedException;

    /**
     * Checks if the candidate is either owner or pending.
     *
     * @param ver Candidate version to check.
     * @return {@code True} if the candidate is either owner or pending.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean hasLockCandidate(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Checks if the candidate is either owner or pending.
     *
     * @param threadId ThreadId.
     * @return {@code True} if the candidate is either owner or pending.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean hasLockCandidate(long threadId) throws GridCacheEntryRemovedException;

    /**
     * @param exclude Exclude versions.
     * @return {@code True} if lock is owned by any thread or node.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByAny(GridCacheVersion... exclude) throws GridCacheEntryRemovedException;

    /**
     * @return {@code True} if lock is owned by current thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread() throws GridCacheEntryRemovedException;

    /**
     * @param exclude Version to exclude from check.
     * @return {@code True} if lock is owned by current thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread(GridCacheVersion exclude) throws GridCacheEntryRemovedException;

    /**
     * @param lockId Lock ID to check.
     * @return {@code True} if lock is owned by candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedLocally(UUID lockId) throws GridCacheEntryRemovedException;

    /**
     * @param threadId Thread ID to check.
     * @param exclude Version to exclude from check.
     * @return {@code True} if lock is owned by given thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread(long threadId, GridCacheVersion exclude) throws GridCacheEntryRemovedException;

    /**
     * @param threadId Thread ID to check.
     * @return {@code True} if lock is owned by given thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread(long threadId) throws GridCacheEntryRemovedException;

    /**
     * @param ver Version to check for ownership.
     * @return {@code True} if owner has the specified version.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedBy(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Will not fail for removed entries.
     *
     * @param threadId Thread ID to check.
     * @return {@code True} if lock is owned by given thread.
     */
    public boolean lockedByThreadUnsafe(long threadId);

    /**
     * @param ver Version to check for ownership.
     * @return {@code True} if owner has the specified version.
     */
    public boolean lockedByUnsafe(GridCacheVersion ver);

    /**
     * @param lockId Lock ID to check.
     * @return {@code True} if lock is owned by candidate.
     */
    public boolean lockedLocallyUnsafe(UUID lockId);

    /**
     * @param ver Lock version to check.
     * @return {@code True} if has candidate with given lock ID.
     */
    public boolean hasLockCandidateUnsafe(GridCacheVersion ver);

    /**
     * @param threadId Thread ID.
     * @return Local candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate<K> localCandidate(long threadId) throws GridCacheEntryRemovedException;

    /**
     * Gets all local candidates.
     *
     * @param exclude Versions to exclude from check.
     * @return All local candidates.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public Collection<GridCacheMvccCandidate<K>> localCandidates(@Nullable GridCacheVersion... exclude)
        throws GridCacheEntryRemovedException;

    /**
     * Gets all remote versions.
     *
     * @param exclude Exclude version.
     * @return All remote versions minus the excluded ones, if any.
     */
    public Collection<GridCacheMvccCandidate<K>> remoteMvccSnapshot(GridCacheVersion... exclude);

    /**
     * Gets lock candidate for given lock ID.
     *
     * @param ver Lock version.
     * @return Lock candidate for given ID.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate<K> candidate(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @return Candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate<K> candidate(UUID nodeId, long threadId)
        throws GridCacheEntryRemovedException;

    /**
     * @return Local owner.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate<K> localOwner() throws GridCacheEntryRemovedException;

    /**
     * @return Metrics.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public GridCacheMetrics metrics() throws GridCacheEntryRemovedException;

    /**
     * @param keyBytes Key bytes.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public void keyBytes(byte[] keyBytes) throws GridCacheEntryRemovedException;

    /**
     * @return Value bytes.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public byte[] valueBytes() throws GridCacheEntryRemovedException;

    /**
     * Gets cached serialized value bytes.
     *
     * @param ver Version for which to get value bytes.
     * @return Serialized value bytes.
     * @throws GridException If serialization failed.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable
    public byte[] valueBytes(GridCacheVersion ver) throws GridException, GridCacheEntryRemovedException;

    /**
     * @return Expiration time.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public long expireTime() throws GridCacheEntryRemovedException;

    /**
     * @return Time to live.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public long ttl() throws GridCacheEntryRemovedException;

    /**
     * @param ttl Time to live.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public void ttl(long ttl) throws GridCacheEntryRemovedException;

    /**
     * @throws GridException If failed to read from swap storage.
     */
    public void unswap() throws GridException;

    /**
     * @return Value class loader.
     */
    @Nullable public ClassLoader valueClassLoader();

    /**
     * @return Key class loader.
     */
    @Nullable public ClassLoader keyClassLoader();
}
