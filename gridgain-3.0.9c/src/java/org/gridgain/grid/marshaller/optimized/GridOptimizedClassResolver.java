package org.gridgain.grid.marshaller.optimized;

import com.sun.grizzly.util.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Resolves class names by serialVersionUID.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings( {"UnnecessaryFullyQualifiedName"})
class GridOptimizedClassResolver {
    /** File name to generate. */
    private static final String FILE_NAME = "optimized-classnames.properties";

    /** Class to serialVersionUID map */
    private static final ConcurrentMap<Class, Long> cls2uid = new ConcurrentWeakHashMap<Class, Long>();

    /** */
    private static final Map<String, Byte> ggxName2id = new HashMap<String, Byte>();

    /** */
    private static final Map<Byte, String> ggxId2name = new HashMap<Byte, String>();

    /** */
    private static final Map<String, Integer> ggName2id = new HashMap<String, Integer>();

    /** */
    private static final Map<Integer, String> ggId2name = new HashMap<Integer, String>();

    /** */
    private static final byte HEADER_NAME = (byte)255;

    /** */
    private static final byte HEADER_GG_NAME = (byte)254;

    /** */
    private static final byte HEADER_USER_NAME = (byte)253;

    /** */
    private static final byte HEADER_ARRAY = (byte)252;

