// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * This class defines externalizable job cancellation request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobCancelRequest implements Externalizable {
    /** */
    private UUID sesId;

    /** */
    private UUID jobId;

    /** */
    private boolean system;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridJobCancelRequest() {
        // No-op.
    }

    /**
     * @param sesId Task session ID.
     */
    public GridJobCancelRequest(UUID sesId) {
        assert sesId != null;

        this.sesId = sesId;

        jobId = null;
    }

    /**
     * @param sesId Task session ID.
     * @param jobId Job ID.
     */
    public GridJobCancelRequest(UUID sesId, UUID jobId) {
        assert sesId != null;

        this.sesId = sesId;
        this.jobId = jobId;
    }

    /**
     * @param sesId Session ID.
     * @param jobId Job ID.
     * @param system System flag.
     */
    public GridJobCancelRequest(UUID sesId, UUID jobId, boolean system) {
        assert sesId != null;

        this.sesId = sesId;
        this.jobId = jobId;
        this.system = system;
    }

    /**
     * Gets execution ID of task to be cancelled.
     *
     * @return Execution ID of task to be cancelled.
     */
    public UUID getSessionId() {
        return sesId;
    }

    /**
     * Gets session ID of job to be cancelled. If {@code null}, then
     * all jobs for the specified task execution ID will be cancelled.
     *
     * @return Execution ID of job to be cancelled.
     */
    public UUID getJobId() {
        return jobId;
    }

    /**
     * @return {@code True} if request to cancel is sent out of system when task
     *       has already reduced and further results are no longer interesting.
     */
    public boolean isSystem() {
        return system;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, sesId);
        U.writeUuid(out, jobId);

        out.writeBoolean(system);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        sesId = U.readUuid(in);
        jobId = U.readUuid(in);

        system = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobCancelRequest.class, this);
    }
}
