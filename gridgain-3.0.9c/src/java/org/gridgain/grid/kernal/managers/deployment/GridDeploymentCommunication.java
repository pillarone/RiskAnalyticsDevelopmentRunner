// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;

/**
 * Communication helper class. Provides request and response sending methods.
 * It uses communication manager as a way of sending and receiving requests.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"deprecation"})
@GridToStringExclude class GridDeploymentCommunication {
    /** */
    private final GridLogger log;

    /** */
    private final GridKernalContext ctx;

    /** */
    private GridMessageListener peerLsnr;

    /**
     * Creates new instance of deployment communication.
     *
     * @param ctx Kernal context.
     * @param log Logger.
     */
    GridDeploymentCommunication(GridKernalContext ctx, GridLogger log) {
        assert log != null;

        this.ctx = ctx;
        this.log = log.getLogger(getClass());
    }

    /**
     * Starts deployment communication.
     */
    void start() {
        peerLsnr = new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                assert nodeId != null;
                assert msg != null;

                GridDeploymentRequest req = (GridDeploymentRequest)msg;

                if (req.isUndeploy()) {
                    processUndeployRequest(nodeId, req);
                }
                else {
                    processResourceRequest(nodeId, req);
                }
            }

            /**
             * @param nodeId Sender node ID.
             * @param req Undeploy request.
             */
            private void processUndeployRequest(UUID nodeId, GridDeploymentRequest req) {
                if (log.isDebugEnabled()) {
                    log.debug("Received undeploy request [nodeId=" + nodeId + ", req=" + req + ']');
                }

                ctx.deploy().undeployTask(nodeId, req.getResourceName());
            }

            /**
             * Handles classes/resources requests.
             *
             * @param nodeId Originating node id.
             * @param req Request.
             */
            private void processResourceRequest(UUID nodeId, GridDeploymentRequest req) {
                if (log.isDebugEnabled()) {
                    log.debug("Received peer class/resources loading request [node=" + nodeId + ", req=" + req + ']');
                }

                String errMsg;

                GridDeploymentResponse res = new GridDeploymentResponse();

                GridDeployment dep = ctx.deploy().getDeployment(req.getClassLoaderId());

                // Null class loader means failure here.
                if (dep != null) {
                    InputStream in = dep.classLoader().getResourceAsStream(req.getResourceName());

                    if (in == null) {
                        errMsg = "Requested resource not found: " + req.getResourceName();

                        // Java requests the same class with BeanInfo suffix during
                        // introspection automatically. Usually nobody uses this kind
                        // of classes. Thus we print it out with DEBUG level.
                        // Also we print it with DEBUG level because of the
                        // frameworks which ask some classes just in case - for
                        // example to identify whether certain framework is available.
                        // Remote node will throw an exception if needs.
                        if (log.isDebugEnabled()) {
                            log.debug(errMsg);
                        }

                        res.setSuccess(false);
                        res.setErrorMessage(errMsg);
                    }
                    else {
                        try {
                            GridByteArrayList bytes = new GridByteArrayList(1024);

                            bytes.readAll(in);

                            res.setSuccess(true);
                            res.setByteSource(bytes);
                        }
                        catch (IOException e) {
                            errMsg = "Failed to read resource due to IO failure: " + req.getResourceName();

                            log.error(errMsg, e);

                            res.setErrorMessage(errMsg);
                            res.setSuccess(false);
                        }
                        finally {
                            U.close(in, log);
                        }
                    }
                }
                else {
                    errMsg = "Failed to find local deployment for peer request: " + req;

                    U.warn(log, errMsg);

                    res.setSuccess(false);
                    res.setErrorMessage(errMsg);
                }

                sendResponse(nodeId, req.getResponseTopic(), res);
            }

            /**
             * @param nodeId Destination node ID.
             * @param topic Response topic.
             * @param res Response.
             */
            private void sendResponse(UUID nodeId, String topic, Serializable res) {
                GridNode node = ctx.discovery().node(nodeId);

                if (node != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Sending peer class loading response [node=" + node.id() + ", res=" + res + ']');
                    }

                    try {
                        ctx.io().send(node, topic, res, GridIoPolicy.P2P_POOL);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to send response to node:" + nodeId, e);
                    }
                }
                else {
                    U.error(log, "Failed to send response (node does not exist): " + nodeId);
                }
            }
        };

        ctx.io().addMessageListener(TOPIC_CLASSLOAD, peerLsnr);
    }

    /**
     * Stops deployment communication.
     */
    void stop() {
        ctx.io().removeMessageListener(TOPIC_CLASSLOAD, peerLsnr);
    }

    /**
     * @param rsrcName Resource to undeploy.
     * @throws GridException If request could not be sent.
     */
    void sendUndeployRequest(String rsrcName) throws GridException {
        Serializable req = new GridDeploymentRequest(null, rsrcName, true);

        Collection<GridNode> rmtNodes = ctx.discovery().remoteNodes();

        if (!rmtNodes.isEmpty()) {
            ctx.io().send(
                rmtNodes,
                TOPIC_CLASSLOAD,
                req,
                GridIoPolicy.P2P_POOL
            );
        }
    }

    /**
     * Sends request to the remote node and wait for response. If there is
     * no response until endTime returns null.
     *
     * @param rsrcName Resource name.
     * @param clsLdrId Class loader ID.
     * @param dstNode Remote node request should be sent to.
     * @param endTime Time in milliseconds when request is decided to
     *      be obsolete.
     * @return Either response value or {@code null} if timeout occurred.
     * @throws GridException Thrown if there is no connection with remote node.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"}) GridDeploymentResponse sendResourceRequest(
        final String rsrcName, UUID clsLdrId, final GridNode dstNode,
        long endTime) throws GridException {
        assert rsrcName != null;
        assert dstNode != null;
        assert clsLdrId != null;

        String resTopic = TOPIC_CLASSLOAD.name(UUID.randomUUID());

        GridDeploymentRequest req = new GridDeploymentRequest(clsLdrId, rsrcName, false);

        req.setResponseTopic(resTopic);

        final Object qryMux = new Object();

        final GridTuple<GridDeploymentResponse> res = F.t1();

        GridLocalEventListener discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                UUID nodeId = discoEvt.eventNodeId();

                if ((evt.type() == EVT_NODE_LEFT || evt.type() == EVT_NODE_FAILED) &&
                    nodeId.equals(dstNode.id())) {
                    GridDeploymentResponse fake = new GridDeploymentResponse();

                    String errMsg = "Originating node left grid (resource will not be peer-loaded)"
                        + "[nodeId=" + dstNode.id() + ", rsrc=" + rsrcName + ']';

                    U.warn(log, errMsg);

                    fake.setSuccess(false);
                    fake.setErrorMessage(errMsg);

                    // We put fake result here to interrupt waiting peer-to-peer thread
                    // because originating node has left grid.
                    synchronized (qryMux) {
                        res.set(fake);

                        qryMux.notifyAll();
                    }
                }
            }
        };

        GridMessageListener resLsnr = new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                assert nodeId != null;
                assert msg != null;

                synchronized (qryMux) {
                    if (!(msg instanceof GridDeploymentResponse)) {
                        U.error(log, "Received unknown peer class loading response [node=" + nodeId + ", msg=" +
                            msg + ']');
                    }
                    else {
                        if (log.isDebugEnabled()) {
                            log.debug("Received peer loading response [node=" + nodeId + ", res=" + msg + ']');
                        }

                        res.set((GridDeploymentResponse)msg);
                    }

                    qryMux.notifyAll();
                }
            }
        };

        try {
            ctx.io().addMessageListener(resTopic, resLsnr);

            // The destination node has potentially left grid here but in this case
            // Communication manager will throw the exception while sending message.
            ctx.event().addLocalEventListener(discoLsnr, EVT_NODE_FAILED, EVT_NODE_LEFT);

            if (log.isDebugEnabled()) {
                log.debug("Sending peer class loading request [node=" + dstNode.id() + ", req=" + req + ']');
            }

            long start = System.currentTimeMillis();

            ctx.io().send(dstNode, TOPIC_CLASSLOAD, req, GridIoPolicy.P2P_POOL);

            synchronized (qryMux) {
                try {
                    long delta = endTime - start;

                    if (log.isDebugEnabled()) {
                        log.debug("Waiting for peer response from node [time=" + delta + "ms, node=" +
                            dstNode.id() + ']');
                    }

                    while (res.get() == null && delta > 0) {
                        qryMux.wait(delta);

                        delta = endTime - System.currentTimeMillis();
                    }
                }
                catch (InterruptedException e) {
                    // Interrupt again to get it in the users code.
                    Thread.currentThread().interrupt();

                    throw new GridException("Got interrupted while waiting for response from node: " +
                        dstNode.id(), e);
                }
            }

            if (res.get() == null) {
                U.error(log, "Failed to receive peer response from node within " + (System.currentTimeMillis() - start) +
                    " ms: " + dstNode.id());
            }
            else if (log.isDebugEnabled()) {
                log.debug("Received peer response from node: " + dstNode.id());
            }

            return res.get();
        }
        finally {
            // Remove discovery listener.
            ctx.event().removeLocalEventListener(discoLsnr, EVT_NODE_FAILED, EVT_NODE_LEFT);

            ctx.io().removeMessageListener(resTopic, resLsnr);
        }
    }
}
