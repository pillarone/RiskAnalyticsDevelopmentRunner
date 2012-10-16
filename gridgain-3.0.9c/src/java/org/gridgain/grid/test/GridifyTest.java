// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test;

import org.junit.runners.*;
import java.lang.annotation.*;

/**
 * Annotation for grid-enabling JUnit3 and Junit4 tests.
 * <h1 class="header">JUnit3</h1>
 * To enable JUnit3 tests using {@code GridifyTest} annotation, simply attach this
 * annotation to {@code "static suite()"} method for a test suite you would like to
 * grid-enable.
 * <pre name="code" class="java">
 * public class GridifyJunit3ExampleTestSuite {
 *     // Standard JUnit3 suite method. Note we attach &#64;GridifyTest
 *     // annotation to it, so it will be grid-enabled.
 *     &#64;GridifyTest
 *     public static TestSuite suite() {
 *         TestSuite suite = new TestSuite("Example Test Suite");
 *
 *         // Nested test suite to run tests A and B sequentially.
 *         TestSuite nested = new TestSuite("Example Nested Sequential Suite");
 *
 *         nested.addTestSuite(TestA.class);
 *         nested.addTestSuite(TestB.class);
 *
 *         // Add tests A and B.
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
 * <h1 class="header">JUnit4</h1>
 * To enable JUnit4 tests using {@code GridifyTest} annotation, you need to attach
 * this annotation to the same class that has {@link Suite} annotation (only {@link Suite}
 * runners can be grid-enabled in JUnit4).
 * <pre name="code" class="java">
 *  &#64;RunWith(Suite.class)
 *  &#64;SuiteClasses({
 *      GridJunit4ExampleNestedSuite.class, // Nested suite that will execute tests A and B added to it sequentially.
 *      TestC.class, // Test C will run in parallel with other tests.
 *      TestD.class // TestD will run in parallel with other tests.
 *  })
 *  &#64;GridifyTest // Run this suite on the grid.
 *  public class GridifyJunit4ExampleSuite {
 *      // No-op.
 *  }
 * </pre>
 * <pre name="code" class="java">
 * &#64;RunWith(Suite.class)
 * &#64;SuiteClasses({
 *     TestA.class,
 *     TestB.class
 * })
 * public class GridJunit4ExampleNestedSuite {
 *     // No-op.
 * }
 * </pre>
 * <p>
 * <h1 class="header">Jboss AOP Configuration</h1>
 * The following configuration needs to be applied to enable JBoss byte code
 * weaving. Note that GridGain is not shipped with JBoss and necessary
 * libraries will have to be downloaded separately (they come standard
 * if you have JBoss installed already):
 * <ul>
 * <li>
 *      The following JVM configuration must be present:
 *      <ul>
 *      <li>{@code -javaagent:[path to jboss-aop-jdk50-4.x.x.jar]}</li>
 *      <li>{@code -Djboss.aop.class.path=[path to gridgain.jar]}</li>
 *      <li>{@code -Djboss.aop.exclude=org,com -Djboss.aop.include=org.gridgain.examples}</li>
 *      </ul>
 * </li>
 * <li>
 *      The following JARs should be in a classpath (all located under {@code ${GRIDGAIN_HOME}/libs} folder):
 *      <ul>
 *      <li>{@code javassist-3.x.x.jar}</li>
 *      <li>{@code jboss-aop-jdk50-4.x.x.jar}</li>
 *      <li>{@code jboss-aspect-library-jdk50-4.x.x.jar}</li>
 *      <li>{@code jboss-common-4.x.x.jar}</li>
 *      <li>{@code trove-1.0.2.jar}</li>
 *      </ul>
 * </li>
 * </ul>
 * <p>
 * <h1 class="header">AspectJ AOP Configuration</h1>
 * The following configuration needs to be applied to enable AspectJ byte code
 * weaving.
 * <ul>
 * <li>
 *      JVM configuration should include:
 *      {@code -javaagent:${GRIDGAIN_HOME}/libs/aspectjweaver-1.6.8.jar}
 * </li>
 * <li>
 *      Classpath should contain the {@code ${GRIDGAIN_HOME}/config/aop/aspectj} folder.
 * </li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"JavaDoc"})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GridifyTest {
    /**
     * Optional configuration path. Default is {@code "config/junit/junit-spring.xml"}.
     */
    String configPath() default "config/junit/junit-spring.xml";

    /**
     * Optional router class. Default is {@link GridTestRouterAdapter} class.
     */
    Class<? extends GridTestRouter> routerClass() default GridTestRouterAdapter.class;

    /**
     * Indicates whether grid is disabled or not.
     */
    boolean disabled() default false;

    /**
     * Distributed test suite timeout in milliseconds, default is {@code 0} which means that
     * tests will never expire.
     */
    long timeout() default 0;
}
