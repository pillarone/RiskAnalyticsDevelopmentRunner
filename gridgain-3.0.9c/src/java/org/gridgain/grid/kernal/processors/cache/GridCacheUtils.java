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
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Cache utility methods.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheUtils {
    /** Peek flags. */
    private static final GridCachePeekMode[] PEEK_FLAGS = new GridCachePeekMode[]{GLOBAL, SWAP};

    /** Per-thread generated UID store. */
    private static final ThreadLocal<String> UUIDS = new ThreadLocal<String>() {
        @Override protected String initialValue() {
            return UUID.randomUUID().toString();
        }
    };

    /** Empty predicate array. */
    private static final GridPredicate[] EMPTY = new GridPredicate[0];

    /** {@link GridCacheReturn}-to-value conversion. */
    private static final GridClosure RET2VAL =
        new GridClosure<GridCacheReturn, Object>() {
            @Override public Object apply(GridCacheReturn ret) {
                return ret.value();
            }

            @Override public String toString() {
                return "Cache return value to value converter.";
            }
        };

    /** {@link GridCacheReturn}-to-success conversion. */
    private static final GridClosure RET2FLAG =
        new GridClosure<GridCacheReturn, Boolean>() {
            @Override public Boolean apply(GridCacheReturn ret) {
                return ret.success();
            }

            @Override public String toString() {
                return "Cache return value to boolean flag converter.";
            }
        };

    /** Partition to state transformer. */
    private static final GridClosure PART2STATE =
        new GridClosure<GridDhtLocalPartition, GridDhtPartitionState>() {
            @Override public GridDhtPartitionState apply(GridDhtLocalPartition p) {
                return p.state();
            }
        };

    /** */
    private static final GridClosure<Integer, GridCacheVersion[]> VER_ARR_FACTORY =
        new C1<Integer, GridCacheVersion[]>() {
            @Override public GridCacheVersion[] apply(Integer size) {
                return new GridCacheVersion[size];
            }
        };

    /** Empty predicate array. */
    private static final GridPredicate[] EMPTY_FILTER = new GridPredicate[0];

    /** Read filter. */
    private static final GridPredicate READ_FILTER = new P1<Object>() {
        @Override public boolean apply(Object e) {
            return ((GridCacheTxEntry)e).op() == READ;
        }

        @Override public String toString() {
            return "Cache transaction read filter";
        }
    };

    /** Write filter. */
    private static final GridPredicate WRITE_FILTER = new P1<Object>() {
        @Override public boolean apply(Object e) {
            return ((GridCacheTxEntry)e).op() != READ;
        }

        @Override public String toString() {
            return "Cache transaction write filter";
        }
    };

    /** Transaction entry to key. */
    private static final GridClosure tx2key = new C1<GridCacheTxEntry, Object>() {
        @Override public Object apply(GridCacheTxEntry e) {
            return e.key();
        }

        @Override public String toString() {
            return "Cache transaction entry to key converter.";
        }
    };

    /** Converts transaction to XID. */
    private static final GridClosure<GridCacheTx, UUID> tx2xid = new C1<GridCacheTx, UUID>() {
        @Override public UUID apply(GridCacheTx tx) {
            return tx.xid();
        }

        @Override public String toString() {
            return "Transaction to XID converter.";
        }
    };

    /** Converts transaction to XID version. */
    private static final GridClosure tx2xidVer = new C1<GridCacheTxEx, GridCacheVersion>() {
        @Override public GridCacheVersion apply(GridCacheTxEx tx) {
            return tx.xidVersion();
        }

        @Override public String toString() {
            return "Transaction to XID version converter.";
        }
    };

    /** Transaction entry to key bytes. */
    private static final GridClosure tx2keyBytes = new C1<GridCacheTxEntry, byte[]>() {
        @Nullable @Override public byte[] apply(GridCacheTxEntry e) {
            return e.keyBytes();
        }

        @Override public String toString() {
            return "Cache transaction entry to key converter.";
        }
    };

    /** Transaction entry to key. */
    private static final GridClosure entry2key = new C1<GridCacheEntryEx, Object>() {
        @Override public Object apply(GridCacheEntryEx e) {
            return e.key();
        }

        @Override public String toString() {
            return "Cache extended entry to key converter.";
        }
    };

    /** Transaction entry to key. */
    private static final GridClosure info2key = new C1<GridCacheEntryInfo, Object>() {
        @Override public Object apply(GridCacheEntryInfo e) {
            return e.key();
        }

        @Override public String toString() {
            return "Cache extended entry to key converter.";
        }
    };

    /**
     * Gets per-thread-unique ID for this thread.
     *
     * @return ID for this thread.
     */
    public static String uuid() {
        return UUIDS.get();
    }

    /**
     * @param msg Message to check.
     * @return {@code True} if preloader message.
     */
    public static boolean isPreloaderMessage(Object msg) {
        return msg instanceof GridCacheMessage && ((GridCacheMessage)msg).isPreloaderMessage();
    }

    /**
     * Writes {@link GridCacheVersion} to output stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param ver Version to write.
     * @throws IOException If write failed.
     */
    public static void writeVersion(DataOutput out, GridCacheVersion ver) throws IOException {
        // Write null flag.
        out.writeBoolean(ver == null);

        if (ver != null) {
            U.writeUuid(out, ver.id());

            out.writeLong(ver.order());
        }
    }

    /**
     * Reads {@link GridCacheVersion} from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read version.
     * @throws IOException If read failed.
     */
    @Nullable public static GridCacheVersion readVersion(DataInput in) throws IOException {
        // If UUID is not null.
        if (!in.readBoolean()) {
            UUID id = U.readUuid(in);

            long order = in.readLong();

            return new GridCacheVersion(order, id);
        }

        return null;
    }

    /**
     * Writes {@link GridCacheMetrics} to output stream. This method is meant
     * to be used by implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param metrics Metrics to write.
     * @throws IOException If write failed.
     */
    public static void writeMetrics(DataOutput out, GridCacheMetrics metrics) throws IOException {
        // Write null flag.
        out.writeBoolean(metrics == null);

        if (metrics != null) {
            out.writeLong(metrics.createTime());
            out.writeLong(metrics.readTime());
            out.writeLong(metrics.writeTime());
            out.writeInt(metrics.reads());
            out.writeInt(metrics.writes());
            out.writeInt(metrics.hits());
            out.writeInt(metrics.misses());
        }
    }

    /**
     * Reads {@link GridCacheMetrics} from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read metrics.
     * @throws IOException If read failed.
     */
    @Nullable public static GridCacheMetrics readMetrics(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            long createTime = in.readLong();
            long readTime = in.readLong();
            long writeTime = in.readLong();
            int reads = in.readInt();
            int writes = in.readInt();
            int hits = in.readInt();
            int misses = in.readInt();

            return new GridCacheMetricsAdapter(createTime, readTime, writeTime, reads, writes, hits, misses);
        }

        return null;
    }

    /**
     * @param ctx Cache context.
     * @param meta Meta name.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Filter for entries with meta.
     */
    public static <K, V> GridPredicate<K> keyHasMeta(final GridCacheContext<K, V> ctx, final String meta) {
        return new P1<K>() {
            @Override public boolean apply(K k) {
                GridCacheEntryEx<K, V> e = ctx.cache().peekEx(k);

                return e != null && e.hasMeta(meta);
            }
        };
    }

    /**
     * @param err If {@code true}, then throw {@link GridCacheFilterFailedException},
     *      otherwise return {@code val} passed in.
     * @param <T> Return type.
     * @return Always return {@code null}.
     * @throws GridCacheFilterFailedException If {@code err} flag is {@code true}.
     */
    @Nullable public static <T> T failed(boolean err) throws GridCacheFilterFailedException {
        return failed(err, (T)null);
    }

    /**
     * @param err If {@code true}, then throw {@link GridCacheFilterFailedException},
     *      otherwise return {@code val} passed in.
     * @param val Value for which evaluation happened.
     * @param <T> Return type.
     * @return Always return {@code val} passed in or throw exception.
     * @throws GridCacheFilterFailedException If {@code err} flag is {@code true}.
     */
    @Nullable public static <T> T failed(boolean err, T val) throws GridCacheFilterFailedException {
        if (err)
            throw new GridCacheFilterFailedException(val);

        return null;
    }

    /**
     * Entry predicate factory mostly used for deserialization.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Factory instance.
     */
    public static <K, V> GridClosure<Integer, GridPredicate<GridCacheEntry<K, V>>[]> factory() {
        return new GridClosure<Integer, GridPredicate<GridCacheEntry<K, V>>[]>() {
            @SuppressWarnings({"unchecked"})
            @Override public GridPredicate<GridCacheEntry<K, V>>[] apply(Integer len) {
                return (GridPredicate<GridCacheEntry<K, V>>[])(len == 0 ? EMPTY : new GridPredicate[len]);
            }
        };
    }

    /**
     * Checks that cache store is present.
     *
     * @param ctx Registry.
     * @throws GridException If cache store is not present.
     */
    public static void checkStore(GridCacheContext<?, ?> ctx) throws GridException {
        if (ctx.config().getStore() == null)
            throw new GridException("Failed to find cache store for method 'reload(..)' " +
                "(is GridCacheStore configured?)");
    }

    /**
     * Loads data from persistent store.
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param key Cache key.
     * @param <V> Value type.
     * @return Loaded value, possibly <tt>null</tt>.
     * @throws GridException If data loading failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <K, V> V loadFromStore(GridCacheContext ctx, GridLogger log, GridCacheTx tx, K key)
        throws GridException {
        GridCacheConfigurationAdapter cfg = ctx.config();

        if (cfg.getStore() != null) {
            if (log.isDebugEnabled())
                log.debug("Loading value from store for key: " + key);

            // Try to load value only by GridCacheInternalStorableKey.
            if (key instanceof GridCacheInternal) {
                if (key instanceof GridCacheInternalStorableKey) {
                    GridCacheStore store = cfg.getStore();

                    GridCacheInternalStorableKey locKey = (GridCacheInternalStorableKey)key;

                    Object val = store.load(ctx.cache().name(), tx, locKey.name());

                    return (V)locKey.stored2cache(val);
                }
                else
                    return null;
            }

            GridCacheStore<K, V> store = cfg.getStore();

            V val = store.load(ctx.cache().name(), tx, key);

            if (log.isDebugEnabled())
                log.debug("Loaded value from store [key=" + key + ", val=" + val + ']');

            return val;
        }

        return null;
    }

    /**
     * Loads data from persistent store.
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param keys Cache keys.
     * @param vis Closure.
     * @param <V> Value type.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If data loading failed.
     */
    public static <K, V> boolean loadAllFromStore(GridCacheContext ctx, GridLogger log, GridCacheTx tx,
        Collection<? extends K> keys, GridInClosure2<K, V> vis) throws GridException {
        GridCacheStore<K, V> store = ctx.config().getStore();

        if (store != null) {
            if (log.isDebugEnabled())
                log.debug("Loading values from store for keys: " + keys);

            if (!keys.isEmpty()) {
                if (keys.size() == 1) {
                    K key = F.first(keys);

                    vis.apply(key, CU.<K, V>loadFromStore(ctx, log, tx, key));

                    return true;
                }

                try {
                    store.loadAll(ctx.cache().name(), tx, keys, vis);
                }
                catch (GridRuntimeException e) {
                    throw U.cast(e);
                }
            }

            if (log.isDebugEnabled())
                log.debug("Loaded values from store for keys: " + keys);

            return true;
        }

        return false;
    }

    /**
     * Loads data from persistent store.
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param vis Closer to cache loaded elements.
     * @param args User arguments.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If data loading failed.
     */
    @SuppressWarnings({"ErrorNotRethrown"})
    public static <K, V> boolean loadCache(GridCacheContext ctx, GridLogger log, GridInClosure2<K, V> vis,
        Object[] args) throws GridException {
        GridCacheStore<K, V> store = ctx.config().getStore();

        if (store != null) {
            if (log.isDebugEnabled())
                log.debug("Loading all values from store.");

            try {
                store.loadAll(ctx.cache().name(), vis, args);
            }
            catch (GridRuntimeException e) {
                throw U.cast(e);
            }
            catch (AssertionError e) {
                throw new GridException(e);
            }

            if (log.isDebugEnabled())
                log.debug("Loaded all values from store.");

            return true;
        }

        return false;
    }

    /**
     * Puts key-value pair into storage.
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param key Key.
     * @param val Value.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If storage failed.
     */
    public static <K, V> boolean putToStore(GridCacheContext<K, V> ctx, GridLogger log, GridCacheTx tx,
        K key, V val) throws GridException {
        GridCacheConfigurationAdapter cfg = ctx.config();

        if (cfg.getStore() != null) {
            if (log.isDebugEnabled())
                log.debug("Storing value in cache store [key=" + key + ", val=" + val + ']');

            if (key instanceof GridCacheInternal) {
                if (key instanceof GridCacheInternalStorableKey && val instanceof GridCacheInternalStorable
                    && ((GridCacheInternalStorable)val).persistent()) {
                    // Set locKey as name of named cache structure.
                    String locKey = ((GridCacheInternalStorableKey)key).name();

                    Object locVal = ((GridCacheInternalStorable)val).cached2Store();

                    cfg.getStore().put(ctx.cache().name(), tx, locKey, locVal);
                }

                return true;
            }
            else
                cfg.getStore().put(ctx.cache().name(), tx, key, val);

            if (log.isDebugEnabled())
                log.debug("Stored value in cache store [key=" + key + ", val=" + val + ']');

            return true;
        }

        return false;
    }

    /**
     * Puts key-value pair into storage.
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param map Map.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If storage failed.
     */
    public static <K, V> boolean putAllToStore(GridCacheContext<K, V> ctx, GridLogger log, GridCacheTx tx,
        Map<? extends K, ? extends V> map) throws GridException {
        if (F.isEmpty(map))
            return true;

        if (map.size() == 1) {
            Map.Entry<? extends K, ? extends V> e = map.entrySet().iterator().next();

            return putToStore(ctx, log, tx, e.getKey(), e.getValue());
        }
        else {
            GridCacheConfigurationAdapter cfg = ctx.config();

            if (cfg.getStore() != null) {
                if (log.isDebugEnabled())
                    log.debug("Storing values in cache store [map=" + map + ']');

                cfg.getStore().putAll(ctx.cache().name(), tx, map);

                if (log.isDebugEnabled())
                    log.debug("Stored value in cache store [map=" + map + ']');

                return true;
            }

            return false;
        }
    }

    /**
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param key Key.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If storage failed.
     */
    public static <K, V> boolean removeFromStore(GridCacheContext<K, V> ctx, GridLogger log, GridCacheTx tx, K key)
        throws GridException {
        GridCacheConfigurationAdapter cfg = ctx.config();

        if (cfg.getStore() != null) {
            if (log.isDebugEnabled())
                log.debug("Removing value from cache store [key=" + key + ']');

            if (key instanceof GridCacheInternal) {
                if (key instanceof GridCacheInternalStorableKey) {
                    // Set locKey as name of named cache structure.
                    String locKey = ((GridCacheInternalStorableKey)key).name();

                    cfg.getStore().remove(ctx.cache().name(), tx, locKey);
                }
                else
                    return false;
            }
            else
                cfg.getStore().remove(ctx.cache().name(), tx, key);

            if (log.isDebugEnabled())
                log.debug("Removed value from cache store [key=" + key + ']');

            return true;
        }

        return false;
    }

    /**
     *
     * @param ctx Cache registry.
     * @param log Logger.
     * @param tx Cache transaction.
     * @param keys Key.
     * @return {@code True} if there is a persistent storage.
     * @throws GridException If storage failed.
     */
    public static <K, V> boolean removeAllFromStore(GridCacheContext<K, V> ctx, GridLogger log, GridCacheTx tx,
        Collection<? extends K> keys) throws GridException {
        if (F.isEmpty(keys))
            return true;

        if (keys.size() == 1) {
            K key = keys.iterator().next();

            return removeFromStore(ctx, log, tx, key);
        }

        GridCacheConfigurationAdapter cfg = ctx.config();

        if (cfg.getStore() != null) {
            if (log.isDebugEnabled())
                log.debug("Removing values from cache store [keys=" + keys + ']');

            cfg.getStore().removeAll(ctx.cache().name(), tx, keys);

            if (log.isDebugEnabled())
                log.debug("Removed values from cache store [keys=" + keys + ']');

            return true;
        }

        return false;
    }

    /**
     * @param ctx Cache registry.
     * @return Space name.
     */
    public static String swapSpaceName(GridCacheContext<?, ?> ctx) {
        String name = ctx.cache().name();

        if (name == null)
            name = "gg-dflt-space";

        return name;
    }

    /**
     * Gets closure which returns {@link GridCacheEntry} given cache key.
     *
     * @param ctx Cache context.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Closure which returns {@link GridCacheEntry} given cache key.
     */
    public static <K, V> GridClosure<K, GridCacheEntry<K, V>> cacheKey2Entry(
        final GridCacheContext<K, V> ctx) {
        return new GridClosure<K, GridCacheEntry<K, V>>() {
            @Nullable @Override public GridCacheEntry<K, V> apply(K k) {
                return ctx.cache().entry(k);
            }

            @Override public String toString() {
                return "Key-to-entry transformer.";
            }
        };
    }

    /**
     * @return Closure that converts {@link GridCacheReturn} to value.
     */
    @SuppressWarnings({"unchecked"})
    public static <V> GridClosure<GridCacheReturn<V>, V> return2value() {
        return (GridClosure<GridCacheReturn<V>, V>)RET2VAL;
    }

    /**
     * @return Partition to state transformer.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridDhtLocalPartition<K, V>, GridDhtPartitionState> part2state() {
        return (GridClosure<GridDhtLocalPartition<K, V>, GridDhtPartitionState>)PART2STATE;
    }

    /**
     * @return Closure that converts {@link GridCacheReturn} to success flag.
     */
    @SuppressWarnings({"unchecked"})
    public static <V> GridClosure<GridCacheReturn<V>, Boolean> return2flag() {
        return RET2FLAG;
    }

    /**
     * Gets all nodes on which cache with the same name is started.
     *
     * @param ctx Cache context.
     * @return All nodes on which cache with the same name is started.
     */
    public static Collection<GridRichNode> allNodes(final GridCacheContext ctx) {
        return F.viewReadOnly(ctx.discovery().allNodes(), ctx.rich().richNode(), new P1<GridNode>() {
            @Override public boolean apply(GridNode node) {
                return cacheNode(ctx, node);
            }
        });
    }

    /**
     * Gets affinity nodes.
     *
     * @param ctx Cache context.
     * @param topOrder Maximum allowed node order.
     * @return Affinity nodes.
     */
    public static Collection<GridRichNode> allNodes(final GridCacheContext ctx, final long topOrder) {
        return F.viewReadOnly(ctx.discovery().allNodes(), ctx.rich().richNode(), new P1<GridNode>() {
            @Override public boolean apply(GridNode node) {
                return node.order() <= topOrder && cacheNode(ctx, node);
            }
        });
    }

    /**
     * Checks if given node contains configured cache with the name
     * as described by given cache context.
     *
     * @param ctx Cache context.
     * @param node Node to check.
     * @return {@code true} if node contains required cache.
     */
    public static boolean cacheNode(GridCacheContext ctx, GridNode node) {
        assert ctx != null;
        assert node != null;

        return U.hasCache(node, ctx.namex());
    }

    /**
     * @param nodes Nodes.
     * @return Maximum node order.
     */
    public static long maxOrder(Collection<? extends GridNode> nodes) {
        if (nodes == null || nodes.isEmpty())
            return -1;

        long max = Long.MIN_VALUE;

        for (GridNode n : nodes) {
            long order = n.order();

            if (order > max)
                max = order;
        }

        return max;
    }

    /**
     * @param nodes Nodes.
     * @return Minimum node order.
     */
    public static long minOrder(Collection<? extends GridNode> nodes) {
        if (nodes == null || nodes.isEmpty())
            return -1;

        long min = Long.MAX_VALUE;

        for (GridNode n : nodes) {
            long order = n.order();

            if (order < min)
                min = order;
        }

        return min;
    }

    /**
     * @param nodes Nodes.
     * @return Oldest node from the given.
     */
    public static GridNode oldest(Collection<? extends GridNode> nodes) {
        assert !F.isEmpty(nodes);

        GridNode oldest = null;

        for (GridNode n : nodes)
            if (oldest == null || n.order() < oldest.order())
                oldest = n;

        assert oldest != null;

        return oldest;
    }

    /**
     * @param nodes Nodes.
     * @return Newest node from the given.
     */
    @Nullable public static GridNode newest(Collection<? extends GridNode> nodes) {
        if (F.isEmpty(nodes))
            return null;

        GridNode newest = null;

        for (GridNode n : nodes)
            if (newest == null || n.order() > newest.order())
                newest = n;

        return newest;
    }

    /**
     * Gets remote nodes on which cache with the same name is started.
     *
     * @param ctx Cache context.
     * @return Remote nodes on which cache with the same name is started.
     */
    public static Collection<GridRichNode> remoteNodes(final GridCacheContext ctx) {
        return GridFunc.viewReadOnly(ctx.discovery().remoteNodes(), ctx.rich().richNode(), new P1<GridNode>() {
            @Override public boolean apply(GridNode node) {
                return cacheNode(ctx, node);
            }
        });
    }

    /**
     * @return Empty filter.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<? super GridCacheEntry<K, V>>[] empty() {
        return (GridPredicate<? super GridCacheEntry<K, V>>[])EMPTY_FILTER;
    }

    /**
     * @return Closure that converts tx entry to key.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheTxEntry<K, V>, K> tx2key() {
        return (GridClosure<GridCacheTxEntry<K, V>, K>)tx2key;
    }

    /**
     * @return Closure that converts tx entry to key.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheTxEntry<K, V>, byte[]> tx2keyBytes() {
        return (GridClosure<GridCacheTxEntry<K, V>, byte[]>)tx2keyBytes;
    }

    @SuppressWarnings( {"unchecked"})
    public static <K, V> GridClosure<GridCacheTxEx<K, V>, GridCacheVersion> tx2xidVersion() {
        return (GridClosure<GridCacheTxEx<K, V>, GridCacheVersion>)tx2xidVer;
    }

    public static GridClosure<GridCacheTx, UUID> tx2xid() {
        return tx2xid;
    }

    /**
     * @return Closure that converts entry to key.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheEntryEx<K, V>, K> entry2Key() {
        return (GridClosure<GridCacheEntryEx<K, V>, K>)entry2key;
    }

    /**
     * @return Closure that converts entry info to key.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheEntryInfo<K, V>, K> info2Key() {
        return (GridClosure<GridCacheEntryInfo<K, V>, K>)info2key;
    }

    /**
     * @return Filter for transaction reads.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheTxEntry<K, V>> reads() {
        return READ_FILTER;
    }

    /**
     * @return Filter for transaction writes.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheTxEntry<K, V>> writes() {
        return WRITE_FILTER;
    }

    /**
     * @param opId Operation ID to filter on.
     * @return Filter for transaction entries with given op ID.
     */
    public static <K, V> GridPredicate<GridCacheTxEntry<K, V>> opId(final int opId) {
        return new P1<GridCacheTxEntry<K, V>>() {
            @Override public boolean apply(GridCacheTxEntry<K, V> e) {
                return e.opId() == opId;
            }
        };
    }

    /**
     * Gets type filter for projections.
     *
     * @param keyType Key type.
     * @param valType Value type.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Type filter.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate2<K, V>[] typeFilter(final Class<?> keyType, final Class<?> valType) {
        return new GridPredicate2[]{
            new P2<K, V>() {
                @Override public boolean apply(K k, V v) {
                    return keyType.isAssignableFrom(k.getClass()) && valType.isAssignableFrom(v.getClass());
                }
            }
        };
    }

    /**
     * @return Boolean reducer.
     */
    public static GridReducer<Boolean, Boolean> boolReducer() {
        return new GridReducer<Boolean, Boolean>() {
            private final AtomicBoolean bool = new AtomicBoolean(true);

            @Override public boolean collect(Boolean b) {
                bool.compareAndSet(true, b);

                // Stop collecting on first failure.
                return bool.get();
            }

            @Override public Boolean apply() {
                return bool.get();
            }
        };
    }

    /**
     * Gets reducer that aggregates maps into one.
     *
     * @param size Predicted size of the resulting map to avoid resizings.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Reducer.
     */
    public static <K, V> GridReducer<Map<K, V>, Map<K, V>> mapsReducer(final int size) {
        return new GridReducer<Map<K, V>, Map<K, V>>() {
            private final Map<K, V> ret = new GridLeanMap<K, V>(size);

            @Override public boolean collect(Map<K, V> map) {
                if (map != null)
                    ret.putAll(map);

                return true;
            }

            @Override public Map<K, V> apply() {
                return ret;
            }
        };
    }

    /**
     * Gets reducer that aggregates collections.
     *
     * @param <T> Collection element type.
     * @return Reducer.
     */
    public static <T> GridReducer<Collection<T>, Collection<T>> reducerCollections() {
        return new GridReducer<Collection<T>, Collection<T>>() {
            private final Collection<T> ret = new LinkedList<T>();

            @Override public boolean collect(Collection<T> c) {
                if (c != null)
                    ret.addAll(c);

                return true;
            }

            @Override public Collection<T> apply() {
                return ret;
            }
        };
    }

    /**
     *
     * @param nodes Set of nodes.
     * @return Primary node.
     */
    public static GridNode primary(Iterable<? extends GridNode> nodes) {
        GridNode n = F.first(nodes);

        assert n != null;

        return n;
    }

    /**
     * @param nodes Set of nodes.
     * @return Primary node.
     */
    public static GridRichNode primary0(Iterable<GridRichNode> nodes) {
        GridRichNode n = F.first(nodes);

        assert n != null;

        return n;
    }

    /**
     * @param nodes Nodes.
     * @param locId Local node ID.
     * @return Local node if it is in the list of nodes, or primary node.
     */
    public static GridRichNode localOrPrimary(Iterable<GridRichNode> nodes, UUID locId) {
        for (GridRichNode n : nodes)
            if (n.id().equals(locId))
                return n;

        return primary0(nodes);
    }

    /**
     * @param nodes Nodes.
     * @return Backup nodes.
     */
    public static Collection<GridRichNode> backups(Collection<GridRichNode> nodes) {
        if (F.isEmpty(nodes))
            return Collections.emptyList();

        final GridNode prim = primary(nodes);

        return F.view(nodes, new P1<GridRichNode>() {
            @Override public boolean apply(GridRichNode node) {
                return node != prim;
            }
        });
    }

    /**
     * @param mappings Mappings.
     * @param k map key.
     * @return Either current list value or newly created one.
     */
    public static <K, V> Collection<V> getOrSet(Map<K, List<V>> mappings, K k) {
        List<V> vals = mappings.get(k);

        if (vals == null)
            mappings.put(k, vals = new LinkedList<V>());

        return vals;
    }

    /**
     * @param mappings Mappings.
     * @param k map key.
     * @return Either current list value or newly created one.
     */
    public static <K, V> Collection<V> getOrSet(ConcurrentMap<K, Collection<V>> mappings, K k) {
        Collection<V> vals = mappings.get(k);

        if (vals == null) {
            Collection<V> old = mappings.putIfAbsent(k, vals = new ConcurrentLinkedQueue<V>());

            if (old != null)
                vals = old;
        }

        return vals;
    }

    /**
     * @return Peek flags.
     */
    public static GridCachePeekMode[] peekFlags() {
        return PEEK_FLAGS;
    }

    /**
     * @param log Logger.
     * @param excl Excludes.
     * @return Future listener that logs errors.
     */
    public static GridInClosure<GridFuture<?>> errorLogger(final GridLogger log,
        final Class<? extends Exception>... excl) {
        return new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> f) {
                try {
                    f.get();
                }
                catch (GridException e) {
                    if (!F.isEmpty(excl))
                        for (Class cls : excl)
                            if (e.hasCause(cls))
                                return;

                    U.error(log, "Future execution resulted in error: " + f, e);
                }
            }
        };
    }

    /**
     * @param ctx Context.
     * @param keys Keys.
     * @return Mapped keys.
     */
    @SuppressWarnings({"unchecked"})
    public static <K> Map<UUID, Collection<K>> mapKeysToNodes(GridCacheContext<K, ?> ctx,
        Collection<? extends K> keys) {
        if (keys == null || keys.isEmpty())
            return Collections.emptyMap();

        // Map all keys to local node for local caches.
        if (ctx.config().getCacheMode() == LOCAL)
            return F.asMap(ctx.nodeId(), (Collection<K>)keys);

        Collection<GridRichNode> nodes = CU.allNodes(ctx);

        if (keys.size() == 1)
            return Collections.singletonMap(CU.primary0(ctx.affinity(F.first(keys), nodes)).id(), (Collection<K>)keys);

        Map<UUID, Collection<K>> map = new GridLeanMap<UUID, Collection<K>>(5);

        for (K k : keys) {
            GridRichNode primary = CU.primary0(ctx.affinity(k, nodes));

            Collection<K> mapped = map.get(primary.id());

            if (mapped == null)
                map.put(primary.id(), mapped = new LinkedList<K>());

            mapped.add(k);
        }

        return map;
    }

    /**
     * @param t Exception to check.
     * @return {@code true} if caused by lock timeout.
     */
    public static boolean isLockTimeout(Throwable t) {
        if (t == null)
            return false;

        while (t instanceof GridException || t instanceof GridRuntimeException)
            t = t.getCause();

        return t instanceof GridCacheLockTimeoutException;
    }

    /**
     * @param ctx Cache context.
     * @param obj Object to marshal.
     * @return Buffer that contains obtained byte array.
     * @throws GridException If marshalling failed.
     */
    public static GridByteArrayList marshal(GridCacheContext ctx, Object obj)
        throws GridException {
        assert ctx != null;

        if (obj != null) {
            if (obj instanceof Iterable)
                ctx.deploy().registerClasses((Iterable)obj);
            else if (obj.getClass().isArray()) {
                if (!U.isPrimitiveArray(obj))
                    ctx.deploy().registerClasses((Object[])obj);
            }
            else
                ctx.deploy().registerClass(obj);
        }

        return U.marshal(ctx.marshaller(), obj);
    }

    /**
     * Method executes any Callable out of scope of transaction.
     * If transaction started by this thread {@code cmd} will be executed in another thread.
     *
     * @param cmd Callable.
     * @param ctx Cache context.
     * @return T Callable result.
     * @throws GridException If execution failed.
     */
    public static <T> T outTx(Callable<T> cmd, GridCacheContext ctx) throws GridException {
        return outTx(cmd, ctx, true);
    }

    /**
     * Method executes any Callable out of scope of transaction.
     * If transaction started by this thread {@code cmd} will be executed in another thread.
     *
     * @param cmd Callable.
     * @param ctx Cache context.
     * @param sys Whether to run on system or public pool.
     * @return T Callable result.
     * @throws GridException If execution failed.
     */
    public static <T> T outTx(Callable<T> cmd, GridCacheContext ctx, boolean sys) throws GridException {
        if (ctx.tm().isInTx())
            return ctx.closures().callLocal(cmd, sys).get();
        else {
            try {
                return cmd.call();
            }
            catch (Exception e) {
                throw new GridException(e);
            }
        }
    }

    /**
     * @param tx Transaction.
     * @return String view of all safe-to-print transaction properties.
     */
    public static String txString(@Nullable GridCacheTx tx) {
        if (tx == null)
            return "null";

        return tx.getClass().getSimpleName() + "[id=" + tx.xid() + ", concurrency=" + tx.concurrency() +
            ", isolation" + tx.isolation() + ", state=" + tx.state() + ", invalidate=" + tx.isInvalidate() +
            ", rollbackOnly=" + tx.isRollbackOnly() + ", nodeId=" + tx.nodeId() + "]";
    }

    /**
     * @param ctx Cache context.
     */
    public static void resetTxContext(GridCacheContext ctx) {
        assert ctx != null;

        ctx.tm().txContextReset();

        if (ctx.isNear())
            ctx.near().dht().context().tm().txContextReset();
    }

    /**
     * @param ctx Cache context.
     */
    public static void unwindEvicts(GridCacheContext ctx) {
        assert ctx != null;

        ctx.evicts().unwind();

        if (ctx.isNear())
            ctx.near().dht().context().evicts().unwind();
    }

    /**
     * Gets primary node on which given key is cached.
     *
     * @param ctx Cache.
     * @param key Key to find primary node for.
     * @return Primary node for the key.
     */
    @SuppressWarnings( {"unchecked"})
    public static GridRichNode primaryNode(GridCacheContext ctx, Object key) {
        assert ctx != null;
        assert key != null;

        GridCacheConfiguration cfg = ctx.cache().configuration();

        if (cfg.getCacheMode() != PARTITIONED)
            return ctx.localNode();

        Collection<GridRichNode> affNodes = allNodes(ctx);

        assert !affNodes.isEmpty();

        GridRichNode primary = F.first(cfg.getAffinity().nodes(ctx.partition(key), affNodes));

        assert primary != null;

        return primary;
    }

    /**
     * @param asc {@code True} for ascending.
     * @return Descending order comparator.
     */
    public static Comparator<? super GridNode> nodeComparator(final boolean asc) {
        return new Comparator<GridNode>() {
            @Override public int compare(GridNode n1, GridNode n2) {
                long o1 = n1.order();
                long o2 = n2.order();

                return asc ? o1 < o2 ? -1 : o1 == o2 ? 0 : 1 : o1 < o2 ? 1 : o1 == o2 ? 0 : -1;
            }
        };
    }

    /**
     * @return Version array factory.
     */
    public static GridClosure<Integer, GridCacheVersion[]> versionArrayFactory() {
        return VER_ARR_FACTORY;
    }
}
