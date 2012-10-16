// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.test.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * JUnit 4 task argument.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4Argument {
    /** */
    private final GridTestRouter router;

    /** */
    private final GridJunit4Runner runner;

    /** */
    private final boolean local;

    /**
     * @param router JUnit router.
     * @param runner JUnit4 runner.
     * @param local Local flag.
     */
    GridJunit4Argument(GridTestRouter router, GridJunit4Runner runner, boolean local) {
        assert router != null;
        assert runner != null;

        this.router = router;
        this.runner = runner;
        this.local = local;
    }

    /**
     * @return Test router.
     */
    GridTestRouter getRouter() {
        return router;
    }

    /**
     * @return Serializable runner.
     */
    GridJunit4Runner getRunner() {
        return runner;
    }

    /**
     * @return {@code True} if runner to be executed locally.
     */
    public boolean isLocal() {
        return local;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJunit4Argument.class, this);
    }
}
