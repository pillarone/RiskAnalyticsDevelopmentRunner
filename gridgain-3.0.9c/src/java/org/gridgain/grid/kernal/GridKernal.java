// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.controllers.*;
import org.gridgain.grid.kernal.controllers.affinity.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.kernal.controllers.rest.*;
import org.gridgain.grid.kernal.executor.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.checkpoint.*;
import org.gridgain.grid.kernal.managers.cloud.*;
import org.gridgain.grid.kernal.managers.collision.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.discovery.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.managers.failover.*;
import org.gridgain.grid.kernal.managers.loadbalancer.*;
import org.gridgain.grid.kernal.managers.metrics.*;
import org.gridgain.grid.kernal.managers.swapspace.*;
import org.gridgain.grid.kernal.managers.topology.*;
import org.gridgain.grid.kernal.managers.tracing.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.closure.*;
import org.gridgain.grid.kernal.processors.email.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.kernal.processors.resource.*;
import org.gridgain.grid.kernal.processors.rich.*;
import org.gridgain.grid.kernal.processors.schedule.*;
import org.gridgain.grid.kernal.processors.session.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.spi.cloud.*;
import org.gridgain.grid.spi.failover.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.spi.topology.*;
import org.gridgain.grid.spi.tracing.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;
import org.springframework.context.*;

import javax.management.*;
import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.kernal.GridKernalState.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;

