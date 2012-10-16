// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.loadbalancer;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Load balancing manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLoadBalancerManager extends GridManagerAdapter<GridLoadBalancingSpi> {
    /** Number of entries to keep in annotation cache. */
    private static final int CLASS_CACHE_SIZE = 2000;

    /** Field cache. */
    private final GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>, List<Field>>> fieldCache =
        new GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>, List<Field>>>(CLASS_CACHE_SIZE);

    /** Method cache. */
    private final GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<Method>>> mtdCache = new GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<Method>>>(CLASS_CACHE_SIZE);

    /** */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param ctx Grid kernal context.
     */
    public GridLoadBalancerManager(GridKernalContext ctx) {
        super(GridLoadBalancingSpi.class, ctx, ctx.config().getLoadBalancingSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        startSpi();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @param ses Task session.
     * @param top Task topology.
     * @param job Job to balance.
     * @return Next balanced node.
     * @throws GridException If anything failed.
     */
    public GridNode getBalancedNode(GridTaskSessionImpl ses, List<GridNode> top, GridJob job)
        throws GridException {
        assert ses != null;
        assert top != null;
        assert job != null;

        // Check cache affinity routing first.
        GridNode affNode = cacheAffinityNode(job, top);

        if (affNode != null) {
            if (log.isDebugEnabled())
                log.debug("Found affinity node for the job [job=" + job + ", affNode=" + affNode.id() + "]");

            return affNode;
        }

        return getSpi(ses.getLoadBalancingSpi()).getBalancedNode(ses, top, job);
    }

    /**
     * @param ses Grid task session.
     * @param top Task topology.
     * @return Load balancer.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public GridLoadBalancer getLoadBalancer(final GridTaskSessionImpl ses, final List<GridNode> top) {
        assert ses != null;

        return new GridLoadBalancerAdapter() {
            @Nullable @Override public GridNode getBalancedNode(GridJob job, @Nullable Collection<GridNode> exclNodes)
                throws GridException {
                A.notNull(job, "job");

                if (F.isEmpty(exclNodes))
                    return GridLoadBalancerManager.this.getBalancedNode(ses, top, job);

                List<GridNode> nodes = F.loseList(top, true, exclNodes);

                if (nodes.isEmpty())
                    return null;

                // Exclude list of nodes from topology.
                return GridLoadBalancerManager.this.getBalancedNode(ses, nodes, job);
            }
        };
    }

    /**
     * @param job Grid job.
     * @param top Topology.
     * @return Cache affinity node or {@code null} if this job is not routed with cache affinity key.
     * @throws GridException If failed to determine whether to use affinity routing.
     */
    @Nullable private GridNode cacheAffinityNode(GridJob job, Collection<GridNode> top) throws GridException {
        assert job != null;
        assert top != null;

        if (log.isDebugEnabled())
            log.debug("Looking for cache affinity node [job=" + job + "]");

        Collection<GridRichNode> nodes = F.viewReadOnly(top, ctx.rich().richNode());

        Object key = annotatedValue(job, GridCacheAffinityMapped.class, new HashSet<Object>(), false).get1();

        String cacheName = (String)annotatedValue(job, GridCacheName.class, new HashSet<Object>(), false).get1();

        if (log.isDebugEnabled())
            log.debug("Affinity properties [key=" + key + ", cacheName=" + cacheName + "]");

        GridNode affNode = null;

        if (key != null) {
            if (!ctx.isEnterprise()) {
                if (U.hasCache(ctx.discovery().localNode(), cacheName)) {
                    GridCacheAffinity<Object> aff = ctx.cache().cache(cacheName).configuration().getAffinity();

                    affNode = CU.primary(aff.nodes(aff.partition(key), nodes));
                }
                else {
                    throw new GridEnterpriseFeatureException("Affinity detection on nodes without cache running is " +
                        " not supported in community edition.");
                }
            }
            else {
                UUID id;

                try {
                    id = ctx.affinity().mapKeyToNode(cacheName, nodes, key, true);
                }
                catch (GridException e) {
                    throw new GridException("Failed to map affinity key to node for job [gridName=" + ctx.gridName() +
                        ", job=" + job + ']', e);
                }

                if (id == null)
                    throw new GridException("Failed to map key to node (is cache with given name started?) [gridName=" +
                        ctx.gridName() + ", key=" + key + ", cacheName=" + cacheName +
                        ", nodes=" + U.toShortString(nodes) + ']');

                for (GridNode node : top)
                    if (node.id().equals(id)) {
                        affNode = node;

                        break;
                    }
            }
        }

        return affNode;
    }

    /**
     * @param target Object to find a value in.
     * @param annCls Annotation class.
     * @param visited Set of visited objects to avoid cycling.
     * @param annFound Flag indicating if value has already been found.
     * @return Value of annotated field or method.
     * @throws GridException If failed to find.
     */
    private GridTuple2<Object, Boolean> annotatedValue(Object target, Class<? extends Annotation> annCls,
        Set<Object> visited, boolean annFound) throws GridException {
        assert target != null;

        // To avoid infinite recursion.
        if (visited.contains(target))
            return F.t(null, annFound);

        visited.add(target);

        Object val = null;

        for (Class<?> cls = target.getClass(); !cls.equals(Object.class); cls = cls.getSuperclass()) {
            // Fields.
            for (Field f : fieldsWithAnnotation(cls, annCls)) {
                f.setAccessible(true);

                Object fieldVal;

                try {
                    fieldVal = f.get(target);
                }
                catch (IllegalAccessException e) {
                    throw new GridException("Failed to get annotated field value [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName(), e);
                }

                if (needsRecursion(f)) {
                    if (fieldVal != null) {
                        // Recursion.
                        GridTuple2<Object, Boolean> tup = annotatedValue(fieldVal, annCls, visited, annFound);

                        if (!annFound && tup.get2())
                            // Update value only if annotation was found in recursive call.
                            val = tup.get1();

                        annFound = tup.get2();
                    }
                }
                else {
                    if (annFound)
                        throw new GridException("Multiple annotations have been found [cls=" + cls.getName() +
                            ", ann=" + annCls.getSimpleName() + "]");

                    val = fieldVal;

                    annFound = true;
                }
            }

            // Methods.
            for (Method m : methodsWithAnnotation(cls, annCls)) {
                if (annFound)
                    throw new GridException("Multiple annotations have been found [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName() + "]");

                m.setAccessible(true);

                try {
                    val = m.invoke(target);
                }
                catch (Exception e) {
                    throw new GridException("Failed to get annotated method value [cls=" + cls.getName() +
                        ", ann=" + annCls.getSimpleName(), e);
                }

                annFound = true;
            }
        }

        return F.t(val, annFound);
    }

    /**
     * @param f Field.
     * @return {@code true} if recursive inspection is required.
     */
    private boolean needsRecursion(Field f) {
        assert f != null;

        // Need to inspect anonymous classes, callable and runnable instances.
        return f.getName().startsWith("this$") || f.getName().startsWith("val$") ||
            Callable.class.isAssignableFrom(f.getType()) || Runnable.class.isAssignableFrom(f.getType()) ||
            GridLambda.class.isAssignableFrom(f.getType());
    }

    /**
     * Gets all entries from the specified class or its super-classes that have
     * been annotated with annotation provided.
     *
     * @param cls Class in which search for methods.
     * @param annCls Annotation.
     * @return Set of entries with given annotations.
     */
    private Iterable<Field> fieldsWithAnnotation(Class<?> cls, Class<? extends Annotation> annCls) {
        List<Field> fields = fieldsFromCache(cls, annCls);

        if (fields == null) {
            fields = new ArrayList<Field>();

            for (Field field : cls.getDeclaredFields()) {
                Annotation ann = field.getAnnotation(annCls);

                if (ann != null || needsRecursion(field))
                    fields.add(field);
            }

            cacheFields(cls, annCls, fields);
        }

        return fields;
    }

    /**
     * Gets set of methods with given annotation.
     *
     * @param cls Class in which search for methods.
     * @param annCls Annotation.
     * @return Set of methods with given annotations.
     */
    private Iterable<Method> methodsWithAnnotation(Class<?> cls, Class<? extends Annotation> annCls) {
        List<Method> mtds = methodsFromCache(cls, annCls);

        if (mtds == null) {
            mtds = new ArrayList<Method>();

            for (Method mtd : cls.getDeclaredMethods()) {
                Annotation ann = mtd.getAnnotation(annCls);

                if (ann != null)
                    mtds.add(mtd);
            }

            cacheMethods(cls, annCls, mtds);
        }

        return mtds;
    }

    /**
     * Gets all fields for a given class with given annotation from cache.
     *
     * @param cls Class to get fields from.
     * @param annCls Annotation class for fields.
     * @return List of fields with given annotation, possibly {@code null}.
     */
    @Nullable private List<Field> fieldsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        lock.readLock().lock();

        try {
            Map<Class<? extends Annotation>, List<Field>> annCache = fieldCache.get(cls);

            if (annCache != null)
                return annCache.get(annCls);
        }
        finally {
            lock.readLock().unlock();
        }

        return null;
    }

    /**
     * Caches list of fields with given annotation from given class.
     *
     * @param cls Class the fields belong to.
     * @param annCls Annotation class for the fields.
     * @param fields Fields to cache.
     */
    private void cacheFields(Class<?> cls, Class<? extends Annotation> annCls, List<Field> fields) {
        lock.writeLock().lock();

        try {
            Map<Class<? extends Annotation>, List<Field>> annFields =
                F.addIfAbsent(fieldCache, cls, F.<Class<? extends Annotation>, List<Field>>newMap());

            assert annFields != null;

            annFields.put(annCls, fields);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets all methods for a given class with given annotation from cache.
     *
     * @param cls Class to get methods from.
     * @param annCls Annotation class for fields.
     * @return List of methods with given annotation, possibly {@code null}.
     */
    @Nullable private List<Method> methodsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        lock.readLock().lock();

        try {
            Map<Class<? extends Annotation>, List<Method>> annCache = mtdCache.get(cls);

            if (annCache != null)
                return annCache.get(annCls);
        }
        finally {
            lock.readLock().unlock();
        }

        return null;
    }

    /**
     * Caches list of methods with given annotation from given class.
     *
     * @param cls Class the fields belong to.
     * @param annCls Annotation class for the fields.
     * @param mtds Methods to cache.
     */
    private void cacheMethods(Class<?> cls, Class<? extends Annotation> annCls,
        List<Method> mtds) {
        lock.writeLock().lock();

        try {
            Map<Class<? extends Annotation>, List<Method>> annMtds = F.addIfAbsent(mtdCache,
                cls, F.<Class<? extends Annotation>, List<Method>>newMap());

            assert annMtds != null;

            annMtds.put(annCls, mtds);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
