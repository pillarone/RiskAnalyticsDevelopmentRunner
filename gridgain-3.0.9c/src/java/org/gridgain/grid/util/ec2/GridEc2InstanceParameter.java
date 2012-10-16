// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.ec2;

import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * EC2 instance parameter.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridEc2InstanceParameter {
    /** */
    private String imgId;

    /** */
    private String keyPair;

    /** */
    private List<String> secGrps;

    /** */
    private String instType;

    /** */
    private int cnt;

    /** */
    private String debug;

    /** */
    private String mainS3bckt;

    /** */
    private String extS3bckt;

    /** */
    private boolean enableMonitoring;

    /**
     * @return Image ID.
     */
    public String getImageId() {
        return imgId;
    }

    /**
     * @param imgId Image ID.
     */
    public void setImageId(String imgId) {
        this.imgId = imgId;
    }

    /**
     * @return Key pair.
     */
    public String getKeyPair() {
        return keyPair;
    }

    /**
     * @param keyPair Key pair.
     */
    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * @return Security groups.
     */
    public List<String> getSecurityGroups() {
        return secGrps;
    }

    /**
     * @param secGrps Security groups.
     */
    public void setSecurityGroups(List<String> secGrps) {
        this.secGrps = secGrps;
    }

    /**
     * @return Instance type.
     */
    public String getInstanceType() {
        return instType;
    }

    /**
     * @param instType Instance type.
     */
    public void setInstanceType(String instType) {
        this.instType = instType;
    }

    /**
     * @return Instance count.
     */
    public int getInstanceCount() {
        return cnt;
    }

    /**
     * @param cnt Instance count.
     */
    public void setInstanceCount(int cnt) {
        this.cnt = cnt;
    }

    /**
     * @return Debug.
     */
    public String getDebug() {
        return debug;
    }

    /**
     * @param debug Debug.
     */
    public void setDebug(String debug) {
        this.debug = debug;
    }

    /**
     * @return Main S3 bucket.
     */
    public String getMainS3Bucket() {
        return mainS3bckt;
    }

    /**
     * @param mainS3bucket Main S3 bucket.
     */
    public void setMainS3Bucket(String mainS3bucket) {
        mainS3bckt = mainS3bucket;
    }

    /**
     * @return Extension S3 bucket.
     */
    public String getExtensionS3Bucket() {
        return extS3bckt;
    }

    /**
     * @param extS3bucket Extension S3 bucket.
     */
    public void setExtensionS3Bucket(String extS3bucket) {
        extS3bckt = extS3bucket;
    }

    /**
     * @return Is monitoring enabled.
     */
    public boolean isEnableMonitoring() {
        return enableMonitoring;
    }

    /**
     * @param enableMonitoring Is monitoring enabled.
     */
    public void setEnableMonitoring(boolean enableMonitoring) {
        this.enableMonitoring = enableMonitoring;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEc2InstanceParameter.class, this);
    }
}
