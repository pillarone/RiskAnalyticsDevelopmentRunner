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
import java.io.*;
import java.util.*;

import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * This class provides implementation for job sibling.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobSiblingImpl extends GridMetadataAwareAdapter implements GridJobSibling, Externalizable {
    /** */
    private UUID sesId;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) // Suppress (this field is final). 
    private UUID jobId;

    /** */
    private transient String taskTopic;

    /** */
    private transient String jobTopic;

    /** */
    private transient UUID nodeId;

    /** */
    private transient boolean isJobDone;

    /** */
    private transient GridKernalContext ctx;

    /** */
    public GridJobSiblingImpl() {
        // No-op.
    }

    /**
     * @param sesId Task session ID.
     * @param jobId Job ID.
     * @param nodeId ID of the node where this sibling was sent for execution.
     * @param ctx Managers registry.
     */
    public GridJobSiblingImpl(UUID sesId, UUID jobId, UUID nodeId, GridKernalContext ctx) {
        assert sesId != null;
        assert jobId != null;
        assert nodeId != null;
        assert ctx != null;

        this.sesId = sesId;
        this.jobId = jobId;
        this.nodeId = nodeId;
        this.ctx = ctx;

        taskTopic = TOPIC_TASK.name(jobId, nodeId);
        jobTopic = TOPIC_JOB.name(jobId, nodeId);
    }

    /** {@inheritDoc} */
    @Override public UUID getJobId() {
        return jobId;
    }

    /**
     * @return Node ID.
     */
    public synchronized UUID nodeId() {
        return nodeId;
    }

    /**
     * @param nodeId Node where this sibling is executing.
     */
    public synchronized void nodeId(UUID nodeId) {
        this.nodeId = nodeId;

        taskTopic = TOPIC_TASK.name(jobId, nodeId);
        jobTopic = TOPIC_JOB.name(jobId, nodeId);
    }

    /**
     * @return {@code True} if job has finished.
     */
    public synchronized boolean isJobDone() {
        return isJobDone;
    }

    /** */
    public synchronized void onJobDone() {
        isJobDone = true;
    }

    /**
     * @return Communication topic for receiving.
     */
    public synchronized String taskTopic() {
        return taskTopic;
    }

    /**
     * @return Communication topic for sending.
     */
    public synchronized String jobTopic() {
        return jobTopic;
    }

    /** {@inheritDoc} */
    @Override public void cancel() throws GridException {
        ctx.io().send(ctx.discovery().allNodes(), TOPIC_CANCEL,
            new GridJobCancelRequest(sesId, jobId), SYSTEM_POOL);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        // Don't serialize node ID.
        U.writeUuid(out, sesId);
        U.writeUuid(out, jobId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Don't serialize node ID.
        sesId = U.readUuid(in);
        jobId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobSiblingImpl.class, this);
    }
}
