// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Convenience class representing mutable tuple of three values.
 * <h2 class="header">Thread Safety</h2>
 * This class doesn't provide any synchronization for multi-threaded access and it is
 * responsibility of the user of this class to provide outside synchronization, if needed.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridFunc#t5()
 * @see GridFunc#t(Object, Object, Object, Object, Object)
 */
public class GridTuple5<V1, V2, V3, V4, V5> extends GridMetadataAwareAdapter implements GridProduct,
    Externalizable {
    /** Value 1. */
    @GridToStringInclude
    private V1 v1;

    /** Value 2. */
    @GridToStringInclude
    private V2 v2;

    /** Value 3. */
    @GridToStringInclude
    private V3 v3;

    /** Value 4. */
    @GridToStringInclude
    private V4 v4;

    /** Value 5. */
    @GridToStringInclude
    private V5 v5;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridTuple5() {
        // No-op.
    }

    /**
     * Fully initializes this tuple.
     *
     * @param v1 First value.
     * @param v2 Second value.
     * @param v3 Third value.
     * @param v4 Forth value.
     * @param v5 Fifth value.
     */
    public GridTuple5(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;
        this.v5 = v5;
    }

    /** {@inheritDoc} */
    @Override public int arity() {
        return 5;
    }

    /** {@inheritDoc} */
    @Override public Object part(int n) {
        switch (n) {
            case 0: return get1();
            case 1: return get2();
            case 2: return get3();
            case 3: return get4();
            case 4: return get5();

            default:
                throw new IndexOutOfBoundsException("Invalid product index: " + n);
        }
    }

    /**
     * Gets first value.
     *
     * @return First value.
     */
    public V1 get1() {
        return v1;
    }

    /**
     * Gets second value.
     *
     * @return Second value.
     */
    public V2 get2() {
        return v2;
    }

    /**
     * Gets third value.
     *
     * @return Third value.
     */
    public V3 get3() {
        return v3;
    }

    /**
     * Gets forth value.
     *
     * @return Forth value.
     */
    public V4 get4() {
        return v4;
    }

    /**
     * Gets fifth value.
     *
     * @return Fifth value.
     */
    public V5 get5() {
        return v5;
    }

    /**
     * Sets first value.
     *
     * @param v1 First value.
     */
    public void set1(V1 v1) {
        this.v1 = v1;
    }

    /**
     * Sets second value.
     *
     * @param v2 Second value.
     */
    public void set2(V2 v2) {
        this.v2 = v2;
    }

    /**
     * Sets third value.
     *
     * @param v3 Third value.
     */
    public void set3(V3 v3) {
        this.v3 = v3;
    }

    /**
     * Sets forth value.
     *
     * @param v4 Forth value.
     */
    public void set4(V4 v4) {
        this.v4 = v4;
    }

    /**
     * Sets fifth value.
     *
     * @param v5 Fifth value.
     */
    public void set5(V5 v5) {
        this.v5 = v5;
    }


    /**
     * Sets all values.
     *
     * @param val1 First value.
     * @param val2 Second value.
     * @param val3 Third value.
     * @param val4 Fourth value.
     * @param val5 Fifth value.
     */
    public void set(V1 val1, V2 val2, V3 val3, V4 val4, V5 val5) {
        set1(val1);
        set2(val2);
        set3(val3);
        set4(val4);
        set5(val5);
    }

    /** {@inheritDoc} */
    @Override public Iterator<Object> iterator() {
        return new Iterator<Object>() {
            private int nextIdx = 1;

            @Override public boolean hasNext() {
                return nextIdx < 6;
            }

            @Nullable @Override public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                Object res = null;
                
                if (nextIdx == 1) {
                    res = get1();
                }
                else if (nextIdx == 2) {
                    res = get2();
                }
                else if (nextIdx == 3) {
                    res = get3();
                }
                else if (nextIdx == 4) {
                    res = get4();
                }
                else if (nextIdx == 5) {
                    res = get5();
                }

                nextIdx++;

                return res;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"CloneDoesntDeclareCloneNotSupportedException"})
    @Override public Object clone() {
        return super.clone();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(v1);
        out.writeObject(v2);
        out.writeObject(v3);
        out.writeObject(v4);
        out.writeObject(v5);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        v1 = (V1)in.readObject();
        v2 = (V2)in.readObject();
        v3 = (V3)in.readObject();
        v4 = (V4)in.readObject();
        v5 = (V5)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (!(o instanceof GridTuple5)) {
            return false;
        }

        GridTuple5<?, ?, ?, ?, ?> t = (GridTuple5<?, ?, ?, ?, ?>)o;

        return F.eq(v1, t.v2) && F.eq(v2, t.v2) && F.eq(v3, t.v3) && F.eq(v4, t.v4) && F.eq(v5, t.v5);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = v1 != null ? v1.hashCode() : 0;

        res = 13 * res + (v2 != null ? v2.hashCode() : 0);
        res = 17 * res + (v3 != null ? v3.hashCode() : 0);
        res = 19 * res + (v4 != null ? v4.hashCode() : 0);
        res = 31 * res + (v5 != null ? v5.hashCode() : 0);

        return res;
    }

    /** {@inheritDoc} */
    @Override public String toString() { return S.toString(GridTuple5.class, this); }
}
