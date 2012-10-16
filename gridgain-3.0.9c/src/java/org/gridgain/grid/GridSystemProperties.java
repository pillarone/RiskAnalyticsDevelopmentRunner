// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.editions.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.test.*;

/**
 * Contains constants for all system properties and environmental variables in GridGain. These
 * properties and variables can be used to affect the behavior of GridGain.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridTestVmParameters
 */
public final class GridSystemProperties {
    /**
     * If this system property is present the GridGain will include grid name into verbose log.
     */
    public static final String GG_LOG_GRID_NAME = "GRIDGAIN_LOG_GRID_NAME";

    /**
     * This property is used internally to pass an exit code to loader when
     * GridGain instance is being restarted.
     */
    public static final String GG_RESTART_CODE = "GRIDGAIN_RESTART_CODE";

    /**
     * Presence of this system property with value {@code true} will make the grid
     * node start as a daemon node. Node that this system property will override
     * {@link GridConfiguration#isDaemon()} configuration.
     */
    public static final String GG_DAEMON = "GRIDGAIN_DAEMON";

    /** Defines GridGain installation folder. */
    public static final String GG_HOME = "GRIDGAIN_HOME";

    /** If this system property is set to {@code false} - no shutdown hook will be set. */
    public static final String GG_NO_SHUTDOWN_HOOK = "GRIDGAIN_NO_SHUTDOWN_HOOK";

    /**
     * Name of the system property to disable requirement for proper node ordering
     * by discovery SPI. Use with care, as proper node ordering is required for
     * cache consistency. If set to {@code true}, then any discovery SPI can be used
     * with distributed cache, otherwise, only discovery SPIs that have annotation
     * {@link GridDiscoverySpiOrderSupport @GridDiscoverySpiOrderSupport(true)} will
     * be allowed.
     */
    public static final String GG_NO_DISCO_ORDER = "GRIDGAIN_NO_DISCO_ORDER";

    /**
     * If this system property is set to {@code false} - no checks for new versions will
     * be performed by GridGain. By default, GridGain periodically checks for the new
     * version and prints out the message into the log if new version of GridGain is
     * available for download.
     */
    public static final String GG_UPDATE_NOTIFIER = "GRIDGAIN_UPDATE_NOTIFIER";

    /**
     * If this system property is present (any value) - no ASCII logo will
     * be printed.
     */
    public static final String GG_NO_ASCII = "GRIDGAIN_NO_ASCII";

    /**
     * This property allows to override Jetty host for REST controller.
     * Note that REST functionality is available only in Enterprise Edition.
     */
    public static final String GG_JETTY_HOST = "GRIDGAIN_JETTY_HOST";

    /**
     * This property allows to override Jetty local port for REST controller.
     * Note that REST functionality is available only in Enterprise Edition.
     */
    public static final String GG_JETTY_PORT = "GRIDGAIN_JETTY_PORT";

    /**
     * Set to either {@code true} or {@code false} to enable or disable quiet mode
     * of GridGain. In quiet mode, only warning and errors are printed into the log
     * additionally to a shortened version of standard output on the start.
     * <p>
     * Note that if you use <tt>ggstart.{sh|bat}</tt> scripts to start GridGain they
     * start by default in quiet mode. You can supply <tt>-v</tt> flag to override it.
     * <p>
     * Note also that in quiet mode no other log is produced (no INFO or DEBUG even
     * if they are enabled in the log configuration).
     */
    public static final String GG_QUIET = "GRIDGAIN_QUIET";

    /**
     * If set to any value will make default log level for Log4j to <tt>DEBUG</tt>.
     */
    public static final String GG_DFLT_LOG4J_DEBUG = "GRIDGAIN_DFLT_LOG4J_DEBUG";

    /**
     * Name of the system property defining name of command line program.
     */
    public static final String GG_PROG_NAME = "GRIDGAIN_PROG_NAME";

    /**
     * Name of the system property defining success file name. This file
     * is used with auto-restarting functionality when GridGain is started
     * by supplied <tt>ggstart.{bat|sh}</tt> scripts.
     */
    public static final String GG_SUCCESS_FILE = "GRIDGAIN_SUCCESS_FILE";

    /**
     * Name of the system property or environment variable to set or override
     * SMTP host. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getSmtpHost()
     */
    public static final String GG_SMTP_HOST = "GRIDGAIN_SMTP_HOST";

    /**
     * Name of the system property or environment variable to set or override
     * SMTP port. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getSmtpPort()
     * @see GridConfiguration#DFLT_SMTP_PORT
     */
    public static final String GG_SMTP_PORT = "GRIDGAIN_SMTP_PORT";

