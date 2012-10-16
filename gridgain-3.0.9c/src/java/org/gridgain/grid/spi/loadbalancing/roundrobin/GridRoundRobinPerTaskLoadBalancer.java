// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.roundrobin;

import org.gridgain.grid.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Load balancer for per-task configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridRoundRobinPerTaskLoadBalancer {
    /** */
    private Queue<GridNode> nodeQueue;

    /** Jobs mapped flag. */
    private AtomicBoolean isMapped = new AtomicBoolean(false);

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Call back for job mapped event.
     */
    void onMapped() {
        isMapped.set(true);
    }

    /**
     * Gets balanced node for given topology. This implementation
     * is to be used only from {@link GridTask#map(List, Object)} method
     * and, therefore, does not need to be thread-safe.
     *
     * @param top Topology to pick from.
     * @return Best balanced node.
     */
    GridNode getBalancedNode(List<GridNode> top) {
        assert top != null;
        assert !top.isEmpty();

        boolean readjust = isMapped.get();

        synchronized (mux) {
            // Populate first time.
            if (nodeQueue == null) {
                nodeQueue = new LinkedList<GridNode>(top);
            }

            // If job has been mapped, then it means
            // that it is most likely being failed over.
            // In this case topology might have changed
            // and we need to readjust with every apply.
            if (readjust) {
                // Add missing nodes.
                for (GridNode node : top) {
                    if (nodeQueue.contains(node) == false) {
                        nodeQueue.offer(node);
                    }
                }
            }

            GridNode next = nodeQueue.poll();

            // In case if jobs have been mapped, we need to
            // make sure that queued node is still in topology.
            if (readjust == true && next != null) {
                while (top.contains(next) == false && nodeQueue.isEmpty() == false) {
                    next = nodeQueue.poll();
                }

                // No nodes found and queue is empty.
                if (next != null && top.contains(next) == false) {
                    return null;
                }
            }

            if (next != null) {
                // Add to the end.
                nodeQueue.offer(next);
            }

            return next;
        }
    }

    /**
     * THIS METHOD IS USED ONLY FOR TESTING.
     *
     * @return Internal list of nodes.
     */
    List<GridNode> getNodes() {
        return Collections.unmodifiableList(new ArrayList<GridNode>(nodeQueue));
    }
}
