// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import junit.framework.*;
import org.gridgain.grid.*;
import org.junit.runners.model.*;
import java.util.*;

/**
 * JUnit suite runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit38SuiteRunner extends GridJunit4SuiteRunner {
    /** */
    private transient TestSuite suite;

    /** */
    private final String name;

    /**
     * Constructor required by JUnit4.
     *
     * @param cls Suite class.
     */
    GridJunit38SuiteRunner(Class<?> cls) {
        super(cls);

        name = cls.getName();
    }

    /**
     * @param suite Test suite.
     */
    GridJunit38SuiteRunner(TestSuite suite) {
        super(suite.getClass());

        name = suite.getName();

        this.suite = suite;
    }

    /**
     * @return Test suite.
     * @throws InitializationError TODO
     */
    TestSuite getSuite() throws InitializationError {
        if (suite == null) {
            suite = GridJunit4Utils.createJunit3Suite(name, getTestClass());
        }

        return suite;
    }

    /** {@inheritDoc} */
    @Override protected List<GridJunit4Runner> createChildren() {
        List<GridJunit4Runner> children = new ArrayList<GridJunit4Runner>();

        try {
            TestSuite suite = getSuite();

            for (int i = 0; i < suite.testCount(); i++) {
                Test test = suite.testAt(i);

                if (test instanceof TestSuite) {
                    children.add(new GridJunit38SuiteRunner((TestSuite)test));
                }
                else if (test instanceof TestCase) {
                    children.add(new GridJunit38ClassRunner((TestCase)test));
                }
                else {
                    throw new GridRuntimeException("Unsupported test class: " + test);
                }
            }
        }
        catch (InitializationError e) {
            throw new GridRuntimeException("Failed to create suite children.", e);
        }

        return children;
    }
}
