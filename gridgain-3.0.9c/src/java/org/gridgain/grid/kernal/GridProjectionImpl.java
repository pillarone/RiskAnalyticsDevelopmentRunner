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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridProjectionImpl extends GridProjectionAdapter implements Externalizable {
    /** Type alias. */
    private static class Stash extends GridTuple3<String, GridPredicate<GridRichNode>, Boolean> {}

    /** */
    private static final ThreadLocal<Stash> stash = new ThreadLocal<Stash>() {
        @Override protected Stash initialValue() {
            return new Stash();
        }
    };

    /** */
    private GridPredicate<GridRichNode> p;

    /** */
    private int hash = -1;

    /** */
    private boolean dynamic;

    /** */
    private UUID[] ids;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridProjectionImpl() {
        super(null);
    }

    /**
     * Creates static projection.
     *
     * @param parent Parent projection.
     * @param ctx Kernal context.
     * @param nodes Collection of nodes for this subgrid.
     */
    public GridProjectionImpl(GridProjection parent, GridKernalContext ctx, Collection<GridRichNode> nodes) {
        super(parent, ctx);

        assert nodes != null;

        ids = U.toArray(F.nodeIds(nodes), new UUID[nodes.size()]);

        p = new GridNodePredicate<GridRichNode>(ids);

        dynamic = false;
    }

    /**
     * Creates dynamic projection.
     *
     * @param parent Projection.
     * @param ctx Kernal context.
     * @param p Predicate.
     */
    public GridProjectionImpl(GridProjection parent, GridKernalContext ctx,
        final GridPredicate<? super GridRichNode> p) {
        super(parent, ctx);

        assert p != null;

        this.p = U.withMeta(new PN() {
            {
                peerDeployLike(p);    
            }

            @Override public boolean apply(GridRichNode n) {
                return p.apply(n);
            }
        }, p);

        dynamic = true;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
        out.writeObject(p);
        out.writeBoolean(dynamic);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple3<String, GridPredicate<GridRichNode>, Boolean> t = stash.get();

        t.set1(U.readString(in));
        t.set2((GridPredicate<GridRichNode>)in.readObject());
        t.set3(in.readBoolean());
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            GridTuple3<String, GridPredicate<GridRichNode>, Boolean> t = stash.get();

            Grid g = G.grid(t.get1());

            // Preserve dynamic nature of the subgrid on demarshalling.
            return t.get3() ? g.projectionForPredicate(t.get2()) : g.projectionForNodes(g.nodes(t.get2()));
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
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
    @Override public int hashCode() {
        // Dynamic subgrids use their predicates' hash code.
        return dynamic ? p.hashCode() : hash == -1 ? hash = Arrays.hashCode(ids) : hash;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof GridProjectionImpl)) {
            return false;
        }

        GridProjectionImpl obj = (GridProjectionImpl)o;

        // Dynamic subgrids use equality of their predicates.
        // For non-dynamic subgrids we use *initial* node IDs. The assumption
        // is that if the node leaves - it leaves all subgrids, and new node can't join existing
        // non-dynamic subgrid. Therefore, it is safe and effective to compare two non-dynamic
        // subgrids by their initial set of node IDs.
        return dynamic ? obj.dynamic && p.equals(obj.p) : !obj.dynamic && Arrays.equals(ids, obj.ids);
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(GridPredicate<? super GridRichNode>[] p) {
        guard();

        try {
            return dynamic ?
                F.view(F.viewReadOnly(ctx.discovery().allNodes(), ctx.rich().richNode()), F.and(p, this.p)) :
                F.view(F.viewReadOnly(ctx.discovery().nodes(F.asList(ids)), ctx.rich().richNode()), F.and(p, this.p));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean dynamic() {
        return dynamic;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridProjectionImpl.class, this);
    }
}