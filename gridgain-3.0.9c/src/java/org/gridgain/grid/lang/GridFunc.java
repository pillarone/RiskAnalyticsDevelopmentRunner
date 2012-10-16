// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * <img id="callout_img" src="{@docRoot}/img/callout_blue.gif"><span id="callout_blue">Start Here</span>&nbsp;
 * Contains factory and utility methods for {@code closures}, {@code predicates}, and {@code tuples}.
 * It also contains functional style collection comprehensions.
 * <p>
 * Most of the methods in this class can be divided into two groups:
 * <ul>
 * <li><b>Factory</b> higher-order methods for closures, predicates and tuples, and</li>
 * <li><b>Utility</b> higher-order methods for processing collections with closures and predicates.</li>
 * </ul>
 * Note that contrary to the usual design this class has substantial number of
 * methods (over 200). This design is chosen to simulate a global namespace
 * (like a {@code Predef} in Scala) to provide general utility and functional
 * programming functionality in a shortest syntactical context using {@code F}
 * typedef.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridFunc {
    /** */
    private static final Random rand = new Random();

    /** */
    private static final GridAbsClosure NOOP = new CA() {
        @Override public void apply() { /* No-op. */ }
    };

    /** */
    private static final GridClosure IDENTITY = new C1() {
        @Override public Object apply(Object o) {
            return o;
        }

        @Override public String toString() {
            return "Identity closure.";
        }
    };

    /** */
    private static final GridPredicate<Object> ALWAYS_TRUE = new P1<Object>() {
        @Override public boolean apply(Object e) {
            return true;
        }

        @Override public String toString() {
            return "Always true predicate.";
        }
    };

    /** */
    private static final GridPredicate<Object> ALWAYS_FALSE = new P1<Object>() {
        @Override public boolean apply(Object e) {
            return false;
        }

        @Override public String toString() {
            return "Always false predicate.";
        }
    };

    /** */
    private static final GridOutClosure<?> LIST_FACTORY = new CO<List>() {
        @Override public List apply() {
            return new ArrayList();
        }

        @Override public String toString() {
            return "Array list factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> LINKED_LIST_FACTORY = new CO<LinkedList>() {
        @Override public LinkedList apply() {
            return new LinkedList();
        }

        @Override public String toString() {
            return "Linked list factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> SET_FACTORY = new CO<Set>() {
        @Override public Set apply() {
            return new HashSet();
        }

        @Override public String toString() {
            return "Hash set factory.";
        }
    };

    /** */
    private static final GridOutClosure<AtomicInteger> ATOMIC_INT_FACTORY = new CO<AtomicInteger>() {
        @Override public AtomicInteger apply() {
            return new AtomicInteger(0);
        }

        @Override public String toString() {
            return "Atomic integer factory.";
        }
    };

    /** */
    private static final GridOutClosure<AtomicLong> ATOMIC_LONG_FACTORY = new CO<AtomicLong>() {
        @Override public AtomicLong apply() {
            return new AtomicLong(0);
        }

        @Override public String toString() {
            return "Atomic long factory.";
        }
    };

    /** */
    private static final GridOutClosure<AtomicBoolean> ATOMIC_BOOL_FACTORY = new CO<AtomicBoolean>() {
        @Override public AtomicBoolean apply() {
            return new AtomicBoolean();
        }

        @Override public String toString() {
            return "Atomic boolean factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> ATOMIC_REF_FACTORY = new CO<AtomicReference>() {
        @Override public AtomicReference apply() {
            return new AtomicReference();
        }

        @Override public String toString() {
            return "Atomic reference factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> MAP_FACTORY = new CO<Map>() {
        @Override public Map apply() {
            return new HashMap();
        }

        @Override public String toString() {
            return "Hash map factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> CONCURRENT_MAP_FACTORY = new CO<ConcurrentMap>() {
        @Override public ConcurrentMap apply() {
            return new ConcurrentHashMap();
        }

        @Override public String toString() {
            return "Concurrent hash map factory.";
        }
    };

    /** */
    private static final GridOutClosure<?> CONCURRENT_SET_FACTORY = new CO<GridConcurrentHashSet>() {
        @Override public GridConcurrentHashSet apply() {
            return new GridConcurrentHashSet();
        }

        @Override public String toString() {
            return "Concurrent hash set factory.";
        }
    };

    /** */
    private static final GridInClosure<?> PRINTLN = new CI1() {
        @Override public void apply(Object o) {
            System.out.println(o);
        }

        @Override public String toString() {
            return "Print line closure.";
        }
    };

    /** */
    private static final GridInClosure<?> PRINT = new CI1() {
        @Override public void apply(Object o) {
            System.out.print(o);
        }

        @Override public String toString() {
            return "Print closure.";
        }
    };

    /** */
    private static final GridOutClosure<?> NILL = new CO() {
        @Nullable @Override public Object apply() {
            return null;
        }

        @Override public String toString() {
            return "Nill closure.";
        }
    };

    /** */
    private static final GridClosure<Runnable, GridAbsClosure> R2C = new C1<Runnable, GridAbsClosure>() {
        @Override public GridAbsClosure apply(Runnable r) {
            return as(r);
        }

        @Override public String toString() {
            return "Runnable to absolute closure transformer.";
        }
    };

    /** */
    private static final GridClosure<GridProjection, GridPredicate<GridRichNode>> P2P =
        new C1<GridProjection, GridPredicate<GridRichNode>>() {
            @Override public GridPredicate<GridRichNode> apply(GridProjection e) {
                return e.predicate();
            }

            @Override public String toString() {
                return "Projection to its predicate transformer closure.";
            }
    };

    /** */
    private static final GridClosure<Object, Class<?>> CLAZZ = new C1<Object, Class<?>>() {
        @Override public Class<?> apply(Object o) {
            return o.getClass();
        }

        @Override public String toString() {
            return "Object to class transformer closure.";
        }
    };

    /** */
    private static final GridClosure MAP_ENTRY_KEY = new GridClosure() {
        @Override public Object apply(Object o) {
            return ((Map.Entry)o).getKey();
        }

        @Override public String toString() {
            return "Map entry to key transformer closure.";
        }
    };

    /** */
    private static final GridClosure MAP_ENTRY_VAL = new GridClosure() {
        @Override public Object apply(Object o) {
            return ((Map.Entry)o).getValue();
        }

        @Override public String toString() {
            return "Map entry to value transformer closure.";
        }
    };

    /** */
    private static final GridClosure CACHE_ENTRY_VAL_GET = new GridClosure() {
        @SuppressWarnings({"unchecked"})
        @Nullable @Override public Object apply(Object o) {
            try {
                return ((GridCacheEntry)o).get();
            }
            catch (GridException e) {
                throw new GridClosureException(e);
            }
        }

        @Override public String toString() {
            return "Cache entry to get-value transformer closure.";
        }
    };

    /** */
    private static final GridClosure CACHE_ENTRY_VAL_PEEK = new GridClosure() {
        @SuppressWarnings({"unchecked"})
        @Nullable @Override public Object apply(Object o) {
            return ((GridCacheEntry<?, ?>)o).peek();
        }

        @Override public String toString() {
            return "Cache entry to peek-value transformer closure.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_HAS_GET_VAL = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            try {
                return ((GridCacheEntry)o).get() != null;
            }
            catch (GridException e) {
                throw new GridClosureException(e);
            }
        }

        @Override public String toString() {
            return "Cache entry has-get-value predicate.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_NO_GET_VAL = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            try {
                return ((GridCacheEntry)o).get() == null;
            }
            catch (GridException e) {
                throw new GridClosureException(e);
            }
        }

        @Override public String toString() {
            return "Cache entry no-get-value predicate.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_HAS_PEEK_VAL = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            return ((GridCacheEntry)o).hasValue();
        }

        @Override public String toString() {
            return "Cache entry has-peek-value predicate.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_NO_PEEK_VAL = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            return !((GridCacheEntry)o).hasValue();
        }

        @Override public String toString() {
            return "Cache entry no-peek-value predicate.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_PRIMARY = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            return ((GridCacheEntry)o).primary();
        }

        @Override public String toString() {
            return "Cache primary entry predicate.";
        }
    };

    /** */
    private static final GridPredicate CACHE_ENTRY_BACKUP = new GridPredicate() {
        @SuppressWarnings({"unchecked"})
        @Override public boolean apply(Object o) {
            return !((GridCacheEntry)o).primary();
        }

        @Override public String toString() {
            return "Cache backup entry predicate.";
        }
    };

    /** */
    private static final GridClosure<GridNode, UUID> NODE2ID = new GridClosure<GridNode, UUID>() {
        @Override public UUID apply(GridNode e) {
            return e.id();
        }

        @Override public String toString() {
            return "Grid node to node ID transformer closure.";
        }
    };

    /** */
    private static final GridPredicate<GridFuture<?>> FINISHED_FUTURE = new GridPredicate<GridFuture<?>>() {
        @Override public boolean apply(GridFuture<?> f) {
            return f.isDone();
        }
    };

    /** */
    private static final GridPredicate<GridFuture<?>> UNFINISHED_FUTURE = new GridPredicate<GridFuture<?>>() {
        @Override public boolean apply(GridFuture<?> f) {
            return !f.isDone();
        }
    };

    /**
     * Gets breaker predicate which will return a predicate that will
     * evaluate to {@code firstVal} when checked the first time,
     * but then will always evaluate to the opposite value.
     *
     * @param firstVal First value.
     * @param <T> Predicate type.
     * @return Breaker predicate.
     */
    public static <T> GridPredicate<T> breaker(final boolean firstVal) {
        return new GridPredicate<T>() {
            private boolean b = true;

            @Override public boolean apply(T e) {
                if (b) {
                    b = false;

                    return firstVal;
                }

                return !firstVal;
            }

            @Override public String toString() {
                return "Breaker predicate.";
            }
        };
    }

    /**
     * Gets closure that transform a grid projection into its predicate.
     *
     * @return Closure transforming a grid projection into its predicate.
     */
    public static GridClosure<GridProjection, GridPredicate<GridRichNode>> predicate() {
        return P2P;
    }

    /**
     * Gets predicate that evaluates to {@code true} only for given local node ID.
     *
     * @param localNodeId Local node ID.
     * @param <T> Type of the node.
     * @return Return {@code true} only for the node with given local node ID.
     */
    public static <T extends GridNode> GridPredicate<T> localNode(final UUID localNodeId) {
        return new P1<T>() {
            @SuppressWarnings("deprecation")
            @Override public boolean apply(T n) {
                return n.getId().equals(localNodeId);
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code false} for given local node ID.
     *
     * @param locNodeId Local node ID.
     * @param <T> Type of the node.
     * @return Return {@code false} for the given local node ID.
     */
    public static <T extends GridNode> GridPredicate<T> remoteNodes(UUID locNodeId) {
        return not(GridFunc.<T>localNode(locNodeId));
    }

    /**
     * Returns out closure that always returns {@code null}.
     *
     * @return Out closure that always returns {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOutClosure<T> nill() {
        return (GridOutClosure<T>)NILL;
    }

    /**
     * Creates closure that will reflectively call a method with the given name on
     * closure's argument and return result of that call.
     * <p>
     * Method reflects the typedef for {@link GridClosure} which is {@link C1}.
     *
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @param <R> Type of closure return value.
     * @param <T> Type of closure argument.
     * @return Reflective closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static <T, R> GridClosure<T, R> cInvoke(final String mtdName, final Object... args) {
        A.notNull(mtdName, "mtdName");

        return new C1<T, R>() {
            private Method mtd;

            @SuppressWarnings("unchecked")
            @Override public R apply(T t) {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(t.getClass(), mtdName, args);

                        mtd.setAccessible(true);
                    }

                    return (R)mtd.invoke(t, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Creates in closure that will reflectively call a method with the given name on
     * closure's argument.
     * <p>
     * Method reflects the typedef for {@link GridClosure} which is {@link C1}.
     *
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @param <T> Type of closure argument.
     * @return Reflective in closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static <T> GridInClosure<T> ciInvoke(final String mtdName, final Object... args) {
        A.notNull(mtdName, "mtdName");

        return new CI1<T>() {
            private Method mtd;

            @Override public void apply(T t) {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(t.getClass(), mtdName, args);

                        mtd.setAccessible(true);
                    }

                    mtd.invoke(t, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Creates out closure that will reflectively call a method with the given name on provided
     * object and return result of that call.
     * <p>
     * Method reflects the typedef for {@link GridOutClosure} which is {@link CO}.
     *
     * @param o Target object to call the method on.
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @param <R> Type of closure return value.
     * @return Reflective out closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static <R> GridOutClosure<R> coInvoke(final Object o, final String mtdName, final Object... args) {
        A.notNull(o, "o", mtdName, "mtdName");

        return new CO<R>() {
            private Method mtd;

            @SuppressWarnings("unchecked")
            @Override public R apply() {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(o.getClass(), mtdName, args);

                        mtd.setAccessible(true);
                    }

                    return (R)mtd.invoke(o, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Creates absolute closure that will reflectively call a method with the given name on provided object.
     * <p>
     * Method reflects the typedef for {@link GridAbsClosure} which is {@link CA}.
     *
     * @param o Target object to call the method on.
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @return Reflective absolute closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static GridAbsClosure caInvoke(final Object o, final String mtdName, @Nullable final Object... args) {
        A.notNull(o, "o", mtdName, "mtdName");

        return new CA() {
            private Method mtd;

            @SuppressWarnings("unchecked")
            @Override public void apply() {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(o.getClass(), mtdName, args);

                        mtd.setAccessible(true);
                    }

                    mtd.invoke(o, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Creates out closure that will reflectively call a static method with the given name
     * and return result of that call.
     * <p>
     * Method reflects the typedef for {@link GridOutClosure} which is {@link CO}.
     *
     * @param cls Class to call a static method on.
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @param <R> Type of closure return value.
     * @return Reflective out closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static <R> GridOutClosure<R> coInvoke(final Class<?> cls, final String mtdName,
        @Nullable final Object... args) {
        A.notNull(cls, "cls", mtdName, "mtdName");

        return new CO<R>() {
            private Method mtd;

            @SuppressWarnings("unchecked")
            @Override public R apply() {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(cls, mtdName, args);

                        mtd.setAccessible(true);
                    }

                    return (R)mtd.invoke(null, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Creates absolute closure that will reflectively call a static method with the given name.
     * <p>
     * Method reflects the typedef for {@link GridAbsClosure} which is {@link CA}.
     *
     * @param cls Class to call a static method on.
     * @param mtdName Method name.
     * @param args Optional set of arguments for the method call.
     * @return Reflective absolute closure.
     * @throws GridClosureException Thrown in case of any reflective invocation errors.
     */
    public static GridAbsClosure caInvoke(final Class<?> cls, final String mtdName, @Nullable final Object... args) {
        A.notNull(cls, "cls", mtdName, "mtdName");

        return new CA() {
            private Method mtd;

            @SuppressWarnings("unchecked")
            @Override public void apply() {
                try {
                    // No synchronization allows for double creation - ignoring...
                    if (mtd == null) {
                        mtd = method(cls, mtdName, args);

                        mtd.setAccessible(true);
                    }

                    mtd.invoke(null, args);
                }
                catch (Throwable e) {
                    throw wrap(e);
                }
            }
        };
    }

    /**
     * Looks up the method with given parameters.
     *
     * @param cls Class to look up in.
     * @param mtdName Method name to look up.
     * @param args Optional set of method parameters.
     * @return Method instance.
     * @throws Exception Thrown in case of any reflective errors.
     */
    private static Method method(Class<?> cls, String mtdName, @Nullable Object... args) throws Exception {
        assert cls != null;
        assert mtdName != null;

        int cnt = 0;

        Method m = null;

        for (Method mtd : cls.getDeclaredMethods())
            if (mtd.getName().equals(mtdName)) {
                cnt++;

                m = mtd;
            }

        if (cnt == 0) {
            throw new NoSuchMethodException(cls.getName() + '#' + mtdName);
        }

        // If there is only one method with provided name we
        // don't use lookup that requires parameters' types since
        // it is a lot more complex to deal with type inheritance there.
        if (cnt == 1) {
            return m;
        }

        if (!isEmpty(args)) {
            assert args != null;

            Class<?>[] types = new Class[args.length];

            int i = 0;

            for (Object arg : args) {
                // This is not going to work in cases when method expects
                // an interface or supertype. Accept this limitation for now...
                types[i++] = arg.getClass();
            }

            return cls.getDeclaredMethod(mtdName, types);
        }
        else {
            return cls.getDeclaredMethod(mtdName);
        }
    }

    /**
     * Gets closure that converts object to its runtime class.
     *
     * @return Closure that converts object to its runtime class.
     */
    public static GridClosure<Object, Class<?>> clazz() {
        return CLAZZ;
    }

    /**
     * Creates new collection by removing duplicates from the given collection.
     *
     * @param c Collection to remove duplicates from.
     * @param <T> Type of the collection.
     * @return De-duped collection.
     */
    public static <T> Collection<T> dedup(Collection<? extends T> c) {
        A.notNull(c, "c");

        Collection<T> set = new GridLeanSet<T>();

        set.addAll(c);

        return set;
    }

    /**
     * Calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @param c Collection of elements.
     * @return Sum of all elements.
     */
    public static int sum(Iterable<Integer> c) {
        A.notNull(c, "c");

        int sum = 0;

        for (int t : c) {
            sum += t;
        }

        return sum;
    }

    /**
     * Calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @param c Collection of elements.
     * @return Sum of all elements.
     */
    public static double sum(Iterable<Double> c) {
        A.notNull(c, "c");

        double sum = 0;

        for (double t : c) {
            sum += t;
        }

        return sum;
    }

    /**
     * Calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @param c Collection of elements.
     * @return Sum of all elements.
     */
    public static BigDecimal sum(Iterable<BigDecimal> c) {
        A.notNull(c, "c");

        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal t : c) {
            sum = sum.add(t);
        }

        return sum;
    }

    /**
     * Calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @param c Collection of elements.
     * @return Sum of all elements.
     */
    public static BigInteger sum(Iterable<BigInteger> c) {
        A.notNull(c, "c");

        BigInteger sum = BigInteger.ZERO;

        for (BigInteger t : c) {
            sum = sum.add(t);
        }

        return sum;
    }

    /**
     * Calculates arithmetic mean.
     * <p>
     * <img src="{@docRoot}/img/avg.png">
     *
     * @param c Input collection.
     * @return Arithmetic mean of the input collection.
     */
    public static double avg(Iterable<? extends Number> c) {
        A.notNull(c, "c");

        double sum = 0;

        int i = 0;

        for (Number t : c) {
            sum += t.doubleValue();

            i++;
        }

        return sum / i;
    }

    /**
     * Gets reducer closure that calculates arithmetic mean.
     * <p>
     * <img src="{@docRoot}/img/avg.png">
     *
     * @return Reducer closure that calculated arithmetic mean.
     */
    public static <T extends Number> GridReducer<T, Double> avgReducer() {
        return new R1<T, Double>() {
            private double sum;
            private int i;

            @Override public boolean collect(T e) {
                sum += e.doubleValue();
                i++;

                return true;
            }

            @Override public Double apply() {
                return sum / i;
            }
        };
    }

    /**
     * Calculates quadratic mean.
     * <p>
     * <img src="{@docRoot}/img/qavg.png">
     *
     * @param c Input collection.
     * @return Quadratic mean of the input collection.
     */
    public static double qavg(Iterable<? extends Number> c) {
        A.notNull(c, "c");

        double sum = 0;

        int i = 0;

        for (Number t : c) {
            double d = t.doubleValue();

            sum += d * d;

            i++;
        }

        return Math.sqrt(sum / i);
    }

    /**
     * Gets reducer closure that calculates quadratic mean.
     * <p>
     * <img src="{@docRoot}/img/qavg.png">
     *
     * @return Reducer closure that calculated quadratic mean.
     */
    public static <T extends Number> GridReducer<T, Double> qavgReducer() {
        return new R1<T, Double>() {
            private double sum;
            private int i;

            @Override public boolean collect(T e) {
                double d = e.doubleValue();

                sum += d * d;

                i++;

                return true;
            }

            @Override public Double apply() {
                return Math.sqrt(sum / i);
            }
        };
    }

    /**
     * Calculates geometric mean.
     * <p>
     * <img src="{@docRoot}/img/gavg.png">
     *
     * @param c Input collection.
     * @return Geometric mean of the input collection.
     */
    public static double gavg(Iterable<? extends Number> c) {
        A.notNull(c, "c");

        double sum = 0;

        int i = 0;

        for (Number t : c) {
            sum *= t.doubleValue();

            i++;
        }

        return Math.pow(sum, 1f / i);
    }

    /**
     * Gets reducer closure that calculates geometric mean.
     * <p>
     * <img src="{@docRoot}/img/gavg.png">
     *
     * @return Reducer closure that calculated geometric mean.
     */
    public static <T extends Number> GridReducer<T, Double> gavgReducer() {
        return new R1<T, Double>() {
            private double sum;
            private int i;

            @Override public boolean collect(T e) {
                sum *= e.doubleValue();

                i++;

                return true;
            }

            @Override public Double apply() {
                return Math.pow(sum, 1f / i);
            }
        };
    }

    /**
     * Calculates weighted mean.
     * <p>
     * <img src="{@docRoot}/img/wavg.png">
     *
     * @param c Collection of elements.
     * @param w Collection of weights.
     * @return Weighted mean of the input collection.
     */
    public static double wavg(Collection<? extends Number> c, Collection<? extends Number> w) {
        A.notNull(c, "c", w, "w");
        A.ensure(c.size() == w.size(), "c.size() == w.size()");

        double sumC = 0;
        double sumW = 0;

        Iterator<? extends Number> iterC = c.iterator();
        Iterator<? extends Number> iterW = w.iterator();

        while (iterC.hasNext()) {
            assert iterW.hasNext();

            double dc = iterC.next().doubleValue();
            double dw = iterW.next().doubleValue();

            sumW += dw;
            sumC += dw * dc;
        }

        return sumC / sumW;
    }

    /**
     * Calculates harmonic mean.
     * <p>
     * <img src="{@docRoot}/img/havg.png">
     *
     * @param c Input collection.
     * @return Harmonic mean of the input collection.
     */
    public static double havg(Iterable<? extends Number> c) {
        A.notNull(c, "c");

        double sum = 0;

        int i = 0;

        for (Number t : c) {

            sum += 1 / t.doubleValue();

            i++;
        }

        return i / sum;
    }

    /**
     * Gets reducer closure that collects only a single value and returns it
     * without any transformations.
     *
     * @return Reducer closure that collects and returns single value.
     */
    public static <T> GridReducer<T, T> singleReducer() {
        return new R1<T, T>() {
            private T obj;

            @Override public boolean collect(T e) {
                obj = e;

                return false;
            }

            @Override public T apply() {
                return obj;
            }
        };
    }

    /**
     * Gets reducer which always returns {@code true} from {@link GridReducer#collect(Object)}
     * method and passed in {@code element} from {@link GridReducer#apply()} method.
     *
     * @param elem Element to return from {@link GridReducer#apply()} method.
     * @param <T> Reducer element type.
     * @param <R> Return element type.
     * @return Passed in element.
     */
    public static <T, R> GridReducer<T, R> continuousReducer(final R elem) {
        return new R1<T, R>() {
            @Override public boolean collect(T e) {
                return true;
            }

            @Override public R apply() {
                return elem;
            }
        };
    }

    /**
     * Gets reducer closure that calculates harmonic mean.
     * <p>
     * <img src="{@docRoot}/img/havg.png">
     *
     * @return Reducer closure that calculated harmonic mean.
     */
    public static <T extends Number> GridReducer<T, Double> havgReducer() {
        return new R1<T, Double>() {
            private double sum;
            private int i;

            @Override public boolean collect(T e) {
                sum += 1 / e.doubleValue();

                i++;

                return true;
            }

            @Override public Double apply() {
                return i / sum;
            }
        };
    }

    /**
     * Gets reducer closure that calculates sum of elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @return Reducer that calculates sum of elements.
     */
    @SuppressWarnings("unchecked")
    public static GridReducer<Integer, Integer> sumIntReducer() {
        return new R1<Integer, Integer>() {
            private int sum;

            @Override public boolean collect(Integer e) {
                sum += e;

                return true;
            }

            @Override public Integer apply() {
                return sum;
            }
        };
    }

    /**
     * Gets reducer closure that calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @return Reducer that calculates sum of all elements.
     */
    @SuppressWarnings("unchecked")
    public static GridReducer<Double, Double> sumDoubleReducer() {
        return new R1<Double, Double>() {
            private double sum;

            @Override public boolean collect(Double e) {
                sum += e;

                return true;
            }

            @Override public Double apply() {
                return sum;
            }
        };
    }

    /**
     * Creates a range list containing numbers in given range.
     *
     * @param fromIncl Inclusive start of the range.
     * @param toExcl Exclusive stop of the range.
     * @return List containing numbers in range.
     */
    public static List<Integer> range(int fromIncl, int toExcl) {
        A.ensure(fromIncl >= 0, "fromIncl >= 0");
        A.ensure(toExcl >= 0, "toExcl >= 0");
        A.ensure(toExcl >= fromIncl, "toExcl > fromIncl");

        if (toExcl == fromIncl)
            return Collections.emptyList();

        List<Integer> list = new ArrayList<Integer>(toExcl - fromIncl);

        for (int i = fromIncl; i < toExcl; i++)
            list.add(i);

        return list;
    }

    /**
     * Gets reducer closure that calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @return Reducer that calculates sum of all elements.
     */
    @SuppressWarnings("unchecked")
    public static GridReducer<BigDecimal, BigDecimal> sumBigDecimalReducer() {
        return new R1<BigDecimal, BigDecimal>() {
            private BigDecimal sum = BigDecimal.ZERO;

            @Override public boolean collect(BigDecimal e) {
                sum = sum.add(e);

                return true;
            }

            @Override public BigDecimal apply() {
                return sum;
            }
        };
    }

    /**
     * Gets reducer closure that calculates sum of all elements.
     * <p>
     * <img src="{@docRoot}/img/sum.png">
     *
     * @return Reducer that calculates sum of all elements.
     */
    @SuppressWarnings("unchecked")
    public static GridReducer<BigInteger, BigInteger> sumBigIntegerReducer() {
        return new R1<BigInteger, BigInteger>() {
            private BigInteger sum = BigInteger.ZERO;

            @Override public boolean collect(BigInteger e) {
                sum = sum.add(e);

                return true;
            }

            @Override public BigInteger apply() {
                return sum;
            }
        };
    }

    /**
     * Gets reducer closure that concatenates strings using provided delimiter.
     *
     * @param delim Delimiter (optional).
     * @return Reducer that concatenates strings using provided delimeter.
     */
    public static GridReducer<String, String> concatReducer(@Nullable final String delim) {
        return new R1<String, String>() {
            private SB sb = new SB();

            private boolean first = true;

            @Override public boolean collect(String s) {
                if (!first && !isEmpty(delim))
                    sb.a(delim);

                sb.a(s);

                first = false;

                return true;
            }

            @Override public String apply() {
                return sb.toString();
            }
        };
    }

    /**
     * Concatenates strings using provided delimiter.
     *
     * @param c Input collection.
     * @param delim Delimiter (optional).
     * @return Concatenated string.
     */
    public static String concat(Iterable<String> c, @Nullable String delim) {
        A.notNull(c, "c");

        boolean first = true;

        SB sb = new SB();

        for (String s : c) {
            if (!first && !isEmpty(delim))
                sb.a(delim);

            sb.a(s);

            first = false;
        }

        return sb.toString();
    }

    /**
     * Gets collections of data items from grid job res casted to specified type.
     * <p>
     * Here's the typical example of how this method is used in {@code reduce()} method
     * implementation (this example sums up all the values of {@code Integer} type):
     * <pre name="code" class="java">
     * public Integer reduce(List&lt;GridJobResult&gt; res) throws GridException {
     *     return F.sum(F.&lt;Integer&gt;jobResults(res));
     * }
     * </pre>
     * <p>
     * Note that this method doesn't create a new collection but simply iterates over the input one.
     *
     * @param res Collection of grid job res.
     * @param <T> Type of the data item to cast to. See {@link GridJobResult#getData()} method.
     * @return Collections of data items casted to type {@code T}.
     * @see GridJobResult#getData()
     */
    public static <T> Collection<T> jobResults(@Nullable Collection<? extends GridJobResult> res) {
        if (isEmpty(res))
            return Collections.emptyList();

        assert res != null;

        Collection<T> c = new ArrayList<T>(res.size());

        for (GridJobResult r : res)
            c.add(r.<T>getData());

        return c;
    }

    /**
     * Convenient utility method that returns collection of node IDs for a given
     * collection of grid nodes.
     * <p>
     * Note that this method doesn't create a new collection but simply iterates
     * over the input one.
     *
     * @param nodes Collection of grid nodes.
     * @return Collection of node IDs for given collection of grid nodes.
     */
    public static Collection<UUID> nodeIds(@Nullable Collection<? extends GridNode> nodes) {
        if (nodes == null || nodes.isEmpty())
            return Collections.emptyList();

        return F.viewReadOnly(nodes, node2id());
    }

    /**
     * Convenient utility method that returns collection of node attributes for a given
     * collection of grid nodes.
     * <p>
     * Note that this method doesn't create a new collection but simply iterates over the input one.
     *
     * @param nodes Collection of grid nodes.
     * @param attr Name of the attribute to return from each node.
     * @param <T> Type of the attribute.
     * @return Collection of node attributes for given collection of grid nodes.
     */
    public static <T> Collection<T> nodeAttributes(Collection<? extends GridNode> nodes, String attr) {
        A.notNull(nodes, "nodes", attr, "attr");

        Collection<T> c = new ArrayList<T>(nodes.size());

        for (GridNode n : nodes)
            c.add(n.<T>attribute(attr));

        return c;
    }

    /**
     * Convenient utility method that returns collections of metadata values for a given
     * collection of metadata aware objects.
     * <p>
     * Note that this method doesn't create a new collection but simply iterates over the input one.
     *
     * @param objs Collection of metadata aware object.
     * @param name Name of the metadata.
     * @param <T> Type of the metadata.
     * @return Collections of metadata value for a given collection of metadata aware objects.
     */
    public static <T> Collection<T> meta(Collection<? extends GridMetadataAware> objs, String name) {
        A.notNull(objs, "objs", name, "attach");

        Collection<T> c = new ArrayList<T>(objs.size());

        for (GridMetadataAware n : objs) {
            c.add(n.<T>meta(name));
        }

        return c;
    }

    /**
     * Gets closure that calls {@code System.out.println()} on its bound variable.
     *
     * @param <T> Type of the bound variable.
     * @return Closure that calls {@code System.out.println()} on its bound variable.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridInClosure<T> println() {
        return (GridInClosure<T>)PRINTLN;
    }

    /**
     * Creates absolute closure that does <tt>System.out.println(msg)</tt>.
     *
     * @param msg Message to print.
     * @return Absolute closure that print message.
     */
    public static GridAbsClosure println(final String msg) {
        return new CA() {
            @Override public void apply() {
                System.out.println(msg);
            }
        };
    }

    /**
     * Creates absolute closure that does <tt>System.out.print(msg)</tt>.
     *
     * @param msg Message to print.
     * @return Absolute closure that print message.
     */
    public static GridAbsClosure print(final String msg) {
        return new CA() {
            @Override public void apply() {
                System.out.print(msg);
            }
        };
    }

    /**
     * Gets closure that prints out its bound variable.
     *
     * @param pre String value to print before each variable.
     * @param post String value to print after each variable.
     * @param <T> Type of the bound variable.
     * @return Closure that calls {@code System.out.print(pre); System.out.print(t); System.out.println(post)}
     *      on its bound variable.
     */
    public static <T> GridInClosure<T> println(@Nullable final String pre, @Nullable final String post) {
        return new CI1<T>() {
            @Override public void apply(T t) {
                String sPre = pre == null ? "" : pre;
                String sPost = post == null ? "" : post;

                System.out.println(sPre + t + sPost);
            }
        };
    }

    /**
     * Gets closure that prints out its bound variable.
     *
     * @param fmt Format string as for {@link PrintStream#printf(String, Object...)} method.
     * @param <T> Type of the bound variable.
     * @return Closure that prints out its bound variable.
     */
    public static <T> GridInClosure<T> printf(final String fmt) {
        return new CI1<T>() {
            @Override public void apply(T t) {
                System.out.printf(fmt, t);
            }
        };
    }

    /**
     * Gets closure that prints out its bound variable
     *
     * @return Closure that prints out its bound variable.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridInClosure<T> print() {
        return (GridInClosure<T>)PRINT;
    }

    /**
     * Gets closure that prints out its bound variable.
     *
     * @param pre String value to print before each variable.
     * @param post String value to print after each variable.
     * @return Closure that prints out its bound variable.
     */
    public static <T> GridInClosure<T> print(@Nullable final String pre, @Nullable final String post) {
        return new CI1<T>() {
            @Override public void apply(T t) {
                String sPre = pre == null ? "" : pre;
                String sPost = post == null ? "" : post;

                System.out.print(sPre + t + sPost);
            }
        };
    }

    /**
     * Converts given option and closure into absolute closure that does nothing if
     * option is a {@code none} value, or applies in closure if option is {@code some} value.
     * Note that peer deployment information will be copied from given in closure.
     *
     * @param opt Option.
     * @param c Closure to apply if option is {@code some} value.
     * @param <T> Type of the option and closure's free variable.
     * @return Absolute closure that does nothing if option is a {@code none} value,
     *      or applies input closure if option is {@code some} value.
     */
    public static <T> GridAbsClosure opt(final GridOpt<T> opt, final GridInClosure<? super T> c) {
        A.notNull(opt, "opt", c, "c");

        return new CA() {
            {
                peerDeployLike(c);
            }

            @Override public void apply() {
                if (opt.isSome()) {
                    c.apply(opt.get());
                }
            }
        };
    }

    /**
     * Converts given option and closure into output closure that does nothing and returns
     * default value if option is a {@code none} value, or applies input closure and returns
     * its value if option is {@code some} value. Note that peer deployment information will
     * be copied from given closure.
     *
     * @param opt Option.
     * @param c Closure to apply if option is {@code some} value.
     * @param dfltVal Default value to return in case if option is {@code none} value.
     * @param <T> Type of the option and closure's free variable.
     * @param <R> Type of the return value.
     * @return Output closure that does nothing returns default value if option is
     *      a {@code none} value, or applies input closure and returns its value if
     *      option is {@code some} value.
     */
    public static <T, R> GridOutClosure<R> opt(final GridOpt<T> opt, final GridClosure<T, R> c, final R dfltVal) {
        A.notNull(opt, "opt", c, "c");

        return new CO<R>() {
            {
                peerDeployLike(c);
            }

            @Override public R apply() {
                return opt.isSome() ? c.apply(opt.get()) : dfltVal;
            }
        };
    }

    /**
     * Gets random value from given collection.
     *
     * @param c Input collection.
     * @param <T> Type of the collection.
     * @return Random value from the input collection.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static <T> T rand(Collection<? extends T> c) {
        A.notNull(c, "c");

        int n = rand.nextInt(c.size());

        int i = 0;

        for (T t : c) {
            if (i++ == n) {
                return t;
            }
        }

        throw new ConcurrentModificationException();
    }

    /**
     * Gets random value from given list. For random-access lists this
     * operation is O(1), otherwise O(n).
     *
     * @param l Input collection.
     * @param <T> Type of the list elements.
     * @return Random value from the input list.
     */
    public static <T> T rand(List<T> l) {
        A.notNull(l, "l");

        return l.get(rand.nextInt(l.size()));
    }

    /**
     * Gets random value from given array. This operation
     * does not iterate through array elements and returns immediately.
     *
     * @param c Input collection.
     * @param <T> Type of the collection.
     * @return Random value from the input collection.
     */
    public static <T> T rand(T... c) {
        A.notNull(c, "c");

        return c[rand.nextInt(c.length)];
    }

    /**
     * Concatenates an element to a collection. If {@code copy} flag is {@code true}, then
     * a new collection will be created and the element and passed in collection will be
     * copied into the new one. The returned collection will be modifiable. If {@code copy}
     * flag is {@code false}, then a read-only view will be created over the element and given
     * collections and no copying will happen.
     *
     * @param copy Copy flag.
     * @param t First element.
     * @param c Second collection.
     * @param <T> Element type.
     * @return Concatenated collection.
     */
    public static <T> Collection<T> concat(boolean copy, @Nullable final T t, @Nullable final Collection<T> c) {
        if (copy) {
            if (isEmpty(c)) {
                Collection<T> l = new ArrayList<T>(1);

                l.add(t);

                return l;
            }

            assert c != null;

            Collection<T> ret = new ArrayList<T>(c.size() + 1);

            ret.add(t);
            ret.addAll(c);

            return ret;
        }
        else {
            if (isEmpty(c)) {
                return Collections.singletonList(t);
            }

            assert c != null;

            return new GridSerializableCollection<T>() {
                @Override public Iterator<T> iterator() {
                    return new GridSerializableIterator<T>() {
                        private Iterator<T> it;

                        @Override public boolean hasNext() {
                            return it == null || it.hasNext();
                        }

                        @Nullable @Override public T next() {
                            if (it == null) {
                                it = c.iterator();

                                return t;
                            }

                            return it.next();
                        }

                        @Override public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override public int size() {
                    return c.size() + 1;
                }

                @Override public boolean equals(Object obj) {
                    return obj instanceof Collection && eqNotOrdered(this, (Collection)obj);
                }
            };
        }
    }

    /**
     * Concatenates 2 collections into one. If {@code copy} flag is {@code true}, then
     * a new collection will be created and these collections will be copied into the
     * new one. The returned collection will be modifiable. If {@code copy} flag is
     * {@code false}, then a read-only view will be created over given collections
     * and no copying will happen.
     *
     * @param copy Copy flag.
     * @param c1 First collection.
     * @param c2 Second collection.
     * @param <T> Element type.
     * @return Concatenated {@code non-null} collection.
     */
    public static <T> Collection<T> concat(boolean copy, @Nullable final Collection<T> c1,
        @Nullable final Collection<T> c2) {
        if (copy) {
            if (isEmpty(c1) && isEmpty(c2)) {
                return new ArrayList<T>(0);
            }

            if (isEmpty(c1)) {
                return new ArrayList<T>(c2);
            }

            if (isEmpty(c2)) {
                return new ArrayList<T>(c1);
            }

            assert c1 != null && c2 != null;

            Collection<T> c = new ArrayList<T>(c1.size() + c2.size());

            c.addAll(c1);
            c.addAll(c2);

            return c;
        }
        else {
            if (isEmpty(c1) && isEmpty(c2)) {
                return Collections.emptyList();
            }

            if (isEmpty(c1) || isEmpty(c2)) {
                Collection<T> c = isEmpty(c1) ? c2 : c1;

                assert c != null;

                return c;
            }

            assert c1 != null && c2 != null;

            return new GridSerializableCollection<T>() {
                @Override public Iterator<T> iterator() {
                    return new GridSerializableIterator<T>() {
                        private Iterator<T> it1 = c1.iterator();
                        private Iterator<T> it2 = c2.iterator();

                        @Override public boolean hasNext() {
                            if (it1 != null) {
                                if (!it1.hasNext()) {
                                    it1 = null;
                                }
                                else {
                                    return true;
                                }
                            }

                            return it2.hasNext();
                        }

                        @Override public T next() {
                            return it1 != null ? it1.next() : it2.next();
                        }

                        @Override public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override public int size() {
                    return c1.size() + c2.size();
                }

                @Override public boolean equals(Object obj) {
                    return obj instanceof Collection && eqNotOrdered(this, (Collection<?>)obj);
                }
            };
        }
    }

    /**
     * Loses all elements in input collection that are contained in {@code filter} collection.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates new collection not modifying input,
     *      otherwise does <tt>in-place</tt> modifications.
     * @param filter Filter collection. If {@code filter} collection is empty or
     *      {@code null} - no elements are lost.
     * @param <T> Type of collections.
     * @return Collection of remaining elements
     */
    public static <T0, T extends T0> Collection<T> lose(Collection<T> c, boolean copy,
        @Nullable Collection<T0> filter) {
        A.notNull(c, "c");

        return lose(c, copy, in(filter));
    }

    /**
     * Loses all elements in input collection that are evaluated to {@code true} by
     * all given predicates.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates new collection without modifying the input one,
     *      otherwise does <tt>in-place</tt> modifications.
     * @param p Predicates to filter by. If no predicates provided - no elements are lost.
     * @param <T> Type of collections.
     * @return Collection of remaining elements.
     */
    public static <T> Collection<T> lose(Collection<T> c, boolean copy, @Nullable GridPredicate<? super T>... p) {
        A.notNull(c, "c");

        Collection<T> res;

        if (!copy) {
            res = c;

            if (!isEmpty(p) && !isAlwaysFalse(p)) {
                for (Iterator<T> iter = res.iterator(); iter.hasNext();) {
                    if (isAll(iter.next(), p)) {
                        iter.remove();
                    }
                }
            }
        }
        else {
            res = new LinkedList<T>();

            if (!isAlwaysTrue(p)) {
                for (T t : c) {
                    if (isEmpty(p) || !isAll(t, p)) {
                        res.add(t);
                    }
                }
            }
        }

        return res;
    }

    /**
     * Loses up to first {@code num} elements of the input collection.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates new collection not modifying input,
     *      otherwise does <tt>in-place</tt> modifications.
     * @param num Maximum number of elements to lose (the actual number can be
     *      less if the input collection contains less elements).
     * @param <T> Type of the collections.
     * @return Collection of remaining elements.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> Collection<T> lose(Collection<? extends T> c, boolean copy, int num) {
        A.notNull(c, "c");
        A.ensure(num >= 0, "num >= 0");

        Collection<T> res;

        if (!copy) {
            res = (Collection<T>)c;

            if (num >= c.size()) {
                res.clear();
            }
            else {
                int i = 0;

                for (Iterator<T> iter = res.iterator(); iter.hasNext();) {
                    iter.next();

                    if (i++ < num) {
                        iter.remove();
                    }
                    else {
                        break;
                    }
                }
            }
        }
        else {
            if (num >= c.size()) {
                return Collections.emptyList();
            }

            res = new ArrayList<T>(c.size() - num);

            int i = 0;

            for (T t : c) {
                if (i++ >= num) {
                    res.add(t);
                }
            }
        }

        return res;
    }

    /**
     * Loses all entries in input map that are evaluated to {@code true} by all given predicates.
     *
     * @param m Map to filter.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicates to use for filtration. If none provided - original map
     *  will (or its copy) be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of the free variable for the predicate and type of map's values.
     * @return Filtered map.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> Map<K, V> lose(Map<K, V> m, boolean copy,
        @Nullable GridPredicate<? super Map.Entry<K, V>>... p) {
        A.notNull(m, "m");

        Map<K, V> res;

        if (!copy) {
            res = m;

            if (!isEmpty(p) && !isAlwaysFalse(p)) {
                for (Iterator<Map.Entry<K, V>> iter = m.entrySet().iterator(); iter.hasNext();) {
                    if (isAll(iter.next(), p)) {
                        iter.remove();
                    }
                }
            }
        }
        else {
            res = new HashMap<K, V>();

            if (!isAlwaysTrue(p)) {
                for (Map.Entry<K, V> e : m.entrySet()) {
                    if (isEmpty(p) || !F.isAll(e, p)) {
                        res.put(e.getKey(), e.getValue());
                    }
                }
            }
        }

        return res;
    }

    /**
     * Loses all entries in input map which keys are evaluated to {@code true} by all
     * given predicates.
     *
     * @param m Map to filter.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicates to use for filtration. If none provided - original
     *      map (or its copy) will be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of map's values.
     * @return Filtered map.
     */
    public static <K, V> Map<K, V> loseKeys(Map<K, V> m, boolean copy,
        @Nullable final GridPredicate<? super K>... p) {
        return lose(m, copy, new P1<Map.Entry<K, V>>() {
            @Override public boolean apply(Map.Entry<K, V> e) {
                return isAll(e.getKey(), p);
            }
        });
    }

    /**
     * Loses all entries in input map which values are evaluated to {@code true} by all
     * given predicates.
     *
     * @param m Map to filter.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicates to use for filtration. If none provided - original
     *      map (or its copy) will be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of map's values.
     * @return Filtered map.
     */
    public static <K, V> Map<K, V> loseValues(Map<K, V> m, boolean copy,
        @Nullable final GridPredicate<? super V>... p) {
        return lose(m, copy, new P1<Map.Entry<K, V>>() {
            @Override public boolean apply(Map.Entry<K, V> e) {
                return isAll(e.getValue(), p);
            }
        });
    }

    /**
     * Loses all elements in input list that are contained in {@code filter} collection.
     *
     * @param c Input list.
     * @param copy If {@code true} method creates new list not modifying input,
     *      otherwise does <tt>in-place</tt> modifications.
     * @param filter Filter collection. If {@code filter} collection is empty or
     *      {@code null} - no elements are lost.
     * @param <T> Type of list.
     * @return List of remaining elements
     */
    public static <T> List<T> loseList(List<T> c, boolean copy, @Nullable Collection<? super T> filter) {
        A.notNull(c, "c");

        List<T> res;

        if (!copy) {
            res = c;

            if (filter != null) {
                res.removeAll(filter);
            }
        }
        else {
            res = new LinkedList<T>();

            for (T t : c) {
                if (filter == null || !filter.contains(t)) {
                    res.add(t);
                }
            }
        }

        return res;
    }

    /**
     * Loses all elements in input set that are contained in {@code filter} collection.
     *
     * @param c Input set.
     * @param copy If {@code true} method creates new list not modifying input,
     *      otherwise does <tt>in-place</tt> modifications.
     * @param filter Filter collection. If {@code filter} collection is empty or
     *      {@code null} - no elements are lost.
     * @param <T> Type of set.
     * @return Set of remaining elements
     */
    public static <T> Set<T> loseSet(Set<T> c, boolean copy, @Nullable Collection<? super T> filter) {
        A.notNull(c, "c");

        Set<T> res;

        if (!copy) {
            res = c;

            if (filter != null) {
                res.removeAll(filter);
            }
        }
        else {
            res = new LinkedHashSet<T>();

            for (T t : c) {
                if (filter == null || !filter.contains(t)) {
                    res.add(t);
                }
            }
        }

        return res;
    }

    /**
     * Removes elements <tt>in-place</tt> from given collection. All elements for which
     * all given predicates, if any, evaluate to {@code true} will be removed from the
     * passed in collection. Note that collection must support {@link Iterator#remove() removal}.
     *
     * @param c Input collection that should support {@link Iterator#remove() removal}.
     * @param p Optional set of predicates. If no predicates provided - no elements will be
     *      dropped and input collection will be returned.
     * @param <T> Type of the collection.
     * @return Input collection with some elements potentially removed.
     */
    public static <T> Iterable<T> drop(Iterable<T> c, @Nullable GridPredicate<? super T>... p) {
        A.notNull(c, "c");

        if (isEmpty(p) || isAlwaysFalse(p)) {
            return c;
        }

        for (Iterator<T> i = c.iterator(); i.hasNext();) {
            if (isAll(i.next(), p)) {
                i.remove();
            }
        }

        return c;
    }

    /**
     * Gets closure which converts node to node ID.
     *
     * @return Closure which converts node to node ID.
     */
    public static GridClosure<GridNode, UUID> node2id() {
        return NODE2ID;
    }

    /**
     * Creates grid node predicate evaluating on the given node ID.
     *
     * @param nodeId Node ID for which returning predicate will evaluate to {@code true}.
     * @return Grid node predicate evaluating on the given node ID.
     * @see #idForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static <T extends GridNode> GridPredicate<T> nodeForNodeId(final UUID nodeId) {
        A.notNull(nodeId, "nodeId");

        return new P1<T>() {
            @Override public boolean apply(GridNode e) {
                return e.id().equals(nodeId);
            }
        };
    }

    /**
     * Creates grid node predicate evaluating on the given node IDs.
     *
     * @param nodeIds Collection of node IDs.
     * @return Grid node predicate evaluating on the given node IDs.
     * @see #idForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static <T extends GridNode> GridPredicate<T> nodeForNodeIds(@Nullable final Collection<UUID>
        nodeIds) {
        if (isEmpty(nodeIds)) {
            return alwaysFalse();
        }

        assert nodeIds != null;

        return new P1<T>() {
            @Override public boolean apply(GridNode e) {
                return nodeIds.contains(e.id());
            }
        };
    }

    /**
     * Creates grid node predicate evaluating on the given node IDs.
     *
     * @param nodeIds Collection of node IDs.
     * @return Grid node predicate evaluating on the given node IDs.
     * @see #idForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static <T extends GridNode> GridPredicate<T> nodeForNodeIds(@Nullable final UUID... nodeIds) {
        if (isEmpty(nodeIds)) {
            return alwaysFalse();
        }

        return new P1<T>() {
            private final UUID[] ids;

            {
                assert nodeIds != null;

                Arrays.sort(nodeIds);

                ids = Arrays.copyOf(nodeIds, nodeIds.length);
            }

            @Override public boolean apply(GridNode e) {
                return Arrays.binarySearch(ids, e.id()) >= 0;
            }
        };
    }

    /**
     * Creates {@link UUID} predicate evaluating on the given node ID.
     *
     * @param nodeId Node ID for which returning predicate will evaluate to {@code true}.
     * @return {@link UUID} predicate evaluating on the given node ID.
     * @see #nodeForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static GridPredicate<UUID> idForNodeId(final UUID nodeId) {
        A.notNull(nodeId, "nodeId");

        return new P1<UUID>() {
            @Override public boolean apply(UUID id) {
                return id.equals(nodeId);
            }
        };
    }

    /**
     * Creates {@link UUID} predicate evaluating on the given node IDs.
     *
     * @param nodeIds Collection of node IDs.
     * @return {@link UUID} predicate evaluating on the given node IDs.
     * @see #nodeForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static GridPredicate<UUID> idForNodeIds(@Nullable final Collection<UUID> nodeIds) {
        if (isEmpty(nodeIds)) {
            return alwaysFalse();
        }

        assert nodeIds != null;

        return new P1<UUID>() {
            @Override public boolean apply(UUID id) {
                return nodeIds.contains(id);
            }
        };
    }

    /**
     * Creates {@link UUID} predicate evaluating on the given node IDs.
     *
     * @param nodeIds Collection of node IDs.
     * @return {@link UUID} predicate evaluating on the given node IDs.
     * @see #nodeForNodeId(UUID)
     * @see #nodeIds(Collection)
     */
    public static GridPredicate<UUID> idForNodeIds(@Nullable final UUID... nodeIds) {
        if (isEmpty(nodeIds)) {
            return alwaysFalse();
        }

        return new P1<UUID>() {
            private final UUID[] ids;

            {
                assert nodeIds != null;

                Arrays.sort(nodeIds);

                ids = Arrays.copyOf(nodeIds, nodeIds.length);
            }

            @Override public boolean apply(UUID id) {
                return Arrays.binarySearch(ids, id) >= 0;
            }
        };
    }

    /**
     * Creates predicates that evaluates to {@code true} for each node in given collection.
     * Note that is collection is empty the result predicate will always evaluate to {@code false}.
     * Implementation simply creates {@link GridNodePredicate} instance.
     *
     * @param nodes Collection of nodes. If none provided - result predicate will always
     *      return {@code false}.
     * @return Predicates that evaluates to {@code true} for each node in given collection.
     */
    public static GridPredicate<GridRichNode> nodeForNodes(@Nullable Collection<? extends GridNode> nodes) {
        return new GridNodePredicate<GridRichNode>(nodeIds(nodes));
    }

    /**
     * Creates predicates that evaluates to {@code true} for each node in given collection.
     * Note that is collection is empty the result predicate will always evaluate to {@code false}.
     * Implementation simply creates {@link GridNodePredicate} instance.
     *
     * @param nodes Collection of nodes. If none provided - result predicate will always
     *      return {@code false}.
     * @return Predicates that evaluates to {@code true} for each node in given collection.
     */
    public static GridPredicate<GridRichNode> nodeForNodes(GridRichNode... nodes) {
        return new GridNodePredicate<GridRichNode>(nodes);
    }

    /**
     * Retains all elements in input collection that are contained in {@code filter}.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates collection not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param filter Filter collection. If filter collection is {@code null} or empty -
     *      an empty collection will be returned.
     * @param <T> Type of collections.
     * @return Collection of retain elements.
     */
    public static <T0, T extends T0> Collection<T> retain(Collection<T> c, boolean copy,
        @Nullable Collection<? extends T0> filter) {
        A.notNull(c, "c");

        return retain(c, copy, in(filter));
    }

    /**
     * Retains all elements in input collection that are evaluated to {@code true}
     * by all given predicates.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates collection not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Predicates to filter by. If no predicates provides - all elements
     *      will be retained.
     * @param <T> Type of collections.
     * @return Collection of retain elements.
     */
    public static <T> Collection<T> retain(Collection<T> c, boolean copy, @Nullable GridPredicate<? super T>... p) {
        A.notNull(c, "c");

        return lose(c, copy, not(p));
    }

    /**
     * Retains only up to first {@code num} elements in the input collection.
     *
     * @param c Input collection.
     * @param copy If {@code true} method creates collection not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param num Maximum number of elements to retain (the actual number can be
     *      less if the input collection contains less elements).
     * @param <T> Type of the collections.
     * @return Collection contains up to {@code num} first elements from the input collection.
     */
    public static <T> Collection<T> retain(Collection<T> c, boolean copy, int num) {
        A.notNull(c, "c");
        A.ensure(num >= 0, "num >= 0");

        Collection<T> res;

        if (!copy) {
            res = c;

            if (num < res.size()) {
                int i = 0;

                for (Iterator<T> iter = res.iterator(); iter.hasNext();) {
                    iter.next();

                    if (i++ >= num) {
                        iter.remove();
                    }
                }
            }
        }
        else {
            res = new ArrayList<T>(num);

            Iterator<? extends T> iter = c.iterator();

            for (int i = 0; i < num && iter.hasNext(); i++) {
                res.add(iter.next());
            }
        }

        return res;
    }

    /**
     * For a given map retains all map entries that satisfy all provided predicates. If
     * no predicates provided - all entries will be retain.
     *
     * @param m Map to retain entries from.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicate to use for filtration. If none provided - original
     *      map (or its copy) will be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of map's values.
     * @return Filtered map.
     */
    public static <K, V> Map<K, V> retain(Map<K, V> m, boolean copy,
        @Nullable GridPredicate<? super Map.Entry<K, V>>... p) {
        return lose(m, copy, F.not(p));
    }

    /**
     * For a given map retains all map entries which keys satisfy all provided predicates. If
     * no predicates provided - all entries will be retain.
     *
     * @param m Map to retain entries from.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicate to use for filtration. If none provided - original
     *      map (or its copy) will be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of map's values.
     * @return Filtered map.
     */
    public static <K, V> Map<K, V> retainKeys(Map<K, V> m, boolean copy, @Nullable GridPredicate<? super K>... p) {
        return loseKeys(m, copy, F.not(p));
    }

    /**
     * For a given map retains all map entries which values satisfy all provided predicates.
     * If no predicates provided - all entries will be retain and the same map will be returned.
     *
     * @param m Map to retain entries from.
     * @param copy If {@code true} method creates new map not modifying input, otherwise does
     *      <tt>in-place</tt> modifications.
     * @param p Optional set of predicate to use for filtration. If none provided - original
     *      map (or its copy) will be returned.
     * @param <K> Type of the free variable for the predicate and type of map's keys.
     * @param <V> Type of map's values.
     * @return Filtered map.
     */
    public static <K, V> Map<K, V> retainValues(Map<K, V> m, boolean copy, @Nullable GridPredicate<? super V>... p) {
        return loseValues(m, copy, F.not(p));
    }

    /**
     * Converts collection of out closures to the read only collection of grid jobs.
     *
     * @param c Closure collection to convert.
     * @return Read only collection of grid job where each job wraps corresponding closure
     *      from input collection.
     */
    public static <T extends Callable<?>> Collection<GridJob> outJobs(@Nullable Collection<? extends T> c) {
        return isEmpty(c) ? Collections.<GridJob>emptyList() : viewReadOnly(c, new C1<T, GridJob>() {
            @Override public GridJob apply(T e) {
                return job(e);
            }
        });
    }

    /**
     * Converts collection of absolute closures to the read only collection of grid jobs.
     *
     * @param c Closure collection to convert.
     * @return Read only collection of grid job where each job wraps corresponding closure
     *      from input collection.
     */
    public static <T extends Runnable> Collection<GridJob> absJobs(@Nullable Collection<? extends T> c) {
        return isEmpty(c) ? Collections.<GridJob>emptyList() : viewReadOnly(c, new C1<T, GridJob>() {
            @Override public GridJob apply(T e) {
                return job(e);
            }
        });
    }

    /**
     * Converts array of out closures to the collection of grid jobs.
     *
     * @param c Closure array to convert.
     * @return Collection of grid job where each job wraps corresponding closure from input array.
     */
    public static Collection<GridJob> outJobs(@Nullable Callable<?>... c) {
        return isEmpty(c) ? Collections.<GridJob>emptyList() : transform(c, new C1<Callable<?>, GridJob>() {
            @Override public GridJob apply(Callable<?> e) {
                return job(e);
            }
        });
    }

    /**
     * Converts array of out closures to the collection of grid jobs.
     *
     * @param c Closure array to convert.
     * @return Collection of grid job where each job wraps corresponding closure from input array.
     */
    public static Collection<GridJob> absJobs(@Nullable Runnable... c) {
        return isEmpty(c) ? Collections.<GridJob>emptyList() : transform(c, new C1<Runnable, GridJob>() {
            @Override public GridJob apply(Runnable e) {
                return job(e);
            }
        });
    }

    /**
     * Converts given closure to a grid job.
     *
     * @param c Closure to convert to grid job.
     * @return Grid job made out of closure.
     */
    public static GridJob job(final Callable<?> c) {
        A.notNull(c, "job");

        if (c instanceof GridJob) {
            return (GridJob) c;
        }

        return U.withMeta(new GridJobAdapterEx() {
            {
                setPeerDeployAware(U.peerDeployAware(c));
            }

            @Override public Object execute() {
                try {
                    return c.call();
                }
                catch (Exception e) {
                    throw new GridRuntimeException(e);
                }
            }
        }, c);
    }

    /**
     * Converts given closure to a grid job.
     *
     * @param r Closure to convert to grid job.
     * @return Grid job made out of closure.
     */
    public static GridJob job(final Runnable r) {
        A.notNull(r, "job");

        if (r instanceof GridJob)
            return (GridJob)r;

        return U.withMeta(new GridJobAdapterEx() {
            {
                peerDeployLike(U.peerDeployAware(r));
            }

            @Nullable
            @Override public Object execute() {
                r.run();

                return null;
            }
        }, r);
    }

    /**
     * Converts given future into the closure. When result closure's {@code apply}
     * method is called it will call {@link Future#get()} method.
     *
     * @param fut Future to convert.
     * @param <T> Type of the future and closure.
     * @return Out closure that wraps given future.
     */
    public static <T> GridOutClosure as(final Future<T> fut) {
        A.notNull(fut, "fut");

        return U.withMeta(new CO<T>() {
            {
                peerDeployLike(U.peerDeployAware(fut));
            }

            @Override public T apply() {
                //noinspection CatchGenericClass
                try {
                    return fut.get();
                }
                catch (Exception e) {
                    throw new GridRuntimeException(e);
                }
            }
        }, fut);
    }

    /**
     * Converts predicate with two separate values to a predicate with tuple.
     *
     * @param p Predicate to convert.
     * @param <E1> Type of the 1st value.
     * @param <E2> Type of the 2nd value.
     * @return Converted predicate.
     */
    public static <E1, E2> GridPredicate<GridTuple2<E1, E2>> as0(final GridPredicate2<? super E1, ? super E2> p) {
        return U.withMeta(new P1<GridTuple2<E1, E2>>() {
            {
                peerDeployLike(p);
            }

            @Override public boolean apply(GridTuple2<E1, E2> e) {
                return p.apply(e.get1(), e.get2());
            }
        }, p);
    }

    /**
     * Converts predicate with tuple of two values to a predicate with separate two values.
     *
     * @param p Predicate to convert.
     * @param <E1> Type of the 1st value.
     * @param <E2> Type of the 2nd value.
     * @return Converted predicate.
     */
    private static <E1, E2> GridPredicate2<E1, E2> as(final GridPredicate<GridTuple2<? super E1, ? super E2>> p) {
        return U.withMeta(new P2<E1, E2>() {
            {
                peerDeployLike(p);
            }

            @Override public boolean apply(E1 e1, E2 e2) {
                return p.apply(F.t(e1, e2));
            }
        }, p);
    }

    /**
     * Converts given object with interface {@link GridFuture} into an object implementing {@link Future}.
     *
     * @param fut Future to convert.
     * @param <T> Type of computation result.
     * @return Instance implementing {@link Future}.
     */
    public static <T> Future<T> as(final GridFuture<T> fut) {
        A.notNull(fut, "fut");

        return new GridSerializableFuture<T>() {
            @Override public boolean cancel(boolean mayInterruptIfRunning) {
                if (mayInterruptIfRunning) {
                    try {
                        return fut.cancel();
                    }
                    catch (GridException e) {
                        throw new GridRuntimeException(e);
                    }
                }
                else {
                    return false;
                }
            }

            @Override public boolean isCancelled() {
                return fut.isCancelled();
            }

            @Override public boolean isDone() {
                return fut.isDone();
            }

            @Override public T get() throws InterruptedException, ExecutionException {
                try {
                    return fut.get();
                }
                catch (GridFutureCancelledException ignore) {
                    throw new CancellationException("The computation was cancelled.");
                }
                catch (GridInterruptedException ignore) {
                    throw new InterruptedException("The computation was interrupted.");
                }
                catch (GridException e) {
                    throw new ExecutionException("The computation failed.", e);
                }
            }

            @Override public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                TimeoutException {
                try {
                    return fut.get(timeout, unit);
                }
                catch (GridFutureCancelledException ignore) {
                    throw new CancellationException("The computation was cancelled.");
                }
                catch (GridInterruptedException ignore) {
                    throw new InterruptedException("The computation was interrupted.");
                }
                catch (GridFutureTimeoutException e) {
                    throw new TimeoutException("The computation timed out: " + e.getMessage());
                }
                catch (GridException e) {
                    throw new ExecutionException("The computation failed.", e);
                }
            }
        };
    }

    /**
     * Gets closure that converts {@link GridFuture} to {@link Future}.
     *
     * @param <T> Type of future.
     * @return Closure that converts {@link GridFuture} to {@link Future}.
     */
    public static <T> GridClosure<GridFuture<T>, Future<T>> future() {
        return new C1<GridFuture<T>, Future<T>>() {
            @Override public Future<T> apply(GridFuture<T> fut) {
                return as(fut);
            }
        };
    }

    /**
     * Executes given {@code body} until provided condition evaluates to
     * {@code true} via {@code while-do} Java loop ({@code test-before}).
     *
     * @param body Body closure.
     * @param cond Condition predicate.
     */
    public static void whileDo(GridAbsClosure body, GridAbsPredicate cond) {
        A.notNull(body, "body", cond, "cond");

        while (cond.apply()) {
            body.apply();
        }
    }

    /**
     * Executes given {@code body} until provided condition evaluates to
     * {@code true} via {@code do-while} Java loop ({@code test-after}).
     *
     * @param body Body closure.
     * @param cond Condition predicate.
     */
    public static void doWhile(GridAbsClosure body, GridAbsPredicate cond) {
        A.notNull(body, "body", cond, "cond");

        do {
            body.apply();
        }
        while (cond.apply());
    }

    /**
     * Executes given {@code body} until provided condition evaluates to
     * {@code true} via {@code do-while} Java loop ({@code test-after}).
     *
     * @param body Body closure.
     * @param cond Condition predicate.
     * @param <R> Type of closure's return value.
     * @return Last return value of the closure.
     * @throws GridClosureException Thrown if callable throws exception.
     */
    public static <R> GridOpt<R> doWhile(Callable<R> body, GridAbsPredicate cond) {
        A.notNull(body, "body", cond, "cond");

        R r;

        try {
            do {
                r = body.call();
            }
            while (cond.apply());
        }
        catch (Exception e) {
            throw wrap(e);
        }

        return GridOpt.make(r);
    }

    /**
     * Executes given {@code body} until provided condition evaluates to
     * {@code true} via {@code while-do} Java loop ({@code test-before}).
     *
     * @param body Body closure.
     * @param cond Condition predicate.
     * @param <R> Type of closure's return value.
     * @return Last return value of the closure.
     * @throws GridClosureException Thrown if callable throws exception.
     */
    public static <R> GridOpt<R> whileDo(Callable<R> body, GridAbsPredicate cond) {
        A.notNull(body, "body", cond, "cond");

        R r = null;

        try {
            while (cond.apply()) {
                r = body.call();
            }
        }
        catch (Exception e) {
            throw wrap(e);
        }

        return GridOpt.make(r);
    }

    /**
     * Given collection of items and a closure this method returns
     * read only collection of closures where each closure is closed on an element
     * of the initial collection.
     *
     * @param c Input collection of elements.
     * @param f Closure to close on each element of input collection.
     * @param <T> Type of the input collection.
     * @param <R> Type of the return value for the closure.
     * @return Read only collection of closures closed on each element of input collection.
     */
    public static <T, R> Collection<GridOutClosure<R>> yield(Collection<? extends T> c,
        final GridClosure<? super T, R> f) {
        A.notNull(c, "c", f, "f");

        return viewReadOnly(c, new C1<T, GridOutClosure<R>>() {
            @Override public GridOutClosure<R> apply(T e) {
                return f.curry(e);
            }
        });
    }

    /**
     * Given collection of items and a closure this method returns read-only collection of
     * closures where each closure is closed on an element of the initial collection.
     *
     * @param c Input collection of elements.
     * @param f Closure to close on each element of input collection.
     * @param <T> Type of the input collection.
     * @return Read only collection of closures closed on each element of input collection.
     */
    public static <T> Collection<GridAbsClosure> yield(Collection<? extends T> c, final GridInClosure<? super T> f) {
        A.notNull(c, "c", f, "f");

        return viewReadOnly(c, new C1<T, GridAbsClosure>() {
            @Override public GridAbsClosure apply(T e) {
                return f.curry(e);
            }
        });
    }

    /**
     * Given collection of items and a closure this method returns collection
     * of closures where each closure is closed on an element of the initial collection.
     *
     * @param c Input collection of elements.
     * @param f Closure to close on each element of input collection.
     * @param <T> Type of the input collection.
     * @return Collection of closures closed on each element of input collection.
     */
    public static <T> Collection<GridAbsClosure> yield(T[] c, GridInClosure<? super T> f) {
        A.notNull(c, "c", f, "f");

        return yield(asList(c), f);
    }

    /**
     * Given array of items and a closure this method returns collection
     * of closures where each closure is closed on a element of the array.
     *
     * @param c Input array of elements.
     * @param f Closure to close on each element of array.
     * @param <T> Type of the input collection.
     * @param <R> Type of the return value for the closure.
     * @return Collection of closures closed on each element of array.
     */
    public static <T, R> Collection<GridOutClosure<R>> yield(T[] c, GridClosure<? super T, R> f) {
        A.notNull(c, "c", f, "f");

        return yield(asList(c), f);
    }

    /**
     * Converts given iterator into instance of {@link Iterable} interface.
     *
     * @param iter Iterator to convert.
     * @param <T> Type of the iterator.
     * @return Iterable over given iterator.
     */
    public static <T> GridIterable<T> as(Iterator<T> iter) {
        A.notNull(iter, "iter");

        return new GridIterableAdapter<T>(iter);
    }

    /**
     * Converts array to {@link List}. Note that resulting list cannot
     * be altered in size, as it it based on the passed in array -
     * only current elements can be changed.
     * <p>
     * Note that unlike {@link Arrays#asList(Object[])}, this method is
     * {@code null}-safe. If {@code null} is passed in, then empty list
     * will be returned.
     *
     * @param vals Array of values
     * @param <T> Array type.
     * @return {@link Iterable} instance for array.
     */
    public static <T> List<T> asList(@Nullable T... vals) {
        return isEmpty(vals) ? Collections.<T>emptyList() : Arrays.asList(vals);
    }

    /**
     * Creates new empty iterator.
     *
     * @param <T> Type of the iterator.
     * @return Newly created empty iterator.
     */
    public static <T> GridIterator<T> emptyIterator() {
        return new GridEmptyIterator<T>();
    }

    /**
     * Flattens collection-of-collections and returns collection over the
     * elements of the inner collections. This method doesn't create any
     * new collections or copies any elements.
     * <p>
     * Note that due to non-copying nature of implementation, the
     * {@link Collection#size() size()} method of resulting collection will have to
     * iterate over all elements to produce size. Method {@link Collection#isEmpty() isEmpty()},
     * however, is constant time and is much more preferable to use instead
     * of {@code 'size()'} method when checking if list is not empty.
     *
     * @param c Input collection of collections.
     * @param <T> Type of the inner collections.
     * @return Iterable over the elements of the inner collections.
     */
    public static <T> Collection<T> flat(@Nullable final Collection<? extends Collection<T>> c) {
        if (F.isEmpty(c)) {
            return Collections.emptyList();
        }

        return new GridSerializableCollection<T>() {
            @Override public Iterator<T> iterator() {
                return flat((Iterable<? extends Iterable<T>>)c);
            }

            @Override public int size() {
                return F.size(iterator());
            }

            @Override public boolean isEmpty() {
                return !iterator().hasNext();
            }
        };
    }

    /**
     * Flattens iterable-of-iterables and returns iterable over the
     * elements of the inner collections. This method doesn't create any
     * new collections or copies any elements.
     *
     * @param c Input collection of collections.
     * @param <T> Type of the inner collections.
     * @return Iterable over the elements of the inner collections.
     */
    public static <T> GridIterator<T> flat(@Nullable final Iterable<? extends Iterable<T>> c) {
        return isEmpty(c) ? GridFunc.<T>emptyIterator() : new GridIteratorAdapter<T>() {
            /** */
            private Iterator<? extends Iterable<T>> a = c.iterator();

            /** */
            private Iterator<T> b;

            /** */
            private boolean moved = true;

            /** */
            private boolean more;

            @Override public boolean hasNext() {
                if (!moved) {
                    return more;
                }

                moved = false;

                if (b != null && b.hasNext()) {
                    return more = true;
                }

                while (a.hasNext()) {
                    b = a.next().iterator();

                    if (b.hasNext()) {
                        return more = true;
                    }
                }

                return more = false;
            }

            @Override public T next() {
                if (hasNext()) {
                    moved = true;

                    return b.next();
                }

                throw new NoSuchElementException();
            }

            @Override public void remove() {
                assert b != null;

                b.remove();
            }
        };
    }

    /**
     * Flattens given set objects into a single collection. Unrolls {@link Collection},
     * {@link Iterable} and {@code Object[]} objects.
     *
     * @param objs Objects to flatten.
     * @return Flattened collection.
     */
    @SuppressWarnings("unchecked")
    public static Collection<Object> flat0(@Nullable Object... objs) {
        if (isEmpty(objs)) {
            return Collections.emptyList();
        }

        assert objs != null;

        Collection<Object> c = new LinkedList<Object>();

        for (Object obj : objs) {
            if (obj instanceof Collection) {
                c.addAll((Collection<Object>)obj);
            }
            else if (obj instanceof Iterable) {
                for (Object o : (Iterable)obj) {
                    c.add(o);
                }
            }
            else if (obj instanceof Object[]) {
                for (Object o : Arrays.asList((Object[])obj)) {
                    c.add(o);
                }
            }
            else {
                c.add(obj);
            }
        }

        return c;
    }

    /**
     * Converts given runnable to an absolute closure.
     *
     * @param r Runnable to convert to closure. If {@code null} - no-op closure is returned.
     * @return Closure that wraps given runnable. Note that wrapping closure always returns {@code null}.
     */
    public static GridAbsClosure as(@Nullable final Runnable r) {
        return U.withMeta(new CA() {
            {
                peerDeployLike(U.peerDeployAware(r));
            }

            @Override public void apply() {
                if (r != null)
                    r.run();
            }
        }, r);
    }

    /**
     * Returns closure that converts {@link Runnable} to {@link GridAbsClosure}.
     *
     * @return closure that converts {@link Runnable} to {@link GridAbsClosure}.
     */
    public static GridClosure<Runnable, GridAbsClosure> r2c() {
        return R2C;
    }

    /**
     * Returns closure that converts {@link Callable} to {@link GridOutClosure}.
     *
     * @return closure that converts {@link Callable} to {@link GridOutClosure}.
     */
    public static <T> GridClosure<Callable<T>, GridOutClosure<T>> c2c() {
        return new C1<Callable<T>, GridOutClosure<T>>() {
            @Override public GridOutClosure<T> apply(Callable<T> c) {
                return as(c);
            }
        };
    }

    /**
     * Converts given callable to an out-closure.
     *
     * @param c Callable to convert to closure.
     * @return Out-closure that wraps given callable. Note that if callable throw
     *      exception the wrapping closure will re-throw it as {@link GridRuntimeException}.
     */
    public static <R> GridOutClosure<R> as(final Callable<R> c) {
        A.notNull(c, "c");

        return U.withMeta(new CO<R>() {
            {
                peerDeployLike(U.peerDeployAware(c));
            }

            @Override public R apply() {
                try {
                    return c.call();
                }
                catch (Exception e) {
                    // No other way...
                    throw wrap(e);
                }
            }
        }, c);
    }

    /**
     * Gets size of the given array with provided optional predicates.
     *
     * @param c Array to size.
     * @param p Optional predicates that filters out elements from count.
     * @param <T> Type of the array.
     * @return Number of elements in the array for which all given predicates
     *      evaluates to {@code true}. If no predicates is provided - all elements are counted.
     */
    public static <T> int size(T[] c, @Nullable GridPredicate<? super T>... p) {
        A.notNull(c, "c");

        return size(asList(c), p);
    }

    /**
     * Gets size of the given collection with provided optional predicates.
     *
     * @param c Collection to size.
     * @param p Optional predicates that filters out elements from count.
     * @param <T> Type of the iterator.
     * @return Number of elements in the collection for which all given predicates
     *      evaluates to {@code true}. If no predicates is provided - all elements are counted.
     */
    public static <T> int size(@Nullable Collection<? extends T> c, @Nullable GridPredicate<? super T>... p) {
        return c == null || c.isEmpty() ? 0 : isEmpty(p) || isAlwaysTrue(p) ? c.size() : size(c.iterator(), p);
    }

    /**
     * Gets size of the given iterator with provided optional predicates. Iterator
     * will be traversed to get the count.
     *
     * @param it Iterator to size.
     * @param p Optional predicates that filters out elements from count.
     * @param <T> Type of the iterator.
     * @return Number of elements in the iterator for which all given predicates
     *      evaluates to {@code true}. If no predicates is provided - all elements are counted.
     */
    public static <T> int size(@Nullable Iterator<? extends T> it, @Nullable GridPredicate<? super T>... p) {
        if (it == null) {
            return 0;
        }

        int n = 0;

        if (!isAlwaysFalse(p)) {
            while (it.hasNext()) {
                if (isAll(it.next(), p)) {
                    n++;
                }
            }
        }

        return n;
    }

    /**
     * Creates write-through light-weight view on given collection with provided predicates. Resulting
     * collection will only "have" elements for which all provided predicate, if any, evaluates
     * to {@code true}. Note that only wrapping collection will be created and no duplication of
     * data will occur. Also note that if array of given predicates is not empty then method
     * {@code size()} uses full iteration through the collection.
     *
     * @param c Input collection that serves as a base for the view.
     * @param p Optional predicated. If predicates are not provided - all elements will be in the view.
     * @param <T> Type of the collection.
     * @return Light-weight view on given collection with provided predicate.
     */
    public static <T> Collection<T> view(@Nullable final Collection<T> c,
        @Nullable final GridPredicate<? super T>... p) {
        if (isEmpty(c) || isAlwaysFalse(p))
            return Collections.emptyList();

        assert c != null;

        return isEmpty(p) || isAlwaysTrue(p) ? c : new GridSerializableCollection<T>() {
            // Pass through (will fail for readonly).
            @Override public boolean add(T e) {
                return isAll(e, p) && c.add(e);
            }

            @Override public Iterator<T> iterator() {
                return F.iterator0(c, false, p);
            }

            @Override public int size() {
                return F.size(c, p);
            }

            @Override public boolean isEmpty() {
                return F.isEmpty(p) ? c.isEmpty() : !iterator().hasNext();
            }
        };
    }

    /**
     * Creates read-only light-weight view on given collection with transformation and provided
     * predicates. Resulting collection will only "have" {@code transformed} elements for which
     * all provided predicate, if any, evaluates to {@code true}. Note that only wrapping
     * collection will be created and no duplication of data will occur. Also note that if array
     * of given predicates is not empty then method {@code size()} uses full iteration through
     * the collection.
     *
     * @param c Input collection that serves as a base for the view.
     * @param trans Transformation closure.
     * @param p Optional predicated. If predicates are not provided - all elements will be in the view.
     * @param <T1> Type of the collection.
     * @return Light-weight view on given collection with provided predicate.
     */
    @SuppressWarnings("RedundantTypeArguments")
    public static <T1, T2> Collection<T2> viewReadOnly(@Nullable final Collection<? extends T1> c,
        final GridClosure<? super T1, T2> trans, @Nullable final GridPredicate<? super T1>... p) {
        A.notNull(trans, "trans");

        if (isEmpty(c) || isAlwaysFalse(p))
            return Collections.emptyList();

        assert c != null;

        return new GridSerializableCollection<T2>() {
            @Override public Iterator<T2> iterator() {
                return F.<T1, T2>iterator(c, trans, true, p);
            }

            @Override public int size() {
                return F.isEmpty(p) ? c.size() : F.size(iterator());
            }

            @Override public boolean isEmpty() {
                return F.isEmpty(p) ? c.isEmpty() : !iterator().hasNext();
            }
        };
    }

    /**
     * Creates read-only light-weight view on given list with provided transformation.
     * Resulting list will only "have" {@code transformed} elements. Note that only wrapping
     * list will be created and no duplication of data will occur.
     *
     * @param c Input list that serves as a base for the view.
     * @param trans Transformation closure.
     * @param <T1> Type of the list.
     * @return Light-weight view on given list with provided transformation.
     */
    @SuppressWarnings("RedundantTypeArguments")
    public static <T1, T2> List<T2> viewListReadOnly(@Nullable final List<? extends T1> c,
        final GridClosure<? super T1, T2> trans) {
        A.notNull(trans, "trans");

        if (isEmpty(c))
            return Collections.emptyList();

        assert c != null;

        return new GridSerializableList<T2>() {
            @Override public T2 get(int idx) {
                return trans.apply(c.get(idx));
            }

            @Override public Iterator<T2> iterator() {
                return F.<T1, T2>iterator(c, trans, true);
            }

            @Override public int size() {
                return c.size();
            }

            @Override public boolean isEmpty() {
                return c.isEmpty();
            }
        };
    }

    /**
     * Creates a view on given list with provided transformer and predicates.
     * Resulting list will only "have" elements for which all provided predicates, if any,
     * evaluate to {@code true}. Note that a new collection will be created and data will
     * be copied.
     *
     * @param c Input list that serves as a base for the view.
     * @param trans Transforming closure from T1 to T2.
     * @param p Optional predicates. If predicates are not provided - all elements will be in the view.
     * @return View on given list with provided predicate.
     */
    public static <T1, T2> List<T2> transformList(Collection<? extends T1> c,
        GridClosure<? super T1, T2> trans, @Nullable GridPredicate<? super T1>... p) {
        A.notNull(c, "c", trans, "trans");

        if (isAlwaysFalse(p)) {
            return Collections.emptyList();
        }

        return new ArrayList<T2>(transform(retain(c, true, p), trans));
    }

    /**
     * Creates a view on given set with provided transformer and predicates. Resulting set
     * will only "have" elements for which all provided predicates, if any, evaluate to {@code true}.
     * Note that a new collection will be created and data will be copied.
     *
     * @param c Input set that serves as a base for the view.
     * @param trans Transforming closure from T1 to T2.
     * @param p Optional predicates. If predicates are not provided - all elements will be in the view.
     * @return View on given set with provided predicate.
     */
    public static <T1, T2> Set<T2> transformSet(Collection<? extends T1> c,
        GridClosure<? super T1, T2> trans, @Nullable GridPredicate<? super T1>... p) {
        A.notNull(c, "c", trans, "trans");

        if (isAlwaysFalse(p)) {
            return Collections.emptySet();
        }

        return new HashSet<T2>(transform(retain(c, true, p), trans));
    }

    /**
     * Creates light-weight view on given map with provided predicates. Resulting map will
     * only "have" keys for which all provided predicates, if any, evaluates to {@code true}.
     * Note that only wrapping map will be created and no duplication of data will occur.
     * Also note that if array of given predicates is not empty then method {@code size()}
     * uses full iteration through the entry set.
     *
     * @param m Input map that serves as a base for the view.
     * @param p Optional predicates. If predicates are not provided - all will be in the view.
     * @param <K> Type of the key.
     * @param <V> Type of the value.
     * @return Light-weight view on given map with provided predicate.
     */
    public static <K0, K extends K0, V0, V extends V0> Map<K, V> view(@Nullable final Map<K, V> m,
        @Nullable final GridPredicate<? super K>... p) {
        if (isEmpty(m) || isAlwaysFalse(p))
            return Collections.emptyMap();

        assert m != null;

        return isEmpty(p) || isAlwaysTrue(p) ? m : new GridSerializableMap<K, V>() {
            /** Entry predicate. */
            private GridPredicate<Map.Entry<K, V>> ep = new P1<Map.Entry<K, V>>() {
                @Override public boolean apply(Entry<K, V> e) {
                    return isAll(e.getKey(), p);
                }
            };

            @Override public Set<Entry<K, V>> entrySet() {
                return new GridSerializableSet<Map.Entry<K, V>>() {
                    @Override public Iterator<Entry<K, V>> iterator() {
                        return iterator0(m.entrySet(), false, ep);
                    }

                    @Override public int size() {
                        return F.size(m.keySet(), p);
                    }

                    @SuppressWarnings({"unchecked"})
                    @Override public boolean remove(Object o) {
                        return F.isAll((Map.Entry<K, V>)o, ep) && m.entrySet().remove(o);
                    }

                    @SuppressWarnings({"unchecked"})
                    @Override public boolean contains(Object o) {
                        return F.isAll((Map.Entry<K, V>)o, ep) && m.entrySet().contains(o);
                    }

                    @Override public boolean isEmpty() {
                        return !iterator().hasNext();
                    }
                };
            }

            @Override public boolean isEmpty() {
                return entrySet().isEmpty();
            }

            @SuppressWarnings({"unchecked"})
            @Nullable @Override public V get(Object key) {
                return isAll((K)key, p) ? m.get(key) : null;
            }

            @Nullable @Override public V put(K key, V val) {
                V oldVal = get(key);

                if (isAll(key, p)) {
                    m.put(key, val);
                }

                return oldVal;
            }

            @SuppressWarnings({"unchecked"})
            @Override public boolean containsKey(Object key) {
                return isAll((K)key, p) && m.containsKey(key);
            }
        };
    }

    /**
     * Read-only view on map that supports transformation of values and key filtering. Resulting map will
     * only "have" keys for which all provided predicates, if any, evaluates to {@code true}.
     * Note that only wrapping map will be created and no duplication of data will occur.
     * Also note that if array of given predicates is not empty then method {@code size()}
     * uses full iteration through the entry set.
     *
     * @param m Input map that serves as a base for the view.
     * @param trans Transformer for map value transformation.
     * @param p Optional predicates. If predicates are not provided - all will be in the view.
     * @param <K> Type of the key.
     * @param <V> Type of the input map value.
     * @param <V1> Type of the output map value.
     * @return Light-weight view on given map with provided predicate and transformer.
     */
    public static <K0, K extends K0, V0, V extends V0, V1> Map<K, V1> viewReadOnly(@Nullable final Map<K, V> m,
        final GridClosure<V, V1> trans, @Nullable final GridPredicate<? super K>... p) {
        A.notNull(trans, "trans");

        if (isEmpty(m) || isAlwaysFalse(p))
            return Collections.emptyMap();

        assert m != null;

        return new GridSerializableMap<K, V1>() {
            /** Entry predicate. */
            private GridPredicate<Map.Entry<K, V>> ep = new P1<Map.Entry<K, V>>() {
                @Override public boolean apply(Entry<K, V> e) {
                    return isAll(e.getKey(), p);
                }
            };

            @Override public Set<Entry<K, V1>> entrySet() {
                return new GridSerializableSet<Map.Entry<K, V1>>() {
                    @Override public Iterator<Entry<K, V1>> iterator() {
                        return new Iterator<Entry<K, V1>>() {
                            private Iterator<Entry<K, V>> it = iterator0(m.entrySet(), true, ep);

                            @Override public boolean hasNext() {
                                return it.hasNext();
                            }

                            @Override public Entry<K, V1> next() {
                                final Entry<K, V> e = it.next();

                                return new Entry<K, V1>() {
                                    @Override public K getKey() {
                                        return e.getKey();
                                    }

                                    @Override public V1 getValue() {
                                        return trans.apply(e.getValue());
                                    }

                                    @Override public V1 setValue(V1 value) {
                                        throw new UnsupportedOperationException("Put is not supported for readonly map view.");
                                    }
                                };
                            }

                            @Override public void remove() {
                                throw new UnsupportedOperationException("Remove is not support for readonly map view.");
                            }
                        };
                    }

                    @Override public int size() {
                        return F.size(m.keySet(), p);
                    }

                    @SuppressWarnings({"unchecked"})
                    @Override public boolean remove(Object o) {
                        throw new UnsupportedOperationException("Remove is not support for readonly map view.");
                    }

                    @SuppressWarnings({"unchecked"})
                    @Override public boolean contains(Object o) {
                        return F.isAll((Map.Entry<K, V>)o, ep) && m.entrySet().contains(o);
                    }

                    @Override public boolean isEmpty() {
                        return !iterator().hasNext();
                    }
                };
            }

            @Override public boolean isEmpty() {
                return entrySet().isEmpty();
            }

            @SuppressWarnings({"unchecked"})
            @Nullable @Override public V1 get(Object key) {
                if (isAll((K)key, p)) {
                    V v = m.get(key);

                    if (v != null)
                        return trans.apply(v);
                }

                return null;
            }

            @Nullable @Override public V1 put(K key, V1 val) {
                throw new UnsupportedOperationException("Put is not supported for readonly map view.");
            }

            @Override public V1 remove(Object key) {
                throw new UnsupportedOperationException("Remove is not supported for readonly map view.");
            }

            @SuppressWarnings({"unchecked"})
            @Override public boolean containsKey(Object key) {
                return isAll((K)key, p) && m.containsKey(key);
            }
        };
    }

    /**
     * Tests if given string is {@code null} or empty.
     *
     * @param s String to test.
     * @return Whether or not the given string is {@code null} or empty.
     */
    public static boolean isEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Tests if the given array is either {@code null} or empty.
     *
     * @param c Array to test.
     * @return Whether or not the given array is {@code null} or empty.
     */
    public static <T> boolean isEmpty(@Nullable T[] c) {
        return c == null || c.length == 0;
    }

    /**
     * Tests if the given array is either {@code null} or empty.
     *
     * @param c Array to test.
     * @return Whether or not the given array is {@code null} or empty.
     */
    public static boolean isEmpty(@Nullable int[] c) {
        return c == null || c.length == 0;
    }

    /**
     * Tests if the given collection is either {@code null} or empty.
     *
     * @param c Collection to test.
     * @return Whether or not the given collection is {@code null} or empty.
     */
    public static boolean isEmpty(@Nullable Iterable<?> c) {
        return c == null || !c.iterator().hasNext();
    }

    /**
     * Tests if the given collection is either {@code null} or empty.
     *
     * @param c Collection to test.
     * @return Whether or not the given collection is {@code null} or empty.
     */
    public static boolean isEmpty(@Nullable Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /**
     * Tests if the given map is either {@code null} or empty.
     *
     * @param m Map to test.
     * @return Whether or not the given collection is {@code null} or empty.
     */
    public static boolean isEmpty(@Nullable Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    /**
     * Utility map getter. This method analogous to {@link #addIfAbsent(Map, Object, Callable)}
     * method but this one doesn't put the default value into the map when key is not found.
     *
     * @param map Map to get value from.
     * @param key Map key (can be {@code null}).
     * @param c Optional factory closure for the default value to be returned in
     *      when {@code key} is not found. If closure is not provided - {@code null} will be returned.
     * @param <K> Map key type.
     * @param <V> Map value type.
     * @return Value for the {@code key} or default value produced by {@code c} if key is not
     *      found (or {@code null} if key is not found and closure is not provided).
     * @throws GridClosureException Thrown in case when callable throws exception.
     * @see #newLinkedList()
     * @see #newList()
     * @see #newSet()
     * @see #newMap()
     * @see #newAtomicLong()
     * @see #newAtomicInt()
     * @see #newAtomicRef()
     * @see #newAtomicBoolean()
     */
    @Nullable
    public static <K, V> V returnIfAbsent(Map<? extends K, ? extends V> map, @Nullable K key,
        @Nullable Callable<V> c) {
        A.notNull(map, "map");

        try {
            return !map.containsKey(key) ? c == null ? null : c.call() : map.get(key);
        }
        catch (Exception e) {
            throw wrap(e);
        }
    }

    /**
     * Returns a factory closure that creates new {@link List} instance. Note that this
     * method does not create a new closure but returns a static one.
     *
     * @param <T> Type parameters for the created {@link List}.
     * @return Factory closure that creates new {@link List} instance every
     *      time its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOutClosure<List<T>> newList() {
        return (GridOutClosure<List<T>>)LIST_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link AtomicInteger} instance
     * initialized to {@code zero}. Note that this method does not create a new
     * closure but returns a static one.
     *
     * @return Factory closure that creates new {@link AtomicInteger} instance
     *      initialized to {@code zero} every time its {@link GridOutClosure#apply()} method is called.
     */
    public static GridOutClosure<AtomicInteger> newAtomicInt() {
        return ATOMIC_INT_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link AtomicLong} instance
     * initialized to {@code zero}. Note that this method does not create a new
     * closure but returns a static one.
     *
     * @return Factory closure that creates new {@link AtomicLong} instance
     *      initialized to {@code zero} every time its {@link GridOutClosure#apply()} method is called.
     */
    public static GridOutClosure<AtomicLong> newAtomicLong() {
        return ATOMIC_LONG_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link AtomicReference} instance
     * initialized to {@code null}. Note that this method does not create a new closure
     * but returns a static one.
     *
     * @param <T> Type of the atomic reference.
     * @return Factory closure that creates new {@link AtomicReference} instance
     *      initialized to {@code null} every time its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOutClosure<AtomicReference<T>> newAtomicRef() {
        return (GridOutClosure<AtomicReference<T>>)ATOMIC_REF_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link AtomicBoolean} instance
     * initialized to {@code false}. Note that this method does not create a new
     * closure but returns a static one.
     *
     * @return Factory closure that creates new {@link AtomicBoolean} instance
     *      initialized to {@code false} every time its {@link GridOutClosure#apply()} method is called.
     */
    public static GridOutClosure<AtomicBoolean> newAtomicBoolean() {
        return ATOMIC_BOOL_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link LinkedList} instance.
     * Note that this method does not create a new closure but returns a static one.
     *
     * @param <T> Type parameters for the created {@link LinkedList}.
     * @return Factory closure that creates new {@link LinkedList} instance every time its {@link
     *         GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOutClosure<LinkedList<T>> newLinkedList() {
        return (GridOutClosure<LinkedList<T>>)LINKED_LIST_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link Set} instance. Note that this
     * method does not create a new closure but returns a static one.
     *
     * @param <T> Type parameters for the created {@link Set}.
     * @return Factory closure that creates new {@link Set} instance every time
     *      its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOutClosure<Set<T>> newSet() {
        return (GridOutClosure<Set<T>>)SET_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link Map} instance. Note
     * that this method does not create a new closure but returns a static one.
     *
     * @param <K> Type of the key for the created {@link Map}.
     * @param <V> Type of the value for the created {@link Map}.
     * @return Factory closure that creates new {@link Map} instance every
     *      time its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> GridOutClosure<Map<K, V>> newMap() {
        return (GridOutClosure<Map<K, V>>)MAP_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link ConcurrentMap} instance.
     * Note that this method does not create a new closure but returns a static one.
     *
     * @param <K> Type of the key for the created {@link ConcurrentMap}.
     * @param <V> Type of the value for the created {@link ConcurrentMap}.
     * @return Factory closure that creates new {@link Map} instance every
     *      time its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> GridOutClosure<ConcurrentMap<K, V>> newCMap() {
        return (GridOutClosure<ConcurrentMap<K, V>>)CONCURRENT_MAP_FACTORY;
    }

    /**
     * Returns a factory closure that creates new {@link GridConcurrentHashSet} instance.
     * Note that this method does not create a new closure but returns a static one.
     *
     * @return Factory closure that creates new {@link GridConcurrentHashSet} instance every
     *      time its {@link GridOutClosure#apply()} method is called.
     */
    @SuppressWarnings("unchecked")
    public static <E> GridOutClosure<Set<E>> newCSet() {
        return (GridOutClosure<Set<E>>)CONCURRENT_SET_FACTORY;
    }

    /**
     * Creates and returns iterable from given collection and optional filtering predicates.
     * Returned iterable collection will only have elements for which all given predicates
     * evaluates to {@code true}. Note that this method will not create new collection but
     * will simply "skip" elements in the provided collection that given predicates doesn't
     * evaluate to {@code true} for.
     *
     * @param c Input collection.
     * @param p Optional filtering predicates.
     * @param <T> Type of the collection elements.
     * @return Iterable from given collection and optional filtering predicates.
     */
    public static <T> GridIterable<T> iterable(Collection<? extends T> c, GridPredicate<? super T>... p) {
        return new GridIterableAdapter<T>(F.iterator0(c, false, p));
    }

    /**
     * Gets closure that wraps access to the given collection. Every time resulting
     * closure is called it will return the next element in the input collection.
     *
     * @param c Input collection.
     * @param cyclic Determines whether input collection will rewind when end is reached
     *      and start again from the beginning - or {@link NoSuchElementException} exception
     *      is thrown.
     * @param <T> Type of the collection.
     * @return Out closure that wraps access to input collection.
     * @see #jobProducer(GridJob[], boolean)
     */
    public static <T> GridOutClosure<T> producer(final Iterable<? extends T> c, final boolean cyclic) {
        A.notNull(c, "c");

        return new CO<T>() {
            private Iterator<? extends T> iter = c.iterator();

            {
                Iterator<? extends T> t = c.iterator();

                if (t.hasNext()) {
                    peerDeployLike(t.next());
                }
            }

            @Override public T apply() {
                if (!iter.hasNext()) {
                    if (cyclic) {
                        iter = c.iterator();
                    }
                    else {
                        throw new NoSuchElementException();
                    }
                }

                return iter.next();
            }
        };
    }

    /**
     * Gets closure that wraps access to the given collection. Every time resulting
     * closure is called it will return the next element in the input collection.
     *
     * @param c Input collection.
     * @param cyclic Determines whether input collection will rewind when end is reached
     *      and start again from the beginning - or {@link NoSuchElementException} exception
     *      is thrown.
     * @param <T> Type of the collection.
     * @return Out closure that wraps access to input collection.
     * @see #jobProducer(GridJob[], boolean)
     */
    public static <T> GridOutClosure<T> producer(T[] c, boolean cyclic) {
        A.notNull(c, "c");

        return producer(asList(c), cyclic);
    }

    /**
     * Gets closure that wraps access to the given collection of {@link GridJob} instances.
     * Every time resulting closure is called it will return the next job in the input collection.
     * Note that this method will wrap job from input collection using {@link GridJobWrapper}
     * class before returning it. It is convenient when the resulting closure is used to produce
     * jobs that server as map's keys.
     *
     * @param c Input jobs array.
     * @param cyclic Determines whether input collection will rewind when end is reached and start
     *      again from the beginning - or {@link NoSuchElementException} exception is thrown.
     * @return Out closure that wraps access to input collection.
     * @see #producer(Object[], boolean)
     * @see #producer(Iterable, boolean)
     */
    public static GridOutClosure<GridJob> jobProducer(GridJob[] c, boolean cyclic) {
        A.notNull(c, "c");

        return jobProducer(asList(c), cyclic);
    }

    /**
     * Gets closure that wraps access to the given collection of {@link GridJob} instances.
     * Every time resulting closure is called it will return the next job in the input collection.
     * Note that this method will wrap job from input collection using {@link GridJobWrapper}
     * class before returning it. It is convenient when the resulting closure is used to produce
     * jobs that server as map's keys.
     *
     * @param jobs Input jobs collection.
     * @param cyclic Determines whether input collection will rewind when end is reached and
     *      start again from the beginning - or {@link NoSuchElementException} exception is thrown.
     * @return Out closure that wraps access to input collection.
     * @see #producer(Object[], boolean)
     * @see #producer(Iterable, boolean)
     */
    public static GridOutClosure<GridJob> jobProducer(final Iterable<? extends GridJob> jobs, final boolean cyclic) {
        A.notNull(jobs, "jobs");

        return new CO<GridJob>() {
            private Iterator<? extends GridJob> iter = jobs.iterator();

            {
                Iterator<? extends GridJob> t = jobs.iterator();

                if (t.hasNext()) {
                    peerDeployLike(t.next());
                }
            }

            @Override public GridJob apply() {
                if (!iter.hasNext()) {
                    if (cyclic) {
                        iter = jobs.iterator();
                    }
                    else {
                        throw new NoSuchElementException();
                    }
                }

                return new GridJobWrapper(iter.next(), true);
            }
        };
    }

    /**
     * Creates and returns iterator from given collection and optional filtering predicates.
     * Returned iterator will only have elements for which all given predicates evaluates to
     * {@code true} (if provided). Note that this method will not create new collection but
     * will simply "skip" elements in the provided collection that given predicates doesn't
     * evaluate to {@code true} for.
     *
     * @param c Input collection.
     * @param readOnly If {@code true}, then resulting iterator will not allow modifications
     *      to the underlying collection.
     * @param p Optional filtering predicates.
     * @param <T> Type of the collection elements.
     * @return Iterator from given collection and optional filtering predicate.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> GridIterator<T> iterator0(Collection<? extends T> c, boolean readOnly,
        GridPredicate<? super T>... p) {
        return F.iterator(c, IDENTITY, readOnly, p);
    }

    /**
     * Creates and returns transforming iterator from given collection and optional
     * filtering predicates. Returned iterator will only have elements for which all
     * given predicates evaluates to {@code true} ( if provided). Note that this method
     * will not create new collection but will simply "skip" elements in the provided
     * collection that given predicates doesn't evaluate to {@code true} for.
     *
     * @param c Input collection.
     * @param trans Transforming closure to convert from T1 to T2.
     * @param readOnly If {@code true}, then resulting iterator will not allow modifications
     *      to the underlying collection.
     * @param p Optional filtering predicates.
     * @param <T1> Type of the collection elements.
     * @param <T2> Type of returned elements.
     * @return Iterator from given collection and optional filtering predicate.
     */
    public static <T1, T2> GridIterator<T2> iterator(final Collection<? extends T1> c,
        final GridClosure<? super T1, T2> trans, final boolean readOnly,
        @Nullable final GridPredicate<? super T1>... p) {
        A.notNull(c, "c", trans, "trans");

        if (isAlwaysFalse(p)) {
            return F.emptyIterator();
        }

        return new GridIteratorAdapter<T2>() {
            /** */
            private T1 elem;

            /** */
            private boolean moved = true;

            /** */
            private boolean more;

            /** */
            private Iterator<? extends T1> iter = c.iterator();

            @Override public boolean hasNext() {
                if (isEmpty(p)) {
                    return iter.hasNext();
                }
                else {
                    if (!moved) {
                        return more;
                    }
                    else {
                        more = false;

                        while (iter.hasNext()) {
                            elem = iter.next();

                            if (isAll(elem, p)) {
                                more = true;
                                moved = false;

                                return true;
                            }
                        }

                        // Give to GC.
                        elem = null;

                        return false;
                    }
                }
            }

            @Nullable @Override public T2 next() {
                if (isEmpty(p)) {
                    return trans.apply(iter.next());
                }
                else {
                    if (hasNext()) {
                        moved = true;

                        return trans.apply(elem);
                    }
                    else {
                        throw new NoSuchElementException();
                    }
                }
            }

            @Override public void remove() {
                if (readOnly) {
                    throw new UnsupportedOperationException("Cannot modify read-only iterator.");
                }

                iter.remove();
            }
        };
    }

    /**
     * Gets predicate that always returns {@code true}. This method returns
     * constant predicate.
     *
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that always returns {@code true}.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> GridPredicate<T> alwaysTrue() {
        return (GridPredicate<T>)ALWAYS_TRUE;
    }

    /**
     * Gets predicate that always returns {@code false}. This method returns
     * constant predicate.
     *
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that always returns {@code false}.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> GridPredicate<T> alwaysFalse() {
        return (GridPredicate<T>)ALWAYS_FALSE;
    }

    /**
     * Tests whether or not given predicate is the one returned from
     * {@link #alwaysTrue()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given predicate is {@code ALWAYS_TRUE} predicate.
     */
    public static boolean isAlwaysTrue(GridPredicate p) {
        return p == ALWAYS_TRUE;
    }

    /**
     * Tests whether or not given set of predicates consists only of one predicate returned from
     * {@link #alwaysTrue()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given contains only {@code ALWAYS_TRUE} predicate.
     */
    public static boolean isAlwaysTrue(@Nullable GridPredicate[] p) {
        return p != null && p.length == 1 && isAlwaysTrue(p[0]);
    }

    /**
     * Tests whether or not given predicate is the one returned from
     * {@link #alwaysFalse()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given predicate is {@code ALWAYS_FALSE} predicate.
     */
    public static boolean isAlwaysFalse(GridPredicate p) {
        return p == ALWAYS_FALSE;
    }

    /**
     * Tests whether or not given set of predicates consists only of one predicate returned from
     * {@link #alwaysFalse()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given contains only {@code ALWAYS_FALSE} predicate.
     */
    public static boolean isAlwaysFalse(@Nullable GridPredicate[] p) {
        return p != null && p.length == 1 && isAlwaysFalse(p[0]);
    }

    /**
     * Tests whether or not given set of predicates consists only of one predicate returned from
     * {@link #alwaysFalse()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given contains only {@code ALWAYS_FALSE} predicate.
     */
    private static boolean isAlwaysFalse(@Nullable Iterable<? extends GridPredicate> p) {
        if (p == null || !p.iterator().hasNext()) {
            return false;
        }

        Iterator<? extends GridPredicate> iter = p.iterator();

        GridPredicate first = iter.next();

        return !iter.hasNext() && isAlwaysFalse(first);
    }

    /**
     * Tests whether or not given set of predicates consists only of one predicate returned from
     * {@link #alwaysTrue()} method.
     *
     * @param p Predicate to check.
     * @return {@code true} if given contains only {@code ALWAYS_FALSE} predicate.
     */
    private static boolean isAlwaysTrue(@Nullable Iterable<? extends GridPredicate> p) {
        if (p == null || !p.iterator().hasNext()) {
            return false;
        }

        Iterator<? extends GridPredicate> iter = p.iterator();

        GridPredicate first = iter.next();

        return !iter.hasNext() && isAlwaysTrue(first);
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is {@code null}.
     *
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is {@code null}.
     */
    public static <T> GridPredicate<T> isNull() {
        return new P1<T>() {
            @Override public boolean apply(T t) {
                return t == null;
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is not {@code null}.
     *
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is not {@code null}.
     */
    public static <T> GridPredicate<T> notNull() {
        return new P1<T>() {
            @Override public boolean apply(T t) {
                return t != null;
            }
        };
    }

    /**
     * Negates given predicates.
     * <p>
     * Gets predicate that evaluates to {@code true} if any of given predicates
     * evaluates to {@code false}. If all predicates evaluate to {@code true} the
     * result predicate will evaluate to {@code false}.
     *
     * @param p Predicate to negate.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Negated predicate.
     */
    public static <T> GridPredicate<T> not(@Nullable final GridPredicate<? super T>... p) {
        return isAlwaysFalse(p) ? F.<T>alwaysTrue() : isAlwaysTrue(p) ? F.<T>alwaysFalse() : new P1<T>() {
            {
                if (!isEmpty(p)) {
                    peerDeployLike(U.peerDeployAware0((Object[])p));
                }
            }

            @Override public boolean apply(T t) {
                return !isAll(t, p);
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is equal
     * to {@code target} or both are {@code null}.
     *
     * @param target Object to compare free variable to.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is equal to
     *      {@code target} or both are {@code null}.
     */
    public static <T> GridPredicate<T> equalTo(@Nullable final T target) {
        return new P1<T>() {
            {
                if (target != null) {
                    peerDeployLike(target);
                }
            }

            @Override public boolean apply(T t) {
                return eq(t, target);
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is not equal
     * to {@code target} or both are {@code null}.
     *
     * @param target Object to compare free variable to.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is not equal
     *      to {@code target} or both are {@code null}.
     */
    public static <T> GridPredicate<T> notEqualTo(@Nullable final T target) {
        return new P1<T>() {
            {
                if (target != null) {
                    peerDeployLike(target);
                }
            }

            @Override public boolean apply(T t) {
                return !eq(t, target);
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is instance of the given class.
     *
     * @param cls Class to compare to.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is instance
     *      of the given class.
     */
    public static <T> GridPredicate<T> instanceOf(final Class<?> cls) {
        A.notNull(cls, "cls");

        return new P1<T>() {
            @Override public boolean apply(T t) {
                return t != null && cls.isAssignableFrom(t.getClass());
            }
        };
    }

    /**
     * Gets predicate that evaluates to {@code true} if its free variable is not an instance
     * of the given class.
     *
     * @param cls Class to compare to.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if its free variable is not an instance
     *      of the given class.
     */
    public static <T> GridPredicate<T> notInstanceOf(final Class<?> cls) {
        A.notNull(cls, "cls");

        return new P1<T>() {
            @Override public boolean apply(T t) {
                return t == null || !cls.isAssignableFrom(t.getClass());
            }
        };
    }

    /**
     * Gets first element from given collection or returns {@code null} if the collection is empty.
     *
     * @param c A collection.
     * @param <T> Type of the collection.
     * @return Collections' first element or {@code null} in case if the collection is empty.
     */
    @Nullable public static <T> T first(@Nullable Iterable<? extends T> c) {
        if (isEmpty(c))
            return null;

        assert c != null;
        
        Iterator<? extends T> it = c.iterator();

        return it.hasNext() ? it.next() : null;
    }

    /**
     * Gets last element from given collection or returns {@code null} if the collection is empty.
     *
     * @param c A collection.
     * @param <T> Type of the collection.
     * @return Collections' first element or {@code null} in case if the collection is empty.
     */
    @Nullable public static <T> T last(@Nullable Iterable<? extends T> c) {
        if (isEmpty(c))
            return null;

        assert c != null;

        if (c instanceof RandomAccess && c instanceof List) {
            List<T> l = (List<T>)c;

            return l.get(l.size() - 1);
        }
        else if (c instanceof NavigableSet) {
            NavigableSet<T> s = (NavigableSet<T>)c;

            return s.last();
        }

        T last = null;

        for (T t : c)
            last = t;

        return last;
    }

    /**
     * Gets first value from given map or returns {@code null} if the map is empty.
     *
     * @param m A map.
     * @param <V> Value type.
     * @return Maps' first value or {@code null} in case if the map is empty.
     */
    @Nullable public static <V> V firstValue(Map<?, V> m) {
        Iterator<V> it = m.values().iterator();

        return it.hasNext() ? it.next() : null;
    }

    /**
     * Gets first key from given map or returns {@code null} if the map is empty.
     *
     * @param m A map.
     * @param <K> Key type.
     * @return Maps' first key or {@code null} in case if the map is empty.
     */
    @Nullable public static <K> K firstKey(Map<K, ?> m) {
        Iterator<K> it = m.keySet().iterator();

        return it.hasNext() ? it.next() : null;
    }

    /**
     * Gets first key from given map or returns {@code null} if the map is empty.
     *
     * @param m A map.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Map's first entry or {@code null} in case if the map is empty.
     */
    @Nullable public static <K, V> Map.Entry firstEntry(Map<K, V> m) {
        Iterator<Map.Entry<K, V>> it = m.entrySet().iterator();

        return it.hasNext() ? it.next() : null;
    }

    /**
     * Tests if all passed in predicates are instances of {@link GridNodePredicate} class.
     *
     * @param ps Collection of predicates to test.
     * @return {@code True} if all passed in predicates are instances of {@link GridNodePredicate} class.
     */
    private static boolean isAllNodePredicates(@Nullable Iterable<? extends GridPredicate<?>> ps) {
        if (isEmpty(ps)) {
            return false;
        }

        assert ps != null;

        for (GridPredicate<?> p : ps) {
            if (!(p instanceof GridNodePredicate)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if all passed in predicates are instances of {@link GridNodePredicate} class.
     *
     * @param ps Collection of predicates to test.
     * @return {@code True} if all passed in predicates are instances of {@link GridNodePredicate} class.
     */
    private static boolean isAllNodePredicates(@Nullable GridPredicate<?>... ps) {
        if (isEmpty(ps)) {
            return false;
        }

        assert ps != null;

        for (GridPredicate<?> p : ps) {
            if (!(p instanceof GridNodePredicate)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get a predicate that evaluates to {@code true} if each of its component predicates
     * evaluates to {@code true}. The components are evaluated in order they are supplied.
     * Evaluation will be stopped as soon as first predicate evaluates to {@code false}.
     * Passed in predicates are NOT copied. If no predicates are passed in the returned
     * predicate will always evaluate to {@code false}.
     *
     * @param ps Passed in predicate.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if each of its component predicates
     *      evaluates to {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridPredicate<T> and(@Nullable final Collection<? extends GridPredicate<? super T>> ps) {
        if (isEmpty(ps)) {
            return F.alwaysTrue();
        }

        assert ps != null;

        if (isAllNodePredicates(ps)) {
            Collection<UUID> ids = new ArrayList<UUID>();

            for (GridPredicate<? super T> p : ps) {
                Collection<UUID> list = Arrays.asList(((GridNodePredicate)p).nodeIds());

                if (ids.isEmpty()) {
                    ids.addAll(list);
                }
                else {
                    ids.retainAll(list);
                }
            }

            // T must be <T extends GridNode>.
            return (GridPredicate<T>)new GridNodePredicate(ids);
        }
        else {
            return new P1<T>() {
                {
                    peerDeployLike(U.peerDeployAware0(ps));
                }

                @Override public boolean apply(T t) {
                    for (GridPredicate<? super T> p : ps) {
                        if (!p.apply(t)) {
                            return false;
                        }
                    }

                    return true;
                }
            };
        }
    }

    /**
     * Get a predicate that evaluates to {@code true} if each of its component predicates
     * evaluates to {@code true}. The components are evaluated in order they are supplied.
     * Evaluation will be stopped as soon as first predicate evaluates to {@code false}.
     * Passed in predicates are NOT copied. If no predicates are passed in the returned
     * predicate will always evaluate to {@code false}.
     *
     * @param p1 Passed in predicates.
     * @param p2 Passed in predicates.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if each of its component predicates
     *      evaluates to {@code true}.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> GridPredicate<T> and(@Nullable final GridPredicate<? super T>[] p1,
        @Nullable final GridPredicate<? super T>... p2) {
        if (isAlwaysFalse(p1) || isAlwaysFalse(p2)) {
            return F.alwaysFalse();
        }

        if (isAlwaysTrue(p1) && isAlwaysTrue(p2)) {
            return F.alwaysTrue();
        }

        final boolean e1 = isEmpty(p1);
        final boolean e2 = isEmpty(p2);

        if (e1 && e2) {
            return F.alwaysTrue();
        }

        if (e1 && !e2) {
            assert p2 != null;

            if (p2.length == 1) {
                return (GridPredicate<T>)p2[0];
            }
        }

        if (!e1 && e2) {
            assert p1 != null;

            if (p1.length == 1) {
                return (GridPredicate<T>)p1[0];
            }
        }

        if ((e1 || isAllNodePredicates(p1)) && (e2 || isAllNodePredicates(p2))) {
            Collection<UUID> ids = new GridLeanSet<UUID>();

            if (!e1) {
                assert p1 != null;

                for (GridPredicate<? super T> p : p1) {
                    ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                }
            }

            if (!e2) {
                assert p2 != null;

                for (GridPredicate<? super T> p : p2) {
                    ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                }
            }

            // T must be <T extends GridNode>.
            return (GridPredicate<T>)new GridNodePredicate(ids);
        }
        else {
            return new P1<T>() {
                {
                    if (!e1 && e2) {
                        peerDeployLike(U.peerDeployAware0((Object[])p1));
                    }
                    else if (e1 && !e2) {
                        peerDeployLike(U.peerDeployAware0((Object[])p2));
                    }
                    else {
                        assert !e1 && !e2;

                        peerDeployLike(flat0(p1,p2));
                    }
                }

                @Override public boolean apply(T t) {
                    if (!e1) {
                        assert p1 != null;

                        for (GridPredicate<? super T> p : p1) {
                            if (!p.apply(t)) {
                                return false;
                            }
                        }
                    }

                    if (!e2) {
                        assert p2 != null;

                        for (GridPredicate<? super T> p : p2) {
                            if (!p.apply(t)) {
                                return false;
                            }
                        }
                    }

                    return true;
                }
            };
        }
    }

    /**
     * Get a predicate that evaluates to {@code true} if each of its component predicates
     * evaluates to {@code true}. The components are evaluated in order they are supplied.
     * Evaluation will be stopped as soon as first predicate evaluates to {@code false}.
     * Passed in predicates are NOT copied. If no predicates are passed in the returned
     * predicate will always evaluate to {@code false}.
     *
     * @param ps Passed in predicate. If none provided - always-{@code false} predicate is
     *      returned.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if each of its component predicates
     *      evaluates to {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridPredicate<T> and(@Nullable final GridPredicate<? super T>... ps) {
        if (isEmpty(ps)) {
            return F.alwaysTrue();
        }

        if (isAlwaysFalse(ps)) {
            return F.alwaysFalse();
        }
        if (isAlwaysTrue(ps)) {
            return F.alwaysTrue();
        }
        else {
            if (isAllNodePredicates(ps)) {
                assert ps != null;

                Collection<UUID> ids = new ArrayList<UUID>();

                for (GridPredicate<? super T> p : ps) {
                    Collection<UUID> list = Arrays.asList(((GridNodePredicate)p).nodeIds());

                    if (ids.isEmpty()) {
                        ids.addAll(list);
                    }
                    else {
                        ids.retainAll(list);
                    }
                }

                // T must be <T extends GridNode>.
                return (GridPredicate<T>)new GridNodePredicate(ids);
            }
            else {
                return new P1<T>() {
                    {
                        peerDeployLike(U.peerDeployAware0((Object[])ps));
                    }

                    @Override public boolean apply(T t) {
                        assert ps != null;

                        for (GridPredicate<? super T> p : ps) {
                            if (!p.apply(t)) {
                                return false;
                            }
                        }

                        return true;
                    }
                };
            }
        }
    }

    /**
     * Get a predicate that evaluates to {@code true} if any of its component
     * predicates evaluates to {@code true}. The components are evaluated in order
     * they are supplied. Evaluation will be stopped as soon as first predicate
     * evaluates to {@code true}. Passed in predicates are NOT copied. If no predicates
     * are passed in the returned predicate will always evaluate to {@code false}.
     *
     * @param ps Passed in predicate.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if any of its component predicates
     *      evaluates to {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridPredicate<T> or(@Nullable final Collection<? extends GridPredicate<? super T>> ps) {
        if (isEmpty(ps)) {
            return F.alwaysFalse();
        }
        else {
            assert ps != null;

            if (isAllNodePredicates(ps)) {
                Collection<UUID> ids = new GridLeanSet<UUID>();

                for (GridPredicate<? super T> p : ps) {
                    ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                }

                // T must be <T extends GridNode>.
                return (GridPredicate<T>)new GridNodePredicate(ids);
            }
            else {
                return new P1<T>() {
                    {
                        peerDeployLike(U.peerDeployAware0(ps));
                    }

                    @Override public boolean apply(T t) {
                        for (GridPredicate<? super T> p : ps) {
                            if (p.apply(t)) {
                                return true;
                            }
                        }

                        return false;
                    }
                };
            }
        }
    }

    /**
     * Get a predicate that evaluates to {@code true} if any of its component predicates
     * evaluates to {@code true}. The components are evaluated in order they are supplied.
     * Evaluation will be stopped as soon as first predicate evaluates to {@code false}.
     * Passed in predicates are NOT copied. If no predicates are passed in the returned
     * predicate will always evaluate to {@code false}.
     *
     * @param p1 Passed in predicates.
     * @param p2 Passed in predicates.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if any of its component predicates
     *      evaluates to {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridPredicate<T> or(@Nullable final GridPredicate<? super T>[] p1,
        @Nullable final GridPredicate<? super T>... p2) {
        if (isEmpty(p1) && isEmpty(p2)) {
            return F.alwaysFalse();
        }

        if (isAlwaysTrue(p1) || isAlwaysTrue(p2)) {
            return F.alwaysTrue();
        }

        if (isAlwaysFalse(p1) && isAlwaysFalse(p2)) {
            return F.alwaysFalse();
        }

        final boolean e1 = isEmpty(p1);
        final boolean e2 = isEmpty(p2);

        if (e1 && e2) {
            return F.alwaysFalse();
        }

        if (e1 && !e2) {
            assert p2 != null;

            if (p2.length == 1) {
                return (GridPredicate<T>)p2[0];
            }
        }

        if (!e1 && e2) {
            assert p1 != null;

            if (p1.length == 1) {
                return (GridPredicate<T>)p1[0];
            }
        }

        if ((e1 || isAllNodePredicates(p1)) && (e2 || isAllNodePredicates(p2))) {
            Collection<UUID> ids = new GridLeanSet<UUID>();

            if (!e1) {
                assert p1 != null;

                for (GridPredicate<? super T> p : p1) {
                    ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                }
            }

            if (!e2) {
                assert p2 != null;

                for (GridPredicate<? super T> p : p2) {
                    ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                }
            }

            // T must be <T extends GridNode>.
            return (GridPredicate<T>)new GridNodePredicate(ids);
        }
        else {
            return new P1<T>() {
                {
                    if (!e1 && e2) {
                        peerDeployLike(U.peerDeployAware0((Object[])p1));
                    }
                    else if (e1 && !e2) {
                        peerDeployLike(U.peerDeployAware0((Object[])p2));
                    }
                    else {
                        assert !e1 && !e2;

                        peerDeployLike(flat0(p1, p2));
                    }
                }

                @Override public boolean apply(T t) {
                    if (!e1) {
                        assert p1 != null;

                        for (GridPredicate<? super T> p : p1) {
                            if (p.apply(t)) {
                                return true;
                            }
                        }
                    }

                    if (!e2) {
                        assert p2 != null;

                        for (GridPredicate<? super T> p : p2) {
                            if (!p.apply(t)) {
                                return true;
                            }
                        }
                    }

                    return false;
                }
            };
        }
    }

    /**
     * Get a predicate that evaluates to {@code true} if any of its component predicates
     * evaluates to {@code true}. The components are evaluated in order they are supplied.
     * Evaluation will be stopped as soon as first predicate evaluates to {@code true}.
     * Passed in predicates are NOT copied. If no predicates are passed in the returned
     * predicate will always evaluate to {@code false}.
     *
     * @param ps Passed in predicate.
     * @param <T> Type of the free variable, i.e. the element the predicate is called on.
     * @return Predicate that evaluates to {@code true} if any of its component predicates evaluates
     * to {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridPredicate<T> or(@Nullable final GridPredicate<? super T>... ps) {
        if (isEmpty(ps) || isAlwaysFalse(ps)) {
            return F.alwaysFalse();
        }
        else {
            if (isAlwaysTrue(ps)) {
                return F.alwaysTrue();
            }
            else {
                if (isAllNodePredicates(ps)) {
                    Collection<UUID> ids = new GridLeanSet<UUID>();

                    assert ps != null;

                    for (GridPredicate<? super T> p : ps) {
                        ids.addAll(Arrays.asList(((GridNodePredicate)p).nodeIds()));
                    }

                    // T must be <T extends GridNode>.
                    return (GridPredicate<T>)new GridNodePredicate(ids);
                }
                else {
                    return new P1<T>() {
                        {
                            peerDeployLike(U.peerDeployAware0((Object[])ps));
                        }

                        @Override public boolean apply(T t) {
                            assert ps != null;

                            for (GridPredicate<? super T> p : ps) {
                                if (p.apply(t)) {
                                    return true;
                                }
                            }

                            return false;
                        }
                    };
                }
            }
        }
    }

    /**
     * Gets a predicate that is composed of given predicate and closure. For every {@code x}
     * it returns predicate {@code p(f(x))}. Note that predicate and closure must be loaded
     * by the same class loader.
     *
     * @param p Predicate.
     * @param f Closure.
     * @param <X> Type of the free variable for the closure.
     * @param <Y> Type of the closure's return value.
     * @return Predicate that is composed of given predicate and closure.
     */
    @SuppressWarnings({"JavaDoc"})
    public static <X, Y> GridPredicate<X> compose(final GridPredicate<? super Y> p,
        final GridClosure<? super X, ? extends Y> f) {
        A.notNull(p, "p", f, "f");

        return isAlwaysFalse(p) ? F.<X>alwaysFalse() : isAlwaysTrue(p) ? F.<X>alwaysTrue() : new P1<X>() {
            {
                peerDeployLike(U.peerDeployAware0(p, f));
            }

            @Override public boolean apply(X x) {
                return p.apply(f.apply(x));
            }
        };
    }

    /**
     * Gets closure that returns constant value.
     *
     * @param val Constant value to return.
     * @param <T1> Type of the free variable for the closure.
     * @param <R> Type of the closure's return value.
     * @return Closure that returns constant value.
     */
    public static <T1, R> GridClosure<T1, R> constant1(@Nullable final R val) {
        return new C1<T1, R>() {
            {
                peerDeployLike(U.peerDeployAware(val));
            }

            @Nullable
            @Override public R apply(T1 t1) {
                return val;
            }
        };
    }

    /**
     * Gets closure that returns constant value.
     *
     * @param val Constant value to return.
     * @param <T1> Type of the free variable for the closure.
     * @param <T2> Type of the free variable for the closure.
     * @param <R> Type of the closure's return value.
     * @return Closure that returns constant value.
     */
    public static <T1, T2, R> GridClosure2<T1, T2, R> constant2(@Nullable final R val) {
        return U.withMeta(new C2<T1, T2, R>() {
            {
                peerDeployLike(U.peerDeployAware(val));
            }

            @Nullable
            @Override public R apply(T1 t1, T2 t2) {
                return val;
            }
        }, val);
    }

    /**
     * Gets closure that returns constant value.
     *
     * @param val Constant value to return.
     * @param <T1> Type of the free variable for the closure.
     * @param <T2> Type of the free variable for the closure.
     * @param <T3> Type of the free variable for the closure.
     * @param <R> Type of the closure's return value.
     * @return Closure that returns constant value.
     */
    public static <T1, T2, T3, R> GridClosure3<T1, T2, T3, R> constant3(@Nullable final R val) {
        return new C3<T1, T2, T3, R>() {
            {
                peerDeployLike(U.peerDeployAware(val));
            }

            @Nullable
            @Override public R apply(T1 t1, T2 t2, T3 t3) {
                return val;
            }
        };
    }

    /**
     * Gets closure that returns constant value.
     *
     * @param val Constant value to return.
     * @param <R> Type of the closure's return value.
     * @return Closure that returns constant value.
     */
    public static <R> GridOutClosure<R> constant(@Nullable final R val) {
        return new CO<R>() {
            {
                peerDeployLike(U.peerDeployAware(val));
            }

            @Nullable
            @Override public R apply() {
                return val;
            }
        };
    }

    /**
     * Creates and returns new factory closure for the given type.
     *
     * @param cls Class of factory.
     * @param <T> Type of factory.
     * @return Factory closure for the given type.
     */
    public static <T> GridOutClosure<T> factory(final Class<T> cls) {
        A.notNull(cls, "cls");

        return new CO<T>() {
            {
                peerDeployLike(U.peerDeployAware(cls));
            }

            @Override public T apply() {
                try {
                    return cls.newInstance();
                }
                catch (Exception e) {
                    throw new GridRuntimeException(e);
                }
            }
        };
    }

    /**
     * Gets identity closure, i.e. the closure that returns its variable value.
     *
     * @param <T> Type of the variable and return value for the closure.
     * @return Identity closure, i.e. the closure that returns its variable value.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> GridClosure<T, T> identity() {
        return IDENTITY;
    }

    /**
     * Converts given closure to predicate.
     *
     * @param c Closure to convert.
     * @return Closure converted to predicate.
     */
    public static GridAbsPredicate as(final Callable<Boolean> c) {
        A.notNull(c, "c");

        return U.withMeta(new GridAbsPredicate() {
            {
                peerDeployLike(U.peerDeployAware(c));
            }

            @Override public boolean apply() {
                try {
                    return c.call();
                }
                catch (Exception e) {
                    throw wrap(e);
                }
            }
        }, c);
    }

    /**
     * Converts given closure to predicate.
     *
     * @param c Closure to convert.
     * @return Closure converted to predicate.
     */
    public static <T> GridPredicate<T> as(final GridClosure<? super T, Boolean> c) {
        A.notNull(c, "c");

        return U.withMeta(new P1<T>() {
            {
                peerDeployLike(U.peerDeployAware(c));
            }

            @Override public boolean apply(T t) {
                return c.apply(t);
            }
        }, c);
    }

    /**
     * Converts given closure to predicate.
     *
     * @param c Closure to convert.
     * @return Closure converted to predicate.
     */
    public static <T1, T2> GridPredicate2<T1, T2> as(final GridClosure2<? super T1, ? super T2, Boolean> c) {
        A.notNull(c, "c");

        return U.withMeta(new P2<T1, T2>() {
            {
                peerDeployLike(U.peerDeployAware(c));
            }

            @Override public boolean apply(T1 t1, T2 t2) {
                return c.apply(t1, t2);
            }
        }, c);
    }

    /**
     * Converts given closure to predicate.
     *
     * @param c Closure to convert.
     * @return Closure converted to predicate.
     */
    public static <T1, T2, T3> GridPredicate3<T1, T2, T3> as(final GridClosure3<? super T1, ? super T2, ? super T3,
        Boolean> c) {
        A.notNull(c, "c");

        return U.withMeta(new P3<T1, T2, T3>() {
            {
                peerDeployLike(U.peerDeployAware(c));
            }

            @Override public boolean apply(T1 t1, T2 t2, T3 t3) {
                return c.apply(t1, t2, t3);
            }
        }, c);
    }

    /**
     * Converts given predicate to closure.
     *
     * @param p Predicate to convert.
     * @return Predicate converted to closure.
     */
    public static GridOutClosure<Boolean> as(final GridAbsPredicate p) {
        A.notNull(p, "p");

        return U.withMeta(new CO<Boolean>() {
            {
                peerDeployLike(U.peerDeployAware(p));
            }

            @Override public Boolean apply() {
                return p.apply();
            }
        }, p);
    }

    /**
     * Converts given predicate to closure.
     *
     * @param p Predicate to convert.
     * @param <X> Type of the free variable for the predicate.
     * @return Predicate converted to closure.
     */
    public static <X> GridClosure<X, Boolean> as(final GridPredicate<? super X> p) {
        A.notNull(p, "p");

        return U.withMeta(new C1<X, Boolean>() {
            {
                peerDeployLike(U.peerDeployAware(p));
            }

            @Override public Boolean apply(X x) {
                return p.apply(x);
            }
        }, p);
    }

    /**
     * Converts given predicate to closure.
     *
     * @param p Predicate to convert.
     * @param <X1> Type of the free variable for the predicate.
     * @param <X2> Type of the free variable for the predicate.
     * @return Predicate converted to closure.
     */
    public static <X1, X2> GridClosure2<X1, X2, Boolean> as(final GridPredicate2<? super X1, ? super X2> p) {
        A.notNull(p, "p");

        return U.withMeta(new C2<X1, X2, Boolean>() {
            {
                peerDeployLike(U.peerDeployAware(p));
            }

            @Override public Boolean apply(X1 x1, X2 x2) {
                return p.apply(x1, x2);
            }
        }, p);
    }

    /**
     * Converts given predicate to closure.
     *
     * @param p Predicate to convert.
     * @param <X1> Type of the free variable for the predicate.
     * @param <X2> Type of the free variable for the predicate.
     * @param <X3> Type of the free variable for the predicate.
     * @return Predicate converted to closure.
     */
    public static <X1, X2, X3> GridClosure3<X1, X2, X3, Boolean> as(
        final GridPredicate3<? super X1, ? super X2, ? super X3> p) {
        A.notNull(p, "p");

        return U.withMeta(new C3<X1, X2, X3, Boolean>() {
            {
                peerDeployLike(U.peerDeployAware(p));
            }

            @Override public Boolean apply(X1 x1, X2 x2, X3 x3) {
                return p.apply(x1, x2, x3);
            }
        }, p);
    }

    /**
     * Composes two closures into one. If {@code a} is a free variable the
     * result closure is {@code g.apply(f.apply(a))}. Note that both closures
     * must be loaded by the same class loader.
     *
     * @param f First closure.
     * @param g Second closure.
     * @param <D> Type of the free variable for the closure.
     * @param <B> Type of return value and of the free variable for the closure.
     * @param <C> Type of the return value for the closure.
     * @return Composition closure.
     */
    @SuppressWarnings({"JavaDoc"})
    public static <D, B, C> GridClosure<D, C> compose(final GridClosure<? super D, ? extends B> f,
        final GridClosure<? super B, C> g) {
        A.notNull(f, "f", g, "g");

        return new C1<D, C>() {
            {
                peerDeployLike(U.peerDeployAware0(f, g));
            }

            @Override public C apply(D a) {
                return g.apply(f.apply(a));
            }
        };
    }

    /**
     * Gets closure that wraps given map access, i.e. returns map's value assuming that
     * free variable is a map's key.
     *
     * @param m Source map.
     * @param <K> Type of the free variable for the closure and type of the map keys.
     * @param <V> Type of the closure's return value and type of the map values.
     * @return Closure that wraps given map access.
     */
    public static <K, V> GridClosure<K, V> forMap(final Map<? extends K, ? extends V> m) {
        A.notNull(m, "m");

        return new C1<K, V>() {
            {
                peerDeployLike(U.peerDeployAware(m));
            }

            @Override public V apply(K k) {
                return m.get(k);
            }
        };
    }

    /**
     * Gets closure that wraps given map's access, i.e. returns map's value assuming that
     * free variable is a map's key or default value produced by optional factory closure
     * if key has no mapping.
     *
     * @param m Source map.
     * @param c Optional factory closure to produce default value to return from result closure if
     *      key has no mapping in given map. If factory closure is {@code null} - the default value
     *      will be {@code null}.
     * @param <K> Type of the free variable for the closure and type of the map keys.
     * @param <V> Type of the closure's return value and type of the map values.
     * @return Closure that wraps given map's access.
     */
    public static <K, V> GridClosure<K, V> forMap(final Map<? extends K, ? extends V> m,
        @Nullable final Callable<V> c) {
        A.notNull(m, "m");

        return new C1<K, V>() {
            {
                peerDeployLike(U.peerDeployAware0(m, c));
            }

            @Nullable
            @Override public V apply(K k) {
                return returnIfAbsent(m, k, c);
            }
        };
    }

    /**
     * Gets closure that return {@code toString()} value for its free variable.
     *
     * @param <T> Type of the free variable for the closure.
     * @return Closure that return {@code toString()} value for its free variable.
     */
    public static <T> GridClosure<T, String> string() {
        return new C1<T, String>() {
            @Override public String apply(T t) {
                return t.toString();
            }
        };
    }

    /**
     * Gets predicate that returns {@code true} if its free variable is contained
     * in given collection.
     *
     * @param c Collection to check for containment.
     * @param <T> Type of the free variable for the predicate and type of the
     *      collection elements.
     * @return Predicate that returns {@code true} if its free variable is
     *      contained in given collection.
     */
    public static <T> GridPredicate<T> in(@Nullable final Collection<? extends T> c) {
        return isEmpty(c) ? GridFunc.<T>alwaysFalse() : new P1<T>() {
            {
                peerDeployLike(U.peerDeployAware0(c));
            }

            @Override public boolean apply(T t) {
                assert c != null;

                return c.contains(t);
            }
        };
    }

    /**
     * Gets predicate that returns {@code true} if its free variable is not
     * contained in given collection.
     *
     * @param c Collection to check for containment.
     * @param <T> Type of the free variable for the predicate and type of the
     *      collection elements.
     * @return Predicate that returns {@code true} if its free variable is not
     *      contained in given collection.
     */
    public static <T> GridPredicate<T> notIn(@Nullable final Collection<? extends T> c) {
        return isEmpty(c) ? GridFunc.<T>alwaysTrue() : new P1<T>() {
            {
                peerDeployLike(U.peerDeployAware0(c));
            }

            @Override public boolean apply(T t) {
                assert c != null;

                return !c.contains(t);
            }
        };
    }

    /**
     * Gets the value with given key. If that value does not exist, calls given
     * closure to get the default value, puts it into the map and returns it. If
     * closure is {@code null} return {@code null}.
     *
     * @param map Concurrent hash map.
     * @param key Key to get the value for.
     * @param c Default value producing closure.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Value for the key or the value produced by the closure if key
     *      does not exist in the map. Return {@code null} if key is not found and
     *      closure is {@code null}.
     */
    @Nullable public static <K, V>  V addIfAbsent(ConcurrentMap<K, V> map, K key, @Nullable Callable<V> c) {
        A.notNull(map, "map", key, "key");

        V v = map.get(key);

        if (v == null && c != null) {
            try {
                v = c.call();
            }
            catch (Exception e) {
                throw F.wrap(e);
            }

            V v0 = map.putIfAbsent(key, v);

            if (v0 != null)
                v = v0;
        }

        return v;
    }

    /**
     * Gets the value with given key. If that value does not exist, puts given
     * value into the map and returns it.
     *
     * @param map Map.
     * @param key Key.
     * @param val Value to put if one does not exist.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Current mapping for a given key.
     */
    public static <K, V> V addIfAbsent(ConcurrentMap<K, V> map, K key, V val) {
        A.notNull(map, "map", key, "key", val, "val");

        V v = map.putIfAbsent(key, val);

        if (v != null)
            val = v;

        return val;
    }

    /**
     * Utility map getter.
     *
     * @param map Map to get value from.
     * @param key Map key (can be {@code null}).
     * @param c Optional factory closure for the default value to be put in when {@code key}
     *      is not found. If closure is not provided - {@code null} will be put into the map.
     * @param <K> Map key type.
     * @param <V> Map value type.
     * @return Value for the {@code key} or default value produced by {@code c} if key is not
     *      found (or {@code null} if key is not found and closure is not provided). Note that
     *      in case when key is not found the default value will be put into the map.
     * @throws GridClosureException Thrown in case when callable throws exception.
     * @see #newLinkedList()
     * @see #newList()
     * @see #newSet()
     * @see #newMap()
     * @see #newAtomicLong()
     * @see #newAtomicInt()
     * @see #newAtomicRef()
     * @see #newAtomicBoolean()
     */
    @Nullable public static <K, V> V addIfAbsent(Map<K, V> map, @Nullable K key, @Nullable Callable<V> c) {
        A.notNull(map, "map");

        try {
            if (!map.containsKey(key)) {
                V v = c == null ? null : c.call();

                map.put(key, v);

                return v;
            }
            else
                return map.get(key);
        }
        catch (Exception e) {
            throw wrap(e);
        }
    }

    /**
     * Utility map getter.
     *
     * @param map Map to get value from.
     * @param key Map key (can be {@code null}).
     * @param v Optional value to be put in when {@code key} is not found.
     *      If not provided - {@code null} will be put into the map.
     * @param <K> Map key type.
     * @param <V> Map value type.
     * @return Value for the {@code key} or default value {@code c} if key is not
     *      found (or {@code null} if key is not found and value is not provided). Note that
     *      in case when key is not found the default value will be put into the map.
     */
    @Nullable public static <K, V> V addIfAbsent(Map<K, V> map, @Nullable K key, @Nullable V v) {
        A.notNull(map, "map");

        try {
            if (!map.containsKey(key)) {
                map.put(key, v);

                return v;
            }
            else
                return map.get(key);
        }
        catch (Exception e) {
            throw wrap(e);
        }
    }

    /**
     * Gets predicate that returns {@code true} if its free variable is contained
     * in given array.
     *
     * @param c Array to check for containment.
     * @param <T> Type of the free variable for the predicate and type of the
     *      array elements.
     * @return Predicate that returns {@code true} if its free variable is
     *      contained in given array.
     */
    public static <T> GridPredicate<T> in(@Nullable T[] c) {
        return isEmpty(c) ? GridFunc.<T>alwaysFalse() : in(asList(c));
    }

    /**
     * Gets predicate that returns {@code true} if its free variable is not
     * contained in given array.
     *
     * @param c Array to check for containment.
     * @param <T> Type of the free variable for the predicate and type of the
     *      array elements.
     * @return Predicate that returns {@code true} if its free variable is not
     *      contained in given array.
     */
    public static <T> GridPredicate<T> notIn(@Nullable T[] c) {
        return isEmpty(c) ? GridFunc.<T>alwaysTrue() : notIn(asList(c));
    }

    /**
     * Reduces collection into single value using given for-all closure.
     *
     * @param c Collection to reduce.
     * @param f For-all closure used for reduction.
     * @param <X> Type of the free variable for the closure and type of the
     *      collection elements.
     * @param <Y> Type of the closure's return value.
     * @return Single value as a result of collection reduction.
     */
    public static <X, Y> Y reduce(Iterable<? extends X> c, GridReducer<? super X, Y> f) {
        A.notNull(c, "c", f, "f");

        for (X x : c)
            f.collect(x);

        return f.apply();
    }

    /**
     * Reduces given map into single value using given for-all closure.
     *
     * @param m Map to reduce.
     * @param f For-all closure used for reduction.
     * @param <X> Type of the free variable for the closure and type of the map keys.
     * @param <Y> Type of the closure's return value and type of the map values.
     * @param <R> Type of the return value for the closure.
     * @return Single value as a result of map reduction.
     */
    @Nullable public static <X, Y, R> R reduce(Map<? extends X, ? extends Y> m,
        GridReducer2<? super X, ? super Y, R> f) {
        A.notNull(m, "m", f, "f");

        for (Map.Entry<? extends X, ? extends Y> e : m.entrySet())
            f.collect(e.getKey(), e.getValue());

        return f.apply();
    }

    /**
     * Calls given {@code one-way} closure over the each element of the provided
     * collection.
     *
     * @param c Collection to call closure over.
     * @param f One-way closure to call over the collection.
     * @param p Optional set of predicates. Only if collection element evaluates
     *      to {@code true} for given predicates the closure will be applied to it.
     *      If no predicates provided - closure will be applied to all collection
     *      elements.
     * @param <X> Type of the free variable for the closure and type of the
     *      collection elements.
     */
    public static <X> void forEach(Iterable<? extends X> c, GridInClosure<? super X> f,
        @Nullable GridPredicate<? super X>... p) {
        A.notNull(c, "c", f, "f");

        for (X x : c)
            if (isAll(x, p))
                f.apply(x);
    }

    /**
     * Calls given {@code one-way} closure over the each element of the provided array.
     *
     * @param c Array to call closure over.
     * @param f One-way closure to call over the array.
     * @param p Optional set of predicates. Only if collection element evaluates
     *      to {@code true} for given predicates the closure will be applied to it.
     *      If no predicates provided - closure will be applied to all collection
     *      elements.
     * @param <X> Type of the free variable for the closure and type of the array
     *      elements.
     */
    @SuppressWarnings("RedundantTypeArguments")
    public static <X> void forEach(X[] c, GridInClosure<? super X> f, @Nullable GridPredicate<? super X>... p) {
        A.notNull(c, "c", f, "f");

        F.<X>forEach(asList(c), f, p);
    }

    /**
     * Creates cloud resource predicate that evaluates to {@code true} if given resource's
     * type is in passed in set of types.
     *
     * @param types Set of types to compare with.
     * @return Predicate for cloud resources that evaluates to {@code true} if given
     *      resource's type one of the passed in.
     * @see #resource(String)
     * @see #resources(boolean, GridCloudResource...)
     * @see #resources(boolean, Collection)
     */
    public static GridPredicate<GridCloudResource> resources(@Nullable final int... types) {
        return isEmpty(types) ? GridFunc.<GridCloudResource>alwaysFalse() : new PCR() {
            @Override public boolean apply(GridCloudResource r) {
                assert types != null;

                int type = r.type();

                for (int t : types) {
                    if (type == t) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    /**
     * Creates cloud resource predicate that evaluates given resource based on matching its
     * ID with provided ID.
     *
     * @param id ID to match in the predicate.
     * @return Cloud resource predicate that evaluates given resource based on matching its ID.
     * @see #resources(int...)
     * @see #resources(boolean, GridCloudResource...)
     * @see #resources(boolean, Collection)
     */
    public static GridPredicate<GridCloudResource> resource(@Nullable final String id) {
        return id == null ? GridFunc.<GridCloudResource>alwaysFalse() : new PCR() {
            @Override public boolean apply(GridCloudResource r) {
                return r.id().equals(id);
            }
        };
    }

    /**
     * Creates cloud resource predicate that evaluates based on whether it has all or any links
     * from the provided collection of links.
     *
     * @param all {@code True} to evaluate based on whether all provided links are the links in
     *      evaluating cloud resource. {@code False} to evaluate based on any instead of all.
     * @param links Collection of cloud resources to match by.
     * @return Cloud resource predicate that evaluates based on whether it has all or any links
     *      from the provided collection of links.
     * @see #resources(int...)
     * @see #resource(String)
     * @see #resources(boolean, GridCloudResource...)
     */
    public static GridPredicate<GridCloudResource> resources(final boolean all,
        @Nullable final Collection<? extends GridCloudResource> links) {
        return new PCR() {
            @Override public boolean apply(GridCloudResource e) {
                Collection<GridCloudResource> el = e.links();

                if (el == null) {
                    return false;
                }

                assert el != null;

                if (all) {
                    return isEmpty(links) ? isEmpty(el) : el.containsAll(links);
                }
                else { // Any.
                    if (!isEmpty(links)) {
                        assert links != null;

                        for (GridCloudResource r2 : el) {
                            if (links.contains(r2)) {
                                return true;
                            }
                        }
                    }

                    return false;
                }
            }
        };
    }

    /**
     * Creates cloud resource predicate that evaluates based on whether it has all or
     * any links from the provided collection of links.
     *
     * @param all {@code True} to evaluate based on whether all provided links are the
     *      links in evaluating cloud resource. {@code False} to evaluate based on any
     *      instead of all.
     * @param links Collection of cloud resources to match by.
     * @return Cloud resource predicate that evaluates based on whether it has all or
     *      any links from the provided collection of links.
     * @see #resources(int...)
     * @see #resource(String)
     * @see #resources(boolean, Collection)
     */
    public static GridPredicate<GridCloudResource> resources(boolean all, @Nullable GridCloudResource... links) {
        return resources(all, isEmpty(links) ? Collections.<GridCloudResource>emptyList() : asList(links));
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware}
     * interface and evaluates to {@code true} if it contains all provided metadata.
     *
     * @param meta Collection of metadata.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware} interface and
     *      evaluates to {@code true} if it contains all provided metadata.
     * @see #meta(String...)
     * @see #meta(Iterable)
     * @see #meta(String, Object)
     */
    public static <T extends GridMetadataAware> GridPredicate<T> metaEntry(@Nullable Map.Entry<String, ?>... meta) {
        return metaEntry(isEmpty(meta) ? Collections.<Map.Entry<String, ?>>emptyList() : asList(meta));
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware} interface
     * and evaluates to {@code true} if it contains all provided metadata.
     *
     * @param meta Collection of metadata.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware} interface and
     *      evaluates to {@code true} if it contains all provided metadata.
     * @see #meta(String...)
     * @see #meta(Iterable)
     * @see #meta(String, Object)
     */
    public static <T extends GridMetadataAware> GridPredicate<T> metaEntry(
        @Nullable final Collection<? extends Map.Entry<String, ?>> meta) {
        return isEmpty(meta) ? GridFunc.<T>alwaysFalse() : new P1<T>() {
            {
                peerDeployLike(U.peerDeployAware0(transform(meta, new C1<Map.Entry<String, ?>, Object>() {
                    @Nullable
                    @Override public Object apply(Map.Entry<String, ?> e) {
                        return e == null ? null : e.getValue();
                    }
                })));
            }

            @Override public boolean apply(T e) {
                assert meta != null;

                for (Map.Entry<String, ?> t : meta) {
                    if (!F.eq(e.meta(t.getKey()), t.getValue())) {
                        return false;
                    }
                }

                return true;
            }
        };
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware} interface
     * and evaluates to {@code true} if it contains all provided metadata.
     *
     * @param meta Collection of metadata as a map.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware}
     *      interface and evaluates to {@code true} if it contains all provided metadata.
     * @see #meta(String...)
     * @see #meta(Iterable)
     * @see #meta(String, Object)
     * @see #metaEntry(java.util.Map.Entry[])
     * @see #metaEntry(Collection)
     */
    public static <T extends GridMetadataAware> GridPredicate<T> meta(@Nullable Map<String, ?> meta) {
        if (isEmpty(meta)) {
            return metaEntry(Collections.<Map.Entry<String, ?>>emptySet());
        }
        else {
            assert meta != null;

            return metaEntry(meta.entrySet());
        }
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware} interface
     * and evaluates to {@code true} if it contains given metadata.
     *
     * @param name Metadata name.
     * @param val Metadata value.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware} interface
     *      and evaluates to {@code true} if it contains given metadata.
     * @see #metaEntry(java.util.Map.Entry[])
     * @see #meta(String...)
     * @see #meta(Iterable)
     */
    public static <T extends GridMetadataAware> GridPredicate<T> meta(String name, Object val) {
        A.notNull(name, "name", val, "val");

        return metaEntry(F.t(name, val));
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware} interface
     * and evaluates to {@code true} if it contains given metadata names (values are ignored).
     *
     * @param names Metadata names to evaluate by.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware} interface and
     *      evaluates to {@code true} if it contains given metadata names (values are ignored).
     */
    public static <T extends GridMetadataAware> GridPredicate<T> meta(@Nullable String... names) {
        return meta(isEmpty(names) ? Collections.<String>emptyList() : asList(names));
    }

    /**
     * Creates predicate that accepts subclass of {@link GridMetadataAware} interface and
     * evaluates to {@code true} if it contains given metadata names (values are ignored).
     *
     * @param names Metadata names to evaluate by.
     * @param <T> Type of returned predicate.
     * @return Predicate that accepts subclass of {@link GridMetadataAware} interface and
     *      evaluates to {@code true} if it contains given metadata names (values are ignored).
     */
    public static <T extends GridMetadataAware> GridPredicate<T> meta(@Nullable final Iterable<String> names) {
        return isEmpty(names) ? GridFunc.<T>alwaysFalse() : new P1<T>() {
            @Override public boolean apply(T e) {
                assert names != null;

                for (String name : names) {
                    if (!e.hasMeta(name)) {
                        return false;
                    }
                }

                return true;
            }
        };
    }

    /**
     * Adds (copies) to given collection all elements in <tt>'from'</tt> array.
     *
     * @param to Collection to copy to.
     * @param from Array to copy from.
     * @param <T> Type of the free variable for the predicate and type of the collection elements.
     * @return Filtered collection. Note that no new collection will be created.
     */
    public static <T> Collection<T> copy(Collection<T> to, T... from) {
        A.notNull(to, "to", from, "from");

        copy(to, asList(from));

        return to;
    }

    /**
     * Adds (copies) to given collection using provided predicates. Element is copied if all
     * predicates evaluate to {@code true}.
     *
     * @param to Collection to copy to.
     * @param from Collection to copy from.
     * @param p Optional set of predicates to use for filtration.
     * @param <T> Type of the free variable for the predicate and type of the collection elements.
     * @return Filtered collection. Note that no new collection will be created.
     */
    public static <T> Collection<T> copy(Collection<T> to, Iterable<? extends T> from,
        @Nullable GridPredicate<? super T>... p) {
        A.notNull(to, "to", from, "from");

        if (!isAlwaysFalse(p)) {
            for (T t : from) {
                if (isAll(t, p)) {
                    to.add(t);
                }
            }
        }

        return to;
    }

    /**
     * Transforms one collection to another using provided closure and predicate. Value from
     * the initial collection will be transformed only if all predicates evaluate to {@code true}
     * for this value. Note that no new collection will be created.
     *
     * @param to Destination collection to transform to.
     * @param from Initial collection to transform.
     * @param f Closure to use for transformation.
     * @param p Optional predicates to use for transformation.
     * @param <X> Type of the free variable for the closure and type of the collection elements.
     * @param <Y> Type of the closure's return value.
     * @return Transformed newly created collection.
     */
    public static <X, Y> Collection<Y> transform(Collection<Y> to, Iterable<? extends X> from,
        GridClosure<? super X, Y> f, @Nullable GridPredicate<? super X>... p) {
        A.notNull(to, "to", from, "from", f, "f");

        if (!isAlwaysFalse(p)) {
            for (X x : from) {
                if (isAll(x, p)) {
                    to.add(f.apply(x));
                }
            }
        }

        return to;
    }

    /**
     * Calls given {@code one-way} closure (if provided) over the each element of the
     * provided map. If provided closure is {@code null} this method is no-op.
     *
     * @param m Map to call closure over.
     * @param f Optional closure to call over the map.
     * @param p Optional set of predicates. Only if map entry evaluates to {@code true} for given
     *      predicates the closure will be applied to it. If no predicates provided - closure will
     *      be applied to all map entries.
     * @param <K> Type of the free variable for the closure and type of the map keys.
     * @param <V> Type of the closure's return value and type of the map values.
     */
    public static <K, V> void forEach(Map<? extends K, ? extends V> m, GridInClosure<? super GridTuple2<K, V>> f,
        @Nullable GridPredicate<? super GridTuple2<K, V>>... p) {
        A.notNull(m, "m");

        if (!isAlwaysFalse(p)) {
            if (f != null) {
                for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                    GridTuple2<K, V> t = F.t(e.getKey(), e.getValue());

                    if (isAll(t, p)) {
                        f.apply(t);
                    }
                }
            }
        }
    }

    /**
     * Transforms one collection to another using provided closure. New collection will be created.
     *
     * @param c Initial collection to transform.
     * @param f Closure to use for transformation.
     * @param <X> Type of the free variable for the closure and type of the collection elements.
     * @param <Y> Type of the closure's return value.
     * @return Transformed newly created collection.
     */
    public static <X, Y> Collection<Y> transform(Collection<? extends X> c, GridClosure<? super X, Y> f) {
        A.notNull(c, "c", f, "f");

        Collection<Y> d = new ArrayList<Y>(c.size());

        for (X x : c) {
            d.add(f.apply(x));
        }

        return d;
    }

    /**
     * Special version of {@code transform} method that works on two types that are in child-parent
     * relationship. It allows for optimization when the input collection is implementation of {@link List}
     * interfaces. In such case no new collection will be created and transformation will happen in place.
     *
     * @param c Initial collection to transform.
     * @param f Closure to use for transformation.
     * @param <X> Type of the free variable for the closure and type of the collection elements.
     * @param <Y> Type of the closure's return value.
     * @return Transformed collection. In case of input collection implementing {@link List} interface
     *      transformation done in place and original collection is returned.
     */
    @SuppressWarnings({"unchecked"})
    public static <X, Y extends X> Collection<Y> upcast(Collection<? extends X> c, GridClosure<? super X, Y> f) {
        A.notNull(c, "c", f, "f");

        if (c instanceof List) {
            for (ListIterator<X> iter = ((List<X>)c).listIterator(); iter.hasNext();) {
                iter.set(f.apply(iter.next()));
            }

            return (Collection<Y>)c;
        }
        else {
            return viewReadOnly(c, f);
        }
    }

    /**
     * Transforms an array to read only collection using provided closure.
     *
     * @param c Initial array to transform.
     * @param f Closure to use for transformation.
     * @param <X> Type of the free variable for the closure and type of the array elements.
     * @param <Y> Type of the closure's return value.
     * @return Transformed read only collection.
     */
    public static <X, Y> Collection<Y> transform(X[] c, GridClosure<? super X, Y> f) {
        A.notNull(c, "c", f, "f");

        return viewReadOnly(asList(c), f);
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given value. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param t Value to test.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <T> Type of the value and free variable of the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      value, {@code false} otherwise.
     */
    public static <T> boolean isAll(@Nullable T t, @Nullable GridPredicate<? super T>... p) {
        return isEmpty(p) || isAll(t, asList(p));
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given value. Note that evaluation will be
     * short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param t Value to test.
     * @param p Optional set of predicates to use for evaluation.
     * @param <T> Type of the value and free variable of the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given value, {@code false}
     *         otherwise.
     */
    public static <T> boolean isAll(@Nullable T t, @Nullable Iterable<? extends GridPredicate<? super T>> p) {
        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate<? super T> r : p) {
                if (r != null && !r.apply(t)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B> boolean isAll2(@Nullable A a, @Nullable B b,
        @Nullable GridPredicate2<? super A, ? super B> p) {
        return p == null || isAll2(a, b, asList(p));
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B> boolean isAll2(@Nullable A a, @Nullable B b,
        @Nullable GridPredicate2<? super A, ? super B>[] p) {
        return isEmpty(p) || isAll2(a, b, asList(p));
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B> boolean isAll2(@Nullable A a, @Nullable B b,
        @Nullable Iterable<? extends GridPredicate2<? super A, ? super B>> p) {
        if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate2<? super A, ? super B> r : p) {
                if (r != null && !r.apply(a, b)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param c 3d value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @param <C> Type of the 3d value for the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B, C> boolean isAll3(@Nullable A a, @Nullable B b, @Nullable C c,
        @Nullable GridPredicate3<? super A, ? super B, ? super C>... p) {
        return isEmpty(p) || isAll3(a, b, c, asList(p));
    }

    /**
     * Tests if all provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code false} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param c 3d value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code true}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @param <C> Type of the 3d value for the predicates.
     * @return Returns {@code true} if all predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B, C> boolean isAll3(@Nullable A a, @Nullable B b, @Nullable C c,
        @Nullable Iterable<? extends GridPredicate3<? super A, ? super B, ? super C>> p) {
        if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate3<? super A, ? super B, ? super C> r : p) {
                if (r != null && !r.apply(a, b, c)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given value. Note
     * that evaluation will be short-circuit when first predicate evaluated to {@code true}
     * is found.
     *
     * @param t Value to test.
     * @param p Optional set of predicates to use for evaluation.
     * @param <T> Type of the value and free variable of the predicates.
     * @return Returns {@code true} if any of predicates evaluates to {@code true} for given
     *      value, {@code false} otherwise.
     */
    public static <T> boolean isAny(@Nullable T t, @Nullable GridPredicate<? super T>... p) {
        return isAny(t, asList(p));
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given value. Note
     * that evaluation will be short-circuit when first predicate evaluated to {@code true}
     * is found.
     *
     * @param t Value to test.
     * @param p Optional set of predicates to use for evaluation.
     * @param <T> Type of the value and free variable of the predicates.
     * @return Returns {@code true} if any of predicates evaluates to {@code true} for given
     *      value, {@code false} otherwise.
     */
    public static <T> boolean isAny(@Nullable T t, @Nullable Iterable<? extends GridPredicate<? super T>> p) {
        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate<? super T> r : p) {
                if (r != null && r.apply(t)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code true} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code false}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @return Returns {@code true} if any of predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B> boolean isAny2(@Nullable A a, @Nullable B b,
        @Nullable GridPredicate2<? super A, ? super B>... p) {
        return !isEmpty(p) && isAny2(a, b, asList(p));
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code true} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code false}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @return Returns {@code true} if any of predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B> boolean isAny2(@Nullable A a, @Nullable B b,
        @Nullable Iterable<? extends GridPredicate2<? super A, ? super B>> p) {
        if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate2<? super A, ? super B> r : p) {
                if (r != null && r.apply(a, b)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code true} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param c 3d value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code false}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @param <C> Type of the 3d value for the predicates.
     * @return Returns {@code true} if any of predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B, C> boolean isAny3(@Nullable A a, @Nullable B b, @Nullable C c,
        @Nullable GridPredicate3<? super A, ? super B, ? super C>... p) {
        return !isEmpty(p) && isAny3(a, b, c, asList(p));
    }

    /**
     * Tests if any of provided predicates evaluate to {@code true} for given values. Note that
     * evaluation will be short-circuit when first predicate evaluated to {@code true} is found.
     *
     * @param a 1st value for predicate.
     * @param b 2nd value for predicate.
     * @param c 3d value for predicate.
     * @param p Optional set of predicates to use for evaluation. If no predicates provides
     *      this method will always return {@code false}.
     * @param <A> Type of the 1st value for the predicates.
     * @param <B> Type of the 2nd value for the predicates.
     * @param <C> Type of the 3d value for the predicates.
     * @return Returns {@code true} if any of predicates evaluate to {@code true} for given
     *      values, {@code false} otherwise.
     */
    public static <A, B, C> boolean isAny3(@Nullable A a, @Nullable B b, @Nullable C c,
        @Nullable Iterable<? extends GridPredicate3<? super A, ? super B, ? super C>> p) {
        if (!isEmpty(p)) {
            assert p != null;

            for (GridPredicate3<? super A, ? super B, ? super C> r : p) {
                if (r != null && r.apply(a, b, c)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Transforms one collection to another using provided closure and optional set of
     * predicates. Value from the initial collection will be transformed only if all
     * predicates evaluate to {@code true} for this value. Note that new collection will
     * be created.
     *
     * @param c Initial collection to transform.
     * @param f Closure to use for transformation.
     * @param p Optional set of predicate to use for transformation.
     * @param <X> Type of the free variable for the closure and type of the collection elements.
     * @param <Y> Type of the closure's return value.
     * @return Transformed newly created collection.
     */
    public static <X, Y> Collection<Y> transform(Collection<? extends X> c, GridClosure<? super X, Y> f,
        GridPredicate<? super X>... p) {
        A.notNull(c, "c", f, "f");

        Collection<Y> d = new ArrayList<Y>(c.size());

        if (!isAlwaysFalse(p)) {
            for (X x : c) {
                if (isAll(x, p)) {
                    d.add(f.apply(x));
                }
            }
        }

        return d;
    }

    /**
     * Gets absolute (no-arg) predicate that returns {@code true} first {@code n}-times
     * it's called - and {@code false} thereafter.
     *
     * @param n Number of calls this predicate will return {@code true}.
     * @return Predicate that returns {@code true} first {@code n}-times it's
     *      called - and {@code false} thereafter.
     */
    public static GridAbsPredicate limit(final int n) {
        return new GridAbsPredicate() {
            private AtomicInteger cnt = new AtomicInteger(0);

            @Override public boolean apply() {
                return cnt.incrementAndGet() < n;
            }
        };
    }

    /**
     * Creates an absolute (no-arg) closure that does nothing.
     *
     * @return Absolute (no-arg) closure that does nothing.
     */
    public static GridAbsClosure noop() {
        return NOOP;
    }

    /**
     * Gets OR-composition predicate out of two provided predicates. Result predicate
     * evaluates to {@code true} if either one of the provided predicates evaluates to {@code true}.
     * Note that evaluation will be "short-circuit" if first predicate evaluate to {@code true}.
     * <p>
     * Note tha both predicates must be loaded by the same class loader.
     *
     * @param pa First predicate to compose of.
     * @param pb Second predicate to compose of.
     * @param <D> Type of the free variable for the first predicate.
     * @param <B> Type of the free variable for the second predicate
     * @return OR-composition predicate.
     */
    public static <D, B> GridPredicate2<D, B> or(final GridPredicate<? super D> pa,
        final GridPredicate<? super B> pb) {
        A.notNull(pa, "pa", pb, "pb");

        return new P2<D, B>() {
            {
                peerDeployLike(U.peerDeployAware0(pa, pb));
            }

            @Override public boolean apply(D d, B b) {
                return pa.apply(d) || pb.apply(b);
            }
        };
    }

    /**
     * Finds and returns first element in given collection for which any of the
     * provided predicates evaluates to {@code true}.
     *
     * @param c Input collection.
     * @param dfltVal Default value to return when no element is found.
     * @param p Optional set of finder predicates.
     * @param <V> Type of the collection elements.
     * @return First element in given collection for which predicate evaluates to
     *      {@code true} - or {@code null} if such element cannot be found.
     */
    @Nullable public static <V> V find(Iterable<? extends V> c, @Nullable V dfltVal,
        @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        if (!isEmpty(p) && !isAlwaysFalse(p)) {
            for (V v : c) {
                if (isAny(v, p)) {
                    return v;
                }
            }
        }

        return dfltVal;
    }

    /**
     * Finds and returns first element in given array for which any of the provided
     * predicates evaluates to {@code true}.
     *
     * @param c Input array.
     * @param dfltVal Default value to return when no element is found.
     * @param p Optional set of finder predicates.
     * @param <V> Type of the array elements.
     * @return First element in given array for which predicate evaluates to
     *      {@code true} - or {@code null} if such element cannot be found.
     */
    @Nullable public static <V> V find(V[] c, V dfltVal, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        return find(asList(c), dfltVal, p);
    }

    /**
     * Finds, transforms and returns first element in given collection for which any of
     * the provided predicates evaluates to {@code true}.
     *
     * @param c Input collection.
     * @param dfltVal Default value to return when no element is found.
     * @param f Transforming closure.
     * @param p Optional set of finder predicates.
     * @param <V> Type of the collection elements.
     * @return First element in given collection for which predicate evaluates to
     *      {@code true} - or {@code null} if such element cannot be found.
     */
    public static <V, Y> Y find(Iterable<? extends V> c, Y dfltVal, GridClosure<? super V, Y> f,
        @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c", f, "f");

        if (isAlwaysTrue(p) && c.iterator().hasNext()) {
            return f.apply(c.iterator().next());
        }

        if (!isEmpty(p) && !isAlwaysFalse(p)) {
            for (V v : c) {
                if (isAny(v, p)) {
                    return f.apply(v);
                }
            }
        }

        return dfltVal;
    }

    /**
     * Finds, transforms and returns first element in given array for which any of the
     * provided predicates evaluates to {@code true}.
     *
     * @param c Input array.
     * @param dfltVal Default value to return when no element is found.
     * @param f Transforming closure.
     * @param p Optional set of finder predicates.
     * @param <V> Type of the array elements.
     * @return First element in given collection for which predicate evaluates to
     *      {@code true} - or {@code null} if such element cannot be found.
     */
    @SuppressWarnings("RedundantTypeArguments")
    public static <V, Y> Y find(V[] c, Y dfltVal, GridClosure<? super V, Y> f,
        @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c", f, "f");

        return F.<V, Y>find(asList(c), dfltVal, f, p);
    }

    /**
     * Checks if collection {@code c1} contains any elements from collection {@code c2}.
     *
     * @param c1 Collection to check for containment. If {@code null} - this method returns {@code false}.
     * @param c2 Collection of elements to check. If {@code null} - this method returns {@code false}.
     * @param <T> Type of the elements.
     * @return {@code true} if collection {@code c1} contains at least one element from collection
     *      {@code c2}.
     */
    public static <T> boolean containsAny(@Nullable Collection<? extends T> c1, @Nullable Iterable<? extends T> c2) {
        if (c1 != null && c2 != null)
            for (T t : c2)
                if (c1.contains(t))
                    return true;

        return false;
    }

    /**
     * Checks if collection {@code c1} contains all elements from collection {@code c2}.
     *
     * @param c1 Collection to check for containment. If {@code null} - this method returns {@code false}.
     * @param c2 Collection of elements to check. If {@code null} - this method returns {@code true}
     *      meaning that {@code null}-collection is treated as empty collection.
     * @param <T> Type of the elements.
     * @return {@code true} if collection {@code c1} contains all elements from collection
     *      {@code c2}.
     */
    public static <T> boolean containsAll(@Nullable Collection<? extends T> c1, @Nullable Iterable<? extends T> c2) {
        if (c1 == null)
            return false;

        if (c2 != null)
            for (T t : c2)
                if (!c1.contains(t))
                    return false;

        return true;
    }

    /**
     * Splits given collection in two using provided set of predicates.
     *
     * @param c Collection to split. If {@code null} - pair of {@code null}s will be returned.
     *      If it's empty - pair of empty collections will be returned.
     * @param p Optional filter. All elements that satisfy all provided predicates
     *      will go into 1st collection in pair. The rest will be in 2nd collection.
     *      If not provided, pair of original and empty collection will be returned.
     * @param <T> Type of the collection.
     * @return Pair of two collections.
     */
    public static <T0, T extends T0> GridPair<Collection<T>> split(@Nullable Collection<T> c,
        @Nullable GridPredicate<? super T>... p) {
        if (c == null) {
            return pair(null, null);
        }

        if (c.isEmpty()) {
            return F.<Collection<T>>pair(Collections.<T>emptyList(), Collections.<T>emptyList());
        }

        if (isEmpty(p)) {
            return pair(c, Collections.<T>emptyList());
        }

        if (isAlwaysTrue(p)) {
            return pair(c, Collections.<T>emptyList());
        }

        if (isAlwaysFalse(p)) {
            return pair(Collections.<T>emptyList(), c);
        }

        Collection<T> c1 = new LinkedList<T>();
        Collection<T> c2 = new LinkedList<T>();

        for (T t : c) {
            if (isAll(t, p)) {
                c1.add(t);
            }
            else {
                c2.add(t);
            }
        }

        return pair(c1, c2);
    }

    /**
     * Creates pair out of given two objects.
     *
     * @param t1 First object in pair.
     * @param t2 Second object in pair.
     * @param <T> Type of objects in pair.
     * @return Pair of objects.
     */
    public static <T> GridPair<T> pair(@Nullable T t1, @Nullable T t2) {
        return new GridPair<T>(t1, t2);
    }

    /**
     * Creates triple out of given three objects.
     *
     * @param t1 First object in triple.
     * @param t2 Second object in triple.
     * @param t3 Third object in triple.
     * @param <T> Type of objects in triple.
     * @return Triple of objects.
     */
    public static <T> GridTriple<T> triple(@Nullable T t1, @Nullable T t2, @Nullable T t3) {
        return new GridTriple<T>(t1, t2, t3);
    }

    /**
     * Partitions input collection in two: first containing elements for which given
     * predicate evaluates to {@code true} - and second containing the elements for which
     * predicate evaluates to {@code false}.
     *
     * @param c Input collection.
     * @param p Partitioning predicate.
     * @param <V> Type of the collection elements.
     * @return Tuple of two collections: first containing elements for which given predicate
     *      evaluates to {@code true} - and second containing the elements for which predicate
     *      evaluates to {@code false}.
     */
    public static <V> GridTuple2<Collection<V>, Collection<V>> partition(Iterable<? extends V> c,
        GridPredicate<? super V> p) {
        A.notNull(c, "c", p, "p");

        Collection<V> c1 = new LinkedList<V>();
        Collection<V> c2 = new LinkedList<V>();

        for (V v : c) {
            if (p.apply(v)) {
                c1.add(v);
            }
            else {
                c2.add(v);
            }
        }

        return t(c1, c2);
    }

    /**
     * Partitions input array in two: first containing elements for which given predicate
     * evaluates to {@code true} - and second containing the elements for which predicate
     * evaluates to {@code false}.
     *
     * @param c Input array.
     * @param p Partitioning predicate.
     * @param <V> Type of the array elements.
     * @return Tuple of two collections: first containing elements for which given predicate
     *      evaluates to {@code true} - and second containing the elements for which predicate
     *      evaluates to {@code false}.
     */
    public static <V> GridTuple2<Collection<V>, Collection<V>> partition(V[] c, GridPredicate<? super V> p) {
        A.notNull(c, "c", p, "p");

        return partition(asList(c), p);
    }

    /**
     * Partitions input map in two: first containing entries for which given predicate evaluates
     * to {@code true} - and second containing the entries for which predicate evaluates to {@code false}.
     *
     * @param m Input map.
     * @param p Partitioning predicate.
     * @param <K> Type of the map keys.
     * @param <V> Type of the map values.
     * @return Tuple of two maps: first containing entries for which given predicate evaluates to
     *      {@code true} - and second containing the entries for which predicate evaluates to {@code false}.
     */
    public static <K, V> GridTuple2<Map<K, V>, Map<K, V>> partition(Map<? extends K, ? extends V> m,
        GridPredicate2<? super K, ? super V> p) {
        A.notNull(m, "m", p, "p");

        Map<K, V> m1 = new HashMap<K, V>();
        Map<K, V> m2 = new HashMap<K, V>();

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            if (p.apply(e.getKey(), e.getValue())) {
                m1.put(e.getKey(), e.getValue());
            }
            else {
                m2.put(e.getKey(), e.getValue());
            }
        }

        return t(m1, m2);
    }

    /**
     * Checks for existence of the element in input collection for which all provided predicates
     * evaluate to {@code true}.
     *
     * @param c Input collection.
     * @param p Optional set of checking predicates.
     * @param <V> Type of the collection elements.
     * @return {@code true} if input collection contains element for which all the provided
     *      predicates evaluates to {@code true} - otherwise returns {@code false}.
     */
    public static <V> boolean exist(Iterable<? extends V> c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            for (V v : c) {
                if (isAll(v, p)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks for existence of the element in input array for which all provided predicates
     * evaluate to {@code true}.
     *
     * @param c Input array.
     * @param p Optional set of checking predicates.
     * @param <V> Type of the array elements.
     * @return {@code true} if input array contains element for which all the provided predicates
     *      valuates to {@code true} - otherwise returns {@code false}.
     */
    public static <V> boolean exist(V[] c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        return exist(asList(c), p);
    }

    /**
     * Applies all given predicates to all elements in given input collection and returns
     * {@code true} if all of them evaluate to {@code true} for all elements. Returns
     * {@code false} otherwise.
     *
     * @param c Input collection.
     * @param p Optional set of checking predicates. If none provided - {@code true} is returned.
     * @param <V> Type of the collection element.
     * @return Returns {@code true} if all given predicates evaluate to {@code true} for
     *      all elements. Returns {@code false} otherwise.
     */
    public static <V> boolean forAll(Iterable<? extends V> c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            for (V v : c) {
                if (!isAll(v, p)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Applies all given predicates to all elements in given input array and returns {@code true}
     * if all of them evaluate to {@code true} for all elements. Returns {@code false} otherwise.
     *
     * @param c Input array.
     * @param p Optional set of checking predicates.
     * @param <V> Type of the array element.
     * @return Returns {@code true} if all given predicates evaluate to {@code true} for all elements.
     *      Returns {@code false} otherwise.
     */
    public static <V> boolean forAll(V[] c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        return forAll(asList(c), p);
    }

    /**
     * Checks for existence of the entry in input map for which all provided predicates
     * evaluate to {@code true}.
     *
     * @param m Input map.
     * @param p Optional set of checking predicate.
     * @param <K> Type of the map keys.
     * @param <V> Type of the map values.
     * @return {@code true} if input map contains entry for which all provided predicates
     *      evaluate to {@code true} - otherwise returns {@code false}.
     */
    public static <K1, K extends K1, V1, V extends V1> boolean exist(Map<K, V> m,
        @Nullable GridPredicate<? super Map.Entry<K, V>>... p) {
        A.notNull(m, "m");

        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            for (Map.Entry<K, V> e : m.entrySet()) {
                if (isAll(e, p)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Applies given predicates to all entries in given input map and returns {@code true}
     * if all of them evaluates to {@code true} for all entries. Returns {@code false} otherwise.
     *
     * @param m Input map.
     * @param p Optional set of checking predicate.
     * @param <K> Type of the map keys.
     * @param <V> Type of the map values.
     * @return Returns {@code true} if all given predicate evaluates to {@code true} for all
     *      entries. Returns {@code false} otherwise.
     */
    public static <K1, K extends K1, V1, V extends V1> boolean forAll(Map<K, V> m,
        @Nullable GridPredicate<? super Map.Entry<K, V>>... p) {
        A.notNull(m, "m");

        if (isAlwaysFalse(p))
            return false;
        else if (isAlwaysTrue(p))
            return true;
        else if (!isEmpty(p))
            for (Map.Entry<K, V> e : m.entrySet())
                if (!isAll(e, p))
                    return false;

        return true;
    }

    /**
     * Applies all given predicates to all elements in given input collection and returns
     * {@code true} if all predicates evaluate to {@code true} for at least one element. Returns
     * {@code false} otherwise. Processing will short-circuit after first element evaluates to
     * {@code true} for all predicates.
     *
     * @param c Input collection.
     * @param p Optional set of checking predicates. If none provided - {@code true} is returned.
     * @param <V> Type of the collection element.
     * @return Returns {@code true} if all given predicates evaluate to {@code true} for
     *      at least one element. Returns {@code false} otherwise.
     */
    public static <V> boolean forAny(Iterable<? extends V> c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            for (V v : c) {
                if (isAll(v, p)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    /**
     * Applies all given predicates to all elements in given input array and returns {@code true}
     * if all of them evaluate to {@code true} for at least one element. Returns {@code false}
     * otherwise. Processing will short-circuit after first element evaluates to
     * {@code true} for all predicates.
     *
     * @param c Input array.
     * @param p Optional set of checking predicates.
     * @param <V> Type of the array element.
     * @return Returns {@code true} if all given predicates evaluate to {@code true} for at
     *      least one element. Returns {@code false} otherwise.
     */
    public static <V> boolean forAny(V[] c, @Nullable GridPredicate<? super V>... p) {
        A.notNull(c, "c");

        return forAny(asList(c), p);
    }

    /**
     * Applies given predicates to all entries in given input map and returns {@code true}
     * if all of them evaluates to {@code true} for at least one entry. Returns {@code false}
     * otherwise. Processing will short-circuit after first entry evaluates to
     * {@code true} for all predicates.
     *
     * @param m Input map.
     * @param p Optional set of checking predicate.
     * @param <K> Type of the map keys.
     * @param <V> Type of the map values.
     * @return Returns {@code true} if all given predicate evaluates to {@code true} for at
     *      least one entry. Returns {@code false} otherwise.
     */
    public static <K1, K extends K1, V1, V extends V1> boolean forAny(Map<K, V> m,
        @Nullable GridPredicate<? super Map.Entry<K, V>>... p) {
        A.notNull(m, "m");

        if (isAlwaysFalse(p)) {
            return false;
        }
        else if (isAlwaysTrue(p)) {
            return true;
        }
        else if (!isEmpty(p)) {
            for (Map.Entry<K, V> e : m.entrySet()) {
                if (isAll(e, p)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    /**
     * Folds-right given collection using provided closure. If input collection contains <tt>a<sub>1</sub>,
     * a<sub>2</sub>, ..., a<sub>n</sub></tt> result value will be
     * <tt>...f(f(f(b,a<sub>1</sub>),a<sub>2</sub>),a<sub>3</sub>)...</tt>
     * where {@code f(x)} is the result of applying each closure from {@code fs} set to the
     * element {@code x} of the input collection {@code c}.
     * <p>
     * For example:
     * <pre name="code" class="java">
     * ...
     *
     * Collection&lt;Integer&gt; nums = new ArrayList&lt;Integer&gt;(size);
     *
     * // Search max value.
     * Integer max = F.fold(nums, F.first(nums), new C2&lt;Integer, Integer, Integer&gt;() {
     *     public Integer apply(Integer n, Integer max) { return Math.max(n, max); }
     * });
     *
     * ...
     * </pre>
     *
     * @param c Input collection.
     * @param b Optional first folding pair element.
     * @param fs Optional set of folding closures.
     * @param <D> Type of the input collection elements and type of the free variable for the closure.
     * @param <B> Type of the folding value and return type of the closure.
     * @return Value representing folded collection.
     */
    @Nullable public static <D, B> B fold(Iterable<? extends D> c, @Nullable B b,
        @Nullable GridClosure2<? super D, ? super B, B>... fs) {
        A.notNull(c, "c");

        if (!isEmpty(fs))
            for (D e : c) {
                assert fs != null;

                for (GridClosure2<? super D, ? super B, B> f : fs)
                    b = f.apply(e, b);
            }

        return b;
    }

    /**
     * Folds-right given array using provided closure. If input array contains
     * <tt>a<sub>1</sub>, a<sub>2</sub>, ...,
     * a<sub>n</sub></tt> result value will be <tt>...f(f(f(b,a<sub>1</sub>),a<sub>2</sub>),a<sub>3</sub>)...</tt>
     * where {@code f(x)} is the result of applying each closure from {@code fs} set to
     * the element {@code x} of the input collection {@code c}.
     *
     * @param c Input array.
     * @param b Optional first folding pair element.
     * @param fs Optional set of folding closures.
     * @param <D> Type of the input array elements and type of the free variable for the closure.
     * @param <B> Type of the folding value and return type of the closure.
     * @return Value representing folded array.
     */
    @Nullable
    public static <D, B> B fold(D[] c, @Nullable B b, @Nullable GridClosure2<? super D, ? super B, B>... fs) {
        A.notNull(c, "c");

        return fold(asList(c), b, fs);
    }

    /**
     * Factory method returning new tuple with given parameter.
     *
     * @param v Parameter for tuple.
     * @param <V> Type of the tuple.
     * @return Newly created tuple.
     */
    public static <V> GridTuple<V> t(@Nullable V v) {
        return new GridTuple<V>(v);
    }

    /**
     * Factory method returning empty tuple.
     *
     * @param <V> Type of the tuple.
     * @return Newly created empty tuple.
     */
    public static <V> GridTuple<V> t1() {
        return new GridTuple<V>();
    }

    /**
     * Factory method returning new tuple with given parameters.
     *
     * @param v1 1st parameter for tuple.
     * @param v2 2nd parameter for tuple.
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @return Newly created tuple.
     */
    public static <V1, V2> GridTuple2<V1, V2> t(@Nullable V1 v1, @Nullable V2 v2) {
        return new GridTuple2<V1, V2>(v1, v2);
    }

    /**
     * Special version of {@link #t(Object, Object)} method that is tailored for
     * {@code with(...)} method in JEXL-based predicates to set up the external context for JEXL evaluation.
     *
     * @param var Name of variable in JEXL context.
     * @param val Variable value in JEXL context.
     * @return Newly created tuple.
     * @see GridJexlPredicate
     * @see GridJexlPredicate2
     * @see GridJexlPredicate3
     */
    public static GridTuple2<String, Object> jexl(String var, @Nullable Object val) {
        return new GridTuple2<String, Object>(var, val);
    }

    /**
     * Factory method returning new empty tuple.
     *
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @return Newly created empty tuple.
     */
    public static <V1, V2> GridTuple2<V1, V2> t2() {
        return new GridTuple2<V1, V2>();
    }

    /**
     * Factory method returning new tuple with given parameters.
     *
     * @param v1 1st parameter for tuple.
     * @param v2 2nd parameter for tuple.
     * @param v3 3rd parameter for tuple.
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @return Newly created tuple.
     */
    public static <V1, V2, V3> GridTuple3<V1, V2, V3> t(@Nullable V1 v1, @Nullable V2 v2, @Nullable V3 v3) {
        return new GridTuple3<V1, V2, V3>(v1, v2, v3);
    }

    /**
     * Factory method returning new tuple with given parameters.
     *
     * @param v1 1st parameter for tuple.
     * @param v2 2nd parameter for tuple.
     * @param v3 3rd parameter for tuple.
     * @param v4 4th parameter for tuple.
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @param <V4> Type of the 4th tuple parameter.
     * @return Newly created tuple.
     */
    public static <V1, V2, V3, V4> GridTuple4<V1, V2, V3, V4> t(@Nullable V1 v1, @Nullable V2 v2, @Nullable V3 v3,
        @Nullable V4 v4) {
        return new GridTuple4<V1, V2, V3, V4>(v1, v2, v3, v4);
    }

    /**
     * Factory method returning new tuple with given parameters.
     *
     * @param v1 1st parameter for tuple.
     * @param v2 2nd parameter for tuple.
     * @param v3 3rd parameter for tuple.
     * @param v4 4th parameter for tuple.
     * @param v5 5th parameter for tuple.
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @param <V4> Type of the 4th tuple parameter.
     * @param <V5> Type of the 5th tuple parameter.
     * @return Newly created tuple.
     */
    public static <V1, V2, V3, V4, V5> GridTuple5<V1, V2, V3, V4, V5> t(@Nullable V1 v1, @Nullable V2 v2,
        @Nullable V3 v3, @Nullable V4 v4, @Nullable V5 v5) {
        return new GridTuple5<V1, V2, V3, V4, V5>(v1, v2, v3, v4, v5);
    }

    /**
     * Creates vararg tuple with given values.
     *
     * @param objs Values for vararg tuple.
     * @return Vararg tuple with given values.
     */
    public static GridTupleV tv(Object... objs) {
        assert objs != null;

        return new GridTupleV(objs);
    }

    /**
     * Factory method returning new empty tuple.
     *
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @return Newly created empty tuple.
     */
    public static <V1, V2, V3> GridTuple3<V1, V2, V3> t3() {
        return new GridTuple3<V1, V2, V3>();
    }

    /**
     * Factory method returning new empty tuple.
     *
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @param <V4> Type of the 4th tuple parameter.
     * @return Newly created empty tuple.
     */
    public static <V1, V2, V3, V4> GridTuple4<V1, V2, V3, V4> t4() {
        return new GridTuple4<V1, V2, V3, V4>();
    }

    /**
     * Factory method returning new empty tuple.
     *
     * @param <V1> Type of the 1st tuple parameter.
     * @param <V2> Type of the 2nd tuple parameter.
     * @param <V3> Type of the 3rd tuple parameter.
     * @param <V4> Type of the 4th tuple parameter.
     * @param <V5> Type of the 5th tuple parameter.
     * @return Newly created empty tuple.
     */
    public static <V1, V2, V3, V4, V5> GridTuple5<V1, V2, V3, V4, V5> t5() {
        return new GridTuple5<V1, V2, V3, V4, V5>();
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value will
     *      evaluate this predicate to {@code false}.
     * @param <T> Type of the free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T> GridJexlPredicate<T> x1(String expr) {
        return new GridJexlPredicate<T>(expr);
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value will evaluate
     *      this predicate to {@code false}.
     * @param var1 Name of the 1st bound variable in JEXL expression.
     * @param var2 Name of the 2nd bound variable in JEXL expression.
     * @param <T1> Type of the 1st free variable, i.e. the element the closure is called on.
     * @param <T2> Type of the 2nd free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T1, T2> GridJexlPredicate2<T1, T2> x2(String expr, String var1, String var2) {
        return new GridJexlPredicate2<T1, T2>(expr, var1, var2);
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value will evaluate
     *      this predicate to {@code false}.
     * @param var1 Name of the 1st bound variable in JEXL expression.
     * @param var2 Name of the 2nd bound variable in JEXL expression.
     * @param var3 Name of the 3rd bound variable in JEXL expression.
     * @param <T1> Type of the 1st free variable, i.e. the element the closure is called on.
     * @param <T2> Type of the 2nd free variable, i.e. the element the closure is called on.
     * @param <T3> Type of the 3rd free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T1, T2, T3> GridJexlPredicate3<T1, T2, T3> x3(String expr, String var1, String var2, String var3) {
        return new GridJexlPredicate3<T1, T2, T3>(expr, var1, var2, var3);
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value will evaluate
     *      this predicate to {@code false}.
     * @param <T1> Type of the 1st free variable, i.e. the element the closure is called on.
     * @param <T2> Type of the 2nd free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T1, T2> GridJexlPredicate2<T1, T2> x2(String expr) {
        return new GridJexlPredicate2<T1, T2>(expr);
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value will evaluate
     *      this predicate to {@code false}.
     * @param <T1> Type of the 1st free variable, i.e. the element the closure is called on.
     * @param <T2> Type of the 2nd free variable, i.e. the element the closure is called on.
     * @param <T3> Type of the 3rd free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T1, T2, T3> GridJexlPredicate3<T1, T2, T3> x3(String expr) {
        return new GridJexlPredicate3<T1, T2, T3>(expr);
    }

    /**
     * Utility shortcut for creating JEXL predicate.
     *
     * @param expr JEXL boolean expression. Note that non-boolean return value
     *      will evaluate this predicate to {@code false}.
     * @param var Name of the bound variable in JEXL expression.
     * @param <T> Type of the free variable, i.e. the element the closure is called on.
     * @return Newly created JEXL predicate.
     */
    public static <T> GridJexlPredicate<T> x1(String expr, String var) {
        return new GridJexlPredicate<T>(expr, var);
    }

    /**
     * Converts collection to map using collection values as keys and
     * passed in default value as values.
     *
     * @param keys Map keys.
     * @param dfltVal Default value.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Resulting map.
     */
    public static <K, V> Map<K, V> zip(Collection<? extends K> keys, V dfltVal) {
        A.notNull(keys, "keys");

        Map<K, V> m = new HashMap<K, V>(keys.size(), 1.0f);

        for (K k : keys) {
            m.put(k, dfltVal);
        }

        return m;
    }

    /**
     * Converts 2 collections into map using first collection as keys and second
     * collection as values. Note that collections must be of equal length.
     *
     * @param keys Map keys.
     * @param vals Map values.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Resulting map.
     */
    public static <K, V> Map<K, V> zip(Collection<? extends K> keys, Collection<? extends V> vals) {
        A.notNull(keys, "keys", vals, "vals");
        A.ensure(keys.size() == vals.size(), "keys.size() == vals.size()");

        Map<K, V> m = new HashMap<K, V>(keys.size(), 1.0f);

        Iterator<? extends V> it = vals.iterator();

        for (K k : keys) {
            m.put(k, it.next());
        }

        return m;
    }

    /**
     * Creates map with given values.
     *
     * @param k Key.
     * @param v Value.
     * @param <K> Key's type.
     * @param <V> Value's type.
     * @return Created map.
     */
    public static <K, V> Map<K, V> asMap(K k, V v) {
        Map<K, V> map = new GridLeanMap<K, V>(1);

        map.put(k, v);

        return map;
    }

    /**
     * Creates map with given values.
     *
     * @param k1 Key 1.
     * @param v1 Value 1.
     * @param k2 Key 2.
     * @param v2 Value 2.
     * @param <K> Key's type.
     * @param <V> Value's type.
     * @return Created map.
     */
    public static <K, V> Map<K, V> asMap(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new GridLeanMap<K, V>(2);

        map.put(k1, v1);
        map.put(k2, v2);

        return map;
    }

    /**
     * Creates map with given values.
     *
     * @param k1 Key 1.
     * @param v1 Value 1.
     * @param k2 Key 2.
     * @param v2 Value 2.
     * @param k3 Key 3.
     * @param v3 Value 3.
     * @param <K> Key's type.
     * @param <V> Value's type.
     * @return Created map.
     */
    public static <K, V> Map<K, V> asMap(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new GridLeanMap<K, V>(3);

        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);

        return map;
    }

    /**
     * Creates map with given values.
     *
     * @param k1 Key 1.
     * @param v1 Value 1.
     * @param k2 Key 2.
     * @param v2 Value 2.
     * @param k3 Key 3.
     * @param v3 Value 3.
     * @param k4 Key 4.
     * @param v4 Value 4.
     * @param <K> Key's type.
     * @param <V> Value's type.
     * @return Created map.
     */
    public static <K, V> Map<K, V> asMap(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new GridLeanMap<K, V>(4);

        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);

        return map;
    }

    /**
     * Creates map with given values.
     *
     * @param k1 Key 1.
     * @param v1 Value 1.
     * @param k2 Key 2.
     * @param v2 Value 2.
     * @param k3 Key 3.
     * @param v3 Value 3.
     * @param k4 Key 4.
     * @param v4 Value 4.
     * @param k5 Key 5.
     * @param v5 Value 5.
     * @param <K> Key's type.
     * @param <V> Value's type.
     * @return Created map.
     */
    public static <K, V> Map<K, V> asMap(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new GridLeanMap<K, V>(5);

        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);

        return map;
    }

    /**
     * Creates read-only list with given values.
     *
     * @param t Element (if {@code null}, then empty list is returned).
     * @param <T> Element's type.
     * @return Created list.
     */
    public static <T> List<T> asList(@Nullable T t) {
        return t == null ? Collections.<T>emptyList() : Collections.singletonList(t);
    }

    /**
     * Creates read-only set with given value.
     *
     * @param t Element (if {@code null}, then empty set is returned).
     * @param <T> Element's type.
     * @return Created set.
     */
    public static <T> Set<T> asSet(@Nullable T t) {
        return t == null ? Collections.<T>emptySet() : Collections.singleton(t);
    }

    /**
     * Creates read-only set with given values.
     *
     * @param t Element (if {@code null}, then empty set is returned).
     * @param <T> Element's type.
     * @return Created set.
     */
    @SuppressWarnings( {"RedundantTypeArguments"})
    public static <T> Set<T> asSet(@Nullable T... t) {
        if (t == null || t.length == 0)
            return Collections.<T>emptySet();

        if (t.length == 1)
            return Collections.singleton(t[0]);

        return new GridLeanSet<T>(asList(t));
    }

    /**
     * Checks if value is contained in the collection passed in. If the collection
     * is {@code null}, then {@code false} is returned.
     *
     * @param c Collection to check.
     * @param t Value to check for containment.
     * @param <T> Value type.
     * @return {@code True} if collection is not {@code null} and contains given
     *      value, {@code false} otherwise.
     */
    public static <T> boolean contains(@Nullable Collection<T> c, @Nullable T t) {
        return c != null && c.contains(t);
    }

    /**
     * Provides predicate which returns {@code true} if it receives an element
     * that is contained in the passed in collection.
     *
     * @param c Collection used for predicate filter.
     * @param <T> Element type.
     * @return Predicate which returns {@code true} if it receives an element
     *  that is contained in the passed in collection.
     */
    public static <T> GridPredicate<T> contains(@Nullable final Collection<T> c) {
        return c == null || c.isEmpty() ? GridFunc.<T>alwaysFalse() : new P1<T>() {
            @Override public boolean apply(T t) {
                return c.contains(t);
            }
        };
    }

    /**
     * Provides predicate which returns {@code true} if it receives an element
     * that is not contained in the passed in collection.
     *
     * @param c Collection used for predicate filter.
     * @param <T> Element type.
     * @return Predicate which returns {@code true} if it receives an element
     *  that is not contained in the passed in collection.
     */
    public static <T> GridPredicate<T> notContains(@Nullable final Collection<T> c) {
        return c == null || c.isEmpty() ? GridFunc.<T>alwaysTrue() : new P1<T>() {
            @Override public boolean apply(T t) {
                return !c.contains(t);
            }
        };
    }

    /**
     * Gets utility predicate that accepts {@link java.util.Map.Entry} value and compares
     * its value to the given value.
     *
     * @param val Value to compare entry's value.
     * @param <K> Map key type.
     * @param <V> Map value type.
     * @return Predicate that accepts {@link java.util.Map.Entry} value and compares its value
     *      to the given value.
     */
    public static <K, V> GridPredicate<Map.Entry<K, V>> mapValue(@Nullable final V val) {
        return new P1<Map.Entry<K, V>>() {
            {
                if (val != null) {
                    peerDeployLike(U.peerDeployAware(val));
                }
            }

            @Override public boolean apply(Map.Entry<K, V> e) {
                return e.getValue().equals(val);
            }
        };
    }

    /**
     * Gets utility predicate that accepts {@code Map.Entry} value and compares its key
     * to the given value.
     *
     * @param key Value to compare entry's key.
     * @param <K> Map key type.
     * @param <V> Map value type.
     * @return Predicate that accepts {@code Map.Entry} value and compares its key
     *      to the given value.
     */
    public static <K, V> GridPredicate<Map.Entry<K, V>> mapKey(@Nullable final K key) {
        return new P1<Map.Entry<K, V>>() {
            {
                if (key != null) {
                    peerDeployLike(U.peerDeployAware(key));
                }
            }

            @Override public boolean apply(Map.Entry<K, V> e) {
                return e.getKey().equals(key);
            }
        };
    }

    /**
     * Tests whether specified arguments are equal, or both {@code null}.
     *
     * @param o1 Object to compare.
     * @param o2 Object to compare.
     * @return Returns {@code true} if the specified arguments are equal, or both {@code null}.
     */
    public static boolean eq(@Nullable Object o1, @Nullable Object o2) {
        return o1 == null ? o2 == null : o2 != null && (o1 == o2 || o1.equals(o2));
    }

    /**
     * Checks if both collections have equal elements in the same order.
     *
     * @param c1 First collection.
     * @param c2 Second collection.
     * @return {@code True} if both collections have equal elements in the same order.
     */
    public static boolean eqOrdered(@Nullable Collection<?> c1, @Nullable Collection<?> c2) {
        if (c1 == c2) {
            return true;
        }

        if (c1 == null || c2 == null) {
            return false;
        }

        if (c1.size() != c2.size()) {
            return false;
        }

        Iterator<?> it1 = c1.iterator();
        Iterator<?> it2 = c2.iterator();

        while (it1.hasNext() && it2.hasNext()) {
            if (!eq(it1.next(), it2.next())) {
                return false;
            }
        }

        return it1.hasNext() == it2.hasNext();
    }

    /**
     * Checks if both collections have equal elements in any order.
     *
     * @param c1 First collection.
     * @param c2 Second collection.
     * @return {@code True} if both collections have equal elements in any order.
     */
    public static boolean eqNotOrdered(@Nullable Collection<?> c1, @Nullable Collection<?> c2) {
        if (c1 == c2) {
            return true;
        }

        if (c1 == null || c2 == null) {
            return false;
        }

        if (c1.size() != c2.size()) {
            return false;
        }

        for (Object o : c1) {
            if (!c2.contains(o)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares two arrays. Unlike {@code Arrays#equals(...)} method this implementation
     * checks two arrays as sets allowing the same elements to be in different indexes.
     *
     * @param a1 First array to check.
     * @param a2 Second array to check.
     * @param sorted Tells whether or not both arrays are pre-sorted so that binary
     *      search could be used instead of iteration.
     * @param dups Tells whether or not arrays can contain duplicates. If arrays contain
     *      duplicate the implementation will have to do double work.
     * @return {@code True} if arrays are equal, {@code false} otherwise.
     */
    public static boolean eqArray(Object[] a1, Object[] a2, boolean sorted, boolean dups) {
        if (a1 == a2) {
            return true;
        }

        if (a1 == null || a2 == null || a1.length != a2.length) {
            return false;
        }

        // Short circuit.
        if (a1.length == 1) {
            return eq(a1[0], a2[0]);
        }

        for (Object o1 : a1) {
            boolean found = false;

            if (sorted) {
                found = Arrays.binarySearch(a2, o1) >= 0;
            }
            else {
                for (Object o2 : a2) {
                    if (eq(o1, o2)) {
                        found = true;

                        break;
                    }
                }
            }

            if (!found) {
                return false;
            }
        }

        // If there are no dups - we can't skip checking seconds array
        // against first one.
        if (dups) {
            for (Object o2 : a2) {
                boolean found = false;

                if (sorted) {
                    found = Arrays.binarySearch(a1, o2) >= 0;
                }
                else {
                    for (Object o1 : a1) {
                        if (eq(o2, o1)) {
                            found = true;

                            break;
                        }
                    }
                }

                if (!found) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Compares two {@link GridNode} instances for equality.
     * <p>
     * Since introduction of {@link GridRichNode} in GridGain 3.0 the semantic of equality between
     * grid nodes has changed. Since rich node wraps thin node instance and in the same time
     * implements {@link GridNode} interface, the proper semantic of comparing two grid node is
     * to ignore their runtime types and compare only by their IDs. This method implements this logic.
     * <p>
     * End users rarely, if ever, need to directly compare two grid nodes for equality. This method is
     * intended primarily for discovery SPI developers that provide implementations of {@link GridNode}
     * interface.
     *
     * @param n1 Grid node 1.
     * @param n2 Grid node 2
     * @return {@code true} if two grid node have the same IDs (ignoring their runtime types),
     *      {@code false} otherwise.
     */
    public static boolean eqNodes(Object n1, Object n2) {
        return n1 == n2 || !(n1 == null || n2 == null) && !(!(n1 instanceof GridNode) || !(n2 instanceof GridNode))
            && ((GridNode)n1).id().equals(((GridNode)n2).id());
    }

    /**
     * Curries collection of closures with given argument.
     *
     * @param iter Collection to curry.
     * @param arg Argument to curry with.
     * @param <T> Type of closure argument.
     * @param <R> Type of closure return value.
     * @return Read only collection of curried closures.
     */
    public static <T, R> Collection<GridOutClosure<R>> curry(Collection<? extends GridClosure<? super T, R>> iter,
        final T arg) {
        A.notNull(iter, "iter", arg, "arg");

        return viewReadOnly(iter, new C1<GridClosure<? super T, R>, GridOutClosure<R>>() {
            @Override public GridOutClosure<R> apply(GridClosure<? super T, R> c) {
                return c.curry(arg);
            }
        });
    }

    /**
     * Curries collection of closures with given collection of arguments.
     *
     * @param in Collection to curry.
     * @param args Collection of arguments to curry with.
     * @param <T> Type of closure argument.
     * @param <R> Type of closure return value.
     * @return Collection of curried closures.
     */
    public static <T, R> Collection<GridOutClosure<R>> curry(Collection<? extends GridClosure<? super T, R>> in,
        Collection<? extends T> args) {
        A.notNull(in, "in", args, "args");
        A.ensure(in.size() == args.size(), "in.size() == args.size()");

        Collection<GridOutClosure<R>> ret = new ArrayList<GridOutClosure<R>>(in.size());

        Iterator<? extends T> iter = args.iterator();

        for (GridClosure<? super T, R> c : in) {
            ret.add(c.curry(iter.next()));
        }

        return ret;
    }

    /**
     * Curries collection of in closures with given collection of arguments. Note that name is
     * different due to type erasure and subsequent name conflict with other {@code curry} methods.
     *
     * @param in Collection to curry.
     * @param args Collection of arguments to curry with.
     * @param <T> Type of closure argument.
     * @return Collection of curried closures.
     */
    public static <T> Collection<GridAbsClosure> curry0(Collection<? extends GridInClosure<? super T>> in,
        Collection<? extends T> args) {
        A.notNull(in, "in", args, "args");
        A.ensure(in.size() == args.size(), "in.size() == args.size()");

        Collection<GridAbsClosure> ret = new ArrayList<GridAbsClosure>(in.size());

        Iterator<? extends T> iter = args.iterator();

        for (GridInClosure<? super T> c : in) {
            ret.add(c.curry(iter.next()));
        }

        return ret;
    }

    /**
     * Curries collection of closures with given collection of arguments.
     *
     * @param c Closure to curry.
     * @param args Collection of arguments to curry with.
     * @param <T> Type of closure argument.
     * @param <R> Type of closure return value.
     * @return Collection of curried closures.
     */
    public static <T, R> Collection<GridOutClosure<R>> curry(GridClosure<? super T, R> c,
        Collection<? extends T> args) {
        A.notNull(c, "c", args, "args");

        Collection<GridOutClosure<R>> ret = new ArrayList<GridOutClosure<R>>(args.size());

        for (T arg : args) {
            ret.add(c.curry(arg));
        }

        return ret;
    }

    /**
     * Curries collection of closures with given collection of arguments.
     *
     * @param cnt Number of closures to produce.
     * @param c Closure to curry.
     * @param pdc Producer of arguments to curry with.
     * @param <T> Type of closure argument.
     * @param <R> Type of closure return value.
     * @return Collection of curried closures.
     */
    public static <T, R> Collection<GridOutClosure<R>> curry(int cnt, GridClosure<? super T, R> c,
        GridOutClosure<T> pdc) {
        A.notNull(c, "c", pdc, "pdc");
        A.ensure(cnt > 0, "cnt > 0");

        Collection<GridOutClosure<R>> ret = new ArrayList<GridOutClosure<R>>(cnt);

        for (int i = 0; i < cnt; i++) {
            ret.add(c.curry(pdc.apply()));
        }

        return ret;
    }

    /**
     * Curries collection of closures with given collection of arguments.
     *
     * @param c Closure to curry.
     * @param args Collection of arguments to curry with.
     * @param <T> Type of closure argument.
     * @return Collection of curried closures.
     */
    public static <T> Collection<GridAbsClosure> curry(GridInClosure<? super T> c, Collection<? extends T> args) {
        A.notNull(c, "c", args, "args");

        Collection<GridAbsClosure> ret = new ArrayList<GridAbsClosure>(args.size());

        for (T arg : args) {
            ret.add(c.curry(arg));
        }

        return ret;
    }

    /**
     * Curries collection of closures with given collection of arguments.
     *
     * @param cnt Number of closures to produce.
     * @param c Closure to curry.
     * @param pdc Producer of arguments to curry with.
     * @param <T> Type of closure argument.
     * @return Collection of curried closures.
     */
    public static <T> Collection<GridAbsClosure> curry(int cnt, GridInClosure<? super T> c, GridOutClosure<T> pdc) {
        A.notNull(c, "c", pdc, "pdc");
        A.ensure(cnt > 0, "cnt > 0");

        Collection<GridAbsClosure> ret = new ArrayList<GridAbsClosure>(cnt);

        for (int i = 0; i < cnt; i++) {
            ret.add(c.curry(pdc.apply()));
        }

        return ret;
    }

    /**
     * Curries collection of closures with given argument.
     *
     * @param iter Collection to curry.
     * @param arg Argument to curry with.
     * @param <T1> Type of 1st closure argument.
     * @param <T2> Type of 2nd closure argument.
     * @param <R> Type of closure return value.
     * @return Read only collection of curried closures.
     */
    public static <T1, T2, R> Collection<GridClosure<T2, R>> curry2(Collection<? extends GridClosure2<? super T1,
        T2, R>> iter, final T1 arg) {
        A.notNull(iter, "iter", arg, "arg");

        return viewReadOnly(iter, new C1<GridClosure2<? super T1, T2, R>, GridClosure<T2, R>>() {
            @Override public GridClosure<T2, R> apply(GridClosure2<? super T1, T2, R> c) {
                return c.curry(arg);
            }
        });
    }

    /**
     * Curries collection of closures with given argument.
     *
     * @param iter Collection to curry.
     * @param arg Argument to curry with.
     * @param <T1> Type of 1st closure argument.
     * @param <T2> Type of 2nd closure argument.
     * @param <T3> Type of 3rd closure argument.
     * @param <R> Type of closure return value.
     * @return Read only collection of curried closures.
     */
    public static <T1, T2, T3, R> Collection<GridClosure2<T2, T3, R>> curry3(
        Collection<? extends GridClosure3<? super T1, T2, T3, R>> iter, final T1 arg) {
        A.notNull(iter, "iter", arg, "arg");

        return viewReadOnly(iter, new C1<GridClosure3<? super T1, T2, T3, R>, GridClosure2<T2, T3, R>>() {
            @Override public GridClosure2<T2, T3, R> apply(GridClosure3<? super T1, T2, T3, R> c) {
                return c.curry(arg);
            }
        });
    }

    /**
     * Gets closure that returns key for an entry. The closure internally
     * delegates to {@link java.util.Map.Entry#getKey()} method.
     *
     * @param <K> Key type.
    * @return Closure that returns key for an entry.
     */
    @SuppressWarnings({"unchecked"})
    public static <K> GridClosure<Map.Entry<K, ?>, K> mapEntry2Key() {
        return (GridClosure<Map.Entry<K, ?>, K>)MAP_ENTRY_KEY;
    }

    /**
     * Gets closure that returns value for an entry. The closure internally
     * delegates to {@link java.util.Map.Entry#getValue()} method.
     *
     * @param <V> Value type.
     * @return Closure that returns key for an entry.
     */
    @SuppressWarnings({"unchecked"})
    public static <V> GridClosure<Map.Entry<?, V>, V> mapEntry2Value() {
        return (GridClosure<Map.Entry<?, V>, V>)MAP_ENTRY_VAL;
    }

    /**
     * Gets closure that returns value for an entry. The closure internally
     * delegates to {@link GridCacheEntry#get(GridPredicate[])} method.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Closure that returns value for an entry.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheEntry<K, V>, V> cacheEntry2Get() {
        return (GridClosure<GridCacheEntry<K, V>, V>)CACHE_ENTRY_VAL_GET;
    }

    /**
     * Gets closure that returns result of
     * {@link GridCacheEntry#peek(GridPredicate[]) GridCacheStrictProjection.peek()} method.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Closure that returns result of
     *      {@link GridCacheEntry#peek(GridPredicate[]) GridCacheStrictProjection.peek()} method.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridClosure<GridCacheEntry<K, V>, V> cacheEntry2Peek() {
        return (GridClosure<GridCacheEntry<K, V>, V>)CACHE_ENTRY_VAL_PEEK;
    }

    /**
     * Gets predicate which returns {@code true} if entry's key is contained in given collection.
     * Note that if collection of provided keys is empty this method returns predicate that
     * evaluates to {@code false} when applying.
     *
     * @param keys Keys to check.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if entry's key is contained in given collection.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheHasKeys(
        @Nullable final Collection<? extends K> keys) {
        return isEmpty(keys) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0(keys));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    return keys != null && keys.contains(e.getKey());
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if entry's key is equal to any of provided keys.
     * Note that if array of provided keys is {@code null} or empty this method returns predicate
     * that evaluates to {@code false} when applying.
     *
     * @param keys Keys to check.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if entry's key
     *      is equal to any of provided keys.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheHasKeys(@Nullable K... keys) {
        if (isEmpty(keys)) {
            return alwaysFalse();
        }

        return cacheHasKeys(asList(keys));
    }

    /**
     * Gets predicate which returns {@code true} if entry expires on or before given time
     * in milliseconds.
     *
     * @param msec Maximum expire time in milliseconds.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if entry
     *      expires on or before given time.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheExpireBefore(final long msec) {
        A.ensure(msec >= 0, "msec >= 0");

        return new GridPredicate<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                return e.expirationTime() <= msec;
            }
        };
    }

    /**
     * Gets predicate which returns {@code true} if entry expires on or after given time
     * in milliseconds.
     *
     * @param msec Minimum expire time in milliseconds.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if entry
     *      expires on or after given time.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheExpireAfter(final long msec) {
        A.ensure(msec >= 0, "msec >= 0");

        return new GridPredicate<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                return e.expirationTime() >= msec;
            }
        };
    }

    /**
     * Gets predicate which returns {@code true} if {@link GridCacheEntry#get(GridPredicate[])}
     * method returns {@code non-null} value.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if {@link GridCacheEntry#get(GridPredicate[])}
     *      method returns {@code non-null} value.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheHasGetValue() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_HAS_GET_VAL;
    }

    /**
     * Gets predicate which returns {@code true} if {@link GridCacheEntry#get(GridPredicate[])}
     * method returns {@code null} value.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if {@link GridCacheEntry#get(GridPredicate[])}
     *      method returns {@code null} value.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNoGetValue() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_NO_GET_VAL;
    }

    /**
     * Gets predicate which returns {@code true} if
     * {@link GridCacheEntry#peek(GridPredicate[]) GridCacheEntry.peek()} method
     * returns {@code non-null} value.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if
     *      {@link GridCacheEntry#peek(GridPredicate[]) GridCacheEntry.peek()}
     *      method returns {@code non-null} value.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheHasPeekValue() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_HAS_PEEK_VAL;
    }

    /**
     * Gets predicate which returns {@code true} if
     * {@link GridCacheEntry#peek(GridPredicate[]) GridCacheEntry.peek()}
     * method returns {@code null} value.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if
     *      {@link GridCacheEntry#peek(GridPredicate[]) GridCacheEntry.peek()}
     *      method returns {@code null} value.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNoPeekValue() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_NO_PEEK_VAL;
    }

    /**
     * Gets predicate which returns {@code true} if {@link GridCacheEntry#primary()}
     * method returns {@code true}.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if {@link GridCacheEntry#primary()}
     *      method returns {@code true}.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cachePrimary() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_PRIMARY;
    }

    /**
     * Gets predicate which returns {@code true} if {@link GridCacheEntry#primary()}
     * method returns {@code false}.
     *
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if {@link GridCacheEntry#primary()}
     *      method returns {@code false}.
     */
    @SuppressWarnings({"unchecked"})
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheBackup() {
        return (GridPredicate<GridCacheEntry<K, V>>)CACHE_ENTRY_BACKUP;
    }

    /**
     * Gets predicate which returns true if {@link GridCacheEntry#get(GridPredicate[])}
     * method returns value that is contained in given collection. Note that if collection
     * of provided values is empty this method returns predicate that evaluates to {@code false}
     * when applying.
     *
     * @param vals Values to check in predicate.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns true if {@link GridCacheEntry#get(GridPredicate[])} methods returns
     *      value that is contained in given collection.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsGet(
        @Nullable final Collection<? extends V> vals) {
        return isEmpty(vals) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0(vals));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    try {
                        V v = e.get();

                        assert vals != null;

                        return v != null && vals.contains(v);
                    }
                    catch (GridException e1) {
                        throw wrap(e1);
                    }
                }
            };
    }

    /**
     * Gets predicate which returns true if {@link GridCacheEntry#get(GridPredicate[])} method returns
     * value that is contained among given values. Note that if array of provided values
     * is {@code null} or empty this method returns predicate that evaluates to
     * {@code false} when applying.
     *
     * @param vals Values to check in predicate.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns true if {@link GridCacheEntry#get(GridPredicate[])} methods returns
     *      value that is contained among given values.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsGet(@Nullable V... vals) {
        if (isEmpty(vals)) {
            return alwaysFalse();
        }

        return cacheContainsGet(asList(vals));
    }

    /**
     * Gets predicate which returns true if {@link GridCacheEntry#peek(GridPredicate[])} methods returns
     * value that is contained in given collection. Note that if collection of provided values
     * is empty this method returns predicate that evaluates to {@code false}
     * when applying.
     *
     * @param vals Values to check in predicate.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns true if {@link GridCacheEntry#peek(GridPredicate[])} methods returns
     *      value that is contained in given collection.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsPeek(
        @Nullable final Collection<? extends V> vals) {
        return isEmpty(vals) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0(vals));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    V v = e.peek();

                    assert vals != null;

                    return v != null && vals.contains(v);
                }
            };
    }

    /**
     * Gets predicate which returns true if {@link GridCacheEntry#peek(GridPredicate[])} methods returns
     * value that is contained among given values. Note that if array of provided values
     * is {@code null} or empty this method returns predicate that evaluates to {@code false}
     * when applying.
     *
     * @param vals Values to check in predicate.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns true if {@link GridCacheEntry#peek(GridPredicate[])} methods returns
     *      value that is contained among given values.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsPeek(@Nullable V... vals) {
        if (isEmpty(vals)) {
            return alwaysFalse();
        }

        return cacheContainsPeek(asList(vals));
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Note that if provided map is empty this method returns predicate that evaluates to
     * {@code false} when applying.
     *
     * @param map Key-value paris to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache contains all given key-value pairs.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsGet(@Nullable final Map<K, V> map) {
        return isEmpty(map) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    if (map != null) {
                        Collection<Object> objs = new ArrayList<Object>(map.size() * 2);

                        objs.addAll(map.keySet());
                        objs.addAll(map.values());

                        peerDeployLike(U.peerDeployAware0(objs));
                    }
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    assert map != null;

                    try {
                        return eq(e.get(), map.get(e.getKey()));
                    }
                    catch (GridException ex) {
                        throw wrap(ex);
                    }
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Note that if provided map is empty this method returns predicate that evaluates to
     * {@code false} when applying.
     *
     * @param map Key-value paris to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry matches any given key-value pair.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsPeek(
        @Nullable final Map<K, V> map) {
        return isEmpty(map) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    if (map != null) {
                        Collection<Object> objs = new ArrayList<Object>(map.size() * 2);

                        objs.addAll(map.keySet());
                        objs.addAll(map.values());

                        peerDeployLike(U.peerDeployAware0(objs));
                    }
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    assert map != null;

                    return eq(e.peek(), map.get(e.getKey()));
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Both, key and value will be checked for containment. Value will be retrieved using
     * {@link GridCacheEntry#get(GridPredicate[])} method. Note that if collection of
     * provided entries is empty this method returns predicate that evaluates to {@code false} when
     * applying.
     *
     * @param entries Key-value paris to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry matches any given key-value pair.
     */
    // cacheEntryPredicateForContainsEntriesGet
    // ptCacheContainsEntriesGet
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsEntriesGet(
        @Nullable final Collection<? extends Map.Entry<K, V>> entries) {
        return isEmpty(entries) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    if (entries != null) {
                        Collection<Object> objs = new ArrayList<Object>(entries.size() * 2);

                        for (Map.Entry<K, V> e : entries) {
                            objs.add(e.getKey());
                            objs.add(e.getValue());
                        }

                        peerDeployLike(U.peerDeployAware0(objs));
                    }
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    try {
                        K k = e.getKey();
                        V v = e.get();

                        assert entries != null;

                        for (Map.Entry<K, V> entry : entries) {
                            if (k.equals(entry.getKey()) && v!= null && v.equals(entry.getValue())) {
                                return true;
                            }
                        }

                        return false;
                    }
                    catch (GridException ex) {
                        throw wrap(ex);
                    }
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Both, key and value will be checked for containment. Value will be retrieved using
     * {@link GridCacheEntry#peek(GridPredicate[])} method. Note that if collection
     * of provided entries is empty this method returns predicate that evaluates to {@code false}
     * when applying.
     *
     * @param entries Key-value paris to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry matches any given key-value pair.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsEntriesPeek(
        @Nullable final Collection<? extends Map.Entry<K, V>> entries) {
        return isEmpty(entries) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    if (entries != null) {
                        Collection<Object> objs = new ArrayList<Object>(entries.size() * 2);

                        for (Map.Entry<K, V> e : entries) {
                            objs.add(e.getKey());
                            objs.add(e.getValue());
                        }

                        peerDeployLike(U.peerDeployAware0(objs));
                    }
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    K k = e.getKey();
                    V v = e.peek();

                    assert entries != null;

                    for (Map.Entry<K, V> entry : entries) {
                        if (eq(k, entry.getKey()) && eq(v, entry.getValue())) {
                            return true;
                        }
                    }

                    return false;
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Both, key and value will be checked for containment. Value will be retrieved using
     * {@link GridCacheEntry#get(GridPredicate[])} method. Note that if array of provided
     * entries is {@code null} or empty this method returns predicate that evaluates to {@code false}
     * when applying.
     *
     * @param entries Key-value pairs to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry matches any given key-value pair.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsEntriesGet(
        @Nullable Map.Entry<K, V>... entries) {
        if (isEmpty(entries)) {
            return alwaysFalse();
        }

        return cacheContainsEntriesGet(asList(entries));
    }

    /**
     * Gets predicate which returns {@code true} if cache entry matches any given key-value pair.
     * Both, key and value will be checked for containment. Value will be retrieved using
     * {@link GridCacheEntry#peek(GridPredicate[])} method. Note that if array of
     * provided entries is {@code null} or empty this method returns predicate that evaluates
     * to {@code false} when applying.
     *
     * @param entries Key-value paris to check for containment.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry matches any given key-value pair.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheContainsEntriesPeek(
        @Nullable Map.Entry<K, V>... entries) {
        if (isEmpty(entries)) {
            return alwaysFalse();
        }

        return cacheContainsEntriesPeek(asList(entries));
    }

    /**
     * Converts key filter to entry filter using {@link GridCacheEntry#getKey()}
     * to get value. Note that if array of provided filters is {@code null} or empty this
     * method returns predicate that evaluates to {@code true} when applying.
     *
     * @param ps Key filter(s) to convert.
     * @return Entry filter.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheKeys(
        @Nullable final GridPredicate<? super K>... ps) {
        return isEmpty(ps) || isAlwaysTrue(ps) ? F.<GridCacheEntry<K, V>>alwaysTrue() :
            isAlwaysFalse(ps) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0((Object[])ps));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    return F.isAll(e.getKey(), ps);
                }
            };
    }

    /**
     * Converts value filter to entry filter using {@link GridCacheEntry#get(GridPredicate[])} to get value.
     * Note that if array of provided filters is {@code null} or empty this method returns
     * predicate that evaluates to {@code true} when applying.
     *
     * @param ps Value filter(s) to convert.
     * @return Entry filter.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheValuesGet(
        @Nullable final GridPredicate<? super V>... ps) {
        return isEmpty(ps) || isAlwaysTrue(ps) ? F.<GridCacheEntry<K, V>>alwaysTrue() :
            isAlwaysFalse(ps) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0((Object[])ps));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    try {
                        V v = e.get();

                        return v != null && F.isAll(v, ps);
                    }
                    catch (GridException ex) {
                        throw wrap(ex);
                    }
                }
            };
    }

    /**
     * Converts value filter to entry filter using {@link GridCacheEntry#peek(GridPredicate[])}
     * to get value. Note that if array of provided filters is {@code null} or empty this method returns
     * predicate that evaluates to {@code true} when applying.
     *
     * @param ps Value filter(s) to convert.
     * @return Entry filter.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheValuesPeek(
        @Nullable final GridPredicate<? super V>... ps) {
        return isEmpty(ps) || isAlwaysTrue(ps) ? F.<GridCacheEntry<K, V>>alwaysTrue() :
            isAlwaysFalse(ps) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0((Object[])ps));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    V v = e.peek();

                    return v != null && F.isAll(v, ps);
                }
            };
    }

    /**
     * Gets node predicate which returns {@code true} for all nodes which have given cache names
     * started.
     *
     * @param cacheNames Cache names to get predicate for. Empty array means default cache name. If
     *      {@code null} array is passed, then {@link #alwaysFalse()} predicate will be returned.
     * @return Predicate which returns {@code true} for all nodes which have given cache names
     *      started.
     */
    public static GridPredicate<GridRichNode> cacheNodesForNames(@Nullable final String... cacheNames) {
        if (cacheNames == null)
            return alwaysFalse();

        return new P1<GridRichNode>() {
            @Override public boolean apply(GridRichNode n) {
                Collection<String> names = U.cacheNames(n);

                for (String name : names) {
                    if (name == null && cacheNames.length == 0)
                        return true;

                    if (U.containsStringArray(cacheNames, name, false))
                        return true;
                }

                return false;
            }
        };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry nodes is a subset of given
     * grid projections' nodes. Note that if array of provided projections is {@code null}
     * or empty this method returns predicate that evaluates to {@code false} when applying.
     *
     * @param prj Array of grid projections.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry nodes is a subset
     *      of given grid projections' nodes.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNodes(@Nullable
        GridProjection... prj) {
        if (isEmpty(prj)) {
            return alwaysFalse();
        }

        assert prj != null;

        Collection<GridRichNode> nodes = new HashSet<GridRichNode>();

        for (GridProjection s : prj) {
            nodes.addAll(s.nodes());
        }

        return cacheNodes(nodes);
    }

    /**
     * Gets predicate which returns {@code true} if cache entry subgrid is a subset of given
     * nodes. Note that if collection of provided nodes is empty this method returns predicate
     * that evaluates to {@code false} when applying.
     *
     * @param nodes Grid nodes.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry mapped to one
     *      of given nodes.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNodes(
        @Nullable final Collection<? extends GridRichNode> nodes) {
        return isEmpty(nodes) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0(nodes));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    assert nodes != null;

                    return nodes.containsAll(e.gridProjection().nodes());
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry subgrid is a subset of given
     * nodes. Note that if array of provided nodes is {@code null} or empty this method returns
     * predicate that evaluates to {@code false} when applying.
     *
     * @param nodes Array of grid nodes.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry subgrid is a subset
     *      of given nodes.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNodes(@Nullable
        GridRichNode... nodes) {
        if (isEmpty(nodes)) {
            return alwaysFalse();
        }

        return cacheNodes(asList(nodes));
    }

    /**
     * Gets predicate which returns {@code true} if cache entry subgrid is a subset of given
     * nodes (specified by ids). Note that if collection of provided node ids is empty this method
     * returns predicate that evaluates to {@code false} when applying.
     *
     * @param nodeIds Collections of node ids.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry subgrid is a subset
     *      of given nodes (specified by ids).
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNodeIds(
        @Nullable final Collection<UUID> nodeIds) {
        return isEmpty(nodeIds) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {

                // Don't set peer deploy aware as UUID is loaded by
                // system class loader.

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    assert nodeIds != null;

                    for (GridRichNode n : e.gridProjection().nodes()) {
                        if (!nodeIds.contains(n.id())) {
                            return false;
                        }
                    }

                    return true;
                }
            };
    }

    /**
     * Gets predicate which returns {@code true} if cache entry subgrid is a subset of given
     * nodes (specified by ids). Note that if array of provided node ids is {@code null} or empty
     * this method returns predicate that evaluates to {@code false} when applying.
     *
     * @param nodeIds Collections of node ids.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Predicate which returns {@code true} if cache entry subgrid is a subset
     *      of given nodes (specified by ids).
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheNodeIds(@Nullable UUID... nodeIds) {
        if (isEmpty(nodeIds)) {
            return alwaysFalse();
        }

        return cacheNodeIds(asList(nodeIds));
    }

    /**
     * Converts metrics filter to entry filter using {@link GridCacheEntry#metrics()}.
     * Note that if collection of provided predicates is empty or {@code null} this method
     * returns predicate that evaluates to {@code true} when applying.
     *
     * @param p Metrics filter(s) to convert.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Entry filter.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheMetrics(
        @Nullable final Collection<? extends GridPredicate<? super GridCacheMetrics>> p) {
        return isEmpty(p) || isAlwaysTrue(p) ? F.<GridCacheEntry<K, V>>alwaysTrue() :
            isAlwaysFalse(p) ? F.<GridCacheEntry<K, V>>alwaysFalse() :
            new GridPredicate<GridCacheEntry<K, V>>() {
                {
                    peerDeployLike(U.peerDeployAware0(p));
                }

                @Override public boolean apply(GridCacheEntry<K, V> e) {
                    return isAll(e.metrics(), p);
                }
            };
    }

    /**
     * Converts metrics filter to entry filter using {@link GridCacheEntry#metrics()}.
     * Note that if array of provided predicates is {@code null} or empty this method
     * returns predicate that evaluates to {@code true} when applying.
     *
     * @param p Metrics filter(s) to convert.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Entry filter.
     */
    public static <K, V> GridPredicate<GridCacheEntry<K, V>> cacheMetrics(
        @Nullable GridPredicate<? super GridCacheMetrics>... p) {
        if (isEmpty(p)) {
            return alwaysTrue();
        }

        return cacheMetrics(asList(p));
    }

    /**
     * Gets event predicate that returns {@code true} only if event type is one of the given.
     * Note that if array of provided types is {@code null} or empty this method returns
     * predicate that evaluates to {@code false} when applying.
     *
     * @param types Event types.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventType(@Nullable final int... types) {
        return isEmpty(types) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent e) {
                assert e != null;

                assert types != null;

                for (int t : types) {
                    if (e.type() == t) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if event id is one of the given.
     * Note that if array of provided ids is empty this method returns predicate that
     * evaluates to {@code false} when applying.
     *
     * @param ids Event ids.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventId(@Nullable final GridUuid... ids) {
        return isEmpty(ids) ? F.<GridEvent>alwaysFalse() :
            new GridPredicate<GridEvent>() {
                // Don't set peer deploy aware as UUID is loaded by
                // system class loader.

                @Override public boolean apply(GridEvent e) {
                    assert e != null;

                    return F.isAll(e.id(), in(ids));
                }
            };
    }

    /**
     * Gets event predicate that returns {@code true} only if event was produced
     * after given timestamp.
     *
     * @param tstamp Timestamp.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventAfter(final long tstamp) {
        A.ensure(tstamp > 0, "tstamp > 0");

        return new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent e) {
                assert e != null;

                return e.timestamp() > tstamp;
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if event was produced on one of
     * given nodes (specified by ids). Note that if array of provided node ids is {@code null}
     * or empty this method returns predicate that evaluates to {@code false} when applying.
     *
     * @param nodeIds Node ids.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventNodeId(@Nullable final UUID... nodeIds) {
        return isEmpty(nodeIds) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            // Don't set peer deploy aware as UUID is loaded by
            // system class loader.

            @Override public boolean apply(GridEvent e) {
                assert e != null;

                return F.isAll(e.nodeId(), in(nodeIds));
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if node that produced the event
     * satisfies all given predicates. Note that if array of provided node predicates is
     * {@code null} or empty this method returns predicate that evaluates to {@code true}
     * assuming that any event produced by any node is ok.
     *
     * @param gridName Grid name.
     * @param p Node predicates.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventNode(@Nullable final String gridName,
        @Nullable final GridPredicate<? super GridRichNode>... p) {
        return isEmpty(p) || isAlwaysTrue(p) ? F.<GridEvent>alwaysTrue() : isAlwaysFalse(p) ? F.<GridEvent>alwaysFalse() :
            new GridPredicate<GridEvent>() {
                {
                    peerDeployLike(U.peerDeployAware0((Object[])p));
                }

                @Override public boolean apply(GridEvent e) {
                    assert e != null;

                    try {
                        Grid g = G.grid(gridName);

                        return g.node(e.nodeId(), p) != null;
                    }
                    catch (IllegalStateException ex) {
                        throw new GridRuntimeException("Invalid grid name: " + gridName, ex);
                    }
                }
            };
    }

    /**
     * Gets event predicate that returns {@code true} only if node that produced the event
     * is one of the given. Note that if array of provided nodes is {@code null} or empty
     * this method returns predicate that evaluates to {@code false} when applying.
     *
     * @param nodes Nodes.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> eventNode(@Nullable final Collection<? extends GridRichNode> nodes) {
        return isEmpty(nodes) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            {
                peerDeployLike(U.peerDeployAware0(nodes));
            }

            @Override public boolean apply(GridEvent e) {
                assert e != null;

                return !forAll(nodes, not(nodeForNodeId(e.nodeId())));
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if event is a cloud event with
     * cloud id and resource id equal to given.
     *
     * @param cloudId Cloud id.
     * @param rsrcId Cloud resource id.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> cloudEventResourceId(String cloudId, String rsrcId) {
        A.notNull(cloudId, "cloudId", rsrcId, "rsrcId");

        return cloudEventResourceId(cloudId, equalTo(rsrcId));
    }

    /**
     * Gets event predicate that returns {@code true} only if event is a cloud event with cloud
     * id equal to given value and command execution id is contained in the given ids. Note that
     * if array of provided command execution ids is {@code null} or empty this method returns
     * predicate that evaluates to {@code false} when applying.
     *
     * @param cloudId Cloud id.
     * @param ids Cloud command execution ids.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> cloudEventCommandId(String cloudId, @Nullable UUID... ids) {
        A.notNull(cloudId, "cloudId");

        return isEmpty(ids) ? F.<GridEvent>alwaysFalse() : cloudEventCommandId(cloudId, in(ids));
    }

    /**
     * Gets event predicate that returns {@code true} only if event is a cloud event with
     * cloud id equal to given value and resource id satisfied to given predicates.
     *
     * @param cloudId Cloud id.
     * @param p Resource id filtering predicates.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> cloudEventResourceId(final String cloudId,
        @Nullable final GridPredicate<? super String>... p) {
        A.notNull(cloudId, "cloudId");

        return isAlwaysFalse(p) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            {
                peerDeployLike(U.peerDeployAware0((Object[])p));
            }

            @Override public boolean apply(GridEvent e) {
                assert e != null;

                if (!(e instanceof GridCloudEvent)) {
                    return false;
                }

                GridCloudEvent evt = (GridCloudEvent)e;

                return eq(evt.cloudId(), cloudId) && isAll(evt.resourceId(), p);
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if event is a cloud event with
     * cloud id equal to given value and resource type is one of the given types. Note
     * that if array of provided event types is {@code null} or empty this method returns
     * predicate that evaluates to {@code false} when applying.
     *
     * @param cloudId Cloud id.
     * @param types Cloud resource types.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> cloudEventResourceTypes(final String cloudId,
        @Nullable final int... types) {
        A.notNull(cloudId, "cloudId");

        return isEmpty(types) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent e) {
                assert e != null;

                if (!(e instanceof GridCloudEvent)) {
                    return false;
                }

                GridCloudEvent evt = (GridCloudEvent)e;

                if (!eq(evt.cloudId(), cloudId)) {
                    return false;
                }

                assert types != null;

                for (int t : types) {
                    if (evt.resourceType() == t) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    /**
     * Gets event predicate that returns {@code true} only if event is a cloud event
     * with cloud id equal to given value and command execution id satisfies to given
     * predicates.
     *
     * @param cloudId Cloud id.
     * @param p Command execution id filtering predicates.
     * @return Event predicate.
     */
    public static GridPredicate<GridEvent> cloudEventCommandId(final String cloudId,
        @Nullable final GridPredicate<? super UUID>... p) {
        A.notNull(cloudId, "cloudId");

        return isAlwaysFalse(p) ? F.<GridEvent>alwaysFalse() : new GridPredicate<GridEvent>() {
            {
                peerDeployLike(U.peerDeployAware0((Object[])p));
            }

            @Override public boolean apply(GridEvent e) {
                assert e != null;

                if (!(e instanceof GridCloudEvent)) {
                    return false;
                }

                GridCloudEvent evt = (GridCloudEvent)e;

                return eq(evt.cloudId(), cloudId) && isAll(evt.executionId(), p);
            }
        };
    }

    /**
     * Shortcut method that creates an instance of {@link GridClosureException}.
     *
     * @param e Exception to wrap.
     * @return Newly created instance of {@link GridClosureException}.
     */
    public static GridClosureException wrap(Throwable e) {
        return new GridClosureException(e);
    }

    /**
     * Checks if two collections passed in intersect.
     *
     * @param <E> Element type.
     * @param s1 Set1.
     * @param s2 Set2.
     * @return {@code True} if there is an intersection, {@code false} otherwise.
     */
    public static <E> boolean intersects(Iterable<E> s1, Collection<E>... s2) {
        for (E e1 : s1) {
            for (Collection<E> s : s2) {
                if (s.contains(e1)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Waits until all passed futures will be executed.
     *
     * @param futs Futures. If none provided - this method is no-op.
     * @throws GridException If any of the futures failed.
     */
    public static <T> void awaitAll(@Nullable GridFuture<T>... futs) throws GridException {
        if (!isEmpty(futs)) {
            awaitAll(asList(futs));
        }
    }

    /**
     * Waits until all passed futures will be executed.
     *
     * @param futs Futures. If none provided - this method is no-op.
     * @throws GridException If any of the futures failed.
     */
    public static <T> void awaitAll(@Nullable Collection<GridFuture<T>> futs) throws GridException {
        awaitAll(0, null, futs);
    }

    /**
     * Waits until all passed futures will be executed.
     *
     * @param timeout Timeout for waiting ({@code 0} for forever).
     * @param futs Futures. If none provided - this method is no-op.
     * @throws GridException If any of the futures failed.
     */
    public static <T> void awaitAll(long timeout, @Nullable Collection<GridFuture<T>> futs) throws GridException {
        awaitAll(timeout, null, futs);
    }

    /**
     * Awaits for all futures to complete and optionally reduces all results into one.
     *
     * @param timeout Timeout for waiting ({@code 0} for forever).
     * @param rdc Optional reducer. If not {@code null}, then results will be reduced into one.
     * @param futs List of futures to wait for.
     * @param <T> Return type of the futures.
     * @param <R> Return type of the reducer.
     * @return Reduced result if reducer is provided, {@code null} otherwise.
     * @throws GridException If any of the futures failed.
     */
    @Nullable public static <T, R> R awaitAll(long timeout, @Nullable GridReducer<T, R> rdc,
        @Nullable Collection<GridFuture<T>> futs) throws GridException {
        if (futs == null || futs.isEmpty()) {
            return null;
        }

        long end = timeout == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;

        // Overflow.
        if (end < 0) {
            end = Long.MAX_VALUE;
        }

        // Note that it is important to wait in the natural order of collection and
        // not via listen method, because caller may actually add to this collection
        // concurrently while this method is in progress.
        for (GridFuture<T> fut : futs) {
            T t;

            if (timeout > 0) {
                long left = end - System.currentTimeMillis();

                if (end <= 0 && !fut.isDone()) {
                    throw new GridFutureTimeoutException("Timed out waiting for all futures: " + futs);
                }

                t = fut.get(left);
            }
            else {
                t = fut.get();
            }

            if (rdc != null) {
                rdc.collect(t);
            }
        }

        return rdc == null ? null : rdc.apply();
    }

    /**
     * Waits for one completed future from passed and returns it.
     *
     * @param futs Futures. If none provided - this method return completed future
     *      with {@code null} value.
     * @param <T> Type of computation result.
     * @return Completed future.
     */
    public static <T> GridFuture<T> awaitOne(GridFuture<T>... futs) {
        return isEmpty(futs) ? new GridFinishedFutureEx<T>() : awaitOne(asList(futs));
    }

    /**
     * Waits for the first completed future from passed and returns it.
     *
     * @param futs Futures.
     * @param <T> Type of computation result.
     * @return Completed future.
     */
    public static <T> GridFuture<T> awaitOne(Iterable<GridFuture<T>> futs) {
        if (F.isEmpty(futs))
            return new GridFinishedFutureEx<T>();

        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicReference<GridFuture<T>> t = new AtomicReference<GridFuture<T>>();

        GridInClosure<GridFuture<T>> c = null;

        for (GridFuture<T> fut : futs)
            if (fut != null) {
                if (!fut.isDone()) {
                    if (c == null) {
                        c = new CI1<GridFuture<T>>() {
                            @Override public void apply(GridFuture<T> e) {
                                t.compareAndSet(null, e);

                                latch.countDown();
                            }
                        };
                    }

                    fut.listenAsync(c);
                }
                else {
                    return fut;
                }
            }

        while (latch.getCount() > 0)
            try {
                latch.await();
            }
            catch (InterruptedException ignore) {
                // Ignore.
            }

        return t.get();
    }

    /**
     * Returns predicate for filtering finished futures.
     *
     * @return Predicate for filtering finished futures.
     */
    public static GridPredicate<GridFuture<?>> finishedFutures() {
        return FINISHED_FUTURE;
    }

    /**
     * Returns predicate for filtering unfinished futures.
     *
     * @return Predicate for filtering unfinished futures.
     */
    public static GridPredicate<GridFuture<?>> unfinishedFutures() {
        return UNFINISHED_FUTURE;
    }
}
