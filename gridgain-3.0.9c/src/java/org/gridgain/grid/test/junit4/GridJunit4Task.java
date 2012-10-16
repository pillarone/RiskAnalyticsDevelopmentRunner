// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.*;
import org.gridgain.grid.resources.*;
import java.util.*;

/**
 * JUnit4 task.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
/*@hide.from.javadoc*/public class GridJunit4Task extends GridTaskAdapter<GridJunit4Argument, Object> {
    /** Deploy class. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient Class<?> cls;

    /** Deploy class loader. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient ClassLoader clsLdr;

    /** Injected local node ID. */
    @GridLocalNodeIdResource
    private UUID locNodeId;

    /** Grid instance. */
    @GridInstanceResource
    private Grid grid;

    /**
     * Creates JUnit 4 task with given deployment information.
     *
     * @param cls Deployment class.
     * @param clsLdr Class loader.
     */
    public GridJunit4Task(Class<?> cls, ClassLoader clsLdr) {
        assert cls != null;
        assert clsLdr != null;

        this.cls = cls;
        this.clsLdr = clsLdr;
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        return cls;
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        return clsLdr;
    }

    /** {@inheritDoc} */
    @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, GridJunit4Argument arg) {
        GridJunit4Runner runner = arg.getRunner();

        if (arg.isLocal()) {
            return Collections.<GridJob, GridNode>singletonMap(new GridJunit4Job(runner), grid.localNode());
        }

        return Collections.singletonMap(new GridJunit4Job(runner), arg.getRouter().route(runner.getTestClass(),
            runner.getDescription().getDisplayName(), subgrid, locNodeId));
    }

    /** {@inheritDoc} */
    @Override public Object reduce(List<GridJobResult> results) throws GridException {
        assert results.size() == 1;

        GridJobResult res = results.get(0);

        if (res.getException() != null) {
            throw res.getException();
        }

        return res.getData();
    }
}
