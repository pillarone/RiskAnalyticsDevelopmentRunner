package org.gridgain.grid.marshaller.optimized;

import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * This class is an extension of {@link ObjectInputStream} compatible with {@link GridOptimizedObjectOutput}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridOptimizedObjectInput extends ObjectInputStream {
    /** */
    private final ClassLoader clsLdr;

    /** */
    @Nullable private final Map<Integer, String> userPreregisteredId2Name;

    /** Wrappers queued for delayed processing. */
    private Queue<GridOptimizedWrapper> wrappers;

    /**
     * @param in Parent input stream.
     * @param clsLdr Custom class loader.
     * @param userPreregisteredId2Name User preregistered class names.
     * @throws IOException If initialization failed.
     */
    GridOptimizedObjectInput(InputStream in, ClassLoader clsLdr,
        @Nullable Map<Integer, String> userPreregisteredId2Name) throws IOException {
        super(in);

        assert clsLdr != null;
        
        this.clsLdr = clsLdr;

        this.userPreregisteredId2Name = userPreregisteredId2Name;

        enableResolveObject(true);
    }

    /** {@inheritDoc} */
    @Override protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        return desc.forClass();
    }

    /** {@inheritDoc} */
    @Override protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        // We must use lookupAny because not all classes can be Serializable.
        return ObjectStreamClass.lookupAny(readClass());
    }

    /**
     * @return Class read.
     * @throws ClassNotFoundException If the class cannot be located.
     * @throws IOException If an I/O error occurs while writing stream header.
     */
    Class readClass() throws ClassNotFoundException, IOException {
        return GridOptimizedClassResolver.readClass(this, clsLdr, userPreregisteredId2Name);
    }

    /** {@inheritDoc} */
    @Override protected Object resolveObject(Object obj) throws IOException {
        if (obj instanceof GridOptimizedWrapper) {
            GridOptimizedWrapper wrapper = (GridOptimizedWrapper)obj;

            return wrapper.wrapped();
        }
        else
            return obj;
    }

    /**
     * Enqueues wrapper for delayed processing.
     *
     * @param wrapper Wrapper to enqueue.
     */
    void delay(GridOptimizedWrapper wrapper) {
        if (wrappers == null)
            wrappers = new LinkedList<GridOptimizedWrapper>();

        wrappers.offer(wrapper);
    }

    /**
     * Delayed post processing of optimized wrappers.
     *
     * @throws IOException If failed.
     * @throws ClassNotFoundException If failed.
     */
    void delayedRead() throws IOException, ClassNotFoundException {
        if (wrappers != null)
            for (GridOptimizedWrapper w = wrappers.poll(); w != null; w = wrappers.poll())
                w.delayedReadExternal(this);

        wrappers = null; // GC.
    }
}
