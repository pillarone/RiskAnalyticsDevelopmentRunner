// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Cloud listener allows SPI to "communicate" with cloud manager. Cloud manager
 * manages multiple cloud SPI instances as well as "implement" coordinator logic.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudSpiListener {
    /**
     * Callback on change in the cloud state.
     *
     * @param snapshot Cloud state snapshot that represents the current (changed) state
     *      of the cloud.
     */
    public void onChange(GridCloudSpiSnapshot snapshot);

    /**
     * Callback on completed cloud command.
     *
     * @param success Whether or not command completed successfully.
     * @param cmdExecId Command execution ID.
     * @param cmd Command itself.
     * @param msg Optional message provided by cloud provider for this command. In case of command
     *      failure this could be the error message.
     */
    public void onCommand(boolean success, UUID cmdExecId, GridCloudCommand cmd, @Nullable String msg);
}
