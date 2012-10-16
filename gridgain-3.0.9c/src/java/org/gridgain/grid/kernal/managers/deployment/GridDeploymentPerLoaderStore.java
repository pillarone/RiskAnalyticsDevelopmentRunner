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
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Deployment storage for {@link GridDeploymentMode#PRIVATE} and
 * {@link GridDeploymentMode#ISOLATED} modes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDeploymentPerLoaderStore extends GridDeploymentStoreAdapter {
    /** Cache keyed by class loader ID. */
    private Map<UUID, IsolatedDeployment> cache = new HashMap<UUID, IsolatedDeployment>();

    /** Discovery listener. */
    private GridLocalEventListener discoLsnr;

    /** Context class loader. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private ClassLoader ctxLdr;

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * @param spi Underlying SPI.
     * @param ctx Grid kernal context.
     * @param comm Deployment communication.
     */
    GridDeploymentPerLoaderStore(GridDeploymentSpi spi, GridKernalContext ctx, GridDeploymentCommunication comm) {
        super(spi, ctx, comm);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        ctxLdr = U.detectClassLoader(getClass());

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        Collection<IsolatedDeployment> copy = new HashSet<IsolatedDeployment>();

        synchronized (mux) {
            for (IsolatedDeployment dep : cache.values()) {
                // Mark undeployed. This way if any event hits after stop,
                // undeployment won't happen twice.
                dep.undeploy();

                copy.add(dep);
            }

            cache.clear();
        }

        for (IsolatedDeployment dep : copy) {
            dep.recordUndeployed();
        }

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                UUID nodeId = ((GridDiscoveryEvent)evt).eventNodeId();

                if (evt.type() == EVT_NODE_LEFT ||
                    evt.type() == EVT_NODE_FAILED) {
                    Collection<IsolatedDeployment> rmv = new LinkedList<IsolatedDeployment>();

                    synchronized (mux) {
                        for (Iterator<IsolatedDeployment> iter = cache.values().iterator(); iter.hasNext();) {
                            IsolatedDeployment dep = iter.next();

                            if (dep.getSenderNodeId().equals(nodeId)) {
                                dep.undeploy();

                                iter.remove();

                                rmv.add(dep);
                            }
                        }
                    }

                    for (IsolatedDeployment dep : rmv) {
                        dep.recordUndeployed();
                    }
                }
            }
        };

        ctx.event().addLocalEventListener(discoLsnr,
            EVT_NODE_FAILED,
            EVT_NODE_LEFT
        );

        Collection<IsolatedDeployment> rmv = new LinkedList<IsolatedDeployment>();

        // Check existing deployments for presence of obsolete nodes.
        synchronized (mux) {
            for (Iterator<IsolatedDeployment> iter = cache.values().iterator(); iter.hasNext();) {
                IsolatedDeployment dep = iter.next();

                GridNode node = ctx.discovery().node(dep.getSenderNodeId());

                if (node == null) {
                    dep.undeploy();

                    iter.remove();

                    rmv.add(dep);
                }
            }
        }

        for (IsolatedDeployment dep : rmv) {
            dep.recordUndeployed();
        }

        if (log.isDebugEnabled()) {
            log.debug("Registered deployment discovery listener: " + discoLsnr);
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        if (discoLsnr != null) {
            ctx.event().removeLocalEventListener(discoLsnr);

            if (log.isDebugEnabled()) {
                log.debug("Unregistered deployment discovery listener: " + discoLsnr);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridDeployment> getDeployments() {
        synchronized (mux) {
            return new LinkedList<GridDeployment>(cache.values());
        }
    }

    /** {@inheritDoc} */
    @Override public GridDeployment getDeployment(UUID ldrId) {
        synchronized (mux) {
            return cache.get(ldrId);
        }
    }

    /** {@inheritDoc} */
    @Override public GridDeployment getDeployment(GridDeploymentMetadata meta) {
        assert meta != null;

        assert ctx.config().isPeerClassLoadingEnabled();

        // Validate metadata.
        assert meta.classLoaderId() != null;
        assert meta.senderNodeId() != null;
        assert meta.sequenceNumber() > 0;

        if (log.isDebugEnabled()) {
            log.debug("Starting to peer-load class based on deployment metadata: " + meta);
        }

        GridNode sender = ctx.discovery().node(meta.senderNodeId());

        if (sender == null) {
            U.warn(log, "Failed to create Private or Isolated mode deployment (sender node left grid): " + sender);

            return null;
        }

        IsolatedDeployment dep;

        synchronized (mux) {
            dep = cache.get(meta.classLoaderId());

            if (dep != null) {
                if (!dep.getSenderNodeId().equals(meta.senderNodeId())) {
                    U.error(log, "Sender node ID does not match for Private or Isolated deployment [expected=" +
                        meta.senderNodeId() + ", dep=" + dep + ']');

                    return null;
                }
            }
            else {
                long undeployTimeout = 0;
                
                // If could not find deployment, make sure to perform clean up.
                // Check if any deployments must be undeployed.
                for (IsolatedDeployment d : cache.values()) {
                    if (d.getSenderNodeId().equals(meta.senderNodeId()) &&
                        !d.isUndeployed() && !d.isPendingUndeploy()) {
                        if (d.sequenceNumber() < meta.sequenceNumber()) {
                            // Undeploy previous class deployments.
                            if (d.existingDeployedClass(meta.className()) != null) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Received request for a class with newer sequence number " +
                                        "(will schedule current class for undeployment) [cls=" +
                                        meta.className() + ", newSeq=" +
                                        meta.sequenceNumber() + ", oldSeq=" + d.sequenceNumber() +
                                        ", senderNodeId=" + meta.senderNodeId() + ", curClsLdrId=" +
                                        d.classLoaderId() + ", newClsLdrId=" +
                                        meta.classLoaderId() + ']');
                                }

                                scheduleUndeploy(d, ctx.config().getNetworkTimeout());
                            }
                        }
                        // If we received execution request even after we waited for P2P
                        // timeout period, we simply ignore it.
                        else if (d.sequenceNumber() > meta.sequenceNumber()) {
                            if (d.deployedClass(meta.className()) != null) {
                                long time = System.currentTimeMillis() - d.timestamp();

                                if (time < ctx.config().getNetworkTimeout()) {
                                    // Set undeployTimeout, so the class will be scheduled
                                    // for undeployment.
                                    undeployTimeout = ctx.config().getNetworkTimeout() - time;

                                    if (log.isDebugEnabled()) {
                                        log.debug("Received execution request for a stale class (will deploy and " +
                                            "schedule undeployment in " + undeployTimeout + "ms) " +
                                            "[cls=" + meta.className() + ", curSeq=" + d.sequenceNumber() +
                                            ", rcvdSeq=" + meta.sequenceNumber() + ", senderNodeId=" +
                                            meta.senderNodeId() + ", curClsLdrId=" + d.classLoaderId() +
                                            ", rcvdClsLdrId=" + meta.classLoaderId() + ']');
                                    }
                                }
                                else {
                                    U.warn(log, "Received execution request for a class that has been redeployed " +
                                        "(will ignore): " + meta.alias());

                                    return null;
                                }
                            }
                        }
                        else {
                            U.error(log, "Sequence number does not correspond to class loader ID [seqNum=" +
                                meta.sequenceNumber() + ", dep=" + d + ']');

                            return null;
                        }
                    }
                }

                ClassLoader parent = meta.parentLoader() == null ? ctxLdr : meta.parentLoader();

                // Safety.
                if (parent == null) {
                    parent = getClass().getClassLoader();
                }

                // Create peer class loader.
                ClassLoader clsLdr = new GridDeploymentClassLoader(
                    meta.classLoaderId(),
                    meta.userVersion(),
                    meta.deploymentMode(),
                    true,
                    ctx,
                    parent,
                    meta.classLoaderId(),
                    meta.senderNodeId(),
                    meta.sequenceNumber(),
                    comm,
                    ctx.config().getNetworkTimeout(),
                    log,
                    ctx.config().getPeerClassLoadingClassPathExclude(),
                    ctx.config().getPeerClassLoadingMissedResourcesCacheSize(),
                    false);

                dep = new IsolatedDeployment(meta.deploymentMode(), clsLdr, meta.classLoaderId(),
                    meta.sequenceNumber(), meta.userVersion(), meta.senderNodeId(), meta.className());

                cache.put(meta.classLoaderId(), dep);

                // In case if deploying stale class.
                if (undeployTimeout > 0) {
                    scheduleUndeploy(dep, undeployTimeout);
                }
            }
        }

        // Make sure that requested class is loaded and cached.
        if (dep != null) {
            Class<?> cls = dep.deployedClass(meta.className(), meta.alias());

            if (cls == null) {
                U.warn(log, "Failed to load peer class [alias=" + meta.alias() + ", dep=" + dep + ']');

                return null;
            }
        }

        return dep;
    }

    /**
     * Schedules existing deployment for future undeployment.
     *
     * @param dep Deployment.
     * @param timeout Timeout for undeployment to occur.
     */
    private void scheduleUndeploy(final IsolatedDeployment dep, final long timeout) {
        assert Thread.holdsLock(mux);

        if (!dep.isUndeployed() && !dep.isPendingUndeploy()) {
            dep.onUndeployScheduled();

            ctx.timeout().addTimeoutObject(new GridTimeoutObject() {
                /** End time. */
                private final long endTime = System.currentTimeMillis() + timeout;

                /** {@inheritDoc} */
                @Override public UUID timeoutId() { return dep.classLoaderId(); }

                /** {@inheritDoc} */
                @Override public long endTime() {
                    return endTime < 0 ? Long.MAX_VALUE : endTime;
                }

                /** {@inheritDoc} */
                @Override public void onTimeout() {
                    boolean removed = false;

                    // Hot redeployment.
                    synchronized (mux) {
                        if (!dep.isUndeployed()) {
                            dep.undeploy();

                            cache.remove(dep.classLoaderId());

                            removed = true;
                        }
                    }

                    if (removed) {
                        dep.recordUndeployed();
                    }
                }
            });
        }
    }

    /** {@inheritDoc} */
    @Override public void explicitUndeploy(UUID nodeId, String rsrcName) {
        assert nodeId != null;
        assert rsrcName != null;

        Collection<IsolatedDeployment> undeployed = new LinkedList<IsolatedDeployment>();

        synchronized (mux) {
            for (Iterator<IsolatedDeployment> iter = cache.values().iterator(); iter.hasNext();) {
                IsolatedDeployment dep = iter.next();

                if (dep.getSenderNodeId().equals(nodeId)) {
                    if (dep.hasName(rsrcName)) {
                        iter.remove();

                        dep.undeploy();

                        undeployed.add(dep);

                        if (log.isInfoEnabled()) {
                            log.info("Undeployed Private or Isolated deployment: " + dep);
                        }
                    }
                }
            }
        }

        for (IsolatedDeployment dep : undeployed) {
            dep.recordUndeployed();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentPerLoaderStore.class, this);
    }

    /** */
    private class IsolatedDeployment extends GridDeployment {
        /** Sender node ID. */
        private final UUID senderNodeId;

        /**
         * @param depMode Deployment mode.
         * @param clsLdr Class loader.
         * @param clsLdrId Class loader ID.
         * @param seqNum Sequence number.
         * @param userVer User version.
         * @param senderNodeId Sender node ID.
         * @param sampleClsName Sample class name.
         */
        IsolatedDeployment(GridDeploymentMode depMode, ClassLoader clsLdr, UUID clsLdrId, long seqNum, String userVer,
            UUID senderNodeId, String sampleClsName) {
            super(depMode, clsLdr, clsLdrId, seqNum, userVer, sampleClsName, false);

            this.senderNodeId = senderNodeId;
        }

        /**
         * Gets property senderNodeId.
         *
         * @return Property senderNodeId.
         */
        UUID getSenderNodeId() {
            return senderNodeId;
        }

        /** {@inheritDoc} */
        @Override public void onDeployed(Class<?> cls) {
            recordDeployed(cls, true);
        }

        /**
         * Called for every deployed class.
         *
         * @param cls Deployed class.
         * @param recordEvt Flag indicating whether to record events.
         */
        void recordDeployed(Class<?> cls, boolean recordEvt) {
            assert !Thread.holdsLock(mux);

            boolean isTask = isTask(cls);

            String msg = (isTask ? "Task" : "Class") + " was deployed in Private or Isolated mode: " + cls;

            if (recordEvt && ctx.event().isRecordable(isTask(cls) ? EVT_TASK_DEPLOYED : EVT_CLASS_DEPLOYED)) {
                GridDeploymentEvent evt = new GridDeploymentEvent();

                // Record task event.
                evt.type(isTask ? EVT_TASK_DEPLOYED : EVT_CLASS_DEPLOYED);
                evt.nodeId(senderNodeId);
                evt.message(msg);
                evt.alias(cls.getName());

                ctx.event().record(evt);
            }

            if (log.isInfoEnabled()) {
                log.info(msg);
            }
        }

        /**
         * Called to record all undeployed classes..
         */
        void recordUndeployed() {
            assert !Thread.holdsLock(mux);

            GridEventStorageManager evts = ctx.event();

            if (evts.isRecordable(EVT_CLASS_UNDEPLOYED) || evts.isRecordable(EVT_TASK_UNDEPLOYED)) {
                for (Map.Entry<String, Class<?>> depCls : deployedClassMap().entrySet()) {
                    boolean isTask = isTask(depCls.getValue());

                    String msg = (isTask ? "Task" : "Class") + " was undeployed in Private or Isolated mode: " +
                        depCls.getValue();

                    if (evts.isRecordable(!isTask ? EVT_CLASS_UNDEPLOYED : EVT_TASK_UNDEPLOYED)) {
                        GridDeploymentEvent evt = new GridDeploymentEvent();

                        evt.nodeId(senderNodeId);
                        evt.message(msg);
                        evt.type(!isTask ? EVT_CLASS_UNDEPLOYED : EVT_TASK_UNDEPLOYED);
                        evt.alias(depCls.getKey());

                        evts.record(evt);
                    }

                    if (log.isInfoEnabled()) {
                        log.info(msg);
                    }
                }
            }

            if (isObsolete()) {
                // Resource cleanup.
                ctx.resource().onUndeployed(this);

                ctx.cache().onUndeployed(classLoader());
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(IsolatedDeployment.class, this, "super", super.toString());
        }
    }
}
