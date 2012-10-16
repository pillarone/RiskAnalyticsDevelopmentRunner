// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Cloud resource defines any type of manageable resource on the cloud.
 * <p>
 * In GridGain a cloud is defined as a collection of resources where all resources
 * in the cloud form a set of undirected graphs. Cloud resource defines any manageable resource in
 * the specific cloud such as image, instance, profile, network attached storage, block device,
 * etc. Each resource has a collection of adjacent resources returned by {@link #links()}
 * method. Note that links are bi-directional, i.e. if node {@code A} has a link to node {@code B},
 * node {@code B} always has link back to {@code A} for any two adjacent nodes {@code A} and {@code B}
 * in the resource graph.
 * <p>
 * Note that this interface does not impose any parent-child or directional relationships on its
 * collection of resources - although they exist in every specific case. In fact, the every cloud provider
 * not only will have potentially different set of resources and/or resource types, but the parent-child
 * relationship can be different as well. {@link GridRichCloud} interface provide convenient methods for
 * navigating cloud resources graphs.
 * <p>
 * Cloud SPI is responsible for producing the cloud snapshot that includes cloud resource
 * graphs.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCloud
 * @see GridRichCloud
 * @see GridCloudResourceType
 */
public interface GridCloudResource extends GridMetadataAware, Comparable<GridCloudResource> {
    /**
     * Gets type of the resource.
     * <p>
     * Resource types are defined in {@link GridCloudResourceType} class. They are
     * specifically constants and not enumerations to allow for user-defined type of
     * resources.
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
     * Gets collection of resources adjacent to this resource.
     *
     * @return Collection of adjacent resources. May return {@code null} if there are no
     *      adjacent resources for this resource.
     */
    @Nullable public Collection<GridCloudResource> links();

    /**
     * Gets map of parameters that cloud provides associates with this resource. Depending on
     * cloud SPI implementation this map can change from call to call as it can be re-acquired
     * on each change in the cloud topology. Consult cloud SPI implementation for more details.
     *
     * @return Map of parameters associated with this resource. This map can be changing from
     *      call to call depending on specific cloud SPI implementation.
     */
    @Nullable public Map<String, String> parameters();
}
