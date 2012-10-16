// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.metrics;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.*;

/**
 * Grid local metrics SPI allows grid to get metrics on local VM. These metrics
 * are a subset of metrics included into {@link GridNode#getMetrics()} method.
 * This way every grid node can become aware of certain changes on other nodes,
 * such as CPU load for example.
 * <p>
 * GridGain comes with following implementations:
 * <ul>
 *      <li>
 *          {@link org.gridgain.grid.spi.metrics.jdk.GridJdkLocalMetricsSpi} - provides Local VM metrics.
 *      </li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridLocalMetricsSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * Provides local VM metrics.
     *
     * @return Local VM metrics.
     */
    public GridLocalMetrics getMetrics();
}
