// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.weblogic;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.logger.java.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import weblogic.common.*;
import weblogic.logging.*;
import weblogic.server.*;
import weblogic.work.j2ee.*;
import javax.management.*;
import javax.naming.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * GridGain loader for WebLogic implemented as a pair of start and shutdown
 * classes. This is a startup class. Please consult WebLogic documentation
 * on how to configure startup classes in Weblogic. Weblogic loader should
 * be used for tight integration with Weblogic AS. Specifically, Weblogic
 * loader integrates GridGain with Weblogic logging, MBean server, and work
 * manager (<a target=_blank href="http://jcp.org/en/jsr/detail?id=237">JSR-237</a>).
 * <p>
 * The following steps should be taken to configure startup and shutdown classes:
 * <ol>
 * <li>
 *      Add Startup and Shutdown Class in admin console ({@code Environment -> Startup & Shutdown Classes -> New}).
 * </li>
 * <li>
 *      Add the following parameters for startup class:
 *      <ul>
 *      <li>Name: {@code GridWeblogicStartup}</li>
 *      <li>Classname: {@code org.gridgain.grid.loaders.weblogic.GridWeblogicStartup}</li>
 *      <li>Arguments: {@code cfgFilePath=config/default-spring.xml}</li>
 *      </ul>
 * </li>
 * <li>
 *      Add the following parameters for shutdown class:
 *      <ul>
 *      <li>Name: {@code GridWeblogicShutdown}</li>
 *      <li>Classname: {@code org.gridgain.grid.loaders.weblogic.GridWeblogicShutdown}</li>
 *      </ul>
 * </li>
 * <li>
 *      Change classpath for WebLogic server in startup script:
 *      {@code CLASSPATH="${CLASSPATH}:${GRIDGAIN_HOME}/gridgain.jar:${GRIDGAIN_HOME}/libs/"}
 * </li>
 * </ol>
 * <p>
 * For more information see
 * <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Configuring+and+Starting+GridGain">Configuring and Starting GridGain</a> and
 * <a target=_blank href="http://edocs.bea.com/wls/docs100/ConsoleHelp/taskhelp/startup_shutdown/UseStartupAndShutdownClasses.html">Startup and Shutdown Classes.</a>
 * <p>
 * <b>Note</b>: Weblogic is not shipped with GridGain. If you don't have Weblogic, you need to
 * download it separately. See <a target=_blank href="http://www.bea.com">http://www.bea.com</a> for
 * more information.
