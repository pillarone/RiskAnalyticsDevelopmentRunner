// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.license;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.*;
import org.jetbrains.annotations.*;

/**
 * Kernal controller responsible for license management.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridLicenseController extends GridController {
    /**
     * This method is called periodically by the GridGain to check the license
     * conformance.
     *
     * @throws GridLicenseException Thrown in case of any license violation.
     */
    public void checkLicense() throws GridLicenseException;

    /**
     * Acks the license to the log.
     */
    public void ackLicense();

    /**
     * Gets enterprise license descriptor.
     *
     * @return Enterprise license descriptor.
     */
    @Nullable public GridEnterpriseLicense license();
}
