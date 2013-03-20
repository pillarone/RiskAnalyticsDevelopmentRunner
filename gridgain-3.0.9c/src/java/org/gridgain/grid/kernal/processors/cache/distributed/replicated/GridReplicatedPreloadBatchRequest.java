// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Preload batch message that carries some number of entries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedPreloadBatchRequest<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Partition. */
    private int part;

    /** Mode. */
    private int mod;

    /** Batch index. */
    private int idx;

    /** */
    private boolean last;

    /** Cache preload entries in serialized form. */
    @GridToStringExclude
    private Collection<byte[]> entryBytes = new LinkedList<byte[]>();

    /** Cache entries. */
    @GridToStringExclude
    private List<GridCacheEntryInfo<K, V>> entries =
        new LinkedList<GridCacheEntryInfo<K, V>>();

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedPreloadBatchRequest() {
        /* No-op. */
    }

    /**
     * @param part Partition.
     * @param mod Mod.
     * @param idx Batch index.
     */
    public GridReplicatedPreloadBatchRequest(int part, int mod, int idx) {
        this.part = part;
        this.mod = mod;
        this.idx = idx;
    }

    /**
     * @param b Batch to copy from.
     */
    public GridReplicatedPreloadBatchRequest(GridReplicatedPreloadBatchRequest<K, V> b) {
        assert b != null;

        part = b.part;
        mod = b.mod;
        idx = b.idx;
        entryBytes = b.entryBytes;
        entries = b.entries;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        // NOTE: we don't need to prepare entryBytes here since we do it
        // iteratively using method addSerializedEntry().
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        entries = unmarshalCollection(entryBytes, ctx, ldr);

        unmarshalInfos(entries, ctx, ldr);
    }

    /**
     * @return Partition.
     */
    public int partition() {
        return part;
    }

    /**
     * @return Mod.
     */
    public int mod() {
        return mod;
    }

    /**
     * @return Batch index.
     */
    public int index() {
        return idx;
    }

    /**
     * @return {@code true} if this is the last batch..
     */
    public boolean last() {
        return last;
    }

    /**
     * @param last {@code true} if this is the last batch.
     */
    public void last(boolean last) {
        this.last = last;
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
     * Adds {@link GridCacheEntryInfo} object in serialized form.
     *
     * @param entry Entry to add.
     */
    public void addSerializedEntry(byte[] entry) {
        assert entry != null;

        entryBytes.add(entry);
    }

    /**
     * @return Entries.
     */
    public Collection<GridCacheEntryInfo<K, V>> entries() {
        return entries;
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

        out.writeInt(part);
        out.writeInt(mod);
        out.writeInt(idx);
        out.writeBoolean(last);

        U.writeCollection(out, entryBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        part = in.readInt();
        mod = in.readInt();
        idx = in.readInt();
        last = in.readBoolean();

        entryBytes = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloadBatchRequest.class, this, "size", size());
    }
}