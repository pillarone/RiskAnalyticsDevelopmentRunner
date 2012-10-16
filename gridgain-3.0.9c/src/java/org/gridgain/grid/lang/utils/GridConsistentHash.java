// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Controls key to node affinity using consistent hash algorithm. This class is thread-safe
 * and does not have to be externally synchronized.
 * <p>
 * For a good explanation of what consistent hashing is, you can refer to
 * <a href="http://weblogs.java.net/blog/tomwhite/archive/2007/11/consistent_hash.html">Tom White's Blog</a>.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridConsistentHash<N> implements Serializable {
    /** MD-5 based hasher function. */
    public static final GridClosure<Object, Integer> MD5_HASHER = new GridClosure<Object, Integer>() {
        @Override public Integer apply(Object o) {
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");

                return U.bytesToInt(md5.digest(toHashBytes(o)), 0);
            }
            catch (NoSuchAlgorithmException e) {
                throw new GridRuntimeException("Failed to get an instance of MD5 message digest", e);
            }
        }

        @Override public String toString() {
            return "MD5 Hasher.";
        }
    };

    /** SHA-1 based hasher function. */
    public static final GridClosure<Object, Integer> SHA1_HASHER = new GridClosure<Object, Integer>() {
        @Override public Integer apply(Object o) {
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

                return U.bytesToInt(sha1.digest(toHashBytes(o)), 0);
            }
            catch (NoSuchAlgorithmException e) {
                throw new GridRuntimeException("Failed to get an instance of SHA-1 message digest", e);
            }
        }

        @Override public String toString() {
            return "SHA1 Hasher.";
        }
    };

    /**
     * Murmur2 hasher function based on endian-neutral implementation.
     * For more information refer to <a href="http://sites.google.com/site/murmurhash/">MurmurHash</a> website.
     * <p>
     * This function is used by default if no hasher function is provided explicitly.
     */
    public static final GridClosure<Object, Integer> MURMUR2_HASHER = new GridClosure<Object, Integer>() {
        private static final long M = 0x5bd1e995;
        private static final int R = 24;
        private static final int seed = PRIME;

        @SuppressWarnings({"fallthrough"})
        @Override public Integer apply(Object o) {
            byte[] data = toHashBytes(o);

            int len = data.length;

            int off = 0;

            long h = seed ^ len;

            while (len >= 4) {
                long k = data[off++];

                k |= data[off++] << 8;
                k |= data[off++] << 16;
                k |= data[off++] << 24;

                k *= M;
                k ^= k >> R;
                k *= M;

                h *= M;
                h ^= k;

                len -= 4;
            }

            // Fall through.
            switch (len) {
                case 3: { h ^= data[2] << 16; }
                case 2: { h ^= data[1] << 8; }
                case 1: { h ^= data[0]; h *= M; }
            }

            h ^= h >> 13;
            h *= M;
            h ^= h >> 15;

            return (int)h;
        }

        @Override public String toString() {
            return "Murmur Hasher.";
        }
    };

    /**
     * Converts an object into byte array for hashing.
     *
     * @param o Object to convert.
     * @return Byte array for hashing.
     */
    private static byte[] toHashBytes(Object o) {
        if (o instanceof byte[]) {
            return (byte[])o;
        }

        if (o instanceof UUID) {
            UUID u = (UUID)o;

            long l = u.getLeastSignificantBits();
            long m = u.getMostSignificantBits();

            byte[] bytes = new byte[16];

            longToBytes(l, bytes, longToBytes(m, bytes, 0));

            return bytes;
        }

        if (o instanceof String) {
            return ((String)o).getBytes();
        }

        if (o instanceof Long) {
            return longToBytes((Long)o);
        }

        if (o instanceof Double) {
            return longToBytes(Double.doubleToRawLongBits((Double)o));
        }

        return intToBytes(o.hashCode());
    }

    /**
     * @param i Integer.
     * @return Byte array.
     */
    public static byte[] intToBytes(int i) {
        byte[] b = new byte[4];

        b[0] = (byte)i;
        b[1] = (byte) (i >>> 8);
        b[2] = (byte) (i >>> 16);
        b[3] = (byte) (i >>> 24);

        return b;
    }

    /**
     * @param l Long.
     * @return Bytes.
     */
    public static byte[] longToBytes(long l) {
        byte[] b = new byte[8];

        longToBytes(l, b, 0);

        return b;
    }

    /**
     * @param l Long.
     * @param b Byte array.
     * @param off Offset.
     * @return Offset.
     */
    public static int longToBytes(long l, byte[] b, int off) {
        b[off++] = (byte)l;
        b[off++] = (byte) (l >>> 8);
        b[off++] = (byte) (l >>> 16);
        b[off++] = (byte) (l >>> 24);
        b[off++] = (byte) (l >>> 32);
        b[off++] = (byte) (l >>> 40);
        b[off++] = (byte) (l >>> 48);
        b[off++] = (byte) (l >>> 56);

        return off;
    }

    /** Prime number. */
    private static final int PRIME = 15485857;

    /** Random generator. */
    private static final Random RAND = new Random();

    /** Null value. */
    private static final String NULL = UUID.randomUUID().toString();

    /** Affinity seed. */
    private final Object affSeed;

    /** Hasher function. */
    private final GridClosure<Object, Integer> hasher;

    /** Map of hash assignments. */
    private final SortedMap<Integer, N> circle = new TreeMap<Integer, N>();

    /** Read/write lock. */
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    /** Number of distinct nodes in the hash. */
    private int nodeCnt;

    /**
     * Constructs consistent hash using empty affinity seed and {@code MD5} hasher function.
     */
    public GridConsistentHash() {
        this(null, null);
    }

    /**
     * Constructs consistent hash using given affinity seed and {@code MD5} hasher function.
     *
     * @param affSeed Affinity seed (will be used as key prefix for hashing).
     */
    public GridConsistentHash(@Nullable Object affSeed) {
        this(affSeed, null);
    }

    /**
     * Constructs consistent hash using given hasher function.
     *
     * @param hasher Hasher function to use for generation of uniformly distributed hashes.
     *      If {@code null}, then {@code MD5} hashing is used.
     */
    public GridConsistentHash(@Nullable GridClosure<Object, Integer> hasher) {
        this(null, hasher);
    }

    /**
     * Constructs consistent hash using given affinity seed and hasher function.
     *
     * @param affSeed Affinity seed (will be used as key prefix for hashing).
     * @param hasher Hasher function to use for generation of uniformly distributed hashes.
     *      If {@code null}, then {@code MD5} hashing is used.
     */
    public GridConsistentHash(@Nullable Object affSeed, @Nullable GridClosure<Object, Integer> hasher) {
        this.affSeed = affSeed == null ? new Integer(PRIME) : affSeed;
        this.hasher = hasher == null ? MD5_HASHER : hasher;
    }

    /**
     * Adds nodes to consistent hash algorithm (if nodes are {@code null} or empty, then no-op).
     *
     * @param nodes Nodes to add.
     * @param replicas Number of replicas for every node.
     */
    public void addNodes(@Nullable Collection<N> nodes, int replicas) {
        if (F.isEmpty(nodes)) {
            return;
        }

        assert nodes != null;

        rw.writeLock().lock();

        try {
            for (N node : nodes) {
                addNode(node, replicas);
            }
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Adds a node to consistent hash algorithm.
     *
     * @param node New node (if {@code null} then no-op).
     * @param replicas Number of replicas for the node.
     * @return {@code True} if node was added, {@code false} if it is {@code null} or
     *      is already contained in the hash.
     */
    public boolean addNode(@Nullable N node, int replicas) {
        if (node == null) {
            return false;
        }
        
        long seed = affSeed.hashCode() * 31 + hash(node);

        rw.writeLock().lock();

        try {
            if (!circle.containsValue(node)) {
                int hash = hash(seed);

                boolean added = false;

                if (!circle.containsKey(hash)) {
                    circle.put(hash, node);

                    added = true;
                }
                    
                for (int i = 1; i <= replicas; i++) {
                    seed = seed * affSeed.hashCode() + i;

                    hash = hash(seed);

                    if (!circle.containsKey(hash)) {
                        circle.put(hash, node);

                        added = true;
                    }
                }

                if (added) {
                    nodeCnt++;
                }

                return added;
            }

            return false;
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Removes given nodes and all their replicas from consistent hash algorithm
     * (if nodes are {@code null} or empty, then no-op).
     *
     * @param nodes Nodes to remove.
     */
    public void removeNodes(@Nullable Collection<N> nodes) {
        if (F.isEmpty(nodes)) {
            return;
        }

        assert nodes != null;

        Collection<N> rmv = new LinkedList<N>();
        
        rw.writeLock().lock();

        try {
            for (Iterator<N> it = circle.values().iterator(); it.hasNext();) {
                N n = it.next();

                if (nodes.contains(n)) {
                    it.remove();

                    if (!rmv.contains(n)) {
                        rmv.add(n);

                        nodeCnt--;
                    }
                }
            }
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Removes a node and all of its replicas.
     *
     * @param node Node to remove (if {@code null}, then no-op).
     * @return {@code True} if node was removed, {@code false} if node is {@code null} or
     *      not present in hash.
     */
    public boolean removeNode(@Nullable N node) {
        if (node == null) {
            return false;
        }

        rw.writeLock().lock();

        try {
            boolean rmv = false;

            for (Iterator<N> it = circle.values().iterator(); it.hasNext();) {
                N n = it.next();

                if (n.equals(node)) {
                    rmv = true;

                    it.remove();
                }
            }

            if (rmv) {
                nodeCnt--;

                return true;
            }

            return false;
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Clears all nodes from consistent hash.
     */
    public void clear() {
        rw.writeLock().lock();

        try {
            circle.clear();
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Gets number of distinct nodes, excluding replicas, in consistent hash.
     *
     * @return Number of distinct nodes, excluding replicas, in consistent hash.
     */
    public int count() {
        rw.readLock().lock();

        try {
            return nodeCnt;
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * Gets size of all nodes (including replicas) in consistent hash.
     *
     * @return Size of all nodes (including replicas) in consistent hash.
     */
    public int size() {
        rw.readLock().lock();

        try {
            return circle.size();
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * Checks if consistent hash has nodes added to it.
     *
     * @return {@code True} if consistent hash is empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Gets set of all distinct nodes in the consistent hash (in no particular order).
     *
     * @return Set of all distinct nodes in the consistent hash.
     */
    public Set<N> nodes() {
        rw.readLock().lock();

        try {
            return new HashSet<N>(circle.values());
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * Picks a random node from consistent hash.
     *
     * @return Random node from consistent hash or {@code null} if there are no nodes.
     */
    @Nullable public N random() {
        return node(RAND.nextLong());
    }

    /**
     * Gets node for a key.
     *
     * @param key Key.
     * @return Node.
     */
    @Nullable public N node(@Nullable Object key) {
        int hash = hash(key);

        rw.readLock().lock();

        try {
            if (circle.isEmpty()) {
                return null;
            }

            SortedMap<Integer, N> tailMap = circle.tailMap(hash);

            // Get first node hash in the circle clock-wise.
            return circle.get(tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey());
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * Gets node for a given key.
     *
     * @param key Key to get node for.
     * @param inc Optional inclusion set. Only nodes contained in this set may be returned.
     *      If {@code null}, then all nodes may be included.
     * @return Node for key, or {@code null} if node was not found.
     */
    @Nullable public N node(@Nullable Object key, @Nullable Collection<N> inc) {
        return node(key, inc, null);
    }

    /**
     * Gets node for a given key.
     *
     * @param key Key to get node for.
     * @param inc Optional inclusion set. Only nodes contained in this set may be returned.
     *      If {@code null}, then all nodes may be included.
     * @param exc Optional exclusion set. Only nodes not contained in this set may be returned.
     *      If {@code null}, then all nodes may be returned.
     * @return Node for key, or {@code null} if node was not found.
     */
    @Nullable public N node(@Nullable Object key, @Nullable final Collection<N> inc,
        @Nullable final Collection<N> exc) {
        if (inc == null && exc == null) {
            return node(key);
        }

        return node(key, new GridPredicate<N>() {
            @Override public boolean apply(N n) {
                return (inc == null || inc.contains(n)) && (exc == null || !exc.contains(n));
            }
        });
    }

    /**
     * Gets node for a given key.
     *
     * @param key Key to get node for.
     * @param p Optional predicate for node filtering.
     * @return Node for key, or {@code null} if node was not found.
     */
    @Nullable public N node(@Nullable Object key, @Nullable GridPredicate<N>... p) {
        if (p == null) {
            return node(key);
        }

        int hash = hash(key);

        rw.readLock().lock();

        try {
            if (circle.isEmpty()) {
                return null;
            }

            SortedMap<Integer, N> tailMap = circle.tailMap(hash);

            int size = circle.size();

            // Move clock-wise starting from selected position.
            int idx = 0;

            for (N n : tailMap.values()) {
                if (apply(p, n)) {
                    return n;
                }

                if (++idx >= size) {
                    break;
                }
            }

            if (idx < size) {
                // Wrap around moving clock-wise.
                for (N n : circle.values()) {
                    if (apply(p, n)) {
                        return n;
                    }

                    if (++idx >= size) {
                        break;
                    }
                }
            }

            return null;
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * Gets specified count of adjacent nodes for a given key. If number of nodes in
     * consistent hash is less than specified count, then all nodes are returned.
     *
     * @param key Key to get adjacent nodes for.
     * @param cnt Number of adjacent nodes to get.
     * @return List containing adjacent nodes for given key.
     */
    public List<N> nodes(@Nullable Object key, int cnt) {
        return nodes(key, cnt, null, null);
    }

    /**
     * Gets specified count of adjacent nodes for a given key. If number of nodes in
     * consistent hash is less than specified count, then all nodes are returned.
     *
     * @param key Key to get adjacent nodes for.
     * @param cnt Number of adjacent nodes to get.
     * @param inc Optional inclusion set. Only nodes contained in this set may be returned.
     *      If {@code null}, then all nodes may be returned.
     * @return List containing adjacent nodes for given key.
     */
    public List<N> nodes(@Nullable Object key, int cnt, @Nullable Collection<N> inc) {
        return nodes(key, cnt, inc, null);
    }

    /**
     * Gets specified count of adjacent nodes for a given key. If number of nodes in
     * consistent hash is less than specified count, then all nodes are returned.
     *
     * @param key Key to get adjacent nodes for.
     * @param cnt Number of adjacent nodes to get.
     * @param inc Optional inclusion set. Only nodes contained in this set may be returned.
     *      If {@code null}, then all nodes may be included.
     * @param exc Optional exclusion set. Only nodes not contained in this set may be returned.
     *      If {@code null}, then all nodes may be returned.
     * @return List containing adjacent nodes for given key.
     */
    public List<N> nodes(@Nullable Object key, int cnt, @Nullable final Collection<N> inc,
        @Nullable final Collection<N> exc) {
        A.ensure(cnt >= 0, "cnt >= 0");
        
        if (cnt == 0) {
            return Collections.emptyList();
        }

        if (cnt == 1) {
            return F.asList(node(key, inc, exc));
        }

        return nodes(key, cnt, new GridPredicate<N>() {
            @Override public boolean apply(N n) {
                return (inc == null || inc.contains(n)) && (exc == null || !exc.contains(n));
            }
        });
    }

    /**
     * Gets specified count of adjacent nodes for a given key. If number of nodes in
     * consistent hash is less than specified count, then all nodes are returned.
     *
     * @param key Key to get adjacent nodes for.
     * @param cnt Number of adjacent nodes to get.
     * @param p Optional predicate to filter out nodes. Nodes that don't pass the filter
     *      will be skipped.
     * @return List containing adjacent nodes for given key.
     */
    public List<N> nodes(@Nullable Object key, int cnt, @Nullable GridPredicate<N>... p) {
        A.ensure(cnt >= 0, "cnt >= 0");

        if (cnt == 0) {
            return Collections.emptyList();
        }

        if (cnt == 1) {
            return F.asList(node(key, p));
        }

        int hash = hash(key);

        List<N> ret = new ArrayList<N>(cnt);

        rw.readLock().lock();

        try {
            if (circle.isEmpty()) {
                return Collections.emptyList();
            }

            SortedMap<Integer, N> tailMap = circle.tailMap(hash);

            int size = circle.size();

            // Move clock-wise starting from selected position.
            int idx = 0;

            for (N n : tailMap.values()) {
                if (!ret.contains(n) && apply(p, n)) {
                    ret.add(n);
                }

                if (++idx >= size || ret.size() == cnt) {
                    break;
                }
            }

            if (idx < size && ret.size() < cnt) {
                // Wrap around moving clock-wise.
                for (N n : circle.values()) {
                    if (!ret.contains(n) && apply(p, n)) {
                        ret.add(n);
                    }

                    if (++idx >= size || ret.size() == cnt) {
                        break;
                    }
                }
            }

            return ret;
        }
        finally {
            rw.readLock().unlock();
        }
    }

    /**
     * @param p Predicate.
     * @param n Node.
     * @return {@code True} if filter passed or empty.
     */
    private boolean apply(GridPredicate<N>[] p, N n) {
        return F.isAll(n, p);
    }

    /**
     * Checks if key belongs to the given node.
     *
     * @param key Key to check.
     * @param node Node to check.
     * @return {@code True} if key belongs to given node.
     */
    public boolean belongs(@Nullable Object key, N node) {
        A.notNull(node, "node");

        N n = node(key);

        return n != null && n.equals(node);
    }

    /**
     * Checks if given key belongs to any of the given nodes.
     *
     * @param key Key to check.
     * @param nodes Nodes to check (if {@code null} then {@code false} is returned).
     * @return {@code True} if key belongs to any of the given nodes.
     */
    public boolean belongs(@Nullable Object key, @Nullable Collection<N> nodes) {
        if (F.isEmpty(nodes)) {
            return false;
        }

        assert nodes != null;
        
        N n = node(key);

        return n != null && nodes.contains(n);
    }

    /**
     * Checks if node the key is mapped to or the given count of its adjacent nodes contain
     * the node passed in.
     *
     * @param key Key to check.
     * @param cnt Number of adjacent nodes to check.
     * @param node Node to check.
     * @return {@code True} if node the key is mapped to or given count of its adjacent
     *      neighbors contain the node.
     */
    public boolean belongs(@Nullable Object key, int cnt, N node) {
        return nodes(key, cnt).contains(node);
    }

    /**
     * Checks if node the key is mapped to or the given count of its adjacent nodes are
     * contained in given set of nodes.
     *
     * @param key Key to check.
     * @param cnt Number of adjacent nodes to check.
     * @param nodes Nodes to check.
     * @return {@code True} if node the key is mapped to or given count of its adjacent
     *      neighbors are contained in given set of nodes.
     */
    public boolean belongs(@Nullable Object key, int cnt, @Nullable Collection<N> nodes) {
        if (F.isEmpty(nodes)) {
            return false;
        }

        assert nodes != null;
        
        return nodes.containsAll(nodes(key, cnt));
    }

    /**
     * @param key Key.
     * @return Non-null key value.
     */
    private Object maskNull(@Nullable Object key) {
        return key == null ? NULL : key;
    }

    /**
     * Gets hash code for a given object.
     *
     * @param o Object to get hash code for.
     * @return Hash code.
     */
    private int hash(Object o) {
        return hasher.apply(maskNull(o));
    }
}
