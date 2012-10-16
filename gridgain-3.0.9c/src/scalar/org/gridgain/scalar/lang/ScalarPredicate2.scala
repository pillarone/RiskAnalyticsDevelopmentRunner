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

import org.gridgain.grid.util.{GridUtils => U}
import org.gridgain.grid.lang.GridPredicate2

/**
 * Peer deploy aware adapter for Java's `GridPredicate2`.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class ScalarPredicate2[T1, T2](private val p: (T1, T2) => Boolean) extends GridPredicate2[T1, T2] {
    assert(p != null)

    peerDeployLike(U.peerDeployAware(p))

    /**
     * Delegates to passed in function.
     */
    def apply(e1: T1, e2: T2) = p(e1, e2)
}