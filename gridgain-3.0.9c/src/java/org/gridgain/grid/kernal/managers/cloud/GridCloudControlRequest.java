// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * Cloud control request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudControlRequest implements Externalizable {
    /** Cloud ID. */
    private String cloudId;
    
    /** Commands map. */
    private Map<UUID, GridCloudCommand> cmds;

    /** Request ID. */
    private UUID reqId;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     */
    public GridCloudControlRequest() {
        /* No-op. */
    }

    /**
     * Creates cloud control request.
     *
     * @param cloudId Cloud ID.
     * @param cmds Cloud commands.
     * @param reqId Command request ID.
     */
    GridCloudControlRequest(String cloudId, Map<UUID, GridCloudCommand> cmds, UUID reqId) {
        assert cloudId != null;
        assert !F.isEmpty(cmds);
        assert reqId != null;

        this.cloudId = cloudId;
        this.cmds = cmds;
        this.reqId = reqId;
    }

    /**
     * Get cloud ID.
     *
     * @return Cloud ID.
     */
    String getCloudId() {
        return cloudId;
    }

    /**
     * Gets collection of cloud commands.
     *
     * @return Cloud commands.
     */
    Map<UUID, GridCloudCommand> getCommands() {
        return cmds;
    }

    /**
     * Gets command request ID.
     *
     * @return command request ID
     */
    UUID getRequestId() {
        return reqId;
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        cloudId = U.readString(in);
        cmds = U.readMap(in);
        reqId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, cloudId);
        U.writeMap(out, cmds);
        U.writeUuid(out, reqId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudControlRequest.class, this);
    }
}
