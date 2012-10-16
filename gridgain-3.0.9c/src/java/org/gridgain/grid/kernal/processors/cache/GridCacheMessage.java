// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Parent of all cache messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheMessage<K, V> implements Externalizable {
    /** ID of this message. */
    private long msgId = -1;

    /** */
    @GridToStringInclude
    private GridDeploymentInfo depInfo;

    /**
     * @return {@code True} if this message is preloader message.
     */
    public boolean isPreloaderMessage() {
        return false;
    }

    /**
     * @return Message ID.
     */
    public long messageId() {
        return msgId;
    }

    /**
     * Sets message ID. This method is package protected and is only called
     * by {@link GridCacheIoManager}.
     *
     * @param msgId New message ID.
     */
    void messageId(long msgId) {
        this.msgId = msgId;
    }

    /**
     * @param filters Predicate filters.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    protected final void prepareFilter(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filters,
        GridCacheContext<K, V> ctx) throws GridException {
        if (filters != null)
            for (GridPredicate filter : filters)
                prepareObject(filter, ctx);
    }

    /**
     * @param o Object to prepare for marshalling.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    protected final void prepareObject(@Nullable Object o, GridCacheContext<K, V> ctx) throws GridException {
        if (o != null) {
            ctx.deploy().registerClass(o);

            ClassLoader ldr = o.getClass().getClassLoader();

            if (ldr instanceof GridDeploymentInfo)
                prepare((GridDeploymentInfo)ldr);
        }
    }

    /**
     * @param depInfo Deployment to set.
     * @see GridCacheDeployable#prepare(GridDeploymentInfo)
     */
    public final void prepare(GridDeploymentInfo depInfo) {
        if (depInfo != this.depInfo) {
            if (this.depInfo != null && depInfo instanceof GridDeployment)
                // Make sure not to replace remote deployment with local.
                if (((GridDeployment)depInfo).isLocal())
                    return;

            this.depInfo = depInfo instanceof GridDeploymentInfoBean ? depInfo : new GridDeploymentInfoBean(depInfo);
        }
    }

    /**
     * @return Preset deployment info.
     * @see GridCacheDeployable#deployInfo()
     */
    public GridDeploymentInfo deployInfo() {
        return depInfo;
    }

    /**
     * This method is called before the whole message is serialized and is responsible
     * for pre-marshalling state that will have to be peer-loaded.
     *
     * @param ctx Cache context.
     * @throws GridException If failed.
     */
    public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        // No-op.
    }

    /**
     * This method is called after the message is deserialized and is responsible for
     * unmarshalling state marshalled in {@link #p2pMarshal(GridCacheContext)} method.
     *
     * @param ctx Context.
     * @param ldr Class loader.
     * @throws GridException If failed.
     */
    public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        // No-op.
    }

    /**
     * @param info Entry to marshal.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    protected final void marshalInfo(GridCacheEntryInfo<K, V> info, GridCacheContext<K, V> ctx) throws GridException {
        assert ctx != null;

        if (info != null) {
            info.marshal(ctx);

            prepareObject(info.key(), ctx);
            prepareObject(info.value(), ctx);
        }
    }

    /**
     * @param info Entry to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @throws GridException If failed.
     */
    protected final void unmarshalInfo(GridCacheEntryInfo<K, V> info, GridCacheContext<K, V> ctx,
        ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (info != null)
            info.unmarshal(ctx, ldr);
    }

    /**
     * @param infos Entries to marshal.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    protected final void marshalInfos(Iterable<? extends GridCacheEntryInfo<K, V>> infos, GridCacheContext<K, V> ctx)
        throws GridException {
        assert ctx != null;

        if (infos != null)
            for (GridCacheEntryInfo<K, V> e : infos)
                marshalInfo(e, ctx);
    }

    /**
     * @param infos Entries to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @throws GridException If failed.
     */
    protected final void unmarshalInfos(Iterable<? extends GridCacheEntryInfo<K, V>> infos, GridCacheContext<K, V> ctx,
        ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (infos != null)
            for (GridCacheEntryInfo<K, V> e : infos)
                unmarshalInfo(e, ctx, ldr);
    }

    /**
     * @param txEntries Entries to marshal.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    protected final void marshalTx(Iterable<GridCacheTxEntry<K, V>> txEntries, GridCacheContext<K, V> ctx)
        throws GridException {
        assert ctx != null;

        if (txEntries != null)
            for (GridCacheTxEntry<K, V> e : txEntries) {
                e.marshal(ctx);

                prepareObject(e.key(), ctx);
                prepareObject(e.value(), ctx);
                prepareFilter(e.filters(), ctx);
            }
    }

    /**
     * @param txEntries Entries to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @throws GridException If failed.
     */
    protected final void unmarshalTx(Iterable<GridCacheTxEntry<K, V>> txEntries, GridCacheContext<K, V> ctx,
        ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (txEntries != null)
            for (GridCacheTxEntry<K, V> e : txEntries)
                e.unmarshal(ctx, ldr);
    }

    /**
     * @param filter Collection to marshal.
     * @param ctx Context.
     * @return Marshalled collection.
     * @throws GridException If failed.
     */
    @Nullable protected final <T> byte[][] marshalFilter(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter,
        GridCacheContext<K, V> ctx) throws GridException {
        assert ctx != null;

        if (filter == null)
            return null;

        byte[][] filterBytes = new byte[filter.length][];

        for (int i = 0; i < filter.length; i++) {
            GridPredicate<? super GridCacheEntry<K, V>> p = filter[i];

            prepareObject(p, ctx);

            filterBytes[i] = p == null ? null : CU.marshal(ctx, p).getEntireArray();
        }

        return filterBytes;
    }

    /**
     * @param byteCol Collection to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @return Unmarshalled collection.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable protected final <T> GridPredicate<? super GridCacheEntry<K, V>>[] unmarshalFilter(
        @Nullable byte[][] byteCol, GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (byteCol == null)
            return null;

        GridPredicate<? super GridCacheEntry<K, V>>[] filter = new GridPredicate[byteCol.length];

        GridMarshaller mrsh = ctx.marshaller();

        for (int i = 0; i < byteCol.length; i++)
            filter[i] = byteCol[i] == null ? null :
                (GridPredicate<? super GridCacheEntry<K, V>>)U.unmarshal(mrsh, new GridByteArrayList(byteCol[i]), ldr);

        return filter;
    }

    /**
     * @param col Collection to marshal.
     * @param ctx Context.
     * @return Marshalled collection.
     * @throws GridException If failed.
     */
    @Nullable protected final List<byte[]> marshalCollection(@Nullable Collection<?> col,
        GridCacheContext<K, V> ctx) throws GridException {
        assert ctx != null;

        if (col == null)
            return null;

        List<byte[]> byteCol = new ArrayList<byte[]>(col.size());

        for (Object o : col) {
            prepareObject(o, ctx);

            byteCol.add(o == null ? null : CU.marshal(ctx, o).getEntireArray());
        }

        return byteCol;
    }

    /**
     * @param byteCol Collection to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @return Unmarshalled collection.
     * @throws GridException If failed.
     */
    @Nullable protected final <T> List<T> unmarshalCollection(@Nullable Collection<byte[]> byteCol,
        GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (byteCol == null)
            return null;

        List<T> col = new ArrayList<T>(byteCol.size());

        GridMarshaller mrsh = ctx.marshaller();

        for (byte[] bytes : byteCol)
            col.add(bytes == null ? null : U.<T>unmarshal(mrsh, new GridByteArrayList(bytes), ldr));

        return col;
    }

    /**
     * @param map Map to marshal.
     * @param ctx Context.
     * @return Marshalled map.
     * @throws GridException If failed.
     */
    @Nullable protected final Map<byte[], byte[]> marshalMap(@Nullable Map<?, ?> map,
        GridCacheContext<K, V> ctx) throws GridException {
        assert ctx != null;

        if (map == null)
            return null;

        Map<byte[], byte[]> byteMap = new HashMap<byte[], byte[]>(map.size());

        for (Map.Entry e : map.entrySet()) {
            prepareObject(e.getKey(), ctx);
            prepareObject(e.getValue(), ctx);

            byteMap.put(
                CU.marshal(ctx, e.getKey()).getEntireArray(),
                CU.marshal(ctx, e.getValue()).getEntireArray()
            );
        }

        return byteMap;
    }

    /**
     * @param byteMap Map to unmarshal.
     * @param ctx Context.
     * @param ldr Loader.
     * @return Unmarshalled map.
     * @throws GridException If failed.
     */
    @Nullable protected final <K1, V1> Map<K1, V1> unmarshalMap(@Nullable Map<byte[], byte[]> byteMap,
        GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        assert ldr != null;
        assert ctx != null;

        if (byteMap == null)
            return null;

        Map<K1, V1> map = new HashMap<K1, V1>(byteMap.size());

        GridMarshaller mrsh = ctx.marshaller();

        for (Map.Entry<byte[], byte[]> e : byteMap.entrySet())
            map.put(
                U.<K1>unmarshal(mrsh, new GridByteArrayList(e.getKey()), ldr),
                U.<V1>unmarshal(mrsh, new GridByteArrayList(e.getValue()), ldr)
            );

        return map;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(msgId);

        out.writeObject(depInfo);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        msgId = in.readLong();

        depInfo = (GridDeploymentInfo)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheMessage.class, this);
    }
}
