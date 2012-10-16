// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Transaction created by system implicitly on remote nodes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedTxRemote<K, V> extends GridDistributedTxRemoteAdapter<K, V> {
    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridReplicatedTxRemote() {
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
     * @param readEntries Read entries.
     * @param writeEntries Write entries.
     * @param ctx Cache registry.
     * @throws GridException If unmarshalling failed.
     */
    public GridReplicatedTxRemote(
        ClassLoader ldr,
        UUID nodeId,
        long rmtThreadId,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        boolean invalidate,
        long timeout,
        Collection<GridCacheTxEntry<K, V>> readEntries,
        Collection<GridCacheTxEntry<K, V>> writeEntries,
        GridCacheContext<K, V> ctx) throws GridException {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        readMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(
            readEntries != null ? readEntries.size() : 0, 1.0f);

        if (readEntries != null) {
            for (GridCacheTxEntry<K, V> entry : readEntries) {
                entry.unmarshal(ctx, ldr);

                // Initialize cache entry.
                entry.cached(ctx.cache().entryEx(entry.key()), entry.keyBytes());

                checkInternal(entry.key());

                readMap.put(entry.key(), entry);
            }
        }

        writeMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(
            writeEntries != null ? writeEntries.size() : 0, 1.0f);

        if (writeEntries != null) {
            for (GridCacheTxEntry<K, V> entry : writeEntries) {
                entry.unmarshal(ctx, ldr);

                // Initialize cache entry.
                entry.cached(ctx.cache().entryEx(entry.key()), entry.keyBytes());

                checkInternal(entry.key());

                writeMap.put(entry.key(), entry);

                addExplicit(entry);
            }
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
     * @param isRead <tt>True<tt> if read operation.
     * @param ctx Cache registry.
     */
    public GridReplicatedTxRemote(
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
        boolean isRead,
        GridCacheContext<K, V> ctx) {
        super(ctx, nodeId, rmtThreadId, xidVer, commitVer, concurrency, isolation, invalidate, timeout);

        readMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(1, 1.0f);
        writeMap = new LinkedHashMap<K, GridCacheTxEntry<K, V>>(1, 1.0f);

        if (isRead) {
            addRead(key, keyBytes);
        }
        else {
            addWrite(key, keyBytes);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridReplicatedTxRemote.class, this, "super", super.toString());
    }
}
