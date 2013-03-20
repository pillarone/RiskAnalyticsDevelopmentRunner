// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridIoSyncMessageRequest implements Externalizable {
    /** */
    private long reqId;

    /** */
    private Object req;

    /**
     * Required for {@link Externalizable}.
     */
    public GridIoSyncMessageRequest() {
        // No-op.
    }

    /**
     * @param reqId TODO
     * @param req TODO
     */
    GridIoSyncMessageRequest(long reqId, Object req) {
        this.reqId = reqId;
        this.req = req;
    }

    /**
     * @return TODO
     */
    public long getRequestId() {
        return reqId;
    }

    /**
     * @return TODO
     */
    public Object getRequest() {
        return req;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(reqId);
        out.writeObject(req);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        reqId = in.readLong();
        req = in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridIoSyncMessageRequest.class, this);
    }
}
