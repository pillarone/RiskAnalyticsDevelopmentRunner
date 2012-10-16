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
import org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Partition topology.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
class GridDhtPartitionTopologyImpl<K, V> implements GridDhtPartitionTopology<K, V> {
    /** If true, then check consistency. */
    private static final boolean CONSISTENCY_CHECK = true;

    /** Flag to control amount of output for full map. */
    private static final boolean FULL_MAP_DEBUG = true;

    /** Context. */
    private final GridCacheContext<K, V> cctx;

    /** Logger. */
    private final GridLogger log;

    /** */
    private final ConcurrentMap<Integer, GridDhtLocalPartition<K, V>> locParts =
        new ConcurrentHashMap<Integer, GridDhtLocalPartition<K,V>>();

    /** Node to partition map. */
    private GridDhtPartitionFullMap node2part;

    /** Partition to node map. */
    private Map<Integer, Set<UUID>> part2node = new HashMap<Integer, Set<UUID>>();

    /** */
    private GridDhtPartitionExchangeId lastExchangeId;

    /** */
    private final AtomicLong updateSeq = new AtomicLong(1);

    /** Lock. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param cctx Context.
     */
    GridDhtPartitionTopologyImpl(GridCacheContext<K, V> cctx) {
        this.cctx = cctx;

        log = cctx.logger(getClass());
    }

    /**
     * @return Full map string representation.
     */
    @SuppressWarnings( {"ConstantConditions"})
    private String fullMapString() {
        return node2part == null ? "null" : FULL_MAP_DEBUG ? node2part.toFullString() : node2part.toString();
    }

    /**
     * @param map Map to get string for.
     * @return Full map string representation.
     */
    @SuppressWarnings( {"ConstantConditions"})
    private String mapString(GridDhtPartitionMap map) {
        return map == null ? "null" : FULL_MAP_DEBUG ? map.toFullString() : map.toString();
    }

