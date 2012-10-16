// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.failover;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.loadbalancing.*;
import java.util.*;

/**
 * This interface defines a set of operations available to failover SPI
 * one a given failed job.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridFailoverContext {
    /**
     * Gets current task session.
     *
     * @return Grid task session.
     */
    public GridTaskSession getTaskSession();

    /**
     * Gets failed result of job execution.
     *
     * @return Result of a failed job.
     */
    public GridJobResult getJobResult();

    /**
     * Gets the next balanced node for failed job. Internally this method will
     * delegate to load balancing SPI (see {@link GridLoadBalancingSpi} to
     * determine the optimal node for execution.
     *
     * @param top Topology to pick balanced node from.
     * @return The next balanced node.
     * @throws GridException If anything failed.
     */
    public GridNode getBalancedNode(List<GridNode> top) throws GridException;
}
