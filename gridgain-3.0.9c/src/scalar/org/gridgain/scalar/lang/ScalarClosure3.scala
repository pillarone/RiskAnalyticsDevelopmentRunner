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

import org.gridgain.grid.lang.GridClosure3
import org.gridgain.grid.util.{GridUtils => U}

/**
 * Peer deploy aware adapter for Java's `GridClosure3`.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class ScalarClosure3[E1, E2, E3, R](private val f: (E1, E2, E3) => R) extends GridClosure3[E1, E2, E3, R] {
    assert(f != null)

    peerDeployLike(U.peerDeployAware(f))

    /**
     * Delegates to passed in function.
     */
    def apply(e1: E1, e2: E2, e3: E3) = f(e1, e2, e3)
}