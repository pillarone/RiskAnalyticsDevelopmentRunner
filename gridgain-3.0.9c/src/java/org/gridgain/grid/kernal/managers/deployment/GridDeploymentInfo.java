// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;

import java.util.*;

/**
 * Deployment info.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridDeploymentInfo {
    /**
     * @return Class loader ID.
     */
    public UUID classLoaderId();

    /**
     * @return User version.
     */
    public String userVersion();

    /**
     * @return Deployment mode.
     */
    public GridDeploymentMode deployMode();

    /**
     * @return Participant map for SHARED mode.
     */
    public Map<UUID, GridTuple2<UUID, Long>> participants();
}
