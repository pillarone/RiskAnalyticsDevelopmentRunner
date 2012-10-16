// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.aop.jboss;

import junit.framework.*;
import org.gridgain.grid.test.*;
import org.gridgain.grid.test.junit3.*;
import org.gridgain.grid.test.junit4.*;
import org.jboss.aop.*;
import org.jboss.aop.advice.*;
import org.jboss.aop.joinpoint.*;
import org.jboss.aop.pointcut.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.lang.reflect.*;
import java.util.*;

import static org.gridgain.grid.test.GridTestVmParameters.*;

/**
 * AspectJ aspect that intercepts on {@link GridifyTest} annotation to
 * execute annotated tests on remote nodes.
 * <p>
 * See {@link GridifyTest} documentation for more information about execution of
 * {@code gridified} JUnits.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridifyTest
 */
@Aspect(scope = Scope.PER_VM)
public class GridifyJunitJbossAspect {
    /** Definition of {@code cflow} pointcut. */
    @CFlowStackDef(cflows={@CFlowDef(expr= "* org.gridgain.grid.test.junit4.GridJunit4Suite->*(..)", called=false)})
    public static final CFlowStack JUNIT4_CFLOW_STACK = null;

    /**
     * Executes JUnit3 tests annotated with {@link GridifyTest @GridifyTest} annotation
     * on the grid.
     *
     * @param invoc Join point provided by JBoss AOP.
     * @return Method execution result.
     * @throws Throwable If execution failed.
     */
    @Bind(pointcut = "execution(* *->@org.gridgain.grid.test.GridifyTest(..)) AND " +
        "execution(public static $instanceof{junit.framework.Test} *->suite())")
    public Object gridifyJunit3(MethodInvocation invoc) throws Throwable {
        Method mtd = invoc.getMethod();

        GridifyTest ann = mtd.getAnnotation(GridifyTest.class);

        assert ann != null : "Intercepted method does not have gridify annotation.";

        Test test = (Test)invoc.invokeNext();

        TestSuite suite;

        if (test instanceof TestSuite) {
            suite = (TestSuite)test;
        }
        else {
            suite = new TestSuite();

            suite.addTest(test);
        }

        // Pickup class loader of caller code. This is considered as
        // entire test suite class loader.
        ClassLoader clsLdr = invoc.getActualMethod().getDeclaringClass().getClassLoader();

        GridJunit3TestSuite gridSuite = new GridJunit3TestSuite(suite, clsLdr);

        Properties props = System.getProperties();

        // System property is given priority.
        if (!props.containsKey(GRIDGAIN_TEST_ROUTER.name())) {
            gridSuite.setRouterClass(ann.routerClass());
        }

        // System property is given priority.
        if (!props.containsKey(GRIDGAIN_CONFIG.name())) {
            gridSuite.setConfigurationPath(ann.configPath());
        }

        // System property is given priority.
        if (!props.containsKey(GRIDGAIN_DISABLED.name())) {
            gridSuite.setDisabled(ann.disabled());
        }

        // System property is given priority.
        if (!props.containsKey(GRIDGAIN_TEST_TIMEOUT.name())) {
            gridSuite.setTimeout(ann.timeout());
        }

        return gridSuite;
    }

    /**
     * Executes JUnit4 tests annotated with {@link GridifyTest @GridifyTest} annotation
     * on the grid.
     *
     * @param invoc Join point provided by JBoss AOP.
     * @return Method execution result.
     * @throws Throwable If execution failed.
     */
    @Bind(pointcut = "execution(public * org.junit.runners.Suite->run(org.junit.runner.notification.RunNotifier))",
        cflow = "org.gridgain.grid.test.aop.jboss.GridifyJunitJbossAspect.JUNIT4_CFLOW_STACK")
    public Object gridifyJunit4(MethodInvocation invoc) throws Throwable {
        Describable suite = (Describable)invoc.getTargetObject();

        // We create class with caller class loader,
        // thus JUnit 4 task will pick up proper class loader.
        ClassLoader clsLdr = invoc.getActualMethod().getDeclaringClass().getClassLoader();

        Class<?> cls = Class.forName(suite.getDescription().getDisplayName(), true, clsLdr);

        if (cls.getAnnotation(GridifyTest.class) != null) {
            new GridJunit4Suite(cls, clsLdr).run((RunNotifier)invoc.getArguments()[0]);

            return null;
        }

        return invoc.invokeNext();
    }
}
