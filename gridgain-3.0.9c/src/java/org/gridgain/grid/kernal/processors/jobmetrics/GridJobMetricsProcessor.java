// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.jobmetrics;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Processes job metrics.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobMetricsProcessor extends GridProcessorAdapter {
    /** Time to live. */
    private long expireTime;

    /** Maximum size. */
    private int histSize;

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> activeJobMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getActiveJobs() > o2.getActiveJobs() ? -1 :
                        o1.getActiveJobs() == o2.getActiveJobs() ? 0 : 1;
                }
            }
        );

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> waitingJobMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getPassiveJobs() > o2.getPassiveJobs() ? -1 :
                        o1.getPassiveJobs() == o2.getPassiveJobs() ? 0 : 1;
                }
            }
        );

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> cancelledJobMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getCancelJobs() > o2.getCancelJobs() ? -1 :
                        o1.getCancelJobs() == o2.getCancelJobs() ? 0 : 1;
                }
            }
        );

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> rejectedJobMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getRejectJobs() > o2.getRejectJobs() ? -1 :
                        o1.getRejectJobs() == o2.getRejectJobs() ? 0 : 1;
                }
            }
        );

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> execTimeMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getMaximumExecutionTime() > o2.getMaximumExecutionTime() ? -1 :
                        o1.getMaximumExecutionTime() == o2.getMaximumExecutionTime() ? 0 : 1;
                }
            }
        );

    /** */
    private GridConcurrentSkipListSet<GridJobMetricsSnapshot> waitTimeMaxSet =
        new GridConcurrentSkipListSet<GridJobMetricsSnapshot>(
            new Comparator<GridJobMetricsSnapshot>() {
                /** {@inheritDoc} */
                @Override public int compare(GridJobMetricsSnapshot o1, GridJobMetricsSnapshot o2) {
                    return o1.getMaximumWaitTime() > o2.getMaximumWaitTime() ? -1 :
                        o1.getMaximumWaitTime() == o2.getMaximumWaitTime() ? 0 : 1;
                }
            }
        );

    /** */
    private Queue<GridJobMetricsSnapshot> queue = new ConcurrentLinkedQueue<GridJobMetricsSnapshot>();

    /** */
    private MetricCounters cntrs = new MetricCounters();

    /**
     * @param ctx Grid kernal context.
     */
    public GridJobMetricsProcessor(GridKernalContext ctx) {
        super(ctx);

        expireTime = ctx.config().getMetricsExpireTime();
        histSize = ctx.config().getMetricsHistorySize();
    }

    /**
     * Gets metrics history size.
     *
     * @return Maximum metrics queue size.
     */
    int getHistorySize() {
        return histSize;
    }

    /**
     * Gets snapshot queue.
     *
     * @return Metrics snapshot queue.
     */
    Queue<GridJobMetricsSnapshot> getQueue() {
        return queue;
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        if (histSize == 0) {
            histSize = GridConfiguration.DFLT_METRICS_HISTORY_SIZE;
        }

        if (expireTime == 0) {
            expireTime = GridConfiguration.DFLT_METRICS_EXPIRE_TIME;
        }

        assertParameter(histSize > 0, "metricsHistorySize > 0");
        assertParameter(expireTime > 0, "metricsExpireTime > 0");

        if (log.isDebugEnabled()) {
            log.debug("Job metrics processor started [histSize=" + histSize + ", expireTime=" + expireTime + ']');
        }
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (log.isDebugEnabled()) {
            log.debug("Job metrics processor stopped.");
        }
    }

    /**
     * Gets latest metrics.
     *
     * @return Latest metrics.
     */
    public GridJobMetrics getJobMetrics() {
        return cntrs.getJobMetrics();
    }

    /**
     * @param metrics New metrics.
     */
    public void addSnapshot(GridJobMetricsSnapshot metrics) {
        long now = System.currentTimeMillis();

        queue.add(metrics);

        // Update counters and obtain number of snapshots in the history
        // (i.e. current queue size). Note, that we cannot do 'queue.size()'
        // here as it has complexity of O(N) for ConcurrentLinkedQueue.
        int curHistorySize = cntrs.onAdd(metrics, now);

        // The queue does not grow out of memory, even though we don't call 'poll()',
        // because iterator eventually will remove first element in the queue, in which
        // case next iteration will clean the internal queue heap.
        for (Iterator<GridJobMetricsSnapshot> iter = queue.iterator(); iter.hasNext();) {
            GridJobMetricsSnapshot m = iter.next();

            if (curHistorySize > histSize || now - m.getTimestamp() >= expireTime) {
                if (m.delete()) {
                    // Update counters.
                    curHistorySize = cntrs.onRemove(m);

                    iter.remove();
                }
            }
            else {
                break;
            }
        }
    }

    /**
     * @param set Set to add to.
     * @param metrics Metrics to add.
     * @return First metric in the set.
     */
    @Nullable private GridJobMetricsSnapshot addToSet(GridConcurrentSkipListSet<GridJobMetricsSnapshot> set,
        GridJobMetricsSnapshot metrics) {
        set.add(metrics);

        return set.firstx();
    }

    /**
     * @param set Set.
     * @param metrics Metrics to remove.
     * @return First metric in the set.
     */
    @Nullable private GridJobMetricsSnapshot removeFromSet(GridConcurrentSkipListSet<GridJobMetricsSnapshot> set,
        GridJobMetricsSnapshot metrics) {
        set.remove(metrics);

        return set.firstx();
    }

    /**
     * All metrics counters.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class MetricCounters {
        /** */
        private int totalActiveJobs;

        /** */
        private int totalWaitingJobs;

        /** */
        private int totalStartedJobs;

        /** */
        private int totalCancelledJobs;

        /** */
        private int totalRejectedJobs;

        /** */
        private int totalFinishedJobs;

        /** */
        private long totalExecTime;

        /** */
        private long totalWaitTime;

        /** */
        private double totalCpuLoad;

        /** */
        private long totalIdleTime;

        /** */
        private long curIdleTime;

        /** */
        private boolean isIdle = true;

        /** */
        private int curHistSize;

        /** */
        private AtomicReference<GridJobMetricsSnapshot> activeMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private AtomicReference<GridJobMetricsSnapshot> waitingMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private AtomicReference<GridJobMetricsSnapshot> cancelledMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private AtomicReference<GridJobMetricsSnapshot> rejectedMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private AtomicReference<GridJobMetricsSnapshot> waitTimeMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private AtomicReference<GridJobMetricsSnapshot> execTimeMax = new AtomicReference<GridJobMetricsSnapshot>(null);

        /** */
        private long idleTimer = System.currentTimeMillis();

        /** */
        private GridJobMetricsSnapshot lastSnapshot;

        /** Mutex. */
        private final Object mux = new Object();

        /**
         * @return Latest job metrics.
         */
        GridJobMetrics getJobMetrics() {
            GridJobMetrics m = new GridJobMetrics();

            GridJobMetricsSnapshot activeMax = this.activeMax.get();
            GridJobMetricsSnapshot waitingMax = this.waitingMax.get();
            GridJobMetricsSnapshot cancelledMax = this.cancelledMax.get();
            GridJobMetricsSnapshot rejectedMax = this.rejectedMax.get();
            GridJobMetricsSnapshot waitTimeMax = this.waitTimeMax.get();
            GridJobMetricsSnapshot execTimeMax = this.execTimeMax.get();

            // Maximums.
            m.setMaximumActiveJobs(activeMax == null ? 0 : activeMax.getActiveJobs());
            m.setMaximumWaitingJobs(waitingMax == null ? 0 : waitingMax.getPassiveJobs());
            m.setMaximumCancelledJobs(cancelledMax == null ? 0 : cancelledMax.getCancelJobs());
            m.setMaximumRejectedJobs(rejectedMax == null ? 0 : rejectedMax.getRejectJobs());
            m.setMaximumJobWaitTime(waitTimeMax == null ? 0 : waitTimeMax.getMaximumWaitTime());
            m.setMaxJobExecutionTime(execTimeMax == null ? 0 : execTimeMax.getMaximumExecutionTime());

            synchronized (mux) {
                // Current metrics.
                m.setCurrentIdleTime(curIdleTime);

                if (lastSnapshot != null) {
                    m.setCurrentActiveJobs(lastSnapshot.getActiveJobs());
                    m.setCurrentWaitingJobs(lastSnapshot.getPassiveJobs());
                    m.setCurrentCancelledJobs(lastSnapshot.getCancelJobs());
                    m.setCurrentRejectedJobs(lastSnapshot.getRejectJobs());
                    m.setCurrentJobExecutionTime(lastSnapshot.getMaximumExecutionTime());
                    m.setCurrentJobWaitTime(lastSnapshot.getMaximumWaitTime());
                }

                // Averages.
                if (curHistSize > 0) {
                    m.setAverageActiveJobs((float)totalActiveJobs / curHistSize);
                    m.setAverageWaitingJobs((float)totalWaitingJobs / curHistSize);
                    m.setAverageCancelledJobs((float)totalCancelledJobs / curHistSize);
                    m.setAverageRejectedJobs((float)totalRejectedJobs / curHistSize);
                    m.setAverageCpuLoad(totalCpuLoad / curHistSize);
                }

                m.setAverageJobExecutionTime(totalFinishedJobs > 0 ? (double)totalExecTime / totalFinishedJobs : 0);
                m.setAverageJobWaitTime(totalStartedJobs > 0 ? (double)totalWaitTime / totalStartedJobs : 0);

                // Totals.
                m.setTotalExecutedJobs(totalFinishedJobs);
                m.setTotalCancelledJobs(totalCancelledJobs);
                m.setTotalRejectedJobs(totalRejectedJobs);

                m.setTotalIdleTime(totalIdleTime);
            }

            return m;
        }

        /**
         * @param metrics New metrics.
         * @param now Current timestamp.
         * @return Current history size (the size of queue).
         */
        int onAdd(GridJobMetricsSnapshot metrics, long now) {
            // Maximums.
            activeMax.set(addToSet(activeJobMaxSet, metrics));
            waitingMax.set(addToSet(waitingJobMaxSet, metrics));
            cancelledMax.set(addToSet(cancelledJobMaxSet, metrics));
            rejectedMax.set(addToSet(rejectedJobMaxSet, metrics));
            waitTimeMax.set(addToSet(waitTimeMaxSet, metrics));
            execTimeMax.set(addToSet(execTimeMaxSet, metrics));

            synchronized (mux) {
                assert curHistSize >= 0;

                curHistSize++;

                // Totals.
                totalActiveJobs += metrics.getActiveJobs();
                totalCancelledJobs += metrics.getCancelJobs();
                totalWaitingJobs += metrics.getPassiveJobs();
                totalRejectedJobs += metrics.getRejectJobs();
                totalWaitTime += metrics.getWaitTime();
                totalExecTime += metrics.getExecutionTime();
                totalStartedJobs += metrics.getStartedJobs();
                totalFinishedJobs += metrics.getFinishedJobs();
                totalCpuLoad += metrics.getCpuLoad();

                // Handle current and total idle times.
                if (metrics.getActiveJobs() > 0) {
                    if (isIdle) {
                        totalIdleTime += now - idleTimer;

                        curIdleTime = 0;

                        isIdle = false;
                    }
                }
                else {
                    if (!isIdle) {
                        isIdle = true;
                    }
                    else {
                        curIdleTime += now - idleTimer;

                        totalIdleTime += now - idleTimer;
                    }

                    // Reset timer.
                    idleTimer = now;
                }

                lastSnapshot = metrics;

                return curHistSize;
            }
        }

        /**
         * @param metrics Expired metrics.
         * @return Current history size (the size of queue).
         */
        int onRemove(GridJobMetricsSnapshot metrics) {
            // Maximums.
            activeMax.set(removeFromSet(activeJobMaxSet, metrics));
            waitingMax.set(removeFromSet(waitingJobMaxSet, metrics));
            cancelledMax.set(removeFromSet(cancelledJobMaxSet, metrics));
            rejectedMax.set(removeFromSet(rejectedJobMaxSet, metrics));
            waitTimeMax.set(removeFromSet(waitTimeMaxSet, metrics));
            execTimeMax.set(removeFromSet(execTimeMaxSet, metrics));

            synchronized (mux) {
                assert curHistSize > 0;

                curHistSize--;

                // Totals.
                totalActiveJobs -= metrics.getActiveJobs();
                totalCancelledJobs -= metrics.getCancelJobs();
                totalWaitingJobs -= metrics.getPassiveJobs();
                totalRejectedJobs -= metrics.getRejectJobs();
                totalWaitTime -= metrics.getWaitTime();
                totalExecTime -= metrics.getExecutionTime();
                totalStartedJobs -= metrics.getStartedJobs();
                totalFinishedJobs -= metrics.getFinishedJobs();
                totalCpuLoad -= metrics.getCpuLoad();

                if (curHistSize == 0) {
                    lastSnapshot = null;
                }

                return curHistSize;
            }
        }
    }
}
