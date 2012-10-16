// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Partitioned cache preload batch.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtPreloadBatchRequest<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Number of bytes taking by fields of request excluding list of entries. */
    public static final int OVERHEAD_BYTES = 10;

    /** Session id. */
    private UUID sesId;

    /** Batch index. */
    private int idx;

    /** Partition. */
    private int part;

    /** Flag indicating the last batch for the partition. */
    private boolean partLast;

    /** Flag indicating that sending session is finished. */
    private boolean sesLast;

    /** All session parts being inserted only into last message. */
    private int[] sesParts;

    /** Cache entries in serialized form. */
    @GridToStringExclude
    private List<byte[]> entryBytes = new LinkedList<byte[]>();

    /** Cache entries. */
    @GridToStringExclude
    private List<GridCacheEntryInfo<K, V>> entries = new LinkedList<GridCacheEntryInfo<K, V>>();

    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtPreloadBatchRequest() {
        // No-op.
    }

    /**
     * @param sesId Session id.
     * @param part Partition number.
     * @param idx Index.
     */
    public GridDhtPreloadBatchRequest(UUID sesId, int part, int idx) {
        assert sesId != null;
        assert part >= 0;
        assert idx >= 0;

        this.sesId = sesId;
        this.part = part;
        this.idx = idx;
    }

    /**
     * @param batch Batch.
     */
    public GridDhtPreloadBatchRequest(GridDhtPreloadBatchRequest<K, V> batch) {
        assert batch != null;

        sesId = batch.sessionId();
        idx = batch.idx;
        part = batch.part;
        partLast = batch.partLast;
        sesLast = batch.sesLast;
        sesParts = batch.sesParts;
        entryBytes = batch.entryBytes;
        entries = batch.entries;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        // NOTE: we don't need to prepare entryBytes here since we do it
        // explicitly using method addSerializedEntry().
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        entries = unmarshalCollection(entryBytes, ctx, ldr);

        unmarshalInfos(entries, ctx, ldr);
    }

    /**
     * @return Session id.
     */
    public UUID sessionId() {
        return sesId;
    }

    /**
     * @return Batch index.
     */
    public int index() {
        return idx;
    }

    /**
     * @return Partition.
     */
    public int partition() {
        return part;
    }

    /**
     * @return {@code true} if this is the last batch for the partition.
     */
    public boolean partitionLast() {
        return partLast;
    }

    /**
     * @param partLast {@code true} if this is the last batch for the partition.
     */
    public void partitionLast(boolean partLast) {
        this.partLast = partLast;
    }

    /**
     * @return {@code true} if preload session is finished.
     */
    public boolean sessionLast() {
        return sesLast;
    }

    /**
     * @param sesLast {@code true} if sending session is finished.
     */
    public void sesLast(boolean sesLast) {
        this.sesLast = sesLast;
    }

    /**
     * @return Partitions of the sending session.
     */
    public int[] sessionPartitions() {
        return sesParts;
    }

    /**
     * @param sesParts Partitions of the sending session.
     */
    public void sessionPartitions(int[] sesParts) {
        this.sesParts = sesParts;
    }

    /**
     * @return Portion of preloaded entries.
     */
    public List<GridCacheEntryInfo<K, V>> entries() {
        return entries;
    }

    /**
     * @param info Preload entry info.
     * @param ctx Cache context.
     * @return Serialized preload entry.
     * @throws GridException If failed to serialize preload entry info.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public byte[] marshal(GridCacheEntryInfo<K, V> info, GridCacheContext<K, V> ctx) throws GridException {
        assert info != null;
        assert ctx != null;

        marshalInfo(info, ctx);

        return CU.marshal(ctx, info).getEntireArray();
    }

    /**
     * Add {@link GridCacheEntryInfo} object in serialized form.
     *
     * @param entry Entry to add.
     */
    public void addSerializedEntry(byte[] entry) {
        assert entry != null;

        entryBytes.add(entry);
    }

    /**
     * @return Number of entries in batch.
     */
    public int size() {
        return entryBytes.size();
    }

    /**
     * @return {@code true} if batch is empty.
     */
    public boolean isEmpty() {
        return entryBytes.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeUuid(out, sesId);
        out.writeInt(idx);
        out.writeInt(part);
        out.writeBoolean(partLast);
        out.writeBoolean(sesLast);
        out.writeObject(sesParts);

        U.writeCollection(out, entryBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        sesId = U.readUuid(in);
        idx = in.readInt();
        part = in.readInt();
        partLast = in.readBoolean();
        sesLast = in.readBoolean();
        sesParts = (int[])in.readObject();

        entryBytes = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPreloadBatchRequest.class, this, "size", size(), "super", super.toString());
    }
}
