// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.stopwatch;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Stopwatch.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public interface GridStopwatch {
    /**
     * @return Watch name.
     */
    public GridStopWatchName name();

    /**
     * Reset watch explicitly (watch is automatically reset on creation).
     */
    public void watch();

    /**
     * Stop watch.
     */
    public void stop();

    /**
     * Checkpoint a single step.
     *
     * @param stepName Step name.
     */
    public void step(String stepName);

    /**
     * Checkpoint a single step and then stop this watch. This method is analogous
     * to calling:
     * <pre>
     *     step(stepName);
     *     stop();
     * </pre>
     *
     * @param stepName Step name.
     */
    public void lastStep(String stepName);

    /**
     * @return Map of steps keyed by name containing execution count and total time for the step.
     */
    public Map<GridStopWatchName, T2<AtomicInteger, AtomicLong>> steps();
}
