// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */
package org.gridgain.grid.test.junit4;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;

/**
 * Junit4 test result.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4Result implements Serializable {
    /** */
    private final String name;

    /** */
    private byte[] stdOut;

    /** */
    private byte[] stdErr;

    /** */
    private boolean ignored;

    /** */
    private Throwable failure;

    /**
     * @param name Test name.
     */
    GridJunit4Result(String name) {
        assert name != null;

        this.name = name;
    }

    /**
     * @param name Test name
     * @param stdOut Standard output from test.
     * @param stdErr Standard error from test.
     * @param failure Test failure.
     */
    GridJunit4Result(String name, byte[] stdOut, byte[] stdErr, Throwable failure) {
        assert name != null;

        this.name = name;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
        this.failure = failure;
    }

    /**
     * @return Test name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Standard out.
     */
    public byte[] getStdOut() {
        return stdOut;
    }

    /**
     * @param stdOut Standard out.
     */
    public void setStdOut(byte[] stdOut) {
        this.stdOut = stdOut;
    }

    /**
     * @return Standard error.
     */
    public byte[] getStdErr() {
        return stdErr;
    }

    /**
     * @param stdErr Standard error.
     */
    public void setStdErr(byte[] stdErr) {
        this.stdErr = stdErr;
    }

    /**
     * @return Test failure.
     */
    public Throwable getFailure() {
        return failure;
    }

    /**
     * @param failure Test failure.
     */
    public void setFailure(Throwable failure) {
        this.failure = failure;
    }

    /**
     * @return {@code True} if test is ignored.
     */
    public boolean isIgnored() {
        return ignored;
    }

    /**
     * @param ignored Ignored test flag.
     */
    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJunit4Result.class, this);
    }
}
