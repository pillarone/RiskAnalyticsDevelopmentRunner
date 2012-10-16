// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import com.sun.grizzly.util.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Default key affinity mapper. If key class has annotation {@link GridCacheAffinityMapped},
 * then the value of annotated method or field will be used to get affinity value instead
 * of the key itself. If there is no annotation, then the key is used as is.
 * <p>
 * Convenience affinity key adapter, {@link GridCacheAffinityKey} can be used in
 * conjunction with this mapper to automatically provide custom affinity keys for cache keys.
 * <p>
 * If non-default affinity mapper is used, is should be provided via
 * {@link GridCacheConfiguration#getAffinityMapper()} configuration property. 
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheDefaultAffinityMapper<K> implements GridCacheAffinityMapper<K> {
    /** Weak fields cache. If class is GC'ed, then it will be removed from this cache. */
    private final ConcurrentMap<Class<?>, AtomicReference<Field>> fields =
        new ConcurrentWeakHashMap<Class<?>, AtomicReference<Field>>();

    /** Weak methods cache. If class is GC'ed, then it will be removed from this cache. */
    private final ConcurrentMap<Class<?>, AtomicReference<Method>> mtds =
        new ConcurrentWeakHashMap<Class<?>, AtomicReference<Method>>();

    /** Logger. */
    @GridLoggerResource
    private GridLogger log;

    /**
     * If key class has annotation {@link GridCacheAffinityMapped},
     * then the value of annotated method or field will be used to get affinity value instead
     * of the key itself. If there is no annotation, then the key is returned as is.
     *
     * @param key Key to get affinity key for.
     * @return Affinity key for given key.
     */
    @Override public Object affinityKey(K key) {
        GridArgumentCheck.notNull(key, "key");

        Field f = field(key.getClass());

        if (f != null) {
            try {
                return f.get(key);
            }
            catch (IllegalAccessException e) {
                U.error(log, "Failed to access affinity field for key [field=" + f + ", key=" + key + ']', e);
            }
        }
        else {
            Method m = method(key.getClass());

            if (m != null) {
                try {
                    return m.invoke(key);
                }
                catch (IllegalAccessException e) {
                    U.error(log, "Failed to invoke affinity method for key [mtd=" + m + ", key=" + key + ']', e);
                }
                catch (InvocationTargetException e) {
                    U.error(log, "Failed to invoke affinity method for key [mtd=" + m + ", key=" + key + ']', e);
                }
            }
        }

        return key;
    }

    /**
     * Gets fields annotated with {@link GridCacheAffinityMapped} annotation.
     *
     * @param cls Class.
     * @return Annotated field. 
     */
    @Nullable private Field field(Class<?> cls) {
        AtomicReference<Field> field = fields.get(cls);

        if (field == null) {
            for (Class<?> c = cls; !c.equals(Object.class); c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    // Account for anonymous inner classes.
                    Annotation ann = f.getAnnotation(GridCacheAffinityMapped.class);

                    if (ann != null) {
                        f.setAccessible(true);

                        fields.putIfAbsent(cls, new AtomicReference<Field>(f));

                        return f;
                    }
                }
            }
            
            fields.putIfAbsent(cls, field = new AtomicReference<Field>());
        }

        return field.get();
    }


    /**
     * Gets method annotated with {@link GridCacheAffinityMapped} annotation.
     *
     * @param cls Class.
     * @return Annotated method.
     */
    @Nullable private Method method(Class<?> cls) {
        AtomicReference<Method> mtd = mtds.get(cls);

        if (mtd == null) {
            for (Class<?> c = cls; !c.equals(Object.class); c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    // Account for anonymous inner classes.
                    Annotation ann = m.getAnnotation(GridCacheAffinityMapped.class);

                    if (ann != null) {
                        if (!F.isEmpty(m.getParameterTypes())) {
                            throw new IllegalStateException("Method annotated with @GridCacheAffinityKey annotation " +
                                "cannot have parameters: " + mtd);
                        }

                        m.setAccessible(true);

                        mtds.putIfAbsent(cls, new AtomicReference<Method>(m));

                        return m;
                    }
                }
            }

            mtds.putIfAbsent(cls, mtd = new AtomicReference<Method>());
        }

        return mtd.get();
    }
}
