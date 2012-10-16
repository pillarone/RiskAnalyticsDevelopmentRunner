// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.apache.log4j.*;
import org.apache.log4j.varia.*;
import org.gridgain.grid.*;
import org.gridgain.grid.test.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import org.junit.runners.*;
import org.junit.runners.model.*;
import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridFactoryState.*;
import static org.gridgain.grid.test.GridTestVmParameters.*;

/**
 * Test suite runner for distributing JUnit4 tests. Simply add tests to this suite runner
 * just like you would for regular JUnit4 suites, and these tests will be executed in parallel
 * on the grid. Note that if there are no other grid nodes, this suite runner will still
 * ensure parallel test execution within single VM.
 * <p>
 * Below is an example of distributed JUnit4 test suite:
 * <pre name="code" class="java">
 * &#64;RunWith(GridJunit4Suite.class)
 * &#64;SuiteClasses({
 *     TestA.class, // TestA will run in parallel on the grid.
 *     TestB.class, // TestB will run in parallel on the grid.
 *     TestC.class, // TestC will run in parallel on the grid.
 *     TestD.class // TestD will run in parallel on the grid.
 * })
 * public class GridJunit4ExampleSuite {
 *     // No-op.
 * }
 * </pre>
 * If you have four tests A, B, C, and D, and if you need to run A and B sequentially, then you
 * should create a nested test suite with test A and B as follows:
 * <pre name="code" class="java">
 * &#64;RunWith(GridJunit4Suite.class)
 * &#64;SuiteClasses({
 *     GridJunit4ExampleNestedSuite.class, // Nested suite that will execute tests A and B added to it sequentially.
 *     TestC.class, // TestC will run in parallel on the grid.
 *     TestD.class // TestD will run in parallel on the grid.
 * })
 * public class GridJunit4ExampleSuite {
 *     // No-op.
 * }
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
 * Note that you can also grid-enable existing JUnit4 tests using {@link GridifyTest @GridifyTest}
 * annotation which you can attach to the same class you attach {@link RunWith} annotation to.
 * Refer to {@link GridifyTest @GridifyTest} documentation for more information.
 * <p>
 * Also note that some tests can only be executed locally mostly due to some environment issues. However
 * they still can benefit from parallel execution with other tests. GridGain supports it via
 * {@link GridJunit4LocalSuite} suites that can be added as nested suites to {@code GridJunit4Suite}. Refer
 * to {@link GridJunit4LocalSuite} documentation for more information.
 * <h1 class="header">Logging</h1>
 * When running distributed JUnit, all the logging that is done to {@link System#out} or {@link System#err}
 * is preserved. GridGain will accumulate all logging that is done on remote nodes, send them back
 * to originating node and associate all log statements with their corresponding tests. This way,
 * for example, if you are running tests from IDEA or Eclipse (or any other IDE) you would still
 * see the logs as if it was a local run. However, since remote nodes keep all log statements done within
 * a single individual test case in memory, you must make sure that enough memory is allocated
 * on every node and that individual test cases do not spit out gigabytes of log statements.
 * Also note, that logs will be sent back to originating node upon completion of every test,
 * so don't be alarmed if you don't see any log statements for a while and then all of them
 * appear at once.
 * <p>
 * GridGain achieves such log transparency via reassigning {@link System#out} or {@link System#err} to
 * internal {@link PrintStream} implementation. However, when using {@code Log4J}
 * within your tests you must make sure that it is configured with {@link ConsoleAppender} and that
 * {@link ConsoleAppender#setFollow(boolean)} attribute is set to {@code true}. Logging to files
 * is not supported yet and is planned for next point release.
 * <p>
 * <h1 class="header">Test Suite Nesting</h1>
 * {@code GridJunit4TestSuite} instances can be nested within each other as deep as needed.
 * However all nested distributed test suites will be treated just like regular JUnit test suites.
 * This approach becomes convenient when you have several distributed test suites that you
 * would like to be able to execute separately in distributed fashion, but at the same time
 * you would like to be able to execute them as a part of larger distributed suites.
 * <p>
 * <h1 class="header">Configuration</h1>
 * To run distributed JUnit tests you need to start other instances of GridGain. You can do
 * so by running {@code GRIDGAIN_HOME/bin/ggjunit.{sh|bat}} script, which will
 * start default configuration. If configuration other than default is required, then
 * use regular {@code GRIDGAIN_HOME/bin/ggstart.{sh|bat}} script and pass your own
 * Spring XML configuration file as a parameter to the script.
 * <p>
 * You can use the following configuration parameters to configure distributed test suite
 * locally. These parameters are set via {@link GridifyTest} annotation. Note that GridGain
 * will check these parameters even if AOP is not enabled. Also note that many parameters
 * can be overridden by setting corresponding VM parameters defined in {@link GridTestVmParameters}
 * at VM startup.
 * <table class="doctable">
 *   <tr>
 *     <th>GridConfiguration Method</th>
 *     <th>Default Value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>{@link GridifyTest#disabled()}</td>
 *     <td>{@code false}</td>
 *     <td>
 *       If {@code true} then GridGain will be turned off and suite will run locally.
 *       This value can be overridden by setting {@link GridTestVmParameters#GRIDGAIN_DISABLED} VM
 *       parameter to {@code true}. This parameter comes handy when you would like to
 *       turn off GridGain without changing the actual code.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link GridifyTest#configPath()}</td>
 *     <td>{@link #DFLT_JUNIT_CONFIG DFLT_JUNIT_CONFIG}</td>
 *     <td>
 *       Optional path to GridGain Spring XML configuration file for running JUnit tests. This
 *       property can be overridden by setting {@link GridTestVmParameters#GRIDGAIN_CONFIG} VM
 *       parameter. Note that the value can be either absolute value or relative to
 *       ${GRIDGAIN_HOME} installation folder.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link GridifyTest#routerClass()}</td>
 *     <td>{@code {@link GridTestRouterAdapter}}</td>
 *     <td>
 *       Optional test router class that implements {@link GridTestRouter} interface.
 *       If not provided, then tests will be routed in round-robin fashion using default
 *       {@link GridTestRouterAdapter}. The value of this parameter can be overridden by setting
 *       {@link GridTestVmParameters#GRIDGAIN_TEST_ROUTER} VM parameter to the name of your
 *       own customer router class.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link GridifyTest#timeout()}</td>
 *     <td>{@code 0} which means that tests will never timeout.</td>
 *     <td>
 *       Maximum timeout value in milliseconds after which test suite will return without
 *       waiting for the remaining tests to complete. This value can be overridden by setting
 *       {@link GridTestVmParameters#GRIDGAIN_TEST_TIMEOUT} VM parameter to the timeout value
 *       for the tests.
 *     </td>
 *   </tr>
 * </table>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJunit4Suite extends GridJunit4SuiteRunner {
    /** Default GridGain configuration file for JUnits (value is {@code config/junit/junit-spring.xml}). */
    public static final String DFLT_JUNIT_CONFIG = "config/junit/junit-spring.xml";

    /** Default JUnit test router (value is {@link GridTestRouterAdapter GridTestRouterAdapter.class.getName()}). */
    public static final String DFLT_JUNIT_ROUTER = GridTestRouterAdapter.class.getName();

    /** Flag indicating whether grid was started in this suite. */
    private boolean selfStarted;

    /** Path to Spring XML configuration file. */
    private String cfgPath = System.getProperty(GRIDGAIN_CONFIG.name()) == null ? DFLT_JUNIT_CONFIG :
        System.getProperty(GRIDGAIN_CONFIG.name());

    /**
     * Check if GridGain is disabled by checking
     * {@link GridTestVmParameters#GRIDGAIN_DISABLED} system property.
     */
    private boolean isDisabled = Boolean.getBoolean(GRIDGAIN_DISABLED.name());

    /** JUnit router class name. */
    private String routerClsName = System.getProperty(GRIDGAIN_TEST_ROUTER.name()) == null ? DFLT_JUNIT_ROUTER :
        System.getProperty(GRIDGAIN_TEST_ROUTER.name());

    /** JUnit router class. */
    private Class<? extends GridTestRouter> routerCls;

    /** JUnit grid name. */
    private String gridName;

    /** JUnit test suite timeout. */
    private long timeout = Long.getLong(GRIDGAIN_TEST_TIMEOUT.name()) == null ? 0 :
        Long.getLong(GRIDGAIN_TEST_TIMEOUT.name());

    /** */
    private final ClassLoader clsLdr;

    /**
     * Creates distributed suite runner for given class.
     *
     * @param cls Class to create suite runner for.
     */
    public GridJunit4Suite(Class<?> cls) {
        super(cls);

        clsLdr = U.detectClassLoader(cls);
    }

    /**
     * Creates distributed suite runner for given class.
     *
     * @param cls Class to create suite runner for.
     * @param clsLdr Tests class loader.
     */
    public GridJunit4Suite(Class<?> cls, ClassLoader clsLdr) {
        super(cls);

        assert clsLdr != null;

        this.clsLdr = clsLdr;
    }

    /**
     * Sets path to GridGain configuration file. By default
     * {@code {GRIDGAIN_HOME}/config/junit/junit-spring.xml} is used.
     *
     * @param cfgPath Path to GridGain configuration file.
     */
    public void setConfigurationPath(String cfgPath) {
        this.cfgPath = cfgPath;
    }

    /**
     * Gets path to GridGain configuration file. By default
     * {@code {GRIDGAIN_HOME}/config/junit/junit-spring.xml} is used.
     *
     * @return Path to GridGain configuration file.
     */
    public String getConfigurationPath() {
        return cfgPath;
    }

    /**
     * Disables GridGain. If set to {@code true} then this suite will execute locally
     * as if GridGain was not in a picture at all.
     *
     * @param disabled If set to {@code true} then this suite will execute locally
     *      as if GridGain was not in a picture at all.
     */
    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
    }

    /**
     * Gets flag indicating whether GridGain should be enabled or not. If set to
     * {@code true} then this suite will execute locally as if GridGain was not
     * in a picture at all.
     *
     * @return Flag indicating whether GridGain should be enabled or not. If set to
     *      {@code true} then this suite will execute locally as if GridGain was not
     *      in a picture at all.
     */
    public boolean isDisabled() {
        return isDisabled;
    }

    /**
     * Sets name of class for routing JUnit tests. By default {@link #DFLT_JUNIT_ROUTER}
     * class name is used.
     *
     * @param routerClsName Junit test router class name.
     */
    public void setRouterClassName(String routerClsName) {
        this.routerClsName = routerClsName;
    }

    /**
     * Gets JUnit test router class name.
     *
     * @return JUnit test router class name.
     */
    public String getRouterClassName() {
        return routerClsName;
    }

    /**
     * Sets router class. By default {@link GridTestRouterAdapter} is used.
     *
     * @param routerCls Router class to use for test routing.
     */
    public void setRouterClass(Class<? extends GridTestRouter> routerCls) {
        this.routerCls = routerCls;
    }

    /**
     * Gets router class used for test routing.
     *
     * @return Router class used for test routing.
     */
    public Class<? extends GridTestRouter> getRouterClass() {
        return routerCls;
    }

    /**
     * Creates JUnit test router. Note that router must have a no-arg constructor.
     *
     * @return JUnit router instance.
     */
    @SuppressWarnings({"unchecked"})
    private GridTestRouter createRouter() {
        GridifyTest ann = getTestClass().getAnnotation(GridifyTest.class);

        if (ann != null) {
            Properties props = System.getProperties();

            // System property is given priority.
            if (!props.containsKey(GRIDGAIN_TEST_ROUTER.name())) {
                routerCls = ann.routerClass();
            }
        }

        try {
            if (routerCls == null) {
                assert routerClsName != null : "Neither outer class or router class name is specified.";

                routerCls = (Class<? extends GridTestRouter>)Class.forName(routerClsName);
            }
            else {
                routerClsName = routerCls.getName();
            }

            return routerCls.newInstance();
        }
        catch (ClassNotFoundException e) {
            throw new GridRuntimeException("Failed to initialize JUnit router: " + routerClsName, e);
        }
        catch (IllegalAccessException e) {
            throw new GridRuntimeException("Failed to initialize JUnit router: " + routerClsName, e);
        }
        catch (InstantiationException e) {
            throw new GridRuntimeException("Failed to initialize JUnit router: " + routerClsName, e);
        }
    }

    /** {@inheritDoc} */
    @Override public void run(RunNotifier notifier) {
        GridifyTest ann = getTestClass().getAnnotation(GridifyTest.class);

        if (ann != null) {
            Properties props = System.getProperties();

            // System property is given priority.
            if (!props.containsKey(GRIDGAIN_DISABLED.name())) {
                isDisabled = ann.disabled();
            }

            if (!props.containsKey(GRIDGAIN_TEST_TIMEOUT.name())) {
                timeout = ann.timeout();
            }
        }

        if (!isDisabled) {
            GridTestRouter router = createRouter();

            Grid grid = startGrid();

            try {
                // Start execution on Grid.
                for (final GridJunit4Runner child : getChildren()) {
                    GridFuture<?> fut = grid.execute(
                        new GridJunit4Task(child.getTestClass(), clsLdr),
                        new GridJunit4Argument(router, child, isLocal(child)),
                        timeout
                    );

                    fut.listenAsync(new CI1<GridFuture<?>>() {
                        @Override public void apply(GridFuture<?> fut) {
                            // Wait for results.
                            try {
                                GridJunit4Runner res = (GridJunit4Runner)fut.get();

                                // Copy results to local test.
                                child.copyResults(res);
                            }
                            catch (GridException e) {
                                handleFail(child, e);
                            }
                        }
                    });
                }

                // Collect results and finish tests sequentially.
                for (GridJunit4Runner child : getChildren()) {
                    // Start test.
                    child.run(notifier);
                }
            }
            finally {
                stopGrid();
            }
        }
        else {
            try {
                new JUnit4(getTestClass()).run(notifier);
            }
            catch (InitializationError e) {
                throw new GridRuntimeException("Failed to initialize suite: " + getTestClass(), e);
            }
        }
    }


    /**
     * Handles test fail.
     *
     * @param child Test.
     * @param e Exception that occurred during test.
     */
    private void handleFail(GridJunit4Runner child, GridException e) {
        // Since we got exception executing task, we cannot say which test failed.
        // So we mark all tests as failed.
        List<GridJunit4Result> failRes = new LinkedList<GridJunit4Result>();

        for (Description desc : child.getDescription().getChildren()) {
            failRes.add(new GridJunit4Result(desc.getDisplayName(), null, null, e));
        }

        child.setResult(failRes);
    }

    /**
     * @return Started grid.
     */
    private Grid startGrid() {
        Properties props = System.getProperties();

        gridName = props.getProperty(GRIDGAIN_NAME.name());

        if (!props.containsKey(GRIDGAIN_NAME.name()) || G.state(gridName) != STARTED) {
            GridifyTest ann = getTestClass().getAnnotation(GridifyTest.class);

            if (ann != null) {
                // System property is given priority.
                if (!props.containsKey(GRIDGAIN_CONFIG.name())) {
                    cfgPath = ann.configPath();
                }
            }

            selfStarted = true;

            // Set class loader for the spring.
            ClassLoader curClsLdr = Thread.currentThread().getContextClassLoader();

            // Add no-op logger to remove no-appender warning.
            Appender app = new NullAppender();

            Logger.getRootLogger().addAppender(app);

            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                Grid grid = G.start(cfgPath);

                gridName = grid.name();

                System.setProperty(GRIDGAIN_NAME.name(), grid.name());

                return grid;
            }
            catch (GridException e) {
                throw new GridRuntimeException("Failed to start grid: " + gridName, e);
            }
            finally {
                Logger.getRootLogger().removeAppender(app);

                Thread.currentThread().setContextClassLoader(curClsLdr);
            }
        }

        return G.grid(gridName);
    }

    /** */
    private void stopGrid() {
        // Only stop grid if it was started here.
        if (selfStarted) {
            G.stop(gridName, true);
        }
    }
}
