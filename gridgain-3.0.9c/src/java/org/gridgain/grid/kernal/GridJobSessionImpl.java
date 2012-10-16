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
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Job session implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobSessionImpl extends GridMetadataAwareAdapter implements GridTaskSessionInternal {
    /** Wrapped task session. */
    private final GridTaskSessionImpl ses;

    /** Job ID. */
    private final UUID jobId;

    /** Processor registry. */
    private final GridKernalContext ctx;

    /**
     * @param ctx Kernal context.
     * @param ses Task session.
     * @param jobId Job ID.
     */
    public GridJobSessionImpl(GridKernalContext ctx, GridTaskSessionImpl ses, UUID jobId) {
        assert ctx != null;
        assert ses != null;
        assert jobId != null;

        this.ctx = ctx;
        this.ses = ses;
        this.jobId = jobId;
    }

    /** {@inheritDoc} */
    @Override public UUID getJobId() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public void onClosed() {
        ses.onClosed();
    }

    /** {@inheritDoc} */
    @Override public boolean isClosed() {
        return ses.isClosed();
    }

    /** {@inheritDoc} */
    @Override public boolean isTaskNode() {
        return ses.isTaskNode();
    }

    /** {@inheritDoc} */
    @Override public String getCheckpointSpi() {
        return ses.getCheckpointSpi();
    }

    /** {@inheritDoc} */
    @Override public String getTopologySpi() {
        return ses.getTopologySpi();
    }

    /** {@inheritDoc} */
    @Override public String getTaskName() {
        return ses.getTaskName();
    }

    /** {@inheritDoc} */
    @Override public UUID getTaskNodeId() {
        return ses.getTaskNodeId();
    }

    /** {@inheritDoc} */
    @Override public long getStartTime() {
        return ses.getStartTime();
    }

    /** {@inheritDoc} */
    @Override public long getEndTime() {
        return ses.getEndTime();
    }

    /** {@inheritDoc} */
    @Override public UUID getId() {
        return ses.getId();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader getClassLoader() {
        return ses.getClassLoader();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridJobSibling> refreshJobSiblings() throws GridException {
        if (!isTaskNode()) {
            Collection<GridJobSibling> sibs = ctx.job().requestJobSiblings(this);

            // Request siblings list from task node (task is continuous).
            ses.setJobSiblings(sibs);

            return sibs;
        }

        return ses.getJobSiblings();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridJobSibling> getJobSiblings() throws GridException {
        if (ses.getJobSiblings() == null) {
            assert !isTaskNode();

            // Request siblings list from task node (task is continuous).
            ses.setJobSiblings(ctx.job().requestJobSiblings(this));
        }

        return ses.getJobSiblings();
    }

    /** {@inheritDoc} */
    @Override public GridJobSibling getJobSibling(UUID jobId) throws GridException {
        for (GridJobSibling sib : getJobSiblings())
            if (sib.getJobId().equals(jobId))
                return sib;

        return null;
    }

    /** {@inheritDoc} */
    @Override public void setAttribute(Object key, @Nullable Object val) throws GridException {
        setAttributes(Collections.singletonMap(key, val));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> V getAttribute(K key) {
        return (V)ses.getAttribute(key);
    }

    /** {@inheritDoc} */
    @Override public void setAttributes(Map<?, ?> attrs) throws GridException {
        ses.setAttributes(attrs);

        if (!isTaskNode()) {
            ctx.job().setAttributes(this, attrs);
        }
    }


    /** {@inheritDoc} */
    @Override public Map<?, ?> getAttributes() {
        return ses.getAttributes();
    }

    /** {@inheritDoc} */
    @Override public void addAttributeListener(GridTaskSessionAttributeListener lsnr, boolean rewind) {
        ses.addAttributeListener(lsnr, rewind);
    }

    /** {@inheritDoc} */
    @Override public boolean removeAttributeListener(GridTaskSessionAttributeListener lsnr) {
        return ses.removeAttributeListener(lsnr);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> V waitForAttribute(K key) throws InterruptedException {
        return (V)ses.waitForAttribute(key);
    }

    /** {@inheritDoc} */
    @Override public <K, V> boolean waitForAttribute(K key, @Nullable V val) throws InterruptedException {
        return ses.waitForAttribute(key, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> V waitForAttribute(K key, long timeout) throws InterruptedException {
        return (V)ses.waitForAttribute(key, timeout);
    }

    /** {@inheritDoc} */
    @Override public <K, V> boolean waitForAttribute(K key, @Nullable V val, long timeout)
        throws InterruptedException {
        return ses.waitForAttribute(key, val, timeout);
    }

    /** {@inheritDoc} */
    @Override public Map<?, ?> waitForAttributes(Collection<?> keys) throws InterruptedException {
        return ses.waitForAttributes(keys);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttributes(Map<?, ?> attrs) throws InterruptedException {
        return ses.waitForAttributes(attrs);
    }

    /** {@inheritDoc} */
    @Override public Map<?, ?> waitForAttributes(Collection<?> keys, long timeout) throws InterruptedException {
        return ses.waitForAttributes(keys, timeout);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForAttributes(Map<?, ?> attrs, long timeout) throws InterruptedException {
        return ses.waitForAttributes(attrs, timeout);
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

        if (ses.isClosed())
            throw new GridException("Failed to save checkpoint (session closed): " + this);

        ctx.checkpoint().storeCheckpoint(this, key, state, scope, timeout, override);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T loadCheckpoint(String key) throws GridException {
        return (T)ctx.checkpoint().loadCheckpoint(this, key);
    }

    /** {@inheritDoc} */
    @Override public boolean removeCheckpoint(String key) throws GridException {
        return ctx.checkpoint().removeCheckpoint(this, key);
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(Object key, @Nullable Object val, GridTaskSessionScope scope)
        throws GridException {
        ses.writeToSwap(key, val, scope);
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(Object key, @Nullable Object val) throws GridException {
        ses.writeToSwap(key, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T readFromSwap(Object key) throws GridException {
        return (T)ses.readFromSwap(key);
    }

    /** {@inheritDoc} */
    @Override public void removeFromSwap(Object key) throws GridException {
        ses.removeFromSwap(key);
    }

    /** {@inheritDoc} */
    @Override public void clearSwap() throws GridException {
        ses.clearSwap();
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> getTopology() throws GridException {
        return ses.getTopology();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobSessionImpl.class, this);
    }
}
