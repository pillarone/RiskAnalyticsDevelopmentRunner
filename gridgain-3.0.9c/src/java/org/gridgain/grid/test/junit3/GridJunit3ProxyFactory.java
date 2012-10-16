// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import javassist.util.proxy.*;
import junit.framework.*;
import org.gridgain.grid.util.test.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Test case proxy factory.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3ProxyFactory {
    /** */
    private static final Map<Class<? extends TestCase>, Class<? extends TestCase>> cache =
        new HashMap<Class<? extends TestCase>, Class<? extends TestCase>>();

    /**
     * @param testCase Test case.
     * @return Proxy test case that simulates local execution.
     */
    @SuppressWarnings({"unchecked"})
    TestCase createProxy(final TestCase testCase) {
        Class<? extends TestCase> proxyCls;

        // Cache proxy classes to avoid redundant class creation.
        synchronized (cache) {
            proxyCls = cache.get(testCase.getClass());

            if (proxyCls == null) {
                ProxyFactory factory = new ProxyFactory() {
                    @Override protected ClassLoader getClassLoader() { return getClass().getClassLoader(); }
                };

                factory.setSuperclass(testCase.getClass());

                factory.setInterfaces(new Class[] { GridJunit3TestCaseProxy.class });

                factory.setFilter(new MethodFilter() {
                    @Override public boolean isHandled(Method m) {
                        return "runBare".equals(m.getName()) || m.getName().startsWith("getGridGain") ||
                            m.getName().startsWith("setGridGain");
                    }
                });

                cache.put(testCase.getClass(), proxyCls = factory.createClass());
            }
        }

        MethodHandler handler = new MethodHandler() {
            /** */
            private byte[] stdOut;

            /** */
            private byte[] stdErr;

            /** */
            private Throwable error;

            /** */
            private Throwable failure;

            /** {@inheritDoc} */
            @Override public Object invoke(Object self, Method m, Method proceed, Object[] args) throws Throwable {
                if ("getGridGainJunit3OriginalTestCase".equals(m.getName())) {
                    return testCase;
                }

                if ("runBare".equals(m.getName())) {
                    GridTestPrintStreamFactory.getStdOut().write(stdOut);
                    GridTestPrintStreamFactory.getStdErr().write(stdErr);

                    if (error != null) {
                        throw error;
                    }

                    if (failure != null) {
                        throw failure;
                    }
                }
                else if ("setGridGainJunit3Result".equals(m.getName())) {
                    stdOut = (byte[])args[0];
                    stdErr = (byte[])args[1];
                    error = (Throwable)args[2];
                    failure = (Throwable)args[3];
                }

                return null;
            }
        };

        TestCase test = GridJunit3Utils.createTest(testCase.getName(), proxyCls);

        ((ProxyObject)test).setHandler(handler);

        return test;
    }
}
