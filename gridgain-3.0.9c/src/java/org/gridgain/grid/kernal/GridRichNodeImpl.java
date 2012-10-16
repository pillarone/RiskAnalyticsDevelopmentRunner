// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridClosureCallMode.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridRichNodeImpl extends GridProjectionAdapter implements GridRichNode, Externalizable {
    /** */
    private static final ThreadLocal<GridTuple2<String, UUID>> stash = new ThreadLocal<GridTuple2<String, UUID>>() {
        @Override protected GridTuple2<String, UUID> initialValue() {
            return F.t2();
        }
    };

    /** Wrapped node. */
    private GridNode node;

    /** Collection of one (this) rich node. */
    private Collection<GridRichNode> nodes;

    /** */
    private GridPredicate<GridRichNode> p;

    /** */
    private boolean isLocal;

    /** */
    private int hash;

    /** */
    private boolean daemon;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridRichNodeImpl() {
        super(null);
    }

    /**
     * Creates new rich grid node.
     *
     * @param ctx Kernal context
     * @param node Newly created grid rich node.
     */
    public GridRichNodeImpl(GridKernalContext ctx, GridNode node) {
        super(ctx.grid(), ctx);

        assert node != null;

        this.node = node;

        daemon = "true".equalsIgnoreCase(node.<String>attribute(ATTR_DAEMON));

        hash = node.hashCode();

        isLocal = node.id().equals(ctx.localNodeId());

        nodes = Collections.<GridRichNode>singletonList(this);

        p = new GridNodePredicate<GridRichNode>(node.id());
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
        U.writeUuid(out, node.id());
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple2<String, UUID> t = stash.get();

        t.set1(U.readString(in));
        t.set2(U.readUuid(in));
    }

    /** {@inheritDoc} */
    @Override public int arity() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public GridRichNode part(int n) {
        switch (n) {
            case 0: return this;

            default:
                throw new IndexOutOfBoundsException("Invalid product index: " + n);
        }
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            GridTuple2<String, UUID> t = stash.get();

            Grid g = G.grid(t.get1());

            GridNode n = g.node(t.get2());

            return n == null ? null : g.rich(n);
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridProjection neighbors() {
        guard();

        try {
            Collection<GridRichNode> neighbors = new ArrayList<GridRichNode>(2);

            neighbors.add(this);

            String macs = attribute(ATTR_MACS);

            assert macs != null;

            for (GridNode n : ctx.discovery().remoteNodes())
                if (n.attribute(ATTR_MACS).equals(macs))
                    neighbors.add(ctx.rich().rich(n));

            // Single element projection gets short circuit to its single rich node.
            return neighbors.size() == 1 ? this : new GridProjectionImpl(this, ctx, neighbors);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public int cpus() {
        return metrics().getTotalCpus();
    }

    /** {@inheritDoc} */
    @Override public Collection<GridProjection> neighborhood() {
        return Collections.<GridProjection>singleton(this);
    }

    /** {@inheritDoc} */
    @Override public GridRichNode youngest() {
        return this;
    }

    /** {@inheritDoc} */
    @Override public GridRichNode oldest() {
        return this;
    }

    /** {@inheritDoc} */
    @Override public int hosts() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public boolean hasRemoteNodes() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> remoteNodes(@Nullable GridPredicate<? super GridRichNode>[] p) {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override public GridProjection remoteProjection(@Nullable GridPredicate<? super GridRichNode>[] p) {
        return new GridProjectionImpl(this, ctx, Collections.<GridRichNode>emptyList());
    }

    /** {@inheritDoc} */
    @Override public boolean hasLocalNode() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public GridNode originalNode() {
        return node;
    }

    /** {@inheritDoc} */
    @Override public boolean isDaemon() {
        return daemon;
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<GridRichNode> predicate() {
        guard();

        try {
            return p;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public String gridName() {
        guard();

        try {
            return attribute(ATTR_GRID_NAME);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return F.eqNodes(this, o);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return hash;
    }

    /** {@inheritDoc} */
    @Override public String id8() {
        return U.id8(id());
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridRichNode o) {
        return o == null ? 1 : node.id().compareTo(o.id());
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(@Nullable GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return F.<GridRichNode>isAll(this, p) ? nodes : Collections.<GridRichNode>emptyList();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <V> V addMeta(String name, V val) {
        return node.addMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Override public <V> V meta(String name) {
        return node.<V>meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Override public <V> V removeMeta(String name) {
        return node.<V>removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> Map<String, V> allMeta() {
        return node.allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return node.hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name, Object val) {
        return node.hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        return node.addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, Callable<V> c) {
        return node.addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        return node.replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        node.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        node.copyMeta(data);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public UUID id() {
        return node.id();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T nodeLocalPut(Object key, @Nullable Object val) throws GridException {
        A.notNull(key, "key", val, "val");

        return isLocal ? (T)ctx.grid().nodeLocal().put(key, val) : this.<T>nodeLocalPutAsync(key, val).get();
    }

    /**
     * @param <T> Type of value previously associated with given key.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private static class NodeLocalPutClosure<T> extends CO<T> {
        /** */
        @GridInstanceResource
        private Grid grid;

        /** */
        private Object key;

        /** */
        private Object val;

        /**
         * @param key Node local key.
         * @param val New node local value.
         */
        NodeLocalPutClosure(Object key, Object val) {
            this.key = key;
            this.val = val;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public T apply() {
            return (T)grid.nodeLocal().put(key, val);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> GridFuture<T> nodeLocalPutAsync(Object key, @Nullable Object val) throws GridException {
        A.notNull(key, "key", val, "val");

        guard();

        try {
            return isLocal ? new GridFinishedFuture<T>(ctx, (T)ctx.grid().nodeLocal().put(key, val)) :
                ctx.closure().callAsync(UNICAST, new NodeLocalPutClosure(key, val), nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public boolean ping() {
        guard();

        try {
            return ctx.discovery().pingNode(node.id());
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isLocal() {
        guard();

        try {
            return ctx.localNodeId().equals(node.id());
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable public GridRichCloud cloud() {
        guard();

        try {
            return ctx.rich().rich(ctx.cloud().cloudByResourceId(node.id().toString()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> callAsync(@Nullable Callable<R> job) throws GridException {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().callAsync(UNICAST, job, nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> callAsync(@Nullable Collection<? extends Callable<R>> jobs)
        throws GridException {
        A.notNull(jobs, "jobs");

        guard();

        try {
            return ctx.closure().callAsync(UNICAST, jobs, nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridFuture<R2> forkjoinAsync(@Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc) throws GridException {
        A.notNull(jobs, "jobs", rdc, "rdc");

        guard();

        try {
            return ctx.closure().forkjoinAsync(UNICAST, jobs, rdc, nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(@Nullable Runnable job) throws GridException {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().runAsync(UNICAST, job, nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> runAsync(@Nullable Collection<? extends Runnable> jobs) throws GridException {
        A.notNull(jobs, "jobs");

        guard();

        try {
            return ctx.closure().runAsync(UNICAST, jobs, nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(@Nullable Runnable job) throws GridException {
        runAsync(job).get();
    }

    /** {@inheritDoc} */
    @Override public void run(@Nullable Collection<? extends Runnable> jobs) throws GridException {
        runAsync(jobs).get();
    }

    /** {@inheritDoc} */
    @Override public <R> R call(@Nullable Callable<R> job) throws GridException {
        return callAsync(job).get();
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(@Nullable Collection<? extends Callable<R>> jobs) throws GridException {
        return callAsync(jobs).get();
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> R2 forkjoin(@Nullable Collection<? extends Callable<R1>> jobs,
        @Nullable GridReducer<R1, R2> rdc)
        throws GridException {
        return forkjoinAsync(jobs, rdc).get();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T nodeLocalGet(Object key) throws GridException {
        return isLocal ? (T)ctx.grid().nodeLocal().get(key) : this.<T>nodeLocalGetAsync(key).get();
    }

    /**
     * @param <T> Type of value returned from node local storage.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private static class NodeLocalGetClosure<T> extends CO<T> {
        /** */
        @GridInstanceResource
        private Grid grid;

        /** */
        private Object key;

        /**
         * @param key Node local key.
         */
        NodeLocalGetClosure(Object key) {
            this.key = key;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public T apply() {
            return (T)grid.nodeLocal().get(key);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> GridFuture<T> nodeLocalGetAsync(Object key) throws GridException {
        A.notNull(key, "key");

        guard();

        try {
            return isLocal ? new GridFinishedFuture<T>(ctx, (T)ctx.grid().nodeLocal().get(key)) :
                ctx.closure().callAsync(UNICAST, new NodeLocalGetClosure(key), nodes);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void nodeLocalRun(Object key, @Nullable GridInClosure<Object> c) throws GridException {
        nodeLocalRunAsync(key, c).get();
    }

    /**
     * Utility absolute closure that helps to execute given in-closure closed on value
     * obtained from node local storage by given key.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private static class NodeLocalRunClosure extends CA {
        /** */
        @GridInstanceResource
        private Grid grid;

        /** */
        private Object key;

        /** */
        private GridInClosure c;

        /**
         * @param key Node local storage key to get value for closure by.
         * @param c Closure to run with value from node local storage.
         */
        NodeLocalRunClosure(Object key, GridInClosure c) {
            this.key = key;
            this.c = c;
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void apply() {
            c.apply(grid.nodeLocal().get(key));
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> nodeLocalRunAsync(Object key, @Nullable GridInClosure<Object> c)
        throws GridException {
        guard();

        try {
            if (c != null) {
                Runnable ca = new NodeLocalRunClosure(key, c);

                return isLocal ? ctx.closure().runLocal(ca) : ctx.closure().runAsync(UNICAST, ca, nodes);
            }

            return new GridFinishedFuture(ctx);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T> T nodeLocalCall(Object key, @Nullable GridClosure<Object, T> c) throws GridException {
        return nodeLocalCallAsync(key, c).get();
    }

    /**
     * Utility closure that helps to execute another closure closed on value
     * obtained from node local storage by given key.
     *
     * @param <T> Type of closure execution result.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private static class NodeLocalCallClosure<T> extends CO<T> {
        /** */
        @GridInstanceResource
        private Grid grid;

        /** */
        private Object key;

        /** */
        private GridClosure<Object, T> c;

        /**
         * @param key Node local storage key to get value for closure by.
         * @param c Closure to run with value from node local storage.
         */
        NodeLocalCallClosure(Object key, GridClosure<Object, T> c) {
            this.key = key;
            this.c = c;
        }

        /** {@inheritDoc} */
        @Override public T apply() {
            return c.apply(grid.nodeLocal().get(key));
        }
    }

    /** {@inheritDoc} */
    @Override public <T> GridFuture<T> nodeLocalCallAsync(Object key, @Nullable GridClosure<Object, T> c)
        throws GridException {
        guard();

        try {
            if (c != null) {
                Callable<T> co = new NodeLocalCallClosure<T>(key, c);

                return isLocal ? ctx.closure().callLocal(co) : ctx.closure().callAsync(UNICAST, co, nodes);
            }

            return new GridFinishedFuture<T>(ctx);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public UUID getId() {
        return node.id();
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return node.order();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Deprecated
    @Override public <T> T getAttribute(String name) {
        return this.<T>attribute(name);
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public GridNodeMetrics getMetrics() {
        return metrics();
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public Map<String, Object> getAttributes() {
        return attributes();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("RedundantTypeArguments")
    @Override public <T> T attribute(String name) {
        return node.<T>attribute(name);
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics metrics() {
        return node.metrics();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> attributes() {
        return node.attributes();
    }

    /** {@inheritDoc} */
    @Override public Collection<String> internalAddresses() {
        return node.internalAddresses();
    }

    /** {@inheritDoc} */
    @Override public Collection<String> externalAddresses() {
        return node.externalAddresses();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override public String getPhysicalAddress() {
        return node.getPhysicalAddress();
    }

    /** {@inheritDoc} */
    @Override public boolean dynamic() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridRichNodeImpl.class, this);
    }
}
