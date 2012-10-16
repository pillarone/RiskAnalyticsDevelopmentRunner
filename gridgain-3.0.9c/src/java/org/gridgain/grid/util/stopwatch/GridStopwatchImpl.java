// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.util.stopwatch;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Accumulates execution statistics for named pieces of code.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
class GridStopwatchImpl implements GridStopwatch {
    /** */
    private static ConcurrentMap<String, GridStopWatchName> names =
        new ConcurrentSkipListMap<String, GridStopWatchName>();

    /** */
    private static AtomicInteger cntr = new AtomicInteger();

    /** */
    private GridStopWatchName name;

    /** */
    private volatile boolean printSteps;

    /** */
    private volatile long start;

    /** */
    private volatile long checkpoint;

    /** */
    private final ConcurrentMap<GridStopWatchName, T2<AtomicInteger, AtomicLong>> steps =
        new ConcurrentSkipListMap<GridStopWatchName, T2<AtomicInteger, AtomicLong>>();

    /**
     * @param name Watch name.
     */
    GridStopwatchImpl(GridStopWatchName name) {
        this(name, true);
    }

    /**
     * @param name Watch name.
     * @param printSteps If {@code true}, then steps will be printed.
     */
    GridStopwatchImpl(GridStopWatchName name, boolean printSteps) {
        this.name = name;
        this.printSteps = printSteps;

        watch();
    }

    /**
     * @param n Name.
     * @return Name.
     */
    private static GridStopWatchName stepName(String n) {
        GridStopWatchName name = names.get(n);

        if (name != null)
            return name;

        return F.addIfAbsent(names, n, new GridStopWatchName(cntr.incrementAndGet(), n));
    }

    /** {@inheritDoc} */
    @Override public GridStopWatchName name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public Map<GridStopWatchName, T2<AtomicInteger, AtomicLong>> steps() {
        return steps;
    }

    /** {@inheritDoc} */
    @Override public void watch() {
        start = System.currentTimeMillis();

        checkpoint = start;
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        // Total time.
        recordStep(name.name(), System.currentTimeMillis() - start);

        start = -1;
        checkpoint = -1;

        W.onStop(this);
    }

    /** {@inheritDoc} */
    @Override public void lastStep(String stepName) {
        step(stepName);

        stop();
    }

    /** {@inheritDoc} */
    @Override public void step(String stepName) {
        assert stepName != null;
        assert !name.name().equals(stepName);

        long delta = System.currentTimeMillis() - checkpoint;

        recordStep(stepName, delta);

        checkpoint = System.currentTimeMillis();
    }

    /**
     * @param stepName Step name.
     * @param time Step time.
     */
    private void recordStep(String stepName, long time) {
        GridStopWatchName name = stepName(stepName);

        T2<AtomicInteger, AtomicLong> t = F.addIfAbsent(steps, name,
            new T2<AtomicInteger, AtomicLong>(new AtomicInteger(), new AtomicLong()));

        t.get1().incrementAndGet();
        t.get2().addAndGet(time);

        if (printSteps)
            U.quiet("*** STOPWATCH(" + name.name() + ':' + stepName + ':' + Thread.currentThread().getName() +
                ") [duration=" + time + ']');
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridStopwatchImpl.class, this);
    }
}
