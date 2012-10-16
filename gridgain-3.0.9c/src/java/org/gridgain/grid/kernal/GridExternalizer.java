// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Efficiently serializes and deserializes known classes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridExternalizer {
    /** Byte to class lookup. */
    private static final Map<Byte, Class> byte2Cls = new HashMap<Byte, Class>();

    /** Class to byte lookup. */
    private static final Map<Class, Byte> cls2Byte = new HashMap<Class, Byte>();

    /** */
    private static final byte NULL_BIT = 0x01;

    /** */
    private static final byte CHECK_BIT = 0x02;

    static {
        byte idGen = Byte.MIN_VALUE;

        register(idGen++, UUID.class);
        register(idGen++, GridUuid.class);
        register(idGen++, ArrayList.class);
        register(idGen++, LinkedList.class);
        register(idGen++, HashMap.class);
        register(idGen++, HashSet.class);
        register(idGen++, LinkedHashMap.class);
        register(idGen++, LinkedHashSet.class);
        register(idGen++, GridCacheVersion.class);
        register(idGen++, GridCacheMvccCandidate.class);
        register(idGen++, GridCacheEntryInfo.class);
        register(idGen++, GridCacheMetricsAdapter.class);
        register(idGen++, GridNearGetRequest.class);
        register(idGen++, GridNearGetResponse.class);
        register(idGen++, GridDistributedLockRequest.class);
        register(idGen++, GridDistributedLockResponse.class);
        register(idGen++, GridDistributedTxFinishRequest.class);
        register(idGen++, GridDistributedTxFinishResponse.class);
        register(idGen++, GridDistributedTxPrepareRequest.class);
        register(idGen++, GridDistributedTxPrepareResponse.class);
        register(idGen++, GridReplicatedPreloadRequest.class);
        register(idGen++, GridReplicatedPreloadResponse.class);
        register(idGen++, GridNearTxFinishRequest.class);
        register(idGen++, GridNearTxFinishResponse.class);
        register(idGen++, GridNearTxPrepareRequest.class);
        register(idGen++, GridNearTxPrepareResponse.class);
        register(idGen++, GridDhtTxPrepareRequest.class);
        register(idGen++, GridDhtTxPrepareResponse.class);
        register(idGen++, GridDhtTxFinishRequest.class);
        register(idGen, GridDhtTxFinishResponse.class);

        assert idGen <= Byte.MAX_VALUE;
    }

    /**
     * @param id Class ID.
     * @param cls Class.
     */
    private static void register(byte id, Class cls) {
        byte2Cls.put(id, cls);
        cls2Byte.put(cls, id);
    }

    /**
     * Ensure singleton.
     */
    private GridExternalizer() {
        // No-op.
    }

    /**
     * Writes an object to output stream.
     *
     * @param out Output stream.
     * @param o Object to write.
     * @throws IOException If failed.
     */
    public static void write(ObjectOutput out, @Nullable Object o) throws IOException {
        byte mask = 0x00;

        set(mask, NULL_BIT, o == null);

        if (o == null) {
            out.writeByte(mask);

            return;
        }

        Byte type = cls2Byte.get(o.getClass());

        set(mask, CHECK_BIT, type != null);

        out.writeByte(mask);

        if (type != null) {
            out.writeByte(type);

            if (o instanceof UUID) {
                writeUuid(out, (UUID)o);
            }
            else if (o instanceof GridUuid) {
                writeGridUuid(out, (GridUuid)o);
            }
            else if (o instanceof Collection) {
                Collection<?> c = (Collection<?>)o;

                out.writeInt(c.size());

                for (Object e : c) {
                    write(out, e);
                }
            }
            else if (o instanceof Map) {
                Map<?, ?> m = (Map<?, ?>)o;

                out.writeInt(m.size());

                for (Map.Entry<?, ?> e : m.entrySet()) {
                    write(out, e.getKey());
                    write(out, e.getValue());
                }
            }
            else if (o instanceof Externalizable) {
                ((Externalizable)o).writeExternal(out);
            }
            else {
                assert false;
            }
        }
        else {
            out.writeObject(o);
        }
    }

    /**
     * Reads an object from input stream.
     *
     * @param in Input stream.
     * @param <T> Return type.
     * @return Deserialized object.
     * @throws IOException If failed.
     * @throws ClassNotFoundException If class not found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <T> T read(ObjectInput in) throws IOException, ClassNotFoundException {
        byte mask = in.readByte();

        if (isSet(mask, NULL_BIT)) {
            return null;
        }

        if (isSet(mask, CHECK_BIT)) {
            byte type = in.readByte();

            Class cls = byte2Cls.get(type);

            if (cls == null) {
                throw new ClassNotFoundException("Failed to find class for type: " + type);
            }

            try {
                if (cls == UUID.class) {
                    return (T)readUuid(in);
                }
                else if (cls == GridUuid.class) {
                    return (T)readGridUuid(in);
                }
                else if (Collection.class.isAssignableFrom(cls)) {
                    int size = in.readInt();

                    Collection c = (Collection<?>)(cls == LinkedList.class ?
                        cls.newInstance() : cls.getConstructor(int.class).newInstance(size));

                    for (int i = 0; i < size; i++) {
                        c.add(read(in));
                    }

                    return (T)c;
                }
                else if (Map.class.isAssignableFrom(cls)) {
                    int size = in.readInt();

                    Map m = (Map)cls.getConstructor(int.class).newInstance(size);

                    for (int i = 0; i < size; i++) {
                        m.put(read(in), read(in));
                    }

                    return (T)m;
                }
                else {
                    assert Externalizable.class.isAssignableFrom(cls);

                    Externalizable e = (Externalizable)cls.newInstance();

                    e.readExternal(in);

                    return (T)e;
                }
            }
            catch (InstantiationException e) {
                throw new IOException("Failed to instantiate class (does it have public empty constructor?): " + cls, e);
            }
            catch (IllegalAccessException e) {
                throw new IOException("Failed to instantiate class (does it have public empty constructor?): " + cls, e);
            }
            catch (NoSuchMethodException e) {
                throw new IOException("Failed to instantiate class (does it have public empty constructor?): " + cls, e);
            }
            catch (InvocationTargetException e) {
                throw new IOException("Failed to instantiate class (does it have public empty constructor?): " + cls, e);
            }
        }
        else {
            return (T)in.readObject();
        }
    }

    /**
     * Writes UUID to output stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param uid UUID to write.
     * @throws IOException If write failed.
     */
    private static void writeUuid(DataOutput out, UUID uid) throws IOException {
        out.writeLong(uid.getMostSignificantBits());
        out.writeLong(uid.getLeastSignificantBits());
    }

    /**
     * Reads UUID from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read UUID.
     * @throws IOException If read failed.
     */
    public static UUID readUuid(DataInput in) throws IOException {
        long most = in.readLong();
        long least = in.readLong();

        return new UUID(most, least);
    }

    /**
     * Writes {@link GridUuid} to output stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param uid UUID to write.
     * @throws IOException If write failed.
     */
    public static void writeGridUuid(DataOutput out, GridUuid uid) throws IOException {
        writeUuid(out, uid.globalId());

        out.writeLong(uid.localId());
    }

    /**
     * Reads {@link GridUuid} from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read UUID.
     * @throws IOException If read failed.
     */
    public static GridUuid readGridUuid(DataInput in) throws IOException {
        UUID globalId = readUuid(in);

        long locId = in.readLong();

        return new GridUuid(globalId, locId);
    }

    /**
     * @param mask Mask.
     * @param bit Bit.
     * @param on Mask to set.
     * @return Updated flags.
     */
    private static byte set(byte mask, byte bit, boolean on) {
        return (byte)(on ? mask | bit : mask & ~bit);
    }

    /**
     * @param mask Mask.
     * @param bit Bit.
     * @return {@code True} if mask is set.
     */
    private static boolean isSet(byte mask, byte bit) {
        return (mask & bit) == bit;
    }
}
