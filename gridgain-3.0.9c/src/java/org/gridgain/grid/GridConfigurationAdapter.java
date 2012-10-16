// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.spi.cloud.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.eventstorage.*;
import org.gridgain.grid.spi.failover.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.spi.topology.*;
import org.gridgain.grid.spi.tracing.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import javax.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Adapter for {@link GridConfiguration} interface. Use it to add custom configuration
 * for grid. Note that you should only set values that differ from defaults, as grid
 * will automatically pick default values for all values that are not set.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridConfigurationAdapter implements GridConfiguration {
    /** Optional grid name. */
    private String gridName;

    /** User attributes. */
    private Map<String, ?> userAttrs;

    /** Logger. */
    private GridLogger log;

    /** Executor service. */
    private ExecutorService execSvc;

    /** Executor service. */
    private ExecutorService systemSvc;

    /** Peer class loading executor service shutdown flag. */
    private boolean p2pSvcShutdown = true;

    /** Executor service shutdown flag. */
    private boolean execSvcShutdown = true;

    /** System executor service shutdown flag. */
    private boolean sysSvcShutdown = true;

    /** Lifecycle email notification. */
    private boolean lifeCycleEmailNtf = true;

    /** Executor service. */
    private ExecutorService p2pSvc;

    /** Gridgain installation folder. */
    private String ggHome;

    /** MBean server. */
    private MBeanServer mbeanSrv;

    /** Local node ID. */
    private UUID nodeId;

    /** Marshaller. */
    private GridMarshaller marsh;

    /** Daemon flag. */
    private boolean daemon;

    /** Jetty XML configuration path. */
    private String jettyPath;

    /** {@code REST} flag. */
    private boolean restEnabled = true;

    /** Whether or not peer class loading is enabled. */
    private boolean p2pEnabled = DFLT_P2P_ENABLED;

    /** List of package prefixes from the system class path that should be P2P loaded. */
    private String[] p2pLocalClsPathExcl;

    /** Events of these types should be recorded. */
    private int[] inclEvtTypes;

    /** Events of these types should not be recorded. */
    private int[] exclEvtTypes;

    /** Maximum network requests timeout. */
    private long netTimeout = DFLT_NETWORK_TIMEOUT;

    /** Metrics history time. */
    private int metricsHistSize = DFLT_METRICS_HISTORY_SIZE;

    /** Metrics expire time. */
    private long metricsExpTime = DFLT_METRICS_EXPIRE_TIME;

    /** Collection of life-cycle beans. */
    private GridLifecycleBean[] lifecycleBeans;

    /** Array of cloud strategies. */
    private GridCloudStrategy[] cloudStrategies;

    /** Array of cloud policies. */
    private GridCloudPolicy[] cloudPolicies;

    /** Discovery SPI. */
    private GridDiscoverySpi discoSpi;

    /** Communication SPI. */
    private GridCommunicationSpi commSpi;

    /** Event storage SPI. */
    private GridEventStorageSpi evtSpi;

    /** Collision SPI. */
    private GridCollisionSpi colSpi;

    /** Metrics SPI. */
    private GridLocalMetricsSpi metricsSpi;

    /** Deployment SPI. */
    private GridDeploymentSpi deploySpi;

    /** Checkpoint SPI. */
    private GridCheckpointSpi[] cpSpi;

    /** Tracing SPI. */
    private GridTracingSpi[] traceSpi;

    /** Failover SPI. */
    private GridFailoverSpi[] failSpi;

    /** Topology SPI. */
    private GridTopologySpi[] topSpi;

    /** Load balancing SPI. */
    private GridLoadBalancingSpi[] loadBalancingSpi;

    /** Checkpoint SPI. */
    private GridSwapSpaceSpi[] swapSpaceSpi;

    /** Cloud SPI. */
    private GridCloudSpi[] cloudSpi;

    /** Cache configurations. */
    private GridCacheConfiguration[] cacheCfg;

    /** Discovery startup delay. */
    private long discoStartupDelay;

    /** Tasks classes sharing mode. */
    private GridDeploymentMode deployMode = DFLT_DEPLOYMENT_MODE;

    /** Cache size of missed resources. */
    private int p2pMissedCacheSize = DFLT_P2P_MISSED_RESOURCES_CACHE_SIZE;

    /** */
    private boolean disableCloudCrd = true;

    /** */
    private String smtpHost;

    /** */
    private int smtpPort = DFLT_SMTP_PORT;

    /** */
    private String smtpUsername;

    /** */
    private String smtpPwd;

    /** */
    private String[] adminEmails;

    /** */
    private String smtpFromEmail = DFLT_SMTP_FROM_EMAIL;

    /** */
    private boolean smtpSsl = DFLT_SMTP_SSL;

    /** */
    private boolean smtpStartTls = DFLT_SMTP_STARTTLS;

    /** Local host. */
    private String locHost;

    /** REST secret key. */
    private String restSecretKey;

    /** Property names to include into node attributes. */
    private String[] includeProps;

    /** License custom URL. */
    private String licUrl;

    /**
     * Creates valid grid configuration with all default values.
     */
    public GridConfigurationAdapter() {
        // No-op.
    }

    /**
     * Creates grid configuration by coping all configuration properties from
     * given configuration.
     *
     * @param cfg Grid configuration to copy from.
     */
    public GridConfigurationAdapter(GridConfiguration cfg) {
        assert cfg != null;

        // SPIs.
        discoSpi = cfg.getDiscoverySpi();
        commSpi = cfg.getCommunicationSpi();
        deploySpi = cfg.getDeploymentSpi();
        evtSpi = cfg.getEventStorageSpi();
        cpSpi = cfg.getCheckpointSpi();
        colSpi = cfg.getCollisionSpi();
        failSpi = cfg.getFailoverSpi();
        topSpi = cfg.getTopologySpi();
        metricsSpi = cfg.getMetricsSpi();
        loadBalancingSpi = cfg.getLoadBalancingSpi();
        traceSpi = cfg.getTracingSpi();
        swapSpaceSpi = cfg.getSwapSpaceSpi();
        cloudSpi = cfg.getCloudSpi();

        /*
         * Order alphabetically for maintenance purposes.
         */
        adminEmails = cfg.getAdminEmails();
        daemon = cfg.isDaemon();
        cacheCfg = cfg.getCacheConfiguration();
        cloudStrategies = cfg.getCloudStrategies();
        cloudPolicies = cfg.getCloudPolicies();
        deployMode = cfg.getDeploymentMode();
        disableCloudCrd = cfg.isDisableCloudCoordinator();
        discoStartupDelay = cfg.getDiscoveryStartupDelay();
        exclEvtTypes = cfg.getExcludeEventTypes();
        execSvc = cfg.getExecutorService();
        ggHome = cfg.getGridGainHome();
        gridName = cfg.getGridName();
        inclEvtTypes = cfg.getIncludeEventTypes();
        includeProps = cfg.getIncludeProperties();
        jettyPath = cfg.getRestJettyPath();
        licUrl = cfg.getLicenseUrl();
        lifecycleBeans = cfg.getLifecycleBeans();
        lifeCycleEmailNtf = cfg.isLifeCycleEmailNotification();
        locHost = cfg.getLocalHost();
        log = cfg.getGridLogger();
        marsh = cfg.getMarshaller();
        mbeanSrv = cfg.getMBeanServer();
        metricsHistSize = cfg.getMetricsHistorySize();
        metricsExpTime = cfg.getMetricsExpireTime();
        netTimeout = cfg.getNetworkTimeout();
        nodeId = cfg.getNodeId();
        p2pEnabled = cfg.isPeerClassLoadingEnabled();
        p2pMissedCacheSize = cfg.getPeerClassLoadingMissedResourcesCacheSize();
        p2pSvc = cfg.getPeerClassLoadingExecutorService();
        restEnabled = cfg.isRestEnabled();
        restSecretKey = cfg.getRestSecretKey();
        smtpHost = cfg.getSmtpHost();
        smtpPort = cfg.getSmtpPort();
        smtpUsername = cfg.getSmtpUsername();
        smtpPwd = cfg.getSmtpPassword();
        smtpFromEmail = cfg.getSmtpFromEmail();
        smtpSsl = cfg.isSmtpSsl();
        smtpStartTls = cfg.isSmtpStartTls();
        systemSvc = cfg.getSystemExecutorService();
        userAttrs = cfg.getUserAttributes();
    }

    /** {@inheritDoc} */
    @Override public boolean isLifeCycleEmailNotification() {
        return lifeCycleEmailNtf;
    }

    /** {@inheritDoc} */
    @Override public String getLicenseUrl() {
        return licUrl;
    }

    /** {@inheritDoc} */
    @Override public boolean isSmtpSsl() {
        return smtpSsl;
    }

    /** {@inheritDoc} */
    @Override public boolean isSmtpStartTls() {
        return smtpStartTls;
    }

    /** {@inheritDoc} */
    @Override public String getSmtpHost() {
        return smtpHost;
    }

    /** {@inheritDoc} */
    @Override public int getSmtpPort() {
        return smtpPort;
    }

    /** {@inheritDoc} */
    @Override public String getSmtpUsername() {
        return smtpUsername;
    }

    /** {@inheritDoc} */
    @Override public String getSmtpPassword() {
        return smtpPwd;
    }

    /** {@inheritDoc} */
    @Override public String[] getAdminEmails() {
        return adminEmails;
    }

    /** {@inheritDoc} */
    @Override public String getSmtpFromEmail() {
        return smtpFromEmail;
    }

    /**
     * Sets license URL different from the default location of the license file.
     *
     * @param licUrl License URl to set.
     */
    public void setLicenseUrl(String licUrl) {
        this.licUrl = licUrl;
    }

    /**
     * Sets whether or not to enable lifecycle email notifications.
     * <p>
     * Note that life cycle notification is only available in Enterprise Edition. In
     * Community Edition this property is ignored.
     *
     * @param lifeCycleEmailNtf {@code True} to enable lifecycle email notifications.
     * @see GridSystemProperties#GG_LIFECYCLE_EMAIL_NOTIFY
     */
    @GridEnterpriseFeature
    public void setLifeCycleEmailNotification(boolean lifeCycleEmailNtf) {
        this.lifeCycleEmailNtf = lifeCycleEmailNtf;
    }

    /**
     * Sets whether or not SMTP uses SSL.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpSsl Whether or not SMTP uses SSL.
     * @see GridSystemProperties#GG_SMTP_SSL
     */
    public void setSmtpSsl(boolean smtpSsl) {
        this.smtpSsl = smtpSsl;
    }

    /**
     * Sets whether or not SMTP uses STARTTLS.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpStartTls Whether or not SMTP uses STARTTLS.
     * @see GridSystemProperties#GG_SMTP_STARTTLS
     */
    public void setSmtpStartTls(boolean smtpStartTls) {
        this.smtpStartTls = smtpStartTls;
    }

    /**
     * Sets SMTP host.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@code #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpHost SMTP host to set or {@code null} to disable sending emails.
     * @see GridSystemProperties#GG_SMTP_HOST
     */
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    /**
     * Sets SMTP port. Default value is {@link #DFLT_SMTP_PORT}.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpPort SMTP port to set.
     * @see #DFLT_SMTP_PORT
     * @see GridSystemProperties#GG_SMTP_PORT
     */
    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    /**
     * Sets SMTP username or {@code null} if not used.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpUsername SMTP username or {@code null}.
     * @see GridSystemProperties#GG_SMTP_USERNAME
     */
    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    /**
     * Sets SMTP password or {@code null} if not used.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #setSmtpHost(String)} is the only mandatory SMTP
     * configuration property.
     *
     * @param smtpPwd SMTP password or {@code null}.
     * @see GridSystemProperties#GG_SMTP_PWD
     */
    public void setSmtpPassword(String smtpPwd) {
        this.smtpPwd = smtpPwd;
    }

    /**
     * Sets optional set of admin emails where email notifications will be set.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @param adminEmails Optional set of admin emails where email notifications will be set.
     *      If {@code null} - emails will be sent only to the email in the license
     *      if one provided.
     * @see GridSystemProperties#GG_ADMIN_EMAILS
     */
    public void setAdminEmails(String[] adminEmails) {
        this.adminEmails = adminEmails;
    }

    /**
     * Sets optional FROM email address for email notifications. By default
     * {@link #DFLT_SMTP_FROM_EMAIL} will be used.
     *
     * @param smtpFromEmail Optional FROM email address for email notifications. If {@code null}
     *      - {@link #DFLT_SMTP_FROM_EMAIL} will be used by default.
     * @see #DFLT_SMTP_FROM_EMAIL
     * @see GridSystemProperties#GG_SMTP_FROM
     */
    public void setSmtpFromEmail(String smtpFromEmail) {
        this.smtpFromEmail = smtpFromEmail;
    }

    /**
     * Gets optional grid name. Returns {@code null} if non-default grid name was not
     * provided.
     *
     * @return Optional grid name. Can be {@code null}, which is default grid name, if
     *      non-default grid name was not provided.
     */
    @Override public String getGridName() {
        return gridName;
    }

    /** {@inheritDoc} */
    @Override public boolean isDaemon() {
        return daemon;
    }

    /**
     * Sets daemon flag.
     * <p>
     * Daemon nodes are the usual grid nodes that participate in topology but not
     * visible on the main APIs, i.e. they are not part of any projections. The only
     * way to see daemon nodes is to use {@link Grid#daemonNodes(GridPredicate[])} method.
     * <p>
     * Daemon nodes are used primarily for management and monitoring functionality that
     * is build on GridGain and needs to participate in the topology but also needs to be
     * excluded from "normal" topology so that it won't participate in task execution
     * or data grid storage.
     *
     * @param daemon Daemon flag.
     */
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /**
     * Sets grid name. Note that {@code null} is a default grid name.
     *
     * @param gridName Grid name to set. Can be {@code null}, which is default
     *      grid name.
     */
    public void setGridName(String gridName) {
        this.gridName = gridName;
    }

    /** {@inheritDoc} */
    @Override public Map<String, ?> getUserAttributes() {
        return userAttrs;
    }

    /**
     * Sets user attributes for this node.
     *
     * @param userAttrs User attributes for this node.
     * @see GridConfiguration#getUserAttributes()
     */
    public void setUserAttributes(Map<String, ?> userAttrs) {
        this.userAttrs = userAttrs;
    }

    /** {@inheritDoc} */
    @Override public GridLogger getGridLogger() {
        return log;
    }

    /**
     * Sets logger to use within grid.
     *
     * @param log Logger to use within grid.
     * @see GridConfiguration#getGridLogger()
     */
    public void setGridLogger(GridLogger log) {
        this.log = log;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getExecutorService() {
        return execSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getSystemExecutorService() {
        return systemSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getPeerClassLoadingExecutorService() {
        return p2pSvc;
    }

    /** {@inheritDoc} */
    @Override public boolean getExecutorServiceShutdown() {
        return execSvcShutdown;
    }

    /** {@inheritDoc} */
    @Override public boolean getSystemExecutorServiceShutdown() {
        return sysSvcShutdown;
    }

    /** {@inheritDoc} */
    @Override public boolean getPeerClassLoadingExecutorServiceShutdown() {
        return p2pSvcShutdown;
    }

    /**
     * Sets thread pool to use within grid.
     *
     * @param execSvc Thread pool to use within grid.
     * @see GridConfiguration#getExecutorService()
     */
    public void setExecutorService(ExecutorService execSvc) {
        this.execSvc = execSvc;
    }

    /**
     * Sets executor service shutdown flag.
     *
     * @param execSvcShutdown Executor service shutdown flag.
     * @see GridConfiguration#getExecutorServiceShutdown()
     */
    public void setExecutorServiceShutdown(boolean execSvcShutdown) {
        this.execSvcShutdown = execSvcShutdown;
    }

    /**
     * Sets system thread pool to use within grid.
     *
     * @param systemSvc Thread pool to use within grid.
     * @see GridConfiguration#getSystemExecutorService()
     */
    public void setSystemExecutorService(ExecutorService systemSvc) {
        this.systemSvc = systemSvc;
    }

    /**
     * Sets system executor service shutdown flag.
     *
     * @param sysSvcShutdown System executor service shutdown flag.
     * @see GridConfiguration#getSystemExecutorServiceShutdown()
     */
    public void setSystemExecutorServiceShutdown(boolean sysSvcShutdown) {
        this.sysSvcShutdown = sysSvcShutdown;
    }

    /**
     * Sets thread pool to use for peer class loading.
     *
     * @param p2pSvc Thread pool to use within grid.
     * @see GridConfiguration#getPeerClassLoadingExecutorService()
     */
    public void setPeerClassLoadingExecutorService(ExecutorService p2pSvc) {
        this.p2pSvc = p2pSvc;
    }

    /**
     * Sets peer class loading executor service shutdown flag.
     *
     * @param p2pSvcShutdown Peer class loading executor service shutdown flag.
     * @see GridConfiguration#getPeerClassLoadingExecutorServiceShutdown()
     */
    public void setPeerClassLoadingExecutorServiceShutdown(boolean p2pSvcShutdown) {
        this.p2pSvcShutdown = p2pSvcShutdown;
    }

    /** {@inheritDoc} */
    @Override public String getGridGainHome() {
        return ggHome;
    }

    /**
     * Sets GridGain installation folder.
     *
     * @param ggHome {@code GridGain} installation folder.
     * @see GridConfiguration#getGridGainHome()
     * @see GridSystemProperties#GG_HOME
     */
    public void setGridGainHome(String ggHome) {
        this.ggHome = ggHome;
    }

    /** {@inheritDoc} */
    @Override public MBeanServer getMBeanServer() {
        return mbeanSrv;
    }

    /**
     * Sets initialized and started MBean server.
     *
     * @param mbeanSrv Initialized and started MBean server.
     */
    public void setMBeanServer(MBeanServer mbeanSrv) {
        this.mbeanSrv = mbeanSrv;
    }

    /** {@inheritDoc} */
    @Override public UUID getNodeId() {
        return nodeId;
    }

    /**
     * Sets unique identifier for local node.
     *
     * @param nodeId Unique identifier for local node.
     * @see GridConfiguration#getNodeId()
     */
    public void setNodeId(@Nullable UUID nodeId) {
        this.nodeId = nodeId;
    }

    /** {@inheritDoc} */
    @Override public GridMarshaller getMarshaller() {
        return marsh;
    }

    /**
     * Sets marshaller to use within grid.
     *
     * @param marsh Marshaller to use within grid.
     * @see GridConfiguration#getMarshaller()
     */
    public void setMarshaller(GridMarshaller marsh) {
        this.marsh = marsh;
    }

    /** {@inheritDoc} */
    @Override public boolean isPeerClassLoadingEnabled() {
        return p2pEnabled;
    }

    /**
     * Enables/disables peer class loading.
     *
     * @param p2pEnabled {@code true} if peer class loading is
     *      enabled, {@code false} otherwise.
     */
    public void setP2PClassLoadingEnabled(boolean p2pEnabled) {
        this.p2pEnabled = p2pEnabled;
    }

    /** {@inheritDoc} */
    @Override public String[] getPeerClassLoadingClassPathExclude() {
        return p2pLocalClsPathExcl;
    }

    /**
     * Sets list of packages in a system class path that should be to P2P
     * loaded even if they exist locally.
     *
     * @param p2pLocalClsPathExcl List of P2P loaded packages. Package
     *      name supports '*' at the end like in package import clause.
     */
    public void setP2PLocalClassPathExclude(String... p2pLocalClsPathExcl) {
        this.p2pLocalClsPathExcl = p2pLocalClsPathExcl;
    }

    /** {@inheritDoc} */
    @Override public int getMetricsHistorySize() {
        return metricsHistSize;
    }

    /**
     * Sets number of metrics kept in history to compute totals and averages.
     * If not explicitly set, then default value is {@code 10,000}.
     *
     * @param metricsHistSize Number of metrics kept in history to use for
     *      metric totals and averages calculations.
     * @see #DFLT_METRICS_HISTORY_SIZE
     */
    public void setMetricsHistorySize(int metricsHistSize) {
        this.metricsHistSize = metricsHistSize;
    }

    /** {@inheritDoc} */
    @Override public long getMetricsExpireTime() {
        return metricsExpTime;
    }

    /**
     * Sets time in milliseconds after which a certain metric value is considered expired.
     * If not set explicitly, then default value is {@code 600,000} milliseconds (10 minutes).
     *
     * @param metricsExpTime The metricsExpTime to set.
     * @see #DFLT_METRICS_EXPIRE_TIME
     */
    public void setMetricsExpireTime(long metricsExpTime) {
        this.metricsExpTime = metricsExpTime;
    }

    /** {@inheritDoc} */
    @Override public long getNetworkTimeout() {
        return netTimeout;
    }

    /**
     * Maximum timeout in milliseconds for network requests.
     * <p>
     * If not provided (value is {@code 0}), then default value
     * {@link #DFLT_NETWORK_TIMEOUT} is used.
     *
     * @param netTimeout Maximum timeout for network requests.
     * @see #DFLT_NETWORK_TIMEOUT
     */
    public void setNetworkTimeout(long netTimeout) {
        this.netTimeout = netTimeout;
    }

    /** {@inheritDoc} */
    @Override public GridLifecycleBean[] getLifecycleBeans() {
        return lifecycleBeans;
    }

    /**
     * Sets a collection of lifecycle beans. These beans will be automatically
     * notified of grid lifecycle events. Use lifecycle beans whenever you
     * want to perform certain logic before and after grid startup and stopping
     * routines.
     *
     * @param lifecycleBeans Collection of lifecycle beans.
     * @see GridLifecycleEventType
     */
    public void setLifecycleBeans(GridLifecycleBean... lifecycleBeans) {
        this.lifecycleBeans = lifecycleBeans;
    }

    /** {@inheritDoc} */
    @Override public GridEventStorageSpi getEventStorageSpi() {
        return evtSpi;
    }

    /**
     * Sets fully configured instance of {@link GridEventStorageSpi}.
     *
     * @param evtSpi Fully configured instance of {@link GridEventStorageSpi}.
     * @see GridConfiguration#getEventStorageSpi()
     */
    public void setEventStorageSpi(GridEventStorageSpi evtSpi) {
        this.evtSpi = evtSpi;
    }

    /** {@inheritDoc} */
    @Override public GridDiscoverySpi getDiscoverySpi() {
        return discoSpi;
    }

    /**
     * Sets fully configured instance of {@link GridDiscoverySpi}.
     *
     * @param discoSpi Fully configured instance of {@link GridDiscoverySpi}.
     * @see GridConfiguration#getDiscoverySpi()
     */
    public void setDiscoverySpi(GridDiscoverySpi discoSpi) {
        this.discoSpi = discoSpi;
    }

    /** {@inheritDoc} */
    @Override public GridCommunicationSpi getCommunicationSpi() {
        return commSpi;
    }

    /**
     * Sets fully configured instance of {@link GridCommunicationSpi}.
     *
     * @param commSpi Fully configured instance of {@link GridCommunicationSpi}.
     * @see GridConfiguration#getCommunicationSpi()
     */
    public void setCommunicationSpi(GridCommunicationSpi commSpi) {
        this.commSpi = commSpi;
    }

    /** {@inheritDoc} */
    @Override public GridCollisionSpi getCollisionSpi() {
        return colSpi;
    }

    /**
     * Sets fully configured instance of {@link GridCollisionSpi}.
     *
     * @param colSpi Fully configured instance of {@link GridCollisionSpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getCollisionSpi()
     */
    public void setCollisionSpi(GridCollisionSpi colSpi) {
        this.colSpi = colSpi;
    }

    /** {@inheritDoc} */
    @Override public GridLocalMetricsSpi getMetricsSpi() {
        return metricsSpi;
    }

    /**
     * Sets fully configured instance of {@link GridLocalMetricsSpi}.
     *
     * @param metricsSpi Fully configured instance of {@link GridLocalMetricsSpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getMetricsSpi()
     */
    public void setMetricsSpi(GridLocalMetricsSpi metricsSpi) {
        this.metricsSpi = metricsSpi;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentSpi getDeploymentSpi() {
        return deploySpi;
    }

    /**
     * Sets fully configured instance of {@link GridDeploymentSpi}.
     *
     * @param deploySpi Fully configured instance of {@link GridDeploymentSpi}.
     * @see GridConfiguration#getDeploymentSpi()
     */
    public void setDeploymentSpi(GridDeploymentSpi deploySpi) {
        this.deploySpi = deploySpi;
    }

    /** {@inheritDoc} */
    @Override public GridCheckpointSpi[] getCheckpointSpi() {
        return cpSpi;
    }

    /**
     * Sets fully configured instance of {@link GridCheckpointSpi}.
     *
     * @param cpSpi Fully configured instance of {@link GridCheckpointSpi}.
     * @see GridConfiguration#getCheckpointSpi()
     */
    public void setCheckpointSpi(GridCheckpointSpi... cpSpi) {
        this.cpSpi = cpSpi;
    }

    /** {@inheritDoc} */
    @Override public GridTracingSpi[] getTracingSpi() {
        return traceSpi;
    }

    /**
     * Sets fully configured instance of {@link GridTracingSpi}.
     *
     * @param traceSpi Fully configured instance of {@link GridTracingSpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getTracingSpi()
     */
    public void setTracingSpi(GridTracingSpi... traceSpi) {
        this.traceSpi = traceSpi;
    }

    /** {@inheritDoc} */
    @Override public GridFailoverSpi[] getFailoverSpi() {
        return failSpi;
    }

    /**
     * Sets fully configured instance of {@link GridFailoverSpi}.
     *
     * @param failSpi Fully configured instance of {@link GridFailoverSpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getFailoverSpi()
     */
    public void setFailoverSpi(GridFailoverSpi... failSpi) {
        this.failSpi = failSpi;
    }

    /** {@inheritDoc} */
    @Override public GridTopologySpi[] getTopologySpi() {
        return topSpi;
    }

    /**
     * Sets fully configured instance of {@link GridTopologySpi}.
     *
     * @param topSpi Fully configured instance of {@link GridTopologySpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getTopologySpi()
     */
    public void setTopologySpi(GridTopologySpi... topSpi) {
        this.topSpi = topSpi;
    }

    /** {@inheritDoc} */
    @Override public GridLoadBalancingSpi[] getLoadBalancingSpi() {
        return loadBalancingSpi;
    }

    /** {@inheritDoc} */
    @Override public long getDiscoveryStartupDelay() {
        return discoStartupDelay;
    }

    /**
     * Sets time in milliseconds after which a certain metric value is considered expired.
     * If not set explicitly, then default value is {@code 600,000} milliseconds (10 minutes).
     *
     * @param discoStartupDelay Time in milliseconds for when nodes
     *      can be out-of-sync during startup.
     */
    public void setDiscoveryStartupDelay(long discoStartupDelay) {
        this.discoStartupDelay = discoStartupDelay;
    }

    /**
     * Sets fully configured instance of {@link GridLoadBalancingSpi}.
     *
     * @param loadBalancingSpi Fully configured instance of {@link GridLoadBalancingSpi} or
     *      {@code null} if no SPI provided.
     * @see GridConfiguration#getLoadBalancingSpi()
     */
    public void setLoadBalancingSpi(GridLoadBalancingSpi... loadBalancingSpi) {
        this.loadBalancingSpi = loadBalancingSpi;
    }

    /**
     * Sets fully configured instances of {@link GridSwapSpaceSpi}.
     *
     * @param swapSpaceSpi Fully configured instances of {@link GridSwapSpaceSpi} or
     *      <tt>null</tt> if no SPI provided.
     * @see GridConfiguration#getSwapSpaceSpi()
     */
    public void setSwapSpaceSpi(GridSwapSpaceSpi... swapSpaceSpi) {
        this.swapSpaceSpi = swapSpaceSpi;
    }

    /** {@inheritDoc} */
    @Override public GridSwapSpaceSpi[] getSwapSpaceSpi() {
        return swapSpaceSpi;
    }

    /**
     * Sets fully configured instances of {@link GridCloudSpi}.
     *
     * @param cloudSpi Fully configured instances of {@link GridCloudSpi} or
     *      <tt>null</tt> if no SPI provided.
     * @see GridConfiguration#getCloudSpi()
     */
    public void setCloudSpi(GridCloudSpi... cloudSpi) {
        this.cloudSpi = cloudSpi;
    }

    /** {@inheritDoc} */
    @Override public GridCloudSpi[] getCloudSpi() {
        return cloudSpi;
    }

    /**
     * Sets task classes and resources sharing mode.
     *
     * @param deployMode Task classes and resources sharing mode.
     */
    public void setDeploymentMode(GridDeploymentMode deployMode) {
        this.deployMode = deployMode;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentMode getDeploymentMode() {
        return deployMode;
    }

    /**
     * Sets size of missed resources cache. Set 0 to avoid
     * missed resources caching.
     *
     * @param p2pMissedCacheSize Size of missed resources cache.
     */
    public void setPeerClassLoadingMissedResourcesCacheSize(int p2pMissedCacheSize) {
        this.p2pMissedCacheSize = p2pMissedCacheSize;
    }

    /** {@inheritDoc} */
    @Override public int getPeerClassLoadingMissedResourcesCacheSize() {
        return p2pMissedCacheSize;
    }

    /** {@inheritDoc} */
    @Override public GridCacheConfiguration[] getCacheConfiguration() {
        return cacheCfg;
    }

    /**
     * Sets cache configurations.
     *
     * @param cacheCfg Cache configurations.
     */
    @SuppressWarnings({"ZeroLengthArrayAllocation"})
    public void setCacheConfiguration(GridCacheConfiguration... cacheCfg) {
        this.cacheCfg = cacheCfg == null ? new GridCacheConfiguration[0] : cacheCfg;
    }

    /** {@inheritDoc} */
    @Override public int[] getIncludeEventTypes() {
        return inclEvtTypes;
    }

    /**
     * Sets array of event types, which will be recorded by {@link GridEventStorageManager#record(GridEvent)}.
     * Note, that either the include event types or the exclude event types can be established.
     *
     * @param inclEvtTypes Include event types.
     */
    public void setIncludeEventTypes(int... inclEvtTypes) {
        this.inclEvtTypes = inclEvtTypes;
    }

    /** {@inheritDoc} */
    @Override public int[] getExcludeEventTypes() {
        return exclEvtTypes;
    }

    /**
     * Sets array of event types, which will not be recorded by {@link GridEventStorageManager#record(GridEvent)}.
     * Note, that either the include event types or the exclude event types can be established.
     *
     * @param exclEvtTypes Exclude event types.
     */
    public void setExcludeEventTypes(int... exclEvtTypes) {
        this.exclEvtTypes = exclEvtTypes;
    }

    /** {@inheritDoc} */
    @Override public boolean isDisableCloudCoordinator() {
        return disableCloudCrd;
    }

    /**
     * Sets whether or not this should be considered for being cloud coordinator.
     *
     * @param disableCloudCrd Whether or not this should be considered for being cloud
     *      coordinator. {@code true} will exclude this node from consideration.
     */
    public void setDisableCloudCoordinator(boolean disableCloudCrd) {
        this.disableCloudCrd = disableCloudCrd;
    }

    /**
     * Sets cloud strategies.
     *
     * @param cloudStrategies Cloud strategies to set.
     */
    public void setCloudStrategies(GridCloudStrategy[] cloudStrategies) {
        this.cloudStrategies = cloudStrategies;
    }

    /** {@inheritDoc} */
    @Override public GridCloudStrategy[] getCloudStrategies() {
        return cloudStrategies;
    }

    /**
     * Sets cloud policies.
     *
     * @param cloudPolicies Cloud policies to set.
     */
    public void setCloudPolicies(GridCloudPolicy[] cloudPolicies) {
        this.cloudPolicies = cloudPolicies;
    }

    /** {@inheritDoc} */
    @Override public GridCloudPolicy[] getCloudPolicies() {
        return cloudPolicies;
    }

    /**
     * Sets path, either absolute or relative to {@code GRIDGAIN_HOME}, to {@code JETTY}
     * XML configuration file. {@code JETTY} is used to support REST over HTTP protocol for
     * accessing GridGain APIs remotely.
     *
     * @param jettyPath Path to {@code JETTY} XML configuration file.
     */
    public void setRestJettyPath(String jettyPath) {
        this.jettyPath = jettyPath;
    }

    /** {@inheritDoc} */
    @Override public String getRestJettyPath() {
        return jettyPath;
    }

    /**
     * Sets flag indicating whether external {@code REST} access is enabled or not.
     *
     * @param restEnabled Flag indicating whether external {@code REST} access is enabled or not.
     */
    public void setRestEnabled(boolean restEnabled) {
        this.restEnabled = restEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isRestEnabled() {
        return restEnabled;
    }

    /**
     * Sets system-wide local address or host for all GridGain components to bind to. If provided it will
     * override all default local bind settings within GridGain or any of its SPIs.
     *
     * @param locHost Local IP address or host to bind to.
     */
    public void setLocalHost(String locHost) {
        this.locHost = locHost;
    }

    /** {@inheritDoc} */
    @Override public String getLocalHost() {
        return locHost;
    }

    /**
     * Sets secret key to authenticate REST requests. If key is {@code null} or empty authentication is disabled.
     *
     * @param restSecretKey REST secret key.
     */
    public void setRestSecretKey(String restSecretKey) {
        this.restSecretKey = restSecretKey;
    }

    /** {@inheritDoc} */
    @Override public String getRestSecretKey() {
        return restSecretKey;
    }

    /** {@inheritDoc} */
    @Override @Nullable public String[] getIncludeProperties() {
        return includeProps;
    }

    /**
     * Sets array of system or environment property names to include into node attributes.
     * See {@link #getIncludeProperties()} for more info.
     *
     * @param includeProps Array of system or environment property names to include into node attributes.
     */
    public void setIncludeProperties(String... includeProps) {
        this.includeProps = includeProps;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridConfigurationAdapter.class, this);
    }
}
