// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid;

/**
 * Base interface for all cloud enabled algorithms such as strategies and policies.
 * This interface provides common semantic for activation and deactivation as well
 * as the check on whether or not given entity is enabled for a particular cloud ID.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudEnabled extends GridMetadataAware {
    /**
     * Called on cloud coordinator start.
     *
     * @throws GridException If start is failed.
     */
    public void activate() throws GridException;

    /**
     * Called on cloud coordinator stop.
     */
    public void deactivate();

    /**
     * Checks if it should be enabled for the specified cloud.
     *
     * @param cloudId Cloud id.
     * @return {@code true} if it should be enabled, {@code false} otherwise.
     */
    public boolean isEnabledFor(String cloudId);
}
