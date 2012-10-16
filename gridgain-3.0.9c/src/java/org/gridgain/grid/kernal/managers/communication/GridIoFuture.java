// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

/**
 * Future for synchronous communication messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIoFuture implements Iterable<GridIoResult>, GridTimeoutObject {
    /** */
    private final Map<UUID, GridIoResult> resMap;

    /** */
    private final List<GridIoResult> completedRes;

    /** */
    private final GridIoResultListener lsnr;

    /** */
    private final UUID timeoutObjId;

    /** Timeout processor. */
    private final GridTimeoutProcessor timeoutProc;

    /** End time. */
    private final long endTime;

    /** Logger. */
    private final GridLogger log;

    /**
     * @param resMap Result map.
     * @param timeout Future timeout.
     * @param ctx Kernal context.
     * @param lsnr Listener.
     */
    @SuppressWarnings({"IfMayBeConditional"})
    GridIoFuture(Map<UUID, GridIoResult> resMap, long timeout,
        GridKernalContext ctx, GridIoResultListener lsnr) {
        this.resMap = resMap;

        this.lsnr = lsnr;

        timeoutProc = ctx.timeout();

        log = ctx.config().getGridLogger().getLogger(getClass());

        completedRes = new ArrayList<GridIoResult>(resMap.size());

        for (GridIoResult commRes : resMap.values()) {
            commRes.setFuture(this);
        }

        if (timeout > 0 && !resMap.isEmpty()) {
            timeoutObjId = UUID.randomUUID();

            endTime = System.currentTimeMillis() + timeout;

            timeoutProc.addTimeoutObject(this);
        }
        else if (timeout < 0) {
            timeoutObjId = null;

            endTime = System.currentTimeMillis();
        }
        else {
            timeoutObjId = null;
            
            endTime = Long.MAX_VALUE;
        }
    }

    /** {@inheritDoc} */
    @Override public UUID timeoutId() {
        return timeoutObjId;
    }

    /** {@inheritDoc} */
    @Override public long endTime() {
        return endTime;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public void onTimeout() {
        Throwable ex = new GridIoTimeoutException(
            "Synchronous request timed out [timeoutId=" + timeoutObjId + ']');

        for (GridIoResult res : resMap.values()) {
            // This will be no-op for completed results.
            res.setException(ex);
        }
    }

    /**
     * @return All results.
     * @throws InterruptedException If thread was interrupted.
     */
    public Collection<GridIoResult> getAllResults() throws InterruptedException {
        synchronized (completedRes) {
            while (completedRes.size() != resMap.size()) {
                completedRes.wait(5000);
            }

            return completedRes;
        }
    }

    /**
     * @return Completed results.
     */
    public Collection<GridIoResult> getCompletedResults() {
        synchronized (completedRes) {
            return new ArrayList<GridIoResult>(completedRes);
        }
    }

    /**
     * @param nodeId Node id.
     * @return Result.
     * @throws InterruptedException If thread was interrupted.
     */
    public GridIoResult getResult(UUID nodeId) throws InterruptedException {
        assert nodeId != null;

        GridIoResult res = resMap.get(nodeId);

        synchronized (completedRes) {
            while (!completedRes.contains(res)) {
                completedRes.wait(5000);
            }
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridIoResult> iterator() {
        return new Iterator<GridIoResult>() {
            /** */
            @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
            private int idx;

            /** {@inheritDoc} */
            @Override public boolean hasNext() {
                return idx < resMap.size();
            }

            /** {@inheritDoc} */
            @Override public GridIoResult next() {
                if (idx == resMap.size()) {
                    throw new NoSuchElementException();
                }

                try {
                    synchronized (completedRes) {
                        while (idx == completedRes.size()) {
                            completedRes.wait(5000);
                        }

                        return completedRes.get(idx++);
                    }
                }
                catch (InterruptedException e) {
                    throw new GridRuntimeException("Thread has been interrupted", e);
                }
            }

            /** {@inheritDoc} */
            @Override public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    /**
     * @return IO result.
     * @throws InterruptedException If thread was interrupted.
     */
    public GridIoResult getFirstResult() throws InterruptedException {
        synchronized (completedRes) {
            while (completedRes.isEmpty()) {
                completedRes.wait(5000);
            }

            return completedRes.get(0);
        }
    }

    /**
     * @return {@code True} if completed.
     */
    public boolean isDone() {
        synchronized (completedRes) {
            return completedRes.size() == resMap.size();
        }
    }

    /**
     * @param res Result.
     */
    void onResult(GridIoResult res) {
        assert res != null;

        if (log.isDebugEnabled()) {
            log.debug("Received communication result: " + res);
        }

        synchronized (completedRes) {
            completedRes.add(res);

            completedRes.notifyAll();

            if (completedRes.size() == resMap.size() && timeoutObjId != null) {
                timeoutProc.removeTimeoutObject(this);

                if (log.isDebugEnabled()) {
                    log.debug("Removed timeout object for future: " + timeoutObjId);
                }
            }
        }

        if (lsnr != null) {
            lsnr.onResult(res);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridIoFuture.class, this);
    }
}
