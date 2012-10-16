// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test;

import org.gridgain.grid.*;
import org.gridgain.grid.test.junit3.*;
import java.util.*;

/**
 * Optional router used to route individual tests to remote nodes for
 * execution. Distributed JUnits can be instructed to use a specific
 * implementation of this router in one of the following ways:
 * <ul>
 * <li>By specifying router class name as {@link GridTestVmParameters#GRIDGAIN_TEST_ROUTER} VM parameter</li>
 * <li>By specifying router class from {@link GridifyTest#routerClass()} annotation method.</li>
 * <li>By setting it on test suite explicitely by calling {@link GridJunit3TestSuite#setRouterClass(Class)} method.</li>
 * </ul>
 * If not provided, then by default {@link GridTestRouterAdapter} is used which routes tests to
 * grid nodes in round-robin fashion. Refer to {@link GridTestRouterAdapter} documentation for
 * more information.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTestRouter {
    /**
     * Routes a test to a specific node. This method is provided so subclasses
     * could specify their own logic for routing tests. It should be useful
     * when some test can only be run on a specific node or on a specific
     * set of nodes.
     * <p>
     * By default tests are routed to grid nodes in round-robin fashion.
     * Set {@link GridTestVmParameters#GRIDGAIN_ROUTER_PREFER_REMOTE} VM parameter
     * to {@code true} to always route tests to remote nodes, assuming there are any.
     * If there are no remote nodes, then tests will still execute locally even
     * if this VM parameter is set.
     *
     * @param test Test class.
     * @param name Test name.
     * @param subgrid List of available grid nodes.
     * @param locNodeId Local node ID.
     * @return Node this test should execute on.
     */
    public GridNode route(Class<?> test, String name, Collection<GridNode> subgrid, UUID locNodeId);
}
