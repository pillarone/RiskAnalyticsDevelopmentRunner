// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

/**
 * Set counterpart for {@link IdentityHashMap}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIdentityHashSet<E> extends GridSetWrapper<E> {
    /**
     * Creates default identity hash set.
     */
    public GridIdentityHashSet() {
        super(new IdentityHashMap<E, Object>());
    }

    /**
     * Creates identity hash set of given size.
     *
     * @param size Start size for the set.
     */
    public GridIdentityHashSet(int size) {
        super(new IdentityHashMap<E, Object>(size));

        A.ensure(size >= 0, "size >= 0");
    }

    /**
     * Creates identity has set initialized given collection.
     *
     * @param vals Values to initialize.
     */
    public GridIdentityHashSet(Collection<E> vals) {
        super(F.isEmpty(vals) ? new IdentityHashMap<E, Object>(0) : new IdentityHashMap<E, Object>(vals.size()), vals);
    }
}
