// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.priorityqueue;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides access to the priority queue collision SPI configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean provides access to the priority queue collision SPI.")
public interface GridPriorityQueueCollisionSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets current number of jobs that wait for the execution.
     *
     * @return Number of jobs that wait for execution.
     */
    @GridMBeanDescription("Number of jobs that wait for execution.")
    public int getCurrentWaitJobsNumber();

    /**
     * Gets current number of jobs that are active, i.e. {@code 'running + held'} jobs.
     *
     * @return Number of active jobs.
     */
    @GridMBeanDescription("Number of active jobs.")
    public int getCurrentActiveJobsNumber();

    /*
     * Gets number of currently running (not {@code 'held}) jobs.
     *
     * @return Number of currently running (not {@code 'held}) jobs.
     */
    @GridMBeanDescription("Number of running jobs.")
    public int getCurrentRunningJobsNumber();

    /**
     * Gets number of currently {@code 'held'} jobs.
     *
     * @return Number of currently {@code 'held'} jobs.
     */
    @GridMBeanDescription("Number of held jobs.")
    public int getCurrentHeldJobsNumber();

    /**
     * Gets number of jobs that can be executed in parallel.
     *
     * @return Number of jobs that can be executed in parallel.
     */
    @GridMBeanDescription("Number of jobs that can be executed in parallel.")
    public int getParallelJobsNumber();

    /**
     * Maximum number of jobs that are allowed to wait in waiting queue. If number
     * of waiting jobs ever exceeds this number, excessive jobs will be rejected.
     *
     * @return Maximum allowed number of waiting jobs.
     */
    @GridMBeanDescription("Maximum allowed number of waiting jobs.")
    public int getWaitingJobsNumber();

    /**
     * Gets key name of task priority attribute.
     *
     * @return Key name of task priority attribute.
     */
    @GridMBeanDescription("Key name of task priority attribute.")
    public String getPriorityAttributeKey();

    /**
     * Gets key name of job priority attribute.
     *
     * @return Key name of job priority attribute.
     */
    @GridMBeanDescription("Key name of job priority attribute.")
    public String getJobPriorityAttributeKey();

    /**
     * Gets default priority to use if a job does not have priority attribute
     * set.
     *
     * @return Default priority to use if a task does not have priority
     *      attribute set.
     */
    @GridMBeanDescription("Default priority to use if a task does not have priority attribute set.")
    public int getDefaultPriority();

    /**
     * Gets value to increment job priority by every time a lower priority job gets
     * behind a higher priority job.
     *
     * @return Value to increment job priority by every time a lower priority job gets
     *      behind a higher priority job.
     */
    @GridMBeanDescription("Value to increment job priority by every time a lower priority job gets behind a higher priority job.")
    public int getStarvationIncrement();

    /**
     * Gets flag indicating whether job starvation prevention is enabled.
     *
     * @return Flag indicating whether job starvation prevention is enabled.
     */
    @GridMBeanDescription("Flag indicating whether job starvation prevention is enabled.")
    public boolean isStarvationPreventionEnabled();
}
