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
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Storage for local deployments.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDeploymentLocalStore extends GridDeploymentStoreAdapter {
    /** Sequence. */
    private static final AtomicLong seq = new AtomicLong(0);

    /** Deployment cache by class name. */
    private final Map<String, LinkedList<GridDeployment>> cache =
        new HashMap<String, LinkedList<GridDeployment>>();

    /** Context class loader. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private ClassLoader ctxLdr;

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * @param spi Deployment SPI.
     * @param ctx Grid kernal context.
     * @param comm Deployment communication.
     */
    GridDeploymentLocalStore(GridDeploymentSpi spi, GridKernalContext ctx, GridDeploymentCommunication comm) {
        super(spi, ctx, comm);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        ctxLdr = U.detectClassLoader(getClass());

        spi.setListener(new LocalDeploymentListener());

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        spi.setListener(null);

        Map<String, List<GridDeployment>> copy;

        synchronized (mux) {
            copy = new HashMap<String, List<GridDeployment>>(cache);

            for (Entry<String, List<GridDeployment>> entry : copy.entrySet()) {
                entry.setValue(new ArrayList<GridDeployment>(entry.getValue()));
            }
        }

        for (List<GridDeployment> deps : copy.values()) {
            for (GridDeployment cls : deps) {
                // We don't record event, as recording causes invocation of
                // discovery manager which is already stopped.
                undeploy(cls.classLoader(), /*don't record event. */false);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridDeployment> getDeployments() {
        Collection<GridDeployment> deps = new ArrayList<GridDeployment>();

        synchronized (mux) {
            for (List<GridDeployment> depList : cache.values()) {
                for (GridDeployment d : depList) {
                    if (!deps.contains(d)) {
                        deps.add(d);
                    }
                }
            }

            return deps;
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDeployment getDeployment(UUID ldrId) {
        synchronized (mux) {
            for (List<GridDeployment> deps : cache.values()) {
                for (GridDeployment dep : deps) {
                    if (dep.classLoaderId().equals(ldrId)) {
                        return dep;
                    }
                }
            }
        }

        for (GridDeployment dep : ctx.task().getUsedDeployments()) {
            if (dep.classLoaderId().equals(ldrId)) {
                return dep;
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Nullable @SuppressWarnings({"UnusedCatchParameter"})
    @Override public GridDeployment getDeployment(GridDeploymentMetadata meta) {
        GridDeployment dep;

        Class<?> cls = null;

        String alias = meta.alias();

        synchronized (mux) {
            // Validate metadata.
            assert meta.alias() != null;

            dep = getDeployment(meta.alias());

            if (dep != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Acquired deployment class from local cache: " + dep);
                }

                return dep;
            }

            GridDeploymentResource rsrc = spi.findResource(meta.alias());

            if (rsrc != null) {
                dep = deploy(ctx.config().getDeploymentMode(), rsrc.getClassLoader(), rsrc.getResourceClass(), alias);

                if (dep == null) {
                    return null;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Acquired deployment class from SPI: " + dep);
                }
            }
            // Auto-deploy.
            else {
                ClassLoader ldr = meta.classLoader();

                if (ldr == null) {
                    ldr = Thread.currentThread().getContextClassLoader();

                    // Safety.
                    if (ldr == null) {
                        ldr = ctxLdr;
                    }
                }

                // Don't auto-deploy locally in case of nested execution.
                if (ldr instanceof GridDeploymentClassLoader) {
                    return null;
                }

                try {
                    // Check that class can be loaded.
                    cls = ldr.loadClass(meta.alias());

                    spi.register(ldr, cls);

                    rsrc = spi.findResource(alias);

                    if (rsrc != null && rsrc.getResourceClass().equals(cls)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Retrieved auto-loaded resource from spi: " + rsrc);
                        }

                        dep = deploy(ctx.config().getDeploymentMode(), ldr, cls, alias);

                        if (dep == null) {
                            return null;
                        }
                    }
                    else {
                        U.warn(log, "Failed to find resource from deployment SPI even after registering it: " +
                            meta.alias());

                        return null;
                    }
                }
                catch (ClassNotFoundException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to load class for local auto-deployment [ldr=" + ldr + ", meta=" + meta + ']');
                    }

                    return null;
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to deploy local class: " + meta.alias(), e);

                    return null;
                }
            }
        }

        if (cls != null) {
            recordDeploy(cls, alias, meta.isRecord());

            dep.addDeployedClass(cls, meta.className(), meta.alias());
        }

        if (log.isDebugEnabled()) {
            log.debug("Acquired deployment class: " + dep);
        }

        return dep;
    }

    /**
     * @param alias Class alias.
     * @return Deployed class.
     */
    @Nullable private GridDeployment getDeployment(String alias) {
        assert Thread.holdsLock(mux);

        LinkedList<GridDeployment> deps = cache.get(alias);

        if (deps != null) {
            assert !deps.isEmpty();

            GridDeployment dep = deps.getFirst();

            if (!dep.isUndeployed()) {
                return dep;
            }
        }

        return null;
    }

    /**
     * @param depMode Deployment mode.
     * @param ldr Class loader to deploy.
     * @param cls Class.
     * @param alias Class alias.
     * @return Deployment.
     */
    @SuppressWarnings({"ConstantConditions"})
    private GridDeployment deploy(GridDeploymentMode depMode, ClassLoader ldr, Class<?> cls, String alias) {
        assert Thread.holdsLock(mux);

        LinkedList<GridDeployment> cachedDeps = null;

        GridDeployment dep = null;

        // Find existing class loader info.
        for (LinkedList<GridDeployment> deps : cache.values()) {
            for (GridDeployment d : deps) {
                if (d.classLoader() == ldr) {
                    // Cache class and alias.
                    d.addDeployedClass(cls, alias);

                    cachedDeps = deps;

                    dep = d;

                    break;
                }
            }

            if (cachedDeps != null) {
                break;
            }
        }

        if (cachedDeps != null) {
            assert dep != null;

            cache.put(alias, cachedDeps);

            if (!cls.getName().equals(alias)) {
                // Cache by class name as well.
                cache.put(cls.getName(), cachedDeps);
            }

            return dep;
        }

        UUID ldrId = UUID.randomUUID();

        long seqNum = seq.incrementAndGet();

        String userVer = getUserVersion(ldr);

        dep = new GridDeployment(depMode, ldr, ldrId, seqNum, userVer, cls.getName(), true);

        dep.addDeployedClass(cls, alias);

        LinkedList<GridDeployment> deps = F.addIfAbsent(cache, alias, F.<GridDeployment>newLinkedList());

        if (!deps.isEmpty()) {
            for (GridDeployment d : deps) {
                if (!d.isUndeployed()) {
                    U.error(log, "Found more than one active deployment for the same resource " +
                        "[cls=" + cls + ", depMode=" + depMode + ", dep=" + d + ']');

                    return null;
                }
            }
        }

        // Add at the beginning of the list for future fast access.
        deps.addFirst(dep);

        if (!cls.getName().equals(alias)) {
            // Cache by class name as well.
            cache.put(cls.getName(), deps);
        }

        if (log.isDebugEnabled()) {
            log.debug("Created new deployment: " + dep);
        }

        return dep;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDeployment explicitDeploy(Class<?> cls, ClassLoader clsLdr) throws GridException {
        try {
            // Make sure not to deploy peer loaded tasks with non-local class loader,
            // if local one exists.
            if (clsLdr.getClass().equals(GridDeploymentClassLoader.class))
                clsLdr = clsLdr.getParent();

            GridDeployment dep;

            synchronized (mux) {
                boolean deployed = spi.register(clsLdr, cls);

                if (deployed) {
                    dep = getDeployment(cls.getName());

                    if (dep == null) {
                        GridDeploymentResource rsrc = spi.findResource(cls.getName());

                        if (rsrc != null && rsrc.getClassLoader() == clsLdr) {
                            dep = deploy(ctx.config().getDeploymentMode(), rsrc.getClassLoader(),
                                rsrc.getResourceClass(), rsrc.getName());
                        }
                    }

                    if (dep != null) {
                        recordDeploy(cls, cls.getName(), true);
                    }
                }
                else {
                    dep = getDeployment(cls.getName());
                }
            }

            return dep;
        }
        catch (GridSpiException e) {
            recordDeployFailed(cls, clsLdr, true);

            // Avoid double wrapping.
            if (e.getCause() instanceof GridException) {
                throw (GridException)e.getCause();
            }

            throw new GridException("Failed to deploy class: " + cls.getName(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public void explicitUndeploy(UUID nodeId, String rsrcName) {
        assert rsrcName != null;

        // Simply delegate to SPI.
        // Internal cache will be cleared once undeployment callback is received from SPI.
        spi.unregister(rsrcName);
    }

    /**
     * Records deploy event.
     *
     * @param cls Deployed class.
     * @param alias Class alias.
     * @param recordEvt Flag indicating whether to record events.
     */
    private void recordDeploy(Class<?> cls, String alias, boolean recordEvt) {
        assert cls != null;

        boolean isTask = isTask(cls);

        String msg = (isTask ? "Task" : "Class") + " locally deployed: " + cls;

        if (recordEvt && ctx.event().isRecordable(isTask ? EVT_TASK_DEPLOYED : EVT_CLASS_DEPLOYED)) {
            GridDeploymentEvent evt = new GridDeploymentEvent();

            evt.message(msg);
            evt.nodeId(ctx.localNodeId());
            evt.type(isTask ? EVT_TASK_DEPLOYED : EVT_CLASS_DEPLOYED);
            evt.alias(alias);

            ctx.event().record(evt);
        }

        // Don't record JDK or Grid classes.
        if (U.isGrid(cls) || U.isJdk(cls))
            return;

        if (log.isInfoEnabled())
            log.info(msg);
    }

    /**
     * Records deploy event.
     *
     * @param cls Deployed class.
     * @param clsLdr Class loader.
     * @param recordEvt Flag indicating whether to record events.
     */
    @SuppressWarnings({"unchecked"})
    private void recordDeployFailed(Class<?> cls, ClassLoader clsLdr, boolean recordEvt) {
        assert cls != null;
        assert clsLdr != null;

        boolean isTask = isTask(cls);

        String msg = "Failed to deploy " + (isTask ? "task" : "class") + " [cls=" + cls + ", clsLdr=" + clsLdr + ']';

        if (recordEvt && ctx.event().isRecordable(isTask ? EVT_CLASS_DEPLOY_FAILED : EVT_TASK_DEPLOY_FAILED)) {
            String taskName = isTask ? U.getTaskName((Class<? extends GridTask<?, ?>>)cls) : null;

            GridDeploymentEvent evt = new GridDeploymentEvent();

            evt.message(msg);
            evt.nodeId(ctx.localNodeId());
            evt.type(isTask(cls) ? EVT_CLASS_DEPLOY_FAILED : EVT_TASK_DEPLOY_FAILED);
            evt.alias(taskName);

            ctx.event().record(evt);
        }

        if (log.isInfoEnabled()) {
            log.info(msg);
        }
    }

    /**
     * Records undeploy event.
     *
     * @param dep Undeployed class loader.
     * @param recordEvt Flag indicating whether to record events.
     */
    private void recordUndeploy(GridDeployment dep, boolean recordEvt) {
        assert dep.isUndeployed();

        if (ctx.event().isRecordable(EVT_TASK_UNDEPLOYED) ||
            ctx.event().isRecordable(EVT_CLASS_UNDEPLOYED)) {
            for (Class<?> cls : dep.deployedClasses()) {
                boolean isTask = isTask(cls);

                String msg = isTask ? "Task locally undeployed: " + cls : "Class locally undeployed: " + cls;

                if (ctx.event().isRecordable(isTask ? EVT_TASK_UNDEPLOYED : EVT_CLASS_UNDEPLOYED)) {
                    if (recordEvt) {
                        GridDeploymentEvent evt = new GridDeploymentEvent();

                        evt.message(msg);
                        evt.nodeId(ctx.localNodeId());
                        evt.type(isTask ? EVT_TASK_UNDEPLOYED : EVT_CLASS_UNDEPLOYED);
                        evt.alias(getAlias(dep, cls));

                        ctx.event().record(evt);
                    }
                }

                if (log.isInfoEnabled()) {
                    log.info(msg);
                }
            }
        }
    }

    /**
     * Gets alias for a class.
     *
     * @param dep Deployment.
     * @param cls Class.
     * @return Alias for a class.
     */
    private String getAlias(GridDeployment dep, Class<?> cls) {
        String alias = cls.getName();

        if (isTask(cls)) {
            GridTaskName ann = dep.annotation(cls, GridTaskName.class);

            if (ann != null) {
                alias = ann.value();
            }
        }

        return alias;
    }

    /**
     * @param ldr Class loader to undeploy.
     * @param recEvt Whether or not to record the event.
     */
    private void undeploy(ClassLoader ldr, boolean recEvt) {
        Collection<GridDeployment> doomed = new HashSet<GridDeployment>();

        synchronized (mux) {
            for (Iterator<LinkedList<GridDeployment>> i1 = cache.values().iterator(); i1.hasNext();) {
                LinkedList<GridDeployment> deps = i1.next();

                for (Iterator<GridDeployment> i2 = deps.iterator(); i2.hasNext();) {
                    GridDeployment dep = i2.next();

                    if (dep.classLoader() == ldr) {
                        dep.undeploy();

                        i2.remove();

                        doomed.add(dep);

                        if (log.isInfoEnabled()) {
                            log.info("Removed undeployed class: " + dep);
                        }
                    }
                }

                if (deps.isEmpty()) {
                    i1.remove();
                }
            }
        }

        for (GridDeployment dep : doomed) {
            if (dep.isObsolete()) {
                // Resource cleanup.
                ctx.resource().onUndeployed(dep);
            }

            if (recEvt) {
                recordUndeploy(dep, true);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentLocalStore.class, this);
    }

    /** */
    private class LocalDeploymentListener implements GridDeploymentListener {
        /** {@inheritDoc} */
        @Override public void onUnregistered(ClassLoader ldr) {
            if (log.isDebugEnabled()) {
                log.debug("Received callback from SPI to unregister class loader: " + ldr);
            }

            undeploy(ldr, true);
        }
    }
}
