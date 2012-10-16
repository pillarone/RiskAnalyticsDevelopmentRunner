// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Key partition.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtLocalPartition<K, V> implements Comparable<GridDhtLocalPartition> {
    /** Partition ID. */
    private final int id;

    /** State. */
    @GridToStringExclude
    private AtomicStampedReference<GridDhtPartitionState> state =
        new AtomicStampedReference<GridDhtPartitionState>(MOVING, 0);

    /**  */
    @GridToStringExclude
    private final GridFutureAdapter<?> rent;

    /** */
    private final ConcurrentMap<K, GridDhtCacheEntry<K, V>> map = new ConcurrentHashMap<K, GridDhtCacheEntry<K,V>>();

    /** */
    private final GridCacheContext<K, V> cctx;

    /** */
    private final GridLogger log;

    /** Create time. */
    @GridToStringExclude
    private final long createTime = System.currentTimeMillis();

    /**
     * @param cctx Context.
     * @param id Partition ID.
     */
    GridDhtLocalPartition(GridCacheContext<K, V> cctx, int id) {
        assert cctx != null;

        this.id = id;
        this.cctx = cctx;

        log = cctx.logger(getClass());

        rent = new GridFutureAdapter<Object>(cctx.kernalContext());
    }

    /**
     * @return Partition ID.
     */
    public int id() {
        return id;
    }

    /**
     * @return Create time.
     */
    long createTime() {
        return createTime;
    }

    /**
     * @return Partition state.
     */
    public GridDhtPartitionState state() {
        return state.getReference();
    }

    /**
     * @return Reservations.
     */
    public int reservations() {
        return state.getStamp();
    }

    /**
     * @return Entries belonging to partition.
     */
    public Collection<GridDhtCacheEntry<K, V>> entries() {
        return map.values();
    }

    /**
     * @return {@code True} if partition is empty.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * @return If partition is moving or owning.
     */
    public boolean valid() {
        GridDhtPartitionState state = state();

        return state == MOVING || state == OWNING;
    }

    /**
     * @param entry Entry to add.
     */
    void onAdded(GridDhtCacheEntry<K, V> entry) {
        assert valid() : "Adding entry to invalid partition: " + this;

        map.put(entry.key(), entry);
    }

    /**
     * @param entry Entry to remove.
     */
    void onRemoved(GridDhtCacheEntry<K, V> entry) {
        assert entry.obsolete();

        // Make sure to remove exactly this entry.
        map.remove(entry.key(), entry);

        // Attempt to evict.
        tryEvict();
    }

    /**
     * Reserves a partition so it won't be cleared.
     *
     * @return {@code True} if reserved.
     */
    public boolean reserve() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == RENTING || s == EVICTED)
                return false;

            if (state.compareAndSet(s, s, reservations, reservations + 1))
                return true;
        }
    }

    /**
     * Releases previously reserved partition.
     */
    public void release() {
        while (true) {
            int reservations = state.getStamp();

            if (reservations == 0)
                return;

            GridDhtPartitionState s = state.getReference();

            assert s != EVICTED;

            // Decrement reservations.
            if (state.compareAndSet(s, s, reservations, --reservations)) {
                tryEvict();

                break;
            }
        }
    }

    /**
     * @return {@code True} if transitioned to OWNING state.
     */
    boolean own() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == RENTING || s == EVICTED)
                return false;

            if (s == OWNING)
                return true;

            assert s == MOVING;

            if (state.compareAndSet(MOVING, OWNING, reservations, reservations)) {
                if (log.isDebugEnabled())
                    log.debug("Owned partition: " + this);

                return true;
            }
        }
    }

    /**
     * @return Future to signal that this node is no longer an owner or backup.
     */
    GridFuture<?> rent() {
        while (true) {
            int reservations = state.getStamp();

            GridDhtPartitionState s = state.getReference();

            if (s == RENTING || s == EVICTED)
                return rent;

            if (state.compareAndSet(s, RENTING, reservations, reservations)) {
                if (log.isDebugEnabled())
                    log.debug("Moved partition to RENTING state: " + this);

                rent.addWatch(cctx.stopwatch("PARTITION_RENT"));

                // Evict asynchronously, as the 'rent' method may be called
                // from within write locks on local partition.
                tryEvictAsync();

                break;
            }
        }

        return rent;
    }

    /**
     * @return Future for evict attempt.
     */
    private GridFuture<Boolean> tryEvictAsync() {
        if (map.isEmpty() && state.compareAndSet(RENTING, EVICTED, 0, 0)) {
            if (log.isDebugEnabled())
                log.debug("Evicted partition: " + this);

            rent.onDone();

            return new GridFinishedFuture<Boolean>(cctx.kernalContext(), true);
        }

        try {
            return cctx.closures().callLocal(new GPC<Boolean>() {
                @Override public Boolean call() {
                    return tryEvict();
                }
            }, /*system pool*/true);
        }
        catch (GridException e) {
            U.error(log, "Failed to execute closure for local partition: " + this, e);

            return new GridFinishedFuture<Boolean>(cctx.kernalContext(), e);
        }
    }

    /**
     * @return {@code True} if entry has been transitioned to state EVICTED.
     */
    private boolean tryEvict() {
        // Attempt to evict partition entries from cache.
        if (state.getReference() == RENTING && state.getStamp() == 0)
            clearAll();

        if (map.isEmpty() && state.compareAndSet(RENTING, EVICTED, 0, 0)) {
            if (log.isDebugEnabled())
                log.debug("Evicted partition: " + this);

            rent.onDone();

            return true;
        }

        return false;
    }

    /**
     * Clears values for this partition.
     */
    private void clearAll() {
        GridCacheVersion clearVer = cctx.versions().next();

        for (Iterator<GridDhtCacheEntry<K, V>> it = map.values().iterator(); it.hasNext();) {
            GridDhtCacheEntry<K, V> cached = it.next();

            // Clear readers so entry can be cleared from cache.
            cached.clearReaders();

            try {
                if (cached.clear(clearVer, cctx.isSwapEnabled(), CU.<K, V>empty()))
                    it.remove();
            }
            catch (GridException e) {
                U.error(log, "Failed to clear cache entry for evicted partition: " + cached, e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        return obj instanceof GridDhtLocalPartition && (obj == this || ((GridDhtLocalPartition)obj).id() == id);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridDhtLocalPartition part) {
        if (part == null)
            return 1;

        return id == part.id() ? 0 : id > part.id() ? 1 : -1;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLocalPartition.class, this,
            "state", state(),
            "reservations", reservations(),
            "empty", map.isEmpty(),
            "createTime", U.format(createTime));
    }
}
