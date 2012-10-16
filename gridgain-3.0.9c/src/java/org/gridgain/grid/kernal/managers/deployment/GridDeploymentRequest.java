// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDeploymentRequest implements Externalizable {
    /** Response topic name. Response should be sent back to this topic. */
    private String resTopic;

    /** Requested class name. */
    private String rsrcName;

    /** Class loader ID. */
    private UUID ldrId;

    /** Undeploy flag. */
    private boolean isUndeploy;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridDeploymentRequest() {
        // No-op.
    }

    /**
     * Creates new request.
     *
     * @param ldrId Class loader ID.
     * @param rsrcName Resource name that should be found and sent back.
     * @param isUndeploy Undeploy property.
     */
    GridDeploymentRequest(UUID ldrId, String rsrcName, boolean isUndeploy) {
        assert isUndeploy || ldrId != null;
        assert rsrcName != null;

        this.ldrId = ldrId;
        this.rsrcName = rsrcName;
        this.isUndeploy = isUndeploy;
    }

    /**
     * Sets response topic.
     *
     * @param resTopic New response topic.
     */
    void setResponseTopic(String resTopic) {
        this.resTopic = resTopic;
    }

    /**
     * Get topic response should be sent to.
     *
     * @return Response topic name.
     */
    String getResponseTopic() {
        return resTopic;
    }

    /**
     * Class name/resource name that is being requested.
     *
     * @return Resource or class name.
     */
    String getResourceName() {
        return rsrcName;
    }

    /**
     * Gets property ldrId.
     *
     * @return Property ldrId.
     */
    UUID getClassLoaderId() {
        return ldrId;
    }

    /**
     * Gets property undeploy.
     *
     * @return Property undeploy.
     */
    boolean isUndeploy() {
        return isUndeploy;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(isUndeploy);
        U.writeString(out, resTopic);
        U.writeString(out, rsrcName);
        U.writeUuid(out, ldrId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        isUndeploy = in.readBoolean();
        resTopic = U.readString(in);
        rsrcName = U.readString(in);
        ldrId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentRequest.class, this);
    }
}
