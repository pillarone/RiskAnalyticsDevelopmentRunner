// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Adapter for {@link GridCacheQueryMetrics} interface.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheQueryMetricsAdapter implements GridCacheQueryMetrics, Externalizable {
    /** Query creation time. */
    private long createTime = System.currentTimeMillis();

    /** First run time. */
    private volatile long firstTime;

    /** Last run time. */
    private volatile long lastTime;

    /** Minimum time of execution. */
    private volatile long minTime;

    /** Maximum time of execution. */
    private volatile long maxTime;

    /** Average time of execution. */
    private volatile long avgTime;

    /** Number of hits. */
    private volatile int execs;

    /** Number of fails. */
    private volatile int fails;

    /** Queue clause. */
    private String clause;

    /**  */
    private GridCacheQueryType type;

    /**  */
    private String className;

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheQueryMetricsAdapter() {
        /* No-op. */
    }

    /**
     *
     * @param query Query which
     */
    public GridCacheQueryMetricsAdapter(GridCacheQueryBase query) {
        clause = query.clause();

        type = query.type();

        className = query.className();
    }

    /** {@inheritDoc} */
    @Override public long firstRunTime() {
        return firstTime;
    }

    /** {@inheritDoc} */
    @Override public long lastRunTime() {
        return lastTime;
    }

    /** {@inheritDoc} */
    @Override public long minimumTime() {
        return minTime;
    }

    /** {@inheritDoc} */
    @Override public long maximumTime() {
        return maxTime;
    }

    /** {@inheritDoc} */
    @Override public long averageTime() {
        return avgTime;
    }

    /** {@inheritDoc} */
    @Override public int executions() {
        return execs;
    }

    /** {@inheritDoc} */
    @Override public int fails() {
        return fails;
    }

    /** {@inheritDoc} */
    @Override public String clause() {
        return clause;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryType type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public String className() {
        return className;
    }

    /**
     * Callback for query execution.
     *
     * @param startTime Start queue time.
     * @param duration Duration of queue execution.
     * @param fail {@code True} query executed unsuccessfully {@code false} otherwise.
     */
    public void onQueryExecute(long startTime, long duration, boolean fail) {
        if (fail) {
            fails++;

            return;
        }

        synchronized (mux) {
            lastTime = startTime;

            if (firstTime == 0) {
                firstTime = lastTime;
                minTime = duration;
                maxTime = duration;
            }

            if (minTime > duration)
                minTime = duration;

            if (maxTime < duration)
                maxTime = duration;

            execs++;

            avgTime = (avgTime * (execs - 1) + duration) / execs;
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(createTime);
        out.writeLong(firstTime);
        out.writeLong(lastTime);
        out.writeLong(minTime);
        out.writeLong(maxTime);
        out.writeLong(avgTime);
        out.writeInt(execs);
        out.writeInt(fails);
        out.writeByte(type.ordinal());
        out.writeUTF(clause);
        out.writeUTF(className);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        createTime = in.readLong();
        firstTime = in.readLong();
        lastTime = in.readLong();
        minTime = in.readLong();
        maxTime = in.readLong();
        avgTime = in.readLong();
        execs = in.readInt();
        fails = in.readInt();
        type = GridCacheQueryType.fromOrdinal(in.readByte());
        clause = in.readUTF();
        className = in.readUTF();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryMetricsAdapter.class, this);
    }
}
