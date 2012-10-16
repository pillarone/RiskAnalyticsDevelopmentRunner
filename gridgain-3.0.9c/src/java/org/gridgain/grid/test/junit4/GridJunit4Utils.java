// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import junit.extensions.*;
import junit.framework.*;
import org.junit.runner.*;
import org.junit.runners.model.*;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * JUnit 4 utils.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
final class GridJunit4Utils {
    /**
     * Enforces singleton.
     */
    private GridJunit4Utils() {
        // No-op.
    }

    /**
     * @param cls Class to get super classes.
     * @return Super classes for given class.
     */
    static List<Class<?>> getSuperClasses(Class<?> cls) {
        List<Class<?>> results= new ArrayList<Class<?>>();

        Class<?> cur = cls;

        while (cur != null) {
            results.add(cur);

            cur = cur.getSuperclass();
        }

        return results;
    }

    /**
     * @param mtd Method.
     * @param results Results.
     * @return {@code True} If method is shadowed by results.
     */
    static boolean isShadowed(Method mtd, Iterable<Method> results) {
        for (Method m : results) {
            if (isShadowed(mtd, m)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param cur Current method.
     * @param prev Previous method.
     * @return {@code True} if method is shadowed by previous method.
     */
    static boolean isShadowed(Method cur, Method prev) {
        if (!prev.getName().equals(cur.getName())) {
            return false;
        }

        if (prev.getParameterTypes().length != cur.getParameterTypes().length) {
            return false;
        }

        for (int i= 0; i < prev.getParameterTypes().length; i++) {
            if (!prev.getParameterTypes()[i].equals(cur.getParameterTypes()[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * @param cls Class to get annotated methods.
     * @param annCls Annotation class.
     * @return Methods annotated with given annotation.
     */
    static List<Method> getAnnotatedMethods(Class<?> cls, Class<? extends Annotation> annCls) {
        List<Method> mtds = new ArrayList<Method>();

        for (Class<?> parent : getSuperClasses(cls)) {
            for (Method mtd : parent.getDeclaredMethods()) {
                Annotation ann = mtd.getAnnotation(annCls);

                if (ann != null && !isShadowed(mtd, mtds)) {
                    mtds.add(mtd);
                }
            }
        }

        return mtds;
    }

    /**
     * @param cls Suite class.
     * @return {@code True} if suite has a static suite method.
     */
    static boolean hasSuiteMethod(Class<?> cls) {
        try {
            cls.getMethod("suite");
        }
        catch (NoSuchMethodException ignored) {
            return false;
        }

        return true;
    }

    /**
     * @param cls Test case class.
     * @return JUnit3 test methods.
     */
    static List<Method> getJunit3Methods(Class<?> cls) {
        List<Method> mtds = new ArrayList<Method>();

        for (Method mtd : cls.getMethods()) {
            if (mtd.getName().startsWith("test")) {
                mtds.add(mtd);
            }
        }

        return mtds;
    }

    /**
     * @param name Name.
     * @param cls Class.
     * @return JUnit3 suite.
     * @throws InitializationError If any exception occurs.
     */
    @SuppressWarnings("unchecked")
    static TestSuite createJunit3Suite(String name, Class<?> cls) throws InitializationError {
        try {
            Method suiteMtd = cls.getMethod("suite");

            if (!Modifier.isStatic(suiteMtd.getModifiers())) {
                throw new InitializationError(cls.getName() + ".suite() must be static");
            }

            // Invoke static suite method.
            return (TestSuite)suiteMtd.invoke(null);
        }
        catch (InvocationTargetException e) {
            throw new InitializationError(e);
        }
        catch (IllegalAccessException e) {
            throw new InitializationError(e);
        }
        catch (NoSuchMethodException e) {
            if (TestCase.class.isAssignableFrom(cls)) {
                return new TestSuite((Class<? extends TestCase>)cls, name);
            }

            if (TestSuite.class.isAssignableFrom(cls)) {
                return (TestSuite)createJunit3Test(name, cls);
            }

            throw new InitializationError(e);
        }
    }

    /**
     * @param test Test.
     * @return Test description.
     */
    @SuppressWarnings({"IfMayBeConditional"})
    public static Description createJunit3Description(Test test) {
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase)test;

            return Description.createTestDescription(testCase.getClass(), testCase.getName());
        }
        else if (test instanceof TestSuite) {
            TestSuite suite = (TestSuite)test;

            String name = suite.getName() == null ? "" : suite.getName();

            Description desc = Description.createSuiteDescription(name);

            int n = suite.testCount();

            for (int i= 0; i < n; i++) {
                desc.addChild(createJunit3Description(suite.testAt(i)));
            }

            return desc;
        }
        else if (test instanceof JUnit4TestAdapter) {
            Describable adapter = (Describable)test;

            return adapter.getDescription();
        }
        else if (test instanceof TestDecorator) {
            return createJunit3Description(((TestDecorator)test).getTest());
        }
        else {
            // This is the best we can do in this case
            return Description.createSuiteDescription(test.getClass());
        }
    }

    /**
     * @param name Name.
     * @param cls Class.
     * @return Created test case.
     */
    static Test createJunit3Test(String name, Class<?> cls) {
        Constructor<?> constructor;

        try {
            constructor = getJunit3TestConstructor(cls);
        }
        catch (NoSuchMethodException e) {
            return junit3Warning("Class "+ cls.getName() + " has no public constructor TestCase(String name)" +
                " or TestCase() [err=" + toString(e) + ']');
        }

        Object test;

        try {
            if (constructor.getParameterTypes().length == 0) {
                test = constructor.newInstance();

                if (test instanceof TestCase) {
                    ((TestCase)test).setName(name);
                }
                else if (test instanceof TestSuite) {
                    ((TestSuite)test).setName(name);
                }
            }
            else {
                test= constructor.newInstance(name);
            }
        }
        catch (InstantiationException e) {
            return(junit3Warning("Cannot instantiate test case: " + name + " ("+ toString(e) + ')'));
        }
        catch (InvocationTargetException e) {
            return(junit3Warning("Exception in constructor: " + name + " (" + toString(e.getTargetException()) + ')'));
        }
        catch (IllegalAccessException e) {
            return(junit3Warning("Cannot access test case: " + name + " (" + toString(e) + ')'));
        }

        return (Test)test;
    }


    /**
     * Gets a constructor which takes a single String as
     * its argument or a no arg constructor.
     *
     * @param cls Class.
     * @return Constructor.
     * @throws NoSuchMethodException If matching constructor is not found.
     */
    @SuppressWarnings({"UnusedCatchParameter"})
    static Constructor<?> getJunit3TestConstructor(Class<?> cls) throws NoSuchMethodException {
        try {
            return cls.getConstructor(String.class);
        }
        catch (NoSuchMethodException e) {
            // fall through
        }

        return cls.getConstructor();
    }

    /**
     * Returns a test which will fail and log a warning message.
     *
     * @param msg Warning message.
     * @return Test.
     */
    static TestCase junit3Warning(final String msg) {
        return new TestCase("warning") {
            /** {@inheritDoc} */
            @Override protected void runTest() { fail(msg); }
        };
    }

    /**
     * Converts the stack trace into a string.
     *
     * @param t Throwable to convert.
     * @return String presentation of the throwable.
     */
    static String toString(Throwable t) {
        StringWriter stringWriter = new StringWriter();

        PrintWriter writer = new PrintWriter(stringWriter);

        t.printStackTrace(writer);

        return stringWriter.toString();
    }
}
