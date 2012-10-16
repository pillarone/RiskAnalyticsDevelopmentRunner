// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.executor;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * This class defines own implementation for {@link GridTask}. This class used by
 * {@link GridExecutorService} when commands submitted and can be
 * randomly assigned to available grid nodes. This grid task creates only one
 * {@link GridJob} and transfer it to any available node. See {@link GridTaskSplitAdapter}
 * for more details.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"TransientFieldNotInitialized"})
public class GridExecutorRunnableTask extends GridTaskAdapter<Runnable, Object> {
    /** Deploy class. */
    private final transient Class<?> p2pCls;

    /** Load balancer. */
    @GridLoadBalancerResource
    private GridLoadBalancer balancer;

    /**
     * Creates runnable task with given deployment class.
     *
     * @param cls Deployment class for peer-deployment.
     */
    public GridExecutorRunnableTask(Class<?> cls) {
        assert cls != null;

        p2pCls = cls;
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        return p2pCls;
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        return p2pCls.getClassLoader();
    }

    /** {@inheritDoc} */
    @Override public final Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, Runnable arg) throws GridException {
        assert subgrid != null;
        assert !subgrid.isEmpty();

        GridJob job = F.job(F.as(arg));

        return Collections.singletonMap(job, balancer.getBalancedNode(job, null));
    }

    /** {@inheritDoc} */
    @Override public Object reduce(List<GridJobResult> results) throws GridException {
        assert results != null;
        assert results.size() == 1;

        return results.get(0).getData();
    }
}
