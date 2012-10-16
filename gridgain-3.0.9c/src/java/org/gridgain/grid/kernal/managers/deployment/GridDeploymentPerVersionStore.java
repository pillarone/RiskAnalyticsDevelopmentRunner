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
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridDeploymentMode.*;
import static org.gridgain.grid.GridEventType.*;

/**
 * Deployment storage for {@link GridDeploymentMode#SHARED} and
 * {@link GridDeploymentMode#CONTINUOUS} modes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDeploymentPerVersionStore extends GridDeploymentStoreAdapter {
    /** Shared deployment cache. */
    private Map<String, List<SharedDeployment>> cache = new HashMap<String, List<SharedDeployment>>();

    /** Set of obsolete class loaders. */
    private Collection<UUID> deadClsLdrs = new GridBoundedLinkedHashSet<UUID>(1000);

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
    GridDeploymentPerVersionStore(GridDeploymentSpi spi, GridKernalContext ctx, GridDeploymentCommunication comm) {
        super(spi, ctx, comm);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        ctxLdr = U.detectClassLoader(getClass());

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        Collection<SharedDeployment> copy = new HashSet<SharedDeployment>();

        synchronized (mux) {
            for (List<SharedDeployment> deps : cache.values())
                for (SharedDeployment dep : deps) {
                    // Mark undeployed.
                    dep.undeploy();

                    copy.add(dep);
                }

            cache.clear();
        }

        for (SharedDeployment dep : copy)
            dep.recordUndeployed();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                if (evt.type() == EVT_NODE_LEFT ||
                    evt.type() == EVT_NODE_FAILED) {
                    GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                    Collection<SharedDeployment> undeployed = new LinkedList<SharedDeployment>();

                    if (log.isDebugEnabled())
                        log.debug("Processing node departure event: " + evt);

                    synchronized (mux) {
                        for (Iterator<List<SharedDeployment>> i1 = cache.values().iterator(); i1.hasNext();) {
                            List<SharedDeployment> deps = i1.next();

                            for (Iterator<SharedDeployment> i2 = deps.iterator(); i2.hasNext();) {
                                SharedDeployment dep = i2.next();

                                dep.removeParticipant(discoEvt.eventNodeId());

                                if (!dep.hasParticipants()) {
                                    if (dep.deployMode() == SHARED) {
                                        if (!dep.isUndeployed()) {
                                            dep.undeploy();

                                            // Undeploy.
                                            i2.remove();

                                            assert !dep.isRemoved();

                                            dep.onRemoved();

                                            undeployed.add(dep);

                                            if (log.isDebugEnabled())
                                                log.debug("Undeployed class loader as there are no participating " +
                                                    "nodes: " + dep);
                                        }
                                    }
                                    else if (log.isDebugEnabled())
                                        log.debug("Preserving deployment without node participants: " + dep);
                                }
                                else if (log.isDebugEnabled())
                                    log.debug("Keeping deployment as it still has participants: " + dep);
                            }

                            if (deps.isEmpty())
                                i1.remove();
                        }
                    }

                    recordUndeployed(undeployed);
                }
            }
        };

        ctx.event().addLocalEventListener(discoLsnr,
            EVT_NODE_FAILED,
            EVT_NODE_LEFT
        );

        Collection<SharedDeployment> undeployed = new LinkedList<SharedDeployment>();

        synchronized (mux) {
            for (Iterator<List<SharedDeployment>> i1 = cache.values().iterator(); i1.hasNext();) {
                List<SharedDeployment> deps = i1.next();

                for (Iterator<SharedDeployment> i2 = deps.iterator(); i2.hasNext();) {
                    SharedDeployment dep = i2.next();

                    for (UUID nodeId : dep.getParticipantNodeIds())
                        if (ctx.discovery().node(nodeId) == null)
                            dep.removeParticipant(nodeId);

                    if (!dep.hasParticipants()) {
                        if (dep.deployMode() == SHARED) {
                            if (!dep.isUndeployed()) {
                                dep.undeploy();

                                // Undeploy.
                                i2.remove();

                                dep.onRemoved();

                                undeployed.add(dep);

                                if (log.isDebugEnabled())
                                    log.debug("Undeployed class loader as there are no participating nodes: " + dep);
                            }
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Preserving deployment without node participants: " + dep);
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Keeping deployment as it still has participants: " + dep);
                }

                if (deps.isEmpty())
                    i1.remove();
            }
        }

        recordUndeployed(undeployed);

        if (log.isDebugEnabled())
            log.debug("Registered deployment discovery listener: " + discoLsnr);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        if (discoLsnr != null) {
            ctx.event().removeLocalEventListener(discoLsnr);

            if (log.isDebugEnabled())
                log.debug("Unregistered deployment discovery listener: " + discoLsnr);
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridDeployment> getDeployments() {
        Collection<GridDeployment> deps = new LinkedList<GridDeployment>();

        synchronized (mux) {
            for (List<SharedDeployment> list : cache.values())
                for (SharedDeployment d : list)
                    deps.add(d);
        }

        return deps;
    }

    /** {@inheritDoc} */
    @Override public GridDeployment getDeployment(final UUID ldrId) {
        synchronized (mux) {
            return F.find(F.flat(cache.values()), null, new P1<SharedDeployment>() {
                @Override public boolean apply(SharedDeployment d) {
                    return d.classLoaderId().equals(ldrId);
                }
            });
        }
    }

    /** {@inheritDoc} */
    @Override public GridDeployment getDeployment(GridDeploymentMetadata meta) {
        assert meta != null;

        assert ctx.config().isPeerClassLoadingEnabled();

        // Validate metadata.
        assert meta.classLoaderId() != null;
        assert meta.senderNodeId() != null;
        assert meta.sequenceNumber() >= -1;
        assert meta.parentLoader() == null;

        if (log.isDebugEnabled())
            log.debug("Starting to peer-load class based on deployment metadata: " + meta);

        while (true) {
            List<SharedDeployment> depsToCheck = null;

            SharedDeployment dep = null;

            synchronized (mux) {
                // Check obsolete request.
                if (isDeadClassLoader(meta))
                    return null;

                List<SharedDeployment> deps = cache.get(meta.userVersion());

                if (deps != null) {
                    assert !deps.isEmpty();

                    for (SharedDeployment d : deps) {
                        if (d.hasParticipant(meta.senderNodeId(), meta.classLoaderId()) ||
                            meta.senderNodeId().equals(ctx.localNodeId())) {
                            // Done.
                            dep = d;

                            break;
                        }
                    }

                    if (dep == null) {
                        GridTuple2<Boolean, SharedDeployment> redeployCheck = checkRedeploy(meta);

                        if (!redeployCheck.get1()) {
                            // Checking for redeployment encountered invalid state.
                            if (log.isDebugEnabled())
                                log.debug("Checking for redeployment encountered invalid state: " + meta);

                            return null;
                        }

                        dep = redeployCheck.get2();

                        if (dep == null) {
                            // Find existing deployments that need to be checked
                            // whether they should be reused for this request.
                            for (SharedDeployment d : deps) {
                                if (!d.isPendingUndeploy() && !d.isUndeployed()) {
                                    if (depsToCheck == null)
                                        depsToCheck = new LinkedList<SharedDeployment>();

                                    if (log.isDebugEnabled())
                                        log.debug("Adding deployment to check: " + d);

                                    depsToCheck.add(d);
                                }
                            }

                            // If no deployment can be reused, create a new one.
                            if (depsToCheck == null) {
                                dep = createNewDeployment(meta, false);

                                deps.add(dep);
                            }
                        }
                    }
                }
                else {
                    GridTuple2<Boolean, SharedDeployment> redeployCheck = checkRedeploy(meta);

                    if (!redeployCheck.get1()) {
                        // Checking for redeployment encountered invalid state.
                        if (log.isDebugEnabled())
                            log.debug("Checking for redeployment encountered invalid state: " + meta);

                        return null;
                    }

                    dep = redeployCheck.get2();

                    if (dep == null)
                        // Create peer class loader.
                        dep = createNewDeployment(meta, true);
                }
            }

            if (dep != null) {
                if (log.isDebugEnabled())
                    log.debug("Found SHARED or CONTINUOUS deployment after first check: " + dep);

                // Cache the deployed class.
                Class<?> cls = dep.deployedClass(meta.className(), meta.alias());

                if (cls == null) {
                    U.warn(log, "Failed to load peer class [alias=" + meta.alias() + ", dep=" + dep + ']');

                    return null;
                }

                return dep;
            }

            assert meta.parentLoader() == null;
            assert depsToCheck != null;
            assert !depsToCheck.isEmpty();

            /*
             * Logic below must be performed outside of synchronization
             * because it involves network calls.
             */

            // Check if class can be loaded from existing nodes.
            // In most cases this loop will find something.
            for (SharedDeployment d : depsToCheck) {
                // Load class. Note, that remote node will not load this class.
                // The class will only be loaded on this node.
                Class<?> cls = d.deployedClass(meta.className(), meta.alias());

                if (cls != null) {
                    synchronized (mux) {
                        if (!d.isUndeployed() && !d.isPendingUndeploy()) {
                            if (!addParticipant(d, meta))
                                return null;

                            if (log.isDebugEnabled())
                                log.debug("Acquired deployment after verifying it's availability on " +
                                    "existing nodes [depCls=" + cls + ", dep=" + d + ", meta=" + meta + ']');

                            return d;
                        }
                    }
                }
                else if (log.isDebugEnabled()) {
                    log.debug("Deployment cannot be reused (class does not exist on participating nodes) [dep=" + d +
                        ", meta=" + meta + ']');
                }
            }

            // We are here either because all participant nodes failed
            // or the class indeed should have a separate deployment.
            for (SharedDeployment d : depsToCheck) {
                // Temporary class loader.
                ClassLoader temp = new GridDeploymentClassLoader(
                    UUID.randomUUID(),
                    meta.userVersion(),
                    meta.deploymentMode(),
                    true,
                    ctx,
                    ctxLdr,
                    meta.classLoaderId(),
                    meta.senderNodeId(),
                    meta.sequenceNumber(),
                    comm,
                    ctx.config().getNetworkTimeout(),
                    log,
                    ctx.config().getPeerClassLoadingClassPathExclude(),
                    0,
                    false);

                String path = U.classNameToResourceName(d.sampleClassName());

                // We check if any random class from existing deployment can be
                // loaded from sender node. If it can, then we reuse existing
                // deployment.
                InputStream rsrcIn = temp.getResourceAsStream(path);

                if (rsrcIn != null) {
                    // We don't need the actual stream.
                    U.close(rsrcIn, log);

                    synchronized (mux) {
                        if (d.isUndeployed() || d.isPendingUndeploy())
                            continue;

                        // Add new node prior to loading the class, so we attempt
                        // to load the class from the latest node.
                        if (!addParticipant(d, meta)) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to participant for deployment [meta=" + meta + ", dep=" + dep +
                                    ']');

                            return null;
                        }
                    }

                    Class<?> depCls = d.deployedClass(meta.className(), meta.alias());

                    if (depCls == null) {
                        U.error(log, "Failed to load peer class after loading it as a resource [alias=" +
                            meta.alias() + ", dep=" + dep + ']');

                        return null;
                    }

                    if (log.isDebugEnabled())
                        log.debug("Acquired deployment class after verifying other class " +
                            "availability on sender node [depCls=" + depCls + ", rndCls=" + d.sampleClass() +
                            ", sampleClsName=" + d.sampleClassName() + ", meta=" + meta + ']');

                    return d;
                }
                else if (log.isDebugEnabled())
                    log.debug("Deployment cannot be reused (random class could not be loaded from sender node) [dep=" +
                        d + ", meta=" + meta + ']');
            }

            synchronized (mux) {
                if (log.isDebugEnabled())
                    log.debug("None of the existing class-loaders fit (will try to create a new one): " + meta);

                // Check obsolete request.
                if (isDeadClassLoader(meta))
                    return null;

                // Check that deployment picture has not changed.
                List<SharedDeployment> deps = cache.get(meta.userVersion());

                if (deps != null) {
                    assert !deps.isEmpty();

                    boolean retry = false;

                    for (SharedDeployment d : deps) {
                        // Double check if sender was already added.
                        if (d.hasParticipant(meta.senderNodeId(), meta.classLoaderId())) {
                            dep = d;

                            retry = false;

                            break;
                        }

                        // New deployment was added while outside of synchronization.
                        // Need to recheck it again.
                        if (!d.isPendingUndeploy() && !d.isUndeployed() && !depsToCheck.contains(d))
                            retry = true;
                    }

                    if (retry) {
                        if (log.isDebugEnabled())
                            log.debug("Retrying due to concurrency issues: " + meta);

                        // Outer while loop.
                        continue;
                    }

                    if (dep == null) {
                        // No new deployments were added, so we can safely add ours.
                        dep = createNewDeployment(meta, false);

                        deps.add(dep);

                        if (log.isDebugEnabled())
                            log.debug("Adding new deployment within second check [dep=" + dep + ", meta=" + meta + ']');
                    }
                }
                else {
                    dep = createNewDeployment(meta, true);

                    if (log.isDebugEnabled())
                        log.debug("Created new deployment within second check [dep=" + dep + ", meta=" + meta + ']');
                }
            }

            if (dep != null) {
                // Cache the deployed class.
                Class<?> cls = dep.deployedClass(meta.className(), meta.alias());

                if (cls == null) {
                    U.warn(log, "Failed to load peer class [alias=" + meta.alias() + ", dep=" + dep + ']');

                    return null;
                }
            }

            return dep;
        }
    }

    /**
     * Records all undeployed tasks.
     *
     * @param undeployed Undeployed deployments.
     */
    private void recordUndeployed(Collection<SharedDeployment> undeployed) {
        if (!F.isEmpty(undeployed))
            for (SharedDeployment d : undeployed)
                d.recordUndeployed();
    }

    /**
     * @param meta Request metadata.
     * @return {@code True} if class loader is obsolete.
     */
    private boolean isDeadClassLoader(GridDeploymentMetadata meta) {
        assert Thread.holdsLock(mux);

        synchronized (mux) {
            if (deadClsLdrs.contains(meta.classLoaderId())) {
                if (log.isDebugEnabled())
                    log.debug("Ignoring request for obsolete class loader: " + meta);

                return true;
            }

            return false;
        }
    }

    /**
     * Adds new participant to deployment.
     *
     * @param dep Shared deployment.
     * @param meta Request metadata.
     * @return {@code True} if participant was added.
     */
    private boolean addParticipant(SharedDeployment dep, GridDeploymentMetadata meta) {
        assert dep != null;
        assert meta != null;

        assert Thread.holdsLock(mux);

        if (!checkModeMatch(dep, meta))
            return false;

        if (meta.participants() != null) {
            for (Map.Entry<UUID, GridTuple2<UUID, Long>> e : meta.participants().entrySet()) {
                dep.addParticipant(e.getKey(), e.getValue().get1(), e.getValue().get2());

                if (log.isDebugEnabled())
                    log.debug("Added new participant [nodeId=" + e.getKey() + ", clsLdrId=" + e.getValue().get1() +
                        ", seqNum=" + e.getValue().get2() + ']');
            }
        }

        if (dep.deployMode() == CONTINUOUS || meta.participants() == null) {
            if (!dep.addParticipant(meta.senderNodeId(), meta.classLoaderId(), meta.sequenceNumber())) {
                U.warn(log, "Failed to create shared mode deployment " +
                    "(requested class loader was already undeployed, did sender node leave grid?) " +
                    "[clsLdrId=" + meta.classLoaderId() + ", senderNodeId=" + meta.senderNodeId() + ']');

                return false;
            }

            if (log.isDebugEnabled())
                log.debug("Added new participant [nodeId=" + meta.senderNodeId() + ", clsLdrId=" +
                    meta.classLoaderId() + ", seqNum=" + meta.sequenceNumber() + ']');
        }

        return true;
    }

    /**
     * Checks if deployment modes match.
     *
     * @param dep Shared deployment.
     * @param meta Request metadata.
     * @return {@code True} if shared deployment modes match.
     */
    private boolean checkModeMatch(GridDeploymentInfo dep, GridDeploymentMetadata meta) {
        if (dep.deployMode() != meta.deploymentMode()) {
            U.warn(log, "Received invalid deployment mode (will not deploy, make sure that all nodes " +
                "executing the same classes in shared mode have identical GridDeploymentMode parameter) [mode=" +
                meta.deploymentMode() + ", expected=" + dep.deployMode() + ']');

            return false;
        }

        return true;
    }

    /**
     * Removes obsolete deployments in case of redeploy.
     *
     * @param meta Request metadata.
     * @return List of shares deployment.
     */
    private GridTuple2<Boolean, SharedDeployment> checkRedeploy(GridDeploymentMetadata meta) {
        assert Thread.holdsLock(mux);

        SharedDeployment newDep = null;

        for (List<SharedDeployment> deps : cache.values()) {
            for (SharedDeployment dep : deps) {
                if (!dep.isUndeployed() && !dep.isPendingUndeploy()) {
                    long undeployTimeout = ctx.config().getNetworkTimeout();

                    SharedDeployment doomed = null;

                    // Only check deployments with no participants.
                    if (!dep.hasParticipants()) {
                        assert dep.deployMode() == CONTINUOUS;

                        if (dep.existingDeployedClass(meta.className()) != null) {
                            // Change from shared deploy to shared undeploy or user version change.
                            // Simply remove all deployments with no participating nodes.
                            if (meta.deploymentMode() == SHARED || !meta.userVersion().equals(dep.userVersion()))
                                doomed = dep;
                        }
                    }
                    // If there are participants, we undeploy if class loader ID on some node changed.
                    else if (dep.existingDeployedClass(meta.className()) != null) {
                        GridTuple2<UUID, Long> ldr = dep.getClassLoaderId(meta.senderNodeId());

                        if (ldr != null) {
                            if (!ldr.get1().equals(meta.classLoaderId())) {
                                // If deployed sequence number is less, then schedule for undeployment.
                                if (ldr.get2() < meta.sequenceNumber()) {
                                    if (log.isDebugEnabled())
                                        log.debug("Received request for a class with newer sequence number " +
                                            "(will schedule current class for undeployment) [newSeq=" +
                                            meta.sequenceNumber() + ", oldSeq=" + ldr.get2() +
                                            ", senderNodeId=" + meta.senderNodeId() + ", newClsLdrId=" +
                                            meta.classLoaderId() + ", oldClsLdrId=" + ldr.get1() + ']');

                                    doomed = dep;
                                }
                                else if (ldr.get2() > meta.sequenceNumber()) {
                                    long time = System.currentTimeMillis() - dep.timestamp();

                                    if (newDep == null && time < ctx.config().getNetworkTimeout()) {
                                        // Set undeployTimeout, so the class will be scheduled
                                        // for undeployment.
                                        undeployTimeout = ctx.config().getNetworkTimeout() - time;

                                        if (log.isDebugEnabled())
                                            log.debug("Received execution request for a stale class (will deploy and " +
                                                "schedule undeployment in " + undeployTimeout + "ms) " + "[curSeq=" +
                                                ldr.get2() + ", staleSeq=" + meta.sequenceNumber() + ", cls=" +
                                                meta.className() + ", senderNodeId=" + meta.senderNodeId() +
                                                ", curLdrId=" + ldr.get1() + ", staleLdrId=" +
                                                meta.classLoaderId() + ']');

                                        // We got the redeployed class before the old one.
                                        // Simply create a temporary deployment for the sender node,
                                        // and schedule undeploy for it.
                                        newDep = createNewDeployment(meta, false);

                                        doomed = newDep;
                                    }
                                    else {
                                        U.warn(log, "Received execution request for a class that has been redeployed " +
                                            "(will ignore): " + meta.alias());

                                        if (log.isDebugEnabled())
                                            log.debug("Received execution request for a class that has been redeployed " +
                                                "(will ignore) [alias=" + meta.alias() + ", dep=" + dep + ']');

                                        return F.t(false, null);
                                    }
                                }
                                else {
                                    U.error(log, "Sequence number does not correspond to class loader ID [seqNum=" +
                                        meta.sequenceNumber() + ", dep=" + dep + ']');

                                    return F.t(false, null);
                                }
                            }
                        }
                    }

                    if (doomed != null) {
                        doomed.onUndeployScheduled();

                        if (log.isDebugEnabled())
                            log.debug("Deployment was scheduled for undeploy: " + doomed);

                        // Lifespan time.
                        final long endTime = System.currentTimeMillis() + undeployTimeout;

                        // Deployment to undeploy.
                        final SharedDeployment undep = doomed;

                        ctx.timeout().addTimeoutObject(new GridTimeoutObject() {
                            /**
                             * {@inheritDoc}
                             */
                            @Override
                            public UUID timeoutId() {
                                return undep.classLoaderId();
                            }

                            /**
                             * {@inheritDoc}
                             */
                            @Override
                            public long endTime() {
                                return endTime < 0 ? Long.MAX_VALUE : endTime;
                            }

                            /**
                             * {@inheritDoc}
                             */
                            @Override
                            public void onTimeout() {
                                boolean removed = false;

                                // Hot redeployment.
                                synchronized (mux) {
                                    assert undep.isPendingUndeploy();

                                    if (!undep.isUndeployed()) {
                                        undep.undeploy();

                                        undep.onRemoved();

                                        removed = true;

                                        Collection<SharedDeployment> deps = cache.get(undep.userVersion());

                                        if (deps != null) {
                                            for (Iterator<SharedDeployment> i = deps.iterator(); i.hasNext();)
                                                if (i.next() == undep)
                                                    i.remove();

                                            if (deps.isEmpty())
                                                cache.remove(undep.userVersion());
                                        }

                                        if (log.isInfoEnabled())
                                            log.info("Undeployed class loader due to deployment mode change, " +
                                                "user version change, or hot redeployment: " + undep);
                                    }
                                }

                                // Outside synchronization.
                                if (removed)
                                    undep.recordUndeployed();
                            }
                        });
                    }
                }
            }
        }

        if (newDep != null) {
            List<SharedDeployment> list = F.addIfAbsent(cache, meta.userVersion(), F.<SharedDeployment>newList());

            assert list != null;

            list.add(newDep);
        }

        return F.t(true, newDep);
    }

    /** {@inheritDoc} */
    @Override public void explicitUndeploy(UUID nodeId, String rsrcName) {
        Collection<SharedDeployment> undeployed = new LinkedList<SharedDeployment>();

        synchronized (mux) {
            for (Iterator<List<SharedDeployment>> i1 = cache.values().iterator(); i1.hasNext();) {
                List<SharedDeployment> deps = i1.next();

                for (Iterator<SharedDeployment> i2 = deps.iterator(); i2.hasNext();) {
                    SharedDeployment dep = i2.next();


                    if (dep.hasName(rsrcName)) {
                        if (!dep.isUndeployed()) {
                            dep.undeploy();

                            dep.onRemoved();

                            // Undeploy.
                            i2.remove();

                            undeployed.add(dep);

                            if (log.isInfoEnabled())
                                log.info("Undeployed per-version class loader: " + dep);
                        }

                        break;
                    }
                }

                if (deps.isEmpty())
                    i1.remove();
            }
        }

        recordUndeployed(undeployed);
    }

    /**
     * Creates and caches new deployment.
     *
     * @param meta Deployment metadata.
     * @param isCache Whether or not to cache.
     * @return New deployment.
     */
    private SharedDeployment createNewDeployment(GridDeploymentMetadata meta, boolean isCache) {
        assert Thread.holdsLock(mux);

        assert meta.parentLoader() == null;

        UUID ldrId = UUID.randomUUID();

        GridDeploymentClassLoader clsLdr;

        if (meta.deploymentMode() == CONTINUOUS || meta.participants() == null) {
            // Create peer class loader.
            // Note that we are passing empty list for local P2P exclude, as it really
            // does not make sense with shared deployment.
            clsLdr = new GridDeploymentClassLoader(
                ldrId,
                meta.userVersion(),
                meta.deploymentMode(),
                false,
                ctx,
                ctxLdr,
                meta.classLoaderId(),
                meta.senderNodeId(),
                meta.sequenceNumber(),
                comm,
                ctx.config().getNetworkTimeout(),
                log,
                ctx.config().getPeerClassLoadingClassPathExclude(),
                ctx.config().getPeerClassLoadingMissedResourcesCacheSize(),
                meta.deploymentMode() == CONTINUOUS /* enable class byte cache in CONTINUOUS mode */);

            if (meta.participants() != null)
                for (Map.Entry<UUID, GridTuple2<UUID, Long>> e : meta.participants().entrySet())
                    clsLdr.register(e.getKey(), e.getValue().get1(), e.getValue().get2());

            if (log.isDebugEnabled())
                log.debug("Created class loader in CONTINUOUS mode or without participants [ldr=" + clsLdr +
                    ", meta=" + meta + ']');
        }
        else {
            assert meta.deploymentMode() == SHARED;

            // Create peer class loader.
            // Note that we are passing empty list for local P2P exclude, as it really
            // does not make sense with shared deployment.
            clsLdr = new GridDeploymentClassLoader(
                ldrId,
                meta.userVersion(),
                meta.deploymentMode(),
                false,
                ctx,
                ctxLdr,
                meta.participants(),
                comm,
                ctx.config().getNetworkTimeout(),
                log,
                ctx.config().getPeerClassLoadingClassPathExclude(),
                ctx.config().getPeerClassLoadingMissedResourcesCacheSize(),
                false);

            if (log.isDebugEnabled())
                log.debug("Created classloader in SHARED mode with participants [ldr=" + clsLdr + ", meta=" + meta +
                    ']');
        }

        // Give this deployment a unique class loader to emphasize that this
        // ID is unique to this shared deployment and is not ID of loader on
        // sender node.
        SharedDeployment dep = new SharedDeployment(meta.deploymentMode(), clsLdr, ldrId, -1,
            meta.userVersion(), meta.alias());

        if (isCache) {
            List<SharedDeployment> deps = new LinkedList<SharedDeployment>();

            deps.add(dep);

            cache.put(meta.userVersion(), deps);
        }

        if (log.isDebugEnabled())
            log.debug("Created new deployment: " + dep);

        return dep;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentPerVersionStore.class, this);
    }

    /** */
    private class SharedDeployment extends GridDeployment {
        /** Flag indicating whether this deployment was removed from cache. */
        private boolean removed;

        /**
         * @param depMode Deployment mode.
         * @param clsLdr Class loader.
         * @param clsLdrId Class loader ID.
         * @param seqNum Sequence number (mostly meaningless for shared deployment).
         * @param userVer User version.
         * @param sampleClsName Sample class name.
         */
        @SuppressWarnings({"TypeMayBeWeakened"}) SharedDeployment(GridDeploymentMode depMode,
            GridDeploymentClassLoader clsLdr, UUID clsLdrId, long seqNum,
            String userVer, String sampleClsName) {
            super(depMode, clsLdr, clsLdrId, seqNum, userVer, sampleClsName, false);
        }

        /**
         * @return Deployment class loader.
         */
        private GridDeploymentClassLoader loader() {
            return (GridDeploymentClassLoader)classLoader();
        }

        /**
         * @param nodeId Grid node ID.
         * @param ldrId Class loader ID.
         * @param seqNum Sequence number for the class loader.
         * @return Whether actually added or not.
         */
        boolean addParticipant(UUID nodeId, UUID ldrId, long seqNum) {
            assert nodeId != null;
            assert ldrId != null;

            assert Thread.holdsLock(mux);

            synchronized (mux) {
                if (!deadClsLdrs.contains(ldrId)) {
                    loader().register(nodeId, ldrId, seqNum);

                    return true;
                }

                return false;
            }
        }

        /**
         * @param nodeId Node ID to remove.
         */
        void removeParticipant(UUID nodeId) {
            assert nodeId != null;

            assert Thread.holdsLock(mux);

            UUID ldrId = loader().unregister(nodeId);

            if (log.isDebugEnabled())
                log.debug("Registering dead class loader ID: " + ldrId);

            synchronized (mux) {
                deadClsLdrs.add(ldrId);
            }
        }

        /**
         * @return Set of participating nodes.
         */
        Collection<UUID> getParticipantNodeIds() {
            assert Thread.holdsLock(mux);

            return loader().registeredNodeIds();
        }

        /**
         * @param nodeId Node ID.
         * @return Class loader ID for node ID.
         */
        GridTuple2<UUID, Long> getClassLoaderId(UUID nodeId) {
            assert nodeId != null;

            assert Thread.holdsLock(mux);

            return loader().registeredClassLoaderId(nodeId);
        }

        /**
         * @return Registered class loader IDs.
         */
        Collection<UUID> getClassLoaderIds() {
            assert Thread.holdsLock(mux);

            return loader().registeredClassLoaderIds();
        }


        /**
         * @return {@code True} if deployment has any node participants.
         */
        boolean hasParticipants() {
            assert Thread.holdsLock(mux);

            return loader().hasRegisteredNodes();
        }

        /**
         * Checks if node is participating in deployment.
         *
         * @param nodeId Node ID to check.
         * @param ldrId Class loader ID.
         * @return {@code True} if node is participating in deployment.
         */
        boolean hasParticipant(UUID nodeId, UUID ldrId) {
            assert nodeId != null;
            assert ldrId != null;

            assert Thread.holdsLock(mux);

            return loader().hasRegisteredNode(nodeId, ldrId);
        }

        /**
         * Gets property removed.
         *
         * @return Property removed.
         */
        boolean isRemoved() {
            assert Thread.holdsLock(mux);

            return removed;
        }

        /**
         * Sets property removed.
         */
        void onRemoved() {
            assert Thread.holdsLock(mux);

            removed = true;

            Collection<UUID> deadIds = loader().registeredClassLoaderIds();

            if (log.isDebugEnabled())
                log.debug("Registering dead class loader IDs: " + deadIds);

            synchronized (mux) {
                deadClsLdrs.addAll(deadIds);
            }
        }

        /** {@inheritDoc} */
        @Override public void onDeployed(Class<?> cls) {
            assert !Thread.holdsLock(mux);

            boolean isTask = isTask(cls);

            String msg = (isTask ? "Task" : "Class") + " was deployed in SHARED or CONTINUOUS mode: " + cls;

            int type = isTask ? EVT_TASK_DEPLOYED : EVT_CLASS_DEPLOYED;

            if (ctx.event().isRecordable(type)) {
                GridDeploymentEvent evt = new GridDeploymentEvent();

                evt.nodeId(ctx.localNodeId());
                evt.message(msg);
                evt.type(type);
                evt.alias(cls.getName());

                ctx.event().record(evt);
            }

            if (log.isInfoEnabled())
                log.info(msg);
        }

        /**
         * Called to record all undeployed classes..
         */
        void recordUndeployed() {
            assert !Thread.holdsLock(mux);

            for (Map.Entry<String, Class<?>> depCls : deployedClassMap().entrySet()) {
                boolean isTask = isTask(depCls.getValue());

                String msg = (isTask ? "Task" : "Class") + " was undeployed in SHARED or CONTINUOUS mode: " +
                    depCls.getValue();

                int type = isTask ? EVT_TASK_UNDEPLOYED : EVT_CLASS_UNDEPLOYED;

                if (ctx.event().isRecordable(type)) {
                    GridDeploymentEvent evt = new GridDeploymentEvent();

                    evt.nodeId(ctx.localNodeId());
                    evt.message(msg);
                    evt.type(type);
                    evt.alias(depCls.getKey());

                    ctx.event().record(evt);
                }

                if (log.isInfoEnabled())
                    log.info(msg);
            }

            if (isObsolete()) {
                // Resource cleanup.
                ctx.resource().onUndeployed(this);

                ctx.cache().onUndeployed(loader());
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SharedDeployment.class, this, "super", super.toString());
        }
    }
}
