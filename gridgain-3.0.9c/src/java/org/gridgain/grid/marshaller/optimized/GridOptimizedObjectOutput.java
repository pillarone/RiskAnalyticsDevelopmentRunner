package org.gridgain.grid.marshaller.optimized;

import org.gridgain.grid.marshaller.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * This class is an extension of {@link ObjectOutputStream}. It's able to serialize non-serializable objects.
 * It performs optimization. It considers {@link GridMarshallerController}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridOptimizedObjectOutput extends ObjectOutputStream {
    /** Whether or not to require an object to be serializable in order to be serialized. */
    private final boolean requireSer;

    /** */
    private final Map<String, Integer> name2id;

    /** */
    private Queue<GridOptimizedWrapper> wrappers;

    /**
     * Constructs a GridOptimizedObjectOutput.
     *
     * @param out An output stream to write to.
     * @param requireSer Flag to enforce {@link Serializable}.
     * @param name2id User preregistered class names.
     * @throws IOException If an I/O error occurs while writing stream header.
     */
    GridOptimizedObjectOutput(OutputStream out, boolean requireSer, Map<String, Integer> name2id)
        throws IOException {
        super(out);

        this.requireSer = requireSer;

        this.name2id = name2id;

        enableReplaceObject(true);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Object replaceObject(Object obj) throws IOException {
        if (obj == null || GridMarshallerController.isExcluded(obj.getClass()))
            return null;

        if (requireSer || obj instanceof Serializable)
            return obj;

        return new GridOptimizedWrapper(obj);
    }

    /** {@inheritDoc} */
    @Override protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        writeClass(desc.forClass());
    }

    /**
     * @param cls Class.
     * @throws IOException If an I/O error occurs while writing stream header.
     */
    void writeClass(Class cls) throws IOException {
        GridOptimizedClassResolver.writeClass(this, cls, name2id);
    }

    /**
     * Adds a wrapper for delayed processing.
     *
     * @param wrapper Wrapper to add.
     */
    void delay(GridOptimizedWrapper wrapper) {
        if (wrappers == null)
            wrappers = new LinkedList<GridOptimizedWrapper>();

        // Add to FIFO queue.
        wrappers.offer(wrapper);
    }

    /**
     * Dequeues all previously enqueued wrappers and writes them to the stream.
     *
     * @throws IOException If failed.
     */
    void delayedWrite() throws IOException {
        if (wrappers != null)
            for (GridOptimizedWrapper w = wrappers.poll(); w != null; w = wrappers.poll())
                w.delayedWriteExternal(this);

        wrappers = null; // GC.
    }
}
