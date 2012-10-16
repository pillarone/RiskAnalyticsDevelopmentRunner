// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers;

import org.gridgain.grid.kernal.controllers.affinity.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * Kernal controller registry.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridControllerRegistry implements Iterable<GridController> {
    /** */
    @GridToStringInclude
    private GridLicenseController licCtrl;

    @GridToStringInclude
    private GridAffinityController affinityCtrl;

    /** */
    private List<GridController> ctrls = new LinkedList<GridController>();

    /**
     * Adds controller to the registry.
     *
     * @param ctrl Controller to add.
     */
    public void add(GridController ctrl) {
        assert ctrl != null;

        if (ctrl instanceof GridLicenseController) {
            licCtrl = (GridLicenseController)ctrl;
        }
        else if (ctrl instanceof GridAffinityController) {
            affinityCtrl = (GridAffinityController)ctrl;
        }

        ctrls.add(ctrl);
    }

    /**
     * Gets license controller.
     *
     * @return License controller.
     */
    public GridLicenseController license() {
        return licCtrl;
    }

    /**
     * Gets cache data affinity controller.
     *
     * @return Cache data affinity controller.
     */
    public GridAffinityController affinity() {
        return affinityCtrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override public Iterator<GridController> iterator() {
        return ctrls.iterator();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridControllerRegistry.class, this);
    }
}
