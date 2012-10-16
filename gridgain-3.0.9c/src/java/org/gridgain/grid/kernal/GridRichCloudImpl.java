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
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridRichCloudImpl extends GridProjectionAdapter implements GridRichCloud, Externalizable {
    /** Type alias. */
    private static class Stash extends GridTuple2<String, String> {}

    /** */
    private static final ThreadLocal<Stash> stash = new ThreadLocal<Stash>() {
        @Override protected Stash initialValue() {
            return new Stash();
        }
    };

    /**
     * Type alias for Resource Hash Set (RHS).
     */
    private static class RHS extends HashSet<GridCloudResource> {}

    /** */    
    private static final GridClosure3<RHS, GridCloudResource, GridPredicate<? super GridCloudResource>[], RHS> traverse =
        new C3<RHS, GridCloudResource, GridPredicate<? super GridCloudResource>[], RHS>() {
            @Override public RHS apply(RHS rhs, GridCloudResource r, GridPredicate<? super GridCloudResource>[] p) {
                assert rhs != null;
                assert r != null;

                if (F.isAll(r, p)) {
                    rhs.add(r);
                }

                Collection<GridCloudResource> links = r.links();

                if (!F.isEmpty(links)) {
                    assert links != null;

                    for (GridCloudResource link : links) {
                        apply(rhs, link, p);
                    }
                }

                return rhs;
            }
        };

    /** */
    private GridCloud cloud;

    /** */
    private int hash;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridRichCloudImpl() {
        super(null);
    }

    /**
     * Creates new rich cloud.
     *
     * @param ctx Kernal context
     * @param cloud Newly created rich cloud.
     */
    @SuppressWarnings({"unchecked"})
    public GridRichCloudImpl(GridKernalContext ctx, GridCloud cloud) {
        super(ctx.grid(), ctx);

        assert cloud != null;

        this.cloud = cloud;

        hash = cloud.hashCode();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, ctx.gridName());
        U.writeString(out, cloud.id());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple2<String, String> t = stash.get();

        t.set1(U.readString(in));
        t.set2(U.readString(in));
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            GridTuple2<String, String> t = stash.get();

            Grid g = G.grid(t.get1());

            GridCloud c = g.cloud(t.get2());

            return c == null ? null : g.rich(c);
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /**
     * Gets the original wrapped grid cloud.
     *
     * @return Original wrapped grid cloud.
     */
    public GridCloud originalCloud() {
        return cloud;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCloudResource> resources(@Nullable GridCloudResource root,
        @Nullable GridPredicate<? super GridCloudResource>... p) {
        guard();

        try {
            if (root != null && !root.cloudId().equals(id())) {
                throw new GridRuntimeException("Cloud resource doesn't belong to this cloud: " + root);
            }

            return root == null ? resources(p) : traverse.apply(new RHS(), root, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCloudResource> resources(GridPredicate<? super GridCloudResource>... p) {
        return cloud.resources(p);
    }

    /** {@inheritDoc} */
    @Override public GridProjection projectionForResources(@Nullable GridCloudResource root,
        @Nullable GridPredicate<? super GridRichNode>... p) {
        guard();

        try {
            if (root != null && !root.cloudId().equals(id())) {
                throw new GridRuntimeException("Cloud resource doesn't belong to this cloud: " + root);
            }
            
            return new GridProjectionImpl(this, ctx, F.and(p, F.nodeForNodeIds(nodeIds(root))));
        }
        finally {
            unguard();
        }
    }

    /**
     * Gets node IDs from the subset of resources on this cloud.
     *
     * @param root Optional resource root. If not provided - all resources in this
     *      cloud will be considered.
     * @return Collection of node IDs from the subset of resource on this cloud.
     */
    private Collection<UUID> nodeIds(@Nullable GridCloudResource root) {
        return F.transform(resources(root, F.resources(GridCloudResourceType.CLD_NODE)),
            new C1<GridCloudResource, UUID>() {
                @Override public UUID apply(GridCloudResource r) {
                    return UUID.fromString(r.id());
                }
            }
        );
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<GridRichNode> predicate() {
        guard();

        try {
            return new GridNodePredicate<GridRichNode>(nodeIds(null));          
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridCloud o) {
        return o == null ? 1 : cloud.id().compareTo(o.id());
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        assert cloud != null;

        return this == o || o instanceof GridRichCloudImpl && cloud.equals(((GridRichCloudImpl)o).cloud);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return hash;
    }
    
    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(GridPredicate<? super GridRichNode>... p) {
        guard();

        try {
            return F.retain(F.viewReadOnly(ctx.discovery().allNodes(), ctx.rich().richNode()), true,
                F.and(p, predicate()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public UUID invoke(GridCloudCommand cmd) throws GridException {
        guard();

        try {
            UUID eid = F.first(ctx.cloud().invoke(id(), F.asList(cmd)));

            assert eid != null;

            return eid;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> invoke(@Nullable Collection<? extends GridCloudCommand> cmds)
        throws GridException {
        guard();

        try {
            return F.isEmpty(cmds) ? Collections.<UUID>emptyList() : ctx.cloud().invoke(id(), cmds);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public String id() {
        guard();

        try {
            return cloud.id();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> parameters() {
        guard();

        try {
            return cloud.parameters();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean dynamic() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridRichCloudImpl.class, this);
    }
}
