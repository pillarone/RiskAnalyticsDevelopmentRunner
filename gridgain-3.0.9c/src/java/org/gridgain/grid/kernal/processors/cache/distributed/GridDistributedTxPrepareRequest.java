// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Transaction prepare request for optimistic and eventually consistent
 * transactions.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedTxPrepareRequest<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Thread ID. */
    @GridToStringInclude
    private long threadId;

    /** Transaction concurrency. */
    @GridToStringInclude
    private GridCacheTxConcurrency concurrency;

    /** Transaction isolation. */
    @GridToStringInclude
    private GridCacheTxIsolation isolation;

    /** Commit version for EC transactions. */
    @GridToStringInclude
    private GridCacheVersion commitVer;

    /** Transaction timeout. */
    @GridToStringInclude
    private long timeout;

    /** Invalidation flag. */
    @GridToStringInclude
    private boolean invalidate;

    /** Transaction read set. */
    @GridToStringInclude
    private Collection<GridCacheTxEntry<K, V>> reads;

    /** Transaction write entries. */
    @GridToStringInclude
    private Collection<GridCacheTxEntry<K, V>> writes;

    /**
     * Required by {@link Externalizable}.
     */
    public GridDistributedTxPrepareRequest() {
        /* No-op. */
    }

    /**
     * @param tx Cache transaction.
     * @param reads Read entries.
     * @param writes Write entries.
     */
    public GridDistributedTxPrepareRequest(GridCacheTxEx<K,V> tx, Collection<GridCacheTxEntry<K, V>> reads,
        Collection<GridCacheTxEntry<K, V>> writes) {
        super(tx.xidVersion(), 0);

        commitVer = tx.ec() ? tx.commitVersion() : null;
        threadId = tx.threadId();
        concurrency = tx.concurrency();
        isolation = tx.isolation();
        timeout = tx.timeout();
        invalidate = tx.isInvalidate();

        this.reads = reads;
        this.writes = writes;
    }

    /**
     * @return Thread ID.
     */
    public long threadId() {
        return threadId;
    }

    /**
     * @return Commit version.
     */
    public GridCacheVersion commitVersion() { return commitVer; }

    /**
     * @return Invalidate flag.
     */
    public boolean isInvalidate() { return invalidate; }

    /**
     * @return Transaction timeout.
     */
    public long timeout() {
        return timeout;
    }

    /**
     * @return Concurrency.
     */
    public GridCacheTxConcurrency concurrency() {
        return concurrency;
    }

    /**
     * @return Isolation level.
     */
    public GridCacheTxIsolation isolation() {
        return isolation;
    }

    /**
     * @return Read set.
     */
    public Collection<GridCacheTxEntry<K, V>> reads() {
        return reads;
    }

    /**
     * @return Write entries.
     */
    public Collection<GridCacheTxEntry<K, V>> writes() {
        return writes;
    }

    /**
     * @param reads Reads.
     */
    protected void reads(Collection<GridCacheTxEntry<K, V>> reads) {
        this.reads = reads;
    }

    /**
     * @param writes Writes.
     */
    protected void writes(Collection<GridCacheTxEntry<K, V>> writes) {
        this.writes = writes;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        marshalTx(writes, ctx);
        marshalTx(reads, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalTx(writes, ctx, ldr);
        unmarshalTx(reads, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        CU.writeVersion(out, commitVer);

        out.writeBoolean(invalidate);

        out.writeLong(threadId);
        out.writeLong(timeout);

        out.writeByte(isolation.ordinal());
        out.writeByte(concurrency.ordinal());

        writeCollection(out, reads);
        writeCollection(out, writes);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        commitVer = CU.readVersion(in);

        invalidate = in.readBoolean();

        threadId = in.readLong();
        timeout = in.readLong();

        isolation = GridCacheTxIsolation.fromOrdinal(in.readByte());
        concurrency = GridCacheTxConcurrency.fromOrdinal(in.readByte());

        reads = readCollection(in);
        writes = readCollection(in);
    }

    /**
     *
     * @param out Output.
     * @param col Set to write.
     * @throws IOException If write failed.
     */
    private void writeCollection(ObjectOutput out, Collection<GridCacheTxEntry<K, V>> col) throws IOException {
        boolean empty = F.isEmpty(col);
        
        // Write null flag.
        out.writeBoolean(empty);

        if (!empty) {
            out.writeInt(col.size());

            for (GridCacheTxEntry<K, V> e : col) {
                V val = e.value();

                try {
                    // Don't serialize value if invalidate is set to true.
                    if (invalidate) {
                        e.value(null);
                    }

                    out.writeObject(e);
                }
                finally {
                    // Set original value back.
                    e.value(val);
                }
            }
        }
    }

    /**
     * @param in Input.
     * @return Deserialized set.
     * @throws IOException If deserialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable
    private Collection<GridCacheTxEntry<K, V>> readCollection(ObjectInput in) throws IOException,
        ClassNotFoundException {
        List<GridCacheTxEntry<K, V>> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<GridCacheTxEntry<K, V>>(size);

            for (int i = 0; i < size; i++) {
                col.add((GridCacheTxEntry<K, V>)in.readObject());
            }
        }

        return col == null ? Collections.<GridCacheTxEntry<K,V>>emptyList() : col;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDistributedTxPrepareRequest.class, this,
            "super", super.toString());
    }
}