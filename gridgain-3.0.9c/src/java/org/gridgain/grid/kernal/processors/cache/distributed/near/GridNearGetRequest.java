// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Get request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearGetRequest<K, V> extends GridCacheMessage<K, V>
    implements GridCacheDeployable, GridCacheVersionable {
    /** Future ID. */
    private GridUuid futId;

    /** Sub ID. */
    private GridUuid miniId;

    /** Version. */
    private GridCacheVersion ver;

    /** */
    @GridToStringInclude
    private Collection<K> keys;

    /** Reload flag. */
    private boolean reload;

    /** */
    @GridToStringExclude
    private Collection<byte[]> keyBytes;

    /** Filter bytes. */
    private byte[][] filterBytes;

    /** Filters. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearGetRequest() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param miniId Sub ID.
     * @param ver Version.
     * @param keys Keys.
     * @param reload Reload flag.
     * @param filter Filter.
     */
    public GridNearGetRequest(GridUuid futId, GridUuid miniId, GridCacheVersion ver, Collection<K> keys,
        boolean reload, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        assert futId != null;
        assert miniId != null;
        assert ver != null;
        assert keys != null;

        this.futId = futId;
        this.miniId = miniId;
        this.ver = ver;
        this.keys = keys;
        this.reload = reload;
        this.filter = filter;
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
     * @return Keys
     */
    public Collection<K> keys() {
        return keys;
    }

    /**
     * @return Reload flag.
     */
    public boolean reload() {
        return reload;
    }

    /**
     * @return Filters.
     */
    public GridPredicate<? super GridCacheEntry<K, V>>[] filter() {
        return filter;
    }

    /**
     * @param ctx Cache context.
     * @throws GridException If failed.
     */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        assert ctx != null;
        assert !F.isEmpty(keys);

        if (keyBytes == null)
            keyBytes = marshalCollection(keys, ctx);

        if (filterBytes == null)
            filterBytes = marshalFilter(filter, ctx);
    }

    /**
     * @param ctx Context.
     * @param ldr Loader.
     * @throws GridException If failed.
     */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (keys == null)
            keys = unmarshalCollection(keyBytes, ctx, ldr);

        if (filter == null)
            filter = unmarshalFilter(filterBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;
        assert ver != null;

        out.writeBoolean(reload);

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);

        out.writeObject(filterBytes);

        U.writeCollection(out, keyBytes);

        CU.writeVersion(out, ver);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        reload = in.readBoolean();

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);

        filterBytes = (byte[][])in.readObject();

        keyBytes = U.readCollection(in);

        ver = CU.readVersion(in);

        assert futId != null;
        assert miniId != null;
        assert ver != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearGetRequest.class, this);
    }
}
