// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.thread;

import java.util.concurrent.*;

/**
 * This class provides implementation of {@link ThreadFactory}  factory
 * for creating grid threads.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridThreadFactory implements ThreadFactory {
    /** Grid name. */
    private final String gridName;

    /**
     * Constructs new thread factory for given grid. All threads will belong
     * to the same default thread group.
     *
     * @param gridName Grid name.
     */
    public GridThreadFactory(String gridName) {
        this.gridName = gridName;
    }

    /** {@inheritDoc} */
    @Override public Thread newThread(Runnable r) {
        return new GridThread(gridName, "gridgain", r);
    }
}
