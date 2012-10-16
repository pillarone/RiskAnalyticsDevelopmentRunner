// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.tostring;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridToStringFieldDescriptor {
    /** Field name. */
    private final String name;

    /** */
    private int order = Integer.MAX_VALUE;

    /**
     * @param name TODO
     */
    GridToStringFieldDescriptor(String name) {
        assert name != null;

        this.name = name;
    }

    /**
     * @return TODO
     */
    int getOrder() { return order; }

    /**
     * @param order TODO
     */
    void setOrder(int order) { this.order = order; }

    /**
     * @return TODO
     */
    String getName() { return name; }
}
