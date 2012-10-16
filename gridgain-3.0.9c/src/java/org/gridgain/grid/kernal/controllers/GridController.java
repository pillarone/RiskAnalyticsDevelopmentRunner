// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.util.tostring.*;

/**
 * Controller for enterprise kernal-level functionality.
 */
@GridToStringExclude
public interface GridController {
    /**
     * Indicates whether or not implementation for this controller is provided. Generally
     * community edition does not provides implementations for controller and kernal
     * substitute them with no-op dynamic proxies.
     * <p>
     * This method should be used in those rare use cases when controller method return
     * value. In such cases, the kernal's dynamic proxy will throw exception and to avoid
     * it the caller can call this method to see if implementation is actually provided.
     *
     * @return {@code True} if controller is implemented (Enterprise Edition), {@link false}
     *      otherwise (Community Edition).
     */
    public boolean implemented();

    /**
     * Initializes the state of the controller after its creation.
     * <p>
     * Controller's life cycle is managed by kernal. Controller has to provide
     * a no-arg constructor that will be used to create an instance of the controller.
     * Once instance of the controller is created, this method will be called
     * to initialize its state. When kernal is finished with its startup sequence it
     * will call {@link #afterKernalStart(GridKernalContext)} method to formally "start" the
     * controller. When kernal is about to stop it will call {@link #beforeKernalStop(boolean)}
     * method to "stop" the controller.
     *
     * @throws GridException Thrown in case of any errors.
     */
    public void init() throws GridException;

    /**
     * Callback that notifies that kernal has successfully started,
     * including all managers, processors and controllers.
     * <p>
     * Controller's life cycle is managed by kernal. Controller has to provide
     * a no-arg constructor that will be used to create an instance of the controller.
     * Once instance of the controller is created, method {@link #init()} will be called
     * to initialize its state. When kernal is finished with its startup sequence it
     * will call this method to formally "start" the controller. When kernal is about to
     * stop it will call {@link #beforeKernalStop(boolean)} method to "stop" the controller.
     *
     * @param ctx Kernal context.
     * @throws GridException Thrown in case of any errors.
     */
    public void afterKernalStart(GridKernalContext ctx) throws GridException;

    /**
     * Callback to notify that kernal is about to stop.
     * <p>
     * Controller's life cycle is managed by kernal. Controller has to provide
     * a no-arg constructor that will be used to create an instance of the controller.
     * Once instance of the controller is created, method {@link #init()} will be called
     * to initialize its state. When kernal is finished with its startup sequence it
     * will call {@link #afterKernalStart(GridKernalContext)} method to formally "start" the controller.
     * When kernal is about to stop it will call this method to "stop" the controller.
     *
     * @param cancel Flag indicating whether jobs should be canceled.
     */
    public void beforeKernalStop(boolean cancel);
}
