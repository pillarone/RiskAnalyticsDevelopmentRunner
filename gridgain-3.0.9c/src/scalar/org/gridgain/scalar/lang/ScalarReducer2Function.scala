// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */
 
package org.gridgain.scalar.lang

import org.gridgain.grid.lang.GridReducer2

/**
 * Wrapping Scala function for `GridReducer2`.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class ScalarReducer2Function[E1, E2, R](val inner: GridReducer2[E1, E2, R]) extends ((Seq[E1], Seq[E2]) => R) {
    assert(inner != null)

    /**
     * Delegates to passed in grid reducer.
     */
    def apply(s1: Seq[E1], s2: Seq[E2]) = {
        for (e1 <- s1; e2 <- s2) inner.collect(e1, e2)

        inner.apply()
    }
}