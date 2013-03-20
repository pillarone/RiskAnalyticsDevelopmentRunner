// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;

/**
 * Transaction finish response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedTxFinishResponse<K, V> extends GridCacheMessage<K, V> {
    /** */
    private GridCacheVersion txId;

    /** Future ID. */
    private GridUuid futId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDistributedTxFinishResponse() {
        /* No-op. */
    }

    /**
     * @param txId Transaction id.
     * @param futId Future ID.
     */
    public GridDistributedTxFinishResponse(GridCacheVersion txId, GridUuid futId) {
        assert txId != null;
        assert futId != null;
        
        this.txId = txId;
        this.futId = futId;
    }

    /**
     *
     * @return Transaction id.
     */
    public GridCacheVersion xid() {
        return txId;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        CU.writeVersion(out, txId);
        U.writeGridUuid(out, futId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        
        txId = CU.readVersion(in);
        futId = U.readGridUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDistributedTxFinishResponse.class, this);
    }
}