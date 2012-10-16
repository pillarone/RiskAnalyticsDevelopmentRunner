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
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridSystemProperties.*;

/**
 * Stopwatch factory.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings( {"PointlessBooleanExpression", "ConstantConditions"})
public class GridStopwatchFactory {
    /** */
    private static ConcurrentMap<String, GridStopWatchName> names =
        new ConcurrentSkipListMap<String, GridStopWatchName>();

    /** */
    private static AtomicInteger cntr = new AtomicInteger();

    /** Enabled flag. */
    public static final boolean ENABLED = U.getBoolean(GG_STOPWATCH_ENABLED);

    /** Print steps. */
    public static final boolean PRINT_STEPS = U.getBoolean(GG_STOPWATCH_PRINTSTEP);

    /** Noop watch. */
    private static final GridStopwatch NOOP = new GridStopwatch() {
        private final GridStopWatchName name = watchName("NOOP");

        @Override public GridStopWatchName name() {
            return name;
        }

        @Override public void watch() {
            // No-op.
        }

        @Override public void stop() {
            // No-op.
        }

        @Override public void step(String stepName) {
            // No-op.
        }

        @Override public void lastStep(String stepName) {
            // No-op.
        }

        @Override public Map<GridStopWatchName, T2<AtomicInteger, AtomicLong>> steps() {
            return Collections.emptyMap();
        }
    };

    /** */
    private static final ConcurrentNavigableMap<GridStopWatchName, ConcurrentMap<GridStopWatchName, T2<AtomicInteger, AtomicLong>>> globalSteps =
        new ConcurrentSkipListMap<GridStopWatchName, ConcurrentMap<GridStopWatchName, T2<AtomicInteger,AtomicLong>>>();

    /**
     * Ensure singleton.
     */
    protected GridStopwatchFactory() {
        // No-op.
    }

    /**
     * @return {@code True} if stopwatch is enabled.
     */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * @return {@code NOOP} watch.
     */
    public static GridStopwatch noop() {
        return NOOP;
    }

    /**
     * @param name Watch name.
     * @return Stopwatch.
     */
    public static GridStopwatch stopwatch(String name) {
        return stopwatch(name, true);
    }

    /**
     * @param name Watch name.
     * @param printSteps If {@code true}, then steps will be printed.
     * @return Stopwatch.
     */
    public static GridStopwatch stopwatch(String name, boolean printSteps) {
        printSteps = PRINT_STEPS && printSteps;

        return ENABLED ? new GridStopwatchImpl(watchName(name), printSteps) : NOOP;
    }

    /**
     * @param n Name.
     * @return Name.
     */
    private static GridStopWatchName watchName(String n) {
        GridStopWatchName name = names.get(n);

        if (name != null)
            return name;

        return F.addIfAbsent(names, n, new GridStopWatchName(cntr.incrementAndGet(), n));
    }

    /**
     * Prints out all steps for a given watch.
     *
     * @param name Watch name.
     * @param steps Optional steps, if {@code null} or empty, then all steps will be printed. 
     */
    public static void print(String name, @Nullable String... steps) {
        if (ENABLED) {
            assert name != null;

            GridStopWatchName n = watchName(name);

            Map<GridStopWatchName, T2<AtomicInteger, AtomicLong>> stepMap = globalSteps.get(n);

            X.println("*** STOPWATCH(" + name + ')' + (stepMap == null ? " IS EMPTY ***" : ""));

            if (stepMap != null) {
                T2<AtomicInteger, AtomicLong> total = null;

                for (Map.Entry<GridStopWatchName, T2<AtomicInteger, AtomicLong>> e : stepMap.entrySet()) {
                    String stepName = e.getKey().name();

                    T2<AtomicInteger, AtomicLong> stats = e.getValue();

                    if (F.isEmpty(steps) || U.containsStringArray(steps, stepName, true)) {
                        if (stepName.equals(name)) {
                            total = stats;

                            continue;
                        }

                        printStats(stepName, stats);
                    }
                }

                printStats("TOTAL", total);
            }
        }
    }

    /**
     * @param name Name.
     * @param stats Stats.
     */
    private static void printStats(String name, T2<AtomicInteger, AtomicLong> stats) {
        if (stats != null) {
            int cnt = stats.get1().get();
            long time = stats.get2().get();

            X.println("    " + name + " [cnt=" + cnt + ", avg=" + (time / cnt) + ", total=" + time + "]");
        }
        else
            X.println("    " + name + " *EMPTY*");
    }

    /**
     * Prints steps for all watches.
     *
     * @param steps Optional steps, if {@code null} or empty, then all steps will be printed.
     */
    public static void printAll(String... steps) {
        if (ENABLED)
            for (GridStopWatchName name : globalSteps.keySet())
                print(name.name(), steps);
    }

    /**
     * Watch stop callback.
     *
     * @param watch Stopped watch.
     */
    protected static void onStop(GridStopwatch watch) {
        if (ENABLED) {
            // Record global steps.
            ConcurrentMap<GridStopWatchName, T2<AtomicInteger, AtomicLong>> stepsForName =
                F.addIfAbsent(globalSteps, watch.name(), F.<GridStopWatchName, T2<AtomicInteger, AtomicLong>>newCMap());

            assert stepsForName != null;

            for (Map.Entry<GridStopWatchName, T2<AtomicInteger, AtomicLong>> e : watch.steps().entrySet()) {
                GridStopWatchName stepName = e.getKey();
                T2<AtomicInteger, AtomicLong> locStep = e.getValue();

                T2<AtomicInteger, AtomicLong> globalStep = stepsForName.get(stepName);

                if (globalStep == null) {
                    T2<AtomicInteger, AtomicLong> prev = stepsForName.putIfAbsent(stepName,
                        globalStep = new T2<AtomicInteger, AtomicLong>(new AtomicInteger(), new AtomicLong()));

                    if (prev != null)
                        globalStep = prev;
                }

                globalStep.get1().addAndGet(locStep.get1().get());
                globalStep.get2().addAndGet(locStep.get2().get());
            }
        }
    }
}
