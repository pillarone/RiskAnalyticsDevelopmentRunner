// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * JUnit 3 serializable test suite.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3SerializableTestSuite implements GridJunit3SerializableTest {
    /** */
    private final String name;

    /** */
    private final Class<? extends Test> testCls;

    /** */
    private List<GridJunit3SerializableTest> tests = new ArrayList<GridJunit3SerializableTest>();

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient TestSuite suite;

    /**
     * @param suite Test suite.
     */
    GridJunit3SerializableTestSuite(TestSuite suite) {
        assert suite != null;

        this.suite = suite;

        testCls = ((GridJunit3TestSuiteProxy)suite).getOriginal().getClass();

        name = suite.getName();

        for (Enumeration<Test> e = suite.tests(); e.hasMoreElements();) {
            Test t = e.nextElement();

            if (t instanceof TestCase) {
                tests.add(new GridJunit3SerializableTestCase((TestCase)t));
            }
            else {
                assert t instanceof TestSuite : "Test is not instance of TestCase or TestSuite: " + t;

                tests.add(new GridJunit3SerializableTestSuite((TestSuite)t));
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public Class<? extends Test> getTestClass() {
        return testCls;
    }

    /** {@inheritDoc} */
    @Override public Test getTest() {
        // Initialize suite after deserialization.
        if (suite == null) {
            suite = new TestSuite(name);

            for (GridJunit3SerializableTest test : tests) {
                suite.addTest(test.getTest());
            }
        }

        return suite;
    }

    /** {@inheritDoc} */
    @Override public GridJunit3SerializableTestCase findTestCase(TestCase t) {
        for (GridJunit3SerializableTest test : tests) {
            GridJunit3SerializableTestCase found = test.findTestCase(t);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void setResult(GridJunit3SerializableTest res) {
        GridJunit3SerializableTestSuite suite = (GridJunit3SerializableTestSuite)res;

        for (int i = 0; i < tests.size(); i++) {
            GridJunit3SerializableTest empty = tests.get(i);

            GridJunit3SerializableTest full = suite.tests.get(i);

            empty.setResult(full);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJunit3SerializableTestSuite.class, this);
    }
}
