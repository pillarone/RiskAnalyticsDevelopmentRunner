package org.gridgain.grid.marshaller.optimized;

import java.io.*;
import java.lang.reflect.*;

import static org.gridgain.grid.marshaller.optimized.GridOptimizedUtils.*;

/**
 * Responsible for serialization of non-serializable objects.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridOptimizedWrapper implements Externalizable {
    /** */
    private Object obj;

    /**
     * Default public constructor as required by {@link Externalizable}. 
     */
    public GridOptimizedWrapper() {
        // No-op.
    }

    /**
     * @param obj Non-serializable to serialize.
     */
    GridOptimizedWrapper(Object obj) {
        assert obj != null;
        assert !(obj instanceof Serializable);

        this.obj = obj;
    }

    /**
     * @return An object.
     */
    Object wrapped() {
        return obj;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        GridOptimizedObjectOutput ggout = (GridOptimizedObjectOutput)out;

        // Only write class without fields.
        ggout.writeClass(obj.getClass());

        ggout.delay(this);
    }

    /**
     * Writes non-serializable object to output.
     *
     * @param out Output.
     * @throws IOException If failed.
     */
    void delayedWriteExternal(GridOptimizedObjectOutput out) throws IOException {
        for (Field f : getFieldsForSerialization(obj.getClass())) {
            Object val = get(f, obj);

            if (f.getType().isPrimitive())
                writePrimitive(out, val);
            else
                out.writeObject(val);
        }
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridOptimizedObjectInput ggin = (GridOptimizedObjectInput)in;

        Class cls = ggin.readClass();

        try {
            // Create object, but do not populate fields.
            obj = forceNewInstance(cls);

            assert obj != null : "Failed to create new instance for class: " + cls;
        }
        catch (InvocationTargetException e) {
            throw new IOException("Failed to create new instance for class: " + cls, e);
        }
        catch (IllegalAccessException e) {
            throw new IOException("Failed to create new instance for class: " + cls, e);
        }
        catch (InstantiationException e) {
            throw new IOException("Failed to create new instance for class: " + cls, e);
        }

        ggin.delay(this);
    }

    /**
     * Delayed read processing.
     *
     * @param in Input.
     * @throws IOException If failed.
     * @throws ClassNotFoundException If class not found.
     */
    void delayedReadExternal(GridOptimizedObjectInput in) throws IOException, ClassNotFoundException {
        for (Field f : getFieldsForSerialization(obj.getClass())) {
            if (f.getType().isPrimitive())
                set(f, obj, readPrimitive(in, f.getType()));
            else
                set(f, obj, in.readObject());
        }
    }
}
