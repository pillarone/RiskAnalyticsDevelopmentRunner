// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.tomcat;

import org.apache.catalina.*;
import org.apache.catalina.mbeans.*;
import org.apache.juli.logging.*;
import org.gridgain.grid.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.logger.tomcat.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import java.net.*;
import java.util.*;

/**
 * This is GridGain loader implemented as Tomcat {@code LifecycleListener}. Tomcat
 * loader should be used to provide tight integration between GridGain and Tomcat.
 * Specifically, Tomcat loader integrates GridGain with Tomcat logging and MBean server.
 * <p>
 * The following steps should be taken to configure this loader:
 * <ol>
 *      <li>Add GridGain libraries in Tomcat common loader.
 *          Add in file <tt>${TOMCAT_HOME}/conf/catalina.properties</tt> for property <tt>common.loader</tt>
 *          the following <tt>${GRIDGAIN_HOME}/gridgain.jar,${GRIDGAIN_HOME}/libs/*.jar</tt>
 *          (replace <tt>${GRIDGAIN_HOME}</tt> with absolute path).
 *      </li>
 *      <li> Add GridGain LifeCycle Listener in <tt>${TOMCAT_HOME}/conf/server.xml</tt>
 *          <pre name="code" class="xml">
 *              &lt;Listener className="org.gridgain.grid.loaders.tomcat.GridTomcatLoader"
 *                  configurationFile="config/default-spring.xml"/&gt;
 *          </pre>
 *      </li>
 * </ol>
 * <p>
 * <b>Note</b>: Tomcat is not shipped with GridGain. If you don't have Tomcat, you need to
 * download it separately. See <a target=_blank href="http://tomcat.apache.org/">http://tomcat.apache.org/</a> for
 * more information.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridLoader(description = "Tomcat loader")
public class GridTomcatLoader implements LifecycleListener {
    /** */
    private static Log log = LogFactory.getLog(GridTomcatLoader.class);

    /** Configuration file path. */
    private String cfgFile;

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /** {@inheritDoc} */
    @Override public void lifecycleEvent(LifecycleEvent evt) {
        Object data = evt.getData();
        String type = evt.getType();
        Lifecycle lifecycle = evt.getLifecycle();

        if (log.isDebugEnabled()) {
            log.debug("Received event [type=" + type +
                ", dataCls=" + (data == null ? null : data.getClass().getName()) + ", data=" + data +
                ", lifecycleCls=" + (lifecycle == null ? null : lifecycle.getClass().getName()) +
                ", lifecycle=" + lifecycle + ']');
        }

        if (Lifecycle.START_EVENT.equals(evt.getType())) {
            try {
                startService();
            }
            catch (Exception e) {
                log.error("Failed to start GridGain.", e);
            }
        }
        else if (Lifecycle.STOP_EVENT.equals(evt.getType())) {
            stopService();
        }

    }

    /**
     * Starts service.
     *
     * @throws GridException Thrown in case of any GridGain error.
     * @throws IllegalArgumentException Thrown in case of invalid arguments.
     */
    @SuppressWarnings({"unchecked"})
    private void startService() throws GridException, IllegalArgumentException {
        if (cfgFile == null) {
            throw new IllegalArgumentException("Failed to read property: configurationFile");
        }

        URL cfgUrl = GridUtils.resolveGridGainUrl(cfgFile);

        if (cfgUrl == null) {
            throw new GridException("Failed to find Spring configuration file (path provided should be " +
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
            throw new GridException("Failed to instantiate Spring XML application context: " + e.getMessage(), e);
        }

        Map cfgMap;

        try {
            // Note: Spring is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate bean [type=" + GridConfiguration.class + ", err=" +
                e.getMessage() + ']', e);
        }

        if (cfgMap == null) {
            throw new GridException("Failed to find a single grid factory configuration in: " + cfgUrl);
        }

        if (cfgMap.isEmpty()) {
            throw new GridException("Can't find grid factory configuration in: " + cfgUrl);
        }

        for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
            assert cfg != null;

            GridConfigurationAdapter adapter = new GridConfigurationAdapter(cfg);

            if (cfg.getMBeanServer() == null) {
                adapter.setMBeanServer(MBeanUtils.createServer());
            }

            if (cfg.getGridLogger() == null) {
                adapter.setGridLogger(new GridTomcatLogger(log));
            }

            Grid grid = G.start(adapter, springCtx);

            // Test if grid is not null - started properly.
            if (grid != null) {
                gridNames.add(grid.name());
            }
        }
    }

    /**
     * Stops service.
     */
    private void stopService() {
        // Stop started grids only.
        for (String name: gridNames) {
            G.stop(name, true);
        }
    }

    /**
     * Gets previously set Spring configuration file.
     *
     * @return Previously set Spring configuration file.
     */
    public String getConfigurationFile() {
        return cfgFile;
    }

    /**
     * Sets Spring XML configuration file path.
     *
     * @param cfgFile Configuration file path.
     */
    public void setConfigurationFile(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTomcatLoader.class, this);
    }
}
