// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.*;
import org.gridgain.grid.kernal.controllers.affinity.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.kernal.controllers.rest.*;
import org.gridgain.grid.kernal.managers.*;
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
import org.gridgain.grid.kernal.processors.*;
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
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.kernal.GridKernalState.*;

/**
 * Implementation of kernal context.
 * 
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridKernalContextImpl extends GridMetadataAwareAdapter implements GridKernalContext, Externalizable {
    /** */
    private static final ThreadLocal<String> stash = new ThreadLocal<String>();

    /** */
    private GridManagerRegistry mgrReg;
    
    /** */
    private GridProcessorRegistry procReg;
    
    /** */
    private GridControllerRegistry ctrlReg;

    /** */
    private Grid grid;
    
    /** */
    private GridConfiguration cfg;
    
    /** */
    private GridKernalGateway gw;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridKernalContextImpl() {
        // No-op.
    }

    /**
     * Creates new kernal context.
     *
     * @param mgrReg Managers registry.
     * @param procReg Processors registry.
     * @param ctrlReg Controller registry.
     * @param grid Grid instance managed by kernal.
     * @param cfg Grid configuration.
     * @param gw Kernal gateway.
     */
    GridKernalContextImpl(GridManagerRegistry mgrReg, GridProcessorRegistry procReg, GridControllerRegistry ctrlReg,
        Grid grid, GridConfiguration cfg, GridKernalGateway gw) {
        assert mgrReg != null;
        assert procReg != null;
        assert ctrlReg != null;
        assert grid != null;
        assert cfg != null;
        assert gw != null;

        this.mgrReg = mgrReg;
        this.procReg = procReg;
        this.ctrlReg = ctrlReg;
        this.grid = grid;
        this.cfg = cfg;
        this.gw = gw;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, grid.name());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        stash.set(U.readString(in));
    }

    /** {@inheritDoc} */
    @Override public String version() {
        return grid().version();
    }

    /** {@inheritDoc} */
    @Override public String build() {
        return grid().build();
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            return ((GridKernal)G.grid(stash.get())).context();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEnterprise() {
        return U.isEnterprise();
    }

    /** {@inheritDoc} */
    @Override public boolean isStopping() {
        GridKernalState state = gw.getState();

        return state == STOPPING || state == STOPPED;
    }

    /** {@inheritDoc} */
    @Override public GridLicenseController license() {
        return ctrlReg.license();
    }

    /** {@inheritDoc} */
    @Override public UUID localNodeId() {
        return discovery() == null ? cfg.getNodeId() : discovery().localNode().id();
    }

    /** {@inheritDoc} */
    @Override public String gridName() {
        return cfg.getGridName();
    }

    /** {@inheritDoc} */
    @Override public GridKernalGateway gateway() {
        return gw;
    }

    /** {@inheritDoc} */
    @Override public Grid grid() {
        return grid;
    }

    /** {@inheritDoc} */
    @Override public GridRichProcessor rich() {
        return procReg.rich();
    }

    /** {@inheritDoc} */
    @Override public GridConfiguration config() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridTaskProcessor task() {
        return procReg.task();
    }

    /** {@inheritDoc} */
    @Override public GridJobProcessor job() {
        return procReg.job();
    }

    /** {@inheritDoc} */
    @Override public GridTimeoutProcessor timeout() {
        return procReg.timeout();
    }

    /** {@inheritDoc} */
    @Override public GridResourceProcessor resource() {
        return procReg.resource();
    }

    /** {@inheritDoc} */
    @Override public GridJobMetricsProcessor jobMetric() {
        return procReg.metric();
    }

    /** {@inheritDoc} */
    @Override public GridCacheProcessor cache() {
        return procReg.cache();
    }

    /** {@inheritDoc} */
    @Override public GridAffinityController affinity() {
        return ctrlReg.affinity();
    }

    /** {@inheritDoc} */
    @Override public GridTaskSessionProcessor session() {
        return procReg.session();
    }

    /** {@inheritDoc} */
    @Override public GridClosureProcessor closure() {
        return procReg.closure();
    }

    /** {@inheritDoc} */
    @Override public GridPortProcessor ports() {
        return procReg.ports();
    }

    /** {@inheritDoc} */
    @Override public GridEmailProcessor email() {
        return procReg.email();
    }

    /** {@inheritDoc} */
    @Override public GridCloudManager cloud() {
        return mgrReg.cloud();
    }

    /** {@inheritDoc} */
    @Override public GridScheduleProcessor schedule() {
        return procReg.schedule();
    }

    /** {@inheritDoc} */
    @Override public GridRestController rest() {
        return procReg.rest();
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentManager deploy() {
        return mgrReg.deploy();
    }

    /** {@inheritDoc} */
    @Override public GridIoManager io() {
        return mgrReg.io();
    }

    /** {@inheritDoc} */
    @Override public GridDiscoveryManager discovery() {
        return mgrReg.discovery();
    }

    /** {@inheritDoc} */
    @Override public GridCheckpointManager checkpoint() {
        return mgrReg.checkpoint();
    }

    /** {@inheritDoc} */
    @Override public GridEventStorageManager event() {
        return mgrReg.event();
    }

    /** {@inheritDoc} */
    @Override public GridTracingManager tracing() {
        return mgrReg.tracing();
    }

    /** {@inheritDoc} */
    @Override public GridFailoverManager failover() {
        return mgrReg.failover();
    }

    /** {@inheritDoc} */
    @Override public GridTopologyManager topology() {
        return mgrReg.topology();
    }

    /** {@inheritDoc} */
    @Override public GridCollisionManager collision() {
        return mgrReg.collision();
    }

    /** {@inheritDoc} */
    @Override public GridLocalMetricsManager localMetric() {
        return mgrReg.metric();
    }

    /** {@inheritDoc} */
    @Override public GridLoadBalancerManager loadBalancing() {
        return mgrReg.loadBalancing();
    }

    /** {@inheritDoc} */
    @Override public GridSwapSpaceManager swap() {
        return mgrReg.swap();
    }

    /** {@inheritDoc} */
    @Override public GridLogger log() {
        return config().getGridLogger();
    }

    /** {@inheritDoc} */
    @Override public GridLogger log(Class<?> cls) {
        return config().getGridLogger().getLogger(cls);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridKernalContextImpl.class, this);
    }
}
