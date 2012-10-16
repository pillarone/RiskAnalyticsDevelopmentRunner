// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.metrics.jdk;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.net.*;

/**
 * This class provides JDK MXBean based local VM metrics. Note that average
 * CPU load cannot be obtained from JDK 1.5 and on some operating systems
 * (including Windows Vista) even from JDK 1.6 (although JDK 1.6 supposedly added
 * support for it). For cases when CPU load cannot
 * be obtained from JDK, GridGain ships with
 * <a href="http://www.hyperic.com/products/sigar.html">Hyperic SIGAR</a> metrics.
 * <p>
 * If CPU load cannot be obtained either from JDK or Hyperic, then
 * {@link GridLocalMetrics#getCurrentCpuLoad()}
 * method will always return {@code -1}.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Always prefer Hyperic Sigar regardless of JDK version (see {@link #setPreferSigar(boolean)})</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridJdkLocalMetricsSpi extends GridSpiAdapter implements GridLocalMetricsSpi,
    GridJdkLocalMetricsSpiMBean {
    /** */
    private MemoryMXBean mem;

    /** */
    private OperatingSystemMXBean os;

    /** */
    private RuntimeMXBean rt;

    /** */
    private ThreadMXBean threads;

    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** */
    private volatile GridLocalMetrics metrics;

    /** */
    private Object sigar;

    /** */
    private Method sigarCpuPercMtd;

    /** */
    private Method sigarCpuCombinedMtd;

    /** */
    private Method jdkCpuLoadMtd;

    /** */
    private volatile boolean preferSigar = true;

    /** */
    private String fsRoot;

    /** */
    private volatile boolean isErrRep;

    /** */
    private File fsRootFile;

    /** {@inheritDoc} */
    @Override public boolean isPreferSigar() {
        return preferSigar;
    }

    /**
     * Configuration parameter indicating if Hyperic Sigar should be used regardless
     * of JDK version. Hyperic Sigar is used to provide CPU load. Starting with JDK 1.6,
     * method {@code OperatingSystemMXBean.getSystemLoadAverage()} method was added.
     * However, even in 1.6 and higher this method does not always provide CPU load
     * on some operating systems (or provides incorrect value) - in such cases Hyperic
     * Sigar will be used automatically.
     * <p>
     * By default the value is {@code true}.
     *
     * @param preferSigar If {@code true} then Hyperic Sigar should be used regardless of
     *      JDK version, if {@code false}, then implementation will attempt to use
     *      {@code OperatingSystemMXBean.getSystemLoadAverage()} for JDK 1.6 and higher.
     */
    @GridSpiConfiguration(optional = true)
    public void setPreferSigar(boolean preferSigar) {
        this.preferSigar = preferSigar;
    }

    /**
     * Set file system root.
     *
     * @param fsRoot File system root.
     */
    @GridSpiConfiguration(optional = true)
    public void setFileSystemRoot(String fsRoot) {
        this.fsRoot = fsRoot;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String gridName) throws GridSpiException {
        startStopwatch();

        if (log.isDebugEnabled()) {
            log.debug(configInfo("preferSigar", preferSigar));
        }

        mem = ManagementFactory.getMemoryMXBean();
        os = ManagementFactory.getOperatingSystemMXBean();
        rt = ManagementFactory.getRuntimeMXBean();
        threads = ManagementFactory.getThreadMXBean();

        // Check Sigar first.
        if (preferSigar) {
            initializeSigar();
        }

        String jdkVer = U.jdkVersion();

        // If sigar isn't found and we have 1.6 JDK - use default.
        // Note that at this point we don't support 1.5 or 1.7 but
        // we assume backward compatibility of 1.7 JDK.
        if (sigar == null && (jdkVer.contains("1.6") || jdkVer.contains("1.7"))) {
            initializeJdk16();
        }

        if (jdkCpuLoadMtd != null) {
            if (log.isInfoEnabled()) {
                log.info("JDK 1.6 method 'OperatingSystemMXBean.getSystemLoadAverage()' " +
                    "will be used to detect average CPU load.");
            }
        }
        else if (sigar != null) {
            if (log.isInfoEnabled()) {
                log.info("Hyperic Sigar 'CpuPerc.getCombined()' method will be used to detect average CPU load.");
            }
        }
        else {
            U.warn(log, "System CPU load cannot be detected (add Hyperic Sigar to classpath). " +
                "CPU load will be returned as -1.");
        }

        if (fsRoot != null) {
            fsRootFile = new File(fsRoot);

            if (!fsRootFile.exists()) {
                U.warn(log, "Invalid file system root name: " + fsRoot);

                fsRootFile = null;
            }
        }
        else {
            fsRootFile = getDefaultFileSystemRoot();
        }

        metrics = getMetrics();

        registerMBean(gridName, this, GridJdkLocalMetricsSpiMBean.class);

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** */
    @SuppressWarnings({"CatchGenericClass"})
    private void initializeSigar() {
        // Detect if Sigar is available in classpath.
        try {
            Object impl = Class.forName("org.hyperic.sigar.Sigar").newInstance();

            Method proxyMtd = Class.forName("org.hyperic.sigar.SigarProxyCache").getMethod("newInstance",
                impl.getClass(), int.class);

            // Update CPU info every 2 seconds.
            sigar = proxyMtd.invoke(null, impl, 2000);

            sigarCpuPercMtd = sigar.getClass().getMethod("getCpuPerc");
            sigarCpuCombinedMtd = sigarCpuPercMtd.getReturnType().getMethod("getCombined");
        }
        // Purposely catch generic exception.
        catch (Exception e) {
            sigar = null;

            // Don't warn about Sigar in enterprise edition
            // since Sigar is not shipped with enterprise edition due to
            // GPL license limitation.
            if (!getSpiContext().isEnterprise()) {
                U.warn(log, "Failed to find Hyperic Sigar in classpath: " + e.getMessage());
            }
        }
    }

    /** */
    @SuppressWarnings({"CatchGenericClass"})
    private void initializeJdk16() {
        try {
            jdkCpuLoadMtd = OperatingSystemMXBean.class.getMethod("getSystemLoadAverage");
        }
        // Purposely catch generic exception.
        catch (Exception e) {
            jdkCpuLoadMtd = null;

            U.warn(log, "Failed to find JDK 1.6 or higher CPU load metrics: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public GridLocalMetrics getMetrics() {
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap;

        // Workaround of exception in WebSphere.
        // We received the following exception:
        // java.lang.IllegalArgumentException: used value cannot be larger than the committed value
        // at java.lang.management.MemoryUsage.<init>(MemoryUsage.java:105)
        // at com.ibm.lang.management.MemoryMXBeanImpl.getNonHeapMemoryUsageImpl(Native Method)
        // at com.ibm.lang.management.MemoryMXBeanImpl.getNonHeapMemoryUsage(MemoryMXBeanImpl.java:143)
        // at org.gridgain.grid.spi.metrics.jdk.GridJdkLocalMetricsSpi.getMetrics(GridJdkLocalMetricsSpi.java:242)
        //
        // We so had to workaround this with exception handling, because we can not control classes from WebSphere.
        try {
            nonHeap = mem.getNonHeapMemoryUsage();
        }
        catch (IllegalArgumentException ignore) {
            nonHeap = new MemoryUsage(0, 0, 0, 0);
        }

        metrics = new GridLocalMetricsAdapter(
            os.getAvailableProcessors(),
            getCpuLoad(),
            heap.getInit(),
            heap.getUsed(),
            heap.getCommitted(),
            heap.getMax(),
            nonHeap.getInit(),
            nonHeap.getUsed(),
            nonHeap.getCommitted(),
            nonHeap.getMax(),
            rt.getUptime(),
            rt.getStartTime(),
            threads.getThreadCount(),
            threads.getPeakThreadCount(),
            threads.getTotalStartedThreadCount(),
            threads.getDaemonThreadCount(),
            fsRootFile == null ? -1 : fsRootFile.getFreeSpace(),
            fsRootFile == null ? -1 : fsRootFile.getTotalSpace(),
            fsRootFile == null ? -1 : fsRootFile.getUsableSpace()
        );

        return metrics;
    }

    /**
     * @return CPU load.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private double getCpuLoad() {
        double load = -1; // Default.

        if (sigar != null) {
            load = getSigarCpuLoad();
        }
        else if (jdkCpuLoadMtd != null) {
            load = getJdk6CpuLoad();
        }

        // Remove odd errors.
        if (Double.isNaN(load) || Double.isInfinite(load)) {
            load = 0.5;
        }

        return load;
    }

    /**
     * @return CPU load obtained with JDK.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private double getJdk6CpuLoad() {
        assert jdkCpuLoadMtd != null;

        try {
            return (Double)jdkCpuLoadMtd.invoke(os) / 100;
        }
        // Purposely catch generic exception.
        catch (Exception e) {
            if (!isErrRep) {
                U.warn(log, "Failed to obtain JDK CPU load (will return -1): " + e.getMessage());

                isErrRep = true;
            }
        }

        return -1;
    }

    /**
     * @return CPU load obtained with Sigar.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private double getSigarCpuLoad() {
        assert sigar != null;

        try {
            return (Double)sigarCpuCombinedMtd.invoke(sigarCpuPercMtd.invoke(sigar));
        }
        // Purposely catch generic exception.
        catch (Exception e) {
            if (!isErrRep) {
                U.warn(log, "Failed to obtain Hyperic Sigar CPU load (will return -1 and won't report again): " +
                    e.getMessage());

                isErrRep = true;
            }
        }

        return -1;
    }

    /**
     * Returns file system root where GridGain JAR was located.
     *
     * @return File system root.
     */
    @SuppressWarnings({"UnusedCatchParameter"})
    @Nullable
    private File getDefaultFileSystemRoot() {
        URL clsUrl = getClass().getResource(getClass().getSimpleName() + ".class");

        try {
            String path = null;

            if (clsUrl != null) {
                path = "jar".equals(clsUrl.getProtocol()) ? new URL(clsUrl.getPath()).getPath() : clsUrl.getPath();
            }

            if (path != null) {
                File dir = new File(path);

                dir = dir.getParentFile();

                while (dir.getParentFile() != null) {
                    dir = dir.getParentFile();
                }

                return dir;
            }
        }
        catch (MalformedURLException e) {
            // No-op.
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public int getAvailableProcessors() {
        return metrics.getAvailableProcessors();
    }

    /** {@inheritDoc} */
    @Override public double getCurrentCpuLoad() {
        return metrics.getCurrentCpuLoad();
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryInitialized() {
        return metrics.getHeapMemoryInitialized();
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryUsed() {
        return metrics.getHeapMemoryUsed();
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryCommitted() {
        return metrics.getHeapMemoryCommitted();
    }

    /** {@inheritDoc} */
    @Override public long getHeapMemoryMaximum() {
        return metrics.getHeapMemoryMaximum();
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryInitialized() {
        return metrics.getNonHeapMemoryInitialized();
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryUsed() {
        return metrics.getNonHeapMemoryUsed();
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryCommitted() {
        return metrics.getNonHeapMemoryCommitted();
    }

    /** {@inheritDoc} */
    @Override public long getNonHeapMemoryMaximum() {
        return metrics.getNonHeapMemoryMaximum();
    }

    /** {@inheritDoc} */
    @Override public long getUptime() {
        return metrics.getUptime();
    }

    /** {@inheritDoc} */
    @Override public long getStartTime() {
        return metrics.getStartTime();
    }

    /** {@inheritDoc} */
    @Override public int getThreadCount() {
        return metrics.getThreadCount();
    }

    /** {@inheritDoc} */
    @Override public int getPeakThreadCount() {
        return metrics.getPeakThreadCount();
    }

    /** {@inheritDoc} */
    @Override public long getTotalStartedThreadCount() {
        return metrics.getTotalStartedThreadCount();
    }

    /** {@inheritDoc} */
    @Override public int getDaemonThreadCount() {
        return metrics.getDaemonThreadCount();
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemFreeSpace() {
        return metrics.getFileSystemFreeSpace();
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemTotalSpace() {
        return metrics.getFileSystemTotalSpace();
    }

    /** {@inheritDoc} */
    @Override public long getFileSystemUsableSpace() {
        return metrics.getFileSystemUsableSpace();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJdkLocalMetricsSpi.class, this);
    }
}
