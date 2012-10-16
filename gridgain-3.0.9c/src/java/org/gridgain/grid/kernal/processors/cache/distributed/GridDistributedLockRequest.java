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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Lock request message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedLockRequest<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Sender node ID. */
    private UUID nodeId;

    /** Thread ID. */
    private long threadId;

    /** Future ID. */
    private GridUuid futId;

    /** Max wait timeout. */
    private long timeout;

    /** Indicates whether lock is obtained within a scope of transaction. */
    private boolean isInTx;

    /** Invalidate flag for transactions. */
    private boolean isInvalidate;

    /** Indicates whether implicit lock so for read or write operation. */
    private boolean isRead;

    /** Transaction isolation. */
    private GridCacheTxIsolation isolation;

    /** Key bytes for keys to lock. */
    @GridToStringInclude
    private List<byte[]> keyBytes;

    /** Keys. */
    private List<K> keys;

    /** Array indicating whether value should be returned for a key. */
    @GridToStringInclude
    private boolean[] retVals;

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridDistributedLockRequest() {
        /* No-op. */
    }

    /**
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @param futId Future ID.
     * @param lockVer Cache version.
     * @param isInTx {@code True} if implicit transaction lock.
     * @param isRead Indicates whether implicit lock is for read or write operation.
     * @param isolation Transaction isolation.
     * @param isInvalidate Invalidation flag.
     * @param timeout Lock timeout.
     * @param keyCnt Number of keys.
     */
    public GridDistributedLockRequest(
        UUID nodeId,
        long threadId,
        GridUuid futId,
        GridCacheVersion lockVer,
        boolean isInTx,
        boolean isRead,
        GridCacheTxIsolation isolation,
        boolean isInvalidate,
        long timeout,
        int keyCnt) {
        super(lockVer, keyCnt);

        assert keyCnt > 0;
        assert futId != null;
        assert !isInTx || isolation != null;

        this.nodeId = nodeId;
        this.threadId = threadId;
        this.futId = futId;
        this.isInTx = isInTx;
        this.isRead = isRead;
        this.isolation = isolation;
        this.isInvalidate = isInvalidate;
        this.timeout = timeout;

        keyBytes = new ArrayList<byte[]>(keyCnt);
        retVals = new boolean[keyCnt];
    }

    /**
     *
     * @return Node ID.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     *
     * @return Owner node thread ID.
     */
    public long threadId() {
        return threadId;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    public UUID lockId() {
        return ver.id();
    }

    /**
     * @return {@code True} if implicit transaction lock.
     */
    public boolean inTx() {
        return isInTx;
    }

    /**
     * @return Invalidate flag.
     */
    public boolean isInvalidate() {
        return isInvalidate;
    }

    /**
     * @return {@code True} if lock is implicit and for a read operation.
     */
    public boolean txRead() {
        return isRead;
    }

    /**
     * @param idx Key index.
     * @return Flag indicating whether a value should be returned.
     */
    public boolean returnValue(int idx) {
        return retVals[idx];
    }

    /**
     * @return Return flags.
     */
    public boolean[] returnFlags() {
        return retVals;
    }

    /**
     * @return Transaction isolation or <tt>null</tt> if not in transaction.
     */
    public GridCacheTxIsolation isolation() {
        return isolation;
    }

    /**
     *
     * @return Key to lock.
     */
    public List<byte[]> keyBytes() {
        return keyBytes;
    }

    /**
     * Adds a key.
     *
     * @param key Key.
     * @param retVal Flag indicating whether value should be returned.
     * @param keyBytes Key bytes.
     * @param cands Candidates.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addKeyBytes(K key, byte[] keyBytes, boolean retVal, Collection<GridCacheMvccCandidate<K>> cands,
        GridCacheContext<K, V> ctx) throws GridException {
        prepareObject(key, ctx);

        candidatesByIndex(this.keyBytes.size(), cands);

        retVals[this.keyBytes.size()] = retVal;

        this.keyBytes.add(keyBytes);
    }

    /**
     * @return Unmarshalled keys.
     */
    public List<K> keys() {
        return keys == null ? Collections.<K>emptyList() : keys;
    }

    /**
     * @return Max lock wait time.
     */
    public long timeout() {
        return timeout;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (F.isEmpty(keyBytes) && !F.isEmpty(keys))
            keyBytes = marshalCollection(keys, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (keys == null)
            keys = unmarshalCollection(keyBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(threadId);
        out.writeLong(timeout);

        U.writeUuid(out, nodeId);
        U.writeGridUuid(out, futId);

        out.writeObject(retVals);
        
        U.writeCollection(out, keyBytes);

        // Transaction attributes.
        out.writeBoolean(isInTx);
        out.writeBoolean(isInvalidate);

        if (isInTx) {
            assert isolation != null;

            out.writeBoolean(isRead);
            
            out.writeByte(isolation.ordinal());
        }
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        threadId = in.readLong();
        timeout = in.readLong();

        nodeId = U.readUuid(in);
        futId = U.readGridUuid(in);

        retVals = (boolean[])in.readObject();

        keyBytes = U.readList(in);

        // Transaction attributes.
        isInTx = in.readBoolean();
        isInvalidate = in.readBoolean();

        if (isInTx) {
            isRead = in.readBoolean();

            isolation = GridCacheTxIsolation.fromOrdinal(in.readByte());
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedLockRequest.class, this, "keysCnt", keyBytes.size(),
            "super", super.toString());
    }
}