// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.timeout;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import java.util.*;

/**
 * Detects timeout events and processes them.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTimeoutProcessor extends GridProcessorAdapter {
    /** */
    private final GridThread timeoutWorker;

    /** Time-based sorted set for timeout objects. */
    private final GridConcurrentSkipListSet<GridTimeoutObject> timeoutObjs =
        new GridConcurrentSkipListSet<GridTimeoutObject>(new Comparator<GridTimeoutObject>() {
            /** {@inheritDoc} */
            @Override public int compare(GridTimeoutObject o1, GridTimeoutObject o2) {
                long time1 = o1.endTime();
                long time2 = o2.endTime();

                return time1 < time2 ? -1 : time1 > time2 ? 1 : o1.timeoutId().compareTo(o2.timeoutId());
            }
        });

    /** */
    private final Object mux = new Object();

    /**
     * @param ctx Kernal context.
     */
    public GridTimeoutProcessor(GridKernalContext ctx) {
        super(ctx);

        timeoutWorker = new GridThread(ctx.config().getGridName(), "grid-timeout-worker",
            new TimeoutWorker());
    }

    /** {@inheritDoc} */
    @Override public void start() {
        timeoutWorker.start();

        if (log.isDebugEnabled())
            log.debug("Timeout processor started.");
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        timeoutWorker.interrupt();

        try {
            timeoutWorker.join();
        }
        catch (InterruptedException e) {
            throw new GridException("Failed to stop timeout processor.", e);
        }

        if (log.isDebugEnabled())
            log.debug("Timeout processor stopped.");
    }

    /**
     * @param timeoutObj Timeout object.
     */
    public void addTimeoutObject(GridTimeoutObject timeoutObj) {
        if (timeoutObj.endTime() <= 0 || timeoutObj.endTime() == Long.MAX_VALUE)
            // Timeout will never happen.
            return;

        timeoutObjs.add(timeoutObj);

        if (timeoutObjs.firstx() == timeoutObj) {
            synchronized (mux) {
                //noinspection NakedNotify
                mux.notifyAll();
            }
        }
    }

    /**
     * @param timeoutObj Timeout object.
     */
    public void removeTimeoutObject(GridTimeoutObject timeoutObj) {
        timeoutObjs.remove(timeoutObj);
    }

    /**
     * Handles job timeouts.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class TimeoutWorker extends GridWorker {
        /** */
        TimeoutWorker() {
            super(ctx.config().getGridName(), "grid-timeout-worker", log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isCancelled()) {
                long now = System.currentTimeMillis();

                for (Iterator<GridTimeoutObject> iter = timeoutObjs.iterator(); iter.hasNext();) {
                    GridTimeoutObject timeoutObj = iter.next();

                    if (timeoutObj.endTime() <= now) {
                        iter.remove();

                        if (log.isDebugEnabled())
                            log.debug("Timeout has occurred: " + timeoutObj);

                        try {
                            timeoutObj.onTimeout();
                        }
                        catch (Throwable e) {
                            U.error(log, "Error when executing timeout callback: " + timeoutObj, e);
                        }
                    }
                    else {
                        break;
                    }
                }

                synchronized (mux) {
                    while (true) {
                        // Access of the first element must be inside of
                        // synchronization block, so we don't miss out
                        // on thread notification events sent from
                        // 'addTimeoutObject(..)' method.
                        GridTimeoutObject first = timeoutObjs.firstx();

                        if (first != null) {
                            long waitTime = first.endTime() - System.currentTimeMillis();

                            if (waitTime > 0)
                                mux.wait(waitTime);
                            else
                                break;
                        }
                        else {
                            mux.wait(5000);
                        }
                    }
                }
            }
        }
    }
}
