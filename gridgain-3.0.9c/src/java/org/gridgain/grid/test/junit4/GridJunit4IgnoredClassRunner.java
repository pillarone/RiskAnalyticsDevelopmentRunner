// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.util.*;

/**
 * JUnit 4 ignored class runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4IgnoredClassRunner extends GridJunit4Runner {
    /** */
    private final Class<?> cls;

    /**
     * @param testClass Test class.
     */
    GridJunit4IgnoredClassRunner(Class<?> testClass) {
        cls = testClass;
    }

    /** {@inheritDoc} */
    @Override public void run(RunNotifier notifier) {
        notifier.fireTestIgnored(getDescription());
    }

    /** {@inheritDoc} */
    @Override public Description getDescription() {
        return Description.createSuiteDescription(cls);
    }

    /** {@inheritDoc} */
    @Override Class<?> getTestClass() {
        return cls;
    }

    /** {@inheritDoc} */
    @Override void copyResults(GridJunit4Runner runner) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override boolean setResult(GridJunit4Result res) {
        // No-op.
        return false;
    }

    /** {@inheritDoc} */
    @Override boolean setResult(List<GridJunit4Result> res) {
        for (GridJunit4Result result : res) {
            if (!setResult(result)) {
                return false;
            }
        }

        return true;
    }
}
