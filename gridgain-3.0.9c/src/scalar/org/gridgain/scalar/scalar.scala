// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */
 
package org.gridgain.scalar

import java.net.URL
import org.springframework.context.ApplicationContext
import org.jetbrains.annotations.Nullable
import java.util.UUID
import org.gridgain.grid._
import org.gridgain.grid.cache._
import org.gridgain.grid.{GridFactory => G}

/**
 * {{{
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 * }}}
 * 
 * ==Overview==
 * `scalar` is the main object that encapsulates Scalar DSL. It includes global functions
 * on "scalar" keyword, helper converters as well as necessary implicit conversions. `scalar` also
 * mimics many methods in `GridFactory` class from Java side.
 *
 * The idea behind Scalar DSL - '''zero additional logic and only conversions''' implemented
 * using Scala "Pimp" pattern. Note that most of the Scalar DSL development happened on Java
 * side of GridGain 3.0 product line - Java APIs had to be adjusted quite significantly to
 * support natural adaptation of functional APIs. That basically means that all functional
 * logic must be available on Java side and Scalar only provides conversions from Scala
 * language constructs to Java constructs. Note that currently GridGain supports Scala 2.8
 * and up only.
 *
 * This design approach ensures that Java side does not starve and usage paradigm
 * is mostly the same between Java and Scala - yet with full power of Scala behind.
 * In other words, Scalar only adds Scala specifics, but not greatly altering semantics
 * of how GridGain APIs work. Most of the time the code in Scalar can be written in
 * Java in almost the same number of lines.
 *
 * ==Suffix '$' In Names==
 * Symbol `$` is used in names when they conflict with the names in the base Java class
 * that Scala pimp is shadowing or with Java package name that your Scala code is importing.
 * Instead of giving two different names to the same function we've decided to simply mark
 * Scala's side method with `$` suffix.
 *
 * ==Importing==
 * Scalar needs to be imported in a proper way so that necessary objects and implicit
 * conversions got available in the scope:
 * <pre name="code" class="scala">
 * import org.gridgain.scalar._
 * import scalar._
 * </pre>
 * This way you import object `scalar` as well as all methods declared or inherited in that
 * object as well.
 *
 * ==Examples==
 * Here are few short examples of how Scalar can be used to program routine distributed
 * task. All examples below use default GridGain configuration and default grid. All these
 * examples take an implicit advantage of auto-discovery and failover, load balancing and
 * collision resolution, zero deployment and many other underlying technologies in the
 * GridGain - while remaining absolutely distilled to the core domain logic. 
 *
 * This code snippet prints out full topology: 
 * <pre name="code" class="scala">
 * scalar {
 *     grid$ foreach (n => println("Node: " + n.id8))
 * }
 * </pre>
 * The obligatory example - cloud enabled `Hello World!`. It splits the phrase
 * into multiple words and prints each word on a separate grid node:
 * <pre name="code" class="scala">
 * scalar {
 *     grid$ *< (SPREAD, (for (w <- "Hello World!".split(" ")) yield () => println(w)))
 * }
 * </pre>
 * This example broadcasts message to all nodes:
 * <pre name="code" class="scala">
 * scalar {
 *     grid$ *< (BROADCAST, () => println("Broadcasting!!!"))
 * }
 * </pre>
 * This example "greets" remote nodes only (note usage of Java-side closure):
 * <pre name="code" class="scala">
 * scalar {
 *     val me = grid$.localNode.id
 *     grid$.remoteProjection() *< (BROADCAST, F.println("Greetings from: " + me))
 * }
 * </pre>
 *
 * Next example creates a function that calculates lengths of the string
 * using MapReduce type of processing by splitting the input string into
 * multiple substrings, calculating each substring length on the remote
 * node and aggregating results for the final length of the original string:
 * <pre name="code" class="scala">
 * def count(msg: String) =
 *     grid$ @< (SPREAD, for (w <- msg.split(" ")) yield () => w.length, (s: Seq[Int]) => s.sum)
 * </pre>
 * This example shows a simple example of how Scalar can be used to work with data grid:
 * <pre name="code" class="scala">
 * scalar {
 *     val t = cache$[Symbol, Double]("partitioned")
 *     t += ('symbol -> 2.0)
 *     t -= ('symbol)
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
object scalar extends ScalarMixin {
    /** Visor copyright blurb. */
    private val COPYRIGHT = "2005-2011 Copyright (C) GridGain Systems, Inc."

    /** Visor version number. */
    private val VER = "3.0.9c"

    /** Visor build number. */
    private val BUILD = "19052011"

    /**
     * Prints Scalar ASCII-logo.
     */
    def logo() {
        val NL = System getProperty "line.separator"

        val s =
            " ________               ______                 " + NL +
            " __  ___/_____________ ____  /______ _________ " + NL +
            " _____ \\ _  ___/_  __ `/__  / _  __ `/__  ___/ " + NL +
            " ____/ / / /__  / /_/ / _  /  / /_/ / _  /     " + NL +
            " /____/  \\___/  \\__,_/  /_/   \\__,_/  /_/      " + NL +
            "      ---==++ GRIDGAIN SCALAR ++==---" + NL +
            "           ver. " + VER + '-' + BUILD + NL +
            COPYRIGHT + NL

        println(s)
    }

    /**
     * Note that grid instance will be stopped with cancel flat set to `true`.
     *
     * @param g Grid instance.
     * @param body Closure with grid instance as body's parameter.
     */
    private def init(g: Grid, body: Grid => Unit) {
        assert(g != null, body != null)

        try {
            body(g)
        }
        finally {
            GridFactory.stop(g.name, true)
        }
    }

    /**
     * Note that grid instance will be stopped with cancel flat set to `true`.
     *
     * @param g Grid instance.
     * @param body Closure with grid instance as body's parameter.
     */
    private def init[T](g: Grid, body: Grid => T): T = {
        assert(g != null, body != null)

        try {
            body(g)
        }
        finally {
            GridFactory.stop(g.name, true)
        }
    }

    /**
     * Note that grid instance will be stopped with cancel flat set to `true`.
     * 
     * @param g Grid instance.
     * @param body Passed by name body.
     */
    private def init0[T](g: Grid, body: => T): T = {
        assert(g != null)
        
        try {
            body
        }
        finally {
            GridFactory.stop(g.name, true)
        }
    }

    /**
     * Utility method for option creation.
     */
    private def couldBe[T](f: => T): Option[T] = {
        try {
            Option(f)
        }
        catch {
            case e: Throwable => None
        }
    }

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed default grid instance.
     */
    def apply(body: Grid => Unit) {
        if (!isStarted) init(GridFactory.start, body) else body(grid$)
    }

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed default grid instance.
     */
    def apply[T](body: Grid => T): T =
        if (!isStarted) init(GridFactory.start, body) else body(grid$)

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed default grid instance.
     */
    def apply[T](body: => T) =
        if (!isStarted) init0(GridFactory.start, body) else body

    /**
     * Executes given closure within automatically managed default grid instance.
     * If default grid is already started the passed in closure will simply
     * execute.
     *
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(body: => Unit) {
        if (!isStarted) init0(GridFactory.start, body) else body
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(springCfgPath: String)(body: => Unit) {
        init0(GridFactory.start(springCfgPath), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param cfg Grid configuration instance.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(cfg: GridConfiguration)(body: => Unit) {
        init0(GridFactory.start(cfg), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param springCtx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(cfg: GridConfiguration, springCtx: ApplicationContext)(body: => Unit) {
        init0(GridFactory.start(cfg, springCtx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param springCtx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(springCfgPath: String, springCtx: ApplicationContext)(body: => Unit) {
        init0(GridFactory.start(springCfgPath, springCtx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCtx Optional Spring application context.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(springCtx: ApplicationContext)(body: => Unit) {
        init0(GridFactory.start(springCtx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCtx Optional Spring application context.
     * @param springCfgUrl Spring XML configuration file URL.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(springCfgUrl: URL, springCtx: ApplicationContext)(body: => Unit) {
        init0(GridFactory.start(springCfgUrl, springCtx), body)
    }

    /**
     * Executes given closure within automatically managed grid instance.
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param body Closure to execute within automatically managed grid instance.
     */
    def apply(springCfgUrl: URL)(body: => Unit) {
        init0(GridFactory.start(springCfgUrl), body)
    }

    /**
     * Gets named cloud from the default grid instance.
     *
     * @param cloudName Cloud name.
     */
    @inline def cloud$(cloudName: String): Option[GridRichCloud] =
        Option(G.grid.cloud(cloudName))

    /**
     * Gets named cloud from the default grid instance.
     *
     * @param cloudName Cloud name.
     */
    @inline def cloud$(gridName: String, cloudName: String): Option[GridRichCloud] =
        grid$(gridName) match {
            case Some(g) => Option(g.cloud(cloudName))
            case None => None
        }

    /**
     * Gets default cache.
     *
     * Note that you always need to provide types when calling
     * this function - otherwise Scala will create `GridCache[Nothing, Nothing]`
     * typed instance that cannot be used.
     */
    @inline def cache$[K, V]: Option[GridCache[K, V]] =
        Option(G.grid.cache[K, V])

    /**
     * Gets named cache from default grid.
     *
     * @param cacheName Name of the cache to get.
     */
    @inline def cache$[K, V](@Nullable cacheName: String): Option[GridCache[K, V]] =
        Option(G.grid.cache(cacheName))

    /**
     * Gets named cache from specified grid.
     *
     * @param gridName Name of the grid.
     * @param cacheName Name of the cache to get.
     */
    @inline def cache$[K, V](@Nullable gridName: String, @Nullable cacheName: String): Option[GridCache[K, V]] =
        grid$(gridName) match {
            case Some(g) => Option(g.cache(cacheName))
            case None => None
        }

    /**
     * Gets default grid instance.
     */
    @inline def grid$: Grid =
        G.grid

    /**
     * Gets named grid.
     *
     * @param name Grid name.
     */
    @inline def grid$(@Nullable name: String): Option[Grid] =
        try {
            Option(G.grid(name))
        }
        catch {
            case _: IllegalStateException => None
        }

    /**
     * Gets grid for given node ID.
     *
     * @param locNodeId Local node ID for which to get grid instance option.
     */
    @inline def grid$(locNodeId: UUID): Option[Grid] = {
        assert(locNodeId != null)

        try {
            Option(G.grid(locNodeId))
        }
        catch {
            case _: IllegalStateException => None
        }
    }

    /**
     * Tests if specified grid is started.
     *
     * @param name Gird name.
     */
    def isStarted(@Nullable name: String) =
        G.state(name) == GridFactoryState.STARTED

    /**
     * Tests if specified grid is stopped.
     *
     * @param name Gird name.
     */
    def isStopped(@Nullable name: String) =
        G.state(name) == GridFactoryState.STOPPED

    /**
     * Tests if default grid is started.
     */
    def isStarted =
        G.state == GridFactoryState.STARTED

    /**
     * Tests if default grid is stopped.
     */
    def isStopped =
        G.state == GridFactoryState.STOPPED

    /**
     * Stops given grid with specified flags.
     * If specified grid is already stopped - it's no-op.
     *
     * @param name Grid name to cancel.
     * @param cancel Whether or not to cancel all currently running jobs.
     * @param wait Whether or not wait for all executing tasks to finish.
     */
    def stop(@Nullable name: String, cancel: Boolean, wait: Boolean) =
        if (isStarted(name))
            G.stop(name, cancel, wait)

    /**
     * Stops given grid and specified cancel flag.
     * If specified grid is already stopped - it's no-op.
     *
     * @param name Grid name to cancel.
     * @param cancel Whether or not to cancel all currently running jobs.
     */
    def stop(@Nullable name: String, cancel: Boolean) =
        if (isStarted(name)) G.stop(name, cancel)

    /**
     * Stops default grid with given flags.
     * If default grid is already stopped - it's no-op.
     *
     * @param cancel Whether or not to cancel all currently running jobs.
     * @param wait Whether or not wait for all executing tasks to finish.
     */
    def stop(cancel: Boolean, wait: Boolean) =
        if (isStarted) G.stop(cancel, wait)

    /**
     * Stops default grid with given cancel flag.
     * If default grid is already stopped - it's no-op.
     *
     * @param cancel Whether or not to cancel all currently running jobs.
     */
    def stop(cancel: Boolean) =
        if (isStarted) G.stop(cancel)

    /**
     * Stops default grid with cancel flag set to `true`.
     * If default grid is already stopped - it's no-op.
     */
    def stop() =
        if (isStarted) G.stop(true)

    /**
     * Sets daemon flag to grid factory. Note that this method should be called
     * before grid instance starts.
     *
     * @param f Daemon flag to set.
     */
    def daemon(f: Boolean) {
        G.setDaemon(f)
    }

    /**
     * Gets daemon flag set in the grid factory.
     */
    def isDaemon =
        G.isDaemon

    /**
     *  Starts default grid. It's no-op if default grid is already started.
     *
     *  @return Started grid.
     */
    def start(): Grid = {
        if (!isStarted) G.start else grid$
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     */
    def start(@Nullable springCfgPath: String): Grid = {
        G.start(springCfgPath)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Started grid.
     */
    def start(cfg: GridConfiguration): Grid = {
        G.start(cfg)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param springCtx Optional Spring application context.
     * @return Started grid.
     */
    def start(cfg: GridConfiguration, @Nullable ctx: ApplicationContext): Grid = {
        G.start(cfg, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param springCtx Optional Spring application context.
     * @return Started grid.
     */
    def start(@Nullable springCfgPath: String = null, @Nullable ctx: ApplicationContext): Grid = {
        G.start(springCfgPath, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCtx Optional Spring application context.
     * @return Started grid.
     */
    def start(@Nullable ctx: ApplicationContext): Grid = {
        G.start(ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @param springCtx Optional Spring application context.
     * @return Started grid.
     */
    def start(springCfgUrl: URL, @Nullable ctx: ApplicationContext): Grid = {
        G.start(springCfgUrl, ctx)
    }

    /**
     * Starts grid with given parameter(s).
     *
     * @param springCfgUrl Spring XML configuration file URL.
     * @return Started grid.
     */
    def start(springCfgUrl: URL): Grid = {
        G.start(springCfgUrl)
    }
}