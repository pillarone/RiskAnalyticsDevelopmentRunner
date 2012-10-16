// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNodeLocalImpl<K, V> extends ConcurrentHashMap<K, V> implements GridNodeLocal<K, V>, Externalizable {
    /** */
    private static final ThreadLocal<String> stash = new ThreadLocal<String>();

    /** */
    private GridMetadataAwareAdapter impl = new GridMetadataAwareAdapter();

    /** */
    private GridKernalContext ctx;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridNodeLocalImpl() {
        // No-op.
    }

    /**
     *
     * @param ctx Kernal context.
     */
    GridNodeLocalImpl(GridKernalContext ctx) {
        assert ctx != null;

        this.ctx = ctx;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        stash.set(U.readString(in));
    }

    /** {@inheritDoc} */
    @Nullable @Override public V addIfAbsent(K key, @Nullable Callable<V> dflt) {
        return F.addIfAbsent(this, key, dflt);
    }

    /** {@inheritDoc} */
    @Override public V addIfAbsent(K key, V val) {
        return F.addIfAbsent(this, key, val);
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            return G.grid(stash.get()).nodeLocal();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public <V> V addMeta(String name, V val) {
        return impl.addMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, V val) {
        return impl.putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        return impl.putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        return impl.addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, Callable<V> c) {
        return impl.addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable
    @Override public <V> V meta(String name) {
        return (V)impl.meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable
    @Override public <V> V removeMeta(String name) {
        return (V)impl.removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean removeMeta(String name, V val) {
        return impl.removeMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> Map<String, V> allMeta() {
        return impl.allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return impl.hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean hasMeta(String name, V val) {
        return impl.hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        return impl.replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        impl.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        impl.copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNodeLocalImpl.class, this);
    }
}

