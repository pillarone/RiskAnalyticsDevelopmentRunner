// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud.ec2lite;

import com.amazonaws.*;
import com.amazonaws.auth.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;
import org.apache.commons.codec.binary.*;
import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.cloud.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.ec2.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridCloudResourceType.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.spi.cloud.GridCloudSpiResourceAction.*;
import static org.gridgain.grid.util.ec2.GridEc2Helper.*;

/**
 * This class defines EC2-based implementation for {@link GridCloudSpi}. It supports
 * seven actions:
 * <ol>
 * <li>{@link #RUN_INST_ACT} run new Amazon EC2 instances;</li>
 * <li>{@link #TERMINATE_INST_ACT} terminate Amazon EC2 instances;</li>
 * <li>{@link #TERMINATE_ALL_INST_ACT} terminate all Amazon EC2 instances;</li>
 * <li>{@link #STOP_INST_ACT} stop Amazon EC2 instances;</li>
 * <li>{@link #STOP_ALL_INST_ACT} stop all Amazon EC2 instances;</li>
 * <li>{@link #START_INST_ACT} start Amazon EC2 instances;</li>
 * <li>{@link #START_ALL_INST_ACT} start all Amazon EC2 instances.</li>
 * </ol>
 * <p>
 * See below for details on each command.
 * <p>
 * For information about Amazon EC2 visit <a href="http://aws.amazon.com">aws.amazon.com</a>.
 *
 * <h1 class="header">Attention</h1>
 * <p>
 * You can configure only one cloud within one Amazon EC2 account. Be very careful when
 * using commands that affects all instances since execution will affect all instances
 * within account (not cloud as it may seem).
 * <p>
 * The most safe usage of EC2 clouds is when you run only cloud within one Amazon EC2 account.
 * If you want to add more clouds you should run them under separate accounts. If you use Amazon
 * EC2 for goals other than running clouds it is recommended you use separate accounts for that.
 *
 * <h1 class="header">Warning</h1>
 * <p>
 * Note that accessing Amazon EC2 service (i.e. running instances within) will result in charges
 * to your Amazon EC2 account.
 * <p>
 * Set test mode of the SPI to {@code true} to avoid calls (see {@link #setTestMode(boolean)}).
 *
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters:
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>Cloud ID (see {@link #setCloudId(String)})</li>
 * <li>State check frequency (see {@link #setStateCheckFrequency(long)}, {@link #MIN_STATE_CHECK_FREQ})</li>
 * <li>Access key ID (see {@link #setAccessKeyId(String)}, {@link GridEc2Helper#getEc2Credentials(String)})</li>
 * <li>Secret access key (see {@link #setSecretAccessKey(String)}, {@link GridEc2Helper#getEc2Credentials(String)})</li>
 * <li>Proxy host (see {@link #setProxyHost(String)})</li>
 * <li>Proxy port (see {@link #setProxyPort(int)})</li>
 * <li>Proxy user name (see {@link #setProxyUsername(String)})</li>
 * <li>Proxy password (see {@link #setProxyPassword(String)})</li>
 * <li>Image IDs (see {@link #setImageIds(List)})</li>
 * <li>Enable monitoring by default (see {@link #setEnableMonitoring(boolean)})</li>
 * </ul>
 * <h1 class="header">Commands</h1>
 * Following commands are supported:
 * <table class="doctable">
 * <tr>
 *      <th>Command</th>
 *      <th>Parameter</th>
 *      <th>Description</th>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Run Instance(s)</b>
 *          <p>
 *          Runs one or more EC2 instances within EC2 account.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #RUN_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater than zero indicating how many nodes to start.
 *          If less or equal to zero exception is thrown.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          Collection containing one and only one {@link GridCloudResourceType#CLD_IMAGE} resource
 *          and zero or more {@link GridCloudResourceType#CLD_SECURITY_GROUP} resources.
 *          Other resources types as well as other combinations are not allowed -
 *          exception will be thrown by {@link #process(GridCloudCommand, UUID)}.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not run any instances).</li>
 *              <li>{@link #INST_TYPE} - Instance type (refer to Amazon EC2 API reference).</li>
 *              <li>{@link #INST_PLACEMENT} - Instance placement (refer to Amazon EC2 API reference).</li>
 *              <li>{@link #INST_KEY_PAIR_NAME} - Key pair name (refer to Amazon EC2 API reference).</li>
 *              <li>{@link #INST_PASS_EC2_KEYS} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI will pass EC2 access credentials to instance via user data.</li>
 *              <li>{@link #INST_MAIN_S3_BUCKET} - Main S3 bucket for instance (passed via user data).</li>
 *              <li>{@link #INST_USER_S3_BUCKET} - User S3 bucket for instance (passed via user data).</li>
 *              <li>{@link #INST_JVM_OPTS} - JVM options for instance (refer to Amazon EC2 API reference).</li>
 *              <li>{@link #INST_MON} - By providing {@code true} or {@code false} string default monitor
 *                  setting may be overridden (see {@link #setEnableMonitoring(boolean)}).</li>
 *          </ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Terminate instance (s)</b>
 *          <p>
 *          Terminates instances.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #TERMINATE_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater or equal to zero. The value indicates how many instances to terminate.
 *          See below for further details.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          Not {@code null} and not empty collection.<br>
 *          Two variants are possible:
 *          <ol>
 *              <li>Collection containing the only element - resource of type {@link GridCloudResourceType#CLD_IMAGE}.
 *                  In this case {@link GridCloudCommand#number()} shows how many instances created out of this image
 *                  to terminate.</li>
 *              <li>Collection containing resources of type {@link GridCloudResourceType#CLD_INSTANCE}. in this case
 *                  {@link GridCloudCommand#number()} should be zero or equal to {@link GridCloudCommand#resources()}
 *                  size; if not then exception is thrown)</li>
 *          </ol>
 *          <p>
 *          SPI will search instances to terminate by a command among instances that are currently
 *          in one of the following states {@link #INST_PENDING_STATE}, {@link #INST_RUNNING_STATE},
 *          {@link #INST_STOPPING_STATE} or {@link #INST_STOPPED_STATE}.
 *          If concrete instances were provided but not found among instances with above states
 *          then exception is thrown.
 *          <p>
 *          If cloud has less instances than it is required to terminate by a command then exception is thrown
 *          at command execution time.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not terminate any instances).</li>
 *          <ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Terminate all instances.</b>
 *          <p>
 *          Terminates all instances in the cloud that are currently
 *          in one of the following states {@link #INST_PENDING_STATE}, {@link #INST_RUNNING_STATE},
 *          {@link #INST_STOPPING_STATE} or {@link #INST_STOPPED_STATE}.
 *          <p>
 *          If cloud has no instances to terminate then this command is no-op.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #TERMINATE_ALL_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns true, than SPI goes through all the motions of running a command, but makes no actual
 *                  changes (does not terminate any instances).</li>
 *          <ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Stop instance (s)</b>
 *          <p>
 *          Stops instances.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #STOP_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater or equal to zero. If not zero then should be equal to {@link GridCloudCommand#resources()}
 *          size (if not then exception is thrown).
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          Not {@code null} and not empty collection containing resources of type
 *          {@link GridCloudResourceType#CLD_INSTANCE} representing instances that are currently in
 *          {@link #INST_RUNNING_STATE} state.
 *          <p>
 *          SPI will search instances to terminate by a command among instances that are currently in
 *          {@link #INST_RUNNING_STATE} state. If provided instances were not found within
 *          running instances then exception is thrown at command execution time.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not stop any instances).</li>
 *              <li>{@link #FORCE_STOP} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then instances are forced to stop (refer to Amazon EC2 API reference).</li>
 *          <ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Stop all instances.</b>
 *          <p>
 *          Stops all instances in the cloud That are currently in {@link #INST_RUNNING_STATE} state.
 *          <p>
 *          If cloud has no running instances then this command is no-op.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #STOP_ALL_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not stop any instances).</li>
 *              <li>{@link #FORCE_STOP} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then instances are forced to stop (refer to Amazon EC2 API reference).</li>
 *          <ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Start instance (s)</b>
 *          <p>
 *          Starts instances that are currently in {@link #INST_STOPPED_STATE}.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #START_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>
 *          Value greater or equal to zero. If not zero then should be equal to {@link GridCloudCommand#resources()}
 *          size (if not then exception is thrown).
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>
 *          Not {@code null} and not empty collection containing resources of type
 *          {@link GridCloudResourceType#CLD_INSTANCE} representing instances that are currently in
 *          {@link #INST_STOPPED_STATE} state.
 *          <p>
 *          SPI will search instances to terminate by a command among instances that are currently in
 *          {@link #INST_STOPPED_STATE} state. If provided instances were not found within
 *          stopped instances then exception is thrown at command execution time.
 *      </td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not start any instances).</li>
 *          <ul>
 *      </td>
 * </tr>
 * <tr>
 *      <td rowspan=4>
 *          <b>Start all instances.</b>
 *          <p>
 *          Starts all instances in the cloud That are currently in {@link #INST_STOPPED_STATE} state.
 *          <p>
 *          If cloud has no stopped instances then this command is no-op.
 *      </td>
 *      <td>{@link GridCloudCommand#action()}</td>
 *      <td>Should return {@link #STOP_ALL_INST_ACT}</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#number()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#resources()}</td>
 *      <td>Any value. Will be ignored silently.</td>
 * </tr>
 * <tr>
 *      <td>{@link GridCloudCommand#parameters()}</td>
 *      <td>
 *          Command parameters map. May be {@code null} or empty. If not, SPI handles the following keys:
 *          <ul>
 *              <li>{@link #CMD_DRY_RUN_KEY} - If provided and {@link Boolean#parseBoolean(String)}
 *                  returns {@code true}, then SPI goes through all the motions of running a command,
 *                  but makes no actual changes (does not start any instances).</li>
 *          <ul>
 *      </td>
 * </tr>
 * </table>
 * <p>
 * If command's action is not recognized exception is thrown by SPI.
 *
 * <h2 class="header">Java Example</h2>
 * GridEc2LiteCloudSpi can be configured as follows:
 * <pre name="code" class="java">
 * GridEc2LiteCloudSpi cloudSpi = new GridEc2LiteCloudSpi();
 *
 * // Override default cloud ID.
 * cloudSpi.setCloudId("myCloud");
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default cloud SPI.
 * cfg.setCloudSpi(cloudSpi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridEc2LiteCloudSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *     ...
 *     &lt;property name="cloudSpi"&gt;
 *         &lt;bean class="org.gridgain.grid.spi.cloud.ec2lite.GridEc2LiteCloudSpi"&gt;
 *             &lt;property name="cloudId" value="myCloud"/&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCloudSpi
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridEc2LiteCloudSpi extends GridSpiAdapter implements GridCloudSpi, GridEc2LiteCloudSpiMBean {
    /** Collection parameter value delimiter. */
    public static final String VAL_DELIM = "|";

    /** Image architecture. */
    public static final String IMG_ARCH = "Architecture";

    /** Image location. */
    public static final String IMG_LOC = "Location";

    /** Image visibility. */
    public static final String IMG_PUBLIC = "Visibility";

    /** Image state. */
    public static final String IMG_STATE = "State";

    /** Instance monitoring state. */
    public static final String INST_MON_STATE = "MonitoringState";

    /** Instance state name. */
    public static final String INST_STATE = "StateName";

    /** Instance state code. */
    public static final String INST_STATE_CODE = "StateCode";

    /** Instance state transition reason. */
    public static final String INST_STATE_TRANS_REASON = "StateTransitionReason";

    /** Instance key pair name. */
    public static final String INST_KEY_PAIR_NAME = "KeyName";

    /** Instance launch time. */
    public static final String INST_LAUNCH_TIME = "LaunchTime";

    /** Instance AMI launch index. */
    public static final String INST_AMI_LAUNCH_IDX = "AmiLaunchIndex";

    /** Instance private DNS name. */
    public static final String INST_PRIV_DNS = "PrivateDnsName";

    /** Instance public DNS name. */
    public static final String INST_PUB_DNS = "PublicDnsName";

    /** Instance placement. */
    public static final String INST_PLACEMENT = "Placement";

    /** Security group description. */
    public static final String GRP_DESCR = "Description";

    /** Image/security group owner ID. */
    public static final String OWNER_ID = "OwnerId";

    /** Image/instance product codes IDs. */
    public static final String PRODUCT_CODE_IDS = "ProductCodeIds";

    /** Image/instance type. Refer to Amazon EC2 documentation for possible values. */
    public static final String INST_TYPE = "Type";

    /** Image/instance platform. */
    public static final String INST_PLATFORM = "Platform";

    /** Image/instance kernel ID. */
    public static final String IMG_KERNEL_ID = "KernelId";

    /** Image/instance ramdisk ID. */
    public static final String IMG_RAMDISK_ID = "RamdiskId";

    /** Instance monitoring flag (command parameter). */
    public static final String INST_MON = "Monitoring";

    /** Instance main S3 bucket (command parameter). */
    public static final String INST_MAIN_S3_BUCKET = "MainS3Bucket";

    /** Instance user S3 bucket (command parameter). */
    public static final String INST_USER_S3_BUCKET = "UserS3Bucket";

    /** Flag of EC2 keys pass to instance (command parameter). */
    public static final String INST_PASS_EC2_KEYS = "PassEc2Keys";

    /** Force instance stop (command parameter). */
    public static final String FORCE_STOP = "forceStop";

    /** Force instance stop (command parameter). */
    public static final String CMD_DRY_RUN_KEY = "gridgain.ec2.cloud.cmd.dry.run";

    /** Instance JVM options (command parameter). */
    public static final String INST_JVM_OPTS = "JvmOpts";

    /** Security group IP permissions count. */
    public static final String GRP_IP_PERMS_CNT = "IpPermissionSize";

    /** Security group IP permission. */
    public static final String GRP_IP_PERM = "IpPermission";

    /** IP permission from port. */
    public static final String IP_PERM_FROM_PORT = "FromPort";

    /** IP permission to port. */
    public static final String IP_PERM_TO_PORT = "ToPort";

    /** IP permission IP range. */
    public static final String IP_PERM_IP_RANGE = "IpRange";

    /** IP permission IP protocol. */
    public static final String IP_PERM_IP_PROTO = "IpProtocol";

    /** IP permissions User ID - Group pair. Used when creating {@link GridCloudResource} for Amazon IpPermission. */
    public static final String USER_ID_GRP_PAIR = "UserIdGroupPair";

    /** Pair constant to represent pair when creating {@link GridCloudResource} for Amazon IpPermission. */
    public static final String PAIR = "Pair";

    /** User ID in pair. */
    public static final String PAIR_USER_ID = "UserId";

    /** Group name in pair. */
    static final String PAIR_GRP = "GroupName";

    /** Instance state 'pending'. */
    public static final String INST_PENDING_STATE = "pending";

    /** Instance state 'running'. */
    public static final String INST_RUNNING_STATE = "running";

    /** Instance state 'shutting-down'. */
    @SuppressWarnings({"UnusedDeclaration"})
    public static final String INST_SHUTTING_DOWN_STATE = "shutting-down";

    /** Instance state 'stopping'. */
    public static final String INST_STOPPING_STATE = "stopping";

    /** Instance state 'stopped'. */
    public static final String INST_STOPPED_STATE = "stopped";

    /** Instance state 'terminated'. */
    public static final String INST_TERMINATED_STATE = "terminated";

    /** EC2 instance ID node attribute. */
    public static final String ATTR_EC2_INSTANCE_ID = "GG_EC2_INSTANCE_ID";

    /** Run instance action. */
    public static final String RUN_INST_ACT = "run";

    /** Terminate instance action. */
    public static final String TERMINATE_INST_ACT = "terminate";

    /** Terminate all instances action. */
    public static final String TERMINATE_ALL_INST_ACT = "terminate-all";

    /** Start instance action. */
    public static final String START_INST_ACT = "start";

    /** Stop instance action. */
    public static final String STOP_INST_ACT = "stop";

    /** Start all instance action. */
    public static final String START_ALL_INST_ACT = "start-all";

    /** Stop all instance action. */
    public static final String STOP_ALL_INST_ACT = "stop-all";

    /** Cloud name prefix for unnamed clouds. */
    private static final String CLOUD_ID_PREF = "ec2-cloud-";

    /** Default check state frequency in milliseconds. */
    public static final long DFLT_STATE_CHECK_FREQ = 10000;

    /** Minimum check state frequency in milliseconds. Values below are not recommended. */
    public static final int MIN_STATE_CHECK_FREQ = 2000;

    /** Cloud EC2 API version parameter. */
    private static final String CLOUD_API_VER_PARAM = "ec2-api-ver";

    /** Cloud EC2 API version value. */
    private static final String CLOUD_API_VER_VAL = "1.1.1";

    /** Cloud parameter key for last cloud update time. */
    private static final String CLOUD_LAST_UPD_TIME_PARAM = "last-cloud-upd-time";

    /** Cloud index for unnamed clouds. */
    private static AtomicInteger cloudIdGen = new AtomicInteger();

    /** Cloud ID. */
    private String cloudId = CLOUD_ID_PREF + cloudIdGen.incrementAndGet();

    /** Frequency to check cloud state change. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long stateCheckFreq = DFLT_STATE_CHECK_FREQ;

    /** Cloud SPI listener. */
    private volatile GridCloudSpiListener lsnr;

    /** Grid name. */
    private String gridName;

    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Cloud commands to execute. */
    private Map<UUID, GridCloudCommand> cmds = new LinkedHashMap<UUID, GridCloudCommand>();

    /** Mutex. */
    private final Object mux = new Object();

    /** Cloud control thread. */
    private CloudControlThread ctrlThread;

    /** EC2 access key. */
    private String accessKeyId;

    /** EC2 secret key. */
    private String secretAccessKey;

    /** Proxy host. */
    private String prxHost;

    /** Proxy port. */
    private int prxPort = -1;

    /** Proxy user name. */
    private String prxUser;

    /** Proxy password. */
    private String prxPwd;

    /** EC2 client. */
    private AmazonEC2 ec2;

    /** If testMode is <tt>true</tt> SPI will not call EC2 it will use mock to emulate calls. */
    private boolean testMode;

    /** Enable monitoring by default flag. */
    private boolean enableMon;

    /** IDs of EC2 images used in this cloud. */
    private List<String> imgIds;

    private GridCloudSpiSnapshot lastSnp;

    /** */
    private Collection<Image> imgs;

    /** */
    private Collection<SecurityGroup> secGrps;

    /** Discovery listener. */
    private final GridLocalEventListener discoLsnr = new GridLocalEventListener() {
        @SuppressWarnings({"NakedNotify"})
        @Override public void onEvent(GridEvent evt) {
            assert evt instanceof GridDiscoveryEvent;

            UUID nodeId = ((GridDiscoveryEvent)evt).eventNodeId();

            if (evt.type() == EVT_NODE_JOINED) {
                GridNode node = getSpiContext().node(nodeId);

                if (node != null && node.attribute(ATTR_EC2_INSTANCE_ID) != null)
                    synchronized (mux) {
                        mux.notifyAll();
                    }
            }
            else
                synchronized (mux) {
                    mux.notifyAll();
                }
        }
    };

    /** {@inheritDoc} */
    @Override public String getCloudId() {
        return cloudId;
    }

    /**
     * Sets Cloud ID.
     *
     * @param cloudId Cloud ID.
     */
    @GridSpiConfiguration(optional = false)
    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    /** {@inheritDoc} */
    @Override public long getStateCheckFrequency() {
        return stateCheckFreq;
    }

    /**
     * Sets frequency to check cloud state change.
     *
     * @param stateCheckFreq Frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setStateCheckFrequency(long stateCheckFreq) {
        this.stateCheckFreq = stateCheckFreq;
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridCloudSpiListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * Sets EC2 access key.
     *
     * @param accessKeyId EC2 access key.
     */
    @GridSpiConfiguration(optional = true)
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    /**
     * Sets EC2 secret key.
     *
     * @param secretAccessKey EC2 access key.
     */
    @GridSpiConfiguration(optional = true)
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    /** {@inheritDoc} */
    @Override public String getProxyHost() {
        return prxHost;
    }

    /**
     * Sets HTTP proxy host to use to connect to EC2 service. This is not required parameter.
     *
     * @param prxHost HTTP proxy host.
     */
    @GridSpiConfiguration(optional = true)
    public void setProxyHost(String prxHost) {
        this.prxHost = prxHost;
    }

    /** {@inheritDoc} */
    @Override public int getProxyPort() {
        return prxPort;
    }

    /**
     * Sets HTTP proxy port to use to connect to EC2 service. This is not required parameter.
     *
     * @param prxPort HTTP proxy port.
     */
    @GridSpiConfiguration(optional = true)
    public void setProxyPort(int prxPort) {
        this.prxPort = prxPort;
    }

    /** {@inheritDoc} */
    @Override public String getProxyUsername() {
        return prxUser;
    }

    /**
     * Sets HTTP proxy user name to use to connect to EC2 service. This is not required parameter.
     *
     * @param prxUser HTTP proxy user name.
     */
    @GridSpiConfiguration(optional = true)
    public void setProxyUsername(String prxUser) {
        this.prxUser = prxUser;
    }

    /**
     * Sets HTTP proxy password to use to connect to EC2 service. This is not required parameter.
     *
     * @param prxPwd HTTP proxy password.
     */
    @GridSpiConfiguration(optional = true)
    public void setProxyPassword(String prxPwd) {
        this.prxPwd = prxPwd;
    }



    /**
     * Gets if test mode is set or not. SPI will not call EC2 in test mode
     * it will use mock to emulate calls.
     *
     * @return <tt>true</tt> if test mode is using, otherwise <tt>false</tt>.
     */
    public boolean isTestMode() {
        return testMode;
    }

    /**
     * Sets test mode. SPI will not call EC2 in test mode it will use mock to emulate calls.
     *
     * @param testMode <tt>true</tt> if test mode is using, otherwise <tt>false</tt>.
     */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /**
     * Sets EC2 image IDs for using in this cloud.
     *
     * @param imgIds EC2 images IDs.
     */
    @GridSpiConfiguration(optional = true)
    public void setImageIds(List<String> imgIds) {
        this.imgIds = imgIds;
    }

    /** {@inheritDoc} */
    @Override public List<String> getImageIds() {
        return imgIds;
    }

    /**
     * Sets monitoring flag.
     *
     * @param enableMon CloudWatch monitoring flag.
     */
    @GridSpiConfiguration(optional = true)
    public void setEnableMonitoring(boolean enableMon) {
        this.enableMon = enableMon;
    }

    /** {@inheritDoc} */
    @Override public boolean isEnableMonitoring() {
        return enableMon;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        this.gridName = gridName;

        if (accessKeyId == null && secretAccessKey == null && !testMode) {
            try {
                GridEc2Keys keys = GridEc2Helper.getEc2Credentials(null);

                accessKeyId = keys.getAccessKeyId();
                secretAccessKey = keys.getSecretAccessKey();
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to get EC2 credentials.", e);
            }
        }

        assertParameter(!F.isEmpty(cloudId), "!F.isEmpty(cloudId)");
        assertParameter(stateCheckFreq > 0, "stateCheckFreq > 0");

        if (!testMode) {
            assertParameter(!F.isEmpty(accessKeyId), "!F.isEmpty(accessKeyId)");
            assertParameter(!F.isEmpty(secretAccessKey), "!F.isEmpty(secretAccessKey)");
        }

        registerMBean(gridName, this, GridEc2LiteCloudSpiMBean.class);

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("cloudId", cloudId));
            log.debug(configInfo("stateCheckFreq", stateCheckFreq));
            log.debug(configInfo("enableMon", enableMon));
            log.debug(configInfo("testMode", testMode));
            log.debug(configInfo("imgIds", imgIds));

            if (prxHost != null) {
                log.debug(configInfo("prxHost", prxHost));
                log.debug(configInfo("prxPort", prxPort));
                log.debug(configInfo("prxUser", prxUser));
            }
        }

        initEC2Client();

        if (stateCheckFreq < MIN_STATE_CHECK_FREQ)
            U.warn(log, "State check frequency is too low (recommended " + MIN_STATE_CHECK_FREQ + " ms or higher): " +
                stateCheckFreq);

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        deactivate();

        unregisterMBean();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public GridCloudSpiSnapshot activate() throws GridSpiException {
        getSpiContext().addLocalEventListener(discoLsnr, EVTS_DISCOVERY);

        lastSnp = makeSnapshot();

        // Start control thread after snapshot is ready.
        ctrlThread = new CloudControlThread();
        ctrlThread.start();

        return lastSnp;
    }

    /** {@inheritDoc} */
    @Override public void deactivate() {
        getSpiContext().removeLocalEventListener(discoLsnr);

        U.interrupt(ctrlThread);
        U.join(ctrlThread, log);
    }

    /**
     * Creates EC2 client based on current SPI parameters.
     */
    private void initEC2Client() {
        ClientConfiguration ec2config = new ClientConfiguration();

        if (prxHost != null) {
            ec2config.setProxyHost(prxHost);

            if (prxPort != -1)
                ec2config.setProxyPort(prxPort);

            if (prxUser != null) {
                ec2config.setProxyUsername(prxUser);

                if (prxPwd != null)
                    ec2config.setProxyPassword(prxPwd);
            }
        }

        // In test mode, the test should inject client itself.
        ec2 = testMode ? null : new AmazonEC2Client(new BasicAWSCredentials(
            accessKeyId, secretAccessKey), ec2config);
    }

    /** {@inheritDoc} */
    @Override public void process(GridCloudCommand cmd, UUID cmdExecId) throws GridSpiException {
        assert cmd != null;
        assert cmdExecId != null;

        String act = cmd.action();

        int num = cmd.number();

        Collection<GridCloudResource> rsrcs = cmd.resources();

        if (RUN_INST_ACT.equalsIgnoreCase(act)) {
            if (num <= 0)
                throw new GridSpiException("Invalid command (number should be positive): " + cmd);

            int imgCnt = 0;

            if (rsrcs != null && !rsrcs.isEmpty())
                for (GridCloudResource rsrc : rsrcs) {
                    int type = rsrc.type();

                    if (type != CLD_IMAGE && type != CLD_SECURITY_GROUP)
                        throw new GridSpiException("Invalid command (resources contain unsupported resource): " + cmd);
                    else if (type == CLD_IMAGE) {
                        imgCnt++;

                        if (imgCnt > 1)
                            // Command is invalid, no need to continue.
                            break;
                    }
                }

            if (imgCnt != 1)
                throw new GridSpiException("Invalid command (exactly one image resource should be provided): " + cmd);

            if (log.isDebugEnabled())
                log.debug("Received command to run instances: " + cmd);
        }
        else if (TERMINATE_INST_ACT.equalsIgnoreCase(act)) {
            if (rsrcs == null || rsrcs.isEmpty())
                throw new GridSpiException("Invalid command (resources cannot be empty): " + cmd);

            boolean terminateByImage = false;

            for (GridCloudResource rsrc : rsrcs)
                if (rsrc.type() == CLD_IMAGE) {
                    if (rsrcs.size() != 1)
                        throw new GridSpiException("Invalid command (if image resource is provided it should be the " +
                            "only resource): " + cmd);
                    else
                        terminateByImage = true;
                } else if (rsrc.type() != CLD_INSTANCE)
                    throw new GridSpiException("Invalid command (resources contain unsupported resource): " + cmd);

            if (terminateByImage) {
                if (num <= 0)
                    throw new GridSpiException("Invalid command (number should be positive): " + cmd);
            }
            else
                if (num != 0 && num != rsrcs.size())
                    throw new GridSpiException("Invalid command (number should be zero or equal to resources size): " +
                        cmd);

            if (log.isDebugEnabled())
                log.debug("Received command to terminate instances: " + cmd);
        }
        else if (TERMINATE_ALL_INST_ACT.equalsIgnoreCase(act)) {
            if (log.isDebugEnabled())
                log.debug("Received command to terminate all instances: " + cmd);
        }
        else if (START_INST_ACT.equalsIgnoreCase(act)) {
            if (rsrcs == null || rsrcs.isEmpty())
                throw new GridSpiException("Invalid command (resources cannot be empty): " + cmd);

            if (num != 0 && num != rsrcs.size())
                    throw new GridSpiException("Invalid command (number should be zero or equal to resources size): " +
                        cmd);

            for (GridCloudResource rsrc : rsrcs)
                if (rsrc.type() != CLD_INSTANCE)
                    throw new GridSpiException("Invalid command (resources contain unsupported resource): " + cmd);
        }
        else if (START_ALL_INST_ACT.equalsIgnoreCase(act)) {
            if (log.isDebugEnabled())
                log.debug("Received command to start all instances: " + cmd);
        }
        else if (STOP_INST_ACT.equalsIgnoreCase(act)) {
            if (rsrcs == null || rsrcs.isEmpty())
                throw new GridSpiException("Invalid command (resources cannot be empty): " + cmd);

            if (num != 0 && num != rsrcs.size())
                    throw new GridSpiException("Invalid command (number should be zero or equal to resources size): " +
                        cmd);

            for (GridCloudResource rsrc : rsrcs)
                if (rsrc.type() != CLD_INSTANCE)
                    throw new GridSpiException("Invalid command (resources contain unsupported resource): " + cmd);

            if (log.isDebugEnabled())
                log.debug("Received command to stop instances: " + cmd);
        }
        else if (STOP_ALL_INST_ACT.equalsIgnoreCase(act)) {
            if (log.isDebugEnabled())
                log.debug("Received command to stop all instances: " + cmd);
        }
        else
            throw new GridSpiException("Invalid command (action unknown): " + cmd);

        synchronized (mux) {
            cmds.put(cmdExecId, cmd);

            mux.notifyAll();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> compare(
        GridCloudSpiSnapshot oldSnp, GridCloudSpiSnapshot newSnp) {
        assert oldSnp != null;
        assert newSnp != null;

        if (F.isEmpty(oldSnp.getResources()) && F.isEmpty(newSnp.getResources()))
            return Collections.emptyList();

        Map<String, GridCloudResource> oldRsrcs = new LinkedHashMap<String, GridCloudResource>();

        if (!F.isEmpty(oldSnp.getResources()))
            for (GridCloudResource rsrc : oldSnp.getResources()) {
                assert rsrc.id() != null;

                oldRsrcs.put(rsrc.id(), rsrc);
            }

        Map<String, GridCloudResource> newRsrcs = new LinkedHashMap<String, GridCloudResource>();

        if (!F.isEmpty(newSnp.getResources()))
            for (GridCloudResource rsrc : newSnp.getResources()) {
                assert rsrc.id() != null;
                assert rsrc.type() == CLD_INSTANCE || rsrc.type() == CLD_IMAGE || rsrc.type() == CLD_SECURITY_GROUP ||
                    rsrc.type() == CLD_NODE;

                newRsrcs.put(rsrc.id(), rsrc);
            }

        Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> res =
            new LinkedList<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>>();

        for (Iterator<Map.Entry<String, GridCloudResource>> iter = newRsrcs.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, GridCloudResource> e = iter.next();

            GridCloudResource newRsrc = e.getValue();

            GridCloudResource oldRsrc = oldRsrcs.remove(e.getKey());

            if (oldRsrc != null) {
                if (!newRsrc.equals(oldRsrc))
                    res.add(F.<GridCloudResource, GridCloudSpiResourceAction>t(newRsrc, CHANGED));

                iter.remove();
            }
            else
                res.add(F.<GridCloudResource, GridCloudSpiResourceAction>t(newRsrc, ADDED));
        }

        if (!oldRsrcs.isEmpty())
            for (GridCloudResource rsrc : oldRsrcs.values())
                res.add(F.<GridCloudResource, GridCloudSpiResourceAction>t(rsrc, REMOVED));

        return res;
    }

    /**
     * Checks cloud state and executes commands.
     */
    private void checkCloud() {
        Map<UUID, GridCloudCommand> tmp = null;

        synchronized (mux) {
            if (!cmds.isEmpty()) {
                tmp = cmds;

                cmds = new LinkedHashMap<UUID, GridCloudCommand>();
            }
        }

        if (!F.isEmpty(tmp)) {
            assert tmp != null;

            for (Map.Entry<UUID, GridCloudCommand> e : tmp.entrySet()) {
                UUID id = e.getKey();
                GridCloudCommand cmd = e.getValue();

                String act = cmd.action();

                assert !F.isEmpty(act);

                if (RUN_INST_ACT.equalsIgnoreCase(act))
                    try {
                        runInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (TERMINATE_INST_ACT.equalsIgnoreCase(act))
                    try {
                        terminateInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (TERMINATE_ALL_INST_ACT.equalsIgnoreCase(act))
                    try {
                        terminateAllInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (STOP_INST_ACT.equalsIgnoreCase(act))
                    try {
                        stopInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (STOP_ALL_INST_ACT.equalsIgnoreCase(act))
                    try {
                        stopAllInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (START_INST_ACT.equalsIgnoreCase(act))
                    try {
                        startInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else if (START_ALL_INST_ACT.equalsIgnoreCase(act))
                    try {
                        startAllInstances(cmd);

                        notifySpiListenerOnCommand(true, id, cmd, null);
                    }
                    catch (GridSpiException ex) {
                        log.error(ex.getMessage());

                        notifySpiListenerOnCommand(false, id, cmd, ex.getMessage());
                    }
                else
                    assert false;
            }
        }

        notifySpiListenerOnChange();
    }

    /**
     * Runs Amazon EC2 instances by command.
     * <p>
     * Command may be very complex and contain plenty of parameters.
     * Refer to class documentation for details.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    private void runInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;

        RunInstancesRequest req = createRunInstancesRequest(cmd);

        Map<String, String> params = cmd.parameters();

        if (params != null && Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY))) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances run omitted.");

            return;
        }

        RunInstancesResult res;

        try {
            res = ec2.runInstances(req);
        }
        catch (AmazonClientException e) {
            throw new GridSpiException("Failed to perform run instances request.", e);
        }

        if (log.isDebugEnabled())
            log.debug("Sent run instances request [imgId=" + req.getImageId() +
                ", minCount=" + req.getMinCount() + ", maxCount=" + req.getMaxCount() + ']');

        Collection<String> instIds = new LinkedList<String>();

        boolean throwEx = false;

        for (Instance item : res.getReservation().getInstances()) {
            String instId = item.getInstanceId();
            String imgId = item.getImageId();

            instIds.add(instId);

            if (log.isDebugEnabled())
                log.debug("Added (ran) new instance [instId=" + instId + ", imgId=" + imgId + ']');

            if (!req.getImageId().equals(imgId))
                throwEx = true;
        }

        if (!instIds.isEmpty()) {
            if (req.isMonitoring()) {
                try {
                    ec2.monitorInstances(new MonitorInstancesRequest().withInstanceIds(instIds));
                }
                catch (AmazonClientException e) {
                    U.error(log, "Failed to start instance monitoring.", e);
                }

                if (log.isDebugEnabled())
                    log.debug("Started instances monitoring: " + instIds);
            }
        }

        if (throwEx || instIds.size() != cmd.number())
            throw new GridSpiException("Cloud command has not been successfully executed: " + cmd);
    }

    /**
     * Creates Amazon EC2 run instances request by provided command.
     *
     * @param cmd Cloud command.
     * @return EC2 run instances request.
     * @throws GridSpiException If any exception occurs.
     */
    private RunInstancesRequest createRunInstancesRequest(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;

        Collection<GridCloudResource> rsrcs = cmd.resources();
        int num = cmd.number();

        assert rsrcs != null && !rsrcs.isEmpty();
        assert num > 0;

        GridCloudResource img = null;
        Collection<String> grps = new ArrayList<String>();

        // Separate image and security groups
        for (GridCloudResource rsrc : rsrcs)
            if (rsrc.type() == CLD_IMAGE)
                img = rsrc;
            else if (rsrc.type() == CLD_SECURITY_GROUP)
                grps.add(rsrc.id());

        assert img != null;

        Map<String, String> imgParams = img.parameters();
        Map<String, String> cmdParams = cmd.parameters();

        if (imgParams == null)
            throw new GridSpiException("Unable to process command (image parameters are null) [cmd=" + cmd +
                ", image=" + img + ']');

        RunInstancesRequest req = new RunInstancesRequest();

        req.setImageId(img.id());
        req.setMinCount(num);
        req.setMaxCount(num);

        if (!grps.isEmpty())
            req.setSecurityGroups(grps);

        String val;

        if (!F.isEmpty(val = imgParams.get(IMG_KERNEL_ID)))
            req.setKernelId(val);

        if (!F.isEmpty(val = imgParams.get(IMG_RAMDISK_ID)))
            req.setRamdiskId(val);

        Collection<String> userDataList = new LinkedList<String>();

        if (cmdParams != null) {
            if (!F.isEmpty(val = cmdParams.get(INST_TYPE)))
                req.setInstanceType(val);

            if (!F.isEmpty(val = cmdParams.get(INST_PLACEMENT)))
                req.setPlacement(new Placement(val));

            if (!F.isEmpty(val = cmdParams.get(INST_KEY_PAIR_NAME)))
                req.setKeyName(val);

            if (!F.isEmpty(val = cmdParams.get(INST_MON)))
                req.setMonitoring(Boolean.parseBoolean(val));

            if (!F.isEmpty(val = cmdParams.get(INST_PASS_EC2_KEYS)) && Boolean.parseBoolean(val)) {
                userDataList.add(GRIDGAIN_ACCESS_KEY_ID_KEY + '=' + accessKeyId);
                userDataList.add(GRIDGAIN_SECRET_KEY_KEY + '=' + secretAccessKey);
            }

            if (!F.isEmpty(val = cmdParams.get(INST_MAIN_S3_BUCKET)))
                userDataList.add(GRIDGAIN_MAIN_S3_BUCKET_KEY + '=' + val);

            if (!F.isEmpty(val = cmdParams.get(INST_USER_S3_BUCKET)))
                userDataList.add(GRIDGAIN_EXT_S3_BUCKET_KEY + '=' + val);

            if (!F.isEmpty(val = cmdParams.get(INST_JVM_OPTS)))
                userDataList.add(val);
        }

        if (req.isMonitoring() == null)
            // Monitoring was not set from params, set default value
            req.setMonitoring(enableMon);

        if (!userDataList.isEmpty())
            req.setUserData(new String(Base64.encodeBase64(F.concat(userDataList, USER_DATA_DELIM).getBytes())));

        return req;
    }

    /**
     * Terminates Amazon EC2 cloud instances by command.
     * <p>
     * Refer to class documentation for details on how to setup such kind of command.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs or no alive instances found
     * or not all provided instances found.
     */
    private void terminateInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert TERMINATE_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<GridCloudResource> rsrcs = cmd.resources();
        int num = cmd.number();

        assert rsrcs != null && !rsrcs.isEmpty();
        assert num >= 0;

        Collection<Instance> runInsts = getEc2Instances(INST_PENDING_STATE, INST_RUNNING_STATE,
            INST_STOPPING_STATE, INST_STOPPED_STATE);

        if (F.isEmpty(runInsts))
            throw new GridSpiException("There are no alive instances to terminate for command: " + cmd);

        Collection<String> instIds = new LinkedList<String>();

        GridCloudResource rsrc = F.first(rsrcs);

        if (rsrc != null && rsrc.type() == CLD_IMAGE) {
            for (Instance inst : runInsts)
                if (inst.getImageId().equals(rsrc.id())) {
                    instIds.add(inst.getInstanceId());

                    if (instIds.size() == num)
                        break;
                }

            if (instIds.size() != num)
                throw new GridSpiException("Necessary instances number to stop was not found for command: " + cmd);
        }
        else {
            Collection<String> runInstIds = F.transform(runInsts, new C1<Instance, String>() {
                @Override public String apply(Instance inst) {
                    return inst.getInstanceId();
                }
            });

            for (GridCloudResource rsrc0 : rsrcs) {
                assert rsrc0.type() == CLD_INSTANCE;

                String id = rsrc0.id();

                if (runInstIds.contains(id))
                    instIds.add(id);
                else
                    throw new GridSpiException("Unable to find alive EC2 instance with given ID [id=" + id +
                        ", cmd=" + cmd + ']');
            }
        }

        Map<String, String> params = cmd.parameters();

        if (params != null && Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY))) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances termination omitted.");

            return;
        }

        terminateInstances(instIds);
    }

    /**
     * Terminates all EC2 cloud instances by command.
     * <p>
     * In order for command to execute properly instances provided in command
     * should be in one of the following states:
     * <ul>
     * <li>{@link #INST_RUNNING_STATE};
     * <li>{@link #INST_STOPPED_STATE};
     * <li>{@link #INST_STOPPING_STATE};
     * <li>{@link #INST_PENDING_STATE}.
     * </ul>
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs or no instances in required states found.
     */
    private void terminateAllInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert TERMINATE_ALL_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<String> instIds = F.transform(getEc2Instances(INST_PENDING_STATE, INST_RUNNING_STATE,
            INST_STOPPING_STATE, INST_STOPPED_STATE),
                new C1<Instance, String>() {
                    @Override public String apply(Instance e) {
                        return e.getInstanceId();
                    }
        });

        if (instIds.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("There are no instances to terminate by command (safely ignoring): " + cmd);

            return;
        }

        Map<String, String> params = cmd.parameters();

        if (params != null && Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY))) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances termination omitted.");

            return;
        }

        terminateInstances(instIds);
    }

    /**
     * Send request to Amazon EC2 to terminate given instances.
     *
     * @param instIds Instances IDs to terminate. Not {@code null} and not empty.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    private void terminateInstances(Collection<String> instIds) throws GridSpiException {
        assert !F.isEmpty(instIds);

        TerminateInstancesRequest req = new TerminateInstancesRequest().withInstanceIds(instIds);

        TerminateInstancesResult res;

        try {
            res = ec2.terminateInstances(req);
        }
        catch (AmazonClientException e) {
            throw new GridSpiException("Failed to perform terminate instances request.", e);
        }

        Collection<String> terminatedInstIds = F.transform(res.getTerminatingInstances(),
            new C1<InstanceStateChange, String>() {
                @Override public String apply(InstanceStateChange ist) {
                    return ist.getInstanceId();
                }
            });

        if (instIds.size() != terminatedInstIds.size() || !instIds.containsAll(terminatedInstIds))
            throw new GridSpiException("Instances were not successfully terminated.");
    }

    /**
     * Starts EC2 instances by command.
     * <p>
     * Instances provided in command should be in {@link #INST_STOPPED_STATE} state
     * in order for command to execute properly.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs or not all provided instances are in required state.
     */
    private void startInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert START_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<GridCloudResource> rsrcs = cmd.resources();

        assert rsrcs != null && !rsrcs.isEmpty();

        Collection<String> stoppedInstIds = F.transform(getEc2Instances(INST_STOPPED_STATE),
            new C1<Instance, String>() {
                @Override public String apply(Instance inst) {
                    return inst.getInstanceId();
                }
            });

        Collection<String> startInstIds = new ArrayList<String>(rsrcs.size());

        for (GridCloudResource rsrc : rsrcs) {
            assert rsrc.type() == CLD_INSTANCE;

            String id = rsrc.id();

            if (stoppedInstIds.contains(id))
                startInstIds.add(id);
            else
                throw new GridSpiException("Unable to find stopped EC2 instance with given ID [id=" + id +
                        ", cmd=" + cmd + ']');
        }

        Map<String, String> params = cmd.parameters();

        if (params != null && Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY))) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances start omitted.");

            return;
        }

        startInstances(startInstIds);
    }

    /**
     * Starts all stopped instances.
     * <p>
     * This method gets all stopped instances from Amazon EC2
     * and attempts to start them.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs or no instances in required states found.
     */
    private void startAllInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert START_ALL_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<String> instIds = F.transform(getEc2Instances(INST_STOPPED_STATE),
            new C1<Instance, String>() {
                @Override public String apply(Instance inst) {
                    return inst.getInstanceId();
                }
            });

        if (instIds.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("There are no instances to start by command (safely ignoring): " + cmd);

            return;
        }

        Map<String, String> params = cmd.parameters();

        if (params != null && Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY))) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances start omitted.");

            return;
        }

        startInstances(instIds);
    }

    /**
     * Sends request to Amazon EC2 to start given instances.
     * <p>
     * Provided instances should be in {@link #INST_STOPPED_STATE} state
     * in order for command to execute properly.
     *
     * @param instIds Instances IDs to start. Not {@code null} and not empty.
     * @throws GridSpiException  Thrown if any exception occurs.
     */
    private void startInstances(Collection<String> instIds) throws GridSpiException {
        assert !F.isEmpty(instIds);

        StartInstancesRequest req = new StartInstancesRequest().withInstanceIds(instIds);

        StartInstancesResult res;

        try {
            res = ec2.startInstances(req);
        }
        catch (AmazonClientException e) {
            throw new GridSpiException("Failed to perform start instances request.", e);
        }

        Collection<String> startInstIds = F.transform(res.getStartingInstances(),
            new C1<InstanceStateChange, String> () {
                @Override public String apply(InstanceStateChange ist) {
                    return ist.getInstanceId();
                }
            });

        if (instIds.size() != startInstIds.size() || !instIds.containsAll(startInstIds))
            throw new GridSpiException("Instances were not successfully started.");
    }

    /**
     * Stops EC2 instances by command.
     * <p>
     * Command may contain {@link #CMD_DRY_RUN_KEY} and {@link #FORCE_STOP}
     * parameters. Refer to class documentation for details.
     * <p>
     * Instances provided in command should be in {@link #INST_RUNNING_STATE} state
     * in order for command to execute properly.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs or not all
     * provided instances are in required states.
     */
    private void stopInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert STOP_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<GridCloudResource> rsrcs = cmd.resources();

        assert rsrcs != null && !rsrcs.isEmpty();

        Collection<String> runningInstIds = F.transform(getEc2Instances(INST_RUNNING_STATE),
            new C1<Instance, String>() {
                @Override public String apply(Instance inst) {
                    return inst.getInstanceId();
                }
            });

        Collection<String> stopInstIds = new ArrayList<String>(rsrcs.size());

        for (GridCloudResource rsrc : rsrcs) {
            assert rsrc.type() == CLD_INSTANCE;

            String id = rsrc.id();

            if (runningInstIds.contains(id))
                stopInstIds.add(id);
            else
                throw new GridSpiException("Unable to find running EC2 instance with given ID [id=" + id +
                        ", cmd=" + cmd + ']');
        }

        Map<String, String> params = cmd.parameters();

        boolean force = false;
        boolean dryRun = false;

        if (params != null) {
            force = Boolean.parseBoolean(params.get(FORCE_STOP));
            dryRun = Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY));
        }

        if (dryRun) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances run omitted.");

            return;
        }

        stopInstances(stopInstIds, force);
    }

    /**
     * Stops all EC2 instances by command.
     * <p>
     * Command may contain {@link #CMD_DRY_RUN_KEY} and {@link #FORCE_STOP}
     * parameters. Refer to class documentation for details.
     * <p>
     * Instances provided in command should be in {@link #INST_RUNNING_STATE} state
     * in order for command to execute properly.
     *
     * @param cmd Cloud command.
     * @throws GridSpiException Thrown if any exception occurs.
     */
    private void stopAllInstances(GridCloudCommand cmd) throws GridSpiException {
        assert cmd != null;
        assert STOP_ALL_INST_ACT.equalsIgnoreCase(cmd.action());

        Collection<String> instIds = F.transform(getEc2Instances(INST_RUNNING_STATE),
            new C1<Instance, String>() {
                @Override public String apply(Instance inst) {
                    return inst.getInstanceId();
                }
            });

        if (instIds.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("There are no instances to stop by command (safely ignoring): " + cmd);

            return;
        }

        Map<String, String> params = cmd.parameters();

        boolean force = false;

        if (params != null && !params.isEmpty())
            force = Boolean.parseBoolean(params.get(FORCE_STOP));

        boolean dryRun = false;

        if (params != null) {
            force = Boolean.parseBoolean(params.get(FORCE_STOP));
            dryRun = Boolean.parseBoolean(params.get(CMD_DRY_RUN_KEY));
        }

        if (dryRun) {
            if (log.isDebugEnabled())
                log.debug("Dry run - instances run omitted.");

            return;
        }

        stopInstances(instIds, force);
    }

    /**
     * Sends request to Amazon EC2 to stop given instances.
     * <p>
     * Provided instances should be in {@link #INST_RUNNING_STATE} state
     * in order for command to execute properly.
     *
     * @param instIds Instances IDs to stop. Not {@code null} and not empty.
     * @param force Pass {@code true} to force instance stop.
     * @throws GridSpiException  Thrown if any exception occurs.
     */
    private void stopInstances(Collection<String> instIds, boolean force) throws GridSpiException {
        assert !F.isEmpty(instIds);

        StopInstancesRequest req = new StopInstancesRequest().withInstanceIds(instIds).withForce(force);

        StopInstancesResult res;

        try {
            res = ec2.stopInstances(req);
        }
        catch (AmazonClientException e) {
            throw new GridSpiException("Failed to perform start instances request.", e);
        }

        Collection<String> stopInstIds = F.transform(res.getStoppingInstances(),
            new C1<InstanceStateChange, String> () {
                @Override public String apply(InstanceStateChange ist) {
                    return ist.getInstanceId();
                }
            });

        if (instIds.size() != stopInstIds.size() || !instIds.containsAll(stopInstIds))
            throw new GridSpiException("Instances were not successfully stopped.");
    }

    /**
     * Notifies SPI listener on cloud change if there were any.
     */
    private void notifySpiListenerOnChange() {
        try {
            GridCloudSpiSnapshot snp = makeSnapshot();

            GridCloudSpiListener tmp = lsnr;

            if (tmp != null && (lastSnp == null || !compare(lastSnp, snp).isEmpty())) {
                lastSnp = snp;

                tmp.onChange(lastSnp);
            }
        }
        catch (GridSpiException e) {
            U.error(log, "Failed to make snapshot.", e);
        }
    }

    /**
     * Notifies SPI listener when cloud command has been processed.
     *
     * @param success Flag of successful command execution.
     * @param cmdExecId Cloud command execution ID.
     * @param cmd Cloud command.
     * @param msg Optional message.
     */
    private void notifySpiListenerOnCommand(boolean success, UUID cmdExecId, GridCloudCommand cmd, @Nullable String msg) {
        assert cmdExecId != null;
        assert cmd != null;

        GridCloudSpiListener tmp = lsnr;

        if (tmp != null)
            tmp.onCommand(success, cmdExecId, cmd, msg);
    }

    /**
     * Makes cloud snapshot.
     *
     * @return Cloud snapshot.
     * @throws GridSpiException If any exception occurs.
     */
    private GridCloudSpiSnapshot makeSnapshot() throws GridSpiException {
        Collection<GridCloudResource> rsrcs = new LinkedList<GridCloudResource>();

        for (SecurityGroup grp : getEc2SecurityGroups())
            rsrcs.add(createSecurityGroupResource(grp));

        Map<String, LinkedList<GridCloudResource>> nodes = new HashMap<String, LinkedList<GridCloudResource>>();

        for (GridNode node : getCloudNodes()) {
            GridCloudResource node0 = createNodeResource(node);

            LinkedList<GridCloudResource> nodes0 = F.addIfAbsent(nodes,
                node.<String>attribute(ATTR_EC2_INSTANCE_ID), F.<GridCloudResource>newLinkedList());

            assert nodes0 != null;

            nodes0.add(node0);
        }

        Map<String, LinkedList<GridCloudResource>> insts = new HashMap<String, LinkedList<GridCloudResource>>();

        for (Instance inst : getEc2Instances()) {
            GridCloudSpiResourceAdapter inst0 = createInstanceResource(inst);

            Collection<GridCloudResource> nodes0 = nodes.get(inst0.id());

            if (!F.isEmpty(nodes0)) {
                inst0.setLinks(nodes0);

                for (GridCloudResource node : nodes0) {
                    ((GridCloudSpiResourceAdapter)node).setLinks(F.<GridCloudResource>asList(inst0));

                    rsrcs.add(node);
                }
            }

            LinkedList<GridCloudResource> insts0 =
                F.addIfAbsent(insts, inst.getImageId(), F.<GridCloudResource>newLinkedList());

            assert insts0 != null;

            insts0.add(inst0);

            rsrcs.add(inst0);
        }

        for (Image img : getEc2Images()) {
            GridCloudSpiResourceAdapter img0 = createImageResource(img);

            Collection<GridCloudResource> insts0 = insts.get(img0.id());

            if (!F.isEmpty(insts0)) {
                img0.setLinks(insts0);

                for (GridCloudResource inst : insts0) {
                    Collection<GridCloudResource> links = inst.links();

                    if (F.isEmpty(links))
                        links = F.<GridCloudResource>asList(img0);
                    else {
                        links = new LinkedList<GridCloudResource>(links);

                        links.add(img0);
                    }

                    ((GridCloudSpiResourceAdapter)inst).setLinks(links);
                }
            }

            rsrcs.add(img0);
        }

        long time = System.currentTimeMillis();

        return new GridCloudSpiSnapshotAdapter(UUID.randomUUID(), time, getSpiContext().localNode().id(), rsrcs,
            F.asMap(CLOUD_API_VER_PARAM, CLOUD_API_VER_VAL, CLOUD_LAST_UPD_TIME_PARAM, String.valueOf(time)));
    }

    /**
     * Gets image resource from EC2 image.
     *
     * @param img EC2 image.
     * @return Image resource.
     */
    private GridCloudSpiResourceAdapter createImageResource(Image img) {
        assert img != null;

        Map<String, String> params = new HashMap<String, String>();

        params.put(IMG_ARCH, img.getArchitecture());
        params.put(IMG_LOC, img.getImageLocation());
        params.put(IMG_STATE, img.getState());
        params.put(INST_TYPE, img.getImageType());
        params.put(IMG_KERNEL_ID, img.getKernelId());
        params.put(OWNER_ID, img.getOwnerId());
        params.put(INST_PLATFORM, img.getPlatform());
        params.put(IMG_RAMDISK_ID, img.getRamdiskId());
        params.put(IMG_PUBLIC, String.valueOf(img.isPublic()));

        params.put(PRODUCT_CODE_IDS, F.concat(F.transform(img.getProductCodes(),
            new C1<ProductCode, String>() {
                @Override public String apply(ProductCode e) {
                    return e.getProductCodeId();
                }
            }), VAL_DELIM));

        return new GridCloudSpiResourceAdapter(img.getImageId(), CLD_IMAGE, cloudId, params);
    }

    /**
     * Gets instance resource from EC2 instance.
     *
     * @param inst EC2 instance.
     * @return Instance resource.
     */
    private GridCloudSpiResourceAdapter createInstanceResource(Instance inst) {
        assert inst != null;

        Map<String, String> params = new HashMap<String, String>();

        params.put(INST_STATE_TRANS_REASON, inst.getStateTransitionReason());
        params.put(INST_KEY_PAIR_NAME, inst.getKeyName());
        params.put(INST_AMI_LAUNCH_IDX, String.valueOf(inst.getAmiLaunchIndex()));
        params.put(INST_LAUNCH_TIME, String.valueOf(inst.getLaunchTime()));
        params.put(INST_TYPE, inst.getInstanceType());
        params.put(INST_STATE, inst.getState().getName());
        params.put(INST_STATE_CODE, String.valueOf(inst.getState().getCode()));
        params.put(IMG_KERNEL_ID, inst.getKernelId());
        params.put(IMG_RAMDISK_ID, inst.getRamdiskId());
        params.put(INST_MON_STATE, inst.getMonitoring().getState());
        params.put(INST_PLACEMENT, inst.getPlacement().getAvailabilityZone());
        params.put(INST_PLATFORM, inst.getPlatform());
        params.put(INST_PRIV_DNS, inst.getPrivateDnsName());
        params.put(INST_PUB_DNS, inst.getPublicDnsName());

        params.put(PRODUCT_CODE_IDS, F.concat(F.transform(inst.getProductCodes(),
            new C1<ProductCode, String>() {
                @Override public String apply(ProductCode e) {
                    return e.getProductCodeId();
                }
            }), VAL_DELIM));

        return new GridCloudSpiResourceAdapter(inst.getInstanceId(), CLD_INSTANCE, cloudId, params);
    }

    /**
     * Gets security group resource from EC2 security group.
     *
     * @param grp EC2 security group.
     * @return Security group resource.
     */
    private GridCloudResource createSecurityGroupResource(SecurityGroup grp) {
        assert grp != null;

        Map<String, String> params = new HashMap<String, String>();

        params.put(OWNER_ID, grp.getOwnerId());
        params.put(GRP_DESCR, grp.getDescription());

        List<IpPermission> perms = grp.getIpPermissions();

        int permSize = F.isEmpty(perms) ? 0 : perms.size();

        params.put(GRP_IP_PERMS_CNT, String.valueOf(permSize));

        for (int i = 0; i < permSize; i++) {
            IpPermission perm = perms.get(i);

            StringBuilder buf = new StringBuilder();

            buf.append('[').append(IP_PERM_IP_PROTO).append('=').append(perm.getIpProtocol()).append(VAL_DELIM)
                .append(IP_PERM_FROM_PORT).append('=').append(perm.getFromPort()).append(VAL_DELIM)
                .append(IP_PERM_TO_PORT).append('=').append(perm.getToPort()).append(VAL_DELIM)
                .append(IP_PERM_IP_RANGE).append('=').append(perm.getIpRanges());

            List<UserIdGroupPair> pairs = perm.getUserIdGroupPairs();

            int pairSize = F.isEmpty(pairs) ? 0 : pairs.size();

            if (pairSize > 0) {
                buf.append(VAL_DELIM).append(USER_ID_GRP_PAIR).append("=[");

                for (int j = 0; j < pairSize; j++) {
                    if (j != 0)
                        buf.append(',');

                    UserIdGroupPair pair = pairs.get(j);

                    buf.append(PAIR).append(j).append("=[")
                        .append(PAIR_USER_ID).append('=').append(pair.getUserId()).append(':')
                        .append(PAIR_GRP).append('=').append(pair.getGroupName()).append(']');
                }

                buf.append(']');
            }

            buf.append(']');

            params.put(GRP_IP_PERM + i, buf.toString());
        }

        return new GridCloudSpiResourceAdapter(grp.getGroupName(), CLD_SECURITY_GROUP, cloudId, params);
    }

    /**
     * Gets node resource from grid node.
     *
     * @param node Node.
     * @return Node resource.
     */
    private GridCloudResource createNodeResource(GridNode node) {
        assert node != null;

        return new GridCloudSpiResourceAdapter(node.id().toString(), CLD_NODE, cloudId);
    }

    /**
     * Gets EC2 images.
     *
     * @return EC2 images.
     * @throws GridSpiException If any exception occurs.
     */
    private Iterable<Image> getEc2Images() throws GridSpiException {
        if (imgs == null) {
            DescribeImagesRequest req = new DescribeImagesRequest();

            if (!F.isEmpty(imgIds))
                req.setImageIds(imgIds);

            try {
                imgs = ec2.describeImages(req).getImages();

                if (log.isDebugEnabled())
                    log.debug("Images initialized: " + imgs);

                return imgs;
            }
            catch (AmazonClientException e) {
                throw new GridSpiException("Failed to get EC2 images by request: " + req, e);
            }
        }
        else
            return imgs;
    }

    /**
     * Gets running EC2 instances.
     *
     * @param states Optional states of the instances for filtering.
     * @return EC2 instances.
     * @throws GridSpiException If any exception occurs.
     */
    private Collection<Instance> getEc2Instances(@Nullable String... states) throws GridSpiException {
        try {
            DescribeInstancesResult res;

            if (!F.isEmpty(states)) {
                DescribeInstancesRequest req = new DescribeInstancesRequest().withFilters(
                    new Filter("instance-state-name", Arrays.asList(states)));

                res = ec2.describeInstances(req);
            }
            else
                res = ec2.describeInstances();

            Collection<Instance> insts = new LinkedList<Instance>();

            for (Reservation rsrv : res.getReservations())
                insts.addAll(rsrv.getInstances());

            return insts;
        }
        catch (AmazonClientException e) {
            throw new GridSpiException("Failed to get EC2 instances.", e);
        }
    }

    /**
     * Gets EC2 security groups.
     *
     * @return EC2 security groups.
     * @throws GridSpiException If any exception occurs.
     */
    private Iterable<SecurityGroup> getEc2SecurityGroups() throws GridSpiException {
        if (secGrps == null) {
            try {
                secGrps = ec2.describeSecurityGroups().getSecurityGroups();

                if (log.isDebugEnabled())
                    log.debug("Security groups initialized: " + secGrps);

                return secGrps;
            }
            catch (AmazonClientException e) {
                throw new GridSpiException("Failed to get EC2 security groups.", e);
            }
        }
        else
            return secGrps;
    }

    /**
     * Gets nodes on cloud.
     *
     * @return Nodes.
     */
    private Iterable<GridNode> getCloudNodes() {
        return F.retain(getSpiContext().nodes(), true, new P1<GridNode>() {
            @Override public boolean apply(GridNode node) {
                return node.attribute(ATTR_EC2_INSTANCE_ID) != null;
            }
        });
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEc2LiteCloudSpi.class, this);
    }

    /**
     * Cloud control thread.
     */
    private class CloudControlThread extends GridSpiThread {
        /** Creates cloud control thread. */
        private CloudControlThread() {
            super(gridName, "grid-ec2lite-cloud-control", log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted()) {
                synchronized (mux) {
                    if (cmds.isEmpty())
                        mux.wait(stateCheckFreq);
                }

                checkCloud();
            }
        }
    }
}
