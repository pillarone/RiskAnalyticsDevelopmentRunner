// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors;

import org.gridgain.grid.kernal.controllers.rest.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.closure.*;
import org.gridgain.grid.kernal.processors.email.*;
import org.gridgain.grid.kernal.processors.job.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.kernal.processors.resource.*;
import org.gridgain.grid.kernal.processors.rich.*;
import org.gridgain.grid.kernal.processors.schedule.*;
import org.gridgain.grid.kernal.processors.session.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;

/**
 * This class provides centralized registry for kernal processors.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridProcessorRegistry implements Iterable<GridProcessor> {
    /** */
    @GridToStringInclude
    private GridTaskProcessor taskProc;

    /** */
    @GridToStringInclude
    private GridJobProcessor jobProc;

    /** */
    @GridToStringInclude
    private GridTimeoutProcessor timeProc;

    /** */
    @GridToStringInclude
    private GridResourceProcessor rsrcProc;

    /** */
    @GridToStringInclude
    private GridJobMetricsProcessor metricsProc;

    /** */
    @GridToStringInclude
    private GridClosureProcessor closureProc;

    /** */
    @GridToStringInclude
    private GridCacheProcessor cacheProc;

    /** */
    @GridToStringInclude
    private GridTaskSessionProcessor sesProc;

    /** */
    @GridToStringInclude
    private GridPortProcessor portProc;

    /** */
    @GridToStringInclude
    private GridEmailProcessor emailProc;

    /** */
    @GridToStringInclude
    private GridRichProcessor richProc;

    /** */
    @GridToStringInclude
    private GridScheduleProcessor scheduleProc;

    /** */
    @GridToStringInclude
    private GridRestController restProc;

    /** */
    private List<GridProcessor> procs = new LinkedList<GridProcessor>();

    /**
     * @param proc Processor to add.
     */
    public void add(GridProcessor proc) {
        assert proc != null;

        if (proc instanceof GridTaskProcessor)
            taskProc = (GridTaskProcessor)proc;
        else if (proc instanceof GridJobProcessor)
            jobProc = (GridJobProcessor)proc;
        else if (proc instanceof GridTimeoutProcessor)
            timeProc = (GridTimeoutProcessor)proc;
        else if (proc instanceof GridResourceProcessor)
            rsrcProc = (GridResourceProcessor)proc;
        else if (proc instanceof GridJobMetricsProcessor)
            metricsProc = (GridJobMetricsProcessor)proc;
        else if (proc instanceof GridCacheProcessor)
            cacheProc = (GridCacheProcessor)proc;
        else if (proc instanceof GridTaskSessionProcessor)
            sesProc = (GridTaskSessionProcessor)proc;
        else if (proc instanceof GridPortProcessor)
            portProc = (GridPortProcessor)proc;
        else if (proc instanceof GridEmailProcessor)
            emailProc = (GridEmailProcessor)proc;
        else if (proc instanceof GridClosureProcessor)
            closureProc = (GridClosureProcessor)proc;
        else if (proc instanceof GridRichProcessor)
            richProc = (GridRichProcessor)proc;
        else if (proc instanceof GridScheduleProcessor)
            scheduleProc = (GridScheduleProcessor)proc;
        else if (proc instanceof GridRestController)
            restProc = (GridRestController)proc;
        else
            assert false : "Unknown processor class: " + proc.getClass();

        procs.add(proc);
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridProcessor> iterator() {
        return procs.iterator();
    }

    /**
     * @return Task processor
     */
    public GridTaskProcessor task() {
        return taskProc;
    }

    /**
     * @return Job processor
     */
    public GridJobProcessor job() {
        return jobProc;
    }

    /**
     * @return Rich processor
     */
    public GridRichProcessor rich() {
        return richProc;
    }

    /**
     * @return Timeout processor.
     */
    public GridTimeoutProcessor timeout() {
        return timeProc;
    }

    /**
     * @return Resource processor.
     */
    public GridResourceProcessor resource() {
        return rsrcProc;
    }

    /**
     * @return Metrics processor.
     */
    public GridJobMetricsProcessor metric() {
        return metricsProc;
    }

    /**
     * @return Cache processor.
     */
    public GridCacheProcessor cache() {
        return cacheProc;
    }

    /**
     * @return Session processor.
     */
    public GridTaskSessionProcessor session() {
        return sesProc;
    }

    /**
     * @return Closure processor.
     */
    public GridClosureProcessor closure() {
        return closureProc;
    }

    /**
     * @return Port processor.
     */
    public GridPortProcessor ports() {
        return portProc;
    }

    /**
     * @return Email processor.
     */
    public GridEmailProcessor email() {
        return emailProc;
    }

    /**
     * @return Schedule processor.
     */
    public GridScheduleProcessor schedule() {
        return scheduleProc;
    }

    /**
     * @return Rest processor.
     */
    public GridRestController rest() {
        return restProc;
    }

    /**
     * @return All processors.
     */
    public List<GridProcessor> processors() {
        return procs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridProcessorRegistry.class, this);
    }
}
