// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 *
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtGetFuture<K, V> extends GridCompoundIdentityFuture<Collection<GridCacheEntryInfo<K, V>>>
    implements GridDhtFuture<K, Collection<GridCacheEntryInfo<K, V>>> {
    /** Message ID. */
    private long msgId;

    /** */
    private UUID reader;

    /** Reload flag. */
    private boolean reload;

    /** Context. */
    private GridCacheContext<K, V> ctx;

    /** Keys. */
    private Collection<? extends K> keys;

    /** Reserved partitions. */
    private Collection<GridDhtLocalPartition> parts = new GridLeanSet<GridDhtLocalPartition>(5);

    /** Future ID. */
    private GridUuid futId;

    /** Version. */
    private GridCacheVersion ver;

    /** Transaction. */
    private GridCacheTxLocalEx<K, V> tx;

    private GridPredicate<? super GridCacheEntry<K, V>>[] filters;

    /** Logger. */
    private GridLogger log;

    /** Retries because ownership changed. */
    private List<K> retries;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtGetFuture() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param msgId Message ID.
     * @param reader Reader.
     * @param keys Keys.
     * @param reload Reload flag.
     * @param tx Transaction.
     * @param filters Filters.
     */
    public GridDhtGetFuture(
        GridCacheContext<K, V> ctx,
        long msgId,
        UUID reader,
        Collection<? extends K> keys,
        boolean reload,
        @Nullable GridCacheTxLocalEx<K, V> tx,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filters
    ) {
        super(ctx.kernalContext(), CU.<GridCacheEntryInfo<K, V>>reducerCollections());

        assert reader != null;
        assert ctx != null;
        assert !F.isEmpty(keys);

        this.reader = reader;
        this.ctx = ctx;
        this.msgId = msgId;
        this.keys = keys;
        this.reload = reload;
        this.filters = filters;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        ver = tx == null ? ctx.versions().next() : tx.xidVersion();

        log = ctx.logger(getClass());
    }

    /**
     * Initializes future.
     */
    void init() {
        map(keys);

        markInitialized();
    }

    /**
     * @return Keys.
     */
    Collection<? extends K> keys() {
        return keys;
    }

    /** {@inheritDoc} */
    @Override public Collection<K> retries() {
        return retries == null ? Collections.<K>emptyList() : retries;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Future version.
     */
    public GridCacheVersion version() {
        return ver;
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Collection<GridCacheEntryInfo<K, V>> res, Throwable err) {
        if (super.onDone(res, err)) {
            // Release all partitions reserved by this future.
            for (GridDhtLocalPartition part : parts) {
                part.release();
            }

            return true;
        }

        return false;
    }

    /**
     * @param keys Keys.
     */
    private void map(final Collection<? extends K> keys) {
        GridFuture<Object> fut = ctx.preloader().request(keys);

        add(new GridEmbeddedFuture<Collection<GridCacheEntryInfo<K, V>>, Object>(ctx.kernalContext(), fut,
            new GridClosure2<Object, Exception, Collection<GridCacheEntryInfo<K, V>>>() {
                @Override public Collection<GridCacheEntryInfo<K, V>> apply(Object o, Exception e) {
                    if (e != null) { // Check error first.
                        if (log.isDebugEnabled())
                            log.debug("Failed to request keys from preloader [keys=" + keys + ", err=" + e + ']');

                        onDone(e);
                    }

                    Collection<K> mappedKeys = new GridLeanSet<K>(keys.size());

                    // Assign keys to primary nodes.
                    for (K key : keys)
                        if (!map(key, parts, mappedKeys)) {
                            if (retries == null)
                                retries = new LinkedList<K>();

                            retries.add(key);
                        }

                    // Add new future.
                    add(getAsync(mappedKeys));

                    // Finish this one.
                    return Collections.emptyList();
                }
            })
        );
    }

    /**
     * @param key Key.
     * @param parts Parts to map.
     * @param keys Keys to map
     * @return {@code True} if mapped.
     */
    private boolean map(K key, Collection<GridDhtLocalPartition> parts, Collection<K> keys) {
        GridDhtLocalPartition part = cache().topology().localPartition(key, false);

        if (part == null)
            return false;

        if (!parts.contains(part))
            // By reserving, we make sure that partition won't be unloaded while processed.
            if (part.reserve()) {
                parts.add(part);

                keys.add(key);

                return true;
            }

        return false;
    }

    /**
     * @param keys Keys to get.
     * @return Future for local get.
     */
    @SuppressWarnings( {"unchecked", "IfMayBeConditional"})
    private GridFuture<Collection<GridCacheEntryInfo<K, V>>> getAsync(final Collection<K> keys) {
        if (F.isEmpty(keys))
            return new GridFinishedFuture<Collection<GridCacheEntryInfo<K, V>>>(ctx.kernalContext(),
                Collections.<GridCacheEntryInfo<K, V>>emptyList());

        final Collection<GridCacheEntryInfo<K, V>> infos = new LinkedList<GridCacheEntryInfo<K, V>>();

        GridCompoundFuture<Boolean, Boolean> txFut = null;

        for (K k : keys) {
            while (true) {
                GridDhtCacheEntry<K, V> e = cache().entryExx(k);

                try {
                    GridCacheEntryInfo<K, V> info = e.info();

                    // If entry is obsolete.
                    if (info == null)
                        continue;

                    // Register reader. If there are active transactions for this entry,
                    // then will wait for their completion before proceeding.
                    GridFuture<Boolean> f = e.addReader(reader, msgId);

                    if (f != null) {
                        if (txFut == null)
                            txFut = new GridCompoundFuture<Boolean, Boolean>(ctx.kernalContext(), CU.boolReducer());

                        txFut.add(f);
                    }

                    infos.add(info);

                    break;
                }
                catch (GridCacheEntryRemovedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry when getting a DHT value: " + e);
                }
            }
        }

        if (txFut != null)
            txFut.markInitialized();

        GridFuture<Map<K, V>> fut;

        if (txFut == null || txFut.isDone()) {
            if (reload)
                fut = cache().reloadAllAsync(keys, true, filters);
            else
                fut = tx == null ? cache().getAllAsync(keys, filters) : tx.getAllAsync(keys, filters);
        }
        else {
            // If we are here, then there were active transactions for some entries
            // when we were adding the reader. In that case we must wait for those
            // transactions to complete.
            fut = new GridEmbeddedFuture<Map<K, V>, Boolean>(
                txFut,
                new C2<Boolean, Exception, GridFuture<Map<K, V>>>() {
                    @Override public GridFuture<Map<K, V>> apply(Boolean b, Exception e) {
                        if (e != null)
                            throw new GridClosureException(e);

                        if (reload)
                            return cache().reloadAllAsync(keys, true, filters);
                        else
                            return tx == null ? cache().getAllAsync(keys, filters) : tx.getAllAsync(keys, filters);
                    }
                },
                ctx.kernalContext());
        }

        return new GridEmbeddedFuture<Collection<GridCacheEntryInfo<K, V>>, Map<K, V>>(ctx.kernalContext(), fut,
            new C2<Map<K, V>, Exception, Collection<GridCacheEntryInfo<K, V>>>() {
                @Override public Collection<GridCacheEntryInfo<K, V>> apply(Map<K, V> map, Exception e) {
                    if (e != null) {
                        onDone(e);

                        return Collections.emptyList();
                    }
                    else {
                        for (Iterator<GridCacheEntryInfo<K, V>> it = infos.iterator(); it.hasNext();) {
                            GridCacheEntryInfo<K, V> info = it.next();

                            V v = map.get(info.key());

                            if (v == null)
                                it.remove();
                            else
                                info.value(v);
                        }

                        return infos;
                    }
                }
            });
    }

    /**
     * @return DHT cache.
     */
    private GridDhtCache<K, V> cache() {
        return (GridDhtCache<K, V>)ctx.cache();
    }
}