/**
 * GridGain kernal.
 * <p/>
 * See <a href="http://en.wikipedia.org/wiki/Kernal">http://en.wikipedia.org/wiki/Kernal</a> for information on the
 * misspelling.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridKernal extends GridProjectionAdapter implements Grid, GridKernalMBean, Externalizable {
    /** Ant-augmented version number. */
    private static final String VER = "3.0.9c";

    /** Ant-augmented build number. */
    private static final String BUILD = "19052011";

    /** Ant-augmented copyright blurb. */
    private static final String COPYRIGHT = "2005-2011 Copyright (C) GridGain Systems, Inc.";

    /** */
    private static final String LICENSE_FILE = "gridgain-license.txt";

    /** System line separator. */
    private static final String NL = System.getProperty("line.separator");

    /** Periodic version check delay. */
    private static final long PERIODIC_VER_CHECK_DELAY = 1000 * 60 * 60; // Every hour.

    /** Periodic version check delay. */
    private static final long PERIODIC_VER_CHECK_CONN_TIMEOUT = 10 * 1000; // 10 seconds.

    /** Periodic version check delay. */
    private static final long PERIODIC_LIC_CHECK_DELAY = 1000 * 60; // Every minute.

    /** */
    private static final ThreadLocal<String> stash = new ThreadLocal<String>();

    /** */
    private static final GridPredicate<GridRichNode>[] EMPTY_PN = new PN[] {};

    /** Shutdown delay in msec. when license violation detected. */
    private static final int SHUTDOWN_DELAY = 60 * 1000;

    /** */
    private GridManagerRegistry mgrReg;

    /** */
    private GridProcessorRegistry procReg;

    /** */
    private GridControllerRegistry ctrlReg;

    /** */
    private GridConfiguration cfg;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridLogger log;

    /** */
    private String gridName;

    /** */
    private ObjectName kernalMBean;

    /** */
    private ObjectName locNodeMBean;

    /** */
    private ObjectName pubExecSvcMBean;

    /** */
    private ObjectName sysExecSvcMBean;

    /** */
    private ObjectName p2PExecSvcMBean;

    /** Kernal start timestamp. */
    private long startTime = System.currentTimeMillis();

    /** Proxy object factory. */
    private GridProxyFactory proxyFact;

    /** Spring context, potentially {@code null}. */
    private ApplicationContext springCtx;

    /** */
    private Timer updateNtfTimer;

    /** */
    private Timer licTimer;

    /** Indicate error on grid stop. */
    private boolean errOnStop;

    /** Node local store. */
    private GridNodeLocal nodeLocal;

    /** Kernal gateway. */
    private final AtomicReference<GridKernalGateway> gw = new AtomicReference<GridKernalGateway>();

    /** */
    private final GridBreaker stopBrk = new GridBreaker();

    /** Set of legacy discovery listeners. */
    @SuppressWarnings("deprecation")
    private final Map<GridDiscoveryListener, GridLocalEventListener> discoLsnrs =
        new IdentityHashMap<GridDiscoveryListener, GridLocalEventListener>();

    /**
     * No-arg constructor is required by externalization.
     */
    public GridKernal() {
        super(null);
    }

    /**
     * @param proxyFact Proxy objects factory.
     * @param springCtx Optional Spring application context.
     */
    public GridKernal(GridProxyFactory proxyFact, @Nullable ApplicationContext springCtx) {
        super(null);

        this.proxyFact = proxyFact;
        this.springCtx = springCtx;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        stash.set(U.readString(in));
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            return G.grid(stash.get());
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isEnterprise() {
        return U.isEnterprise();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> getAllNodes() {
        return nodes(EMPTY_PN);
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public String getName() {
        return gridName;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return gridName;
    }

    /** {@inheritDoc} */
    @Override public String getCopyright() {
        return COPYRIGHT;
    }

    /** {@inheritDoc} */
    @Override public String getLicenseFilePath() {
        assert cfg != null;

        return cfg.getGridGainHome() + File.separator + LICENSE_FILE;
    }

    /** {@inheritDoc} */
    @Override public long getStartTimestamp() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public String getStartTimestampFormatted() {
        return DateFormat.getDateTimeInstance().format(new Date(startTime));
    }

    /** {@inheritDoc} */
    @Override public long getUpTime() {
        return System.currentTimeMillis() - startTime;
    }

    /** {@inheritDoc} */
    @Override public String getUpTimeFormatted() {
        return X.timeSpan2HMSM(System.currentTimeMillis() - startTime);
    }

    /** {@inheritDoc} */
    @Override public String getFullVersion() {
        return VER + '-' + BUILD;
    }

    /** {@inheritDoc} */
    @Override public String getCheckpointSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getCheckpointSpi());
    }

    /** {@inheritDoc} */
    @Override public String getCloudSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getCloudSpi());
    }

    /** {@inheritDoc} */
    @Override public String getSwapSpaceSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getSwapSpaceSpi());
    }

    /** {@inheritDoc} */
    @Override public String getCommunicationSpiFormatted() {
        assert cfg != null;

        return cfg.getCommunicationSpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getDeploymentSpiFormatted() {
        assert cfg != null;

        return cfg.getDeploymentSpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getDiscoverySpiFormatted() {
        assert cfg != null;

        return cfg.getDiscoverySpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getEventStorageSpiFormatted() {
        assert cfg != null;

        return cfg.getEventStorageSpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getCollisionSpiFormatted() {
        assert cfg != null;

        return cfg.getCollisionSpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getFailoverSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getFailoverSpi());
    }

    /** {@inheritDoc} */
    @Override public String getLoadBalancingSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getLoadBalancingSpi());
    }

    /** {@inheritDoc} */
    @Override public String getMetricsSpiFormatted() {
        assert cfg != null;

        return cfg.getMetricsSpi().toString();
    }

    /** {@inheritDoc} */
    @Override public String getTopologySpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getTopologySpi());
    }

    /** {@inheritDoc} */
    @Override public String getTracingSpiFormatted() {
        assert cfg != null;

        return Arrays.toString(cfg.getTracingSpi());
    }

    /** {@inheritDoc} */
    @Override public String getOsInformation() {
        return U.osString();
    }

    /** {@inheritDoc} */
    @Override public String getJdkInformation() {
        return U.jdkString();
    }

    /** {@inheritDoc} */
    @Override public String getOsUser() {
        return System.getProperty("user.name");
    }

    /** {@inheritDoc} */
    @Override public String getVmName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    /** {@inheritDoc} */
    @Override public String getInstanceName() {
        return gridName;
    }

    /** {@inheritDoc} */
    @Override public String getExecutorServiceFormatted() {
        assert cfg != null;

        return cfg.getExecutorService().toString();
    }

    /** {@inheritDoc} */
    @Override public String getGridGainHome() {
        assert cfg != null;

        return cfg.getGridGainHome();
    }

    /** {@inheritDoc} */
    @Override public String getGridLoggerFormatted() {
        assert cfg != null;

        return cfg.getGridLogger().toString();
    }

    /** {@inheritDoc} */
    @Override public String getMBeanServerFormatted() {
        assert cfg != null;

        return cfg.getMBeanServer().toString();
    }

    /** {@inheritDoc} */
    @Override public UUID getLocalNodeId() {
        assert cfg != null;

        return cfg.getNodeId();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public Collection<String> getUserAttributesFormatted() {
        assert cfg != null;

        // That's why Java sucks...
        return F.transform(cfg.getUserAttributes().entrySet(), new C1<Map.Entry<String, ?>, String>() {
            @Override
            public String apply(Map.Entry<String, ?> e) {
                return e.getKey() + ", " + e.getValue().toString();
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean isPeerClassLoadingEnabled() {
        assert cfg != null;

        return cfg.isPeerClassLoadingEnabled();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public Collection<String> getLifecycleBeansFormatted() {
        GridLifecycleBean[] beans = cfg.getLifecycleBeans();

        return F.isEmpty(beans) ? Collections.<String>emptyList() : F.transform(beans, F.<GridLifecycleBean>string());
    }

    /**
     * @param spiCls SPI class.
     * @return Spi version.
     * @throws GridException Thrown if {@link GridSpiInfo} annotation cannot be found.
     */
    private Serializable getSpiVersion(Class<? extends GridSpi> spiCls) throws GridException {
        assert spiCls != null;

        GridSpiInfo ann = U.getAnnotation(spiCls, GridSpiInfo.class);

        if (ann == null)
            throw new GridException("SPI implementation does not have annotation: " + GridSpiInfo.class);

        return ann.version();
    }

    /**
     * @param attrs Current attributes.
     * @param name  New attribute name.
     * @param value New attribute value.
     * @throws GridException If duplicated SPI name found.
     */
    private void add(Map<String, Object> attrs, String name, @Nullable Serializable value) throws GridException {
        assert attrs != null;
        assert name != null;

        if (attrs.put(name, value) != null) {
            if (name.endsWith(ATTR_SPI_CLASS))
                // User defined duplicated names for the different SPIs.
                throw new GridException("Failed to set SPI attribute. Duplicated SPI name found: " +
                    name.substring(0, name.length() - ATTR_SPI_CLASS.length()));

            // Otherwise it's a mistake of setting up duplicated attribute.
            assert false : "Duplicate attribute: " + name;
        }
    }

    /**
     * Notifies life-cycle beans of grid event.
     *
     * @param evt Grid event.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void notifyLifecycleBeans(GridLifecycleEventType evt) {
        if (cfg.getLifecycleBeans() != null) {
            for (GridLifecycleBean bean : cfg.getLifecycleBeans()) {
                try {
                    bean.onLifecycleEvent(evt);
                }
                // Catch generic throwable to secure against user assertions.
                catch (Throwable e) {
                    U.error(log, "Failed to notify lifecycle bean (safely ignored) [evt=" + evt +
                        ", gridName=" + gridName + ", bean=" + bean + ']', e);
                }
            }
        }
    }

    /**
     * @param cfg Grid configuration to use.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings({"CatchGenericClass"})
    public void start(final GridConfiguration cfg) throws GridException {
        gw.compareAndSet(null, new GridKernalGatewayImpl(cfg.getGridName()));

        GridKernalGateway gw = this.gw.get();

        gw.writeLock();

        try {
            switch (gw.getState()) {
                case STARTED: {
                    U.warn(log, "Grid has already been started (ignored).");

                    return;
                }

                case STARTING: {
                    U.warn(log, "Grid is already in process of being started (ignored).");

                    return;
                }

                case STOPPING: {
                    throw new GridException("Grid is in process of being stopped");
                }

                case STOPPED: {
                    break;
                }
            }

            gw.setState(STARTING);
        }
        finally {
            gw.writeUnlock();
        }

        assert cfg != null;

        // Make sure we got proper configuration.
        A.notNull(cfg.getNodeId(), "cfg.getNodeId()");

        A.notNull(cfg.getMBeanServer(), "cfg.getMBeanServer()");
        A.notNull(cfg.getGridLogger(), "cfg.getGridLogger()");
        A.notNull(cfg.getMarshaller(), "cfg.getMarshaller()");
        A.notNull(cfg.getExecutorService(), "cfg.getExecutorService()");
        A.notNull(cfg.getUserAttributes(), "cfg.getUserAttributes()");

        // All SPIs should be non-null.
        A.notNull(cfg.getSwapSpaceSpi(), "cfg.getSwapSpaceSpi()");
        A.notNull(cfg.getCheckpointSpi(), "cfg.getCheckpointSpi()");
        A.notNull(cfg.getCommunicationSpi(), "cfg.getCommunicationSpi()");
        A.notNull(cfg.getDeploymentSpi(), "cfg.getDeploymentSpi()");
        A.notNull(cfg.getDiscoverySpi(), "cfg.getDiscoverySpi()");
        A.notNull(cfg.getEventStorageSpi(), "cfg.getEventStorageSpi()");
        A.notNull(cfg.getMetricsSpi(), "cfg.getMetricsSpi()");
        A.notNull(cfg.getCloudSpi(), "cfg.getCloudSpi()");
        A.notNull(cfg.getCollisionSpi(), "cfg.getCollisionSpi()");
        A.notNull(cfg.getFailoverSpi(), "cfg.getFailoverSpi()");
        A.notNull(cfg.getLoadBalancingSpi(), "cfg.getLoadBalancingSpi()");
        A.notNull(cfg.getTopologySpi(), "cfg.getTopologySpi()");

        gridName = cfg.getGridName();

        this.cfg = cfg;

        log = cfg.getGridLogger().getLogger(getClass().getName() + (gridName != null ? '%' + gridName : ""));

        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();

        // Ack various information.
        ackAsciiLogo();
        ackEdition();
        ackDaemon();
        ackLanguageRuntime();
        ackRemoteManagement();
        ackVmArguments(rtBean);
        ackClassPaths(rtBean);
        ackSystemProperties();
        ackEnvironmentVariables();
        ackSmtpConfiguration();
        ackCacheConfiguration();

        // Run background network diagnostics.
        GridDiagnostic.runBackgroundCheck(gridName, cfg.getExecutorService(), log);

        GridUpdateNotifier verChecker = null;

        String isNotify = System.getProperty(GG_UPDATE_NOTIFIER);

        if (isNotify == null || !"false".equals(isNotify)) {
            verChecker = new GridUpdateNotifier(gridName, false, 0);

            verChecker.checkForNewVersion(cfg.getExecutorService(), log);
        }

        // Ack 3-rd party licenses location.
        if (log.isInfoEnabled() && cfg.getGridGainHome() != null)
            log.info("3-rd party licenses can be found at: " + cfg.getGridGainHome() + File.separatorChar + "libs" +
                File.separatorChar + "licenses");

        // Check that user attributes are not conflicting
        // with internally reserved names.
        for (String name : cfg.getUserAttributes().keySet())
            if (name.startsWith(ATTR_PREFIX))
                throw new GridException("User attribute has illegal name: '" + name + "'. Note that all names " +
                    "starting with '" + ATTR_PREFIX + "' are reserved for internal use.");

        // Ack local node user attributes.
        logNodeUserAttributes();

        // Ack configuration.
        ackSpis();

        Map<String, Object> attrs = createNodeAttributes(cfg);

        // Spin out SPIs & managers.
        try {
            mgrReg = new GridManagerRegistry();
            procReg = new GridProcessorRegistry();
            ctrlReg = new GridControllerRegistry();

            GridKernalContext ctx = new GridKernalContextImpl(mgrReg, procReg, ctrlReg, this, cfg, gw);

            nodeLocal = new GridNodeLocalImpl(ctx);

            // Set context into rich adapter.
            setKernalContext(ctx);

            // Start and configure resource processor first as it contains resources used
            // by all other managers and processors.
            GridResourceProcessor rsrcProc = new GridResourceProcessor(ctx);

            rsrcProc.setSpringContext(springCtx);

            startProcessor(rsrcProc);

            // Inject resources into lifecycle beans.
            if (cfg.getLifecycleBeans() != null)
                for (GridLifecycleBean bean : cfg.getLifecycleBeans())
                    rsrcProc.inject(bean);

            // Lifecycle notification.
            notifyLifecycleBeans(GridLifecycleEventType.BEFORE_GRID_START);

            // Start some other processors (order & place is important).
            startProcessor(new GridEmailProcessor(ctx));
            startProcessor(new GridPortProcessor(ctx));
            startProcessor(new GridRichProcessor(ctx));
            startProcessor(new GridJobMetricsProcessor(ctx));

            // Start tracing manager first if there is SPI.
            // By default no tracing SPI provided.
            if (cfg.getTracingSpi() != null) {
                GridTracingManager traceMgr = new GridTracingManager(ctx);

                // Configure proxy factory for tracing.
                traceMgr.setProxyFactory(proxyFact);

                startManager(traceMgr, attrs);
            }

            // Timeout processor needs to be started before managers,
            // as managers may depend on it.
            startProcessor(new GridTimeoutProcessor(ctx));

            // Start SPI managers.
            // NOTE: that order matters as there are dependencies between managers.
            startManager(new GridLocalMetricsManager(ctx), attrs);
            startManager(new GridIoManager(ctx), attrs);
            startManager(new GridCheckpointManager(ctx), attrs);

            startManager(new GridEventStorageManager(ctx), attrs);
            startManager(new GridDeploymentManager(ctx), attrs);
            startManager(new GridLoadBalancerManager(ctx), attrs);
            startManager(new GridFailoverManager(ctx), attrs);
            startManager(new GridCollisionManager(ctx), attrs);
            startManager(new GridTopologyManager(ctx), attrs);
            startManager(new GridSwapSpaceManager(ctx), attrs);
            startManager(new GridCloudManager(ctx), attrs);

            // Create the controllers. Order is important.
            createController(GridLicenseController.class);
            createController(GridAffinityController.class);
            createController(GridRestController.class);

            // Start processors before discovery manager, so they will
            // be able to start receiving messages once discovery completes.
            startProcessor(new GridTaskSessionProcessor(ctx));
            startProcessor(new GridTaskProcessor(ctx));
            startProcessor(new GridJobProcessor(ctx));
            startProcessor(new GridCacheProcessor(ctx));
            startProcessor(new GridClosureProcessor(ctx));
            startProcessor(new GridScheduleProcessor(ctx));

            gw.writeLock();

            try {
                gw.setState(STARTED);

                // Start discovery manager last to make sure that grid is fully initialized.
                startManager(new GridDiscoveryManager(ctx), attrs);
            }
            finally {
                gw.writeUnlock();
            }

            // Callbacks.
            for (GridManager mgr : mgrReg)
                mgr.onKernalStart();

            // Callbacks - start the controllers.
            for (GridController ctrl : ctrlReg)
                ctrl.afterKernalStart(ctx);

            // Ack the license.
            ctx.license().ackLicense();

            // Callbacks.
            for (GridProcessor proc : procReg)
                proc.onKernalStart();

            // Register MBeans.
            registerKernalMBean();
            registerLocalNodeMBean();
            registerExecutorMBeans();

            // Lifecycle bean notifications.
            notifyLifecycleBeans(GridLifecycleEventType.AFTER_GRID_START);
        }
        catch (Throwable e) {
            U.error(log, "Got exception while starting. Will rollback startup routine.", e);

            stop(false, false);

            throw new GridException(e);
        }

        // Mark start timestamp.
        startTime = System.currentTimeMillis();

        // Ack latest version information.
        if (verChecker != null)
            verChecker.reportStatus(log);

        updateNtfTimer = new Timer("gridgain-update-notifier-timer");

        // Setup periodic version check.
        updateNtfTimer.scheduleAtFixedRate(new GridTimerTask() {
            @Override public void safeRun() throws InterruptedException {
                GridUpdateNotifier notifier = new GridUpdateNotifier(gridName, true, nodes(EMPTY_PN).size());

                notifier.checkForNewVersion(cfg.getExecutorService(), log);

                Thread.sleep(10 * 1000); // 10 seconds to open HTML page.

                notifier.reportStatus(log);
            }
        }, PERIODIC_VER_CHECK_DELAY, PERIODIC_VER_CHECK_DELAY);

        licTimer = new Timer("gridgain-license-checker");

        // Setup periodic license check.
        licTimer.scheduleAtFixedRate(new GridTimerTask() {
            @Override public void safeRun() throws InterruptedException {
                try {
                    ctx.license().checkLicense();
                }
                // This exception only happens when license controller was unable
                // to resolve license violation on its own and this grid instance
                // now needs to be shutdown.
                //
                // Note that in most production configurations the license will
                // have certain grace period and license controller will attempt
                // to reload the license during the grace period.
                //
                // This exception thrown here means that grace period, if any,
                // has expired and license violation is still unresolved.
                catch (GridLicenseException ignored) {
                    U.error(log, "License violation is unresolved. GridGain node will shutdown in " +
                        (SHUTDOWN_DELAY / 1000) + " sec.");
                    U.error(log, "  ^-- Contact your support for immediate assistance (!)");

                    // Allow interruption to break from here since
                    // node is stopping anyways.
                    Thread.sleep(SHUTDOWN_DELAY);

                    G.stop(gridName, true);
                }
                // Safety net.
                catch (Throwable e) {
                    U.error(log, "Unable to check the license due to system error.", e);
                    U.error(log, "Grid instance will be stopped...");

                    // Stop the grid if we get unknown license-related error.
                    // Should never happen. Practically an assertion...
                    G.stop(gridName, true);
                }
            }
        }, PERIODIC_LIC_CHECK_DELAY, PERIODIC_LIC_CHECK_DELAY);

        if (log.isQuiet()) {
            U.quiet("System info:");
            U.quiet("    JVM: " + U.jvmVendor() + ", " + U.jreName() + " ver. " + U.jreVersion());
            U.quiet("    OS: " + U.osString() + ", " + System.getProperty("user.name"));
            U.quiet("    VM name: " + rtBean.getName());

            SB sb = new SB();

            for (GridPortRecord rec : ctx.ports().records())
                sb.a(rec.protocol()).a(":").a(rec.port()).a(" ");

            U.quiet("Local ports used [" + sb.toString().trim() + ']');

            GridNode locNode = localNode();

            U.quiet("GridGain started OK", "  ^-- [" +
                "grid=" + (gridName == null ? "default" : gridName) +
                ", nodeId8=" + U.id8(locNode.id()) +
                ", order=" + locNode.order() +
                ", CPUs=" + locNode.metrics().getTotalCpus() +
                ", addrs=" + getAddresses(locNode) +
                ']');

            U.quiet("ZZZzz zz z...");
        }
        else if (log.isInfoEnabled()) {
            String ack = "GridGain ver. " + VER + '-' + BUILD;

            String dash = U.dash(ack.length());

            SB sb = new SB();

            for (GridPortRecord rec : ctx.ports().records())
                sb.a(rec.protocol()).a(":").a(rec.port()).a(" ");

            String str =
                NL + NL +
                    ">>> " + dash + NL +
                    ">>> " + ack + NL +
                    ">>> " + dash + NL +
                    ">>> OS name: " + U.osString() + NL +
                    ">>> OS user: " + System.getProperty("user.name") + NL +
                    ">>> CPU(s): " + localNode().metrics().getTotalCpus() + NL +
                    ">>> VM information: " + U.jdkString() + NL +
                    ">>> VM name: " + rtBean.getName() + NL +
                    ">>> Grid name: " + gridName + NL +
                    ">>> Local node [" +
                        "ID=" + localNode().id().toString().toUpperCase() +
                        ", order=" + localNode().order() +
                    "]" + NL +
                    ">>> Local node addresses: " + getAddresses(localNode()) + NL +
                    ">>> Local ports: " + sb + NL;

            str += ">>> GridGain documentation: http://www.gridgain.com/product.html" + NL;

            log.info(str);
        }

        // Send node start email notification, if enabled.
        if (isSmtpEnabled() && isAdminEmailsSet() && cfg.isLifeCycleEmailNotification() && isEnterprise()) {
            SB sb = new SB();

            for (GridPortRecord rec : ctx.ports().records())
                sb.a(rec.protocol()).a(":").a(rec.port()).a(" ");

            String nid = localNode().id().toString().toUpperCase();
            String nid8 = localNode().id8().toUpperCase();

            GridEnterpriseLicense lic = ctx.license().license();

            String body =
                "GridGain node started with the following parameters:" + NL +
                NL +
                "----" + NL +
                "GridGain ver. " + VER + '-' + BUILD + NL +
                "Grid name: " + gridName + NL +
                "Node ID: " + nid + NL +
                "Node order: " + localNode().order() + NL +
                "Node addresses: " + getAddresses(localNode()) + NL +
                "Local ports: " + sb + NL +
                "OS name: " + U.osString() + NL +
                "OS user: " + System.getProperty("user.name") + NL +
                "CPU(s): " + localNode().metrics().getTotalCpus() + NL +
                "JVM name: " + U.jvmName() + NL +
                "JVM vendor: " + U.jvmVendor() + NL +
                "JVM version: " + U.jvmVersion() + NL +
                "VM name: " + rtBean.getName() + NL +
                "License ID: "  +  lic.getId().toString().toUpperCase() + NL +
                "Licesned to: " + lic.getUserOrganization() + NL +
                "----" + NL +
                NL +
                "NOTE:" + NL +
                "This message is sent automatically to all configured admin emails." + NL +
                "To change this behavior use 'lifeCycleEmailNotify' grid configuration property." +
                NL + NL +
                "| www.gridgain.com" + NL +
                "| support@gridgain.com" + NL;

            sendAdminEmailAsync("GridGain node started: " + nid8, body, false);
        }
    }

    /**
     * @param node Grid node to get addresses for.
     * @return String containing internal and external addresses.
     */
    private String getAddresses(GridNode node) {
        Collection<String> addrs = new HashSet<String>();

        addrs.addAll(node.internalAddresses());
        addrs.addAll(node.externalAddresses());

        return addrs.toString();
    }

    /**
     * Creates attributes map and fills it in.
     *
     * @param cfg Grid configuration.
     * @return Map of all node attributes.
     * @throws GridException thrown if was unable to set up attribute.
     */
    @SuppressWarnings({"SuspiciousMethodCalls", "unchecked"})
    private Map<String, Object> createNodeAttributes(GridConfiguration cfg) throws GridException {
        Map<String, Object> attrs = new HashMap<String, Object>();

        final String[] includeProps = cfg.getIncludeProperties();

        if (includeProps == null || includeProps.length > 0) {
            try {
                // Stick all environment settings into node attributes.
                attrs.putAll(F.view(System.getenv(), new P1<String>() {
                    @Override public boolean apply(String name) {
                        return includeProps == null || U.containsStringArray(includeProps, name, true);
                    }
                }));

                if (log.isDebugEnabled())
                    log.debug("Added environment properties to node attributes.");
            }
            catch (SecurityException e) {
                throw new GridException("Failed to add environment properties to node attributes due to " +
                    "security violation: " + e.getMessage());
            }

            try {
                // Stick all system properties into node's attributes overwriting any
                // identical names from environment properties.
                for (Map.Entry<Object, Object> e : F.view(System.getProperties(), new P1<Object>() {
                    @Override public boolean apply(Object o) {
                        String name = (String)o;

                        return includeProps == null || U.containsStringArray(includeProps, name, true);
                    }
                }).entrySet()) {
                    Object val = attrs.get(e.getKey());

                    if (val != null && !val.equals(e.getValue()))
                        U.warn(log, "System property will override environment variable with the same name: "
                            + e.getKey());

                    attrs.put((String)e.getKey(), e.getValue());
                }

                if (log.isDebugEnabled())
                    log.debug("Added system properties to node attributes.");
            }
            catch (SecurityException e) {
                throw new GridException("Failed to add system properties to node attributes due to security violation: " +
                    e.getMessage());
            }
        }

        // Add local network IPs and MACs.
        String ips = U.allLocalIps(); // Exclude loopbacks.
        String macs = U.allLocalMACs(); // Only enabled network interfaces.

        // Ack network context.
        if (log.isInfoEnabled()) {
            log.info("Non-loopback local IPs: " + (ips == null ? "N/A" : ips));
            log.info("Enabled local MACs: " + (macs == null ? "N/A" : macs));
        }

        // Warn about loopback.
        if (ips == null && macs == null)
            U.warn(log, "GridGain is starting on loopback address... Only nodes on the same physical " +
                "computer can participate in topology.",
                "GridGain is starting on loopback address...");

        // Stick in network context into attributes.
        add(attrs, ATTR_IPS, (ips == null ? "" : ips));
        add(attrs, ATTR_MACS, (macs == null ? "" : macs));

        // Stick in some system level attributes
        add(attrs, ATTR_JIT_NAME, U.getCompilerMx() == null ? "" : U.getCompilerMx().getName());
        add(attrs, ATTR_BUILD_VER, getFullVersion());
        add(attrs, ATTR_USER_NAME, System.getProperty("user.name"));
        add(attrs, ATTR_GRID_NAME, gridName);

        add(attrs, ATTR_DEPLOYMENT_MODE, cfg.getDeploymentMode());
        add(attrs, ATTR_LANG_RUNTIME, getLanguage());

        // Check daemon system property and override configuration if it's set.
        if (isDaemon())
            add(attrs, ATTR_DAEMON, "true");

        // Check edition and stick in edition attribute.
        if (isEnterprise())
            add(attrs, ATTR_ENT_EDITION, "true");

        // In case of the parsing error, JMX remote disabled or port not being set
        // node attribute won't be set.
        if (isJmxRemoteEnabled()) {
            String portStr = System.getProperty("com.sun.management.jmxremote.port");

            if (portStr != null)
                try {
                    add(attrs, ATTR_JMX_PORT, Integer.parseInt(portStr));
                }
                catch (NumberFormatException ignore) {
                    // No-op.
                }
        }

        /*
         * Stick in SPI versions and classes attributes.
         */

        // Collision SPI.
        Class<? extends GridSpi> spiCls = cfg.getCollisionSpi().getClass();

        add(attrs, U.spiAttribute(cfg.getCollisionSpi(), ATTR_SPI_CLASS), spiCls.getName());
        add(attrs, U.spiAttribute(cfg.getCollisionSpi(), ATTR_SPI_VER), getSpiVersion(spiCls));

        // SwapSpace SPIs.
        for (GridSwapSpaceSpi swapSpi : cfg.getSwapSpaceSpi()) {
            spiCls = swapSpi.getClass();

            add(attrs, U.spiAttribute(swapSpi, ATTR_SPI_CLASS), spiCls.getName());
            add(attrs, U.spiAttribute(swapSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
        }

        // Cloud SPIs.
        if (cfg.getCloudSpi() != null) {
            if (!cfg.isDisableCloudCoordinator()) {
                ArrayList<String> cloudIds = new ArrayList<String>();

                for (GridCloudSpi cloudSpi : cfg.getCloudSpi()) {
                    spiCls = cloudSpi.getClass();

                    add(attrs, U.spiAttribute(cloudSpi, ATTR_SPI_CLASS), spiCls.getName());
                    add(attrs, U.spiAttribute(cloudSpi, ATTR_SPI_VER), getSpiVersion(spiCls));

                    cloudIds.add(cloudSpi.getCloudId());
                }

                add(attrs, ATTR_CLOUD_IDS, cloudIds);
            }
            else
                add(attrs, ATTR_CLOUD_CRD_DISABLE, null);
        }

        // Topology SPIs.
        for (GridTopologySpi topSpi : cfg.getTopologySpi()) {
            spiCls = topSpi.getClass();

            add(attrs, U.spiAttribute(topSpi, ATTR_SPI_CLASS), spiCls.getName());
            add(attrs, U.spiAttribute(topSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
        }

        // Discovery SPI.
        spiCls = cfg.getDiscoverySpi().getClass();

        add(attrs, U.spiAttribute(cfg.getDiscoverySpi(), ATTR_SPI_CLASS), spiCls.getName());
        add(attrs, U.spiAttribute(cfg.getDiscoverySpi(), ATTR_SPI_VER), getSpiVersion(spiCls));

        // Failover SPIs.
        for (GridFailoverSpi failSpi : cfg.getFailoverSpi()) {
            spiCls = failSpi.getClass();

            add(attrs, U.spiAttribute(failSpi, ATTR_SPI_CLASS), spiCls.getName());
            add(attrs, U.spiAttribute(failSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
        }

        // Communication SPI.
        spiCls = cfg.getCommunicationSpi().getClass();

        add(attrs, U.spiAttribute(cfg.getCommunicationSpi(), ATTR_SPI_CLASS), spiCls.getName());
        add(attrs, U.spiAttribute(cfg.getCommunicationSpi(), ATTR_SPI_VER), getSpiVersion(spiCls));

        // Event storage SPI.
        spiCls = cfg.getEventStorageSpi().getClass();

        add(attrs, U.spiAttribute(cfg.getEventStorageSpi(), ATTR_SPI_CLASS), spiCls.getName());
        add(attrs, U.spiAttribute(cfg.getEventStorageSpi(), ATTR_SPI_VER), getSpiVersion(spiCls));

        // Tracing SPIs.
        if (cfg.getTracingSpi() != null)
            for (GridTracingSpi traceSpi : cfg.getTracingSpi()) {
                spiCls = traceSpi.getClass();

                add(attrs, U.spiAttribute(traceSpi, ATTR_SPI_CLASS), spiCls.getName());
                add(attrs, U.spiAttribute(traceSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
            }

        // Checkpoints SPIs.
        for (GridCheckpointSpi cpSpi : cfg.getCheckpointSpi()) {
            spiCls = cpSpi.getClass();

            add(attrs, U.spiAttribute(cpSpi, ATTR_SPI_CLASS), spiCls.getName());
            add(attrs, U.spiAttribute(cpSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
        }

        // Load balancing SPIs.
        for (GridLoadBalancingSpi loadSpi : cfg.getLoadBalancingSpi()) {
            spiCls = loadSpi.getClass();

            add(attrs, U.spiAttribute(loadSpi, ATTR_SPI_CLASS), spiCls.getName());
            add(attrs, U.spiAttribute(loadSpi, ATTR_SPI_VER), getSpiVersion(spiCls));
        }

        // Metrics SPI.
        spiCls = cfg.getMetricsSpi().getClass();

        add(attrs, U.spiAttribute(cfg.getMetricsSpi(), ATTR_SPI_CLASS), spiCls.getName());
        add(attrs, U.spiAttribute(cfg.getMetricsSpi(), ATTR_SPI_VER), getSpiVersion(spiCls));

        // Deployment SPI.
        spiCls = cfg.getDeploymentSpi().getClass();

        add(attrs, U.spiAttribute(cfg.getDeploymentSpi(), ATTR_SPI_VER), getSpiVersion(spiCls));
        add(attrs, U.spiAttribute(cfg.getDeploymentSpi(), ATTR_SPI_CLASS), spiCls.getName());

        // Set cache attribute.
        GridCacheAttributes[] cacheAttrVals = new GridCacheAttributes[cfg.getCacheConfiguration().length];

        int i = 0;

        for (GridCacheConfiguration cacheCfg : cfg.getCacheConfiguration()) {
            GridCacheAffinity aff = cacheCfg.getAffinity();

            cacheAttrVals[i++] = new GridCacheAttributes(
                cacheCfg.getName(),
                cacheCfg.getCacheMode() != null ? cacheCfg.getCacheMode() : GridCacheConfiguration.DFLT_CACHE_MODE,
                cacheCfg.getCacheMode() == PARTITIONED && cacheCfg.isNearEnabled(),
                cacheCfg.getPreloadMode(),
                aff != null ? aff.getClass().getCanonicalName() : null);
        }

        attrs.put(ATTR_CACHE, cacheAttrVals);

        // Set user attributes for this node.
        if (cfg.getUserAttributes() != null)
            for (Map.Entry<String, ?> e : cfg.getUserAttributes().entrySet()) {
                if (attrs.containsKey(e.getKey()))
                    U.warn(log, "User or internal attribute has the same name as environment or system " +
                        "property and will take precedence: " + e.getKey());

                attrs.put(e.getKey(), e.getValue());
            }

        return attrs;
    }

    /** @throws GridException If registration failed. */
    private void registerKernalMBean() throws GridException {
        try {
            kernalMBean = U.registerMBean(
                cfg.getMBeanServer(),
                cfg.getGridName(),
                "Kernal",
                getClass().getSimpleName(),
                this,
                GridKernalMBean.class);

            if (log.isDebugEnabled())
                log.debug("Registered kernal MBean: " + kernalMBean);
        }
        catch (JMException e) {
            kernalMBean = null;

            throw new GridException("Failed to register kernal MBean.", e);
        }
    }

    /** @throws GridException If registration failed. */
    private void registerLocalNodeMBean() throws GridException {
        GridNodeMetricsMBean mbean = new GridLocalNodeMetrics(mgrReg.discovery().localNode());

        try {
            locNodeMBean = U.registerMBean(
                cfg.getMBeanServer(),
                cfg.getGridName(),
                "Kernal",
                mbean.getClass().getSimpleName(),
                mbean,
                GridNodeMetricsMBean.class);

            if (log.isDebugEnabled())
                log.debug("Registered local node MBean: " + locNodeMBean);
        }
        catch (JMException e) {
            locNodeMBean = null;

            throw new GridException("Failed to register local node MBean.", e);
        }
    }

    /** @throws GridException If registration failed. */
    private void registerExecutorMBeans() throws GridException {
        pubExecSvcMBean = registerExecutorMBean(cfg.getExecutorService(), "GridExecutionExecutor");
        sysExecSvcMBean = registerExecutorMBean(cfg.getSystemExecutorService(), "GridSystemExecutor");
        p2PExecSvcMBean = registerExecutorMBean(cfg.getPeerClassLoadingExecutorService(), "GridClassLoadingExecutor");
    }

    /**
     * @param exec Executor service to register.
     * @param name Property name for executor.
     * @return Name for created MBean.
     * @throws GridException If registration failed.
     */
    private ObjectName registerExecutorMBean(ExecutorService exec, String name) throws GridException {
        try {
            ObjectName res = U.registerMBean(
                cfg.getMBeanServer(),
                cfg.getGridName(),
                "Thread Pools",
                name,
                new GridExecutorServiceMBeanAdapter(exec),
                GridExecutorServiceMBean.class);

            if (log.isDebugEnabled())
                log.debug("Registered executor service MBean: " + res);

            return res;
        }
        catch (JMException e) {
            throw new GridException("Failed to register executor service MBean [name=" + name + ", exec=" + exec + ']',
                e);
        }
    }

    /**
     * Unregisters given mbean.
     *
     * @param mbean MBean to unregister.
     * @return {@code True} if successfully unregistered, {@code false} otherwise.
     */
    private boolean unregisterMBean(@Nullable ObjectName mbean) {
        if (mbean != null)
            try {
                cfg.getMBeanServer().unregisterMBean(mbean);

                if (log.isDebugEnabled())
                    log.debug("Unregistered MBean: " + mbean);

                return true;
            }
            catch (JMException e) {
                U.error(log, "Failed to unregister MBean.", e);

                return false;
            }

        return true;
    }

    /**
     * @param mgr   Manager to start.
     * @param attrs SPI attributes to set.
     * @throws GridException Throw in case of any errors.
     */
    private void startManager(GridManager mgr, Map<String, Object> attrs) throws GridException {
        assert mgrReg != null;

        mgr.addSpiAttributes(attrs);

        // Set all node attributes into discovery manager,
        // so they can be distributed to all nodes.
        if (mgr instanceof GridDiscoveryManager)
            ((GridDiscoveryManager)mgr).setNodeAttributes(attrs);

        // Add manager to registry before it starts to avoid
        // cases when manager is started but registry does not
        // have it yet.
        mgrReg.add(mgr);

        try {
            mgr.start();
        }
        catch (GridException e) {
            throw new GridException("Failed to start manager: " + mgr, e);
        }
    }

    /**
     * @param proc Processor to start.
     * @throws GridException Thrown in case of any error.
     */
    private void startProcessor(GridProcessor proc) throws GridException {
        procReg.add(proc);

        try {
            proc.start();
        }
        catch (GridException e) {
            throw new GridException("Failed to start processor: " + proc, e);
        }
    }

    /**
     * Creates controller for given interface. Implementation should be in "impl" sub-package
     * and class name should have "Impl" postfix.
     *
     * @param itf Controller interface.
     * @param <T> Type of the controller interface.
     * @return Controller instance or no-op dynamic proxy for this interface.
     * @throws GridException Thrown if controller fails to initialize.
     * @throws IllegalArgumentException Thrown if input class is not a valid controller
     *      interface.
     */
    @SuppressWarnings("unchecked")
    private <T extends GridController> T createController(Class<T> itf) throws GridException {
        assert itf != null;

        Package pkg = itf.getPackage();

        if (pkg == null)
            throw new GridException("Internal error (package object was not found) for: " + itf);

        Class<?> cls = null;

        GridController ctrl = null;

        try {
            cls = Class.forName(pkg.getName() + ".impl." + itf.getSimpleName() + "Impl");
        }
        catch (ClassNotFoundException ignore) {
            ctrl = (GridController)Proxy.newProxyInstance(itf.getClassLoader(), new Class[] {itf},
                new InvocationHandler() {
                    @Nullable
                    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return "implemented".equals(method.getName()) && args == null ?  Boolean.FALSE : null;
                    }
                }
            );
        }

        if (cls != null) {
            if (!itf.isAssignableFrom(cls))
                throw new IllegalArgumentException("Type does not represent valid controller: " + itf);

            try {
                ctrl = (GridController)cls.newInstance();

                ctrl.init();
            }
            catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Failed to instantiate controller for: " + itf, e);
            }
            catch (InstantiationException e) {
                throw new IllegalArgumentException("Failed to instantiate controller for: " + itf, e);
            }
        }

        assert ctrl != null;

        if (log.isDebugEnabled())
            log.debug("Controller started [itf=" + itf.getSimpleName() + ", proxy=" + !ctrl.implemented() + ']');

        ctrlReg.add(ctrl);

        return (T)ctrl;
    }

    /**
     * Gets "on" or "off" string for given boolean value.
     *
     * @param b Boolean value to convert.
     * @return Result string.
     */
    private String onOff(boolean b) {
        return b ? "on" : "off";
    }

    /**
     *
     * @return Whether or not REST is enabled.
     */
    private boolean isRestEnabled() {
        assert cfg != null;

        return cfg.isRestEnabled();
    }

    /**
     * Acks remote management.
     */
    private void ackRemoteManagement() {
        if (!isEnterprise() && isRestEnabled())
            U.warn(log, "REST is not supported in Community Edition (ignoring).");

        SB sb = new SB();

        sb.a("Remote Management [");

        boolean on = isJmxRemoteEnabled();

        sb.a("restart: ").a(onOff(isRestartEnabled())).a(", ");
        sb.a("REST: ").a(onOff(isRestEnabled())).a(", ");
        sb.a("JMX (");
        sb.a("remote: ").a(onOff(on));

        if (on) {
            sb.a(", ");

            sb.a("port: ").a(System.getProperty("com.sun.management.jmxremote.port", "<n/a>")).a(", ");
            sb.a("auth: ").a(onOff(Boolean.getBoolean("com.sun.management.jmxremote.authenticate"))).a(", ");
            sb.a("ssl: ").a(onOff(Boolean.getBoolean("com.sun.management.jmxremote.ssl")));
        }

        sb.a(")");

        sb.a(']');

        U.log(log, sb);
    }

    /**
     * Acks GridGain edition.
     */
    private void ackEdition() {
        assert log != null;

        U.log(log, "<< " + (isEnterprise() ? "Enterprise" : "Community") + " Edition >>");
    }

    /**
     * Acks ASCII-logo. Thanks to http://patorjk.com/software/taag
     */
    private void ackAsciiLogo() {
        assert log != null;

        if (System.getProperty(GG_NO_ASCII) == null) {
            String tag = "---==++ HIGH PERFORMANCE CLOUD COMPUTING ++==---";
            String ver = "ver. " + VER + '-' + BUILD;

            // Big thanks to: http://patorjk.com/software/taag
            // Font name "Small Slant"
            if (log.isQuiet())
                U.quiet(
                    "  _____     _     _______      _         ____  ___",
                    " / ___/____(_)___/ / ___/___ _(_)___    |_  / <  /",
                    "/ (_ // __/ // _  / (_ // _ `/ // _ \\  _/_ <_ / / ",
                    "\\___//_/ /_/ \\_,_/\\___/ \\_,_/_//_//_/ /____(_)_/",
                    "",
                    tag,
                    U.pad((tag.length() - ver.length()) / 2) + ver,
                    " " + COPYRIGHT,
                    "",
                    "Quiet mode.",
                    "  ^-- To disable add -DGRIDGAIN_QUIET=false or \"-v\" to ggstart.{sh|bat}"
                );
            else if (log.isInfoEnabled())
                log.info(NL + NL +
                    ">>>   _____     _     _______      _         ____  ___" + NL +
                    ">>>  / ___/____(_)___/ / ___/___ _(_)___    |_  / <  /" + NL +
                    ">>> / (_ // __/ // _  / (_ // _ `/ // _ \\  _/_ <_ / / " + NL +
                    ">>> \\___//_/ /_/ \\_,_/\\___/ \\_,_/_//_//_/ /____(_)_/" + NL +
                    ">>> " + NL +
                    ">>> " + tag + NL +
                    ">>> " + U.pad((tag.length() - ver.length()) / 2) + ver + NL +
                    ">>>  " + COPYRIGHT + NL
                );
        }
    }

    /**
     * Logs out language runtime.
     */
    private void ackLanguageRuntime() {
        U.log(log, "Language runtime: " + getLanguage());
    }

    /**
     * @return Language runtime.
     */
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private String getLanguage() {
        boolean scala = false;
        boolean groovy = false;
        boolean clojure = false;

        for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
            String s = elem.getClassName().toLowerCase();

            if (s.contains("scala")) {
                scala = true;

                break;
            }
            else if (s.contains("groovy")) {
                groovy = true;

                break;
            }
            else if (s.contains("clojure")) {
                clojure = true;

                break;
            }
        }

        if (scala) {
            Properties props = new Properties();

            try {
                props.load(getClass().getResourceAsStream("/library.properties"));

                return "Scala ver. " + props.getProperty("version.number", "<unknown>");
            }
            catch (Throwable ignore) {
                return "Scala ver. <unknown>";
            }
        }

        // How to get Groovy and Clojure version at runtime?!?
        return groovy ? "Groovy" : clojure ? "Clojure" : U.jdkName() + " ver. " + U.jdkVersion();
    }

    /**
     * Stops the processor.
     *
     * @param proc Processor to stop.
     * @param cancel Cancellation flag.
     * @param wait Wait flag.
     */
    private void stopProcessor(@Nullable GridProcessor proc, boolean cancel, boolean wait) {
        if (proc != null)
            try {
                proc.stop(cancel, wait);
            }
            catch (Throwable e) {
                errOnStop = true;

                U.error(log, "Failed to stop processor (ignoring): " + proc, e);
            }
    }

    /**
     * Stops grid instance.
     *
     * @param cancel Whether or not to cancel running jobs.
     * @param wait If {@code true} then method will wait for all task being executed until they finish their
     *      execution.
     */
    public void stop(boolean cancel, boolean wait) {
        String nid = getLocalNodeId().toString().toUpperCase();
        String nid8 = U.id8(getLocalNodeId()).toUpperCase();

        gw.compareAndSet(null, new GridKernalGatewayImpl(gridName));

        boolean firstStop = false;

        GridKernalGateway gw = this.gw.get();

        synchronized (stopBrk) {
            gw.writeLock();

            try {
                switch (gw.getState()) {
                    case STARTED: {
                        if (stopBrk.isOn()) {
                            firstStop = true;

                            stopBrk.trip();
                        }

                        break;
                    }

                    case STARTING: {
                        U.warn(log, "Attempt to stop starting grid. This operation " +
                            "cannot be guaranteed to be successful.");

                        break;
                    }

                    case STOPPING: {
                        if (log.isDebugEnabled())
                            log.debug("Grid is being stopped by another thread. Aborting this stop sequence " +
                                "allowing other thread to finish[]");

                        return;
                    }

                    case STOPPED: {
                        if (log.isDebugEnabled())
                            log.debug("Grid is already stopped. Nothing to do[]");

                        return;
                    }
                }
            }
            finally {
                gw.writeUnlock();
            }
        }

        // Notify lifecycle beans in case when this thread is first to
        // stop the kernal. Notify outside of the lock.
        if (firstStop) {
            if (log.isDebugEnabled())
                log.debug("Notifying lifecycle beans.");

            notifyLifecycleBeans(GridLifecycleEventType.BEFORE_GRID_STOP);
        }

        // Callback processor while kernal is still functional
        // if called in the same thread, at least.
        for (GridProcessor proc : procReg.processors())
            try {
                proc.onKernalStop(cancel, wait);
            }
            catch (Throwable e) {
                errOnStop = true;

                U.error(log, "Failed to pre-stop processor: " + proc, e);
            }

        // Callback controller while kernal is still functional
        // if called in the same thread, at least.
        for (GridController ctrl : ctrlReg)
            try {
                ctrl.beforeKernalStop(cancel);

                if (log.isDebugEnabled())
                    log.debug("Controller stopped: " + ctrl.getClass().getSimpleName());
            }
            catch (Throwable e) {
                errOnStop = true;

                U.error(log, "Failed to stop controller: " + ctrl, e);
            }

        // Callback managers while kernal is still functional
        // if called in the same thread, at least.
        for (GridManager mgr : mgrReg.getManagers())
            try {
                mgr.onKernalStop();
            }
            catch (Throwable e) {
                errOnStop = true;

                U.error(log, "Failed to pre-stop manager: " + mgr, e);
            }

        gw.writeLock();

        try {
            assert gw.getState() == STARTED || gw.getState() == STARTING;

            // No more kernal calls from this point on.
            gw.setState(STOPPING);

            // Cancel update notification timer.
            if (updateNtfTimer != null)
                updateNtfTimer.cancel();

            // Cancel license timer.
            if (licTimer != null)
                licTimer.cancel();

            // Clear node local store.
            nodeLocal.clear();

            if (log.isDebugEnabled())
                log.debug("Grid " + (gridName == null ? "" : '\'' + gridName + "' ") + "is stopping[]");
        }
        finally {
            gw.writeUnlock();
        }

        // Unregister MBeans.
        if (!(
            unregisterMBean(pubExecSvcMBean) &
                unregisterMBean(sysExecSvcMBean) &
                unregisterMBean(p2PExecSvcMBean) &
                unregisterMBean(kernalMBean) &
                unregisterMBean(locNodeMBean)))
            errOnStop = false;

        // Stop most processors *before* managers.
        stopProcessor(procReg.job(), cancel, wait);
        stopProcessor(procReg.task(), cancel, wait);
        stopProcessor(procReg.cache(), cancel, wait);
        stopProcessor(procReg.closure(), cancel, wait);
        stopProcessor(procReg.rich(), cancel, wait);
        stopProcessor(procReg.ports(), cancel, wait);
        stopProcessor(procReg.metric(), cancel, wait);
        stopProcessor(procReg.schedule(), cancel, wait);
        stopProcessor(procReg.email(), cancel, wait);
        stopProcessor(procReg.session(), cancel, wait);

        // Check for null. Registry can be null if grid was not started successfully
        // and none of the managers were started.
        if (mgrReg != null) {
            List<GridManager> mgrs = mgrReg.getManagers();

            // Stop managers in reverse order.
            for (int i = mgrs.size() - 1; i >= 0; i--) {
                GridManager mgr = mgrs.get(i);

                try {
                    mgr.stop();

                    if (log.isDebugEnabled())
                        log.debug("Manager stopped: " + mgr);
                }
                catch (Throwable e) {
                    errOnStop = true;

                    U.error(log, "Failed to stop manager (ignoring): " + mgr, e);
                }
            }
        }

        // Stop resource and timeout processor *after* managers.
        stopProcessor(procReg.resource(), cancel, wait);
        stopProcessor(procReg.timeout(), cancel, wait);

        // Lifecycle notification.
        notifyLifecycleBeans(GridLifecycleEventType.AFTER_GRID_STOP);

        mgrReg = null;

        for (GridWorker w : GridWorkerGroup.instance(gridName).activeWorkers()) {
            String n1 = w.gridName() == null ? "" : w.gridName();
            String n2 = gridName == null ? "" : gridName;

            /*
             * We should never get a runnable from one grid instance
             * in the runnable group for another grid instance.
             */
            assert n1.equals(n2) : "Different grid names: [n1=" + n1 + ", n2=" + n2 + "]";

            if (log.isDebugEnabled())
                log.debug("Joining on runnable after grid has stopped: " + w);

            try {
                w.join();
            }
            catch (InterruptedException e) {
                errOnStop = true;

                U.error(log, "Got interrupted during grid stop (ignoring).", e);

                break;
            }
        }

        // Release memory.
        GridWorkerGroup.removeInstance(gridName);

        gw.writeLock();

        try {
            gw.setState(STOPPED);
        }
        finally {
            gw.writeUnlock();
        }

        // Ack stop.
        if (log.isQuiet()) {
            if (!errOnStop)
                U.quiet("GridGain stopped OK [uptime=" +
                    X.timeSpan2HMSM(System.currentTimeMillis() - startTime) + ']');
            else
                U.quiet("GridGain stopped wih ERRORS [uptime=" +
                    X.timeSpan2HMSM(System.currentTimeMillis() - startTime) + ']');
        }
        else if (log.isInfoEnabled())
            if (!errOnStop) {
                String ack = "GridGain ver. " + VER + '-' + BUILD + " stopped OK";

                String dash = U.dash(ack.length());

                log.info(NL + NL +
                    ">>> " + dash + NL +
                    ">>> " + ack + NL +
                    ">>> " + dash + NL +
                    ">>> Grid name: " + gridName + NL +
                    ">>> Grid uptime: " + X.timeSpan2HMSM(System.currentTimeMillis() - startTime) +
                    NL +
                    NL);
            }
            else {
                String ack = "GridGain ver. " + VER + '-' + BUILD + " stopped with ERRORS";

                String dash = U.dash(ack.length());

                log.info(NL + NL +
                    ">>> " + ack + NL +
                    ">>> " + dash + NL +
                    ">>> Grid name: " + gridName + NL +
                    ">>> Grid uptime: " + X.timeSpan2HMSM(System.currentTimeMillis() - startTime) +
                    NL +
                    ">>> See log above for detailed error message." + NL +
                    ">>> Note that some errors during stop can prevent grid from" + NL +
                    ">>> maintaining correct topology since this node may have" + NL +
                    ">>> not exited grid properly." + NL +
                    NL);
            }

        // Print out all the stop watches.
        W.printAll();

        // Send node start email notification, if enabled.
        if (isSmtpEnabled() && isAdminEmailsSet() && cfg.isLifeCycleEmailNotification() && isEnterprise()) {
            String errOk = errOnStop ? "with ERRORS" : "OK";

            String headline = "GridGain ver. " + VER + '-' + BUILD + " stopped " + errOk + ":";
            String subj = "GridGain node stopped " + errOk + ": " + nid8;

            GridEnterpriseLicense lic = ctx.license().license();

            String body =
                headline + NL +
                NL +
                "----" + NL +
                "GridGain ver. " + VER + '-' + BUILD + NL +
                "Grid name: " + gridName + NL +
                "Node ID: " + nid + NL +
                "Node uptime: " + X.timeSpan2HMSM(System.currentTimeMillis() - startTime) + NL +
                "License ID: "  +  lic.getId().toString().toUpperCase() + NL +
                "Licesned to: " + lic.getUserOrganization() + NL +
                "----" + NL +
                NL +
                "NOTE:" + NL +
                "This message is sent automatically to all configured admin emails." + NL +
                "To change this behavior use 'lifeCycleEmailNotify' grid configuration property.";

            if (errOnStop)
                body +=
                    NL + NL +
                    "NOTE:" + NL +
                    "See node's log for detailed error message." + NL +
                    "Some errors during stop can prevent grid from" + NL +
                    "maintaining correct topology since this node may " + NL +
                    "have not exited grid properly.";

            body +=
                NL + NL +
                "| www.gridgain.com" + NL +
                "| support@gridgain.com" + NL;

            // We can't use email processor at this point.
            // So we use "raw" method of sending.
            try {
                U.sendEmail(
                    // Static SMTP configuration data.
                    cfg.getSmtpHost(),
                    cfg.getSmtpPort(),
                    cfg.isSmtpSsl(),
                    cfg.isSmtpStartTls(),
                    cfg.getSmtpUsername(),
                    cfg.getSmtpPassword(),
                    cfg.getSmtpFromEmail(),

                    // Per-email data.
                    subj,
                    body,
                    false,
                    Arrays.asList(cfg.getAdminEmails())
                );
            }
            catch (GridException e) {
                U.error(log, "Failed to send lifecyce email notification.", e);
            }
        }
    }

    /**
     * Returns {@code true} if grid successfully stop.
     *
     * @return true if grid successfully stop.
     */
    @Deprecated
    public boolean isStopSuccess() {
        return !errOnStop;
    }

    /**
     * USED ONLY FOR TESTING.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Internal cache instance.
     */
    /*@java.test.only*/
    public <K, V> GridCacheAdapter<K, V> internalCache() {
        return internalCache(null);
    }

    /**
     * USED ONLY FOR TESTING.
     *
     * @param name Cache name.
     * @param <K>  Key type.
     * @param <V>  Value type.
     * @return Internal cache instance.
     */
    /*@java.test.only*/
    public <K, V> GridCacheAdapter<K, V> internalCache(@Nullable String name) {
        return procReg.cache().internalCache(name);
    }

    /**
     * It's intended for use by internal marshalling implementation only.
     *
     * @return Kernal context.
     */
    public GridKernalContext context() {
        return ctx;
    }

    /**
     * Prints all system properties in debug mode.
     */
    private void ackSystemProperties() {
        assert log != null;

        if (log.isDebugEnabled())
            for (Object key : U.asIterable(System.getProperties().keys()))
                log.debug("System property [" + key + '=' + System.getProperty((String)key) + ']');
    }

    /**
     * Prints all user attributes in info mode.
     */
    private void logNodeUserAttributes() {
        assert log != null;

        if (log.isInfoEnabled())
            for (Map.Entry<?, ?> attr : cfg.getUserAttributes().entrySet())
                log.info("Local node user attribute [" + attr.getKey() + '=' + attr.getValue() + ']');
    }

    /**
     * Prints all environment variables in debug mode.
     */
    private void ackEnvironmentVariables() {
        assert log != null;

        if (log.isDebugEnabled())
            for (Map.Entry<?, ?> envVar : System.getenv().entrySet())
                log.debug("Environment variable [" + envVar.getKey() + '=' + envVar.getValue() + ']');
    }

    /**
     * Acks daemon mode status.
     */
    private void ackDaemon() {
        U.log(log, "Daemon mode: " + (isDaemon() ? "on" : "off"));
    }

    /**
     *
     * @return {@code True} is this node is daemon.
     */
     private boolean isDaemon() {
        assert cfg != null;

        return cfg.isDaemon() || "true".equalsIgnoreCase(System.getProperty(GG_DAEMON));
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isJmxRemoteEnabled() {
        return System.getProperty("com.sun.management.jmxremote") != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isRestartEnabled() {
        return System.getProperty(GG_SUCCESS_FILE) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isSmtpEnabled() {
        assert cfg != null;

        return cfg.getSmtpHost() != null;
    }

    /**
     * Prints all configuration properties in info mode and SPIs in debug mode.
     */
    private void ackSpis() {
        assert log != null;

        if (log.isDebugEnabled()) {
            log.debug("+-------------+");
            log.debug("START SPI LIST:");
            log.debug("+-------------+");
            log.debug("Grid checkpoint SPI     : " + Arrays.toString(cfg.getCheckpointSpi()));
            log.debug("Grid collision SPI      : " + cfg.getCollisionSpi());
            log.debug("Grid communication SPI  : " + cfg.getCommunicationSpi());
            log.debug("Grid deployment SPI     : " + cfg.getDeploymentSpi());
            log.debug("Grid discovery SPI      : " + cfg.getDiscoverySpi());
            log.debug("Grid event storage SPI  : " + cfg.getEventStorageSpi());
            log.debug("Grid failover SPI       : " + Arrays.toString(cfg.getFailoverSpi()));
            log.debug("Grid load balancing SPI : " + Arrays.toString(cfg.getLoadBalancingSpi()));
            log.debug("Grid metrics SPI        : " + cfg.getMetricsSpi());
            log.debug("Grid swap space SPI     : " + Arrays.toString(cfg.getSwapSpaceSpi()));
            log.debug("Grid cloud SPI          : " + Arrays.toString(cfg.getCloudSpi()));
            log.debug("Grid topology SPI       : " + Arrays.toString(cfg.getTopologySpi()));
            log.debug("Grid tracing SPI        : " + Arrays.toString(cfg.getTracingSpi()));
        }
    }

    private void ackCacheConfiguration() {
        GridCacheConfiguration[] cacheCfgs = cfg.getCacheConfiguration();

        if (cacheCfgs == null || cacheCfgs.length == 0)
            U.warn(log, "Cache is not configured - data grid is off.");
        else {
            SB sb = new SB();

            for (GridCacheConfiguration c : cacheCfgs)
                sb.a("'").a(c.getName()).a("', ");

            String names = sb.toString();

            U.log(log, "Configured caches [" + names.substring(0, names.length() - 2) + ']');
        }
    }

    /**
     * Prints out SMTP configuration.
     */
    private void ackSmtpConfiguration() {
        // Ack SMTP only in Enterprise Edition.
        if (isEnterprise()) {
            String host = cfg.getSmtpHost();

            boolean ssl = cfg.isSmtpSsl();
            int port = cfg.getSmtpPort();

            if (host != null) {
                String from = cfg.getSmtpFromEmail();

                if (log.isQuiet())
                    U.quiet("SMTP enabled [host=" + host + ":" + port + ", ssl=" + (ssl ? "on" : "off") + ", from=" +
                        from + ']');
                else if (log.isInfoEnabled()) {
                    String[] adminEmails = cfg.getAdminEmails();

                    log.info("SMTP enabled [host=" + host + ", port=" + port + ", ssl=" + ssl + ", from=" +
                        from + ']');
                    log.info("Admin emails: " + (!isAdminEmailsSet() ? "N/A" : Arrays.toString(adminEmails)));
                }

                if (isEnterprise() && !isAdminEmailsSet())
                    U.warn(log, "Admin emails are not set - automatic email notifications are off.");
            }
            else
                U.warn(log, "SMTP is not configured - email notifications are off.");
        }
    }

    /**
     * Tests whether or not admin emails are set.
     *
     * @return {@code True} if admin emails are set and not empty.
     */
    private boolean isAdminEmailsSet() {
        assert cfg != null;

        String[] a = cfg.getAdminEmails();

        return a != null && a.length > 0;
    }

    /**
     * Prints out VM arguments and GRIDGAIN_HOME in info mode.
     *
     * @param rtBean Java runtime bean.
     */
    private void ackVmArguments(RuntimeMXBean rtBean) {
        assert log != null;

        // Ack GRIDGAIN_HOME and VM arguments.
        if (log.isQuiet())
            U.quiet("GRIDGAIN_HOME=" + cfg.getGridGainHome());
        else if (log.isInfoEnabled()) {
            log.info("GRIDGAIN_HOME=" + cfg.getGridGainHome());
            log.info("VM arguments: " + rtBean.getInputArguments());
        }
    }

    /**
     * Prints out class paths in debug mode.
     *
     * @param rtBean Java runtime bean.
     */
    private void ackClassPaths(RuntimeMXBean rtBean) {
        assert log != null;

        // Ack all class paths.
        if (log.isDebugEnabled()) {
            log.debug("Boot class path: " + rtBean.getBootClassPath());
            log.debug("Class path: " + rtBean.getClassPath());
            log.debug("Library path: " + rtBean.getLibraryPath());
        }
    }

    /**
     * Returns processor registry. For tests only.
     *
     * @return processor registry.
     */
    @Deprecated
    public GridProcessorRegistry getProcessorRegistry() {
        return procReg;
    }

    /**
     * This method is for internal testing only.
     *
     * @return Manager registry.
     */
    @Deprecated
    public GridManagerRegistry getManagerRegistry() {
        return mgrReg;
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public GridConfiguration getConfiguration() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridConfiguration configuration() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridLogger log() {
        guard();

        try {
            return log;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodes(Collection<? extends GridNode> nodes) {
        A.notNull(nodes, "nodes");

        guard();

        try {
            return new GridProjectionImpl(this, ctx, F.viewReadOnly(nodes, ctx.rich().richNode()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodes(GridRichNode[] nodes) {
        return projectionForNodes(Arrays.asList(nodes));
    }

    /** {@inheritDoc} */
    @Override public GridEvent waitForEvent(long timeout, @Nullable Runnable c,
        @Nullable GridPredicate<? super GridEvent> p, int[] types) throws GridException {
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            return ctx.event().waitForEvent(timeout, c, p, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean removeCheckpoint(String key) {
        A.notNull(key, "key");

        guard();

        try {
            return ctx.checkpoint().removeCheckpoint(key);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridEvent> waitForEventAsync(@Nullable GridPredicate<? super GridEvent> p,
        int[] types) {
        A.notNull(p, "p");

        guard();

        try {
            return ctx.event().waitForEvent(p, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public String version() {
        return VER;
    }

    /** {@inheritDoc} */
    @Override public String build() {
        return BUILD;
    }

    /** {@inheritDoc} */
    @Override public String copyright() {
        return COPYRIGHT;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public GridPredicate<GridRichNode> predicate() {
        guard();

        try {
            return F.alwaysTrue();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(String nodeId) {
        A.notNull(nodeId, "nodeId");

        return pingNode(UUID.fromString(nodeId));
    }

    /** {@inheritDoc} */
    @Override public long topologyHash(Iterable<? extends GridNode> nodes) {
        A.notNull(nodes, "nodes");

        guard();

        try {
            return ctx.discovery().topologyHash(nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void undeployTaskFromGrid(String taskName) throws JMException {
        A.notNull(taskName, "taskName");

        try {
            undeployTask(taskName);
        }
        catch (GridException e) {
            throw U.jmException(e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public String executeTask(String taskName, String arg) throws JMException {
        try {
            return this.<String, String>execute(taskName, arg, null).get();
        }
        catch (GridException e) {
            throw U.jmException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> sendAdminEmailAsync(String subj, String body, boolean html) {
        A.notNull(subj, "subj");
        A.notNull(body, "body");

        if (!isEnterprise())
            throw new GridEnterpriseFeatureException();

        if (isSmtpEnabled() && isAdminEmailsSet()) {
            guard();

            try {
                return ctx.email().schedule(subj, body, html, Arrays.asList(cfg.getAdminEmails()));
            }
            finally {
                unguard();
            }
        }
        else
            return new GridFinishedFuture<Boolean>(ctx, false);
    }

    /** {@inheritDoc} */
    @Override public void sendAdminEmail(String subj, String body, boolean html) throws GridException {
        A.notNull(subj, "subj");
        A.notNull(body, "body");

        if (!isEnterprise())
            throw new GridEnterpriseFeatureException();

        if (isSmtpEnabled() && isAdminEmailsSet()) {
            guard();

            try {
                ctx.email().sendNow(subj, body, html, Arrays.asList(cfg.getAdminEmails()));
            }
            finally {
                unguard();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridEnterpriseLicense license() {
        guard();

        try {
            return ctx.license().license();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void addLocalEventListener(GridLocalEventListener lsnr, int[] types) {
        A.notNull(lsnr, "lsnr");
        A.notNull(types, "types");

        guard();

        try {
            if (types.length == 0)
                throw new GridRuntimeException("Array of event types cannot be empty.");

            ctx.event().addLocalEventListener(lsnr, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void addLocalEventListener(GridLocalEventListener lsnr, int type, @Nullable int... types) {
        A.notNull(lsnr, "lsnr");

        guard();

        try {
            addLocalEventListener(lsnr, new int[] { type });

            if (types != null && types.length > 0)
                addLocalEventListener(lsnr, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean pingNodeByAddress(String host) {
        guard();

        try {
            for (GridRichNode n : nodes(EMPTY_PN))
                if (n.externalAddresses().contains(host) || n.internalAddresses().contains(host))
                    return n.ping();

            return false;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean removeLocalEventListener(GridLocalEventListener lsnr, int[] types) {
        A.notNull(lsnr, "lsnr");

        guard();

        try {
            return ctx.event().removeLocalEventListener(lsnr, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public void addMessageListener(GridMessageListener lsnr, GridPredicate<Object>[] p) {
        guard();

        try {
            ctx.io().addUserMessageListener(lsnr, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public boolean removeMessageListener(GridMessageListener lsnr) {
        guard();

        try {
            return ctx.io().removeUserMessageListener(lsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridRichNode localNode() {
        guard();

        try {
            GridRichNode node = ctx.rich().rich(ctx.discovery().localNode());

            assert node != null;

            return node;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runLocal(Runnable r) throws GridException {
        A.notNull(r, "r");

        guard();

        try {
            return ctx.closure().runLocal(r, true);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public ExecutorService newGridExecutorService() {
        guard();

        try {
            return new GridExecutorService(ctx.grid(), log());
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callLocal(Callable<R> c) throws GridException {
        A.notNull(c, "c");

        guard();

        try {
            return ctx.closure().callLocal(c, true);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridScheduleFuture<R> scheduleLocal(Callable<R> c, String pattern) throws GridException {
        guard();

        try {
            return ctx.schedule().schedule(c, pattern);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridScheduleFuture<?> scheduleLocal(Runnable c, String pattern) throws GridException {
        guard();

        try {
            return ctx.schedule().schedule(c, pattern);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridNodeLocal<K, V> nodeLocal() {
        guard();

        try {
            return nodeLocal;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        A.notNull(nodeId, "nodeId");

        guard();

        try {
            return ctx.discovery().pingNode(nodeId);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void deployTask(Class<? extends GridTask> taskCls) throws GridException {
        A.notNull(taskCls, "taskCls");

        deployTask(taskCls, U.detectClassLoader(taskCls));
    }

    /** {@inheritDoc} */
    @Override public void deployTask(Class<? extends GridTask> taskCls, ClassLoader clsLdr) throws GridException {
        A.notNull(taskCls, "taskCls", clsLdr, "clsLdr");

        guard();

        try {
            ctx.deploy().deploy(taskCls, clsLdr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection parent() {
        return null; // Kernal is the root projection - therefore it doesn't have parent.
    }

    /** {@inheritDoc} */
    @Override public Map<String, Class<? extends GridTask<?, ?>>>
    localTasks(@Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>[] p) {
        guard();

        try {
            return ctx.deploy().findAllTasks(p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void undeployTask(String taskName) throws GridException {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.deploy().undeployTask(taskName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridEvent> localEvents(GridPredicate<? super GridEvent>[] p) {
        guard();

        try {
            return ctx.event().localEvents(p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void recordLocalEvent(GridEvent evt) {
        A.notNull(evt, "evt");

        guard();

        try {
            ctx.event().record(evt);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCache<K, V> cache(@Nullable String name) {
        guard();

        try {
            return ctx.cache().cache(name);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCache<K, V> cache() {
        guard();

        try {
            return ctx.cache().cache();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCache<?, ?>> caches(GridPredicate<? super GridCache<?, ?>>[] p) {
        guard();

        try {
            return F.retain(ctx.cache().caches(), true, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(@Nullable String space, Object key, @Nullable Object val) throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            if (space == null)
                ctx.swap().writeGlobal(key, val);
            else
                ctx.swap().write(space, key, val);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public <T> T readFromSwap(@Nullable String space, Object key) throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            return space == null ? ctx.swap().<T>readGlobal(key) : ctx.swap().<T>read(space, key);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean removeFromSwap(@Nullable String space, Object key, final GridInClosure<Object> c)
        throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            GridInClosureX<GridSwapByteArray> c1 = null;

            if (c != null)
                c1 = new CIX1<GridSwapByteArray>() {
                    @Override public void applyx(GridSwapByteArray removed) throws GridException {
                        Object val = null;

                        if (removed != null)
                            val = U.unmarshal(cfg.getMarshaller(), new ByteArrayInputStream(
                                removed.getArray(), removed.getOffset(), removed.getLength()),
                                getClass().getClassLoader());

                        c.apply(val);
                    }
                };

            return space == null ? ctx.swap().removeGlobal(key, c1) : ctx.swap().remove(space, key, c1);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void clearSwapSpace(@Nullable String space) throws GridException {
        guard();

        try {
            if (space == null)
                ctx.swap().clearGlobal();
            else
                ctx.swap().clear(space);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichCloud> clouds(GridPredicate<? super GridRichCloud>[] p) {
        guard();

        try {
            return F.lose(F.transform(ctx.cloud().clouds(), ctx.rich().richCloud()), false, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable public GridRichCloud cloud(String cloudId) {
        A.notNull(cloudId, "cloudId");

        guard();

        try {
            return ctx.rich().rich(ctx.cloud().cloud(cloudId));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> getRemoteNodes(GridPredicate<? super GridRichNode>[] p) {
        return remoteNodes(p);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode getLocalNode() {
        return localNode();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> getNodes(GridPredicate<? super GridRichNode>[] p) {
        return nodes(p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public GridNode getNode(UUID nodeId) {
        return node(nodeId, null);
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(UUID[] nodeIds) {
        return projectionForNodeIds(Arrays.asList(nodeIds));
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(Collection<UUID> nodeIds) {
        A.notNull(nodeIds, "nodeIds");

        guard();

        try {
            return new GridProjectionImpl(this, ctx, F.viewReadOnly(nodeIds, new C1<UUID, GridRichNode>() {
                @SuppressWarnings("unchecked")
                @Override public GridRichNode apply(UUID nodeId) {
                    return node(nodeId, null);
                }
            }));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public void addDiscoveryListener(final GridDiscoveryListener lsnr) {
        A.notNull(lsnr, "lsnr");

        guard();

        try {
            GridLocalEventListener evtLsnr = new GridLocalEventListener() {
                @SuppressWarnings("unchecked")
                @Override public void onEvent(GridEvent evt) {
                    assert evt != null;
                    assert evt instanceof GridDiscoveryEvent;

                    GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                    GridDiscoveryEventType type;

                    if (discoEvt.type() == EVT_NODE_FAILED)
                        type = GridDiscoveryEventType.FAILED;
                    else if (discoEvt.type() == EVT_NODE_JOINED)
                        type = GridDiscoveryEventType.JOINED;
                    else if (discoEvt.type() == EVT_NODE_LEFT)
                        type = GridDiscoveryEventType.LEFT;
                    else if (discoEvt.type() == EVT_NODE_METRICS_UPDATED)
                        type = GridDiscoveryEventType.METRICS_UPDATED;
                    else
                        return;

                    lsnr.onDiscovery(type, node(discoEvt.eventNodeId(), null));
                }
            };

            ctx.event().addLocalEventListener(evtLsnr, EVTS_DISCOVERY_ALL);

            synchronized (discoLsnrs) {
                discoLsnrs.put(lsnr, evtLsnr);
            }
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public boolean removeDiscoveryListener(GridDiscoveryListener lsnr) {
        A.notNull(lsnr, "lsnr");

        guard();

        try {
            GridLocalEventListener evtLsnr;

            synchronized (discoLsnrs) {
                evtLsnr = discoLsnrs.remove(lsnr);
            }

            return evtLsnr != null && ctx.event().removeLocalEventListener(evtLsnr);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public void sendMessage(GridNode node, Object msg) throws GridException {
        A.notNull(node, "node", msg, "msg");

        send(msg, asArray(F.<GridRichNode>nodeForNodeId(node.id())));
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(Collection<? extends GridNode> nodes, Object msg) throws GridException {
        A.notNull(nodes, "nodes", msg, "msg");

        guard();

        try {
            send(msg, asArray(F.<GridRichNode>nodeForNodeIds(F.nodeIds(nodes))));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return F.view(F.viewReadOnly(ctx.discovery().allNodes(), ctx.rich().richNode()), p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridRichNode rich(GridNode node) {
        guard();

        try {
            GridRichNode n = ctx.rich().rich(node);

            assert n != null;

            return n;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridRichCloud rich(GridCloud cloud) {
        guard();

        try {
            GridRichCloud r = ctx.rich().rich(cloud);

            assert r != null;

            return r;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Map<String, Class<? extends GridTask<?, ?>>> getLocalTasks(@Nullable GridPredicate<? super Class<?
        extends GridTask<?, ?>>>[] p) {
        guard();

        try {
            return ctx.deploy().findAllTasks(p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean dynamic() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridKernal.class, this);
    }
}
