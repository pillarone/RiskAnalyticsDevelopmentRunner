// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud.jvm;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.cloud.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridCloudResourceType.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;
import static org.gridgain.grid.spi.cloud.GridCloudSpiResourceAction.*;

/**
 * This class defines local JVM-based implementation for {@link GridCloudSpi}. It supports
 * three actions - starting and stopping GridGain nodes and stopping entire cloud
 * (see below for details).
 *
 * <p>
 * This class is intended for testing purposes. It is ideal for developing and testing
 * cloud-related functionality as all nodes will be spawn locally in-VM meaning that they can be
 * debugged locally and without wasting paid resources on actual clouds such as
 * Amazon EC2 or Rackspace.
 * <p>
 * One caveat of this SPI implementation is that all nodes started by this SPI will have
 * unique grid {@link Grid#name() name}. In most cases it should not affect the user's
 * logic.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>Cloud ID (see {@link #setCloudId(String)})</li>
 * <li>State check frequency (see {@link #setStateCheckFrequency(long)})</li>
 * </ul>
 * <h1 class="header">Commands</h1>
 * Following commands are supported:
 * <table class="doctable">
 * <tr>
 *      <th>Command</th>
 *      <th>Parameter</th>
 *      <th>Description</th>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Add Node(s)</b>
 *          <p>
 *          Adds one or more GridGain nodes
 *          started locally in-VM. Note that each node will
 *          have unique {@link Grid#name() grid name}.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link GridJvmCloudSpi#START_NODES_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater than zero indicating how many nodes to start.
 *          If less or equal to zero exception is thrown.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          Value should be empty or {@code null}. if not empty or {@code null} then
 *          collection will be ignored with a warning.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not SPI handles the following keys:
 *          <ul>
 *              <li>{@link GridJvmCloudSpi#GRID_CFG_PATH_KEY} - Grid configuration path for newly started node,
 *                  if not provided node will be started with default configuration.</li>
 *              <li>{@link GridJvmCloudSpi#CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command, but makes no actual
 *                  changes (does not start any nodes).></li>
 *          </ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Remove Node(s)</b>
 *          <p>
 *          Removes one or more GridGain nodes previously
 *          started locally in-VM.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link GridJvmCloudSpi#STOP_NODES_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater or equal to zero. The value indicates how many nodes to stop.
 *          See below for further details.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          If {@code null} or empty collection the implementation will randomly select
 *          nodes to stop (in this case {@link GridCloudCommand#number()} should return value greater than zero;
 *          if not then exception is thrown).
 *          <p>
 *          Otherwise, collection of resources with {@link GridCloudResourceType#CLD_NODE}
 *          type and belonging to the SPI&quot;s cloud is expected and nodes from this collection will be removed
 *          (in this case parameter {@link GridCloudCommand#number()} should be zero or equal to
 *          {@link GridCloudCommand#resources()} size; if not then exception is thrown)
 *          <p>
 *          If cloud has less nodes than it is required to stop by a command then exception is thrown.
 *          <p>
 *          If at least on element in the collection is not of {@link GridCloudResourceType#CLD_NODE} or does not
 *          belong to SPI's exception is thrown.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link GridJvmCloudSpi#CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not stop any nodes).</li>
 *          </ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Stop Cloud</b>
 *          <p>
 *          Removes all GridGain nodes previously
 *          started locally in-VM. If cloud is empty this command is no-op.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link GridJvmCloudSpi#STOP_CLOUD_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link GridJvmCloudSpi#CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not stop any nodes).</li>
 *          <ul>
 *      </td>
 * </tr>
 * </table>
 * <p>
 * If command's action is not recognized exception is thrown by SPI.
 *
 * <h2 class="header">Java Example</h2>
 * GridJvmCloudSpi can be configured as follows:
 * <pre name="code" class="java">
 * GridJvmCloudSpi cloudSpi = new GridJvmCloudSpi();
 *
 * // Override default cloud ID.
 * cloudSpi.setCloudId("myCloud");
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default cloud SPI.
 * cfg.setCloudSpi(cloudSpi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridJvmCloudSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *     ...
 *     &lt;property name="cloudSpi"&gt;
 *         &lt;bean class="org.gridgain.grid.spi.cloud.jvm.GridJvmCloudSpi"&gt;
 *             &lt;property name="cloudId" value="myCloud"/&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCloudSpi
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridJvmCloudSpi extends GridSpiAdapter implements GridCloudSpi, GridJvmCloudSpiMBean {
    /** Start nodes action */
    public static final String START_NODES_ACT = "gridgain.jvm.cloud.start.nodes.action";

    /** Stop nodes action */
    public static final String STOP_NODES_ACT = "gridgain.jvm.cloud.stop.nodes.action";

    /** Stop entire cloud action */
    public static final String STOP_CLOUD_ACT = "gridgain.jvm.cloud.stop.clouds.action";

    /** Grid configuration path command parameter. */
    public static final String GRID_CFG_PATH_KEY = "gridgain.jvm.cloud.cfg.path";

    /** Dry-run command parameter. */
    public static final String CMD_DRY_RUN_KEY = "gridgain.jvm.cloud.cmd.dry.run";

    /** Cloud ID prefix for unnamed clouds. */
    public static final String CLOUD_ID_PREFIX = "jvm-cloud-";

    /** Default state check frequency in milliseconds. */
    public static final long DFLT_STATE_CHECK_FREQ = 3000;

    /** JVM cloud ID node attribute. */
    public static final String ATTR_CLOUD_ID = "gridgain.jvm.cloud.id";

    /** Grid node name prefix. */
    public static final String GRID_NODE_NAME_PREF = "gridgain.jvm.cloud.node.";

    /** Default configuration path. */
    public static final String DFLT_CFG_PATH = "config/default-spring.xml";

    /** Cloud index for unnamed clouds. */
    private static AtomicInteger cloudIdGen = new AtomicInteger();

    /** Cloud parameter key for last cloud update time. */
    private static final String CLOUD_LAST_UPD_TIME_PARAM = "last-cloud-upd-time";

    /** Cloud ID. */
    private String cloudId = CLOUD_ID_PREFIX + cloudIdGen.incrementAndGet();

    /** Frequency to check cloud state change. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long stateCheckFreq = DFLT_STATE_CHECK_FREQ;

    /** Cloud SPI listener. */
    private volatile GridCloudSpiListener lsnr;

    /** Grid name. */
    private String gridName;

    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Cloud commands to execute. */
    private Map<UUID, GridCloudCommand> cmds = new LinkedHashMap<UUID, GridCloudCommand>();

    /** Mutex. */
    private final Object mux = new Object();

    /** Cloud control thread. */
    private CloudControlThread ctrlThread;

    /** Current cloud snapshot. */
    private GridCloudSpiSnapshot lastSnp;

    /**
     * Loads grid configuration from path.
     *
     * @param cfgPath Grid configuration path.
     * @return Grid configuration.
     * @throws GridException Thrown if any exception occurs.
     */
    @SuppressWarnings({"unchecked"})
    private GridConfiguration loadConfiguration(String cfgPath) throws GridException {
        String path = !F.isEmpty(cfgPath) ? cfgPath : DFLT_CFG_PATH;

        URL url = GridUtils.resolveGridGainUrl(path);

        if (url == null)
            throw new GridException("Spring XML configuration path is invalid: " + path +
                ". Note that this path should be either absolute or a relative local file system path, " +
                "relative to META-INF in classpath or valid URL to GRIDGAIN_HOME.");

        GenericApplicationContext springCtx;

        try {
            springCtx = new GenericApplicationContext();

            XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(springCtx);

            xmlReader.loadBeanDefinitions(new UrlResource(url));

            springCtx.refresh();
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate Spring XML application context: " + e.getMessage(), e);
        }

        Map cfgMap;

        try {
            // Note: Spring version is not generics-friendly.
            cfgMap = springCtx.getBeansOfType(GridConfiguration.class);
        }
        catch (BeansException e) {
            throw new GridException("Failed to instantiate bean [type=" + GridConfiguration.class + ", err=" +
                e.getMessage() + ']', e);
        }

        if (cfgMap == null)
            throw new GridException("Failed to find a single grid configuration in: " + url);

        if (cfgMap.isEmpty())
            throw new GridException("Can't find grid configuration in: " + url);

        if (cfgMap.size() > 1)
            throw new GridException("Found more than one grid configuration in: " + url);

        return new GridConfigurationAdapter(F.<GridConfiguration>first(cfgMap.values()));
    }

    /** {@inheritDoc} */
    @Override public String getCloudId() {
        return cloudId;
    }

    /**
     * Sets Cloud ID.
     *
     * @param cloudId Cloud ID.
     */
    @GridSpiConfiguration(optional = false)
    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    /** {@inheritDoc} */
    @Override public long getStateCheckFrequency() {
        return stateCheckFreq;
    }

    /**
     * Sets frequency to check cloud state change.
     *
     * @param stateCheckFreq Frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setStateCheckFrequency(long stateCheckFreq) {
        this.stateCheckFreq = stateCheckFreq;
    }

    /** {@inheritDoc} */
    @Override public void setListener(@Nullable GridCloudSpiListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String gridName) throws GridSpiException {
        this.gridName = gridName;

        assertParameter(!F.isEmpty(cloudId), "!F.isEmpty(cloudId)");
        assertParameter(stateCheckFreq > 0, "stateCheckFreq > 0");

        registerMBean(gridName, this, GridJvmCloudSpiMBean.class);

        if (log.isDebugEnabled()) {
            log.debug(configInfo("cloudId", cloudId));
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        deactivate();

        unregisterMBean();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public GridCloudSpiSnapshot activate() throws GridSpiException {
        lastSnp = makeSnapshot();

        // Start control thread after snapshot is ready.
        ctrlThread = new CloudControlThread();
        ctrlThread.start();

        return lastSnp;
    }

    /** {@inheritDoc} */
    @Override public void deactivate() {
        U.interrupt(ctrlThread);
        U.join(ctrlThread, log);

        lastSnp = null;
    }

    /** {@inheritDoc} */
    @Override public void process(GridCloudCommand cmd, UUID cmdExecId) throws GridSpiException {
        assert cmd != null;
        assert !F.isEmpty(cmd.action());
        assert cmdExecId != null;

        int num = cmd.number();

        Collection<GridCloudResource> rsrcs = cmd.resources();

        String act = cmd.action();

        if (START_NODES_ACT.equals(act)) {
            if (num <= 0)
                throw  new GridSpiException("Invalid command (number should be positive value): " + cmd);

            if (!F.isEmpty(rsrcs))
                U.warn(log, "Non-empty resources collection (will be ignored): " + cmd);

            if (log.isDebugEnabled())
                log.debug("Received command to start grid nodes in cloud: " + cmd);
        }
        else if (STOP_NODES_ACT.equals(act)) {
            if (num <= 0 && F.isEmpty(rsrcs))
                throw  new GridSpiException("Invalid command (number should be positive value or resources " +
                    "collection should be non-empty): " + cmd);

            if (rsrcs != null && !rsrcs.isEmpty()) {
                if (num != 0 && num != rsrcs.size())
                    throw new GridSpiException("Invalid command (number should be zero or value equal to " +
                        "resources collection size): " + cmd);

                for (GridCloudResource rsrc : rsrcs) {
                    if (!cloudId.equals(rsrc.cloudId()))
                        throw  new GridSpiException("Command contains resource that doesn't belong to " +
                            "the cloud [cmd=" + cmd + ", cloudId=" + cloudId + ']');

                    if (rsrc.type() != CLD_NODE)
                        throw  new GridSpiException("Command contains cloud resource of type other than CLD_NODE: " +
                            cmd);
                }
            }

            if (log.isDebugEnabled())
                log.debug("Received command to stop grid nodes in cloud: " + cmd);
        }
        else if (STOP_CLOUD_ACT.equals(act)) {
            if (log.isDebugEnabled())
                log.debug("Received command to stop entire cloud: " + cmd);
        }
        else
            throw new GridSpiException("Invalid command (action unknown): " + cmd);

        synchronized (mux) {
            cmds.put(cmdExecId, cmd);

            mux.notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> compare(
        GridCloudSpiSnapshot oldSnp, GridCloudSpiSnapshot newSnp) {
        assert oldSnp != null;
        assert newSnp != null;

        if (F.isEmpty(oldSnp.getResources()) && F.isEmpty(newSnp.getResources()))
            return Collections.emptyList();

        Map<String, GridCloudResource> oldRsrcs = new LinkedHashMap<String, GridCloudResource>();

        if (!F.isEmpty(oldSnp.getResources()))
            for (GridCloudResource rsrc : oldSnp.getResources()) {
                assert rsrc.id() != null;
                assert rsrc.type() == CLD_NODE;

                oldRsrcs.put(rsrc.id(), rsrc);
            }

        Map<String, GridCloudResource> newRsrcs = new LinkedHashMap<String, GridCloudResource>();

        if (!F.isEmpty(newSnp.getResources()))
            for (GridCloudResource rsrc : newSnp.getResources()) {
                assert rsrc.id() != null;
                assert rsrc.type() == CLD_NODE;

                newRsrcs.put(rsrc.id(), rsrc);
            }

        for (Iterator<String> iter = newRsrcs.keySet().iterator(); iter.hasNext();)
            if (oldRsrcs.remove(iter.next()) != null)
                iter.remove();

        if (F.isEmpty(oldRsrcs) && F.isEmpty(newRsrcs))
            return Collections.emptyList();

        Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> res =
            new LinkedList<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>>();

        for (GridCloudResource rsrc : oldRsrcs.values())
            res.add(F.<GridCloudResource, GridCloudSpiResourceAction>t(rsrc, REMOVED));

        for (GridCloudResource rsrc : newRsrcs.values())
            res.add(F.<GridCloudResource, GridCloudSpiResourceAction>t(rsrc, ADDED));

        return res;
    }

    /**
     * Makes cloud snapshot.
     *
     * @return Cloud snapshot.
     */
    private GridCloudSpiSnapshot makeSnapshot() {
        long time = System.currentTimeMillis();

        return new GridCloudSpiSnapshotAdapter(
            UUID.randomUUID(),
            time,
            getSpiContext().localNode().id(),
            F.view(F.transform(getCloudNodeIds(), new C1<UUID, GridCloudResource>() {
                @Override public GridCloudResource apply(UUID id) {
                    assert id != null;

                    return new GridCloudSpiResourceAdapter(id.toString(), CLD_NODE, cloudId);
                }
            })),
            F.asMap(CLOUD_LAST_UPD_TIME_PARAM, String.valueOf(time))
        );
    }

    /**
     * Notifies SPI listener on cloud change if there were any.
     */
    private void notifySpiListenerOnChange() {
        GridCloudSpiSnapshot snp = makeSnapshot();

        GridCloudSpiListener tmp = lsnr;

        if (tmp != null && (lastSnp == null || !compare(lastSnp, snp).isEmpty())) {
            lastSnp = snp;

            tmp.onChange(lastSnp);
        }
    }

    /**
     * Notifies SPI listener when cloud command has been processed.
     *
     * @param success Flag of successful command execution.
     * @param cmdExecId Cloud command execution ID.
     * @param cmd Cloud command.
     * @param msg Optional message.
     */
    private void notifySpiListenerOnCommand(boolean success, UUID cmdExecId, GridCloudCommand cmd,
        @Nullable String msg) {
        assert cmdExecId != null;
        assert cmd != null;

        GridCloudSpiListener tmp = lsnr;

        if (tmp != null)
            tmp.onCommand(success, cmdExecId, cmd, msg);
    }

    /**
     * Checks cloud state and executes commands.
     */
    private void checkCloud() {
        Map<UUID, GridCloudCommand> tmp = null;

        synchronized (mux) {
            if (!cmds.isEmpty()) {
                tmp = cmds;

                cmds = new LinkedHashMap<UUID, GridCloudCommand>();
            }
        }

        if (tmp != null && !tmp.isEmpty())
            for (Map.Entry<UUID, GridCloudCommand> e : tmp.entrySet()) {
                UUID id = e.getKey();

                GridCloudCommand cmd = e.getValue();

                if (START_NODES_ACT.equals(cmd.action()))
                    try {
                        startGridNodes(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (STOP_NODES_ACT.equals(cmd.action()))
                    try {
                        stopGridNodes(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (STOP_CLOUD_ACT.equals(cmd.action()))
                    try {
                        stopCloud(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
            }

        notifySpiListenerOnChange();
    }

    /**
     * Gets cloud nodes IDs.
     *
     * @return Cloud node IDs.
     */
    private Collection<UUID> getCloudNodeIds() {
        return F.transform(
            getSpiContext().remoteNodes(),
            new C1<GridNode, UUID>() {
                @Override public UUID apply(GridNode node) {
                    return node.id();
                }
            },
            new P1<GridNode>() {
                @Override public boolean apply(GridNode node) {
                    return cloudId.equals(node.attribute(ATTR_CLOUD_ID));
                }
            }
        );
    }

    /**
     * Starts grid nodes by command.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    @SuppressWarnings({"unchecked"})
    private void startGridNodes(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert START_NODES_ACT.equals(cmd.action());
        assert cmd.number() > 0;

        String path = null;

        boolean dryRun = false;

        Map<String, String> cmdParams = cmd.parameters();

        if (cmdParams != null) {
            path = cmdParams.get(GRID_CFG_PATH_KEY);

            dryRun = Boolean.parseBoolean(cmdParams.get(CMD_DRY_RUN_KEY));
        }

        GridConfiguration cfg;

        try {
            cfg = loadConfiguration(path);
        }
        catch (GridException e) {
            throw new GridSpiException(e);
        }

        int num = cmd.number();

        boolean throwEx = false;

        if (!dryRun)
            for (int i = 0; i < num; i++) {
                GridConfigurationAdapter cfg0 = new GridConfigurationAdapter(cfg);

                UUID id = UUID.randomUUID();

                String name = GRID_NODE_NAME_PREF + id;

                cfg0.setNodeId(id);
                cfg0.setGridName(name);

                Map<String, Object> attrs = (Map<String, Object>)cfg0.getUserAttributes();

                if (attrs == null)
                    cfg0.setUserAttributes(attrs = new HashMap<String, Object>(1));

                attrs.put(ATTR_CLOUD_ID, cloudId);

                try {
                    G.start(cfg0);

                    if (log.isDebugEnabled())
                        log.debug("Node has been started: " + cfg);
                }
                catch (GridException e) {
                    U.error(log, "Failed to start grid node: " + cfg, e);

                    throwEx = true;
                }
            }
        else
            if (log.isDebugEnabled())
                log.debug("Dry run - node start omitted.");

        if (throwEx)
            throw new GridSpiException("Cloud command failed: " + cmd);
    }

    /**
     * Stops grid nodes by command.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    private void stopGridNodes(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert STOP_NODES_ACT.equals(cmd.action());
        assert cmd.number() > 0 || !F.isEmpty(cmd.resources());

        Collection<UUID> nodeIds = getCloudNodeIds();

        if (F.isEmpty(nodeIds))
            throw new GridSpiException("Unable to execute command (stop nodes), cloud is empty: " + cmd);

        boolean dryRun = false;

        Map<String, String> cmdParams = cmd.parameters();

        if (cmdParams != null)
            dryRun = Boolean.parseBoolean(cmdParams.get(CMD_DRY_RUN_KEY));

        Map<UUID, String> namesToStop = new LinkedHashMap<UUID, String>();

        Collection<GridCloudResource> rsrcs = cmd.resources();

        if (rsrcs != null && !rsrcs.isEmpty()) {
            assert cmd.number() == 0 || cmd.number() == rsrcs.size();

            for (GridCloudResource rsrc : rsrcs) {
                UUID nodeId = UUID.fromString(rsrc.id());

                if (!nodeIds.contains(nodeId))
                    throw new GridSpiException("Unknown node ID: " + nodeId);

                GridNode node = getSpiContext().node(nodeId);

                if (node != null) {
                    String name = node.attribute(ATTR_GRID_NAME);

                    if (!F.isEmpty(name))
                        namesToStop.put(nodeId, name);
                    else
                        throw new GridSpiException("Failed to stop node (grid name attribute not found for " +
                            "node): " + node);
                }
                else
                    U.warn(log, "Node has been stopped not from this SPI: " + nodeId);
            }

            if (namesToStop.size() < rsrcs.size())
                throw new GridSpiException("Not all nodes to stop were found for command [cmd=" + cmd +
                    ", namesToStop=" + namesToStop + ']');
        }
        else {
            int num = cmd.number();

            for (UUID nodeId : nodeIds) {
                GridNode node = getSpiContext().node(nodeId);

                if (node != null) {
                    String name = node.attribute(ATTR_GRID_NAME);

                    if (!F.isEmpty(name)) {
                        namesToStop.put(nodeId, name);

                        if (namesToStop.size() == num)
                            break;
                    }
                    else
                        throw new GridSpiException("Failed to stop node (grid name attribute not found for " +
                            "node): " + node);
                }
                else
                    U.warn(log, "Node has been stopped from outside: " + nodeId);
            }

            if (namesToStop.size() < num)
                throw new GridSpiException("Failed to find enough nodes to stop [cmd=" + cmd +
                    ", found=" + namesToStop + ", requested=" + num + ']');
        }

        if (!namesToStop.isEmpty()) {
            if (!dryRun) {
                if (stopGridNodes(namesToStop))
                    throw new GridSpiException("Cloud command failed: " + cmd);
            }
            else
                if (log.isDebugEnabled())
                    log.debug("Dry run - node stop omitted.");
        }
        else
            U.warn(log, "No nodes to stop were found in cloud: " + cmd);
    }

    /**
     * Stops all grid nodes in cloud.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    private void stopCloud(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert STOP_CLOUD_ACT.equals(cmd.action());

        Collection<UUID> nodeIds = getCloudNodeIds();

        if (F.isEmpty(nodeIds)) {
            if (log.isDebugEnabled())
                log.debug("Cloud is empty, command is safely ignored.");

            return;
        }

        boolean dryRun = false;

        Map<String, String> cmdParams = cmd.parameters();

        if (cmdParams != null)
            dryRun = Boolean.parseBoolean(cmdParams.get(CMD_DRY_RUN_KEY));

        Map<UUID, String> namesToStop = new LinkedHashMap<UUID, String>();

        for (UUID nodeId : nodeIds) {
            GridNode node = getSpiContext().node(nodeId);

            if (node != null) {
                String gridName = node.attribute(ATTR_GRID_NAME);

                if (!F.isEmpty(gridName))
                    namesToStop.put(nodeId, gridName);
                else
                    throw new GridSpiException("Failed to stop node (grid name attribute not found for " +
                        "node): " + node);
            }
            else
                U.warn(log, "Node has been stopped not from this SPI: " + nodeId);
        }

        if (!namesToStop.isEmpty()) {
            if (!dryRun) {
                if (stopGridNodes(namesToStop))
                    throw new GridSpiException("Cloud command failed: " + cmd);
            }
            else
                if (log.isDebugEnabled())
                    log.debug("Dry run - cloud stop omitted.");
        }
        else
            U.warn(log, "No nodes to stop were found in cloud: " + cmd);
    }

    /**
     * Stops grid nodes by their names.
     *
     * @param namesToStop Nodes map.
     *
     * @return {@code true} if there was at least one node not found and stopped.
     */
    private boolean stopGridNodes(Map<UUID, String> namesToStop) {
        assert !F.isEmpty(namesToStop);

        boolean retval = false;

        for (Map.Entry<UUID, String> e : namesToStop.entrySet()) {
            UUID nodeId = e.getKey();
            String gridName = e.getValue();

            if (G.stop(gridName, true)) {
                if (log.isDebugEnabled())
                    log.debug("Node has been stopped [nodeId=" + nodeId + ", gridName=" + gridName + ']');
            }
            else {
                U.warn(log, "Node was not found in JVM [nodeId=" + nodeId + ", gridName=" + gridName + ']');

                retval = true;
            }
        }

        return retval;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJvmCloudSpi.class, this);
    }

    /**
     * Cloud control thread.
     */
    private class CloudControlThread extends GridSpiThread {
        /** Creates cloud control thread. */
        private CloudControlThread() {
            super(gridName, "grid-jvm-cloud-control", log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted()) {
                synchronized (mux) {
                    if (cmds.isEmpty())
                        mux.wait(stateCheckFreq);
                }

                checkCloud();
            }
        }
    }
}
