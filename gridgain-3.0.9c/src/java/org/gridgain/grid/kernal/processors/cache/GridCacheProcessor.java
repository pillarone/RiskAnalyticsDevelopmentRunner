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
import org.gridgain.grid.cache.affinity.partitioned.*;
import org.gridgain.grid.cache.affinity.replicated.*;
import org.gridgain.grid.cache.eviction.always.*;
import org.gridgain.grid.cache.eviction.lirs.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.kernal.processors.cache.datastructures.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.kernal.processors.cache.local.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import javax.management.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridDeploymentMode.*;
import static org.gridgain.grid.cache.GridCacheConfiguration.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * Cache processor.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheProcessor extends GridProcessorAdapter {
    /** Null cache name. */
    private static final String NULL_NAME = UUID.randomUUID().toString();

    /** */
    private static final String DIST_QUERY_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.query.GridCacheDistributedQueryManager";

    /** */
    private static final String LOC_QUERY_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.query.GridCacheLocalQueryManager";

    /** */
    private static final String ENT_DATA_STRUCTURES_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.datastructures.GridCacheEnterpriseDataStructuresManager";

    /** */
    private static final String CMN_DATA_STRUCTURES_MGR_CLS =
        "org.gridgain.grid.kernal.processors.cache.datastructures.GridCacheCommunityDataStructuresManager";

    /** */
    private final Map<String, GridCacheAdapter<?, ?>> caches;

    /** Map of proxies. */
    private final Map<String, GridCache<?, ?>> proxies;

    /** MBean server. */
    private final MBeanServer mBeanSrv;

    /** Cache MBeans. */
    private final Collection<ObjectName> cacheMBeans = new LinkedList<ObjectName>();

    /**
     * @param ctx Kernal context.
     */
    public GridCacheProcessor(GridKernalContext ctx) {
        super(ctx);

        caches = new ConcurrentHashMap<String, GridCacheAdapter<?, ?>>();

        proxies = new ConcurrentHashMap<String, GridCache<?, ?>>();

        mBeanSrv = ctx.config().getMBeanServer();
    }

    /**
     * @param name Cache name.
     * @return Masked name accounting for {@code nulls}.
     */
    private String maskName(String name) {
        return name == null ? NULL_NAME : name;
    }

    /**
     * @param cfg Initializes cache configuration with proper defaults.
     */
    @SuppressWarnings( {"unchecked"})
    private void initialize(GridCacheConfigurationAdapter cfg) {
        if (cfg.getCacheMode() == null)
            cfg.setCacheMode(DFLT_CACHE_MODE);

        if (cfg.getDefaultTxConcurrency() == null)
            cfg.setDefaultTxConcurrency(DFLT_TX_CONCURRENCY);

        if (cfg.getDefaultTxIsolation() == null)
            cfg.setDefaultTxIsolation(DFLT_TX_ISOLATION);

        if (cfg.getAffinity() == null) {
            if (cfg.getCacheMode() == REPLICATED)
                cfg.setAffinity(new GridCacheReplicatedAffinity());
            else if (cfg.getCacheMode() == PARTITIONED)
                cfg.setAffinity(new GridCachePartitionedAffinity());
            else
                cfg.setAffinity(new LocalAffinity());
        }

        if (cfg.getAffinityMapper() == null)
            cfg.setAffinityMapper(new GridCacheDefaultAffinityMapper());

        if (cfg.getEvictionPolicy() == null)
            cfg.setEvictionPolicy(new GridCacheLirsEvictionPolicy(DFLT_CACHE_SIZE));

        if (cfg.getPreloadMode() == null)
            cfg.setPreloadMode(GridCachePreloadMode.ASYNC);

        if (cfg.getCacheMode() == PARTITIONED) {
            if (!cfg.isNearEnabled()) {
                if (cfg.getNearEvictionPolicy() != null)
                    U.warn(log, "Ignoring near eviction policy since near cache is disabled.");

                cfg.setNearEvictionPolicy(new GridCacheAlwaysEvictionPolicy());
            }

            if (cfg.getNearEvictionPolicy() == null)
                cfg.setNearEvictionPolicy(new GridCacheLirsEvictionPolicy((DFLT_NEAR_SIZE)));
        }
    }

    /**
     * @param cfg Configuration to validate.
     * @throws GridException If failed.
     */
    private void validate(GridCacheConfiguration cfg) throws GridException {
        if (cfg.getCacheMode() == REPLICATED && cfg.getAffinity().getClass().equals(GridCachePartitionedAffinity.class))
            throw new GridException("Cannot start REPLICATED cache with GridCachePartitionedAffinity " +
                "(switch to GridCacheReplicatedAffinity or provide custom affinity) [cacheName=" + cfg.getName() + ']');

        if (cfg.getCacheMode() == PARTITIONED && cfg.getAffinity().getClass().equals(GridCacheReplicatedAffinity.class))
            throw new GridException("Cannot start PARTITIONED cache with GridCacheReplicatedAffinity (switch " +
                "to GridCachePartitionedAffinity or provide custom affinity) [cacheName=" + cfg.getName() + ']');

        if (cfg.getCacheMode() == LOCAL && cfg.getAffinity() != null)
            U.warn(log, "GridCacheAffinity configuration parameter will be ignored for local cache [cacheName=" +
                cfg.getName() + ']');

        if (cfg.getPreloadMode() != NONE) {
            assertParameter(cfg.getPreloadThreadPoolSize() > 0, "preloadThreadPoolSize > 0");
            assertParameter(cfg.getPreloadBatchSize() > 0, "preloadBatchSize > 0");
        }
    }

    /**
     * @param ctx Context.
     * @return DHT managers.
     */
    private Iterable<GridCacheManager> dhtManagers(GridCacheContext ctx) {
        GridCacheQueryManager qryMgr = ctx.queries();

        return qryMgr != null ?
            F.asList(ctx.mvcc(), ctx.tm(), ctx.swap(), ctx.dgc(), ctx.evicts(), qryMgr) :
            F.asList(ctx.mvcc(), ctx.tm(), ctx.swap(), ctx.dgc(), ctx.evicts());
    }

    /**
     * @param ctx Context.
     * @return Managers present in both, DHT and Near caches.
     */
    private Collection<GridCacheManager> dhtExcludes(GridCacheContext ctx) {
        GridCacheQueryManager qryMgr = ctx.queries();

        return ctx.config().getCacheMode() != PARTITIONED ? Collections.<GridCacheManager>emptyList() :
            qryMgr != null ? F.asList(ctx.dgc(), qryMgr) : F.<GridCacheManager>asList(ctx.dgc());
    }

    /**
     * @param cfg Configuration.
     * @throws GridException If failed to inject.
     */
    private void injectResources(GridCacheConfiguration cfg) throws GridException {
        injectResource(cfg.getEvictionPolicy());
        injectResource(cfg.getAffinity());
        injectResource(cfg.<Object>getAffinityMapper());
        injectResource(cfg.getTransactionManagerLookup());
        injectResource(cfg.getCloner());
        injectResource(cfg.getStore());
    }

    /**
     * @param rsrc Resource.
     * @throws GridException If failed.
     */
    private void injectResource(Object rsrc) throws GridException {
        if (rsrc != null) {
            ctx.resource().injectGeneric(rsrc);
        }
    }

    /**
     * @param cfg Configuration.
     */
    private void cleanupResources(GridCacheConfiguration cfg) {
        cleanupResource(cfg.getEvictionPolicy());
        cleanupResource(cfg.getAffinity());
        cleanupResource(cfg.<Object>getAffinityMapper());
        cleanupResource(cfg.getTransactionManagerLookup());
        cleanupResource(cfg.getCloner());
        cleanupResource(cfg.getStore());
    }

    /**
     * @param rsrc Resource.
     */
    private void cleanupResource(Object rsrc) {
        if (rsrc != null) {
            try {
                ctx.resource().cleanupGeneric(rsrc);
            }
            catch (GridException e) {
                U.error(log, "Failed to cleanup resource: " + rsrc, e);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void start() throws GridException {
        assert caches != null;

        if (ctx.config().isDaemon())
            return;

        GridDeploymentMode depMode = ctx.config().getDeploymentMode();

        if (!F.isEmpty(ctx.config().getCacheConfiguration()))
            if (depMode != CONTINUOUS && depMode != SHARED)
                U.warn(log, "Deployment mode for cache is not CONTINUOUS or SHARED " +
                    "(it is recommended that you change deployment mode and restart): " + depMode);

        for (GridCacheConfiguration c : ctx.config().getCacheConfiguration()) {
            GridCacheConfigurationAdapter cfg = new GridCacheConfigurationAdapter(c);

            // Initialize defaults.
            initialize(cfg);

            validate(cfg);

            injectResources(cfg);

            GridCacheMvccManager mvccMgr = new GridCacheMvccManager();
            GridCacheTxManager tm = new GridCacheTxManager();
            GridCacheVersionManager verMgr = new GridCacheVersionManager();
            GridCacheEventManager evtMgr = new GridCacheEventManager();
            GridCacheSwapManager swapMgr = new GridCacheSwapManager(cfg.getCacheMode() != PARTITIONED);
            GridCacheDgcManager dgcMgr = new GridCacheDgcManager();
            GridCacheDeploymentManager depMgr = new GridCacheDeploymentManager();
            GridCacheEvictionManager evictMgr = new GridCacheEvictionManager();
            GridCacheQueryManager qryMgr = queryManager(cfg);
            GridCacheIoManager ioMgr = new GridCacheIoManager();
            GridCacheDataStructuresManager dataStructuresMgr = dataStructuresManager();

            GridCacheContext<?, ?> cacheCtx = new GridCacheContext(
                ctx,
                cfg,

                /*
                 * Managers in starting order!
                 * ===========================
                 */
                mvccMgr,
                verMgr,
                evtMgr,
                swapMgr,
                depMgr,
                evictMgr,
                ioMgr,
                qryMgr,
                dgcMgr,
                tm,
                dataStructuresMgr);

            GridCacheAdapter cache = null;

            switch (cfg.getCacheMode()) {
                case LOCAL: {
                    cache = new GridLocalCache(cacheCtx);

                    break;
                }
                case REPLICATED: {
                    cache = new GridReplicatedCache(cacheCtx);

                    break;
                }
                case PARTITIONED: {
                    cache = new GridNearCache(cacheCtx);

                    break;
                }

                default: {
                    assert false : "Invalid cache mode: " + cfg.getCacheMode();
                }
            }

            cacheCtx.cache(cache);

            // Start managers.
            for (GridCacheManager mgr : F.view(cacheCtx.managers(), F.notContains(dhtExcludes(cacheCtx))))
                mgr.start(cacheCtx);

            caches.put(maskName(cfg.getName()), cache);

            /*
             * Start DHT cache.
             * ================
             */
            if (cfg.getCacheMode() == PARTITIONED) {
                /*
                 * Specifically don't create the following managers
                 * here and reuse the one from Near cache:
                 * 1. GridCacheVersionManager
                 * 2. GridCacheIoManager
                 * 3. GridCacheDeploymentManager
                 * 4. GridCacheEventManager
                 * 5. GridCacheQueryManager (note, that we start it for DHT cache though).
                 * 6. GridCacheDgcManager (TODO: CODE: need to expand DGC to work for near and partitioned caches).
                 * ===============================================
                 */
                mvccMgr = new GridCacheMvccManager();
                tm = new GridCacheTxManager();
                swapMgr = new GridCacheSwapManager(true);
                evictMgr = new GridCacheEvictionManager();

                cacheCtx = new GridCacheContext(
                    ctx,
                    cfg,

                    /*
                     * Managers in starting order!
                     * ===========================
                     */
                    mvccMgr,
                    verMgr,
                    evtMgr,
                    swapMgr,
                    depMgr,
                    evictMgr,
                    ioMgr,
                    qryMgr,
                    dgcMgr,
                    tm,
                    dataStructuresMgr);

                GridNearCache near = (GridNearCache)cache;
                GridDhtCache dht = new GridDhtCache(cacheCtx);

                cacheCtx.cache(dht);

                near.dht(dht);
                dht.near(near);

                // Start managers.
                for (GridCacheManager mgr : dhtManagers(cacheCtx))
                    mgr.start(cacheCtx);

                dht.start();

                if (log.isDebugEnabled())
                    log.debug("Started DHT cache: " + dht.name());
            }

            cache.start();

            if (log.isInfoEnabled())
                log.info("Started cache [name=" + cfg.getName() + ", mode=" + cfg.getCacheMode() + ']');
        }

        for (Map.Entry<String, GridCacheAdapter<?, ?>> e : caches.entrySet()) {
            GridCacheAdapter cache = e.getValue();

            proxies.put(e.getKey(), new GridCacheProxyImpl(cache.context(), null));
        }

        if (log.isDebugEnabled())
            log.debug("Started cache processor.");
    }

    /**
     * @param rmt Node.
     * @throws GridException If check failed.
     */
    private void checkCache(GridNode rmt) throws GridException {
        GridCacheAttributes[] localAttrs = U.cacheAttributes(ctx.discovery().localNode());

        for (GridCacheAttributes a1 : U.cacheAttributes(rmt)) {
            for (GridCacheAttributes a2 : localAttrs) {
                if (F.eq(a1.cacheName(), a2.cacheName())) {
                    if (a1.cacheMode() != a2.cacheMode())
                        throw new GridException("Cache mode mismatch (fix cache mode in configuration or specify " +
                            "empty cache configuration list if default cache should not be started) [cacheName=" +
                            a1.cacheName() + ", localCacheMode=" + a2.cacheMode() +
                            ", remoteCacheMode=" + a1.cacheMode() + ']');

                    if (a1.cachePreloadMode() != a2.cachePreloadMode())
                        throw new GridException("Cache preload mode mismatch (fix cache preload mode in " +
                            "configuration or specify empty cache configuration list if default cache should " +
                            "not be started) [cacheName=" + a1.cacheName() +
                            ", localCachePreloadMode=" + a2.cachePreloadMode() +
                            ", remoteCachePreloadMode=" + a1.cachePreloadMode() + ']');

                    if (!F.eq(a1.cacheAffinityClassName(), a2.cacheAffinityClassName()))
                        throw new GridException(U.compact("Cache affinity mismatch (fix cache affinity in " +
                            "configuration or specify empty cache configuration list if default cache should " +
                            "not be started) [cacheName=" + a1.cacheName() +
                            ", localCacheAffinity=" + a2.cacheAffinityClassName() +
                            ", remoteCacheAffinity=" + a1.cacheAffinityClassName() + ']'));
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        if (ctx.config().isDaemon())
            return;

        for (GridNode n : ctx.discovery().remoteNodes())
            checkCache(n);

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            GridCacheContext<?, ?> ctx = cache.context();

            // Start DHT cache as well.
            if (ctx.config().getCacheMode() == PARTITIONED) {
                GridDhtCache dht = ctx.near().dht();

                GridCacheContext<?, ?> dhtCtx = dht.context();

                for (GridCacheManager mgr : dhtManagers(dhtCtx))
                    mgr.onKernalStart();

                dht.onKernalStart();

                if (log.isDebugEnabled())
                    log.debug("Executed onKernalStart() callback for DHT cache: " + dht.name());
            }

            for (GridCacheManager mgr : F.view(ctx.managers(), F.notContains(dhtExcludes(ctx))))
                mgr.onKernalStart();

            cache.onKernalStart();

            if (log.isDebugEnabled())
                log.debug("Executed onKernalStart() callback for cache [name=" + cache.name() + ", mode=" +
                    cache.configuration().getCacheMode() + ']');
        }

        for (GridCache<?, ?> proxy : proxies.values()) {
            try {
                String name = proxy.name() == null ? "default" : proxy.name();

                ObjectName mb = U.registerMBean(mBeanSrv, ctx.gridName(), "Cache", name,
                    new GridCacheMBeanAdapter(proxy), GridCacheMBean.class);

                cacheMBeans.add(mb);

                if (log.isDebugEnabled())
                    log.debug("Registered cache MBean: " + mb);
            }
            catch (JMException ex) {
                U.error(log, "Failed to register cache MBean.", ex);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void onKernalStop(boolean cancel, boolean wait) {
        if (ctx.config().isDaemon())
            return;

        if (!F.isEmpty(cacheMBeans))
            for (ObjectName mb : cacheMBeans) {
                try {
                    mBeanSrv.unregisterMBean(mb);

                    if (log.isDebugEnabled())
                        log.debug("Unregistered cache MBean: " + mb);
                }
                catch (JMException e) {
                    U.error(log, "Failed to unregister cache MBean: " + mb, e);
                }
            }

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            GridCacheContext ctx = cache.context();

            if (ctx.config().getCacheMode() == PARTITIONED) {
                GridDhtCache dht = ctx.near().dht();

                GridCacheContext<?, ?> dhtCtx = dht.context();

                for (GridCacheManager mgr : dhtManagers(dhtCtx))
                    mgr.onKernalStop();

                dht.onKernalStop();
            }

            List<GridCacheManager> mgrs = ctx.managers();

            Collection<GridCacheManager> excludes = dhtExcludes(ctx);

            // Reverse order.
            for (ListIterator<GridCacheManager> it = mgrs.listIterator(mgrs.size()); it.hasPrevious();) {
                GridCacheManager mgr = it.previous();

                if (!excludes.contains(mgr))
                    mgr.onKernalStop();
            }

            cache.onKernalStop();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (ctx.config().isDaemon())
            return;

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            cache.stop();

            GridCacheContext ctx = cache.context();

            if (ctx.config().getCacheMode() == PARTITIONED) {
                GridDhtCache dht = ctx.near().dht();

                dht.stop();

                GridCacheContext<?, ?> dhtCtx = dht.context();

                for (GridCacheManager mgr : dhtManagers(dhtCtx))
                    mgr.stop(cancel, wait);
            }

            List<GridCacheManager> mgrs = ctx.managers();

            Collection<GridCacheManager> excludes = dhtExcludes(ctx);

            // Reverse order.
            for (ListIterator<GridCacheManager> it = mgrs.listIterator(mgrs.size()); it.hasPrevious();) {
                GridCacheManager mgr = it.previous();

                if (!excludes.contains(mgr))
                    mgr.stop(cancel, wait);
            }

            cleanupResources(ctx.config());

            if (log.isInfoEnabled())
                log.info("Stopped cache: " + cache.name());
        }

        if (log.isDebugEnabled())
            log.debug("Stopped cache processor.");
    }

    /**
     * @param cfg Cache configuration.
     * @return Query manager.
     * @throws GridException In case of error.
     */
    @Nullable private GridCacheQueryManager queryManager(GridCacheConfiguration cfg) throws GridException {
        if (cfg.getCacheMode() == LOCAL) {
            try {
                Class cls = Class.forName(LOC_QUERY_MGR_CLS);

                if (log.isDebugEnabled())
                    log.debug("Local query manager found: " + cls);

                return (GridCacheQueryManager)cls.newInstance();
            }
            catch (Exception ex) {
                throw new GridException("Failed to find local query manager.", ex);
            }
        }
        else {
            try {
                Class cls = Class.forName(DIST_QUERY_MGR_CLS);

                if (log.isDebugEnabled())
                    log.debug("Distributed query manager found: " + cls);

                return (GridCacheQueryManager)cls.newInstance();
            }
            catch (Exception ignored) {
                // No-op.
            }
        }

        return null;
    }

    /**
     * @return Data structures manager.
     * @throws GridException In case of error.
     */
    private GridCacheDataStructuresManager dataStructuresManager() throws GridException {
        String clsName = U.isEnterprise() ? ENT_DATA_STRUCTURES_MGR_CLS : CMN_DATA_STRUCTURES_MGR_CLS;

        try {
            Class cls = Class.forName(clsName);

            if (log.isDebugEnabled())
                log.debug("Data structures manager found." + cls);

            return (GridCacheDataStructuresManager)cls.newInstance();
        }
        catch (Exception ex) {
            throw new GridException("Failed to find data structures manager.", ex);
        }
    }

    /**
     * @return Last data version.
     */
    public long lastDataVersion() {
        long max = 0;

        for (GridCacheAdapter<?, ?> cache : caches.values()) {
            GridCacheContext<?, ?> ctx = cache.context();

            if (ctx.versions().last().order() > max)
                max = ctx.versions().last().order();

            if (ctx.isNear()) {
                ctx = ctx.near().dht().context();

                if (ctx.versions().last().order() > max)
                    max = ctx.versions().last().order();
            }
        }

        assert caches.isEmpty() || max > 0;

        return max;
    }

    /**
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Default cache.
     */
    public <K, V> GridCache<K, V> cache() {
        return cache(null);
    }

    /**
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Default cache.
     */
    public <K, V> GridCacheAdapter<K, V> internalCache() {
        return internalCache(null);
    }

    /**
     * @param name Cache name.
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Cache instance for given name.
     */
    @SuppressWarnings("unchecked")
    public <K, V> GridCache<K, V> cache(@Nullable String name) {
        if (log.isDebugEnabled())
            log.debug("Getting cache for name: " + name);

        return (GridCache<K, V>)proxies.get(maskName(name));
    }

    /**
     * @return All configured cache instances.
     */
    public Collection<GridCache<?, ?>> caches() {
        return proxies.values();
    }

    /**
     * @param name Cache name.
     * @param <K> type of keys.
     * @param <V> type of values.
     * @return Cache instance for given name.
     */
    @SuppressWarnings("unchecked")
    public <K, V> GridCacheAdapter<K, V> internalCache(@Nullable String name) {
        if (log.isDebugEnabled()) {
            log.debug("Getting internal cache adapter: " + name);
        }

        return (GridCacheAdapter<K, V>)caches.get(maskName(name));
    }

    /**
     * Callback invoked by deployment manager for whenever a class loader
     * gets undeployed.
     *
     * @param ldr Class loader.
     */
    public void onUndeployed(ClassLoader ldr) {
        if (!ctx.isStopping())
            for (GridCacheAdapter<?, ?> cache : caches.values())
                cache.onUndeploy(ldr);
    }

    /**
     *
     */
    private static class LocalAffinity implements GridCacheAffinity<Object> {
        /** {@inheritDoc} */
        @Override public Collection<GridRichNode> nodes(int partition, Collection<GridRichNode> nodes) {
            for (GridRichNode n : nodes)
                if (n.isLocal())
                    return Arrays.asList(n);

            throw new GridRuntimeException("Local node is not included into affinity nodes for 'LOCAL' cache");
        }

        /** {@inheritDoc} */
        @Override public void reset() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public int partitions() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override public int partition(Object key) {
            return 0;
        }
    }
}
