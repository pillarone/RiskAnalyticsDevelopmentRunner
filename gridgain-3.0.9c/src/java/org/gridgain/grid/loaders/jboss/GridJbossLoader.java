// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.jboss;

import org.gridgain.grid.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.logger.jboss.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jboss.system.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import java.net.*;
import java.util.*;

/**
 * This is GridGain loader implemented as JBoss service. See {@link GridJbossLoaderMBean} for
 * configuration information (according to JBoss service convention). This loader should be
 * used for tight integration with JBoss. Specifically, it integrates GridGain with JBoss's logger
 * and MBean server. This loader should be used with {@code [GRIDGAIN_HOME/config/jboss/jboss-service.xml}
 * file shipped with GridGain. See <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Configuring+and+Starting+GridGain">Configuring and Starting GridGain</a>
 * for more information.
 * <p>
 * <b>Note</b>: JBoss is not shipped with GridGain. If you don't have JBoss, you need to
 * download it separately. See <a target=_blank href="http://www.jboss.com">http://www.jboss.com</a> for
 * more information.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridLoader(description = "JBoss loader")
public class GridJbossLoader extends ServiceMBeanSupport implements GridJbossLoaderMBean {
    /** Configuration file path. */
    private String cfgFile;

    /** */
    private Collection<String> gridNames = new ArrayList<String>();

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override protected void startService() throws Exception {
        if (cfgFile == null) {
            throw new IllegalArgumentException("Failed to read property: configurationFile");
        }

        URL cfgUrl = U.resolveGridGainUrl(cfgFile);

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
                adapter.setMBeanServer(getServer());
            }

            if (cfg.getGridLogger() == null) {
                adapter.setGridLogger(new GridJbossLogger(getLog()));
            }

            Grid grid = G.start(adapter, springCtx);

            // Test if grid is not null - started properly.
            if (grid != null) {
                gridNames.add(grid.name());
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected void stopService() throws Exception {
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
        return S.toString(GridJbossLoader.class, this);
    }
}
