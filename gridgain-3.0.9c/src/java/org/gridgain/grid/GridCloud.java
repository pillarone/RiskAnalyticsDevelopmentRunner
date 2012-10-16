// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This interface defines cloud descriptor.
 * <p>
 * Cloud logically defines collection of cloud {@link GridCloudResource resources}. Cloud resources
 * form one or more undirected graphs where each resource in the graph can link to zero or more other
 * resources in the same graph. Note that resource graphs cannot have loops.
 * <p>
 * This interface provide a system view on the cloud. All user-level APIs work with
 * {@link GridRichCloud} interface that provides much more functionality and extends this
 * interface. Consult {@link GridRichCloud} for more information.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridRichCloud
 */
public interface GridCloud {
    /**
     * Gets unique ID of this cloud.
     *
     * @return Unique ID of this cloud.
     */
    public String id();

    /**
     * Gets optional vendor's parameters associated with this cloud. In general, it is up to the
     * cloud SPI implementation to decide what, if any, metadata will be provided. Also, there is
     * no specific requirement fot this metadata to be constant from call to call and SPI implementation
     * can decide to make the metadata dynamically changing. Please consult cloud SPI
     * implementation for further details.
     *
     * @return Vendor's parameters associated with this cloud.
     */
    public Map<String, String> parameters();

    /**
     * Gets collection of resources that define this cloud current state. Note that this method
     * effectively returns a current snapshot of the cloud and <i>can be changing from call to call</i>.
     * All resources in this cloud can be logically viewed as a collection of disjoint graphs.
     * Note that resource graphs cannot have loops.
     * <p>
     * Note that you can subscribe for cloud events and detect when cloud resources are changing if
     * more deterministic behavior is required.
     *
     * @param p Optional filter for the resources. If none provided - all cloud resources from the
     *      current snapshot will be returned.
     * @return Collection of cloud resources that define this cloud current state.
     */
    public Collection<GridCloudResource> resources(@Nullable GridPredicate<? super GridCloudResource>... p);
}
