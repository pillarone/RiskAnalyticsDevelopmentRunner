// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.coherence;

import com.tangosol.net.*;
import com.tangosol.util.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Oracle Coherence implementation of {@link GridCommunicationSpi} SPI. It uses Coherence data
 * grid framework to communicate with remote nodes.
 * <p>
 * SPI uses Coherence asynchronous apply {@link InvocationService#execute(Invocable, Set, InvocationObserver)}
 * to send a message to remote node. If parameter {@link #setAcknowledgment(boolean)}
 * is set to {@code true}, then this SPI will use Coherence
 * {@link InvocationObserver} to wait for request completion acknowledgment.
 * <p>
 * This SPI has no mandatory parameters.
 * <p>
 * This SPI has the following optional parameters:
 * <ul>
 * <li>Send message acknowledgments (see {@link #setAcknowledgment(boolean)}).</li>
 * <li>Coherence invocation service name created and used by GridGain (see {@link #setServiceName(String)}}).</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridCoherenceCommunicationSpi needs to be explicitly configured to override default
 * TCP communication SPI.
 * <pre name="code" class="java">
 * GridCoherenceCommunicationSpi commSpi = new GridCoherenceCommunicationSpi();
 *
 * // Override default false setting.
 * commSpi.setAcknowledgement(true);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default communication SPI.
 * cfg.setCommunicationSpi(commSpi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridCoherenceCommunicationSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *       ...
 *       &lt;property name="communicationSpi"&gt;
 *           &lt;bean class="org.gridgain.grid.spi.communication.coherence.GridCoherenceCommunicationSpi"&gt;
 *               &lt;property name="acknowledgment" value="true"/&gt;
 *           &lt;/bean&gt;
 *       &lt;/property&gt;
 *       ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <b>Note</b>: Coherence is not shipped with GridGain. If you don't have Coherence, you need to
 * download it separately. See <a target=_blank href="http://www.oracle.com/tangosol/index.html">http://www.oracle.com/tangosol/index.html</a> for
 * more information. Once installed, Coherence should be available on the classpath for
 * GridGain. If you use {@code ${GRIDGAIN_HOME}/bin/ggstart.{sh|bat}} script to start
 * a grid node you can simply add Coherence JARs to {@code ${GRIDGAIN_HOME}/bin/setenv.{sh|bat}}
 * scripts that's used to set up class path for the main scripts.
 * <p>
 * <b>Note</b>: When using Coherence SPIs (communication or discovery) you cannot start
 * multiple GridGain instances in the same VM due to limitations of Coherence. GridGain runtime
 * will detect this situation and prevent GridGain from starting in such case.
 * See {@link GridSpiMultipleInstancesSupport} for details.
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCommunicationSpi
 */
@SuppressWarnings("deprecation")
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(false)
public class GridCoherenceCommunicationSpi extends GridSpiAdapter implements GridCommunicationSpi,
    GridCoherenceCommunicationSpiMBean {
    /**
     * Default Coherence configuration path relative to GridGain installation home folder
     * (value is {@code config/coherence/coherence.xml}).
     */
    public static final String DFLT_CONFIG_FILE = "config/coherence/coherence.xml";

    /**
     * Name of cluster member Id attribute added to local node attributes at startup
     * (value is {@code comm.coherence.member.uid}).
     */
    public static final String ATTR_NODE_UID = "comm.coherence.member.uid";

    /** Default Coherence invocation service name (value is {@code gridgain.comm.srvc}). */
    public static final String DFLT_GRIDGAIN_SRVC = "gridgain.comm.srvc";

    /** Coherence service name attribute. */
    private static final String COHERENCE_SERVICE_NAME = "gg:communication:coherenceservicename";

    /** */
    @GridLoggerResource
    private GridLogger log;

    /** */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** Acknowledgment property. */
    private boolean ack;

    /** Coherence service name. */
    private String srvcName = DFLT_GRIDGAIN_SRVC;

    /** IoC configuration parameter to specify the name of the Coherence configuration file. */
    private String cfgFile = DFLT_CONFIG_FILE;

    /** */
    private volatile GridMessageListener lsnr;

    /** Coherence invocation service. */
    private InvocationService srvc;

    /** Map of all discovered nodes with Coherence member UID's. */
    private final Map<UUID, UID> allNodes = new HashMap<UUID, UID>();

    /** */
    private MemberListener mbrListener;

    /** */
    private String gridName;

    /** */
    private ExecutorService clusterExec;

    /** Flag to start Coherence cache server on a dedicated daemon thread. */
    private boolean startCacheServerDaemon = true;

    /**
     * Sets sending acknowledgment property. This configuration parameter is optional.
     * Used in {@link #sendMessage(Collection, Serializable)}
     * to send message to remote nodes with or without waiting for completion.
     * <p>
     * If not provided, default value is {@code false}.
     *
     * @param ack Sending acknowledgment.
     */
    @GridSpiConfiguration(optional = true)
    public void setAcknowledgment(boolean ack) {
        this.ack = ack;
    }

    /**
     * Sets name for Coherence service invocation used in grid.
     * <p>
     * If not provided, default value is {@link #DFLT_GRIDGAIN_SRVC}.
     *
     * @param srvcName Invocation service name.
     */
    @GridSpiConfiguration(optional = true)
    public void setServiceName(String srvcName) {
        this.srvcName = srvcName;
    }

    /**
     * Sets either absolute or relative to GridGain installation home folder path to Coherence XML
     * configuration file. This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link #DFLT_CONFIG_FILE}.
     *
     * @param cfgFile Path to Coherence configuration file.
     */
    @GridSpiConfiguration(optional = true)
    public void setConfigurationFile(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    /**
     * Sets flag to start Coherence cache server on a dedicated daemon thread.
     * This configuration parameter is optional.
     * See <a href="http://download.oracle.com/otn_hosted_doc/coherence/342/com/tangosol/net/DefaultCacheServer.html">DefaultCacheServer</a>
     * for more information.
     * Note that Coherence cluster services used in SPI should be declared
     * as "autostart" in configuration with started {@code DefaultCacheServer}
     * to avoid reconnection problem between grid nodes.
     * If not provided, default value is {@code true}.
     *
     * @param startCacheServerDaemon Flag indicates whether
     *      Coherence DefaultCacheServer should be started in SPI or not.
     */
    @GridSpiConfiguration(optional = true)
    public void setStartCacheServerDaemon(boolean startCacheServerDaemon) {
        this.startCacheServerDaemon = startCacheServerDaemon;
    }

    /** {@inheritDoc} */
    @Override public String getLocalUid() {
        return srvc == null ? null :
            U.byteArray2HexString(srvc.getCluster().getLocalMember().getUid().toByteArray());
    }

    /** {@inheritDoc} */
    @Override public boolean isAcknowledgment() {
        return ack;
    }

    /** {@inheritDoc} */
    @Override public String getServiceName() {
        return srvcName;
    }

    /** {@inheritDoc} */
    @Override public String getConfigurationFile() {
        return cfgFile;
    }

    /** {@inheritDoc} */
    @Override public boolean isStartCacheServerDaemon() {
        return startCacheServerDaemon;
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        assertParameter(srvcName != null, "srvcName != null");

        clusterExec = Executors.newCachedThreadPool(
            new GridSpiThreadFactory(gridName, "grid-comm-coherence-restarter", log));

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

            // Get coherence invocation service defined in configuration file.
            srvc = (InvocationService)factory.ensureService(srvcName);
        }
        else {
            // Get coherence invocation service.
            srvc = CacheFactory.getInvocationService(srvcName);
        }

        if (srvc == null) {
            throw new GridSpiException("Failed to create coherence invocation service in cluster.");
        }

        if (startCacheServerDaemon) {
            // Start the cache server on a dedicated daemon thread.
            DefaultCacheServer.startDaemon();
        }

        // Set communication attributes for node.
        return F.<String, Object>asMap(createSpiAttributeName(COHERENCE_SERVICE_NAME), srvcName);
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        this.gridName = gridName;

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("ack", ack));
            log.debug(configInfo("srvcName", srvcName));
            log.debug(configInfo("cfgFile", cfgFile));
            log.debug(configInfo("startCacheServerDaemon", startCacheServerDaemon));
        }

        srvc.setUserContext(this);

        mbrListener = createServiceMembershipListener();

        srvc.addMemberListener(mbrListener);

        // Make grid nodes search in cluster.
        findRemoteMember(null);

        registerMBean(gridName, this, GridCoherenceCommunicationSpiMBean.class);

        // Ack start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        if (srvc != null) {
            // Clear context.
            srvc.setUserContext(null);

            srvc.removeMemberListener(mbrListener);

            srvc.shutdown();
        }

        // Stop coherence restarter thread pool.
        U.shutdownNow(getClass(), clusterExec, log);

        // Shutdown the cache server.
        if (startCacheServerDaemon) {
            DefaultCacheServer.shutdown();
        }

        clusterExec = null;
        srvc = null;

        synchronized (allNodes) {
            allNodes.clear();
        }

        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(srvc.getCluster().getLocalMember().getPort(), GridPortProtocol.TCP);
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(GridNode destNode, Serializable msg) throws GridSpiException {
        assert destNode != null;
        assert msg != null;

        if (log.isDebugEnabled()) {
            log.debug("Sending message to node [node=" + destNode + ", msg=" + msg + ']');
        }

        sendMessage(Collections.singletonList(destNode), msg);
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(Collection<? extends GridNode> destNodes, Serializable msg) throws GridSpiException {
        assert destNodes != null;
        assert msg != null;
        assert destNodes.size() != 0;

        if (log.isDebugEnabled()) {
            log.debug("Sending message to nodes [nodes=" + destNodes + ", msg=" + msg + ']');
        }

        Set<Member> mbrs = new HashSet<Member>(destNodes.size());

        Map<Member, GridNode> activeNodes = new HashMap<Member, GridNode>();

        GridSpiException err = null;

        for (GridNode node : destNodes) {
            Member mbr = findLocalMember(node);

            // Implicitly scan members.
            if (mbr == null) {
                mbr = findRemoteMember(node);
            }

            if (mbr != null) {
                mbrs.add(mbr);

                activeNodes.put(mbr, node);
            }
            else {
                U.error(log, "Failed to send message. Node not found in cluster [node=" + node +
                    ", msg=" + msg + ']');

                // Store only first exception.
                if (err == null) {
                    err = new GridSpiException("Failed to send message. Node not found in cluster [node=" + node +
                        ", msg=" + msg + ']');
                }
            }
        }

        if (mbrs.size() > 0) {
            if (ack) {
                MessageObserver observer = new MessageObserver(activeNodes);

                // Asynchronously invoke the agent task with waiting for observer completion.
                srvc.execute(new GridCoherenceCommunicationAgent(nodeId, msg), mbrs, observer);

                try {
                    observer.waitAcknowledgment();
                }
                catch (InterruptedException e) {
                    if (err == null) {
                        err = new GridSpiException("Failed to send message. Sender was interrupted " +
                            "[activeNodes=" + activeNodes + ", msg=" + msg + ']', e);
                    }

                    throw err;
                }

                if (err == null) {
                    err = observer.getError();
                }
            }
            else {
                // Asynchronously invoke the agent task on set of members.
                srvc.execute(new GridCoherenceCommunicationAgent(nodeId, msg), mbrs,
                    new MessageObserver(activeNodes));
            }
        }

        if (err != null) {
            throw err;
        }
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridMessageListener lsnr) {
        // Note that we allow null listener to be set.
        this.lsnr = lsnr;
    }

    /**
     * Method is called by {@link GridCoherenceCommunicationAgent} when arrived.
     *
     * @param nodeId Sender's node Id.
     * @param msg Message object.
     */
    void onMessage(UUID nodeId, Serializable msg) {
        GridMessageListener localCopy = lsnr;

        if (localCopy != null)
            localCopy.onMessage(nodeId, msg);
        else
            if (log.isDebugEnabled())
                log.debug("Received communication message without any registered listeners (will ignore) " +
                    "[senderNodeId=" + nodeId + ']');
    }

    /**
     * Creates membership listener for cluster.
     *
     * @return Listener.
     */
    private MemberListener createServiceMembershipListener() {
        return new MemberListener() {
            @SuppressWarnings("unchecked")
            public void memberJoined(final MemberEvent evt) {
                final Member mbr = evt.getMember();

                if (log.isDebugEnabled()) {
                    log.debug("Coherence cluster member joined: " + getMemberInfo(mbr));
                }

                // Cluster services should be restarted after apply in separate thread.
                clusterExec.submit(new Runnable() {
                    /** {@inheritDoc} */
                    @Override public void run() {
                        // If local node joined in cluster again then we need to scan cluster for exist nodes.
                        // Usually local node joined in cluster after disconnection (and local cluster
                        // services will be restarted).
                        if (evt.isLocal()) {
                            assert mbr.equals(srvc.getCluster().getLocalMember());

                            Collection<UID> mbrUids = new HashSet<UID>(srvc.getCluster().getMemberSet().size());

                            for (Member clusterMbr : (Set<Member>)srvc.getCluster().getMemberSet()) {
                                mbrUids.add(clusterMbr.getUid());
                            }

                            synchronized (allNodes) {
                                allNodes.put(nodeId, mbr.getUid());

                                // Remove old nodeId's.
                                allNodes.values().retainAll(mbrUids);
                            }
                        }
                        else {
                            Map mbrNodes = srvc.query(new GridCoherenceCommunicationInfoAgent(nodeId),
                                Collections.singleton(mbr));

                            if (log.isDebugEnabled()) {
                                log.debug("Received response from joined node [mbr=" + getMemberInfo(mbr) +
                                    ", res=" + mbrNodes + ']');
                            }

                            UUID nodeId;

                            if (mbrNodes != null && (nodeId = (UUID)mbrNodes.get(mbr)) != null) {
                                synchronized (allNodes) {
                                    allNodes.put(nodeId, mbr.getUid());
                                }
                            }
                        }
                    }
                });
            }

            /** {@inheritDoc} */
            @Override public void memberLeaving(MemberEvent evt) {
                // No-op.
            }

            /** {@inheritDoc} */
            @Override public void memberLeft(MemberEvent evt) {
                final Member mbr = evt.getMember();

                if (log.isDebugEnabled()) {
                    log.debug("Coherence member left: " + getMemberInfo(mbr));
                }

                // Cluster services should be restarted after apply in separate thread.
                clusterExec.submit(new Runnable() {
                    /** {@inheritDoc} */
                    @Override public void run() {
                        processDeletedMember(mbr);

                        // This apply used to restart cluster service on local node if stopped.
                        srvc.getCluster().getLocalMember();
                    }
                });
            }
        };
    }

    /**
     * Makes search for Cluster member with defined grid node.
     *
     * @param node Grid node.
     * @return Member in cluster or {@code null} if not found.
     */
    @SuppressWarnings({"unchecked"})
    private Member findLocalMember(GridNode node) {
        assert node != null;
        assert node.id() != null;

        // Check is local node.
        if (nodeId.equals(node.id())) {
            return srvc.getCluster().getLocalMember();
        }

        UID mbrUid;

        synchronized (allNodes) {
            mbrUid = allNodes.get(node.id());
        }

        if (mbrUid != null) {
            for (Member mbr : (Set<Member>)srvc.getCluster().getMemberSet()) {
                if (mbr.getUid().equals(mbrUid)) {
                    return mbr;
                }
            }
        }

        return null;
    }

    /**
     * Find member in cluster and add them in collection.
     * This function sent broadcast message to all nodes in cluster
     * to get grid nodeId's if member was not found in collection.
     *
     * @param node Grid node.
     * @return Member in cluster or {@code null} if not found.
     */
    @SuppressWarnings({"unchecked"})
    private Member findRemoteMember(GridNode node) {
        // Check is local node.
        if (node != null && nodeId.equals(node.id())) {
            return srvc.getCluster().getLocalMember();
        }

        Set<Member> mbrs = new HashSet<Member>(srvc.getCluster().getMemberSet().size());

        // Prepare collection of all "unknown" members from cluster.
        synchronized (allNodes) {
            for (Member mbr : (Set<Member>)srvc.getCluster().getMemberSet()) {
                boolean found = false;

                for (UID uid : allNodes.values()) {
                    if (uid.equals(mbr.getUid())) {
                        found = true;

                        break;
                    }
                }

                if (!found) {
                    mbrs.add(mbr);
                }
            }
        }

        Map mbrNodes = null;

        if (!mbrs.isEmpty()) {
            mbrNodes = srvc.query(new GridCoherenceCommunicationInfoAgent(nodeId), mbrs);
        }

        synchronized (allNodes) {
            if (mbrNodes != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Node scanning returns result [mbrs=" + mbrs + ", res=" + mbrNodes + ']');
                }

                for (Entry<Member, UUID> entry : (Set<Entry<Member, UUID>>)mbrNodes.entrySet()) {
                    if (entry.getValue() != null) {
                        allNodes.put(entry.getValue(), entry.getKey().getUid());
                    }
                }
            }

            if (node == null) {
                return null;
            }
            else {
                UID mbrUid = allNodes.get(node.id());

                if (mbrUid != null) {
                    for (Member mbr : (Set<Member>)srvc.getCluster().getMemberSet()) {
                        if (mbr.getUid().equals(mbrUid)) {
                            return mbr;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Delete member from local collection.
     *
     * @param mbr Cluster member.
     */
    private void processDeletedMember(Member mbr) {
        synchronized (allNodes) {
            for (Iterator<UID> iter = allNodes.values().iterator(); iter.hasNext();) {
                UID mbrUid = iter.next();

                if (mbrUid.equals(mbr.getUid())) {
                    iter.remove();

                    if (log.isDebugEnabled()) {
                        log.debug("Remove node from collection: " + getMemberInfo(mbr));
                    }

                    return;
                }
            }
        }
    }

    /**
     * Prepare member data info for logging.
     *
     * @param mbr Cluster member.
     * @return String with cluster member information.
     */
    private String getMemberInfo(Member mbr) {
        if (mbr == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("[uid=").append(U.byteArray2HexString(mbr.getUid().toByteArray()));
        builder.append(", id=").append(mbr.getId());
        builder.append(", addr=").append(mbr.getAddress());
        builder.append(", port=").append(mbr.getPort());
        builder.append(", machineId=").append(mbr.getMachineId());
        builder.append(", machineName=").append(mbr.getMachineName());
        builder.append(", processName=").append(mbr.getProcessName());
        builder.append(']');

        return builder.toString();
    }

    /**
     * Observer for messages which was sent to another nodes.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private class MessageObserver implements InvocationObserver {
        /** Indicates is sending operation was finished or not. */
        private boolean isCompleted;

        /** */
        private final Object mux = new Object();

        /** Message will be sent to these nodes. */
        private Map<Member, GridNode> nodes;

        /** */
        private AtomicReference<GridSpiException> err = new AtomicReference<GridSpiException>(null);

        /**
         * Creates observer.
         *
         * @param nodes Nodes where the agents being sent.
         */
        MessageObserver(Map<Member, GridNode> nodes) {
            assert nodes != null;
            assert nodes.size() > 0;

            this.nodes = nodes;
        }

        /** {@inheritDoc} */
        @Override public void memberCompleted(Member mbr, Object obj) {
            if (log.isDebugEnabled()) {
                log.debug("Message was sent to node: " + getMemberInfo(mbr));
            }
        }

        /** {@inheritDoc} */
        @Override public void memberFailed(Member mbr, Throwable t) {
            U.error(log, "Failed to send message: " + getMemberInfo(mbr), t);

            GridNode failedNode = nodes.get(mbr);

            assert failedNode != null;

            String errMsg = "Failed to send message: " + failedNode;

            log.error(errMsg, t);

            // Record exception.
            // Only record the first exception.
            if (err.get() == null) {
                // This temp variable is required to overcome bug
                // in Ant preparer task for now..
                GridSpiException e1 = new GridSpiException(errMsg, t);

                // Only record the first exception.
                err.compareAndSet(null, e1);
            }
        }

        /** {@inheritDoc} */
        @Override public void memberLeft(Member mbr) {
            U.error(log, "Member left and message wasn't sent: " + getMemberInfo(mbr));
        }

        /** {@inheritDoc} */
        @Override public void invocationCompleted() {
            if (log.isDebugEnabled()) {
                log.debug("Message observer invocation completed: " + nodes);
            }

            synchronized (mux) {
                isCompleted = true;

                mux.notifyAll();
            }
        }

        /**
         * Called by SPI when message has been sent to more than one node.
         *
         * @return Exception object or {@code null} if no errors.
         */
        public GridSpiException getError() {
            return err.get();
        }

        /**
         * Wait until message was sent.
         *
         * @throws InterruptedException This exception is propagated from {@link Object#wait()}.
         */
        public void waitAcknowledgment() throws InterruptedException {
            synchronized (mux) {
                while (true) {
                    // This condition is taken out of the loop to avoid
                    // potentially wrong optimization by the compiler of
                    // moving field access out of the loop causing this loop
                    // to never exit.
                    if (isCompleted) {
                        break;
                    }

                    mux.wait(5000);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));
        attrs.add(createSpiAttributeName(COHERENCE_SERVICE_NAME));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceCommunicationSpi.class, this);
    }
}
