// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * This class provide implementation for task future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
 */
public class GridTaskFutureImpl<R> extends GridFutureAdapter<R> implements GridTaskFuture<R> {
    /** */
    private transient GridTaskSession ses;

    /** Mapped flag. */
    private boolean mapped;

    /** */
    private transient GridKernalContext ctx;

    /**
     * Required by {@link Externalizable}.
     */
    public GridTaskFutureImpl() {
        // No-op.
    }

    /**
     * @param ses Task session instance.
     * @param ctx Kernal context.
     */
    public GridTaskFutureImpl(GridTaskSession ses, GridKernalContext ctx) {
        super(ctx);
        
        assert ses != null;
        assert ctx != null;

        this.ses = ses;
        this.ctx = ctx;
    }

    /**
     * Gets task timeout.
     *
     * @return Task timeout.
     */
    @Override public GridTaskSession getTaskSession() {
        if (ses == null)
            throw new IllegalStateException("Cannot access task session after future has been deserialized.");

        return ses;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws GridException {
        checkValid();
        
        if (onCancelled()) {
            assert ctx != null;
            
            ctx.io().send(ctx.discovery().allNodes(), TOPIC_CANCEL, new GridJobCancelRequest(ses.getId()), SYSTEM_POOL);

            return true;
        }

        return isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean isMapped() {
        synchronized (mutex()) {
            return mapped;
        }
    }

    /**
     * Callback for completion of task mapping stage.
     */
    public void onMapped() {
        synchronized (mutex()) {
            mapped = true;

            mutex().notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap() throws GridException {
        synchronized (mutex()) {
            while (!mapped && !isDone())
                try {
                    mutex().wait();
                }
                catch (InterruptedException e) {
                    throw new GridInterruptedException("Got interrupted while waiting for map completion.", e);
                }

            return mapped;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap(long timeout) throws GridException {
        return waitForMap(timeout, MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public boolean waitForMap(long timeout, TimeUnit unit) throws GridException {
        long now = System.currentTimeMillis();

        long end = now + MILLISECONDS.convert(timeout, unit);

        // Account for overflow.
        if (end < 0)
            end = Long.MAX_VALUE;

        synchronized (mutex()) {
            while (!mapped && !isDone() && now < end) {
                try {
                    mutex().wait(end - now);
                }
                catch (InterruptedException e) {
                    throw new GridInterruptedException("Got interrupted while waiting for map completion.", e);
                }

                now = System.currentTimeMillis();
            }

            return mapped;
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        if (isDone())
            out.writeBoolean(isMapped());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        if (isValid()) {
            boolean mapped = in.readBoolean();

            synchronized (mutex()) {
                this.mapped = mapped;
            }
        }

        ses = null;
        ctx = null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskFutureImpl.class, this);
    }
}
