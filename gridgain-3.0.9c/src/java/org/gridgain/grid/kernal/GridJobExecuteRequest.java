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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.util.*;

/**
 * This class defines externalizable job execution request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobExecuteRequest implements GridTaskMessage, Externalizable {
    /** */
    private UUID sesId;

    /** */
    private UUID jobId;

    /** */
    @GridToStringExclude
    private GridByteArrayList jobBytes;

    /** */
    private long startTaskTime = -1;

    /** */
    private long timeout = -1;

    /** */
    private String taskName;

    /** */
    private String userVer;

    /** */
    private long seqNum;

    /** */
    private String taskClsName;

    /** Node class loader participants. */
    private Map<UUID, GridTuple2<UUID, Long>> ldrParticipants;

    /** ID of the node that initiated the task. */
    private UUID taskNodeId;

    /** */
    @GridToStringExclude
    private GridByteArrayList sesAttrs;

    /** */
    @GridToStringExclude
    private GridByteArrayList jobAttrs;

    /** Checkpoint SPI name. */
    private String cpSpi;

    /** */
    private Collection<GridJobSibling> siblings;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient long createTime = System.currentTimeMillis();

    /** */
    private UUID clsLdrId;

    /** */
    private GridDeploymentMode depMode;

    /** */
    private boolean dynamicSiblings;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     */
    public GridJobExecuteRequest() {
        // No-op.
    }

    /**
     * @param sesId Task session ID.
     * @param jobId Job ID.
     * @param taskName Task name.
     * @param userVer Code version.
     * @param seqNum Internal task version for the task originating node.
     * @param taskClsName Fully qualified task name.
     * @param jobBytes Job serialized body.
     * @param startTaskTime Task execution start time.
     * @param timeout Task execution timeout.
     * @param taskNodeId Original task execution node ID.
     * @param siblings Collection of split siblings.
     * @param sesAttrs Map of session attributes.
     * @param jobAttrs Job context attributes.
     * @param cpSpi Collision SPI.
     * @param clsLdrId Task local class loader id.
     * @param depMode Task deployment mode.
     * @param dynamicSiblings {@code True} if siblings are dynamic.
     * @param ldrParticipants Other node class loader IDs that can also load classes
     *      for this task.
     */
    public GridJobExecuteRequest(UUID sesId, UUID jobId, String taskName, String userVer, long seqNum,
        String taskClsName, GridByteArrayList jobBytes, long startTaskTime, long timeout, UUID taskNodeId,
        Collection<GridJobSibling> siblings, GridByteArrayList sesAttrs, GridByteArrayList jobAttrs, String cpSpi,
        UUID clsLdrId, GridDeploymentMode depMode, boolean dynamicSiblings,
        Map<UUID, GridTuple2<UUID, Long>> ldrParticipants) {
        assert sesId != null;
        assert jobId != null;
        assert taskName != null;
        assert taskClsName != null;
        assert jobBytes != null;
        assert taskNodeId != null;
        assert sesAttrs != null;
        assert jobAttrs != null;
        assert clsLdrId != null;
        assert userVer != null;
        assert seqNum >= -1;
        assert depMode != null;

        this.sesId = sesId;
        this.jobId = jobId;
        this.taskName = taskName;
        this.userVer = userVer;
        this.taskClsName = taskClsName;
        this.jobBytes = jobBytes;
        this.startTaskTime = startTaskTime;
        this.timeout = timeout;
        this.taskNodeId = taskNodeId;
        this.siblings = siblings;
        this.sesAttrs = sesAttrs;
        this.jobAttrs = jobAttrs;
        this.clsLdrId = clsLdrId;
        this.depMode = depMode;
        this.seqNum = seqNum;
        this.dynamicSiblings = dynamicSiblings;
        this.ldrParticipants = ldrParticipants;

        this.cpSpi = cpSpi == null || cpSpi.length() == 0 ? null : cpSpi;
    }

    /** {@inheritDoc} */
    @Override public UUID getSessionId() {
        return sesId;
    }

    /**
     * @return Job session ID.
     */
    public UUID getJobId() {
        return jobId;
    }

    /**
     * @return Task version.
     */
    public String getTaskClassName() {
        return taskClsName;
    }

    /**
     * @return Task name.
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * @return Task version.
     */
    public String getUserVersion() {
        return userVer;
    }

    /**
     * @return Serialized job bytes.
     */
    public GridByteArrayList getJobBytes() {
        return jobBytes;
    }

    /**
     * @return Task start time.
     */
    public long getStartTaskTime() {
        return startTaskTime;
    }

    /**
     * @return Timeout.
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * @return Task node ID.
     */
    public UUID getTaskNodeId() {
        return taskNodeId;
    }

    /**
     * Gets this instance creation time.
     *
     * @return This instance creation time.
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * @return Job siblings.
     */
    public Collection<GridJobSibling> getSiblings() {
        return siblings;
    }

    /**
     * @return Session attributes.
     */
    public GridByteArrayList getSessionAttributes() {
        return sesAttrs;
    }

    /**
     * @return Job attributes.
     */
    public GridByteArrayList getJobAttributes() {
        return jobAttrs;
    }

    /**
     * @return Checkpoint SPI name.
     */
    public String getCheckpointSpi() {
        return cpSpi;
    }

    /**
     * @return Task local class loader id.
     */
    public UUID getClassLoaderId() {
        return clsLdrId;
    }

    /**
     * @return TODO
     */
    public Long getSequenceNumber() {
        return seqNum;
    }

    /**
     * @return TODO
     */
    public GridDeploymentMode getDeploymentMode() {
        return depMode;
    }

    /**
     * Returns true if siblings list is dynamic, i.e. task is continuous.
     *
     * @return True if siblings list is dynamic.
     */
    public boolean isDynamicSiblings() {
        return dynamicSiblings;
    }

    /**
     * @return Node class loader participant map.
     */
    public Map<UUID, GridTuple2<UUID, Long>> getLoaderParticipants() {
        return ldrParticipants;
    }

    /**
     * @param ldrParticipants Node class loader participant map.
     */
    public void setLoaderParticipants(Map<UUID, GridTuple2<UUID, Long>> ldrParticipants) {
        this.ldrParticipants = ldrParticipants;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(depMode.ordinal());

        out.writeLong(startTaskTime);
        out.writeLong(timeout);
        out.writeLong(seqNum);

        out.writeObject(siblings);
        out.writeObject(jobBytes);
        out.writeObject(sesAttrs);
        out.writeObject(jobAttrs);
        out.writeObject(ldrParticipants);

        out.writeBoolean(dynamicSiblings);

        U.writeString(out, userVer);
        U.writeString(out, cpSpi);
        U.writeString(out, taskName);
        U.writeString(out, taskClsName);

        U.writeUuid(out, sesId);
        U.writeUuid(out, jobId);
        U.writeUuid(out, taskNodeId);
        U.writeUuid(out, clsLdrId);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        depMode = GridDeploymentMode.values()[in.readInt()];

        startTaskTime = in.readLong();
        timeout = in.readLong();
        seqNum = in.readLong();

        siblings = (Collection<GridJobSibling>)in.readObject();
        jobBytes = (GridByteArrayList)in.readObject();
        sesAttrs = (GridByteArrayList)in.readObject();
        jobAttrs = (GridByteArrayList)in.readObject();
        ldrParticipants = (Map<UUID, GridTuple2<UUID, Long>>)in.readObject();

        dynamicSiblings = in.readBoolean();

        userVer = U.readString(in);
        cpSpi = U.readString(in);
        taskName = U.readString(in);
        taskClsName = U.readString(in);

        sesId = U.readUuid(in);
        jobId = U.readUuid(in);
        taskNodeId = U.readUuid(in);
        clsLdrId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobExecuteRequest.class, this);
    }
}
