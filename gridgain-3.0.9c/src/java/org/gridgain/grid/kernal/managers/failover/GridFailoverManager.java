// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.failover;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.spi.failover.*;
import java.util.*;

/**
 * Grid failover spi manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridFailoverManager extends GridManagerAdapter<GridFailoverSpi> {
    /**
     * @param ctx Kernal context.
     */
    public GridFailoverManager(GridKernalContext ctx) {
        super(GridFailoverSpi.class, ctx, ctx.config().getFailoverSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        startSpi();

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /**
     * @param taskSes Task session.
     * @param jobRes Job result.
     * @param top Collection of all top nodes that does not include the failed node.
     * @return New node to route this job to.
     */
    public GridNode failover(GridTaskSessionImpl taskSes, GridJobResult jobRes, List<GridNode> top) {
        return getSpi(taskSes.getFailoverSpi()).failover(new GridFailoverContextImpl(taskSes, jobRes,
            ctx.loadBalancing()), top);
    }
}
