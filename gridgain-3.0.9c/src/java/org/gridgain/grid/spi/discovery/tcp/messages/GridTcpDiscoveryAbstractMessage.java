// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Base class to implement discovery messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridTcpDiscoveryAbstractMessage implements Externalizable {
    /** Sender of the message. */
    private UUID senderNodeId;

    /** Message ID. */
    private GridUuid id;

    /** Verified flag. */
    private boolean verified;

    /** Verifier node ID. */
    private UUID verifierNodeId;

    /** Topology version of the SPI must be set to after processing of the message (if topology store is used). */
    private long topVer;

    /**
     * Default no-arg constructor for {@link Externalizable} interface.
     */
    protected GridTcpDiscoveryAbstractMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node ID.
     */
    protected GridTcpDiscoveryAbstractMessage(UUID creatorNodeId) {
        id = GridUuid.fromUuid(creatorNodeId);
    }

    /**
     * Gets creator node.
     *
     * @return Creator node ID.
     */
    public UUID creatorNodeId() {
        return id.globalId();
    }

    /**
     * Gets message ID.
     *
     * @return Message ID.
     */
    public GridUuid id() {
        return id;
    }

    /**
     * Gets sender node ID.
     *
     * @return Sender node ID.
     */
    public UUID senderNodeId() {
        return senderNodeId;
    }

    /**
     * Sets sender node ID.
     *
     * @param senderNodeId Sender node ID.
     */
    public void senderNodeId(UUID senderNodeId) {
        this.senderNodeId = senderNodeId;
    }

    /**
     * Checks whether message is verified.
     *
     * @return {@code true} if message was verified.
     */
    public boolean verified() {
        return verified;
    }

    /**
     * Gets verifier node ID.
     *
     * @return verifier node ID.
     */
    public UUID verifierNodeId() {
        return verifierNodeId;
    }

    /**
     * Verifies the message and stores verifier ID.
     *
     * @param verifierNodeId Verifier node ID.
     */
    public void verify(UUID verifierNodeId) {
        verified = true;

        this.verifierNodeId = verifierNodeId;
    }

    /**
     * Gets topology version.
     * <p>
     * Topology version of the SPI must be set to after processing of the message.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @return Topology version.
     */
    public long topologyVersion() {
        return topVer;
    }

    /**
     * Sets topology version.
     * <p>
     * Topology version of the SPI must be set to after processing of the message.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @param topVer Topology version.
     */
    public void topologyVersion(long topVer) {
        this.topVer = topVer;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, senderNodeId);
        U.writeGridUuid(out, id);
        U.writeUuid(out, verifierNodeId);
        out.writeBoolean(verified);
        out.writeLong(topVer);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        senderNodeId = U.readUuid(in);
        id = U.readGridUuid(in);
        verifierNodeId = U.readUuid(in);
        verified = in.readBoolean();
        topVer = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        else if (obj instanceof GridTcpDiscoveryAbstractMessage)
            return id.equals(((GridTcpDiscoveryAbstractMessage) obj).id);

        return false;
    }

    /** {@inheritDoc} */
    @Override public final int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryAbstractMessage.class, this);
    }
}
