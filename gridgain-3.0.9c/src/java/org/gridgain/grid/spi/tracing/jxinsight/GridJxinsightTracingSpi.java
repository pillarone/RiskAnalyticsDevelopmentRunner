// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.tracing.jxinsight;

import com.jinspired.jxinsight.trace.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.tracing.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * Tracing SPI implementation that receives method apply notifications from local grid
 * and informs JXInsight Tracer.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has no optional configuration parameters.
 * <b>Note</b>: JXInsight is not shipped with GridGain. If you don't have JXInsight, you need to
 * download it separately. See <a target=_blank href="http://www.jinspired.com/products/jxinsight">http://www.jinspired.com/products/jxinsight</a>
 * for more information. Once installed, JXInsight should be available on the classpath for
 * GridGain. If you use {@code ${GRIDGAIN_HOME}/bin/ggstart.{sh|bat}} script to start
 * a grid node you can simply add JXInsight JARs to {@code ${GRIDGAIN_HOME}/bin/setenv.{sh|bat}}
 * scripts that's used to set up class path for the main scripts.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(false)
public class GridJxinsightTracingSpi extends GridSpiAdapter implements GridTracingSpi, GridJxinsightTracingSpiMBean {
    /** Logger. */
    @GridLoggerResource private GridLogger log;

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        registerMBean(gridName, this, GridJxinsightTracingSpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled() ==true) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void beforeCall(Class<?> cls, String methodName, Object[] args) {
        if (Tracer.isEnabled()) {
            Tracer.start(cls.getSimpleName() + '.' + methodName);
        }
    }

    /** {@inheritDoc} */
    @Override public void afterCall(Class<?> cls, String methodName, Object[] args, Object res, Throwable exc) {
        if (Tracer.isEnabled()) {
            String trace = cls.getSimpleName() + '.' + methodName;

            if (Tracer.getCurrentTrace().equals(trace))  {
                Tracer.stop();
            }
            else {
                U.error(log, "Unable to close trace as it was not started in this thread (ignoring): " + trace);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJxinsightTracingSpi.class, this);
    }
}
