// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.tracing;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.spi.tracing.*;

/**
 * This class defines a grid tracing manager manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTracingManager extends GridManagerAdapter<GridTracingSpi> {
    /** Interception listener. */
    private GridProxyListener lsnr = new GridMethodsInterceptionListener();

    /** Proxy factory. */
    private GridProxyFactory proxyFactory;

    /**
     * Creates new tracing manager.
     *
     * @param ctx Grid kernal context.
     */
    public GridTracingManager(GridKernalContext ctx) {
        super(GridTracingSpi.class, ctx, ctx.config().getTracingSpi());
    }

    /**
     * Sets proxy factory.
     *
     * @param proxyFactory Proxy factory.
     */
    public void setProxyFactory(GridProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        assert proxyFactory != null;

        startSpi();

        proxyFactory.addListener(lsnr);

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        proxyFactory.removeListener(lsnr);

        stopSpi();

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /**
     * Class handles all interception and redirects to the registered SPI.
     */
    private class GridMethodsInterceptionListener implements GridProxyListener {
        /** {@inheritDoc} */
        @Override public void beforeCall(Class<?> cls, String methodName, Object[] args) {
            for (GridTracingSpi spi : getSpis()) {
                spi.beforeCall(cls, methodName, args);
            }
        }

        /** {@inheritDoc} */
        @Override public void afterCall(Class<?> cls, String methodName, Object[] args, Object res, Throwable exc) {
            for (GridTracingSpi spi : getSpis()) {
                spi.afterCall(cls, methodName, args, res, exc);
            }
        }
    }
}
