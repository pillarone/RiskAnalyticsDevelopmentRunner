// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import java.util.concurrent.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridExecutorServiceMBeanAdapter implements GridExecutorServiceMBean {
    /** */
    private final ExecutorService exec;

    /**
     * Creates adapter.
     *
     * @param exec Executor service
     */
    public GridExecutorServiceMBeanAdapter(ExecutorService exec) {
        assert exec != null;

        this.exec = exec;
    }

    /** {@inheritDoc} */
    @Override public int getActiveCount() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getActiveCount() : -1;
    }

    /** {@inheritDoc} */
    @Override public long getCompletedTaskCount() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getCompletedTaskCount() : -1;
    }

    /** {@inheritDoc} */
    @Override public int getCorePoolSize() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getCorePoolSize() : -1;
    }

    /** {@inheritDoc} */
    @Override public int getLargestPoolSize() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getLargestPoolSize() : -1;
    }

    /** {@inheritDoc} */
    @Override public int getMaximumPoolSize() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getMaximumPoolSize() : -1;
    }

    /** {@inheritDoc} */
    @Override public int getPoolSize() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getPoolSize() : -1;
    }

    /** {@inheritDoc} */
    @Override public long getTaskCount() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getTaskCount() : -1;
    }

    /** {@inheritDoc} */
    @Override public int getQueueSize() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getQueue().size() : -1;
    }

    /** {@inheritDoc} */
    @Override public long getKeepAliveTime() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ?
            ((ThreadPoolExecutor)exec).getKeepAliveTime(TimeUnit.MILLISECONDS) : -1;
    }

    /** {@inheritDoc} */
    @Override public boolean isShutdown() {
        assert exec != null;

        return exec.isShutdown();
    }

    /** {@inheritDoc} */
    @Override public boolean isTerminated() {
        return exec.isTerminated();
    }

    /** {@inheritDoc} */
    @Override public boolean isTerminating() {
        assert exec != null;

        return exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).isTerminating() : false;
    }

    /** {@inheritDoc} */
    @Override public String getRejectedExecutionHandlerClass() {
        assert exec != null;

        if (exec instanceof ThreadPoolExecutor == false) {
            return "";
        }

        RejectedExecutionHandler handler = ((ThreadPoolExecutor)exec).getRejectedExecutionHandler();

        return handler == null ? "" : handler.getClass().getName();
    }

    /** {@inheritDoc} */
    @Override public String getThreadFactoryClass() {
        assert exec != null;

        if (exec instanceof ThreadPoolExecutor == false) {
            return "";
        }

        ThreadFactory factory = ((ThreadPoolExecutor)exec).getThreadFactory();

        return factory == null ? "" : factory.getClass().getName();
    }
}