    /**
     * Waits for renting partitions.
     *
     * @throws GridException If failed.
     */
    private void waitForRent() throws GridException {
        // Synchronously wait for all renting partitions to complete.
        for (Iterator<GridDhtLocalPartition<K, V>> it = locParts.values().iterator(); it.hasNext();) {
            GridDhtLocalPartition<K, V> p = it.next();

            if (!p.valid()) {
                // Wait for partition to empty out.
                p.rent().get();

                // Remove evicted partition.
                it.remove();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void beforeExchange(GridDhtPartitionExchangeId exchId) throws GridException {
        waitForRent();

        GridRichNode loc = cctx.localNode();

        Collection<GridRichNode> allNodes = CU.allNodes(cctx);

        GridNode oldest = CU.oldest(allNodes);

        int num = cctx.partitions();

        lock.writeLock().lock();

        try {
            if (log.isDebugEnabled())
                log.debug("Partition map beforeExchange [exchId=" + exchId + ", fullMap=" + fullMapString() + ']');

            long updateSeq = this.updateSeq.incrementAndGet();

            if (oldest.id().equals(loc.id())) {
                if (node2part == null) {
                    node2part = new GridDhtPartitionFullMap(loc.id(), loc.order(), updateSeq);

                    if (log.isDebugEnabled())
                        log.debug("Created brand new full topology map on oldest node [exchId=" +
                            exchId + ", fullMap=" + fullMapString() + ']');
                }
                else if (!node2part.valid()) {
                    node2part = new GridDhtPartitionFullMap(loc.id(), loc.order(), updateSeq, node2part, false);

                    if (log.isDebugEnabled())
                        log.debug("Created new full topology map on oldest node [exchId=" + exchId + ", fullMap=" +
                            node2part + ']');
                }
                else if (!node2part.nodeId().equals(loc.id())) {
                    node2part = new GridDhtPartitionFullMap(loc.id(), loc.order(), updateSeq, node2part, false);

                    if (log.isDebugEnabled())
                        log.debug("Copied old map into new map on oldest node (previous oldest node left) [exchId=" +
                            exchId + ", fullMap=" + fullMapString() + ']');
                }
            }

            if (cctx.preloadEnabled()) {
                for (int p = 0; p < num; p++) {
                    // If this is the first node in grid.
                    if (oldest.id().equals(loc.id()) && oldest.id().equals(exchId.nodeId())) {
                        assert exchId.isJoined();

                        GridDhtLocalPartition<K, V> locPart = localPartition(p, true);

                        assert locPart != null;

                        boolean owned = locPart.own();

                        assert owned : "Failed to own partition for oldest node [cacheName" + cctx.name() + ", part=" +
                            locPart + ']';

                        if (log.isDebugEnabled())
                            log.debug("Owned partition for oldest node: " + locPart);

                        updateLocal(p, loc.id(), locPart.state(), updateSeq);
                    }
                    // If this is not the first node in grid.
                    else {
                        if (node2part != null && node2part.valid()) {
                            if (cctx.belongs(p, loc, allNodes)) {
                                // This will make sure that all non-existing partitions
                                // will be created in MOVING state.
                                GridDhtLocalPartition<K, V> locPart = localPartition(p, true);

                                updateLocal(p, loc.id(), locPart.state(), updateSeq);
                            }
                            else {
                                GridDhtLocalPartition<K, V> locPart = localPartition(p, false);

                                if (locPart != null) {
                                    GridDhtPartitionState state = locPart.state();

                                    if (state == MOVING || locPart.isEmpty()) {
                                        locPart.rent();

                                        updateLocal(p, loc.id(), locPart.state(), updateSeq);

                                        if (log.isDebugEnabled())
                                            log.debug("Evicting moving partition (it does not belong to affinity): " +
                                                locPart);
                                    }
                                }
                            }
                        }
                        // If this node's map is empty, we pre-create local partitions,
                        // so local map will be sent correctly during exchange.
                        else if (cctx.belongs(p, loc, allNodes))
                            localPartition(p, true);
                    }
                }
            }
            else {
                // If preloader is disabled, then we simply clear out
                // the partitions this node is not responsible for.
                for (int p = 0; p < num; p++) {
                    GridDhtLocalPartition<K, V> locPart = localPartition(p, false);

                    if (locPart != null) {
                        if (!cctx.belongs(p, loc, allNodes)) {
                            GridDhtPartitionState state = locPart.state();

                            if (state.active()) {
                                locPart.rent();

                                if (log.isDebugEnabled())
                                    log.debug("Evicting partition with preloading disabled " +
                                        "(it does not belong to affinity): " + locPart);
                            }
                        }
                    }
                    else if (cctx.belongs(p, loc, allNodes))
                        // Pre-create partitions.
                        localPartition(p, true);
                }
            }

            checkEvictions(updateSeq);

            consistencyCheck();

            if (log.isDebugEnabled())
                log.debug("Partition map after beforeExchange [exchId=" + exchId + ", fullMap=" +
                    fullMapString() + ']');
        }
        finally {
            lock.writeLock().unlock();
        }

        // Wait for evictions.
        waitForRent();
    }

    /** {@inheritDoc} */
    @Override public void afterExchange(GridDhtPartitionExchangeId exchId) throws GridException {
        waitForRent();

        GridRichNode loc = cctx.localNode();

        Collection<GridRichNode> allNodes = CU.allNodes(cctx);

        int num = cctx.partitions();

        lock.writeLock().lock();

        try {
            if (log.isDebugEnabled())
                log.debug("Partition map before afterExchange [exchId=" + exchId + ", fullMap=" +
                    fullMapString() + ']');

            long updateSeq = this.updateSeq.incrementAndGet();

            for (int p = 0; p < num; p++) {
                if (cctx.belongs(p, loc, allNodes)) {
                    GridDhtLocalPartition<K, V> locPart = localPartition(p, false);

                    // This partition will be created during next topology event,
                    // which obviously has happened at this point.
                    if (locPart == null) {
                        if (log.isDebugEnabled())
                            log.debug("Skipping local partition afterExchange (will not create): " + p);

                        continue;
                    }

                    GridDhtPartitionState state = locPart.state();

                    // Don't do anything if this node is joining.
                    if (state == MOVING) {
                        Collection<GridNode> owners = owners(p);

                        // If there are no other owners, then become an owner.
                        if (F.isEmpty(owners)) {
                            boolean owned = locPart.own();

                            assert owned : "Failed to own partition [cacheName" + cctx.name() + ", locPart=" +
                                locPart + ']';

                            updateLocal(p, loc.id(), locPart.state(), updateSeq);

                            if (log.isDebugEnabled())
                                log.debug("Owned partition: " + locPart);
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Will not own partition (there are owners to preload from) [locPart=" +
                                locPart + ", owners = " + owners + ']');
                    }
                }
            }

            consistencyCheck();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridDhtLocalPartition<K, V> localPartition(int p, boolean create) {
        while (true) {
            GridDhtLocalPartition<K, V> loc = locParts.get(p);

            if (loc != null && loc.state() == EVICTED) {
                locParts.remove(p, loc);

                continue;
            }

            if (loc == null && create) {
                GridDhtLocalPartition<K, V> old = locParts.putIfAbsent(p,
                    loc = new GridDhtLocalPartition<K, V>(cctx, p));

                if (old != null)
                    loc = old;
                else if (log.isDebugEnabled())
                    log.debug("Created local partition: " + loc);
            }

            return loc;
        }
    }

    /** {@inheritDoc} */
    @Override public GridDhtLocalPartition<K, V> localPartition(K key, boolean create) {
        return localPartition(cctx.partition(key), create);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridDhtLocalPartition<K, V>> localPartitions() {
        return new LinkedList<GridDhtLocalPartition<K, V>>(locParts.values());
    }

    /** {@inheritDoc} */
    @Override public GridDhtLocalPartition<K, V> onAdded(GridDhtCacheEntry<K, V> e) {
        /*
         * Make sure not to acquire any locks here as this method
         * may be called from sensitive synchronization blocks.
         * ===================================================
         */

        int p = cctx.partition(e.key());

        GridDhtLocalPartition<K, V> loc = localPartition(p, true);

        assert loc != null;

        updateSeq.incrementAndGet();

        loc.onAdded(e);

        return loc;
    }

    /** {@inheritDoc} */
    @Override public void onRemoved(GridDhtCacheEntry<K, V> e) {
        /*
         * Make sure not to acquire any locks here as this method
         * may be called from sensitive synchronization blocks.
         * ===================================================
         */

        GridDhtLocalPartition<K, V> loc = localPartition(e.key(), false);

        if (loc != null) {
            updateSeq.incrementAndGet();

            loc.onRemoved(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridDhtPartitionMap localPartitionMap() {
        lock.readLock().lock();

        try {
            return new GridDhtPartitionMap(cctx.nodeId(), updateSeq.get(),
                F.viewReadOnly(locParts, CU.<K, V>part2state()), true);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridNode> nodes(int p) {
        if (!cctx.preloadEnabled())
            return new ArrayList<GridNode>(cctx.affinity(p, CU.allNodes(cctx)));

        Collection<UUID> affIds = F.viewReadOnly(cctx.affinity(p, CU.allNodes(cctx)), F.node2id());

        lock.readLock().lock();

        try {
            assert node2part != null && node2part.valid();

            Collection<UUID> nodeIds = part2node.get(p);

            if (nodeIds == null)
                nodeIds = Collections.emptyList();

            return new ArrayList<GridNode>(cctx.discovery().nodes(
                F.concat(false, affIds, F.view(nodeIds, F.notIn(affIds)))));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridNode> owners(int p) {
        if (!cctx.preloadEnabled())
            return new ArrayList<GridNode>(cctx.affinity(p, CU.allNodes(cctx)));

        lock.readLock().lock();

        try {
            assert node2part != null && node2part.valid();

            return new ArrayList<GridNode>(cctx.discovery().nodes(F.view(part2node.get(p), withState(p, OWNING))));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridNode> moving(int p) {
        lock.readLock().lock();

        try {
            assert node2part != null && node2part.valid();

            return new ArrayList<GridNode>(cctx.discovery().nodes(F.view(part2node.get(p), withState(p, MOVING))));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public long updateSequence() {
        return updateSeq.get();
    }

    /** {@inheritDoc} */
    @Override public GridDhtPartitionFullMap partitionMap(boolean onlyActive) {
        lock.readLock().lock();

        try {
            assert node2part != null && node2part.valid();

            GridDhtPartitionFullMap m = node2part;

            return new GridDhtPartitionFullMap(m.nodeId(), m.nodeOrder(), m.updateSequence(), m, onlyActive);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDhtPartitionMap update(@Nullable GridDhtPartitionExchangeId exchId,
        GridDhtPartitionFullMap partMap) {
        if (log.isDebugEnabled())
            log.debug("Updating full partition map [exchId=" + exchId + ", parts=" + fullMapString() + ']');

        lock.writeLock().lock();

        try {
            if (exchId != null && lastExchangeId != null && lastExchangeId.compareTo(exchId) >= 0) {
                if (log.isDebugEnabled())
                    log.debug("Stale exchange id for full partition map update (will ignore) [lastExchId=" +
                        lastExchangeId + ", exchId=" + exchId + ']');

                return null;
            }

            if (node2part != null && node2part.compareTo(partMap) >= 0) {
                if (log.isDebugEnabled())
                    log.debug("Stale partition map for full partition map update (will ignore) [lastExchId=" +
                        lastExchangeId + ", exchId=" + exchId + ", curMap=" + node2part + ", newMap=" + partMap + ']');

                return null;
            }

            long updateSeq = this.updateSeq.incrementAndGet();

            if (exchId != null)
                lastExchangeId = exchId;

            if (node2part != null) {
                for (GridDhtPartitionMap part : node2part.values()) {
                    GridDhtPartitionMap newPart = partMap.get(part.nodeId());

                    // If for some nodes current partition has a newer map,
                    // then we keep the newer value.
                    if (newPart != null && newPart.updateSequence() < part.updateSequence()) {
                        if (log.isDebugEnabled())
                            log.debug("Overriding partition map in full update map [exchId=" + exchId + ", curPart=" +
                                mapString(part) + ", newPart=" + mapString(newPart) + ']');

                        partMap.put(part.nodeId(), part);
                    }
                }

                for (Iterator<UUID> it = partMap.keySet().iterator(); it.hasNext();) {
                    UUID nodeId = it.next();

                    if (!cctx.discovery().alive(nodeId)) {
                        if (log.isDebugEnabled())
                            log.debug("Removing left node from full map update [nodeId=" + nodeId + ", partMap=" +
                                partMap + ']');

                        it.remove();
                    }
                }
            }

            node2part = partMap;

            Map<Integer, Set<UUID>> p2n = new HashMap<Integer, Set<UUID>>(cctx.partitions(), 1.0f);

            for (Map.Entry<UUID, GridDhtPartitionMap> e : partMap.entrySet()) {
                for (Integer p : e.getValue().keySet()) {
                    Set<UUID> ids = p2n.get(p);

                    if (ids == null)
                        // Initialize HashSet to size 3 in anticipation that there won't be
                        // more than 3 nodes per partitions.
                        p2n.put(p, ids = new HashSet<UUID>(3));

                    ids.add(e.getKey());
                }
            }

            part2node = p2n;

            boolean changed = checkEvictions(updateSeq);

            consistencyCheck();

            return changed ? localPartitionMap() : null;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable public GridDhtPartitionMap update(@Nullable GridDhtPartitionExchangeId exchId,
        GridDhtPartitionMap parts) {
        if (log.isDebugEnabled())
            log.debug("Updating single partition map [exchId=" + exchId + ", parts=" + mapString(parts) + ']');

        if (!cctx.discovery().alive(parts.nodeId())) {
            if (log.isDebugEnabled())
                log.debug("Received partition update for non-existing node (will ignore) [exchId=" + exchId +
                    ", parts=" + parts + ']');

            return null;
        }

        lock.writeLock().lock();

        try {
            if (lastExchangeId != null && exchId != null && lastExchangeId.compareTo(exchId) > 0) {
                if (log.isDebugEnabled())
                    log.debug("Stale exchange id for single partition map update (will ignore) [lastExchId=" +
                        lastExchangeId + ", exchId=" + exchId + ']');

                return null;
            }

            if (exchId != null)
                lastExchangeId = exchId;

            if (node2part == null)
                // Create invalid partition map.
                node2part = new GridDhtPartitionFullMap();

            GridDhtPartitionMap cur = node2part.get(parts.nodeId());

            if (cur != null && cur.updateSequence() >= parts.updateSequence()) {
                if (log.isDebugEnabled())
                    log.debug("Stale update sequence for single partition map update (will ignore) [exchId=" + exchId +
                        ", curSeq=" + cur.updateSequence() + ", newSeq=" + parts.updateSequence() + ']');

                return null;
            }

            long updateSeq = this.updateSeq.incrementAndGet();

            node2part = new GridDhtPartitionFullMap(node2part, updateSeq);

            node2part.put(parts.nodeId(), parts);

            part2node = new HashMap<Integer, Set<UUID>>(part2node);

            // Add new mappings.
            for (Integer p : parts.keySet()) {
                Set<UUID> ids = part2node.get(p);

                if (ids == null)
                    // Initialize HashSet to size 3 in anticipation that there won't be
                    // more than 3 nodes per partition.s
                    part2node.put(p, ids = new HashSet<UUID>(3));

                ids.add(parts.nodeId());
            }

            // Remove obsolete mappings.
            if (cur != null) {
                for (Integer p : F.view(cur.keySet(), F.notIn(parts.keySet()))) {
                    Set<UUID> ids = part2node.get(p);

                    if (ids != null)
                        ids.remove(parts.nodeId());
                }
            }

            boolean changed = checkEvictions(updateSeq);

            consistencyCheck();

            return changed ? localPartitionMap() : null;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @param updateSeq Update sequence.
     * @return Checks if any of the local partitions need to be evicted.
     */
    private boolean checkEvictions(long updateSeq) {
        assert lock.isWriteLockedByCurrentThread();

        boolean changed = false;

        Collection<GridRichNode> allNodes = CU.allNodes(cctx);

        UUID locId = cctx.nodeId();

        for (GridDhtLocalPartition<K, V> part : locParts.values()) {
            GridDhtPartitionState state = part.state();

            if (state.active()) {
                int p = part.id();

                Collection<GridRichNode> affNodes = cctx.affinity(p, allNodes);

                if (!affNodes.contains(cctx.localNode())) {
                    Collection<UUID> nodeIds = F.view(part2node.get(p), withState(p, OWNING));

                    int ownerCnt = nodeIds.size();
                    int affCnt = affNodes.size();

                    if (ownerCnt > affCnt) {
                        List<GridNode> sorted = new ArrayList<GridNode>(cctx.discovery().nodes(nodeIds));

                        // Sort by node orders in ascending order.
                        Collections.sort(sorted, CU.nodeComparator(true));

                        int diff = sorted.size() - affCnt;

                        for (int i = 0; i < diff; i++) {
                            GridNode n = sorted.get(i);

                            if (locId.equals(n.id())) {
                                part.rent();

                                updateLocal(part.id(), locId, part.state(), updateSeq);

                                consistencyCheck();

                                changed = true;

                                break;
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Updates value for single partition.
     *
     * @param p Partition.
     * @param nodeId Node ID.
     * @param state State.
     * @param updateSeq Update sequence.
     */
    private void updateLocal(int p, UUID nodeId, GridDhtPartitionState state, long updateSeq) {
        assert lock.isWriteLockedByCurrentThread();
        assert nodeId.equals(cctx.nodeId());

        GridDhtPartitionMap map = node2part.get(nodeId);

        if (map == null)
            node2part.put(nodeId, map = new GridDhtPartitionMap(nodeId, updateSeq,
                Collections.<Integer, GridDhtPartitionState>emptyMap(), false));

        map.put(p, state);

        Set<UUID> ids = part2node.get(p);

        if (ids == null)
            part2node.put(p, ids = new HashSet<UUID>(3));

        ids.add(nodeId);
    }

    /** {@inheritDoc} */
    @Override public void remove(UUID nodeId) {
        assert nodeId != null;

        GridNode oldest = CU.oldest(CU.allNodes(cctx));

        GridNode loc = cctx.localNode();

        lock.writeLock().lock();

        try {
            if (node2part != null) {
                node2part = oldest.equals(loc) && !node2part.nodeId().equals(loc.id()) ?
                    new GridDhtPartitionFullMap(loc.id(), loc.order(), updateSeq.incrementAndGet(), node2part, false) :
                    new GridDhtPartitionFullMap(node2part, updateSeq.incrementAndGet());

                part2node = new HashMap<Integer, Set<UUID>>(part2node);

                GridDhtPartitionMap parts = node2part.remove(nodeId);

                if (parts != null) {
                    for (Integer p : parts.keySet()) {
                        Set<UUID> nodeIds = part2node.get(p);

                        if (nodeIds != null) {
                            nodeIds.remove(nodeId);

                            if (nodeIds.isEmpty())
                                part2node.remove(p);
                        }
                    }
                }

                consistencyCheck();
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean own(GridDhtLocalPartition<K, V> part) {
        GridRichNode loc = cctx.localNode();

        lock.writeLock().lock();

        try {
            if (part.own()) {
                updateLocal(part.id(), loc.id(), part.state(), updateSeq.incrementAndGet());

                consistencyCheck();

                return true;
            }

            consistencyCheck();

            return false;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDhtPartitionMap partitions(UUID nodeId) {
        lock.readLock().lock();

        try {
            return node2part.get(nodeId);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @param p Partition.
     * @param match State to match.
     * @return Filter for owners of this partition.
     */
    private GridPredicate<UUID> withState(final int p, final GridDhtPartitionState match) {
        return new P1<UUID>() {
            @Override public boolean apply(UUID nodeId) {
                GridDhtPartitionMap parts = node2part.get(nodeId);

                // Set can be null if node has been removed.
                if (parts != null) {
                    GridDhtPartitionState state = parts.get(p);

                    return state == match;
                }

                return false;
            }
        };
    }

    /**
     * Checks consistency after all operations.
     */
    private void consistencyCheck() {
        if (CONSISTENCY_CHECK) {
            assert lock.writeLock().isHeldByCurrentThread();

            if (node2part == null)
                return;

            for (Map.Entry<UUID, GridDhtPartitionMap> e : node2part.entrySet())
                for (Integer p : e.getValue().keySet()) {
                    Set<UUID> nodeIds = part2node.get(p);

                    assert nodeIds != null : "Failed consistency check [part=" + p + ", nodeId=" + e.getKey() + ']';
                    assert nodeIds.contains(e.getKey()) : "Failed consistency check [part=" + p + ", nodeId=" +
                        e.getKey() + ", nodeIds=" + nodeIds + ']';
                }

            for (Map.Entry<Integer, Set<UUID>> e : part2node.entrySet())
                for (UUID nodeId : e.getValue()) {
                    GridDhtPartitionMap map = node2part.get(nodeId);

                    assert map != null : "Failed consistency check [part=" + e.getKey() + ", nodeId=" + nodeId + ']';
                    assert map.containsKey(e.getKey()) : "Failed consistency check [part=" + e.getKey() +
                        ", nodeId=" + nodeId + ']';
                }
        }
    }
}
