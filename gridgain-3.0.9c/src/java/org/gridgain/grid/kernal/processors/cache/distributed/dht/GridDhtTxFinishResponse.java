// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * DHT transaction finish response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtTxFinishResponse<K, V> extends GridDistributedTxFinishResponse<K, V> {
    /** Mini future ID. */
    private GridUuid miniId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDhtTxFinishResponse() {
        // No-op.
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     */
    public GridDhtTxFinishResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId) {
        super(xid, futId);

        assert miniId != null;

        this.miniId = miniId;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert miniId != null;

        U.writeGridUuid(out, miniId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxFinishResponse.class, this, super.toString());
    }
}
