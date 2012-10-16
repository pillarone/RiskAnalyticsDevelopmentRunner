// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.json.*;
import org.jetbrains.annotations.*;
import javax.management.*;
import java.io.*;
import java.text.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * This class provides convenient adapter for SPI implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridSpiAdapter implements GridSpi, GridSpiManagementMBean, GridSpiJsonConfigurable {
    /** System line separator. */
    private static final String NL = System.getProperty("line.separator");

    /** Instance of SPI annotation. */
    private GridSpiInfo spiAnn;

    /** */
    private ObjectName spiMBean;

    /** SPI start timestamp. */
    private long startTstamp;

    /** */
    @GridLoggerResource
    private GridLogger log;

    /** */
    @GridMBeanServerResource
    private MBeanServer jmx;

    /** */
    @GridHomeResource
    private String ggHome;

    /** */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** SPI name. */
    private String name;

    /** Grid SPI context. */
    private GridSpiContext spiCtx = new GridDummySpiContext(null);

    /** Discovery listener. */
    private GridLocalEventListener paramsLsnr;

    /**
     * Creates new adapter and initializes it from the current (this) class.
     * SPI name will be initialized to the simple name of the class
     * (see {@link Class#getSimpleName()}).
     */
    protected GridSpiAdapter() {
        for (Class<?> cls = getClass(); cls != null; cls = cls.getSuperclass())
            if ((spiAnn = cls.getAnnotation(GridSpiInfo.class)) != null)
                break;

        assert spiAnn != null : "Every SPI must have @GridSpiInfo annotation.";

        name = U.getSimpleName(getClass());
    }

    /**
     * Starts startup stopwatch.
     */
    protected void startStopwatch() {
        startTstamp = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override public final String getAuthor() {
        return spiAnn.author();
    }

    /** {@inheritDoc} */
    @Override public final String getVendorUrl() {
        return spiAnn.url();
    }

    /** {@inheritDoc} */
    @Override public final String getVendorEmail() {
        return spiAnn.email();
    }

    /** {@inheritDoc} */
    @Override public final String getVersion() {
        return spiAnn.version();
    }

    /** {@inheritDoc} */
    @Override public final String getStartTimestampFormatted() {
        return DateFormat.getDateTimeInstance().format(new Date(startTstamp));
    }

    /** {@inheritDoc} */
    @Override public final String getUpTimeFormatted() {
        return X.timeSpan2HMSM(getUpTime());
    }

    /** {@inheritDoc} */
    @Override public final long getStartTimestamp() {
        return startTstamp;
    }

    /** {@inheritDoc} */
    @Override public final long getUpTime() {
        return startTstamp == 0 ? 0 : System.currentTimeMillis() - startTstamp;
    }

    /** {@inheritDoc} */
    @Override public UUID getLocalNodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @Override public final String getGridGainHome() {
        return ggHome;
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /**
     * Sets SPI name.
     *
     * @param name SPI name.
     */
    @GridSpiConfiguration(optional = true)
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(final GridSpiContext spiCtx) throws GridSpiException {
        assert spiCtx != null;

        this.spiCtx = spiCtx;

        getSpiContext().addLocalEventListener(paramsLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                GridNode node = spiCtx.node(((GridDiscoveryEvent)evt).eventNodeId());

                if (node != null)
                    checkConfigurationConsistency(node);
            }
        }, EVT_NODE_JOINED);

        for (GridNode node : getSpiContext().remoteNodes())
            checkConfigurationConsistency(node);
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        if (spiCtx != null && paramsLsnr != null)
            spiCtx.removeLocalEventListener(paramsLsnr);

        GridNode locNode = spiCtx == null ? null : spiCtx.localNode();

        // Set dummy no-op context.
        spiCtx = new GridDummySpiContext(locNode);
    }

    /**
     * This method returns SPI internal instances that need to be injected as well.
     * Usually these will be instances provided to SPI externally by user, e.g. during
     * SPI configuration.
     * 
     * @return Internal SPI objects that also need to be injected.
     */
    public Collection<Object> injectables() {
        return Collections.emptyList();
    }

    /**
     * Gets SPI context.
     *
     * @return SPI context.
     */
    protected GridSpiContext getSpiContext() {
        return spiCtx;
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        return Collections.emptyMap();
    }

    /**
     * Throws exception with uniform error message if given parameter's assertion condition
     * is {@code false}.
     *
     * @param cond Assertion condition to check.
     * @param condDesc Description of failed condition. Note that this description should include
     *      JavaBean name of the property (<b>not</b> a variable name) as well condition in
     *      Java syntax like, for example:
     *      <pre name="code" class="java">
     *      ...
     *      assertParameter(dirPath != null, "dirPath != null");
     *      ...
     *      </pre>
     *      Note that in case when variable name is the same as JavaBean property you
     *      can just copy Java condition expression into description as a string.
     * @throws GridSpiException Thrown if given condition is {@code false}
     */
    protected final void assertParameter(boolean cond, String condDesc) throws GridSpiException {
        if (!cond)
            throw new GridSpiException("SPI parameter failed condition check: " + condDesc);
    }

    /**
     * Gets uniformly formatted message for SPI start.
     *
     * @return Uniformly formatted message for SPI start.
     * @throws GridSpiException If SPI is missing {@link GridSpiInfo} annotation.
     */
    protected final String startInfo() throws GridSpiException {
        GridSpiInfo ann = getClass().getAnnotation(GridSpiInfo.class);

        if (ann == null)
            throw new GridSpiException("@GridSpiInfo annotation is missing for the SPI.");

        return "SPI started ok [startMs=" + getUpTime() + ", spiMBean=" + spiMBean + ']';
    }

    /**
     * Gets uniformly format message for SPI stop.
     *
     * @return Uniformly format message for SPI stop.
     */
    protected final String stopInfo() { return "SPI stopped ok."; }

    /**
     * Gets uniformed string for configuration parameter.
     *
     * @param name Parameter name.
     * @param value Parameter value.
     * @return Uniformed string for configuration parameter.
     */
    protected final String configInfo(String name, Object value) {
        assert name != null;

        return "Using parameter [" + name + '=' + value + ']';
    }

    /**
     * @param msg Error message.
     * @param locVal Local node value.
     * @param rmtVal Remote node value.
     * @return Error text.
     */
    private static String format(String msg, Object locVal, Object rmtVal) {
        return msg + NL +
            ">>> => Local node:  " + locVal + NL +
            ">>> => Remote node: " + rmtVal + NL;
    }

    /**
     * Registers SPI MBean. Note that SPI can only register one MBean.
     *
     * @param gridName Grid name. If null, then name will be empty.
     * @param impl MBean implementation.
     * @param mbeanItf MBean interface (if {@code null}, then standard JMX
     *    naming conventions are used.
     * @param <T> Type of the MBean
     * @throws GridSpiException If registration failed.
     */
    protected final <T extends GridSpiManagementMBean> void registerMBean(String gridName, T impl, Class<T> mbeanItf)
        throws GridSpiException {
        assert mbeanItf == null || mbeanItf.isInterface();
        assert jmx != null;

        try {
            spiMBean = U.registerMBean(jmx, gridName, "SPIs", getName(), impl, mbeanItf);

            if (log.isDebugEnabled())
                log.debug("Registered SPI MBean: " + spiMBean);
        }
        catch (JMException e) {
            throw new GridSpiException("Failed to register SPI MBean: " + spiMBean, e);
        }
    }

    /**
     * Unregisters MBean.
     *
     * @throws GridSpiException If bean could not be unregistered.
     */
    protected final void unregisterMBean() throws GridSpiException {
        // Unregister SPI MBean.
        if (spiMBean != null) {
            assert jmx != null;

            try {
                jmx.unregisterMBean(spiMBean);

                if (log.isDebugEnabled())
                    log.debug("Unregistered SPI MBean: " + spiMBean);
            }
            catch (JMException e) {
                throw new GridSpiException("Failed to unregister SPI MBean: " + spiMBean, e);
            }
        }
    }

    /**
     * Checks remote node SPI configuration and prints warnings if necessary.
     *
     * @param node Remote node.
     */
    private void checkConfigurationConsistency(GridNode node) {
        assert node != null;

        /*** Don't compare SPIs from different virtual grids. ***/

        String locGridName = getSpiContext().localNode().attribute(GridNodeAttributes.ATTR_GRID_NAME);
        String rmtGridName = node.attribute(GridNodeAttributes.ATTR_GRID_NAME);

        if (!F.eq(locGridName, rmtGridName)) {
            if (log.isDebugEnabled())
                log.debug("Skip consistency check for SPIs from different grids [locGridName=" + locGridName +
                    ", rmtGridName=" + rmtGridName + ']');

            return;
        }

        List<String> attrs = getConsistentAttributeNames();

        String clsAttr = createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS);
        String verAttr = createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER);

        /*
         * Optional SPI means that we should not print warning if SPIs are different but
         * still need to compare attributes if SPIs are the same.
         */
        boolean isSpiOptional = !attrs.contains(clsAttr);
        boolean isSpiConsistent = false;

        String name = getName();

        SB sb = new SB();

        /*
         * If there are any attributes do compare class and version
         * (do not print warning for the optional SPIs).
         */
        if (!attrs.isEmpty()) {
            /* Check SPI class and version. */
            String locCls = getSpiContext().localNode().attribute(clsAttr);
            String rmtCls = node.attribute(clsAttr);

            String locVer = getSpiContext().localNode().attribute(verAttr);
            String rmtVer = node.attribute(verAttr);

            assert locCls != null: "Local SPI class name attribute not found: " + clsAttr;
            assert locVer != null: "Local SPI version attribute not found: " + verAttr;

            if (!isSpiOptional) {
                if (rmtCls == null)
                    sb.a(format(">>> Remote SPI is not configured: " + name, locCls, rmtCls));
                else if (!locCls.equals(rmtCls))
                    sb.a(format(">>> Remote SPI is of different type: " + name, locCls, rmtCls));
                else if (!F.eq(rmtVer, locVer)) {
                    if (rmtVer.contains("x.x") || locVer.contains("x.x")) {
                        if (log.isDebugEnabled())
                            log.debug("Skip SPI version check for 'x.x' development version in: " + name);
                    }
                    else
                        sb.a(format(">>> Remote SPI is of different version: " + name, locVer, rmtVer));
                }
                else
                    isSpiConsistent = true;
            }
        }

        // It makes no sense to compare inconsistent SPIs attributes.
        if (isSpiConsistent)
            // Process all SPI specific attributes.
            for (String attr: attrs) {
                // Ignore class and version attributes processed above.
                if (!attr.equals(clsAttr) && !attr.equals(verAttr)) {
                    // This check is considered as optional if no attributes
                    Object rmtVal = node.attribute(attr);
                    Object locVal = getSpiContext().localNode().attribute(attr);

                    if (locVal == null && rmtVal == null)
                        continue;

                    if (locVal == null || rmtVal == null || !locVal.equals(rmtVal))
                        sb.a(format(">>> Remote node has different " + getName() + " SPI attribute " +
                            attr, locVal, rmtVal));
                }
        }

        if (sb.length() > 0)
            U.warn(log, NL + NL +
                ">>> +-------------------------------------------------------------------+" + NL +
                ">>> + Courtesy notice that joining node has inconsistent configuration. +" + NL +
                ">>> + Ignore this message if you are sure that this is done on purpose. +" + NL +
                ">>> + ------------------------------------------------------------------+" + NL +
                ">>> Remote Node ID: " + node.id().toString().toUpperCase() + NL + sb);
    }

    /**
     * Returns back a list of attributes that should be consistent
     * for this SPI. Consistency means that remote node has to
     * have the same attribute with the same value.
     *
     * @return List or attribute names.
     */
    protected List<String> getConsistentAttributeNames() {
        return Collections.emptyList();
    }

    /**
     * Creates new name for the given attribute. Name contains
     * SPI name prefix.
     *
     * @param attrName SPI attribute name.
     * @return New name with SPI name prefix.
     */
    protected String createSpiAttributeName(String attrName) {
        return U.spiAttribute(this, attrName);
    }

    /** {@inheritDoc} */
    @GridSpiConfiguration(optional = true)
    @Override public void setJson(String json) {
        assert json != null;

        try {
            GridJsonDeserializer.inject(this, json);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /**
     * Temporarily SPI context.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private static class GridDummySpiContext implements GridSpiContext {
        /** */
        private final GridNode locNode;

        /**
         * Create temp SPI context.
         *
         * @param locNode Local node.
         */
        GridDummySpiContext(GridNode locNode) {
            this.locNode = locNode;
        }

        /** {@inheritDoc} */
        @Override public void addLocalEventListener(GridLocalEventListener lsnr, int... types) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @SuppressWarnings("deprecation")
        @Override public void addMessageListener(GridMessageListener lsnr, String topic) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public void recordEvent(GridEvent evt) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public void registerPort(int port, GridPortProtocol protocol) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public void deregisterPort(int port, GridPortProtocol protocol) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public boolean isEnterprise() {
            return U.isEnterprise();
        }

        /** {@inheritDoc} */
        @Override public void deregisterPorts() {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public <K, V> V get(String cacheName, K key) throws GridException {
            return null;
        }

        /** {@inheritDoc} */
        @Override public <K, V> V put(String cacheName, K key, V val, long ttl) throws GridException {
            return null;
        }

        /** {@inheritDoc} */
        @Override public <K, V> V putIfAbsent(String cacheName, K key, V val, long ttl) throws GridException {
            return null;
        }

        /** {@inheritDoc} */
        @Override public <K, V> V remove(String cacheName, K key) throws GridException {
            return null;
        }

        /** {@inheritDoc} */
        @Override public <K> boolean containsKey(String cacheName, K key) {
            return false;
        }

        /** {@inheritDoc} */
        @Override public void writeToSwap(String spaceName, Object key, @Nullable Object val) throws GridException {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public <T> T readFromSwap(String spaceName, Object key) throws GridException {
            return null;
        }

        /** {@inheritDoc} */
        @Override public boolean removeFromSwap(String spaceName, Object key) throws GridException {
            return false;
        }

        /** {@inheritDoc} */
        @Override public Collection<GridNode> nodes() {
            return  locNode == null  ? Collections.<GridNode>emptyList() : Collections.singletonList(locNode);
        }

        /** {@inheritDoc} */
        @Override public GridNode localNode() {
            return locNode;
        }

        /** {@inheritDoc} */
        @Override @Nullable
        public GridNode node(UUID nodeId) {
            return null;
        }

        /** {@inheritDoc} */
        @Override public Collection<GridNode> remoteNodes() {
            return Collections.emptyList();
        }

        /** {@inheritDoc} */
        @Override public Collection<GridNode> topology(GridTaskSession taskSes, Collection<? extends GridNode> grid) {
            return Collections.emptyList();
        }

        /** {@inheritDoc} */
        @Override public boolean pingNode(UUID nodeId) {
            return locNode != null && nodeId.equals(locNode.id());
        }

        /** {@inheritDoc} */
        @Override public boolean removeLocalEventListener(GridLocalEventListener lsnr) {
            return false;
        }

        /** {@inheritDoc} */
        @SuppressWarnings("deprecation")
        @Override public boolean removeMessageListener(GridMessageListener lsnr, String topic) {
            return false;
        }

        /** {@inheritDoc} */
        @Override public void send(GridNode node, Serializable msg, String topic) {
            /* No-op. */
        }

        /** {@inheritDoc} */
        @Override public void send(Collection<? extends GridNode> nodes, Serializable msg, String topic) {
            /* No-op. */
        }
    }
}
