// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Task session.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTaskSessionImpl extends GridMetadataAwareAdapter implements GridTaskSessionInternal {
    /** */
    private final String taskName;

    /** */
    private final String userVer;

    /** */
    private final String taskClsName;

    /** */
    private final UUID sesId;

    /** */
    private final long startTime;

    /** */
    private final long endTime;

    /** */
    private final UUID taskNodeId;

    /** */
    private final GridKernalContext ctx;

    /** */
    private Collection<GridJobSibling> siblings;

    /** */
    private final Map<Object, Object> attrs = new HashMap<Object, Object>(1);

    /** */
    private List<GridTaskSessionAttributeListener> lsnrs = Collections.emptyList();

    /** */
    private ClassLoader clsLdr;

    /** */
    private boolean closed;

    /** */
    private String topSpi;

    /** */
    private String cpSpi;

    /** */
    private String failSpi;

    /** */
    private String loadSpi;

    /** */
    private String swapSpi;

    /** */
    private long seqNum;

    /** */
    private final Object mux = new Object();

    /**
     * @param taskNodeId Task node ID.
     * @param taskName Task name.
     * @param userVer Task code version. Might be null if deployment failed.
     * @param seqNum Task internal node version.
     * @param taskClsName Task class name.
     * @param sesId Task session ID.
     * @param startTime Task execution start time.
     * @param endTime Task execution end time.
     * @param siblings Collection of siblings.
     * @param attrs Session attributes.
     * @param ctx Grid Kernal Context.
     */
    public GridTaskSessionImpl(
        UUID taskNodeId,
        String taskName,
        String userVer,
        Long seqNum,
        String taskClsName,
        UUID sesId,
        long startTime,
        long endTime,
        Collection<GridJobSibling> siblings,
        Map<Object, Object> attrs,
        GridKernalContext ctx) {
        assert taskNodeId != null;
        assert taskName != null;
        assert sesId != null;
        assert attrs != null;
        assert ctx != null;
        assert seqNum != null;

        this.taskNodeId = taskNodeId;
        this.taskName = taskName;
        this.userVer = userVer;
        this.seqNum = seqNum;

        // Note that class name might be null here if task was not explicitly
        // deployed.
        this.taskClsName = taskClsName;
        this.sesId = sesId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.siblings = siblings != null ? Collections.unmodifiableCollection(siblings) : null;
        this.ctx = ctx;

        this.attrs.putAll(attrs);
    }

    /** {@inheritDoc} */
    @Nullable @Override public UUID getJobId() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void onClosed() {
        synchronized (mux) {
            closed = true;

            mux.notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isClosed() {
        synchronized (mux) {
            return closed;
        }
    }

    /**
     * @return Task node ID.
     */
    @Override public UUID getTaskNodeId() {
        return taskNodeId;
    }

    /** {@inheritDoc} */
    @Override public long getStartTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> V waitForAttribute(K key) throws InterruptedException {
        return (V)waitForAttribute(key, 0);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttribute(Object key, Object val) throws InterruptedException {
        return waitForAttribute(key, val, 0);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> V waitForAttribute(K key, long timeout) throws InterruptedException {
        A.notNull(key, "key");

        if (timeout == 0)
            timeout = Long.MAX_VALUE;

        long now = System.currentTimeMillis();

        // Prevent overflow.
        long end = now + timeout < 0 ? Long.MAX_VALUE : now + timeout;

        // Don't wait longer than session timeout.
        if (end > endTime)
            end = endTime;

        synchronized (mux) {
            while (!closed && !attrs.containsKey(key) && now < end) {
                mux.wait(end - now);

                now = System.currentTimeMillis();
            }

            if (closed)
                throw new InterruptedException("Session was closed: " + this);

            return (V)attrs.get(key);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttribute(Object key, Object val, long timeout) throws InterruptedException {
        A.notNull(key, "key");

        if (timeout == 0)
            timeout = Long.MAX_VALUE;

        long now = System.currentTimeMillis();

        // Prevent overflow.
        long end = now + timeout < 0 ? Long.MAX_VALUE : now + timeout;

        // Don't wait longer than session timeout.
        if (end > endTime)
            end = endTime;

        synchronized (mux) {
            boolean isFound = false;

            while (!closed && !(isFound = isAttributeSet(key, val)) && now < end) {
                mux.wait(end - now);

                now = System.currentTimeMillis();
            }

            if (closed)
                throw new InterruptedException("Session was closed: " + this);

            return isFound;
        }
    }

    /**
     * {@inheritDoc}
     * @param keys Attribute keys.
     */
    @Override public Map<?, ?> waitForAttributes(Collection<?> keys) throws InterruptedException {
        return waitForAttributes(keys, 0);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttributes(Map<?, ?> attrs) throws InterruptedException {
        return waitForAttributes(attrs, 0);
    }

    /** {@inheritDoc} */
    @Override public Map<?, ?> waitForAttributes(Collection<?> keys, long timeout)
        throws InterruptedException {
        A.notNull(keys, "keys");

        if (keys.isEmpty())
            return Collections.emptyMap();

        if (timeout == 0)
            timeout = Long.MAX_VALUE;

        long now = System.currentTimeMillis();

        // Prevent overflow.
        long end = now + timeout < 0 ? Long.MAX_VALUE : now + timeout;

        // Don't wait longer than session timeout.
        if (end > endTime)
            end = endTime;

        synchronized (mux) {
            while (!closed && !attrs.keySet().containsAll(keys) && now < end) {
                mux.wait(end - now);

                now = System.currentTimeMillis();
            }

            if (closed)
                throw new InterruptedException("Session was closed: " + this);

            Map<Object, Object> retVal = new HashMap<Object, Object>(keys.size());

            for (Object key : keys)
                retVal.put(key, attrs.get(key));

            return retVal;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttributes(Map<?, ?> attrs, long timeout) throws InterruptedException {
        A.notNull(attrs, "attrs");

        if (attrs.isEmpty()) {
            return true;
        }

        if (timeout == 0)
            timeout = Long.MAX_VALUE;

        long now = System.currentTimeMillis();

        // Prevent overflow.
        long end = now + timeout < 0 ? Long.MAX_VALUE : now + timeout;

        // Don't wait longer than session timeout.
        if (end > endTime)
            end = endTime;

        synchronized (mux) {
            boolean isFound = false;

            while (!closed && !(isFound = this.attrs.entrySet().containsAll(attrs.entrySet())) && now < end) {
                mux.wait(end - now);

                now = System.currentTimeMillis();
            }

            if (closed)
                throw new InterruptedException("Session was closed: " + this);

            return isFound;
        }
    }

    /** {@inheritDoc} */
    @Override public String getTaskName() {
        return taskName;
    }

    /**
     * Returns task class name.
     *
     * @return Task class name.
     */
    public String getTaskClassName() {
        return taskClsName;
    }

    /** {@inheritDoc} */
    @Override public UUID getId() {
        return sesId;
    }

    /** {@inheritDoc} */
    @Override public long getEndTime() {
        return endTime;
    }

    /**
     * @return Task version.
     */
    public String getUserVersion() {
        return userVer;
    }

    /** {@inheritDoc} */
    @Override public ClassLoader getClassLoader() {
        synchronized (mux) {
            return clsLdr;
        }
    }

    /**
     * @param clsLdr Class loader.
     */
    public void setClassLoader(ClassLoader clsLdr) {
        assert clsLdr != null;

        synchronized (mux) {
            this.clsLdr = clsLdr;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isTaskNode() {
        return taskNodeId.equals(ctx.discovery().localNode().id());
    }

    /** {@inheritDoc} */
    @Override public Collection<GridJobSibling> refreshJobSiblings() throws GridException {
        return getJobSiblings();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridJobSibling> getJobSiblings() throws GridException {
        synchronized (mux) {
            return siblings;
        }
    }

    /**
     * @param siblings Siblings.
     */
    public void setJobSiblings(Collection<GridJobSibling> siblings) {
        synchronized (mux) {
            this.siblings = Collections.unmodifiableCollection(siblings);
        }
    }

    /**
     * @param siblings Siblings.
     */
    public void addJobSiblings(Collection<GridJobSibling> siblings) {
        assert isTaskNode();

        synchronized (mux) {
            Collection<GridJobSibling> tmp = new ArrayList<GridJobSibling>(this.siblings);

            tmp.addAll(siblings);

            this.siblings = Collections.unmodifiableCollection(tmp);
        }
    }

    /** {@inheritDoc} */
    @Override public GridJobSibling getJobSibling(UUID jobId) throws GridException {
        A.notNull(jobId, "jobId");

        Collection<GridJobSibling> tmp = getJobSiblings();

        for (GridJobSibling sibling : tmp)
            if (sibling.getJobId().equals(jobId))
                return sibling;

        return null;
    }

    /** {@inheritDoc} */
    @Override public void setAttribute(Object key, Object val) throws GridException {
        A.notNull(key, "key");

        setAttributes(Collections.singletonMap(key, val));
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> V getAttribute(K key) {
        A.notNull(key, "key");

        synchronized (mux) {
            return (V)attrs.get(key);
        }
    }

    /** {@inheritDoc} */
    @Override public void setAttributes(Map<?, ?> attrs) throws GridException {
        A.notNull(attrs, "attrs");

        if (attrs.isEmpty())
            return;

        // Note that there is no mux notification in this block.
        // The reason is that we wait for ordered attributes to
        // come back from task prior to notification. The notification
        // will happen in 'setInternal(...)' method.
        synchronized (mux) {
            this.attrs.putAll(attrs);
        }

        if (isTaskNode())
            ctx.task().setAttributes(this, attrs);
    }

    /** {@inheritDoc} */
    @Override public Map<Object, Object> getAttributes() {
        synchronized (mux) {
            return U.sealMap(attrs);
        }
    }

    /**
     * @param attrs Attributes to set.
     */
    public void setInternal(Map<?, ?> attrs) {
        A.notNull(attrs, "attrs");

        if (attrs.isEmpty())
            return;

        List<GridTaskSessionAttributeListener> lsnrs;

        synchronized (mux) {
            this.attrs.putAll(attrs);

            lsnrs = this.lsnrs;

            mux.notifyAll();
        }

        for (Map.Entry<?, ?> entry : attrs.entrySet())
            for (GridTaskSessionAttributeListener lsnr : lsnrs)
                lsnr.onAttributeSet(entry.getKey(), entry.getValue());
    }

    /** {@inheritDoc} */
    @Override public void addAttributeListener(GridTaskSessionAttributeListener lsnr, boolean rewind) {
        A.notNull(lsnr, "lsnr");

        Map<Object, Object> attrs = null;

        List<GridTaskSessionAttributeListener> lsnrs;

        synchronized (mux) {
            lsnrs = new ArrayList<GridTaskSessionAttributeListener>(this.lsnrs.size());

            lsnrs.addAll(this.lsnrs);

            lsnrs.add(lsnr);

            lsnrs = Collections.unmodifiableList(lsnrs);

            this.lsnrs = lsnrs;

            if (rewind)
                attrs = new HashMap<Object, Object>(this.attrs);
        }

        if (rewind) {
            for (Map.Entry<Object, Object> entry : attrs.entrySet()) {
                for (GridTaskSessionAttributeListener l : lsnrs) {
                    l.onAttributeSet(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean removeAttributeListener(GridTaskSessionAttributeListener lsnr) {
        A.notNull(lsnr, "lsnr");

        synchronized (mux) {
            List<GridTaskSessionAttributeListener> lsnrs = new ArrayList<GridTaskSessionAttributeListener>(this.lsnrs);

            boolean removed = lsnrs.remove(lsnr);

            this.lsnrs = Collections.unmodifiableList(lsnrs);

            return removed;
        }
    }

    /** {@inheritDoc} */
    @Override public void saveCheckpoint(String key, Object state) throws GridException {
        saveCheckpoint(key, state, GridTaskSessionScope.SESSION_SCOPE, 0);
    }

    /** {@inheritDoc} */
    @Override public void saveCheckpoint(String key, Object state, GridTaskSessionScope scope, long timeout)
        throws GridException {
        saveCheckpoint(key, state, scope, timeout, true);
    }

    /** {@inheritDoc} */
    @Override public void saveCheckpoint(String key, Object state, GridTaskSessionScope scope,
        long timeout, boolean override) throws GridException {
        A.notNull(key, "key");
        A.ensure(timeout >= 0, "timeout >= 0");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to save checkpoint (session closed): " + this);
        }

        ctx.checkpoint().storeCheckpoint(this, key, state, scope, timeout, override);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T loadCheckpoint(String key) throws GridException {
        A.notNull(key, "key");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to load checkpoint (session closed): " + this);
        }

        return (T)ctx.checkpoint().loadCheckpoint(this, key);
    }

    /** {@inheritDoc} */
    @Override public boolean removeCheckpoint(String key) throws GridException {
        A.notNull(key, "key");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to remove checkpoint (session closed): " + this);
        }

        return ctx.checkpoint().removeCheckpoint(this, key);
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(Object key, Object val, GridTaskSessionScope scope) throws GridException {
        A.notNull(key, "key");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to write data (session closed): " + this);
        }

        ctx.swap().write(this, key, val, scope);
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(Object key, Object val) throws GridException {
        writeToSwap(key, val, GridTaskSessionScope.SESSION_SCOPE);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T readFromSwap(Object key) throws GridException {
        A.notNull(key, "key");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to read data (session closed): " + this);
        }

        return (T)ctx.swap().read(this, key);
    }

    /** {@inheritDoc} */
    @Override public void removeFromSwap(Object key) throws GridException {
        A.notNull(key, "key");

        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to remove data (session closed): " + this);
        }

        ctx.swap().remove(this, key);
    }

    /** {@inheritDoc} */
    @Override public void clearSwap() throws GridException {
        synchronized (mux) {
            if (closed)
                throw new GridException("Failed to remove data (session closed): " + this);
        }

        ctx.swap().clear(this);
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> getTopology() throws GridException {
        return F.nodeIds(ctx.topology().getTopology(this, ctx.discovery().allNodes()));
    }

    /**
     * @param key Key.
     * @param val Value.
     * @return {@code true} if key/value pair was set.
     */
    private boolean isAttributeSet(Object key, Object val) {
        if (attrs.containsKey(key)) {
            Object stored = attrs.get(key);

            if (val == null && stored == null)
                return true;

            if (val != null && stored != null)
                return val.equals(stored);
        }

        return false;
    }

    /**
     * @return Topology SPI name.
     */
    @Override public String getTopologySpi() {
        return topSpi;
    }

    /**
     * @param topSpi Topology SPI name.
     */
    public void setTopologySpi(String topSpi) {
        this.topSpi = topSpi;
    }

    /** {@inheritDoc} */
    @Override public String getCheckpointSpi() {
        return cpSpi;
    }

    /**
     * @param cpSpi Checkpoint SPI name.
     */
    public void setCheckpointSpi(String cpSpi) {
        this.cpSpi = cpSpi;
    }

    /**
     *
     * @return SwapSpace SPI name.
     */
    public String getSwapSpaceSpi() {
        return swapSpi;
    }

    /**
     *
     * @param swapSpi SwapSpace SPI name.
     */
    public void setSwapSpaceSpi(String swapSpi) {
        this.swapSpi = swapSpi;
    }

    /**
     * @return Failover SPI name.
     */
    public String getFailoverSpi() {
        return failSpi;
    }

    /**
     * @param failSpi Failover SPI name.
     */
    public void setFailoverSpi(String failSpi) {
        this.failSpi = failSpi;
    }

    /**
     * @return Load balancing SPI name.
     */
    public String getLoadBalancingSpi() {
        return loadSpi;
    }

    /**
     * @param loadSpi Load balancing SPI name.
     */
    public void setLoadBalancingSpi(String loadSpi) {
        this.loadSpi = loadSpi;
    }

    /**
     * @return Task internal version.
     */
    public long getSequenceNumber() {
        return seqNum;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskSessionImpl.class, this);
    }
}
