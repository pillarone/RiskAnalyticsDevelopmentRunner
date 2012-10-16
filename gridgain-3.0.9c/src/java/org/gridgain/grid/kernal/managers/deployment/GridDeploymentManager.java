// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.deployment.protocol.gg.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Deployment manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDeploymentManager extends GridManagerAdapter<GridDeploymentSpi> {
    /** Local deployment storage. */
    private GridDeploymentStore locStore;

    /** Isolated mode storage. */
    private GridDeploymentStore ldrStore;

    /** Shared mode storage. */
    private GridDeploymentStore verStore;

    /** */
    private GridDeploymentCommunication comm;

    /**
     * @param ctx Grid kernal context.
     */
    public GridDeploymentManager(GridKernalContext ctx) {
        super(GridDeploymentSpi.class, ctx, ctx.config().getDeploymentSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        GridProtocolHandler.registerDeploymentManager(this);

        if (ctx.config().isPeerClassLoadingEnabled())
            assertParameter(ctx.config().getNetworkTimeout() > 0, "networkTimeout > 0");

        startSpi();

        comm = new GridDeploymentCommunication(ctx, log);

        comm.start();

        locStore = new GridDeploymentLocalStore(getSpi(), ctx, comm);
        ldrStore = new GridDeploymentPerLoaderStore(getSpi(), ctx, comm);
        verStore = new GridDeploymentPerVersionStore(getSpi(), ctx, comm);

        locStore.start();
        ldrStore.start();
        verStore.start();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        GridProtocolHandler.deregisterDeploymentManager();

        if (verStore != null)
            verStore.stop();

        if (ldrStore != null)
            ldrStore.stop();

        if (locStore != null)
            locStore.stop();

        if (comm != null)
            comm.stop();

        getSpi().setListener(null);

        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        locStore.onKernalStart();
        ldrStore.onKernalStart();
        verStore.onKernalStart();
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        if (verStore != null)
            verStore.onKernalStop();

        if (ldrStore != null)
            ldrStore.onKernalStop();

        if (locStore != null)
            locStore.onKernalStop();
    }

    /**
     * @param p Filtering predicate.
     * @return All deployed tasks for given predicate.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Class<? extends GridTask<?, ?>>> findAllTasks(
        @Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>... p) {
        Collection<GridDeployment> deps = locStore.getDeployments();

        Map<String, Class<? extends GridTask<?, ?>>> map = new HashMap<String, Class<? extends GridTask<?, ?>>>();

        for (GridDeployment dep : deps)
            for (Map.Entry<String, Class<?>> clsEntry : dep.deployedClassMap().entrySet())
                if (GridTask.class.isAssignableFrom(clsEntry.getValue())) {
                    Class<? extends GridTask<?, ?>> taskCls = (Class<? extends GridTask<?, ?>>)clsEntry.getValue();

                    if (F.isAll(taskCls, p))
                        map.put(clsEntry.getKey(), taskCls);
                }

        return map;
    }

    /**
     * @param taskName Task name.
     */
    public void undeployTask(String taskName) {
        assert taskName != null;

        locStore.explicitUndeploy(null, taskName);

        try {
            comm.sendUndeployRequest(taskName);
        }
        catch (GridException e) {
            U.error(log, "Failed to send undeployment request for task: " + taskName, e);
        }
    }

    /**
     * @param nodeId Node ID.
     * @param taskName Task name.
     */
    void undeployTask(UUID nodeId, String taskName) {
        assert taskName != null;

        locStore.explicitUndeploy(nodeId, taskName);
        ldrStore.explicitUndeploy(nodeId, taskName);
        verStore.explicitUndeploy(nodeId, taskName);
    }

    /**
     * @param cls Class to deploy.
     * @param clsLdr Class loader.
     * @throws GridException If deployment failed.
     * @return Grid deployment.
     */
    @Nullable public GridDeployment deploy(Class<?> cls, ClassLoader clsLdr) throws GridException {
        if (clsLdr == null)
            clsLdr = getClass().getClassLoader();

        if (clsLdr instanceof GridDeploymentClassLoader) {
            GridDeploymentInfo ldr = (GridDeploymentInfo)clsLdr;

            GridDeploymentMetadata meta = new GridDeploymentMetadata();

            meta.alias(cls.getName());
            meta.classLoader(clsLdr);

            // Check for nested execution. In that case, if task
            // is available locally by name, then we should ignore
            // class loader ID.
            GridDeployment d = locStore.getDeployment(meta);

            if (d == null) {
                d = ldrStore.getDeployment(ldr.classLoaderId());

                if (d == null)
                    d = verStore.getDeployment(ldr.classLoaderId());
            }

            return d;
        }
        else {
            return locStore.explicitDeploy(cls, clsLdr);
        }
    }

    /**
     * Gets class loader based on given ID.
     *
     * @param ldrId Class loader ID.
     * @return Class loader of {@code null} if not found.
     */
    @Nullable public GridDeployment getLocalDeployment(UUID ldrId) {
        return locStore.getDeployment(ldrId);
    }

    /**
     * Gets any deployment by loader ID.
     *
     * @param ldrId Loader ID.
     * @return Deployment for given ID.
     */
    @Nullable public GridDeployment getDeployment(UUID ldrId) {
        GridDeployment d = locStore.getDeployment(ldrId);

        if (d == null) {
            d = ldrStore.getDeployment(ldrId);

            if (d == null)
                d = verStore.getDeployment(ldrId);
        }

        return d;
    }

    /**
     * @param rsrcName Resource to find deployment for.
     * @return Found deployment or {@code null} if one was not found.
     */
    @Nullable public GridDeployment getDeployment(String rsrcName) {
        GridDeployment d = getLocalDeployment(rsrcName);

        if (d == null) {
            ClassLoader ldr = Thread.currentThread().getContextClassLoader();

            if (ldr instanceof GridDeploymentClassLoader) {
                GridDeploymentInfo depLdr = (GridDeploymentInfo)ldr;

                d = ldrStore.getDeployment(depLdr.classLoaderId());

                if (d == null)
                    d = verStore.getDeployment(depLdr.classLoaderId());
            }
        }

        return d;
    }

    /**
     * @param rsrcName Class name.
     * @return Grid cached task.
     */
    @Nullable public GridDeployment getLocalDeployment(String rsrcName) {
        GridDeploymentMetadata meta = new GridDeploymentMetadata();

        meta.record(true);
        meta.deploymentMode(ctx.config().getDeploymentMode());
        meta.alias(rsrcName);
        meta.className(rsrcName);
        meta.senderNodeId(ctx.localNodeId());

        return locStore.getDeployment(meta);
    }

    /**
     * @param depMode Deployment mode.
     * @param rsrcName Resource name (could be task name).
     * @param clsName Class name.
     * @param seqVer Sequence version.
     * @param userVer User version.
     * @param senderNodeId Sender node ID.
     * @param clsLdrId Class loader ID.
     * @param participants Node class loader participant map.
     * @param nodeFilter Node filter for class loader.
     * @return Deployment class if found.
     */
    @Nullable public GridDeployment getGlobalDeployment(
        GridDeploymentMode depMode,
        String rsrcName,
        String clsName,
        long seqVer,
        String userVer,
        UUID senderNodeId,
        UUID clsLdrId,
        Map<UUID, GridTuple2<UUID, Long>> participants,
        @Nullable GridPredicate<GridNode> nodeFilter) {

        GridDeploymentMetadata meta = new GridDeploymentMetadata();

        meta.deploymentMode(depMode);
        meta.className(clsName);
        meta.alias(rsrcName);
        meta.sequenceNumber(seqVer);
        meta.userVersion(userVer);
        meta.senderNodeId(senderNodeId);
        meta.classLoaderId(clsLdrId);
        meta.participants(participants);
        meta.nodeFilter(nodeFilter);

        if (!ctx.config().isPeerClassLoadingEnabled()) {
            meta.record(true);

            return locStore.getDeployment(meta);
        }

        // In shared mode, if class is locally available, we never load
        // from remote node simply because the class loader needs to be "shared".
        if (isPerVersionMode(meta.deploymentMode())) {
            meta.record(true);

            boolean reuse = true;

            // Check local exclusions.
            if (!senderNodeId.equals(ctx.localNodeId())) {
                String[] p2pExc = ctx.config().getPeerClassLoadingClassPathExclude();

                if (p2pExc != null) {
                    for (String rsrc : p2pExc) {
                        if (rsrc.equals(meta.alias()) || rsrc.equals(meta.className())) {
                            if (log.isDebugEnabled())
                                log.debug("Will not reuse local deployment because resource is excluded [meta=" +
                                    meta + ']');

                            reuse = false;

                            break;
                        }
                    }
                }
            }

            if (reuse) {
                GridDeployment locDep = locStore.getDeployment(meta);

                if (locDep != null) {
                    if (!isPerVersionMode(locDep.deployMode())) {
                        U.warn(log, "Failed to deploy class in SHARED or CONTINUOUS mode (class is locally deployed " +
                            "in some other mode). Either change GridConfiguration.getDeploymentMode() property to " +
                            "SHARED or CONTINUOUS or remove class from local classpath and any of " +
                            "the local GAR deployments that may have it [cls=" + meta.className() + ", depMode=" +
                            locDep.deployMode() + ']', "Failed to deploy class in SHARED or CONTINUOUS mode.");

                        return null;
                    }

                    if (!locDep.userVersion().equals(meta.userVersion())) {
                        U.warn(log, "Failed to deploy class in SHARED or CONTINUOUS mode for given user version " +
                            "(class is locally deployed for a different user version) [cls=" + meta.className() +
                            ", localVer=" + locDep.userVersion() +
                            ", otherVer=" + meta.userVersion() + ']',
                            "Failed to deploy class in SHARED or CONTINUOUS mode.");

                        return null;
                    }

                    if (log.isDebugEnabled())
                        log.debug("Reusing local deployment for shared mode: " + locDep);

                    return locDep;
                }
            }

            return verStore.getDeployment(meta);
        }

        // Private or Isolated mode.
        meta.record(false);

        GridDeployment dep = locStore.getDeployment(meta);

        if (senderNodeId.equals(ctx.localNodeId())) {
            if (dep == null)
                U.warn(log, "Task got undeployed while deployment was in progress: " + meta);

            // For local execution, return the same deployment as for the task.
            return dep;
        }

        if (dep != null)
            meta.parentLoader(dep.classLoader());

        meta.record(true);

        return ldrStore.getDeployment(meta);
    }

    /**
     * @param mode Mode to check.
     * @return {@code True} if shared mode.
     */
    private boolean isPerVersionMode(GridDeploymentMode mode) {
        return mode == GridDeploymentMode.CONTINUOUS || mode == GridDeploymentMode.SHARED;
    }

    /**
     * @param ldr Class loader to get id for.
     * @return Id for given class loader or {@code null} if given loader is not
     *      grid deployment class loader.
     */
    @Nullable public UUID getClassLoaderId(ClassLoader ldr) {
        assert ldr != null;

        if (ldr instanceof GridDeploymentClassLoader)
            return ((GridDeploymentInfo)ldr).classLoaderId();

        return null;
    }

    /**
     * @param ldr Loader to check.
     * @return {@code True} if P2P class loader.
     */
    public boolean isGlobalLoader(ClassLoader ldr) {
        return ldr instanceof GridDeploymentClassLoader;
    }
}
