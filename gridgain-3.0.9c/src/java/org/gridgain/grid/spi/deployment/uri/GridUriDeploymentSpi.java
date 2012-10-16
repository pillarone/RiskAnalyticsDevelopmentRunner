// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.spi.deployment.uri.scanners.*;
import org.gridgain.grid.spi.deployment.uri.scanners.file.*;
import org.gridgain.grid.spi.deployment.uri.scanners.ftp.*;
import org.gridgain.grid.spi.deployment.uri.scanners.http.*;
import org.gridgain.grid.spi.deployment.uri.scanners.mail.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.*;

/**
 * Implementation of {@link GridDeploymentSpi} which can deploy tasks from
 * different sources like file system folders, FTP, email and HTTP.
 * There are different ways to deploy tasks in grid and every deploy method
 * depends on selected source protocol. This SPI is configured to work
 * with a list of URI's. Every URI contains all data about protocol/transport
 * plus configuration parameters like credentials, scan frequency, and others.
 * <p>
 * When SPI establishes a connection with a URI, it downloads deployable units
 * to the temporary directory in order to prevent it from any changes while
 * scanning. Use method {@link #setTemporaryDirectoryPath(String) setTemporaryDirectoryPath(String)})
 * to set custom temporary folder for downloaded deployment units.
 * <p>
 * SPI tracks all changes of every given URI. This means that if any file is
 * changed or deleted, SPI will re-deploy or delete corresponding tasks.
 * Note that the very first apply to {@link #findResource(String)} findClassLoader(String)}
 * is blocked until SPI finishes scanning all URI's at least once.
 * <p>
 * There are several deployable unit types supported:
 * <ul>
 * <li>GAR file.</li>
 * <li>Local disk folder with structure of unpacked GAR file.</li>
 * <li>Local disk folder containing only compiled Java classes.</li>
 * </ul>
 * <h1 class="header">GAR file</h1>
 * GAR file is a deployable unit. GAR file is based on <a href="http://www.gzip.org/zlib/">ZLIB</a>
 * compression format like simple JAR file and its structure is similar to WAR archive.
 * GAR file has {@code '.gar'} extension.
 * <p>
 * GAR file structure (file or directory ending with {@code '.gar'}):
 *   <pre class="snippet">
 *      META-INF/
 *              |
 *               - gridgain.xml
 *               - ...
 *      lib/
 *         |
 *          -some-lib.jar
 *          - ...
 *      xyz.class
 *      ...</pre>
 * <ul>
 * <li>
 * {@code META-INF/} entry may contain {@code gridgain.xml} file which is a
 * task descriptor file. The purpose of task descriptor XML file is to specify
 * all tasks to be deployed. This file is a regular
 * <a href="http://www.springframework.org/documentation">Spring</a> XML
 * definition file.  {@code META-INF/} entry may also contain any other file
 * specified by JAR format.
 * </li>
 * <li>
 * {@code lib/} entry contains all library dependencies.
 * </li>
 * <li>Compiled Java classes must be placed in the root of a GAR file.</li>
 * </ul>
 * GAR file may be deployed without descriptor file. If there is no descriptor file, SPI
 * will scan all classes in archive and instantiate those that implement
 * {@link GridTask} interface. In that case, all grid task classes must have a
 * public no-argument constructor. Use {@link GridTaskAdapter} adapter for
 * convenience when creating grid tasks.
 * <p>
 * By default, all downloaded GAR files that have digital signature in {@code META-INF}
 * folder will be verified and deployed only if signature is valid.
 * <p>
 * <h1 class="header">URI</h1>
 * This SPI uses a hierarchical URI definition. For more information about standard URI
 * syntax refer to {@link URI java.net.URI} documentation.
 * <blockquote class="snippet">
 * [<i>scheme</i><tt><b>:</b></tt>][<tt><b>//</b></tt><i>authority</i>][<i>path</i>][<tt><b>?</b></tt><i>query</i>][<tt><b>#</b></tt><i>fragment</i>]
 * </blockquote>
 * <p>
 * Every URI defines its own deployment repository which will be scanned for any changes.
 * URI itself has all information about protocol, connectivity, scan intervals and other
 * parameters.
 * <p>
 * URI's may contain special characters, like spaces. If {@code encodeUri}
 * flag is set to {@code true} (see {@link #setEncodeUri(boolean)}), then
 * URI 'path' field will be automatically encoded. By default this flag is
 * set to {@code true}.
 * <p>
 * <h1 class="header">Configuration</h1>
 * {@code GridUriDeploymentSpi} has the following optional configuration
 * parameters (there are no mandatory parameters):
 * <ul>
 * <li>
 * Temporary directory path where scanned GAR files and directories are
 * copied to (see {@link #setTemporaryDirectoryPath(String) setTemporaryDirectoryPath(String)}).
 * </li>
 * <li>
 * List of URIs to scan (see {@link #setUriList(List)}). If not
 * specified, then URI specified by {@link #DFLT_DEPLOY_DIR DFLT_DEPLOY_DIR} is used.
 * </li>
 * <li>
 * Flag to control encoding of the {@code 'path'} portion of URI
 * (see {@link #setEncodeUri(boolean) setEncodeUri(boolean)}).
 * </li>
 * </ul>
 * <h1 class="header">Protocols</h1>
 * Following protocols are supported in SPI:
 * <ul>
 * <li><a href="#file">file://</a> - File protocol</li>
 * <li><a href="#classes">classes://</a> - Custom File protocol.</li>
 * <li><a href="#ftp">ftp://</a> - File transfer protocol</li>
 * <li><a href="#mail">pop3://</a> - POP3 mail protocol</li>
 * <li><a href="#mail">pop3s://</a> - Secured POP3 mail protocol</li>
 * <li><a href="#mail">imap://</a> - IMAP mail protocol</li>
 * <li><a href="#mail">imaps://</a> - Secured IMAP mail protocol</li>
 * <li><a href="#http">http://</a> - HTTP protocol</li>
 * <li><a href="#http">https://</a> - Secure HTTP protocol</li>
 * </ul>
 * In addition to SPI configuration parameters, all necessary configuration
 * parameters for selected URI should be defined in URI. Different protocols
 * have different configuration parameters described below. Parameters are
 * separated by '{@code ;}' character.
 * <p>
 * <a name="file"></a>
 * <h1 class="header">File</h1>
 * For this protocol SPI will scan folder specified by URI on file system and
 * download any GAR files or directories that end with .gar from source
 * directory defined in URI. For file system URI must have scheme equal to {@code file}.
 * <p>
 * Following parameters are supported for FILE protocol:
 * <table class="doctable">
 *  <tr>
 *      <th>Parameter</th>
 *      <th>Description</th>
 *      <th>Optional</th>
 *      <th>Default</th>
 *  </tr>
 *  <tr>
 *      <td>freq</td>
 *      <td>File directory scan frequency in milliseconds.</td>
 *      <td>Yes</td>
 *      <td>{@code 5000} ms specified in {@link #DFLT_DISK_SCAN_FREQUENCY DFLT_DISK_SCAN_FREQUENCY}.</td>
 *  </tr>
 * </table>
 * <h2 class="header">File URI Example</h2>
 * The following example will scan {@code 'c:/Program files/gridgain/deployment'}
 * folder on local box every {@code '5000'} milliseconds. Note that since path
 * has spaces, {@link #setEncodeUri(boolean) setEncodeUri(boolean)} parameter must
 * be set to {@code true} (which is default behavior).
 * <blockquote class="snippet">
 * {@code file://freq=5000@localhost/c:/Program files/gridgain/deployment}
 * </blockquote>
 * <a name="classes"></a>
 * <h1 class="header">Classes</h1>
 * For this protocol SPI will scan folder specified by URI on file system
 * looking for compiled classes that implement {@link GridTask} interface.
 * This protocol comes very handy during development, as it allows developer
 * to specify IDE compilation output folder as URI and all task classes
 * in that folder will be deployed automatically.
 * <p>
 * Following parameters are supported for CLASSES protocol:
 * <table class="doctable">
 *  <tr>
 *      <th>Parameter</th>
 *      <th>Description</th>
 *      <th>Optional</th>
 *      <th>Default</th>
 *  </tr>
 *  <tr>
 *      <td>freq</td>
 *      <td>File directory scan frequency in milliseconds.</td>
 *      <td>Yes</td>
 *      <td>{@code 5000} ms specified in {@link #DFLT_DISK_SCAN_FREQUENCY DFLT_DISK_SCAN_FREQUENCY}.</td>
 *  </tr>
 * </table>
 * <h2 class="header">Classes URI Example</h2>
 * The following example will scan {@code 'c:/Program files/gridgain/deployment'}
 * folder on local box every {@code '5000'} milliseconds. Note that since path
 * has spaces, {@link #setEncodeUri(boolean) setEncodeUri(boolean)} parameter must
 * be set to {@code true} (which is default behavior).
 * <blockquote class="snippet">
 * {@code classes://freq=5000@localhost/c:/Program files/gridgain/deployment}
 * </blockquote>
 * <a name="ftp"></a>
 * <h1 class="header">FTP</h1>
 * For FTP protocol SPI will scan and download only GAR files from source
 * directory defined in URI. SPI doesn't scan FTP folders recursively.
 * The following parameters are supported for FTP protocol:
 * <table class="doctable">
 *  <tr>
 *      <th>Parameter</th>
 *      <th>Description</th>
 *      <th>Optional</th>
 *      <th>Default</th>
 *  </tr>
 *  <tr>
 *      <td>freq</td>
 *      <td>FTP location scan frequency in milliseconds.</td>
 *      <td>Yes</td>
 *      <td>{@code 300000} ms specified in {@link #DFLT_FTP_SCAN_FREQUENCY DFLT_FTP_SCAN_FREQUENCY}.</td>
 *  </tr>
 *  <tr>
 *      <td>username:password</td>
 *      <td>
 *          FTP username and password specified in standard URI server-based
 *          authority format.
 *      </td>
 *      <td>No</td>
 *      <td>---</td>
 *  </tr>
 * </table>
 * <h2 class="header">FTP URI Example</h2>
 * Here is an example of an FTP URI that connects identified as
 * {@code username:password} to {@code 'localhost'} on port {@code '21'},
 * with initial path set to {@code 'gridgain/deployment'}
 * <blockquote class="snippet">
 * ftp://username:password;freq=10000@localhost:21/gridgain/deployment
 * </blockquote>
 * <p>
 * <a name="mail"></a>
 * <h1 class="header">Mail</h1>
 * For Mail protocols this SPI scans mail inboxes for new mail messages looking
 * for GAR file attachments. Once a mail message with GAR file is found, it
 * will be deployed. Mail protocols works with following schemes: {@code pop3},
 * {@code pop3s}, {@code imap}, and {@code imaps}.
 * <p>
 * The following parameters are supported for Mail protocols:
 * <table class="doctable">
 *  <tr>
 *      <th>Parameter</th>
 *      <th>Description</th>
 *      <th>Optional</th>
 *      <th>Default</th>
 *  </tr>
 *  <tr>
 *      <td>freq</td>
 *      <td>Main inbox scan frequency in milliseconds.</td>
 *      <td>Yes</td>
 *      <td>{@code 300000} ms specified in {@link #DFLT_MAIL_SCAN_FREQUENCY DFLT_MAIL_SCAN_FREQUENCY}.</td>
 *  </tr>
 *  <tr>
 *      <td>username:password</td>
 *      <td>
 *          Mail username and password specified in standard URI server-based
 *          authority format.
 *      </td>
 *      <td>No</td>
 *      <td>---</td>
 *  </tr>
 *  <tr>
 *      <td>auth</td>
 *      <td>
 *          Connection type. Can be one of the following:
 *          <ul>
 *          <li>none</li>
 *          <li>ssl</li>
 *          <li>starttls</li>
 *          </ul>
 *      </td>
 *      <td>Yes</td>
 *      <td>{@code none}</td>
 *  </tr>
 *  <tr>
 *      <td>subj</td>
 *      <td>
 *          Subject filter for mail messages used by SPI. All messages with
 *          different subjects will be ignored.
 *      </td>
 *      <td>Yes</td>
 *      <td>
 *          {@code 'grid.email.deploy.msg'} specified in
 *          {@link #DFLT_MAIL_SUBJECT DFLT_MAIL_SUBJECT}
 *      </td>
 *  </tr>
 * </table>
 * <h2 class="header">Mail URI Example</h2>
 * The following example demonstrates Mail URI that will connect user
 * identified as {@code username:password} with authorization set to
 * {@code 'none'} to host {@code 'pop.gmail.com'} on port {@code '110'}
 * scanning inbox every {@code '120000'} milliseconds (2 minutes).
 * <blockquote class="snippet">
 * {@code pop3://username:password;auth=ssl;freq=120000@pop.gmail.com:995}
 * </blockquote>
 * <p>
 * <a name="http"></a>
 * <h1 class="header">HTTP</h1>
 * For HTTP protocols this SPI scans and downloads GAR files from source
 * directory defined in URI. SPI does not scan HTTP folders recursively. Only
 * HTTP links that end with {@code '.gar'} extension will be downloaded.
 * HTTP protocol works with scheme {@code http} and {@code https}.
 * <p>
 * The following parameters are supported for HTTP protocols:
 * <table class="doctable">
 *  <tr>
 *      <th>Parameter</th>
 *      <th>Description</th>
 *      <th>Optional</th>
 *      <th>Default</th>
 *  </tr>
 *  <tr>
 *      <td>freq</td>
 *      <td>HTTP directory scan frequency in milliseconds.</td>
 *      <td>Yes</td>
 *      <td>{@code 300000} ms specified in {@link #DFLT_HTTP_SCAN_FREQUENCY DFLT_HTTP_SCAN_FREQUENCY}.</td>
 *  </tr>
 *  <tr>
 *      <td>username:password</td>
 *      <td>
 *          Optional HTTP directory username and password specified in standard
 *          URI server-based authority format.
 *      </td>
 *      <td>Yes</td>
 *      <td>---</td>
 *  </tr>
 * </table>
 * <h2 class="header">HTTP URI Example</h2>
 * The following example will scan {@code 'gridgain/deployment'} folder with
 * on site {@code 'www.mysite.com'} using authentication
 * {@code 'username:password'} every {@code '10000'} milliseconds.
 * <blockquote class="snippet">
 * {@code http://username:password;freq=10000@www.mysite.com:110/gridgain/deployment}
 * </blockquote>
 * <h2 class="header">Java Example</h2>
 * GridUriDeploymentSpi needs to be explicitly configured to override default local deployment SPI.
 * <pre name="code" class="java">
 * GridUriDeploymentSpi deploySpi = new GridUriDeploymentSpi();
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * List&lt;String&gt; uris = new ArrayList&lt;String&gt;(5);
 *
 * uris.add("http://www.site.com/tasks");
 * uris.add("ftp://ftpuser:password;freq=10000@localhost:21/gg-test/deployment");
 * uris.add("file://freq=20000@localhost/c:/Program files/gg-deployment");
 * uris.add("pop3://test%20user:test%20password;subj=grid.deploy.subj;auth=none@pop.mail.ru:110");
 * uris.add("classes:///c:/Java_Projects/myproject/out");
 *
 * // Set URIs.
 * deploySpi.setUriList(uris);
 *
 * // Override temporary directory path.
 * deploySpi.setTemporaryDirectoryPath("c:/tmp/grid");
 *
 * //  Override default deployment SPI.
 * cfg.setDeploymentSpi(deploySpi);
 *
 * //  Start grid.
 * G.start(cfg);
 * </pre>
 * <p>
 * <h2 class="header">Spring Example</h2>
 * GridUriDeploymentSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="deploymentSpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.deployment.uri.GridUriDeploymentSpi"&gt;
 *                 &lt;property name="temporaryDirectoryPath" value="c:/tmp/grid"/&gt;
 *                 &lt;property name="uriList"&gt;
 *                     &lt;list&gt;
 *                         &lt;value&gt;http://www.site.com/tasks&lt;/value&gt;
 *                         &lt;value&gt;ftp://ftpuser:password;freq=10000@localhost:21/gg-test/deployment&lt;/value&gt;
 *                         &lt;value&gt;file://freq=20000@localhost/c:/Program files/gg-deployment&lt;/value&gt;
 *                         &lt;value&gt;pop3://test%20user:test%20password;subj=grid.deploy.subj;auth=none@pop.mail.ru:110&lt;/value&gt;
 *                         &lt;value&gt;classes:///c:/Java_Projects/myproject/out&lt;/value&gt;
 *                     &lt;/list&gt;
 *                 &lt;/property&gt;
 *             &lt;/bean&gt;
 *         &lt;/property&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridDeploymentSpi
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
@SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
public class GridUriDeploymentSpi extends GridSpiAdapter implements GridDeploymentSpi, GridUriDeploymentSpiMBean {
    /**
     * Default deployment directory where SPI will pick up GAR files (value is {@code work/deployment/file}).
     * Note that this path relative to {@code GRIDGAIN_HOME} folder.
     */
    public static final String DFLT_DEPLOY_DIR = "work/deployment/file";

    /** Default scan frequency for {@code file://} and {@code classes://} protocols (value is {@code 5000}). */
    public static final int DFLT_DISK_SCAN_FREQUENCY = 5000;

    /** Default scan frequency for {@code ftp://} protocol (value is {@code 300000}). */
    public static final int DFLT_FTP_SCAN_FREQUENCY = 300000;

    /** Default scan frequency for {@code http://} protocol (value is {@code 300000}). */
    public static final int DFLT_HTTP_SCAN_FREQUENCY = 300000;

    /**
     * Default scan frequency for {@code pop3://}, {@code pop3s://}, {@code imap://},
     * {@code imaps://} protocols (value is {@code 300000} which is 5 minutes).
     */
    public static final int DFLT_MAIL_SCAN_FREQUENCY = 300000;

    /** Default Mail subject. */
    public static final String DFLT_MAIL_SUBJECT = "grid.email.deploy.msg";

    /** Default task description file path and name (value is {@code META-INF/gridgain.xml}). */
    public static final String XML_DESCRIPTOR_PATH = "META-INF/gridgain.xml";

    /**
     * Default temporary directory name relative to file path
     * {@link #setTemporaryDirectoryPath(String)}} (value is {@code gg.uri.deployment.tmp}).
     */
    public static final String DEPLOY_TMP_ROOT_NAME = "gg.uri.deployment.tmp";

    /** Temporary directory name. */
    private String tmpDirPath;

    /** Sub-folder of 'tmpDirPath'. */
    private String deployTmpDirPath;

    /** List of URIs to be scanned. */
    private List<String> uriList = new ArrayList<String>();

    /** List of encoded URIs. */
    private Collection<URI> uriEncodedList = new ArrayList<URI>();

    /** */
    @SuppressWarnings({"CollectionDeclaredAsConcreteClass"})
    private final LinkedList<GridUriDeploymentUnitDescriptor> unitLoaders =
        new LinkedList<GridUriDeploymentUnitDescriptor>();

    /** */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private final LastTimeUnitDescriptorComparator unitComparator = new LastTimeUnitDescriptorComparator();

    /** List of scanners. Every URI has it's own scanner. */
    private final Collection<GridUriDeploymentScanner> scanners = new ArrayList<GridUriDeploymentScanner>();

    /** Whether URIs should be encoded or not. */
    private boolean encodeUri = true;

    /** Whether first scan cycle is completed or not. */
    private int firstScanCntr;

    /** Deployment listener which processes all notifications from scanners. */
    private volatile GridDeploymentListener lsnr;

    /** */
    private final Object mux = new Object();

    /** */
    @GridLoggerResource
    private GridLogger log;

    /** */
    @GridMarshallerResource
    private GridMarshaller marshaller;

    /**
     * Sets absolute path to temporary directory which will be used by
     * deployment SPI to keep all deployed classes in.
     * <p>
     * If not provided, default value is {@code java.io.tmpdir} system property value.
     *
     * @param tmpDirPath Temporary directory path.
     */
    @GridSpiConfiguration(optional = true)
    public void setTemporaryDirectoryPath(String tmpDirPath) {
        this.tmpDirPath = tmpDirPath;
    }

    /**
     * Sets list of URI which point to GAR file and which should be
     * scanned by SPI for the new tasks.
     * <p>
     * If not provided, default value is list with
     * {@code file://${GRIDGAIN_HOME}/work/deployment/file} element.
     * Note that system property {@code GRIDGAIN_HOME} must be set.
     * For unknown {@code GRIDGAIN_HOME} list of URI must be provided explicitly.
     *
     * @param uriList GAR file URIs.
     */
    @GridSpiConfiguration(optional = true)
    public void setUriList(List<String> uriList) {
        this.uriList = uriList;
    }

    /**
     * Indicates that URI must be encoded before usage. Encoding means replacing
     * all occurrences of space with '%20', percent sign with '%25'
     * and semicolon with '%3B'.
     * <p>
     * If not provided, default value is {@code true}.
     *
     * @param encodeUri {@code true} if every URI should be encoded and
     *      {@code false} otherwise.
     */
    @GridSpiConfiguration(optional = true)
    public void setEncodeUri(boolean encodeUri) {
        this.encodeUri = encodeUri;
    }

    /** {@inheritDoc} */
    @Override public String getTemporaryDirectoryPath() {
        return tmpDirPath;
    }

    /** {@inheritDoc} */
    @Override public List<String> getUriList() {
        return Collections.unmodifiableList(uriList);
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridDeploymentListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        for (GridUriDeploymentScanner scanner : scanners)
            scanner.cancel();

        for (GridUriDeploymentScanner scanner : scanners)
            scanner.join();

        // Clear inner collections.
        uriEncodedList.clear();
        scanners.clear();

        List<ClassLoader> tmpClsLdrs;

        // Release all class loaders.
        synchronized (mux) {
            tmpClsLdrs = new ArrayList<ClassLoader>(unitLoaders.size());

            for (GridUriDeploymentUnitDescriptor desc : unitLoaders)
                tmpClsLdrs.add(desc.getClassLoader());
        }

        for (ClassLoader ldr : tmpClsLdrs)
            onUnitReleased(ldr);

        // Delete temp directory.
        if (deployTmpDirPath != null)
            U.delete(new File(deployTmpDirPath));

        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(uriList != null, "uriList != null");

        initializeUriList();

        if (uriEncodedList.isEmpty())
            addDefaultUri();

        initializeTemporaryDirectoryPath();

        registerMBean(gridName, this, GridUriDeploymentSpiMBean.class);

        FilenameFilter filter = new FilenameFilter() {
            @Override public boolean accept(File dir, String name) {
                assert name != null;

                return name.toLowerCase().endsWith(".gar");
            }
        };

        firstScanCntr = 0;

        GridUriDeploymentScannerListener lsnr = new GridUriDeploymentScannerListener() {
            @Override public void onNewOrUpdatedFile(File file, String uri, long tstamp) {
                if (log.isInfoEnabled())
                    log.info("Found new or updated GAR units [uri=" + U.hidePassword(uri) +
                        ", file=" + file.getAbsolutePath() + ", tstamp=" + tstamp + ']');

                try {
                    GridUriDeploymentFileProcessorResult fileRes = GridUriDeploymentFileProcessor.processFile(file, uri,
                        new File(deployTmpDirPath), log);

                    if (fileRes != null)
                        newUnitReceived(uri, fileRes.getFile(), tstamp, fileRes.getClassLoader(),
                            fileRes.getTaskClasses());
                }
                catch (GridSpiException e) {
                    U.error(log, "Error when processing file: " + file.getAbsolutePath(), e);
                }
            }

            /** {@inheritDoc} */
            @Override public void onDeletedFiles(List<String> uris) {
                if (log.isInfoEnabled()) {
                    List<String> uriList = null;

                    if (uris != null) {
                        uriList = new ArrayList<String>();

                        for (String uri : uris)
                            uriList.add(U.hidePassword(uri));
                    }

                    log.info("Found deleted GAR units [uris=" + uriList + ']');
                }

                processDeletedFiles(uris);
            }

            /** {@inheritDoc} */
            @Override public void onFirstScanFinished() {
                synchronized (mux) {
                    firstScanCntr++;

                    if (isFirstScanFinished(firstScanCntr))
                        mux.notifyAll();
                }
            }
        };

        for (URI uri : uriEncodedList) {
            String proto = uri.getScheme();

            File file = new File(deployTmpDirPath);

            long freq = -1;

            try {
                freq = getFrequencyFromUri(uri);
            }
            catch (NumberFormatException e) {
                U.error(log, "Error parsing parameter value for frequency.", e);
            }

            assert proto != null;

            GridUriDeploymentScanner scanner;

            if ("file".equals(proto)) {
                scanner = new GridUriDeploymentFileScanner(gridName, uri, file, freq > 0 ? freq :
                    DFLT_DISK_SCAN_FREQUENCY, filter, lsnr, log);
            }
            else if ("http".equals(proto) || "https".equals(proto)) {
                scanner = new GridUriDeploymentHttpScanner(gridName, uri, file, freq > 0 ? freq :
                    DFLT_HTTP_SCAN_FREQUENCY, filter, lsnr, log);
            }
            else if ("pop3".equals(proto) || "pop3s".equals(proto) ||
                "imap".equals(proto) || "imaps".equals(proto)) {
                scanner = new GridUriDeploymentMailScanner(gridName, uri, file, freq > 0 ? freq :
                    DFLT_MAIL_SCAN_FREQUENCY, filter, lsnr, log, marshaller);
            }
            else if ("ftp".equals(proto)) {
                scanner = new GridUriDeploymentFtpScanner(gridName, uri, file, freq > 0 ? freq :
                    DFLT_FTP_SCAN_FREQUENCY, filter, lsnr, log);
            }
            else {
                throw new GridSpiException("Unsupported protocol: " + proto);
            }

            scanners.add(scanner);

            scanner.start();
        }

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("tmpDirPath", tmpDirPath));
            log.debug(configInfo("uriList", uriList));
            log.debug(configInfo("encodeUri", encodeUri));
            log.debug(configInfo("scanners", scanners));
        }

        // Ack ok start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * Gets URI refresh frequency.
     * URI is parsed and {@code freq} parameter value returned.
     *
     * @param uri URI to be parsed.
     * @return {@code -1} if there if no {@code freq} parameter otherwise
     *      returns frequency.
     * @throws NumberFormatException Thrown if {@code freq} parameter value
     *      is not a number.
     */
    private long getFrequencyFromUri(URI uri) throws NumberFormatException {
        assert uri != null;

        String userInfo = uri.getUserInfo();

        if (userInfo != null) {
            String[] arr = userInfo.split(";");

            if (arr != null && arr.length > 0)
                for (String el : arr)
                    if (el.startsWith("freq="))
                        return Long.parseLong(el.substring(5));
        }

        return -1;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public GridDeploymentResource findResource(String rsrcName) {
        assert rsrcName != null;

        // Wait until all scanners finish their first scanning.
        try {
            synchronized (mux) {
                while (!isFirstScanFinished(firstScanCntr))
                    mux.wait(5000);
            }
        }
        catch (InterruptedException e) {
            U.error(log, "Failed to wait while all scanners finish their first scanning.", e);

            return null;
        }

        synchronized (mux) {
            // Last updated class loader has highest priority in search.
            for (GridUriDeploymentUnitDescriptor unitDesc : unitLoaders) {
                Map<String, String> rsrcs = unitDesc.getResources();

                // Try to find resource for current class loader.
                String clsName = rsrcName;

                if (rsrcs.containsKey(rsrcName))
                    clsName = rsrcs.get(rsrcName);

                try {
                    ClassLoader ldr = unitDesc.getClassLoader();

                    Class<?> cls = ldr instanceof GridUriDeploymentClassLoader ?
                        ((GridUriDeploymentClassLoader)ldr).loadClassGarOnly(clsName) :
                        ldr.loadClass(clsName);

                    assert cls != null;

                    if (rsrcs.containsKey(rsrcName)) {
                        // Recalculate resource name in case if access is performed by
                        // class name and not the resource name.
                        String alias = getResourceName(clsName, rsrcs);

                        return new GridDeploymentResourceAdapter(alias, cls, unitDesc.getClassLoader());
                    }
                    // Ignore invalid tasks.
                    else if (!GridTask.class.isAssignableFrom(cls)) {
                        // Add resource in map.
                        rsrcs.put(rsrcName, clsName);

                        return new GridDeploymentResourceAdapter(rsrcName, cls, unitDesc.getClassLoader());
                    }
                }
                catch (ClassNotFoundException ignored) {
                    // No-op.
                }
            }

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean register(ClassLoader ldr, Class<?> rsrc) throws GridSpiException {
        A.notNull(ldr, "ldr");
        A.notNull(rsrc, "rsrc");

        long tstamp = System.currentTimeMillis();

        Collection<ClassLoader> removedClsLdrs = new ArrayList<ClassLoader>();

        Map<String, String> newRsrcs;

        synchronized (mux) {
            GridUriDeploymentUnitDescriptor desc = null;

            // Find existing class loader.
            for (GridUriDeploymentUnitDescriptor unitDesc : unitLoaders)
                if (unitDesc.getClassLoader().equals(ldr)) {
                    desc = unitDesc;

                    break;
                }

            if (desc == null) {
                desc = new GridUriDeploymentUnitDescriptor(tstamp, ldr);

                // New unit has largest timestamp.
                assert unitLoaders.size() <= 0 || unitComparator.compare(desc, unitLoaders.getFirst()) <= 0;

                unitLoaders.addFirst(desc);
            }

            newRsrcs = addResources(ldr, desc.getResources(), new Class<?>[]{rsrc});

            if (!F.isEmpty(newRsrcs))
                removeResources(ldr, newRsrcs, removedClsLdrs);
        }

        for (ClassLoader cldLdr : removedClsLdrs)
            onUnitReleased(cldLdr);

        return !F.isEmpty(newRsrcs);
    }

    /** {@inheritDoc} */
    @Override public boolean unregister(String rsrcName) {
        assert rsrcName != null;

        Collection<ClassLoader> removedClsLdrs = new ArrayList<ClassLoader>();

        boolean removed;

        synchronized (mux) {
            Map<String, String> rsrcs = new HashMap<String, String>(1);

            rsrcs.put(rsrcName, rsrcName);

            removed = removeResources(null, rsrcs, removedClsLdrs);
        }

        for (ClassLoader cldLdr : removedClsLdrs)
            onUnitReleased(cldLdr);

        return removed;
    }

    /**
     * Add new classes in class loader resource map.
     * Note that resource map may contain two entries for one added class:
     * task name -> class name and class name -> class name.
     *
     * @param ldr Registered class loader.
     * @param ldrRsrcs Class loader resources.
     * @param clss Registered classes array.
     * @return Map of new resources added for registered class loader.
     * @throws GridSpiException If resource already registered. Exception thrown
     * if registered resources conflicts with rule when all task classes must be
     * annotated with different task names.
     */
    @Nullable
    private Map<String, String> addResources(ClassLoader ldr, Map<String, String> ldrRsrcs, Class<?>[] clss)
        throws GridSpiException {
        assert ldr != null;
        assert ldrRsrcs != null;
        assert clss != null;

        // Maps resources to classes.
        // Map may contain 2 entries for one class.
        Map<String, String> regRsrcs = new HashMap<String, String>(clss.length * 2, 1.0f);

        // Check alias collision between added classes.
        for (Class<?> cls : clss) {
            String alias = null;

            if (GridTask.class.isAssignableFrom(cls)) {
                GridTaskName nameAnn = U.getAnnotation(cls, GridTaskName.class);

                if (nameAnn != null)
                    alias = nameAnn.value();
            }

            // If added classes maps to one alias.
            if (alias != null && regRsrcs.containsKey(alias) &&
                !regRsrcs.get(alias).equals(cls.getName())) {
                throw new GridSpiException("Failed to register resources with given task name " +
                    "(found another class with same task name) [taskName=" + alias +
                    ", cls1=" + cls.getName() + ", cls2=" + regRsrcs.get(alias) + ", ldr=" + ldr + ']');
            }

            if (alias != null)
                regRsrcs.put(alias, cls.getName());

            regRsrcs.put(cls.getName(), cls.getName());
        }

        Map<String, String> newRsrcs = null;

        // Check collisions between added and exist classes.
        for (Entry<String, String> entry : regRsrcs.entrySet()) {
            String newAlias = entry.getKey();
            String newName = entry.getValue();

            if (ldrRsrcs.containsKey(newAlias)) {
                String existingCls = ldrRsrcs.get(newAlias);

                // Different classes for the same resource name.
                if (!ldrRsrcs.get(newAlias).equals(newName))
                    throw new GridSpiException("Failed to register resources with given task name " +
                        "(found another class with same task name in the same class loader) [taskName=" + newAlias +
                        ", existingCls=" + existingCls + ", newCls=" + newName + ", ldr=" + ldr + ']');
            }
            // Add resources that should be removed for another class loaders.
            else {
                if (newRsrcs == null)
                    newRsrcs = new HashMap<String, String>(regRsrcs.size());

                newRsrcs.put(newAlias, newName);
            }
        }

        // New resources to register. Add it all.
        if (newRsrcs != null)
            ldrRsrcs.putAll(newRsrcs);

        return newRsrcs;
    }

    /**
     * Remove resources for all class loaders except {@code ignoreClsLdr}.
     *
     * @param ignoreClsLdr Ignored class loader or {@code null} to remove for all class loaders.
     * @param rsrcs Resources that should be used in search for class loader to remove.
     * @param removedClsLdrs Class loaders to remove.
     * @return {@code True} if resource was removed.
     */
    private boolean removeResources(ClassLoader ignoreClsLdr, Map<String, String> rsrcs,
        Collection<ClassLoader> removedClsLdrs) {
        assert Thread.holdsLock(mux);
        assert rsrcs != null;

        boolean res = false;

        for (Iterator<GridUriDeploymentUnitDescriptor> iter = unitLoaders.iterator(); iter.hasNext();) {
            GridUriDeploymentUnitDescriptor desc = iter.next();
            ClassLoader ldr = desc.getClassLoader();

            if (ignoreClsLdr == null || !ldr.equals(ignoreClsLdr)) {
                Map<String, String> clsLdrRsrcs = desc.getResources();

                assert clsLdrRsrcs != null;

                boolean isRemoved = false;

                // Check class loader's registered resources.
                for (String rsrcName : rsrcs.keySet()) {
                    // Remove classloader if resource found.
                    if (clsLdrRsrcs.containsKey(rsrcName)) {
                        iter.remove();

                        // Add class loaders in collection to notify listener outside synchronization block.
                        removedClsLdrs.add(ldr);

                        isRemoved = true;
                        res = true;

                        break;
                    }
                }

                if (isRemoved)
                    continue;

                // Check is possible to load resources with classloader.
                for (Entry<String, String> entry : rsrcs.entrySet()) {
                    // Check classes with class loader only when classes points to classes to avoid redundant check.
                    // Resources map contains two entries for class with task name(alias).
                    if (entry.getKey().equals(entry.getValue()) && isResourceExist(ldr, entry.getKey())) {
                        iter.remove();

                        // Add class loaders in collection to notify listener outside synchronization block.
                        removedClsLdrs.add(ldr);

                        res = true;

                        break;
                    }
                }
            }
        }

        return res;
    }

    /**
     * Gets resource name for a given class name.
     *
     * @param clsName Class name.
     * @param rsrcs Map of resources.
     * @return Resource name.
     */
    private String getResourceName(String clsName, Map<String, String> rsrcs) {
        assert Thread.holdsLock(mux);

        String rsrcName = clsName;

        for (Entry<String, String> e : rsrcs.entrySet())
            if (e.getValue().equals(clsName) && !e.getKey().equals(clsName)) {
                rsrcName = e.getKey();

                break;
            }

        return rsrcName;
    }

    /**
     * Check is class can be reached.
     *
     * @param ldr Class loader.
     * @param clsName Class name.
     * @return {@code true} if class can be loaded.
     */
    private boolean isResourceExist(ClassLoader ldr, String clsName) {
        String rsrc = clsName.replaceAll("\\.", "/") + ".class";

        InputStream in = null;

        try {
            in = ldr instanceof GridUriDeploymentClassLoader ?
                ((GridUriDeploymentClassLoader)ldr).getResourceAsStreamGarOnly(rsrc) :
                ldr.getResourceAsStream(rsrc);

            return in != null;
        }
        finally {
            U.close(in, log);
        }
    }

    /**
     * Tests whether first scan is finished or not.
     *
     * @param cntr Number of already scanned URIs.
     * @return {@code true} if all URIs have been scanned at least once and
     *      {@code false} otherwise.
     */
    private boolean isFirstScanFinished(int cntr) {
        assert uriEncodedList != null;

        return cntr >= uriEncodedList.size();
    }

    /**
     * Fills in list of URIs with all available URIs and encodes them if
     * encoding is enabled.
     *
     * @throws GridSpiException Thrown if at least one URI has incorrect syntax.
     */
    private void initializeUriList() throws GridSpiException {
        for (String uri : uriList) {
            assertParameter(uri != null, "uriList.get(X) != null");

            assert uri != null;

            String encUri = encodeUri(uri.replaceAll("\\\\", "/"));

            URI uriObj;

            try {
                uriObj = new URI(encUri);
            }
            catch (URISyntaxException e) {
                throw new GridSpiException("Failed to parse URI [uri=" + U.hidePassword(uri) +
                    ", encodedUri=" + U.hidePassword(encUri) + ']', e);
            }

            if (uriObj.getScheme() == null || uriObj.getScheme().trim().length() == 0)
                throw new GridSpiException("Failed to get 'scheme' from URI [uri=" +
                    U.hidePassword(uri) +
                    ", encodedUri=" + U.hidePassword(encUri) + ']');

            uriEncodedList.add(uriObj);
        }
    }

    /**
     * Add configuration for file scanner {@link GridUriDeploymentFileScanner}.
     *
     * @throws GridSpiException Thrown if default URI syntax is incorrect.
     */
    private void addDefaultUri() throws GridSpiException {
        assert uriEncodedList != null;

        URI uri;

        // GridGain home found.
        if (getGridGainHome() != null && getGridGainHome().length() > 0) {
            File dir = new File(getGridGainHome(), DFLT_DEPLOY_DIR);

            if (!dir.exists())
                throw new GridSpiException("Failed to initialize default file scanner where URI points to folder " +
                    "relative to GRIDGAIN_HOME (folder doesn't exist): " + dir);

            uri = dir.toURI();
        }
        // Unknown GridGain home.
        else
            throw new GridSpiException("Failed to initialize default file scanner with unknown GRIDGAIN_HOME. " +
                "Provide URI's explicitly for SPI or set system property GRIDGAIN_HOME.");

        uriEncodedList.add(uri);
    }

    /**
     * Encode URI path if encoding is enabled. Set of encoded characters
     * in path is (' ', ';', '%').
     *
     * @param path URI which should be encoded.
     * @return Either encoded URI if encoding is enabled or given one
     *      if encoding is disabled.
     */
    private String encodeUri(String path) {
        return encodeUri ? new GridUriDeploymentUriParser(path).parse() : path;
    }

    /**
     * Initializes temporary directory path. Path consists of base path
     * (either {@link #tmpDirPath} value or {@code java.io.tmpdir}
     * system property value if first is {@code null}) and path relative
     * to base one - {@link #DEPLOY_TMP_ROOT_NAME}.
     *
     * @throws GridSpiException Thrown if temporary directory could not be created.
     */
    private void initializeTemporaryDirectoryPath() throws GridSpiException {
        String tmpDirPath = this.tmpDirPath == null ? System.getProperty("java.io.tmpdir") : this.tmpDirPath;

        if (tmpDirPath == null)
            throw new GridSpiException("Error initializing temporary deployment directory.");

        File dir = new File(tmpDirPath + File.separator + DEPLOY_TMP_ROOT_NAME);

        if (!dir.mkdirs() && !dir.exists())
            throw new GridSpiException("Error initializing temporary deployment directory: " + dir);

        this.tmpDirPath = tmpDirPath;

        deployTmpDirPath = dir.getPath();
    }

    /**
     * Deploys all tasks that correspond to given descriptor.
     * First method checks tasks versions and stops processing tasks that
     * have both versioned and unversioned instances.
     * <p>
     * Than it deletes tasks with lower version and deploys newest tasks.
     *
     * @param newDesc Tasks deployment descriptor.
     * @param clss Registered classes.
     */
    private void newUnitReceived(GridUriDeploymentUnitDescriptor newDesc, Collection<Class<?>> clss) {
        assert newDesc != null;
        assert newDesc.getType() == GridUriDeploymentUnitDescriptor.Type.FILE;

        if (clss != null && !clss.isEmpty()) {
            try {
                addResources(newDesc.getClassLoader(), newDesc.getResources(),
                    clss.toArray(new Class<?>[clss.size()]));
            }
            catch (GridSpiException e) {
                U.warn(log, "Failed to register GAR class loader [newDesc=" + newDesc +
                    ", msg=" + e.getMessage() + ']');
            }
        }

        Collection<ClassLoader> removedClsLdrs = new ArrayList<ClassLoader>();

        synchronized (mux) {
            boolean isAdded = false;
            boolean ignoreNewUnit = false;

            for (ListIterator<GridUriDeploymentUnitDescriptor> iter = unitLoaders.listIterator();
                 iter.hasNext();) {
                GridUriDeploymentUnitDescriptor desc = iter.next();

                assert !newDesc.getClassLoader().equals(desc.getClassLoader()) :
                    "Uri scanners always create new class loader for every GAR file: " + newDesc;

                // Only for GAR files. Undeploy all for overwritten GAR files.
                if (desc.getType() == GridUriDeploymentUnitDescriptor.Type.FILE &&
                    newDesc.getUri().equals(desc.getUri()) && !newDesc.getFile().equals(desc.getFile())) {
                    // Remove descriptor.
                    iter.remove();

                    // Add class loaders in collection to notify listener outside synchronization block.
                    removedClsLdrs.add(desc.getClassLoader());

                    // Last descriptor.
                    if (!iter.hasNext())
                        // New descriptor will be added after loop.
                        break;

                    continue;
                }

                if (!isAdded) {
                    // Unit with largest timestamp win.
                    // Insert it before current element.
                    if (unitComparator.compare(newDesc, desc) <= 0) {
                        // Remove current class loader if found collisions.
                        if (checkUnitCollision(desc, newDesc)) {
                            iter.remove();
                            iter.add(newDesc);

                            // Add class loaders in collection to notify listener outside synchronization block.
                            removedClsLdrs.add(desc.getClassLoader());
                        }
                        // Or add new class loader before current class loader.
                        else {
                            iter.set(newDesc);
                            iter.add(desc);
                        }

                        isAdded = true;
                    }
                    else if (checkUnitCollision(newDesc, desc)) {
                        // Don't add new unit if found collisions with latest class loader.
                        ignoreNewUnit = true;
                        break;
                    }
                }
                // New descriptor already added and we need to check other class loaders for collisions.
                else if (checkUnitCollision(newDesc, desc)) {
                    iter.remove();

                    // Add class loaders in collection to notify listener outside synchronization block.
                    removedClsLdrs.add(desc.getClassLoader());
                }
            }

            if (!ignoreNewUnit) {
                if (!isAdded)
                    unitLoaders.add(newDesc);

                if (log.isInfoEnabled())
                    log.info("Class loader (re)registered [clsLdr=" + newDesc.getClassLoader() +
                        ", tstamp=" + newDesc.getTimestamp() +
                        ", uri='" + U.hidePassword(newDesc.getUri()) +
                        "', file=" + (newDesc.getFile() == null ? "N/A" : newDesc.getFile()) + ']');
            }
        }

        for (ClassLoader cldLdr : removedClsLdrs)
            onUnitReleased(cldLdr);
    }

    /**
     * Check collisions in added descriptor {@code newDesc} with another descriptor {@code existDesc}.
     *
     * @param newDesc New added descriptor.
     * @param existDesc Exist descriptor.
     * @return {@code True} if collisions found.
     */
    private boolean checkUnitCollision(GridUriDeploymentUnitDescriptor newDesc,
        GridUriDeploymentUnitDescriptor existDesc) {
        assert newDesc != null;
        assert existDesc != null;

        Map<String, String> rsrcs = newDesc.getResources();

        for (Entry<String, String> entry : existDesc.getResources().entrySet()) {
            String rsrcName = entry.getKey();
            String rsrcCls = entry.getValue();

            if (rsrcs.containsKey(rsrcName)) {
                U.warn(log, "Found collision with resource name in different GAR files. " +
                    "Class loader will be removed [rsrcName=" + rsrcName + ", cls1=" + rsrcs.get(rsrcName) +
                    ", cls2=" + rsrcCls + ", newDesc=" + newDesc + ", existDesc=" + existDesc + ']');

                return true;
            }
            else if (isResourceExist(newDesc.getClassLoader(), rsrcCls)) {
                U.warn(log, "Found collision with resource class in different GAR files. " +
                    "Class loader will be removed [rsrcName=" + rsrcName + ", rsrcCls=" + rsrcCls +
                    ", removedDesc=" + newDesc + ", existDesc=" + existDesc + ']');

                return true;
            }
        }

        return false;
    }

    /**
     * Deploys or redeploys given tasks.
     *
     * @param uri GAR file deployment URI.
     * @param file GAR file.
     * @param tstamp File modification date.
     * @param ldr Class loader.
     * @param clss List of tasks which were found in GAR file.
     */
    private void newUnitReceived(String uri, File file, long tstamp, ClassLoader ldr,
        Collection<Class<? extends GridTask<?, ?>>> clss) {
        assert uri != null;
        assert file != null;
        assert tstamp > 0;

        // To avoid units with incorrect timestamp.
        tstamp = Math.min(tstamp, System.currentTimeMillis());

        // Create descriptor.
        GridUriDeploymentUnitDescriptor desc = new GridUriDeploymentUnitDescriptor(uri, file, tstamp, ldr);

        newUnitReceived(desc, clss != null && !clss.isEmpty() ? new ArrayList<Class<?>>(clss) : null);
    }

    /**
     * Removes all tasks that belong to GAR files which are on list
     * of removed files.
     *
     * @param uris List of removed files.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private void processDeletedFiles(List<String> uris) {
        assert uris != null;

        if (uris.isEmpty())
            return;

        synchronized (mux) {
            Set<String> uriSet = new HashSet<String>(uris);

            for (Iterator<GridUriDeploymentUnitDescriptor> iter = unitLoaders.iterator(); iter.hasNext();) {
                GridUriDeploymentUnitDescriptor desc = iter.next();

                if (desc.getType() == GridUriDeploymentUnitDescriptor.Type.FILE && uriSet.contains(desc.getUri())) {
                    // Remove descriptor.
                    iter.remove();

                    onUnitReleased(desc.getClassLoader());
                }
            }
        }
    }

    /**
     * Notifies listener about released class loader.
     *
     * @param clsLdr Released class loader.
     */
    private void onUnitReleased(ClassLoader clsLdr) {
        // Make sure we don't remove system class loader.
        if (!clsLdr.equals(getClass().getClassLoader()))
            GridUriDeploymentFileProcessor.cleanupUnit(clsLdr, log);

        GridDeploymentListener tmp = lsnr;

        if (tmp != null)
            tmp.onUnregistered(clsLdr);
    }

    /**
     * Task deployment descriptor comparator.
     * The greater descriptor is those one that has less timestamp.
     */
    private static class LastTimeUnitDescriptorComparator implements Comparator<GridUriDeploymentUnitDescriptor>,
        Serializable {
        /** {@inheritDoc} */
        @Override public int compare(GridUriDeploymentUnitDescriptor o1, GridUriDeploymentUnitDescriptor o2) {
            if (o1.getTimestamp() < o2.getTimestamp())
                return 1;

            return o1.getTimestamp() == o2.getTimestamp() ? 0 : -1;
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUriDeploymentSpi.class, this);
    }
}
