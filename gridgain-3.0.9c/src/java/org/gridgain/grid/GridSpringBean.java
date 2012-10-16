// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Grid Spring bean allows to bypass {@link GridFactory} methods.
 * In other words, this bean class allows to inject new grid instance from
 * Spring configuration file directly without invoking static
 * {@link GridFactory} methods. This class can be wired directly from
 * Spring and can be referenced from within other Spring beans.
 * By virtue of implementing {@link DisposableBean} and {@link InitializingBean}
 * interfaces, {@code GridSpringBean} automatically starts and stops underlying
 * grid instance.
 * <p>
 * <h1 class="header">Spring Configuration Example</h1>
 * Here is a typical example of describing it in Spring file:
 * <pre name="code" class="xml">
 * &lt;bean id="mySpringBean" class="org.gridgain.grid.GridSpringBean" scope="singleton"&gt;
 *     &lt;property name="configuration"&gt;
 *         &lt;bean id="grid.cfg" class="org.gridgain.grid.GridConfigurationAdapter" scope="singleton"&gt;
 *             &lt;property name="gridName" value="mySpringGrid"/&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 * &lt;/bean&gt;
 * </pre>
 * Or use default configuration:
 * <pre name="code" class="xml">
 * &lt;bean id="mySpringBean" class="org.gridgain.grid.GridSpringBean" scope="singleton"/&gt;
 * </pre>
 * <h1 class="header">Java Example</h1>
 * Here is how you may access this bean from code:
 * <pre name="code" class="java">
 * AbstractApplicationContext ctx = new FileSystemXmlApplicationContext("/path/to/spring/file");
 *
 * // Register Spring hook to destroy bean automatically.
 * ctx.registerShutdownHook();
 *
 * Grid grid = (Grid)ctx.getBean("mySpringBean");
 * </pre>
 * <p>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridSpringBean extends GridMetadataAwareAdapter implements Grid, DisposableBean, InitializingBean,
    ApplicationContextAware {
    /** */
    private Grid g;

    /** */
    private GridConfiguration cfg;

    /** */
    private ApplicationContext appCtx;

    /** {@inheritDoc} */
    @Deprecated
    @Override public GridConfiguration getConfiguration() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridConfiguration configuration() {
        return cfg;
    }

    /**
     * Sets grid configuration.
     *
     * @param cfg Grid configuration.
     */
    public void setConfiguration(GridConfiguration cfg) {
        this.cfg = cfg;
    }

    /** {@inheritDoc} */
    @Override public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        appCtx = ctx;
    }

    /** {@inheritDoc} */
    @Override public Grid grid() {
        assert g != null;

        return g;
    }

    /** {@inheritDoc} */
    @Override public void destroy() throws Exception {
        // If there were some errors when afterPropertiesSet() was called.
        if (g != null) {
            // Do not cancel started tasks, wait for them.
            G.stop(g.name(), false, true);
        }
    }

    /** {@inheritDoc} */
    @Override public void afterPropertiesSet() throws Exception {
        if (cfg == null) {
            cfg = new GridConfigurationAdapter();
        }

        G.start(cfg, appCtx);

        g = G.grid(cfg.getGridName());
    }

    /** {@inheritDoc} */
    @Override public GridLogger log() {
        assert cfg != null;

        return cfg.getGridLogger();
    }

    /** {@inheritDoc} */
    @Override public GridProjectionMetrics projectionMetrics() throws GridException {
        assert g != null;

        return g.projectionMetrics();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> daemonNodes(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.daemonNodes(p);
    }

    /** {@inheritDoc} */
    @Override public GridProjection parent() {
        assert g != null;

        return g.parent();
    }

    /** {@inheritDoc} */
    @Override public int size(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.size(p);
    }

    /** {@inheritDoc} */
    @Override public String version() {
        assert g != null;

        return g.version();
    }

    /** {@inheritDoc} */
    @Override public String copyright() {
        assert g != null;

        return g.copyright();
    }

    /** {@inheritDoc} */
    @Override public String build() {
        assert g != null;

        return g.build();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridProjection> neighborhood() {
        assert g != null;

        return g.neighborhood();
    }

    /** {@inheritDoc} */
    @Override public boolean hasRemoteNodes() {
        assert g != null;

        return g.hasRemoteNodes();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodeId8(String id8) {
        assert g != null;

        return g.nodeId8(id8);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public Collection<GridRichNode> getAllNodes() {
        assert g != null;

        return g.getAllNodes();
    }

    /** {@inheritDoc} */
    @Override public boolean hasLocalNode() {
        assert g != null;

        return g.hasLocalNode();
    }

    /** {@inheritDoc} */
    @Override public GridRichNode youngest() {
        assert g != null;

        return g.youngest();
    }

    /** {@inheritDoc} */
    @Override public GridRichNode oldest() {
        assert g != null;

        return g.oldest();
    }

    /** {@inheritDoc} */
    @Override public int hosts() {
        assert g != null;

        return g.hosts();
    }

    /** {@inheritDoc} */
    @Override public int cpus() {
        assert g != null;

        return g.cpus();
    }

    /** {@inheritDoc} */
    @Override public boolean isRestartEnabled() {
        assert g != null;

        return g.isRestartEnabled();
    }

    /** {@inheritDoc} */
    @Override public GridEnterpriseLicense license() {
        assert g != null;

        return g.license();
    }

    /** {@inheritDoc} */
    @Override public boolean isJmxRemoteEnabled() {
        assert g != null;

        return g.isJmxRemoteEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isSmtpEnabled() {
        assert g != null;

        return g.isSmtpEnabled();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> sendAdminEmailAsync(String subj, String body, boolean html) {
        assert g != null;

        return g.sendAdminEmailAsync(subj, body, html);
    }

    /** {@inheritDoc} */
    @Override public void sendAdminEmail(String subj, String body, boolean html) throws GridException {
        assert g != null;

        g.sendAdminEmail(subj, body, html);
    }

    /** {@inheritDoc} */
    @Override public <T> GridOutClosure<GridFuture<T>> gridify(GridClosureCallMode mode, Callable<T> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public GridOutClosure<GridFuture<?>> gridify(GridClosureCallMode mode, Runnable r,
        @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.gridify(mode, r, p);
    }

    /** {@inheritDoc} */
    @Override public <E, T> GridClosure<E, GridFuture<T>> gridify(GridClosureCallMode mode, GridClosure<E, T> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, T> GridClosure2<E1, E2, GridFuture<T>> gridify(GridClosureCallMode mode,
        GridClosure2<E1, E2, T> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3, T> GridClosure3<E1, E2, E3, GridFuture<T>> gridify(
        GridClosureCallMode mode, GridClosure3<E1, E2, E3, T> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E> GridClosure<E, GridFuture<?>> gridify(GridClosureCallMode mode, GridInClosure<E> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2> GridClosure2<E1, E2, GridFuture<?>> gridify(GridClosureCallMode mode,
        GridInClosure2<E1, E2> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<?>> gridify(
        GridClosureCallMode mode, GridInClosure3<E1, E2, E3> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public GridOutClosure<GridFuture<Boolean>> gridify(GridClosureCallMode mode, GridAbsPredicate c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E> GridClosure<E, GridFuture<Boolean>> gridify(GridClosureCallMode mode, GridPredicate<E> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2> GridClosure2<E1, E2, GridFuture<Boolean>> gridify(GridClosureCallMode mode,
        GridPredicate2<E1, E2> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public <E1, E2, E3> GridClosure3<E1, E2, E3, GridFuture<Boolean>> gridify(
        GridClosureCallMode mode, GridPredicate3<E1, E2, E3> c,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.gridify(mode, c, p);
    }

    /** {@inheritDoc} */
    @Override public boolean dynamic() {
        assert g != null;

        return g.dynamic();
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodes(@Nullable Collection<? extends GridNode> nodes) {
        assert g != null;

        return g.projectionForNodes(nodes);
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodes(@Nullable GridRichNode... nodes) {
        assert g != null;

        return g.projectionForNodes(nodes);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(@Nullable Collection<UUID> ids) {
        assert g != null;

        return g.nodes(ids);
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross(@Nullable GridProjection... prjs) {
        assert g != null;

        return g.cross(prjs);
    }

    /** {@inheritDoc} */
    @Override public ExecutorService newGridExecutorService() {
        assert g != null;

        return g.executor();
    }

    /** {@inheritDoc} */
    @Override public GridEvent waitForEvent(long timeout, @Nullable Runnable c,
        @Nullable GridPredicate<? super GridEvent> p, @Nullable int... types) throws GridException {
        assert g != null;

        return g.waitForEvent(timeout, c, p, types);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridEvent> waitForEventAsync(@Nullable GridPredicate<? super GridEvent> p,
        @Nullable int... types) {
        assert g != null;

        return g.waitForEventAsync(p, types);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode rich(GridNode node) {
        assert g != null;

        return g.rich(node);
    }

    /** {@inheritDoc} */
    @Override public GridRichCloud rich(GridCloud cloud) {
        assert g != null;

        return g.rich(cloud);
    }

    /** {@inheritDoc} */
    @Override public GridProjection merge(@Nullable GridProjection... prjs) {
        assert g != null;

        return g.merge(prjs);
    }

    /** {@inheritDoc} */
    @Override public long topologyHash(Iterable<? extends GridNode> nodes) {
        assert g != null;

        return g.topologyHash(nodes);
    }

    /** {@inheritDoc} */
    @Override public void addLocalEventListener(GridLocalEventListener lsnr, int[] types) {
        assert g != null;

        g.addLocalEventListener(lsnr, types);
    }

    /** {@inheritDoc} */
    @Override public void addLocalEventListener(GridLocalEventListener lsnr, int type, @Nullable int... types) {
        assert g != null;

        g.addLocalEventListener(lsnr, type, types);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable GridNode node,
        @Nullable GridPredicate2<UUID, ? super T>... p) throws GridException {
        assert g != null;

        return g.remoteListenAsync(node, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable Collection<? extends GridNode> nodes,
        @Nullable GridPredicate2<UUID, ? super T>... p) throws GridException {
        assert g != null;

        return g.remoteListenAsync(nodes, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> remoteListenAsync(@Nullable GridPredicate<? super GridRichNode> pn,
        @Nullable GridPredicate2<UUID, ? super T>... p) throws GridException {
        assert g != null;

        return g.remoteListenAsync(pn, p);
    }

    /** {@inheritDoc} */
    @Override public boolean removeLocalEventListener(GridLocalEventListener lsnr, @Nullable int... types) {
        assert g != null;

        return g.removeLocalEventListener(lsnr, types);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public void addMessageListener(GridMessageListener lsnr, @Nullable GridPredicate<Object>... p) {
        assert g != null;

        g.addMessageListener(lsnr, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public boolean removeMessageListener(GridMessageListener lsnr) {
        assert g != null;

        return g.removeMessageListener(lsnr);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode localNode() {
        assert g != null;

        return g.localNode();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runLocal(@Nullable Runnable r) throws GridException {
        assert g != null;

        return g.runLocal(r);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callLocal(@Nullable Callable<R> c) throws GridException {
        assert g != null;

        return g.callLocal(c);
    }

    /** {@inheritDoc} */
    @Override public <R> GridScheduleFuture<R> scheduleLocal(@Nullable Callable<R> r, String pattern) throws GridException {
        assert g != null;

        return g.scheduleLocal(r, pattern);
    }

    /** {@inheritDoc} */
    @Override public GridScheduleFuture<?> scheduleLocal(@Nullable Runnable r, String pattern) throws GridException {
        assert g != null;

        return g.scheduleLocal(r, pattern);
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridNodeLocal<K, V> nodeLocal() {
        assert g != null;

        return g.nodeLocal();
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        assert g != null;

        return g.pingNode(nodeId);
    }

    /** {@inheritDoc} */
    @Override public void deployTask(Class<? extends GridTask> taskCls) throws GridException {
        assert g != null;

        g.deployTask(taskCls);
    }

    /** {@inheritDoc} */
    @Override public void deployTask(Class<? extends GridTask> taskCls, ClassLoader clsLdr) throws GridException {
        assert g != null;

        g.deployTask(taskCls, clsLdr);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Class<? extends GridTask<?, ?>>> localTasks(
        @Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>... p) {
        assert g != null;

        return g.localTasks(p);
    }

    /** {@inheritDoc} */
    @Override public void undeployTask(String taskName) throws GridException {
        assert g != null;

        g.undeployTask(taskName);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridEvent> localEvents(@Nullable GridPredicate<? super GridEvent>... p) {
        assert g != null;

        return g.localEvents(p);
    }

    /** {@inheritDoc} */
    @Override public void recordLocalEvent(GridEvent evt) {
        assert g != null;

        g.recordLocalEvent(evt);
    }

    /** {@inheritDoc} */
    @Deprecated
    @SuppressWarnings("deprecation")
    @Override public String getName() {
        assert g != null;

        return g.getName();
    }

    /** {@inheritDoc} */
    @Override public String name() {
        assert g != null;

        return g.name();
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCache<K, V> cache(String name) {
        assert g != null;

        return g.cache(name);
    }

    /** {@inheritDoc} */
    @Override public <K, V> GridCache<K, V> cache() {
        assert g != null;

        return g.cache();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCache<?, ?>> caches(@Nullable GridPredicate<? super GridCache<?, ?>>... p) {
        assert g != null;

        return g.caches(p);
    }

    /** {@inheritDoc} */
    @Override public void writeToSwap(@Nullable String space, Object key, @Nullable Object val) throws GridException {
        assert g != null;

        g.writeToSwap(space, key, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Override public <T> T readFromSwap(@Nullable String space, Object key) throws GridException {
        assert g != null;

        return g.<T>readFromSwap(space, key);
    }

    /** {@inheritDoc} */
    @Override public boolean removeFromSwap(@Nullable String space, Object key, @Nullable GridInClosure<Object> c)
        throws GridException {
        assert g != null;

        return g.removeFromSwap(space, key, c);
    }

    /** {@inheritDoc} */
    @Override public void clearSwapSpace(@Nullable String space) throws GridException {
        assert g != null;

        g.clearSwapSpace(space);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichCloud> clouds(@Nullable GridPredicate<? super GridRichCloud>... p) {
        assert g != null;

        return g.clouds(p);
    }

    /** {@inheritDoc} */
    @Override public GridRichCloud cloud(String cloudId) {
        assert g != null;

        return g.cloud(cloudId);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> getRemoteNodes(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.remoteNodes(p);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode getLocalNode() {
        assert g != null;

        return g.localNode();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> getNodes(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.nodes(p);
    }

    /** {@inheritDoc} */
    @Override public GridNode getNode(UUID nodeId) {
        assert g != null;

        return g.node(nodeId);
    }

    /** {@inheritDoc} */
    @Override public <T, R> R executeSync(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.executeSync(task, arg, timeout);
    }

    /** {@inheritDoc} */
    @Override public <T, R> R executeSync(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.executeSync(taskCls, arg, timeout, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Override public <T, R> R executeSync(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.<T, R>executeSync(taskName, arg, timeout, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskName, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskName, arg, timeout, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, GridTaskListener lsnr,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskName, arg, lsnr, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskName, arg, timeout, lsnr, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskCls, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        long timeout, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskCls, arg, timeout, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskCls, arg, lsnr, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg,
        long timeout, GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(taskCls, arg, timeout, lsnr, p);
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<GridRichNode> predicate() {
        assert g != null;

        return g.predicate();
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(task, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(task, arg, timeout, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, GridTaskListener lsnr,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(task, arg, lsnr, p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        GridTaskListener lsnr, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.execute(task, arg, timeout, lsnr, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T extends Callable<R1>> R2 mapreduce(@Nullable GridMapper<T, GridRichNode> mapper,
        @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduce(mapper, jobs, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T extends Callable<R1>> GridFuture<R2> mapreduceAsync(
        @Nullable GridMapper<T, GridRichNode> mapper, @Nullable Collection<T> jobs, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduceAsync(mapper, jobs, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public boolean isEnterprise() {
        assert g != null;

        return g.isEnterprise();
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(@Nullable UUID... ids) {
        assert g != null;

        return g.projectionForNodeIds(ids);
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForPredicate(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.projectionForPredicate(p);
    }

    /** {@inheritDoc} */
    @Override public long topologyHash(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.topologyHash(p);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> remoteNodes(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.remoteNodes(p);
    }

    /** {@inheritDoc} */
    @Override public GridProjection remoteProjection(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.remoteProjection(p);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.nodes(p);
    }

    /** {@inheritDoc} */
    @Override public void run(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        g.run(mapper, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public void run(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, job, p);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Runnable job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.runAsync(mode, job, p);
    }

    /** {@inheritDoc} */
    @Override public void run(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable Collection<? extends Runnable> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.runAsync(mode, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <R> R call(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.call(mode, job, p);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable Callable<R> job,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, job, p);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.call(mode, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends Callable<R>> jobs,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> R2 reduce(GridClosureCallMode mode, @Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduce(mode, jobs, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        Collection<? extends Callable<R1>> jobs, GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduceAsync(mode, jobs, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Object msg, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        g.send(msg, p);
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Collection<?> msgs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        g.send(msgs, p);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode node(UUID nodeId, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.node(nodeId, p);
    }

    /** {@inheritDoc} */
    @Override public boolean isEmptyFor(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.isEmptyFor(p);
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        assert g != null;

        return g.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean contains(GridNode node, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.contains(node, p);
    }

    /** {@inheritDoc} */
    @Override public boolean contains(UUID nodeId, @Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.contains(nodeId, p);
    }

    /** {@inheritDoc} */
    @Override public List<GridEvent> remoteEvents(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... pn) throws GridException {
        assert g != null;

        return g.remoteEvents(pe, timeout, pn);
    }

    /** {@inheritDoc} */
    @Override public <T> void listen(@Nullable GridPredicate2<UUID, ? super T>... p) {
        assert g != null;

        g.listen(p);
    }

    /** {@inheritDoc} */
    @Override public ExecutorService executor(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.executor(p);
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridRichNode> iterator() {
        assert g != null;

        return g.iterator();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<List<GridEvent>> remoteEventsAsync(GridPredicate<? super GridEvent> pe, long timeout,
        @Nullable GridPredicate<? super GridRichNode>... pn) throws GridException {
        assert g != null;

        return g.remoteEventsAsync(pe, timeout, pn);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Class<? extends GridTask<?, ?>>> getLocalTasks(
        @Nullable GridPredicate<? super Class<? extends GridTask<?, ?>>>... p) {
        assert g != null;

        return g.localTasks(p);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public void sendMessage(GridNode node, Object msg) throws GridException {
        assert g != null;

        g.sendMessage(node, msg);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public void sendMessage(Collection<? extends GridNode> nodes, Object msg) throws GridException {
        assert g != null;

        g.sendMessage(nodes, msg);
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForNodeIds(@Nullable Collection<UUID> nodeIds) {
        assert g != null;

        return g.projectionForNodeIds(nodeIds);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public void addDiscoveryListener(GridDiscoveryListener lsnr) {
        assert g != null;

        g.addDiscoveryListener(lsnr);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"deprecation"})
    @Override public boolean removeDiscoveryListener(GridDiscoveryListener lsnr) {
        assert g != null;

        return g.removeDiscoveryListener(lsnr);
    }

    /** {@inheritDoc} */
    @Override public GridPair<GridProjection> split(@Nullable GridPredicate<? super GridRichNode>... p) {
        assert g != null;

        return g.split(p);
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross(@Nullable Collection<? extends GridNode> nodes) {
        assert g != null;

        return g.cross(nodes);
    }

    /** {@inheritDoc} */
    @Override public GridProjection cross0(@Nullable GridRichNode... nodes) {
        assert g != null;

        return g.cross(nodes);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.call(mapper, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> callAsync(@Nullable GridMapper<Callable<R>, GridRichNode> mapper,
        @Nullable Collection<? extends Callable<R>> jobs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.callAsync(mapper, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.call(mode, jobs, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, jobs, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.call(mode, job, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, job, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.call(mode, job, pdc, cnt, p);
    }

    /** {@inheritDoc} */
    @Override public <T, R> GridFuture<Collection<R>> callAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, job, pdc, cnt, p);
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode,
        @Nullable Collection<? extends GridInClosure<? super T>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, jobs, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridInClosure<? super T>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.runAsync(mode, jobs, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, job, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable Collection<? extends T> args, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.runAsync(mode, job, args, p);
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, job, pdc, cnt, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.runAsync(mode, job, pdc, cnt, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduce(mode, jobs, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduceAsync(mode, jobs, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable Collection<? extends T> args, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduce(mode, job, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduceAsync(mode, job, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 reduce(GridClosureCallMode mode, @Nullable GridClosure<? super T, R1> job,
        @Nullable GridOutClosure<T> pdc, int cnt, @Nullable GridReducer<R1, R2> rdc,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduce(mode, job, pdc, cnt, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> reduceAsync(GridClosureCallMode mode,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.reduceAsync(mode, job, pdc, cnt, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduce(mapper, jobs, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(
        @Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable Collection<? extends GridClosure<? super T, R1>> jobs, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduceAsync(mapper, jobs, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduce(mapper, job, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<GridOutClosure<R1>,
        GridRichNode> mapper, @Nullable GridClosure<? super T, R1> job, @Nullable Collection<? extends T> args,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduceAsync(mapper, job, args, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 mapreduce(@Nullable GridMapper<GridOutClosure<R1>, GridRichNode> mapper,
        @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduce(mapper, job, pdc, cnt, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> GridFuture<R2> mapreduceAsync(@Nullable GridMapper<GridOutClosure<R1>,
        GridRichNode> mapper, @Nullable GridClosure<? super T, R1> job, @Nullable GridOutClosure<T> pdc, int cnt,
        @Nullable GridReducer<R1, R2> rdc, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.mapreduceAsync(mapper, job, pdc, cnt, rdc, p);
    }

    /** {@inheritDoc} */
    @Override public <T> void run(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        g.run(mode, job, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<?> runAsync(GridClosureCallMode mode, @Nullable GridInClosure<? super T> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.runAsync(mode, job, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <R, T> R call(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job, @Nullable T arg,
        @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.call(mode, job, arg, p);
    }

    /** {@inheritDoc} */
    @Override public <R, T> GridFuture<R> callAsync(GridClosureCallMode mode, @Nullable GridClosure<? super T, R> job,
        @Nullable T arg, @Nullable GridPredicate<? super GridRichNode>... p) throws GridException {
        assert g != null;

        return g.callAsync(mode, job, arg, p);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(@Nullable GridMapper<Runnable, GridRichNode> mapper,
        @Nullable Collection<? extends Runnable> jobs, @Nullable GridPredicate<? super GridRichNode>... p)
        throws GridException {
        assert g != null;

        return g.runAsync(mapper, jobs, p);
    }

    /** {@inheritDoc} */
    @Override public <K> Map<UUID, Collection<K>> mapKeysToNodes(String cacheName,
        @Nullable Collection<? extends K> keys) throws GridException {
        assert g != null;

        return g.mapKeysToNodes(cacheName, keys);
    }

    /** {@inheritDoc} */
    @Override public <K> Map<UUID, Collection<K>> mapKeysToNodes(
        @Nullable Collection<? extends K> keys) throws GridException {
        assert g != null;

        return g.mapKeysToNodes(keys);
    }

    /** {@inheritDoc} */
    @Override public <K> UUID mapKeyToNode(K key) throws GridException {
        assert g != null;

        return g.mapKeyToNode(key);
    }

    /** {@inheritDoc} */
    @Override public <K> UUID mapKeyToNode(String cacheName, K key) throws GridException {
        assert g != null;

        return g.mapKeyToNode(cacheName, key);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSpringBean.class, this);
    }
}
