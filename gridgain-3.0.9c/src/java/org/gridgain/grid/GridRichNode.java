// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Defines a "rich" node as a single node-based grid projection.
 * <p>
 * All main grid entities such as grid, projection, cloud and a node instances can be viewed as
 * collection of grid nodes (in case of the grid node this collection consist of only one
 * element). As such they all share the same set of operations that can be performed on a set
 * grid nodes. These operations are defined in {@link GridProjection} interface and called
 * <tt>monadic</tt> as they are equally defined on any arbitrary set of nodes.
 * <h2 class="header">Thread Safety</h2>
 * Implementation of this interface provides necessary thread synchronization.
 * <h2 class="header">Nullable and Monads</h2>
 * Many methods in this interface accepts nullable parameters. Although it may seem counter intuitive
 * for some of them - it is done to promote monadic usage of this interface. Java doesn't support
 * natively concepts like <tt>Option</tt> in Scala and returning, accepting, and properly handling
 * {@code null} values is Java's way to support such monadic invocations.
 * <p>
 * All methods that accept {@code null} values (for monadic purposes) will gracefully handle it by
 * either returning a finished future, or empty collection, {@code null} value, or combination of the
 * above. Most method calls therefore can be chained without an explicit checks for {@code null}s.
 * <p>
 * The downside of this approach that inadvertent errors of passing {@code null} will not result
 * in {@link NullPointerException} and may be harder to catch.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridRichNode extends GridProjection, GridNode, Comparable<GridRichNode>,
    GridTypedProduct<GridRichNode> {
    /**
     * Gets the projection on all nodes that reside on the same physical computer as this node.
     * <p>
     * Detection of the same physical computer is based on comparing set of network interface MACs.
     * If two nodes have the same set of MACs, GridGain considers these nodes running on the same
     * physical computer. Note that this same logic is used in license management.
     * <p>
     * Knowing your neighbors can be very important when performing a dynamic split since nodes on the
     * same computer will often bypass network when communicating with each other leading to much better
     * performance for certain use cases. Conversely, one would like to avoid loading the nodes
     * from the same physical computer with tasks as these nodes share CPU and memory between them resulting
     * in reduced performance comparing to a no-neighboring split.
     *
     * @return Project containing all nodes that reside on the same physical computer as this
     *      node (including it). This method never returns {@code null}.
     * @see GridProjection#neighborhood()
     */
    public GridProjection neighbors();

    /**
     * Gets the original grid node wrapped by this rich interface.
     *
     * @return Original grid node.
     */
    public GridNode originalNode();

    /**
     * Tests whether or not this node is a local node.
     *
     * @return {@code True} if this node is a local node, {@code false} otherwise.
     */
    public boolean isLocal();

    /**
     * Tests whether or not this node is a daemon.
     * <p>
     * Daemon nodes are the usual grid nodes that participate in topology but not
     * visible on the main APIs, i.e. they are not part of any projections. The only
     * way to see daemon nodes is to use {@link Grid#daemonNodes(GridPredicate[])} method.
     * <p>
     * Daemon nodes are used primarily for management and monitoring functionality that
     * is build on GridGain and needs to participate in the topology but should be
     * excluded from "normal" topology so that it won't participate in task execution
     * or data grid.
     *  
     * @return {@code True} if this node is a daemon, {@code false} otherwise.
     */
    public boolean isDaemon();

    /**
     * Gets name of the grid this node belongs to.
     *
     * @return Name of the grid this node belongs to. Note that default grid has
     *      {@code null} name.
     */
    @Nullable public String gridName();

    /**
     * Pings this node.
     * <p>
     * Discovery SPIs usually have some latency in discovering failed nodes. Hence,
     * communication to remote nodes may fail at times if an attempt was made to
     * establish communication with a failed node. This method can be used to check
     * if communication has failed due to node failure or due to some other reason.
     *
     * @return {@code true} if node for a given ID is alive, {@code false} otherwise.
     * @see Grid#pingNode(UUID)
     */
    public boolean ping();

    /**
     * Gets first 8 characters from node ID. This is non-unique ID can be used
     * for informational purposes where it is more convenient that a longer but
     * guaranteed unique node ID.
     *
     * @return 8-character non-unique node ID.
     */
    public String id8();

    /**
     * Gets the cloud this node belongs to.
     *
     * @return Cloud or {@code null} if this node does not belong to any cloud.
     */
    public GridRichCloud cloud();

    /**
     * Convenient shortcut that asynchronously executes given closure on this node.
     * <p>
     * Unlike its sibling method {@link #call(Callable)} this method does
     * not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param job Closure to invoke. If {@code null} - this method is no-op.
     * @param <R> Type of the closure return value.
     * @return Non-cancellable closure result future.
     * @throws GridException Thrown in case of any error.
     * @see PN
     * @see #call(GridClosureCallMode, Callable, GridPredicate[])
     */
    public <R> GridFuture<R> callAsync(@Nullable Callable<R> job) throws GridException;

    /**
     * Convenient shortcut that asynchronously executes given closures on this node.
     * <p>
     * Unlike its sibling method {@link #call(Collection)} this method does
     * not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Closures to invoke. If {@code null} or empty - this method is no-op.
     * @param <R> Type of the closure return value.
     * @return Future collection of closure results. Order is undefined.
     * @throws GridException Thrown in case of any error.
     * @see PN
     */
    public <R> GridFuture<Collection<R>> callAsync(@Nullable Collection<? extends Callable<R>> jobs)
        throws GridException;

    /**
     * Convenient shortcut that asynchronously executes given jobs on this node.
     * <p>
     * Unlike its sibling method {@link #forkjoin(Collection, GridReducer)}
     * this method does not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Closures to executes. If {@code null} or empty - this method is no-op.
     * @param rdc Result reducing closure If {@code null} - this method is no-op.
     * @param <R1> Closure result type.
     * @param <R2> Type of the reduced value.
     * @return Future value produced by reducing closure.
     * @throws GridException Thrown in case of any error.
     * @see PN
     */
    public <R1, R2> GridFuture<R2> forkjoinAsync(@Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc) throws GridException;

    /**
     * Convenient shortcut that asynchronously executes given closure on this node.
     * <p>
     * Unlike its sibling method {@link #run(Runnable)} this method does
     * not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param job Job closure to execute. If {@code null} - this method is no-op.
     * @return Non-cancellable future of this execution.
     * @throws GridException Thrown in case of any error.
     * @see PN
     * @see #run(GridClosureCallMode, Runnable, GridPredicate[])
     */
    public GridFuture<?> runAsync(@Nullable Runnable job) throws GridException;

    /**
     * Convenient shortcut that asynchronously executes given closures on this node.
     * <p>
     * Unlike its sibling method {@link #run(Collection)} this method does
     * not block and returns immediately with future. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Job closures to execute. If {@code null} or empty - this method is no-op.
     * @return Non-cancellable future of this execution.
     * @throws GridException Thrown in case of any error.
     * @see PN
     * @see #run(GridClosureCallMode, Collection, GridPredicate[])
     */
    public GridFuture<?> runAsync(@Nullable Collection<? extends Runnable> jobs) throws GridException;

    /**
     * Convenient shortcut that executes given closure on this node. This method simply delegates to
     * {@link #run(GridClosureCallMode, Runnable, GridPredicate[])}.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param job Job closure to execute. If {@code null} - this method is no-op.
     * @throws GridException Thrown in case of any error.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    public void run(@Nullable Runnable job) throws GridException;

    /**
     * Convenient shortcut that executes given closures on this node. This method simply
     * delegates to {@link #run(GridClosureCallMode, Collection, GridPredicate[])}.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Job closures to execute. If {@code null} or empty - this method is no-op.
     * @throws GridException Thrown in case of any error.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    public void run(@Nullable Collection<? extends Runnable> jobs) throws GridException;

    /**
     * Convenient shortcut that executes given closure on this node. This method simply delegates to
     * {@link #call(GridClosureCallMode, Callable, org.gridgain.grid.lang.GridPredicate[])}.
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision
     * resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param job Closure to invoke. If {@code null} - this method is no-op.
     * @param <R> Type of the closure return value.
     * @return Closure result.
     * @throws GridException Thrown in case of any error.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    public <R> R call(@Nullable Callable<R> job) throws GridException;

    /**
     * Convenient shortcut that executes given closures on this node. This method simply delegates to
     * {@link GridProjection#call(GridClosureCallMode, Collection, GridPredicate[])}
     * <p>
     * This method will block until the execution is complete. All default SPI implementations
     * configured for this grid instance will be used (i.e. failover, load balancing, collision
     * resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message, splits it into individual
     * words and prints each word on an individual grid node using typedefs, functional APIs and closure
     * execution on the grid:
     * <pre name="code" class="java">
     * public static void sayIt(String phrase) throws GridException {
     *     G.grid().call(SPREAD, F.yield(phrase.split(" "), F.&lt;String&gt;printf("%s")));
     * }
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Closures to invoke. If {@code null} or empty - this method is no-op.
     * @param <R> Type of the closure return value.
     * @return Collection of closure results. Order is undefined.
     * @throws GridException Thrown in case of any error.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    public <R> Collection<R> call(@Nullable Collection<? extends Callable<R>> jobs) throws GridException;

    /**
     * Convenient shortcut that executes given jobs on this node and reduces their results.
     * This method simply delegates to {@link #reduce(GridClosureCallMode, Collection, GridReducer, GridPredicate[])}.
     * <p>
     * This method will block until its execution is complete or an exception is thrown.
     * All default SPI implementations configured for this grid instance will be
     * used (i.e. failover, load balancing, collision resolution, etc.).
     * Note that if you need greater control on any aspects of Java code execution on the grid
     * you should implement {@link GridTask} which will provide you with full control over the execution.
     * <p>
     * Here's a general example of the Java method that takes a text message and calculates its length
     * by splitting it by spaces, calculating the length of each word on individual (remote) grid node
     * and then summing (reducing) results from all nodes to produce the final length of the input string
     * using function APIs, typedefs, and execution closures on the grid:
     * <pre name="code" class="java">
     * public static int length(final String msg) throws GridException {
     *     return G.grid().reduce(SPREAD,
     *         F.yield(
     *             msg.split(" "),
     *             new C1&lt;String, Integer&gt;() {
     *                 &#64;Override public Integer apply(String e) { return e.length(); }
     *             }
     *         ),
     *         new R1&lt;Integer, Integer&gt;() {
     *             private int len = 0;
     *
     *             &#64;Override public void apply(Integer e) { len += e + 1; }
     *             &#64;Override public Integer reduce() { return len - 1; }
     *         }
     *     );
     * }
     * </pre>
     * <p>
     * Note that class {@link GridAbsClosure} implements {@link Runnable} and class {@link GridOutClosure}
     * implements {@link Callable} interface. Note also that class {@link GridFunc} and typedefs provide rich
     * APIs and functionality for closures and predicates based processing in GridGain. While Java interfaces
     * {@link Runnable} and {@link Callable} allow for lowest common denominator for APIs - it is advisable
     * to use richer Functional Programming support provided by GridGain available in {@link org.gridgain.grid.lang}
     * package.
     * <p>
     * Notice that {@link Runnable} and {@link Callable} implementations must support serialization as required
     * by the configured marshaller. For example, JDK marshaller will require that implementations would
     * be serializable. Other marshallers, e.g. JBoss marshaller, may not have this limitation. Please consult
     * with specific marshaller implementation for the details. Note that all closures and predicates in
     * {@link org.gridgain.grid.lang} package are serializable and can be freely used in the distributed
     * context with all marshallers currently shipped with GridGain.
     *
     * @param jobs Closures to executes. If {@code null} or empty - this method is no-op.
     * @param rdc Result reducing closure. If {@code null} - this method is no-op.
     * @param <R1> Closure result type.
     * @param <R2> Type of the reduced value.
     * @return Value produced by reducing closure.
     * @throws GridException Thrown in case of any error.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     */
    public <R1, R2> R2 forkjoin(@Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc) throws GridException;

    /**
     * Gets node local value from this node with given key. If this node is remote the value
     * will be fetched from the remote node. Note that this method will block until the value
     * is retrieved. Note also that {@code key} must serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local ket.
     * @param <T> Type of the value.
     * @return Node local value from this node.
     * @throws GridException Thrown in case of any failure.
     * @throws GridInterruptedException Subclass of {@link GridException} thrown if the wait was interrupted.
     * @throws GridFutureCancelledException Subclass of {@link GridException} throws if computation was cancelled.
     * @see #nodeLocalGetAsync(Object)
     */
    public <T> T nodeLocalGet(Object key) throws GridException;

    /**
     * Gets node local value from this node with given key. If this node is remote the value
     * will be fetched from the remote node. Note that unlike its sibling {@link #nodeLocalGet(Object)}
     * this method will not block and will return immediately with future. Note also that {@code key}
     * must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local ket.
     * @param <T> Type of the value.
     * @return Future of node local value from this node.
     * @throws GridException Thrown in case of any failure.
     * @see #nodeLocalGet(Object)
     */
    public <T> GridFuture<T> nodeLocalGetAsync(Object key) throws GridException;

    /**
     * Puts value into node local storage of this node. If this node is remote the value
     * will be transfered to remote node and put there in remote node's local storage.
     * Note that unlike its sibling {@link #nodeLocalPutAsync(Object, Object)}
     * this method will block until operation is complete. Note also that {@code key} and
     * {@code value} must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param val Node local value.
     * @param <T> Type of the previous value, if any.
     * @return Previous value or {@code null}.
     * @throws GridException Thrown in case of any failure.
     */
    public <T> T nodeLocalPut(Object key, @Nullable Object val) throws GridException;

    /**
     * Puts value into node local storage of this node. If this node is remote the value
     * will be transfered to remote node and put there in remote node's local storage.
     * Note that unlike its sibling {@link #nodeLocalPut(Object, Object)}
     * this method will not block and will return with the future. Note also that {@code key}
     * and {@code value} must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param val Node local value.
     * @param <T> Type of the previous value, if any.
     * @return Future of previous value.
     * @throws GridException Thrown in case of any failure.
     */
    public <T> GridFuture<T> nodeLocalPutAsync(Object key, @Nullable Object val) throws GridException;

    /**
     * Runs given closure on this node using value from node local storage of this node.
     * Note that unlike its sibling {@link #nodeLocalRunAsync(Object, GridInClosure)}
     * this method will block until operation is complete. Note also that {@code key}
     * must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param c Closure to run. If {@code null} - this method is no-op.
     * @throws GridException Thrown in case of any failure.
     * @see #run(Runnable)
     */
    public void nodeLocalRun(Object key, @Nullable GridInClosure<Object> c) throws GridException;

    /**
     * Runs given closure on this node using value from node local storage of this node.
     * Note that unlike its sibling {@link #nodeLocalRun(Object, GridInClosure)}
     * this method will not block and will return with the future. Note also that {@code key}
     * must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param c Closure to run. If {@code null} - this method is no-op.
     * @return Future.
     * @throws GridException Thrown in case of any failure.
     * @see #runAsync(Runnable)
     */
    public GridFuture<?> nodeLocalRunAsync(Object key, @Nullable GridInClosure<Object> c) throws GridException;

    /**
     * Runs given closure on this node using value from node local storage of this node.
     * Note that unlike its sibling {@link #nodeLocalCallAsync(Object, GridClosure)}
     * this method will block until operation is complete. Note also that {@code key}
     * must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param c Closure to run. If {@code null} - this method is no-op.
     * @param <T> Type of the closure return value.
     * @return Closure result.
     * @throws GridException Thrown in case of any failure.
     * @see #call(Callable)
     */
    public <T> T nodeLocalCall(Object key, @Nullable GridClosure<Object, T> c) throws GridException;

    /**
     * Runs given closure on this node using value from node local storage of this node.
     * Note that unlike its sibling {@link #nodeLocalCall(Object, GridClosure)} this
     * method will not block and will return with the future. Note also that {@code key}
     * must be serializable as per current marshaller.
     * <p>
     * Note that for the local node this method will be optimized to avoid unnecessary
     * distribution overhead and operation will be performed in a local context.
     *
     * @param key Node local key.
     * @param c Closure to run. If {@code null} - this method is no-op.
     * @param <T> Type of the closure return value.
     * @return Future of closure result.
     * @throws GridException Thrown in case of any failure.
     * @see #callAsync(Callable)
     */
    public <T> GridFuture<T> nodeLocalCallAsync(Object key, @Nullable GridClosure<Object, T> c) throws GridException;
}
