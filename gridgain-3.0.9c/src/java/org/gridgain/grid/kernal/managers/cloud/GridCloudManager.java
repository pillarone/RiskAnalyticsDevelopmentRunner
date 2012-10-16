// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.cloud.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.jsr305.*;
import org.jetbrains.annotations.*;

import javax.management.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.cloud.GridCloudControlResponse.*;
import static org.gridgain.grid.kernal.managers.cloud.GridCloudMessage.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;
import static org.gridgain.grid.spi.cloud.GridCloudSpiResourceAction.*;

/**
 * This class defines a cloud manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCloudManager extends GridManagerAdapter<GridCloudSpi> {
    /** Default number of resend attempts */
    public static final int DFLT_CMDS_RESEND_ATTEMPTS = 0;

    /** Local node ID. */
    private final UUID locNodeId;

    /** Grid name. */
    private final String gridName;

    /** Flag which identifies whether this node can be (or supposed to be) cloud coordinator or not. */
    private final boolean disableCloudCrd;

    /** MBean server. */
    private final MBeanServer mBeanSrv;

    /** Descriptors of the clouds coordinated by the current manager. */
    private Map<String, CloudDescriptor> descrsMap;

    /** Cloud strategy descriptors. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private Collection<CloudEnabledDescriptor<GridCloudStrategy>> sgyDescrs;

    /** Cloud policy descriptors. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private Collection<CloudEnabledDescriptor<GridCloudPolicy>> plcDescrs;

    /** Mutex. */
    private final Object mux = new Object();

    /** Adapters for clouds available on the entire grid. */
    private final Map<String, GridCloudAdapter> cloudMap = new GridLeanMap<String, GridCloudAdapter>();

    /** Cloud MBeans. */
    private final Collection<ObjectName> cloudMBeans = new LinkedList<ObjectName>();

    /** Comparator to ease coordinator finding. */
    private final Comparator<GridNode> crdComp = new CoordinatorComparator();

    /** Map to store GridCloudControlResponses. */
    private final Map<UUID, GridCloudControlResponse> ressMap = new HashMap<UUID, GridCloudControlResponse>();

    /** Cloud commands resend attempts. */
    private int cmdsResendAttempts = DFLT_CMDS_RESEND_ATTEMPTS;

    /** Local coordinators store */
    private final Map<String, UUID> crdsMap = new HashMap<String, UUID>();

    /** Discovery listener. */
    private final GridLocalEventListener discoLsnr = new GridLocalEventListener() {
        @Override public void onEvent(GridEvent evt) {
            assert evt instanceof GridDiscoveryEvent;

            if (evt.type() == EVT_NODE_JOINED)
                onNodeJoined((GridDiscoveryEvent) evt);
            else if (evt.type() == EVT_NODE_LEFT)
                onNodeLeft((GridDiscoveryEvent) evt);
        }
    };

    /** Cloud command listener. */
    @SuppressWarnings({"deprecation"})
    private final GridMessageListener cmdLsnr = new GridMessageListener() {
        @Override public void onMessage(UUID nodeId, Object msg) {
            if (msg instanceof GridCloudControlRequest) {
                GridCloudControlRequest req = (GridCloudControlRequest)msg;

                if (log.isDebugEnabled())
                    log.debug("Received cloud control request [nodeId=" + nodeId + ", req=" + req + ']');

                process(req.getCloudId(), req.getRequestId(), req.getCommands(), nodeId);
            }
            else if (msg instanceof GridCloudControlResponse) {
                GridCloudControlResponse res = (GridCloudControlResponse) msg;

                if (log.isDebugEnabled())
                    log.debug("Received cloud control response [nodeId=" + nodeId + ", res=" + res + ']');

                synchronized (ressMap) {
                    ressMap.put(res.getRequestId(), res);
                }
            }
            else
                U.warn(log, "Got unexpected message type (will skip) [msg=" + msg + ']');
        }
    };

    /** Cloud message listener. */
    @SuppressWarnings({"deprecation"})
    private final GridMessageListener msgLsnr = new GridMessageListener() {
        @Override public void onMessage(UUID nodeId, Object msg) {
            if (!(msg instanceof GridCloudMessage)) {
                U.warn(log, "Got unexpected message type (will skip) [msg=" + msg + ']');

                return;
            }

            GridCloudMessage cloudMsg = (GridCloudMessage)msg;

            int type = cloudMsg.getType();

            if (type == CMD_EVT_TYPE) {
                if (log.isDebugEnabled())
                    log.debug("Received cloud command event message [msg=" + msg + ", nodeId=" + nodeId + ']');

                processCommandEventMessage(cloudMsg);
            }
            else if (type == RSRC_EVT_TYPE) {
                if (log.isDebugEnabled())
                    log.debug("Received cloud resource event message [msg=" + msg + ", nodeId=" + nodeId + ']');

                processResourceEventMessage(cloudMsg);
            }
            else if (type == LAST_CLOUD_STATE_TYPE) {
                if (log.isDebugEnabled())
                    log.debug("Received last cloud state message [msg=" + msg + ", nodeId=" + nodeId + ']');

                if (!locNodeId.equals(nodeId) && !disableCloudCrd)
                    processCloudStateMessage(cloudMsg);

                processCloudSnapshotMessage(cloudMsg);
            }
            else if (type == LAST_CLOUD_VER_TYPE) {
                if (!locNodeId.equals(nodeId) && !disableCloudCrd) {
                    if (log.isDebugEnabled())
                        log.debug("Received cloud version message [msg=" + msg + ", nodeId=" + nodeId + ']');

                    processCloudStateMessage(cloudMsg);
                }
            }
            else
                U.warn(log, "Received cloud message of unknown type (will skip) [msg=" + msg +
                    ", nodeId=" + nodeId + ']');
        }
    };

    /**
     * Creates cloud manager.
     *
     * @param ctx Grid kernal context.
     */
    public GridCloudManager(GridKernalContext ctx) {
        super(GridCloudSpi.class, ctx, ctx.config().getCloudSpi());

        locNodeId = ctx.config().getNodeId();
        gridName = ctx.config().getGridName();
        disableCloudCrd = ctx.config().isDisableCloudCoordinator();
        mBeanSrv = ctx.config().getMBeanServer();
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        if (!disableCloudCrd) {
            Map<String, CloudDescriptor> tmp = new HashMap<String, CloudDescriptor>();

            for (GridCloudSpi spi : getSpis()) {
                final String cloudId = spi.getCloudId();

                if (F.isEmpty(cloudId))
                    throw new GridException("SPI can not return null or empty cloud ID [spi=" + spi + ']');

                if (tmp.containsKey(cloudId))
                    throw new GridException("Found duplicated cloud ID [cloudId=" + cloudId + ']');

                spi.setListener(new GridCloudSpiListener() {
                    @Override public void onChange(GridCloudSpiSnapshot snapshot) {
                        assert snapshot != null;

                        onCloudSnapshot(cloudId, snapshot, false);
                    }

                    @Override public void onCommand(boolean success, UUID cmdExecId, GridCloudCommand cmd, String msg) {
                        assert cmdExecId != null;
                        assert cmd != null;

                        onCommandProcessed(cloudId, success, cmdExecId, cmd, msg);
                    }
                });

                tmp.put(cloudId, new CloudDescriptor(spi));
            }

            descrsMap = Collections.unmodifiableMap(tmp);

            GridCloudPolicy[] plcs = ctx.config().getCloudPolicies();

            if (!F.isEmpty(plcs)) {
                Collection<CloudEnabledDescriptor<GridCloudPolicy>> descrs =
                    new ArrayList<CloudEnabledDescriptor<GridCloudPolicy>>(plcs.length);

                for (GridCloudPolicy p : plcs) {
                    ctx.resource().inject(p);

                    descrs.add(new CloudEnabledDescriptor<GridCloudPolicy>(p));
                }

                plcDescrs = Collections.unmodifiableCollection(descrs);
            }

            GridCloudStrategy[] sgys = ctx.config().getCloudStrategies();

            if (!F.isEmpty(sgys)) {
                Collection<CloudEnabledDescriptor<GridCloudStrategy>> descrs =
                    new ArrayList<CloudEnabledDescriptor<GridCloudStrategy>>(sgys.length);

                for (GridCloudStrategy s : sgys) {
                    ctx.resource().inject(s);

                    descrs.add(new CloudEnabledDescriptor<GridCloudStrategy>(s));
                }

                sgyDescrs = Collections.unmodifiableCollection(descrs);
            }
        }

        startSpi();

        if (!disableCloudCrd)
            ctx.io().addMessageListener(TOPIC_CLOUD_CONTROL, cmdLsnr);

        ctx.io().addMessageListener(TOPIC_CLOUD_STATE, msgLsnr);

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        super.onKernalStart();

        if (!disableCloudCrd) {
            checkCoordinatorship(descrsMap.keySet());

            ctx.event().addLocalEventListener(discoLsnr, EVTS_DISCOVERY);
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        descrsMap = null;
        plcDescrs = null;
        sgyDescrs = null;

        cloudMap.clear();
        cloudMBeans.clear();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    @Override public void onKernalStop() {
        if (!disableCloudCrd) {
            if (ctx.discovery() != null)
                ctx.event().removeLocalEventListener(discoLsnr);

            if (ctx.io() != null)
                ctx.io().removeMessageListener(TOPIC_CLOUD_CONTROL, cmdLsnr);
        }

        if (ctx.io() != null)
            ctx.io().removeMessageListener(TOPIC_CLOUD_STATE, msgLsnr);

        if (!disableCloudCrd) {
            for (Map.Entry<String, CloudDescriptor> e : descrsMap.entrySet()) {
                String cloudId = e.getKey();
                CloudDescriptor descr = e.getValue();

                synchronized (descr) {
                    if (descr.isActive()) {
                        descr.deactivate();

                        deactivateCloudEnabledObjects(cloudId);
                    }

                    descr.getSpi().setListener(null);
                }
            }

            if (!F.isEmpty(sgyDescrs))
                for (CloudEnabledDescriptor<GridCloudStrategy> descr : sgyDescrs)
                    synchronized (descr) {
                        GridCloudStrategy s = descr.getObject();

                        if (descr.isActive()) {
                            U.warn(log, "Cloud strategy is still active (will deactivate immediately) [strategy=" +
                                s + ']');

                            s.deactivate();
                        }

                        try {
                            ctx.resource().cleanup(s);
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to cleanup cloud strategy resources [strategy=" + s + ']', e);
                        }
                    }

            if (!F.isEmpty(plcDescrs))
                for (CloudEnabledDescriptor<GridCloudPolicy> descr : plcDescrs)
                    synchronized (descr) {
                        GridCloudPolicy p = descr.getObject();

                        if (descr.isActive()) {
                            U.warn(log, "Cloud policy is still active (will deactivate immediately) [policy=" +
                                p + ']');

                            p.deactivate();
                        }

                        try {
                            ctx.resource().cleanup(p);
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to cleanup cloud policy resources [policy=" + p + ']', e);
                        }
                    }
        }

        synchronized (mux) {
            if (!F.isEmpty(cloudMBeans))
                for (ObjectName mb : cloudMBeans)
                    try {
                        mBeanSrv.unregisterMBean(mb);

                        if (log.isDebugEnabled())
                            log.debug("Unregistered cloud MBean: " + mb);
                    }
                    catch (JMException e) {
                        U.error(log, "Failed to unregister cloud MBean: " + mb, e);
                    }
        }

        super.onKernalStop();
    }

    /**
     * Invokes commands for given cloud.
     * <p>If cloud coordinator is local node, commands are processed locally.
     * <p>If cloud coordinator is remote node, sends commands to cloud coordinator over network.
     * <p>If sending itself succeeds, registers timeout object that waits
     * for command delivery receipt and resends the message if necessary having
     * {@link GridCloudManager#getCommandsResendAttempts()} attempts number.
     * <p>If exception is thrown prior to sending message over the network no resend attempts
     * are taken.
     *
     * @param cloudId Cloud ID.
     * @param cmds Commands to execute.
     * @return Command execution IDs for caller to track commands execution results.
     * @throws GridException Thrown if
     * <ul>
     * <li>Cloud coordinator cannot be resolved;
     * <li>At least one of provided {@link GridCloudCommand} has empty or {@code null} id;
     * <li>At least two commands have equal ids;
     * <li>One of the commands is associated with resource that does not belong to cloud.
     */
    public Collection<UUID> invoke(String cloudId, Collection<? extends GridCloudCommand> cmds) throws GridException {
        assert !F.isEmpty(cloudId);

        if (F.isEmpty(cmds))
            return Collections.emptyList();

        GridNode crd = getCoordinator(cloudId);

        if (crd == null)
            throw new GridException("Failed to find coordinator for cloud ID [cloudId=" + cloudId + ']');

        Map<UUID, GridCloudCommand> cmdMap = new LinkedHashMap<UUID, GridCloudCommand>();

        Collection<String> ids = new LinkedList<String>();

        for (GridCloudCommand cmd : cmds) {
            String id = cmd.id();
            Collection<GridCloudResource> rsrcs = cmd.resources();

            if (F.isEmpty(id))
                throw new GridException("Command ID must be not null and not empty [cmd=" + cmd + ']');

            if (ids.contains(id))
                throw new GridException("Duplicate command ID has been found [cmd=" + cmd + ']');

            if (!F.isEmpty(rsrcs)) {
                assert rsrcs != null;

                for (GridCloudResource rsrc : rsrcs)
                    if (!cloudId.equals(rsrc.cloudId()))
                        throw new GridException("Resource does not belong to cloud [cloudId=" + cloudId +
                            ", rsrc=" + rsrc + ']');
            }

            ids.add(id);

            cmdMap.put(UUID.randomUUID(), cmd);
        }

        if (locNodeId.equals(crd.id())) {
            if (log.isDebugEnabled())
                log.debug("Coordinator is local node, commands will be processed locally [coordinator=" +
                    crd + ", commands=" + cmdMap + ']');

            process(cloudId, null, cmdMap, null);
        }
        else
            sendCloudCommandsToCoordinator(cloudId, cmdMap, 0);

        return cmdMap.keySet();
    }

    /**
     * Sends commands to cloud coordinator over network.
     * If sending itself succeeds (but not delivery) registers timeout object that waits
     * for command delivery receipt and resends the message if necessary having
     * {@link GridCloudManager#getCommandsResendAttempts()} attempts number.
     *
     * @param cloudId Cloud ID.
     * @param cmds Cloud commands.
     * @param attempt Resend attempt (initially call with 0).
     * @throws GridException Thrown if any exception occurs.
     */
    private void sendCloudCommandsToCoordinator(String cloudId, Map<UUID, GridCloudCommand> cmds, int attempt)
        throws GridException {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(cmds);
        assert attempt <= getCommandsResendAttempts();

        if (log.isDebugEnabled() && attempt > 0)
            log.debug("Trying to resend CloudControlRequest [attempt=" + attempt + ']');

        GridNode crd = getCoordinator(cloudId);

        if (crd == null)
            throw new GridException("Failed to find coordinator for cloud ID [cloudId=" + cloudId + ']');

        UUID reqId = UUID.randomUUID();

        ctx.io().send(crd, TOPIC_CLOUD_CONTROL, new GridCloudControlRequest(cloudId, cmds, reqId),
            SYSTEM_POOL);

        if (log.isDebugEnabled())
            log.debug("Sent cloud commands to coordinator [coordinator=" + crd + ", commands=" + cmds +
                "reqId=" + reqId + ']');

        if (attempt < getCommandsResendAttempts())
            ctx.timeout().addTimeoutObject(createCommandReceiptProcessor(cloudId, cmds, attempt + 1, reqId));
    }

    /**
     * Creates timeout object that waits for cloud commands delivery receipt and tries to resend commands to cloud
     * coordinator if necessary.
     *
     * @param cloudId Cloud ID.
     * @param cmds Cloud commands.
     * @param attempt Resend attempt initial value for returned object.
     * @param reqId Cloud control request to wait receipt for.
     * @return Timeout object.
     */
    private GridTimeoutObject createCommandReceiptProcessor(final String cloudId,
        final Map<UUID, GridCloudCommand> cmds, final int attempt, final UUID reqId) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(cmds);
        assert attempt <= getCommandsResendAttempts();
        assert reqId != null;

        return new GridTimeoutObject() {
            private UUID id = UUID.randomUUID();

            private long endTime = System.currentTimeMillis() + ctx.config().getNetworkTimeout() * 2;

            @Override public UUID timeoutId() { return id; }

            @Override public long endTime() { return endTime; }

            @Override public void onTimeout() {
                GridCloudControlResponse res;

                synchronized (ressMap) {
                    res = ressMap.remove(reqId);
                }

                if (res != null)
                    if (res.getRespStatus() == RS_OK) {
                        if (log.isDebugEnabled())
                            log.debug("Grid cloud control request handled [res=" + res + ']');

                        return;
                    }
                    else
                        U.warn(log, "Grid cloud control request handled with error [res="
                            + res + ']');

                int attemptsLeft = getCommandsResendAttempts() - attempt;

                U.warn(log, "Grid cloud control request was not handled (no receipt) [cloudId=" + cloudId +
                    "reqId=" + reqId + ", attemptsLeft=" + attemptsLeft + ']');

                for (int i = attempt; i <= getCommandsResendAttempts(); i++)
                    try {
                        sendCloudCommandsToCoordinator(cloudId, cmds, getCommandsResendAttempts() - attemptsLeft);

                        break;
                    }
                    catch (GridException ex) {
                        U.warn(log, "CloudControlRequest sending failed: " + ex.getMessage());

                        waitForCloudUpdates(cloudId);
                    }
            }
        };
    }

    /**
     * Creates runnable that tries to resend commands to cloud coordinator.
     *
     * @param cloudId Cloud ID.
     * @param cmds Cloud commands.
     * @param attempt Resend attempt initial value for returned object.
     * @return Runnable.
     */
    private Runnable createCommandResendRunnable(final String cloudId, final Map<UUID, GridCloudCommand> cmds,
        final int attempt) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(cmds);
        assert attempt <= getCommandsResendAttempts();

        return new Runnable() {
            @Override public void run() {
                for (int i = attempt; i <= getCommandsResendAttempts(); i++)
                    try {
                        waitForCloudUpdates(cloudId);

                        sendCloudCommandsToCoordinator(cloudId, cmds, i);

                        break;
                    }
                    catch (GridException ex) {
                        U.warn(log, "CloudControlRequest sending failed: " + ex.getMessage());
                    }
            }
        };
    }

    /**
     * Processes node join.
     *
     * @param evt GridDiscoveryEvent to process.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void onNodeJoined(GridDiscoveryEvent evt) {
        assert evt != null;
        assert evt.type() == EVT_NODE_JOINED;

        UUID joinedNodeId = evt.eventNodeId();

        GridNode node = ctx.discovery().node(joinedNodeId);

        if (node == null)
            return;

        Collection<GridCloudMessage> msgs = new LinkedList<GridCloudMessage>();

        for (CloudDescriptor descr : descrsMap.values())
            synchronized (descr) {
                GridCloudMessage msg;

                if (descr.isActive() && (msg = descr.getLastStateMessage()) != null)
                    msgs.add(msg);
            }

        for (GridCloudMessage msg : msgs)
            sendCloudMessage(msg, node);

        Collection<String> cloudIds = node.attribute(ATTR_CLOUD_IDS);

        // Node has joined, so we can only pass coordinatorship of currently coordinated clouds
        // if and only if new node is more suitable to be a coordinator
        if (!F.isEmpty(cloudIds) && crdComp.compare(ctx.discovery().localNode(), node) > 0) {
            Collection<String> checkCloudIds = new HashSet<String>();

            for (CloudDescriptor desc : descrsMap.values())
                synchronized (desc) {
                    String cloudId = desc.getSpi().getCloudId();

                    if (desc.isActive() && cloudIds.contains(cloudId))
                        checkCloudIds.add(cloudId);
                }

            if (!F.isEmpty(checkCloudIds))
                checkCoordinatorship(checkCloudIds);
        }
    }

    /**
     * Processes node left.
     *
     * @param evt GridDiscoveryEvent to process.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void onNodeLeft(GridDiscoveryEvent evt) {
        assert evt != null;
        assert evt.type() == EVT_NODE_LEFT;

        UUID leftNodeId = evt.eventNodeId();

        Collection<String> cloudIds = evt.shadow().attribute(ATTR_CLOUD_IDS);

        if (F.isEmpty(cloudIds))
            return;

        synchronized (mux) {
            for (String cloudId : cloudIds)
                if (leftNodeId.equals(crdsMap.get(cloudId)))
                    crdsMap.remove(cloudId);
        }

        // Node has left, so we can only intercept coordinatorship of currently inactive clouds
        Collection<String> checkCloudIds = new HashSet<String>();

        for (CloudDescriptor desc : descrsMap.values())
            synchronized (desc) {
                String cloudId = desc.getSpi().getCloudId();

                if (!desc.isActive() && cloudIds.contains(cloudId))
                    checkCloudIds.add(cloudId);
            }

        if (!F.isEmpty(checkCloudIds))
            checkCoordinatorship(checkCloudIds);
    }

    /**
     * Processes cloud commands for given cloud.
     *
     * @param cloudId Cloud ID.
     * @param reqId Grid cloud control request ID. Null if request came from local node.
     * @param cmds Commands to execute.
     * @param senderId ID of the node which sent the request. Null if request came from local node.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void process(String cloudId, @Nullable UUID reqId, Map<UUID, GridCloudCommand> cmds,
        @Nullable UUID senderId) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(cmds);

        CloudDescriptor descr = descrsMap.get(cloudId);

        if (descr == null) {
            U.error(log, "Received command batch for unknown cloud (will skip batch) [cloudId=" + cloudId +
                ", cmds=" + cmds + ']');

            if (senderId != null && reqId != null)
                sendCloudControlResponse(cloudId, reqId, senderId, RS_UNKNOWN_CLOUD);

            sendCommandEvents(cloudId, cmds, EVT_CLOUD_COMMAND_FAILED,
                "Received command batch for unknown cloud [cloudId=" + cloudId + ']');

            return;
        }

        synchronized (descr) {
            GridCloudSpi spi = descr.getSpi();

            if (!descr.isActive()) {
                if (senderId != null && reqId != null)
                    sendCloudControlResponse(cloudId, reqId, senderId, RS_NOT_CRD);

                sendCommandEvents(cloudId, cmds, EVT_CLOUD_COMMAND_FAILED,
                    "Command batch received by non coordinator node.");

                return;
            }

            if (senderId != null && reqId != null)
                sendCloudControlResponse(cloudId, reqId, senderId, RS_OK);

            Collection<String> ids = new LinkedList<String>();

            for (Map.Entry<UUID, GridCloudCommand> e : cmds.entrySet()) {
                UUID cmdExecId = e.getKey();
                GridCloudCommand cmd = e.getValue();

                String id = cmd.id();

                assert !ids.contains(id);

                if (descr.isCommandExecuting(id)) {
                    if (log.isDebugEnabled())
                        log.debug("Command with same ID is executing now [cmd=" + cmd + ']');

                    sendCommandEvent(cloudId, cmdExecId, cmd, EVT_CLOUD_COMMAND_REJECTED_BY_ID,
                        "Command with same ID is executing now.");

                    continue;
                }

                if (!F.isEmpty(plcDescrs)) {
                    boolean acc = true;

                    for (CloudEnabledDescriptor<GridCloudPolicy> plcDescr : plcDescrs)
                        synchronized (plcDescr) {
                            GridCloudPolicy p = plcDescr.getObject();

                            if (p.isEnabledFor(cloudId)) {
                                if (!p.accept(cloudId, cmd)) {
                                    acc = false;

                                    break;
                                }
                            }
                        }

                    if (!acc) {
                        if (log.isDebugEnabled())
                            log.debug("Command is not allowed by cloud policy [cmd=" + cmd + ']');

                        sendCommandEvent(cloudId, cmdExecId, cmd, EVT_CLOUD_COMMAND_REJECTED_BY_POLICY,
                            "Command is not allowed by cloud policy.");

                        continue;
                    }
                }

                try {
                    spi.process(cmd, cmdExecId);

                    if (log.isDebugEnabled())
                        log.debug("Handled cloud command [cmd=" + cmd + ']');

                    ids.add(id);

                    descr.registerCommand(id);
                }
                catch (GridSpiException ex) {
                    U.error(log, "Failed to handle cloud command [cmd=" + cmd + ']', ex);

                    sendCommandEvent(cloudId, cmdExecId, cmd, EVT_CLOUD_COMMAND_FAILED, ex.getMessage());
                }
            }
        }
    }

    /**
     * Responds to {@link GridCloudControlRequest}.
     *
     * @param cloudId Cloud ID.
     * @param reqId Grid cloud control request ID. Null if request came from local node.
     * @param senderId Sender node ID. Null if request came from local node.
     * @param respStatus Request result.
     */
    private void sendCloudControlResponse(String cloudId, UUID reqId, UUID senderId, int respStatus) {
        assert cloudId != null;
        assert reqId != null;
        assert senderId != null;
        assert respStatus > 0;

        GridCloudControlResponse res = new GridCloudControlResponse(cloudId, locNodeId, reqId,
                respStatus);

        if (log.isDebugEnabled())
            log.debug("Responding to GridCloudControlRequest [res=" + res + ']');

        try {
            ctx.io().send(senderId, TOPIC_CLOUD_CONTROL, res, SYSTEM_POOL);
        }
        catch (GridException ex) {
            U.error(log, "Responding to GridCloudControlRequest failed [res=" + res + ']', ex);
        }
    }

    /**
     * Processes SPI command execution notification.
     *
     * @param cloudId Cloud ID.
     * @param success Flag of successful command execution.
     * @param cmdExecId Command execution ID.
     * @param cmd Cloud command.
     * @param msg Optional message.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void onCommandProcessed(String cloudId, boolean success, UUID cmdExecId, GridCloudCommand cmd, @Nullable
        String msg) {
        assert !F.isEmpty(cloudId);
        assert cmdExecId != null;
        assert cmd != null;

        CloudDescriptor descr = descrsMap.get(cloudId);

        assert descr != null;

        synchronized (descr) {
            descr.deregisterCommand(cmd.id());
        }

        if (log.isDebugEnabled())
            log.debug("Command has been processed by SPI [cloudId=" + cloudId + ", success=" + success +
                ", cmdExecId=" + cmdExecId + ", cmd=" + cmd + ", msg=" + msg + ']');

        int evtType = success ? EVT_CLOUD_COMMAND_EXECUTED : EVT_CLOUD_COMMAND_FAILED;

        sendCommandEvent(cloudId, cmdExecId, cmd, evtType, msg);
    }

    /**
     * Sends command execution event messages.
     *
     * @param cloudId Cloud ID.
     * @param cmds Map with command execution IDs as a key and cloud commands as a value.
     * @param evtType Event type.
     * @param msg Optional message.
     */
    private void sendCommandEvents(String cloudId, Map<UUID, GridCloudCommand> cmds, @Positive int evtType,
        @Nullable String msg) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(cmds);
        assert evtType > 0;

        for (Map.Entry<UUID, GridCloudCommand> e : cmds.entrySet())
            sendCommandEvent(cloudId, e.getKey(), e.getValue(), evtType, msg);
    }

    /**
     * Sends command execution event message.
     *
     * @param cloudId Cloud ID.
     * @param cmdExecId Cloud command execution ID.
     * @param cmd Cloud command.
     * @param evtType Event type.
     * @param msg Optional message.
     */
    private void sendCommandEvent(String cloudId, UUID cmdExecId, GridCloudCommand cmd, @Positive int evtType,
        @Nullable String msg) {
        assert !F.isEmpty(cloudId);
        assert cmdExecId != null;
        assert cmd != null;
        assert evtType > 0;

        sendCloudMessage(new GridCloudMessage(cloudId, cmdExecId, evtType, msg));
    }

    /**
     * Processes command execution event message.
     *
     * @param msg Cloud message to process.
     */
    private void processCommandEventMessage(GridCloudMessage msg) {
        assert msg != null;
        assert msg.getType() == CMD_EVT_TYPE;

        int evtType = msg.getEventType();

        if (ctx.event().isRecordable(evtType)) {
            GridEvent evt = new GridCloudEvent(locNodeId, msg.getMessage(), evtType, msg.getCloudId(),
                msg.getCommandExecutionId());

            ctx.event().record(evt);
        }
    }

    /**
     * Processes SPI cloud snapshot notification.
     *
     * @param cloudId Cloud ID.
     * @param snp Cloud snapshot.
     * @param initSnp Value {@code true} specifies that snapshot has been received
     *     after cloud coordinator activation. Value {@code false} specifies that snapshot has
     *     been received after cloud has been changed.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void onCloudSnapshot(String cloudId, GridCloudSpiSnapshot snp, boolean initSnp) {
        assert !F.isEmpty(cloudId);
        assert snp != null;

        CloudDescriptor descr = descrsMap.get(cloudId);

        assert descr != null;

        GridCloudMessage msg = null;

        Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> diff = null;

        synchronized (descr) {
            if (descr.isActive()) {
                if (initSnp && descr.getLastStateMessage() != null)
                    // Skip initial snapshot if SPI already changed cloud state by listener.
                    return;

                GridCloudSpiSnapshot lastSnp = descr.getLastSnapshot();

                if (lastSnp != null)
                    diff = descr.getSpi().compare(lastSnp, snp);

                if (lastSnp == null || !diff.isEmpty()) {
                    if (log.isDebugEnabled())
                        log.debug("Got new SPI cloud snapshot [cloudId=" + cloudId + ", snapshot=" + snp + ']');

                    descr.incrementVersion();

                    msg = new GridCloudMessage(cloudId, descr.getVersion(), snp);

                    descr.setLastStateMessage(msg);
                    descr.setLastSnapshot(snp);
                }
            }
        }

        if (msg != null)
            sendCloudMessage(msg);

        if (!F.isEmpty(diff))
            sendResourceEvents(cloudId, diff);
    }

    /**
     * Sends resource change events.
     *
     * @param cloudId Cloud ID.
     * @param diff Difference of cloud snapshots comparison.
     */
    private void sendResourceEvents(String cloudId,
        Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> diff) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(diff);

        for (GridTuple2<GridCloudResource, GridCloudSpiResourceAction> t : diff) {
            GridCloudResource rsrc = t.get1();
            GridCloudSpiResourceAction act = t.get2();

            int evtType = 0;

            GridCloudResourceShadow shadow = null;

            if (act == ADDED)
                evtType = EVT_CLOUD_RESOURCE_ADDED;
            else if (act == CHANGED)
                evtType = EVT_CLOUD_RESOURCE_CHANGED;
            else if (act == REMOVED) {
                evtType = EVT_CLOUD_RESOURCE_REMOVED;

                shadow = new GridCloudResourceShadowAdapter(rsrc);
            }

            assert evtType != 0;

            sendCloudMessage(new GridCloudMessage(cloudId, rsrc.id(), rsrc.type(), evtType, shadow));
        }
    }

    /**
     * Processes resource event message.
     *
     * @param msg Cloud message to process.
     */
    private void processResourceEventMessage(GridCloudMessage msg) {
        assert msg != null;
        assert msg.getType() == RSRC_EVT_TYPE;

        if (ctx.event().isRecordable(msg.getEventType())) {
            GridEvent evt = new GridCloudEvent(locNodeId, null, msg.getEventType(), msg.getCloudId(),
                msg.getResourceId(), msg.getResourceType(), msg.getShadow());

            ctx.event().record(evt);
        }
    }

    /**
     * Sends cloud message.
     *
     * @param msg Cloud message.
     * @param nodes Grid nodes to send message.
     */
    private void sendCloudMessage(GridCloudMessage msg, GridNode... nodes) {
        assert msg != null;

        try {
            ctx.io().send(F.isEmpty(nodes) ? ctx.discovery().allNodes() : Arrays.asList(nodes),
                TOPIC_CLOUD_STATE, msg, SYSTEM_POOL);
        }
        catch (GridException e) {
            U.error(log, "Failed to send cloud message [msg=" + msg + ", nodes=" + Arrays.toString(nodes) + ']', e);
        }
    }

    /**
     * Processes cloud snapshot message.
     *
     * @param msg Cloud message to process.
     */
    private void processCloudSnapshotMessage(GridCloudMessage msg) {
        assert msg != null;
        assert msg.getType() == LAST_CLOUD_STATE_TYPE;

        String cloudId = msg.getCloudId();
        long ver = msg.getVersion();
        GridCloudSpiSnapshot snp = msg.getSnapshot();

        assert !F.isEmpty(cloudId);
        assert ver > 0;
        assert snp != null;

        GridCloudMessage lastVerToCrd = null;

        synchronized (mux) {
            GridCloudAdapter old = cloudMap.get(cloudId);

            if (old != null && old.version() >= ver) {
                if (log.isDebugEnabled())
                    log.debug("Received message with old cloud state (will skip) [currentVersion=" + old.version()
                        + ", msg=" + msg + ']');

                lastVerToCrd = new GridCloudMessage(cloudId, old.version());
            }
            else {
                if (old != null) {
                    old.version(ver);
                    old.resources(snp.getResources());
                    old.parameters(snp.getParameters());
                }
                else {
                    GridCloudAdapter cloud = new GridCloudAdapter(cloudId, ver, snp.getResources(), snp.getParameters());

                    cloudMap.put(cloudId, cloud);

                    try {
                        ObjectName mb = U.registerMBean(mBeanSrv, gridName, "Clouds", cloudId,
                            new GridCloudMBeanAdapter(cloud), GridCloudMBean.class);

                        cloudMBeans.add(mb);

                        if (log.isDebugEnabled())
                            log.debug("Registered cloud MBean: " + mb);
                    }
                    catch (JMException e) {
                        U.error(log, "Failed to register cloud MBean.", e);
                    }
                }

                crdsMap.put(cloudId, snp.getCoordinatorNodeId());

                mux.notifyAll();
            }
        }

        if (lastVerToCrd != null) {
            UUID crdId = snp.getCoordinatorNodeId();

            assert crdId != null;

            sendCloudMessage(lastVerToCrd, ctx.discovery().node(crdId));
        }
        else {
            if (ctx.event().isRecordable(EVT_CLOUD_CHANGED)) {
                GridEvent evt = new GridCloudEvent(locNodeId, null, EVT_CLOUD_CHANGED, cloudId, ver);

                ctx.event().record(evt);
            }
        }
    }

    /**
     * Processes cloud version and state messages.
     *
     * @param msg Cloud message to process.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void processCloudStateMessage(GridCloudMessage msg) {
        assert msg != null;
        assert msg.getType() == LAST_CLOUD_STATE_TYPE || msg.getType() == LAST_CLOUD_VER_TYPE;

        CloudDescriptor descr = descrsMap.get(msg.getCloudId());

        if (descr != null) {
            GridCloudMessage stateMsg = null;

            synchronized (descr) {
                long oldVer = descr.getVersion();

                long newVer = msg.getVersion();

                if (descr.isActive()) {
                    if (oldVer <= newVer) {
                        descr.setVersion(newVer);
                        descr.incrementVersion();

                        if (descr.getLastStateMessage() != null) {
                            stateMsg = descr.getLastStateMessage();

                            stateMsg.setVersion(descr.getVersion());
                        }
                    }
                }
                else
                    if (oldVer < newVer) {
                        descr.setVersion(newVer);

                        if (msg.getType() == LAST_CLOUD_STATE_TYPE) {
                            GridCloudSpiSnapshot snp = msg.getSnapshot();

                            if (log.isDebugEnabled())
                                log.debug("Received message with new snapshot [snapshot=" + snp + ']');

                            descr.setLastSnapshot(snp);
                        }
                    }
            }

            if (stateMsg != null)
                sendCloudMessage(stateMsg);
        }
    }

    /**
     * Checks whether the node should be coordinator for every cloud from provided collection.
     *
     * @param cloudIds Cloud IDs to check coordinatorship.
     */
    private void checkCoordinatorship(Iterable<String> cloudIds) {
        for (String cloudId : cloudIds)
            try {
                checkCoordinatorship(cloudId);
            }
            catch (GridException e) {
                U.error(log, "Failed to check cloud coordinator [cloudId=" + cloudId + ']', e);
            }
    }

    /**
     * Checks whether the node should be coordinator for cloud with passed cloud ID.
     *
     * @param cloudId Cloud ID.
     * @throws GridException Thrown if any exception occurs.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void checkCoordinatorship(String cloudId) throws GridException {
        assert !F.isEmpty(cloudId);

        GridNode crd = resolveCoordinator(cloudId);

        assert crd != null;

        CloudDescriptor desc = descrsMap.get(cloudId);

        assert desc != null;

        GridCloudSpiSnapshot snapshot = null;

        synchronized (desc) {
            if (crd.id().equals(locNodeId)) {
                if (!desc.isActive())
                    try {
                        if (log.isInfoEnabled())
                            log.info("Activating local cloud coordinator [cloudId=" + cloudId + ", nodeId=" + locNodeId + ']');

                        snapshot = desc.activate();

                        activateCloudEnabledObjects(cloudId);
                    }
                    catch (GridSpiException e) {
                        throw new GridException("Failed to activate cloud coordinator [cloudId=" + cloudId + ']', e);
                    }
            }
            else {
                if (desc.isActive()) {
                    if (log.isInfoEnabled())
                        log.info("Deactivating local cloud coordinator [cloudId=" + cloudId + ", nodeId=" + locNodeId + ']');

                    desc.deactivate();

                    deactivateCloudEnabledObjects(cloudId);
                }
            }
        }

        if (snapshot != null)
            onCloudSnapshot(cloudId, snapshot, true);
    }

    /**
     * Activates cloud enabled objects for passed cloud ID.
     *
     * @param cloudId Cloud ID.
     * @throws GridException Thrown if any exception occurs.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void activateCloudEnabledObjects(String cloudId) throws GridException {
        assert !F.isEmpty(cloudId);

        if (!F.isEmpty(plcDescrs))
            for (CloudEnabledDescriptor<GridCloudPolicy> plcDescr : plcDescrs)
                synchronized (plcDescr) {
                    plcDescr.activate(cloudId);
                }

        if (!F.isEmpty(sgyDescrs))
            for (CloudEnabledDescriptor<GridCloudStrategy> sgyDescr : sgyDescrs)
                synchronized (sgyDescr) {
                    sgyDescr.activate(cloudId);
                }
    }

    /**
     * Deactivates cloud enabled object for passed cloud ID.
     *
     * @param cloudId Cloud ID.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void deactivateCloudEnabledObjects(String cloudId) {
        assert !F.isEmpty(cloudId);

        if (!F.isEmpty(sgyDescrs))
            for (CloudEnabledDescriptor<GridCloudStrategy> sgyDescr : sgyDescrs)
                synchronized (sgyDescr) {
                    sgyDescr.deactivate(cloudId);
                }

        if (!F.isEmpty(plcDescrs))
            for (CloudEnabledDescriptor<GridCloudPolicy> plcDescr : plcDescrs)
                synchronized (plcDescr) {
                    plcDescr.deactivate(cloudId);
                }
    }

    /**
     * Attempts to get coordinator locally, then resolves it through all nodes.
     *
     * @param cloudId Cloud ID.
     * @return Node which is a cloud coordinator.
     */
    @Nullable private GridNode getCoordinator(String cloudId) {
        assert cloudId != null;

        UUID crdId;

        synchronized (mux) {
            crdId = crdsMap.get(cloudId);
        }

        if (log.isDebugEnabled())
            log.debug("Trying to resolve coordinator ID locally [crdId=" + crdId + ", cloudId=" + cloudId + ']');

        GridNode crdNode = null;

        if (crdId != null)
            crdNode = ctx.discovery().node(crdId);

        if (crdNode == null) {
            crdNode = resolveCoordinator(cloudId);

            if (log.isDebugEnabled())
                log.debug("Local resolve failed. Tried to resolve coordinator node [crdNode=" + crdNode +
                    ", cloudId=" + cloudId + ']');
        }

        return crdNode;
    }

    /**
     * Guaranteed way to find cloud coordinator or ensure that it is absent in the current topology.
     * <p>Gets coordinator by iterating over all nodes in the grid that may be the coordinator of
     * the given cloud and choosing the node with the most up time.
     *
     * @param cloudId Cloud ID.
     * @return Node which is currently a cloud coordinator.
     */
    @Nullable private GridNode resolveCoordinator(final String cloudId) {
        assert cloudId != null;

        Collection<GridNode> crds = F.retain(ctx.discovery().allNodes(), true, new P1<GridNode>() {
            @Override public boolean apply(GridNode gridNode) {
                Collection<String> ids = gridNode.attribute(ATTR_CLOUD_IDS);

                return !F.isEmpty(ids) && ids.contains(cloudId);
            }
        });

        if (crds.isEmpty())
            return null;

        return Collections.max(crds, crdComp);
    }

    /**
     * Waits for cloud updates.
     * <p>Waits until information on {@code cloudId} cloud (or all clouds on the grid in case of {@code null cloudId})
     * comes but no more than {@link GridConfiguration#getNetworkTimeout()} period of time.
     *
     * @param cloudId Optional cloud ID.
     */
    private void waitForCloudUpdates(@Nullable String cloudId) {
        Collection<String> cloudIds = new LinkedHashSet<String>();

        if (F.isEmpty(cloudId)) {
            for (GridNode node : ctx.discovery().allNodes()) {
                Collection<String> ids = node.attribute(ATTR_CLOUD_IDS);

                if (ids != null)
                    cloudIds.addAll(ids);
            }

            if (cloudIds.isEmpty())
                return;
        }
        else
            cloudIds.add(cloudId);

        long timeout = ctx.config().getNetworkTimeout();

        long end = System.currentTimeMillis() + timeout;

        synchronized (mux) {
            while (!cloudMap.keySet().containsAll(cloudIds) && end > System.currentTimeMillis())
                try {
                    mux.wait(timeout);
                }
                catch (InterruptedException ignored) {
                    U.warn(log, "Interrupted while cloud updates waiting.");

                    return;
                }
        }
    }

    /**
     * Gets cloud by ID.
     * <p>Method may wait until information on {@code cloudId} cloud comes but no more than
     * {@link GridConfiguration#getNetworkTimeout()} period of time.
     *
     * @param cloudId Cloud ID.
     * @return {@link GridCloud} or {@code null} if cloud ID is unknown on the grid.
     */
    @Nullable public GridCloud cloud(String cloudId) {
        assert !F.isEmpty(cloudId);

        waitForCloudUpdates(cloudId);

        synchronized (mux) {
            return cloudMap.get(cloudId);
        }
    }

    /**
     * Gets cloud by resource ID.
     * <p>Method may wait until information on all clouds on the grid comes but no more than
     * {@link GridConfiguration#getNetworkTimeout()} period of time.
     *
     * @param rsrcId Cloud resource ID.
     * @return {@link GridCloud} or {@code null} if resource with provided ID
     * is not found in clouds on the grid.
     */
    @Nullable public GridCloud cloudByResourceId(String rsrcId) {
        assert !F.isEmpty(rsrcId);

        GridPredicate<GridCloudResource> p = F.resource(rsrcId);

        waitForCloudUpdates(null);

        synchronized (mux) {
            for (GridCloudAdapter cloud : cloudMap.values())
                if (cloud.resource(p) != null)
                    return cloud;
        }

        return null;
    }

    /**
     * Gets all clouds known on the grid.
     * <p>Method may wait until information on all clouds on the grid comes but no more than
     * {@link GridConfiguration#getNetworkTimeout()} period of time.
     *
     * @return Collection of clouds (may be empty but never {@code null}).
     */
    public Collection<GridCloud> clouds() {
        waitForCloudUpdates(null);

        synchronized (mux) {
            return new LinkedList<GridCloud>(cloudMap.values());
        }
    }

    /**
     * Gets cloud commands resend attempts number.
     *
     * @return Cloud commands resend attempts number.
     */
    public int getCommandsResendAttempts() {
        return cmdsResendAttempts;
    }

    /**
     * Sets cloud commands resend attempts number.
     *
     * @param cmdsResendAttempts Cloud commands resend attempts number (non-negative).
     */
    public void setCommandsResendAttempts(@NonNegative int cmdsResendAttempts) {
        assert cmdsResendAttempts >= 0;

        this.cmdsResendAttempts = cmdsResendAttempts;
    }

    /**
     * Cloud descriptor.
     */
    private static class CloudDescriptor {
        /** Cloud version increment. */
        private static final int CLOUD_VER_INCREMENT = 10000;

        /** Cloud version. */
        private long ver = -1;

        /** Last state cloud message. */
        private GridCloudMessage lastStateMsg;

        /** Last cloud snapshot. */
        private GridCloudSpiSnapshot lastSnp;

        /** Executing command IDs. */
        private final Collection<String> execCmdIds = new LinkedList<String>();

        /** Cloud SPI. */
        private final GridCloudSpi spi;

        /** Cloud coordinator activity flag. */
        private boolean active;

        /**
         * Creates cloud descriptor.
         *
         * @param spi Cloud SPI.
         */
        private CloudDescriptor(GridCloudSpi spi) {
            assert spi != null;

            this.spi = spi;
        }

        /**
         * Gets cloud SPI.
         *
         * @return Cloud SPI.
         */
        private GridCloudSpi getSpi() {
            return spi;
        }

        /**
         * Gets last state cloud message.
         *
         * @return Last state cloud message.
         */
        private GridCloudMessage getLastStateMessage() {
            assert Thread.holdsLock(this);

            return lastStateMsg;
        }

        /**
         * Sets last state cloud message.
         *
         * @param lastStateMsg Last cloud update message.
         */
        private void setLastStateMessage(GridCloudMessage lastStateMsg) {
            assert Thread.holdsLock(this);
            assert lastStateMsg != null;

            this.lastStateMsg = lastStateMsg;
        }

        /**
         * Gets last cloud snapshot.
         *
         * @return Last cloud snapshot.
         */
        private GridCloudSpiSnapshot getLastSnapshot() {
            assert Thread.holdsLock(this);

            return lastSnp;
        }

        /**
         * Sets last cloud snapshot.
         *
         * @param lastSnp Last cloud snapshot.
         */
        private void setLastSnapshot(GridCloudSpiSnapshot lastSnp) {
            assert Thread.holdsLock(this);
            assert lastSnp != null;

            this.lastSnp = lastSnp;
        }

        /**
         * Gets cloud version.
         *
         * @return Cloud version.
         */
        private long getVersion() {
            assert Thread.holdsLock(this);

            return ver;
        }

        /**
         * Sets cloud version.
         *
         * @param ver Cloud version.
         */
        private void setVersion(long ver) {
            assert Thread.holdsLock(this);
            assert ver > 0;

            this.ver = ver;
        }

        /**
         * Increments cloud version.
         */
        private void incrementVersion() {
            assert Thread.holdsLock(this);

            ver++;
        }

        /**
         * Registers command execution.
         *
         * @param cmdId Command ID.
         */
        private void registerCommand(String cmdId) {
            assert Thread.holdsLock(this);
            assert !F.isEmpty(cmdId);
            assert !execCmdIds.contains(cmdId);

            execCmdIds.add(cmdId);
        }

        /**
         * Deregisters command execution that determines that command with this ID has been executed.
         *
         * @param cmdId Command ID to deregister.
         * @return {@code true} if command ID has been deregistered.
         */
        private boolean deregisterCommand(String cmdId) {
            assert Thread.holdsLock(this);
            assert !F.isEmpty(cmdId);

            return execCmdIds.remove(cmdId);
        }

        /**
         * Checks whether the command with passed ID is executing now.
         *
         * @param cmdId Executed command ID.
         * @return {@code true} if command is executing now, {@code false} - otherwise.
         */
        private boolean isCommandExecuting(String cmdId) {
            assert Thread.holdsLock(this);
            assert !F.isEmpty(cmdId);

            return execCmdIds.contains(cmdId);
        }

        /**
         * Gets cloud coordinator activity flag.
         *
         * @return {@code true} if coordinator for this cloud is active, {@code false} - otherwise.
         */
        private boolean isActive() {
            assert Thread.holdsLock(this);

            return active;
        }

        /**
         * Activates SPI.
         *
         * @return Cloud snapshot.
         * @throws GridSpiException Thrown if any exception occurs.
         */
        private GridCloudSpiSnapshot activate() throws GridSpiException {
            assert Thread.holdsLock(this);
            assert !active;

            GridCloudSpiSnapshot snp = spi.activate();

            ver += CLOUD_VER_INCREMENT;

            active = true;

            return snp;
        }

        /**
         * Deactivates SPI.
         */
        private void deactivate() {
            assert Thread.holdsLock(this);
            assert active;

            spi.deactivate();

            lastStateMsg = null;

            active = false;

            execCmdIds.clear();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(CloudDescriptor.class, this);
        }
    }

    /**
     * Cloud enabled object descriptor.
     */
    private static class CloudEnabledDescriptor<T extends GridCloudEnabled> {
        /** Cloud enabled object. */
        private T obj;

        /** Activation calls counter. */
        private int callCnt;

        /**
         * Creates descriptor.
         *
         * @param obj Cloud enabled object.
         */
        private CloudEnabledDescriptor(T obj) {
            assert obj != null;

            this.obj = obj;
        }

        /**
         * Gets cloud enabled object.
         *
         * @return Cloud enabled object.
         */
        private T getObject() {
            assert Thread.holdsLock(this);

            return obj;
        }

        /**
         * Activates cloud enabled object if it is enabled for the passed cloud ID
         * and has not been activated earlier.
         *
         * @param cloudId ID of cloud for which coordinator has been activated.
         * @throws GridException Thrown if any exception occurs.
         */
        private void activate(String cloudId) throws GridException {
            assert Thread.holdsLock(this);
            assert !F.isEmpty(cloudId);

            if (obj.isEnabledFor(cloudId)) {
                if (callCnt == 0)
                    obj.activate();

                callCnt++;
            }
        }

        /**
         * Deactivates cloud enabled object if it is enabled for the passed cloud ID and should not be activated for
         * other clouds.
         *
         * @param cloudId ID of cloud for which coordinator has been deactivated.
         */
        @SuppressWarnings({"NotifyNotInSynchronizedContext"})
        private void deactivate(String cloudId) {
            assert Thread.holdsLock(this);
            assert !F.isEmpty(cloudId);

            if (obj.isEnabledFor(cloudId)) {
                assert callCnt > 0;

                callCnt--;

                if (callCnt == 0)
                    obj.deactivate();

                notifyAll();
            }
        }

        /**
         * Gets cloud enabled object activity flag.
         *
         * @return Activity flag.
         */
        private boolean isActive() {
            assert Thread.holdsLock(this);

            return callCnt > 0;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(CloudEnabledDescriptor.class, this);
        }
    }

    /**
     * Compares two grid nodes and tells what node is more suitable for being cloud coordinator.
     * More suitable node is greater than less suitable (comparator will return 0)
     */
    @SuppressWarnings({"ComparatorNotSerializable"})
    private static class CoordinatorComparator implements Comparator<GridNode> {
        /** {@inheritDoc} */
        @SuppressWarnings({"IfMayBeConditional"})
        @Override public int compare(GridNode crd1, GridNode crd2) {
            assert crd1 != null;
            assert crd2 != null;

            // We can use only these node attributes for coordinator choosing because we get them with nodes.
            // To get any others it is necessary to send separate requests to every node, that is not applicable
            // for this task.
            if (crd2.metrics().getNodeStartTime() < crd1.metrics().getNodeStartTime())
                return -1;
            else if (crd2.metrics().getNodeStartTime() == crd1.metrics().getNodeStartTime())
                return crd2.id().compareTo(crd1.id());
            else
                return 1;
        }
    }
}
