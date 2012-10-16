// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.websphere;

import com.ibm.websphere.management.*;
import com.ibm.websphere.runtime.*;
import com.ibm.ws.asynchbeans.*;
import com.ibm.wsspi.asynchbeans.*;
import org.apache.commons.logging.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.spring.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.logger.jcl.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import javax.management.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This is GridGain loader implemented as Websphere custom service (MBean). Websphere
 * loader should be used to provide tight integration between GridGain and Websphere AS.
 * Specifically, Websphere loader integrates GridGain with Websphere logging, MBean server and work
 * manager (<a target=_blank href="http://jcp.org/en/jsr/detail?id=237">JSR-237</a>).
 * <p>
 * The following steps should be taken to configure this loader:
 * <ol>
 * <li>
 *      Add CustomService in administration console ({@code Application Servers -> server1 -> Custom Services -> New}).
 * </li>
 * <li>
 *      Add custom property for this service: {@code cfgFilePath=config/default-spring.xml}.
 * </li>
 * <li>
 *      Add the following parameters:
 *      <ul>
 *      <li>Classname: {@code org.gridgain.grid.loaders.websphere.GridWebsphereLoader}</li>
 *      <li>Display Name: {@code GridGain}</li>
 *      <li>
 *          Classpath (replace {@code ${GRIDGAIN_HOME}} with absolute path):
 *          {@code ${GRIDGAIN_HOME}/gridgain.jar:${GRIDGAIN_HOME}/libs/}
 *      </li>
 *      </ul>
 * </li>
 * </ol>
 * <p>
 * For more information consult <a target=_blank href="http://publib.boulder.ibm.com/infocenter/wasinfo/v6r1/index.jsp?topic=/com.ibm.websphere.base.doc/info/aes/ae/trun_customservice.html">Developing Custom Services</a>
 * and <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Configuring+and+Starting+GridGain">Configuring and Starting GridGain</a>.
 * <p>
 * <b>Note</b>: Websphere is not shipped with GridGain. If you don't have Websphere, you need to
 * download it separately. See <a target=_blank href="http://www.ibm.com/software/websphere/">http://www.ibm.com/software/websphere/</a> for
 * more information.
 * <p>
 * <h1 class="header">How to use AOP with Websphere</h1>
 * The following steps should be taken before using {@link Gridify} annotation in applications on Websphere.
 * <h2 class="header">AspectJ AOP</h2>
 * <ol>
 * <li>
 *      Add {@code GridWebsphereLoader} with configuration described above.
 * </li>
 * <li>
 *      Classpath text field for Custom Service ({@code GridWebsphereLoader}) should contain
 *      the {@code ${GRIDGAIN_HOME}/config/aop/aspectj} folder..
 * </li>
 * <li>
 *      Add JVM option {@code -javaagent:${GRIDGAIN_HOME}/libs/aspectjweaver-1.6.8.jar}
 *      (replace {@code ${GRIDGAIN_HOME}} with absolute path) in admin console
 *      ({@code Application servers > server1 > Process Definition > Java Virtual Machine} text field
 *      {@code Generic JVM arguments})
 * </li>
 * <li>
 *      Add java permission for GridGain classes in {@code server.policy} file.
 *      For example, in file {@code /opt/IBM/WebSphere/AppServer/profiles/AppSrv01/properties/server.policy}
 * <pre name="code" class="java">
 * grant codeBase "file:/home/link/svnroot/gridgain/work/libs/-" {
 *     // Allow everything for now
 *     permission java.security.AllPermission;
 * };
 * </pre>
 * </ol>
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
@GridLoader(description = "Websphere loader")
public class GridWebsphereLoader implements GridWebsphereLoaderMBean, CustomService {
    /** Configuration file path. */
    private String cfgFile;

    /** Configuration file path variable name. */
    private static final String cfgFilePathParam = "cfgFilePath";

    /** */
    private static final String workMgrParam = "wmName";

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    public void initialize(Properties properties) throws Exception {
        GridLogger log = new GridJclLogger(LogFactory.getLog("GridGain"));

        cfgFile = properties.getProperty(cfgFilePathParam);

        if (cfgFile == null) {
            throw new IllegalArgumentException("Failed to read property: " + cfgFilePathParam);
        }

        String workMgrName = properties.getProperty(workMgrParam);

        URL cfgUrl = U.resolveGridGainUrl(cfgFile);

        if (cfgUrl == null) {
            throw new IllegalArgumentException("Failed to find Spring configuration file (path provided should be " +
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
            throw new IllegalArgumentException("Failed to instantiate Spring XML application context: " +
                e.getMessage(), e);
        }

        Map cfgMap;

        try {
            // Note: Spring is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new IllegalArgumentException("Failed to instantiate bean [type=" + GridConfiguration.class +
                ", err=" + e.getMessage() + ']', e);
        }

        if (cfgMap == null) {
            throw new IllegalArgumentException("Failed to find a single grid factory configuration in: " + cfgUrl);
        }

        if (cfgMap.size() == 0) {
            throw new IllegalArgumentException("Can't find grid factory configuration in: " + cfgUrl);
        }

        try {
            ExecutorService execSvc = null;

            MBeanServer mbeanSrvr = null;

            for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
                assert cfg != null;

                GridConfigurationAdapter adapter = new GridConfigurationAdapter(cfg);

                // Set WebSphere logger.
                if (cfg.getGridLogger() == null) {
                    adapter.setGridLogger(log);
                }

                if (cfg.getExecutorService() == null) {
                    if (execSvc == null) {
                        if (workMgrName != null) {
                            execSvc = new GridThreadWorkManagerExecutor(workMgrName);
                        }
                        else {
                            // Obtain/create singleton.
                            J2EEServiceManager j2eeMgr = J2EEServiceManager.getSelf();

                            // Start it if was not started before.
                            j2eeMgr.start();

                            // Create new configuration.
                            CommonJWorkManagerConfiguration workMgrCfg = j2eeMgr.createCommonJWorkManagerConfiguration();

                            workMgrCfg.setName("GridGain");
                            workMgrCfg.setJNDIName("wm/gridgain");

                            // Set worker.
                            execSvc = new GridThreadWorkManagerExecutor(j2eeMgr.getCommonJWorkManager(workMgrCfg));
                        }
                    }

                    adapter.setExecutorService(execSvc);
                }

                if (cfg.getMBeanServer() == null) {
                    if (mbeanSrvr == null) {
                        mbeanSrvr = AdminServiceFactory.getMBeanFactory().getMBeanServer();
                    }

                    adapter.setMBeanServer(mbeanSrvr);
                }

                Grid grid = G.start(adapter, springCtx);

                // Test if grid is not null - started properly.
                if (grid != null) {
                    gridNames.add(grid.getName());
                }
            }
        }
        catch (GridException e) {
            // Stop started grids only.
            for (String name: gridNames) {
                G.stop(name, true);
            }

            throw new IllegalArgumentException("Failed to start GridGain.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public void shutdown() throws Exception {
        // Stop started grids only.
        for (String name: gridNames) {
            G.stop(name, true);
        }
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
        return S.toString(GridWebsphereLoader.class, this);
    }
}
