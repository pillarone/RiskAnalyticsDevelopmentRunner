// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.events.*;
import java.util.*;

/**
 * When a cloud resource is removed it leaves a shadow. A resource shadow is a read-only
 * snapshot of its last internal state. Resource shadow is available on {@link GridCloudEvent}
 * when resource is removed.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudResourceShadow extends GridMetadataAware {
    /**
     * Gets type of the resource.
     *
     * @return Resource type.
     * @see GridCloudResourceType
     */
    public int type();

    /**
     * Gets ID of this resource. This is vendor specific value that is provided by the
     * cloud provider.
     *
     * @return Resource ID.
     */
    public String id();

    /**
     * Gets ID of the cloud this resource belongs to.
     *
     * @return Cloud ID.
     */
    public String cloudId();

    /**
     * Gets map of parameters that cloud provides associates with this resource. Depending on
     * cloud SPI implementation this map can change from call to call as it can be re-acquired
     * on each change in the cloud topology. Consult cloud SPI implementation for more details.
     *
     * @return Map of parameters associated with this resource. This map can be changing from
     *      call to call depending on specific cloud SPI implementation.
     */
    public Map<String, String> parameters();
}
