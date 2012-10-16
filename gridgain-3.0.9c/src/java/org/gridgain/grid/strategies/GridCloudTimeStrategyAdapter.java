// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.strategies;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.concurrent.atomic.*;

/**
 * Time-based strategy adapter that triggers {@link #onTime()} method with configured frequency.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCloudTimeStrategyAdapter extends GridCloudEnabledAdapter implements GridCloudStrategy {
    /** Thread name. */
    private static final String THREAD_NAME = "cloud-time-strategy-worker-thread";

    /** Default frequency value. */
    public static final long DFLT_FREQ = 10000;

    /** Frequency of triggering in milliseconds. */
    private AtomicLong freq = new AtomicLong(DFLT_FREQ);

    /** */
    private GridThread wrk;

    /** Grid instance. */
    @GridInstanceResource
    protected Grid grid;

    /** Logger. */
    @GridLoggerResource
    protected GridLogger log;

    /** {@inheritDoc} */
    @Override public void activate() throws GridException {
        assert grid != null;

        wrk = new GridThread(grid.name(), THREAD_NAME, new CA() {
            @SuppressWarnings({"BusyWait"})
            @Override public void apply()  {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(freq.get());
                    }
                    catch (InterruptedException ignored) {
                        return;
                    }

                    onTime();
                }
            }
        });

        wrk.start();

        if (log.isDebugEnabled())
            log.debug("Grid cloud time strategy activated: " + this);
    }

    /** Called on every time period based on configured frequency. */
    public abstract void onTime();

    /** {@inheritDoc} */
    @Override public void deactivate() {
        assert log != null;

        U.interrupt(wrk);

        U.join(wrk, log);

        wrk = null;
    }

    /**
     * Sets frequency of triggering. This method should be called before activation.
     *
     * @param f Frequency in milliseconds.
     */
    public void setFrequency(long f) {
        A.ensure(f > 0, "f > 0");

        freq.set(f);
    }

    /**
     * Gets frequency of triggering.
     *
     * @return Frequency in milliseconds.
     */
    public long getFrequency() {
        return freq.get();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudTimeStrategyAdapter.class, this);
    }
}
