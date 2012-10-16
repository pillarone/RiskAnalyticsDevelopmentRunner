// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.concurrent.atomic.*;

/**
 * This class provides convenient adapter for threads used by SPIs.
 * This class adds necessary plumbing on top of the {@link Thread} class:
 * <ul>
 * <li>Consistent naming of threads</li>
 * <li>Dedicated parent thread group</li>
 * <li>Backing interrupted flag</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridSpiThread extends Thread {
    /** Default thread's group. */
    public static final ThreadGroup DFLT_GRP = new ThreadGroup("gridgain-spi");

    /** Number of all system threads in the system. */
    private static final AtomicLong cntr = new AtomicLong(0);

    /** Grid logger. */
    private final GridLogger log;

    /**
     * Internally maintained thread interrupted flag to avoid any bug issues with {@link Thread}
     * native implementation. May have been fixed in JDK 5.0.
     */
    private volatile boolean interrupted;

    /**
     * Creates thread with given {@code name}.
     *
     * @param gridName Name of grid this thread is created in.
     * @param name Thread's name.
     * @param log Grid logger to use.
     */
    protected GridSpiThread(String gridName, String name, GridLogger log) {
        super(DFLT_GRP, name + "-#" + cntr.incrementAndGet() + '%' + gridName);

        assert log != null;

        this.log = log;
    }

    /** {@inheritDoc} */
    @Override public boolean isInterrupted() {
        return super.isInterrupted() || interrupted;
    }

    /** {@inheritDoc} */
    @Override public void interrupt() {
        interrupted = true;

        super.interrupt();
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings({"CatchGenericClass"})
    public final void run() {
        try {
            body();
        }
        catch (InterruptedException e) {
            if (log.isDebugEnabled()) {
                log.debug("Caught interrupted exception: " + e);
            }
        }
        // Catch everything to make sure that it gets logged properly and
        // not to kill any threads from the underlying thread pool.
        catch (Throwable e) {
            U.error(log, "Runtime error caught during grid runnable execution: " + this, e);
        }
        finally {
            cleanup();

            if (log.isDebugEnabled()) {
                if (isInterrupted()) {
                    log.debug("Grid runnable finished due to interruption without cancellation: " + getName());
                }
                else {
                    log.debug("Grid runnable finished normally: " + getName());
                }
            }
        }
    }

    /**
     * Should be overridden by child classes if cleanup logic is required.
     */
    protected void cleanup() {
        // No-op.
    }

    /**
     * Body of SPI thread.
     *
     * @throws InterruptedException If thread got interrupted.
     */
    protected abstract void body() throws InterruptedException;

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSpiThread.class, this, "name", getName());
    }
}
