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
 * Adapter for {@link GridTaskAdapter}
 * overriding {@code reduce(...)} method to return {@code null}. This adapter should
 * be used for tasks that don't return any value.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <T> Type of the task argument.
 */
public abstract class GridTaskNoReduceAdapter<T> extends GridTaskAdapter<T, Void> {
    /** Empty constructor. */
    protected GridTaskNoReduceAdapter() {
        // No-op.
    }

    /**
     * Constructor that receives deployment information for task.
     *
     * @param p Deployment information.
     */
    protected GridTaskNoReduceAdapter(GridPeerDeployAware p) {
        super(p);
    }

    /** {@inheritDoc} */
    @Override public Void reduce(List<GridJobResult> results) throws GridException {
        return null;
    }
}
