// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * Convenient thread-safe breaker control. It starts in <tt>on</tt> state, can be tripped and sets into
 * <tt>off</tt> state. It can never go back to <tt>on</tt> state again - as any breakers it cannot
 * be fixed, it can only be replaced. 
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridBreaker extends GridAbsClosure {
    /** */
    private volatile boolean blown;

    /**
     * Trips this breaker.
     * As any breaker - it can't be fixed, and it can only be replaced...
     */
    public void trip() {
        blown = true;
    }

    /** {@inheritDoc} */
    @Override public void apply() {
        trip();
    }

    /**
     * Checks the breaker if it is on.
     *
     * @return {@code true} if breaker is on, {@code false} if it was tripped.
     */
    public boolean isOn() {
        return !blown;
    }

    /**
     * Checks the breaker if it was tripped.
     *
     * @return {@code true} if breaker is tripped, {@code false} otherwise.
     */
    public boolean isOff() {
        return blown;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridBreaker.class, this);
    }
}
