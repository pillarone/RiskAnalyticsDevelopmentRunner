// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;
import java.util.*;

/**
 * Defines functional mapping interface. It assumes two types, set of values
 * of one type and a function that converts values of one type to another using
 * given set of values.
 * <p>
 * In GridGain it is primarily used as a mapping routine between closures and grid nodes
 * in {@link Grid#mapreduce(GridMapper, Collection, GridReducer, GridPredicate[])} method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridMapper<T1, T2> extends GridClosure<T1, T2> {
    /**
     * Collects values to be used by this mapper. This interface doesn't define how
     * many times this method can be called. This method, however, should be called
     * before {@link #apply(Object)} method.
     * <p>
     * Note that if this method is called this mapper can be treated as standard
     * closure. Note also that {@link #curry(Object)} currying the mapper will effectively
     * make it a constant mapper. 
     * 
     * @param vals Values to use during mapping.
     */
    public abstract void collect(Collection<T2> vals);
}
