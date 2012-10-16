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
import org.jetbrains.annotations.*;

import javax.management.*;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This interface defines grid runtime configuration. This configuration is passed to
 * {@link GridFactory#start(GridConfiguration)} method. It defines all configuration
 * parameters required to start a grid instance. Usually, a special
 * class called "loader" will create an instance of this interface and apply
 * {@link GridFactory#start(GridConfiguration)} method to initialize GridGain instance.
 * <p>
 * Note, that absolutely every configuration property in {@code GridConfiguration} is optional.
 * One can simply create new instance of {@link GridConfigurationAdapter}, for example,
 * and pass it to {@link GridFactory#start(GridConfiguration)} to start grid with
 * default configuration. See {@link GridFactory} documentation for information about
 * default configuration properties used and more information on how to start grid.
 * <p>
 * For more information about grid configuration and startup refer to {@link GridFactory}
 * documentation which includes description and default values for every configuration
 * property.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridConfiguration {
    /**
     * Default flag for peer class loading. By default the value is {@code true}
     * which means that peer class loading is enabled.
     */
    public static final boolean DFLT_P2P_ENABLED = true;

    /** Default metrics history size (value is {@code 10000}). */
    public static final int DFLT_METRICS_HISTORY_SIZE = 10000;

    /**
     * Default metrics expire time. The value is {@link Long#MAX_VALUE} which
     * means that metrics never expire.
     */
    public static final long DFLT_METRICS_EXPIRE_TIME = Long.MAX_VALUE;

    /** Default maximum peer class loading timeout in milliseconds (value is {@code 5,000ms}). */
    public static final long DFLT_NETWORK_TIMEOUT = 5000;

    /** Default discovery startup delay in milliseconds (value is {@code 60,000ms}). */
    public static final long DFLT_DISCOVERY_STARTUP_DELAY = 60000;

    /** Default deployment mode (value is {@link GridDeploymentMode#SHARED}). */
    public static final GridDeploymentMode DFLT_DEPLOYMENT_MODE = GridDeploymentMode.SHARED;

    /** Default cache size for missed resources. */
    public static final int DFLT_P2P_MISSED_RESOURCES_CACHE_SIZE = 100;

    /** Default SMTP port. */
    public static final int DFLT_SMTP_PORT = 25;

    /** Default SSL enabled flag. */
    public static final boolean DFLT_SMTP_SSL = false;

    /** Default STARTTLS enabled flag. */
    public static final boolean DFLT_SMTP_STARTTLS = false;

    /** Default FROM email address. */
    public static final String DFLT_SMTP_FROM_EMAIL = "info@gridgain.com";

    /**
     * Whether or not send email notifications on node start and stop. Note if enabled
     * email notifications will only be sent if SMTP is configured and at least one
     * admin email is provided.
     * <p>
     * By default - email notifications are enabled.
     * <p>
     * Note that life cycle notification is only available in Enterprise Edition. In
     * Community Edition this property is ignored.
     *
     * @return {@code True} to enable lifecycle email notifications.
     * @see #getSmtpHost()
     * @see #getAdminEmails()
     */
    @GridEnterpriseFeature
    public boolean isLifeCycleEmailNotification();

    /**
     * Gets SMTP host name or {@code null} if SMTP is not configured.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@code getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return SMTP host name or {@code null} if SMTP is not configured.
     * @see GridSystemProperties#GG_SMTP_HOST
     */
    @GridEnterpriseFeature
    @Nullable public String getSmtpHost();

    /**
     * Whether or not to use SSL fot SMTP. Default is {@link #DFLT_SMTP_SSL}.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return Whether or not to use SSL fot SMTP.
     * @see #DFLT_SMTP_SSL
     * @see GridSystemProperties#GG_SMTP_SSL
     */
    @GridEnterpriseFeature
    public boolean isSmtpSsl();

    /**
     * Whether or not to use STARTTLS fot SMTP. Default is {@link #DFLT_SMTP_STARTTLS}.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return Whether or not to use STARTTLS fot SMTP.
     * @see #DFLT_SMTP_STARTTLS
     * @see GridSystemProperties#GG_SMTP_STARTTLS
     */
    @GridEnterpriseFeature
    public boolean isSmtpStartTls();

    /**
     * Gets SMTP port. Default value is {@link #DFLT_SMTP_PORT}.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return SMTP port.
     * @see #DFLT_SMTP_PORT
     * @see GridSystemProperties#GG_SMTP_PORT
     */
    @GridEnterpriseFeature
    public int getSmtpPort();

    /**
     * Gets SMTP username or {@code null} if not used.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return SMTP username or {@code null}.
     * @see GridSystemProperties#GG_SMTP_USERNAME
     */
    @GridEnterpriseFeature
    @Nullable public String getSmtpUsername();

    /**
     * SMTP password or {@code null} if not used.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     * <p>
     * Note that {@link #getSmtpHost()} is the only mandatory SMTP
     * configuration property.
     *
     * @return SMTP password or {@code null}.
     * @see GridSystemProperties#GG_SMTP_PWD
     */
    @GridEnterpriseFeature
    @Nullable public String getSmtpPassword();

    /**
     * Gets optional set of admin emails where email notifications will be set.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @return Optional set of admin emails where email notifications will be set.
     *      If {@code null} - emails will be sent only to the email in the license
     *      if one provided.
     * @see GridSystemProperties#GG_ADMIN_EMAILS
     */
    @GridEnterpriseFeature
    @Nullable public String[] getAdminEmails();

    /**
     * Gets optional FROM email address for email notifications. By default
     * {@link #DFLT_SMTP_FROM_EMAIL} will be used.
     *
     * @return Optional FROM email address for email notifications. If {@code null}
     *      - {@link #DFLT_SMTP_FROM_EMAIL} will be used by default.
     * @see #DFLT_SMTP_FROM_EMAIL
     * @see GridSystemProperties#GG_SMTP_FROM
     */
    @GridEnterpriseFeature
    @Nullable public String getSmtpFromEmail();

    /**
     * Gets optional name of this grid instance. If name is not provides, {@code null} will
     * be used as a default name for the grid instance.
     *
     * @return Grid name. Can be {@code null} which is default if non-default name was
     *      not provided.
     */
    public String getGridName();

    /**
     * Gets optional set of cloud strategies. Note that cloud strategies are only
     * activated on the local node if the local node is acting as cloud coordinator.
     *
     * @return Optional set of cloud strategies.
     */
    public GridCloudStrategy[] getCloudStrategies();

    /**
     * Gets optional set of cloud policies. Note that cloud policies are only
     * activated on the local node if the local node is acting as cloud coordinator.
     *
     * @return Optional set of cloud policies.
     */
    public GridCloudPolicy[] getCloudPolicies();

    /**
     * Should return any user-defined attributes to be added to this node. These attributes can
     * then be accessed on nodes by calling {@link GridNode#getAttribute(String)} or
     * {@link GridNode#getAttributes()} methods.
     * <p>
     * Note that system adds the following (among others) attributes automatically:
     * <ul>
     * <li>{@code {@link System#getProperties()}} - All system properties.</li>
     * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
     * </ul>
     * <p>
     * Note that grid will add all System properties and environment properties
     * to grid node attributes also. SPIs may also add node attributes that are
     * used for SPI implementation.
     * <p>
     * <b>NOTE:</b> attributes names starting with {@code org.gridgain} are reserved
     * for internal use.
     *
     * @return User defined attributes for this node.
     */
    public Map<String, ?> getUserAttributes();

    /**
     * Should return an instance of logger to use in grid. If not provided, default value will be used.
     * See {@link GridFactory} for information on default configuration.
     *
     * @return Logger to use in grid.
     */
    public GridLogger getGridLogger();

    /**
     * Should return an instance of marshaller to use in grid. If not provided, default value will be used.
     * See {@link GridFactory} for information on default configuration.
     *
     * @return Marshaller to use in grid.
     */
    public GridMarshaller getMarshaller();

    /**
     * Should return an instance of fully configured thread pool to be used in grid.
     * This executor service will be in charge of processing {@link GridTask GridTasks}
     * and {@link GridJob GridJobs}.
     * <p>
     * If not provided, default value will be used. See {@link GridFactory} for
     * information on default configuration.
     *
     * @return Thread pool implementation to be used in grid to process job execution
     *      requests and user messages sent to the node.
     */
    public ExecutorService getExecutorService();

     /**
     * Shutdown flag for executor service.
     * <p>
     * If not provided, default value {@code true} will be used which will shutdown
     * executor service when GridGain stops regardless of whether it was started before
     * GridGain or by GridGain.
     *
     * @return Executor service shutdown flag.
     */
    public boolean getExecutorServiceShutdown();

    /**
     * Executor service that is in charge of processing {@link GridTaskSession} messages
     * and job responses.
     * <p>
     * If not provided, default value will be used. See {@link GridFactory} for
     * information on default configuration.
     *
     * @return Thread pool implementation to be used in grid for job responses
     *      and session attributes processing.
     */
    public ExecutorService getSystemExecutorService();

    /**
     * Shutdown flag for system executor service.
     * <p>
     * If not provided, default value {@code true} will be used which will shutdown
     * executor service when GridGain stops regardless of whether it was started before
     * GridGain or by GridGain.
     *
     * @return System executor service shutdown flag.
     */
    public boolean getSystemExecutorServiceShutdown();

    /**
     * Should return GridGain installation home folder. If not provided, the system will check
     * {@code GRIDGAIN_HOME} system property and environment variable in that order. If
     * {@code GRIDGAIN_HOME} still could not be obtained, then grid will not start and exception
     * will be thrown.
     *
     * @return GridGain installation home or {@code null} to make the system attempt to
     *      infer it automatically.
     * @see GridSystemProperties#GG_HOME
     */
    @Nullable public String getGridGainHome();

    /**
     * Should return MBean server instance. If not provided, the system will use default
     * platform MBean server.
     *
     * @return MBean server instance or {@code null} to make the system create a default one.
     * @see ManagementFactory#getPlatformMBeanServer()
     */
    public MBeanServer getMBeanServer();

    /**
     * Unique identifier for this node within grid. If not provided, default value will be used.
     * See {@link GridFactory} for information on default configuration.
     *
     * @return Unique identifier for this node within grid.
     */
    public UUID getNodeId();

    /**
     * Number of node metrics to keep in memory to calculate totals and averages.
     * If not provided (value is {@code 0}), then default value
     * {@link #DFLT_METRICS_HISTORY_SIZE} is used.
     *
     * @return Metrics history size.
     * @see #DFLT_METRICS_HISTORY_SIZE
     */
    public int getMetricsHistorySize();

    /**
     * Elapsed time in milliseconds after which node metrics are considered expired.
     * If not provided (value is {@code 0}), then default value
     * {@link #DFLT_METRICS_EXPIRE_TIME} is used.
     *
     * @return Metrics expire time.
     * @see #DFLT_METRICS_EXPIRE_TIME
     */
    public long getMetricsExpireTime();

    /**
     * Maximum timeout in milliseconds for network requests.
     * <p>
     * If not provided (value is {@code 0}), then default value
     * {@link #DFLT_NETWORK_TIMEOUT} is used.
     *
     * @return Maximum timeout for network requests.
     * @see #DFLT_NETWORK_TIMEOUT
     */
    public long getNetworkTimeout();

    /**
     * Returns a collection of life-cycle beans. These beans will be automatically
     * notified of grid life-cycle events. Use life-cycle beans whenever you
     * want to perform certain logic before and after grid startup and stopping
     * routines.
     *
     * @return Collection of life-cycle beans.
     * @see GridLifecycleBean
     * @see GridLifecycleEventType
     */
    public GridLifecycleBean[] getLifecycleBeans();

    /**
     * Should return fully configured discovery SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid discovery SPI implementation or {@code null} to use default implementation.
     */
    public GridDiscoverySpi getDiscoverySpi();

    /**
     * Should return fully configured SPI communication  implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid communication SPI implementation or {@code null} to use default implementation.
     */
    public GridCommunicationSpi getCommunicationSpi();

    /**
     * Should return fully configured event SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid event SPI implementation or {@code null} to use default implementation.
     */
    public GridEventStorageSpi getEventStorageSpi();

    /**
     * Should return fully configured collision SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid collision SPI implementation or {@code null} to use default implementation.
     */
    public GridCollisionSpi getCollisionSpi();

    /**
     * Should return fully configured metrics SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid metrics SPI implementation or {@code null} to use default implementation.
     */
    public GridLocalMetricsSpi getMetricsSpi();

    /**
     * Should return fully configured deployment SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid deployment SPI implementation or {@code null} to use default implementation.
     */
    public GridDeploymentSpi getDeploymentSpi();

    /**
     * Should return fully configured checkpoint SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid checkpoint SPI implementation or {@code null} to use default implementation.
     */
    public GridCheckpointSpi[] getCheckpointSpi();

    /**
     * Should return fully configured failover SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid failover SPI implementation or {@code null} to use default implementation.
     */
    public GridFailoverSpi[] getFailoverSpi();

    /**
     * Should return fully configured topology SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid topology SPI implementation or {@code null} to use default implementation.
     */
    public GridTopologySpi[] getTopologySpi();

    /**
     * Should return fully configured load balancing SPI implementation. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     *
     * @return Grid load balancing SPI implementation or {@code null} to use default implementation.
     */
    public GridLoadBalancingSpi[] getLoadBalancingSpi();

    /**
     * Should return fully configured tracing SPI implementation. If not provided, returns
     * {@code null}. Note that tracing SPI is optional. If not provided - no tracing will
     * be configured (i.e. there is no default tracing SPI implementation).
     *
     * @return Grid tracing SPI implementation or {@code null} to <b>not use tracing.</b>
     */
    public GridTracingSpi[] getTracingSpi();

    /**
     * Should return fully configured swap space SPI implementations. If not provided, default
     * implementation will be used. See {@link GridFactory} for information on default configuration.
     * <p>
     * Note that user can provide one or multiple instances of this SPI (and select later which one
     * is used in a particular context).
     *
     * @return Grid swap space SPI implementation or <tt>null</tt> to use default implementation.
     */
    public GridSwapSpaceSpi[] getSwapSpaceSpi();

    /**
     * Should return fully configured cloud SPI implementations.
     * Note that cloud SPI is optional. If not provided, no cloud support
     * will be used.
     * <p>
     * Note that user can provide one or multiple instances of this SPI (and select later which one
     * is used in a particular context).
     *
     * @return Grid cloud SPI implementation or <tt>null</tt> to
     *      not use cloud SPI.
     */
    public GridCloudSpi[] getCloudSpi();

    /**
     * This value is used to expire messages from waiting list whenever node
     * discovery discrepancies happen.
     * <p>
     * During startup, it is possible for some SPIs, such as
     * {@code GridJmsDiscoverySpi}, to have a
     * small time window when <tt>Node A</tt> has discovered <tt>Node B</tt>, but <tt>Node B</tt>
     * has not discovered <tt>Node A</tt> yet. Such time window is usually very small,
     * a matter of milliseconds, but certain JMS providers may be very slow and hence have
     * larger discovery delay window.
     * <p>
     * The default value of this property is {@code 60,000} specified by
     * {@link #DFLT_DISCOVERY_STARTUP_DELAY}. This should be good enough for vast
     * majority of configurations. However, if you do anticipate an even larger
     * delay, you should increase this value.
     *
     * @return Time in milliseconds for when nodes can be out-of-sync.
     */
    public long getDiscoveryStartupDelay();

    /**
     * Gets deployment mode for deploying tasks and other classes on this node.
     * Refer to {@link GridDeploymentMode} documentation for more information.
     *
     * @return Deployment mode.
     */
    public GridDeploymentMode getDeploymentMode();

    /**
     * Returns {@code true} if peer class loading is enabled, {@code false}
     * otherwise. Default value is {@code true} specified by {@link #DFLT_P2P_ENABLED}.
     * <p>
     * When peer class loading is enabled and task is not deployed on local node,
     * local node will try to load classes from the node that initiated task
     * execution. This way, a task can be physically deployed only on one node
     * and then internally penetrate to all other nodes.
     * <p>
     * See {@link GridTask} documentation for more information about task deployment.
     *
     * @return {@code true} if peer class loading is enabled, {@code false}
     *      otherwise.
     */
    public boolean isPeerClassLoadingEnabled();

    /**
     * Should return list of packages from the system classpath that need to
     * be peer-to-peer loaded from task originating node.
     * '*' is supported at the end of the package name which means
     * that all sub-packages and their classes are included like in Java
     * package import clause.
     *
     * @return List of peer-to-peer loaded package names.
     */
    public String[] getPeerClassLoadingClassPathExclude();

    /**
     * Returns missed resources cache size. If size greater than {@code 0}, missed
     * resources will be cached and next resource request ignored. If size is {@code 0},
     * then request for the resource will be sent to the remote node every time this
     * resource is requested.
     *
     * @return Missed resources cache size.
     */
    public int getPeerClassLoadingMissedResourcesCacheSize();

    /**
     * Should return an instance of fully configured executor service which
     * is in charge of peer class loading requests/responses. If you don't use
     * peer class loading and use GAR deployment only we would recommend to decrease
     * the value of total threads to {@code 1}.
     * <p>
     * If not provided, default value will be used. See {@link GridFactory} for
     * information on default configuration.
     *
     * @return Thread pool implementation to be used for peer class loading
     *      requests handling.
     */
    public ExecutorService getPeerClassLoadingExecutorService();

    /**
     * Should return flag of peer class loading executor service shutdown when the grid stops.
     * <p>
     * If not provided, default value {@code true} will be used which means
     * that when grid will be stopped it will shut down peer class loading executor service.
     *
     * @return Peer class loading executor service shutdown flag.
     */
    public boolean getPeerClassLoadingExecutorServiceShutdown();

    /**
     * Gets array of event types, which will be recorded.
     * <p>
     * Note, that either the include event types or the exclude event types can be provided - but not both.
     * To disable all events this method should return an empty array and {@link #getExcludeEventTypes()} should
     * return {@code null} (which it does by default).
     * <p>
     * Note that by default all events in GridGain are enabled and therefore generated and stored
     * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
     * under the load and therefore it creates a significant additional load on the system. If these events are
     * not needed by the application this load is unnecessary and leads to significant performance degradation.
     * <p>
     * It is <b>highly recommended</b> to enable only those events that your application logic requires
     * by using either this method or
     * {@link GridConfiguration#getExcludeEventTypes()} methods in GridGain configuration. Note that certain
     * events are required for GridGain's internal operations and such events will still be generated but not
     * stored by event storage SPI if they are disabled in GridGain configuration.
     *
     * @return Include event types.
     * @see #getExcludeEventTypes()
     */
    @Nullable public int[] getIncludeEventTypes();

    /**
     * Gets array of event types, which will not be recorded.
     * <p>
     * Note, that either the include event types or the exclude event types can be provided - but not both.
     * To enable all events this method should return an empty array and {@link #getIncludeEventTypes()} should
     * return {@code null} (which it does by default).
     * <p>
     * Note that by default all events in GridGain are enabled and therefore generated and stored
     * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
     * under the load and therefore it creates a significant additional load on the system. If these events are
     * not needed by the application this load is unnecessary and leads to significant performance degradation.
     * <p>
     * It is <b>highly recommended</b> to enable only those events that your application logic requires
     * by using either this method or
     * {@link GridConfiguration#getIncludeEventTypes()} methods in GridGain configuration. Note that certain
     * events are required for GridGain's internal operations and such events will still be generated but not
     * stored by event storage SPI if they are disabled in GridGain configuration.
     *
     * @return Exclude event types.
     * @see #getIncludeEventTypes()
     */
    @Nullable public int[] getExcludeEventTypes();

    /**
     * Gets configuration (descriptors) for all caches.
     *
     * @return Array of fully initialized cache descriptors.
     */
    public GridCacheConfiguration[] getCacheConfiguration();

    /**
     * Whether or not this node should be a daemon node.
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
     * @return {@code True} if this node should be a daemon node, {@code false} otherwise.
     * @see Grid#daemonNodes(GridPredicate[])
     */
    public boolean isDaemon();

    /**
     * Returning {@code true} allows to exclude local node from being considered for cloud
     * coordinator.
     *
     * @return {@code true} if local node should be excluded from being a cloud
     *      coordinator, {@code false} otherwise.
     */
    public boolean isDisableCloudCoordinator();

    /**
     * Gets path, either absolute or relative to {@code GRIDGAIN_HOME}, to {@code Jetty}
     * XML configuration file. {@code Jetty} is used to support REST over HTTP protocol for
     * accessing GridGain APIs remotely.
     * <p>
     * By default, {@code Jetty} configuration file is located under {@code GRIDGAIN_HOME/config/rest-jetty.xml}.
     * <p>
     * Note that REST support available in Enterprise Edition only.
     *
     * @return Path to {@code JETTY} XML configuration file.
     * @see GridSystemProperties#GG_JETTY_HOST
     * @see GridSystemProperties#GG_JETTY_PORT
     */
    @GridEnterpriseFeature
    public String getRestJettyPath();

    /**
     * Gets flag indicating whether external {@code REST} access is enabled or not. By default,
     * external {@code REST} access is turned on.
     * <p>
     * Note that REST support available in Enterprise Edition only.
     *
     * @return Flag indicating whether external {@code REST} access is enabled or not.
     * @see GridSystemProperties#GG_JETTY_HOST
     * @see GridSystemProperties#GG_JETTY_PORT
     */
    @GridEnterpriseFeature
    public boolean isRestEnabled();

    /**
     * Gets system-wide local address or host for all GridGain components to bind to. If provided it will
     * override all default local bind settings within GridGain or any of its SPIs.
     *
     * @return Local IP address or host to bind to.
     */
    @Nullable public String getLocalHost();

    /**
     * Gets secret key to authenticate REST requests. If key is {@code null} or empty authentication is disabled.
     * <p>
     * Note that REST support available in Enterprise Edition only.
     *
     * @return Secret key.
     * @see GridSystemProperties#GG_JETTY_HOST
     * @see GridSystemProperties#GG_JETTY_PORT
     */
    @GridEnterpriseFeature
    @Nullable public String getRestSecretKey();

    /**
     * Gets array of system or environment properties to include into node attributes.
     * If this array is {@code null}, which is default, then all system and environment
     * properties will be included. If this array is empty, then none will be included.
     * Otherwise, for every name provided, first a system property will be looked up,
     * and then, if it is not found, environment property will be looked up.
     *
     * @return Array of system or environment properties to include into node attributes.
     */
    @Nullable public String[] getIncludeProperties();

    /**
     * Gets custom license file URL to be used instead of default license file location.
     *
     * @return Custom license file URL or {@code null} to use the default
     *      <code>$GRIDGAIN_HOME</code>-related location.
     */
    @GridEnterpriseFeature
    @Nullable public String getLicenseUrl();
}
