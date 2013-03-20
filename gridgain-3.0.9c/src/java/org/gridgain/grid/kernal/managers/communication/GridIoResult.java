// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIoResult {
    /** */
    private boolean isComplete;

    /** */
    private Throwable ex;

    /** */
    private Object res;

    /** */
    private final UUID nodeId;

    /** */
    private GridIoFuture fut;

    /**
     * @param nodeId TODO
     */
    GridIoResult(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * @param fut TODO
     */
    void setFuture(GridIoFuture fut) {
        this.fut = fut;
    }

    /**
     * @return TODO
     */
    public UUID getNodeId() {
        return nodeId;
    }

    /**
     * @return TODO
     */
    public synchronized Throwable getException() {
        assert isComplete;

        return ex;
    }

    /**
     * @return TODO
     */
    public synchronized boolean isSuccess() {
        assert isComplete;

        return ex == null;
    }

    /**
     * @param ex TODO
     */
    void setException(Throwable ex) {
        synchronized (this) {
            if (isComplete) {
                return;
            }

            isComplete = true;

            this.ex = ex;
        }

        if (fut != null) {
            fut.onResult(this);
        }
    }

    /**
     * @param res TODO
     */
    void setResult(Object res) {
        synchronized (this) {
            if (isComplete) {
                return;
            }

            isComplete = true;

            this.res = res;
        }

        if (fut != null) {
            fut.onResult(this);
        }
    }

    /**
     * @param <T> Cast result to any type.
     * @return TODO
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T getResult() {
        assert isComplete;

        return (T)res;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridIoResult.class, this);
    }
}
