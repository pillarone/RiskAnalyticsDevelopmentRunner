// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;

import java.io.*;
import java.util.*;

/**
 * Grid unique version.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheVersion implements Comparable<GridCacheVersion>, Externalizable {
    /** Order. */
    private long order;

    /** Version ID. */
    private UUID id;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheVersion() {
        /* No-op. */
    }

    /**
     *
     * @param order Version order.
     * @param id Version ID.
     */
    public GridCacheVersion(long order, UUID id) {
        assert id != null;

        this.order = order;
        this.id = id;
    }

    /**
     * @return Version order.
     */
    public long order() {
        return order;
    }

    /**
     * @return Version ID.
     */
    public UUID id() {
        return id;
    }

    /**
     * @param ver Version.
     * @return {@code True} if this version is greater.
     */
    public boolean isGreater(GridCacheVersion ver) {
        return compareTo(ver) > 0;
    }

    /**
     * @param ver Version.
     * @return {@code True} if this version is greater or equal.
     */
    public boolean isGreaterEqual(GridCacheVersion ver) {
        return compareTo(ver) >= 0;
    }

    /**
     * @param ver Version.
     * @return {@code True} if this version is less.
     */
    public boolean isLess(GridCacheVersion ver) {
        return compareTo(ver) < 0;
    }

    /**
     * @param ver Version.
     * @return {@code True} if this version is less or equal.
     */
    public boolean isLessEqual(GridCacheVersion ver) {
        return compareTo(ver) <= 0;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(order);

        GridUtils.writeUuid(out, id);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        order = in.readLong();

        id = GridUtils.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        GridCacheVersion other = (GridCacheVersion)obj;

        return order == other.order && id.equals(other.id);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridCacheVersion other) {
        return order < other.order ? -1 : order > other.order ? 1 : id.compareTo(other.id);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheVersion.class, this);
    }
}