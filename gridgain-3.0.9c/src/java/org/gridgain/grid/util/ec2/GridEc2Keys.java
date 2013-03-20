// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.ec2;

import org.gridgain.grid.typedef.internal.*;

/**
 * EC2 keys.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridEc2Keys {
    /** */
    private String accessKeyId;

    /** */
    private String secretAccessKey;

    /**
     *
     */
    public GridEc2Keys() {
        /* No-op. */
    }

    /**
     *
     * @param accessKeyId Access key ID.
     * @param secretAccessKey Secret access key.
     */
    public GridEc2Keys(String accessKeyId, String secretAccessKey) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    /**
     * @return Access key ID.
     */
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * @param accessKeyId Access key ID.
     */
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    /**
     * @return Secret access key.
     */
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    /**
     * @param secretAccessKey Secret access key.
     */
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEc2Keys.class, this);
    }
}
