// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.util.*;

/**
 * Job sibling interface defines a job from the same split. In other words a sibling is a job returned
 * from the same {@link GridTask#map(List, Object)} method invocation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridJobSibling extends GridMetadataAware {
    /**
     * Gets ID of this grid job sibling. Note that ID stays constant
     * throughout job life time, even if a job gets failed over to another
     * node.
     *
     * @return Job ID.
     */
    public UUID getJobId();

    /**
     * Sends a request to cancel this sibling.
     *
     * @throws GridException If cancellation message could not be sent.
     */
    public void cancel() throws GridException;
}
