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
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction created by system implicitly on remote nodes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxRemote<K, V> extends GridDistributedTxRemoteAdapter<K, V> {
    /** Evicted keys. */
    private Collection<K> evicted = new LinkedList<K>();

    /** Evicted keys. */
    private Collection<byte[]> evictedBytes = new LinkedList<byte[]>();

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxRemote() {
        // No-op.
    }

    /**
     * @param ldr Class loader.
     * @param nodeId Node ID.
     * @param rmtThreadId Remote thread ID.
     * @param xidVer XID version.
     * @param commitVer Commit version.
     * @param concurrency Concurrency level (should be pessimistic).
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param timeout Timeout.
     * @param writeEntries Write entries.
     * @param ctx Cache registry.
     * @throws GridException If unmarshalling failed.
     */
    public GridNearTxRemote(
        ClassLoader ldr,
        UUID nodeId,
        long rmtThreadId,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout,
        Collection<GridCacheTxEntry<K, V>> writeEntries,
        GridCacheContext<K, V> ctx) throws GridException {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        readMap = Collections.emptyMap();

        writeMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(
            writeEntries != null ? writeEntries.size() : 0, 1.0f);

        if (writeEntries != null)
            for (GridCacheTxEntry<K, V> entry : writeEntries) {
                entry.unmarshal(ctx, ldr);

                addEntry(entry);
            }
    }

    /**
     * This constructor is meant for pessimistic transactions.
     *
     * @param nodeId Node ID.
     * @param rmtThreadId Remote thread ID.
     * @param xidVer XID version.
     * @param commitVer Commit version.
     * @param concurrency Concurrency level (should be pessimistic).
     * @param isolation Transaction isolation.
     * @param invalidate Invalidate flag.
     * @param timeout Timeout.
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param val Value.
     * @param valBytes Value bytes.
     * @param ctx Cache registry.
     * @throws GridException If failed.
     */
    public GridNearTxRemote(
        UUID nodeId,
        long rmtThreadId,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout,
        K key,
        byte[] keyBytes,
        V val,
        byte[] valBytes,
        GridCacheContext<K, V> ctx) throws GridException {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        readMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(1, 1.0f);
        writeMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(1, 1.0f);

        addEntry(key, keyBytes, val, valBytes);
    }

    /** {@inheritDoc} */
    @Override public boolean near() {
        return true;
    }

    /**
     * @return Evicted keys.
     */
    public Collection<K> evicted() {
        return evicted;
    }

    /**
     * @return Evicted bytes.
     */
    public Collection<byte[]> evictedBytes() {
        return evictedBytes;
    }

    /**
     * @return {@code True} if transaction contains a valid list of evicted bytes.
     */
    public boolean hasEvictedBytes() {
        // Sizes of byte list and key list can differ if node crashed and transaction moved
        // from local to remote. This check is for safety, as nothing bad will happen if
        // some near keys will remain in remote node readers list.
        return !evictedBytes.isEmpty() && evictedBytes.size() == evicted.size();
    }

    /**
     * @param entry Entry to enlist.
     * @throws GridException If failed.
     * @return {@code True} if entry was enlisted.
     */
    private boolean addEntry(GridCacheTxEntry<K, V> entry) throws GridException {
        checkInternal(entry.key());

        GridNearCacheEntry<K, V> cached = ctx.near().peekExx(entry.key());

        if (cached == null) {
            evicted.add(entry.key());

            if (entry.keyBytes() != null)
                evictedBytes.add(entry.keyBytes());

            return false;
        }
        else {
            cached.unswap();

            try {
                if (cached.peek(GLOBAL, CU.<K, V>empty()) == null && cached.evictInternal(false, xidVer, null)) {
                    evicted.add(entry.key());

                    if (entry.keyBytes() != null)
                        evictedBytes.add(entry.keyBytes());

                    return false;
                }
                else {
                    // Initialize cache entry.
                    entry.cached(cached, entry.keyBytes());

                    writeMap.put(entry.key(), entry);

                    addExplicit(entry);

                    return true;
                }
            }
            catch (GridCacheEntryRemovedException ignore) {
                evicted.add(entry.key());

                if (entry.keyBytes() != null)
                    evictedBytes.add(entry.keyBytes());

                if (log.isDebugEnabled())
                    log.debug("Got removed entry when adding to remote transaction (will ignore): " + cached);

                return false;
            }
        }
    }

    /**
     * @param key Key to add to write set.
     * @param keyBytes Key bytes.
     * @param val Value.
     * @param valBytes Value bytes.
     */
    void addWrite(K key, byte[] keyBytes, V val, byte[] valBytes) {
        checkInternal(key);

        GridNearCacheEntry<K, V> cached = ctx.near().entryExx(key);

        GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(ctx, this, NOOP, val, 0, cached);

        txEntry.keyBytes(keyBytes);
        txEntry.valueBytes(valBytes);

        writeMap.put(key, txEntry);
    }

    /**
     * @param key Key to add to read set.
     * @param keyBytes Key bytes.
     * @param val Value.
     * @param valBytes Value bytes.
     * @throws GridException If failed.
     * @return {@code True} if entry has been enlisted.
     */
    private boolean addEntry(K key, byte[] keyBytes, V val, byte[] valBytes) throws GridException {
        checkInternal(key);

        GridNearCacheEntry<K, V> cached = ctx.near().peekExx(key);

        try {
            if (cached == null) {
                evicted.add(key);

                if (keyBytes != null)
                    evictedBytes.add(keyBytes);

                return false;
            }
            else {
                cached.unswap();

                if (cached.peek(GLOBAL, CU.<K, V>empty()) == null && cached.evictInternal(false, xidVer, null)) {
                    evicted.add(key);

                    if (keyBytes != null)
                        evictedBytes.add(keyBytes);

                    return false;
                }
                else {
                    GridCacheTxEntry<K, V> txEntry = new GridCacheTxEntry<K, V>(ctx, this, NOOP, val, 0, cached);

                    txEntry.keyBytes(keyBytes);
                    txEntry.valueBytes(valBytes);

                    writeMap.put(key, txEntry);

                    return true;
                }
            }
        }
        catch (GridCacheEntryRemovedException ignore) {
            evicted.add(key);

            if (keyBytes != null)
                evictedBytes.add(keyBytes);

            if (log.isDebugEnabled())
                log.debug("Got removed entry when adding reads to remote transaction (will ignore): " + cached);

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridNearTxRemote.class, this, "super", super.toString());
    }
}
