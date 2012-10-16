// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.checkpoint;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * This class defines checkpoint request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCheckpointRequest implements Externalizable {
    /** */
    private UUID sesId;

    /** */
    private String key;

    /** */
    private String cpSpi;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCheckpointRequest() {
        // No-op.
    }

    /**
     * @param sesId Task session ID.
     * @param key Checkpoint key.
     * @param cpSpi Checkpoint SPI.
     */
    public GridCheckpointRequest(UUID sesId, String key, String cpSpi) {
        assert sesId != null;
        assert key != null;

        this.sesId = sesId;
        this.key = key;

        this.cpSpi = cpSpi == null || cpSpi.length() == 0 ? null : cpSpi;
    }

    /**
     * @return Session ID.
     */
    public UUID getSessionId() {
        return sesId;
    }

    /**
     * @return Checkpoint key.
     */
    public String getKey() {
        return key;
    }

    /**
     * @return Checkpoint SPI.
     */
    public String getCheckpointSpi() {
        return cpSpi;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, sesId);
        U.writeString(out, key);
        U.writeString(out, cpSpi);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        sesId = U.readUuid(in);
        key = U.readString(in);
        cpSpi = U.readString(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCheckpointRequest.class, this);
    }
}
