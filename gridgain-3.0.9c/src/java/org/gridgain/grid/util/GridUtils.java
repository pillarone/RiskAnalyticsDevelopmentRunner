// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.mail.inbox.*;
import org.gridgain.grid.util.mbean.*;
import org.gridgain.grid.util.worker.*;
import org.gridgain.jsr305.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.core.io.*;
import sun.misc.*;

import javax.mail.*;
import javax.mail.Authenticator;
import javax.mail.internet.*;
import javax.management.*;
import javax.naming.*;
import javax.net.ssl.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.management.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.security.*;
import java.security.cert.*;
import java.sql.*;
import java.text.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;

import static org.gridgain.grid.GridSystemProperties.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;

/**
 * Collection of utility methods used throughout the system.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"UnusedReturnValue", "UnnecessaryFullyQualifiedName"})
public abstract class GridUtils {
    /** Sun-specific JDK constructor factory for objects that don't have empty constructor. */
    private static final Method CTOR_FACTORY;

    /** Sun JDK reflection factory. */
    private static final Object SUN_REFLECT_FACTORY;

    /** Public {@code java.lang.Object} no-argument constructor. */
    private static final Constructor OBJECT_CTOR;

    /** All grid event names. */
    private static final Map<Integer, String> GRID_EVT_NAMES = new HashMap<Integer, String>();

    /** All grid events. */
    private static final int[] GRID_EVTS;

    /** Empty integers array. */
    private static final int[] EMPTY_INTS = new int[0];

    /** Empty  longs. */
    private static final long[] EMPTY_LONGS = new long[0];

    /** System line separator. */
    private static final String NL = System.getProperty("line.separator");

    /** Default initial buffer size for the {@link GridByteArrayOutputStream}. */
    public static final int DFLT_BUFFER_SIZE = 512;

    /** Path to {@code gridgain.xml} file. */
    public static final String GRIDGAIN_XML_PATH = "META-INF/gridgain.xml";

    /** Default user version. */
    public static final String DFLT_USER_VERSION = "0";

    /** System class loader user version. */
    private static final AtomicReference<String> SYS_LDR_VER = new AtomicReference<String>(null);

    /** Cache for {@link GridPeerDeployAware} fields to speed up reflection. */
    private static final ConcurrentMap<String, GridTuple2<Class<?>, Collection<Field>>> p2pFields =
        new ConcurrentHashMap<String, GridTuple2<Class<?>, Collection<Field>>>();

    /** Secure socket protocol to use. */
    private static final String HTTPS_PROTOCOL = "TLS";

    /** OS JDK string. */
    private static String osJdkStr;

    /** OS string. */
    private static String osStr;

    /** JDK string. */
    private static String jdkStr;

    /** Indicates whether current OS is Windows 95. */
    private static boolean win95;

    /** Indicates whether current OS is Windows 98. */
    private static boolean win98;

    /** Indicates whether current OS is Windows NT. */
    private static boolean winNt;

    /** Indicates whether current OS is Windows Vista. */
    private static boolean winVista;

    /** Indicates whether current OS is Windows 7. */
    private static boolean win7;

    /** Indicates whether current OS is Windows 2000. */
    private static boolean win2k;

    /** Indicates whether current OS is Windows XP. */
    private static boolean winXp;

    /** Indicates whether current OS is Windows Server 2003. */
    private static boolean win2003;

    /** Indicates whether current OS is UNIX flavor. */
    private static boolean unix;

    /** Indicates whether current OS is Solaris. */
    private static boolean solaris;

    /** Indicates whether current OS is Linux flavor. */
    private static boolean linux;

    /** Indicates whether current OS is NetWare. */
    private static boolean netware;

    /** Indicates whether current OS is Mac OS. */
    private static boolean mac;

    /** Indicates whether current OS architecture is Sun Sparc. */
    private static boolean sparc;

    /** Indicates whether current OS architecture is Intel X86. */
    private static boolean x86;

    /** Name of the underlying OS. */
    private static String osName;

    /** Version of the underlying OS. */
    private static String osVer;

    /** CPU architecture of the underlying OS. */
    private static String osArch;

    /** Name of the Java Runtime. */
    private static String javaRtName;

    /** Name of the Java Runtime version. */
    private static String javaRtVer;

    /** Name of the JDK vendor. */
    private static String jdkVendor;

    /** Name of the JDK. */
    private static String jdkName;

    /** Version of the JDK. */
    private static String jdkVer;

    /** Name of JVM specification. */
    private static String jvmSpecName;

    /** Version of JVM implementation. */
    private static String jvmImplVer;

    /** Vendor's name of JVM implementation. */
    private static String jvmImplVendor;

    /** Name of the JVM implementation. */
    private static String jvmImplName;

    /** JMX domain as 'xxx.gridgain'. */
    public static final String JMX_DOMAIN = GridUtils.class.getName().substring(0, GridUtils.class.getName().
        indexOf('.', GridUtils.class.getName().indexOf('.') + 1));

    /** Default buffer size = 4K. */
    private static final int BUF_SIZE = 4096;

    /** Byte bit-mask. */
    private static final int MASK = 0xf;

    /** Long date format pattern for log messages. */
    private static final SimpleDateFormat LONG_DATE_FMT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    /**
     * Short date format pattern for log messages in "quiet" mode.
     * Only time is included since we don't expect "quiet" mode to be used
     * for longer runs.
     */
    private static final SimpleDateFormat SHORT_DATE_FMT = new SimpleDateFormat("HH:mm:ss");

    /** Debug date format. */
    private static final SimpleDateFormat DEBUG_DATE_FMT = new SimpleDateFormat("HH:mm:ss,SS");

    /** Cached local host address to make sure that every time the same local host is returned. */
    private static InetAddress localHost;

    /** */
    private static boolean ent;

    /**
     * Initializes enterprise check.
     */
    static {
        try {
            Class.forName("org.gridgain.grid.util.GridEnterpriseCrumb");

            ent = true;
        }
        catch (ClassNotFoundException ignore) {
            ent = false;
        }

        String osName = System.getProperty("os.name");

        String osLow = osName.toLowerCase();

        // OS type detection.
        if (osLow.contains("win")) {
            if (osLow.contains("95"))
                win95 = true;
            else if (osLow.contains("98"))
                win98 = true;
            else if (osLow.contains("nt"))
                winNt = true;
            else if (osLow.contains("2000"))
                win2k = true;
            else if (osLow.contains("vista"))
                winVista = true;
            else if (osLow.contains("xp"))
                winXp = true;
            else if (osLow.contains("2003"))
                win2003 = true;
            else if (osLow.contains("7"))
                win7 = true;
        }
        else if (osLow.contains("netware"))
            netware = true;
        else if (osLow.contains("mac os"))
            mac = true;
        else {
            // UNIXs flavors tokens.
            for (CharSequence os : new String[]{"ix", "inux", "olaris", "un", "ux", "sco", "bsd", "att"})
                if (osLow.contains(os)) {
                    unix = true;

                    break;
                }

            // UNIX name detection.
            if (osLow.contains("olaris"))
                solaris = true;
            else if (osLow.contains("inux"))
                linux = true;
        }

        String osArch = System.getProperty("os.arch");

        String archStr = osArch.toLowerCase();

        // OS architecture detection.
        if (archStr.contains("x86"))
            x86 = true;
        else if (archStr.contains("sparc"))
            sparc = true;

        String javaRtName = System.getProperty("java.runtime.name");
        String javaRtVer = System.getProperty("java.runtime.version");
        String jdkVendor = System.getProperty("java.specification.vendor");
        String jdkName = System.getProperty("java.specification.name");
        String jdkVer = System.getProperty("java.specification.version");
        String osVer = System.getProperty("os.version");
        String jvmSpecName = System.getProperty("java.vm.specification.name");
        String jvmImplVer = System.getProperty("java.vm.version");
        String jvmImplVendor = System.getProperty("java.vm.vendor");
        String jvmImplName = System.getProperty("java.vm.name");

        String jdkStr = javaRtName + ' ' + javaRtVer + ' ' + jvmImplVendor + ' ' + jvmImplName + ' ' +
            jvmImplVer;

        osStr = osName + ' ' + osVer + ' ' + osArch;
        osJdkStr = osLow + ", " + jdkStr;

        // Copy auto variables to static ones.
        GridUtils.osName = osName;
        GridUtils.jdkName = jdkName;
        GridUtils.jdkVendor = jdkVendor;
        GridUtils.jdkVer = jdkVer;
        GridUtils.jdkStr = jdkStr;
        GridUtils.osVer = osVer;
        GridUtils.osArch = osArch;
        GridUtils.jvmSpecName = jvmSpecName;
        GridUtils.jvmImplVer = jvmImplVer;
        GridUtils.jvmImplVendor = jvmImplVendor;
        GridUtils.jvmImplName = jvmImplName;
        GridUtils.javaRtName = javaRtName;
        GridUtils.javaRtVer = javaRtVer;

        try {
            OBJECT_CTOR = Object.class.getConstructor();
        }
        catch (NoSuchMethodException e) {
            throw withCause(new AssertionError("Object class does not have empty constructor (is JDK corrupted?)."), e);
        }

        // Constructor factory.
        Method ctorFac = null;
        Object refFac = null;

        try {
            Class refFactoryCls = Class.forName("sun.reflect.ReflectionFactory");

            refFac = refFactoryCls.getMethod("getReflectionFactory").invoke(null);

            ctorFac = refFac.getClass().getMethod("newConstructorForSerialization", Class.class,
                Constructor.class);
        }
        catch (NoSuchMethodException ignored) {
            // No-op.
        }
        catch (InvocationTargetException ignored) {
            // No-op.
        }
        catch (IllegalAccessException ignored) {
            // No-op.
        }
        catch (ClassNotFoundException ignored) {
            // No-op.
        }

        CTOR_FACTORY = ctorFac;
        SUN_REFLECT_FACTORY = refFac;

        // Event names initialization.
        for (Field field : GridEventType.class.getFields()) {
            if (field.getType().equals(int.class)) {
                try {
                    int type = field.getInt(null);

                    String prev = GRID_EVT_NAMES.put(type, field.getName());

                    // Check for duplicate event types.
                    assert prev == null : "Duplicate event [type=" + type + ", name1=" + prev +
                        ", name2=" + field.getName() + ']';
                }
                catch (IllegalAccessException e) {
                    throw new GridRuntimeException(e);
                }
            }
        }

        // Event array initialization.
        GRID_EVTS = toIntArray(GRID_EVT_NAMES.keySet());

        assert GRID_EVTS.length == GridEventType.EVTS_ALL.length;

        // Sort for fast event lookup.
        Arrays.sort(GRID_EVTS);
    }

    /**
     * Return SUN specific constructor factory.
     *
     * @return SUN specific constructor factory.
     */
    @Nullable public static Method ctorFactory() {
        return CTOR_FACTORY;
    }

    /**
     * @return Empty constructor for object class.
     */
    public static Constructor objectConstructor() {
        return OBJECT_CTOR;
    }

    /**
     * SUN JDK specific reflection factory for objects without public constructor.
     *
     * @return Reflection factory for objects without public constructor.
     */
    @Nullable public static Object sunReflectionFactory() {
        return SUN_REFLECT_FACTORY;
    }

    /**
     * Gets name for given grid event type.
     *
     * @param type Event type.
     * @return Event name.
     */
    public static String gridEventName(int type) {
        String name = GRID_EVT_NAMES.get(type);

        return name != null ? name : Integer.toString(type);
    }

    /**
     * Gets all event types.
     *
     * @param excl Optional exclude events.
     * @return All events minus excluded ones.
     */
    public static int[] gridEvents(final int... excl) {
        if (F.isEmpty(excl))
            return GRID_EVTS;

        List<Integer> evts = toIntList(GRID_EVTS, new P1<Integer>() {
            @Override public boolean apply(Integer i) {
                return !containsIntArray(excl, i);
            }
        });

        return toIntArray(evts);
    }

    /**
     * This method should be used for adding quick debug statements in code
     * while debugging. Calls to this method should never be committed to trunk.
     *
     * @param msg Message to debug.
     */
    public static void debug(String msg) {
        X.println('<' + DEBUG_DATE_FMT.format(new Date(System.currentTimeMillis())) + "><DEBUG><" +
            Thread.currentThread().getName() + "> " + msg);
    }

    /**
     * This method should be used for adding quick debug statements in code
     * while debugging. Calls to this method should never be committed to trunk.
     *
     * @param log Logger.
     * @param msg Message to debug.
     */
    public static void debug(GridLogger log, String msg) {
        log.info(msg);
    }

    /**
     *
     * @param smtpHost SMTP host.
     * @param smtpPort SMTP port.
     * @param ssl SMTP SSL.
     * @param startTls Start TLS flag.
     * @param username Email authentication user name.
     * @param pwd Email authentication password.
     * @param from From email.
     * @param subj Email subject.
     * @param body Email body.
     * @param html HTML format flag.
     * @param addrs Addresses to send email to.
     * @throws GridException Thrown in case when sending email failed.
     */
    public static void sendEmail(String smtpHost, int smtpPort, boolean ssl, boolean startTls,
        final String username, final String pwd, String from, String subj, String body,
        boolean html, Collection<String> addrs) throws GridException {
        assert smtpHost != null;
        assert smtpPort > 0;
        assert from != null;
        assert subj != null;
        assert body != null;
        assert addrs != null;
        assert !addrs.isEmpty();

        Properties props = new Properties();

        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.host", smtpHost);
        props.setProperty("mail.smtp.port", Integer.toString(smtpPort));

        if (ssl)
            props.setProperty("mail.smtp.ssl", "true");

        if (startTls)
            props.setProperty("mail.smtp.starttls.enable", "true");

        Authenticator auth = null;

        // Add property for authentication by username.
        if (username != null && username.length() > 0) {
            props.setProperty("mail.smtp.auth", "true");

            auth = new Authenticator() {
                @Override public javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(username, pwd);
                }
            };
        }

        Session ses = Session.getInstance(props, auth);

        MimeMessage email = new MimeMessage(ses);

        try {
            email.setFrom(new InternetAddress(from));
            email.setSubject(subj);
            email.setSentDate(new Date());

            if (html)
                email.setText(body, "UTF-8", "html");
            else
                email.setText(body);

            Address[] rcpts = new Address[addrs.size()];

            int i = 0;

            for (String addr : addrs)
                rcpts[i++] = new InternetAddress(addr);

            email.setRecipients(MimeMessage.RecipientType.TO, rcpts);

            Transport.send(email);
        }
        catch (MessagingException e) {
            throw new GridException("Failed to send email.", e);
        }
    }

    /**
     * Gets empty constructor for class even if the class does not have empty constructor
     * declared. This method is guaranteed to work with SUN JDK and other JDKs still need
     * to be tested.
     *
     * @param cls Class to get empty constructor for.
     * @return Empty constructor if one could be found or {@code null} otherwise.
     * @throws GridException If failed.
     */
    @Nullable public static Constructor<?> forceEmptyConstructor(Class<?> cls) throws GridException {
        Constructor<?> ctor = null;

        try {
            return cls.getDeclaredConstructor();
        }
        catch (Exception ignore) {
            Method ctorFac = U.ctorFactory();
            Object sunRefFac = U.sunReflectionFactory();

            if (ctorFac != null && sunRefFac != null)
                try {
                    ctor = (Constructor)ctorFac.invoke(sunRefFac, cls, U.objectConstructor());
                }
                catch (IllegalAccessException e) {
                    throw new GridException("Failed to get object constructor for class: " + cls, e);
                }
                catch (InvocationTargetException e) {
                    throw new GridException("Failed to get object constructor for class: " + cls, e);
                }
        }

        return ctor;
    }

    /**
     * Creates new instance of a class only if it has an empty constructor (can be non-public).
     *
     * @param cls Class to instantiate.
     * @return New instance of the class or {@code null} if empty constructor could not be assigned.
     * @throws GridException If failed.
     */
    @Nullable public static <T> T newInstance(Class<T> cls) throws GridException {
        boolean set = false;

        Constructor<T> ctor = null;

        try {
            ctor = cls.getDeclaredConstructor();

            if (ctor == null)
                return null;

            if (!ctor.isAccessible()) {
                ctor.setAccessible(true);

                set = true;
            }

            return ctor.newInstance();
        }
        catch (NoSuchMethodException e) {
            throw new GridException("Failed to find empty constructor for class: " + cls, e);
        }
        catch (InstantiationException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        catch (IllegalAccessException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        catch (InvocationTargetException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        finally {
            if (ctor != null && set)
                ctor.setAccessible(false);
        }
    }

    /**
     * Creates new instance of a class even if it does not have public constructor.
     *
     * @param cls Class to instantiate.
     * @return New instance of the class or {@code null} if empty constructor could not be assigned.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <T> T forceNewInstance(Class<?> cls) throws GridException {
        Constructor ctor = forceEmptyConstructor(cls);

        if (ctor == null)
            return null;

        boolean set = false;

        try {

            if (!ctor.isAccessible()) {
                ctor.setAccessible(true);

                set = true;
            }

            return (T)ctor.newInstance();
        }
        catch (InstantiationException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        catch (IllegalAccessException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        catch (InvocationTargetException e) {
            throw new GridException("Failed to create new instance for class: " + cls, e);
        }
        finally {
            if (set)
                ctor.setAccessible(false);
        }
    }

    /**
     * Tests whether or not this GridGain runtime runs on an enterprise edition. This method
     * is primarily for informational purpose.
     *
     * @return {@code True} for enterprise edition, {@code false} - for community edition.
     * @see GridEnterpriseFeatureException
     * @see GridEnterpriseOnly
     */
    public static boolean isEnterprise() {
        return ent;
    }

    /**
     * Pretty-formatting for minutes.
     *
     * @param mins Minutes to format.
     * @return Formatted presentation of minutes.
     */
    public static String formatMins(long mins) {
        assert mins >= 0;

        if (mins == 0)
            return "< 1 min";

        SB sb = new SB();

        long dd = mins / 1440; // 1440 mins = 60 mins * 24 hours

        if (dd > 0)
            sb.a(dd).a(dd == 1 ? " day " : " days ");

        mins %= 1440;

        long hh = mins / 60;

        if (hh > 0)
            sb.a(hh).a(hh == 1 ? " hour " : " hours ");

        mins %= 60;

        if (mins > 0)
            sb.a(mins).a(mins == 1 ? " min " : " mins ");

        return sb.toString().trim();
    }

    /**
     * Gets 8-character substring of UUID (for terse logging).
     *
     * @param id Input ID.
     * @return 8-character ID substring.
     */
    public static String id8(UUID id) {
        return id.toString().substring(0, 8);
    }

    /**
     *
     * @param len Number of characters to fill in.
     * @param ch Character to fill with.
     * @return String.
     */
    public static String filler(int len, char ch) {
        char[] a = new char[len];

        Arrays.fill(a, ch);

        return new String(a);
    }

    /**
     * Writes array to output stream.
     *
     * @param out Output stream.
     * @param arr Array to write.
     * @param <T> Array type.
     * @throws IOException If failed.
     */
    public static <T> void writeArray(ObjectOutput out, T[] arr) throws IOException {
        int len = arr == null ? 0 : arr.length;

        out.writeInt(len);

        if (arr != null && arr.length > 0)
            for (T t : arr)
                out.writeObject(t);
    }


    /**
     * Reads array from input stream.
     *
     * @param in Input stream.
     * @return Deserialized array.
     * @throws IOException If failed.
     * @throws ClassNotFoundException If class not found.
     */
    @Nullable public static Object[] readArray(ObjectInput in) throws IOException, ClassNotFoundException {
        int len = in.readInt();

        Object[] arr = null;

        if (len > 0) {
            arr = new Object[len];

            for (int i = 0; i < len; i++)
                arr[i] = in.readObject();
        }

        return arr;
    }

    /**
     * Reads array from input stream.
     *
     * @param in Input stream.
     * @param factory Array factory.
     * @param <T> Array type.
     * @return Deserialized array.
     * @throws IOException If failed.
     * @throws ClassNotFoundException If class not found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <T> T[] readArray(ObjectInput in, GridClosure<Integer, T[]> factory) throws IOException,
        ClassNotFoundException {
        int len = in.readInt();

        T[] arr = null;

        if (len > 0) {
            arr = factory.apply(len);

            for (int i = 0; i < len; i++)
                arr[i] = (T)in.readObject();
        }

        return arr;
    }

    /**
     *
     * @param out Output.
     * @param col Set to write.
     * @throws IOException If write failed.
     */
    public static void writeCollection(ObjectOutput out, Collection<?> col) throws IOException {
        // Write null flag.
        out.writeBoolean(col == null);

        if (col != null) {
            out.writeInt(col.size());

            for (Object o : col)
                out.writeObject(o);
        }
    }

    /**
     *
     * @param out Output.
     * @param col Set to write.
     * @throws IOException If write failed.
     */
    public static void writeIntCollection(DataOutput out, Collection<Integer> col) throws IOException {
        // Write null flag.
        out.writeBoolean(col == null);

        if (col != null) {
            out.writeInt(col.size());

            for (Integer i : col)
                out.writeInt(i);
        }
    }

    /**
     * @param in Input.
     * @return Deserialized set.
     * @throws IOException If deserialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <E> Collection<E> readCollection(ObjectInput in)
        throws IOException, ClassNotFoundException {
        List<E> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<E>(size);

            for (int i = 0; i < size; i++)
                col.add((E)in.readObject());
        }

        return col;
    }

    /**
     * @param in Input.
     * @return Deserialized set.
     * @throws IOException If deserialization failed.
     */
    @Nullable public static Collection<Integer> readIntCollection(DataInput in) throws IOException {
        List<Integer> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<Integer>(size);

            for (int i = 0; i < size; i++)
                col.add(in.readInt());
        }

        return col;
    }

    /**
     *
     * @param m Map to copy.
     * @param <K> Key type.
     * @param <V> Value type
     * @return Copied map.
     */
    public static <K, V> Map<K, V> copyMap(Map<K, V> m) {
        return new HashMap<K, V>(m);
    }

    /**
     *
     * @param m Map to seal.
     * @param <K> Key type.
     * @param <V> Value type
     * @return Sealed map.
     */
    public static <K, V> Map<K, V> sealMap(Map<K, V> m) {
        return Collections.unmodifiableMap(new HashMap<K, V>(m));
    }

    /**
     * Gets display name of the network interface this IP address belongs to.
     *
     * @param addr IP address for which to find network interface name.
     * @return Network interface name or {@code null} if can't be found.
     */
    @Nullable public static String getNetworkInterfaceName(String addr) {
        assert addr != null;

        try {
            InetAddress inetAddr = InetAddress.getByName(addr);

            for (NetworkInterface itf : asIterable(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress itfAddr : asIterable(itf.getInetAddresses()))
                    if (itfAddr.equals(inetAddr))
                        return itf.getDisplayName();
        }
        catch (UnknownHostException ignore) {
            return null;
        }
        catch (SocketException ignore) {
            return null;
        }

        return null;
    }

    /**
     * Determines whether current local host is different from previously cached.
     *
     * @return {@code true} or {@code false} depending on whether or not local host
     *      has changed from the cached value.
     * @throws IOException If attempt to get local host failed.
     */
    public static synchronized boolean isLocalHostChanged() throws IOException {
        return localHost != null && !resetLocalHost().equals(localHost);
    }

    /**
     * Gets local host. Implementation will first attempt to get a non-loopback
     * address. If that fails, then loopback address will be returned.
     * <p>
     * Note that this method is synchronized to make sure that local host
     * initialization happens only once.
     *
     * @return Address representing local host.
     * @throws IOException If attempt to get local host failed.
     */
    public static synchronized InetAddress getLocalHost() throws IOException {
        if (localHost == null)
            // Cache it.
            localHost = resetLocalHost();

        return localHost;
    }

    /**
     * @return Local host.
     * @throws IOException If attempt to get local host failed.
     */
    private static InetAddress resetLocalHost() throws IOException {
        localHost = InetAddress.getLocalHost();

        // It should not take longer than 2 seconds to reach
        // local address on any network.
        int reachTimeout = 2000;

        if (localHost.isLoopbackAddress() || !reachable(localHost, reachTimeout))
            for (NetworkInterface itf : asIterable(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress addr : asIterable(itf.getInetAddresses()))
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && reachable(addr, reachTimeout)) {
                        localHost = addr;

                        break;
                    }

        return localHost;
    }

    /**
     * Checks if address can be reached.
     *
     * @param addr Address to check.
     * @param reachTimeout Timeout for the check.
     * @return {@code True} if address is reachable.
     */
    public static boolean reachable(InetAddress addr, int reachTimeout) {
        try {
            return addr.isReachable(reachTimeout);
        }
        catch (IOException ignore) {
            return false;
        }
    }

    /**
     * Gets comma separate list of all local non-loopback IPs known to this JVM.
     * Note that this will include both IPv4 and IPv6 addresses (even if one "resolves"
     * into another). Loopbacks will be skipped.
     *
     * @return Comma separate list of all known local IPs.
     */
    @Nullable public static String allLocalIps() {
        Collection<String> ips = new HashSet<String>(4);

        try {
            Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();

            if (itfs != null)
                for (NetworkInterface itf : asIterable(itfs)) {
                    if (!itf.isLoopback()) {
                        Enumeration<InetAddress> addrs = itf.getInetAddresses();

                        if (addrs != null)
                            for (InetAddress addr : asIterable(addrs))
                                if (!addr.isLoopbackAddress())
                                    ips.add(addr.getHostAddress());
                    }
                }
        }
        catch (SocketException ignore) {
            return null;
        }

        if (ips.isEmpty())
            return null;

        SB sb = new SB();

        int n = ips.size();

        int i = 1;

        for (String ip : ips) {
            sb.a(ip);

            if (i < n)
                sb.a(", ");

            i++;
        }

        return sb.toString();
    }

    /**
     * Gets comma separate list of all local enabled MACs known to this JVM. It
     * is using hardware address of the network interface that is not guaranteed to be
     * MAC addresses (but in most cases it is).
     * <p>
     * Note that if network interface is disabled - its MAC won't be included. All
     * local network interfaces are probed including loopbacks. Virtual interfaces
     * (sub-interfaces) are skipped.
     *
     * @return Comma separate list of all known enabled local MACs or <tt>null</tt>
     *      if no MACs could be found.
     */
    @Nullable public static String allLocalMACs() {
        Collection<String> macs = new ArrayList<String>(3);

        try {
            Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();

            if (itfs != null)
                for (NetworkInterface itf : asIterable(itfs)) {
                    byte[] hwAddr = itf.getHardwareAddress();

                    if (hwAddr != null)
                        macs.add(byteArray2HexString(hwAddr));
                }
        }
        catch (SocketException ignore) {
            return null;
        }

        if (macs.isEmpty())
            return null;

        SB sb = new SB();

        int n = macs.size();

        int i = 1;

        for (String mac : macs) {
            sb.a(mac);

            if (i < n)
                sb.a(", ");

            i++;
        }

        return sb.toString();
    }

    /**
     * Marshals object to a {@link GridByteArrayList} using given {@link GridMarshaller}.
     *
     * @param marshaller Marshaller.
     * @param obj Object to marshal.
     * @return Buffer that contains obtained byte array.
     * @throws GridException If marshalling failed.
     */
    public static GridByteArrayList marshal(GridMarshaller marshaller, Object obj) throws GridException {
        assert marshaller != null;

        GridByteArrayOutputStream out = null;

        try {
            out = new GridByteArrayOutputStream(DFLT_BUFFER_SIZE);

            marshaller.marshal(obj, out);

            return out.toByteArrayList();
        }
        finally {
            U.close(out, null);
        }
    }

    /**
     * Marshals object to a output stream using given {@link GridMarshaller}.
     *
     * @param marshaller Marshaller.
     * @param obj Object to marshal.
     * @param out Output stream to marshal into.
     * @throws GridException If marshalling failed.
     */
    public static void marshal(GridMarshaller marshaller, Object obj, OutputStream out) throws GridException {
        assert marshaller != null;

        marshaller.marshal(obj, out);
    }

    /**
     * Unmarshalls object from a {@link GridByteArrayList} using given class loader.
     *
     * @param <T> Type of unmarshalled object.
     * @param marshaller Marshaller.
     * @param buf Buffer that contains byte array with marshalled object.
     * @param clsLdr Class loader to use.
     * @return Unmarshalled object.
     * @throws GridException If marshalling failed.
     */
    @SuppressWarnings("unchecked")
    public static <T> T unmarshal(GridMarshaller marshaller, GridByteArrayList buf, ClassLoader clsLdr)
        throws GridException {
        assert marshaller != null;
        assert buf != null;

        ByteArrayInputStream in = null;

        try {
            in = new ByteArrayInputStream(buf.getArray(), 0, buf.getSize());

            return (T)marshaller.unmarshal(in, clsLdr);
        }
        finally {
            U.close(in, null);
        }
    }

    /**
     * Unmarshalls object from a input stream using given class loader.
     *
     * @param <T> Type of unmarshalled object.
     * @param marshaller Marshaller.
     * @param in Input stream that provides marshalled object bytes.
     * @param clsLdr Class loader to use.
     * @return Unmarshalled object.
     * @throws GridException If marshalling failed.
     */
    @SuppressWarnings("unchecked")
    public static <T> T unmarshal(GridMarshaller marshaller, InputStream in, ClassLoader clsLdr)
        throws GridException {
        assert marshaller != null;
        assert in != null;
        assert clsLdr != null;

        return (T)marshaller.unmarshal(in, clsLdr);
    }

    /**
     * Gets user version for given class loader by checking
     * {@code META-INF/gridgain.xml} file for {@code userVersion} attribute. If
     * {@code gridgain.xml} file is not found, or user version is not specified there,
     * then default version (empty string) is returned.
     *
     * @param ldr Class loader.
     * @param log Logger.
     * @return User version for given class loader or empty string if no version
     *      was explicitly specified.
     */
    public static String getUserVersion(ClassLoader ldr, GridLogger log) {
        assert ldr != null;
        assert log != null;

        // For system class loader return cached version.
        if (ldr == GridUtils.class.getClassLoader() && SYS_LDR_VER.get() != null)
            return SYS_LDR_VER.get();

        String usrVer = DFLT_USER_VERSION;

        InputStream in = ldr.getResourceAsStream(GRIDGAIN_XML_PATH);

        if (in != null) {
            // Note: use ByteArrayResource instead of InputStreamResource because
            // InputStreamResource doesn't work.
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try {
                copy(in, out);

                AbstractBeanFactory factory = new XmlBeanFactory(new ByteArrayResource(out.toByteArray()));

                usrVer = (String)factory.getBean("userVersion");

                usrVer = usrVer == null ? DFLT_USER_VERSION : usrVer.trim();
            }
            catch (NoSuchBeanDefinitionException ignored) {
                if (log.isInfoEnabled())
                    log.info("User version is not explicitly defined (will use default version) [file=" +
                        GRIDGAIN_XML_PATH + ", clsLdr=" + ldr + ']');

                usrVer = DFLT_USER_VERSION;
            }
            catch (BeansException e) {
                U.error(log, "Failed to parse Spring XML file (will use default user version) [file=" +
                    GRIDGAIN_XML_PATH + ", clsLdr=" + ldr + ']', e);

                usrVer = DFLT_USER_VERSION;
            }
            catch (IOException e) {
                U.error(log, "Failed to read Spring XML file (will use default user version) [file=" +
                    GRIDGAIN_XML_PATH + ", clsLdr=" + ldr + ']', e);

                usrVer = DFLT_USER_VERSION;
            }
            finally {
                close(out, log);
            }
        }

        // For system class loader return cached version.
        if (ldr == GridUtils.class.getClassLoader())
            SYS_LDR_VER.compareAndSet(null, usrVer);

        return usrVer;
    }

    /**
     * Downloads resource by URL.
     *
     * @param url URL to download.
     * @param file File where downloaded resource should be stored.
     * @return File where downloaded resource should be stored.
     * @throws IOException If error occurred.
     */
    public static File downloadUrl(URL url, File file) throws IOException {
        assert url != null;
        assert file != null;

        InputStream in = null;
        OutputStream out = null;

        try {
            URLConnection conn = url.openConnection();

            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection)conn;

                https.setHostnameVerifier(new DeploymentHostnameVerifier());

                SSLContext ctx = SSLContext.getInstance(HTTPS_PROTOCOL);

                ctx.init(null, getTrustManagers(), null);

                // Initialize socket factory.
                https.setSSLSocketFactory(ctx.getSocketFactory());
            }

            in = conn.getInputStream();

            if (in == null)
                throw new IOException("Failed to open connection: " + url.toString());

            out = new BufferedOutputStream(new FileOutputStream(file));

            copy(in, out);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to open HTTPs connection [url=" + url.toString() + ", msg=" + e + ']', e);
        }
        catch (KeyManagementException e) {
            throw new IOException("Failed to open HTTPs connection [url=" + url.toString() + ", msg=" + e + ']', e);
        }
        finally {
            close(in, null);
            close(out, null);
        }

        return file;
    }

    /**
     * Construct array with one trust manager which don't reject input certificates.
     *
     * @return Array with one X509TrustManager implementation of trust manager.
     */
    private static TrustManager[] getTrustManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                @Nullable @Override public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    /* No-op. */
                }

                @Override public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    /* No-op. */
                }
            }
        };
    }

    /**
     * Copies metadata from given object into the passed in metadata aware
     * instance - if object is not {@code null} and implements {@link GridMetadataAware}.
     * Otherwise - returns passed metadata aware instance unchanged.
     *
     * @param t Passed in metadata aware instance.
     * @param obj Object to copy metadata from.
     * @param <T> Type of the metadata aware instance.
     * @return Metadata aware instance with metadata potentially copied.
     */
    public static <T extends GridMetadataAware> T withMeta(T t, @Nullable Object obj) {
        assert t != null;

        if (obj != null && obj instanceof GridMetadataAware)
            t.copyMeta((GridMetadataAware)obj);

        return t;
    }

    /**
     * Replace password in URI string with a single '*' character.
     * <p>
     * Parses given URI by applying &quot;.*://(.*:.*)@.*&quot;
     * regular expression pattern and than if URI matches it
     * replaces password strings between '/' and '@' with '*'.
     *
     * @param uri URI which password should be replaced.
     * @return Converted URI string
     */
    @Nullable public static String hidePassword(@Nullable String uri) {
        if (uri == null)
            return null;

        if (Pattern.matches(".*://(.*:.*)@.*", uri)) {
            int userInfoLastIdx = uri.indexOf('@');

            assert userInfoLastIdx != -1;

            String str = uri.substring(0, userInfoLastIdx);

            int userInfoStartIdx = str.lastIndexOf('/');

            str = str.substring(userInfoStartIdx + 1);

            String[] params = str.split(";");

            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < params.length; i++) {
                int idx;

                if ((idx = params[i].indexOf(':')) != -1)
                    params[i] = params[i].substring(0, idx + 1) + '*';

                builder.append(params[i]);

                if (i != params.length - 1)
                    builder.append(';');
            }

            return new StringBuilder(uri).replace(userInfoStartIdx + 1, userInfoLastIdx,
                builder.toString()).toString();
        }

        return uri;
    }

    /**
     * Verifier always returns successful result for any host.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private static class DeploymentHostnameVerifier implements HostnameVerifier {
        // Remote host trusted by default.
        @Override public boolean verify(String hostname, SSLSession ses) {
            return true;
        }
    }

    /**
     * Makes a {@code '+---+'} dash line.
     *
     * @param len Length of the dash line to make.
     * @return Dash line.
     */
    public static String dash(int len) {
        char[] dash = new char[len];

        Arrays.fill(dash, '-');

        dash[0] = dash[len - 1] = '+';

        return new String(dash);
    }

    /**
     * Creates space filled string of given length.
     *
     * @param len Number of spaces.
     * @return Space filled string of given length.
     */
    public static String pad(int len) {
        char[] dash = new char[len];

        Arrays.fill(dash, ' ');

        return new String(dash);
    }

    /**
     * Formats system time in milliseconds for printing in logs.
     *
     * @param sysTime System time.
     * @return Formatted time string.
     */
    public static String format(long sysTime) {
        return LONG_DATE_FMT.format(new java.util.Date(sysTime));
    }

    /**
     * Takes given collection, shuffles it and returns iterable instance.
     *
     * @param <T> Type of elements to create iterator for.
     * @param col Collection to shuffle.
     * @return Iterable instance over randomly shuffled collection.
     */
    public static <T> Iterable<T> randomIterable(Collection<T> col) {
        List<T> list = new ArrayList<T>(col);

        Collections.shuffle(list);

        return list;
    }

    /**
     * Converts enumeration to iterable so it can be used in {@code foreach} construct.
     *
     * @param <T> Types of instances for iteration.
     * @param e Enumeration to convert.
     * @return Iterable over the given enumeration.
     */
    public static <T> GridIterableOpt<T> asIterable(final Enumeration<T> e) {
        return new GridIterableOpt<T>(e == null ? null : new Iterable<T>() {
            @Override public Iterator<T> iterator() {
                return new Iterator<T>() {
                    @Override public boolean hasNext() {
                        return e.hasMoreElements();
                    }

                    @SuppressWarnings({"IteratorNextCanNotThrowNoSuchElementException"})
                    @Override public T next() {
                        return e.nextElement();
                    }

                    @Override public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });
    }

    /**
     * Copy source file (or folder) to destination file (or folder). Supported source & destination:
     * <ul>
     * <li>File to File</li>
     * <li>File to Folder</li>
     * <li>Folder to Folder</li>
     * </ul>
     *
     * @param src Source file or folder.
     * @param dest Destination file or folder.
     * @param overwrite Whether or not overwrite existing files and folders.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public static void copy(File src, File dest, boolean overwrite) throws IOException {
        assert src != null;
        assert dest != null;

        /*
         * Supported source & destination:
         * ===============================
         * 1. File -> File
         * 2. File -> Directory
         * 3. Directory -> Directory
         */

        // Source must exist.
        if (!src.exists())
            throw new FileNotFoundException("Source can't be found: " + src);

        // Check that source and destination are not the same.
        if (src.getAbsoluteFile().equals(dest.getAbsoluteFile()))
            throw new IOException("Source and destination are the same [src=" + src + ", dest=" + dest + ']');

        if (dest.exists()) {
            if (!dest.isDirectory() && !overwrite)
                throw new IOException("Destination already exists: " + dest);

            if (!dest.canWrite())
                throw new IOException("Destination is not writable:" + dest);
        }
        else {
            File parent = dest.getParentFile();

            if (parent != null && !parent.exists())
                // Ignore any errors here.
                // We will get errors when we'll try to open the file stream.
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();

            // If source is a directory, we should create destination directory.
            if (src.isDirectory())
                //noinspection ResultOfMethodCallIgnored
                dest.mkdir();
        }

        if (src.isDirectory()) {
            // In this case we have Directory -> Directory.
            // Note that we copy the content of the directory and not the directory itself.

            File[] files = src.listFiles();

            for (File file : files) {
                if (file.isDirectory()) {
                    File dir = new File(dest, file.getName());

                    if (!dir.exists() && !dir.mkdirs())
                        throw new IOException("Can't create directory: " + dir);

                    copy(file, dir, overwrite);
                }
                else
                    copy(file, dest, overwrite);
            }
        }
        else {
            // In this case we have File -> File or File -> Directory.
            File file = dest.exists() && dest.isDirectory() ? new File(dest, src.getName()) : dest;

            if (!overwrite && file.exists())
                throw new IOException("Destination already exists: " + file);

            FileInputStream in = null;
            FileOutputStream out = null;

            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(file);

                copy(in, out);
            }
            finally {
                if (in != null)
                    in.close();

                if (out != null)
                    out.close();
            }
        }
    }

    /**
     * Copies input byte stream to output byte stream.
     *
     * @param in Input byte stream.
     * @param out Output byte stream.
     * @return Number of the copied bytes.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public static int copy(InputStream in, OutputStream out) throws IOException {
        assert in != null;
        assert out != null;

        byte[] buf = new byte[BUF_SIZE];

        int cnt = 0;

        for (int n; (n = in.read(buf)) > 0;) {
            out.write(buf, 0, n);

            cnt += n;
        }

        return cnt;
    }

    /**
     * Copies input character stream to output character stream.
     *
     * @param in Input character stream.
     * @param out Output character stream.
     * @return Number of the copied characters.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public static int copy(Reader in, Writer out) throws IOException {
        assert in != null;
        assert out != null;

        char[] buf = new char[BUF_SIZE];

        int cnt = 0;

        for (int n; (n = in.read(buf)) > 0;) {
            out.write(buf, 0, n);

            cnt += n;
        }

        return cnt;
    }

    /**
     * Utility method that sets cause into exception and returns it.
     *
     * @param e Exception to set cause to and return.
     * @param cause Optional cause to set (if not {@code null}).
     * @param <E> Type of the exception.
     * @return Passed in exception with optionally set cause.
     */
    public static <E extends Throwable> E withCause(E e, @Nullable Throwable cause) {
        assert e != null;

        if (cause != null)
            e.initCause(cause);

        return e;
    }

    /**
     * Deletes file or directory with all sub-directories and files.
     *
     * @param file File or directory to delete.
     * @return {@code true} if and only if the file or directory is successfully deleted,
     *      {@code false} otherwise
     */
    public static boolean delete(File file) {
        assert file != null;

        boolean res = true;

        if (file.isDirectory()) {
            File[] files = file.listFiles();

            if (files != null && files.length > 0)
                for (File file1 : files)
                    if (file1.isDirectory())
                        res &= delete(file1);
                    else if (file1.getName().endsWith("jar"))
                        try {
                            // Why do we do this?
                            new JarFile(file1, false).close();

                            res &= file1.delete();
                        }
                        catch (IOException ignore) {
                            // Ignore it here...
                        }
                    else
                        res &= file1.delete();

            res &= file.delete();
        }
        else
            res = file.delete();

        return res;
    }

    /**
     * Gets boolean system or environment property.
     *
     * @param name Property name.
     * @return {@code True} if system or environment property is set to {@code true}. Otherwise returns {@code false}.
     */
    public static boolean getBoolean(String name) {
        String v = X.getSystemOrEnv(name);

        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    /**
     * Retrieves {@code GRIDGAIN_HOME} property. The property is retrieved from system
     * properties or from environment in that order.
     *
     * @return {@code GRIDGAIN_HOME} property.
     */
    @Nullable public static String getGridGainHome() {
        return X.getSystemOrEnv(GG_HOME);
    }

    /**
     * Gets URL representing the path passed in. First the check is made if path is absolute.
     * If not, then the check is made if path is relative to {@code META-INF} folder in classpath.
     * If not, then the check is made if path is relative to ${GRIDGAIN_HOME}.
     * If all checks fail,
     * then {@code null} is returned, otherwise URL representing path is returned.
     * <p>
     * See {@link #getGridGainHome()} for information on how {@code GRIDGAIN_HOME} is retrieved.
     *
     * @param path Path to resolve.
     * @return Resolved path as URL, or {@code null} if path cannot be resolved.
     * @see #getGridGainHome()
     */
    @Nullable public static URL resolveGridGainUrl(String path) {
        return resolveGridGainUrl(path, true);
    }

    /**
     * Gets URL representing the path passed in. First the check is made if path is absolute.
     * If not, then the check is made if path is relative to {@code META-INF} folder in classpath.
     * If not, then the check is made if path is relative to ${GRIDGAIN_HOME}.
     * If all checks fail,
     * then {@code null} is returned, otherwise URL representing path is returned.
     * <p>
     * See {@link #getGridGainHome()} for information on how {@code GRIDGAIN_HOME} is retrieved.
     *
     * @param path Path to resolve.
     * @param metaInf Flag to indicate whether META-INF folder should be checked or class path root.
     * @return Resolved path as URL, or {@code null} if path cannot be resolved.
     * @see #getGridGainHome()
     */
    @SuppressWarnings({"UnusedCatchParameter"})
    @Nullable public static URL resolveGridGainUrl(String path, boolean metaInf) {
        assert path != null;

        /*
         * 1. Check relative to GRIDGAIN_HOME specified in
         *    configuration, if any.
         */

        String home = getGridGainHome();

        if (home != null) {
            File file = new File(home, path);

            try {
                if (file.exists())
                    // Note: we use that method's chain instead of File.getURL() with due
                    // Sun bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6179468
                    return file.toURI().toURL();
            }
            catch (MalformedURLException e) {
                // No-op.
            }
        }

        /*
         * 2. Check given path as absolute.
         */

        File file = new File(path);

        try {
            if (file.exists())
                // Note: we use that method's chain instead of File.getURL() with due
                // Sun bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6179468
                return file.toURI().toURL();
        }
        catch (MalformedURLException e) {
            // No-op.
        }

        /*
         * 3. Check META-INF or root.
         */

        String locPath = (metaInf ? "META-INF/" : "") + path.replaceAll("\\\\", "/");

        return Thread.currentThread().getContextClassLoader().getResource(locPath);
    }

    /**
     * Converts byte array to formatted string. If calling:
     * <pre name="code" class="java">
     * ...
     * byte[] data = {10, 20, 30, 40, 50, 60, 70, 80, 90};
     *
     * U.byteArray2String(data, "0x%02X", ",0x%02X")
     * ...
     * </pre>
     * the result will be:
     * <pre name="code" class="java">
     * ...
     * 0x0A, 0x14, 0x1E, 0x28, 0x32, 0x3C, 0x46, 0x50, 0x5A
     * ...
     * </pre>
     *
     * @param arr Array of byte.
     * @param headerFmt C-style string format for the first element.
     * @param bodyFmt C-style string format for second and following elements, if any.
     * @return String with converted bytes.
     */
    public static String byteArray2String(byte[] arr, String headerFmt, String bodyFmt) {
        assert arr != null;
        assert headerFmt != null;
        assert bodyFmt != null;

        SB sb = new SB();

        sb.a('{');

        boolean first = true;

        for (byte b : arr)
            if (first) {
                sb.a(String.format(headerFmt, b));

                first = false;
            }
            else
                sb.a(String.format(bodyFmt, b));

        sb.a('}');

        return sb.toString();
    }

    /**
     * Converts byte array to hex string.
     *
     * @param arr Array of bytes.
     * @return Hex string.
     */
    public static String byteArray2HexString(byte[] arr) {
        SB sb = new SB(arr.length << 1);

        for (byte b : arr)
            sb.a(Integer.toHexString(MASK & b >>> 4)).a(Integer.toHexString(MASK & b));

        return sb.toString().toUpperCase();
    }

    /**
     * Convert string with hex values to byte array.
     *
     * @param hex Hexadecimal string to convert.
     * @return array of bytes defined as hex in string.
     * @throws IllegalArgumentException If input character differs from certain hex characters.
     */
    public static byte[] hexString2ByteArray(String hex) throws IllegalArgumentException {
        // If Hex string has odd character length.
        if (hex.length() % 2 != 0)
            hex = '0' + hex;

        char[] chars = hex.toCharArray();

        byte[] bytes = new byte[chars.length / 2];

        int byteCount = 0;

        for (int i = 0; i < chars.length; i += 2) {
            int newByte = 0;

            newByte |= hexCharToByte(chars[i]);

            newByte <<= 4;

            newByte |= hexCharToByte(chars[i + 1]);

            bytes[byteCount] = (byte)newByte;

            byteCount++;
        }

        return bytes;
    }

    /**
     * Return byte value for certain character.
     *
     * @param ch Character
     * @return Byte value.
     * @throws IllegalArgumentException If input character differ from certain hex characters.
     */
    @SuppressWarnings({"UnnecessaryFullyQualifiedName"})
    private static byte hexCharToByte(char ch) throws IllegalArgumentException {
        switch (ch) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return (byte)(ch - '0');

            case 'a':
            case 'A':
                return 0xa;

            case 'b':
            case 'B':
                return 0xb;

            case 'c':
            case 'C':
                return 0xc;

            case 'd':
            case 'D':
                return 0xd;

            case 'e':
            case 'E':
                return 0xe;

            case 'f':
            case 'F':
                return 0xf;

            default:
                throw new IllegalArgumentException("Hex decoding wrong input character [character=" + ch + ']');
        }
    }

    /**
     * Converts primitive double to byte array.
     *
     * @param d Double to convert.
     * @return Byte array.
     */
    public static byte[] doubleToBytes(double d) {
        return longToBytes(Double.doubleToLongBits(d));
    }

    /**
     * Converts primitive {@code double} type to byte array and stores
     * it in the specified byte array.
     *
     * @param d Double to convert.
     * @param bytes Array of bytes.
     * @param off Offset.
     * @return New offset.
     */
    public static int doubleToBytes(double d, byte[] bytes, int off) {
        return longToBytes(Double.doubleToLongBits(d), bytes, off);
    }

    /**
     * Converts primitive float to byte array.
     *
     * @param f Float to convert.
     * @return Array of bytes.
     */
    public static byte[] floatToBytes(float f) {
        return intToBytes(Float.floatToIntBits(f));
    }

    /**
     * Converts primitive float to byte array.
     *
     * @param f Float to convert.
     * @param bytes Array of bytes.
     * @param off Offset.
     * @return New offset.
     */
    public static int floatToBytes(float f, byte[] bytes, int off) {
        return intToBytes(Float.floatToIntBits(f), bytes, off);
    }

    /**
     * Converts primitive {@code long} type to byte array.
     *
     * @param l Long value.
     * @return Array of bytes.
     */
    public static byte[] longToBytes(long l) {
        byte[] bytes = new byte[(Long.SIZE >> 3)];

        longToBytes(l, bytes, 0);

        return bytes;
    }

    /**
     * Converts primitive {@code long} type to byte array and stores it in specified
     * byte array.
     *
     * @param l Long value.
     * @param bytes Array of bytes.
     * @param off Offset in {@code bytes} array.
     * @return Number of bytes overwritten in {@code bytes} array.
     */
    public static int longToBytes(long l, byte[] bytes, int off) {
        int bytesCnt = Long.SIZE >> 3;

        for (int i = 0; i < bytesCnt; i++) {
            int shift = bytesCnt - i - 1 << 3;

            bytes[off++] = (byte)(l >>> shift & 0xff);
        }

        return off;
    }

    /**
     * Converts primitive {@code int} type to byte array.
     *
     * @param i Integer value.
     * @return Array of bytes.
     */
    public static byte[] intToBytes(int i) {
        byte[] bytes = new byte[(Integer.SIZE >> 3)];

        intToBytes(i, bytes, 0);

        return bytes;
    }

    /**
     * Converts primitive {@code int} type to byte array and stores it in specified
     * byte array.
     *
     * @param i Integer value.
     * @param bytes Array of bytes.
     * @param off Offset in {@code bytes} array.
     * @return Number of bytes overwritten in {@code bytes} array.
     */
    public static int intToBytes(int i, byte[] bytes, int off) {
        int bytesCnt = Integer.SIZE >> 3;

        for (int j = 0; j < bytesCnt; j++) {
            int shift = bytesCnt - j - 1 << 3;

            bytes[off++] = (byte)(i >>> shift & 0xff);
        }

        return off;
    }

    /**
     * Constructs {@code int} from byte array.
     *
     * @param bytes Array of bytes.
     * @param off Offset in {@code bytes} array.
     * @return Integer value.
     */
    public static int bytesToInt(byte[] bytes, int off) {
        assert bytes != null;

        int bytesCnt = Integer.SIZE >> 3;

        if (off + bytesCnt > bytes.length)
            // Just use the remainder.
            bytesCnt = bytes.length - off;

        int res = 0;

        for (int i = 0; i < bytesCnt; i++) {
            int shift = bytesCnt - i - 1 << 3;

            res |= (0xffL & bytes[off++]) << shift;
        }

        return res;
    }

    /**
     * Constructs {@code long} from byte array.
     *
     * @param bytes Array of bytes.
     * @param off Offset in {@code bytes} array.
     * @return Long value.
     */
    public static long bytesToLong(byte[] bytes, int off) {
        assert bytes != null;

        int bytesCnt = Long.SIZE >> 3;

        if (off + bytesCnt > bytes.length)
            bytesCnt = bytes.length - off;

        long res = 0;

        for (int i = 0; i < bytesCnt; i++) {
            int shift = bytesCnt - i - 1 << 3;

            res |= (0xffL & bytes[off++]) << shift;
        }

        return res;
    }

    /**
     * Constructs double from byte array.
     *
     * @param bytes Byte array.
     * @param off Offset in {@code bytes} array.
     * @return Double value.
     */
    public static double bytesToDouble(byte[] bytes, int off) {
        return Double.longBitsToDouble(bytesToLong(bytes, off));
    }

    /**
     * Constructs float from byte array.
     *
     * @param bytes Byte array.
     * @param off Offset in {@code bytes} array.
     * @return Float value.
     */
    public static float bytesToFloat(byte[] bytes, int off) {
        return Float.intBitsToFloat(bytesToInt(bytes, off));
    }

    /**
     * Checks for containment of the value in the array.
     * Both array cells and value may be {@code null}. Two {@code null}s are considered equal.
     *
     * @param arr Array of objects.
     * @param val Value to check for containment inside of array.
     * @return {@code true} if contains object, {@code false} otherwise.
     */
    public static boolean containsObjectArray(Object[] arr, Object val) {
        assert arr != null;

        if (arr.length == 0)
            return false;

        for (Object o : arr) {
            // If both are nulls, then they are equal.
            if (o == null && val == null)
                return true;

            // Only one is null and the other one isn't.
            if (o == null || val == null)
                return false;

            // Both are not nulls.
            if (o.equals(val))
                return true;
        }

        return false;
    }

    /**
     * Checks for containment of the value in the array.
     *
     * @param arr Array of objects.
     * @param val Value to check for containment inside of array.
     * @return {@code true} if contains object, {@code false} otherwise.
     */
    public static boolean containsIntArray(int[] arr, int val) {
        assert arr != null;

        if (arr.length == 0)
            return false;

        for (int i : arr)
            if (i == val)
                return true;

        return false;
    }

    /**
     * Checks for containment of given string value in the specified array.
     * Array's cells and string value can be {@code null}. Tow {@code null}s are considered equal.
     *
     * @param arr Array of strings.
     * @param val Value to check for containment inside of array.
     * @param ignoreCase Ignoring case if {@code true}.
     * @return {@code true} if contains string, {@code false} otherwise.
     */
    public static boolean containsStringArray(String[] arr, @Nullable String val, boolean ignoreCase) {
        assert arr != null;

        for (String s : arr) {
            // If both are nulls, then they are equal.
            if (s == null && val == null)
                return true;

            // Only one is null and the other one isn't.
            if (s == null || val == null)
                continue;

            // Both are not nulls.
            if (ignoreCase) {
                if (s.equalsIgnoreCase(val))
                    return true;
            }
            else if (s.equals(val))
                return true;
        }

        return false;
    }

    /**
     * Checks for containment of given string value in the specified collection.
     * Collection elements and string value can be {@code null}. Tow {@code null}s are considered equal.
     *
     * @param c Array of strings.
     * @param val Value to check for containment inside of array.
     * @param ignoreCase Ignoring case if {@code true}.
     * @return {@code true} if contains string, {@code false} otherwise.
     */
    public static boolean containsStringCollection(Iterable<String> c, @Nullable String val, boolean ignoreCase) {
        assert c != null;

        for (String s : c) {
            // If both are nulls, then they are equal.
            if (s == null && val == null)
                return true;

            // Only one is null and the other one isn't.
            if (s == null || val == null)
                continue;

            // Both are not nulls.
            if (ignoreCase) {
                if (s.equalsIgnoreCase(val))
                    return true;
            }
            else if (s.equals(val))
                return true;
        }

        return false;
    }

    /**
     * Checks for containment of value matching given regular expression in the provided array.
     *
     * @param arr Array of strings.
     * @param regex Regular expression.
     * @return {@code true} if string matching given regular expression found, {@code false} otherwise.
     */
    public static boolean containsRegexArray(String[] arr, String regex) {
        assert arr != null;
        assert regex != null;

        for (String s : arr)
            if (s != null && s.matches(regex))
                return true;

        return false;
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Closeable rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Closeable rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Socket rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Socket rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable ServerSocket rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable ServerSocket rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable AbstractInterruptibleChannel rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable AbstractInterruptibleChannel rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exceptions.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable SelectionKey rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            // This apply will automatically deregister the selection key as well.
            close(rsrc.channel(), log);
    }

    /**
     * Quietly closes given resource ignoring possible checked exceptions.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable SelectionKey rsrc) {
        if (rsrc != null)
            // This apply will automatically deregister the selection key as well.
            closeQuiet(rsrc.channel());
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param purge Whether or not to purge mail inbox on closing.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable GridMailInbox rsrc, boolean purge, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close(purge);
            }
            catch (GridMailException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param purge Whether or not to purge mail inbox on closing.
     */
    public static void closeQuiet(@WillClose @Nullable GridMailInbox rsrc, boolean purge) {
        if (rsrc != null)
            try {
                rsrc.close(purge);
            }
            catch (GridMailException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Reader rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Reader rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable ZipFile rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable ZipFile rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void close(@WillClose @Nullable DatagramSocket rsrc) {
        if (rsrc != null)
            rsrc.close();
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Selector rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                if (rsrc.isOpen())
                    rsrc.close();
            }
            catch (IOException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Selector rsrc) {
        if (rsrc != null)
            try {
                if (rsrc.isOpen())
                    rsrc.close();
            }
            catch (IOException ignored) {
                // No-op.
            }
    }

    /**
     * Closes given resource logging possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Context rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (NamingException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes given resource ignoring possible checked exception.
     *
     * @param rsrc Resource to close. If it's {@code null} - it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Context rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (NamingException ignored) {
                // No-op.
            }
    }

    /**
     * Closes JDBC connection logging possible checked exception.
     *
     * @param rsrc JDBC connection to close. If connection is {@code null}, it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Connection rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes JDBC connection ignoring possible checked exception.
     *
     * @param rsrc JDBC connection to close. If connection is {@code null}, it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Connection rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException ignored) {
                // No-op.
            }
    }

    /**
     * Closes JDBC statement logging possible checked exception.
     *
     * @param rsrc JDBC statement to close. If statement is {@code null}, it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable Statement rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes JDBC statement ignoring possible checked exception.
     *
     * @param rsrc JDBC statement to close. If statement is {@code null}, it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable Statement rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException ignored) {
                // No-op.
            }
    }

    /**
     * Closes JDBC result set logging possible checked exception.
     *
     * @param rsrc JDBC result set to close. If result set is {@code null}, it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable ResultSet rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Quietly closes JDBC result set ignoring possible checked exception.
     *
     * @param rsrc JDBC result set to close. If result set is {@code null}, it's no-op.
     */
    public static void closeQuiet(@WillClose @Nullable ResultSet rsrc) {
        if (rsrc != null)
            try {
                rsrc.close();
            }
            catch (SQLException ignored) {
                // No-op.
            }
    }

    /**
     * Closes class loader logging possible checked exception.
     * Note: this issue for problem <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5041014">
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5041014</a>.
     *
     * @param clsLdr Class loader. If it's {@code null} - it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void close(@WillClose @Nullable URLClassLoader clsLdr, @Nullable GridLogger log) {
        if (clsLdr != null)
            try {
                URLClassPath path = SharedSecrets.getJavaNetAccess().getURLClassPath(clsLdr);

                Field ldrFld = path.getClass().getDeclaredField("loaders");

                ldrFld.setAccessible(true);

                Iterable ldrs = (Iterable)ldrFld.get(path);

                for (Object ldr : ldrs)
                    if (ldr.getClass().getName().endsWith("JarLoader"))
                        try {
                            Field jarFld = ldr.getClass().getDeclaredField("jar");

                            jarFld.setAccessible(true);

                            ZipFile jar = (ZipFile)jarFld.get(ldr);

                            jar.close();
                        }
                        catch (Exception e) {
                            warn(log, "Failed to close resource: " + e.getMessage());
                        }
            }
            catch (Exception e) {
                warn(log, "Failed to close resource: " + e.getMessage());
            }
    }

    /**
     * Rollbacks JDBC connection logging possible checked exception.
     *
     * @param rsrc JDBC connection to rollback. If connection is {@code null}, it's no-op.
     * @param log Logger to log possible checked exception with (optional).
     */
    public static void rollbackConnection(@WillClose @Nullable Connection rsrc, @Nullable GridLogger log) {
        if (rsrc != null)
            try {
                rsrc.rollback();
            }
            catch (SQLException e) {
                warn(log, "Failed to rollback JDBC connection: " + e.getMessage());
            }
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log WARN message. If {@code log} is {@code null}
     * or in QUIET mode it will add <code>(!)</code> prefix to the message.
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param msg Message to log.
     */
    public static void warn(@Nullable GridLogger log, Object msg) {
        assert msg != null;

        String s = msg.toString();

        warn(log, s, shorten(s));
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log ERROR message. If {@code log} is {@code null}
     * or in QUIET mode it will add <code>(!!)</code> prefix to the message.
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param msg Message to log.
     */
    public static void error(@Nullable GridLogger log, Object msg) {
        assert msg != null;

        String s = msg.toString();

        error(log, s, shorten(s), null);
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log WARN message. If {@code log} is {@code null}
     * or in QUIET mode it will add <code>(!)</code> prefix to the message.
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param longMsg Message to log using normal logger.
     * @param shortMsg Message to log using quiet logger.
     */
    public static void warn(@Nullable GridLogger log, Object longMsg, Object shortMsg) {
        assert longMsg != null;
        assert shortMsg != null;

        if (log != null && !log.isQuiet()) {
            log.warning(compact(longMsg.toString()));

            return;
        }

        assert log == null || log.isQuiet();

        System.err.println("[" + SHORT_DATE_FMT.format(new java.util.Date()) + "] (!) " + compact(shortMsg.toString()));
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log INFO message.
     * <p>
     * <b>NOTE:</b> unlike the normal logging when INFO level may not be enabled and
     * therefore no logging will happen - using this method the log will be written
     * always either via INFO log or quiet mode.
     * <p>
     * <b>USE IT APPROPRIATELY.</b>
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param longMsg Message to log using normal logger.
     * @param shortMsg Message to log using quiet logger.
     */
    public static void log(@Nullable GridLogger log, Object longMsg, Object shortMsg) {
        assert longMsg != null;
        assert shortMsg != null;

        if (log != null && !log.isQuiet() && log.isInfoEnabled())
            log.info(compact(longMsg.toString()));
        else
            quiet(shortMsg);
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log INF0 message.
     * <p>
     * <b>NOTE:</b> unlike the normal logging when INFO level may not be enabled and
     * therefore no logging will happen - using this method the log will be written
     * always either via INFO log or quiet mode.
     * <p>
     * <b>USE IT APPROPRIATELY.</b>
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param msg Message to log.
     */
    public static void log(@Nullable GridLogger log, Object msg) {
        assert msg != null;

        String s = msg.toString();

        log(log, s, shorten(s));
    }

    /**
     * Depending on whether or not log is provided and quiet mode is enabled logs given
     * messages as quiet message or normal log ERROR message. If {@code log} is {@code null}
     * or in QUIET mode it will add <code>(!!)</code> prefix to the message.
     *
     * @param log Optional logger to use when QUIET mode is not enabled.
     * @param longMsg Message to log using normal logger.
     * @param shortMsg Message to log using quiet logger.
     * @param e Optional exception.
     */
    public static void error(@Nullable GridLogger log, Object longMsg, Object shortMsg, @Nullable Throwable e) {
        assert longMsg != null;
        assert shortMsg != null;

        if (log != null && !log.isQuiet()) {
            if (e == null)
                log.error(compact(longMsg.toString()));
            else
                log.error(compact(longMsg.toString()), e);

            return;
        }

        assert log == null || log.isQuiet();

        System.err.println("[" + SHORT_DATE_FMT.format(new java.util.Date()) + "] (!!) " +
            compact(shortMsg.toString()));

        if (e != null)
            e.printStackTrace(System.err);
    }

    /**
     * Shortcut for <code>error(null, longMsg, shortMsg)</code>.
     *
     * @param log Optional logger.
     * @param shortMsg Message to log using quiet logger.
     * @param e Optional exception.
     */
    public static void error(@Nullable GridLogger log, Object shortMsg, @Nullable Throwable e) {
        assert shortMsg != null;

        String s = shortMsg.toString();

        error(log, s, shorten(s), e);
    }

    /**
     * Shortens the given string.
     *
     * @param s String to shorten.
     * @return Shorten string.
     */
    /*private*/public static String shorten(String s) { // Public scope for testing only.
        assert s != null;

        return s.length() < 100 ? s : s.substring(0, 96) + "|...";
    }

    /**
     *
     * @param objs Objects to log in quiet mode.
     */
    public static void quiet(Object... objs) {
        assert objs != null;

        String time = SHORT_DATE_FMT.format(new java.util.Date());

        SB sb = new SB();

        for (Object obj : objs)
            sb.a('[').a(time).a("] ").a(obj.toString()).a(NL);

        System.out.print(compact(sb.toString()));
    }

    /**
     * Quietly rollbacks JDBC connection ignoring possible checked exception.
     *
     * @param rsrc JDBC connection to rollback. If connection is {@code null}, it's no-op.
     */
    public static void rollbackConnectionQuiet(@WillClose @Nullable Connection rsrc) {
        if (rsrc != null)
            try {
                rsrc.rollback();
            }
            catch (SQLException ignored) {
                // No-op.
            }
    }

    /**
     * Constructs JMX object name with given properties.
     * Map with ordered {@code groups} used for proper object name construction.
     *
     * @param gridName Grid name.
     * @param grp Name of the group.
     * @param name Name of mbean.
     * @return JMX object name.
     * @throws MalformedObjectNameException Thrown in case of any errors.
     */
    public static ObjectName makeMBeanName(@Nullable String gridName, @Nullable String grp, String name)
        throws MalformedObjectNameException {
        SB sb = new SB(JMX_DOMAIN + ':');

        if (gridName != null && gridName.length() > 0)
            sb.a("grid=").a(gridName).a(',');

        if (grp != null)
            sb.a("group=").a(grp).a(',');

        sb.a("name=").a(name);

        return new ObjectName(sb.toString());
    }

    /**
     * Registers MBean with the server.
     *
     * @param <T> Type of mbean.
     * @param mbeanSrv MBean server.
     * @param gridName Grid name.
     * @param grp Name of the group.
     * @param name Name of mbean.
     * @param impl MBean implementation.
     * @param itf MBean interface.
     * @return JMX object name.
     * @throws JMException If MBean creation failed.
     */
    public static <T> ObjectName registerMBean(MBeanServer mbeanSrv, @Nullable String gridName, @Nullable String grp,
        String name, T impl, Class<T> itf) throws JMException {
        assert mbeanSrv != null;
        assert name != null;
        assert itf != null;

        DynamicMBean mbean = new GridStandardMBean(impl, itf);

        mbean.getMBeanInfo();

        return mbeanSrv.registerMBean(mbean, makeMBeanName(gridName, grp, name)).getObjectName();
    }

    /**
     * Convenience method that interrupts a given thread if it's not {@code null}.
     *
     * @param t Thread to interrupt.
     */
    public static void interrupt(@Nullable Thread t) {
        if (t != null)
            t.interrupt();
    }

    /**
     * Convenience method that interrupts a given thread if it's not {@code null}.
     *
     * @param workers Threads to interrupt.
     */
    public static void interrupt(Iterable<? extends Thread> workers) {
        if (workers != null)
            for (Thread worker : workers)
                worker.interrupt();
    }

    /**
     * Waits for completion of a given thread. If thread is {@code null} then
     * this method returns immediately returning {@code true}
     *
     * @param t Thread to join.
     * @param log Logger for logging errors.
     * @return {@code true} if thread has finished, {@code false} otherwise.
     */
    public static boolean join(@Nullable Thread t, GridLogger log) {
        if (t != null)
            try {
                t.join();

                return true;
            }
            catch (InterruptedException ignore) {
                warn(log, "Got interrupted while waiting for completion of a thread: " + t);

                return false;
            }

        return true;
    }

    /**
     * Waits for completion of a given threads. If thread is {@code null} then
     * this method returns immediately returning {@code true}
     *
     * @param workers Thread to join.
     * @param log Logger for logging errors.
     * @return {@code true} if thread has finished, {@code false} otherwise.
     */
    public static boolean joinThreads(Iterable<? extends Thread> workers, GridLogger log) {
        boolean retval = true;

        if (workers != null)
            for (Thread worker : workers)
                if (!join(worker, log))
                    retval = false;

        return retval;
    }

    /**
     * Cancels given runnable.
     *
     * @param w Worker to cancel - it's no-op if runnable is {@code null}.
     */
    public static void cancel(GridWorker w) {
        if (w != null)
            w.cancel();
    }

    /**
     * Cancels collection of runnables.
     *
     * @param ws Collection of workers - it's no-op if collection is {@code null}.
     */
    public static void cancel(Iterable<? extends GridWorker> ws) {
        if (ws != null)
            for (GridWorker w : ws)
                w.cancel();
    }

    /**
     * Joins runnable.
     *
     * @param w Worker to join.
     * @param log The logger to possible exception.
     * @return {@code true} if worker has not been interrupted, {@code false} if it was interrupted.
     */
    public static boolean join(GridWorker w, GridLogger log) {
        if (w != null)
            try {
                w.join();
            }
            catch (InterruptedException ignore) {
                warn(log, "Got interrupted while waiting for completion of runnable: " + w);

                return false;
            }

        return true;
    }

    /**
     * Joins given collection of runnables.
     *
     * @param ws Collection of workers to join.
     * @param log The logger to possible exceptions.
     * @return {@code true} if none of the worker have been interrupted,
     *      {@code false} if at least one was interrupted.
     */
    public static boolean join(Iterable<? extends GridWorker> ws, GridLogger log) {
        boolean retval = true;

        if (ws != null)
            for (GridWorker w : ws)
                if (!join(w, log))
                    retval = false;

        return retval;
    }

    /**
     * Shutdowns given {@code ExecutorService} and wait for executor service to stop.
     *
     * @param owner The ExecutorService owner.
     * @param exec ExecutorService to shutdown.
     * @param log The logger to possible exceptions and warnings.
     */
    public static void shutdownNow(Class<?> owner, ExecutorService exec, GridLogger log) {
        if (exec != null) {
            List<Runnable> tasks = exec.shutdownNow();

            if (!F.isEmpty(tasks))
                U.warn(log, "Runnable tasks outlived thread pool executor service [owner=" + getSimpleName(owner) +
                    ", tasks=" + tasks + ']');

            try {
                exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException ignore) {
                warn(log, "Got interrupted while waiting for executor service to stop.");
            }
        }
    }

    /**
     * Writes UUIDs to output stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param col UUIDs to write.
     * @throws IOException If write failed.
     */
    public static void writeUuids(DataOutput out, @Nullable Collection<UUID> col) throws IOException {
        // Write null flag.
        out.writeBoolean(col == null);

        if (col != null) {
            out.writeInt(col.size());

            for (UUID id : col)
                writeUuid(out, id);
        }
    }


    /**
     * Reads UUIDs from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read UUIDs.
     * @throws IOException If read failed.
     */
    @Nullable public static List<UUID> readUuids(DataInput in) throws IOException {
        List<UUID> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<UUID>(size);

            for (int i = 0; i < size; i++)
                col.add(readUuid(in));
        }

        return col;
    }

    /**
     * Writes UUID to output stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param out Output stream.
     * @param uid UUID to write.
     * @throws IOException If write failed.
     */
    public static void writeUuid(DataOutput out, UUID uid) throws IOException {
        // Write null flag.
        out.writeBoolean(uid == null);

        if (uid != null) {
            out.writeLong(uid.getMostSignificantBits());
            out.writeLong(uid.getLeastSignificantBits());
        }
    }

    /**
     * Reads UUID from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read UUID.
     * @throws IOException If read failed.
     */
    @Nullable public static UUID readUuid(DataInput in) throws IOException {
        // If UUID is not null.
        if (!in.readBoolean()) {
            long most = in.readLong();
            long least = in.readLong();

            return new UUID(most, least);
        }

        return null;
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
        // Write null flag.
        out.writeBoolean(uid == null);

        if (uid != null) {
            writeUuid(out, uid.globalId());

            out.writeLong(uid.localId());
        }
    }

    /**
     * Reads {@link GridUuid} from input stream. This method is meant to be used by
     * implementations of {@link Externalizable} interface.
     *
     * @param in Input stream.
     * @return Read UUID.
     * @throws IOException If read failed.
     */
    @Nullable public static GridUuid readGridUuid(DataInput in) throws IOException {
        // If UUID is not null.
        if (!in.readBoolean()) {
            UUID globalId = readUuid(in);

            long locId = in.readLong();

            return new GridUuid(globalId, locId);
        }

        return null;
    }

    /**
     * Writes byte array to output stream accounting for <tt>null</tt> values.
     *
     * @param out Output stream to write to.
     * @param arr Array to write, possibly <tt>null</tt>.
     * @throws IOException If write failed.
     */
    public static void writeByteArray(ObjectOutput out, byte[] arr) throws IOException {
        if (arr == null)
            out.writeInt(-1);
        else {
            out.writeInt(arr.length);

            out.write(arr);
        }
    }

    /**
     * Reads byte array from input stream accounting for <tt>null</tt> values.
     *
     * @param in Stream to read from.
     * @return Read byte array, possibly <tt>null</tt>.
     * @throws IOException If read failed.
     */
    @Nullable public static byte[] readByteArray(DataInput in) throws IOException {
        int len = in.readInt();

        if (len == -1)
            return null; // Value "-1" indicates null.

        byte[] res = new byte[len];

        in.readFully(res);

        return res;
    }

    /**
     * @param out Output.
     * @param map Map to write.
     * @throws IOException If write failed.
     */
    public static void writeMap(ObjectOutput out, Map<?, ?> map) throws IOException {
        // Write null flag.
        out.writeBoolean(map == null);

        if (map != null) {
            out.writeInt(map.size());

            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.writeObject(e.getKey());
                out.writeObject(e.getValue());
            }
        }
    }

    /**
     *
     * @param in Input.
     * @return Read map.
     * @throws IOException If de-serialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <K, V> Map<K, V> readMap(ObjectInput in) throws IOException, ClassNotFoundException {
        Map<K, V> map = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            map = new HashMap<K, V>(size, 1.0f);

            for (int i = 0; i < size; i++)
                map.put((K)in.readObject(), (V)in.readObject());
        }

        return map;
    }

    /**
     * @param out Output.
     * @param map Map to write.
     * @throws IOException If write failed.
     */
    public static void writeIntKeyMap(ObjectOutput out, Map<Integer, ?> map) throws IOException {
        // Write null flag.
        out.writeBoolean(map == null);

        if (map != null) {
            out.writeInt(map.size());

            for (Map.Entry<Integer, ?> e : map.entrySet()) {
                out.writeInt(e.getKey());
                out.writeObject(e.getValue());
            }
        }
    }

    /**
     *
     * @param in Input.
     * @return Read map.
     * @throws IOException If de-serialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <V> Map<Integer, V> readIntKeyMap(ObjectInput in) throws IOException,
        ClassNotFoundException {
        Map<Integer, V> map = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            map = new HashMap<Integer, V>(size, 1.0f);

            for (int i = 0; i < size; i++)
                map.put(in.readInt(), (V)in.readObject());
        }

        return map;
    }

    /**
     * @param in Input.
     * @return Deserialized list.
     * @throws IOException If deserialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <E> List<E> readList(ObjectInput in) throws IOException, ClassNotFoundException {
        List<E> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<E>(size);

            for (int i = 0; i < size; i++)
                col.add((E)in.readObject());
        }

        return col;
    }

    /**
     * @param in Input.
     * @return Deserialized list.
     * @throws IOException If deserialization failed.
     */
    @Nullable public static List<Integer> readIntList(DataInput in) throws IOException {
        List<Integer> col = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            col = new ArrayList<Integer>(size);

            for (int i = 0; i < size; i++)
                col.add(in.readInt());
        }

        return col;
    }

    /**
     * @param in Input.
     * @return Deserialized set.
     * @throws IOException If deserialization failed.
     * @throws ClassNotFoundException If deserialized class could not be found.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable public static <E> Set<E> readSet(ObjectInput in) throws IOException, ClassNotFoundException {
        Set<E> set = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            set = new HashSet(size, 1.0f);

            for (int i = 0; i < size; i++)
                set.add((E)in.readObject());
        }

        return set;
    }

    /**
     * @param in Input.
     * @return Deserialized set.
     * @throws IOException If deserialization failed.
     */
    @Nullable public static Set<Integer> readIntSet(DataInput in) throws IOException {
        Set<Integer> set = null;

        // Check null flag.
        if (!in.readBoolean()) {
            int size = in.readInt();

            set = new HashSet<Integer>(size, 1.0f);

            for (int i = 0; i < size; i++)
                set.add(in.readInt());
        }

        return set;
    }

    /**
     * Writes string to output stream accounting for {@code null} values.
     *
     * @param out Output stream to write to.
     * @param s String to write, possibly {@code null}.
     * @throws IOException If write failed.
     */
    public static void writeString(DataOutput out, String s) throws IOException {
        // Write null flag.
        out.writeBoolean(s == null);

        if (s != null)
            out.writeUTF(s);
    }

    /**
     * Reads string from input stream accounting for {@code null} values.
     *
     * @param in Stream to read from.
     * @return Read string, possibly {@code null}.
     * @throws IOException If read failed.
     */
    @Nullable public static String readString(DataInput in) throws IOException {
        // If value is not null, then read it. Otherwise return null.
        return !in.readBoolean() ? in.readUTF() : null;
    }

    /**
     * Writes enum to output stream accounting for {@code null} values.
     *
     * @param out Output stream to write to.
     * @param e Enum value to write, possibly {@code null}.
     * @throws IOException If write failed.
     */
    public static <E extends Enum> void writeEnum(DataOutput out, E e) throws IOException {
        out.writeBoolean(e == null);

        if (e != null)
            out.writeInt(e.ordinal());
    }

    /**
     * Reads enum from input stream accounting for {@code null} values.
     *
     * @param in Stream to read from.
     * @param cls Enum class.
     * @return Read enum value, possibly {@code null}.
     * @throws IOException If read failed.
     */
    @Nullable public static <E extends Enum> E readEnum(DataInput in, Class<E> cls) throws IOException {
        return !in.readBoolean() ? enumFromOrdinal(cls, in.readInt()) : null;
    }

    /**
     * Gets enum value from its ordinal number.
     *
     * @param cls Enum class to get value of.
     * @param ord Constant ordinal number.
     * @return Enum value or {@code null} if value with such ordinal number is not found.
     */
    @Nullable public static <E extends Enum> E enumFromOrdinal(Class<E> cls, int ord) {
        E vals[] = cls.getEnumConstants();

        return vals != null && ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    /**
     * Gets collection value by index.
     *
     * @param vals Collection of values.
     * @param idx Index of value in the collection.
     * @param <T> Type of collection values.
     * @return Value at the given index.
     */
    public static <T> T getByIndex(Collection<T> vals, int idx) {
        assert idx < vals.size();

        int i = 0;

        for (T val : vals) {
            if (idx == i)
                return val;

            i++;
        }

        assert false : "Should never be reached.";

        return null;
    }

    /**
     * Gets annotation for a class.
     *
     * @param <T> Type of annotation to return.
     * @param cls Class to get annotation from.
     * @param annCls Annotation to get.
     * @return Instance of annotation, or {@code null} if not found.
     */
    @Nullable public static <T extends Annotation> T getAnnotation(Class<?> cls, Class<T> annCls) {
        for (Class<?> c = cls; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
            T ann = c.getAnnotation(annCls);

            if (ann != null)
                return ann;
        }

        return null;
    }

    /**
     * Gets simple class name taking care of empty names.
     *
     * @param cls Class to get the name for.
     * @return Simple class name.
     */
    public static String getSimpleName(Class<?> cls) {
        return cls.getSimpleName().length() == 0 ? cls.getName() : cls.getSimpleName();
    }

    /**
     * Checks if the map passed in is contained in base map.
     *
     * @param base Base map.
     * @param map Map to check.
     * @return {@code True} if all entries within map are contained in base map,
     *      {@code false} otherwise.
     */
    @SuppressWarnings({"SuspiciousMethodCalls"})
    public static boolean containsAll(Map<?, ?> base, Map<?, ?> map) {
        assert base != null;
        assert map != null;

        for (Map.Entry<?, ?> entry : map.entrySet())
            if (base.containsKey(entry.getKey())) {
                Object val = base.get(entry.getKey());

                if (val == null && entry.getValue() == null)
                    continue;

                if (val == null || entry.getValue() == null || !val.equals(entry.getValue()))
                    // Mismatch found.
                    return false;
            }
            else
                return false;

        // All entries in 'map' are contained in base map.
        return true;
    }

    /**
     * Gets task name for the given task class.
     *
     * @param taskCls Task class.
     * @return Either task name from class annotation (see {@link GridTaskName}})
     *      or task class name if there is no annotation.
     */
    public static String getTaskName(Class<? extends GridTask<?, ?>> taskCls) {
        GridTaskName nameAnn = getAnnotation(taskCls, GridTaskName.class);

        return nameAnn == null ? taskCls.getName() : nameAnn.value();
    }

    /**
     * Creates SPI attribute name by adding prefix to the attribute name.
     * Prefix is an SPI name + '.'.
     *
     * @param spi SPI.
     * @param attrName attribute name.
     * @return SPI attribute name.
     */
    public static String spiAttribute(GridSpi spi, String attrName) {
        assert spi != null;
        assert spi.getName() != null;

        return spi.getName() + '.' + attrName;
    }

    /**
     * Gets resource path for the class.
     *
     * @param clsName Class name.
     * @return Resource name for the class.
     */
    public static String classNameToResourceName(String clsName) {
        return clsName.replaceAll("\\.", "/") + ".class";
    }

    /**
     * Gets runtime MBean.
     *
     * @return Runtime MBean.
     */
    public static RuntimeMXBean getRuntimeMx() {
        return ManagementFactory.getRuntimeMXBean();
    }

    /**
     * Gets threading MBean.
     *
     * @return Threading MBean.
     */
    public static ThreadMXBean getThreadMx() {
        return ManagementFactory.getThreadMXBean();
    }

    /**
     * Gets OS MBean.
     * @return OS MBean.
     */
    public static OperatingSystemMXBean getOsMx() {
        return ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Gets memory MBean.
     *
     * @return Memory MBean.
     */
    public static MemoryMXBean getMemoryMx() {
        return ManagementFactory.getMemoryMXBean();
    }

    /**
     * Gets compilation MBean.
     *
     * @return Compilation MBean.
     */
    public static CompilationMXBean getCompilerMx() {
        return ManagementFactory.getCompilationMXBean();
    }

    /**
     * Detects class loader for given class and returns class loaded by appropriate class loader.
     * This method will first check if {@link Thread#getContextClassLoader()} is appropriate.
     * If yes, then context class loader will be returned, otherwise
     * the {@link Class#getClassLoader()} will be returned.
     *
     * @param cls Class to find class loader for.
     * @return Class loader for given class (never {@code null}}
     */
    public static ClassLoader detectClassLoader(Class<?> cls) {
        ClassLoader ldr = Thread.currentThread().getContextClassLoader();

        if (ldr != null) {
            if (ldr == cls.getClassLoader())
                return ldr;

            try {
                // Check if context loader is wider than direct object class loader.
                Class<?> c = Class.forName(cls.getName(), true, ldr);

                if (c == cls)
                    return ldr;
            }
            catch (ClassNotFoundException ignored) {
                // No-op.
            }
        }

        ldr = cls.getClassLoader();

        // Class loader is null for JDK classes in certain environments.
        if (ldr == null)
            ldr = GridUtils.class.getClassLoader();

        return ldr;
    }

    /**
     * Tests whether or not given class is loadable provided class loader.
     *
     * @param clsName Class name to test.
     * @param ldr Class loader to test with. If {@code null} - we'll use system class loader instead.
     *      If System class loader is not set - this method will return {@code false}.
     * @return {@code True} if class is loadable, {@code false} otherwise.
     */
    public static boolean isLoadableBy(String clsName, @Nullable ClassLoader ldr) {
        assert clsName != null;

        if (ldr == null)
            ldr = ClassLoader.getSystemClassLoader();

        if (ldr == null)
            return false;

        try {
            ldr.loadClass(clsName);

            return true;
        }
        catch (ClassNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Gets the peer deploy aware instance for the object with the widest class loader.
     * If collection is {@code null}, empty or contains only {@code null}s - the peer
     * deploy aware object based on system class loader will be returned.
     *
     * @param c Collection.
     * @return Peer deploy aware object from this collection with the widest class loader.
     * @throws IllegalArgumentException Thrown in case when common class loader for all
     *      elements in this collection cannot be found. In such case - peer deployment
     *      is not possible.
     */
    public static GridPeerDeployAware peerDeployAware0(@Nullable Iterable<?> c) {
        if (!F.isEmpty(c)) {
            assert c != null;

            boolean notAllNulls = false;

            for (Object obj : c)
                if (obj != null) {
                    notAllNulls = true;

                    ClassLoader ldr = obj.getClass().getClassLoader();

                    boolean found = true;

                    for (Object obj2 : c)
                        if (obj2 != null && obj2 != obj && !isLoadableBy(obj2.getClass().getName(), ldr)) {
                            found = false;

                            break;
                        }

                    if (found)
                        return peerDeployAware(obj);
                }

            // If all are nulls - don't throw an exception.
            if (notAllNulls)
                throw new IllegalArgumentException("Failed to find common class loader for all elements in " +
                    "given collection. Peer deployment cannot be performed for such collection.");
        }

        return peerDeployAware(Collections.<Object>emptyList());
    }

    /**
     * Gets the peer deploy aware instance for the object with the widest class loader.
     * If collection is {@code null}, empty or contains only {@code null}s - the peer
     * deploy aware object based on system class loader will be returned.
     *
     * @param c Collection.
     * @return Peer deploy aware object from this collection with the widest class loader.
     * @throws IllegalArgumentException Thrown in case when common class loader for all
     *      elements in this collection cannot be found. In such case - peer deployment
     *      is not possible.
     */
    public static GridPeerDeployAware peerDeployAware0(@Nullable Object... c) {
        if (!F.isEmpty(c)) {
            assert c != null;

            boolean notAllNulls = false;

            for (Object obj : c)
                if (obj != null) {
                    notAllNulls = true;

                    ClassLoader ldr = obj.getClass().getClassLoader();

                    boolean found = true;

                    for (Object obj2 : c)
                        if (obj2 != null && obj2 != obj && !isLoadableBy(obj2.getClass().getName(), ldr)) {
                            found = false;

                            break;
                        }

                    if (found)
                        return peerDeployAware(obj);
                }

            // If all are nulls - don't throw an exception.
            if (notAllNulls)
                throw new IllegalArgumentException("Failed to find common class loader for all elements in " +
                    "given collection. Peer deployment cannot be performed for such collection.");
        }

        return peerDeployAware(Collections.<Object>emptyList());
    }

    /**
     * Creates an instance of {@link GridPeerDeployAware} for object.
     *
     * @param obj Object to deploy.
     * @return {@link GridPeerDeployAware} instance for given object.
     */
    public static GridPeerDeployAware peerDeployAware(Object obj) {
        assert obj != null;

        if (obj instanceof GridPeerDeployAware)
            return (GridPeerDeployAware)obj;

        final Class<?> cls = obj instanceof Class ? (Class)obj : obj.getClass();

        return new GridPeerDeployAware() {
            @Override public Class<?> deployClass() {
                return cls;
            }

            @Override public ClassLoader classLoader() {
                return cls.getClassLoader();
            }
        };
    }

    /**
     * Unwraps top level user class for wrapped objects.
     *
     * @param obj Object to check.
     * @return Top level user class.
     */
    public static GridPeerDeployAware detectPeerDeployAware(GridPeerDeployAware obj) {
        GridPeerDeployAware p = nestedPeerDeployAware(obj, true, new GridIdentityHashSet<Object>(3));

        // Pass in obj.getClass() to avoid infinite recursion.
        return p != null ? p : peerDeployAware(obj.getClass());
    }

    /**
     * Gets peer deploy class if there is any {@link GridPeerDeployAware} within reach.
     *
     * @param obj Object to check.
     * @param top Indicates whether object is top level or a nested field.
     * @param processed Set of processed objects to avoid infinite recursion.
     * @return Peer deploy class, or {@code null} if one could not be found.
     */
    @Nullable private static GridPeerDeployAware nestedPeerDeployAware(Object obj, boolean top, Set<Object> processed) {
        // Avoid infinite recursion.
        if (!processed.add(obj))
            return null;

        if (obj instanceof GridPeerDeployAware) {
            GridPeerDeployAware p = (GridPeerDeployAware)obj;

            if (!top && p.deployClass() != null)
                return p;

            for (Class<?> cls = obj.getClass(); !cls.equals(Object.class); cls = cls.getSuperclass()) {
                // Cache by class name instead of class to avoid infinite growth of the
                // caching map in case of multiple redeployment of the same class.
                GridTuple2<Class<?>, Collection<Field>> tup = p2pFields.get(cls.getName());

                boolean cached = tup != null && tup.get1().equals(cls);

                Iterable<Field> fields = cached ? tup.get2() : Arrays.asList(cls.getDeclaredFields());

                if (!cached) {
                    tup = F.t2();

                    tup.set1(cls);
                }

                for (Field f : fields)
                    // Special handling for anonymous classes.
                    if (cached || f.getName().startsWith("this$") || f.getName().startsWith("val$")) {
                        if (!cached) {
                            f.setAccessible(true);

                            if (tup.get2() == null)
                                tup.set2(new LinkedList<Field>());

                            tup.get2().add(f);
                        }

                        try {
                            Object o = f.get(obj);

                            if (o != null) {
                                // Recursion.
                                p = nestedPeerDeployAware(o, false, processed);

                                if (p != null) {
                                    if (!cached)
                                        // Potentially replace identical value
                                        // stored by another thread.
                                        p2pFields.put(cls.getName(), tup);

                                    return p;
                                }
                            }
                        }
                        catch (IllegalAccessException ignored) {
                            return null;
                        }
                    }
            }
        }
        // Don't go into internal GridGain structures.
        else if (isGridGain(obj.getClass()))
            return null;
        else if (obj instanceof Iterable)
            for (Object o : (Iterable<?>)obj) {
                // Recursion.
                GridPeerDeployAware p = nestedPeerDeployAware(o, false, processed);

                if (p != null)
                    return p;
            }
        else if (obj.getClass().isArray()) {
            Class<?> type = obj.getClass().getComponentType();

            // We don't care about primitives or internal JDK types.
            if (!type.isPrimitive() && !isJdk(type)) {
                Object[] arr = (Object[])obj;

                for (Object o : arr) {
                    // Recursion.
                    GridPeerDeployAware p = nestedPeerDeployAware(o, false, processed);

                    if (p != null)
                        return p;
                }
            }
        }

        return null;
    }

    /**
     * Checks if given class is of {@code GridGain} type.
     *
     * @param cls Class to check.
     * @return {@code True} if given class is of {@code GridGain} type.
     */
    public static boolean isGridGain(Class<?> cls) {
        return cls.getName().startsWith("org.gridgain");
    }

    /**
     * Checks if given class is of {@code Grid} type.
     *
     * @param cls Class to check.
     * @return {@code True} if given class is of {@code Grid} type.
     */
    public static boolean isGrid(Class<?> cls) {
        return cls.getName().startsWith("org.gridgain.grid");
    }

    /**
     * Replaces all occurrences of {@code org.gridgain.grid.} with {@code o.g.g.},
     * {@code org.gridgain.visor.} with {@code o.g.v.}, and {@code org.gridgain.scalar.} with {@code o.g.s.}.
     *
     * @param s String to replace in.
     * @return Replaces string.
     */
    public static String compact(String s) {
        return s.replace("org.gridgain.grid.", "o.g.g.").
            replace("org.gridgain.visor.", "o.g.v.").
            replace("org.gridgain.scalar.", "o.g.s.");
    }

    /**
     * Check if given class is of JDK type.
     *
     * @param cls Class to check.
     * @return {@code True} if object is JDK type.
     */
    public static boolean isJdk(Class<?> cls) {
        String s = cls.getName();

        return s.startsWith("java.") || s.startsWith("javax.");
    }

    /**
     * Converts {@link InterruptedException} to {@link GridException}.
     *
     * @param mux Mux to wait on.
     * @throws GridInterruptedException If interrupted.
     */
    @SuppressWarnings({"WaitNotInLoop", "WaitWhileNotSynced"})
    public static void wait(Object mux) throws GridInterruptedException {
        try {
            mux.wait();
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /**
     * Unzip file to folder.
     *
     * @param zipFile ZIP file.
     * @param toDir Directory to unzip file content.
     * @param log Grid logger.
     * @throws IOException In case of error.
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static void unzip(File zipFile, File toDir, GridLogger log) throws IOException {
        ZipFile zip = null;

        try {
            zip = new ZipFile(zipFile);

            for (ZipEntry entry : asIterable(zip.entries())) {
                if (entry.isDirectory()) {
                    // Assume directories are stored parents first then children.
                    new File(toDir, entry.getName()).mkdirs();

                    continue;
                }

                InputStream in = null;
                OutputStream out = null;

                try {
                    in = zip.getInputStream(entry);

                    File outFile = new File(toDir, entry.getName());

                    if (!outFile.getParentFile().exists())
                        outFile.getParentFile().mkdirs();

                    out = new BufferedOutputStream(new FileOutputStream(outFile));

                    copy(in, out);
                }
                finally {
                    close(in, log);
                    close(out, log);
                }
            }
        }
        finally {
            if (zip != null)
                zip.close();
        }
    }

    /**
     * Gets OS JDK string.
     *
     * @return OS JDK string.
     */
    public static String osJdkString() {
        return osJdkStr;
    }

    /**
     * Gets OS string.
     *
     * @return OS string.
     */
    public static String osString() {
        return osStr;
    }

    /**
     * Gets JDK string.
     *
     * @return JDK string.
     */
    public static String jdkString() {
        return jdkStr;
    }

    /**
     * Indicates whether current OS is Linux flavor.
     *
     * @return {@code true} if current OS is Linux - {@code false} otherwise.
     */
    public static boolean isLinux() {
        return linux;
    }

    /**
     * Gets JDK name.
     * @return JDK name.
     */
    public static String jdkName() {
        return jdkName;
    }

    /**
     * Gets JDK vendor.
     *
     * @return JDK vendor.
     */
    public static String jdkVendor() {
        return jdkVendor;
    }

    /**
     * Gets JDK version.
     *
     * @return JDK version.
     */
    public static String jdkVersion() {
        return jdkVer;
    }

    /**
     * Gets OS CPU-architecture.
     *
     * @return OS CPU-architecture.
     */
    public static String osArchitecture() {
        return osArch;
    }

    /**
     * Gets underlying OS name.
     *
     * @return Underlying OS name.
     */
    public static String osName() {
        return osName;
    }

    /**
     * Gets underlying OS version.
     *
     * @return Underlying OS version.
     */
    public static String osVersion() {
        return osVer;
    }

    /**
     * Indicates whether current OS is Mac OS.
     *
     * @return {@code true} if current OS is Mac OS - {@code false} otherwise.
     */
    public static boolean isMacOs() {
        return mac;
    }

    /**
     * Indicates whether current OS is Netware.
     *
     * @return {@code true} if current OS is Netware - {@code false} otherwise.
     */
    public static boolean isNetWare() {
        return netware;
    }

    /**
     * Indicates whether current OS is Solaris.
     *
     * @return {@code true} if current OS is Solaris (SPARC or x86) - {@code false} otherwise.
     */
    public static boolean isSolaris() {
        return solaris;
    }

    /**
     * Indicates whether current OS is Solaris on Spark box.
     *
     * @return {@code true} if current OS is Solaris SPARC - {@code false} otherwise.
     */
    public static boolean isSolarisSparc() {
        return solaris && sparc;
    }

    /**
     * Indicates whether current OS is Solaris on x86 box.
     *
     * @return {@code true} if current OS is Solaris x86 - {@code false} otherwise.
     */
    public static boolean isSolarisX86() {
        return solaris && x86;
    }

    /**
     * Indicates whether current OS is UNIX flavor.
     *
     * @return {@code true} if current OS is UNIX - {@code false} otherwise.
     */
    public static boolean isUnix() {
        return unix;
    }

    /**
     * Indicates whether current OS is Windows.
     *
     * @return {@code true} if current OS is Windows (any versions) - {@code false} otherwise.
     */
    public static boolean isWindows() {
        return winXp || win95 || win98 || winNt || win2k ||
            win2003 || winVista || win7;
    }

    /**
     * Indicates whether current OS is Windows Vista.
     *
     * @return {@code true} if current OS is Windows Vista - {@code false} otherwise.
     */
    public static boolean isWindowsVista() {
        return winVista;
    }

    /**
     * Indicates whether current OS is Windows 7.
     *
     * @return {@code true} if current OS is Windows 7 - {@code false} otherwise.
     */
    public static boolean isWindows7() {
        return win7;
    }

    /**
     * Indicates whether current OS is Windows 2000.
     *
     * @return {@code true} if current OS is Windows 2000 - {@code false} otherwise.
     */
    public static boolean isWindows2k() {
        return win2k;
    }

    /**
     * Indicates whether current OS is Windows Server 2003.
     *
     * @return {@code true} if current OS is Windows Server 2003 - {@code false} otherwise.
     */
    public static boolean isWindows2003() {
        return win2003;
    }

    /**
     * Indicates whether current OS is Windows 95.
     *
     * @return {@code true} if current OS is Windows 95 - {@code false} otherwise.
     */
    public static boolean isWindows95() {
        return win95;
    }

    /**
     * Indicates whether current OS is Windows 98.
     *
     * @return {@code true} if current OS is Windows 98 - {@code false} otherwise.
     */
    public static boolean isWindows98() {
        return win98;
    }

    /**
     * Indicates whether current OS is Windows NT.
     *
     * @return {@code true} if current OS is Windows NT - {@code false} otherwise.
     */
    public static boolean isWindowsNt() {
        return winNt;
    }

    /**
     * Indicates that GridGain has been sufficiently tested on the current OS.
     *
     * @return {@code true} if current OS was sufficiently tested - {@code false} otherwise.
     */
    public static boolean isSufficientlyTestedOs() {
        return
            win2k ||
                win7 ||
                winXp ||
                winVista ||
                mac ||
                linux ||
                solaris;
    }

    /**
     * Indicates whether current OS is Windows XP.
     *
     * @return {@code true} if current OS is Windows XP- {@code false} otherwise.
     */
    public static boolean isWindowsXp() {
        return winXp;
    }

    /**
     * Gets JVM specification name.
     *
     * @return JVM specification name.
     */
    public static String jvmSpec() {
        return jvmSpecName;
    }

    /**
     * Gets JVM implementation version.
     *
     * @return JVM implementation version.
     */
    public static String jvmVersion() {
        return jvmImplVer;
    }

    /**
     * Gets JVM implementation vendor.
     *
     * @return JVM implementation vendor.
     */
    public static String jvmVendor() {
        return jvmImplVendor;
    }

    /**
     * Gets JVM implementation name.
     *
     * @return JVM implementation name.
     */
    public static String jvmName() {
        return jvmImplName;
    }

    /**
     * Gets Java Runtime name.
     *
     * @return Java Runtime name.
     */
    public static String jreName() {
        return javaRtName;
    }

    /**
     * Gets Java Runtime version.
     *
     * @return Java Runtime version.
     */
    public static String jreVersion() {
        return javaRtVer;
    }

    /**
     * Sets thread context class loader to the given loader, executes the closure, and then
     * resets thread context class loader to its initial value.
     *
     * @param ldr Class loader to run the closure under.
     * @param c Callable to run.
     * @param <R> Return type.
     * @return Return value.
     * @throws GridException If call failed.
     */
    @Nullable public static <R> R withThreadLoader(ClassLoader ldr, Callable<R> c) throws GridException {
        Thread curThread = Thread.currentThread();

        // Get original context class loader.
        ClassLoader ctxLoader = curThread.getContextClassLoader();

        //noinspection CatchGenericClass
        try {
            curThread.setContextClassLoader(ldr);

            return c.call();
        }
        catch (GridException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new GridException(e);
        }
        finally {
            // Set the original class loader back.
            curThread.setContextClassLoader(ctxLoader);
        }
    }

    /**
     * Sets thread context class loader to the given loader, executes the closure, and then
     * resets thread context class loader to its initial value.
     *
     * @param ldr Class loader to run the closure under.
     * @param c Closure to run.
     * @param <R> Return type.
     * @return Return value.
     */
    @Nullable public static <R> R wrapThreadLoader(ClassLoader ldr, GridOutClosure<R> c) {
        Thread curThread = Thread.currentThread();

        // Get original context class loader.
        ClassLoader ctxLdr = curThread.getContextClassLoader();

        try {
            curThread.setContextClassLoader(ldr);

            return c.apply();
        }
        finally {
            // Set the original class loader back.
            curThread.setContextClassLoader(ctxLdr);
        }
    }

    /**
     * Sets thread context class loader to the given loader, executes the closure, and then
     * resets thread context class loader to its initial value.
     *
     * @param ldr Class loader to run the closure under.
     * @param c Closure to run.
     */
    public static void wrapThreadLoader(ClassLoader ldr, Runnable c) {
        Thread curThread = Thread.currentThread();

        // Get original context class loader.
        ClassLoader ctxLdr = curThread.getContextClassLoader();

        try {
            curThread.setContextClassLoader(ldr);

            c.run();
        }
        finally {
            // Set the original class loader back.
            curThread.setContextClassLoader(ctxLdr);
        }
    }

    /**
     * Short node representation.
     *
     * @param n Grid node.
     * @return Short string representing the node.
     */
    public static String toShortString(GridNode n) {
        return "GridNode [id=" + n.id() + ", intAddr=" + n.internalAddresses() + ", extAddr=" +
            n.externalAddresses() + ']';
    }

    /**
     * Short node representation.
     *
     * @param ns Grid nodes.
     * @return Short string representing the node.
     */
    public static String toShortString(Collection<? extends GridNode> ns) {
        SB sb = new SB("Grid nodes [cnt=" + ns.size());

        for (GridNode n : ns)
            sb.a(", ").a(toShortString(n));

        return sb.a(']').toString();
    }

    /**
     * Converts collection of integers into array.
     *
     * @param c Collection of integers.
     * @return Integer array.
     */
    public static int[] toIntArray(@Nullable Collection<Integer> c) {
        if (c == null || c.isEmpty())
            return EMPTY_INTS;

        int[] arr = new int[c.size()];

        int idx = 0;

        for (Integer i : c)
            arr[idx++] = i;

        return arr;
    }

    /**
     * Converts array of integers into list.
     *
     * @param arr Array of integers.
     * @param p Optional predicate array.
     * @return List of integers.
     */
    public static List<Integer> toIntList(@Nullable int[] arr, GridPredicate<Integer>... p) {
        if (arr == null || arr.length == 0)
            return Collections.emptyList();

        List<Integer> ret = new ArrayList<Integer>(arr.length);

        if (F.isEmpty(p))
            for (int i : arr)
                ret.add(i);
        else {
            for (int i : arr)
                if (F.isAll(i, p))
                    ret.add(i);
        }

        return ret;
    }

    /**
     * Converts collection of integers into array.
     *
     * @param c Collection of integers.
     * @return Integer array.
     */
    public static long[] toLongArray(@Nullable Collection<Long> c) {
        if (c == null || c.isEmpty())
            return EMPTY_LONGS;

        long[] arr = new long[c.size()];

        int idx = 0;

        for (Long l : c)
            arr[idx++] = l;

        return arr;
    }

    /**
     * Converts array of longs into list.
     *
     * @param arr Array of longs.
     * @return List of longs.
     */
    public static List<Long> toLongList(@Nullable long[] arr) {
        if (arr == null || arr.length == 0)
            return Collections.emptyList();

        List<Long> ret = new ArrayList<Long>(arr.length);

        for (long l : arr)
            ret.add(l);

        return ret;
    }

    /**
     * Copies all elements from collection to array and asserts that
     * array is big enough to hold the collection. This method should
     * always be preferred to {@link Collection#toArray(Object[])}
     * method.
     *
     * @param c Collection to convert to array.
     * @param arr Array to populate.
     * @param <T> Element type.
     * @return Passed in array.
     */
    @SuppressWarnings({"MismatchedReadAndWriteOfArray"})
    public static <T> T[] toArray(Collection<? extends T> c, T[] arr) {
        T[] a = c.toArray(arr);

        assert a == arr;

        return arr;
    }

    /**
     *
     * @param t Tokenizer.
     * @param str Input string.
     * @param date Date.
     * @return Next token.
     * @throws GridException Thrown in case of any errors.
     */
    private static boolean checkNextToken(StringTokenizer t, String str, String date) throws GridException {
        try {
            if (t.nextToken().equals(str))
                return true;
            else
                throw new GridException("Invalid date format: " + date);
        }
        catch (NoSuchElementException ignored) {
            return false;
        }
    }

    /**
     *
     * @param str ISO date.
     * @return Calendar instance.
     * @throws GridException Thrown in case of any errors.
     */
    public static Calendar parseIsoDate(String str) throws GridException {
        StringTokenizer t = new StringTokenizer(str, "+-:.TZ", true);

        Calendar cal = Calendar.getInstance();
        cal.clear();

        try {
            if (t.hasMoreTokens())
                cal.set(Calendar.YEAR, Integer.parseInt(t.nextToken()));
            else
                return cal;

            if (checkNextToken(t, "-", str) && t.hasMoreTokens())
                cal.set(Calendar.MONTH, Integer.parseInt(t.nextToken()) - 1);
            else
                return cal;

            if (checkNextToken(t, "-", str) && t.hasMoreTokens())
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(t.nextToken()));
            else
                return cal;

            if (checkNextToken(t, "T", str) && t.hasMoreTokens())
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t.nextToken()));
            else {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                return cal;
            }

            if (checkNextToken(t, ":", str) && t.hasMoreTokens())
                cal.set(Calendar.MINUTE, Integer.parseInt(t.nextToken()));
            else {
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                return cal;
            }

            if (!t.hasMoreTokens())
                return cal;

            String token = t.nextToken();

            if (":".equals(token)) { // Seconds.
                if (t.hasMoreTokens()) {
                    cal.set(Calendar.SECOND, Integer.parseInt(t.nextToken()));

                    if (!t.hasMoreTokens())
                        return cal;

                    token = t.nextToken();

                    if (".".equals(token)) {
                        String nt = t.nextToken();

                        while (nt.length() < 3)
                            nt += "0";

                        nt = nt.substring(0, 3); // Cut trailing chars.

                        cal.set(Calendar.MILLISECOND, Integer.parseInt(nt));

                        if (!t.hasMoreTokens())
                            return cal;

                        token = t.nextToken();
                    }
                    else
                        cal.set(Calendar.MILLISECOND, 0);
                }
                else
                    throw new GridException("Invalid date format: " + str);
            }
            else {
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }

            if (!"Z".equals(token)) {
                if (!"+".equals(token) && !"-".equals(token))
                    throw new GridException("Invalid date format: " + str);

                boolean plus = "+".equals(token);

                if (!t.hasMoreTokens())
                    throw new GridException("Invalid date format: " + str);

                token = t.nextToken();

                int tzHour;
                int tzMin;

                if (token.length() == 4) {
                    tzHour = Integer.parseInt(token.substring(0, 2));
                    tzMin = Integer.parseInt(token.substring(2, 4));
                }
                else {
                    tzHour = Integer.parseInt(token);

                    if (checkNextToken(t, ":", str) && t.hasMoreTokens())
                        tzMin = Integer.parseInt(t.nextToken());
                    else
                        throw new GridException("Invalid date format: " + str);
                }

                if (plus)
                    cal.set(Calendar.ZONE_OFFSET, (tzHour * 60 + tzMin) * 60 * 1000);
                else
                    cal.set(Calendar.ZONE_OFFSET, -(tzHour * 60 + tzMin) * 60 * 1000);
            }
            else
                cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        catch (NumberFormatException ex) {
            throw new GridException("Invalid date format: " + str, ex);
        }

        return cal;
    }

    /**
     * Adds values to collection and returns the same collection to allow chaining.
     *
     * @param c Collection to add values to.
     * @param vals Values.
     * @param <V> Value type.
     * @return Passed in collection.
     */
    public static <V, C extends Collection<? super V>> C addAll(C c, V... vals) {
        Collections.addAll(c, vals);

        return c;
    }

    /**
     * Adds values to collection and returns the same collection to allow chaining.
     *
     * @param m Map to add entries to.
     * @param entries Entries.
     * @param <K> Key type.
     * @param <V> Value type.
     * @param <M> Map type.
     * @return Passed in collection.
     */
    public static <K, V, M extends Map<K, V>> M addAll(M m, Map.Entry<K, V>... entries) {
        for (Map.Entry<K, V> e : entries)
            m.put(e.getKey(), e.getValue());

        return m;
    }

    /**
     * Adds values to collection and returns the same collection to allow chaining.
     *
     * @param m Map to add entries to.
     * @param entries Entries.
     * @param <K> Key type.
     * @param <V> Value type.
     * @param <M> Map type.
     * @return Passed in collection.
     */
    public static <K, V, M extends Map<K, V>> M addAll(M m, GridTuple2<K, V>... entries) {
        for (GridTuple2<K, V> t : entries)
            m.put(t.get1(), t.get2());

        return m;
    }

    /**
     * Utility method creating {@link JMException} with given cause.
     *
     * @param e Cause exception.
     * @return Newly created {@link JMException}.
     */
    public static JMException jmException(Throwable e) {
        JMException x = new JMException();

        x.initCause(e);

        return x;
    }

    /**
     * Unwraps closure exceptions.
     *
     * @param t Exception.
     * @return Unwrapped exception.
     */
    public static Exception unwrap(Throwable t) {
        assert t != null;

        while (true) {
            if (t instanceof Error)
                throw (Error)t;

            if (t instanceof GridClosureException) {
                t = ((GridClosureException)t).unwrap();

                continue;
            }

            return (Exception)t;
        }
    }

    /**
     * Casts this throwable as {@link GridException}. Creates wrapping
     * {@link GridException}, if needed.
     *
     * @param t Throwable to cast.
     * @return Grid exception.
     */
    public static GridException cast(Throwable t) {
        assert t != null;

        while (true) {
            if (t instanceof Error)
                throw (Error)t;

            if (t instanceof GridClosureException) {
                t = ((GridClosureException)t).unwrap();

                continue;
            }

            if (t instanceof GridException)
                return (GridException)t;

            if (!(t instanceof GridRuntimeException) || t.getCause() == null)
                return new GridException(t);

            assert t.getCause() != null; // ...and it is GridRuntimeException.

            t = t.getCause();
        }
    }

    /**
     * Parses passed string with specified date.
     *
     * @param src String to parse.
     * @param pattern Pattern.
     * @return Parsed date.
     * @throws java.text.ParseException If exception occurs while parsing.
     */
    public static Date parse(String src, String pattern) throws java.text.ParseException {
        java.text.DateFormat format = new java.text.SimpleDateFormat(pattern);

        return format.parse(src);
    }

    /**
     * Checks if class loader is an internal P2P class loader.
     *
     * @param o Object to check.
     * @return {@code True} if P2P class loader.
     */
    public static boolean p2pLoader(Object o) {
        return o != null && p2pLoader(o.getClass().getClassLoader());
    }

    /**
     * Checks if class loader is an internal P2P class loader.
     *
     * @param ldr Class loader to check.
     * @return {@code True} if P2P class loader.
     */
    public static boolean p2pLoader(ClassLoader ldr) {
        return ldr instanceof GridDeploymentInfo;
    }

    /**
     * Formats passed date with specified pattern.
     *
     * @param date Date to format.
     * @param pattern Pattern.
     * @return Formatted date.
     */
    public static String format(Date date, String pattern) {
        java.text.DateFormat format = new java.text.SimpleDateFormat(pattern);

        return format.format(date);
    }

    /**
     * @param ctx Kernal context.
     * @return Closure that converts node ID to a node.
     */
    public static GridClosure<UUID, GridNode> id2Node(final GridKernalContext ctx) {
        assert ctx != null;

        return new C1<UUID, GridNode>() {
            @Nullable @Override public GridNode apply(UUID id) {
                return ctx.discovery().node(id);
            }
        };
    }

    /**
     * @param ctx Kernal context.
     * @return Closure that converts node ID to a node.
     */
    public static GridClosure<UUID, GridRichNode> id2RichNode(final GridKernalContext ctx) {
        assert ctx != null;

        return new C1<UUID, GridRichNode>() {
            @Override @Nullable public GridRichNode apply(UUID id) {
                return ctx.rich().rich(ctx.discovery().node(id));
            }
        };
    }

    /**
     * Dumps stack for given thread.
     *
     * @param t Thread to dump stack for.
     */
    public static void dumpStack(Thread t) {
        dumpStack(t, System.err);
    }

    /**
     * Dumps stack for given thread.
     *
     * @param t Thread to dump stack for.
     * @param s <code>PrintStream</code> to use for output
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    public static void dumpStack(Thread t, PrintStream s) {
        synchronized (s) {
            s.println("Dumping stack trace for thread: " + t);

            for (StackTraceElement trace : t.getStackTrace())
                s.println("\tat " + trace);
        }
    }

    /**
     * Checks if object is a primitive array.
     *
     * @param obj Object to check.
     * @return {@code True} if Object is primitive array.
     */
    public static boolean isPrimitiveArray(Object obj) {
        Class<?> cls = obj.getClass();

        return cls.isArray() && (
            cls.equals(byte[].class) ||
                cls.equals(short[].class) ||
                cls.equals(char[].class) ||
                cls.equals(int[].class) ||
                cls.equals(long[].class) ||
                cls.equals(float[].class) ||
                cls.equals(double[].class) ||
                cls.equals(boolean[].class));
    }

    /**
     * Awaits for the latch.
     *
     * @param latch Latch to wait for.
     * @throws GridInterruptedException Wrapped {@link InterruptedException}.
     */
    public static void await(CountDownLatch latch) throws GridInterruptedException {
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /**
     * Sleeps for given number of milliseconds.
     *
     * @param ms Time to sleep.
     * @throws GridInterruptedException Wrapped {@link InterruptedException}.
     */
    public static void sleep(long ms) throws GridInterruptedException {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /**
     * Gets cache attributes for the node.
     *
     * @param n Node to get cache attributes for.
     * @return Array of cache attributes for the node.
     */
    public static GridCacheAttributes[] cacheAttributes(GridNode n) {
        return n.attribute(ATTR_CACHE);
    }

    /**
     * Gets view on all cache names started on the node.
     *
     * @param n Node to get cache names for.
     * @return Cache names for the node.
     */
    public static Collection<String> cacheNames(GridNode n) {
        return F.viewReadOnly(
            F.asList(n.<GridCacheAttributes[]>attribute(ATTR_CACHE)),
            new C1<GridCacheAttributes, String>() {
                @Override public String apply(GridCacheAttributes attrs) {
                    return attrs.cacheName();
                }
            });
    }

    /**
     * Checks if given node has specified cache started.
     *
     * @param n Node to check.
     * @param cacheName Cache name to check.
     * @return {@code True} if given node has specified cache started.
     */
    public static boolean hasCache(GridNode n, String cacheName) {
        GridCacheAttributes[] caches = n.attribute(ATTR_CACHE);

        if (caches != null)
            for (GridCacheAttributes attrs : caches)
                if (F.eq(cacheName, attrs.cacheName()))
                    return true;

        return false;
    }

    /**
     * Gets cache mode or a cache on given node or {@code null} if cache is not
     * present on given node.
     *
     * @param n Node to check.
     * @param cacheName Cache to check.
     * @return Cache mode or {@code null} if cache is not found.
     */
    @Nullable public static GridCacheMode cacheMode(GridNode n, String cacheName) {
        GridCacheAttributes[] caches = n.attribute(ATTR_CACHE);

        if (caches != null)
            for (GridCacheAttributes attrs : caches)
                if (F.eq(cacheName, attrs.cacheName()))
                    return attrs.cacheMode();

        return null;
    }

    /**
     * Checks if given node has near cache enabled for the specified
     * partitioned cache.
     *
     * @param n Node.
     * @param cacheName Cache name.
     * @return {@code true} if given node has near cache enabled for the
     *      specified partitioned cache.
     */
    public static boolean hasNearCache(GridNode n, String cacheName) {
        GridCacheAttributes[] caches = n.attribute(ATTR_CACHE);

        if (caches != null)
            for (GridCacheAttributes attrs : caches)
                if (F.eq(cacheName, attrs.cacheName()))
                    return attrs.nearCacheEnabled();

        return false;
    }

    /**
     * @return {@code true} if future notification should work synchronously.
     */
    public static boolean isFutureNotificationSynchronous() {
        return "true".equalsIgnoreCase(X.getSystemOrEnv(GridSystemProperties.GG_FUT_SYNC_NOTIFICATION));
    }

    /**
     * @return {@code true} if future notification should work concurrently.
     */
    public static boolean isFutureNotificationConcurrent() {
        return "true".equalsIgnoreCase(X.getSystemOrEnv(GridSystemProperties.GG_FUT_CONCURRENT_NOTIFICATION));
    }

    /**
     * Adds listener to asynchronously log errors.
     *
     * @param f Future to listen to.
     * @param log Logger.
     */
    public static void asyncLogError(GridFuture<?> f, final GridLogger log) {
        if (f != null)
            f.listenAsync(new CI1<GridFuture<?>> () {
                @Override public void apply(GridFuture<?> f) {
                    try {
                        f.get();
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to execute future: " + f, e);
                    }
                }
            });
    }

    /**
     * Converts collection of nodes to collection of node IDs.
     *
     * @param nodes Nodes.
     * @return Node IDs.
     */
    public static Collection<UUID> nodeIds(@Nullable Collection<? extends GridNode> nodes) {
        return F.viewReadOnly(nodes, F.node2id());
    }

    /**
     * Converts collection of Grid instances to collection of node IDs.
     *
     * @param grids Grids.
     * @return Node IDs.
     */
    public static Collection<UUID> gridIds(@Nullable Collection<? extends Grid> grids) {
        return F.viewReadOnly(grids, new C1<Grid, UUID>() {
            @Override public UUID apply(Grid g) {
                return g.localNode().id();
            }
        });
    }

    /**
     * Converts collection of Grid instances to collection of grid names.
     *
     * @param grids Grids.
     * @return Grid names.
     */
    public static Collection<String> grids2names(@Nullable Collection<? extends Grid> grids) {
        return F.viewReadOnly(grids, new C1<Grid, String>() {
            @Override public String apply(Grid g) {
                return g.name();
            }
        });
    }

    /**
     * Converts collection of grid nodes to collection of grid names.
     *
     * @param nodes Nodes.
     * @return Grid names.
     */
    public static Collection<String> nodes2names(@Nullable Collection<? extends GridNode> nodes) {
        return F.viewReadOnly(nodes, new C1<GridNode, String>() {
            @Override public String apply(GridNode n) {
                return G.grid(n.id()).name();
            }
        });
    }
}
