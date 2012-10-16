// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This is a faster performing version of {@link UUID}. On basic tests this version is at least
 * 10x time faster for ID creation. It uses extra memory for 8-byte counter additionally to
 * internal UUID.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridUuid extends GridOutClosure<GridUuid> implements Comparable<GridUuid>,
    Iterable<GridUuid> {
    /** */
    private static final UUID ID = UUID.randomUUID();

    /** */
    private static final AtomicLong cntGen = new AtomicLong(System.currentTimeMillis());

    /** */
    private final UUID gid;

    /** */
    private final long locId;

    /**
     * Creates new pseudo-random ID.
     *
     * @return Newly created pseudo-random ID.
     */
    public static GridUuid randomUuid() {
        return new GridUuid(ID, cntGen.getAndIncrement());
    }

    /**
     * Constructs new {@code GridUuid} based on global and local ID portions.
     *
     * @param id UUID instance.
     * @return Newly created pseudo-random ID.
     */
    public static GridUuid fromUuid(UUID id) {
        A.notNull(id, "id");

        return new GridUuid(id, cntGen.getAndIncrement());
    }

    /**
     * Constructs {@code GridUuid} from a global and local identifiers.
     * 
     * @param gid UUID.
     * @param locId Counter.
     */
    public GridUuid(UUID gid, long locId) {
        assert gid != null;

        this.gid = gid;
        this.locId = locId;
    }

    /**
     * Gets global ID portion of this {@code GridUuid}.
     *
     * @return Global ID portion of this {@code GridUuid}.
     */
    public UUID globalId() {
        return gid;
    }

    /**
     * Gets local ID portion of this {@code GridUuid}.
     *
     * @return Local ID portion of this {@code GridUuid}.
     */
    public long localId() {
        return locId;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridUuid o) {
        if (o == this) {
            return 0;
        }

        if (o == null) {
            return 1;
        }

        return locId < o.locId ? -1 : locId > o.locId ? 1 : gid.compareTo(o.globalId());
    }

    /** {@inheritDoc} */
    @Override public GridUuid apply() {
        return this;
    }

    /** {@inheritDoc} */
    @Override public GridIterator<GridUuid> iterator() {
        return F.iterator(Collections.singleton(this), F.<GridUuid>identity(), true);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof GridUuid)) {
            return false;
        }

        GridUuid that = (GridUuid)obj;

        return that.locId == locId && that.gid.equals(gid);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return (int)locId % Integer.MAX_VALUE;
    }

    /** {@inheritDoc} */
    @Override public Object clone() {
        return super.clone(); 
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUuid.class, this);
    }
}
