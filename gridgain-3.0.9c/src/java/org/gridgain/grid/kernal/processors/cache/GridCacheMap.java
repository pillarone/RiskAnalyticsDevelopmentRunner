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
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.cache.GridCacheFlag.*;

/**
 * Underlying map used by distributed cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"ObjectEquality"})
public class GridCacheMap<K, V> {
    /** Random. */
    private static final Random RAND = new Random();

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /** The load factor used when none specified in constructor. */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** The table, resized as necessary. Length MUST Always be a power of two. */
    private Bucket<K, V>[] table;

    /** Set of indexes that have been set. */
    private BitSet bits;

    /** Bucket count. */
    private int bucketCnt;

    /** The number of key-value mappings contained in this identity hash map. */
    private final AtomicInteger size = new AtomicInteger(0);

    /** The number of GridCacheInternal key-value mappings contained in this identity hash map. */
    private final AtomicInteger pubSize = new AtomicInteger(0);

    /**
     * The next size value at which to resize (capacity * load factor).
     */
    private int threshold;

    /** The load factor for the hash table. */
    private final float loadFactor;

    /** Cache context. */
    private final GridCacheContext<K, V> ctx;

    /** Entry factory. */
    private GridCacheMapEntryFactory<K, V> factory;

    /**
     * Cache map lock.
     * <p>
     * Note, this lock internally used only for collections. For other
     * operations, this look is used externally.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Filters cache internal entry. */
    private static final P1<GridCacheEntry<?, ?>> NON_INTERNAL =
        new P1<GridCacheEntry<?, ?>>() {
            @Override public boolean apply(GridCacheEntry<?, ?> entry) {
                return !(entry.getKey() instanceof GridCacheInternal);
            }
        };

    /** Non-internal predicate array. */
    public static final GridPredicate[] NON_INTERNAL_ARR = new P1[]{NON_INTERNAL};

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and load factor.
     *
     * @param ctx Cache registry.
     * @param initialCapacity The initial capacity.
     * @param loadFactor The load factor.
     * @throws IllegalArgumentException if the initial capacity is negative
     *      or the load factor is nonpositive.
     */
    @SuppressWarnings("unchecked")
    public GridCacheMap(GridCacheContext<K, V> ctx, int initialCapacity, float loadFactor) {
        assert ctx != null;
        assert initialCapacity > 0;
        assert loadFactor > 0 && !Float.isNaN(loadFactor);

        this.ctx = ctx;

        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        // Find a power of 2 >= initialCapacity
        int capacity = 1;

        while (capacity < initialCapacity)
            capacity <<= 1;

        this.loadFactor = loadFactor;

        threshold = (int)(capacity * loadFactor);

        table = new Bucket[capacity];

        bits = new BitSet(capacity);

        init();
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param ctx Entry factory.
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public GridCacheMap(GridCacheContext<K, V> ctx, int initialCapacity) {
        this(ctx, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Initialization hook for subclasses. This method is called
     * in all constructors and pseudo-constructors (clone, readObject)
     * after HashMap has been initialized but before any entries have
     * been inserted.  (In the absence of this method, readObject would
     * require explicit knowledge of subclasses.)
     */
    protected void init() { /* No-op. */ }

    /**
     * Sets factory for entries.
     *
     * @param factory Entry factory.
     */
    public void setEntryFactory(GridCacheMapEntryFactory<K, V> factory) {
        assert factory != null;

        this.factory = factory;
    }

    /**
     * Read lock.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"}) void readLock() {
        lock.readLock().lock();
    }

    /**
     * Read unlock.
     */
    void readUnlock() {
        lock.readLock().unlock();
    }

    /**
     * Write lock.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"}) void writeLock() {
        lock.writeLock().lock();
    }

    /**
     * Write unlock.
     */
    void writeUnlock() {
        lock.writeLock().unlock();
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because HashMap uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits.
     *
     * @param h Value to hash.
     * @return Hashed value.
     */
    private int hash(int h) {
        h ^= (h >>> 20) ^ (h >>> 12);

        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Check for equality of non-null reference x and possibly-null y.
     *
     * @param x Object 1.
     * @param y Object 2.
     * @return {@code True} if objects are equal.
     */
    private boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /**
     * Returns index for hash code h.
     *
     * @param h Hash code.
     * @param len table length.
     * @return Index within the table.
     */
    private int indexFor(int h, int len) {
        return h & (len - 1);
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        return size.get();
    }

    /**
     * @return Public size.
     */
    public int publicSize() {
        return pubSize.get();
    }

    /**
     * @return Bucket count.
     */
    public int buckets() {
        return bucketCnt;
    }

    /**
     * @return Average bucket size.
     */
    public double averageBucketSize() {
        return (double)size() / bucketCnt;
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return size.get() == 0;
    }

    /**
     * Returns the value to which the specified key is mapped in this identity
     * hash map, or {@code null} if the map contains no mapping for this key.
     * A return value of {@code null} does not <i>necessarily</i> indicate
     * that the map contains no mapping for the key; it is also possible that
     * the map explicitly maps the key to {@code null}. The
     * <tt>containsKey</tt> method may be used to distinguish these two cases.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value to which this map maps the specified key, or
     *      {@code null} if the map contains no mapping for this key.
     * @see #put(Object, Object, long)
     */

    @Nullable public V get(Object key) {
        GridCacheMapEntry<K, V> entry = getEntry(key);

        return entry == null ? null : entry.rawGet();
    }

    /**
     * @param i Index.
     * @return Entry.
     */
    @Nullable private GridCacheMapEntry<K, V> at(int i) {
        Bucket<K, V> b = table[i];

        return b == null ? null : b.entry();
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     *
     * @param k Key.
     * @return Entry.
     */
    @Nullable public GridCacheMapEntry<K, V> getEntry(Object k) {
        assert k != null;

        int hash = hash(k.hashCode());

        int i = indexFor(hash, table.length);

        GridCacheMapEntry<K, V> e = at(i);

        while (e != null && !(e.hash() == hash && eq(k, e.key())))
            e = e.next();

        return e;
    }

    /**
     * Caller of this method should acquire readLock on cache.
     *
     * @return Random entry out of hash map.
     */
    @Nullable public GridCacheMapEntry<K, V> randomEntry() {
        while (true) {
            if (publicSize() == 0)
                return null;

            int size = size();

            // Min sample size should be an average of 10 buckets sizes.
            int minSampleSize = (int)Math.ceil(averageBucketSize() * 5);

            int sampleSize = size < minSampleSize ? size : minSampleSize;

            int lucky = RAND.nextInt(sampleSize);

            int bucketIdx = bits.nextSetBit(RAND.nextInt(table.length));

            // Wrap around.
            if (bucketIdx == -1)
                bucketIdx = bits.nextSetBit(0);

            int cnt = 0;

            while (true) {
                Bucket<K, V> b = table[bucketIdx];

                assert b != null;

                // If we found our bucket.
                if (cnt + b.count() > lucky) {
                    for (GridCacheMapEntry<K, V> e = b.entry(); e != null; e = e.next()) {
                        if (cnt++ == lucky) {
                            if (e.key() instanceof GridCacheInternal)
                                break; // For loop.

                            return e;
                        }
                    }

                    // This means that we hit GridCacheInternal entry, so we retry from scratch.
                    break;
                }
                else {
                    cnt += b.count();

                    bucketIdx = bits.nextSetBit(bucketIdx + 1);

                    // Wrap around.
                    if (bucketIdx == -1)
                        bucketIdx = bits.nextSetBit(0);
                }
            }
        }
    }

    /**
     * Returns {@code true} if this map contains a mapping for the
     * specified key.
     *
     * @param k The key whose presence in this map is to be tested
     * @return {@code True} if this map contains a mapping for the specified
     * key.
     */

    public boolean containsKey(Object k) {
        assert k != null;

        int hash = hash(k.hashCode());

        int i = indexFor(hash, table.length);

        GridCacheMapEntry<K, V> e = at(i);

        while (e != null) {
            if (e.hash() == hash && eq(k, e.key()))
                return true;

            e = e.next();
        }

        return false;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param val value to be associated with the specified key.
     * @param ttl Object time to live.
     * @return previous value associated with specified key, or {@code null}
     *      if there was no mapping for key.  A {@code null} return can
     *      also indicate that the HashMap previously associated
     *      {@code null} with the specified key.
     */
    @Nullable public V put(K key, V val, long ttl) {
        assert key != null;

        int hash = hash(key.hashCode());

        int i = indexFor(hash, table.length);

        for (GridCacheMapEntry<K, V> e = at(i); e != null; e = e.next()) {
            Object k;

            if (e.hash() == hash && ((k = e.key()) == key || key.equals(k))) {
                V old = e.rawGet();

                e.rawPut(val, ttl);

                return old;
            }
        }

        addEntry(hash, key, val, i, ttl);

        return null;
    }

    /**
     *
     * @param key Key.
     * @param val Value.
     * @param ttl Time to live.
     * @return Cache entry for corresponding key-value pair.
     */
    public GridCacheMapEntry<K, V> putEntry(K key, V val, long ttl) {
        assert key != null;

        int hash = hash(key.hashCode());

        int i = indexFor(hash, table.length);

        for (GridCacheMapEntry<K, V> e = at(i); e != null; e = e.next()) {
            Object k;

            if (e.hash() == hash && ((k = e.key()) == key || key.equals(k))) {
                e.rawPut(val, ttl);

                return e;
            }
        }

        return addEntry(hash, key, val, i, ttl);
    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *      must be greater than current capacity unless current
     *      capacity is MAXIMUM_CAPACITY (in which case value is irrelevant).
     */
    private void resize(int newCapacity) {
        Bucket<?, ?>[] oldTable = table;

        int oldCapacity = oldTable.length;

        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;

            return;
        }

        Bucket<K, V>[] newTable = new Bucket[newCapacity];

        BitSet newBits = new BitSet(newCapacity);

        transfer(newTable, newBits);

        table = newTable;
        bits = newBits;

        threshold = (int)(newCapacity * loadFactor);
    }

    /**
     * Transfer all entries from current table to newTable.
     *
     * @param newTable Table to transfer to.
     * @param newBits New bits.
     */
    private void transfer(Bucket<K, V>[] newTable, BitSet newBits) {
        Bucket<K, V>[] src = table;

        int newCapacity = newTable.length;

        for (int j = 0; j < src.length; j++) {
            Bucket<K, V> b = src[j];

            if (b != null) {
                src[j] = null;

                GridCacheMapEntry<K, V> e = b.entry();

                assert e != null;

                do {
                    GridCacheMapEntry<K, V> next = e.next();

                    int i = indexFor(e.hash(), newCapacity);

                    Bucket<K, V> newBucket = newTable[i];

                    if (newBucket == null) {
                        newBucket = new Bucket<K, V>();

                        newTable[i] = newBucket;

                        newBits.set(i);
                    }

                    e.next(newBucket.entry());

                    newBucket.onAdd(e);

                    e = next;
                }
                while (e != null);
            }
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map
     * These mappings will replace any mappings that
     * this map had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @param ttl Time to live.
     * @throws NullPointerException if the specified map is null.
     */
    public void putAll(Map<? extends K, ? extends V> m, long ttl) {
        int numKeysToBeAdded = m.size();

        if (numKeysToBeAdded == 0)
            return;

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourselves
         * to at most one extra resize.
         */
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);

            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;

            int newCapacity = table.length;

            while (newCapacity < targetCapacity)
                newCapacity <<= 1;

            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue(), ttl);
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap. Returns null if the HashMap contains no mapping
     * for this key.
     *
     * @param k Key.
     * @return Removed entry, possibly {@code null}.
     */
    @Nullable public GridCacheMapEntry<K, V> removeEntry(Object k) {
        assert k != null;

        int hash = hash(k.hashCode());

        int i = indexFor(hash, table.length);

        Bucket<K, V> b = table[i];

        GridCacheMapEntry<K, V> prev = b == null ? null : b.entry();

        GridCacheMapEntry<K, V> e = prev;

        while (e != null) {
            GridCacheMapEntry<K, V> next = e.next();

            if (e.hash() == hash && eq(k, e.key())) {
                // Calculate only non internal entry.
                if (!(e.key instanceof GridCacheInternal))
                    pubSize.decrementAndGet();

                size.decrementAndGet();

                if (prev == e) {
                    if (next == null) {
                        table[i] = null;

                        bits.clear(i);

                        bucketCnt--;
                    }
                    else {
                        b.onRemove(next);
                    }
                }
                else {
                    prev.next(next);

                    b.onRemove();
                }

                return e;
            }

            prev = e;

            e = next;
        }

        return e;
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or {@code null}
     *           if there was no mapping for key.  A {@code null} return can
     *           also indicate that the map previously associated {@code null}
     *           with the specified key.
     */
    @Nullable public V remove(Object key) {
        GridCacheMapEntry<K, V> e = removeEntry(key);

        return (e == null ? null : e.rawGet());
    }

    /**
     * Add a new entry with the specified key, value and hash code to
     * the specified bucket.  It is the responsibility of this
     * method to resize the table if appropriate.
     *
     * @param hash Hash.
     * @param key Key.
     * @param val Value.
     * @param ttl Time to live.
     * @param idx Bucket index.
     * @return Added entry.
     */
    private GridCacheMapEntry<K, V> addEntry(int hash, K key, V val, int idx, long ttl) {
        Bucket<K, V> b = table[idx];

        if (b == null) {
            b = new Bucket<K, V>();

            table[idx] = b;

            bits.set(idx);

            bucketCnt++;
        }

        GridCacheMapEntry<K, V> e = factory.create(ctx, key, hash, val, b.entry(), ttl);

        b.onAdd(e);

        // Calculate only non internal entry.
        if (!(key instanceof GridCacheInternal))
            pubSize.getAndIncrement();

        if (size.getAndIncrement() >= threshold)
            resize(2 * table.length);

        return e;
    }

    /**
     * Entry wrapper set.
     *
     * @param filter Filter.
     * @return Entry wrapper set.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public Set<GridCacheEntryImpl<K, V>> wrappers(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return (Set<GridCacheEntryImpl<K, V>>)(Set<? extends GridCacheEntry<K, V>>)entries(filter);
    }

    /**
     * Entry wrapper set casted to projections.
     *
     * @param filter Filter to check.
     * @return Entry projections set.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public Set<GridCacheEntry<K, V>> projections(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return (Set<GridCacheEntry<K, V>>)(Set<? extends GridCacheEntry<K, V>>)wrappers(filter);
    }

    /**
     * Same as {@link #wrappers(GridPredicate[])}
     *
     * @param filter Filter.
     * @return a collection view of the mappings contained in this map.
     */
    @SuppressWarnings({"unchecked"})
    public Set<GridCacheEntry<K, V>> entries(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new EntrySet<K, V>(this, filter);
    }

    /**
     * Internal entry set.
     *
     * @param filter Filter.
     * @return a collection view of the mappings contained in this map.
     */
    public Set<GridCacheEntryEx<K, V>> entries0(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new Set0<K, V>(this, filter);
    }

    /**
     * Key set.
     *
     * @param filter Filter.
     * @return a set view of the keys contained in this map.
     */
    public Set<K> keySet(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new KeySet<K, V>(this, filter);
    }

    /**
     * Collection of non-{@code null} values.
     *
     * @param filter Filter.
     * @return a collection view of the values contained in this map.
     */
    public Collection<V> values(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return F.view(allValues(filter), F.<V>notNull());
    }

    /**
     * Collection of all (possibly {@code null}) values.
     *
     * @param filter Filter.
     * @return a collection view of the values contained in this map.
     */
    public Collection<V> allValues(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new Values<K, V>(this, filter);
    }

    /**
     * Iterator over {@link GridCacheEntryEx} elements.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class Iterator0<K, V> implements Iterator<GridCacheEntryEx<K, V>>, Externalizable {
        /** Next entry to return. */
        private GridCacheMapEntry<K, V> next;

        /** Current slot. */
        private int idx = -1;

        /** Current entry. */
        private GridCacheMapEntry<K, V> cur;

        /** Iterator filter. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

        /** Outer cache map. */
        private GridCacheMap<K, V> map;

        /** Cache context. */
        private GridCacheContext<K, V> ctx;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Iterator0() {
            // No-op.
        }

        /**
         * Creates iterator.
         *
         * @param map Cache map.
         * @param filter Entry filter.
         */
        @SuppressWarnings({"unchecked"})
        Iterator0(GridCacheMap<K, V> map,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            this.filter = filter;

            this.map = map;

            ctx = map.ctx;

            do {
                map.readLock();

                try {
                    if (next != null)
                        next = next.next();

                    if (next == null) {
                        Bucket<K, V>[] t = map.table;

                        int i = idx < 0 ? t.length : idx;

                        Bucket<K, V> b = null;

                        if (map.size.get() != 0)  // Advance to first entry.
                            while (i > 0 && (b = t[--i]) == null) { /* No-op. */ }
                        else
                            break; // While.

                        next = b == null ? null : b.entry();

                        idx = i;
                    }
                }
                finally {
                    map.readUnlock();
                }

                if (next != null) {
                    // Verify outside of read-lock.
                    if (next.visitable(NON_INTERNAL_ARR) && next.visitable(filter))
                        break; // While.
                }
            }
            while (idx > 0);
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return next != null;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public GridCacheEntryEx<K, V> next() {
            GridCacheMapEntry<K, V> e = next;

            if (e == null)
                throw new NoSuchElementException();

            while (idx >= 0) {
                map.readLock();

                try {
                    GridCacheMapEntry<K, V> n = next == null ? null : next.next();

                    Bucket<K, V>[] t = map.table;

                    int i = idx;

                    // What if table shrunk?
                    if (i > t.length)
                        i = t.length;

                    // Skip over obsolete.
                    while (n == null && i >= 0) {
                        i--;

                        if (i >= 0)
                            n = t[i] == null ? null : t[i].entry();
                    }

                    idx = i;

                    next = n;
                }
                finally {
                    map.readUnlock();
                }

                // Verify outside of read-lock.
                if (next != null && (next.visitable(NON_INTERNAL_ARR) && next.visitable(filter)))
                    break; // While.
            }

            cur = e;

            return cur;
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            if (cur == null)
                throw new IllegalStateException();

            GridCacheMapEntry<K, V> e = cur;

            cur = null;

            try {
                ctx.cache().remove(e.key(), CU.<K, V>empty());
            }
            catch (GridException ex) {
                throw new GridRuntimeException(ex);
            }
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(ctx);
            out.writeObject(filter);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            ctx = (GridCacheContext<K, V>)in.readObject();
            filter = (GridPredicate<? super GridCacheEntry<K, V>>[])in.readObject();
        }

        /**
         * Reconstructs object on demarshalling.
         *
         * @return Reconstructed object.
         * @throws ObjectStreamException Thrown in case of demarshalling error.
         */
        protected Object readResolve() throws ObjectStreamException {
            return ctx.cache().map().entries0(filter).iterator();
        }
    }

    /**
     * Iterator over hash table.
     * <p>
     * Note, class is static for {@link Externalizable}.
     */
    private static class EntryIterator<K, V> implements Iterator<GridCacheEntry<K, V>>, Externalizable {
        /** Base iterator. */
        private Iterator0<K, V> it;

        /** */
        private GridCacheContext<K, V> ctx;

        /** */
        private GridCacheProjectionImpl<K, V> prjPerCall;

        /** */
        private GridCacheFlag[] forcedFlags;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public EntryIterator() {
            // No-op.
        }

        /**
         * Creates iterator.
         *
         * @param map Cache map.
         * @param filter Entry filter.
         * @param ctx Cache context.
         * @param prjPerCall Projection per call.
         * @param forcedFlags Forced flags.
         */
        EntryIterator(
            GridCacheMap<K, V> map,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter,
            GridCacheContext<K, V> ctx,
            GridCacheProjectionImpl<K, V> prjPerCall,
            GridCacheFlag[] forcedFlags) {
            it = new Iterator0<K, V>(map, filter);

            this.ctx = ctx;
            this.prjPerCall = prjPerCall;
            this.forcedFlags = forcedFlags;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Override public GridCacheEntry<K, V> next() {
            GridCacheProjectionImpl<K, V> oldPrj = ctx.projectionPerCall();

            ctx.projectionPerCall(prjPerCall);

            GridCacheFlag[] oldFlags = ctx.forceFlags(forcedFlags);

            try {
                return it.next().wrap(true);
            }
            finally {
                ctx.projectionPerCall(oldPrj);
                ctx.forceFlags(oldFlags);
            }
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0<K, V>)in.readObject();
        }
    }

    /**
     * Value iterator.
     * <p>
     * Note that class is static for {@link Externalizable}.
     */
    private static class ValueIterator<K, V> implements Iterator<V>, Externalizable {
        /** Hash table iterator. */
        private Iterator0<K, V> it;

        /** Context. */
        private GridCacheContext<K, V> ctx;

        /** */
        private boolean clone;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public ValueIterator() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Value filter.
         * @param ctx Cache context.
         * @param clone Clone flag.
         */
        private ValueIterator(
            GridCacheMap<K, V> map,
            GridPredicate<? super GridCacheEntry<K, V>>[] filter,
            GridCacheContext<K, V> ctx,
            boolean clone) {
            it = new Iterator0<K, V>(map, filter);

            this.ctx = ctx;
            this.clone = clone;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Nullable @Override public V next() {
            V val = it.next().wrap(true).peek(CU.<K, V>empty());

            try {
                return clone ? ctx.cloneValue(val) : val;
            }
            catch (GridException e) {
                throw new GridRuntimeException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0)in.readObject();
        }
    }

    /**
     * Key iterator.
     */
    private static class KeyIterator<K, V> implements Iterator<K>, Externalizable {
        /** Hash table iterator. */
        private Iterator0<K, V> it;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public KeyIterator() {
            // No-op.
        }

        /**
         * @param map Cache context.
         * @param filter Filter.
         */
        private KeyIterator(GridCacheMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            it = new Iterator0<K, V>(map, filter);
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return it.hasNext();
        }

        /** {@inheritDoc} */
        @Override public K next() {
            return it.next().key();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            it.remove();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(it);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            it = (Iterator0)in.readObject();
        }
    }

    /**
     * Key set.
     */
    private static class KeySet<K, V> extends AbstractSet<K> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public KeySet() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Key filter.
         */
        private KeySet(GridCacheMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, filter);
        }

        /** {@inheritDoc} */
        @Override public Iterator<K> iterator() {
            return set.keyIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return set.containsKey((K)o);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean remove(Object o) {
            return set.removeKey((K)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }

    /**
     * Value set.
     * <p>
     * Note that the set is static for {@link Externalizable} support.
     */
    private static class Values<K, V> extends AbstractCollection<V> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Values() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Value filter.
         */
        private Values(GridCacheMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, filter);
        }

        /** {@inheritDoc} */
        @Override public Iterator<V> iterator() {
            return set.valueIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return set.containsValue((V)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }

    /**
     * Entry set.
     */
    private static class EntrySet<K, V> extends AbstractSet<GridCacheEntry<K, V>> implements Externalizable {
        /** Base entry set. */
        private Set0<K, V> set;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public EntrySet() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Key filter.
         */
        private EntrySet(GridCacheMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            set = new Set0<K, V>(map, filter);
        }

        /** {@inheritDoc} */
        @Override public Iterator<GridCacheEntry<K, V>> iterator() {
            return set.entryIterator();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return set.size();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean contains(Object o) {
            return o instanceof GridCacheEntryImpl && set.contains(((GridCacheEntryImpl<K, V>)o).unwrap());
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean remove(Object o) {
            return set.removeKey((K)o);
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            set.clear();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(set);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            set = (Set0<K, V>)in.readObject();
        }
    }

    /**
     * Entry set.
     */
    @SuppressWarnings("unchecked")
    private static class Set0<K, V> extends AbstractSet<GridCacheEntryEx<K, V>> implements Externalizable {
        /** Filter. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] filter;

        /** Base map. */
        private GridCacheMap<K, V> map;

        /** Context. */
        private GridCacheContext<K, V> ctx;

        /** */
        private GridCacheProjectionImpl prjPerCall;

        /** */
        private GridCacheFlag[] forcedFlags;

        /** */
        private boolean clone;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public Set0() {
            // No-op.
        }

        /**
         * @param map Base map.
         * @param filter Filter.
         */
        private Set0(GridCacheMap<K, V> map, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
            assert map != null;

            this.map = map;
            this.filter = filter;

            ctx = map.ctx;

            prjPerCall = ctx.projectionPerCall();
            forcedFlags = ctx.forcedFlags();
            clone = ctx.hasFlag(CLONE);
        }

        /** {@inheritDoc} */
        @Override public boolean isEmpty() {
            return !iterator().hasNext();
        }

        /** {@inheritDoc} */
        @Override public Iterator<GridCacheEntryEx<K, V>> iterator() {
            return new Iterator0<K, V>(map, filter);
        }

        /**
         * @return Entry iterator.
         */
        Iterator<GridCacheEntry<K, V>> entryIterator() {
            return new EntryIterator<K, V>(map, filter, ctx, prjPerCall, forcedFlags);
        }

        /**
         * @return Key iterator.
         */
        Iterator<K> keyIterator() {
            return new KeyIterator<K, V>(map, filter);
        }

        /**
         * @return Value iterator.
         */
        Iterator<V> valueIterator() {
            return new ValueIterator<K, V>(map, filter, ctx, clone);
        }

        /**
         * Checks for key containment.
         *
         * @param k Key to check.
         * @return {@code True} if key is in the map.
         */
        boolean containsKey(K k) {
            GridCacheEntryEx<K, V> e = ctx.cache().peekEx(k);

            return e != null && !e.obsolete() && F.isAll(e.wrap(false), filter);
        }

        /**
         * @param v Checks if value is contained in
         * @return {@code True} if value is in the set.
         */
        boolean containsValue(V v) {
            A.notNull(v, "value");

            if (v == null)
                return false;

            for (Iterator<V> it = valueIterator(); it.hasNext(); ) {
                V v0 = it.next();

                if (F.eq(v0, v))
                    return true;
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public boolean contains(Object o) {
            if (!(o instanceof GridCacheEntryEx))
                return false;

            GridCacheEntryEx<K, V> e = (GridCacheEntryEx<K, V>)o;

            GridCacheEntryEx<K, V> cur = ctx.cache().peekEx(e.key());

            return cur != null && cur.equals(e);
        }

        /** {@inheritDoc} */
        @Override public boolean remove(Object o) {
            return o instanceof GridCacheEntry && removeKey(((Map.Entry<K, V>)o).getKey());
        }

        /**
         * @param k Key to remove.
         * @return If key has been removed.
         */
        boolean removeKey(K k) {
            try {
                return ctx.cache().remove(k, CU.<K, V>empty()) != null;
            }
            catch (GridException e) {
                throw new GridRuntimeException("Failed to remove cache entry for key: " + k, e);
            }
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return F.isEmpty(filter) ? map.publicSize() : F.size(iterator());
        }

        /** {@inheritDoc} */
        @Override public void clear() {
            ctx.cache().clearAll(new KeySet<K, V>(map, filter));
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(ctx);
            out.writeObject(filter);
        }

        /** {@inheritDoc} */
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            ctx = (GridCacheContext<K, V>)in.readObject();
            filter = (GridPredicate<? super GridCacheEntry<K, V>>[])in.readObject();

            map = ctx.cache().map();
        }
    }

    /**
     * Hash bucket.
     */
    private static class Bucket<K, V> {
        /** */
        private int cnt;

        /** */
        private GridCacheMapEntry<K, V> entry;

        /**
         * @return Bucket count.
         */
        int count() {
            return cnt;
        }

        /**
         * @return Entry.
         */
        GridCacheMapEntry<K, V> entry() {
            return entry;
        }

        /**
         * @param entry New root.
         * @return New count.
         */
        int onAdd(GridCacheMapEntry<K, V> entry) {
            this.entry = entry;

            return ++cnt;
        }

        /**
         * @param entry New root.
         * @return New count.
         */
        int onRemove(GridCacheMapEntry<K, V> entry) {
            this.entry = entry;

            return --cnt;
        }

        /**
         * @return New count.
         */
        int onRemove() {
            return --cnt;
        }
    }
}
