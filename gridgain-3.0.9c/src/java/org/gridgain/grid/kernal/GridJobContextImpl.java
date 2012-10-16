// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Remote job context implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobContextImpl extends GridMetadataAwareAdapter implements GridJobContext {
    /** Kernal context ({@code null} for job result context). */
    private GridKernalContext ctx;

    /** */
    private UUID jobId;

    /** */
    @GridToStringInclude private final Map<Object, Object> attrs = new HashMap<Object, Object>(1);

    /**
     * @param ctx Kernal context.
     * @param jobId Job ID.
     */
    public GridJobContextImpl(@Nullable GridKernalContext ctx, UUID jobId) {
        assert jobId != null;

        this.ctx = ctx;
        this.jobId = jobId;
    }

    /**
     * @param ctx Kernal context.
     * @param jobId Job ID.
     * @param attrs Job attributes.
     */
    public GridJobContextImpl(GridKernalContext ctx, UUID jobId,
        Map<? extends Serializable, ? extends Serializable> attrs) {
        this(ctx, jobId);

        synchronized (this.attrs) {
            this.attrs.putAll(attrs);
        }
    }

    /** {@inheritDoc} */
    @Override public UUID getJobId() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public void setAttribute(Object key, @Nullable Object val) {
        A.notNull(key, "key");

        synchronized (attrs) {
            attrs.put(key, val);
        }
    }

    /** {@inheritDoc} */
    @Override public void setAttributes(Map<?, ?> attrs) {
        A.notNull(attrs, "attrs");

        synchronized (this.attrs) {
            this.attrs.putAll(attrs);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> V getAttribute(K key) {
        A.notNull(key, "key");

        synchronized (attrs) {
            return (V)attrs.get(key);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<Object, Object> getAttributes() {
        synchronized (attrs) {
            return U.sealMap(attrs);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean heldcc() {
        if (ctx == null) {
            return false;
        }

        GridJobWorker job = ctx.job().activeJob(jobId);

        return job != null && job.held();
    }

    /** {@inheritDoc} */
    @Override public <T> T holdcc() {
        return this.<T>holdcc(0);
    }

    /** {@inheritDoc} */
    @Override public <T> T holdcc(long timeout) {
        if (ctx != null) {
            GridJobWorker job = ctx.job().activeJob(jobId);

            // Completed?
            if (job != null) {
                if (timeout > 0 && !job.isDone()) {
                    final long endTime = System.currentTimeMillis() + timeout;

                    // Overflow.
                    if (endTime > 0) {
                        ctx.timeout().addTimeoutObject(new GridTimeoutObject() {
                            private final UUID id = UUID.randomUUID();

                            @Override public UUID timeoutId() {
                                return id;
                            }

                            @Override public long endTime() {
                                return endTime;
                            }

                            @Override public void onTimeout() {
                                callcc();
                            }
                        });
                    }
                }

                job.hold();
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void callcc() {
        if (ctx != null) {
            GridJobWorker job = ctx.job().activeJob(jobId);

            if (job != null) {
                // Execute in the same thread.
                job.execute();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobContextImpl.class, this);
    }
}
