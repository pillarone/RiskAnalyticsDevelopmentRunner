// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers;

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
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * This class provides centralized registry for kernal managers.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridManagerRegistry implements Iterable<GridManager> {
    /** */
    @GridToStringExclude
    private GridDeploymentManager depMgr;

    /** */
    @GridToStringExclude
    private GridIoManager ioMgr;

    /** */
    @GridToStringExclude
    private GridDiscoveryManager discoMgr;

    /** */
    @GridToStringExclude
    private GridCheckpointManager cpMgr;

    /** */
    @GridToStringExclude
    private GridEventStorageManager evtMgr;

    /** */
    @GridToStringExclude
    private GridFailoverManager failoverMgr;

    /** */
    @GridToStringExclude
    private GridTopologyManager topMgr;

    /** */
    @GridToStringExclude
    private GridCollisionManager colMgr;

    /** */
    @GridToStringExclude
    private GridLoadBalancerManager loadMgr;

    /** */
    @GridToStringExclude
    private GridLocalMetricsManager metricsMgr;

    /** */
    @GridToStringExclude
    private GridTracingManager traceMgr;

    /** */
    @GridToStringExclude
    private GridCloudManager cloudMgr;

    /** */
    @GridToStringExclude
    private GridSwapSpaceManager swapspaceMgr;

    /** */
    @GridToStringExclude
    private List<GridManager> mgrs = new LinkedList<GridManager>();

    /**
     * @param mgr Manager to add.
     */
    public void add(GridManager mgr) {
        assert mgr != null;

        if (mgr instanceof GridDeploymentManager) {
            depMgr = (GridDeploymentManager)mgr;
        }
        else if (mgr instanceof GridIoManager) {
            ioMgr = (GridIoManager)mgr;
        }
        else if (mgr instanceof GridDiscoveryManager) {
            discoMgr = (GridDiscoveryManager)mgr;
        }
        else if (mgr instanceof GridCheckpointManager) {
            cpMgr = (GridCheckpointManager)mgr;
        }
        else if (mgr instanceof GridEventStorageManager) {
            evtMgr = (GridEventStorageManager)mgr;
        }
        else if (mgr instanceof GridFailoverManager) {
            failoverMgr = (GridFailoverManager)mgr;
        }
        else if (mgr instanceof GridTopologyManager) {
            topMgr = (GridTopologyManager)mgr;
        }
        else if (mgr instanceof GridCollisionManager) {
            colMgr = (GridCollisionManager)mgr;
        }
        else if (mgr instanceof GridLocalMetricsManager) {
            metricsMgr = (GridLocalMetricsManager)mgr;
        }
        else if (mgr instanceof GridLoadBalancerManager) {
            loadMgr = (GridLoadBalancerManager)mgr;
        }
        else if (mgr instanceof GridTracingManager) {
            traceMgr = (GridTracingManager)mgr;
        }
        else if (mgr instanceof GridCloudManager) {
            cloudMgr = (GridCloudManager)mgr;
        }
        else if (mgr instanceof GridSwapSpaceManager) {
            swapspaceMgr = (GridSwapSpaceManager)mgr;
        }
        else {
            assert false : "Unknown manager class: " + mgr.getClass();
        }

        mgrs.add(mgr);
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridManager> iterator() {
        return mgrs.iterator();
    }

    /**
     * @return Deployment manager.
     */
    public GridDeploymentManager deploy() {
        return depMgr;
    }

    /**
     * @return Communication manager.
     */
    public GridIoManager io() {
        return ioMgr;
    }

    /**
     * @return Discovery manager.
     */
    public GridDiscoveryManager discovery() {
        return discoMgr;
    }

    /**
     * @return Checkpoint manager.
     */
    public GridCheckpointManager checkpoint() {
        return cpMgr;
    }

    /**
     * @return Event storage manager.
     */
    public GridEventStorageManager event() {
        return evtMgr;
    }

    /**
     * @return Tracing manager, possibly {@code null} if tracing is disabled.
     */
    public GridTracingManager tracing() {
        return traceMgr;
    }

    /**
     * @return Failover manager.
     */
    public GridFailoverManager failover() {
        return failoverMgr;
    }

    /**
     * @return Topology manager.
     */
    public GridTopologyManager topology() {
        return topMgr;
    }

    /**
     * @return Collision manager.
     */
    public GridCollisionManager collision() {
        return colMgr;
    }

    /**
     * @return Metrics manager.
     */
    public GridLocalMetricsManager metric() {
        return metricsMgr;
    }

    /**
     * @return Load balancing manager.
     */
    public GridLoadBalancerManager loadBalancing() {
        return loadMgr;
    }

    /**
     * @return Cloud manager.
     */
    public GridCloudManager cloud() {
        return cloudMgr;
    }

    /**
     *
     * @return Swap space manager.
     */
    public GridSwapSpaceManager swap() {
        return swapspaceMgr;
    }

    /**
     * @return List of started managers.
     */
    public List<GridManager> getManagers() {
        return mgrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridManagerRegistry.class, this);
    }
}
