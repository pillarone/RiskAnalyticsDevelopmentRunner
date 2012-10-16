// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import java.io.*;
import java.util.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTaskSessionRequest implements GridTaskMessage, Externalizable {
    /** Changed attributes. */
    private GridByteArrayList attrs;

    /** Task session ID. */
    private UUID sesId;

    /** ID of job within a task. */
    private UUID jobId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridTaskSessionRequest() {
        // No-op.
    }

    /**
     * @param sesId Session ID.
     * @param jobId Job ID within the session.
     * @param attrs Changed attribute.
     */
    public GridTaskSessionRequest(UUID sesId, UUID jobId, GridByteArrayList attrs) {
        assert sesId != null;
        assert attrs != null;

        this.sesId = sesId;
        this.attrs = attrs;
        this.jobId = jobId;
    }

    /**
     * @return Changed attributes.
     */
    public GridByteArrayList getAttributes() {
        return attrs;
    }

    /**
     * @return Session ID.
     */
    @Override public UUID getSessionId() {
        return sesId;
    }

    /**
     * @return Job ID.
     */
    public UUID getJobId() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(attrs);

        U.writeUuid(out, sesId);
        U.writeUuid(out, jobId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        attrs = (GridByteArrayList)in.readObject();

        sesId = U.readUuid(in);
        jobId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskSessionRequest.class, this);
    }
}
