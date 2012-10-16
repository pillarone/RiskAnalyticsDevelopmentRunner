// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.coherence;

import com.tangosol.net.*;
import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.coherence.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import java.util.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Load balancing SPI which uses data affinity for routing jobs to remote nodes. It provides
 * ability to <b>collocate computations with data</b>. Coherence Cache provides partitioned
 * cache feature which allows you to segment your cached data across cluster. This SPI
 * delegates to Coherence Cache to find out which node is responsible for caching data
 * and routes a job to it.
 * <p>
 * Note, that instead of regular {@link GridJob}, this SPI expects {@link GridCoherenceAffinityJob}
 * which allows user to specify affinity key and cache name.
 * <p>
 * <h1 class="header">Coding Example</h1>
 * To use load balancers for your job routing, in your {@link GridTask#map(List, Object)}
 * implementation use load balancer to find out the node this job should be routed to
 * (see {@link GridLoadBalancerResource} documentation for information on how a load balancer
 * can be injected into your task). However, the preferred way here is to use
 * {@link GridTaskSplitAdapter}, as it will handle affinity assignment of jobs to nodes
 * automatically. Node that when working with affinity load balancing, your task's
 * {@code map(..)} or {@code split(..)} methods should return {@link GridCoherenceAffinityJob}
 * instances instead of {@link GridJob} ones. {@link GridCoherenceAffinityJob} adds two additional
 * methods to grid job: {@link GridCoherenceAffinityJob#getAffinityKey()} and
 * {@link GridCoherenceAffinityJob#getCacheName()} which will allow GridGain to
 * delegate routing to Coherence Cache, so jobs for the same cache with the same key will
 * be always routed to the same node. In case if regular
 * {@link GridJob} is returned, not the {@link GridCoherenceAffinityJob}, it will be routed
 * to a randomly picked node.
 * <p>
 * Here is an example of a grid task that uses affinity load balancing. Note how load balancing
 * jobs is absolutely transparent to the user and is simply a matter of proper grid configuration.
 * <pre name="code" class="java">
 * public class MyFooBarCoherenceAffinityTask extends GridTaskSplitAdapter&lt;List&lt;Integer&gt;,Object&gt; {
 *     // For this example we receive a list of cache keys and for every key
 *     // create a job that accesses it.
 *     &#64;Override
 *     protected Collection&lt;? extends GridJob&gt; split(int gridSize, List&lt;Integer&gt; cacheKeys) throws GridException {
 *         List&lt;MyGridAffinityJob&gt; jobs = new ArrayList&lt;MyGridAffinityJob&gt;(gridSize);
 *
 *         for (Integer cacheKey : cacheKeys) {
 *             jobs.add(new MyFooBarCoherenceAffinityJob(cacheKey));
 *         }
 *
 *         // Node assignment via load balancer
 *         // happens automatically.
 *         return jobs;
 *     }
 *     ...
 * }
 * </pre>
 * Here is the example of grid jobs created by the task above:
 * <pre name="code" class="java">
 * public class MyFooBarCoherenceAffinityJob extends GridCoherenceAffinityJobAdapter&lt;Integer, Serializable&gt; {
 *    ...
 *    private static final String CACHE_NAME = "myDistributedCache";
 *
 *    public MyFooBarCoherenceAffinityJob(Integer cacheKey) {
 *        super(CACHE_NAME, cacheKey);
 *    }
 *
 *    public Serializable execute() throws GridException {
 *        ...
 *        // Access data by the same key returned in 'getAffinityKey()' method
 *        // and for cache with name returned in 'getCacheName()'.
 *        NamedCache mycache = CacheFactory.cache(getCacheName);
 *
 *        mycache.get(getAffinityKey());
 *        ...
 *    }
 * }
 * </pre>
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has no optional configuration parameters.
 * <p>
 * Below is a Java example of configuration for Coherence affinity load balancing SPI:
 * <pre name="code" class="java">
 * GridCoherenceLoadBalancingSpi spi = new GridCoherenceLoadBalancingSpi();
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default load balancing SPI.
 * cfg.setLoadBalancingSpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * Here is Spring XML configuration example:
 * <pre name="code" class="xml">
 * &lt;property name="loadBalancingSpi">
 *     &lt;bean class="org.gridgain.grid.spi.loadBalancing.coherence.GridCoherenceLoadBalancingSpi"/&gt;
 * &lt;/property&gt;
 * </pre>
 * <p>
 * For more information, how to create and use Coherence distributed cache see
 * <a target=_blank href="http://wiki.tangosol.com/display/COH33UG/Partitioned+Cache+Service">Partitioned Cache Service</a>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridCoherenceLoadBalancingSpi extends GridSpiAdapter implements GridLoadBalancingSpi,
    GridCoherenceLoadBalancingSpiMBean {
    /** Random number generator. */
    private Random rand = new Random();

    /** Grid logger. */
    @GridLoggerResource private GridLogger log;

    /** */
    private GridLocalEventListener lsnr;

    /** Maps coherence members to grid nodes. */
    private Map<Integer, GridNode> mbrIdMap = new HashMap<Integer, GridNode>();

    /** Maps GridNode ID to Coherence member ID. */
    private Map<UUID, Integer> nodeIdMap = new HashMap<UUID, Integer>();

    /** */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String gridName) throws GridSpiException {
        startStopwatch();

        registerMBean(gridName, this, GridCoherenceLoadBalancingSpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        mbrIdMap = null;
        nodeIdMap = null;

        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(final GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().addLocalEventListener(lsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                rwLock.writeLock().lock();

                try {
                    if (evt.type() == EVT_NODE_JOINED) {
                        GridNode node = spiCtx.node(discoEvt.eventNodeId());

                        if (node != null) {
                            GridCoherenceMember gridMbr = (GridCoherenceMember)node.attribute(
                                GridCoherenceDiscoverySpi.ATTR_COHERENCE_MBR);

                            if (gridMbr == null) {
                                U.error(log, "GridCoherenceLoadBalancingSpi can only be used with " +
                                    "GridCoherenceDiscoverySpi.");

                                return;
                            }

                            mbrIdMap.put(gridMbr.getId(), node);
                            nodeIdMap.put(node.id(), gridMbr.getId());

                            if (log.isDebugEnabled()) {
                                log.debug("Added node: " + node);
                            }
                        }
                    }
                    else {
                        Integer mbrId = nodeIdMap.get(discoEvt.eventNodeId());

                        if (mbrId != null) {
                            mbrIdMap.remove(mbrId);
                            nodeIdMap.remove(discoEvt.eventNodeId());

                            if (log.isDebugEnabled()) {
                                log.debug("Removed node: " + discoEvt.eventNodeId());
                            }
                        }
                    }
                }
                finally {
                    rwLock.writeLock().unlock();
                }
            }
        },
            EVT_NODE_FAILED,
            EVT_NODE_JOINED,
            EVT_NODE_LEFT
        );

        rwLock.writeLock().lock();

        try {
            for (GridNode node : getSpiContext().remoteNodes()) {
                GridCoherenceMember gridMbr = (GridCoherenceMember)node.attribute(
                    GridCoherenceDiscoverySpi.ATTR_COHERENCE_MBR);

                if (gridMbr == null) {
                    U.error(log, "GridCoherenceLoadBalancingSpi can only be used with GridCoherenceDiscoverySpi.");

                    continue;
                }

                if (!mbrIdMap.containsKey(gridMbr.getId())) {
                    mbrIdMap.put(gridMbr.getId(), node);

                    if (log.isDebugEnabled()) {
                        log.debug("Added node: " + node);
                    }
                }
            }
        }
        finally {
            rwLock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        if (lsnr != null) {
            GridSpiContext ctx = getSpiContext();

            if (ctx != null) {
                ctx.removeLocalEventListener(lsnr);
            }
        }

        super.onContextDestroyed();
    }

    /** {@inheritDoc} */
    @Override public GridNode getBalancedNode(GridTaskSession ses, List<GridNode> top, GridJob job) throws GridException {
        A.notNull(ses, "ses");
        A.notNull(top, "top");
        A.notNull(job, "job");

        if (job instanceof GridCoherenceAffinityJob) {
            GridCoherenceAffinityJob<?> affJob = (GridCoherenceAffinityJob<?>)job;

            Object key = affJob.getAffinityKey();

            if (key != null ) {
                // Look for node through of Coherence cache key.
                NamedCache cache = CacheFactory.getCache(affJob.getCacheName());

                if (cache == null) {
                    throw new GridException("Failed to find cache for name: " + affJob.getCacheName());
                }

                CacheService svc = cache.getCacheService();

                if (!(svc instanceof PartitionedService)) {
                    throw new GridException("Cache is not coherence 'partitioned' cache: " + affJob.getCacheName());
                }

                Member mbr = ((PartitionedService)svc).getKeyOwner(key);

                if (mbr != null) {
                    GridNode resNode = null;

                    rwLock.readLock().lock();

                    try {
                        resNode = mbrIdMap.get(mbr.getId());

                        if (log.isDebugEnabled()) {
                            log.debug("Getting balanced node for data [key=" + key + ", gridNodesSize=" +
                                mbrIdMap.size() + ", mbr=" + new GridCoherenceMember(mbr) + ", node=" + resNode + ']');
                        }
                    }
                    finally {
                        rwLock.readLock().unlock();
                    }

                    if (resNode != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Picked coherence affinity node for specified cache key [cacheKey=" + key +
                                ", node=" + resNode + ']');
                        }

                        return resNode;
                    }
                    else if (log.isDebugEnabled()) {
                        log.debug("Coherence affinity key owner is not a member of task topology [coherenceMbr=" + mbr +
                            ", taskTopology=" + top + ']');
                    }
                }
            }
            else if (log.isDebugEnabled()) {
                log.debug("No cache key was passed to coherence load balancer (random node will be picked).");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Coherence affinity node could not be picked (random node will be used): " + job);
        }

        return top.get(rand.nextInt(top.size()));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceLoadBalancingSpi.class, this);
    }
}
