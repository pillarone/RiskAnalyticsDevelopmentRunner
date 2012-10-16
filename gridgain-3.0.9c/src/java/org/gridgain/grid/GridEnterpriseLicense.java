// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.util.*;

/**
 * GridGain Enterprise license descriptor. GridGain license is available for
 * information purposes and is checked automatically by GridGain software.
 * <p>
 * Not that this license is for Enterprise Edition and available only in
 * Enterprise Edition of GridGain. License descriptor can be obtains by
 * calling {@link Grid#license()} method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see Grid#license()
 */
public interface GridEnterpriseLicense {
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
     * Version regular expression.
     *
     * @return Version regular expression.
     */
    public String getVersionRegexp();

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
     * Gets license note. It may include textual description of license limitations such
     * as as "Development Only" or "Load-Testing and Staging Only".
     *
     * @return License note.
     */
    public String getLicenseNote();

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
     * Gets user organization contact name.
     *
     * @return User organization contact name.
     */
    public String getUserName();

    /**
     * Gets expire date.
     *
     * @return Expire date.
     */
    public Date getExpireDate();

    /**
     * Gets maximum number of nodes. If zero - no restriction.
     *
     * @return Maximum number of nodes.
     */
    public int getMaxNodes();

    /**
     * Gets maximum number of physical computers or virtual instances. If zero - no restriction.
     * Note that individual physical computer or virtual instance is determined by number of enabled
     * MACs on each computer or instance.
     *
     * @return Maximum number of computers or virtual instances.
     */
    public int getMaxComputers();

    /**
     * Gets maximum number of CPUs. If zero - no restriction.
     *
     * @return Maximum number of CPUs.
     */
    public int getMaxCpus();

    /**
     * Gets maximum up time in minutes. If zero - no restriction.
     *
     * @return Maximum up time in minutes.
     */
    public long getMaxUpTime();

    /**
     * Gets license violation grace period in minutes. If zero - no grace period.
     *
     * @return License violation grace period in minutes.
     */
    public long getGracePeriod();
}
