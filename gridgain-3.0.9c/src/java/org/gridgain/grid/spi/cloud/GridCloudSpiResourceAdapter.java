// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Convenient implementation for {@link GridCloudResource} supporting metadata. This class is intended
 * for use by SPI implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCloudSpiResourceAdapter extends GridMetadataAwareAdapter implements GridCloudResource {
    /** Resource ID. */
    private String id;

    /** Cloud ID. */
    private String cloudId;

    /** Resource type. */
    private int type;

    /** Resource links. */
    private Collection<GridCloudResource> links;

    /** Resource parameters. */
    @GridToStringInclude
    private Map<String, String> params;

    /**
     * Creates resource adapter.
     *
     * @param id Resource ID.
     * @param type Resource type.
     * @param cloudId Cloud ID.
     */
    public GridCloudSpiResourceAdapter(String id, int type, String cloudId) {
        this(null, id, type, cloudId, null, null);
    }

    /**
     * Creates resource adapter.
     *
     * @param id Resource ID.
     * @param type Resource type.
     * @param cloudId Cloud ID.
     * @param params Resource params.
     */
    public GridCloudSpiResourceAdapter(String id, int type, String cloudId, Map<String, String> params) {
        this(null, id, type, cloudId, null, params);
    }

    /**
     * Creates resource adapter.
     *
     * @param id Resource ID.
     * @param type Resource type.
     * @param cloudId Cloud ID.
     * @param links Resource links.
     * @param params Resource params.
     */
    public GridCloudSpiResourceAdapter(String id, int type, String cloudId, Collection<GridCloudResource> links,
        Map<String, String> params) {
        this(null, id, type, cloudId, links, params);
    }

    /**
     * Creates resource adapter.
     *
     * @param attaches Attaches.
     * @param id Resource ID.
     * @param type Resource type.
     * @param cloudId Cloud ID.
     * @param links Resource links.
     * @param params Resource params.
     */
    public GridCloudSpiResourceAdapter(Map<String, Object> attaches, String id, int type, String cloudId,
        Collection<GridCloudResource> links, Map<String, String> params) {
        super(attaches);

        A.ensure(!F.isEmpty(id), "!F.isEmpty(id)");
        A.ensure(type > 0, "type > 0");
        A.ensure(!F.isEmpty(cloudId), "!F.isEmpty(cloudId)");

        this.id = id;
        this.type = type;
        this.cloudId = cloudId;
        this.links = links != null ? Collections.unmodifiableCollection(links) : null;
        this.params = params != null ? Collections.unmodifiableMap(params) : null;
    }

    /** {@inheritDoc} */
    @Override public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public String cloudId() {
        return cloudId;
    }

    /** {@inheritDoc} */
    @Override public int type() {
        return type;
    }

    /** {@inheritDoc} */
    @Nullable @Override public Collection<GridCloudResource> links() {
        return links;
    }

    /**
     * Sets resource links.
     *
     * @param links Resource links.
     */
    public void setLinks(Collection<GridCloudResource> links) {
        this.links = links != null ? Collections.unmodifiableCollection(links) : null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public Map<String, String> parameters() {
        return params;
    }

    /**
     * Sets resource parameters.
     *
     * @param params Resource parameters.
     */
    public void setParameters(Map<String, String> params) {
        this.params = params != null ? Collections.unmodifiableMap(params) : null;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridCloudResource o) {
        int hashCode = hashCode();
        int thatHashCode = o != null ? o.hashCode() : 0;

        return hashCode > thatHashCode ? 1 : hashCode == thatHashCode ? 0 : -1;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof GridCloudSpiResourceAdapter))
            return false;

        GridCloudSpiResourceAdapter that = (GridCloudSpiResourceAdapter)o;

        if (type != that.type)
            return false;

        if (id != null ? !id.equals(that.id) : that.id != null)
            return false;

        if (cloudId != null ? !cloudId.equals(that.cloudId) : that.cloudId != null)
            return false;

        if (params != null) {
            if (that.params == null || params.size() != that.params.size())
                return false;

            for (Map.Entry<String, String> e : params.entrySet()) {
                String val = e.getValue();
                String thatVal = that.params.get(e.getKey());

                if (val != null ? !val.equals(thatVal) : thatVal != null)
                    return false;
            }
        }
        else if (that.params != null)
            return false;

        if (links != null) {
            if (that.links == null || links.size() != that.links.size())
                return false;

            for (GridCloudResource thatRsrc : that.links) {
                boolean contains = false;

                for (GridCloudResource rsrc : links)
                    if (rsrc == thatRsrc || rsrc.id().equals(thatRsrc.id())) {
                        contains = true;

                        break;
                    }

                if (!contains)
                    return false;
            }
        }
        else
            return that.links == null;

        return true;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (cloudId != null ? cloudId.hashCode() : 0);
        result = 31 * result + type;

        int hashCode = 0;

        if (!F.isEmpty(params))
            for (String val : params.values())
                hashCode += val != null ? val.hashCode() : 0;

        result = 31 * result + hashCode;

        hashCode = 0;

        if (!F.isEmpty(links))
            for (GridCloudResource rsrc : links)
                hashCode += rsrc.id().hashCode();

        result = 31 * result + hashCode;

        return result;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudSpiResourceAdapter.class, this);
    }
}
