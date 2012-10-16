// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.resource;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Resource container contains caches for classes used for injection.
 * Caches used to improve the efficiency of standard Java reflection mechanism.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridResourceIoc {
    /** Number of entries to keep in annotation cache. */
    private static final int CLASS_CACHE_SIZE = 2000;

    /** Task class resource mapping. Used to efficiently cleanup resources related to class loader. */
    private final Map<ClassLoader, Set<Class<?>>> taskMap = new HashMap<ClassLoader, Set<Class<?>>>();

    /** Field cache. */
    private final GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<GridResourceField>>> fieldCache = new GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<GridResourceField>>>(CLASS_CACHE_SIZE);

    /** Method cache. */
    private final GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<GridResourceMethod>>> mtdCache = new GridBoundedLinkedHashMap<Class<?>, Map<Class<? extends Annotation>,
        List<GridResourceMethod>>>(CLASS_CACHE_SIZE);

    /**
     * Cache for classes that do not require injection with some annotation.
     * Maps annotation classes to set a set of target classes to skip.
     */
    private final ConcurrentMap<Class<? extends Annotation>, Set<Class<?>>> skipCache =
        new ConcurrentHashMap<Class<? extends Annotation>, Set<Class<?>>>();

    /** */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param ldr Class loader.
     */
    void onUndeployed(ClassLoader ldr) {
        lock.writeLock().lock();

        try {
            Set<Class<?>> clss = taskMap.remove(ldr);

            if (clss != null) {
                fieldCache.keySet().removeAll(clss);
                mtdCache.keySet().removeAll(clss);

                for (Map.Entry<Class<? extends Annotation>, Set<Class<?>>> e : skipCache.entrySet()) {
                    Set<Class<?>> skipClss = e.getValue();

                    if (skipClss != null) {
                        e.getValue().removeAll(clss);
                    }
                }
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all internal caches.
     */
    void undeployAll() {
        lock.writeLock().lock();

        try {
            taskMap.clear();
            mtdCache.clear();
            fieldCache.clear();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Injects given resource via field or setter with specified annotations
     * on provided target object.
     *
     * @param target Target object.
     * @param annCls Setter annotation.
     * @param injector Resource to inject.
     * @param dep Deployment.
     * @param depCls Deployment class.
     * @throws GridException Thrown in case of any errors during injection.
     */
    void inject(Object target, Class<? extends Annotation> annCls, GridResourceInjector injector,
        @Nullable GridDeployment dep, @Nullable Class<?> depCls) throws GridException {
        assert target != null;
        assert annCls != null;
        assert injector != null;

        // Use identity hash set to compare via referential equality.
        injectInternal(target, annCls, injector, dep, depCls, new GridIdentityHashSet<Object>(3));
    }

    /**
     * @param target Target object.
     * @param annCls Setter annotation.
     * @param injector Resource to inject.
     * @param dep Deployment.
     * @param depCls Deployment class.
     * @param checkedObjs Set of already inspected objects to avoid indefinite recursion.
     * @throws GridException Thrown in case of any errors during injection.
     */
    private void injectInternal(Object target, Class<? extends Annotation> annCls, GridResourceInjector injector,
        @Nullable GridDeployment dep, @Nullable Class<?> depCls, Set<Object> checkedObjs) throws GridException {
        assert target != null;
        assert annCls != null;
        assert injector != null;
        assert checkedObjs != null;

        Set<Class<?>> skipClss = F.addIfAbsent(skipCache, annCls, F.<Class<?>>newCSet());

        assert skipClss != null;

        // Skip this class if it does not need to be injected.
        if (skipClss.contains(target.getClass()))
            return;

        // Check if already inspected to avoid indefinite recursion.
        if (checkedObjs.contains(target))
            return;

        checkedObjs.add(target);

        int annCnt = 0;

        for (Class<?> cls = target.getClass(); !cls.equals(Object.class); cls = cls.getSuperclass()) {
            for (GridResourceField field : getFieldsWithAnnotation(dep, cls, annCls)) {
                Field f = field.getField();

                if (GridResourceUtils.mayRequireResources(f)) {
                    f.setAccessible(true);

                    try {
                        Object obj = f.get(target);

                        if (obj != null) {
                            // Recursion.
                            injectInternal(obj, annCls, injector, dep, depCls, checkedObjs);
                        }
                    }
                    catch (IllegalAccessException e) {
                        throw new GridException("Failed to inject resource [field=" + f.getName() +
                            ", target=" + target + ']', e);
                    }
                }
                else {
                    injector.inject(field, target, depCls, dep);
                }

                annCnt++;
            }

            for (GridResourceMethod mtd : getMethodsWithAnnotation(dep, cls, annCls)) {
                injector.inject(mtd, target, depCls, dep);

                annCnt++;
            }
        }

        if (annCnt == 0) {
            skipClss = skipCache.get(annCls);

            assert skipClss != null;

            skipClss.add(target.getClass());
        }
    }

    /**
     * Checks if annotation is presented on a field or method of the specified object.
     *
     * @param target Target object.
     * @param annCls Annotation class to find on fields or methods of target object.
     * @param dep Deployment.
     * @return {@code true} if annotation is presented, {@code false} if it's not.
     */
    boolean isAnnotationPresent(Object target, Class<? extends Annotation> annCls, @Nullable GridDeployment dep) {
        for (Class<?> cls = target.getClass(); !cls.equals(Object.class); cls = cls.getSuperclass()) {
            List<GridResourceField> fields = getFieldsWithAnnotation(dep, cls, annCls);

            if (!F.isEmpty(fields)) {
                return true;
            }

            List<GridResourceMethod> mtds = getMethodsWithAnnotation(dep, cls, annCls);

            if (!F.isEmpty(mtds)) {
                return true;
            }
        }

        return false;
    }

    /**
     * For tests only.
     *
     * @param cls Class for test.
     * @return {@code true} if cached, {@code false} otherwise.
     */
    boolean isCached(Class<?> cls) {
        return isCached(cls.getName());
    }

    /**
     * For tests only.
     *
     * @param clsName Class for test.
     * @return {@code true} if cached, {@code false} otherwise.
     */
    boolean isCached(String clsName) {
        lock.readLock().lock();

        try {
            for (Class<?> aClass : fieldCache.keySet()) {
                if (aClass.getName().equals(clsName)) {
                    return true;
                }
            }

            for (Class<?> aClass : mtdCache.keySet()) {
                if (aClass.getName().equals(clsName)) {
                    return true;
                }
            }

            return false;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets set of methods with given annotation.
     *
     * @param dep Deployment.
     * @param rsrcCls Class in which search for methods.
     * @param annCls Annotation.
     * @return Set of methods with given annotations.
     */
    List<GridResourceMethod> getMethodsWithAnnotation(@Nullable GridDeployment dep, Class<?> rsrcCls,
        Class<? extends Annotation> annCls) {
        List<GridResourceMethod> mtds = getMethodsFromCache(rsrcCls, annCls);

        if (mtds == null) {
            mtds = new ArrayList<GridResourceMethod>();

            for (Method mtd : rsrcCls.getDeclaredMethods()) {
                Annotation ann = mtd.getAnnotation(annCls);

                if (ann != null) {
                    mtds.add(new GridResourceMethod(mtd, ann));
                }
            }

            cacheMethods(dep, rsrcCls, annCls, mtds);
        }

        return mtds;
    }

    /**
     * Gets all entries from the specified class or its super-classes that have
     * been annotated with annotation provided.
     *
     * @param cls Class in which search for methods.
     * @param dep Deployment.
     * @param annCls Annotation.
     * @return Set of entries with given annotations.
     */
    private List<GridResourceField> getFieldsWithAnnotation(@Nullable GridDeployment dep, Class<?> cls,
        Class<? extends Annotation> annCls) {
        List<GridResourceField> fields = getFieldsFromCache(cls, annCls);

        if (fields == null) {
            fields = new ArrayList<GridResourceField>();

            for (Field field : cls.getDeclaredFields()) {
                // Account for anonymous inner classes.
                Annotation ann = field.getAnnotation(annCls);

                if (ann != null || GridResourceUtils.mayRequireResources(field)) {
                    fields.add(new GridResourceField(field, ann));
                }
            }

            cacheFields(dep, cls, annCls, fields);
        }

        return fields;
    }

    /**
     * Gets all fields for a given class with given annotation from cache.
     *
     * @param cls Class to get fields from.
     * @param annCls Annotation class for fields.
     * @return List of fields with given annotation, possibly {@code null}.
     */
    @Nullable
    private List<GridResourceField> getFieldsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        lock.readLock().lock();

        try {
            Map<Class<? extends Annotation>, List<GridResourceField>> annCache = fieldCache.get(cls);

            if (annCache != null) {
                return annCache.get(annCls);
            }
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
     * @param dep Deployment.
     * @param annCls Annotation class for the fields.
     * @param fields Fields to cache.
     */
    private void cacheFields(@Nullable GridDeployment dep, Class<?> cls, Class<? extends Annotation> annCls,
        List<GridResourceField> fields) {
        lock.writeLock().lock();

        try {
            if (dep != null) {
                Set<Class<?>> classes = F.addIfAbsent(taskMap, dep.classLoader(), F.<Class<?>>newSet());

                assert classes != null;

                classes.add(cls);
            }

            Map<Class<? extends Annotation>, List<GridResourceField>> rsrcFields =
                F.addIfAbsent(fieldCache, cls, F.<Class<? extends Annotation>, List<GridResourceField>>newMap());

            assert rsrcFields != null;

            rsrcFields.put(annCls, fields);
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
    @Nullable
    private List<GridResourceMethod> getMethodsFromCache(Class<?> cls, Class<? extends Annotation> annCls) {
        lock.readLock().lock();

        try {
            Map<Class<? extends Annotation>, List<GridResourceMethod>> annCache = mtdCache.get(cls);

            if (annCache != null) {
                return annCache.get(annCls);
            }
        }
        finally {
            lock.readLock().unlock();
        }

        return null;
    }

    /**
     * Caches list of methods with given annotation from given class.
     *
     * @param rsrcCls Class the fields belong to.
     * @param dep Deployment.
     * @param annCls Annotation class for the fields.
     * @param mtds Methods to cache.
     */
    private void cacheMethods(@Nullable GridDeployment dep, Class<?> rsrcCls, Class<? extends Annotation> annCls,
        List<GridResourceMethod> mtds) {
        lock.writeLock().lock();

        try {
            if (dep != null) {
                Set<Class<?>> classes = F.addIfAbsent(taskMap, dep.classLoader(), F.<Class<?>>newSet());

                assert classes != null;

                classes.add(rsrcCls);
            }

            Map<Class<? extends Annotation>, List<GridResourceMethod>> rsrcMtds = F.addIfAbsent(mtdCache,
                rsrcCls, F.<Class<? extends Annotation>, List<GridResourceMethod>>newMap());

            assert rsrcMtds != null;

            rsrcMtds.put(annCls, mtds);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}