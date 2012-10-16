// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import java.util.*;

/**
 * Defines "rich" iterable interface that is also acts as lambda function and iterator.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridIterator
 */
public interface GridIterable<T> extends GridIterator<T> {
    /**
     * Returns {@link GridIterator} which extends regular {@link Iterator} interface
     * and adds methods that account for possible failures in cases when iterating
     * over data that has been partially received over network.
     *
     * @return Instance of new {@link GridIterator}.
     */
    @Override public GridIterator<T> iterator();
}
