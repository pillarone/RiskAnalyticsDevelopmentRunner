// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3TestSuiteProxy extends TestSuite {
    /** */
    private final GridJunit3ProxyFactory factory;

    /** */
    private final TestSuite original;

    /**
     * @param suite Test suite to wrap.
     * @param factory Proxy factory.
     */
    GridJunit3TestSuiteProxy(TestSuite suite, GridJunit3ProxyFactory factory) {
        assert suite != null;
        assert factory != null;

        original = suite;

        setName(suite.getName());

        this.factory = factory;

        for (int i = 0; i < suite.testCount(); i++) {
            addTest(suite.testAt(i));
        }
    }

    /**
     * @return Original test suite.
     */
    TestSuite getOriginal() {
        return original;
    }

    /** {@inheritDoc} */
    @Override public void addTest(Test test) {
        if (test instanceof GridJunit3TestSuiteProxy || test instanceof GridJunit3TestCaseProxy) {
            super.addTest(test);
        }
        else if (test instanceof TestSuite) {
            super.addTest(new GridJunit3TestSuiteProxy((TestSuite)test, factory));
        }
        else {
            assert test instanceof TestCase : "Test must be either instance of TestSuite or TestCase: " + test;

            super.addTest(factory.createProxy((TestCase)test));
        }
    }
}
