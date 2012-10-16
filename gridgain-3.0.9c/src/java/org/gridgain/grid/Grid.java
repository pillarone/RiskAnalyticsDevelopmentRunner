// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.deployment.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.loadbalancing.*;
import org.gridgain.grid.spi.topology.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * <img id="callout_img" src="{@docRoot}/img/callout_blue.gif"><span id="callout_blue">Start Here</span>&nbsp;
 * Main <b>Computational Grid</b> APIs.
 * <p>
 * You can obtain an instance of {@code Grid} through {@link GridFactory#grid()},
 * or for named grids you can use {@link GridFactory#grid(String)}. Note that you
 * can have multiple instances of {@code Grid} running in the same VM. For
 * information on how to start or stop Grid please refer to {@link GridFactory} class.
 * <p>
 * {@code Grid} interface allows you to perform all the main operations on the grid. Note also that
 * this interface extends {@link GridProjection} and defined as a global monad over all
 * nodes in the grid, i.e. set of all nodes across all clouds and the nodes outside of any clouds.
 * <p>
 * Following short video provides quick overview of basic Compute Grid capabilities:
 * <p>
 * <a href="http://www.youtube.com/user/gridgain?hd=1#p/a/u/0/MXIUc6Tm5uU" target="youtube"><img src="http://gridgain.com/images/video_mapreduce_thumb.png"></a>
 * <p>
 * For more information see
 * <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Grid+Interface">Grid Interface</a>
 * on Wiki.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface Grid extends GridProjection {
    /**
     * Gets enterprise license descriptor.
     *
     * @return Enterprise license descriptor or {@code null} when running on Community Edition.
     */
    @Nullable
    @GridEnterpriseFeature("Returns 'null' in Community Edition.")
    public GridEnterpriseLicense license();

    /**
     * Whether or not node restart is enabled. Node restart us supported when this node was started
     * with <code>bin/ggstart.{sh|bat}</code> script using <code>-r</code> argument. Node can be
     * programmatically restarted using {@link GridFactory#restart(boolean, boolean)}} method.
     *
     * @return {@code True} if restart mode is enabled, {@code false} otherwise.
     * @see GridFactory#restart(boolean, boolean)
     */
    public boolean isRestartEnabled();

    /**
     * Whether or not remote JMX management is enabled for this node. Remote JMX management is
     * enabled when the following system property is set:
     * <ul>
     *     <li>{@code com.sun.management.jmxremote}</li>
     * </ul>
     *
     * @return {@code True} if remote JMX management is enabled - {@code false} otherwise.
     */
    public boolean isJmxRemoteEnabled();

    /**
     * Whether or not SMTP is configured. Note that SMTP is considered configured if
     * SMTP host is provided in configuration (see {@link GridConfiguration#getSmtpHost()}.
     * <p>
     * If SMTP is not configured all emails notifications will be disabled.
     *
     * @return {@code True} if SMTP is configured - {@code false} otherwise.
     * @see GridConfiguration#getSmtpFromEmail()
     * @see GridConfiguration#getSmtpHost()
     * @see GridConfiguration#getSmtpPassword()
     * @see GridConfiguration#getSmtpPort()
     * @see GridConfiguration#getSmtpUsername()
     * @see GridConfiguration#isSmtpSsl()
     * @see GridConfiguration#isSmtpStartTls()
     * @see #sendAdminEmailAsync(String, String, boolean)
     */
    public boolean isSmtpEnabled();

    /**
     * Schedule sending of given email to all configured admin emails. If no admin emails are configured this
     * method is no-op. If SMTP is not configured this method is no-op.
     * <p>
     * Note that this method returns immediately with the future and all emails will be sent asynchronously
     * in a different thread. If email queue is full or sending has failed - the email will be lost.
     * Email queue can fill up if rate of scheduling emails is greater than the rate of SMTP sending.
     * <p>
     * Implementation is not performing any throttling and it is responsibility of the caller to properly
     * throttle the emails, if necessary.
     * <p>
     * Note that this feature only available in Enterprise Edition.
     *
     * @param subj Subject of the email.
     * @param body Body of the email.
     * @param html If {@code true} the email body will have MIME <code>html</code> subtype.
     * @return Email's future. You can use this future to check on the status of the email. If future
     *      completes ok and its result value is {@code true} email was successfully sent. In all
     *      other cases - sending process has failed.
     * @see #isSmtpEnabled()
     * @see #sendAdminEmail(String, String, boolean)
     * @see GridConfiguration#getAdminEmails()
     */
    @GridEnterpriseFeature
    public GridFuture<Boolean> sendAdminEmailAsync(String subj, String body, boolean html);

    /**
     * Sends given email to all configured admin emails. If no admin emails are configured this
     * method is no-op. If SMTP is not configured this method is no-op.
     * <p>
     * Note that this method will return only if email is successfully sent. In case of sending failure
     * it will throw exception. This method will block until either email is successfully sent or
     * exception is thrown due to failure.
     * <p>
     * Implementation is not performing any throttling and it is responsibility of the caller to properly
     * throttle the emails, if necessary.
     * <p>
     * Note that this feature only available in Enterprise Edition.
     *
     * @param subj Subject of the email.
     * @param body Body of the email.
     * @param html If {@code true} the email body will have MIME <code>html</code> subtype.
     * @throws GridException Thrown in case when sending has failed.
     * @see #isSmtpEnabled()
     * @see #sendAdminEmailAsync(String, String, boolean)
     * @see GridConfiguration#getAdminEmails()
     */
    @GridEnterpriseFeature
    public void sendAdminEmail(String subj, String body, boolean html) throws GridException;

    /**
     * Gets grid's logger.
     *
     * @return Grid's logger.
     */
    public GridLogger log();

    /**
     * Deprecated in favor of {@link #nodes(GridPredicate[])} method.
     * <p>
     * Gets collection of all grid nodes.
     *
     * @return Collection of all grid nodes.
     */
    @Deprecated
    public Collection<GridRichNode> getAllNodes();

    /**
     * Tests whether or not this GridGain runtime runs on an enterprise edition. This method
     * is primarily for informational purpose.
     *
     * @return {@code True} for enterprise edition, {@code false} - for community edition.
     * @see GridEnterpriseFeatureException
     * @see GridEnterpriseOnly
     */
    public boolean isEnterprise();

    /**
     * This method is deprecated in favor of {@link #executor(GridPredicate[])} method.
     * <p>
     * Creates new {@link ExecutorService} which will execute all submitted
     * {@link Callable} and {@link Runnable} tasks on this projection.
     * <p>
     * User may run {@link Callable} and {@link Runnable} tasks
     * just like normally with {@link ExecutorService java.util.ExecutorService},
     * but these tasks must implement {@link Serializable} interface.
     * <p>
     * The execution will happen either locally or remotely, depending on
     * configuration of {@link GridLoadBalancingSpi} and {@link GridTopologySpi}.
     * <p>
     * The typical Java example could be:
     * <pre name="code" class="java">
     * ...
     * ExecutorService exec = grid.newGridExecutorService();
     *
     * Future&lt;String&gt; fut = exec.submit(new MyCallable());
     * ...
     * String res = fut.get();
     * ...
     * </pre>
     *
     * @return {@code ExecutorService} which delegates all calls to grid.
     */
    @Deprecated
    public ExecutorService newGridExecutorService();

    /**
     * Creates grid rich node wrapped around given thin node.
     * <p>
     * Note that in most cases end user should not have to use this method as most of
     * the APIs returning rich nodes already. This functionality exists for easier
     * migration from previous versions as well as for internal purposes.
     * <p>
     * Note also that GridGain caches rich instances internal and for same thin node
     * it will always return the same rich node.
     *
     * @param node Thin node to wrap.
     * @return Rich node.
     */
    public GridRichNode rich(GridNode node);

    /**
     * Creates grid rich cloud wrapped around given thin cloud.
     * <p>
     * Note that in most cases end user should not have to use this method as most of
     * the APIs returning rich clouds already. This functionality exists for easier
     * migration from previous versions as well as for internal purposes.
     * <p>
     * Note also that GridGain caches rich instances internal and for same thin cloud
     * it will always return the same rich cloud.
     *
     * @param cloud Thin cloud to wrap.
     * @return Rich cloud.
     */
    public GridRichCloud rich(GridCloud cloud);

    /**
     * Blocks and waits for the local event.
     * <p>
     * This is one of the two similar methods providing different semantic for waiting for events.
     * One method (this one) uses passed in optional continuation so that caller can pass a logic that
     * emits the event, and another method returns future allowing caller a more discrete control.
     * <p>
     * This method returns when either event of specified type has been generated and passed the
     * optional predicate, if any, or the timeout has elapsed. Note that some local events are generated
     * in response to the actions on remote nodes.
     * <p>
     * This method encapsulates an important paradigm as many operations in GridGain cause local events
     * to be generated even for the operations that may happen on the remote nodes. This method provides
     * convenient one-stop blocking and waiting functionality for such cases.
     *
     * @param timeout Timeout in milliseconds. If timeout value is less than or equal to zero, the
     *      method will not wait at all and will return immediately.
     * @param c Optional continuation. If specified it will ba called right after the event listener
     *      is registered but before the wait countdown started. This parameter is important when
     *      you need to avoid a window between execution of an operation that can cause the event
     *      and settings the event listener. Passing this operation as continuation into this method
     *      allows to avoid this window.
     * @param p Optional filtering predicate. Only if predicates evaluates to {@code true} will the event
     *      end the wait. Note that events of provided types only will be fed to the predicate.
     * @param types Types of the events to wait for.
     * @return Grid event if one occurred or {@code null} if the call got timed out.
     * @throws GridException Thrown only when continuation throws any exception.
     * @see #waitForEventAsync(GridPredicate, int...)
     */
    public GridEvent waitForEvent(long timeout, @Nullable Runnable c,
        @Nullable GridPredicate<? super GridEvent> p, @Nullable int... types) throws GridException;

    /**
     * Gets event future that allows for asynchronous waiting for the specified events.
     * <p>
     * This is one of the two similar methods providing different semantic for waiting for events.
     * One method uses passed in optional continuation so that caller can pass a logic that
     * emits the event, and another method (this one) returns future allowing caller a more discrete
     * control.
     * <p>
     * This method returns a future which by calling one of its {@code get} methods will block
     * and wait for the specified event (either indefinitely or with provided timeout). Note that
     * you need to call this method to acquire the future before emitting the event itself. This
     * way you can avoid the window when event is emitted but no listener is set for it.
     * <p>
     * This method encapsulates an important paradigm as many operations in GridGain cause local events
     * to be generated even for the operations that may happen on the remote nodes. This method provides
     * convenient one-stop blocking and waiting functionality for such cases.
     *
     * @param p Optional filtering predicate. Only if predicates evaluates to {@code true} will the event
     *      end the wait. Note that events of provided types only will be fed to the predicate.
     * @param types Types of the events to wait for.
     * @return Grid event future.
     * @see #waitForEvent(long, Runnable, GridPredicate, int...)
     */
    public GridFuture<GridEvent> waitForEventAsync(@Nullable GridPredicate<? super GridEvent> p,
        @Nullable int... types);

    /**
     * This method calculates hash value of the given set of nodes (a topology).
     * Topology hash can be used in applications with optimistic locking scenario
     * that relying on unchanged topology during a long operation.
     * <p>
     * Note that since GridGain topology architecture is peer-to-peer (without centralized
     * coordination) there is still a small window in which different nodes would have
     * different version for the same topology. Therefore, this version cannot be used
     * in strict ACID context. Values returned by this method are not guaranteed to be
     * sequential. Standard implementation uses CRC32 hash method.
     *
     * @param nodes Collection of grid nodes. Note that this can be either full topology or
     *      any subset of it.
     * @return 8-byte topology hash value.
     */
    public long topologyHash(Iterable<? extends GridNode> nodes);

    /**
     * Adds an event listener for local events.
     * <p>
     * Note that by default all events in GridGain are enabled and therefore generated and stored
     * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
     * under the load and therefore it creates a significant additional load on the system. If these events are
     * not needed by the application this load is unnecessary and leads to significant performance degradation.
     * <p>
     * It is <b>highly recommended</b> to enable only those events that your application logic requires
     * by using either  {@link GridConfiguration#getExcludeEventTypes()} or
     * {@link GridConfiguration#getIncludeEventTypes()} methods in GridGain configuration. Note that certain
     * events are required for GridGain's internal operations and such events will still be generated but not stored by
     * event storage SPI if they are disabled in GridGain configuration.
     * <p>
     * Note also that since event types are defined as integer the unknown (invalid) event types cannot be detected
     * and therefore will be ignored (because there is no way to know which user-defined types are used).
     *
     * @param lsnr Event listener for local events to add.
     * @param types Event types for which this listener will be notified. If this array is empty an exception
     *      will be thrown.
     *      <p>
     *      <b>NOTE:</b> subscribing to large set of events will impose significant performance penalty.
     * @throws GridRuntimeException Thrown in case when passed in array of event types is empty.
     * @see GridEvent
     * @see GridEventType
     * @see #addLocalEventListener(GridLocalEventListener, int, int...)
     */
    public void addLocalEventListener(GridLocalEventListener lsnr, int[] types);

    /**
     * Adds an event listener for local events.
     * <p>
     * Note that by default all events in GridGain are enabled and therefore generated and stored
     * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
     * under the load and therefore it creates a significant additional load on the system. If these events are
     * not needed by the application this load is unnecessary and leads to significant performance degradation.
     * <p>
     * It is <b>highly recommended</b> to enable only those events that your application logic requires
     * by using either  {@link GridConfiguration#getExcludeEventTypes()} or
     * {@link GridConfiguration#getIncludeEventTypes()} methods in GridGain configuration. Note that certain
     * events are required for GridGain's internal operations and such events will still be generated but not stored by
     * event storage SPI if they are disabled in GridGain configuration.
     * <p>
     * Note that unlike its sibling method this method never throws an exception because its signature
     * guarantees that there is at least one event type to subscribe for.
     * <p>
     * Note also that since event types are defined as integer the unknown (invalid) event types cannot be detected
     * and therefore will be ignored (because there is no way to know which user-defined types are used).
     *
     * @param lsnr Event listener for local events to add.
     * @param type Event type for which this listener will be notified.
     * @param types Optional event types for which this listener will be notified.
     * @see GridEvent
     * @see GridEventType
     * @see #addLocalEventListener(GridLocalEventListener, int[])
     */
    public void addLocalEventListener(GridLocalEventListener lsnr, int type, @Nullable int... types);

    /**
     * Removes local event listener.
     *
     * @param lsnr Local event listener to remove.
     * @param types Types of events for which to remove listener. If not specified,
     *      then listener will be removed for all types it was registered for.
     * @return {@code true} if listener was removed, {@code false} otherwise.
     * @see GridEventType
     * @see GridEvent
     */
    public boolean removeLocalEventListener(GridLocalEventListener lsnr, @Nullable int... types);

    /**
     * Register a message listener to receive messages that are sent by remote nodes and which pass
     * all provided message filters. See {@link #listen(GridPredicate2[])} method for more convenient
     * message listening API.
     *
     * @param lsnr Message listener to register.
     * @param p Message filter predicates. If none is provided - every message received will be
     *      delivered to registered listener.
     * @see #listen(GridPredicate2[])
     * @see #send(Collection, GridPredicate[])
     * @see #send(Object, GridPredicate[])
     * @see #removeMessageListener(GridMessageListener)
     */
   @SuppressWarnings("deprecation")
   @Deprecated
   public void addMessageListener(GridMessageListener lsnr, @Nullable GridPredicate<Object>... p);

    /**
     * Removes a previously registered message listener. See {@link #listen(GridPredicate2[])} method
     * for more convenient message listening API.
     *
     * @param lsnr Message listener to remove.
     * @return {@code true} of message listener was removed, {@code false} if it was not
     *      previously registered.
     * @see #listen(GridPredicate2[])
     * @see #addMessageListener(GridMessageListener, GridPredicate[])
     * @see #send(Collection, GridPredicate[])
     * @see #send(Object, GridPredicate[])
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public boolean removeMessageListener(GridMessageListener lsnr);

    /**
     * Gets local grid node.
     *
     * @return Local grid node.
     */
    public GridRichNode localNode();

    /**
     * Executes given closure on internal system thread pool asynchronously.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     *
     * @param r Runnable to execute. If {@code null} - this method is no-op.
     * @return Future for this execution.
     * @throws GridException Thrown in case of rejected execution by internal system thread pool.
     * @see #callLocal(Callable)
     * @see GridAbsClosure
     */
    public GridFuture<?> runLocal(@Nullable Runnable r) throws GridException;

    /**
     * Executes given callable on internal system thread pool asynchronously.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     *
     * @param c Callable to execute. If {@code null} - this method is no-op.
     * @return Future for this execution.
     * @param <R> Type of the return value for the closure.
     * @throws GridException Thrown in case of rejected execution by internal system thread pool.
     * @see #runLocal(Runnable)
     * @see GridOutClosure
     */
    public <R> GridFuture<R> callLocal(@Nullable Callable<R> c) throws GridException;

    /**
     * Schedules closure for execution using local <b>cron-based</b> scheduling.
     * <p>
     * Here's an example of scheduling a closure that broadcasts a message
     * to all nodes five times, once every minute, with initial delay in two seconds:
     * <pre name="code" class="java">
     * G.grid().scheduleLocal(
     *     new CA() { // CA is a type alias for GridAbsClosure.
     *         &#64;Override public void apply() {
     *             try {
     *                 g.run(BROADCAST, F.println("Hello Node! :)");
     *             }
     *             catch (GridException e) {
     *                 throw new GridClosureException(e);
     *             }
     *         }
     *     }, "{2, 5} * * * * *" // 2 seconds delay with 5 executions only.
     * );
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     *
     * @param c Closure to schedule to run as a background cron-based job.
     *      If {@code null} - this method is no-op.
     * @param pattern Scheduling pattern in UNIX cron format with optional prefix <tt>{n1, n2}</tt>
     *      where {@code n1} is delay of scheduling in seconds and {@code n2} is the number of execution. Both
     *      parameters are optional.
     * @return Scheduled execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridScheduleFuture<?> scheduleLocal(@Nullable Runnable c, String pattern) throws GridException;

    /**
     * Schedules closure for execution using local <b>cron-based</b> scheduling.
     * <p>
     * Here's an example of scheduling a closure that broadcasts a message
     * to all nodes five times, once every minute, with initial delay in two seconds:
     * <pre name="code" class="java">
     * G.grid().scheduleLocal(
     *     new CO<String>() { // CO is a type alias for GridOutClosure.
     *         &#64;Override public String apply() {
     *             try {
     *                 g.run(BROADCAST, F.println("Hello Node! :)");
     *
     *                 return "OK";
     *             }
     *             catch (GridException e) {
     *                 throw new GridClosureException(e);
     *             }
     *         }
     *     }, "{2, 5} * * * * *" // 2 seconds delay with 5 executions only.
     * );
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     *
     * @param c Closure to schedule to run as a background cron-based job.
     *       If {@code null} - this method is no-op.
     * @param pattern Scheduling pattern in UNIX cron format with optional prefix <tt>{n1, n2}</tt>
     *      where {@code n1} is delay of scheduling in seconds and {@code n2} is the number of execution. Both
     *      parameters are optional.
     * @return Scheduled execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridScheduleFuture<R> scheduleLocal(@Nullable Callable<R> c, String pattern) throws GridException;

    /**
     * Gets node-local storage instance.
     * <p>
     * Node-local values are similar to thread locals in a way that these values are not
     * distributed and kept only on local node (similar like thread local values are attached to the
     * current thread only). Node-local values are used primarily by closures executed from the remote
     * nodes to keep intermediate state on the local node between executions.
     * <p>
     * There's only one instance of node local storage per local node. Node local storage is
     * based on {@link ConcurrentMap} and is safe for multi-threaded access.
     *
     * @return Node local storage instance for the local node.
     */
    public <K, V> GridNodeLocal<K, V> nodeLocal();

    /**
     * Pings a remote node.
     * <p>
     * Discovery SPIs usually have some latency in discovering failed nodes. Hence,
     * communication to remote nodes may fail at times if an attempt was made to
     * establish communication with a failed node. This method can be used to check
     * if communication has failed due to node failure or due to some other reason.
     *
     * @param nodeId ID of a node to ping.
     * @return {@code true} if node for a given ID is alive, {@code false} otherwise.
     * @see GridDiscoverySpi
     */
    public boolean pingNode(UUID nodeId);

    /**
     * Explicitly deploys given grid task on the local node. Upon completion of this method,
     * a task can immediately be executed on the grid, considering that all participating
     * remote nodes also have this task deployed. If peer-class-loading is enabled
     * (see {@link GridConfiguration#isPeerClassLoadingEnabled()}), then other nodes
     * will automatically deploy task upon execution request from the originating node without
     * having to manually deploy it.
     * <p>
     * Another way of class deployment which is supported is deployment from local class path.
     * Class from local class path has a priority over P2P deployed.
     * Following describes task class deployment:
     * <ul>
     * <li> If peer class loading is enabled (see {@link GridConfiguration#isPeerClassLoadingEnabled()})
     * <ul> Task class loaded from local class path if it is not defined as P2P loaded
     *      (see {@link GridConfiguration#getPeerClassLoadingClassPathExclude()}).</ul>
     * <ul> If there is no task class in local class path or task class needs to be peer loaded
     *      it is downloaded from task originating node.</ul>
     * </li>
     * <li> If peer class loading is disabled (see {@link GridConfiguration#isPeerClassLoadingEnabled()})
     * <ul> Check that task class was deployed (either as GAR or explicitly) and use it.</ul>
     * <ul> If task class was not deployed then we try to find it in local class path by task
     *      name. Task name should correspond task class name.</ul>
     * <ul> If task has custom name (that does not correspond task class name) and this
     *      task was not deployed before then exception will be thrown.</ul>
     * </li>
     * </ul>
     * <p>
     * Note that this is an alternative deployment method additionally to deployment SPI that
     * provides more formal method of deploying a task, e.g. deployment of GAR files and/or URI-based
     * deployment. See {@link GridDeploymentSpi} for detailed information about grid task deployment.
     * <p>
     * Note that class can be deployed multiple times on remote nodes, i.e. re-deployed. GridGain
     * maintains internal version of deployment for each instance of deployment (analogous to
     * class and class loader in Java). Execution happens always on the latest deployed instance
     * (latest that is on the node where execution request is originated). This allows a very
     * convenient development model when a developer can execute a task on the grid from IDE,
     * then realize that he made a mistake, stop his node in IDE, fix mistake and re-execute the
     * task. Grid will automatically detect that task got renewed and redeploy it on all remote
     * nodes upon execution.
     * <p>
     * This method has no effect if the class passed in was already deployed. Implementation
     * checks for this condition and returns immediately.
     *
     * @param taskCls Task class to deploy. If task class has {@link GridTaskName} annotation,
     *      then task will be deployed under a name specified within annotation. Otherwise, full
     *      class name will be used as task's name.
     * @throws GridException If task is invalid and cannot be deployed.
     * @see GridDeploymentSpi
     */
    @SuppressWarnings("unchecked")
    public void deployTask(Class<? extends GridTask> taskCls) throws GridException;

    /**
     * Explicitly deploys given grid task on the local node. Upon completion of this method,
     * a task can immediately be executed on the grid, considering that all participating
     * remote nodes also have this task deployed. If peer-class-loading is enabled
     * (see {@link GridConfiguration#isPeerClassLoadingEnabled()}), then other nodes
     * will automatically deploy task upon execution request from the originating node without
     * having to manually deploy it.
     * <p>
     * Another way of class deployment which is supported is deployment from local class path.
     * Class from local class path has a priority over P2P deployed.
     * Following describes task class deployment:
     * <ul>
     * <li> If peer class loading is enabled (see {@link GridConfiguration#isPeerClassLoadingEnabled()})
     * <ul> Task class loaded from local class path if it is not defined as P2P loaded
     *      (see {@link GridConfiguration#getPeerClassLoadingClassPathExclude()}).</ul>
     * <ul> If there is no task class in local class path or task class needs to be peer loaded
     *      it is downloaded from task originating node using provided class loader.</ul>
     * </li>
     * <li> If peer class loading is disabled (see {@link GridConfiguration#isPeerClassLoadingEnabled()})
     * <ul> Check that task class was deployed (either as GAR or explicitly) and use it.</ul>
     * <ul> If task class was not deployed then we try to find it in local class path by task
     *      name. Task name should correspond task class name.</ul>
     * <ul> If task has custom name (that does not correspond task class name) and this
     *      task was not deployed before then exception will be thrown.</ul>
     * </li>
     * </ul>
     * <p>
     * Note that this is an alternative deployment method additionally to deployment SPI that
     * provides more formal method of deploying a task, e.g. deployment of GAR files and/or URI-based
     * deployment. See {@link GridDeploymentSpi} for detailed information about grid task deployment.
     * <p>
     * Note that class can be deployed multiple times on remote nodes, i.e. re-deployed. GridGain
     * maintains internal version of deployment for each instance of deployment (analogous to
     * class and class loader in Java). Execution happens always on the latest deployed instance
     * (latest that is on the node where execution request is originated). This allows a very
     * convenient development model when a developer can execute a task on the grid from IDE,
     * then realize that he made a mistake, stop his node in IDE, fix mistake and re-execute the
     * task. Grid will automatically detect that task got renewed and redeploy it on all remote
     * nodes upon execution.
     * <p>
     * This method has no effect if the class passed in was already deployed. Implementation
     * checks for this condition and returns immediately.
     *
     * @param taskCls Task class to deploy. If task class has {@link GridTaskName} annotation,
     *      then task will be deployed under a name specified within annotation. Otherwise, full
     *      class name will be used as task's name.
     * @param clsLdr Task resources/classes class loader. This class loader is in charge
     *      of loading all necessary resources.
     * @throws GridException If task is invalid and cannot be deployed.
     * @see GridDeploymentSpi
     */
    @SuppressWarnings("unchecked")
    public void deployTask(Class<? extends GridTask> taskCls, ClassLoader clsLdr) throws GridException;

    /**
     * Gets map of all locally deployed tasks keyed by their task name satisfying all given predicates.
     * If no tasks were locally deployed, then empty map is returned. If no predicates provided - all
     * locally deployed tasks, if any, will be returned.
     *
     * @param p Set of filtering predicates. If no predicates provided - all
     *      locally deployed tasks, if any, will be returned.
     * @return Map of locally deployed tasks keyed by their task name.
     */
    public Map<String, Class<? extends GridTask<?, ?>>> localTasks(
        @Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>... p);

    /**
     * Deprecated in favor of following two monadic methods:
     * <ul>
     * <li>{@link #send(Collection, GridPredicate[])}</li>
     * <li>{@link #send(Object, GridPredicate[])}</li>
     * </ul>
     * <p>
     * Sends message to the given grid node.
     *
     * @param node Node to send message to.
     * @param msg Message to send.
     * @throws GridException Thrown in case of any errors.
     */
    @Deprecated
    public void sendMessage(GridNode node, Object msg) throws GridException;

    /**
     * Deprecated in favor of following two monadic methods:
     * <ul>
     * <li>{@link #send(Collection, GridPredicate[])}</li>
     * <li>{@link #send(Object, GridPredicate[])}</li>
     * </ul>
     * <p>
     * Sends message to the given grid nodes.
     *
     * @param nodes Nodes to send message to.
     * @param msg Message to send.
     * @throws GridException Thrown in case of any errors.
     */
    @Deprecated
    public void sendMessage(Collection<? extends GridNode> nodes, Object msg) throws GridException;

    /**
     * Deprecated in favor of {@link #localTasks(GridPredicate[])} method.
     * <p>
     * Gets map of all locally deployed tasks keyed by their task name satisfying all given predicates.
     * If no tasks were locally deployed, then empty map is returned. If no predicates provided - all
     * locally deployed tasks, if any, will be returned.
     *
     * @param p Set of filtering predicates. If no predicates provided - all
     *      locally deployed tasks, if any, will be returned.
     * @return Map of locally deployed tasks keyed by their task name.
     */
    @Deprecated
    public Map<String, Class<? extends GridTask<?, ?>>> getLocalTasks(
        @Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>... p);

    /**
     * Makes the best attempt to undeploy a task from the whole grid. Note that this
     * method returns immediately and does not wait until the task will actually be
     * undeployed on every node.
     * <p>
     * Note that GridGain maintains internal versions for grid tasks in case of redeployment.
     * This method will attempt to undeploy all versions on the grid task with
     * given name.
     *
     * @param taskName Name of the task to undeploy. If task class has {@link GridTaskName} annotation,
     *      then task was deployed under a name specified within annotation. Otherwise, full
     *      class name should be used as task's name.
     * @throws GridException Thrown if undeploy failed.
     */
    public void undeployTask(String taskName) throws GridException;

    /**
     * Queries local node for events using passed-in predicate filters for event selection.
     *
     * @param p Mandatory predicates to filter events. All predicates must be satisfied for the
     *      event to be returned.
     *      <p>
     *      <b>Note:</b> unlike other methods in GridGain APIs if no predicates is provided this
     *      method will return no results. This exception is made to avoid situation when all local
     *      events are erroneously returned. Returning all local events may result in creating
     *      collection with tens of thousands elements seriously compromising the system's performance.
     * @return Collection of grid events found on local node.
     * @see PE
     */
    public Collection<GridEvent> localEvents(@Nullable GridPredicate<? super GridEvent>... p);

    /**
     * Records locally generated event. Registered local listeners will be notified, if any. This
     * event can be obtained from the remote nodes by performing a distributed query using
     * {@link #remoteEvents(GridPredicate, long, GridPredicate[])} method.
     *
     * @param evt Locally generated event.
     */
    public void recordLocalEvent(GridEvent evt);

    /**
     * This method is deprecated in favor of {@link #name()}.
     * <p>
     * Gets the name of the grid this grid instance (and correspondingly its local node) belongs to.
     * Note that single Java VM can have multiple grid instances all belonging to different grids. Grid
     * name allows to indicate to what grid this particular grid instance (i.e. grid runtime and its
     * local node) belongs to.
     * <p>
     * If default grid instance is used, then
     * {@code null} is returned. Refer to {@link GridFactory} documentation
     * for information on how to start named grids.
     *
     * @return Name of the grid, or {@code null} for default grid.
     */
    @Deprecated
    public String getName();

    /**
     * Gets the name of the grid this grid instance (and correspondingly its local node) belongs to.
     * Note that single Java VM can have multiple grid instances all belonging to different grids. Grid
     * name allows to indicate to what grid this particular grid instance (i.e. grid runtime and its
     * local node) belongs to.
     * <p>
     * If default grid instance is used, then
     * {@code null} is returned. Refer to {@link GridFactory} documentation
     * for information on how to start named grids.
     *
     * @return Name of the grid, or {@code null} for default grid.
     */
    public String name();

    /**
     * Ges version string of the GridGain instance. This method is for information
     * purpose only.
     *
     * @return GridGain version string (excluding the build number).
     * @see #build()
     */
    public String version();

    /**
     * Gets build number of this GridGain instance. This method is for information
     * purpose only.
     *
     * @return GridGain instance build number.
     * @see #version()
     */
    public String build();

    /**
     * Copyright statement for GridGain code.
     *
     * @return Legal copyright statement for GridGain code.
     */
    public String copyright();

    /**
     * Gets the cache instance for the given name if one is configured or
     * <tt>null</tt> otherwise.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     * @param name Cache name.
     * @return Cache instance for given name or <tt>null</tt> if one does not exist.
     */
    public <K, V> GridCache<K, V> cache(@Nullable String name);

    /**
     * Gets default cache instance if one is configured or <tt>null</tt> otherwise.
     * The {@link GridCache#name()} method on default instance returns <tt>null</tt>.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Default cache instance.
     */
    public <K, V> GridCache<K, V> cache();

    /**
     * Gets configured cache instance that satisfy all provided predicates. If no predicates
     * provided - all configured caches will be returned.
     *
     * @param p Predicates. If none provided - all configured caches will be returned.
     * @return Configured cache instances that satisfy all provided predicates.
     */
    public Collection<GridCache<?, ?>> caches(@Nullable GridPredicate<? super GridCache<?, ?>>... p);

    /**
     * Writes given data to specified swap space.
     *
     * @param space Optional swap space name. If {@code null} is passed - global swap space will be used.
     * @param key Data key.
     * @param val Data value.
     * @throws GridException Thrown in case of any errors.
     */
    public void writeToSwap(@Nullable String space, Object key, @Nullable Object val) throws GridException;

    /**
     * Reads data stored by {@link #writeToSwap(String, Object, Object)} method.
     *
     * @param space Optional swap space name. If {@code null} is passed - global swap space will be used.
     * @param key Data key.
     * @return Data read from swap space or {@code null} if no data was stored.
     * @throws GridException Thrown in case of any errors.
     */
    @Nullable public <T> T readFromSwap(@Nullable String space, Object key) throws GridException;

    /**
     * Removes data stored by {@link #writeToSwap(String, Object, Object)} method.
     *
     * @param space Optional swap space name. If {@code null} is passed - global
     *      swap space will be used.
     * @param key Data key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is not executed.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException Thrown in case of any errors.
     */
    public boolean removeFromSwap(@Nullable String space, Object key, @Nullable GridInClosure<Object> c)
        throws GridException;

    /**
     * Clears all entry from the specified swap space.
     *
     * @param space Optional swap space name. If {@code null} is passed - global swap space will be used.
     * @throws GridException Thrown in case of any errors.
     */
    public void clearSwapSpace(@Nullable String space) throws GridException;

    /**
     * This method is deprecated in favor of {@link #configuration()}.
     * <p>
     * Gets the configuration of this grid instance.
     *
     * @return Grid configuration instance.
     */
    @Deprecated
    public GridConfiguration getConfiguration();

    /**
     * Gets the configuration of this grid instance.
     *
     * @return Grid configuration instance.
     */
    public GridConfiguration configuration();

    /**
     * Gets collection of clouds that satisfy all given predicates. Note that if no predicates provided
     * - all cloud descriptors will be returned.
     *
     * @param p Predicates. If none is provided - all cloud will be returned.
     * @return Collection of clouds that satisfy all given predicates.
     * @see org.gridgain.grid.typedef.PCR
     */
    public Collection<GridRichCloud> clouds(@Nullable GridPredicate<? super GridRichCloud>... p);

    /**
     * Gets the cloud for given cloud ID.
     *
     * @param cloudId Cloud ID.
     * @return Cloud for given ID or {@code null} if such cloud was not found.
     */
    public GridRichCloud cloud(String cloudId);

    /**
     * Use {@link #remoteNodes(GridPredicate[])} instead.
     *
     * @param p Predicates to filter remote nodes. If none provided - all remote nodes will be returned.
     * @return Nodes for which the predicates returned {@code true}.
     * @see #remoteNodes(GridPredicate[])
     */
    @Deprecated
    public Collection<GridRichNode> getRemoteNodes(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Use {@link #localNode()} instead.
     *
     * @return Local grid node.
     * @see #localNode()
     */
    @Deprecated
    public GridRichNode getLocalNode();

    /**
     * Use {@link #nodes(GridPredicate[])} instead.
     *
     * @param p Predicate to filter nodes. If none provided - all nodes will be returned.
     * @return Nodes for which the predicate returned {@code true}.
     */
    @Deprecated
    public Collection<GridRichNode> getNodes(@Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Use {@link #node(UUID, GridPredicate[])} instead.
     *
     * @param nodeId ID of a node to get.
     * @return Node for a given ID or {@code null} is such node has not been discovered.
     */
    @Deprecated
    public GridNode getNode(UUID nodeId);

    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link #addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     *
     * @param lsnr Discovery listener to add.
     */
    @SuppressWarnings({"deprecation"})
    @Deprecated
    public void addDiscoveryListener(GridDiscoveryListener lsnr);

    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link #addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     *
     * @param lsnr Discovery listener to remove.
     * @return {@code True} if listener was removed, {@code false} otherwise.
     */
    @SuppressWarnings({"deprecation"})
    @Deprecated
    public boolean removeDiscoveryListener(GridDiscoveryListener lsnr);
}
