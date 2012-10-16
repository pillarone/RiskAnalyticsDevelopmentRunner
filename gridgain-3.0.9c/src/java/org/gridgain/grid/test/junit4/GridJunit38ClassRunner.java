// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import junit.framework.*;
import org.gridgain.grid.util.test.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.io.*;
import java.util.*;

/**
 * JUnit class runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit38ClassRunner extends GridJunit4Runner {
    /** */
    private final String name;

    /** */
    private final Class<?> cls;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridJunit4Result res;

    /** */
    private transient TestCase test;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private transient Description desc;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient Object mux = new Object();

    /**
     * @param cls Runner class.
     */
    GridJunit38ClassRunner(Class<?> cls) {
        this.cls = cls;

        name = cls.getName();
    }

    /**
     * @param test Test.
     */
    GridJunit38ClassRunner(TestCase test) {
        cls = test.getClass();

        name = Description.createTestDescription(test.getClass(), test.getName()).getDisplayName();

        this.test = test;
    }

    /**
     * @return Gets test case for given class.
     */
    private Test getTest() {
        if (test == null) {
            test = (TestCase)GridJunit4Utils.createJunit3Test(name, getTestClass());
        }

        return test;
    }

    /** {@inheritDoc} */
    @Override Class<?> getTestClass() {
        return cls;
    }

    /** {@inheritDoc} */
    @Override void copyResults(GridJunit4Runner runner) {
        GridJunit38ClassRunner resRunner =  (GridJunit38ClassRunner)runner;

        if (resRunner.name.equals(name)) {
            synchronized (mux) {
                res = resRunner.res;

                mux.notifyAll();
            }
        }
    }

    /** {@inheritDoc} */
    @Override boolean setResult(GridJunit4Result res) {
        if (name.equals(res.getName())) {
            this.res = res;

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override boolean setResult(List<GridJunit4Result> res) {
        assert res.size() == 1;

        for (GridJunit4Result result : res) {
            if (!setResult(result)) {
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public Description getDescription() {
        if (desc == null) {
            desc = GridJunit4Utils.createJunit3Description(getTest());
        }

        return desc;
    }

    /** {@inheritDoc} */
    @Override public void run(RunNotifier notifier) {
        notifier.fireTestStarted(desc);

        try {
            // Wait for results.
            synchronized (mux) {
                while (true) {
                    // This condition is taken out of the loop to avoid
                    // potentially wrong optimization by the compiler of
                    // moving field access out of the loop causing this loop
                    // to never exit.
                    if (res != null) {
                        break;
                    }

                    try {
                        mux.wait(5000);
                    }
                    catch (InterruptedException e) {
                        notifier.fireTestFailure(new Failure(desc, e));

                        return;
                    }
                }
            }

            try {
                GridTestPrintStreamFactory.getStdOut().write(res.getStdOut());
                GridTestPrintStreamFactory.getStdErr().write(res.getStdErr());

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
