// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.concurrent.atomic.*;

/**
 * Adapter for cache managers.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheManager<K, V> {
    /** Context. */
    protected GridCacheContext<K, V> cctx;

    /** Logger. */
    protected GridLogger log;

    /** Starting flag. */
    private final AtomicBoolean starting = new AtomicBoolean(false);

    /**
     * Starts manager.
     *
     * @param cctx Context.
     * @throws GridException If failed.
     */
    public final void start(GridCacheContext<K, V> cctx) throws GridException {
        if (!starting.compareAndSet(false, true))
            assert false : "Method start is called more than once for manager: " + this;

        assert cctx != null;

        this.cctx = cctx;

        log = cctx.logger(getClass());

        start0();
        
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * @return Logger.
     */
    protected GridLogger log() {
        return log;
    }

    /**
     * @return Context.
     */
    protected GridCacheContext<K, V> context() {
        return cctx;
    }

    /**
     * @throws GridException If failed.
     */
    protected void start0() throws GridException {
        // No-op.
    }

    /**
     * Stops manager.
     *
     * @param cancel Cancel flag.
     * @param wait Wait flag.
     */
    public final void stop(boolean cancel, boolean wait) {
        stop0(cancel, wait);
        
        if (log != null && log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @param cancel Cancel flag.
     * @param wait Wait flag.
     */
    protected void stop0(boolean cancel, boolean wait) {
        // No-op.
    }

    /**
     * @throws GridException If failed.
     */
    public final void onKernalStart() throws GridException {
        onKernalStart0();

        if (log != null && log.isDebugEnabled())
            log.debug(kernalStartInfo());
    }

    /**
     * 
     */
    public final void onKernalStop() {
        onKernalStop0();
        
        if (log != null && log.isDebugEnabled())
            log.debug(kernalStopInfo());
    }

    /**
     * @throws GridException If failed.
     */
    protected void onKernalStart0() throws GridException {
        // No-op.
    }

    /**
     * 
     */
    protected void onKernalStop0() {
        // No-op.
    }

    /**
     * @return Start info.
     */
    protected String startInfo() {
        return "Cache manager started: " + cctx.name();
    }

    /**
     * @return Stop info.
     */
    protected String stopInfo() {
        return "Cache manager stopped: " + cctx.name();
    }

    /**
     * @return Start info.
     */
    protected String kernalStartInfo() {
        return "Cache manager received onKernalStart() callback: " + cctx.name();
    }

    /**
     * @return Stop info.
     */
    protected String kernalStopInfo() {
        return "Cache manager received onKernalStop() callback: " + cctx.name();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheManager.class, this);
    }
}
