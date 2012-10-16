// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit3;

import junit.framework.*;

/**
 * Proxy interface for local tests.
 * <p>
 * Note that this interface must be declared {@code public} in order for
 * JavaAssist to work.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
/*@hide.from.javadoc*/public interface GridJunit3TestCaseProxy {
    /*
     * This class public only due to limitation/design of JUnit3 framework.
     */

    /**
     * @return Original test case.
     */
    TestCase getGridGainJunit3OriginalTestCase();

    /**
     * @param stdOut Standard output in serialized form.
     * @param errOut Standard error output in serialized form.
     * @param error Optional error.
     * @param failure Optional failure.
     */
    void setGridGainJunit3Result(byte[] stdOut, byte[] errOut, Throwable error, Throwable failure);
}
