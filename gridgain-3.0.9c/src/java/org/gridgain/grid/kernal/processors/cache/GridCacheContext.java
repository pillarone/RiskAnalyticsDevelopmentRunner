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
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.cloner.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.discovery.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.managers.swapspace.*;
import org.gridgain.grid.kernal.processors.cache.datastructures.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.kernal.processors.cache.local.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.kernal.processors.closure.*;
import org.gridgain.grid.kernal.processors.rich.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * Cache context.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridCacheContext<K, V> implements Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<String, String>> stash = new ThreadLocal<GridTuple2<String, String>>() {
        @Override protected GridTuple2<String, String> initialValue() {
            return F.t2();
        }
    };

    /** Empty cache version array. */
    private static final GridCacheVersion[] EMPTY_VERSION = new GridCacheVersion[0];

    /** Kernal context. */
    private GridKernalContext ctx;

    /** Logger. */
    private GridLogger log;

    /** Cache configuration. */
    private GridCacheConfigurationAdapter cacheCfg;

    /** Cache transaction manager. */
    private GridCacheTxManager<K, V> txMgr;

    /** Version manager. */
    private GridCacheVersionManager<K, V> verMgr;

    /** Lock manager. */
    private GridCacheMvccManager<K, V> mvccMgr;

    /** Event manager. */
    private GridCacheEventManager<K, V> evtMgr;

    /** Query manager. */
    private GridCacheQueryManager<K, V> qryMgr;

    /** Swap manager. */
    private GridCacheSwapManager<K, V> swapMgr;

    /** Garbage collector manager.*/
    private GridCacheDgcManager<K, V> dgcMgr;

    /** Deployment manager. */
    private GridCacheDeploymentManager<K, V> depMgr;

    /** Communication manager. */
    private GridCacheIoManager<K, V> ioMgr;

    /** Evictions manager. */
    private GridCacheEvictionManager<K, V> evictMgr;

    /** Data structures manager. */
    private GridCacheDataStructuresManager<K, V> dataStructuresMgr;

    /** Managers. */
    private List<GridCacheManager<K, V>> mgrs = new LinkedList<GridCacheManager<K, V>>();

    /** Cache gateway. */
    private GridCacheGateway<K, V> gate;

    /** Grid cache. */
    private GridCacheAdapter<K, V> cache;

    /** No-value filter array. */
    private GridPredicate<GridCacheEntry<K, V>>[] noValArr;

    /** Has-value filter array. */
    private GridPredicate<GridCacheEntry<K, V>>[] hasValArr;

    /** No-peek-value filter array. */
    private GridPredicate<GridCacheEntry<K, V>>[] noPeekArr;

    /** Has-peek-value filter array. */
    private GridPredicate<GridCacheEntry<K, V>>[] hasPeekArr;

    /** No-op filter array. */
    private GridPredicate<GridCacheEntry<K, V>>[] trueArr;

    /**
     * Thread local projection. If it's set it means that method call was initiated
     * by child projection of initial cache.
     */
    private ThreadLocal<GridCacheProjectionImpl<K, V>> prjPerCall = new ThreadLocal<GridCacheProjectionImpl<K, V>>();

    /** Thread local forced flags that affect any projection in the same thread. */
    private static ThreadLocal<GridCacheFlag[]> forcedFlags = new ThreadLocal<GridCacheFlag[]>();

    /** Constant array to avoid recreation. */
    private static final GridCacheFlag[] FLAG_LOCAL_READ = new GridCacheFlag[]{LOCAL, READ};

    /** Local flag array. */
    private static final GridCacheFlag[] FLAG_LOCAL = new GridCacheFlag[]{LOCAL};

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheContext() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     * @param cacheCfg Cache configuration.
     * @param mvccMgr Cache locking manager.
     * @param verMgr Cache version manager.
     * @param evtMgr Cache event manager.
     * @param swapMgr Cache swap manager.
     * @param depMgr Cache deployment manager.
     * @param evictMgr Cache eviction manager.
     * @param ioMgr Cache communication manager.
     * @param qryMgr Cache query manager.
     * @param dgcMgr Distributed garbage collector manager.
     * @param txMgr Cache transaction manager.
     * @param dataStructuresMgr Cache dataStructures manager.
     */
    @SuppressWarnings({"unchecked"})
    public GridCacheContext(
        GridKernalContext ctx,
        GridCacheConfigurationAdapter cacheCfg,

        /*
        * Managers in starting order!
        * ===========================
        */

        GridCacheMvccManager<K, V> mvccMgr,
        GridCacheVersionManager<K, V> verMgr,
        GridCacheEventManager<K, V> evtMgr,
        GridCacheSwapManager<K, V> swapMgr,
        GridCacheDeploymentManager<K, V> depMgr,
        GridCacheEvictionManager<K, V> evictMgr,
        GridCacheIoManager<K, V> ioMgr,
        GridCacheQueryManager<K, V> qryMgr,
        GridCacheDgcManager<K, V> dgcMgr,
        GridCacheTxManager<K, V> txMgr,
        GridCacheDataStructuresManager<K, V> dataStructuresMgr) {
        assert ctx != null;
        assert cacheCfg != null;

        assert mvccMgr != null;
        assert verMgr != null;
        assert evtMgr != null;
        assert swapMgr != null;
        assert depMgr != null;
        assert evictMgr != null;
        assert ioMgr != null;
        assert dgcMgr != null;
        assert txMgr != null;
        assert dataStructuresMgr != null;

        this.ctx = ctx;
        this.cacheCfg = cacheCfg;

        /*
         * Managers in starting order!
         * ===========================
         */
        this.mvccMgr = add(mvccMgr);
        this.verMgr = add(verMgr);
        this.evtMgr = add(evtMgr);
        this.swapMgr = add(swapMgr);
        this.depMgr = add(depMgr);
        this.evictMgr = add(evictMgr);
        this.ioMgr = add(ioMgr);
        this.qryMgr = add(qryMgr);
        this.dgcMgr = add(dgcMgr);
        this.txMgr = add(txMgr);
        this.dataStructuresMgr = add(dataStructuresMgr);

        log = ctx.log(getClass());

        noValArr = new GridPredicate[]{F.cacheNoGetValue()};
        hasValArr = new GridPredicate[]{F.cacheHasGetValue()};
        noPeekArr = new GridPredicate[]{F.cacheNoPeekValue()};
        hasPeekArr = new GridPredicate[]{F.cacheHasPeekValue()};
        trueArr = new GridPredicate[]{F.alwaysTrue()};

        gate = new GridCacheGateway<K, V>(this);
    }

    /**
     * @param mgr Manager to add.
     * @return Added manager.
     */
    @Nullable private <T extends GridCacheManager<K, V>> T add(@Nullable T mgr) {
        if (mgr != null)
            mgrs.add(mgr);

        return mgr;
    }

    /**
     * @return Cache managers.
     */
    public List<GridCacheManager<K, V>> managers() {
        return mgrs;
    }

    /**
     * @param cache Cache.
     */
    public void cache(GridCacheAdapter<K, V> cache) {
        this.cache = cache;
    }

    /**
     * @return Local cache.
     */
    public GridLocalCache<K, V> local() {
        return (GridLocalCache<K, V>)cache;
    }

    /**
     * @return {@code True} if cache is DHT.
     */
    public boolean isDht() {
        return cache instanceof GridDhtCache;
    }

    /**
     * @return {@code True} is cache is near cache.
     */
    public boolean isNear() {
        return cache instanceof GridNearCache;
    }

    /**
     * @return DHT cache.
     */
    public GridDhtCache<K, V> dht() {
        return (GridDhtCache<K, V>)cache;
    }

    /**
     * @return Near cache.
     */
    public GridNearCache<K, V> near() {
        return (GridNearCache<K, V>)cache;
    }

    /**
     * @return Replicated cache.
     */
    public GridReplicatedCache<K, V> replicated() {
        return (GridReplicatedCache<K, V>)cache;
    }

    /**
     * @return Cache gateway.
     */
    public GridCacheGateway<K, V> gate() {
        return gate;
    }

    /**
     * @return {@code True} if enterprise edition.
     */
    public boolean isEnterprise() {
        return ctx.isEnterprise();
    }

    /**
     * @return Kernal context.
     */
    public GridKernalContext kernalContext() {
        return ctx;
    }

    /**
     * @return Grid instance.
     */
    public Grid grid() {
        return ctx.grid();
    }

    /**
     * @return Grid name.
     */
    public String gridName() {
        return ctx.gridName();
    }

    /**
     * @return Cache name.
     */
    public String name() {
        return cacheCfg.getName();
    }

    /**
     * Gets public name for cache.
     *
     * @return Public name of the cache.
     */
    public String namex() {
        return isDht() ? dht().near().name() : name();
    }

    /**
     * Gets public cache name substituting null name by {@code 'defaultCache'}.
     *
     * @return Public cache name substituting null name by {@code 'defaultCache'}.
     */
    public String namexx() {
        String name = namex();

        return name == null ? "defaultCache" : name;
    }

    /**
     * @return Preloader.
     */
    public GridCachePreloader<K, V> preloader() {
        return cache().preloader();
    }

    /**
     * @return Local node ID.
     */
    public UUID nodeId() {
        return ctx.localNodeId();
    }

    /**
     * @return {@code True} if preload is enabled.
     */
    public boolean preloadEnabled() {
        return cacheCfg.getPreloadMode() != NONE;
    }

    /**
     * @param name Step name.
     * @return Formatted step name.
     */
    public GridStopwatch stopwatch(String name) {
        return W.enabled() ? W.stopwatch(name() + ':' + name) : W.noop();
    }

    /**
     * @return Local node.
     */
    public GridRichNode localNode() {
        GridRichNode loc = ctx.rich().rich(ctx.discovery().localNode());

        assert loc != null;

        return loc;
    }

    /**
     * @param nodeId Node id.
     * @return Node.
     */
    @Nullable public GridRichNode node(UUID nodeId) {
        assert nodeId != null;

        GridNode node = ctx.discovery().node(nodeId);

        if (node == null)
            return null;

        return ctx.rich().rich(node);
    }

    /**
     * @return Remote node predicate.
     */
    public GridPredicate<GridNode> remotes() {
        return F.remoteNodes(nodeId());
    }

    /**
     * @return Partition count.
     */
    public int partitions() {
        return cacheCfg.getAffinity().partitions();
    }

    /**
     * @param part Partition number to check.
     * @param node Node.
     * @return {@code true} if given partition belongs to specified node.
     */
    public boolean belongs(int part, GridRichNode node) {
        return belongs(part, node, CU.allNodes(this));
    }

    /**
     * @param part Partition number to check.
     * @param allNodes All cache nodes.
     * @param node Node.
     * @return {@code true} if given partition belongs to specified node.
     */
    public boolean belongs(int part, GridRichNode node, Collection<GridRichNode> allNodes) {
        assert node != null;
        assert part >= 0;

        return cacheCfg.getAffinity().nodes(part, allNodes).contains(node);
    }

    /**
     * @param key Key.
     * @param node Node.
     * @return {@code true} if given key belongs to specified node according
     *      to affinity function.
     */
    public boolean belongs(K key, GridRichNode node) {
        assert node != null;

        return belongs(partition(key), node);
    }

    /**
     * @param node Node.
     * @param exclNode Optional exclude node.
     * @return Partitions for which given node is primary.
     */
    public Set<Integer> primaryPartitions(GridNode node, @Nullable GridRichNode exclNode) {
        GridCacheAffinity<K> aff = cacheCfg.getAffinity();

        Collection<GridRichNode> nodes = new ArrayList<GridRichNode>(CU.allNodes(this));

        if (exclNode != null)
            nodes.remove(exclNode);

        Set<Integer> parts = new HashSet<Integer>();

        int partCnt = aff.partitions();

        for (int i = 0; i < partCnt; i++) {
            Collection<GridRichNode> col = aff.nodes(i, nodes);

            if (!col.isEmpty() && F.eqNodes(node, F.first(col)))
                parts.add(i);
        }

        return parts;
    }

    /**
     * @return Marshaller.
     */
    public GridMarshaller marshaller() {
        return ctx.config().getMarshaller();
    }

    /**
     * @param ctgr Category to log.
     * @return Logger.
     */
    public GridLogger logger(String ctgr) {
        return new GridCacheLogger(this, ctgr);
    }

    /**
     * @param cls Class to log.
     * @return Logger.
     */
    public GridLogger logger(Class<?> cls) {
        return logger(cls.getName());
    }

    /**
     * @return Grid configuration.
     */
    public GridConfiguration gridConfig() {
        return ctx.config();
    }

    /**
     * @return Grid communication manager.
     */
    public GridIoManager gridIO() {
        return ctx.io();
    }

    /**
     * @return Grid timeout processor.
     */
    public GridTimeoutProcessor time() {
        return ctx.timeout();
    }

    /**
     * @return Grid deployment manager.
     */
    public GridDeploymentManager gridDeploy() {
        return ctx.deploy();
    }

    /**
     * @return Grid swap space manager.
     */
    public GridSwapSpaceManager gridSwap() {
        return ctx.swap();
    }

    /**
     * @return Grid event storage manager.
     */
    public GridEventStorageManager gridEvents() {
        return ctx.event();
    }

    /**
     * @return Closures processor.
     */
    public GridClosureProcessor closures() {
        return ctx.closure();
    }

    /**
     * @return Grid discovery manager.
     */
    public GridDiscoveryManager discovery() {
        return ctx.discovery();
    }

    /**
     * @return Grid rich processor.
     */
    public GridRichProcessor rich() {
        return ctx.rich();
    }

    /**
     * @return Cache instance.
     */
    public GridCacheAdapter<K, V> cache() {
        return cache;
    }

    /**
     * @return Cache API.
     */
    public GridCache<K, V> cacheApi() {
        return cache;
    }

    /**
     * @return Cache configuration for given cache instance.
     */
    public GridCacheConfigurationAdapter config() {
        return cacheCfg;
    }

    /**
     * @return Cache transaction manager.
     */
    public GridCacheTxManager<K, V> tm() {
        return txMgr;
    }

    /**
     * @return Lock order manager.
     */
    public GridCacheVersionManager<K, V> versions() {
        return verMgr;
    }

    /**
     * @return Lock manager.
     */
    public GridCacheMvccManager<K, V> mvcc() {
        return mvccMgr;
    }

    /**
     * @return Event manager.
     */
    public GridCacheEventManager<K, V> events() {
        return evtMgr;
    }

    /**
     * @return Query manager.
     */
    @Nullable public GridCacheQueryManager<K, V> queries() {
        return qryMgr;
    }

    /**
     * @return Swap manager.
     */
    public GridCacheSwapManager<K, V> swap() {
        return swapMgr;
    }

    /**
     * @return Swap manager.
     */
    public GridCacheDgcManager<K, V> dgc() {
        return dgcMgr;
    }

    /**
     * @return Cache deployment manager.
     */
    public GridCacheDeploymentManager<K, V> deploy() {
        return depMgr;
    }

    /**
     * @return Cache communication manager.
     */
    public GridCacheIoManager<K, V> io() {
        return ioMgr;
    }

    /**
     * @return Eviction manager.
     */
    public GridCacheEvictionManager<K, V> evicts() {
        return evictMgr;
    }

    /**
     * @return Sequence manager.
     */
    public GridCacheDataStructuresManager<K, V> dataStructures() {
        return dataStructuresMgr;
    }

    /**
     * @return No get-value filter.
     */
    public GridPredicate<GridCacheEntry<K, V>>[] noGetArray() {
        return noValArr;
    }

    /**
     * @return Has get-value filer.
     */
    public GridPredicate<GridCacheEntry<K, V>>[] hasGetArray() {
        return hasValArr;
    }

    /**
     * @return No get-value filter.
     */
    public GridPredicate<GridCacheEntry<K, V>>[] noPeekArray() {
        return noPeekArr;
    }

    /**
     * @return Has get-value filer.
     */
    public GridPredicate<GridCacheEntry<K, V>>[] hasPeekArray() {
        return hasPeekArr;
    }

    /**
     * @param val Value to check.
     * @return Predicate array that checks for value.
     */
    @SuppressWarnings({"unchecked"})
    public GridPredicate<GridCacheEntry<K, V>>[] equalsPeekArray(V val) {
        assert val != null;

        return new GridPredicate[]{F.cacheContainsPeek(val)};
    }

    /**
     * @return Empty filter.
     */
    public GridPredicate<GridCacheEntry<K, V>> truex() {
        return F.alwaysTrue();
    }

    /**
     * @return No-op array.
     */
    public GridPredicate<GridCacheEntry<K, V>>[] trueArray() {
        return trueArr;
    }

    /**
     * @return Empty cache version array.
     */
    public GridCacheVersion[] emptyVersion() {
        return EMPTY_VERSION;
    }

    /**
     * @param p Single predicate.
     * @return Array containing single predicate.
     */
    @SuppressWarnings({"unchecked"})
    public GridPredicate<GridCacheEntry<K, V>>[] vararg(GridPredicate<? super GridCacheEntry<K, V>> p) {
        return p == null ? CU.<K, V>empty() : new GridPredicate[]{p};
    }

    /**
     * Same as {@link GridFunc#isAll(Object, GridPredicate[])}, but safely unwraps
     * exceptions.
     *
     * @param e Element.
     * @param p Predicates.
     * @return {@code True} if predicates passed.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"ErrorNotRethrown"})
    public <K, V> boolean isAll(GridCacheEntryEx<K, V> e,
        GridPredicate<? super GridCacheEntry<K, V>>[] p) throws GridException {
        return isAll(e.wrap(false), p);
    }

    /**
     * Same as {@link GridFunc#isAll(Object, GridPredicate[])}, but safely unwraps
     * exceptions.
     *
     * @param e Element.
     * @param p Predicates.
     * @param <E> Element type.
     * @return {@code True} if predicates passed.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"ErrorNotRethrown"})
    public <E> boolean isAll(E e, GridPredicate<? super E>[] p) throws GridException {
        // We should allow only local read-only operations within filter checking.
        GridCacheFlag[] oldFlags = forceFlags(FLAG_LOCAL_READ);

        try {
            boolean pass = F.isAll(e, p);

            if (log.isDebugEnabled())
                log.debug("Evaluated filters for entry [pass=" + pass + ", entry=" + e + ", filters=" +
                    Arrays.toString(p) + ']');

            return pass;
        }
        catch (RuntimeException ex) {
            throw U.cast(ex);
        }
        finally {
            forceFlags(oldFlags);
        }
    }

    /**
     * Forces LOCAL flag.
     *
     * @return Previously forced flags.
     */
    @Nullable public GridCacheFlag[] forceLocal() {
        return forceFlags(FLAG_LOCAL);
    }

    /**
     * Forces LOCAL and READ flags.
     *
     * @return Forced flags that were set prior to method call.
     */
    @Nullable public GridCacheFlag[] forceLocalRead() {
        return forceFlags(FLAG_LOCAL_READ);
    }

    /**
     * Force projection flags for the current thread. These flags will affect all
     * projections (even without flags) used within the current thread.
     *
     * @param flags Flags to force.
     * @return Forced flags that were set prior to method call.
     */
    @Nullable public GridCacheFlag[] forceFlags(@Nullable GridCacheFlag[] flags) {
        GridCacheFlag[] oldFlags = forcedFlags.get();

        forcedFlags.set(F.isEmpty(flags) ? null : flags);

        return oldFlags;
    }

    /**
     * Gets forced flags for current thread.
     *
     * @return Forced flags.
     */
    public GridCacheFlag[] forcedFlags() {
        return forcedFlags.get();
    }

    /**
     * NOTE: Use this method always when you need to calculate partition id for
     * a key provided by user. It's required since we should apply affinity mapper
     * logic in order to find a key that will eventually be passed to affinity function.
     *
     * @param key Key.
     * @return Partition.
     */
    public int partition(K key) {
        return cacheCfg.getAffinity().partition(cacheCfg.<Object>getAffinityMapper().affinityKey(key));
    }

    /**
     * @param key Key.
     * @param nodes Nodes.
     * @return Affinity nodes.
     */
    public Collection<GridRichNode> affinity(K key, Collection<GridRichNode> nodes) {
        GridCacheAffinity<Object> aff = cacheCfg.getAffinity();

        int part = partition(key);

        Collection<GridRichNode> affNodes = aff.nodes(part, nodes);

        if (F.isEmpty(affNodes))
            throw new GridRuntimeException("Grid cache affinity returned empty nodes collection.");

        return affNodes;
    }

    /**
     * @param part Partition.
     * @param nodes Nodes.
     * @return Affinity nodes.
     */
    public Collection<GridRichNode> affinity(int part, Collection<GridRichNode> nodes) {
        GridCacheAffinity<K> aff = cacheCfg.getAffinity();

        Collection<GridRichNode> affNodes = aff.nodes(part, nodes);

        if (F.isEmpty(affNodes))
            throw new GridRuntimeException("Grid cache affinity returned empty nodes collection for nodes: " +
                U.nodeIds(nodes));

        return affNodes;
    }

    /**
     * @param n Node to check.
     * @param key Key to check.
     * @return {@code True} if checked node is primary for given key.
     */
    public boolean primary(GridNode n, K key) {
        GridNode primary = F.first(affinity(partition(key), CU.allNodes(this)));

        assert primary != null : "Primary node returned by affinity cannot be null: " + key;

        return primary.id().equals(n.id());
    }

    /**
     * @param n Node to check.
     * @param p Partition.
     * @return {@code True} if checked node is primary for given key.
     */
    public boolean primary(GridNode n, int p) {
        GridNode primary = F.first(affinity(p, CU.allNodes(this)));

        assert primary != null : "Primary node returned by affinity cannot be null: " + p;

        return primary.id().equals(n.id());
    }

    /**
     * @param key Key.
     * @return Nodes for the key.
     */
    public Collection<GridRichNode> allNodes(K key) {
        return cacheCfg.getAffinity().nodes(partition(key), CU.allNodes(this));
    }

    /**
     * @param keys keys.
     * @return Nodes for the keys.
     */
    public Collection<GridRichNode> allNodes(Iterable<? extends K> keys) {
        Collection<Collection<GridRichNode>> colcol = new LinkedList<Collection<GridRichNode>>();

        Collection<GridRichNode> nodes = CU.allNodes(this);

        for (K key : keys)
            colcol.add(cacheCfg.<K>getAffinity().nodes(partition(key), nodes));

        return F.flat(colcol);
    }

    /**
     * @param key Key.
     * @return Nodes for the key.
     */
    public Collection<GridRichNode> remoteNodes(K key) {
        return cacheCfg.getAffinity().nodes(partition(key), CU.remoteNodes(this));
    }

    /**
     * @param keys keys.
     * @return Nodes for the keys.
     */
    public Collection<GridRichNode> remoteNodes(Iterable<? extends K> keys) {
        Collection<Collection<GridRichNode>> colcol = new GridLeanSet<Collection<GridRichNode>>();

        Collection<GridRichNode> rmts = CU.remoteNodes(this);

        for (K key : keys)
            colcol.add(cacheCfg.<K>getAffinity().nodes(partition(key), rmts));

        return F.flat(colcol);
    }

    /**
     * Clone cached object.
     *
     * @param obj Object to clone
     * @return Clone of the given object.
     * @throws GridException If failed to clone object.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public <T> T cloneValue(@Nullable T obj) throws GridException {
        if (obj == null)
            return obj;

        GridCacheCloner c = cacheCfg.getCloner();

        if (c != null)
            return c.cloneValue(obj);

        return X.cloneObject(obj, false, true);
    }

    /**
     * Sets thread local projection.
     *
     * @param prj Flags to set.
     */
    void projectionPerCall(@Nullable GridCacheProjectionImpl<K, V> prj) {
        if (isDht())
            dht().near().context().prjPerCall.set(prj);
        else
            prjPerCall.set(prj);
    }

    /**
     * Gets thread local projection.
     * @return Projection per call.
     */
    public GridCacheProjectionImpl<K, V> projectionPerCall() {
        return isDht() ? dht().near().context().prjPerCall.get() : prjPerCall.get();
    }

    /**
     *
     * @param flag Flag to check.
     * @return {@code true} if the given flag is set.
     */
    public boolean hasFlag(GridCacheFlag flag) {
        assert flag != null;

        if (isDht())
            return dht().near().context().hasFlag(flag);

        GridCacheProjectionImpl<K, V> prj = prjPerCall.get();

        GridCacheFlag[] forced = forcedFlags.get();

        return (prj != null && prj.flags().contains(flag)) || (forced != null && U.containsObjectArray(forced, flag));
    }

    /**
     * Checks whether any of the given flags is set.
     *
     * @param flags Flags to check.
     * @return {@code true} if any of the given flags is set.
     */
    public boolean hasAnyFlags(GridCacheFlag[] flags) {
        assert !F.isEmpty(flags);

        if (isDht())
            return dht().near().context().hasAnyFlags(flags);

        GridCacheProjectionImpl<K, V> prj = prjPerCall.get();

        if (prj == null && F.isEmpty(forcedFlags.get()))
            return false;

        for (GridCacheFlag f : flags)
            if (hasFlag(f))
                return true;

        return false;
    }

    /**
     * Checks whether any of the given flags is set.
     *
     * @param flags Flags to check.
     * @return {@code true} if any of the given flags is set.
     */
    public boolean hasAnyFlags(Collection<GridCacheFlag> flags) {
        assert !F.isEmpty(flags);

        if (isDht())
            return dht().near().context().hasAnyFlags(flags);

        GridCacheProjectionImpl<K, V> prj = prjPerCall.get();

        if (prj == null && F.isEmpty(forcedFlags.get()))
            return false;

        for (GridCacheFlag f : flags)
            if (hasFlag(f))
                return true;

        return false;
    }

    /**
     * @param flag Flag to check.
     */
    public void denyOnFlag(GridCacheFlag flag) {
        assert flag != null;

        if (hasFlag(flag))
            throw new GridCacheFlagException(flag);
    }

    /**
     *
     */
    public void denyOnLocalRead() {
        denyOnFlags(FLAG_LOCAL_READ);
    }

    /**
     * @param flags Flags.
     */
    public void denyOnFlags(GridCacheFlag[] flags) {
        assert !F.isEmpty(flags);

        if (hasAnyFlags(flags))
            throw new GridCacheFlagException(flags);
    }

    /**
     * @param flags Flags.
     */
    public void denyOnFlags(Collection<GridCacheFlag> flags) {
        assert !F.isEmpty(flags);

        if (hasAnyFlags(flags))
            throw new GridCacheFlagException(flags);
    }

    /**
     * Clones cached object depending on whether or not {@link GridCacheFlag#CLONE} flag
     * is set thread locally.
     *
     * @param obj Object to clone.
     * @return Clone of the given object.
     * @throws GridException If failed to clone.
     */
    @Nullable public <T> T cloneOnFlag(@Nullable T obj) throws GridException {
        return hasFlag(CLONE) ? cloneValue(obj) : obj;
    }

    /**
     * @param f Target future.
     * @return Wrapped future that is aware of cloning behaviour.
     */
    public GridFuture<V> wrapClone(GridFuture<V> f) {
        if (!hasFlag(CLONE))
            return f;

        return new GridFutureWrapper<V, V>(f, new CX1<V, V>() {
            /** */
            private V clone;

            @Nullable @Override public V applyx(V e) throws GridException {
                if (clone != null)
                    return clone;

                return clone = cloneValue(e);
            }
        });
    }

    /**
     * @param f Target future.
     * @return Wrapped future that is aware of cloning behaviour.
     */
    public GridFuture<Map<K, V>> wrapCloneMap(GridFuture<Map<K, V>> f) {
        if (!hasFlag(CLONE))
            return f;

        return new GridFutureWrapper<Map<K, V>, Map<K, V>>(f, new CX1<Map<K, V>, Map<K, V>>() {
            /** */
            private Map<K, V> map;

            @Nullable @Override public Map<K, V> applyx(Map<K, V> m) throws GridException {
                if (map != null)
                    return map;

                map = new GridLeanMap<K, V>();

                for (Map.Entry<K, V> e : m.entrySet())
                    map.put(e.getKey(), cloneValue(e.getValue()));

                return map;
            }
        });
    }

    /**
     * @param flags Flags to turn on.
     * @throws GridCacheFlagException If given flags are conflicting with given transaction.
     */
    public void checkTxFlags(@Nullable Collection<GridCacheFlag> flags) throws GridCacheFlagException {
        GridCacheTxEx tx = tm().tx();

        if (tx == null || F.isEmpty(flags))
            return;

        assert flags != null;

        if (flags.contains(INVALIDATE) && !tx.isInvalidate())
            throw new GridCacheFlagException(INVALIDATE);

        if (flags.contains(SYNC_COMMIT) && !tx.syncCommit())
            throw new GridCacheFlagException(SYNC_COMMIT);

        if (flags.contains(SYNC_ROLLBACK) && !tx.syncRollback())
            throw new GridCacheFlagException(SYNC_ROLLBACK);
    }

    /**
     * Creates Runnable that can be executed safely in a different thread inheriting
     * the same thread local projection as for the current thread. If no projection is
     * set for current thread then there's no need to create new object and method simply
     * returns given Runnable.
     *
     * @param r Runnable.
     * @return Runnable that can be executed in a different thread with the same
     *      projection as for current thread.
     */
    public Runnable projectSafe(final Runnable r) {
        assert r != null;

        if (F.isEmpty(projectionPerCall()) && F.isEmpty(forcedFlags()))
            return r;

        // Have to get projection per call used by calling thread to use it in a new thread.
        final GridCacheProjectionImpl<K, V> prj = prjPerCall.get();

        // Get flags in the same thread.
        final GridCacheFlag[] flags = forcedFlags();

        return new GPR() {
            @Override public void run() {
                GridCacheProjectionImpl<K, V> oldPrj = projectionPerCall();

                projectionPerCall(prj);

                GridCacheFlag[] oldFlags = forceFlags(flags);

                try {
                    r.run();
                }
                finally {
                    projectionPerCall(oldPrj);

                    forceFlags(oldFlags);
                }
            }
        };
    }

    /**
     * Creates callable that can be executed safely in a different thread inheriting
     * the same thread local projection as for the current thread. If no projection is
     * set for current thread then there's no need to create new object and method simply
     * returns given callable.
     *
     * @param r Callable.
     * @return Callable that can be executed in a different thread with the same
     *      projection as for current thread.
     */
    public <T> Callable<T> projectSafe(final Callable<T> r) {
        assert r != null;

        if (F.isEmpty(projectionPerCall()) && F.isEmpty(forcedFlags()))
            return r;

        // Have to get projection per call used by calling thread to use it in a new thread.
        final GridCacheProjectionImpl<K, V> prj = prjPerCall.get();

        // Get flags in the same thread.
        final GridCacheFlag[] flags = forcedFlags();

        return new GPC<T>() {
            @Override public T call() throws Exception {
                GridCacheProjectionImpl<K, V> oldPrj = projectionPerCall();

                projectionPerCall(prj);

                GridCacheFlag[] oldFlags = forceFlags(flags);

                try {
                    return r.call();
                }
                finally {
                    projectionPerCall(oldPrj);

                    forceFlags(oldFlags);
                }
            }
        };
    }

    /**
     * @return {@code true} if swap storage is enabled.
     */
    public boolean isSwapEnabled() {
        return cacheCfg.isSwapEnabled() && !hasFlag(SKIP_SWAP) && swapMgr.enabled();
    }

    /**
     * @return {@code true} if store is enabled.
     */
    public boolean isStoreEnabled() {
        return cacheCfg.isStoreEnabled() && !hasFlag(SKIP_STORE);
    }

    /**
     * @return {@code true} if invalidation is enabled.
     */
    public boolean isInvalidate() {
        return cacheCfg.isInvalidate() || hasFlag(INVALIDATE);

    }

    /**
     * @return {@code true} if synchronous commit is enabled.
     */
    public boolean syncCommit() {
        return cacheCfg.isSynchronousCommit() || hasFlag(SYNC_COMMIT);
    }

    /**
     * @return {@code true} if synchronous rollback is enabled.
     */
    public boolean syncRollback() {
        return cacheCfg.isSynchronousRollback() || hasFlag(SYNC_ROLLBACK);
    }

    /**
     * @param nearNodeId Near node ID.
     * @param entry Entry.
     * @param log Log.
     * @param dhtMap Dht mappings.
     * @param nearMap Near mappings.
     */
    public void dhtMap(UUID nearNodeId, GridDhtCacheEntry<K, V> entry, GridLogger log,
        Map<GridNode, List<GridDhtCacheEntry<K, V>>> dhtMap,
        Map<GridNode, List<GridDhtCacheEntry<K, V>>> nearMap) {
        Collection<GridNode> dhtNodes = dht().topology().nodes(entry.partition());

        if (log.isDebugEnabled())
            log.debug("Mapping entry to DHT nodes [nodes=" + U.toShortString(dhtNodes) + ", entry=" + entry + ']');

        Collection<UUID> readers = entry.readers();

        Collection<GridNode> nearNodes = null;

        if (!F.isEmpty(readers)) {
            nearNodes = discovery().nodes(readers, F.<UUID>not(F.idForNodeId(nearNodeId)));

            if (log.isDebugEnabled())
                log.debug("Mapping entry to near nodes [nodes=" + U.toShortString(nearNodes) + ", entry=" + entry + ']');
        }
        else if (log.isDebugEnabled())
            log.debug("Entry has no near readers: " + entry);

        map(entry, F.view(dhtNodes, F.remoteNodes(nodeId())), dhtMap); // Exclude local node.
        map(entry, nearNodes, nearMap);
    }

    /**
     * @param entry Entry.
     * @param nodes Nodes.
     * @param map Map.
     */
    private void map(GridDhtCacheEntry<K, V> entry, Iterable<GridNode> nodes,
        Map<GridNode, List<GridDhtCacheEntry<K, V>>> map) {
        if (nodes != null) {
            for (GridNode n : nodes) {
                List<GridDhtCacheEntry<K, V>> entries = map.get(n);

                if (entries == null)
                    map.put(n, entries = new LinkedList<GridDhtCacheEntry<K, V>>());

                entries.add(entry);
            }
        }
    }

    /**
     * @return Timeout for initial map exchange before preloading. We make it {@code 4} times
     * bigger than network timeout by default.
     */
    public long preloadExchangeTimeout() {
        long timeout = gridConfig().getNetworkTimeout() * 4;

        return timeout < 0 ? Long.MAX_VALUE : timeout;
    }

    /**
     * Waits for partition locks and transactions release.
     *
     * @param nodeId Node ID.
     * @return {@code true} if waiting was successful.
     */
    @SuppressWarnings({"unchecked"})
    public GridFuture<?> nodeReleaseFuture(UUID nodeId) {
        assert nodeId != null;

        GridCompoundIdentityFuture fut = new GridCompoundIdentityFuture(kernalContext());

        fut.add(tm().finishNode(nodeId));
        fut.add(mvcc().finishNode(nodeId));

        if (isDht()) {
            fut.add(dht().near().context().tm().finishNode(nodeId));
            fut.add(dht().near().context().mvcc().finishNode(nodeId));
        }

        fut.markInitialized();

        return fut;
    }

    /**
     * Waits for partition locks and transactions release.
     *
     * @param parts Partitions.
     * @return {@code true} if waiting was successful.
     */
    @SuppressWarnings({"unchecked"})
    public GridFuture<?> partitionReleaseFuture(Collection<Integer> parts) {
        assert parts != null;

        if (parts.isEmpty())
            return new GridFinishedFuture<Object>(kernalContext());

        GridCompoundIdentityFuture fut = new GridCompoundIdentityFuture(kernalContext());

        fut.add(tm().finishPartitions(parts));
        fut.add(mvcc().finishPartitions(parts));

        if (isDht()) {
            fut.add(dht().near().context().tm().finishPartitions(parts));
            fut.add(dht().near().context().mvcc().finishPartitions(parts));
        }

        fut.markInitialized();

        return fut;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, gridName());
        U.writeString(out, namex());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple2<String, String> t = stash.get();

        t.set1(U.readString(in));
        t.set2(U.readString(in));
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        GridTuple2<String, String> t = stash.get();

        try {
            GridKernal grid = (GridKernal)G.grid(t.get1());

            if (grid == null)
                throw new IllegalStateException("Failed to find grid for name: " + t.get1());

            GridCacheAdapter<K, V> cache = grid.internalCache(t.get2());

            if (cache == null)
                throw new IllegalStateException("Failed to find cache for name: " + t.get2());

            return cache.context();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "GridCacheContext: " + name();
    }
}
