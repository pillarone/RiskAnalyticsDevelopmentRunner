// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import java.io.*;
import java.lang.reflect.*;

/**
 * JUnit 3 utils.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
final class GridJunit3Utils {
    /**
     * Enforces singleton.
     */
    private GridJunit3Utils() {
        // No-op.
    }

    /**
     * @param name Test name.
     * @param cls Test class.
     * @return Created test case.
     */
    static TestCase createTest(String name, Class<? extends TestCase> cls) {
        assert name != null;
        assert cls != null;

        Constructor<?> constructor;

        try {
            constructor = getTestConstructor(cls);
        }
        catch (NoSuchMethodException e) {
            return warning("Class "+ cls.getName() + " has no public constructor TestCase(String name)" +
                " or TestCase() [err=" + toString(e) + ']');
        }

        Object test;

        try {
            if (constructor.getParameterTypes().length == 0) {
                test = constructor.newInstance();

                if (test instanceof TestCase) {
                    ((TestCase)test).setName(name);
                }
            }
            else {
                test = constructor.newInstance(name);
            }
        }
        catch (InstantiationException e) {
            throw new RuntimeException("Cannot instantiate class.", e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException("Exception in constructor: " +
                name + " (" + toString(e.getTargetException()) + ')', e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access test case: " + name + " (" + toString(e) + ')', e);
        }

        return (TestCase)test;
    }

    /**
     * Gets a constructor which takes a single String as
     * its argument or a no arg constructor.
     *
     * @param cls Test class.
     * @return Constructor.
     * @throws NoSuchMethodException If matching constructor is not found.
     */
    static Constructor<?> getTestConstructor(Class<? extends TestCase> cls) throws NoSuchMethodException {
        try {
            return cls.getConstructor(String.class);
        }
        catch (NoSuchMethodException ignored) {
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
    static TestCase warning(final String msg) {
        return new TestCase("warning") {
            /** {@inheritDoc} */
            @Override protected void runTest() {
                fail(msg);
            }
        };
    }

    /**
     * Converts the stack trace into a string.
     *
     * @param t Throwable to convert.
     * @return Stack trace as a string.
     */
    static String toString(Throwable t) {
        StringWriter stringWriter = new StringWriter();

        PrintWriter writer = new PrintWriter(stringWriter);

        t.printStackTrace(writer);

        return stringWriter.toString();
    }
}
