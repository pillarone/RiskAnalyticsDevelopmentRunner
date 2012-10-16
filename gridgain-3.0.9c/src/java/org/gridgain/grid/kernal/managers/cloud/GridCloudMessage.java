// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.GridCloudResourceShadow;
import org.gridgain.grid.spi.cloud.GridCloudSpiSnapshot;
import org.gridgain.grid.typedef.F;
import org.gridgain.grid.typedef.internal.S;
import org.gridgain.grid.typedef.internal.U;
import org.gridgain.jsr305.NonNegative;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import static org.gridgain.grid.GridEventType.EVT_CLOUD_RESOURCE_REMOVED;

/**
 * Cloud state message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudMessage implements Externalizable {
    /** Command execution event message. */
    static final int CMD_EVT_TYPE = 1;

    /** Last cloud state message. */
    static final int LAST_CLOUD_STATE_TYPE = 2;

    /** Resource event message. */
    static final int RSRC_EVT_TYPE = 3;

    /** Last cloud version message. */
    static final int LAST_CLOUD_VER_TYPE = 4;

    /** Message type. */
    private int type;

    /** Cloud ID. */
    private String cloudId;

    /** Command execution ID. */
    private UUID cmdExecId;

    /** Event type. */
    private int evtType;

    /** Message. */
    private String msg;

    /** Cloud version. */
    private long ver;

    /** Cloud snapshot. */
    private GridCloudSpiSnapshot snp;

    /** Cloud resource ID. */
    private String rsrcId;

    /** Cloud resource type. */
    private int rsrcType;

    /** Cloud resource shadow. */
    private GridCloudResourceShadow shadow;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     */
    public GridCloudMessage() {
        /** No-op. */
    }

    /**
     * Creates cloud command execution event message.
     *
     * @param cloudId Cloud ID.
     * @param cmdExecId Command execution ID.
     * @param evtType Event type.
     * @param msg Optional message.
     */
    GridCloudMessage(String cloudId, UUID cmdExecId, @NonNegative int evtType, String msg) {
        assert !F.isEmpty(cloudId);
        assert cmdExecId != null;
        assert evtType > 0;

        type = CMD_EVT_TYPE;

        this.cloudId = cloudId;
        this.cmdExecId = cmdExecId;
        this.evtType = evtType;
        this.msg = msg;
    }

    /**
     * Creates last cloud state message.
     *
     * @param cloudId Cloud ID.
     * @param ver Cloud version.
     * @param snp Cloud snapshot.
     */
    GridCloudMessage(String cloudId, long ver, GridCloudSpiSnapshot snp) {
        assert !F.isEmpty(cloudId);
        assert ver > 0;
        assert snp != null;

        type = LAST_CLOUD_STATE_TYPE;

        this.cloudId = cloudId;
        this.ver = ver;
        this.snp = snp;
    }

    /**
     * Creates resource event message.
     *
     * @param cloudId Cloud ID.
     * @param rsrcId Cloud resource ID.
     * @param rsrcType Cloud resource type.
     * @param evtType Event type.
     * @param shadow Cloud resource shadow.
     */
    GridCloudMessage(String cloudId, String rsrcId, int rsrcType, @NonNegative int evtType,
        GridCloudResourceShadow shadow) {
        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(rsrcId);
        assert rsrcType > 0;
        assert evtType > 0;
        assert evtType != EVT_CLOUD_RESOURCE_REMOVED && shadow == null ||
            evtType == EVT_CLOUD_RESOURCE_REMOVED && shadow != null;

        type = RSRC_EVT_TYPE;

        this.cloudId = cloudId;
        this.rsrcId = rsrcId;
        this.rsrcType = rsrcType;
        this.evtType = evtType;
        this.shadow = shadow;
    }

    /**
     * Creates last cloud version message.
     *
     * @param cloudId Cloud ID.
     * @param ver Cloud version.
     */
    GridCloudMessage(String cloudId, long ver) {
        assert !F.isEmpty(cloudId);
        assert ver > 0;

        type = LAST_CLOUD_VER_TYPE;

        this.cloudId = cloudId;
        this.ver = ver;
    }

    /**
     * Gets message type.
     *
     * @return Message type.
     */
    int getType() {
        return type;
    }

    /**
     * Gets cloud ID.
     * @return Cloud ID.
     */
    String getCloudId() {
        return cloudId;
    }

    /**
     * Gets command execution ID.
     *
     * @return Command execution ID.
     */
    UUID getCommandExecutionId() {
        return cmdExecId;
    }

    /**
     * Gets event type.
     *
     * @return Event type.
     */
    int getEventType() {
        return evtType;
    }

    /**
     * Gets message.
     *
     * @return Message.
     */
    String getMessage() {
        return msg;
    }

    /**
     * Gets cloud version.
     *
     * @return Cloud version.
     */
    long getVersion() {
        return ver;
    }

    /**
     * Sets cloud version.
     *
     * @param ver Cloud version.
     */
    void setVersion(long ver) {
        this.ver = ver;
    }

    /**
     * Gets cloud snapshot.
     *
     * @return Cloud snapshot.
     */
    GridCloudSpiSnapshot getSnapshot() {
        return snp;
    }

    /**
     * Gets cloud resource ID.
     *
     * @return Cloud resource ID.
     */
    String getResourceId() {
        return rsrcId;
    }

    /**
     * Gets cloud resource type.
     *
     * @return Cloud resource type.
     */
    int getResourceType() {
        return rsrcType;
    }

    /**
     * Gets cloud resource shadow.
     *
     * @return Cloud resource shadow.
     */
    GridCloudResourceShadow getShadow() {
        return shadow;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(type);
        U.writeString(out, cloudId);
        U.writeUuid(out, cmdExecId);
        out.writeInt(evtType);
        U.writeString(out, msg);
        out.writeLong(ver);
        out.writeObject(snp);
        U.writeString(out, rsrcId);
        out.writeInt(rsrcType);
        out.writeObject(shadow);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = in.readInt();
        cloudId = U.readString(in);
        cmdExecId = U.readUuid(in);
        evtType = in.readInt();
        msg = U.readString(in);
        ver = in.readLong();
        snp = (GridCloudSpiSnapshot)in.readObject();
        rsrcId = U.readString(in);
        rsrcType = in.readInt();
        shadow = (GridCloudResourceShadow)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudMessage.class, this);
    }
}
