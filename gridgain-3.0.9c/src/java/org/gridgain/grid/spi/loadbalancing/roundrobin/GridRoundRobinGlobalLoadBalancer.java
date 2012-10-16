// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.roundrobin;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Load balancer that works in global (not-per-task) mode.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridRoundRobinGlobalLoadBalancer {
    /** */
    @SuppressWarnings({"CollectionDeclaredAsConcreteClass"})
    private final LinkedList<UUID> nodeQueue = new LinkedList<UUID>();

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridSpiContext ctx;

    /** */
    private GridLocalEventListener lsnr;

    /** */
    private final GridLogger log;

    /** */
    private final Object mux = new Object();

    /**
     * @param log Grid logger.
     */
    GridRoundRobinGlobalLoadBalancer(GridLogger log) {
        assert log != null;

        this.log = log;
    }

    /**
     * @param ctx Load balancing context.
     */
    void onContextInitialized(GridSpiContext ctx) {
        synchronized (mux) {
            this.ctx = ctx;
        }

        ctx.addLocalEventListener(
            lsnr = new GridLocalEventListener() {
                @Override public void onEvent(GridEvent evt) {
                    assert evt instanceof GridDiscoveryEvent;

                    GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                    synchronized (mux) {
                        if (evt.type() == EVT_NODE_JOINED) {
                            nodeQueue.addFirst(discoEvt.eventNodeId());
                        }
                        else {
                            assert evt.type() == EVT_NODE_LEFT || evt.type() == EVT_NODE_FAILED;

                            nodeQueue.remove(discoEvt.eventNodeId());
                        }
                    }
                }
            },
            EVT_NODE_FAILED, EVT_NODE_JOINED, EVT_NODE_LEFT
        );

        synchronized (mux) {
            for (GridNode node : ctx.nodes()) {
                if (!nodeQueue.contains(node.id())) {
                    nodeQueue.add(node.id());
                }
            }
        }
    }

    /** */
    void onContextDestroyed() {
        ctx.removeLocalEventListener(lsnr);
    }

    /**
     * THIS METHOD IS USED ONLY FOR TESTING.
     *
     * @return Internal list of nodes.
     */
    List<UUID> getNodeIds() {
        synchronized (mux) {
            return Collections.unmodifiableList(new ArrayList<UUID>(nodeQueue));
        }
    }

    /**
     * Gets balanced node for given topology.
     *
     * @param top Topology to pick from.
     * @return Best balanced node.
     * @throws GridException Thrown in case of any error.
     */
    GridNode getBalancedNode(Collection<GridNode> top) throws GridException {
        assert !F.isEmpty(top);

        Map<UUID, GridNode> topMap = new HashMap<UUID, GridNode>(top.size());

        for (GridNode node : top) {
            topMap.put(node.id(), node);
        }

        synchronized (mux) {
            GridNode found = null;

            for (Iterator<UUID> iter = nodeQueue.iterator(); iter.hasNext();) {
                UUID nodeId = iter.next();

                if (topMap.containsKey(nodeId)) {
                    found = topMap.get(nodeId);

                    iter.remove();

                    break;
                }
            }

            if (found != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Found round-robin node: " + found);
                }

                nodeQueue.addLast(found.getId());

                return found;
            }
        }

        throw new GridException("Task topology does not have alive nodes: " + top);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridRoundRobinGlobalLoadBalancer.class, this);
    }
}
