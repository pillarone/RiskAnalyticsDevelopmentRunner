// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.closure;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"UnusedDeclaration"})
public class GridClosureProcessor extends GridProcessorAdapter {
    /** */
    private GridWorkerPool sysPool;

    /** */
    private GridWorkerPool pubPool;

    /**
     *
     * @param ctx Kernal context.
     */
    public GridClosureProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        sysPool = new GridWorkerPool(ctx.config().getSystemExecutorService(), log);
        pubPool = new GridWorkerPool(ctx.config().getExecutorService(), log);

        if (log.isDebugEnabled())
            log.debug("Started closure processor.");
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (sysPool != null)
            sysPool.join(true);

        if (pubPool != null)
            pubPool.join(true);

        if (log.isDebugEnabled())
            log.debug("Stopped closure processor.");
    }

    /**
     *
     * @param mode Distribution mode.
     * @param jobs Closures to execute.
     * @param nodes Grid nodes.
     * @return Task execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable Collection<? extends GridNode> nodes) throws GridException {
        return runAsync(mode, jobs, nodes, false);
    }

    /**
     *
     * @param mode Distribution mode.
     * @param jobs Closures to execute.
     * @param nodes Grid nodes.
     * @param sys If {@code true}, then system pool will be used.
     * @return Task execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable Collection<? extends GridNode> nodes, boolean sys) throws GridException {
        assert mode != null;

        if (F.isEmpty(jobs) || F.isEmpty(nodes))
            return new GridFinishedFuture(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T1(mode, jobs, nodes), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#runAsync(GridClosureCallMode, Collection, Collection)}.
     */
    private static class T1 extends GridTaskNoReduceAdapter<Void> {
        /** */
        @GridLoadBalancerResource
        private GridLoadBalancer lb;

        /** */
        private GridTuple3<GridClosureCallMode, Collection<? extends Runnable>,
            Collection<? extends GridNode>> t;

        /**
         *
         * @param mode Call mode.
         * @param jobs Collection of jobs.
         * @param nodes Collection of nodes.
         */
        private T1(GridClosureCallMode mode, Collection<? extends Runnable> jobs,
            Collection<? extends GridNode> nodes) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridClosureCallMode, Collection<? extends Runnable>,
                Collection<? extends GridNode>>t(mode, jobs, nodes);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg)
            throws GridException {
            return absMap(t.get1(), t.get2(), F.retain(t.get3(), true, subgrid), lb);
        }
    }

    /**
     *
     * @param mode Distribution mode.
     * @param job Closure to execute.
     * @param nodes Grid nodes.
     * @return Task execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable Collection<? extends GridNode> nodes) throws GridException {
        return runAsync(mode, job, nodes, false);
    }

    /**
     *
     * @param mode Distribution mode.
     * @param job Closure to execute.
     * @param nodes Grid nodes.
     * @param sys If {@code true}, then system pool will be used.
     * @return Task execution future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable Collection<? extends GridNode> nodes, boolean sys) throws GridException {
        assert mode != null;

        if (job == null || F.isEmpty(nodes))
            return new GridFinishedFuture(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T2(mode, job, nodes), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#runAsync(GridClosureCallMode, Runnable, Collection)}.
     */
    private static class T2 extends GridTaskNoReduceAdapter<Void> {
        /** */
        @GridLoadBalancerResource
        private GridLoadBalancer lb;

        /** */
        private GridTuple3<GridClosureCallMode, Runnable, Collection<? extends GridNode>> t;

        /**
         *
         * @param mode Call mode.
         * @param job Job.
         * @param nodes Collection of nodes.
         */
        private T2(GridClosureCallMode mode, Runnable job, Collection<? extends GridNode> nodes) {
            super(U.peerDeployAware(job));

            t = F.<GridClosureCallMode, Runnable, Collection<? extends GridNode>>t(mode, job, nodes);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg)
            throws GridException {
            return absMap(t.get1(), F.asList(t.get2()), F.retain(t.get3(), true, subgrid), lb);
        }
    }

    /**
     * Maps {@link Runnable} jobs to specified nodes based on distribution mode.
     *
     * @param mode Distribution mode.
     * @param jobs Closures to map.
     * @param nodes Grid nodes.
     * @param lb Load balancer.
     * @throws GridException Thrown in case of any errors.
     * @return Mapping.
     */
    private static Map<GridJob, GridNode> absMap(GridClosureCallMode mode, Collection<? extends Runnable> jobs,
        Collection<? extends GridNode> nodes, GridLoadBalancer lb) throws GridException {
        assert mode != null;
        assert jobs != null;
        assert nodes != null;
        assert lb != null;

        if (!F.isEmpty(jobs) && !F.isEmpty(nodes)) {
            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>(jobs.size(), 1);

            switch (mode) {
                case BROADCAST: {
                    for (GridNode n : nodes) {
                        for (Runnable r : jobs) {
                            map.put(new GridJobWrapper(F.job(r), true), n);
                        }
                    }

                    break;
                }

                case SPREAD: {
                    Iterator<? extends GridNode> n = nodes.iterator();

                    for (Runnable r : jobs) {
                        if (!n.hasNext()) {
                            n = nodes.iterator();
                        }

                        map.put(F.job(r), n.next());
                    }

                    break;
                }

                case BALANCE: {
                    for (Runnable r : jobs) {
                        GridJob job = F.job(r);

                        map.put(job, lb.getBalancedNode(job, null));
                    }

                    break;
                }

                case UNICAST: {
                    GridNode n = lb.getBalancedNode(F.job(F.rand(jobs)), null);

                    for (Runnable r : jobs) {
                        map.put(F.job(r), n);
                    }

                    break;
                }
            }

            return map;
        }
        else {
            return Collections.emptyMap();
        }
    }

    /**
     * Maps {@link Callable} jobs to specified nodes based on distribution mode.
     *
     * @param mode Distribution mode.
     * @param jobs Closures to map.
     * @param nodes Grid nodes.
     * @param lb Load balancer.
     * @throws GridException Thrown in case of any errors.
     * @return Mapping.
     */
    private static <R> Map<GridJob, GridNode> outMap(GridClosureCallMode mode, Collection<? extends Callable<R>> jobs,
        Collection<? extends GridNode> nodes, GridLoadBalancer lb) throws GridException {
        assert mode != null;
        assert jobs != null;
        assert nodes != null;
        assert lb != null;

        if (!F.isEmpty(jobs) && !F.isEmpty(nodes)) {
            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>(jobs.size(), 1);

            switch (mode) {
                case BROADCAST: {
                    for (GridNode n : nodes) {
                        for (Callable<R> c : jobs) {
                            map.put(new GridJobWrapper(F.job(c), true), n);
                        }
                    }

                    break;
                }

                case SPREAD: {
                    Iterator<? extends GridNode> n = nodes.iterator();

                    for (Callable<R> c : jobs) {
                        if (!n.hasNext()) {
                            n = nodes.iterator();
                        }

                        map.put(F.job(c), n.next());
                    }

                    break;
                }

                case UNICAST: {
                    GridNode n = lb.getBalancedNode(F.job(F.rand(jobs)), null);

                    for (Callable<R> c : jobs) {
                        map.put(F.job(c), n);
                    }

                    break;
                }

                case BALANCE: {
                    for (Callable<R> c : jobs) {
                        GridJob job = F.job(c);

                        map.put(job, lb.getBalancedNode(job, null));
                    }

                    break;
                }
            }

            return map;
        }
        else {
            return Collections.emptyMap();
        }
    }

    /**
     *
     * @param mode Distribution mode.
     * @param jobs Closures to execute.
     * @param rdc Reducer.
     * @param nodes Grid nodes.
     * @param <R1> Type.
     * @param <R2> Type.
     * @return Reduced result.
     * @throws GridException Thrown in case of any errors.
     */
    public <R1, R2> GridFuture<R2> forkjoinAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc, @Nullable Collection<? extends GridNode> nodes) throws GridException {
        assert mode != null;

        if (F.isEmpty(jobs) || rdc == null || F.isEmpty(nodes)) {
            return new GridFinishedFuture<R2>(ctx);
        }

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T3<R1, R2>(mode, jobs, rdc, nodes), null, 0, null);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#forkjoinAsync(GridClosureCallMode, Collection, GridReducer, Collection)}
     */
    private static class T3<R1, R2> extends GridTaskAdapter<Void, R2> {
        /** */
        @GridLoadBalancerResource
        private GridLoadBalancer lb;

        /** */
        private GridTuple4<GridClosureCallMode, Collection<? extends Callable<R1>>, GridReducer<R1, R2>,
            Collection<? extends GridNode>> t;

        /**
         *
         * @param mode Call mode.
         * @param jobs Collection of jobs.
         * @param rdc Reducer.
         * @param nodes Collection of nodes.
         */
        private T3(GridClosureCallMode mode, Collection<? extends Callable<R1>> jobs, GridReducer<R1, R2> rdc,
            Collection<? extends GridNode> nodes) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridClosureCallMode, Collection<? extends Callable<R1>>, GridReducer<R1, R2>,
                Collection<? extends GridNode>>t(mode, jobs, rdc, nodes);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg)
            throws GridException {
            return outMap(t.get1(), t.get2(), F.retain(t.get4(), true, subgrid), lb);
        }

        /** {@inheritDoc} */
        @Override public R2 reduce(List<GridJobResult> res) {
            GridReducer<R1, R2> rdc = t.get3();

            for (R1 r : F.<R1>jobResults(res)) {
                rdc.collect(r);
            }

            return rdc.apply();
        }
    }

    /**
     * @param mapper Mapper.
     * @param jobs Closures to execute.
     * @return Grid future.
     * @param nodes Grid nodes.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable Collection<? extends GridNode> nodes)
        throws GridException {
        return runAsync(mapper, jobs, nodes, false);
    }

    /**
     * @param mapper Mapper.
     * @param jobs Closures to execute.
     * @param sys If {@code true}, then system pool will be used.
     * @return Grid future.
     * @param nodes Grid nodes.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runAsync(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable Collection<? extends GridNode> nodes, boolean sys)
        throws GridException {
        if (mapper == null || F.isEmpty(jobs) || F.isEmpty(nodes))
            return new GridFinishedFuture(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T4(mapper, jobs, nodes, ctx), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#runAsync(GridMapper, Collection, Collection)}
     */
    private static class T4 extends GridTaskNoReduceAdapter<Void> {
        /** */
        private GridTuple4<GridMapper<Runnable, GridRichNode>, Collection<? extends Runnable>,
            Collection<? extends GridNode>, GridKernalContext> t;

        /**
         *
         * @param mapper Mapper.
         * @param jobs Collection of jobs.
         * @param nodes Collection of nodes.
         * @param ctx Kernal context.
         */
        private T4(GridMapper<Runnable, GridRichNode> mapper,
            Collection<? extends Runnable> jobs, Collection<? extends GridNode> nodes, GridKernalContext ctx) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridMapper<Runnable, GridRichNode>, Collection<? extends Runnable>,
                Collection<? extends GridNode>, GridKernalContext>t(mapper, jobs, nodes, ctx);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg) {
            t.get1().collect(F.viewReadOnly(F.retain(t.get3(), true, subgrid), t.get4().rich().richNode()));

            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>(t.get2().size(), 1);

            for (Runnable r : t.get2()) {
                map.put(F.job(r), t.get1().apply(r));
            }

            return map;
        }
    }

    /**
     * @param mapper Mapper.
     * @param jobs Closures to execute.
     * @return Grid future.
     * @param nodes Grid nodes.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<Collection<R>> callAsync(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable Collection<? extends GridNode> nodes)
        throws GridException {
        return callAsync(mapper, jobs, nodes, false);
    }

    /**
     * @param mapper Mapper.
     * @param jobs Closures to execute.
     * @return Grid future.
     * @param nodes Grid nodes.
     * @param sys If {@code true}, then system pool will be used.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<Collection<R>> callAsync(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable Collection<? extends GridNode> nodes,
        boolean sys)
        throws GridException {
        if (mapper == null || F.isEmpty(jobs) || F.isEmpty(nodes))
            return new GridFinishedFuture<Collection<R>>(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T5<R>(mapper, jobs, nodes, ctx), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#callAsync(GridMapper, Collection, Collection)}
     */
    private static class T5<R> extends GridTaskAdapter<Void, Collection<R>> {
        /** */
        private GridTuple4<GridMapper<Callable<R>, GridRichNode>, Collection<? extends Callable<R>>,
            Collection<? extends GridNode>, GridKernalContext> t;

        /**
         *
         * @param mapper Mapper.
         * @param jobs Collection of jobs.
         * @param nodes Collection of nodes.
         * @param ctx Kernal context.
         */
        private T5(GridMapper<Callable<R>, GridRichNode> mapper,
            Collection<? extends Callable<R>> jobs, Collection<? extends GridNode> nodes, GridKernalContext ctx) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridMapper<Callable<R>, GridRichNode>, Collection<? extends Callable<R>>,
                Collection<? extends GridNode>, GridKernalContext>t(mapper, jobs, nodes, ctx);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg) {
            t.get1().collect(F.viewReadOnly(F.retain(t.get3(), true, subgrid), t.get4().rich().richNode()));

            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>(t.get2().size(), 1);

            for (Callable<R> c : t.get2()) {
                map.put(F.job(c), t.get1().apply(c));
            }

            return map;
        }

        /** {@inheritDoc} */
        @Override public Collection<R> reduce(List<GridJobResult> res) {
            return F.jobResults(res);
        }
    }

    /**
     *
     * @param mapper Mapper.
     * @param jobs Closures to execute.
     * @param rdc Reducer.
     * @param nodes Grid nodes.
     * @param <R1> Type.
     * @param <R2> Type.
     * @param <C> Any subclass or {@code Callable<R1>}.
     * @return Reduced result future.
     * @throws GridException Thrown in case of any errors.
     */
    public <R1, R2, C extends Callable<R1>> GridFuture<R2> forkjoinAsync(@Nullable GridMapper<C, GridRichNode> mapper,
        @Nullable Collection<C> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable Collection<? extends GridNode> nodes) throws GridException {
        if (mapper == null || F.isEmpty(jobs) || rdc == null || F.isEmpty(nodes)) {
            return new GridFinishedFuture<R2>(ctx);
        }

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T6<R1, R2, C>(mapper, jobs, rdc, nodes, ctx), null, 0, null);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#forkjoinAsync(GridMapper, Collection, GridReducer, Collection)}
     */
    private static class T6<R1, R2, C extends Callable<R1>> extends GridTaskAdapter<Void, R2> {
        /** */
        private GridTuple5<GridMapper<C, GridRichNode>, Collection<C>,
            GridReducer<R1, R2>, Collection<? extends GridNode>, GridKernalContext> t;

        /**
         *
         * @param mapper Mapper.
         * @param jobs Collection of jobs.
         * @param rdc Reducer.
         * @param nodes Collection of nodes.
         * @param ctx Kernal context.
         */
        private T6(GridMapper<C, GridRichNode> mapper, Collection<C> jobs, GridReducer<R1, R2> rdc,
            Collection<? extends GridNode> nodes, GridKernalContext ctx) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridMapper<C, GridRichNode>, Collection<C>,
                GridReducer<R1, R2>, Collection<? extends GridNode>, GridKernalContext>t(mapper, jobs, rdc, nodes, ctx);
        }

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg) {
            t.get1().collect(F.viewReadOnly(F.retain(t.get4(), true, subgrid), t.get5().rich().richNode()));

            Map<GridJob, GridNode> map = new HashMap<GridJob, GridNode>(t.get2().size(), 1);

            for (C c : t.get2()) {
                map.put(F.job(c), t.get1().apply(c));
            }

            return map;
        }

        /** {@inheritDoc} */
        @Override public R2 reduce(List<GridJobResult> res) {
            for (R1 r : F.<R1>jobResults(res)) {
                t.get3().collect(r);
            }

            return t.get3().apply();
        }
    }

    /**
     *
     * @param mode Distribution mode.
     * @param jobs Closures to execute.
     * @param nodes Grid nodes.
     * @param <R> Type.
     * @return Grid future for collection of closure results.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable Collection<? extends GridNode> nodes)
        throws GridException {
        return callAsync(mode, jobs, nodes, false);
    }

    /**
     *
     * @param mode Distribution mode.
     * @param jobs Closures to execute.
     * @param nodes Grid nodes.
     * @param sys If {@code true}, then system pool will be used.
     * @param <R> Type.
     * @return Grid future for collection of closure results.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable Collection<? extends GridNode> nodes,
        boolean sys)
        throws GridException {
        assert mode != null;

        if (F.isEmpty(jobs) || F.isEmpty(nodes))
            return new GridFinishedFuture<Collection<R>>(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T7<R>(mode, jobs, nodes), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#callAsync(GridClosureCallMode, Collection, Collection)}
     */
    private static class T7<R> extends GridTaskAdapter<Void, Collection<R>> {
        /** */
        private GridTuple3<GridClosureCallMode, Collection<? extends Callable<R>>,
            Collection<? extends GridNode>> t;

        /**
         *
         * @param mode Call mode.
         * @param jobs Collection of jobs.
         * @param nodes Collection of nodes.
         */
        private T7(GridClosureCallMode mode, Collection<? extends Callable<R>> jobs,
            Collection<? extends GridNode> nodes) {
            super(U.peerDeployAware0(jobs));

            t = F.<GridClosureCallMode, Collection<? extends Callable<R>>,
                Collection<? extends GridNode>>t(mode, jobs, nodes);
        }

        /** */
        @GridLoadBalancerResource
        private GridLoadBalancer lb;

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg)
            throws GridException {
            return outMap(t.get1(), t.get2(), F.retain(t.get3(), true, subgrid), lb);
        }

        /** {@inheritDoc} */
        @Override public Collection<R> reduce(List<GridJobResult> res) {
            return F.jobResults(res);
        }
    }

    /**
     *
     * @param mode Distribution mode.
     * @param job Closure to execute.
     * @param nodes Grid nodes.
     * @param <R> Type.
     * @return Grid future for collection of closure results.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<R> callAsync(GridClosureCallMode mode,
        @Nullable Callable<R> job, @Nullable Collection<? extends GridNode> nodes) throws GridException {
        return callAsync(mode, job, nodes, false);
    }

    /**
     *
     * @param mode Distribution mode.
     * @param job Closure to execute.
     * @param nodes Grid nodes.
     * @param sys If {@code true}, then system pool will be used.
     * @param <R> Type.
     * @return Grid future for collection of closure results.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<R> callAsync(GridClosureCallMode mode,
        @Nullable Callable<R> job, @Nullable Collection<? extends GridNode> nodes, boolean sys) throws GridException {
        assert mode != null;

        if (job == null || F.isEmpty(nodes))
            return new GridFinishedFuture<R>(ctx);

        ctx.task().setProjectionContext(nodes);

        return ctx.task().execute(new T8<R>(mode, job, nodes), null, 0, null, sys);
    }

    /**
     * Task that is free of dragged in enclosing context for the method
     * {@link GridClosureProcessor#callAsync(GridClosureCallMode, Callable, Collection)}
     */
    private static class T8<R> extends GridTaskAdapter<Void, R> {
        /** */
        private GridTuple3<GridClosureCallMode, Callable<R>, Collection<? extends GridNode>> t;

        /**
         *
         * @param mode Call mode.
         * @param job Job.
         * @param nodes Collection of nodes.
         */
        private T8(GridClosureCallMode mode, Callable<R> job, Collection<? extends GridNode> nodes) {
            super(U.peerDeployAware(job));

            t = F.<GridClosureCallMode, Callable<R>, Collection<? extends GridNode>>t(mode, job, nodes);
        }

        /** */
        @GridLoadBalancerResource
        private GridLoadBalancer lb;

        /** {@inheritDoc} */
        @Override public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable Void arg)
            throws GridException {
            return outMap(t.get1(), F.asList(t.get2()), F.retain(t.get3(), true, subgrid), lb);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"RedundantTypeArguments", "ConstantConditions"})
        @Override public R reduce(List<GridJobResult> res) {
            return F.first(res).<R>getData();
        }
    }

    /**
     * Gets either system or public pool.
     *
     * @param sys Whether to get system or public pool.
     * @return Requested worker pool.
     */
    private GridWorkerPool pool(boolean sys) {
        return sys ? sysPool : pubPool;
    }

    /**
     * Executes closure on public pool.
     *
     * @param c Closure to execute.
     * @return Future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runLocal(Runnable c) throws GridException {
        return runLocal(c, false);
    }

    /**
     * Future for locally executed closure that defines cancellation logic.
     */
    private static class LocalExecutionFuture<T> extends GridFutureAdapter<T> {
        /** */
        private GridWorker w;

        /**
         * @param ctx Context.
         */
        LocalExecutionFuture(GridKernalContext ctx) {
            super(ctx);
        }

        /**
         * Empty constructor required for {@link java.io.Externalizable}.
         */
        public LocalExecutionFuture() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public boolean cancel() throws GridException {
            assert w != null;

            if (!onCancelled()) {
                return false;
            }

            w.cancel();

            return true;
        }

        /**
         * @param w Worker.
         */
        public void setWorker(GridWorker w) {
            assert w != null;

            this.w = w;
        }
    }

    /**
     * @param c Closure to execute.
     * @param sys Whether to run on system or public pool.
     * @return Future.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<?> runLocal(@Nullable final Runnable c, boolean sys) throws GridException {
        if (c == null)
            return new GridFinishedFuture(ctx);

        // Inject only if needed.
        if (!(c instanceof GridPlainRunnable))
            ctx.resource().inject(ctx.deploy().getDeployment(c.getClass().getName()), c.getClass(), c);

        final LocalExecutionFuture fut = new LocalExecutionFuture(ctx);

        GridWorker w = new GridWorker(ctx.config().getGridName(), "closure-proc-worker", log) {
            @SuppressWarnings({"ConstantConditions"})
            @Override protected void body() {
                try {
                    c.run();

                    fut.onDone();
                }
                catch (Throwable e) {
                    if (e instanceof Error)
                        U.error(log, "Closure execution failed with error.", e);

                    fut.onDone(U.cast(e));
                }
            }
        };

        fut.setWorker(w);

        pool(sys).execute(w);

        return fut;
    }

    /**
     * Executes closure on public pool. Companion to {@link #runLocal(Runnable, boolean)} but
     * in case of rejected execution re-runs the closure in the current thread (blocking).
     *
     * @param c Closure to execute.
     * @return Future.
     */
    public GridFuture<?> runLocalSafe(Runnable c) {
        return runLocalSafe(c, false);
    }

    /**
     * Companion to {@link #runLocal(Runnable, boolean)} but in case of rejected execution re-runs
     * the closure in the current thread (blocking).
     *
     * @param c Closure to execute.
     * @param sys Whether to run on system or public pool.
     * @return Future.
     */
    @SuppressWarnings({"ConstantConditions"})
    public GridFuture<?> runLocalSafe(Runnable c, boolean sys) {
        try {
            return runLocal(c, sys);
        }
        catch (Throwable e) {
            if (e instanceof Error)
                U.error(log, "Closure execution failed with error.", e);

            // If execution was rejected - rerun locally.
            if (e.getCause() instanceof RejectedExecutionException) {
                try {
                    c.run();

                    return new GridFinishedFuture(ctx);
                }
                catch (Throwable t) {
                    if (t instanceof Error)
                        U.error(log, "Closure execution failed with error.", t);

                    return new GridFinishedFuture(ctx, U.cast(t));
                }
            }
            // If failed for other reasons - return error future.
            else
                return new GridFinishedFuture(ctx, U.cast(e));
        }
    }

    /**
     * Executes closure on public pool.
     *
     * @param c Closure to execute.
     * @param <R> Type of closure return value.
     * @return Future.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<R> callLocal(Callable<R> c) throws GridException {
        return callLocal(c, false);
    }

    /**
     * @param c Closure to execute.
     * @param sys Whether to run on system or public pool.
     * @param <R> Type of closure return value.
     * @return Future.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridFuture<R> callLocal(@Nullable final Callable<R> c, boolean sys) throws GridException {
        if (c == null)
            return new GridFinishedFuture<R>(ctx);

        // Inject only if needed.
        if (!(c instanceof GridPlainCallable))
            ctx.resource().inject(ctx.deploy().getDeployment(c.getClass().getName()), c.getClass(), c);

        final LocalExecutionFuture<R> fut = new LocalExecutionFuture<R>(ctx);

        GridWorker w = new GridWorker(ctx.config().getGridName(), "closure-proc-worker", log) {
            @Override protected void body() {
                try {
                    fut.onDone(c.call());
                }
                catch (Throwable e) {
                    if (e instanceof Error)
                        U.error(log, "Closure execution failed with error.", e);

                    fut.onDone(U.cast(e));
                }
            }
        };

        fut.setWorker(w);

        pool(sys).execute(w);

        return fut;
    }

    /**
     * Executes closure on public pool. Companion to {@link #callLocal(Callable, boolean)}
     * but in case of rejected execution re-runs the closure in the current thread (blocking).
     *
     * @param c Closure to execute.
     * @return Future.
     */
    public <R> GridFuture<R> callLocalSafe(Callable<R> c) {
        return callLocalSafe(c, false);
    }

    /**
     * Companion to {@link #callLocal(Callable, boolean)} but in case of rejected execution re-runs
     * the closure in the current thread (blocking).
     *
     * @param c Closure to execute.
     * @param sys Whether to run on system or public pool.
     * @return Future.
     */
    public <R> GridFuture<R> callLocalSafe(Callable<R> c, boolean sys) {
        try {
            return callLocal(c, sys);
        }
        catch (GridException e) {
            // If execution was rejected - rerun locally.
            if (e.getCause() instanceof RejectedExecutionException) {
                try {
                    return new GridFinishedFuture<R>(ctx, c.call());
                }
                // If failed again locally - return error future.
                catch (Exception e2) {
                    return new GridFinishedFuture<R>(ctx, U.cast(e2));
                }
            }
            // If failed for other reasons - return error future.
            else
                return new GridFinishedFuture<R>(ctx, U.cast(e));
        }
    }
}
