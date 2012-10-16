// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.tostring;

import org.gridgain.grid.typedef.internal.*;

/**
 * TODO: provide class description here.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridToStringThreadLocal {
    /** */
    private SB sb = new SB(256);

    /** */
    private Object[] addNames = new Object[5];

    /** */
    private Object[] addVals = new Object[5];

    /**
     * @return TODO
     */
    SB getStringBuilder() {
        return sb;
    }

    /**
     * @return TODO
     */
    Object[] getAdditionalNames() {
        return addNames;
    }

    /**
     * @return TODO
     */
    Object[] getAdditionalValues() {
        return addVals;
    }
}
