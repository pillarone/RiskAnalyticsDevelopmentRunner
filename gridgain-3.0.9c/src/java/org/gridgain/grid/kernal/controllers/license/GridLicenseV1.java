// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.license;

import java.util.*;

/**
 * GridGain license version 1.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridLicenseV1 {
    /**
     * Gets license version.
     *
     * @return License version.
     */
    public String getVersion();

    /**
     * Gets license ID.
     *
     * @return License ID.
     */
    public UUID getId();

    /**
     * Gets issue date.
     *
     * @return Issue date.
     */
    public Date getIssueDate();

    /**
     * Gets issue organization.
     *
     * @return Issue organization.
     */
    public String getIssueOrganization();

    /**
     * Gets user organization.
     *
     * @return User organization.
     */
    public String getUserOrganization();

    /**
     * Gets user organization URL.
     *
     * @return User organization URL.
     */
    public String getUserWww();

    /**
     * Gets user organization e-mail.
     *
     * @return User organization e-mail.
     */
    public String getUserEmail();

    /**
     * Gets license type.
     *
     * @return License type.
     */
    public GridLicenseType getType();

    /**
     * Gets metering key 1.
     *
     * @return Metering key 1.
     */
    public String getMeteringKey1();

    /**
     * Gets metering key 2.
     *
     * @return Metering key 2.
     */
    public String getMeteringKey2();

    /**
     * Gets expire date.
     *
     * @return Expire date.
     */
    public Date getExpireDate();

    /**
     * Gets maximum number of nodes.
     *
     * @return Maximum number of nodes.
     */
    public int getMaxNodes();

    /**
     * Gets maximum number of CPUs.
     *
     * @return Maximum number of CPUs.
     */
    public int getMaxCpus();

    /**
     * Gets signature.
     *
     * @return Signature.
     */
    public String getSignature();
}
