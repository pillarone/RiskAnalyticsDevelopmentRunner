// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */
package org.gridgain.grid.kernal.managers.topology;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.topology.*;
import java.util.*;

/**
 * Grid topology spi manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTopologyManager extends GridManagerAdapter<GridTopologySpi> {
    /**
     * @param ctx Grid kernal context.
     */
    public GridTopologyManager(GridKernalContext ctx) {
        super(GridTopologySpi.class, ctx, ctx.config().getTopologySpi());
    }

    /**
     * @throws GridException Thrown in case of any errors.
     */
    @Override public void start() throws GridException {
        startSpi();

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /**
     * @throws GridException Thrown in case of any errors.
     */
    @Override public void stop() throws GridException {
        stopSpi();

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /**
     * @param taskSes Internal task session.
     * @param grid Overall grid available for topology resolution.
     * @return Task topology.
     * @throws GridException Thrown in case of any errors.
     */
    public Collection<? extends GridNode> getTopology(GridTaskSessionInternal taskSes,
        Collection<? extends GridNode> grid) throws GridException {
        try {
            return getSpi(taskSes.getTopologySpi()).getTopology(taskSes, grid);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to get topology for task [taskSes=" + taskSes + ", grid=" + grid + ']', e);
        }
    }
}
