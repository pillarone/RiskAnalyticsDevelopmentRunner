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
 * Local test suites will always be executed locally within distributed test suites.
 * They will be executed locally even if grid topology does not include local node.
 * Such functionality is very useful for tests that cannot be executed remotely
 * usually due to environment reasons, but can still benefit from parallel
 * execution with other tests within the same distributed test suite.
 * <p>
 * To use local test suite within distributed test suite, simply add
 * it to distributed test suite as follows:
 * <pre name="code" class="java">
 * public class GridJunit3ExampleTestSuite {
 *     // Local test suite example.
 *     public static TestSuite suite() {
 *         TestSuite suite = new GridJunit3TestSuite("Example Grid Test Suite");
 *
 *         // Local nested test suite to always run tests A and B
 *         // on the local node.
 *         TestSuite nested = new GridJunit3LocalTestSuite("Example Nested Sequential Suite");
 *
 *         nested.addTestSuite(TestA.class);
 *         nested.addTestSuite(TestB.class);
 *
 *         // Add local tests A and B.
 *         suite.addTest(nested);
 *
 *         // Add other tests.
 *         suite.addTestSuite(TestC.class);
 *         suite.addTestSuite(TestD.class);
 *
 *         return suite;
 *     }
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJunit3LocalTestSuite extends TestSuite {
    /** */
    public GridJunit3LocalTestSuite() {
        // No-op.
    }

    /**
     * @param cls Test case class.
     */
    public GridJunit3LocalTestSuite(Class<? extends TestCase> cls) {
        super(cls);
    }

    /**
     * @param cls Test case class.
     * @param name Name.
     */
    public GridJunit3LocalTestSuite(Class<? extends TestCase> cls, String name) {
        super(cls, name);
    }

    /**
     * @param name Name.
     */
    public GridJunit3LocalTestSuite(String name) {
        super(name);
    }

    /**
     * @param classes Test case classes.
     */
    public GridJunit3LocalTestSuite(Class<?>... classes) {
        super(classes);
    }

    /**
     * @param classes Test case classes.
     * @param name Name.
     */
    public GridJunit3LocalTestSuite(Class<? extends TestCase>[] classes, String name) {
        super(classes, name);
    }
}
