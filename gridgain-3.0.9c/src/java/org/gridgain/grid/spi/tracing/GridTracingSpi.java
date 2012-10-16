// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.tracing;

import org.gridgain.grid.spi.*;
import org.jetbrains.annotations.*;

/**
 * SPI provides pluggable tracing facility for GridGain. System runtime intercepts main
 * implementation methods and notifies this SPI. Implementation of this SPI should provide
 * any necessary processing of interception callbacks like collecting statistics, searching
 * for patterns, passing further to external monitoring console, etc.
 * <p>
 * GridGain comes with one default implementation:
 * <ul>
 *      <li>
 *          {@link org.gridgain.grid.spi.tracing.jxinsight.GridJxinsightTracingSpi} - receives method apply notifications from local grid
 *          and informs JXInsight Tracer.
 *      </li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTracingSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * This callback is called right before target method interception.
     *
     * @param cls Callee class.
     * @param methodName Callee method name.
     * @param args Callee method parameters.
     */
    public void beforeCall(Class<?> cls, String methodName, Object[] args);

    /**
     * This callback is called right after target method interception.
     *
     * @param cls Callee class.
     * @param methodName Callee method name.
     * @param args Callee method parameters.
     * @param res Call result. Might be {@code null} if apply
     *      returned {@code null} or if exception happened.
     * @param e Exception thrown by given method apply, if any.
     */
    public void afterCall(Class<?> cls, String methodName, Object[] args, @Nullable Object res, Throwable e);
}
