// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.cache.cloner;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * Basic cache cloner based on utilization of {@link Cloneable} interface. If
 * a passed in object implements {@link Cloneable} then its implementation of
 * {@link Object#clone()} is used to get a copy. Otherwise, the object itself
 * will be returned without cloning.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheBasicCloner implements GridCacheCloner {
    /** {@inheritDoc} */
    @Override public <T> T cloneValue(T val) throws GridException {
        return X.cloneObject(val, false, true);
    }
}
