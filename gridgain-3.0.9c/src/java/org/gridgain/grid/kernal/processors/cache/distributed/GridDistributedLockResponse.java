// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Lock response message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedLockResponse<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Future ID. */
    private GridUuid futId;

    /** Error. */
    private Throwable err;

    /** Value bytes. */
    private List<byte[]> valBytes;

    /** Values. */
    @GridToStringInclude
    private List<V> vals;

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridDistributedLockResponse() {
        /* No-op. */
    }

    /**
     * @param lockVer Lock version.
     * @param futId Future ID.
     * @param cnt Key count.
     */
    public GridDistributedLockResponse(GridCacheVersion lockVer, GridUuid futId, int cnt) {
        super(lockVer, cnt);

        assert futId != null;

        this.futId = futId;

        vals = new ArrayList<V>(cnt);
        valBytes = new ArrayList<byte[]>(cnt);
    }

    /**
     * @param lockVer Lock ID.
     * @param futId Future ID.
     * @param err Error.
     */
    public GridDistributedLockResponse(GridCacheVersion lockVer, GridUuid futId, Throwable err) {
        super(lockVer, 0);

        assert futId != null;

        this.futId = futId;
        this.err = err;
    }

    /**
     * @param lockVer Lock ID.
     * @param futId Future ID.
     * @param cnt Count.
     * @param err Error.
     */
    public GridDistributedLockResponse(GridCacheVersion lockVer, GridUuid futId, int cnt, Throwable err) {
        super(lockVer, cnt);

        assert futId != null;

        this.futId = futId;
        this.err = err;

        vals = new ArrayList<V>(cnt);
        valBytes = new ArrayList<byte[]>(cnt);
    }

    /**
     *
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     *
     * @return Lock ID.
     */
    public UUID lockId() {
        return ver.id();
    }

    /**
     * @return Error.
     */
    public Throwable error() {
        return err;
    }

    /**
     * @param idx Index of locked flag.
     * @return Value of locked flag at given index.
     */
    public boolean isCurrentlyLocked(int idx) {
        assert idx >= 0;

        Collection<GridCacheMvccCandidate<K>> cands = candidatesByIndex(idx);

        for (GridCacheMvccCandidate<K> cand : cands)
            if (cand.owner())
                return true;

        return false;
    }

    /**
     * @param idx Candidates index.
     * @param cands Collection of candidates.
     * @param committedVers Committed versions relative to lock version.
     * @param rolledbackVers Rolled back versions relative to lock version.
     */
    public void setCandidates(int idx, Collection<GridCacheMvccCandidate<K>> cands,
        Collection<GridCacheVersion> committedVers, Collection<GridCacheVersion> rolledbackVers) {
        assert idx >= 0;

        completedVersions(committedVers, rolledbackVers);

        candidatesByIndex(idx, cands);
    }

    /**
     * @param idx Value index.
     *
     * @return Value bytes (possibly {@code null}).
     */
    @Nullable public byte[] valueBytes(int idx) {
        return F.isEmpty(valBytes) ? null : valBytes.get(idx);
    }

    /**
     * @param val Value.
     * @param valBytes Value bytes (possibly {@code null}).
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addValueBytes(V val, byte[] valBytes, GridCacheContext<K, V> ctx) throws GridException {
        prepareObject(val, ctx);

        vals.add(val);

        this.valBytes.add(valBytes);
    }

    /**
     * @return Values.
     */
    public List<V> values() {
        return vals;
    }

    /**
     * @param idx Index.
     * @return Value for given index.
     */
    @Nullable public V value(int idx) {
        return F.isEmpty(vals) ? null : vals.get(idx);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (F.isEmpty(valBytes) && !F.isEmpty(vals))
            valBytes = marshalCollection(vals, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (F.isEmpty(vals) && !F.isEmpty(valBytes))
            vals = unmarshalCollection(valBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeGridUuid(out, futId);
        U.writeCollection(out, valBytes);

        out.writeObject(err);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        valBytes = U.readList(in);

        err = (Throwable)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedLockResponse.class, this, super.toString());
    }
}