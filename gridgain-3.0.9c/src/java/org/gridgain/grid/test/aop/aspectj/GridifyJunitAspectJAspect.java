// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.aop.aspectj;

import junit.framework.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.*;
import org.gridgain.grid.test.*;
import org.gridgain.grid.test.junit3.*;
import org.gridgain.grid.test.junit4.*;
import org.junit.runner.Describable;
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
@Aspect
public class GridifyJunitAspectJAspect {
    /**
     * Executes JUnit3 tests annotated with {@link GridifyTest @GridifyTest} annotation
     * on the grid.
     *
     * @param joinPoint Join point provided by AspectJ AOP.
     * @return Method execution result.
     * @throws Throwable If execution failed.
     */
    @Around("execution(static junit.framework.Test+ *.suite(..))")
    public Object gridifyJunit3(ProceedingJoinPoint joinPoint) throws Throwable {
        Method mtd = ((MethodSignature)joinPoint.getSignature()).getMethod();

        GridifyTest ann = mtd.getAnnotation(GridifyTest.class);

        if (ann == null) {
            return joinPoint.proceed();
        }

        Test test = (Test)joinPoint.proceed();

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
        ClassLoader clsLdr = joinPoint.getSignature().getDeclaringType().getClassLoader();

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
     * @param joinPoint Join point provided by AspectJ AOP.
     * @return Method execution result.
     * @throws Throwable If execution failed.
     */
    @Around("execution(public void (org.junit.runners.Suite).run(org.junit.runner.notification.RunNotifier))"
        + "&& !cflow(target(org.gridgain.grid.test.junit4.GridJunit4Suite))")
    public Object gridifyJunit4(ProceedingJoinPoint joinPoint) throws Throwable {
        Describable suite = (Describable)joinPoint.getTarget();

        // We create class with caller class loader,
        // thus JUnit 4 task will pick up proper class loader.
        ClassLoader clsLdr = joinPoint.getSignature().getDeclaringType().getClassLoader();

        Class<?> cls = Class.forName(suite.getDescription().getDisplayName(), true, clsLdr);

        if (cls.getAnnotation(GridifyTest.class) != null) {
            new GridJunit4Suite(cls, clsLdr).run((RunNotifier)joinPoint.getArgs()[0]);

            return null;
        }

        return joinPoint.proceed();
    }
}
