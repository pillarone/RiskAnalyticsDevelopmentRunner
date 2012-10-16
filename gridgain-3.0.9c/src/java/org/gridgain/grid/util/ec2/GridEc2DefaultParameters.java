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
 * EC2 default parameters.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridEc2DefaultParameters {
    /** */
    private final String imgId;

    /** */
    private final String keypair;

    /** */
    private final String instType;

    /** */
    private final String mainS3Bckt;

    /**
     *
     * @param imgId Image ID.
     * @param keypair Keypair.
     * @param instType Instance type.
     * @param mainS3Bckt Main S3 bucket.
     */
    GridEc2DefaultParameters(String imgId, String keypair, String instType, String mainS3Bckt) {
        assert imgId != null;
        assert keypair != null;
        assert instType != null;
        assert mainS3Bckt != null;

        this.imgId = imgId;
        this.keypair = keypair;
        this.instType = instType;
        this.mainS3Bckt = mainS3Bckt;
    }

    /**
     * @return Image ID.
     */
    public String getImageId() {
        return imgId;
    }

    /**
     * @return Keypair.
     */
    public String getKeypair() {
        return keypair;
    }

    /**
     * @return Instance type.
     */
    public String getInstanceType() {
        return instType;
    }

    /**
     * @return Main S3 bucket.
     */
    public String getMainS3Bucket() {
        return mainS3Bckt;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEc2DefaultParameters.class, this);
    }
}
