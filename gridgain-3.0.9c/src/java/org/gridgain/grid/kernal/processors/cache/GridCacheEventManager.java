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
import org.gridgain.grid.events.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Cache event manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheEventManager<K, V> extends GridCacheManager<K, V> {
    /** Local node ID. */
    private UUID locNodeId;

    /** Event queue. */
    private ConcurrentLinkedQueue<GridCacheEvent> evts = new ConcurrentLinkedQueue<GridCacheEvent>();

    /** Unwinding flag to make sure that only one thread unwinds. */
    private AtomicBoolean unwinding = new AtomicBoolean(false);

    /** {@inheritDoc} */
    @Override public void start0() {
        locNodeId = cctx.nodeId();
    }

    /**
     * Adds local event listener.
     *
     * @param lsnr Listener.
     * @param evts Types of events.
     */
    public void addListener(GridLocalEventListener lsnr, int... evts) {
        cctx.gridEvents().addLocalEventListener(lsnr, evts);
    }

    /**
     * Removes local event listener.
     *
     * @param lsnr Local event listener.
     */
    public void removeListener(GridLocalEventListener lsnr) {
        cctx.gridEvents().removeLocalEventListener(lsnr);
    }

    /**
     * @param part Partition.
     * @param key Key for the event.
     * @param tx Possible surrounding transaction.
     * @param owner Possible surrounding lock.
     * @param type Event type.
     * @param newVal New value.
     * @param oldVal Old value.
     */
    public void addEvent(int part, K key, GridCacheTx tx, GridCacheMvccCandidate<K> owner,
        int type, V newVal, V oldVal) {
        addEvent(part, key, locNodeId, tx, owner, type, newVal, oldVal);
    }

    /**
     * @param part Partition.
     * @param key Key for the event.
     * @param nodeId Node ID.
     * @param tx Possible surrounding transaction.
     * @param owner Possible surrounding lock.
     * @param type Event type.
     * @param newVal New value.
     * @param oldVal Old value.
     */
    public void addEvent(int part, K key, UUID nodeId, GridCacheTx tx,
        GridCacheMvccCandidate<K> owner, int type, V newVal, V oldVal) {
        addEvent(part, key, nodeId, tx == null ? null : tx.xid(), owner == null ? null : owner.id(), type,
            newVal, oldVal);
    }

    /**
     * @param part Partition.
     * @param key Key for the event.
     * @param nodeId Node ID.
     * @param owner Possible surrounding lock.
     * @param type Event type.
     * @param newVal New value.
     * @param oldVal Old value.
     */
    public void addEvent(int part, K key, UUID nodeId, GridCacheMvccCandidate<K> owner,
        int type, V newVal, V oldVal) {
        GridCacheTx tx = owner == null ? null : cctx.tm().tx(owner.version());

        addEvent(part, key, nodeId, tx == null ? null : tx.xid(), owner == null ? null : owner.id(), type,
            newVal, oldVal);
    }

    /**
     * @param part Partition.
     * @param key Key for the event.
     * @param nodeId Node ID.
     * @param xid Transaction ID.
     * @param lockId Lock ID.
     * @param type Event type.
     * @param newVal New value.
     * @param oldVal Old value.
     */
    public void addEvent(int part, K key, UUID nodeId, UUID xid, @Nullable UUID lockId, int type, @Nullable V newVal, @Nullable V oldVal) {
        assert key != null;

        if (cctx.gridEvents().isRecordable(type))
            // Events are not made for internal entry.
            if (!(key instanceof GridCacheInternal))
                evts.add(new GridCacheEvent(cctx.name(), nodeId, "Cache event.", type, part, key, xid, lockId,
                    newVal, oldVal));
    }

    /**
     * Adds preloading event.
     *
     * @param part Partition.
     * @param type Event type.
     * @param discoNode Discovery node.
     * @param discoType Discovery event type.
     * @param discoTimestamp Discovery event timestamp.
     */
    public void addPreloadEvent(int part, int type, GridNodeShadow discoNode, int discoType, long discoTimestamp) {
        assert discoNode != null;
        assert type > 0;
        assert discoType > 0;
        assert discoTimestamp > 0;

        if (cctx.gridEvents().isRecordable(type))
            cctx.gridEvents().record(new GridCachePreloadEvent(cctx.name(), locNodeId, "Cache preloading event.",
                type, part, discoNode, discoType, discoTimestamp));
    }

    /**
     * Processes all events in the queue.
     */
    void unwind() {
        // Only one thread should unwind for efficiency.
        if (unwinding.compareAndSet(false, true)) {
            try {
                for (GridEvent evt = evts.poll(); evt != null; evt = evts.poll())
                    cctx.gridEvents().record(evt);
            }
            finally {
                unwinding.set(false);
            }
        }
    }
}