* <p>
 * <h1 class="header">How to use AOP with Bea WebLogic</h1>
 * The following steps should be taken before using {@link Gridify} annotation in applications on Bea WebLogic.
 * <h2 class="header">AspectJ AOP</h2>
 * <ol>
 * <li>
 *      Add {@code GridWeblogicStartup} with configuration described above.
 * </li>
 * <li>
 *      Classpath of the WebLogic should contain  the ${GRIDGAIN_HOME}/config/aop/aspectj folder
 *      as well as as all GridGain libraries (see above).
 * </li>
 * <li>
 *      Add JVM option {@code -javaagent:${GRIDGAIN_HOME}/libs/aspectjweaver-1.6.8.jar}
 *      (replace {@code ${GRIDGAIN_HOME}} with absolute path) into startWeblogic.{sh|bat} script
 *      which is located in "your_domain/bin" directory.
 * </li>
 * </ol>
 * <b>Note</b>: Bea Weblogic works much slower if you use AspectJ with it.
 * <p>
 * <h2 class="header">Spring AOP</h2>
 * Spring AOP framework is based on dynamic proxy implementation and doesn't require any
 * specific runtime parameters for online weaving. All weaving is on-demand and should be performed
 * by calling method {@link GridifySpringEnhancer#enhance(Object)} for the object that has method
 * with Gridify annotation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridLoader(description = "Weblogic loader")
@SuppressWarnings("deprecation")
public class GridWeblogicStartup implements GridWeblogicStartupMBean {
    /** Configuration file path variable name. */
    private static final String cfgFilePathParam = "cfgFilePath";

    /** */
    private static final String workMgrParam = "wmName";

    /** Configuration file path. */
    private String cfgFile;

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /**
     * See <a href="http://e-docs.bea.com/wls/docs100/javadocs/weblogic/common/T3StartupDef.html">
     * http://e-docs.bea.com/wls/docs100/javadocs/weblogic/common/T3StartupDef.html</a> for more
     * information.
     *
     * @param str Virtual name by which the class is registered as a {@code startupClass} in
     *      the {@code config.xml} file
     * @param params A hashtable that is made up of the name-value pairs supplied from the
     *      {@code startupArgs} property
     * @return Result string (log message).
     * @throws Exception Thrown if error occurred.
     */
    @SuppressWarnings({"unchecked", "CatchGenericClass"})
    public String startup(String str, Hashtable params) throws Exception {
        GridLogger log = new GridJavaLogger(LoggingHelper.getServerLogger());

        cfgFile = (String)params.get(cfgFilePathParam);

        if (cfgFile == null) {
            throw new IllegalArgumentException("Failed to read property: " + cfgFilePathParam);
        }

        String workMgrName = (String)params.get(workMgrParam);

        URL cfgUrl = U.resolveGridGainUrl(cfgFile);

        if (cfgUrl == null) {
            throw new ServerLifecycleException("Failed to find Spring configuration file (path provided should be " +
                "either absolute, relative to GRIDGAIN_HOME, or relative to META-INF folder): " + cfgFile);
        }

        GenericApplicationContext springCtx;

        try {
            springCtx = new GenericApplicationContext();

            XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(springCtx);

            xmlReader.loadBeanDefinitions(new UrlResource(cfgUrl));

            springCtx.refresh();
        }
        catch (BeansException e) {
            throw new ServerLifecycleException("Failed to instantiate Spring XML application context: " +
                e.getMessage(), e);
        }

        Map cfgMap;

        try {
            // Note: Spring is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new ServerLifecycleException("Failed to instantiate bean [type=" + GridConfiguration.class +
                ", err=" + e.getMessage() + ']', e);
        }

        if (cfgMap == null) {
            throw new ServerLifecycleException("Failed to find a single grid factory configuration in: " + cfgUrl);
        }

        if (cfgMap.size() == 0) {
            throw new ServerLifecycleException("Can't find grid factory configuration in: " + cfgUrl);
        }

        try {
            ExecutorService execSvc = null;

            MBeanServer mbeanSrv = null;

            for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
                assert cfg != null;

                GridConfigurationAdapter adapter = new GridConfigurationAdapter(cfg);

                // Set logger.
                if (cfg.getGridLogger() == null) {
                    adapter.setGridLogger(log);
                }

                if (cfg.getExecutorService() == null) {
                    if (execSvc == null) {
                        execSvc = workMgrName != null ? new GridThreadWorkManagerExecutor(workMgrName) :
                            new GridThreadWorkManagerExecutor(J2EEWorkManager.getDefault());
                    }

                    adapter.setExecutorService(execSvc);
                }

                if (cfg.getMBeanServer() == null) {
                    if (mbeanSrv == null) {
                        InitialContext ctx = null;

                        try {
                            ctx = new InitialContext();

                            mbeanSrv = (MBeanServer)ctx.lookup("java:comp/jmx/runtime");
                        }
                        catch (Exception e) {
                            throw new IllegalArgumentException("MBean server was not provided and failed to obtain " +
                                "Weblogic MBean server.", e);
                        }
                        finally {
                            if (ctx != null) {
                                ctx.close();
                            }
                        }
                    }

                    adapter.setMBeanServer(mbeanSrv);
                }

                Grid grid = G.start(adapter, springCtx);

                // Test if grid is not null - started properly.
                if (grid != null) {
                    gridNames.add(grid.getName());
                }
            }

            return getClass().getSimpleName() + " started successfully.";
        }
        catch (GridException e) {
            // Stop started grids only.
            for (String name: gridNames) {
                G.stop(name, true);
            }

            throw new ServerLifecycleException("Failed to start GridGain.", e);
        }
    }

    /**
     * See <a href="http://e-docs.bea.com/wls/docs100/javadocs/weblogic/common/T3StartupDef.html">
     * http://e-docs.bea.com/wls/docs100/javadocs/weblogic/common/T3StartupDef.html</a> for more
     * information.
     *
     * @param t3ServicesDef Weblogic services accessor.
     */
    public void setServices(T3ServicesDef t3ServicesDef) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public String getConfigurationFile() {
        return cfgFile;
    }

    /** {@inheritDoc} */
    @Override public void setConfigurationFile(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridWeblogicStartup.class, this);
    }
}
