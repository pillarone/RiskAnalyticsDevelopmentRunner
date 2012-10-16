// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.controllers.affinity.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.kernal.controllers.rest.*;
import org.gridgain.grid.kernal.managers.checkpoint.*;
import org.gridgain.grid.kernal.managers.cloud.*;
import org.gridgain.grid.kernal.managers.collision.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.discovery.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.managers.failover.*;
import org.gridgain.grid.kernal.managers.loadbalancer.*;
import org.gridgain.grid.kernal.managers.metrics.*;
import org.gridgain.grid.kernal.managers.swapspace.*;
import org.gridgain.grid.kernal.managers.topology.*;
import org.gridgain.grid.kernal.managers.tracing.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.closure.*;
import org.gridgain.grid.kernal.processors.email.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.kernal.processors.resource.*;
import org.gridgain.grid.kernal.processors.rich.*;
import org.gridgain.grid.kernal.processors.schedule.*;
import org.gridgain.grid.kernal.processors.session.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public interface GridKernalContext extends GridMetadataAware {
    /**
     * Gets local node ID.
     *
     * @return Local node ID.
     */
    public UUID localNodeId();

    /**
     * Gets grid name.
     *
     * @return Grid name.
     */
    public String gridName();

    /**
     * Ges version string of the GridGain instance. This method is for information
     * purpose only.
     *
     * @return GridGain version string (excluding the build number).
     * @see #build()
     */
    public String version();

    /**
     * Gets build number of this GridGain instance. This method is for information
     * purpose only.
     *
     * @return GridGain instance build number.
     * @see #version()
     */
    public String build();

    /**
     * Gets logger.
     *
     * @return Logger.
     */
    public GridLogger log();

    /**
     * Gets logger for given class.
     *
     * @param cls Class to get logger for.
     * @return Logger.
     */
    public GridLogger log(Class<?> cls);

    /**
     * Tests whether or not this GridGain runtime runs on an enterprise edition. This method
     * is primarily for informational purpose.
     *
     * @return {@code True} for enterprise edition, {@code false} - for community edition.
     * @see GridEnterpriseFeatureException
     * @see GridEnterpriseOnly
     */
    public boolean isEnterprise();

    /**
     * @return {@code True} if grid is in the process of stopping.
     */
    public boolean isStopping();

    /**
     * Gets kernal gateway.
     *
     * @return Kernal gateway.
     */
    public GridKernalGateway gateway();

    /**
     * Gets grid instance managed by kernal.
     *
     * @return Grid instance.
     */
    public Grid grid();
    
    /**
     * Gets grid configuration.
     *
     * @return Grid configuration.
     */
    public GridConfiguration config();

    /**
     * Gets task processor.
     *
     * @return Task processor
     */
    public GridTaskProcessor task();

    /**
     * Gets license controller. Only "implemented" in
     * Enterprise edition.
     *
     * @return License controller.
     */
    public GridLicenseController license();

    /**
     * Gets cache data affinity controller.
     *
     * @return Cache data affinity controller.
     */
    public GridAffinityController affinity();

    /**
     * Gets job processor.
     *
     * @return Job processor
     */
    public GridJobProcessor job();

    /**
     * Gets timeout processor.
     *
     * @return Timeout processor.
     */
    public GridTimeoutProcessor timeout();

    /**
     * Gets resource processor.
     *
     * @return Resource processor.
     */
    public GridResourceProcessor resource();

    /**
     * Gets job metric processor.
     *
     * @return Metrics processor.
     */
    public GridJobMetricsProcessor jobMetric();

    /**
     * Gets caches processor.
     *
     * @return Cache processor.
     */
    public GridCacheProcessor cache();

    /**
     * Gets task session processor.
     *
     * @return Session processor.
     */
    public GridTaskSessionProcessor session();

    /**
     * Gets closure processor.
     *
     * @return Closure processor.
     */
    public GridClosureProcessor closure();

    /**
     * Gets port processor.
     *
     * @return Port processor.
     */
    public GridPortProcessor ports();

    /**
     * Gets email processor.
     *
     * @return Email processor.
     */
    public GridEmailProcessor email();

    /**
     * Gets cloud manager.
     *
     * @return Cloud manager.
     */
    public GridCloudManager cloud();

    /**
     * Gets rich processor.
     *
     * @return Rich processor.
     */
    public GridRichProcessor rich();

    /**
     * Gets schedule processor.
     *
     * @return Schedule processor.
     */
    public GridScheduleProcessor schedule();

    /**
     * Gets REST processor.
     *
     * @return REST processor.
     */
    public GridRestController rest();

    /**
     * Gets deployment manager.
     *
     * @return Deployment manager.
     */
    public GridDeploymentManager deploy();

    /**
     * Gets communication manager.
     *
     * @return Communication manager.
     */
    public GridIoManager io();

    /**
     * Gets discovery manager.
     *
     * @return Discovery manager.
     */
    public GridDiscoveryManager discovery();

    /**
     * Gets checkpoint manager.
     *
     * @return Checkpoint manager.
     */
    public GridCheckpointManager checkpoint();

    /**
     * Gets event storage manager.
     *
     * @return Event storage manager.
     */
    public GridEventStorageManager event();

    /**
     * Gets tracing manager.
     *
     * @return Tracing manager, possibly {@code null} if tracing is disabled.
     */
    public GridTracingManager tracing();

    /**
     * Gets failover manager.
     *
     * @return Failover manager.
     */
    public GridFailoverManager failover();

    /**
     * Gets topology manager.
     *
     * @return Topology manager.
     */
    public GridTopologyManager topology();
    
    /**
     * Gets collision manager.
     * 
     * @return Collision manager.
     */
    public GridCollisionManager collision();

    /**
     * Gets local metrics manager.
     *
     * @return Metrics manager.
     */
    public GridLocalMetricsManager localMetric();

    /**
     * Gets load balancing manager.
     *
     * @return Load balancing manager.
     */
    public GridLoadBalancerManager loadBalancing();

    /**
     * Gets swap space manager.
     * 
     * @return Swap space manager.
     */
    public GridSwapSpaceManager swap();
}
