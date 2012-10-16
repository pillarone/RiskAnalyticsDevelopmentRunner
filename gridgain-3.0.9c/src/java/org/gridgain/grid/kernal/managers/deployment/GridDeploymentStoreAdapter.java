// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

/**
 * Adapter for all store implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
abstract class GridDeploymentStoreAdapter implements GridDeploymentStore {
    /** Logger. */
    protected final GridLogger log;

    /** Deployment SPI. */
    protected final GridDeploymentSpi spi;

    /** Kernal context. */
    protected final GridKernalContext ctx;

    /** Deployment communication. */
    protected final GridDeploymentCommunication comm;

    /**
     * @param spi Underlying SPI.
     * @param ctx Grid kernal context.
     * @param comm Deployment communication.
     */
    GridDeploymentStoreAdapter(GridDeploymentSpi spi, GridKernalContext ctx, GridDeploymentCommunication comm) {
        assert spi != null;
        assert ctx != null;
        assert comm != null;

        this.spi = spi;
        this.ctx = ctx;
        this.comm = comm;

        log = ctx.config().getGridLogger().getLogger(getClass());
    }

    /**
     * @return Startup log message.
     */
    protected final String startInfo() {
        return "Deployment store started: " + this;
    }

    /**
     * @return Stop log message.
     */
    protected final String stopInfo() {
        return "Deployment store stopped: " + this;
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        if (log.isDebugEnabled())
            log.debug("Ignoring kernel started callback: " + this);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDeployment explicitDeploy(Class<?> cls, ClassLoader clsLdr) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Ignoring explicit deploy [cls=" + cls + ", clsLdr=" + clsLdr + ']');

        return null;
    }

    /**
     * @param ldr Class loader.
     * @return User version.
     */
    protected final String getUserVersion(ClassLoader ldr) {
        return U.getUserVersion(ldr, log);
    }

    /**
     * @param cls Class to check.
     * @return {@code True} if class is task class.
     */
    protected final boolean isTask(Class<?> cls) {
        return GridTask.class.isAssignableFrom(cls);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentStoreAdapter.class, this);
    }
}
