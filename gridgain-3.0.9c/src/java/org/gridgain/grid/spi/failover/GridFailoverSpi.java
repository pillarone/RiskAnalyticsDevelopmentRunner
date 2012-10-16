// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.failover;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.*;
import java.util.*;

/**
 * Failover SPI provides developer with ability to supply custom logic for handling
 * failed execution of a grid job. Job execution can fail for a number of reasons:
 * <ul>
 *      <li>Job execution threw an exception (runtime, assertion or error)</li>
 *      <li>Node on which job was execution left topology (crashed or stopped)</li>
 *      <li>Collision SPI on remote node cancelled a job before it got a chance to execute (job rejection).</li>
 * </ul>
 * In all cases failover SPI takes failed job (as failover context) and list of all
 * grid nodes and provides another node on which the job execution will be retried.
 * It is up to failover SPI to make sure that job is not mapped to the node it
 * failed on. The failed node can be retrieved from
 * {@link GridJobResult#getNode() GridFailoverContext.getJobResult().node()}
 * method.
 * <p>
 * GridGain comes with the following built-in failover SPI implementations:
 * <ul>
 *      <li>{@link org.gridgain.grid.spi.failover.never.GridNeverFailoverSpi}</li>
 *      <li>{@link org.gridgain.grid.spi.failover.always.GridAlwaysFailoverSpi}</li>
 *      <li>{@link org.gridgain.grid.spi.failover.jobstealing.GridJobStealingFailoverSpi}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridFailoverSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * This method is called when method {@link GridTask#result(GridJobResult, List)} returns
     * value {@link GridJobResultPolicy#FAILOVER} policy indicating that the result of
     * job execution must be failed over. Implementation of this method should examine failover
     * context and choose one of the grid nodes from supplied {@code topology} to retry job execution
     * on it. For best performance it is advised that {@link GridFailoverContext#getBalancedNode(List)}
     * method is used to select node for execution of failed job.
     *
     * @param ctx Failover context.
     * @param top Collection of all grid nodes within task topology (may include failed node).
     * @return New node to route this job to or {@code null} if new node cannot be picked.
     *      If job failover fails (returns {@code null}) the whole task will be failed.
     */
    public GridNode failover(GridFailoverContext ctx, List<GridNode> top);
}
