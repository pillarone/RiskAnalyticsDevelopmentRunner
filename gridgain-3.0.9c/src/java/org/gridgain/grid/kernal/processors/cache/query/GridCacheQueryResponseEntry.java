// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Class to store query results returned by remote nodes. It's required to fully
 * control serialization process. Local entries can be returned to user as is.
 */
public class GridCacheQueryResponseEntry<K, V> implements Map.Entry<K, V>, Externalizable {
    /** */
    @GridToStringInclude
    private K key;

    /** */
    @GridToStringExclude
    private byte[] keyBytes;

    /** */
    @GridToStringInclude
    private V val;

    /** */
    @GridToStringExclude
    private byte[] valBytes;

    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheQueryResponseEntry() {
        // No-op.
    }

    /**
     * @param key Key.
     * @param val Value.
     */
    public GridCacheQueryResponseEntry(K key, V val) {
        this.key = key;
        this.val = val;
    }

    /** @return Key. */
    @Override public K getKey() {
        return key;
    }

    /**
     * @param key Key.
     */
    public void setKey(K key) {
        this.key = key;
    }

    /**
     * @return Key bytes.
     */
    public byte[] keyBytes() {
        return keyBytes;
    }

    /**
     * @param keyBytes Key bytes.
     */
    public void keyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    /**
     * @return Value.
     */
    @Override public V getValue() {
        return val;
    }

    /**
     * @param val Value
     */
    @Override public V setValue(V val) {
        this.val = val;

        return val;
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        return valBytes;
    }

    /**
     * @param valBytes Value bytes.
     */
    public void valueBytes(byte[] valBytes) {
        this.valBytes = valBytes;
    }

    /**
     * Unmarshal key if it is empty and there are data in key bytes.
     *
     * @param marshaller Marshaller to use.
     * @param clsLdr Classloader to use.
     * @throws GridException in case of error.
     */
    @SuppressWarnings("unchecked")
    public void unmarshal(GridMarshaller marshaller, ClassLoader clsLdr) throws GridException {
        if (keyBytes != null && key == null)
            key = (K)U.unmarshal(marshaller, new GridByteArrayList(keyBytes), clsLdr);

        if (valBytes != null && val == null)
            val = (V)U.unmarshal(marshaller, new GridByteArrayList(valBytes), clsLdr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        if (keyBytes != null) {
            // Key.
            out.writeObject(null);

            U.writeByteArray(out, keyBytes);
        }
        else {
            out.writeObject(key);

            U.writeByteArray(out, null);
        }

        if (valBytes != null) {
            // Value.
            out.writeObject(null);

            U.writeByteArray(out, valBytes);
        }
        else {
            out.writeObject(val);

            U.writeByteArray(out, null);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        key = (K)in.readObject();

        keyBytes = U.readByteArray(in);

        val = (V)in.readObject();

        valBytes = U.readByteArray(in);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        GridCacheQueryResponseEntry entry = (GridCacheQueryResponseEntry)o;

        return key.equals(entry.key);

    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return key.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "[" + key + "=" + val + "]";
    }
}
