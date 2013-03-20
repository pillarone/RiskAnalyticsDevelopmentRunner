// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Convenient POJO adapter for {@link GridCloudCommand}.
 * <h2 class="header">Thread Safety</h2>
 * This class is a simple bean and it doesn't provide any synchronization.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCloudCommandAdapter extends GridMetadataAwareAdapter implements GridCloudCommand {
    /** Command ID. */
    private String id;

    /** Numeric value associated with this command. */
    private int num;

    /** Action string associated with this command. */
    private String act;

    /** Resources associated with this command. */
    @GridToStringInclude
    private Collection<GridCloudResource> rsrcs;

    /** Command parameters. */
    @GridToStringInclude
    private Map<String, String> params;

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param act Optional action string associated with this command.
     */
    public GridCloudCommandAdapter(String id, @Nullable String act) {
        this(id, act, 0, null, null);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param num Numeric value associated with this command.
     */
    public GridCloudCommandAdapter(String id, int num) {
        this(id, null, num, null, null);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param act Optional action string associated with this command. 
     * @param num Numeric value associated with this command.
     */
    public GridCloudCommandAdapter(String id, @Nullable String act, int num) {
        this(id, act, num, null, null);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param num Numeric value associated with this command.
     * @param params Optional command parameters.
     */
    public GridCloudCommandAdapter(String id, int num, @Nullable Map<String, String> params) {
        this(id, null, num, null, params);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param act Optional action string associated with this command.
     * @param num Numeric value associated with this command.
     * @param params Optional command parameters.
     */
    public GridCloudCommandAdapter(String id, String act, int num, @Nullable Map<String, String> params) {
        this(id, act, num, null, params);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param num Numeric value associated with this command.
     * @param rsrcs Optional resources associated with this command.
     */
    public GridCloudCommandAdapter(String id, int num, @Nullable Collection<GridCloudResource> rsrcs) {
        this(id, null, num, rsrcs, null);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param act Optional action string associated with this command.
     * @param num Numeric value associated with this command.
     * @param rsrcs Optional resources associated with this command.
     */
    public GridCloudCommandAdapter(String id, @Nullable String act, int num,
        @Nullable Collection<GridCloudResource> rsrcs) {
        this(id, act, num, rsrcs, null);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param num Numeric value associated with this command.
     * @param rsrcs Optional resources associated with this command.
     * @param params Optional command parameters.
     */
    public GridCloudCommandAdapter(String id, int num, @Nullable Collection<GridCloudResource> rsrcs,
        @Nullable Map<String, String> params) {
        this(id, null, num, rsrcs, params);
    }

    /**
     * Creates cloud command POJO.
     *
     * @param id Command ID.
     * @param num Numeric value associated with this command.
     * @param rsrcs Optional resources associated with this command.
     * @param params Optional command parameters.
     * @param act Optional action string associated with this command.
     */
    public GridCloudCommandAdapter(String id, @Nullable String act, int num,
        @Nullable Collection<GridCloudResource> rsrcs, @Nullable Map<String, String> params) {
        A.ensure(!F.isEmpty(id), "!F.isEmpty(id)");

        this.id = id;
        this.act = act;
        this.num = num;
        this.rsrcs = rsrcs;
        this.params = params;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridCloudCommand o) {
        return o == null ? 1 : id.compareTo(o.id());
    }

    /** {@inheritDoc} */
    @Override public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public int number() {
        return num;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public String action() {
        return act;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Collection<GridCloudResource> resources() {
        return rsrcs;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Map<String, String> parameters() {
        return params;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof GridCloudCommandAdapter)) {
            return false;
        }

        GridCloudCommandAdapter it = (GridCloudCommandAdapter)o;

        return num == it.num &&
            !(id != null ? !id.equals(it.id) : it.id != null) &&
            !(act != null ? !act.equals(it.act) : it.act != null) &&
            !(params != null ? !params.equals(it.params) : it.params != null) &&
            !(rsrcs != null ? !rsrcs.equals(it.rsrcs) : it.rsrcs != null);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int hash = id != null ? id.hashCode() : 0;

        hash = 31 * hash + num;
        hash = 31 * hash + (rsrcs != null ? rsrcs.hashCode() : 0);
        hash = 31 * hash + (act != null ? act.hashCode() : 0);
        hash = 31 * hash + (params != null ? params.hashCode() : 0);

        return hash;
    }

    /**
     * Sets numeric value associated with this command.
     *
     * @param num Numeric value.
     */
    public void number(int num) {
        this.num = num;
    }

    /**
     * Sets optional action string associated with this command.
     *
     * @param act Action string.
     */
    public void action(String act) {
        this.act = act;
    }

    /**
     * Sets resource collection associated with this command.
     *
     * @param rsrcs Resource collection.
     */
    public void resources(Collection<GridCloudResource> rsrcs) {
        this.rsrcs = rsrcs;
    }

    /**
     * Sets command parameters.
     *
     * @param params Command parameters.
     */
    public void parameters(Map<String, String> params) {
        this.params = params;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudCommandAdapter.class, this);
    }
}