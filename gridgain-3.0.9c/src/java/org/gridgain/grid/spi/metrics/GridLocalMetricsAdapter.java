// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.metrics;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;

/**
 * Adapter for {@link GridLocalMetrics} interface.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLocalMetricsAdapter implements GridLocalMetrics, Externalizable {
    /** */
    private int availProcs = -1;

    /** */
    private double load = -1;

    /** */
    private long heapInit = -1;

    /** */
    private long heapUsed = -1;

    /** */
    private long heapCommitted = -1;

    /** */
    private long heapMax = -1;

    /** */
    private long nonHeapInit = -1;

    /** */
    private long nonHeapUsed = -1;

    /** */
    private long nonHeapCommitted = -1;

    /** */
    private long nonHeapMax = -1;

    /** */
    private long upTime = -1;

    /** */
    private long startTime = -1;

    /** */
    private int threadCnt = -1;

    /** */
    private int peakThreadCnt = -1;

    /** */
    private long startedThreadCnt = -1;

    /** */
    private int daemonThreadCnt = -1;

    /** */
    private long fileSystemFreeSpace = -1;

    /** */
    private long fileSystemTotalSpace = -1;

    /** */
    private long fileSystemUsableSpace = -1;

    /**
     * Empty constructor.
     */
    public GridLocalMetricsAdapter() {
        // No-op.
    }

    /**
     * Constructor to initialize all possible metrics.
     *
     * @param availProcs Number of available processors.
     * @param load Average system load for the last minute.
     * @param heapInit Heap initial memory.
     * @param heapUsed Heap used memory.
     * @param heapCommitted Heap committed memory.
     * @param heapMax Heap maximum memory.
     * @param nonHeapInit Non-heap initial memory.
     * @param nonHeapUsed Non-heap used memory.
     * @param nonHeapCommitted Non-heap committed memory.
     * @param nonHeapMax Non-heap maximum memory.
     * @param upTime VM uptime.
     * @param startTime VM start time.
     * @param threadCnt Current active thread count.
     * @param peakThreadCnt Peak thread count.
     * @param startedThreadCnt Started thread count.
     * @param daemonThreadCnt Daemon thread count.
     * @param fileSystemFreeSpace Disk free space.
     * @param fileSystemTotalSpace Disk total space.
     * @param fileSystemUsableSpace Disk usable space.
     */
    public GridLocalMetricsAdapter(
        int availProcs,
        double load,
        long heapInit,
        long heapUsed,
        long heapCommitted,
        long heapMax,
        long nonHeapInit,
        long nonHeapUsed,
        long nonHeapCommitted,
        long nonHeapMax,
        long upTime,
        long startTime,
        int threadCnt,
        int peakThreadCnt,
        long startedThreadCnt,
        int daemonThreadCnt,
        long fileSystemFreeSpace,
        long fileSystemTotalSpace,
        long fileSystemUsableSpace) {
        this.availProcs = availProcs;
        this.load = load;
        this.heapInit = heapInit;
        this.heapUsed = heapUsed;
        this.heapCommitted = heapCommitted;
        this.heapMax = heapMax;
        this.nonHeapInit = nonHeapInit;
        this.nonHeapUsed = nonHeapUsed;
        this.nonHeapCommitted = nonHeapCommitted;
        this.nonHeapMax = nonHeapMax;
        this.upTime = upTime;
        this.startTime = startTime;
        this.threadCnt = threadCnt;
        this.peakThreadCnt = peakThreadCnt;
        this.startedThreadCnt = startedThreadCnt;
        this.daemonThreadCnt = daemonThreadCnt;
        this.fileSystemFreeSpace = fileSystemFreeSpace;
        this.fileSystemTotalSpace = fileSystemTotalSpace;
        this.fileSystemUsableSpace = fileSystemUsableSpace;
    }

    /** {@inheritDoc} */
    @Override public int getAvailableProcessors() {
        return availProcs;
    }

    /** {@inheritDoc} */
    @Override public double getCurrentCpuLoad() {
        return load;
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryInitialized() {
        return heapInit;
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryUsed() {
        return heapUsed;
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryCommitted() {
        return heapCommitted;
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryMaximum() {
        return heapMax;
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryInitialized() {
        return nonHeapInit;
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryUsed() {
        return nonHeapUsed;
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryCommitted() {
        return nonHeapCommitted;
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryMaximum() {
        return nonHeapMax;
    }

    /** {@inheritDoc} */
    @Override public long getUptime() {
        return upTime;
    }

    /** {@inheritDoc} */
    @Override public long getStartTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public int getThreadCount() {
        return threadCnt;
    }

    /** {@inheritDoc} */
    @Override public int getPeakThreadCount() {
        return peakThreadCnt;
    }

    /** {@inheritDoc} */
    @Override public long getTotalStartedThreadCount() {
        return startedThreadCnt;
    }

    /** {@inheritDoc} */
    @Override public int getDaemonThreadCount() {
        return daemonThreadCnt;
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemFreeSpace() {
        return fileSystemFreeSpace;
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemTotalSpace() {
        return fileSystemTotalSpace;
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemUsableSpace() {
        return fileSystemUsableSpace;
    }

    /**
     * Sets available processors.
     *
     * @param availProcs Available processors.
     */
    public void setAvailableProcessors(int availProcs) {
        this.availProcs = availProcs;
    }

    /**
     * Sets CPU load average over last minute.
     *
     * @param load CPU load average over last minute.
     */
    public void setCurrentCpuLoad(double load) {
        this.load = load;
    }

    /**
     * Sets heap initial memory.
     *
     * @param heapInit Heap initial memory.
     */
    public void setHeapMemoryInitialized(long heapInit) {
        this.heapInit = heapInit;
    }

    /**
     * Sets used heap memory.
     *
     * @param heapUsed Used heap memory.
     */
    public void setHeapMemoryUsed(long heapUsed) {
        this.heapUsed = heapUsed;
    }

    /**
     * Sets committed heap memory.
     *
     * @param heapCommitted Committed heap memory.
     */
    public void setHeapMemoryCommitted(long heapCommitted) {
        this.heapCommitted = heapCommitted;
    }

    /**
     * Sets maximum possible heap memory.
     *
     * @param heapMax Maximum possible heap memory.
     */
    public void setHeapMemoryMaximum(long heapMax) {
        this.heapMax = heapMax;
    }

    /**
     * Sets initial non-heap memory.
     *
     * @param nonHeapInit Initial non-heap memory.
     */
    public void setNonHeapMemoryInitialized(long nonHeapInit) {
        this.nonHeapInit = nonHeapInit;
    }

    /**
     * Sets used non-heap memory.
     *
     * @param nonHeapUsed Used non-heap memory.
     */
    public void setNonHeapMemoryUsed(long nonHeapUsed) {
        this.nonHeapUsed = nonHeapUsed;
    }

    /**
     * Sets committed non-heap memory.
     *
     * @param nonHeapCommitted Committed non-heap memory.
     */
    public void setNonHeapMemoryCommitted(long nonHeapCommitted) {
        this.nonHeapCommitted = nonHeapCommitted;
    }

    /**
     * Sets maximum possible non-heap memory.
     *
     * @param nonHeapMax Maximum possible non-heap memory.
     */
    public void setNonHeapMemoryMaximum(long nonHeapMax) {
        this.nonHeapMax = nonHeapMax;
    }

    /**
     * Sets VM up time.
     *
     * @param upTime VN up time.
     */
    public void setUpTime(long upTime) {
        this.upTime = upTime;
    }

    /**
     * Sets VM start time.
     *
     * @param startTime VM start time.
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Sets thread count.
     *
     * @param threadCnt Thread count.
     */
    public void setThreadCount(int threadCnt) {
        this.threadCnt = threadCnt;
    }

    /**
     * Sets peak thread count.
     *
     * @param peakThreadCnt Peak thread count.
     */
    public void setPeakThreadCount(int peakThreadCnt) {
        this.peakThreadCnt = peakThreadCnt;
    }

    /**
     * Sets started thread count.
     *
     * @param startedThreadCnt Started thread count.
     */
    public void setTotalStartedThreadCount(long startedThreadCnt) {
        this.startedThreadCnt = startedThreadCnt;
    }

    /**
     * Sets daemon thread count.
     *
     * @param daemonThreadCnt Daemon thread count.
     */
    public void setDaemonThreadCount(int daemonThreadCnt) {
        this.daemonThreadCnt = daemonThreadCnt;
    }

    /**
     * Sets the number of unallocated bytes in the partition.
     *
     * @param fileSystemFreeSpace The number of unallocated bytes in the partition.
     */
    public void setFileSystemFreeSpace(long fileSystemFreeSpace) {
        this.fileSystemFreeSpace = fileSystemFreeSpace;
    }

    /**
     * Sets size of the partition.
     *
     * @param fileSystemTotalSpace Size of the partition.
     */
    public void setFileSystemTotalSpace(long fileSystemTotalSpace) {
        this.fileSystemTotalSpace = fileSystemTotalSpace;
    }

    /**
     * Sets the number of bytes available to this virtual machine on the partition.
     *
     * @param fileSystemUsableSpace The number of bytes available to
     *      this virtual machine on the partition.
     */
    public void setFileSystemUsableSpace(long fileSystemUsableSpace) {
        this.fileSystemUsableSpace = fileSystemUsableSpace;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(availProcs);
        out.writeDouble(load);
        out.writeLong(heapInit);
        out.writeLong(heapUsed);
        out.writeLong(heapCommitted);
        out.writeLong(heapMax);
        out.writeLong(nonHeapInit);
        out.writeLong(nonHeapUsed);
        out.writeLong(nonHeapCommitted);
        out.writeLong(nonHeapMax);
        out.writeLong(upTime);
        out.writeLong(startTime);
        out.writeInt(threadCnt);
        out.writeInt(peakThreadCnt);
        out.writeLong(startedThreadCnt);
        out.writeInt(daemonThreadCnt);
        out.writeLong(fileSystemFreeSpace);
        out.writeLong(fileSystemTotalSpace);
        out.writeLong(fileSystemUsableSpace);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        availProcs = in.readInt();
        load = in.readDouble();
        heapInit = in.readLong();
        heapUsed = in.readLong();
        heapCommitted = in.readLong();
        heapMax = in.readLong();
        nonHeapInit = in.readLong();
        nonHeapUsed = in.readLong();
        nonHeapCommitted = in.readLong();
        nonHeapMax = in.readLong();
        upTime = in.readLong();
        startTime = in.readLong();
        threadCnt = in.readInt();
        peakThreadCnt = in.readInt();
        startedThreadCnt = in.readLong();
        daemonThreadCnt = in.readInt();
        fileSystemFreeSpace = in.readLong();
        fileSystemTotalSpace= in.readLong();
        fileSystemUsableSpace = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLocalMetricsAdapter.class, this);
    }
}
