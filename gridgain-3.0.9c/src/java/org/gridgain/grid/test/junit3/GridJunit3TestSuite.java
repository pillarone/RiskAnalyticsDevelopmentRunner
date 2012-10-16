// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import org.apache.log4j.*;
import org.apache.log4j.varia.*;
import org.gridgain.grid.*;
import org.gridgain.grid.test.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

import static org.gridgain.grid.test.GridTestVmParameters.*;

/**
 * Test suite for distributing JUnit3 tests. Simply add tests to this suite just like
 * you would for regular JUnit3 suites, and these tests will be executed in parallel
 * on the grid. Note that if there are no other grid nodes, this suite will still
 * ensure parallel test execution within single JVM.
 * <p>
 * Below is an example of distributed JUnit3 test suite:
 * <pre name="code" class="java">
 * public class GridJunit3ExampleTestSuite {
 *     // Standard JUnit3 static suite method.
 *     public static TestSuite suite() {
 *         TestSuite suite = new GridJunit3TestSuite("Example Grid Test Suite");
 *
 *         // Add tests.
 *         suite.addTestSuite(TestA.class);
 *         suite.addTestSuite(TestB.class);
 *         suite.addTestSuite(TestC.class);
 *
 *         return suite;
 *     }
 * }
 *</pre>
 * If you have four tests A, B, C, and D, and if you need to run A and B sequentially, then you
 * should create a nested test suite with test A and B as follows:
 * <pre name="code" class="java">
 * public class GridJunit3ExampleTestSuite {
 *     // Standard JUnit3 static suite method.
 *     public static TestSuite suite() {
 *         TestSuite suite = new GridJunit3TestSuite("Example Grid Test Suite");
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
 *
 *         return suite;
 *     }
 * }
 * </pre>
 * <p>
 * Note that you can also grid-enable existing JUnit3 tests using {@link GridifyTest @GridifyTest}
 * annotation which you can attach to your {@code suite()} methods of existing test suite. Refer
 * to {@link GridifyTest @GridifyTest} documentation for more information.
 * <p>
 * Also note that some tests can only be executed locally mostly due to some environment issues. However
 * they still can benefit from parallel execution with other tests. GridGain supports it via
 * {@link GridJunit3LocalTestSuite} suites that can be added to {@code GridJunit3TestSuite}. Refer
 * to {@link GridJunit3LocalTestSuite} documentation for more information.
 * <h1 class="header">Logging</h1>
 * When running distributed JUnit, all the logging that is done to {@link System#out} or {@link System#err}
 * is preserved. GridGain will accumulate all logging that is done on remote nodes, send them back
 * to originating node and associate all log statements with their corresponding tests. This way,
 * for example, if you are running tests from and IDEA or Eclipse (or any other IDE) you would still
 * see the logs as if it was a local run. However, since remote nodes keep all log statements done within
 * a single individual test case in memory, you must make sure that enough memory is allocated
 * on every node and that individual test cases do not spit out gigabytes of log statements.
 * Also note, that logs will be sent back to originating node upon completion of every test,
 * so don't be alarmed if you don't see any log statements for a while and then all of them
 * appear at once.
 * <p>
 * GridGain achieves such log transparency via reassigning {@link System#out} or {@link System#err} to
 * internal {@link PrintStream} implementation. However, when using {@code Log4J} (or any other
 * logging framework) within your tests you must make sure that it is configured with
 * {@link ConsoleAppender} and that {@link ConsoleAppender#setFollow(boolean)} attribute is set to
 * {@code true}. Logging to files is not supported yet and is planned for next point release.
 * <p>
 * <h1 class="header">Test Suite Nesting</h1>
 * {@code GridJunit3TestSuite} instances can be nested within each other as deep as needed.
 * However all nested distributed test suites will be treated just like regular JUnit test suites,
 * and not as distributed test suites. This approach becomes convenient when you have several
 * distributed test suites that you would like to be able to execute separately in distributed
 * fashion, but at the same time you would like to be able to execute them as a part of larger
 * distributed suites.
 * <p>
 * <h1 class="header">Configuration</h1>
 * To run distributed JUnit tests you need to start other instances of GridGain. You can do
 * so by running {@code GRIDGAIN_HOME/bin/ggjunit.{sh|bat}} script, which will
 * start default configuration. If configuration other than default is required, then
 * use regular {@code GRIDGAIN_HOME/bin/ggstart.{sh|bat}} script and pass your own
 * Spring XML configuration file as a parameter to the script.
 * <p>
 * You can use the following configuration parameters to configure distributed test suite
 * locally. Note that many parameters can be overridden by setting corresponding VM parameters
 * defined in {@link GridTestVmParameters} at VM startup.
 * <table class="doctable">
 *   <tr>
 *     <th>GridConfiguration Method</th>
 *     <th>Default Value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #setDisabled(boolean) setDisabled(boolean)}</td>
 *     <td>{@code false}</td>
 *     <td>
 *       If {@code true} then GridGain will be turned off and suite will run locally.
 *       This value can be overridden by setting {@link GridTestVmParameters#GRIDGAIN_DISABLED} VM
 *       parameter to {@code true}. This parameter comes handy when you would like to
 *       turn off GridGain without changing the actual code.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link #setConfigurationPath(String) setConfigurationPath(String)}</td>
 *     <td>{@link #DFLT_JUNIT_CONFIG DFLT_JUNIT_CONFIG}</td>
 *     <td>
 *       Optional path to GridGain Spring XML configuration file for running JUnit tests. This
 *       property can be overridden by setting {@link GridTestVmParameters#GRIDGAIN_CONFIG} VM
 *       parameter. Note that the value can be either absolute value or relative to
 *       ${GRIDGAIN_HOME} installation folder.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link #setRouterClassName(String) setRouterClassName(String)}</td>
 *     <td>{@link #DFLT_JUNIT_ROUTER DFLT_JUNIT_ROUTER}</td>
 *     <td>
 *       Optional name of test router class that implements {@link GridTestRouter} interface.
 *       If not provided, then tests will be routed in round-robin fashion using default
 *       {@link GridTestRouterAdapter}. The value of this parameter can be overridden by setting
 *       {@link GridTestVmParameters#GRIDGAIN_TEST_ROUTER} VM parameter to the name of your
 *       own customer router class.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link #setRouterClass(Class) setRouterClass(Class)}</td>
 *     <td>{@code null}</td>
 *     <td>
 *       Same as {@link #setRouterClassName(String) setRouterClassName(String)}, but sets the
 *       actual class instead of the name.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@link #setTimeout(long) setTimeout(long)}</td>
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
public class GridJunit3TestSuite extends TestSuite {
    /** Default GridGain configuration file for JUnits (value is {@code config/junit/junit-spring.xml}). */
    public static final String DFLT_JUNIT_CONFIG = "config/junit/junit-spring.xml";

    /** Default JUnit test router (value is {@link GridTestRouterAdapter GridTestRouterAdapter.class.getName()}). */
    public static final String DFLT_JUNIT_ROUTER = GridTestRouterAdapter.class.getName();

    /** */
    private final Collection<String> locTests = new HashSet<String>();

    /** JUnit3 JavaAssist proxy.  */
    private final GridJunit3ProxyFactory factory = new GridJunit3ProxyFactory();

    /** Flag indicating whether grid was started in this suite. */
    private boolean selfStarted;

    /** Junit3 Spring configuration path. */
    private String cfgPath = System.getProperty(GRIDGAIN_CONFIG.name()) == null ? DFLT_JUNIT_CONFIG :
        System.getProperty(GRIDGAIN_CONFIG.name());

    /**
     * Check if GridGain is disabled by checking
     * {@link GridTestVmParameters#GRIDGAIN_DISABLED} system property.
     */
    private boolean isDisabled = Boolean.getBoolean(GRIDGAIN_DISABLED.name());

    /** JUnit test router class name. */
    private String routerClsName = System.getProperty(GRIDGAIN_TEST_ROUTER.name()) == null ? DFLT_JUNIT_ROUTER :
        System.getProperty(GRIDGAIN_TEST_ROUTER.name());

    /** JUnit test router class. */
    private Class<? extends GridTestRouter> routerCls;

    /** Local suite in case if grid is disabled or if this is a nested suite within other distributed suite. */
    private TestSuite copy;

    /** JUnit grid name. */
    private String gridName;

    /** Test timeout. */
    private long timeout = Long.getLong(GRIDGAIN_TEST_TIMEOUT.name()) == null ? 0 :
        Long.getLong(GRIDGAIN_TEST_TIMEOUT.name());

    /** */
    private ClassLoader clsLdr;

    /**
     * Empty test suite.
     */
    public GridJunit3TestSuite() {
        if (copy == null) {
            copy = new TestSuite();
        }
    }

    /**
     * @param name Test suite name.
     */
    public GridJunit3TestSuite(String name) {
        super(name);

        if (copy == null) {
            copy = new TestSuite(name);
        }
    }

    /**
     * Test suite for one class.
     *
     * @param cls Class for test suite.
     */
    public GridJunit3TestSuite(Class<? extends TestCase> cls) {
        super(cls);

        if (copy == null) {
            copy = new TestSuite(cls);
        }
    }

    /**
     * Test suite for a given test class with specified test name.
     *
     * @param cls Test class.
     * @param name Test name.
     */
    public GridJunit3TestSuite(Class<? extends TestCase> cls, String name) {
        super(cls, name);

        if (copy == null) {
            copy = new TestSuite(cls, name);
        }
    }

    /**
     * Copies non-distributed test suite into distributed one.
     *
     * @param suite Test suite to copy.
     */
    public GridJunit3TestSuite(TestSuite suite) {
        super(suite.getName());

        if (copy == null) {
            copy = new TestSuite(suite.getName());
        }

        for (int i = 0; i < suite.testCount(); i++) {
            addTest(suite.testAt(i));
        }
    }

    /**
     * Empty test suite with given class loader.
     *
     * @param clsLdr Tests class loader.
     */
    public GridJunit3TestSuite(ClassLoader clsLdr) {
        this();

        assert clsLdr != null;

        this.clsLdr = clsLdr;
    }

    /**
     * @param name Test suite name.
     * @param clsLdr Tests class loader.
     */
    public GridJunit3TestSuite(String name, ClassLoader clsLdr) {
        this(name);

        assert clsLdr != null;

        this.clsLdr = clsLdr;
    }

    /**
     * Test suite for one class.
     *
     * @param cls Class for test suite.
     * @param clsLdr Tests class loader.
     */
    public GridJunit3TestSuite(Class<? extends TestCase> cls, ClassLoader clsLdr) {
        this(cls);

        assert clsLdr != null;

        this.clsLdr = clsLdr;
    }

    /**
     * Test suite for a given test class with specified test name.
     *
     * @param cls Test class.
     * @param name Test name.
     * @param clsLdr Tests class loader.
     */
    public GridJunit3TestSuite(Class<? extends TestCase> cls, String name, ClassLoader clsLdr) {
        this(cls, name);

        assert clsLdr != null;

        this.clsLdr = clsLdr;
    }

    /**
     * Copies non-distributed test suite into distributed one.
     *
     * @param suite Test suite to copy.
     * @param clsLdr Tests class loader.
     */
    public GridJunit3TestSuite(TestSuite suite, ClassLoader clsLdr) {
        this(suite);

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
     * Gets identical suite for local (non-distributed) execution.
     *
     * @return Local suite.
     */
    public TestSuite getLocalCopy() {
        return copy;
    }

    /** {@inheritDoc} */
    @Override public void setName(String name) {
        if (copy != null) {
            copy.setName(name);
        }

        super.setName(name);
    }

    /**
     * Gets timeout for running distributed test suite.
     *
     * @return Timeout for tests.
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets timeout for running distributed test suite. By default, test execution
     * does not expire.
     *
     * @param timeout Timeout for tests.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /** {@inheritDoc} */
    @Override public Test testAt(int index) {
        return isDisabled ? copy.testAt(index) : super.testAt(index);
    }

    /** {@inheritDoc} */
    @Override public int testCount() {
        return isDisabled ? copy.testCount() : super.testCount();
    }

    /** {@inheritDoc} */
    @Override public Enumeration<Test> tests() {
        return isDisabled ? copy.tests() : super.tests();
    }

    /**
     * The added suite will be always executed locally, but in parallel
     * with other locally or remotely running tests. This comes handy for
     * tests that cannot be distributed for some environmental reasons,
     * but still would benefit from parallel execution.
     * <p>
     * Note, that local suites will be executed on local node even if grid
     * topology only allows remote nodes.
     *
     * @param localSuite Test to execute locally in parallel with other local or
     *      distributed tests.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public void addTest(GridJunit3LocalTestSuite localSuite) {
        if (!locTests.contains(localSuite.getName())) {
            locTests.add(localSuite.getName());
        }

        addTest((Test)localSuite);
    }

    /**
     * Adds a test to be executed on the grid. In case of test suite,
     * all tests inside of test suite will be executed sequentially
     * on some remote node.
     *
     * @param test Test to add.
     */
    @Override public void addTest(Test test) {
        if (copy == null) {
            copy = new TestSuite(getName());
        }

        // Add test to the list of local ones.
        if (test instanceof GridJunit3LocalTestSuite) {
            String testName = ((TestSuite)test).getName();

            if (!locTests.contains(testName)) {
                locTests.add(testName);
            }
        }

        if (test instanceof GridJunit3TestSuite) {
            copy.addTest(((GridJunit3TestSuite)test).copy);

            super.addTest(new GridJunit3TestSuiteProxy(((GridJunit3TestSuite)test).copy, factory));
        }
        else if (test instanceof GridJunit3TestSuiteProxy) {
            copy.addTest(((GridJunit3TestSuiteProxy)test).getOriginal());

            super.addTest(test);
        }
        else if (test instanceof GridJunit3TestCaseProxy) {
            copy.addTest(((GridJunit3TestCaseProxy)test).getGridGainJunit3OriginalTestCase());

            super.addTest(test);
        }
        else if (test instanceof TestSuite) {
            copy.addTest(test);

            super.addTest(new GridJunit3TestSuiteProxy((TestSuite)test, factory));
        }
        else {
            assert test instanceof TestCase : "Test must be either instance of TestSuite or TestCase: " + test;

            copy.addTest(test);

            super.addTest(factory.createProxy((TestCase)test));
        }
    }

    /**
     * Creates JUnit test router. Note that router must have a no-arg constructor.
     *
     * @return JUnit router instance.
     */
    @SuppressWarnings({"unchecked"})
    private GridTestRouter createRouter() {
        try {
            if (routerCls == null) {
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

    /**
     * Runs all tests belonging to this test suite on the grid.
     *
     * @param result Test result collector.
     */
    @Override public void run(TestResult result) {
        if (isDisabled) {
            copy.run(result);
        }
        else {
            GridTestRouter router = createRouter();

            Grid grid = startGrid();

            try {
                List<GridTaskFuture<?>> futs = new ArrayList<GridTaskFuture<?>>(testCount());

                List<GridJunit3SerializableTest> tests = new ArrayList<GridJunit3SerializableTest>(testCount());

                for (int i = 0; i < testCount(); i++) {
                    Test junit = testAt(i);

                    GridJunit3SerializableTest test;

                    if (junit instanceof TestSuite) {
                        test = new GridJunit3SerializableTestSuite((TestSuite)junit);
                    }
                    else {
                        assert junit instanceof TestCase : "Test must be either TestSuite or TestCase: " +
                            junit;

                        test = new GridJunit3SerializableTestCase((TestCase)junit);
                    }

                    tests.add(test);

                    if (clsLdr == null) {
                        clsLdr = U.detectClassLoader(junit.getClass());
                    }

                    futs.add(grid.execute(
                        new GridJunit3Task(junit.getClass(), clsLdr),
                        new GridJunit3Argument(router, test, locTests.contains(test.getName())),
                        timeout));
                }

                for (int i = 0; i < testCount(); i++) {
                    GridTaskFuture<?> fut = futs.get(i);

                    GridJunit3SerializableTest origTest = tests.get(i);

                    try {
                        GridJunit3SerializableTest resTest = (GridJunit3SerializableTest)fut.get();

                        origTest.setResult(resTest);

                        origTest.getTest().run(result);
                    }
                    catch (GridException e) {
                        handleFail(result, origTest, e);
                    }
                }
            }
            finally {
                stopGrid();
            }
        }
    }

    /**
     * Handles test fail.
     *
     * @param result Test result.
     * @param origTest Original JUnit test.
     * @param e Exception thrown from grid.
     */
    private void handleFail(TestResult result, GridJunit3SerializableTest origTest, Throwable e) {
        // Simulate that all tests were run.
        origTest.getTest().run(result);

        // For the tests suite we assume that all tests failed because
        // entire test suite execution failed and there is no way to get
        // broken tests.
        if (origTest.getTest() instanceof GridJunit3TestSuiteProxy) {
            TestSuite suite = (((TestSuite)origTest.getTest()));

            for (int j = 0; j < suite.testCount(); j++) {
                result.addError(suite.testAt(j), e);
            }
        }
        else if (origTest.getTest() instanceof GridJunit3TestCaseProxy) {
            result.addError(origTest.getTest(), e);
        }
    }

    /**
     * Starts Grid instance. Note that if grid is already started,
     * then it will be looked up and returned from this method.
     *
     * @return Started grid.
     */
    private Grid startGrid() {
        Properties props = System.getProperties();

        gridName = props.getProperty(GRIDGAIN_NAME.name());

        if (!props.containsKey(GRIDGAIN_NAME.name()) ||
            G.state(gridName) != GridFactoryState.STARTED) {
            selfStarted = true;

            // Set class loader for the spring.
            ClassLoader curCl = Thread.currentThread().getContextClassLoader();

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
                throw new GridRuntimeException("Failed to start grid: " + cfgPath, e);
            }
            finally {
                Logger.getRootLogger().removeAppender(app);

                Thread.currentThread().setContextClassLoader(curCl);
            }
        }

        return G.grid(gridName);
    }

    /**
     * Stops grid only if it was started by this test suite.
     */
    private void stopGrid() {
        // Only stop grid if it was started here.
        if (selfStarted) {
            G.stop(gridName, true);
        }
    }
}
