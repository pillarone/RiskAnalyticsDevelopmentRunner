// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.license.impl;

import org.gridgain.grid.typedef.internal.*;
import javax.xml.bind.annotation.adapters.*;
import java.util.*;

/**
 * Date-string XML adapter for JAXB.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLicenseDateAdapter extends XmlAdapter<String, Date> {
    /** Pattern. */
    private String ptrn = "MM/dd/yyyy";

    /**
     * Create date adapter.
     */
    public GridLicenseDateAdapter() {
        // No-op.
    }

    /**
     * Creates date adapter.
     *
     * @param ptrn Pattern.
     */
    public GridLicenseDateAdapter(String ptrn) {
        this.ptrn = ptrn;
    }

    /** {@inheritDoc} */
    @Override public Date unmarshal(String str) throws Exception {
        return U.parse(str, ptrn);
    }

    /** {@inheritDoc} */
    @Override public String marshal(Date date) throws Exception {
        return U.format(date, ptrn);
    }
}
