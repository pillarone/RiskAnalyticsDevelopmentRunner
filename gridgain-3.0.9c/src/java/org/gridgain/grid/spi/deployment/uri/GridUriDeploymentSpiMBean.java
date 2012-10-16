// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean for {@link GridUriDeploymentSpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to URI deployment SPI configuration.")
public interface GridUriDeploymentSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets temporary directory path.
     *
     * @return Temporary directory path.
     */
    @GridMBeanDescription("Temporary directory path.")
    public String getTemporaryDirectoryPath();

    /**
     * Gets list of URIs that are processed by SPI.
     *
     * @return List of URIs.
     */
    @GridMBeanDescription("List of URIs.")
    public List<String> getUriList();
}
