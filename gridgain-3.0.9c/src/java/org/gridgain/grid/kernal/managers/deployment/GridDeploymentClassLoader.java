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
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Class loader that is able to resolve task subclasses and resources
 * by requesting remote node. Every class that could not be resolved
 * by system class loader will be downloaded from given remote node
 * by task deployment identifier. If identifier has been changed on
 * remote node this class will throw exception.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"CustomClassloader"}) class GridDeploymentClassLoader extends ClassLoader
    implements GridDeploymentInfo {
    /**
     * Sets up custom protocol handler factory.
     * TODO: Uncomment when custom protocol to load resources is tested.
     */
//    static {
//        URL.setURLStreamHandlerFactory(
//            new URLStreamHandlerFactory() {
//                private final URLStreamHandler handler = new GridProtocolHandler();
//
//                @Override @Nullable public URLStreamHandler createURLStreamHandler(String proto) {
//                    assert proto != null;
//
//                    return "gg".equals(proto.toLowerCase()) ? handler : null;
//                }
//            }
//        );
//    }

    /** Class loader ID. */
    private final UUID id;

    /** {@code True} for single node deployment. */
    private final boolean singleNode;

    /** Manager registry. */
    @GridToStringExclude
    private final GridKernalContext ctx;

    /** */
    @GridToStringExclude
    private final GridLogger log;

    /**
     * Node ID -> Loader ID + seqNum.
     * <p>
     * This map is ordered by access order to make sure that P2P requests
     * are sent to the last accessed node first.
     */
    @GridToStringInclude
    private final Map<UUID, GridTuple2<UUID, Long>> nodeLdrMap;

    /** */
    @GridToStringExclude
    private final GridDeploymentCommunication comm;

    /** */
    private final String[] p2pExclude;

    /** P2P timeout. */
    private final long p2pTimeout;

    /** Cache of missed resources names. */
    @GridToStringExclude
    private final GridBoundedLinkedHashSet<String> missedRsrcs;

    /** Map of class byte code if it's not available locally. */
    @GridToStringExclude
    private final ConcurrentMap<String, byte[]> byteMap;

    /** User version. */
    private final String usrVer;

    /** Deployment mode. */
    private final GridDeploymentMode depMode;

    /** */
    private final Object mux = new Object();

    /**
     * Creates a new peer class loader.
     * <p>
     * If there is a security manager, its
     * {@link SecurityManager#checkCreateClassLoader()}
     * method is invoked. This may result in a security exception.
     *
     * @param id Class loader ID.
     * @param usrVer User version.
     * @param depMode Deployment mode.
     * @param singleNode {@code True} for single node.
     * @param ctx Kernal context.
     * @param parent Parent class loader.
     * @param clsLdrId Remote class loader identifier.
     * @param nodeId ID of node that have initiated task.
     * @param seqNum Sequence number for the class loader.
     * @param comm Communication manager loader will work through.
     * @param p2pTimeout Timeout for class-loading requests.
     * @param log Logger.
     * @param p2pExclude List of P2P loaded packages.
     * @param missedResourcesCacheSize Size of the missed resources cache.
     * @param clsBytesCacheEnabled Flag to enable class byte cache.
     * @throws SecurityException If a security manager exists and its
     *      {@code checkCreateClassLoader} method doesn't allow creation
     *      of a new class loader.
     */
    GridDeploymentClassLoader(UUID id, String usrVer, GridDeploymentMode depMode, boolean singleNode,
        GridKernalContext ctx, ClassLoader parent, UUID clsLdrId, UUID nodeId, long seqNum,
        GridDeploymentCommunication comm, long p2pTimeout, GridLogger log, String[] p2pExclude,
        int missedResourcesCacheSize, boolean clsBytesCacheEnabled) throws SecurityException {
        super(parent);

        assert id != null;
        assert depMode != null;
        assert ctx != null;
        assert comm != null;
        assert p2pTimeout > 0;
        assert log != null;
        assert clsLdrId != null;

        this.id = id;
        this.usrVer = usrVer;
        this.depMode = depMode;
        this.singleNode = singleNode;
        this.ctx = ctx;
        this.comm = comm;
        this.p2pTimeout = p2pTimeout;
        this.log = log;
        this.p2pExclude = p2pExclude;

        Map<UUID, GridTuple2<UUID, Long>> map = new LinkedHashMap<UUID, GridTuple2<UUID, Long>>(1, 0.75f, true);

        map.put(nodeId, F.t(clsLdrId, seqNum));

        nodeLdrMap = singleNode ? Collections.unmodifiableMap(map) : map;

        missedRsrcs = missedResourcesCacheSize > 0 ?
            new GridBoundedLinkedHashSet<String>(missedResourcesCacheSize) : null;

        byteMap = clsBytesCacheEnabled ? new ConcurrentHashMap<String, byte[]>() : null;
    }

    /**
     * Creates a new peer class loader.
     * <p>
     * If there is a security manager, its
     * {@link SecurityManager#checkCreateClassLoader()}
     * method is invoked. This may result in a security exception.
     *
     * @param id Class loader ID.
     * @param usrVer User version.
     * @param depMode Deployment mode.
     * @param singleNode {@code True} for single node.
     * @param ctx Kernal context.
     * @param parent Parent class loader.
     * @param participants Participating nodes class loader map.
     * @param comm Communication manager loader will work through.
     * @param p2pTimeout Timeout for class-loading requests.
     * @param log Logger.
     * @param p2pExclude List of P2P loaded packages.
     * @param missedResourcesCacheSize Size of the missed resources cache.
     * @param clsBytesCacheEnabled Flag to enable class byte cache.
     * @throws SecurityException If a security manager exists and its
     *      {@code checkCreateClassLoader} method doesn't allow creation
     *      of a new class loader.
     */
    GridDeploymentClassLoader(UUID id, String usrVer, GridDeploymentMode depMode, boolean singleNode,
        GridKernalContext ctx, ClassLoader parent, Map<UUID, GridTuple2<UUID, Long>> participants,
        GridDeploymentCommunication comm, long p2pTimeout, GridLogger log, String[] p2pExclude,
        int missedResourcesCacheSize, boolean clsBytesCacheEnabled) throws SecurityException {
        super(parent);

        assert id != null;
        assert depMode != null;
        assert ctx != null;
        assert comm != null;
        assert p2pTimeout > 0;
        assert log != null;
        assert participants != null;

        this.id = id;
        this.usrVer = usrVer;
        this.depMode = depMode;
        this.singleNode = singleNode;
        this.ctx = ctx;
        this.comm = comm;
        this.p2pTimeout = p2pTimeout;
        this.log = log;
        this.p2pExclude = p2pExclude;

        nodeLdrMap = new LinkedHashMap<UUID, GridTuple2<UUID, Long>>(1, 0.75f, true);

        nodeLdrMap.putAll(participants);

        missedRsrcs = missedResourcesCacheSize > 0 ?
            new GridBoundedLinkedHashSet<String>(missedResourcesCacheSize) : null;

        byteMap = clsBytesCacheEnabled ? new ConcurrentHashMap<String, byte[]>() : null;
    }

    /** {@inheritDoc} */
    @Override public UUID classLoaderId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentMode deployMode() {
        return depMode;
    }

    /** {@inheritDoc} */
    @Override public String userVersion() {
        return usrVer;
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, GridTuple2<UUID, Long>> participants() {
        synchronized (mux) {
            return new HashMap<UUID, GridTuple2<UUID, Long>>(nodeLdrMap);
        }
    }

    /**
     * Adds new node and remote class loader id to this class loader.
     * Class loader will ask all associated nodes for the class/resource
     * until find it.
     *
     * @param nodeId Participating node ID.
     * @param ldrId Participating class loader id.
     * @param seqNum Sequence number for the class loader.
     */
    void register(UUID nodeId, UUID ldrId, long seqNum) {
        assert nodeId != null;
        assert ldrId != null;

        synchronized (mux) {
            // Make sure to do get in order to change iteration order,
            // i.e. put this node first.
            GridTuple2<UUID, Long> pair = nodeLdrMap.get(nodeId);

            if (pair == null)
                nodeLdrMap.put(nodeId, F.t(ldrId, seqNum));
        }
    }

    /**
     * Remove remote node and remote class loader id associated with it from
     * internal map.
     *
     * @param nodeId Participating node ID.
     * @return Removed class loader ID.
     */
    @Nullable UUID unregister(UUID nodeId) {
        assert nodeId != null;

        synchronized (mux) {
            GridTuple2<UUID, Long> removed = nodeLdrMap.remove(nodeId);

            return removed == null ? null : removed.get1();
        }
    }

    /**
     * @return Registered nodes.
     */
    Collection<UUID> registeredNodeIds() {
        synchronized (mux) {
            return new ArrayList<UUID>(nodeLdrMap.keySet());
        }
    }

    /**
     * @return Registered class loader IDs.
     */
    Collection<UUID> registeredClassLoaderIds() {
        Collection<UUID> ldrIds = new LinkedList<UUID>();

        synchronized (mux) {
            for (GridTuple2<UUID, Long> pair : nodeLdrMap.values())
                ldrIds.add(pair.get1());
        }

        return ldrIds;
    }

    /**
     * @param nodeId Node ID.
     * @return Class loader ID for node ID.
     */
    GridTuple2<UUID, Long> registeredClassLoaderId(UUID nodeId) {
        synchronized (mux) {
            return nodeLdrMap.get(nodeId);
        }
    }

    /**
     * Checks if node is participating in deployment.
     *
     * @param nodeId Node ID to check.
     * @param ldrId Class loader ID.
     * @return {@code True} if node is participating in deployment.
     */
    boolean hasRegisteredNode(UUID nodeId, UUID ldrId) {
        assert nodeId != null;
        assert ldrId != null;

        GridTuple2<UUID, Long> pair;

        synchronized (mux) {
            pair = nodeLdrMap.get(nodeId);
        }

        return pair != null && ldrId.equals(pair.get1());
    }

    /**
     * @return {@code True} if class loader has registered nodes.
     */
    boolean hasRegisteredNodes() {
        synchronized (mux) {
            return !nodeLdrMap.isEmpty();
        }
    }

    /**
     * @param name Name of the class.
     * @return {@code True} if locally excluded.
     */
    private boolean isLocallyExcluded(String name) {
        if (p2pExclude != null) {
            for (String path : p2pExclude) {
                // Remove star (*) at the end.
                if (path.endsWith("*"))
                    path = path.substring(0, path.length() - 1);

                if (name.startsWith(path))
                    return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
        assert !Thread.holdsLock(mux);

        // Check if we have package name on list of P2P loaded.
        // GridJob must be always loaded locally to avoid
        // any possible class casting issues.
        Class<?> cls = null;

        try {
            if (!"org.gridgain.grid.GridJob".equals(name)) {
                if (isLocallyExcluded(name))
                    // P2P loaded class.
                    cls = p2pLoadClass(name, true);
            }

            if (cls == null)
                cls = loadClass(name, true);
        }
        catch (ClassNotFoundException e) {
            throw e;
        }
        // Catch Throwable to secure against any errors resulted from
        // corrupted class definitions or other user errors.
        catch (Throwable e) {
            throw new ClassNotFoundException("Failed to load class due to unexpected error: " + name, e);
        }

        return cls;
    }

    /**
     * Loads the class with the specified binary name.  The
     * default implementation of this method searches for classes in the
     * following order:
     * <p>
     * <ol>
     * <li> Invoke {@link #findLoadedClass(String)} to check if the class
     * has already been loaded. </li>
     * <li>Invoke the {@link #findClass(String)} method to find the class.</li>
     * </ol>
     * <p> If the class was found using the above steps, and the
     * {@code resolve} flag is true, this method will then invoke the {@link
     * #resolveClass(Class)} method on the resulting {@code Class} object.
     *
     * @param name The binary name of the class.
     * @param resolve If {@code true} then resolve the class.
     * @return The resulting {@code Class} object.
     * @throws ClassNotFoundException If the class could not be found
     */
    @Nullable private Class<?> p2pLoadClass(String name, boolean resolve) throws ClassNotFoundException {
        assert !Thread.holdsLock(mux);

        // First, check if the class has already been loaded.
        Class<?> cls = findLoadedClass(name);

        if (cls == null)
            cls = findClass(name);

        if (resolve)
            resolveClass(cls);

        return cls;
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        assert !Thread.holdsLock(mux);

        if (!isLocallyExcluded(name)) {
            // This is done for URI deployment in which case the parent loader
            // does not have the requested resource, but it is still locally
            // available.
            GridDeployment dep = ctx.deploy().getLocalDeployment(name);

            if (dep != null) {
                if (log.isDebugEnabled())
                    log.debug("Found class in local deployment [cls=" + name + ", dep=" + dep + ']');

                return dep.deployedClass(name);
            }
        }

        String path = U.classNameToResourceName(name);

        GridByteArrayList byteSrc = sendClassRequest(name, path);

        synchronized (this) {
            Class<?> cls = findLoadedClass(name);

            if (cls == null) {
                if (byteMap != null)
                    byteMap.put(path, byteSrc.getArray());

                cls = defineClass(name, byteSrc.getInternalArray(), 0, byteSrc.getSize());

                /* Define package in classloader. See URLClassLoader.defineClass(). */
                int i = name.lastIndexOf('.');

                if (i != -1) {
                    String pkgName = name.substring(0, i);

                    if (getPackage(pkgName) == null){
                         /* Too much nulls is normal because we don't have package's meta info */
                         definePackage(pkgName, null, null, null, null, null, null, null);
                     }
                }
            }

            return cls;
        }
    }

    /**
     * Computes end time based on timeout value passed in.
     *
     * @param timeout Timeout.
     * @return End time.
     */
    private long computeEndTime(long timeout) {
        long endTime = System.currentTimeMillis() + timeout;

        // Account for overflow.
        if (endTime < 0)
            endTime = Long.MAX_VALUE;

        return endTime;
    }

    /**
     * Sends class-loading request to all nodes associated with this class loader.
     *
     * @param name Class name.
     * @param path Class path.
     * @return Class byte source.
     * @throws ClassNotFoundException If class was not found.
     */
    @SuppressWarnings({"CallToNativeMethodWhileLocked", "ThrowableInstanceNeverThrown"})
    private GridByteArrayList sendClassRequest(String name, String path) throws ClassNotFoundException {
        assert !Thread.holdsLock(mux);

        long endTime = computeEndTime(p2pTimeout);

        Collection<Map.Entry<UUID, GridTuple2<UUID, Long>>> entries;

        synchronized (mux) {
            // Skip requests for the previously missed classes.
            if (missedRsrcs != null && missedRsrcs.contains(path))
                throw new ClassNotFoundException("Failed to peer load class [class=" + name + ", nodeClsLdrIds=" +
                    nodeLdrMap + ", parentClsLoader=" + getParent() + ']');

            // If single-node mode, then node cannot change and we simply reuse the entry set.
            // Otherwise, copy and preserve order for iteration.
            entries = singleNode ? nodeLdrMap.entrySet() :
                new ArrayList<Map.Entry<UUID, GridTuple2<UUID, Long>>>(nodeLdrMap.entrySet());
        }

        GridException err = null;

        for (Map.Entry<UUID, GridTuple2<UUID, Long>> entry : entries) {
            UUID nodeId = entry.getKey();

            if (nodeId.equals(ctx.discovery().localNode().id()))
                // Skip local node as it is already used as parent class loader.
                continue;

            UUID ldrId = entry.getValue().get1();

            GridNode node = ctx.discovery().node(nodeId);

            if (node == null) {
                if (log.isDebugEnabled())
                    log.debug("Found inactive node is class loader (will skip): " + nodeId);

                continue;
            }

            try {
                GridDeploymentResponse res = comm.sendResourceRequest(path, ldrId, node, endTime);

                if (res == null) {
                    String msg = "Failed to send class-loading node request to node (is node alive?) [node=" +
                        node.id() + ", clsName=" + name + ", clsPath=" + path + ", clsLdrId=" + ldrId +
                        ", parentClsLdr=" + getParent() + ']';

                    U.warn(log, msg);

                    err = new GridException(msg);

                    continue;
                }

                if (res.isSuccess())
                    return res.getByteSource();

                // In case of shared resources/classes all nodes should have it.
                if (log.isDebugEnabled())
                    log.debug("Failed to find class on remote node [class=" + name + ", nodeId=" + node.id() +
                        ", clsLdrId=" + entry.getValue());

                synchronized (mux) {
                    if (missedRsrcs != null)
                        missedRsrcs.add(path);
                }

                throw new ClassNotFoundException("Failed to peer load class [class=" + name + ", nodeClsLdrs=" +
                    entries + ", parentClsLoader=" + getParent() + ']');
            }
            catch (GridException e) {
                // This thread should be interrupted again in communication if it
                // got interrupted. So we assume that thread can be interrupted
                // by processing cancellation request.
                if (Thread.currentThread().isInterrupted())
                    U.error(log, "Failed to find class probably due to task/job cancellation: " + name, e);
                else {
                    U.warn(log, "Failed to send class-loading node request to node (is node alive?) [node=" +
                        node.id() + ", clsName=" + name + ", clsPath=" + path + ", clsLdrId=" + ldrId +
                        ", parentClsLdr=" + getParent() + ", err=" + e + ']');

                    err = e;
                }
            }
        }

        throw new ClassNotFoundException("Failed to peer load class [class=" + name + ", nodeClsLdrs=" +
            entries + ", parentClsLoader=" + getParent() + ']', err);
    }

    /** {@inheritDoc} */
    @Override @Nullable public InputStream getResourceAsStream(String name) {
        assert !Thread.holdsLock(mux);

        if (byteMap != null && name.endsWith(".class")) {
            byte[] bytes = byteMap.get(name);

            if (bytes != null) {
                if (log.isDebugEnabled())
                    log.debug("Got class definition from byte code cache: " + name);

                return new ByteArrayInputStream(bytes);
            }
        }

        InputStream in = ClassLoader.getSystemResourceAsStream(name);

        if (in == null)
            in = super.getResourceAsStream(name);

        if (in == null)
            in = sendResourceRequest(name);

        return in;
    }

    /**
     * Sends resource request to all remote nodes associated with this class loader.
     *
     * @param name Resource name.
     * @return InputStream for resource or {@code null} if resource could not be found.
     */
    @SuppressWarnings({"CallToNativeMethodWhileLocked"})
    @Nullable
    private InputStream sendResourceRequest(String name) {
        assert !Thread.holdsLock(mux);

        long endTime = computeEndTime(p2pTimeout);

        Collection<Map.Entry<UUID, GridTuple2<UUID, Long>>> entries;

        synchronized (mux) {
            // Skip requests for the previously missed classes.
            if (missedRsrcs != null && missedRsrcs.contains(name))
                return null;

            // If single-node mode, then node cannot change and we simply reuse the entry set.
            // Otherwise, copy and preserve order for iteration.
            entries = singleNode ? nodeLdrMap.entrySet() :
                new ArrayList<Map.Entry<UUID, GridTuple2<UUID, Long>>>(nodeLdrMap.entrySet());
        }

        for (Map.Entry<UUID, GridTuple2<UUID, Long>> entry : entries) {
            UUID nodeId = entry.getKey();

            if (nodeId.equals(ctx.discovery().localNode().id()))
                // Skip local node as it is already used as parent class loader.
                continue;

            UUID ldrId = entry.getValue().get1();

            GridNode node = ctx.discovery().node(nodeId);

            if (node == null) {
                if (log.isDebugEnabled())
                    log.debug("Found inactive node is class loader (will skip): " + nodeId);

                continue;
            }

            try {
                // Request is sent with timeout that is why we can use synchronization here.
                GridDeploymentResponse res = comm.sendResourceRequest(name, ldrId, node, endTime);

                if (res == null) {
                    U.warn(log, "Failed to get resource from node (is node alive?) [nodeId=" +
                        node.id() + ", clsLdrId=" + entry.getValue() + ", resName=" +
                        name + ", parentClsLdr=" + getParent() + ']');
                }
                else if (!res.isSuccess()) {
                    synchronized (mux) {
                        // Cache unsuccessfully loaded resource.
                        if (missedRsrcs != null)
                            missedRsrcs.add(name);
                    }

                    // Some frameworks like Spring often ask for the resources
                    // just in case - none will happen if there are no such
                    // resources. So we print out INFO level message.
                    if (log.isInfoEnabled())
                        log.info("Failed to get resource from node [nodeId=" +
                            node.id() + ", clsLdrId=" + entry.getValue() + ", resName=" +
                            name + ", parentClsLdr=" + getParent() + ", msg=" + res.getErrorMessage() + ']');

                    // Do not ask other nodes in case of shared mode all of them should have the resource.
                    return null;
                }
                else {
                    return new ByteArrayInputStream(res.getByteSource().getInternalArray(), 0,
                        res.getByteSource().getSize());
                }
            }
            catch (GridException e) {
                // This thread should be interrupted again in communication if it
                // got interrupted. So we assume that thread can be interrupted
                // by processing cancellation request.
                if (Thread.currentThread().isInterrupted()) {
                    U.error(log, "Failed to get resource probably due to task/job cancellation: " + name, e);
                }
                else {
                    U.warn(log, "Failed to get resource from node (is node alive?) [nodeId=" +
                        node.id() + ", clsLdrId=" + entry.getValue() + ", resName=" +
                        name + ", parentClsLdr=" + getParent() + ", err=" + e + ']');
                }
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentClassLoader.class, this);
    }
}
