// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.concurrent.*;

/**
 * Convenience adapter for {@link GridJob} implementations. It provides the
 * following functionality:
 * <ul>
 * <li>
 *      Default implementation of {@link GridJob#cancel()} method and ability
 *      to check whether cancellation occurred.
 * </li>
 * <li>
 *      Ability to set and get a job arguments via {@link #setArguments(Object...)}
 *      and {@link #getArgument(int)} methods.
 * </li>
 * </ul>
 * Here is an example of how {@code GridJobAdapterEx} can be used from task logic
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
 *             jobs.add(new GridJobAdapterEx() {
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
public abstract class GridJobAdapterEx extends GridLambdaAdapter implements GridJob, Callable<Object>,
    GridJobContinuation {
    /** Job argument. */
    private Object[] args;

    /** Cancellation flag. */
    private volatile boolean cancelled;

    /** Peer deploy aware class. */
    private transient volatile GridPeerDeployAware pda;

    /** Job context. */
    @GridJobContextResource
    private transient GridJobContext jobCtx;

    /**
     * No-arg constructor.
     */
    protected GridJobAdapterEx() {
        /* No-op. */
    }

    /**
     * Creates job with one arguments. This constructor exists for better
     * backward compatibility with internal GridGain 2.x code.
     *
     * @param arg Job argument.
     */
    protected GridJobAdapterEx(@Nullable Object arg) {
        args = new Object[]{arg};
    }

    /**
     * Creates job with specified arguments.
     *
     * @param args Optional job arguments.
     */
    protected GridJobAdapterEx(@Nullable Object... args) {
        this.args = args;
    }

    /** {@inheritDoc} */
    @Override public boolean heldcc() {
        return jobCtx != null && jobCtx.heldcc();
    }

    /** {@inheritDoc} */
    @Override public void callcc() {
        if (jobCtx != null)
            jobCtx.callcc();
    }

    /** {@inheritDoc} */
    @Nullable @Override public <T> T holdcc() {
        return jobCtx == null ? null : jobCtx.<T>holdcc();
    }

    /** {@inheritDoc} */
    @Nullable @Override public <T> T holdcc(long timeout) {
        return jobCtx == null ? null : jobCtx.<T>holdcc(timeout);
    }

    /**
     * Sets given arguments.
     *
     * @param args Optional job arguments to set.
     */
    public void setArguments(@Nullable Object... args) {
        this.args = args;
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
     * This method is deprecated in favor if {@link #argument(int)}.
     * <p>
     * Gets job argument.
     *
     * @param idx Index of the argument.
     * @param <T> Type of the argument to return.
     * @return Job argument.
     * @throws NullPointerException Thrown in case when there no arguments set.
     * @throws IllegalArgumentException Thrown if index is invalid.
     */
    @Deprecated @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable public <T> T getArgument(int idx) {
        return this.<T>argument(idx);
    }

    /**
     * Gets job argument.
     *
     * @param idx Index of the argument.
     * @param <T> Type of the argument to return.
     * @return Job argument.
     * @throws NullPointerException Thrown in case when there no arguments set.
     * @throws IllegalArgumentException Thrown if index is invalid.
     */
    @SuppressWarnings("unchecked")
    @Nullable public <T> T argument(int idx) {
        A.notNull(args, "args");
        A.ensure(idx >= 0 && idx < args.length, "idx >= 0 && idx < args.length");

        return (T)args[idx];
    }

    /**
     * This method is deprecated in favor of {@link #argument()}.
     * <p>
     * This is convenient method retain for better backward compatibility with {@link GridJobAdapter}
     * class. It returns first argument if there is only one set.
     *
     * @param <T> Type of the argument to return.
     * @return First argument if there is only one set.
     * @throws NullPointerException Thrown in case when there no arguments set.
     * @throws IllegalArgumentException Thrown in case there isn't one argument only.
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "deprecation"})
    @Nullable public <T> T getArgument() {
        A.notNull(args, "args");
        A.ensure(args.length == 1, "args.length == 1");

        return (T)args[0];
    }

    /**
     * This is convenient method retain for better backward compatibility with {@link GridJobAdapter}
     * class. It returns first argument if there is only one set.
     *
     * @param <T> Type of the argument to return.
     * @return First argument if there is only one set.
     * @throws NullPointerException Thrown in case when there no arguments set.
     * @throws IllegalArgumentException Thrown in case there isn't one argument only.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    @Nullable public <T> T argument() {
        A.notNull(args, "args");
        A.ensure(args.length == 1, "args.length == 1");

        return (T)args[0];
    }

    /** {@inheritDoc} */
    @Nullable @Override public final Object call() throws Exception {
        return execute();
    }

    /**
     * Sets peer deploy aware anchor object for this job.
     *
     * @param pda Peer deploy aware.
     */
    public void setPeerDeployAware(GridPeerDeployAware pda) {
        assert pda != null;

        this.pda = pda;
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        if (pda == null)
            pda = U.detectPeerDeployAware(this);

        return pda.deployClass();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        if (pda == null)
            pda = U.detectPeerDeployAware(this);

        return pda.classLoader();
    }
}
