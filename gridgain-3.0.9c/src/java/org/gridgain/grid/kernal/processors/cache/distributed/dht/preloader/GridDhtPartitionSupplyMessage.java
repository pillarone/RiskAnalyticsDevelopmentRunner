// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Partition supply message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDhtPartitionSupplyMessage<K, V> extends GridCacheMessage<K, V> {
    /** Worker ID. */
    private int workerId = -1;

    /** Update sequence. */
    private long updateSeq;

    /** Partitions that have been fully sent. */
    @GridToStringInclude
    private Set<Integer> last;

    /** Partitions which were not found. */
    @GridToStringInclude
    private Set<Integer> missed;

    /** Entries. */
    private Map<Integer, Collection<GridCacheEntryInfo<K, V>>> infos =
        new HashMap<Integer, Collection<GridCacheEntryInfo<K,V>>>();

    /** Cache entries in serialized form. */
    @GridToStringExclude
    private Map<Integer, Collection<byte[]>> infoBytes = new HashMap<Integer, Collection<byte[]>>();

    /** Message size. */
    private transient int msgSize;

    /**
     * @param workerId Worker ID.
     * @param updateSeq Update sequence for this node.
     */
    GridDhtPartitionSupplyMessage(int workerId, long updateSeq) {
        assert workerId >= 0;
        assert updateSeq > 0;

        this.updateSeq = updateSeq;
        this.workerId = workerId;
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtPartitionSupplyMessage() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean isPreloaderMessage() {
        return true;
    }

    /**
     * @return Worker ID.
     */
    int workerId() {
        return workerId;
    }

    /**
     * @return Update sequence.
     */
    long updateSequence() {
        return updateSeq;
    }

    /**
     * @return Flag to indicate last message for partition.
     */
    Set<Integer> last() {
        return last == null ? Collections.<Integer>emptySet() : last;
    }

    /**
     * @param p Partition which was fully sent.
     */
    void last(int p) {
        if (last == null)
            last = new HashSet<Integer>();

        if (last.add(p)) {
            msgSize += 4;

            // If partition is empty, we need to add it.
            Collection<byte[]> serInfo = infoBytes.get(p);

            if (serInfo == null)
                infoBytes.put(p, new LinkedList<byte[]>());
        }
    }

    /**
     * @param p Missed partition.
     */
    void missed(int p) {
        if (missed == null)
            missed = new HashSet<Integer>();

        if (missed.add(p))
            msgSize += 4;
    }

    /**
     * @return Missed partitions.
     */
    Set<Integer> missed() {
        return missed == null ? Collections.<Integer>emptySet() : missed;
    }

    /**
     * @return Entries.
     */
    Map<Integer, Collection<GridCacheEntryInfo<K, V>>> infos() {
        return infos;
    }

    /**
     * @return Message size.
     */
    int messageSize() {
        return msgSize;
    }

    /**
     * @param p Partition.
     * @param info Entry to add.
     * @param ctx Cache context.
     * @throws GridException If failed.
     */
    void addEntry(int p, GridCacheEntryInfo<K, V> info, GridCacheContext<K, V> ctx) throws GridException {
        assert info != null;

        marshalInfo(info, ctx);

        byte[] bytes = CU.marshal(ctx, info).getEntireArray();

        msgSize += bytes.length;

        Collection<byte[]> serInfo = infoBytes.get(p);

        if (serInfo == null) {
            msgSize += 4;

            infoBytes.put(p, serInfo = new LinkedList<byte[]>());
        }

        serInfo.add(bytes);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        // Do nothing here as marshalling was done when info was added.
        super.p2pMarshal(ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        for (Map.Entry<Integer, Collection<byte[]>> e : infoBytes.entrySet()) {
            Collection<GridCacheEntryInfo<K, V>> entries = unmarshalCollection(e.getValue(), ctx, ldr);

            unmarshalInfos(entries, ctx, ldr);

            infos.put(e.getKey(), entries);
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(workerId);
        out.writeLong(updateSeq);

        U.writeIntCollection(out, last);
        U.writeIntCollection(out, missed);

        U.writeIntKeyMap(out, infoBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        workerId = in.readInt();
        updateSeq = in.readLong();

        last = U.readIntSet(in);
        missed = U.readIntSet(in);

        infoBytes = U.readIntKeyMap(in);

        assert workerId >= 0;
        assert updateSeq > 0;
    }

    /**
     * @return Number of entries in message.
     */
    private int size() {
        return infos.isEmpty() ? infoBytes.size() : infos.size();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionSupplyMessage.class, this,
            "size", size(),
            "parts", infos.keySet(),
            "super", super.toString());
    }
}
