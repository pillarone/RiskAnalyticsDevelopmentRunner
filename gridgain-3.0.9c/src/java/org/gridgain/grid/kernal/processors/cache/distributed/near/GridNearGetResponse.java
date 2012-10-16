// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Get response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearGetResponse<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable,
    GridCacheVersionable {
    /** Future ID. */
    private GridUuid futId;

    /** Sub ID. */
    private GridUuid miniId;

    /** Version. */
    private GridCacheVersion ver;

    /** Result. */
    @GridToStringInclude
    private Collection<GridCacheEntryInfo<K, V>> entries;

    /** Keys to retry due to ownership shift. */
    @GridToStringInclude
    private Collection<K> retries;

    /** Retry bytes. */
    private Collection<byte[]> retryBytes;

    /** Error. */
    private Throwable err;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearGetResponse() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param miniId Sub ID.
     * @param ver Version.
     */
    public GridNearGetResponse(GridUuid futId, GridUuid miniId, GridCacheVersion ver) {
        assert futId != null;
        assert miniId != null;
        assert ver != null;

        this.futId = futId;
        this.miniId = miniId;
        this.ver = ver;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Sub ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return ver;
    }

    /**
     * @return Entries.
     */
    public Collection<GridCacheEntryInfo<K, V>> entries() {
        return entries;
    }

    /**
     * @param entries Entries.
     */
    public void entries(Collection<GridCacheEntryInfo<K, V>> entries) {
        this.entries = entries;
    }

    /**
     * @return Failed filter set.
     */
    public Collection<K> retries() {
        return retries == null ? Collections.<K>emptyList() : retries;
    }

    /**
     * @param retries Keys to retry due to ownership shift.
     */
    public void retries(Collection<K> retries) {
        this.retries = retries;
    }

    /**
     * @return Error.
     */
    public Throwable error() {
        return err;
    }

    /**
     * @param err Error.
     */
    public void error(Throwable err) {
        this.err = err;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        marshalInfos(entries, ctx);

        if (retryBytes == null)
            retryBytes = marshalCollection(retries, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalInfos(entries, ctx, ldr);

        if (retries == null)
            retries = unmarshalCollection(retryBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;
        assert ver != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, entries);
        U.writeCollection(out, retryBytes);

        CU.writeVersion(out, ver);

        out.writeObject(err);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);
        entries = U.readCollection(in);
        retryBytes = U.readList(in);

        ver = CU.readVersion(in);

        err = (Throwable)in.readObject();

        if (retries == null)
            retries = Collections.emptyList();

        assert futId != null;
        assert miniId != null;
        assert ver != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearGetResponse.class, this);
    }
}
