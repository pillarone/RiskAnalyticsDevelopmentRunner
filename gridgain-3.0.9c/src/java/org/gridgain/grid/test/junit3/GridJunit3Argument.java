// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import org.gridgain.grid.test.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * JUnit3 task argument.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3Argument {
    /** */
    private final GridTestRouter router;

    /** */
    private final GridJunit3SerializableTest test;

    /** */
    private final boolean local;

    /**
     * @param router JUnit router.
     * @param test JUnit3 test.
     * @param local Local flag.
     */
    GridJunit3Argument(GridTestRouter router, GridJunit3SerializableTest test, boolean local) {
        assert router != null;
        assert test != null;

        this.router = router;
        this.test = test;
        this.local = local;
    }

    /**
     * @return Test router.
     */
    GridTestRouter getRouter() {
        return router;
    }

    /**
     * @return Serializable test.
     */
    GridJunit3SerializableTest getTest() {
        return test;
    }

    /**
     * @return {@code True} if test is local.
     */
    public boolean isLocal() {
        return local;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJunit3Argument.class, this);
    }
}
