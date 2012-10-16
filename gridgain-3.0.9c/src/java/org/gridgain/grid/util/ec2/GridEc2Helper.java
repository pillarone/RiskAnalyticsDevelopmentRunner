// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.ec2;

import com.amazonaws.*;
import com.amazonaws.auth.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;
import org.apache.commons.codec.binary.*;
import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * EC2 Helper.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridEc2Helper {
    /** */
    private static final String EC2_PENDING_STATE = "pending";

    /** */
    private static final String EC2_RUNNING_STATE = "running";

    /** */
    public static final String USER_DATA_DELIM = "|";

    /** Default EC2 keys property file location */
    public static final String DFLT_EC2_KEYS_PATH = "work/ec2/ec2_keys.properties";

    /** */
    public static final String EC2_DFLT_PARAMS_PATH = "bin/ec2/ec2.properties";

    /** Property name (system/environment) containing path to file with EC2 keys. */
    public static final String EC2_KEYS_PROP_NAME = "GRIDGAIN_EC2_KEYS";

    /** */
    public static final String ACCESS_KEY_ID_KEY = "access_key_id";

    /** */
    public static final String SECRET_ACCESS_KEY_KEY = "secret_access_key";

    /** */
    public static final String GRIDGAIN_MAIN_S3_BUCKET_KEY = "gridgain.s3.bucket";

    /** */
    public static final String GRIDGAIN_EXT_S3_BUCKET_KEY = "gridgain.ext.s3.bucket";

    /** */
    public static final String GRIDGAIN_ACCESS_KEY_ID_KEY = "gridgain.access.key.id";

    /** */
    public static final String GRIDGAIN_SECRET_KEY_KEY = "gridgain.secret.access.key";

    /** */
    private final AmazonEC2 ec2;

    /** */
    private GridEc2Keys ec2keys;

    /**
     *
     * @throws GridException Thrown in case of any exception.
     */
    public GridEc2Helper() throws GridException {
        ec2 = initEc2Client(null);
    }

    /**
     * @param keyFilePath Keys file path.
     * @throws GridException Thrown in case of any exception.
     */
    public GridEc2Helper(String keyFilePath) throws GridException {
        ec2 = initEc2Client(keyFilePath);
    }

    /**
     * @param accessKeyId Access key ID.
     * @param secretAccessKey Secret access key.
     * @throws GridException Thrown in case of any exception.
     */
    public GridEc2Helper(String accessKeyId, String secretAccessKey) throws GridException {
        ec2keys = new GridEc2Keys(accessKeyId, secretAccessKey);
        ec2 = initEc2Client();
    }

    /**
     * @param param EC2 instance parameter.
     * @return List of started instances' IDs.
     * @throws GridException Thrown in case of any exception.
     */
    public List<String> startInstances(GridEc2InstanceParameter param) throws GridException {
        assert param != null;

        SB usrDat = new SB();

        if (param.getMainS3Bucket() != null) {
            usrDat.a(USER_DATA_DELIM);
            usrDat.a(GRIDGAIN_MAIN_S3_BUCKET_KEY).a("=").a(param.getMainS3Bucket());
        }

        if (param.getExtensionS3Bucket() != null) {
            usrDat.a(USER_DATA_DELIM);
            usrDat.a(GRIDGAIN_EXT_S3_BUCKET_KEY).a("=").a(param.getExtensionS3Bucket());
        }

        usrDat.a(USER_DATA_DELIM);
        usrDat.a(GRIDGAIN_ACCESS_KEY_ID_KEY).a("=").a(ec2keys.getAccessKeyId());
        usrDat.a(USER_DATA_DELIM);
        usrDat.a(GRIDGAIN_SECRET_KEY_KEY).a("=").a(ec2keys.getSecretAccessKey());


        if (param.getDebug() != null) {
            usrDat.a(USER_DATA_DELIM);
            usrDat.a("-Dgg.*.log.debug=").a(param.getDebug());
        }

        return startInstances(
            param.getImageId(),
            param.getKeyPair(),
            param.getSecurityGroups(),
            param.getInstanceType(),
            usrDat.toString(),
            param.getInstanceCount(),
            param.isEnableMonitoring());
    }

    /**
     * @param discoGrp Discovery group.
     * @param discoHub Discovery hub.
     * @param commGrp Communication group.
     * @param commHub Communication hub.
     * @param mainBucket Main bucket.
     * @param userBucket User bucket.
     * @param debug Debug.
     * @param accessKeyId Access key ID.
     * @param secretKey Secret key.
     * @return User data.
     */
    public static String makeUserData(String discoGrp, boolean discoHub, String commGrp, boolean commHub,
        String mainBucket, String userBucket, String debug, String accessKeyId, String secretKey) {
        assert mainBucket != null;

        SB usrDat = new SB();

        usrDat.a(GRIDGAIN_MAIN_S3_BUCKET_KEY).a("=").a(mainBucket);

        if (userBucket != null) {
            usrDat.a(USER_DATA_DELIM);
            usrDat.a(GRIDGAIN_EXT_S3_BUCKET_KEY).a("=").a(userBucket);
        }

        usrDat.a(USER_DATA_DELIM);
        usrDat.a(GRIDGAIN_ACCESS_KEY_ID_KEY).a("=").a(accessKeyId);
        usrDat.a(USER_DATA_DELIM);
        usrDat.a(GRIDGAIN_SECRET_KEY_KEY).a("=").a(secretKey);


        if (debug != null) {
            usrDat.a(USER_DATA_DELIM);
            usrDat.a("-Dgg.*.log.debug=").a(debug);
        }

        return usrDat.toString();
    }

    /**
     * @param imgId EC2 image ID.
     * @param keyPair Key pair name.
     * @param secGrps Security groups.
     * @param instType EC2 Instance type.
     * @param userData Instances user data.
     * @param count Instance count.
     * @param enableMon Enable CloudWatch monitoring.
     * @return List of started instances' IDs.
     * @throws GridException Thrown in case of any exception.
     */
    private List<String> startInstances(String imgId, String keyPair, Collection<String> secGrps, String instType,
        String userData, int count, boolean enableMon) throws GridException {
        RunInstancesRequest req = new RunInstancesRequest();

        req.setImageId(imgId);
        req.setInstanceType(instType);
        req.setKeyName(keyPair);
        req.setMaxCount(count);
        req.setMinCount(count);
        req.setMonitoring(enableMon);

        if (!F.isEmpty(secGrps))
            req.setSecurityGroups(secGrps);

        if (userData != null)
            req.setUserData(new String(Base64.encodeBase64(userData.getBytes())));

        RunInstancesResult res;

        try {
            res = ec2.runInstances(req);
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 run instances request: " + ex.getMessage(), ex);
        }

        List<Instance> running = res.getReservation().getInstances();

        if (running == null)
            throw new GridException("Received unexpected EC2 response (instances have not been started).");

        List<String> ids = new ArrayList<String>();

        for (Instance inst :  running)
            ids.add(inst.getInstanceId());

        if (enableMon) {
            MonitorInstancesRequest mReq = new MonitorInstancesRequest();

            mReq.setInstanceIds(ids);

            try {
                ec2.monitorInstances(mReq);
            }
            catch (AmazonClientException ex) {
                throw new GridException("Failed to start instances monitoring.", ex);
            }
        }

        return ids;
    }

    /**
     * Gets instance public DNS name.
     *
     * @param instId Instance ID.
     * @return Instance public address.
     * @throws GridException Thrown in case of any exception.
     */
    @Nullable
    public String getInstancePublicDnsName(String instId) throws GridException {
        assert instId != null;

        DescribeInstancesRequest req = new DescribeInstancesRequest().withInstanceIds(instId);

        DescribeInstancesResult res;

        try {
            res = ec2.describeInstances(req);
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 describe instances request: " + ex.getMessage(), ex);
        }

        if (res.getReservations() != null) {
            Reservation r = res.getReservations().get(0);

            if (r.getInstances() != null) {
                Instance inst = r.getInstances().get(0);

                if (instId.equals(inst.getInstanceId()))
                    if (inst.getPublicDnsName() != null && inst.getPublicDnsName().length() > 0)
                        return inst.getPublicDnsName();
            }
        }

        return null;
    }

    /**
     * Checks whether instance is in running or pending state.
     *
     * @param instId Instance ID.
     * @return {@code true} if instance is in running or pending state, {@code false} otherwise.
     * @throws GridException Thrown in case of any exception.
     */
    public boolean isInstanceRunningOrPending(String instId)  throws GridException {
        assert instId != null;

        DescribeInstancesRequest req = new DescribeInstancesRequest().withInstanceIds(instId);

        DescribeInstancesResult res;

        try {
            res = ec2.describeInstances(req);
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 describe instances request: " + ex.getMessage(), ex);
        }

        if (res.getReservations() != null) {
            Reservation r = res.getReservations().get(0);
            if (r.getInstances() != null) {
                Instance inst = r.getInstances().get(0);
                if (instId.equals(inst.getInstanceId()))
                    if (inst.getPublicDnsName() != null && inst.getPublicDnsName().length() > 0)
                        return EC2_RUNNING_STATE.equals(inst.getState().getName())
                                || EC2_PENDING_STATE.equals(inst.getState().getName());
            }
        }

        return false;
    }

    /**
     * Gets list of instances' IDs by instance states that are in one of the
     *
     * @param states States.
     * @return List of instances' IDs.
     * @throws GridException Thrown in case of any exception.
     */
    public List<String> getInstanceIds(String... states) throws GridException {
        Set<String> set;

        if (states == null || states.length == 0) {
            return Collections.emptyList();
        }
        else {
            set = new HashSet<String>(Arrays.asList(states));
        }

        List<String> ids = new ArrayList<String>();

        DescribeInstancesResult res;

        try {
            res = ec2.describeInstances(new DescribeInstancesRequest());
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 describe instances request: " + ex.getMessage(), ex);
        }


        if (res.getReservations() != null)
            for (Reservation r : res.getReservations())
                if (r.getInstances() != null)
                    for (Instance inst : r.getInstances())
                        if (set.contains(inst.getState().getName()))
                            ids.add(inst.getInstanceId());

        return ids;
    }

    /**
     * @param instIds Instances' IDs.
     * @return List of terminated instances' IDs.
     * @throws GridException Thrown in case of any exception.
     */
    public List<String> terminateInstances(Collection<String> instIds) throws GridException {
        TerminateInstancesRequest req = new TerminateInstancesRequest();

        req.setInstanceIds(instIds);

        TerminateInstancesResult res;

        try {
            res = ec2.terminateInstances(req);
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 terminate instances request: " + ex.getMessage(), ex);
        }

        List<String> termIds = new ArrayList<String>();

        if (res.getTerminatingInstances() != null)
            for (InstanceStateChange isc : res.getTerminatingInstances())
                termIds.add(isc.getInstanceId());

        return termIds;
    }

    /**
     * @param instIds Instances' IDs.
     * @return List of stopped instances' IDs.
     * @throws GridException Thrown in case of any exception.
     */
    public List<String> stopInstances(Collection<String> instIds) throws GridException {
        StopInstancesRequest req = new StopInstancesRequest();

        req.setInstanceIds(instIds);

        StopInstancesResult res;

        try {
            res = ec2.stopInstances(req);
        }
        catch (AmazonClientException ex) {
            throw new GridException("Failed to perform EC2 stop instances request: " + ex.getMessage(), ex);
        }

        List<String> stopIds = new ArrayList<String>();

        if (res.getStoppingInstances() != null)
            for (InstanceStateChange isc : res.getStoppingInstances())
                stopIds.add(isc.getInstanceId());

        return stopIds;
    }

    /**
     * @return EC2 default parameters.
     * @throws GridException Thrown in case of any exception.
     */
    public static GridEc2DefaultParameters getDefaults() throws GridException {
        URL propURL = GridUtils.resolveGridGainUrl(EC2_DFLT_PARAMS_PATH);

        if (propURL == null) {
            throw new GridException("Failed to resolve path to default properties: " + EC2_DFLT_PARAMS_PATH);
        }

        File f;

        try {
            f = new File(propURL.toURI());
        }
        catch (URISyntaxException e) {
            throw new GridException("Invalid path to default properties: " + propURL, e);
        }

        if (!f.exists()) {
            throw new GridException("Default properties file not found: " + f);
        }

        if (f.isDirectory() || !f.canRead()) {
            throw new GridException("Default properties file must be readable file: " + f);
        }

        Properties props = new Properties();
        FileInputStream in = null;

        try {
            in = new FileInputStream(f);

            props.load(in);
        }
        catch (IOException e) {
            throw new GridException("Failed to load default properties file: " + f, e);
        }
        finally {
            GridUtils.close(in, null);
        }

        String imgId = getParameter(props, "image_id", "Image ID", true);
        String keypair = getParameter(props, "keypair", "Key pair name", true);
        String instType = getParameter(props, "instance_type", "Instance type", true);
        String mainS3Bucket = getParameter(props, "main_s3_bucket", "Main S3 bucket", false);

        return new GridEc2DefaultParameters(imgId, keypair, instType, mainS3Bucket);
    }

    /**
     * @param props Properties.
     * @param name Name.
     * @param descr Description.
     * @param required Is required.
     * @return Parameter value.
     * @throws GridException Thrown in case of any exception.
     */
    private static String getParameter(Properties props, String name, String descr, boolean required)
        throws GridException {
        assert props != null;
        assert name != null;

        String val = props.getProperty(name);

        if (required && (val == null || val.length() == 0)) {
            throw new GridException("Failed to load parameter from default property file: " + descr);
        }

        return val;
    }

    /**
     * Creates {@link GridEc2Keys} object using key file path provided.
     * <p>
     * If key file path is {@code null} method will try to get
     * system property {@link #EC2_KEYS_PROP_NAME}, then environment property
     * {@link #EC2_KEYS_PROP_NAME} values. If no result {@link #DFLT_EC2_KEYS_PATH} will be used.
     *
     * @param keyFilePath Optional keys file path.
     * @return EC2 keys.
     * @throws GridException Thrown in case of any exception.
     */
    public static GridEc2Keys getEc2Credentials(@Nullable String keyFilePath) throws GridException {
        if (keyFilePath == null) {
            keyFilePath = System.getProperty(EC2_KEYS_PROP_NAME);

            if (keyFilePath == null) {
                keyFilePath = System.getenv(EC2_KEYS_PROP_NAME);
            }

            if (keyFilePath == null) {
                keyFilePath = DFLT_EC2_KEYS_PATH;
            }
        }

        URL propURL = GridUtils.resolveGridGainUrl(keyFilePath);

        if (propURL == null) {
            throw new GridException("Can not resolve keys file path: " + keyFilePath);
        }

        File f;

        try {
            f = new File(propURL.toURI());
        }
        catch (URISyntaxException e) {
            throw new GridException("Invalid EC2 keys file path: " + keyFilePath, e);
        }

        if (!f.exists()) {
            throw new GridException("EC2 keys file not found: " + keyFilePath);
        }

        if (f.isDirectory() || !f.canRead()) {
            throw new GridException("EC2 keys file must be readable file: " + keyFilePath);
        }

        Properties props = new Properties();
        FileInputStream in = null;

        try {
            in = new FileInputStream(f);

            props.load(in);
        }
        catch (IOException e) {
            throw new GridException("Failed to load EC2 keys file: " + keyFilePath, e);
        }
        finally {
            GridUtils.close(in, null);
        }

        return new GridEc2Keys(props.getProperty(ACCESS_KEY_ID_KEY), props.getProperty(SECRET_ACCESS_KEY_KEY));
    }

    /**
     * @param keyFilePath Keys file path.
     * @return EC2 client.
     * @throws GridException Thrown in case of any exception.
     */
    private AmazonEC2 initEc2Client(String keyFilePath) throws GridException {
        ec2keys = getEc2Credentials(keyFilePath);

        return initEc2Client();
    }

    /**
     * @return EC2 client.
     * @throws GridException Thrown in case of any exception.
     */
    private AmazonEC2 initEc2Client() throws GridException {
        if (ec2keys == null || ec2keys.getAccessKeyId() == null || ec2keys.getSecretAccessKey() == null) {
            throw new GridException("Some EC2 keys not specified.");
        }

        return new AmazonEC2Client(new BasicAWSCredentials(ec2keys.getAccessKeyId(), ec2keys.getSecretAccessKey()),
            new ClientConfiguration());
    }
}
