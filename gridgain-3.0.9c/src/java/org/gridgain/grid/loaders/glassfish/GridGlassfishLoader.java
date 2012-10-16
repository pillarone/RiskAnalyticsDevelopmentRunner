// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.glassfish;

import com.sun.appserv.server.*;
import org.apache.commons.logging.*;
import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.logger.jcl.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import java.net.*;
import java.util.*;

/**
 * This is GridGain loader implemented as GlassFish life-cycle listener module. GlassFish
 * loader should be used to provide tight integration between GridGain and GlassFish AS.
 * Current loader implementation works on both GlassFish v1 and GlassFish v2 servers.
 * <p>
 * The following steps should be taken to configure this loader:
 * <ol>
 * <li>
 *      Add GridGain libraries in GlassFish common loader.<br/>
 *      See GlassFish <a target=_blank href="https://glassfish.dev.java.net/javaee5/docs/DG/beade.html">Class Loaders</a>.
 * </li>
 * <li>
 *      Create life-cycle listener module.<br/>
 *      Use command line or administration GUI.<br/>
 *      asadmin> create-lifecycle-module --user admin --passwordfile ../adminpassword.txt
 *      --classname "org.gridgain.grid.loaders.glassfish.GridGlassfishLoader" --property cfgFilePath="config/default-spring.xml" GridGain
 * </li>
 * </ol>
 * <p>
 * For more information consult <a target=_blank href="https://glassfish.dev.java.net/javaee5/docs/DocsIndex.html">GlassFish Project - Documentation Home Page</a>
 * and <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Configuring+and+Starting+GridGain">Configuring and Starting GridGain</a>.
 * <p>
 * <b>Note</b>: GlassFish is not shipped with GridGain. If you don't have GlassFish, you need to
 * download it separately. See <a target=_blank href="https://glassfish.dev.java.net">https://glassfish.dev.java.net</a> for
 * more information.
 * <p>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridGlassfishLoader implements LifecycleListener {
    /**
     * Configuration file path.
     */
    private String cfgFile;

    /**
     * Configuration file path variable name.
     */
    private static final String cfgFilePathParam = "cfgFilePath";

    /** */
    private ClassLoader ctxClsLdr;

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /** {@inheritDoc} */
    @Override public void handleEvent(LifecycleEvent evt) throws ServerLifecycleException {
        if (evt.getEventType() == LifecycleEvent.INIT_EVENT) {
            start((Properties)evt.getData());
        }
        else if (evt.getEventType() == LifecycleEvent.SHUTDOWN_EVENT) {
            stop();
        }
    }

    /**
     * Starts all grids with given properties.
     *
     * @param props Startup properties.
     * @throws ServerLifecycleException Thrown in case of startup fails.
     */
    @SuppressWarnings({"unchecked"})
    private void start(Properties props) throws ServerLifecycleException {
        GridLogger log = new GridJclLogger(LogFactory.getLog("GridGain"));

        if (props != null) {
            cfgFile = props.getProperty(cfgFilePathParam);
        }

        if (cfgFile == null) {
            throw new ServerLifecycleException("Failed to read property: " + cfgFilePathParam);
        }

        ctxClsLdr = Thread.currentThread().getContextClassLoader();

        // Set thread context classloader because Spring use it for loading classes.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

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
            for (GridConfiguration cfg : (Collection<GridConfiguration>)cfgMap.values()) {
                assert cfg != null;

                GridConfigurationAdapter adapter = new GridConfigurationAdapter(cfg);

                // Set Glassfish logger.
                if (cfg.getGridLogger() == null) {
                    adapter.setGridLogger(log);
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

            throw new ServerLifecycleException("Failed to start GridGain.", e);
        }
    }

    /**
     * Stops grids.
     */
    private void stop() {
        Thread.currentThread().setContextClassLoader(ctxClsLdr);

        // Stop started grids only.
        for (String name: gridNames) {
            G.stop(name, true);
        }
    }
}