    /**
     * Initialize predefined classes to optimize.
     */
    static {
        Class[] superOptCls = new Class[] {
            // Array types.
            byte[].class,
            short[].class,
            int[].class,
            long[].class,
            float[].class,
            double[].class,
            boolean[].class,
            char[].class,

            // Boxed types.
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Boolean.class,
            Character.class,
            String.class,

            // Atomic.
            AtomicBoolean.class,AtomicInteger.class,
            AtomicLong.class,AtomicReference.class,
            AtomicMarkableReference.class,
            AtomicStampedReference.class,
            AtomicIntegerArray.class,
            AtomicReferenceArray.class,

            // Concurrent types.
            ConcurrentHashMap.class,
            ConcurrentLinkedQueue.class,
            ConcurrentSkipListMap.class,
            ConcurrentSkipListSet.class,
            LinkedBlockingDeque.class,
            LinkedBlockingQueue.class,
            PriorityBlockingQueue.class,
            CopyOnWriteArrayList.class,
            CopyOnWriteArraySet.class,

            // Locks.
            ReentrantLock.class,
            ReentrantReadWriteLock.class,
            ReentrantReadWriteLock.ReadLock.class,
            ReentrantReadWriteLock.WriteLock.class,

            // Util types.
            Date.class,
            UUID.class,
            Calendar.class,
            Random.class,
            Calendar.class,
            Currency.class,
            ArrayList.class,
            LinkedList.class,
            Stack.class,
            Vector.class,
            HashMap.class,
            HashSet.class,
            Hashtable.class,
            TreeMap.class,
            TreeSet.class,
            IdentityHashMap.class,
            LinkedHashMap.class,
            LinkedHashSet.class,
            ArrayDeque.class,
            BitSet.class,
            EnumMap.class,
            EnumSet.class,

            // SQL types.
            java.sql.Date.class,
            Time.class,
            Timestamp.class,

            // Math types.
            BigDecimal.class,
            BigInteger.class,

            // GridGain types.
            GridUuid.class,
            GridOptimizedWrapper.class,
            GridBoundedConcurrentOrderedSet.class,
            GridBoundedLinkedHashSet.class,
            GridCollectionOpt.class,
            GridConcurrentHashSet.class,
            GridConcurrentLinkedQueue.class,
            GridConcurrentPhantomHashSet.class,
            GridConcurrentSkipListSet.class,
            GridConcurrentWeakHashSet.class,
            GridIdentityHashSet.class,
            GridLeanSet.class,
            GridSetWrapper.class
        };

        // Have to leave a range for special purposes.
        assert superOptCls.length < 230;

        for (int i = 0; i < superOptCls.length; i++) {
            Class cls = superOptCls[i];

            ggxName2id.put(cls.getName(), (byte)i);

            ggxId2name.put((byte)i, cls.getName());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(
            GridOptimizedClassResolver.class.getResourceAsStream(FILE_NAME),
            GridOptimizedUtils.UTF_8));

        try {
            for (int i = 0; ; i++) {
                String clsName = reader.readLine();

                if (clsName == null)
                    break;

                ggName2id.put(clsName, i);
                ggId2name.put(i, clsName);
            }
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
        finally {
            U.close(reader, null);
        }
    }

    /**
     * Ensure singleton.
     */
    private GridOptimizedClassResolver() {
        // No-op.
    }

    /**
     * @param cls A Class.
     * @return serialVersionUID.
     * @throws IOException If serial version UID failed. 
     */
    private static short shortClassId(Class cls) throws IOException {
        Long uid = cls2uid.get(cls);

        if (uid == null)
            cls2uid.putIfAbsent(cls, uid = GridOptimizedUtils.computeSerialVersionUid(cls));

        return uid.shortValue();
    }

    /**
     * @param in DataInput to read from.
     * @param clsLdr ClassLoader.
     * @param usrId2Name User preregistered class names.
     * @return Class read.
     * @throws IOException If serial version UID failed.
     * @throws ClassNotFoundException If the class cannot be located by the specified class loader.
     */
    static Class readClass(DataInput in, ClassLoader clsLdr, @Nullable Map<Integer, String> usrId2Name)
        throws IOException, ClassNotFoundException {
        assert in != null;
        assert clsLdr != null;
        assert usrId2Name != null;

        byte header = in.readByte();

        String name = ggxId2name.get(header);

        if (name != null)
            return Class.forName(name, true, clsLdr);

        if (header == HEADER_GG_NAME) {
            int id = in.readInt();

            name = ggId2name.get(id);

            if (name == null)
                throw new IOException("Failed to find optimized class ID " +
                    "(is same GridGain version running on all nodes?): " + id);
        }
        else if (header == HEADER_USER_NAME) {
            int id = in.readInt();

            name = usrId2Name.get(id);

            if (name == null)
                throw new IOException("Failed to find user defined class ID " +
                    "(make sure to register identical classes on all nodes for optimization): " + id);
        }
        else if (header == HEADER_ARRAY) {
            name = readClass(in, clsLdr, usrId2Name).getName();

            name = name.charAt(0) == '[' ? "[" + name : "[L" + name + ';';

            return Class.forName(name, true, clsLdr);
        }
        else if (header == HEADER_NAME) {
            byte[] nameBytes = new byte[in.readShort()];

            in.readFully(nameBytes);

            name = new String(nameBytes, GridOptimizedUtils.UTF_8);
        }
        else
            throw new IOException("Unexpected optimized stream header: " + header);

        // If we use aop we can receive primitive type name. Why?
        Class cls = primitive(name);

        if (cls == null)
            cls = Class.forName(name, true, clsLdr);

        short actual = shortClassId(cls);

        short expected = in.readShort();

        if (actual != expected)
            throw new IOException("Optimized stream class checksum mismatch [expected=" + expected +
                ", actual=" + actual + ", cls=" + cls + ']');

        return cls;
    }

    /**
     * @param out DataOutput,
     * @param cls Class to write.
     * @param usrName2Id User preregistered class names.
     * @throws IOException If serial version UID failed.
     */
    static void writeClass(DataOutput out, Class cls, Map<String, Integer> usrName2Id) throws IOException {
        assert usrName2Id != null;
        assert out != null;
        assert cls != null;

        String name = cls.getName();

        Byte superHeader = ggxName2id.get(name);

        if (superHeader != null) {
            out.write(superHeader);

            return;
        }

        Integer id;

        if ((id = ggName2id.get(name)) != null) {
            out.write(HEADER_GG_NAME);

            out.writeInt(id);
        }
        else if ((id = usrName2Id.get(name)) != null) {
            out.write(HEADER_USER_NAME);

            out.writeInt(id);
        }
        else if (cls.isArray()) {
            out.write(HEADER_ARRAY);

            writeClass(out, cls.getComponentType(), usrName2Id);

            return;
        }
        else {
            out.write(HEADER_NAME);

            byte[] bytes = name.getBytes(GridOptimizedUtils.UTF_8);

            out.writeShort(bytes.length);

            out.write(bytes);
        }

        out.writeShort(shortClassId(cls));
    }

    /**
     *
     * @param name Name of primitive class.
     * @return Primitive type class or null.
     */
    private static Class primitive(String name) {
        if ("byte".equals(name))
            return byte.class;

        if ("short".equals(name))
            return short.class;

        if ("int".equals(name))
            return int.class;

        if ("long".equals(name))
            return long.class;

        if ("char".equals(name))
            return char.class;

        if ("float".equals(name))
            return float.class;

        if ("double".equals(name))
            return double.class;

        if ("boolean".equals(name))
            return boolean.class;

        if ("void".equals(name))
            return void.class;

        return null;
    }
}
