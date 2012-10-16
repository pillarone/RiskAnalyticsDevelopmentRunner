// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.managers.swapspace.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Handles all swap operations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheSwapManager<K, V> extends GridCacheManager<K, V> {
    /** Swap manager. */
    private GridSwapSpaceManager swapMgr;

    /** */
    private String spaceName;

    /** Flag to indicate if swap is enabled. */
    private final boolean enabled;

    /**
     * @param enabled Flag to indicate if swap is enabled.
     */
    public GridCacheSwapManager(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        spaceName = CU.swapSpaceName(cctx);

        swapMgr = cctx.gridSwap();
    }

    /**
     * @return Flag to indicate if swap is enabled.
     */
    boolean enabled() {
        return enabled;
    }

    /**
     *
     * @return Swap size.
     * @throws GridException If failed.
     */
    long swapSize() throws GridException {
        return enabled ? swapMgr.swapSize(spaceName) : -1;
    }

    /**
     * Recreates raw swap entry (that just has been  received from swap storage).
     *
     * @param e Swap entry to reconstitute.
     * @return Reconstituted swap entry or {@code null} if entry is obsolete.
     * @throws GridException If failed.
     */
    @Nullable private GridCacheSwapEntry<V> recreateEntry(GridCacheSwapEntry<V> e) throws GridException {
        assert e != null;

        ClassLoader ldr = e.classLoaderId() != null ? cctx.deploy().getClassLoader(e.classLoaderId()) :
            cctx.deploy().localLoader();

        if (ldr == null)
            return null;

        e.value(this.<V>unmarshal(e.valueBytes(), ldr));

        e.metrics().delegate((GridCacheMetricsAdapter)cctx.cache().metrics());

        return e;
    }

    /**
     * @param keyBytes Key to remove.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable GridCacheSwapEntry<V> read(byte[] keyBytes) throws GridException {
        if (!enabled)
            return null;

        GridSwapByteArray valBytes = swapMgr.read(spaceName, new GridSwapByteArray(keyBytes));

        if (valBytes == null)
            return null;

        // To unmarshal swap entry itself local class loader will be enough.
        return recreateEntry((GridCacheSwapEntry<V>)unmarshal(valBytes, cctx.deploy().localLoader()));
    }

    /**
     * @param keyBytes Key to remove.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable GridCacheSwapEntry<V> readAndRemove(byte[] keyBytes) throws GridException {
        if (!enabled)
            return null;

        final GridTuple<GridSwapByteArray> t = F.t1();

        swapMgr.remove(spaceName, new GridSwapByteArray(keyBytes), new CI1<GridSwapByteArray>() {
            @Override public void apply(GridSwapByteArray removed) {
                t.set(removed);
            }
        });

        if (t.get() == null)
            return null;

        // To unmarshal swap entry itself local class loader will be enough.
        return recreateEntry((GridCacheSwapEntry<V>)unmarshal(t.get(), cctx.deploy().localLoader()));
    }

    /**
     * @param entry Entry to read.
     * @return Read value.
     * @throws GridException If read failed.
     */
    @Nullable GridCacheSwapEntry<V> read(GridCacheMapEntry<K, V> entry) throws GridException {
        if (!enabled)
            return null;

        return read(entry.getOrMarshalKeyBytes());
    }

    /**
     * @param key Key to read swap entry for.
     * @return Read value.
     * @throws GridException If read failed.
     */
    @Nullable GridCacheSwapEntry<V> read(K key) throws GridException {
        if (!enabled)
            return null;

        return read(CU.marshal(cctx, key).getEntireArray());
    }

    /**
     * @param entry Entry to read.
     * @return Read value.
     * @throws GridException If read failed.
     */
    @Nullable GridCacheSwapEntry<V> readAndRemove(GridCacheMapEntry<K, V> entry) throws GridException {
        if (!enabled)
            return null;

        return readAndRemove(entry.getOrMarshalKeyBytes());
    }

    /**
     * @param key Key to read swap entry for.
     * @return Read value.
     * @throws GridException If read failed.
     */
    @Nullable GridCacheSwapEntry<V> readAndRemove(K key) throws GridException {
        if (!enabled)
            return null;

        return readAndRemove(CU.marshal(cctx, key).getEntireArray());
    }

    /**
     * @param key Key to remove.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException If failed.
     */
    boolean remove(byte[] key) throws GridException {
        return enabled && swapMgr.remove(spaceName, new GridSwapByteArray(key), null);
    }

    /**
     * Writes a versioned value to swap.
     *
     * @param key Key.
     * @param val Value.
     * @param ver Version.
     * @param metrics Metrics.
     * @param ttl Entry time to live.
     * @param expireTime Swap entry expiration time.
     * @param clsLdrId Class loader id for entry value.
     * @throws GridException If failed.
     */
    void write(byte[] key, byte[] val, GridCacheVersion ver, long ttl, long expireTime,
        GridCacheMetricsAdapter metrics, UUID clsLdrId) throws GridException {
        if (!enabled)
            return;

        GridCacheSwapEntry<V> entry = new GridCacheSwapEntry<V>(val, ver, ttl, expireTime, metrics, clsLdrId);

        swapMgr.write(spaceName, new GridSwapByteArray(key), new GridSwapByteArray(marshal(entry)));
    }

    /**
     * @param bytes Bytes to unmarshal.
     * @param ldr Class loader.
     * @param <T> Type to unmarshal.
     * @return Unmarshalled value.
     * @throws GridException If unmarshal failed.
     */
    @SuppressWarnings({"unchecked", "TypeMayBeWeakened"})
    private <T> T unmarshal(byte[] bytes, ClassLoader ldr) throws GridException {
        return (T)U.unmarshal(cctx.marshaller(), new ByteArrayInputStream(bytes), ldr);
    }

    /**
     * @param bytes Bytes to unmarshal.
     * @param ldr Class loader.
     * @param <T> Type to unmarshal.
     * @return Unmarshalled value.
     * @throws GridException If unmarshal failed.
     */
    @SuppressWarnings({"unchecked", "TypeMayBeWeakened"})
    private <T> T unmarshal(GridSwapByteArray bytes, ClassLoader ldr) throws GridException {
        return (T)U.unmarshal(cctx.marshaller(),
            new ByteArrayInputStream(bytes.getArray(), bytes.getOffset(), bytes.getLength()), ldr);
    }

    /**
     * @param obj Object to marshal.
     * @return Marshalled byte array.
     * @throws GridException If marshalling failed.
     */
    private byte[] marshal(Object obj) throws GridException {
        return CU.marshal(cctx, obj).getEntireArray();
    }
}