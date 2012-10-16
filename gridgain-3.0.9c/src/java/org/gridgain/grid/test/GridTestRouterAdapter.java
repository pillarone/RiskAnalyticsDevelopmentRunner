// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

import static org.gridgain.grid.test.GridTestVmParameters.*;

/**
 * Round-Robin implementation of {@link GridTestRouter} interface.
 * The implementation makes sure that nodes are assigned to tests
 * sequentially, one after another.
 * <p>
 * Note that if {@link GridTestVmParameters#GRIDGAIN_ROUTER_PREFER_REMOTE} VM parameter
 * is set to {@code true}, then tests will be routed to remote nodes assuming there
 * are any. If there are no remote nodes, tests will be routed to local node.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTestRouterAdapter implements GridTestRouter {
    /** */
    private LinkedList<GridNode> nodes = new LinkedList<GridNode>();

    /**
     * Check if local node should not execute distributed tests by checking
     * {@link GridTestVmParameters#GRIDGAIN_ROUTER_PREFER_REMOTE} system
     * property.
     */
    private boolean isPreferRemote = Boolean.parseBoolean(System.getProperty(GRIDGAIN_ROUTER_PREFER_REMOTE.name()));

    /** {@inheritDoc} */
    @Override public synchronized GridNode route(Class<?> test, String name, Collection<GridNode> subgrid,
        UUID locNodeId) {
        assert !F.isEmpty(subgrid);

        // Remove old nodes.
        nodes.retainAll(subgrid);

        for (GridNode node : subgrid) {
            if (!nodes.contains(node)) {
                nodes.addFirst(node);
            }
        }

        if (nodes.size() == 1) {
            return nodes.getFirst();
        }

        // Rotate the first node to the end of the list.
        nodes.addLast(nodes.removeFirst());

        if (isPreferRemote) {
            while (nodes.getLast().id().equals(locNodeId)) {
                // Rotate the first node to the end of the list.
                nodes.addLast(nodes.removeFirst());
            }
        }

        // Return the node that was just rotated.
        return nodes.getLast();
    }
}
