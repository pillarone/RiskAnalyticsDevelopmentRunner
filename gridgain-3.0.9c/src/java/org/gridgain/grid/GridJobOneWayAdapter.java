// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.util.*;

/**
 * Utility adapter for jobs returning no value.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridJobOneWayAdapter extends GridJobAdapterEx {
    /** {@inheritDoc} */
    @Override public final Object execute() throws GridException {
        oneWay();

        return null;
    }

    /**
     * This method is called by {@link #execute()} and allows to avoid manual {@code return null}.
     *
     * @throws GridException If job execution caused an exception. This exception will be
     *      returned in {@link GridJobResult#getException()} method passed into
     *      {@link GridTask#result(GridJobResult, List)} method into task on caller node.
     *      If execution produces a {@link RuntimeException} or {@link Error}, then
     *      it will be wrapped into {@link GridException}.
     */
    protected abstract void oneWay() throws GridException;
}
