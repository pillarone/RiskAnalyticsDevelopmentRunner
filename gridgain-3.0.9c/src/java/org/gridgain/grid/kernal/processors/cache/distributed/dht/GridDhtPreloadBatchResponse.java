// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Partitioned cache preload batch response. This is response message corresponding
 * to {@link GridDhtPreloadBatchRequest}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtPreloadBatchResponse<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Session id. */
    private UUID sesId;

    /** Partition id. */
    private int part;

    /** If partition became obsolete for receiving node. */
    private boolean obsolete;

    /** Batch index. */
    private long batchIdx;

    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtPreloadBatchResponse() {
        // No-op.
    }

    /**
     * @param sesId Session id.
     * @param part Partition id.
     * @param batchIdx Batch index.
     * @param obsolete {@code true} if partition became obsolete for receiving node.
     */
    public GridDhtPreloadBatchResponse(UUID sesId, int part, long batchIdx, boolean obsolete) {
        assert sesId != null;
        assert part >= 0;

        this.sesId = sesId;
        this.part = part;
        this.batchIdx = batchIdx;
        this.obsolete = obsolete;
    }

    /**
     * @return Session id.
     */
    public UUID sessionId() {
        return sesId;
    }

    /**
     * @return Partition id.
     */
    public int partition() {
        return part;
    }

    /**
     * @return Batch index.
     */
    public long batchIndex() {
        return batchIdx;
    }

    /**
     * @return {@code true} if partition became obsolete for receiving node.
     */
    public boolean obsolete() {
        return obsolete;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeUuid(out, sesId);
        out.writeLong(batchIdx);
        out.writeInt(part);
        out.writeBoolean(obsolete);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        sesId = U.readUuid(in);
        batchIdx = in.readLong();
        part = in.readInt();
        obsolete = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPreloadBatchResponse.class, this);
    }
}
