// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.util.mbean.*;

/**
 * This interface defines JMX view on {@link GridFactory}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to grid life-cycle operations.")
public interface GridFactoryMBean {
    /**
     * Gets state of default grid instance.
     *
     * @return State of default grid instance.
     * @see GridFactory#getState()
     */
    @GridMBeanDescription("State of default grid instance.")
    public String getState();

    /**
     * Gets state for a given grid instance.
     *
     * @param name Name of grid instance.
     * @return State of grid instance with given name.
     * @see GridFactory#getState(String)
     */
    @GridMBeanDescription("Gets state for a given grid instance. Returns state of grid instance with given name.")
    @GridMBeanParametersNames(
        "name"
    )
    @GridMBeanParametersDescriptions(
        "Name of grid instance."
    )
    public String getState(String name);

    /**
     * Stops default grid instance.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     * @see GridFactory#stop(boolean)
     */
    @GridMBeanDescription("Stops default grid instance. Return true if default grid instance was " +
        "indeed stopped, false otherwise (if it was not started).")
    @GridMBeanParametersNames(
        "cancel"
    )
    @GridMBeanParametersDescriptions(
        "If true then all jobs currently executing on default grid will be cancelled."
    )
    public boolean stop(boolean cancel);

    /**
     * Stops named grid. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * grid name is {@code null}, then default no-name grid will be stopped.
     * It does not wait for the tasks to finish their execution.
     *
     * @param name Grid name. If {@code null}, then default no-name grid will
     *      be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link GridJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @return {@code true} if named grid instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     * @see GridFactory#stop(String, boolean)
     */
    @GridMBeanDescription("Stops grid by name. Cancels running jobs if cancel is true. Returns true if named " +
        "grid instance was indeed found and stopped, false otherwise.")
    @GridMBeanParametersNames(
        {
            "name",
            "cancel"
        })
    @GridMBeanParametersDescriptions(
        {
            "Grid instance name to stop.",
            "Whether or not running jobs should be cancelled."
        }
    )
    public boolean stop(String name, boolean cancel);

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * It does not wait for the tasks to finish their execution.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @see GridFactory#stopAll(boolean)
     */
    @GridMBeanDescription("Stops all started grids.")
    @GridMBeanParametersNames(
        "cancel"
    )
    @GridMBeanParametersDescriptions(
        "If true then all jobs currently executing on all grids will be cancelled."
    )
    public void stopAll(boolean cancel);

    /**
     * Stops default grid. This method is identical to {@code G.stop(null, cancel, wait)} apply.
     * If wait parameter is set to {@code true} then it will wait for all
     * tasks to be finished.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @param wait If {@code true} then method will wait for all task being
     *      executed until they finish their execution.
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     * @see GridFactory#stop(boolean, boolean)
     */
    @GridMBeanDescription("Stops default grid. Return true if default grid instance was indeed " +
        "stopped, false otherwise (if it was not started).")
    @GridMBeanParametersNames(
        {
            "cancel",
            "wait"
        })
    @GridMBeanParametersDescriptions(
        {
            "If true then all jobs currently executing on default grid will be cancelled.",
            "If true then method will wait for all task being executed until they finish their execution."
        }
    )
    public boolean stop(boolean cancel, boolean wait);

    /**
     * Stops named grid. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * grid name is {@code null}, then default no-name grid will be stopped.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     *
     * @param name Grid name. If {@code null}, then default no-name grid will
     *      be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link GridJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @param wait If {@code true} then method will wait for all task being
     *      executed until they finish their execution.
     * @return {@code true} if named grid instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     * @see GridFactory#stop(String, boolean, boolean)
     */
    @GridMBeanDescription("Stops named grid.Return true  if named grid instance was indeed stopped, " +
        "false otherwise (if it was not started).")
    @GridMBeanParametersNames(
        {
            "name",
            "cancel",
            "wait"
        })
    @GridMBeanParametersDescriptions(
        {
            "Grid name. If null, then default no-name grid will be stopped.",
            "If true then all jobs currently executing on default grid will be cancelled.",
            "If true then method will wait for all task being executed until they finish their execution."
        }
    )
    public boolean stop(String name, boolean cancel, boolean wait);

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @param wait If {@code true} then method will wait for all task being
     *      executed until they finish their execution.
     * @see GridFactory#stopAll(boolean, boolean)
     */
    @GridMBeanDescription("Stops all started grids.")
    @GridMBeanParametersNames(
        {
            "cancel",
            "wait"
        })
    @GridMBeanParametersDescriptions(
        {
            "If true then all jobs currently executing on default grid will be cancelled.",
            "If true then method will wait for all task being executed until they finish their execution."
        }
    )
    public void stopAll(boolean cancel, boolean wait);

    /**
     * // TODO: add javadoc.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link GridJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @param wait If {@code true} then method will wait for all task being
     *      executed until they finish their execution.
     * @see GridFactory#stopAll(boolean, boolean)
     */
    @GridMBeanDescription("Restart JVM.")
    @GridMBeanParametersNames(
        {
            "cancel",
            "wait"
        })
    @GridMBeanParametersDescriptions(
        {
            "If true then all jobs currently executing on default grid will be cancelled.",
            "If true then method will wait for all task being executed until they finish their execution."
        }
    )
    public void restart(boolean cancel, boolean wait);
}
