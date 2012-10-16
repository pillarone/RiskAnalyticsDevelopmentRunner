// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class is deprecated in favor of {@link GridJobAdapterEx} class.
 * <p>
 * Convenience adapter for {@link GridJob} implementations. It provides the
 * following functionality:
 * <ul>
 * <li>
 *      Default implementation of {@link GridJob#cancel()} method and ability
 *      to check whether cancellation occurred.
 * </li>
 * <li>
 *      Ability to set and get a job argument via {@link #setArgument(Serializable)}
 *      and {@link #getArgument()} methods.
 * </li>
 * </ul>
 * Here is an example of how {@code GridJobAdapter} can be used from task logic
 * to create jobs. The example creates job adapter as anonymous class, but you
 * are free to create a separate class for it.
 * <pre name="code" class="java">
 * public class TestGridTask extends GridTaskSplitAdapter&lt;String, Integer&gt; {
 *     // Used to imitate some logic for the
 *     // sake of this example
 *     private int multiplier = 3;
 *
 *     &#64;Override
 *     protected Collection&lt;? extends GridJob&gt; split(int gridSize, final String arg) throws GridException {
 *         List&lt;GridJobAdapter&lt;String&gt;&gt; jobs = new ArrayList&lt;GridJobAdapter&lt;String&gt;&gt;(gridSize);
 *
 *         for (int i = 0; i < gridSize; i++) {
 *             jobs.add(new GridJobAdapter() {
 *                 // Job execution logic.
 *                 public Object execute() throws GridException {
 *                     return multiplier * arg.length();
 *                 }
 *             });
 *        }
 *
 *         return jobs;
 *     }
 *
 *     // Aggregate multiple job results into
 *     // one task result.
 *     public Integer reduce(List&lt;GridJobResult&gt; results) throws GridException {
 *         int sum = 0;
 *
 *         // For the sake of this example, let's sum all results.
 *         for (GridJobResult res : results) {
 *             sum += (Integer)res.getData();
 *         }
 *
 *         return sum;
 *     }
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Deprecated
public abstract class GridJobAdapter<G extends Serializable> extends GridMetadataAwareAdapter implements GridJob,
    Callable<Object>, GridPeerDeployAware {
    /** Job argument. */
    private List<G> args;

    /** Cancellation flag. */
    private volatile boolean cancelled;

    /** Peer deploy aware class. */
    private transient volatile GridPeerDeployAware p;

    /**
     * No-arg constructor.
     * <p>
     * <b>Note:</b> the job argument will be <tt>null</tt> which usually <i>is not</i> the intended behavior.
     * You can use {@link #setArgument(Serializable)} to  set job argument.
     */
    protected GridJobAdapter() {
        args = new ArrayList<G>(1);
    }

    /**
     * Creates job with specified arguments.
     *
     * @param args Job arguments.
     */
    protected GridJobAdapter(G... args) {
        if (args == null)
            this.args = new ArrayList<G>(1);
        else {
            this.args = new ArrayList<G>(args.length);

            this.args.addAll(Arrays.asList(args));
        }
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        if (p == null)
            p = U.detectPeerDeployAware(this);

        return p.deployClass();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        if (p == null)
            p = U.detectPeerDeployAware(this);

        return p.classLoader();
    }

    /** {@inheritDoc} */
    @Override public void cancel() {
        cancelled = true;
    }

    /**
     * This method tests whether or not this job was cancelled. This method
     * is thread-safe and can be called without extra synchronization.
     * <p>
     * This method can be periodically called in {@link GridJob#execute()} method
     * implementation to check whether or not this job cancelled. Note that system
     * calls {@link #cancel()} method only as a hint and this is a responsibility of
     * the implementation of the job to properly cancel its execution.
     *
     * @return {@code true} if this job was cancelled, {@code false} otherwise.
     */
    protected final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets an optional job argument at position <tt>0</tt>.
     *
     * @param arg Executable argument.
     */
    public void setArgument(G arg) {
        if (args.isEmpty())
            args.add(arg);
        else
            args.set(0, arg);
    }

    /**
     * Adds an optional job argument.
     *
     * @param arg Job argument.
     */
    public void addArgument(G arg) {
        args.add(arg);
    }

    /**
     * Gets job argument at position 0 or <tt>null</tt> if no argument was previously set.
     *
     * @return Job argument.
     */
    @Nullable public G getArgument() {
        return args.isEmpty() ? null : args.get(0);
    }

    /**
     * Gets argument at specified position.
     *
     * @param pos Position of the argument.
     * @return Argument at specified position.
     */
    public G getArgument(int pos) {
        A.ensure(pos < args.size(), "position < args.size()");

        return args.get(pos);
    }

    /**
     * Gets ordered list of all job arguments set so far. Note that
     * the same list as used internally is returned, so modifications
     * to it will affect the state of this job adapter instance.
     *
     * @return Ordered list of all job arguments.
     */
    public List<G> getAllArguments() {
        return args;
    }

    /** {@inheritDoc} */
    @Nullable @Override public final Object call() throws Exception {
        return execute();
    }
}
