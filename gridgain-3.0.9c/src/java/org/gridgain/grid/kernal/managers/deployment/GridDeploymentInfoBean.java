// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Deployment info bean.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDeploymentInfoBean implements GridDeploymentInfo, Externalizable {
    /** */
    private UUID clsLdrId;

    /** */
    private GridDeploymentMode depMode;

    /** */
    private String userVer;

    /** Node class loader participant map. */
    private Map<UUID, GridTuple2<UUID, Long>> participants;

    /**
     * Required by {@link Externalizable}.
     */
    public GridDeploymentInfoBean() {
        /* No-op. */
    }

    /**
     * @param clsLdrId Class loader ID.
     * @param depMode Deployment mode.
     * @param userVer User version.
     * @param participants Participants.
     */
    public GridDeploymentInfoBean(UUID clsLdrId, GridDeploymentMode depMode, String userVer,
        Map<UUID, GridTuple2<UUID, Long>> participants) {
        this.clsLdrId = clsLdrId;
        this.depMode = depMode;
        this.userVer = userVer;
        this.participants = participants;
    }

    /**
     * @param dep Grid deployment.
     */
    public GridDeploymentInfoBean(GridDeploymentInfo dep) {
        clsLdrId = dep.classLoaderId();
        depMode = dep.deployMode();
        userVer = dep.userVersion();
        participants = dep.participants();
    }

    /** {@inheritDoc} */
    @Override public UUID classLoaderId() {
        return clsLdrId;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentMode deployMode() {
        return depMode;
    }

    /** {@inheritDoc} */
    @Override public String userVersion() {
        return userVer;
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, GridTuple2<UUID, Long>> participants() {
        return participants;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return clsLdrId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return o == this || o instanceof GridDeploymentInfoBean && clsLdrId.equals(((GridDeploymentInfoBean)o).clsLdrId);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, clsLdrId);

        U.writeString(out, userVer);

        out.writeByte(depMode != null ? depMode.ordinal() : -1);

        U.writeMap(out, participants);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        clsLdrId = U.readUuid(in);

        userVer = U.readString(in);

        byte ordinal = in.readByte();

        depMode = ordinal >= 0 ? GridDeploymentMode.fromOrdinal(ordinal) : null;

        participants = U.readMap(in);
    }


    /** {@inheritDoc} */
    @Override public String toString() { return S.toString(GridDeploymentInfoBean.class, this); }
}
