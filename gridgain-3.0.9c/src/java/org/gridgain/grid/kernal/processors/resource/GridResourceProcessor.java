// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.resource;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import org.springframework.aop.framework.*;
import org.springframework.context.*;

import javax.management.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Processor for all Grid and task/job resources.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridResourceProcessor extends GridProcessorAdapter {
    /** Grid instance injector. */
    private GridResourceBasicInjector<Grid> gridInjector;

    /** GridGain home folder injector. */
    private GridResourceBasicInjector<String> ggHomeInjector;

    /** Grid name injector. */
    private GridResourceBasicInjector<String> ggNameInjector;

    /** Local host binding injector. */
    private GridResourceBasicInjector<String> locHostInjector;

    /** MBean server injector. */
    private GridResourceBasicInjector<MBeanServer> mbeanSrvInjector;

    /** Grid thread executor injector. */
    private GridResourceBasicInjector<Executor> execInjector;

    /** Grid marshaller injector. */
    private GridResourceBasicInjector<GridMarshaller> marshallerInjector;

    /** Local node ID injector. */
    private GridResourceBasicInjector<UUID> nodeIdInjector;

    /** Spring application context injector. */
    private GridResourceBasicInjector<ApplicationContext> springCtxInjector;

    /** Spring bean resources injector. */
    private GridResourceSpringBeanInjector springBeanInjector;

    /** Task resources injector. */
    private GridResourceCustomInjector customInjector;

    /** Logger. */
    private GridLogger injectLog;

    /** Cleaning injector. */
    private final GridResourceInjector nullInjector = new GridResourceBasicInjector<Object>(null);

    /** */
    private final GridResourceIoc ioc = new GridResourceIoc();

    /**
     * Creates resources processor.
     *
     * @param ctx Kernal context.
     */
    public GridResourceProcessor(GridKernalContext ctx) {
        super(ctx);

        injectLog = ctx.config().getGridLogger();

        gridInjector = new GridResourceBasicInjector<Grid>(ctx.grid());
        ggHomeInjector = new GridResourceBasicInjector<String>(ctx.config().getGridGainHome());
        ggNameInjector = new GridResourceBasicInjector<String>(ctx.config().getGridName());
        locHostInjector = new GridResourceBasicInjector<String>(ctx.config().getLocalHost());
        mbeanSrvInjector = new GridResourceBasicInjector<MBeanServer>(ctx.config().getMBeanServer());
        marshallerInjector = new GridResourceBasicInjector<GridMarshaller>(ctx.config().getMarshaller());
        execInjector = new GridResourceBasicInjector<Executor>(ctx.config().getExecutorService());
        nodeIdInjector = new GridResourceBasicInjector<UUID>(ctx.config().getNodeId());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        customInjector = new GridResourceCustomInjector(injectLog, ioc);

        customInjector.setExecutorInjector(execInjector);
        customInjector.setGridgainHomeInjector(ggHomeInjector);
        customInjector.setGridNameInjector(ggNameInjector);
        customInjector.setGridInjector(gridInjector);
        customInjector.setMbeanServerInjector(mbeanSrvInjector);
        customInjector.setNodeIdInjector(nodeIdInjector);
        customInjector.setMarshallerInjector(marshallerInjector);
        customInjector.setSpringContextInjector(springCtxInjector);
        customInjector.setSpringBeanInjector(springBeanInjector);

        if (log.isDebugEnabled())
            log.debug("Started resource processor.");
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) {
        ioc.undeployAll();

        if (log.isDebugEnabled())
            log.debug("Stopped resource processor.");
    }

    /**
     * Sets Spring application context.
     *
     * @param springCtx Spring application context.
     */
    public void setSpringContext(ApplicationContext springCtx) {
        springCtxInjector = new GridResourceBasicInjector<ApplicationContext>(springCtx);

        springBeanInjector = new GridResourceSpringBeanInjector(springCtx);
    }

    /**
     * Callback to be called when class loader is undeployed.
     *
     * @param dep Deployment to release resources for.
     */
    public void onUndeployed(GridDeployment dep) {
        customInjector.undeploy(dep);

        ioc.onUndeployed(dep.classLoader());
    }

    /**
     * @param dep Deployment.
     * @param target Target object.
     * @param annCls Annotation class.
     * @throws GridException If failed to execute annotated methods.
     */
    public void invokeAnnotated(GridDeployment dep, Object target, Class<? extends Annotation> annCls)
        throws GridException {
        if (target != null) {
            List<Method> mtds = getMethodsWithAnnotation(dep, target.getClass(), annCls);

            if (mtds != null) {
                for (Method mtd : mtds) {
                    try {
                        mtd.setAccessible(true);

                        mtd.invoke(target);
                    }
                    catch (IllegalArgumentException e) {
                        throw new GridException("Failed to invoke annotated method [job=" + target + ", mtd=" + mtd +
                            ", ann=" + annCls + ']', e);
                    }
                    catch (IllegalAccessException e) {
                        throw new GridException("Failed to invoke annotated method [job=" + target + ", mtd=" + mtd +
                            ", ann=" + annCls + ']', e);
                    }
                    catch (InvocationTargetException e) {
                        throw new GridException("Failed to invoke annotated method [job=" + target + ", mtd=" + mtd +
                            ", ann=" + annCls + ']', e);
                    }
                }
            }
        }
    }

    /**
     * Injects resources into generic class.
     *
     * @param dep Deployment.
     * @param depCls Deployed class.
     * @param target Target instance to inject into.
     * @throws GridException Thrown in case of any errors.
     */
    public void inject(GridDeployment dep, Class<?> depCls, Object target) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + target);

        // Unwrap Proxy object.
        target = unwrapTarget(target);

        injectBasicResource(target, GridLoggerResource.class, injectLog.getLogger(target.getClass()), dep, depCls);

        ioc.inject(target, GridInstanceResource.class, gridInjector, dep, depCls);
        ioc.inject(target, GridExecutorServiceResource.class, execInjector, dep, depCls);
        ioc.inject(target, GridLocalNodeIdResource.class, nodeIdInjector, dep, depCls);
        ioc.inject(target, GridLocalHostResource.class, locHostInjector, dep, depCls);
        ioc.inject(target, GridMBeanServerResource.class, mbeanSrvInjector, dep, depCls);
        ioc.inject(target, GridHomeResource.class, ggHomeInjector, dep, depCls);
        ioc.inject(target, GridNameResource.class, ggNameInjector, dep, depCls);
        ioc.inject(target, GridMarshallerResource.class, marshallerInjector, dep, depCls);
        ioc.inject(target, GridSpringApplicationContextResource.class, springCtxInjector, dep, depCls);
        ioc.inject(target, GridSpringResource.class, springBeanInjector, dep, depCls);

        // Inject users resource.
        ioc.inject(target, GridUserResource.class, customInjector, dep, depCls);
    }

    /**
     * @param obj Object to inject.
     * @throws GridException If failed to inject.
     */
    public void injectGeneric(Object obj) throws GridException {
        assert obj != null;

        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + obj);

        // Unwrap Proxy object.
        obj = unwrapTarget(obj);

        injectBasicResource(obj, GridLoggerResource.class, injectLog.getLogger(obj.getClass()));

        // No deployment for lifecycle beans.
        ioc.inject(obj, GridExecutorServiceResource.class, execInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nodeIdInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, locHostInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, mbeanSrvInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, ggHomeInjector, null, null);
        ioc.inject(obj, GridNameResource.class, ggNameInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, marshallerInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, springCtxInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, springBeanInjector, null, null);
        ioc.inject(obj, GridInstanceResource.class, gridInjector, null, null);
    }

    /**
     * @param obj Object.
     * @throws GridException If failed.
     */
    public void cleanupGeneric(Object obj) throws GridException {
        if (obj != null) {
            if (log.isDebugEnabled())
                log.debug("Cleaning up resources: " + obj);

            // Unwrap Proxy object.
            obj = unwrapTarget(obj);

            // Caching key is null for the life-cycle beans.
            ioc.inject(obj, GridLoggerResource.class, nullInjector, null, null);
            ioc.inject(obj, GridExecutorServiceResource.class, nullInjector, null, null);
            ioc.inject(obj, GridLocalNodeIdResource.class, nullInjector, null, null);
            ioc.inject(obj, GridLocalHostResource.class, locHostInjector, null, null);
            ioc.inject(obj, GridMBeanServerResource.class, nullInjector, null, null);
            ioc.inject(obj, GridHomeResource.class, nullInjector, null, null);
            ioc.inject(obj, GridNameResource.class, nullInjector, null, null);
            ioc.inject(obj, GridMarshallerResource.class, nullInjector, null, null);
            ioc.inject(obj, GridSpringApplicationContextResource.class, nullInjector, null, null);
            ioc.inject(obj, GridSpringResource.class, nullInjector, null, null);
            ioc.inject(obj, GridInstanceResource.class, nullInjector, null, null);
        }
    }

    /**
     * Injects held resources into given {@code job}.
     *
     * @param dep Deployment.
     * @param taskCls Task class.
     * @param job Grid job to inject resources to.
     * @param ses Current task session.
     * @param jobCtx Job context.
     * @throws GridException Thrown in case of any errors.
     */
    public void inject(GridDeployment dep, Class<?> taskCls, GridJob job, GridTaskSession ses,
        GridJobContextImpl jobCtx) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + job);

        // Unwrap Proxy object.
        Object jobObj = unwrapTarget(unwrapJob(job));

        injectBasicResource(jobObj, GridLoggerResource.class, injectLog.getLogger(jobObj.getClass()), dep, taskCls);
        injectBasicResource(jobObj, GridTaskSessionResource.class, ses, dep, taskCls);
        injectBasicResource(jobObj, GridJobContextResource.class, jobCtx, dep, taskCls);

        ioc.inject(jobObj, GridInstanceResource.class, gridInjector, dep, taskCls);
        ioc.inject(jobObj, GridExecutorServiceResource.class, execInjector, dep, taskCls);
        ioc.inject(jobObj, GridLocalNodeIdResource.class, nodeIdInjector, dep, taskCls);
        ioc.inject(jobObj, GridLocalHostResource.class, locHostInjector, dep, taskCls);
        ioc.inject(jobObj, GridMBeanServerResource.class, mbeanSrvInjector, dep, taskCls);
        ioc.inject(jobObj, GridHomeResource.class, ggHomeInjector, dep, taskCls);
        ioc.inject(jobObj, GridNameResource.class, ggNameInjector, dep, taskCls);
        ioc.inject(jobObj, GridMarshallerResource.class, marshallerInjector, dep, taskCls);
        ioc.inject(jobObj, GridSpringApplicationContextResource.class, springCtxInjector, dep, taskCls);
        ioc.inject(jobObj, GridSpringResource.class, springBeanInjector, dep, taskCls);

        // Inject users resource.
        ioc.inject(jobObj, GridUserResource.class, customInjector, dep, taskCls);
    }

    /**
     * Gets rid of job wrapper, if any.
     *
     * @param job Job to unwrap.
     * @return Unwrapped job.
     */
    private GridJob unwrapJob(GridJob job) {
        if (job instanceof GridJobWrapper)
            return ((GridJobWrapper)job).wrappedJob();

        return job;
    }

    /**
     * Injects held resources into given grid task.
     *
     * @param dep Deployed class.
     * @param task Grid task.
     * @param ses Grid task session.
     * @param balancer Load balancer.
     * @param mapper Continuous task mapper.
     * @throws GridException Thrown in case of any errors.
     */
    public void inject(GridDeployment dep, GridTask<?, ?> task, GridTaskSessionImpl ses,
        GridLoadBalancer balancer, GridTaskContinuousMapper mapper) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + task);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(task);

        Class<?> cls = obj.getClass();

        // Basic injection.
        injectBasicResource(obj, GridLoggerResource.class, injectLog.getLogger(cls), dep, cls);
        injectBasicResource(obj, GridTaskSessionResource.class, ses, dep, cls);
        injectBasicResource(obj, GridLoadBalancerResource.class, balancer, dep, cls);
        injectBasicResource(obj, GridTaskContinuousMapperResource.class, mapper, dep, cls);

        ioc.inject(obj, GridInstanceResource.class, gridInjector, dep, cls);
        ioc.inject(obj, GridExecutorServiceResource.class, execInjector, dep, cls);
        ioc.inject(obj, GridLocalNodeIdResource.class, nodeIdInjector, dep, cls);
        ioc.inject(obj, GridLocalHostResource.class, locHostInjector, dep, cls);
        ioc.inject(obj, GridMBeanServerResource.class, mbeanSrvInjector, dep, cls);
        ioc.inject(obj, GridHomeResource.class, ggHomeInjector, dep, cls);
        ioc.inject(obj, GridNameResource.class, ggNameInjector, dep, cls);
        ioc.inject(obj, GridMarshallerResource.class, marshallerInjector, dep, cls);
        ioc.inject(obj, GridSpringApplicationContextResource.class, springCtxInjector, dep, cls);
        ioc.inject(obj, GridSpringResource.class, springBeanInjector, dep, cls);

        // Inject users resource.
        ioc.inject(obj, GridUserResource.class, customInjector, dep, cls);
    }

    /**
     * Checks if annotation presents in specified object.
     *
     * @param dep Class deployment.
     * @param target Object to check.
     * @param annCls Annotation to find.
     * @return {@code true} if annotation is presented, {@code false} otherwise.
     */
    public boolean isAnnotationPresent(GridDeployment dep, Object target, Class<? extends Annotation> annCls) {
        return ioc.isAnnotationPresent(target, annCls, dep);
    }

    /**
     * Injects held resources into given SPI implementation.
     *
     * @param spi SPI implementation.
     * @throws GridException Throw in case of any errors.
     */
    public void inject(GridSpi spi) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + spi);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(spi);

        injectBasicResource(obj, GridLoggerResource.class, injectLog.getLogger(obj.getClass()));

        // Caching key is null for the SPIs.
        ioc.inject(obj, GridExecutorServiceResource.class, execInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nodeIdInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, locHostInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, mbeanSrvInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, ggHomeInjector, null, null);
        ioc.inject(obj, GridNameResource.class, ggNameInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, marshallerInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, springCtxInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, springBeanInjector, null, null);
    }

    /**
     * Cleans up resources from given SPI implementation. Essentially, this
     * method injects {@code null}s into SPI implementation.
     *
     * @param spi SPI implementation.
     * @throws GridException Thrown in case of any errors.
     */
    public void cleanup(GridSpi spi) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Cleaning up resources: " + spi);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(spi);

        ioc.inject(obj, GridLoggerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridExecutorServiceResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, nullInjector, null, null);
        ioc.inject(obj, GridNameResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, nullInjector, null, null);
    }

    /**
     * Injects held resources into given lifecycle bean.
     *
     * @param lifecycleBean Lifecycle bean.
     * @throws GridException Thrown in case of any errors.
     */
    public void inject(GridLifecycleBean lifecycleBean) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + lifecycleBean);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(lifecycleBean);

        injectBasicResource(obj, GridLoggerResource.class, injectLog.getLogger(obj.getClass()));

        // No deployment for lifecycle beans.
        ioc.inject(obj, GridExecutorServiceResource.class, execInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nodeIdInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, locHostInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, mbeanSrvInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, ggHomeInjector, null, null);
        ioc.inject(obj, GridNameResource.class, ggNameInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, marshallerInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, springCtxInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, springBeanInjector, null, null);
        ioc.inject(obj, GridInstanceResource.class, gridInjector, null, null);
    }

    /**
     * Cleans up resources from given lifecycle beans. Essentially, this
     * method injects {@code null}s into lifecycle bean.
     *
     * @param lifecycleBean Lifecycle bean.
     * @throws GridException Thrown in case of any errors.
     */
    public void cleanup(GridLifecycleBean lifecycleBean) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Cleaning up resources: " + lifecycleBean);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(lifecycleBean);

        // Caching key is null for the life-cycle beans.
        ioc.inject(obj, GridLoggerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridExecutorServiceResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, nullInjector, null, null);
        ioc.inject(obj, GridNameResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, nullInjector, null, null);
        ioc.inject(obj, GridInstanceResource.class, nullInjector, null, null);
    }

    /**
     * Injects held resources into given cloud enabled algorithms (such as strategies and policies).
     *
     * @param ce Cloud enabled.
     * @throws GridException Throw in case of any errors.
     */
    public void inject(GridCloudEnabled ce) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Injecting resources: " + ce);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(ce);

        injectBasicResource(ce, GridLoggerResource.class, injectLog.getLogger(ce.getClass()));

        // No deployment for cloud enabled.
        ioc.inject(obj, GridExecutorServiceResource.class, execInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nodeIdInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, locHostInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, mbeanSrvInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, ggHomeInjector, null, null);
        ioc.inject(obj, GridNameResource.class, ggNameInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, marshallerInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, springCtxInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, springBeanInjector, null, null);
        ioc.inject(obj, GridInstanceResource.class, gridInjector, null, null);
    }

    /**
     * Cleans up resources from given cloud enabled algorithms (such as strategies and policies).
     * Essentially, this method injects <tt>null</tt>s into cloud enabled.
     *
     * @param ce Cloud enabled.
     * @throws GridException Thrown in case of any errors.
     */
    public void cleanup(GridCloudEnabled ce) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Cleaning up resources: " + ce);

        // Unwrap Proxy object.
        Object obj = unwrapTarget(ce);

        // Caching key is null.
        ioc.inject(obj, GridLoggerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridExecutorServiceResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalNodeIdResource.class, nullInjector, null, null);
        ioc.inject(obj, GridLocalHostResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMBeanServerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridHomeResource.class, nullInjector, null, null);
        ioc.inject(obj, GridNameResource.class, nullInjector, null, null);
        ioc.inject(obj, GridMarshallerResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringApplicationContextResource.class, nullInjector, null, null);
        ioc.inject(obj, GridSpringResource.class, nullInjector, null, null);
        ioc.inject(obj, GridInstanceResource.class, nullInjector, null, null);
    }

    /**
     * This method is declared public as it is used from tests as well.
     * Note, that this method can be used only with unwrapped objects
     * (see {@link #unwrapTarget(Object)}).
     *
     * @param target Target object.
     * @param annCls Setter annotation.
     * @param rsrc Resource to inject.
     * @param dep Deployment.
     * @param depCls Deployed class.
     * @throws GridException If injection failed.
     */
    public void injectBasicResource(Object target, Class<? extends Annotation> annCls, Object rsrc,
        GridDeployment dep, Class<?> depCls) throws GridException {
        // Safety.
        assert !(rsrc instanceof GridResourceInjector) : "Invalid injection.";

        // Basic injection don't cache anything. Use null as a key.
        ioc.inject(target, annCls, new GridResourceBasicInjector<Object>(rsrc), dep, depCls);
    }

    /**
     * This method is declared public as it is used from tests as well.
     * Note, that this method can be used only with unwrapped objects
     * (see {@link #unwrapTarget(Object)}).
     *
     * @param target Target object.
     * @param annCls Setter annotation.
     * @param rsrc Resource to inject.
     * @throws GridException If injection failed.
     */
    public void injectBasicResource(Object target, Class<? extends Annotation> annCls, Object rsrc)
        throws GridException {
        // Safety.
        assert !(rsrc instanceof GridResourceInjector) : "Invalid injection.";

        // Basic injection don't cache anything. Use null as a key.
        ioc.inject(target, annCls, new GridResourceBasicInjector<Object>(rsrc), null, null);
    }

    /**
     * Gets list of methods in specified class annotated with specified annotation.
     *
     * @param dep Class deployment.
     * @param rsrcCls Class to find methods in.
     * @param annCls Annotation to find annotated methods with.
     * @return List of annotated methods.
     */
    @Nullable public List<Method> getMethodsWithAnnotation(GridDeployment dep, Class<?> rsrcCls,
        Class<? extends Annotation> annCls) {
        assert dep != null;
        assert rsrcCls != null;
        assert annCls != null;

        List<GridResourceMethod> mtds = ioc.getMethodsWithAnnotation(dep, rsrcCls, annCls);

        if (!F.isEmpty(mtds)) {
            List<Method> res = new ArrayList<Method>();

            for (GridResourceMethod mtd : mtds)
                res.add(mtd.getMethod());

            return res;
        }

        return null;
    }

    /**
     * Returns GridResourceIoc object. For tests only!!!
     *
     * @return GridResourceIoc object.
     */
    GridResourceIoc getResourceIoc() {
        return ioc;
    }

    /**
     * Returns GridResourceCustomInjector object. For tests only!!!
     *
     * @return GridResourceCustomInjector object.
     */
    GridResourceCustomInjector getResourceCustomInjector() {
        return customInjector;
    }

    /**
     * Return original object if Spring AOP used with proxy objects.
     *
     * @param target Target object.
     * @return Original object wrapped by proxy.
     * @throws GridException If unwrap failed.
     */
    private Object unwrapTarget(Object target) throws GridException {
        if (target instanceof Advised) {
            try {
                return ((Advised)target).getTargetSource().getTarget();
            }
            catch (Exception e) {
                throw new GridException("Failed to unwrap Spring proxy target [cls=" + target.getClass().getName() +
                    ", target=" + target + ']', e);
            }
        }

        return target;
    }
}
