// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.util.test.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * JUnit 4 class runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4ClassRunner extends GridJunit4Runner {
    /** */
    private final Class<?> cls;

    /** */
    private final Map<String, GridJunit4Result> resMap = new HashMap<String, GridJunit4Result>();

    /** */
    private transient List<Method> testMtds;

    /** */
    private transient Description desc;

    /**
     * @param cls Runner class.
     */
    GridJunit4ClassRunner(Class<?> cls) {
        this.cls = cls;
    }

    /** {@inheritDoc} */
    @Override public final Description getDescription() {
        if (desc == null) {
            desc = createDescription();
        }

        return desc;
    }

    /**
     * @return Description for test class.
     */
    protected Description createDescription() {
        Description desc = Description.createSuiteDescription(cls.getName(), cls.getAnnotations());

        for (Method mtd : getTestMethods()) {
            desc.addChild(getDescription(mtd));
        }

        return desc;
    }

    /** {@inheritDoc} */
    @Override public void run(RunNotifier notifier) {
        if (!getTestMethods().isEmpty()) {
            for (Method mtd : getTestMethods()) {
                Description desc = getDescription(mtd);

                notifier.fireTestStarted(desc);

                try {
                    // Wait for results.
                    GridJunit4Result res;

                    synchronized (resMap) {
                        while (resMap.isEmpty()) {
                            try {
                                resMap.wait(5000);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();

                                notifier.fireTestFailure(new Failure(desc, e));

                                return;
                            }
                        }

                        res = resMap.get(desc.getDisplayName());
                    }

                    try {
                        if (res.getStdOut() != null) {
                            GridTestPrintStreamFactory.getStdOut().write(res.getStdOut());
                        }

                        if (res.getStdErr() != null) {
                            GridTestPrintStreamFactory.getStdErr().write(res.getStdErr());
                        }

                        if (res.getFailure() != null) {
                            notifier.fireTestFailure(new Failure(desc, res.getFailure()));
                        }

                        if (res.isIgnored()) {
                            notifier.fireTestIgnored(desc);
                        }
                    }
                    catch (IOException e) {
                        notifier.fireTestFailure(new Failure(desc, e));
                    }
                }
                finally {
                    notifier.fireTestFinished(desc);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override Class<?> getTestClass() {
        return cls;
    }

    /** {@inheritDoc} */
    @Override void copyResults(GridJunit4Runner runner) {
        synchronized (resMap) {
            GridJunit4ClassRunner resRunner = (GridJunit4ClassRunner)runner;

            resMap.putAll(resRunner.resMap);

            resMap.notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override boolean setResult(GridJunit4Result res) {
        for (Method testMtd : getTestMethods()) {
            if (getDescription(testMtd).getDisplayName().equals(res.getName())) {
                addResult(res.getName(), res);

                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override boolean setResult(List<GridJunit4Result> res) {
        synchronized(resMap) {
            for (GridJunit4Result result : res) {
                if (!setResult(result)) {
                    return false;
                }
            }

            resMap.notifyAll();
        }

        return true;
    }

    /**
     * @param name Name.
     * @param res New result.
     * @return Previous result.
     */
    private GridJunit4Result addResult(String name, GridJunit4Result res) {
        return resMap.put(name, res);
    }

    /**
     * @return Methods annotated with given annotation.
     */
    private Collection<Method> getTestMethods() {
        if (testMtds == null) {
            testMtds = GridJunit4Utils.getAnnotatedMethods(cls, Test.class);
        }

        return testMtds;
    }

    /**
     * @param mtd Method to get description for.
     * @return Method description.
     */
    protected Description getDescription(Method mtd) {
        return Description.createTestDescription(cls, mtd.getName(), mtd.getAnnotations());
    }
}
