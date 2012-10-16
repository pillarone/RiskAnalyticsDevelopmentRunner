// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.checkpoint.coherence;

import com.tangosol.net.*;
import com.tangosol.util.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;

import java.net.*;
import java.util.*;

/**
 * This class defines Coherence-based checkpoint SPI implementation. All checkpoints are
 * stored in distributed cache and available from all nodes in the grid. Note that every
 * node must have access to the cache. The reason of having it is because a job state
 * can be saved on one node and loaded on another (e.g., if a job gets
 * preempted on a different node after node failure).
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>Cache name (see {@link #setCacheName(String)})</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * {@code GridCoherenceCheckpointSpi} can be configured as follows:
 * <pre name="code" class="java">
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * GridCoherenceCheckpointSpi checkpointSpi = new GridCoherenceCheckpointSpi();
 *
 * // Override default cache name.
 * checkpointSpi.setCacheName("myCacheName");
 *
 * // Override default checkpoint SPI.
 * cfg.setCheckpointSpi(checkpointSpi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * {@code GridCoherenceCheckpointSpi} can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *     ...
 *     &lt;property name="checkpointSpi"&gt;
 *         &lt;bean class="org.gridgain.grid.spi.checkpoint.coherence.GridCoherenceCheckpointSpi"&gt;
 *             &lt;!-- Change to own cache name in your environment. --&gt;
 *             &lt;property name="cacheName" value="myCacheName"/&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <h1 class="header">Availability</h1>
 * <b>Note</b>: Coherence is not shipped with GridGain. If you don't have Coherence, you need to
 * download it separately. See <a target=_blank href="http://www.tangosol.com">http://www.tangosol.com</a> for
 * more information. Once installed, Coherence should be available on the classpath for
 * GridGain. If you use {@code ${GRIDGAIN_HOME}/bin/ggstart.{sh|bat}} script to start
 * a grid node you can simply add Coherence JARs to {@code ${GRIDGAIN_HOME}/bin/setenv.{sh|bat}}
 * scripts that's used to set up class path for the main scripts.
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridCoherenceCheckpointSpi extends GridSpiAdapter implements GridCheckpointSpi,
    GridCoherenceCheckpointSpiMBean {
    /**
     * Default Coherence configuration path relative to GridGain installation home folder
     * (value is {@code config/coherence/coherence.xml}).
     */
    public static final String DFLT_CFG_FILE = "config/coherence/coherence.xml";

    /** Default Coherence cache name (value is {@code gridgain.checkpoint.cache}). */
    public static final String DFLT_CACHE_NAME = "gridgain.checkpoint.cache";

    /** */
    @GridLoggerResource
    private GridLogger log;

    /** IoC configuration parameter to specify the name of the Coherence configuration file. */
    private String cfgFile = DFLT_CFG_FILE;

    /** Flag to start Coherence cache server on a dedicated daemon thread. */
    private boolean startCacheServerDaemon;

    /** Cache name. */
    private String cacheName = DFLT_CACHE_NAME;

    /** Coherence cache. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private NamedCache cache;

    /** Listener. */
    private GridCheckpointListener lsnr;

    /** Task that takes care about outdated files. */
    private GridCoherenceTimeoutTask timeoutTask;

    /** */
    private final Object mux = new Object();

    /**
     * Sets name for Coherence cache used in grid.
     * <p>
     * If not provided, default value is {@link #DFLT_CACHE_NAME}.
     *
     * @param cacheName Coherence cache name used in grid.
     */
    @GridSpiConfiguration(optional = true)
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /** {@inheritDoc} */
    @Override public String getCacheName() {
        return cacheName;
    }

    /**
     * Sets either absolute or relative to GridGain installation home folder path to Coherence XML
     * configuration file. This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link #DFLT_CFG_FILE}.
     *
     * @param cfgFile Path to Coherence configuration file.
     */
    @GridSpiConfiguration(optional = true)
    public void setConfigurationFile(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    /** {@inheritDoc} */
    @Override public String getConfigurationFile() {
        return cfgFile;
    }

    /**
     * Sets flag to start Coherence cache server on a dedicated daemon thread.
     * This configuration parameter is optional.
     * See <a href=
     * "http://download.oracle.com/otn_hosted_doc/coherence/342/com/tangosol/net/DefaultCacheServer.html">
     * DefaultCacheServer</a> for more information.
     * Note that Coherence cluster services used in SPI should be declared
     * as "autostart" in configuration with started {@code DefaultCacheServer}
     * to avoid reconnection problem between grid nodes.
     * If not provided, default value is {@code false}.
     *
     * @param startCacheServerDaemon Flag indicates whether
     *      Coherence DefaultCacheServer should be started in SPI or not.
     */
    @GridSpiConfiguration(optional = true)
    public void setStartCacheServerDaemon(boolean startCacheServerDaemon) {
        this.startCacheServerDaemon = startCacheServerDaemon;
    }

    /** {@inheritDoc} */
    @Override public boolean isStartCacheServerDaemon() {
        return startCacheServerDaemon;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assertParameter(!F.isEmpty(cacheName), "!F.isEmpty(cacheName)");

        // Start SPI start stopwatch.
        startStopwatch();

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("cacheName", cacheName));
            log.debug(configInfo("cfgFile", cfgFile));
            log.debug(configInfo("startCacheServerDaemon", startCacheServerDaemon));
        }

        if (cfgFile != null) {
            URL cfgUrl = U.resolveGridGainUrl(cfgFile);

            if (cfgUrl == null) {
                throw new GridSpiException("Invalid Coherence configuration file: " + cfgFile);
            }
            else if (log.isDebugEnabled()) {
                log.debug("Coherence configuration: " + cfgUrl);
            }

            ConfigurableCacheFactory factory = new DefaultConfigurableCacheFactory(cfgUrl.toString());

            // Specify singleton factory.
            CacheFactory.setConfigurableCacheFactory(factory);

            // Start all services that are declared as requiring an "autostart" in the configurable factory.
            DefaultCacheServer.start(factory);

            // Get coherence cache defined in configuration file.
            cache = factory.ensureCache(cacheName, getClass().getClassLoader());
        }
        else {
            // Get coherence cache.
            cache = CacheFactory.getCache(cacheName);
        }

        if (cache == null) {
            throw new GridSpiException("Failed to obtain Coherence Cache for cacheName:" + cacheName);
        }

        cache.addMapListener(new MapListener() {
            @Override public void entryInserted(MapEvent evt) { /* No-op. */ }
            @Override public void entryUpdated(MapEvent evt) { /* No-op. */ }

            @Override public void entryDeleted(MapEvent evt) {
                Object v = evt.getOldValue();

                if (v instanceof GridCoherenceCheckpointData) {
                    GridCheckpointListener tmp = lsnr;

                    if (tmp != null) {
                        tmp.onCheckpointRemoved((String)evt.getKey());
                    }
                }
            }
        });

        // Separate thread used because Coherence don't notify listener about expired entries.
        // Coherence remove expired entries from cache w/o notification MapListener#entryDeleted().
        timeoutTask = new GridCoherenceTimeoutTask(gridName, log);

        timeoutTask.setCheckpointListener(lsnr);

        timeoutTask.start();

        registerMBean(gridName, this, GridCoherenceCheckpointSpiMBean.class);

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        if (timeoutTask != null) {
            GridUtils.interrupt(timeoutTask);
            GridUtils.join(timeoutTask, log);
        }

        unregisterMBean();

        cache = null;

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public byte[] loadCheckpoint(String key) throws GridSpiException {
        assert key != null;

        GridCoherenceCheckpointData data;

        synchronized (mux) {
            data = (GridCoherenceCheckpointData)cache.get(key);
        }

        if (data != null) {
            return data.getExpireTime() == 0 ? data.getState() : data.getExpireTime() >
                System.currentTimeMillis() ? data.getState() : null;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public boolean saveCheckpoint(String key, byte[] state, long timeout, boolean override)
        throws GridSpiException {
        assert key != null;

        long expTime = 0;

        if (timeout > 0) {
            expTime = System.currentTimeMillis() + timeout;

            if (expTime < 0) {
                expTime = Long.MAX_VALUE;
            }
        }

        synchronized (mux) {
            if (!override && cache.containsKey(key)) {
                return false;
            }

            GridCoherenceCheckpointData data = new GridCoherenceCheckpointData(state, expTime, key);

            cache.put(key, data, timeout);
        }

        if (timeout > 0) {
            timeoutTask.add(new GridCoherenceTimeData(expTime, key));
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean removeCheckpoint(String key) {
        assert key != null;

        timeoutTask.remove(key);

        synchronized (mux) {
            // Note, that at this point Coherence will directly call MapListener about deleted entry.
            return cache.remove(key) != null;
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public void setCheckpointListener(GridCheckpointListener lsnr) {
        this.lsnr = lsnr;

        if (timeoutTask != null) {
            timeoutTask.setCheckpointListener(lsnr);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceCheckpointSpi.class, this);
    }
}
