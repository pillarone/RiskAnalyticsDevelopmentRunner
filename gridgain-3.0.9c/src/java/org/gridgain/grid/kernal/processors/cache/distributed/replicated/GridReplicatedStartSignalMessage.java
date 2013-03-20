// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Start signal message for replicated preloader (either request and response).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedStartSignalMessage<K, V> extends GridCacheMessage<K, V> {
    /** Flag indicating whether this message is request or response. */
    private boolean req;

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedStartSignalMessage() {
        // No-op.
    }

    /**
     * @param req {@code true} for request, {@code false} for response.
     */
    public GridReplicatedStartSignalMessage(boolean req) {
        this.req = req;
    }

    /**
     * @return {@code true} if this message is request, {@code false} if response.
     */
    public boolean request() {
        return req;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(req);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        req = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedStartSignalMessage.class, this);
    }
}
