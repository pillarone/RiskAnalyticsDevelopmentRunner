// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * JUnit 3 serializable test case.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit3SerializableTestCase implements GridJunit3SerializableTest {
    /** */
    private final String name;

    /** */
    private final Class<? extends TestCase> testCls;

    /** */
    private Throwable error;

    /** */
    private Throwable failure;

    /** */
    private byte[] stdOut;

    /** */
    private byte[] stdErr;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient TestCase test;

    /**
     * @param test Test case.
     */
    GridJunit3SerializableTestCase(TestCase test) {
        assert test != null;

        this.test = test;

        name = test.getName();

        testCls = ((GridJunit3TestCaseProxy)test).getGridGainJunit3OriginalTestCase().getClass();
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public Test getTest() {
        // Initialize test on deserialization.
        if (test == null) {
            test = GridJunit3Utils.createTest(name, testCls);
        }

        return test;
    }

    /** {@inheritDoc} */
    @Override public Class<? extends TestCase> getTestClass() {
        return testCls;
    }

    /** {@inheritDoc} */
    @Override public GridJunit3SerializableTestCase findTestCase(TestCase t) {
        return test == t ? this : null;
    }

    /** {@inheritDoc} */
    @Override public void setResult(GridJunit3SerializableTest res) {
        GridJunit3SerializableTestCase test = (GridJunit3SerializableTestCase)res;

        setError(test.getError());
        setFailure(test.getFailure());

        GridJunit3TestCaseProxy proxy = (GridJunit3TestCaseProxy)this.test;

        proxy.setGridGainJunit3Result(test.getStandardOut(), test.getStandardError(), test.getError(),
            test.getFailure());
    }

    /**
     * @param failure Throwable to set.
     */
    void setFailure(Throwable failure) {
        this.failure = failure;
    }

    /**
     * @param error Error to set.
     */
    void setError(Throwable error) {
        this.error = error;
    }

    /**
     * @param stdOut Standard output to set.
     */
    public void setStandardOut(byte[] stdOut) {
        this.stdOut = stdOut;
    }

    /**
     * @param stdErr Standard error output to set.
     */
    public void setStandardError(byte[] stdErr) {
        this.stdErr = stdErr;
    }

    /**
     * @return List of failures that occurred.
     */
    Throwable getFailure() {
        return failure;
    }

    /**
     * @return List of errors that occurred.
     */
    Throwable getError() {
        return error;
    }

    /**
     * @return Standard output.
     */
    public byte[] getStandardOut() {
        return stdOut;
    }

    /**
     * @return Error output.
     */
    public byte[] getStandardError() {
        return stdErr;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJunit3SerializableTestCase.class, this);
    }
}
