// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.concurrent.atomic.*;

import static java.lang.Math.*;

/**
 * Adapter for cache metrics.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheMetricsAdapter implements GridCacheMetrics, Externalizable {
    /** Create time. */
    private long createTime = System.currentTimeMillis();

    /** Last read time. */
    private final AtomicLong readTime = new AtomicLong(System.currentTimeMillis());

    /** Last update time. */
    private final AtomicLong writeTime = new AtomicLong(System.currentTimeMillis());

    /** Number of reads. */
    private final AtomicInteger reads = new AtomicInteger();

    /** Number of writes. */
    private final AtomicInteger writes = new AtomicInteger();

    /** Number of hits. */
    private final AtomicInteger hits = new AtomicInteger();

    /** Number of misses. */
    private final AtomicInteger misses = new AtomicInteger();

    /** Cache metrics. */
    @GridToStringExclude
    private GridCacheMetricsAdapter delegate;

    /**
     *
     */
    public GridCacheMetricsAdapter() {
        delegate = null;
    }

    /**
     * @param delegate Delegate cache metrics.
     */
    public GridCacheMetricsAdapter(GridCacheMetricsAdapter delegate) {
        assert delegate != null;

        this.delegate = delegate;
    }

    /**
     * @param createTime Create time.
     * @param readTime Read time.
     * @param writeTime Write time.
     * @param reads Reads.
     * @param writes Writes.
     * @param hits Hits.
     * @param misses Misses.
     */
    public GridCacheMetricsAdapter(long createTime, long readTime, long writeTime, int reads, int writes, int hits,
        int misses) {
        this.createTime = createTime;
        this.readTime.set(readTime);
        this.writeTime.set(writeTime);
        this.reads.set(reads);
        this.writes.set(writes);
        this.hits.set(hits);
        this.misses.set(misses);
    }

    /**
     * @param delegate Metrics to delegate to.
     */
    public void delegate(GridCacheMetricsAdapter delegate) {
        this.delegate = delegate;
    }

    /** {@inheritDoc} */
    @Override public long createTime() {
        return createTime;
    }

    /** {@inheritDoc} */
    @Override public long writeTime() {
        return writeTime.get();
    }

    /** {@inheritDoc} */
    @Override public long readTime() {
        return readTime.get();
    }

    /** {@inheritDoc} */
    @Override public int reads() {
        return reads.get();
    }

    /** {@inheritDoc} */
    @Override public int writes() {
        return writes.get();
    }

    /** {@inheritDoc} */
    @Override public int hits() {
        return hits.get();
    }

    /** {@inheritDoc} */
    @Override public int misses() {
        return misses.get();
    }

    /**
     * Cache write callback.
     * @param isHit Hit or miss flag.
     */
    public void onRead(boolean isHit) {
        readTime.set(System.currentTimeMillis());

        reads.incrementAndGet();

        if (isHit)
            hits.incrementAndGet();
        else
            misses.incrementAndGet();

        if (delegate != null)
            delegate.onRead(isHit);
    }

    /**
     * Cache read callback.
     */
    public void onWrite() {
        writeTime.set(System.currentTimeMillis());

        writes.incrementAndGet();

        if (delegate != null)
            delegate.onWrite();
    }

    /**
     * Create a copy of given metrics object.
     *
     * @param m Metrics to copy from.
     * @return Copy of given metrics.
     */
    public static GridCacheMetricsAdapter copyOf(GridCacheMetrics m) {
        assert m != null;

        return new GridCacheMetricsAdapter(
            m.createTime(),
            m.readTime(),
            m.writeTime(),
            m.reads(),
            m.writes(),
            m.hits(),
            m.misses()
        );
    }

    /**
     * Create a copy of this metrics merged with the given metrics.
     *
     * @param m1 1st metrics.
     * @param m2 2nd metrics.
     * @return Copy of given metrics.
     */
    public static GridCacheMetricsAdapter merge(GridCacheMetrics m1, GridCacheMetrics m2) {
        assert m1 != null;
        assert m2 != null;

        return new GridCacheMetricsAdapter(
            min(m1.createTime(), m2.createTime()),
            max(m1.readTime(), m2.readTime()),
            m1.writeTime() + m2.writeTime(),
            m1.reads() + m2.reads(),
            m1.writes() + m2.writes(),
            m1.hits() + m2.hits(),
            m1.misses() + m2.misses()
        );
    }

    /**
     * Clears metrics.
     *
     * NOTE: this method is for testing purposes only!
     */
    void clear() {
        createTime = System.currentTimeMillis();
        readTime.set(System.currentTimeMillis());
        writeTime.set(System.currentTimeMillis());
        reads.set(0);
        writes.set(0);
        hits.set(0);
        misses.set(0);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(createTime);
        out.writeLong(readTime.get());
        out.writeLong(writeTime.get());

        out.writeInt(reads.get());
        out.writeInt(writes.get());
        out.writeInt(hits.get());
        out.writeInt(misses.get());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        createTime = in.readLong();
        readTime.set(in.readLong());
        writeTime.set(in.readLong());

        reads.set(in.readInt());
        writes.set(in.readInt());
        hits.set(in.readInt());
        misses.set(in.readInt());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheMetricsAdapter.class, this);
    }
}
