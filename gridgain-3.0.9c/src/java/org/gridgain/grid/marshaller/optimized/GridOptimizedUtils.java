package org.gridgain.grid.marshaller.optimized;

import sun.reflect.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/**
 * Miscellaneous utility methods to facilitate {@link GridOptimizedMarshaller}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridOptimizedUtils {
    /** UTF-8 character name. */
    static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Whether constructor for serialization is available. */
    static final boolean SERIALIZATION_CONSTRUCTOR_AVAILABLE;

    /**
     * Compares fields by name.
     */
    private static final Comparator<Field> FIELD_NAME_COMPARATOR = new Comparator<Field>() {
        @Override public int compare(Field f1, Field f2) {
            return f1.getName().compareTo(f2.getName());
        }
    };

    /**
     * {@link Object} default constructor.
     */
    private static final Constructor<Object> OBJECT_CONSTRUCTOR;

    /**
     *
     */
    static {
        try {
            OBJECT_CONSTRUCTOR = Object.class.getConstructor();
        }
        catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }

        SERIALIZATION_CONSTRUCTOR_AVAILABLE = serializationConstructorAvailable();
    }

    /**
     * Suppresses default constructor, ensuring non-instantiability.
     */
    private GridOptimizedUtils() {
        // No-op.
    }

    /**
     * Throws throwable if it's not checked.
     *
     * @param t A throwable to examine.
     */
    public static void rethrowNotChecked(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException)t;
        }

        if (t instanceof Error) {
            throw (Error)t;
        }
    }

    /**
     * @return Whether constructor for serialization is available.
     */
    private static boolean serializationConstructorAvailable() {
        try {
            /**
             * A sample of non-serializable class that has no default constructor.
             */
            class NoConstructor {
                @SuppressWarnings( {"UnusedDeclaration"}) NoConstructor(int arg) {
                    // No-op.
                }
            }

            forceNewInstance(NoConstructor.class);

            return true;
        }
        catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * Constructs an instance even if the corresponding class doesn't have a default constructor.
     *
     * @param cls A class to instantiate.
     * @return A newly allocated instance.
     * @throws InvocationTargetException Something went wrong while instantiating.
     * @throws IllegalAccessException Something went wrong while instantiating.
     * @throws InstantiationException Something went wrong while instantiating.
     */
    static Object forceNewInstance(Class cls) throws InvocationTargetException, IllegalAccessException,
        InstantiationException {
        return ReflectionFactory.getReflectionFactory().newConstructorForSerialization(cls,
            OBJECT_CONSTRUCTOR).newInstance();
    }

    /**
     * Returns fields to use in a serialization/deserialization process.
     *
     * @param cls A class to examine.
     * @return All fields in a deterministic order that are not static and not transient.
     */
    static List<Field> getFieldsForSerialization(Class cls) {
        List<Field> allFields = new LinkedList<Field>();

        while (cls != null && !cls.equals(Object.class)) {
            List<Field> fields = new LinkedList<Field>();

            for (Field f : cls.getDeclaredFields()) {
                int modifiers = f.getModifiers();

                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    f.setAccessible(true);

                    fields.add(f);
                }
            }

            Collections.sort(fields, FIELD_NAME_COMPARATOR);

            allFields.addAll(fields);

            // If cls an interface, a primitive type, or void, then getSuperclass returns null.
            cls = cls.getSuperclass();
        }

        return allFields;
    }

    /**
     * Writes the primitive value to the stream.
     *
     * @param out   A stream to write to.
     * @param value A value to write.
     * @throws IOException If an I/O error occurs.
     */
    static void writePrimitive(DataOutput out, Object value) throws IOException {
        if (value instanceof Byte)
            out.writeByte((Byte)value);
        else if (value instanceof Short)
            out.writeShort((Short)value);
        else if (value instanceof Integer)
            out.writeInt((Integer)value);
        else if (value instanceof Long)
            out.writeLong((Long)value);
        else if (value instanceof Float)
            out.writeFloat((Float)value);
        else if (value instanceof Double)
            out.writeDouble((Double)value);
        else if (value instanceof Boolean)
            out.writeBoolean((Boolean)value);
        else if (value instanceof Character)
            out.writeChar((Character)value);
        else
            throw new IllegalArgumentException();
    }

    /**
     * Reads a primitive value of the specified class type from the stream.
     *
     * @param in  A stream to read from.
     * @param cls A class type of the primitive.
     * @return A primitive.
     * @throws IOException If an I/O error occurs.
     */
    static Object readPrimitive(DataInput in, Class cls) throws IOException {
        if (cls == byte.class)
            return in.readByte();

        if (cls == short.class)
            return in.readShort();

        if (cls == int.class)
            return in.readInt();

        if (cls == long.class)
            return in.readLong();

        if (cls == float.class)
            return in.readFloat();

        if (cls == double.class)
            return in.readDouble();

        if (cls == boolean.class)
            return in.readBoolean();

        if (cls == char.class)
            return in.readChar();

        throw new IllegalArgumentException();
    }

    /**
     * Computes the serial version UID value for the given class.
     * The code is taken from {@link ObjectStreamClass#computeDefaultSUID(Class)}.
     *
     * @param cls A class.
     * @return A serial version UID.
     * @throws IOException If failed.
     */
    static long computeSerialVersionUid(Class cls) throws IOException {
        if (Serializable.class.isAssignableFrom(cls) && !Enum.class.isAssignableFrom(cls)) {
            return ObjectStreamClass.lookup(cls).getSerialVersionUID();
        }

        MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to get digest for SHA.", e);
        }

        md.update(cls.getName().getBytes(UTF_8));

        for (Field f : getFieldsForSerialization(cls)) {
            md.update(f.getName().getBytes(UTF_8));
            md.update(f.getType().getName().getBytes(UTF_8));
        }

        byte[] hashBytes = md.digest();

        long hash = 0;

        // Composes a single-long hash from the byte[] hash.
        for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--)
            hash = (hash << 8) | (hashBytes[i] & 0xFF);

        return hash;
    }

    /**
     * Delegates to {@link Field#get(Object)} hiding IllegalAccessException.
     *
     * @param f A field.
     * @param obj An object.
     * @return The value of the represented field in object.
     * @throws IOException If failed.
     */
    static Object get(Field f, Object obj) throws IOException {
        try {
            return f.get(obj);
        }
        catch (IllegalAccessException e) {
            throw new IOException("Failed to get field value: " + f, e);
        }
    }

    /**
     * Delegates to {@link Field#set(Object,Object)} hiding IllegalAccessException.
     *
     * @param f A field.
     * @param obj An object.
     * @param value A value.
     * @throws IOException If failed.
     */
    static void set(Field f, Object obj, Object value) throws IOException {
        try {
            f.set(obj, value);
        }
        catch (IllegalAccessException e) {
            throw new IOException("Failed to set field value: " + f, e);
        }
    }
}
