// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.thread;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import java.util.concurrent.atomic.*;

/**
 * This class adds some necessary plumbing on top of the {@link Thread} class.
 * Specifically, it adds:
 * <ul>
 *      <li>Consistent naming of threads</li>
 *      <li>Dedicated parent thread group</li>
 *      <li>Backing interrupted flag</li>
 * </ul>
 * <b>Note</b>: this class is intended for internal use only.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridThread extends Thread {
    /** Default thread's group. */
    public static final ThreadGroup DFLT_GRP = new ThreadGroup("gridgain");

    /** Number of all grid threads in the system. */
    private static final AtomicLong threadCntr = new AtomicLong(0);

    /**
     * Creates thread with given worker.
     *
     * @param worker Runnable to create thread with.
     */
    public GridThread(GridWorker worker) {
        this(DFLT_GRP, worker.gridName(), worker.name(), worker);
    }

    /**
     * Creates grid thread with given name for a given grid.
     *
     * @param gridName Name of grid this thread is created for.
     * @param threadName Name of thread.
     * @param r Runnable to execute.
     */
    public GridThread(String gridName, String threadName, Runnable r) {
        this(DFLT_GRP, gridName, threadName, r);
    }

    /**
     * Creates grid thread with given name for a given grid with specified
     * thread group.
     *
     * @param grp Thread group.
     * @param gridName Name of grid this thread is created for.
     * @param threadName Name of thread.
     * @param r Runnable to execute.
     */
    public GridThread(ThreadGroup grp, String gridName, String threadName, Runnable r) {
        super(grp, r, createName(threadCntr.incrementAndGet(), threadName, gridName));
    }

    /**
     * Creates new thread name.
     *
     * @param num Thread number.
     * @param threadName Thread name.
     * @param gridName Grid name.
     * @return New thread name.
     */
    private static String createName(long num, String threadName, String gridName) {
        return threadName + "-#" + num + '%' + gridName + '%';
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridThread.class, this, "name", getName());
    }
}
