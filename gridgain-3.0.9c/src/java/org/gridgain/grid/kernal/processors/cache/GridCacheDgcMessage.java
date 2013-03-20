// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * GC message (either request and response).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCacheDgcMessage<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** {@code true} - request, {@code false} - response. */
    private boolean req;

    /** */
    @GridToStringInclude
    private Map<K, Collection<GridCacheVersion>> map = new HashMap<K, Collection<GridCacheVersion>>();

    /** */
    @GridToStringExclude
    private byte[] mapBytes;

    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheDgcMessage() {
        /* No-op. */
    }

    /**
     * @param req {@code true} for request, {@code false} for response.
     */
    GridCacheDgcMessage(boolean req) {
        this.req = req;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (map != null) {
            for (K key : map.keySet())
                prepareObject(key, ctx);

            mapBytes = CU.marshal(ctx, map).getEntireArray();
        }
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (mapBytes != null)
            map = U.unmarshal(ctx.marshaller(), new GridByteArrayList(mapBytes), ldr);
    }

    /**
     * @return Request or response.
     */
    boolean request() {
        return req;
    }

    /**
     * Add information about key and version to request.
     *
     * @param key Key.
     * @param ver Version.
     */
    void addCandidate(K key, GridCacheVersion ver) {
        Collection<GridCacheVersion> col = F.addIfAbsent(map, key, new CO<Collection<GridCacheVersion>>() {
            @Override public Collection<GridCacheVersion> apply() {
                return new ArrayList<GridCacheVersion>();
            }
        });

        assert col != null;

        col.add(ver);
    }

    /**
     * @return Candidates map.
     */
    Map<K, Collection<GridCacheVersion>> candidatesMap() {
        return Collections.unmodifiableMap(map);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        req = in.readBoolean();
        mapBytes = U.readByteArray(in);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(req);
        U.writeByteArray(out, mapBytes);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheDgcMessage.class, this);
    }
}

