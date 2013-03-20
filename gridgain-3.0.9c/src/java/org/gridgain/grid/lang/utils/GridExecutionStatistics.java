// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Accumulates execution statistics for named pieces of code.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridExecutionStatistics {
    /** */
    private String name;

    /** Map of execution counters. */
    private ConcurrentMap<String, AtomicInteger> cntMap = new ConcurrentHashMap<String, AtomicInteger>();

    /** Map of execution durations. */
    private ConcurrentMap<String, AtomicLong> durationMap = new ConcurrentHashMap<String, AtomicLong>();

    /** Execution start time for the current thread. */
    private ThreadLocal<GridTuple2<String, Long>> startTime = new ThreadLocal<GridTuple2<String, Long>>() {
        @Override protected GridTuple2<String, Long> initialValue() {
            return F.t(null, 0L);
        }
    };

    /**
     * @param name Statistics name.
     */
    public GridExecutionStatistics(String name) {
        this.name = name;
    }

    /**
     * @param name Execution name.
     */
    public void watchExecution(String name) {
        assert name != null;

        startTime.get().put(name, System.currentTimeMillis());
    }

    /**
     * Stop watching execution started previously in the same thread.
     */
    public void stopWatching() {
        String name = startTime.get().get1();

        long time = startTime.get().get2();

        AtomicInteger cnt = F.addIfAbsent(cntMap, name, F.newAtomicInt());

        assert cnt != null;

        cnt.incrementAndGet();

        AtomicLong d = F.addIfAbsent(durationMap, name, F.newAtomicLong());

        assert d != null;

        d.addAndGet(System.currentTimeMillis() - time);
    }

    /**
     * Prints statistics.
     */
    public void print() {
        X.println("*** Execution statistics: " + name);

        for (Map.Entry<String, AtomicInteger> e : cntMap.entrySet()) {
            int cnt = e.getValue().get();

            assert cnt > 0;

            long totalDuration = durationMap.get(e.getKey()).get();

            long avgDuration = totalDuration / cnt;

            X.println("\t" + e.getKey() + "->[executions=" + cnt +
                ", average duration=" + avgDuration + ", total duration=" + totalDuration + "]");
        }
    }
}
