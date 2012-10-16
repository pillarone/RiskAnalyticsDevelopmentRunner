// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.pimps

import org.gridgain.grid._
import org.gridgain.grid.lang._
import org.gridgain.scalar._
import scalar._
import org.jetbrains.annotations.Nullable

/**
 * ==Overview==
 * Defines Scalar "pimp" for `GridProjection` on Java side.
 *
 * Essentially this class extends Java `GridProjection` interface with Scala specific
 * API adapters using primarily implicit conversions defined in `ScalarMixin` object. What
 * it means is that you can use functions defined in this class on object
 * of Java `GridProjection` type. Scala will automatically (implicitly) convert it into
 * Scalar's pimp and replace the original call with a call on that pimp.
 *
 * Note that Scalar provide extensive library of implicit conversion between Java and
 * Scala GridGain counterparts in `ScalarMixin` object
 *
 * ==Suffix '$' In Names==
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Scala pimp is shadowing or with Java package name that your Scala code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Scala's side method with `$` suffix.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class ScalarProjectionPimp(private val impl: GridProjection) extends Iterable[GridRichNode] {
    if (impl == null)
        throw new GridException("Implementation is set to 'null'.")

    /** Type alias for '() => Unit'. */
    protected type Run = () => Unit

    /** Type alias for '() => R'. */
    protected type Call[R] = () => R

    /** Type alias for '(E1) => R'. */
    protected type Call1[E1, R] = (E1) => R

    /** Type alias for '(E1, E2) => R'. */
    protected type Call2[E1, E2, R] = (E1, E2) => R

    /** Type alias for '(E1, E2, E3) => R'. */
    protected type Call3[E1, E2, E3, R] = (E1, E2, E3) => R

    /** Type alias for '() => Boolean'. */
    protected type Pred = () => Boolean

    /** Type alias for '(E1) => Boolean'. */
    protected type Pred1[E1] = (E1) => Boolean

    /** Type alias for '(E1, E2) => Boolean'. */
    protected type Pred2[E1, E2] = (E1, E2) => Boolean

    /** Type alias for '(E1, E2, E3) => Boolean'. */
    protected type Pred3[E1, E2, E3] = (E1, E2, E3) => Boolean

    /** Type alias for node filter predicate. */
    protected type NodeFilter = GridPredicate[_ >: GridRichNode]

    /**
     * Gets iterator for this projection's nodes.
     */
    def iterator = nodes$().iterator

    /**
     * Gets sequence of all nodes in this projection for given predicate.
     *
     * @param p Optional node filter predicates. It none provided or `null` -
     *      all nodes will be returned.
     * @see `org.gridgain.grid.GridProjection.nodes(...)`
     */
    def nodes$(@Nullable p: NodeFilter*): Seq[GridRichNode] =
        toScalaSeq(impl.nodes(p: _*))

    /**
     * Gets sequence of all remote nodes in this projection for given predicate.
     *
     * @param p Optional node filter predicates. It none provided or `null` -
     *      all remote nodes will be returned.
     * @see `org.gridgain.grid.GridProjection.remoteNodes(...)`
     */
    def remoteNodes$(@Nullable p: NodeFilter*): Seq[GridRichNode] =
        toScalaSeq(impl.remoteNodes(p: _*))

    /**
     * <b>Alias</b> for method `send$(...)`.
     *
     * @param obj Optional object to send. If `null` - this method is no-op.
     * @param p Optional node filter predicates. If none provided or `null` -
     *      all nodes in the projection will be used.
     * @see `org.gridgain.grid.GridProjection.send(...)`
     */
    def !<(@Nullable obj: AnyRef, @Nullable p: NodeFilter*) {
        impl.send(obj, p: _*)
    }

    /**
     * <b>Alias</b> for method `send$(...)`.
     *
     * @param seq Optional sequence of objects to send. If empty or `null` - this
     *      method is no-op.
     * @param p Optional node filter predicates. If none provided or `null` -
     *      all nodes in the projection will be used.
     * @see `org.gridgain.grid.GridProjection.send(...)`
     */
    def !<(@Nullable seq: Seq[AnyRef], @Nullable p: NodeFilter*) {
        impl.send(seq, p: _*)
    }

    /**
     * Sends given object to the nodes in this projection.
     *
     * @param obj Optional object to send. If `null` - this method is no-op.
     * @param p Optional node filter predicates. If none provided or `null` -
     *      all nodes in the projection will be used.
     * @see `org.gridgain.grid.GridProjection.send(...)`
     */
    def send$(@Nullable obj: AnyRef, @Nullable p: NodeFilter*) {
        impl.send(obj, p: _*)
    }

    /**
     * Sends given object to the nodes in this projection.
     *
     * @param seq Optional sequence of objects to send. If empty or `null` - this
     *      method is no-op.
     * @param p Optional node filter predicates. If none provided or `null` -
     *      all nodes in the projection will be used.
     * @see `org.gridgain.grid.GridProjection.send(...)`
     */
    def send$(@Nullable seq: Seq[AnyRef], @Nullable p: NodeFilter*) {
        impl.send(seq, p: _*)
    }

    /**
     * Synchronous closures call on this projection with return value.
     * This call will block until all results are received and ready.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this
     *      method is no-op and returns `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or `null` (see above).
     */
    def call$[R](mode: GridClosureCallMode, @Nullable s: Seq[Call[R]], @Nullable p: NodeFilter*): Seq[R] =
        toScalaSeq(callAsync$(mode, s, p: _*).get)

    /**
     * <b>Alias</b> for the same function `call$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this
     *      method is no-op and returns `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def #<[R](mode: GridClosureCallMode, @Nullable s: Seq[Call[R]], @Nullable p: NodeFilter*): Seq[R] =
        call$(mode, s, p: _*)

    /**
     * Synchronous closure call on this projection with return value.
     * This call will block until all results are received and ready.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If `null` - this method is no-op and returns `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def call$[R](mode: GridClosureCallMode, @Nullable s: Call[R], @Nullable p: NodeFilter*): Seq[R] =
        call$(mode, Seq(s), p: _*)

    /**
     * <b>Alias</b> for the same function `call$`.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If `null` - this method is no-op and returns `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Sequence of result values from all nodes where given closures were executed
     *      or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def #<[R](mode: GridClosureCallMode, @Nullable s: Call[R], @Nullable p: NodeFilter*): Seq[R] =
        call$(mode, s, p: _*)

    /**
     * Synchronous closures call on this projection without return value.
     * This call will block until all executions are complete.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this
     *      method is no-op.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def run$(mode: GridClosureCallMode, @Nullable s: Seq[Call[Unit]], @Nullable p: NodeFilter*) {
        runAsync$(mode, s, p: _*).get
    }

    /**
     * <b>Alias</b> alias for the same function `run$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this
     *      method is no-op.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def *<(mode: GridClosureCallMode, @Nullable s: Seq[Call[Unit]], @Nullable p: NodeFilter*) {
        run$(mode, s, p: _*)
    }

    /**
     * Synchronous closure call on this projection without return value.
     * This call will block until all executions are complete.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If empty or `null` - this method is no-op.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def run$(mode: GridClosureCallMode, @Nullable s: Call[Unit], @Nullable p: NodeFilter*) {
        run$(mode, Seq(s), p: _*)
    }

    /**
     * <b>Alias</b> for the same function `run$`.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If empty or `null` - this method is no-op.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def *<(mode: GridClosureCallMode, @Nullable s: Call[Unit], @Nullable p: NodeFilter*) {
        run$(mode, s, p: _*)
    }

    /**
     * Asynchronous closures call on this projection with return value. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and finished future over `null` is returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future of Java collection containing result values from all nodes where given
     *      closures were executed or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def callAsync$[R](mode: GridClosureCallMode, @Nullable s: Seq[Call[R]], @Nullable p: NodeFilter*):
        GridFuture[java.util.Collection[R]] = {
        assert(mode != null)

        impl.callAsync[R](mode, toJavaCollection(s, (f: Call[R]) => toOutClosure(f)), p: _*)
    }

    /**
     * <b>Alias</b> for the same function `callAsync$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and finished future over `null` is returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future of Java collection containing result values from all nodes where given
     *      closures were executed or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def #?[R](mode: GridClosureCallMode, @Nullable s: Seq[Call[R]], @Nullable p: NodeFilter*):
        GridFuture[java.util.Collection[R]] = {
        callAsync$(mode, s, p: _*)
    }

    /**
     * Asynchronous closure call on this projection with return value. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If `null` - this method is no-op and finished
     *      future over `null` is returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future of Java collection containing result values from all nodes where given
     *      closures were executed or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def callAsync$[R](mode: GridClosureCallMode, @Nullable s: Call[R], @Nullable p: NodeFilter*):
        GridFuture[java.util.Collection[R]] = {
        callAsync$(mode, Seq(s), p: _*)
    }

    /**
     * <b>Alias</b> for the same function `callAsync$`.
     *
     * @param mode Closure call mode.
     * @param s Optional closure to call. If `null` - this method is no-op and finished
     *      future over `null` is returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future of Java collection containing result values from all nodes where given
     *      closures were executed or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def #?[R](mode: GridClosureCallMode, @Nullable s: Call[R], @Nullable p: NodeFilter*):
        GridFuture[java.util.Collection[R]] = {
        callAsync$(mode, s, p: _*)
    }

    /**
     * Asynchronous closures call on this projection without return value. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of absolute closures to call. If empty or `null` - this method
     *      is no-op and finished future over `null` will be returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def runAsync$(mode: GridClosureCallMode, @Nullable s: Seq[Call[Unit]], @Nullable p: NodeFilter*):
        GridFuture[_] = {
        assert(mode != null)

        impl.runAsync(mode, toJavaCollection(s, (f: Call[Unit]) => toAbsClosure(f)), p: _*)
    }

    /**
     * <b>Alias</b> for the same function `runAsync$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of absolute closures to call. If empty or `null` - this method
     *      is no-op and finished future over `null` will be returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.call(...)`
     */
    def *?(mode: GridClosureCallMode, @Nullable s: Seq[Call[Unit]], @Nullable p: NodeFilter*):
        GridFuture[_] = {
        runAsync$(mode, s, p: _*)
    }

    /**
     * Asynchronous closure call on this projection without return value. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional absolute closure to call. If `null` - this method
     *      is no-op and finished future over `null` will be returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def runAsync$(mode: GridClosureCallMode, @Nullable s: Call[Unit], @Nullable p: NodeFilter*):
        GridFuture[_] = {
        runAsync$(mode, Seq(s), p: _*)
    }

    /**
     * <b>Alias</b> for the same function `runAsync$`.
     *
     * @param mode Closure call mode.
     * @param s Optional absolute closure to call. If `null` - this method
     *      is no-op and finished future over `null` will be returned.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @see `org.gridgain.grid.GridProjection.run(...)`
     */
    def *?(mode: GridClosureCallMode, @Nullable s: Call[Unit], @Nullable p: NodeFilter*):
        GridFuture[_] = {
        runAsync$(mode, s, p: _*)
    }

    /**
     * Asynchronous closures execution on this projection with reduction. This call will
     * return immediately with the future that can be used to wait asynchronously for the results.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return finished future over `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return finished future over `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future over the reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.reduce(...)`
     */
    def reduceAsync$[R1, R2](mode: GridClosureCallMode, s: Seq[Call[R1]], r: Seq[R1] => R2,
        @Nullable p: NodeFilter*): GridFuture[R2] = {
        assert(mode != null && s != null && r != null)

        impl.reduceAsync(mode, toJavaCollection(s, (f: Call[R1]) => toOutClosure(f)), r, p: _*)
    }

    /**
     * <b>Alias</b> for the same function `reduceAsync$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return finished future over `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return finished future over `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future over the reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.reduce(...)`
     */
    def @?[R1, R2](mode: GridClosureCallMode, s: Seq[Call[R1]], r: Seq[R1] => R2,
        @Nullable p: NodeFilter*): GridFuture[R2] = {
        reduceAsync$(mode, s, r, p: _*)
    }

    /**
     * Synchronous closures execution on this projection with reduction.
     * This call will block until all results are reduced.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.reduce(...)`
     */
    def reduce$[R1, R2](mode: GridClosureCallMode, @Nullable s: Seq[Call[R1]], @Nullable r: Seq[R1] => R2,
        @Nullable p: NodeFilter*): R2 =
        reduceAsync$(mode, s, r, p: _*).get

    /**
     * <b>Alias</b> for the same function `reduce$`.
     *
     * @param mode Closure call mode.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.reduce(...)`
     */
    def @<[R1, R2](mode: GridClosureCallMode, @Nullable s: Seq[Call[R1]], @Nullable r: Seq[R1] => R2,
        @Nullable p: NodeFilter*): R2 =
        reduceAsync$(mode, s, r, p: _*).get

    /**
     * Asynchronous closures execution on this projection with mapping and reduction.
     * This call will return immediately with the future that can be used to wait asynchronously for
     * the results.
     *
     * @param m Mapping function.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Future over the reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.mapreduce(...)`
     */
    def mapreduceAsync$[R1, R2](m: Seq[GridRichNode] => (java.util.concurrent.Callable[R1] => GridRichNode),
        @Nullable s: Seq[Call[R1]], @Nullable r: Seq[R1] => R2, @Nullable p: NodeFilter*): GridFuture[R2] = {
        assert(m != null)

        impl.mapreduceAsync(toMapper(m), toJavaCollection[Call[R1], java.util.concurrent.Callable[R1]](s,
            (f: Call[R1]) => toOutClosure(f)), toReducer(r), p: _*)
    }

    /**
     * Synchronous closures execution on this projection with mapping and reduction.
     * This call will block until all results are reduced.
     *
     * @param m Mapping function.
     * @param s Optional sequence of closures to call. If empty or `null` - this method
     *      is no-op and will return `null`.
     * @param r Optional reduction function. If `null` - this method
     *      is no-op and will return `null`.
     * @param p Optional node filter predicate. If none provided or `null` - all
     *      nodes in projection will be used.
     * @return Reduced result or `null` (see above).
     * @see `org.gridgain.grid.GridProjection.mapreduce(...)`
     */
    def mapreduce$[R1, R2](m: Seq[GridRichNode] => (java.util.concurrent.Callable[R1] => GridRichNode),
        @Nullable s: Seq[Call[R1]], @Nullable r: Seq[R1] => R2, @Nullable p: NodeFilter*): R2 =
        mapreduceAsync$(m, s, r, p: _*).get

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using given closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed-on-this-projection version of the given closure.
     * @see `org.gridgain.grid.GridProjection.gridify(...)`
     */
    def gridify$[R](mode: GridClosureCallMode, c: Call[R], @Nullable p: NodeFilter*): Call[GridFuture[R]] =
        impl.gridify(mode, toOutClosure(c), p: _*)

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using given closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed-on-this-projection version of the given closure.
     * @see `org.gridgain.grid.GridProjection.gridify(...)`
     */
    def gridify$[E1, R](mode: GridClosureCallMode, c: Call1[E1, R], @Nullable p: NodeFilter*):
        Call1[E1, GridFuture[R]] =
        impl.gridify(mode, toClosure(c), p: _*)

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using given closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed-on-this-projection version of the given closure.
     * @see `org.gridgain.grid.GridProjection.gridify(...)`
     */
    def gridify$[E1, E2, R](mode: GridClosureCallMode, c: Call2[E1, E2, R], @Nullable p: NodeFilter*):
        Call2[E1, E2, GridFuture[R]] =
        impl.gridify(mode, toClosure2(c), p: _*)

    /**
     * Curries given closure into distribution version of it. When resulting closure is
     * called it will return future without blocking and execute given closure asynchronously
     * on this projection using given closure call mode.
     * <p>
     * This method effectively allows to convert "local" closure into a distributed one
     * that will take the same parameters (if any), execute "somewhere" on this projection,
     * and produce the same result but via future.
     *
     * @param mode Closure call mode with to curry given closure.
     * @param c Closure to convert.
     * @param p Optional set of filtering predicates. If none provided - all nodes from this
     *      projection will be candidates for load balancing.
     * @return Distributed-on-this-projection version of the given closure.
     * @see `org.gridgain.grid.GridProjection.gridify(...)`
     */
    def gridify$[E1, E2, E3, R](mode: GridClosureCallMode, c: Call3[E1, E2, E3, R], @Nullable p: NodeFilter*):
        Call3[E1, E2, E3, GridFuture[R]] =
        impl.gridify(mode, toClosure3(c), p: _*)
}