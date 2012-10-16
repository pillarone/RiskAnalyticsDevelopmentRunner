// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.test.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.io.*;

/**
 * JUnit 4 job..
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"ParameterNameDiffersFromOverriddenParameter"})
class GridJunit4Job extends GridJobAdapterEx {
    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /**
     * @param arg Argument.
     */
    GridJunit4Job(GridJunit4Runner arg) {
        super(arg);

        assert arg != null;
    }

    /** {@inheritDoc} */
    @Override public GridJunit4Runner execute() throws GridException {
        final GridJunit4Runner runner = argument();

        assert runner != null;

        JUnitCore core = new JUnitCore();

        core.addListener(new RunListener() {
            /** */
            private GridTestPrintStream out;

            /** */
            private GridTestPrintStream err;

            /** */
            private Throwable failure;

            /** {@inheritDoc} */
            @Override public void testStarted(Description desc) throws Exception {
                GridTestPrintStreamFactory.getStdOut().println("Distributed test started: " + desc);

                out = GridTestPrintStreamFactory.acquireOut();
                err = GridTestPrintStreamFactory.acquireErr();
            }

            /** {@inheritDoc} */
            @Override public void testFinished(Description desc) throws Exception {
                try {
                    runner.setResult(new GridJunit4Result(desc.getDisplayName(), getBytes(out), getBytes(err),
                        failure));
                }
                catch (IOException e) {
                    U.error(log, "Error resetting output.", e);
                }

                GridTestPrintStreamFactory.releaseOut();
                GridTestPrintStreamFactory.releaseErr();

                out = null;
                err = null;
                failure = null;

                GridTestPrintStreamFactory.getStdOut().println("Distributed test finished: " + desc);
            }

            /** {@inheritDoc} */
            @Override public void testFailure(Failure failure) throws Exception {
                this.failure = failure.getException();
            }

            /** {@inheritDoc} */
            @Override public void testIgnored(Description desc) throws Exception {
                GridJunit4Result res = new GridJunit4Result(desc.getDisplayName());

                res.setIgnored(true);

                runner.setResult(res);
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
        });

        core.run(runner.getTestClass());

        return runner;
    }
}
