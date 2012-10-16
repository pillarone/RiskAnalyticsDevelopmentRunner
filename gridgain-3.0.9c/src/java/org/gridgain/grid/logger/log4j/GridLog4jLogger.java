// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.logger.log4j;

import org.apache.log4j.*;
import org.apache.log4j.varia.*;
import org.apache.log4j.xml.*;
import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;

import static org.gridgain.grid.GridSystemProperties.*;

/**
 * Log4j-based implementation for logging. This logger should be used
 * by loaders that have prefer <a target=_new href="http://logging.apache.org/log4j/docs/">log4j</a>-based logging.
 * <p>
 * Here is a typical example of configuring log4j logger in GridGain configuration file:
 * <pre name="code" class="xml">
 *      &lt;property name="gridLogger"&gt;
 *          &lt;bean class="org.gridgain.grid.logger.log4j.GridLog4jLogger"&gt;
 *              &lt;constructor-arg type="java.lang.String" value="config/default-log4j.xml"/&gt;
 *          &lt;/bean>
 *      &lt;/property&gt;
 * </pre>
 * and from your code:
 * <pre name="code" class="java">
 *      GridConfiguration cfg = new GridConfigurationAdapter();
 *      ...
 *      URL xml = U.resolveGridGainUrl("modules/tests/config/log4j-test.xml");
 *      GridLogger log = new GridLog4jLogger(xml);
 *      ...
 *      cfg.setGridLogger(log);
 * </pre>
 *
 * Please take a look at <a target=_new href="http://logging.apache.org/log4j/1.2/index.html>Apache Log4j 1.2</a>
 * for additional information.
 * <p>
 * It's recommended to use GridGain logger injection instead of using/instantiating
 * logger in your task/job code. See {@link GridLoggerResource} annotation about logger
 * injection.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLog4jLogger extends GridMetadataAwareAdapter implements GridLogger {
    /** Log4j implementation proxy. */
    @GridToStringExclude private final Logger impl;

    /** Path to configuration file. */
    private final String path;

    /**
     * Creates new logger and automatically detects if root logger already
     * has appenders configured. If it does not, the root logger will be
     * configured with default appender (analogous to calling
     * {@link #GridLog4jLogger(boolean) GridLog4jLogger(boolean)}
     * with parameter {@code true}, otherwise, existing appenders will be used (analogous
     * to calling {@link #GridLog4jLogger(boolean) GridLog4jLogger(boolean)}
     * with parameter {@code false}).
     */
    public GridLog4jLogger() {
        this(!isConfigured());
    }

    /**
     * Creates new logger. If initialize parameter is {@code true} the Log4j
     * logger will be initialized with default console appender. In this case
     * the log level will be set to {@code DEBUG} if system property
     * {@link GridSystemProperties#GG_DFLT_LOG4J_DEBUG} is present with any {@code non-null}
     * value, otherwise the log level will be set to {@code INFO}.
     *
     * @param init If {@code true}, then a default console appender with
     *      following pattern layout will be created: {@code %d{ABSOLUTE} %-5p [%c{1}] %m%n}.
     *      If {@code false}, then no implicit initialization will take place,
     *      and {@code Log4j} should be configured prior to calling this
     *      constructor.
     */
    public GridLog4jLogger(boolean init) {
        if (init) {
            String fmt = "[%d{ABSOLUTE}][%-5p][%t][%c{1}] %m%n";

            // Configure output that should go to System.out
            ConsoleAppender app = new ConsoleAppender(new PatternLayout(fmt), ConsoleAppender.SYSTEM_OUT);

            LevelRangeFilter lvlFilter = new LevelRangeFilter();

            lvlFilter.setLevelMin(Level.DEBUG);
            lvlFilter.setLevelMax(Level.INFO);

            app.addFilter(lvlFilter);

            BasicConfigurator.configure(app);

            // Configure output that should go to System.err
            app = new ConsoleAppender(new PatternLayout(fmt), ConsoleAppender.SYSTEM_ERR);

            app.setThreshold(Level.WARN);

            impl = Logger.getRootLogger();

            impl.addAppender(app);

            impl.setLevel(System.getProperty(GG_DFLT_LOG4J_DEBUG) != null ? Level.DEBUG : Level.INFO);
        }
        else {
            impl = Logger.getRootLogger();
        }

        path = null;
    }

    /**
     * Creates new logger with given implementation.
     *
     * @param impl Log4j implementation to use.
     */
    public GridLog4jLogger(Logger impl) {
        assert impl != null;

        this.impl = impl;

        path = null;
    }

    /**
     * Creates new logger with given configuration {@code path}.
     *
     * @param path Path to log4j configuration XML file.
     * @throws GridException Thrown in case logger can't be created.
     */
    public GridLog4jLogger(String path) throws GridException {
        if (path == null)
            throw new GridException("Configuration XML file for Log4j must be specified.");

        this.path = path;

        URL cfgUrl = U.resolveGridGainUrl(path);

        if (cfgUrl == null)
            throw new GridException("Log4j configuration path was not found: " + path);

        DOMConfigurator.configure(cfgUrl);

        impl = Logger.getRootLogger();
    }

    /**
     * Creates new logger with given configuration {@code cfgFile}.
     *
     * @param cfgFile Log4j configuration XML file.
     * @throws GridException Thrown in case logger can't be created.
     */
    public GridLog4jLogger(File cfgFile) throws GridException {
        if (cfgFile == null)
            throw new GridException("Configuration XML file for Log4j must be specified.");

        if (!cfgFile.exists() || cfgFile.isDirectory())
            throw new GridException("Log4j configuration path was not found or is a directory: " + cfgFile);

        path = cfgFile.getAbsolutePath();

        DOMConfigurator.configure(path);

        impl = Logger.getRootLogger();
    }

    /**
     * Creates new logger with given configuration {@code cfgUrl}.
     *
     * @param cfgUrl URL for Log4j configuration XML file.
     * @throws GridException Thrown in case logger can't be created.
     */
    public GridLog4jLogger(URL cfgUrl) throws GridException {
        if (cfgUrl == null)
            throw new GridException("Configuration XML file for Log4j must be specified.");

        DOMConfigurator.configure(cfgUrl);

        impl = Logger.getRootLogger();

        path = null;
    }

    /**
     * Checks if Log4j is already configured within this VM or not.
     *
     * @return {@code True} if log4j was already configured, {@code false} otherwise.
     */
    public static boolean isConfigured() {
        return Logger.getRootLogger().getAllAppenders().hasMoreElements();
    }

    /**
     * Sets level for internal log4j implementation.
     *
     * @param level Log level to set.
     */
    public void setLevel(Level level) {
        impl.setLevel(level);
    }

    /**
     * Gets {@link GridLogger} wrapper around log4j logger for the given
     * category. If category is {@code null}, then root logger is returned. If
     * category is an instance of {@link Class} then {@code (Class)ctgr).getName()}
     * is used as category name.
     *
     * @param ctgr {@inheritDoc}
     * @return {@link GridLogger} wrapper around log4j logger.
     */
    @Override public GridLog4jLogger getLogger(Object ctgr) {
        return new GridLog4jLogger(ctgr == null ? Logger.getRootLogger() :
            ctgr instanceof Class ? Logger.getLogger(((Class<?>)ctgr).getName()) :
                Logger.getLogger(ctgr.toString()));
    }

    /** {@inheritDoc} */
    @Override public void debug(String msg) {
        if (!impl.isDebugEnabled())
            warning("Logging at DEBUG level without checking if DEBUG level is enabled: " + msg);

        impl.debug(msg);
    }

    /** {@inheritDoc} */
    @Override public void info(String msg) {
        if (!impl.isInfoEnabled())
            warning("Logging at INFO level without checking if INFO level is enabled: " + msg);

        impl.info(msg);
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg) {
        impl.warn(msg);
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg, @Nullable Throwable e) {
        impl.warn(msg, e);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg) {
        impl.error(msg);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg, @Nullable Throwable e) {
        impl.error(msg, e);
    }

    /** {@inheritDoc} */
    @Override public boolean isDebugEnabled() {
        return impl.isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isInfoEnabled() {
        return impl.isInfoEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isQuiet() {
        return !isInfoEnabled() && !isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLog4jLogger.class, this);
    }
}
