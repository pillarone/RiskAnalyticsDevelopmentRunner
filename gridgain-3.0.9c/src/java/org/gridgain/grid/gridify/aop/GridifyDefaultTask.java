// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.gridify.aop;

import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.gridify.*;
import java.util.*;

/**
 * Default gridify task which simply executes a method on remote node.
 * <p>
 * See {@link Gridify} documentation for more information about execution of
 * {@code gridified} methods.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see Gridify
 */
public class GridifyDefaultTask extends GridTaskAdapter<GridifyArgument, Object> {
    /** Deploy class. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient Class<?> p2pCls;

    /** Class loader. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient ClassLoader clsLdr;

    /** Grid instance. */
    @GridInstanceResource
    private Grid grid;

    /** Load balancer. */
    @GridLoadBalancerResource
    private GridLoadBalancer balancer;

    /**
     * Creates gridify default task with given deployment class.
     *
     * @param cls Deployment class for peer-deployment.
     */
    public GridifyDefaultTask(Class<?> cls) {
        assert cls != null;

        p2pCls = cls;

        clsLdr = U.detectClassLoader(cls);
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        return p2pCls;
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        return clsLdr;
    }

    /**
     * {@inheritDoc}
     */
    @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, GridifyArgument arg) throws GridException {
        assert !subgrid.isEmpty() : "Subgrid should not be empty: " + subgrid;
        
        assert grid != null : "Grid instance could not be injected";
        assert balancer != null : "Load balancer could not be injected";

        GridJob job = new GridifyJobAdapter(arg);

        GridNode node = balancer.getBalancedNode(job, Collections.<GridNode>singletonList(grid.localNode()));

        if (node != null) {
            // Give preference to remote nodes.
            return Collections.singletonMap(job, node);
        }

        return Collections.singletonMap(job, balancer.getBalancedNode(job, null));
    }

    /** {@inheritDoc} */
    @Override public final Object reduce(List<GridJobResult> results) throws GridException {
        assert results.size() == 1;

        GridJobResult res = results.get(0);

        if (res.getException() != null) {
            throw res.getException();
        }

        return res.getData();
    }
}
