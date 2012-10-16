// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Convenient adapter for {@link GridMetadataAware}.
 * <h2 class="header">Thread Safety</h2>
 * This class provides necessary synchronization for thread-safe access.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings( {"SynchronizeOnNonFinalField"})
public class GridMetadataAwareAdapter implements GridMetadataAware, Cloneable {
    /** Attributes. */
    @GridToStringInclude
    private GridLeanMap<String, Object> data;

    /** Serializable mutex. */
    @SuppressWarnings( {"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridMutex mux;

    /**
     * Default constructor.
     */
    public GridMetadataAwareAdapter() {
        mux = new GridMutex();
    }

    /**
     * Creates adapter with predefined data.
     *
     * @param data Data to copy.
     */
    public GridMetadataAwareAdapter(Map<String, Object> data) {
        mux = new GridMutex();

        if (data != null && !data.isEmpty())
            this.data = new GridLeanMap<String, Object>(data);
    }

    /**
     * Ensures that internal data storage is created.
     *
     * @return {@code true} if data storage was created.
     */
    private boolean ensureData() {
        assert Thread.holdsLock(mux);

        if (data == null) {
            data = new GridLeanMap<String, Object>();

            return true;
        }
        else
            return false;
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        A.notNull(from, "from");

        synchronized (mux) {
            ensureData();

            data.putAll(from.allMeta());
        }
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        A.notNull(data, "data");

        synchronized (mux) {
            ensureData();

            this.data.putAll(data);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override @Nullable public <V> V addMeta(String name, V val) {
        A.notNull(name, "name", val, "val");

        synchronized (mux) {
            ensureData();

            return (V)data.put(name, val);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable
    @Override public <V> V meta(String name) {
        A.notNull(name, "name");

        synchronized (mux) {
            return data == null ? null : (V)data.get(name);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable
    @Override public <V> V removeMeta(String name) {
        A.notNull(name, "name");

        synchronized (mux) {
            if (data == null)
                return null;

            V old = (V)data.remove(name);

            if (data.isEmpty())
                data = null;

            return old;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> boolean removeMeta(String name, V val) {
        A.notNull(name, "name", val, "val");

        synchronized (mux) {
            if (data == null)
                return false;

            V old = (V)data.get(name);

            if (old != null && old.equals(val)) {
                data.remove(name);

                return true;
            }

            return false;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> Map<String, V> allMeta() {
        synchronized (mux) {
            if (data == null)
                return Collections.emptyMap();

            if (data.size() <= 5)
                // This is a singleton unmodifiable map.
                return (Map<String, V>)data;

            // Return a copy.
            return new HashMap<String, V>((Map<String, V>) data);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return meta(name) != null;
    }

    /** {@inheritDoc} */
    @Override public <V> boolean hasMeta(String name, V val) {
        A.notNull(name, "name");

        Object v = meta(name);

        return v != null && v.equals(val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override @Nullable public <V> V putMetaIfAbsent(String name, V val) {
        A.notNull(name, "name", val, "val");

        synchronized (mux) {
            V v = (V) meta(name);

            if (v == null)
                return addMeta(name, val);

            return v;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "ClassReferencesSubclass"})
    @Override @Nullable public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        A.notNull(name, "name", c, "c");

        synchronized (mux) {
            V v = (V) meta(name);

            if (v == null)
                try {
                    return addMeta(name, c.call());
                }
                catch (Exception e) {
                    throw F.wrap(e);
                }

            return v;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        A.notNull(name, "name", val, "val");

        synchronized (mux) {
            V v = (V) meta(name);

            if (v == null)
                addMeta(name, v = val);

            return v;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public <V> V addMetaIfAbsent(String name, @Nullable Callable<V> c) {
        A.notNull(name, "name", c, "c");

        synchronized (mux) {
            V v = (V) meta(name);

            if (v == null && c != null)
                try {
                    addMeta(name, v = c.call());
                }
                catch (Exception e) {
                    throw F.wrap(e);
                }

            return v;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        A.notNull(name, "name", newVal, "newVal", curVal, "curVal");

        synchronized (mux) {
            if (hasMeta(name)) {
                V val = this.<V>meta(name);

                if (val != null && val.equals(curVal)) {
                    addMeta(name, newVal);

                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Convenience way for super-classes which implement {@link Externalizable} to
     * serialize metadata. Super-classes must call this method explicitly from
     * within {@link Externalizable#writeExternal(ObjectOutput)} methods implementation.
     *
     * @param out Output to write to.
     * @throws IOException If I/O error occurred.
     */
    protected void writeExternalMeta(ObjectOutput out) throws IOException {
        Map<String, Object> cp;

        // Avoid code warning (suppressing is bad here, because we need this warning for other places).
        synchronized (mux) {
            cp = data;
        }

        out.writeObject(cp);
    }

    /**
     * Convenience way for super-classes which implement {@link Externalizable} to
     * serialize metadata. Super-classes must call this method explicitly from
     * within {@link Externalizable#readExternal(ObjectInput)} methods implementation.
     *
     * @param in Input to read from.
     * @throws IOException If I/O error occurred.
     * @throws ClassNotFoundException If some class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    protected void readExternalMeta(ObjectInput in) throws IOException, ClassNotFoundException {
        GridLeanMap<String, Object> cp = (GridLeanMap<String, Object>)in.readObject();

        synchronized (mux) {
            data = cp;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "OverriddenMethodCallDuringObjectConstruction"})
    @Override public Object clone() {
        try {
            GridMetadataAwareAdapter clone = (GridMetadataAwareAdapter)super.clone();

            clone.mux = (GridMutex)mux.clone();

            clone.data = null;

            clone.copyMeta(this);

            return clone;
        }
        catch (CloneNotSupportedException ignore) {
            throw new InternalError();
        }
    }
}
