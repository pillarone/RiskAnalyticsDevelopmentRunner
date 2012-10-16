// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.affinity.partitioned;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.stopwatch.*;
import org.jetbrains.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Affinity function for partitioned cache. This function supports the following
 * configuration:
 * <ul>
 * <li>
 *      {@code backups} - Use ths flag to control how many back up nodes will be
 *      assigned to every key. The default value is defined by {@link #DFLT_BACKUP_COUNT}.
 * </li>
 * <li>
 *      {@code replicas} - Generally the more replicas a node gets, the more key assignments
 *      it will receive. You can configure different number of replicas for a node by
 *      setting user attribute with name {@link #getReplicaCountAttributeName()} to some
 *      number. Default value is {@code 512} defined by {@link #DFLT_REPLICA_COUNT} constant.
 * </li>
 * <li>
 *      {@code backupFilter} - Optional filter for back up nodes. If provided, then only
 *      nodes that pass this filter will be selected as backup nodes and only nodes that
 *      don't pass this filter will be selected as primary nodes. If not provided, then
 *      primary and backup nodes will be selected out of all nodes available for this cache.
 * </li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCachePartitionedAffinity<K> implements GridCacheAffinity<K> {
    /** Default number of partitions. */
    public static final int DFLT_PARTITION_COUNT = 521;

    /** Default number of backups. */
    public static final int DFLT_BACKUP_COUNT = 1;

    /** Default replica count for partitioned caches. */
    public static final int DFLT_REPLICA_COUNT = 512;

    /**
     * Name of node attribute to specify number of replicas for a node.
     * Default value is {@code gg:affinity:node:replicas}.
     */
    public static final String DFLT_REPLICA_COUNT_ATTR_NAME = "gg:affinity:node:replicas";

    /** Value to calculate replicas for partition hash. */
    private static final int MAX_PARTITIONS = Short.MAX_VALUE;

    /** Cache of partition consistent hashes to save on memory and initialization time. */
    private static final ConcurrentMap<Integer, GridConsistentHash<Integer>> partHashCache =
        new ConcurrentHashMap<Integer, GridConsistentHash<Integer>>();

    /** Partition hash. */
    private transient GridConsistentHash<Integer> partHash;

    /** Node hash. */
    private transient GridConsistentHash<UUID> nodeHash;

    /** Total number of partitions. */
    private int parts = DFLT_PARTITION_COUNT;

    /** */
    private int replicas = DFLT_REPLICA_COUNT;

    /** */
    private int backups = DFLT_BACKUP_COUNT;

    /** */
    private String attrName = DFLT_REPLICA_COUNT_ATTR_NAME;

    /** Optional backup filter. */
    private GridPredicate<GridRichNode> backupFilter;

    /** Hasher function. */
    private GridClosure<Object, Integer> hasher = GridConsistentHash.MD5_HASHER;

    /** Initialization flag. */
    private AtomicBoolean init = new AtomicBoolean(false);

    /** Latch for initializing. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient CountDownLatch initLatch = new CountDownLatch(1);

    /** */
    @GridInstanceResource
    private transient Grid grid;

    /** Nodes IDs. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient Collection<UUID> addedNodes = new GridConcurrentHashSet<UUID>();

    /** Optional backup filter. */
    private final GridPredicate<UUID> backupIdFilter = new GridPredicate<UUID>() {
        @Override public boolean apply(UUID e) {
            if (backupFilter == null)
                return true;

            GridRichNode n = grid.node(e);

            return n != null && backupFilter.apply(n);
        }
    };

    /** Optional primary filter. */
    private final GridPredicate<UUID> primaryIdFilter = F.not(backupIdFilter);

    /**
     * Empty constructor with all defaults.
     */
    public GridCachePartitionedAffinity() {
        // No-op.
    }

    /**
     * Initializes affinity with specified number of backups.
     *
     * @param backups Number of back up servers per key.
     */
    public GridCachePartitionedAffinity(int backups) {
        this.backups = backups;
    }

    /**
     * Initializes affinity with specified number of backups and partitions.
     *
     * @param backups Number of back up servers per key.
     * @param parts Total number of partitions.
     */
    public GridCachePartitionedAffinity(int backups, int parts) {
        this.backups = backups;
        this.parts = parts;
    }

    /**
     * Initializes optional counts for replicas and backups.
     *
     * @param backups Backups count.
     * @param parts Total number of partitions.
     * @param backupFilter Optional back up filter for nodes. If provided, then primary nodes
     *      will be selected from all nodes outside of this filter, and backups will be selected
     *      from all nodes inside it.
     */
    public GridCachePartitionedAffinity(int backups, int parts, @Nullable GridPredicate<GridRichNode> backupFilter) {
        this.backups = backups;
        this.parts = parts;
        this.backupFilter = backupFilter;
    }

    /**
     * Gets default count of virtual replicas in consistent hash ring.
     * <p>
     * To determine node replicas, node attribute with {@link #getReplicaCountAttributeName()}
     * name will be checked first. If it is absent, then this value will be used.
     *
     * @return Count of virtual replicas in consistent hash ring.
     */
    public int getDefaultReplicas() {
        return replicas;
    }

    /**
     * Sets default count of virtual replicas in consistent hash ring.
     * <p>
     * To determine node replicas, node attribute with {@link #getReplicaCountAttributeName} name
     * will be checked first. If it is absent, then this value will be used.
     *
     * @param replicas Count of virtual replicas in consistent hash ring.s
     */
    public void setDefaultReplicas(int replicas) {
        this.replicas = replicas;
    }

    /**
     * Gets count of key backups for redundancy.
     *
     * @return Key backup count.
     */
    public int getKeyBackups() {
        return backups;
    }

    /**
     * Sets count of key backups for redundancy.
     *
     * @param backups Key backup count.
     */
    public void setKeyBackups(int backups) {
        this.backups = backups;
    }

    /**
     * Gets total number of key partitions. To ensure that all partitions are
     * equally distributed across all nodes, please make sure that this
     * number is significantly larger than a number of nodes. Also, partition
     * size should be relatively small. Try to avoid having partitions with more
     * than quarter million keys.
     * <p>
     * Note that for fully replicated caches this method should always
     * return {@code 1}.
     *
     * @return Total partition count.
     */
    public int getPartitions() {
        return parts;
    }

    /**
     * Sets total number of partitions.
     *
     * @param parts Total number of partitions.
     */
    public void setPartitions(int parts) {
        this.parts = parts;
    }

    /**
     * Gets optional backup filter. If not {@code null}, then primary nodes will be
     * selected from all nodes outside of this filter, and backups will be selected
     * from all nodes inside it.
     *
     * @return Optional backup filter.
     */
    @Nullable public GridPredicate<GridRichNode> getBackupFilter() {
        return backupFilter;
    }

    /**
     * Sets optional backup filter. If provided, then primary nodes will be selected
     * from all nodes outside of this filter, and backups will be selected from all
     * nodes inside it.
     *
     * @param backupFilter Optional backup filter.
     */
    public void setBackupFilter(@Nullable GridPredicate<GridRichNode> backupFilter) {
        this.backupFilter = backupFilter;
    }

    /**
     * Gets hasher function for consistent hash.
     *
     * @return Hasher function for consistent hash.
     */
    public GridClosure<Object, Integer> getHasher() {
        return hasher;
    }

    /**
     * Sets hasher function for consistent hash.
     *
     * @param hasher Hasher function for consistent hash.
     */
    public void setHasher(GridClosure<Object, Integer> hasher) {
        this.hasher = hasher;
    }

    /**
     * Gets optional attribute name for replica count. If not provided, the
     * default is {@link #DFLT_REPLICA_COUNT_ATTR_NAME}.
     *
     * @return User attribute name for replica count for a node.
     */
    public String getReplicaCountAttributeName() {
        return attrName;
    }

    /**
     * Sets optional attribute name for replica count. If not provided, the
     * default is {@link #DFLT_REPLICA_COUNT_ATTR_NAME}.
     *
     * @param attrName User attribute name for replica count for a node.
     */
    public void setReplicaCountAttributeName(String attrName) {
        this.attrName = attrName;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(int part, Collection<GridRichNode> nodes) {
        if (F.isEmpty(nodes))
            return Collections.emptyList();

        GridStopwatch watch = W.stopwatch("AFFINITY_CHECK", false);

        try {
            initialize();

            addIfAbsent(nodes);

            Collection<UUID> nodeIds = F.viewReadOnly(nodes, F.node2id());

            if (backupFilter != null) {
                UUID primaryId = nodeHash.node(part, primaryIdFilter, F.contains(nodeIds));

                Collection<UUID> backupIds = nodeHash.nodes(part, backups, backupIdFilter, F.contains(nodeIds));

                if (F.isEmpty(backupIds))
                    return grid.nodes(F.asList(primaryId));

                return grid.nodes(F.concat(false, primaryId, backupIds));
            }
            else {
                Collection<UUID> ids = nodeHash.nodes(part, backups + 1, nodeIds);

                if (ids.size() == 1)
                    return Collections.singletonList(grid.node(ids.iterator().next()));

                return grid.nodes(ids);
            }
        }
        finally {
            watch.stop();
        }
    }

    /** {@inheritDoc} */
    @Override public int partition(K key) {
        initialize();

        return partHash.node(key);
    }

    /** {@inheritDoc} */
    @Override public int partitions() {
        GridStopwatch watch = W.stopwatch("AFFINITY_PARTITIONS", false);

        try {
            initialize();

            return parts;
        }
        finally {
            watch.stop();
        }
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        addedNodes = new GridConcurrentHashSet<UUID>();

        initLatch = new CountDownLatch(1);

        init.set(false);
    }

    /** {@inheritDoc} */
    private void initialize() {
        if (init.compareAndSet(false, true)) {
            nodeHash = new GridConsistentHash<UUID>(hasher);

            GridConsistentHash<Integer> partHash = partHashCache.get(parts);

            if (partHash == null) {
                partHash = new GridConsistentHash<Integer>(hasher);

                int partReplicas = MAX_PARTITIONS / parts;

                // Initialize partition hash.
                for (int i = 0; i < parts; i++)
                    partHash.addNode(i, partReplicas);

                // Cache consistent hash for given number of partitions to reuse
                // in other caches.
                partHashCache.put(parts, partHash);
            }

            this.partHash = partHash;

            // Only listen to removals, adding happens on demand.
            grid.addLocalEventListener(new GridLocalEventListener() {
                @Override public void onEvent(GridEvent evt) {
                    checkRemoved();
                }
            }, EVT_NODE_FAILED, EVT_NODE_LEFT);

            initLatch.countDown();
        }
        else {
            try {
                initLatch.await();
            }
            catch (InterruptedException ignore) {
                // No-op.
            }
        }
    }

    /**
     * @param n Node.
     * @return Replicas.
     */
    private int replicas(GridNode n) {
        Integer nodeReplicas = n.attribute(attrName);

        if (nodeReplicas == null)
            nodeReplicas = replicas;

        return nodeReplicas;
    }

    /**
     * @param nodes Nodes to add.
     */
    private void addIfAbsent(Iterable<? extends GridNode> nodes) {
        for (GridNode n : nodes)
            addIfAbsent(n);
    }

    /**
     * @param n Node to add.
     */
    private void addIfAbsent(GridNode n) {
        if (!addedNodes.contains(n.id()))
            add(n);
    }

    /**
     * @param n Node to add.
     */
    private void add(GridNode n) {
        if (grid.node(n.id()) != null)
            add(n.id(), replicas(n));
    }

    /**
     * @param id Node ID to add.
     * @param replicas Replicas.
     */
    private void add(UUID id, int replicas) {
        nodeHash.addNode(id, replicas);

        addedNodes.add(id);
    }

    /**
     * Cleans up removed nodes.
     */
    private void checkRemoved() {
        for (Iterator<UUID> it = addedNodes.iterator(); it.hasNext(); ) {
            UUID id = it.next();

            Grid grid = this.grid;

            if (grid == null)
                break;

            if (grid.node(id) == null) {
                it.remove();

                nodeHash.removeNode(id);
            }
        }
    }
}
