// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.jsr305.*;

import java.io.*;
import java.util.*;

/**
 * Cloud control response. Coordinator node sends it as receipt.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudControlResponse implements Externalizable {
    /** Response status: node that received command is not cloud coordinator. */
    static final int RS_NOT_CRD = 1;

    /** Response status: unknown cloud. */
    static final int RS_UNKNOWN_CLOUD = 2;

    /** Response status: OK, commands has been received and coordinator will try execute them. */
    static final int RS_OK = 200;

    /** Cloud ID. */
    private String cloudId;

    /** ID of the node sent the {@link GridCloudControlRequest}. */
    private UUID rcptId;

    /** ID of the request this response send in response to. */
    private UUID reqId;

    /** Status of the response. */
    private int respStatus;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     */
    public GridCloudControlResponse() {
        /* No-op. */
    }

    /**
     * Creates cloud control response.
     *
     * @param cloudId Cloud ID.
     * @param rcptId Cloud coordinator node ID.
     * @param reqId Command request ID.
     * @param respStatus Request result.
     */
    GridCloudControlResponse(String cloudId, UUID rcptId, UUID reqId, @NonNegative int respStatus) {
        assert cloudId != null;
        assert rcptId != null;
        assert reqId != null;
        assert respStatus > 0;

        this.cloudId = cloudId;
        this.rcptId = rcptId;
        this.reqId = reqId;
        this.respStatus = respStatus;
    }

    /**
     * Gets cloud ID.
     *
     * @return Cloud ID.
     */
    String getCloudId() {
        return cloudId;
    }

    /**
     * Gets recipient ID.
     *
     * @return Recipient ID.
     */
    UUID getRecipientId() {
        return rcptId;
    }
    /**
     * Gets ID of the request this response send in response to.
     *
     * @return command request ID
     */
    UUID getRequestId() {
        return reqId;
    }

    /**
     * Gets response status.
     *
     * @return Request result.
     */
    int getRespStatus() {
        return respStatus;
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        cloudId = U.readString(in);
        rcptId = U.readUuid(in);
        reqId = U.readUuid(in);
        respStatus = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, cloudId);
        U.writeUuid(out, rcptId);
        U.writeUuid(out, reqId);
        out.writeInt(respStatus);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudControlResponse.class, this);
    }
}
