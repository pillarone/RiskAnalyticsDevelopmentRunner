// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.jobstealing;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.io.*;
import java.util.*;

/**
 * Management MBean for job stealing based collision SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean for job stealing based collision SPI.")
public interface GridJobStealingCollisionSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets current number of jobs to be stolen. This is outstanding
     * requests number.
     *
     * @return Number of jobs to be stolen.
     */
    @GridMBeanDescription("Number of jobs to be stolen.")
    public int getCurrentJobsToStealCount();

    /**
     * Gets current number of jobs that wait for the execution.
     *
     * @return Number of jobs that wait for execution.
     */
    @GridMBeanDescription("Number of jobs that wait for execution.")
    public int getCurrentWaitJobsCount();

    /**
     * Gets current number of jobs that are being executed.
     *
     * @return Number of active jobs.
     */
    @GridMBeanDescription("Number of active jobs.")
    public int getCurrentActiveJobsCount();

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
     * Gets total number of stolen jobs.
     *
     * @return Number of stolen jobs.
     */
    @GridMBeanDescription("Number of stolen jobs.")
    public int getTotalStolenJobsCount();

    /**
     * Gets number of jobs that can be executed in parallel.
     *
     * @return Number of jobs that can be executed in parallel.
     */
    @GridMBeanDescription("Number of jobs that can be executed in parallel.")
    public int getActiveJobsThreshold();

    /**
     * Gets job count threshold at which this node will
     * start stealing jobs from other nodes.
     *
     * @return Job count threshold.
     */
    @GridMBeanDescription("Job count threshold.")
    public int getWaitJobsThreshold();

    /**
     * Message expire time configuration parameter. If no response is received
     * from a busy node to a job stealing message, then implementation will
     * assume that message never got there, or that remote node does not have
     * this node included into topology of any of the jobs it has.
     *
     * @return Message expire time.
     */
    @GridMBeanDescription("Message expire time.")
    public long getMessageExpireTime();

    /**
     * Gets flag indicating whether this node should attempt to steal jobs
     * from other nodes. If {@code false}, then this node will steal allow
     * jobs to be stolen from it, but won't attempt to steal any jobs from
     * other nodes.
     * <p>
     * Default value is {@code true}.
     *
     * @return Flag indicating whether this node should attempt to steal jobs
     *      from other nodes.
     */
    @GridMBeanDescription("Flag indicating whether this node should attempt to steal jobs from other nodes.")
    public boolean isStealingEnabled();

    /**
     * Gets maximum number of attempts to steal job by another node.
     * If not specified, {@link GridJobStealingCollisionSpi#DFLT_MAX_STEALING_ATTEMPTS}
     * value will be used.
     *
     * @return Maximum number of attempts to steal job by another node.
     */
    @GridMBeanDescription("Maximum number of attempts to steal job by another node.")
    public int getMaximumStealingAttempts();

    /**
     * Configuration parameter to enable stealing to/from only nodes that
     * have these attributes set (see {@link GridNode#getAttribute(String)} and
     * {@link GridConfiguration#getUserAttributes()} methods).
     *
     * @return Node attributes to enable job stealing for.
     */
    @GridMBeanDescription("Node attributes to enable job stealing for.")
    public Map<String, ? extends Serializable> getStealingAttributes();
}
