// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.junit.runners.*;
import org.junit.runners.model.*;

/**
 * Parallel local runner for the grid. Use this runner when your test
 * suite should only be run locally but in parallel with other local
 * or distributed tests. Having local tests execute in parallel with
 * distributed tests will still give a significant performance boost
 * in many cases.
 * <p>
 * To use local test suite within distributed test suite, simply add
 * it to distributed test suite as follows:
 * <pre name="code" class="java">
 * &#64;RunWith(GridJunit4Suite.class)
 * &#64;SuiteClasses({
 *     TestA.class,
 *     TestB.class,
 *     GridJunit4ExampleNestedLocalSuite.class, // Local suite that will execute its test C locally.
 * })
 * public class GridJunit4ExampleSuite {
 *     // No-op.
 * }
 * </pre>
 * <pre name="code" class="java">
 * &#64;RunWith(GridJunit4LocalSuite.class) // Specify local suite to run tests.
 * &#64;SuiteClasses({
 *     TestC.class,
 *     TestD.class
 * })
 * public class GridJunit4ExampleNestedLocalSuite {
 *     // No-op.
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJunit4LocalSuite extends Suite {
    /**
     * Constructor required by JUnit4.
     *
     * @param cls Suite class.
     * @param builder Runner builder.
     * @throws InitializationError If error occurred during initialization.
     */
    public GridJunit4LocalSuite(Class<?> cls, RunnerBuilder builder) throws InitializationError {
        super(cls, builder);
    }
}
