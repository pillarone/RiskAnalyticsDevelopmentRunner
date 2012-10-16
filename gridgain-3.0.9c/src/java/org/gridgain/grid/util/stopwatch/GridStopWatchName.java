// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.stopwatch;

import org.gridgain.grid.typedef.internal.*;

/**
 * Stop watch or step name.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridStopWatchName implements Comparable<GridStopWatchName> {
    /** ID. */
    private final int id;

    /** Name. */
    private final String name;

    /**
     * @param id ID.
     * @param name Name.
     */
    GridStopWatchName(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * @return ID.
     */
    int id() {
        return id;
    }

    /**
     * @return Name.
     */
    String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        GridStopWatchName other = (GridStopWatchName)o;

        return id == other.id;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridStopWatchName o) {
        return id < o.id ? -1 : id == o.id ? 0 : 1;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridStopWatchName.class, this);
    }
}