    /**
     * Name of the system property or environment variable to set or override
     * SMTP username. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getSmtpUsername()
     */
    public static final String GG_SMTP_USERNAME = "GRIDGAIN_SMTP_USERNAME";

    /**
     * Name of the system property or environment variable to set or override
     * SMTP password. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getSmtpPassword()
     */
    public static final String GG_SMTP_PWD = "GRIDGAIN_SMTP_PASSWORD";

    /**
     * Name of the system property or environment variable to set or override
     * SMTP FROM email. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getSmtpFromEmail()
     * @see GridConfiguration#DFLT_SMTP_FROM_EMAIL
     */
    public static final String GG_SMTP_FROM = "GRIDGAIN_SMTP_FROM";

    /**
     * Name of the system property or environment variable to set or override
     * list of admin emails. Value of this property should be comma-separated list
     * of emails. If provided - it will override the property in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#getAdminEmails()
     */
    public static final String GG_ADMIN_EMAILS = "GRIDGAIN_ADMIN_EMAILS";

    /**
     * Name of the system property or environment variable to set or override
     * whether or not to use SSL. If provided - it will override the property
     * in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#isSmtpSsl()
     * @see GridConfiguration#DFLT_SMTP_SSL
     */
    public static final String GG_SMTP_SSL = "GRIDGAIN_SMTP_SSL";

    /**
     * Name of the system property or environment variable to set or override
     * whether or not to enable email notifications for node lifecycle. If provided -
     * it will override the property in grid configuration.
     * <p>
     * Note that life cycle notification is only available in Enterprise Edition. In
     * Community Edition this property is ignored.
     *
     * @see GridConfiguration#isLifeCycleEmailNotification()
     */
    @GridEnterpriseFeature
    public static final String GG_LIFECYCLE_EMAIL_NOTIFY = "GRIDGAIN_LIFECYCLE_EMAIL_NOTIFY";

    /**
     * Name of the system property or environment variable to set or override
     * whether or not to use STARTTLS. If provided - it will override the property
     * in grid configuration.
     * <p>
     * Note that GridGain uses SMTP to send emails in critical
     * situations such as license expiration or fatal system errors.
     * It is <b>highly</b> recommended to configure SMTP in production
     * environment.
     *
     * @see GridConfiguration#isSmtpStartTls()
     * @see GridConfiguration#DFLT_SMTP_STARTTLS
     */
    public static final String GG_SMTP_STARTTLS = "GRIDGAIN_SMTP_STARTTLS";

    /**
     * Name of system property to set system-wide local IP address or host. If provided it will
     * override all default local bind settings within GridGain or any of its SPIs.
     * <p>
     * Note that system-wide local bind address can also be set via {@link GridConfiguration#getLocalHost()}
     * method. However, system properties have priority over configuration properties specified in
     * {@link GridConfiguration}.
     */
    public static final String GG_LOCAL_HOST = "GRIDGAIN_LOCAL_HOST";

    /**
     * Name of the system property or environment variable to activate synchronous
     * listener notification for future objects implemented in GridGain. I.e.
     * closure passed into method {@link GridFuture#listenAsync(GridInClosure)} will
     * be evaluated in the same thread that will end the future.
     *
     * @see GridFuture#syncNotify()
     */
    public static final String GG_FUT_SYNC_NOTIFICATION = "GRIDGAIN_FUTURE_SYNC_NOTIFICATION";

    /**
     * Name of the system property or environment variable to activate concurrent
     * listener notification for future objects implemented in GridGain. I.e.
     * upon future completion every listener will be notified concurrently in a
     * separate thread.
     *
     * @see GridFuture#concurrentNotify()
     */
    public static final String GG_FUT_CONCURRENT_NOTIFICATION = "GRIDGAIN_FUTURE_CONCURRENT_NOTIFICATION";

    /**
     * System property to enable or disable stop watch. If enabled, GridGain will watch
     * various internal execution points and will print out stats at the end.
     */
    public static final String GG_STOPWATCH_ENABLED = "GRIDGAIN_STOPWATCH";

    /**
     * If {@link #GG_STOPWATCH_ENABLED} is {@code true}, then this property will control whether
     * or not to print out every execution step or not.
     */
    public static final String GG_STOPWATCH_PRINTSTEP = "GRIDGAIN_STOPWATCH_PRINTSTEP";

    /**
     * Enforces singleton.
     */
    private GridSystemProperties() {
        // No-op.
    }
}
