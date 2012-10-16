// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.metrics;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.spi.metrics.*;

/**
 * This class defines a grid local metric manager manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLocalMetricsManager extends GridManagerAdapter<GridLocalMetricsSpi> {
    /**
     * @param ctx Grid kernal context.
     */
    public GridLocalMetricsManager(GridKernalContext ctx) {
        super(GridLocalMetricsSpi.class, ctx, ctx.config().getMetricsSpi());
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
     * Gets local VM metrics.
     *
     * @return Local VM metrics.
     */
    public GridLocalMetrics getMetrics() {
        return getSpi().getMetrics();
    }
}
