// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.local;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import static org.gridgain.grid.kernal.GridNodeAttributes.ATTR_SPI_CLASS;
import static org.gridgain.grid.kernal.GridNodeAttributes.ATTR_SPI_VER;

/**
 * Local deployment SPI that implements only within VM deployment on local
 * node via {@link #register(ClassLoader, Class)} method. This SPI requires
 * no configuration.
 * <p>
 * Note that if peer class loading is enabled (which is default behavior,
 * see {@link GridConfiguration#isPeerClassLoadingEnabled()}), then it is
 * enough to deploy a task only on one node and all other nodes will load
 * required classes from the node that initiated task execution.
 * <p>
 * <h1 class="header">Configuration</h1>
 * This SPI requires no configuration.
 * <h2 class="header">Example</h2>
 * There is no point to explicitly configure {@code GridLocalDeploymentSpi}
 * with {@link GridConfiguration} as it is used by default and has no
 * configuration parameters.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridDeploymentSpi
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridLocalDeploymentSpi extends GridSpiAdapter implements GridDeploymentSpi, GridLocalDeploymentSpiMBean {
    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource private GridLogger log;

    /** List of all deployed class loaders. */
    @SuppressWarnings({"CollectionDeclaredAsConcreteClass"})
    private final LinkedList<ClassLoader> clsLdrs = new LinkedList<ClassLoader>();

    /** Map of all resources. */
    private Map<ClassLoader, Map<String, String>> ldrRsrcs = new HashMap<ClassLoader, Map<String, String>>();

    /** Deployment SPI listener.    */
    private volatile GridDeploymentListener lsnr;

    /** Mutex. */
    private final Object mux = new Object();

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        registerMBean(gridName, this, GridLocalDeploymentSpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        List<ClassLoader> tmpClsLdrs;

        synchronized (mux) {
            tmpClsLdrs = new ArrayList<ClassLoader>(clsLdrs);
        }

        for (ClassLoader ldr : tmpClsLdrs)
            onClassLoaderReleased(ldr);

        // Ack ok stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridDeploymentResource findResource(String rsrcName) {
        assert rsrcName != null;

        synchronized (mux) {
            // Last updated class loader has highest priority in search.
            for (ClassLoader ldr : clsLdrs) {
                Map<String, String> rsrcs = ldrRsrcs.get(ldr);

                // Return class if it was found in resources map.
                if (rsrcs != null && rsrcs.containsKey(rsrcName)) {
                    String clsName = rsrcs.get(rsrcName);

                    // Recalculate resource name in case if access is performed by
                    // class name and not the resource name.
                    rsrcName = getResourceName(clsName, rsrcs);

                    assert clsName != null;

                    try {
                        Class<?> cls = ldr.loadClass(clsName);

                        assert cls != null;

                        // Return resource.
                        return new GridDeploymentResourceAdapter(rsrcName, cls, ldr);
                    }
                    catch (ClassNotFoundException ignored) {
                        // No-op.
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets resource name for a given class name.
     *
     * @param clsName Class name.
     * @param rsrcs Map of resources.
     * @return Resource name.
     */
    private String getResourceName(String clsName, Map<String, String> rsrcs) {
        assert Thread.holdsLock(mux);

        String rsrcName = clsName;

        for (Entry<String, String> e : rsrcs.entrySet())
            if (e.getValue().equals(clsName) && !e.getKey().equals(clsName)) {
                rsrcName = e.getKey();

                break;
            }

        return rsrcName;
    }

    /** {@inheritDoc} */
    @Override public boolean register(ClassLoader ldr, Class<?> rsrc) throws GridSpiException {
        A.notNull(ldr, "ldr");
        A.notNull(rsrc, "rsrc");

        Collection<ClassLoader> removedClsLdrs = new ArrayList<ClassLoader>();

        Map<String, String> newRsrcs;

        synchronized (mux) {
            Map<String, String> clsLdrRsrcs = ldrRsrcs.get(ldr);

            if (clsLdrRsrcs == null)
                clsLdrRsrcs = new HashMap<String, String>();

            newRsrcs = addResource(ldr, clsLdrRsrcs, rsrc);

            if (!F.isEmpty(newRsrcs))
                removeResources(ldr, newRsrcs, removedClsLdrs);

            if (!ldrRsrcs.containsKey(ldr)) {
                assert !clsLdrs.contains(ldr);

                clsLdrs.addFirst(ldr);

                ldrRsrcs.put(ldr, clsLdrRsrcs);
            }
        }

        for (ClassLoader cldLdr : removedClsLdrs)
            onClassLoaderReleased(cldLdr);

        return !F.isEmpty(newRsrcs);
    }

    /** {@inheritDoc} */
    @Override public boolean unregister(String rsrcName) {
        Collection<ClassLoader> removedClsLdrs = new ArrayList<ClassLoader>();

        boolean removed;

        synchronized (mux) {
            Map<String, String> rsrcs = new HashMap<String, String>(1);

            rsrcs.put(rsrcName, rsrcName);

            removed = removeResources(null, rsrcs, removedClsLdrs);
        }

        for (ClassLoader cldLdr : removedClsLdrs)
            onClassLoaderReleased(cldLdr);

        return removed;
    }

    /**
     * Add new classes in class loader resource map.
     * Note that resource map may contain two entries for one added class:
     * task name -> class name and class name -> class name.
     *
     * @param ldr Registered class loader.
     * @param ldrRsrcs Class loader resources.
     * @param cls Registered classes collection.
     * @return Map of new resources added for registered class loader.
     * @throws GridSpiException If resource already registered. Exception thrown
     * if registered resources conflicts with rule when all task classes must be
     * annotated with different task names.
     */
    @Nullable private Map<String, String> addResource(ClassLoader ldr, Map<String, String> ldrRsrcs, Class<?> cls)
        throws GridSpiException {
        assert Thread.holdsLock(mux);
        assert ldr != null;
        assert ldrRsrcs != null;
        assert cls != null;

        // Maps resources to classes.
        // Map may contain 2 entries for one class.
        Map<String, String> regRsrcs = new HashMap<String, String>(2, 1.0f);

        // Check alias collision for added classes.
        String alias = null;

        if (GridTask.class.isAssignableFrom(cls)) {
            GridTaskName nameAnn = U.getAnnotation(cls, GridTaskName.class);

            if (nameAnn != null)
                alias = nameAnn.value();
        }

        if (alias != null)
            regRsrcs.put(alias, cls.getName());

        regRsrcs.put(cls.getName(), cls.getName());

        Map<String, String> newRsrcs = null;

        // Check collisions for added classes.
        for (Entry<String, String> entry : regRsrcs.entrySet()) {
            if (ldrRsrcs.containsKey(entry.getKey())) {
                String newAlias = entry.getKey();
                String newName = entry.getValue();

                String existingCls = ldrRsrcs.get(newAlias);

                // Different classes for the same resource name.
                if (!ldrRsrcs.get(newAlias).equals(newName))
                    throw new GridSpiException("Failed to register resources with given task name " +
                        "(found another class with same task name in the same class loader) [taskName=" + newAlias +
                        ", existingCls=" + existingCls + ", newCls=" + newName + ", ldr=" + ldr + ']');
            }
            // Add resources that should be removed for another class loaders.
            else {
                if (newRsrcs == null)
                    newRsrcs = new HashMap<String, String>(regRsrcs.size());

                newRsrcs.put(entry.getKey(), entry.getValue());
            }
        }

        // New resources to register. Add it all.
        if (newRsrcs != null)
            ldrRsrcs.putAll(newRsrcs);

        return newRsrcs;
    }

    /**
     * Remove resources for all class loaders except {@code ignoreClsLdr}.
     *
     * @param clsLdrToIgnore Ignored class loader or {@code null} to remove for all class loaders.
     * @param rsrcs Resources that should be used in search for class loader to remove.
     * @param rmvClsLdrs Class loaders to remove.
     * @return {@code True} if resource was removed.
     */
    private boolean removeResources(ClassLoader clsLdrToIgnore, Map<String, String> rsrcs,
        Collection<ClassLoader> rmvClsLdrs) {
        assert Thread.holdsLock(mux);
        assert rsrcs != null;

        boolean res = false;

        for (Iterator<ClassLoader> iter = clsLdrs.iterator(); iter.hasNext();) {
            ClassLoader ldr = iter.next();

            if (clsLdrToIgnore == null || !ldr.equals(clsLdrToIgnore)) {
                Map<String, String> clsLdrRsrcs = ldrRsrcs.get(ldr);

                assert clsLdrRsrcs != null;

                boolean isRemoved = false;

                // Check class loader registered resources.
                for (String rsrcName : rsrcs.keySet()) {
                    // Remove classloader if resource found.
                    if (clsLdrRsrcs.containsKey(rsrcName)) {
                        iter.remove();

                        ldrRsrcs.remove(ldr);

                        // Add class loaders in collection to notify listener outside synchronization block.
                        rmvClsLdrs.add(ldr);

                        isRemoved = true;
                        res = true;

                        break;
                    }
                }

                if (isRemoved)
                    continue;

                // Check is possible to load resources with classloader.
                for (Entry<String, String> entry : rsrcs.entrySet()) {
                    // Check classes with class loader only when classes points to classes to avoid redundant check.
                    // Resources map contains two entries for class with task name(alias).
                    if (entry.getKey().equals(entry.getValue()) &&
                        isResourceExist(ldr, entry.getKey())) {
                        iter.remove();

                        ldrRsrcs.remove(ldr);

                        // Add class loaders in collection to notify listener outside synchronization block.
                        rmvClsLdrs.add(ldr);

                        res = true;

                        break;
                    }
                }
            }
        }

        return res;
    }

    /**
     * Check is class can be reached.
     *
     * @param ldr Class loader.
     * @param clsName Class name.
     * @return {@code true} if class can be loaded.
     */
    private boolean isResourceExist(ClassLoader ldr, String clsName) {
        assert ldr != null;
        assert clsName != null;

        String rsrcName = clsName.replaceAll("\\.", "/") + ".class";

        InputStream in = null;

        try {
            in = ldr.getResourceAsStream(rsrcName);

            return in != null;
        }
        finally {
            U.close(in, log);
        }
    }

    /**
     * Notifies listener about released class loader.
     *
     * @param clsLdr Released class loader.
     */
    private void onClassLoaderReleased(ClassLoader clsLdr) {
        GridDeploymentListener tmp = lsnr;

        if (tmp != null)
            tmp.onUnregistered(clsLdr);
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridDeploymentListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(ATTR_SPI_VER));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLocalDeploymentSpi.class, this);
    }
}
