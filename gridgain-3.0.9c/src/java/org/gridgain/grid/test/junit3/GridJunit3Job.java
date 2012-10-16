// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.test.*;
import java.io.*;

/**
 * JUnit 3 job.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3Job extends GridJobAdapterEx {
    /** */
    @GridLoggerResource
    private GridLogger log;

    /**
     * @param arg JUnit3 test.
     */
    GridJunit3Job(GridJunit3SerializableTest arg) {
        super(arg);
    }

    /** {@inheritDoc} */
    @Override public GridJunit3SerializableTest execute() throws GridException {
        final GridJunit3SerializableTest testArg = argument();

        assert testArg != null;

        TestResult collector = new TestResult();

        collector.addListener(new TestListener() {
            /** */
            private GridTestPrintStream out;

            /** */
            private GridTestPrintStream err;

            /** {@inheritDoc} */
            @Override public void addError(Test test, Throwable e) {
                assert test instanceof TestCase : "Errors can be added only to TestCases: " + test;

                testArg.findTestCase((TestCase)test).setError(e);
            }

            /** {@inheritDoc} */
            @Override public void addFailure(Test test, AssertionFailedError e) {
                assert test instanceof TestCase : "Failures can be added only to TestCases: " + test;

                testArg.findTestCase((TestCase)test).setFailure(e);
            }

            /** {@inheritDoc} */
            @Override public void startTest(Test test) {
                GridTestPrintStreamFactory.getStdOut().println("Distributed test started: " + getTestName(test));

                out = GridTestPrintStreamFactory.acquireOut();
                err = GridTestPrintStreamFactory.acquireErr();
            }

            /** {@inheritDoc} */
            @Override public void endTest(Test test) {
                GridJunit3SerializableTestCase testCase = testArg.findTestCase((TestCase)test);

                try {
                    testCase.setStandardOut(getBytes(out));
                    testCase.setStandardError(getBytes(err));
                }
                catch (IOException e) {
                    U.error(log, "Error resetting output.", e);

                    if (testCase.getError() == null) {
                        testCase.setError(e);
                    }
                    else if (testCase.getFailure() == null) {
                        testCase.setFailure(e);
                    }
                    else {
                        // Override initial error.
                        testCase.setError(e);
                    }
                }

                GridTestPrintStreamFactory.releaseOut();
                GridTestPrintStreamFactory.releaseErr();

                out = null;
                err = null;

                GridTestPrintStreamFactory.getStdOut().println("Distributed test finished: " + getTestName(test));
            }

            /**
             * @param out Output stream to gen bytes.
             * @return Output bytes.
             * @throws IOException If error occur.
             */
            private byte[] getBytes(GridTestPrintStream out) throws IOException {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

                out.purge(byteOut);

                return byteOut.toByteArray();
            }

            /**
             * @param test JUnit3 test.
             * @return Test name.
             */
            private String getTestName(Test test) {
                if (test instanceof TestSuite) {
                    return ((TestSuite)test).getName();
                }

                return test.getClass().getName() + '.' + ((TestCase)test).getName();
            }
        });

        // Run Junits.
        testArg.getTest().run(collector);

        return testArg;
    }
}
