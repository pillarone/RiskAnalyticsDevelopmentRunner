// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.strategies;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;

/**
 * Cloud strategy that provides custom actions based on average CPU load on specified
 * subset of nodes. This strategy supports maximum and minimum values for CPU load as
 * well as number of measurements that should all consequently be above or below the
 * threshold for the actions to be triggered (to smooth out the fluctuations near the
 * threshold).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCloudCpuStrategyAdapter extends GridCloudTimeStrategyAdapter {
    /** Cpu load upper bound. */
    private float maxCpu;

    /** Cpu load lower bound. */
    private float minCpu;

    /** Predicates filtering grid nodes. */
    private GridPredicate<GridRichNode>[] p;

    /** Number of calculations to ensure that cpu load has changed its interval. */
    private int sampleNum;

    /** Counter of the same CPU load interval calculations. */
    private int sampleCnt;

    /** Last measured cpu load average. */
    private double lastCpu;

    /** Flag that shows that it must trigger only when interval has changed. */
    private boolean once;

    /** */
    @GridToStringExclude
    private final GridMutex mux = new GridMutex();

    /** {@inheritDoc} */
    @Override public void deactivate() {
        super.deactivate();

        sampleCnt = 0;
        lastCpu = 0;
    }

    /**
     * Called when CPU average exceeds configured {@link #maxCpu} value.
     *
     * @param cpuAvg CPU load average over grid nodes.
     */
    public abstract void onCpuAboveMax(double cpuAvg);

    /**
     * Called when CPU average becomes less than configured {@link #minCpu} value.
     *
     * @param cpuAvg CPU load average over grid nodes.
     */
    public abstract void onCpuBelowMin(double cpuAvg);

    /** {@inheritDoc} */
    @Override public void onTime() {
        GridPredicate<GridRichNode>[] p;
        float minCpu;
        float maxCpu;
        int sampleNum;
        boolean once;

        synchronized (mux) {
            p = this.p;
            minCpu = this.minCpu;
            maxCpu = this.maxCpu;
            sampleNum = this.sampleNum;
            once = this.once;
        }

        if (minCpu >= maxCpu) {
            U.error(log, "Min CPU bound must be less than max CPU.");

            return;
        }

        Collection<GridRichNode> nodes = p != null ? grid.nodes(p) : grid.nodes();

        if (nodes.isEmpty()) {
            U.warn(log, "There are no nodes satisfying given predicates. Skipping...");

            return;
        }

        double totalCpu = 0;

        int validCpus = nodes.size();

        for (GridRichNode n : nodes) {
            double cpu = n.metrics().getCurrentCpuLoad();

            if (cpu < 0) {
                validCpus--;

                U.warn(log, "Invalid CPU load metric [node=" + n + ']');
            }
            else
                totalCpu += cpu;
        }

        if (validCpus == 0) {
            // It's impossible to calculate CPU load average.
            U.error(log, "Failed to calculate CPU load average over grid nodes." +
                " There are no nodes with valid CPU load metric.");

            return;
        }

        double cpuAv = totalCpu / validCpus;

        if (log.isDebugEnabled())
            log.debug("CPU load calculated [cpuAv=" + cpuAv + ", validCpus=" + validCpus + ']');

        if (cpuAv < minCpu) {
            if (lastCpu < minCpu)
                sampleCnt++;
            else
                sampleCnt = 1;

            if (sampleCnt == sampleNum) {
                onCpuBelowMin(cpuAv);

                if (!once)
                    sampleCnt = 0;
            }
            else if (sampleCnt > sampleNum)
                // Return the counter value back to avoid integer type overflow.
                sampleCnt--;
        }
        else if (cpuAv > maxCpu) {
            if (lastCpu > maxCpu)
                sampleCnt++;
            else
                sampleCnt = 1;

            if (sampleCnt == sampleNum) {
                onCpuAboveMax(cpuAv);

                if (!once)
                    sampleCnt = 0;
            }
            else if (sampleCnt > sampleNum)
                // Return the counter value back to avoid integer type overflow.
                sampleCnt--;
        }
        else
            sampleCnt = 0;

        lastCpu = cpuAv;
    }

    /**
     * Sets CPU usage upper bound. Value must belong to <tt>(0, 1)</tt> interval.
     * This method can be called before and after activation. The change
     * will take an effect next time method {@link #onTime()} is called.
     *
     * @param maxCpu Max CPU usage.
     */
    public void setMaxCpu(float maxCpu) {
        A.ensure(maxCpu > 0 && maxCpu < 1, "maxCpu > 0 && maxCpu < 1");

        synchronized (mux) {
            this.maxCpu = maxCpu;
        }
    }

    /**
     * Gets CPU usage upper bound.
     *
     * @return Value in <tt>(0, 1)</tt> interval .
     */
    public float getMaxCpu() {
        synchronized (mux) {
            return maxCpu;
        }
    }

    /**
     * Sets CPU usage lower bound. Value must belong to <tt>(0, 1)</tt> interval .
     * This method can be called before and after activation. The change
     * will take an effect next time method {@link #onTime()} is called.
     *
     * @param minCpu Min cpu usage.
     */
    public void setMinCpu(float minCpu) {
        A.ensure(minCpu > 0 && minCpu < 1, "minCpu > 0 && minCpu < 1");

        synchronized (mux) {
            this.minCpu = minCpu;
        }
    }

    /**
     * Gets CPU usage lower bound.
     *
     * @return Value from <tt>(0, 1)</tt> interval.
     */
    public float getMinCpu() {
        synchronized (mux) {
            return minCpu;
        }
    }

    /**
     * Sets samples number. Number of samples defines how many measurements (samples)
     * should be consequently outside of the threshold for the actions to be
     * triggered (to smooth out the fluctuations near the threshold).
     * <p>
     * This method can be called before and after activation. The change
     * will take an effect next time method {@link #onTime()} is called.
     *
     * @param num Samples number. Value must be greater than zero.
     */
    public void setSampleNumber(int num) {
        A.ensure(num > 0, "num > 0");

        synchronized (mux) {
            sampleNum = num;
        }
    }

    /**
     * Gets sample number. Number of samples defines how many measurements (samples)
     * should be consequently outside of the threshold for the actions to be
     * triggered (to smooth out the fluctuations near the threshold).
     *
     * @return Sample number.
     */
    public int getSampleNumber() {
        synchronized (mux) {
            return sampleNum;
        }
    }

    /**
     * Sets predicates filtering nodes this strategy is working on. If none
     * provided or not set - all nodes in the topology will be used. This method
     * can be called before and after activation. The change will take an effect next
     * time method {@link #onTime()} is called.
     *
     * @param p Optional set of predicates that will define a subset of nodes
     *      this strategy is working on.
     */
    public void setPredicates(GridPredicate<GridRichNode>... p) {
        synchronized (mux) {
            this.p = p;
        }
    }

    /**
     * Gets predicates filtering nodes this strategy is working on.
     *
     * @return Array of predicates.
     */
    public GridPredicate<GridRichNode>[] getPredicates() {
        synchronized (mux) {
            return p;
        }
    }

    /**
     * Gets once per threshold flag. This flag indicates whether strategy must be triggered only
     * once when interval has changed. Default value is {@code false} meaning that it will trigger
     * every {@link #getSampleNumber() number of measurements} as long as CPU average stays outside
     * of the threshold.
     *
     * @return {@code true} if once per interval, {@code false} otherwise.
     */
    public boolean isOncePerThreshold() {
        synchronized (mux) {
            return once;
        }
    }

    /**
     * Sets flag indicating whether strategy must be triggered only once when threshold was crossed.
     * Default value is {@code false} meaning that it will trigger every {@link #getSampleNumber() number
     * of measurements} as long as CPU average stays outside of the threshold. If set to {@code true} the
     * strategy will be triggered only once while threshold was crossed and CPU average doesn't cross it
     * again.
     *
     * @param once Once per interval flag.
     */
    public void setOncePerThreshold(boolean once) {
        synchronized (mux) {
            this.once = once;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudCpuStrategyAdapter.class, this, "super", super.toString());
    }
}
