// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.tostring;

import java.util.*;

/**
 * TODO: provide class description here.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridToStringClassDescriptor {
    /** */
    private final String sqn;

    /** */
    private final String fqn;

    /** */
    private List<GridToStringFieldDescriptor> fields = new ArrayList<GridToStringFieldDescriptor>();

    /**
     * @param cls TODO
     */
    GridToStringClassDescriptor(Class<?> cls) {
        assert cls != null;

        fqn = cls.getName();
        sqn = cls.getSimpleName();
    }

    /**
     * @param field TODO
     */
    void addField(GridToStringFieldDescriptor field) {
        assert field != null;

        fields.add(field);
    }

    /** */
    void sortFields() {
        Collections.sort(fields, new Comparator<GridToStringFieldDescriptor>() {
            /** {@inheritDoc} */
            @Override public int compare(GridToStringFieldDescriptor arg0, GridToStringFieldDescriptor arg1) {
                return arg0.getOrder() < arg1.getOrder() ? -1 : arg0.getOrder() > arg1.getOrder() ? 1 : 0;
            }
        });
    }

    /**
     * @return TODO
     */
    String getSimpleClassName() {
        return sqn;
    }

    /**
     * @return TODO
     */
    String getFullyQualifiedClassName() {
        return fqn;
    }

    /**
     * @return TODO
     */
    List<GridToStringFieldDescriptor> getFields() {
        return fields;
    }
}
