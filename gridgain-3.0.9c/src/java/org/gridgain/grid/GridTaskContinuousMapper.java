// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.resources.*;
import java.util.*;

/**
 * Defines a mapper that can be used for asynchronous job sending.
 * <p>
 * Continuous mapper methods can be used right after it injected into a task.
 * Mapper can not be used after {@link GridTask#result(GridJobResult, List)}
 * method returned the {@link GridJobResultPolicy#REDUCE} policy. Also if
 * {@link GridTask#result(GridJobResult, List)} method returned the
 * {@link GridJobResultPolicy#WAIT} policy and all jobs are finished then task
 * will go to reducing results and continuous mapper can not be used.
 * <p>
 * Note that whenever continuous mapper is used, {@link GridTask#map(List, Object)}
 * method is allowed to return {@code null} in cased when at least one job
 * has been sent prior to completing the {@link GridTask#map(List, Object)} method.
 * <p>
 * Task continuous mapper can be injected into a task using IoC (dependency
 * injection) by attaching {@link GridTaskContinuousMapperResource}
 * annotation to a field or a setter method inside of {@link GridTask} implementations
 * as follows:
 * <pre name="code" class="java">
 * ...
 * // This field will be injected with task continuous mapper.
 * &#64GridTaskContinuousMapperResource
 * private GridTaskContinuousMapper mapper;
 * ...
 * </pre>
 * or from a setter method:
 * <pre name="code" class="java">
 * // This setter method will be automatically called by the system
 * // to set grid task continuous mapper.
 * &#64GridTaskContinuousMapperResource
 * void setSession(GridTaskContinuousMapper mapper) {
 *     this.mapper = mapper;
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTaskContinuousMapper {
    /**
     * Sends given job to a specific grid node.
     *
     * @param job Job instance to send. If {@code null} is passed, exception will be thrown.
     * @param node Grid node. If {@code null} is passed, exception will be thrown.
     * @throws GridException If job can not be processed.
     */
    public void send(GridJob job, GridNode node) throws GridException;

    /**
     * Sends collection of grid jobs to assigned nodes.
     *
     * @param mappedJobs Map of grid jobs assigned to grid node. If {@code null}
     *      or empty list is passed, exception will be thrown.
     * @throws GridException If job can not be processed.
     */
    public void send(Map<? extends GridJob, GridNode> mappedJobs) throws GridException;

    /**
     * Sends job to a node automatically picked by the underlying load balancer.
     *
     * @param job Job instance to send. If {@code null} is passed, exception will be thrown.
     * @throws GridException If job can not be processed.
     */
    public void send(GridJob job) throws GridException;

    /**
     * Sends collection of jobs to nodes automatically picked by the underlying load balancer.
     *
     * @param jobs Collection of grid job instances. If {@code null} or empty
     *      list is passed, exception will be thrown.
     * @throws GridException If job can not be processed.
     */
    public void send(Collection<? extends GridJob> jobs) throws GridException;
}
