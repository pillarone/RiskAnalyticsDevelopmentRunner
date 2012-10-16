// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace.file;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * File-based implementation of swap space SPI.
 * <p>
 * Key-value pairs are stored by this implementation in separate files with names
 * generated using provided space name and key. The format of the file are the
 * following:
 * <p>
 * <tt>[value length (integer) bytes][value bytes]</tt>
 * <p>
 * where <strong>value length bytes</strong> describes number of bytes containing in
 * <strong>value  bytes</strong> respectively. Length of this field is calculated as
 * {@link Integer#SIZE} &gt;&gt; 2 that reflects number of bytes in integer value for
 *  current JVM.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridFileSwapSpaceSpi extends GridSpiAdapter implements GridSwapSpaceSpi, GridFileSwapSpaceSpiMBean {
    /**
     * Default checkpoint directory (value is {@code work/swapspace/file}).
     * Note that this path used relatively {@code GRIDGAIN_HOME} directory when
     * {@code GRIDGAIN_HOME} exists. For unknown {@code GRIDGAIN_HOME} another
     * directory {@link #DFLT_TMP_DIRECTORY} is used.
     */
    public static final String DFLT_BASE_DIRECTORY_PATH = "work/swapspace/grid_";

    /**
     * Default directory name for SPI when {@code GRIDGAIN_HOME} not defined.
     * This directory name relative to file path in {@code java.io.tmpdir}
     * system property value (value is {@code gg.file.swapspace}).
     */
    public static final String DFLT_TMP_DIRECTORY = "gg.file.swapspace";

    /**
     * Default maximum size in bytes for all swap spaces (value is {@code -1})
     * means unlimited size.
     */
    public static final long DFLT_MAX_SWAP_SIZE = -1;

    /**
     * Default maximum swap space names count (value is {@code -1})
     * means unlimited count.
     */
    public static final int DFLT_MAX_SWAP_CNT = -1;

    /**
     * Constant space name when it is null.
     */
    private static final String NULL_SPACE_NAME = "gg_null_space_name";

    /** Constant value when value is null. */
    private static final int NULL_VAL_LEN = -1;

    /** File extension. */
    private static final String FILE_EXT = "swp";

    /** Injected grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Swap space directory where all files are stored. */
    private String dirPath;

    /** Maximum swap space size in bytes for all spaces. */
    private long maxSwapSize = DFLT_MAX_SWAP_SIZE;

    /** Maximum swap space count for all spaces. */
    private int maxSwapCnt = DFLT_MAX_SWAP_CNT;

    /** Size in bytes for every space. */
    private ConcurrentMap<String, AtomicLong> spaceSizes = new ConcurrentHashMap<String, AtomicLong>();

    /** Keys counter for every space. */
    private ConcurrentMap<String, AtomicInteger> spaceCnts = new ConcurrentHashMap<String, AtomicInteger>();

    /** Total size. */
    private final AtomicLong totalSize = new AtomicLong(0);

    /** Total count. */
    private final AtomicLong totalCnt = new AtomicLong(0);

    /** Maximal number of file read-write locks. */
    private static final int LOCKS_CNT = 64;

    /** Maximal number of replicas. */
    private static final int REPLICAS_CNT = 64;

    /** Consistent hash of locks. */
    private final GridConsistentHash<Lock> lockHash = new GridConsistentHash<Lock>();

    /**
     * Either {@link #dirPath} value if it is absolute path or
     * {@code GRID_GAIN_HOME}/{@link #dirPath} if one above was not found.
     */
    private File folder;

    /** */
    private boolean persist;

    /** */
    private boolean deleteCorrupted = true;

    /** {@inheritDoc} */
    @Override public String getDirectoryPath() {
        return dirPath;
    }

    /**
     * Sets path to a directory where swap space values will be stored. The
     * path can either be absolute or relative to {@code GRIDGAIN_HOME} system
     * or environment variable.
     * <p>
     * If not provided, default value is {@link #DFLT_BASE_DIRECTORY_PATH}.
     *
     * @param dirPath Absolute or GridGain installation home folder relative path
     *      where swap space values will be stored.
     */
    @GridSpiConfiguration(optional = true)
    public void setDirectoryPath(String dirPath) {
        this.dirPath = dirPath;
    }

    /**
     * Data will not be removed from disk if this property is {@code true}.
     *
     * @return {@code true} if data must not be deleted at SPI stop, {@code false} otherwise.
     */
    @Override public boolean isPersistent() {
        return persist;
    }

    /**
     * Data will not be removed from disk if this property is {@code true}.
     * <p>
     * Note that if this property is set to {@code false} then swap file names
     * are formed using local node id to isolate different executions of the
     * same GridGain installation from each other in order to avoid concurrent
     * access problems.
     * <p>
     * But if this property is {@code true} then file names are free of local
     * node id because the node id is different for every next execution and
     * there should be a possibility to parse file names at startup to restore
     * previously stored swap entries. So, in this case, executing more than
     * one grid with the same name is not safe since started grids will share
     * the same directory and the same files.
     *
     * @param persist {@code true} if data must not be deleted at SPI stop,
     *      otherwise {@code false}.
     */
    @GridSpiConfiguration(optional = true)
    public void setPersistent(boolean persist) {
        this.persist = persist;
    }

    /**
     * If this property is {@code true} then spi will check for corrupted files at SPI
     * start and delete them.
     *
     * @return {@code true} if corrupted files should be deleted at SPI start,
     *      {@code false} otherwise.
     */
    @Override public boolean isDeleteCorrupted() {
        return deleteCorrupted;
    }

    /**
     * If this property is {@code true} then spi will check for corrupted files at SPI
     * start and delete them.
     *
     * @param deleteCorrupted {@code true} if corrupted files should be deleted at SPI start,
     *      {@code false} otherwise.
     */
    @GridSpiConfiguration(optional = true)
    public void setDeleteCorrupted(boolean deleteCorrupted) {
        this.deleteCorrupted = deleteCorrupted;
    }

    /**
     * Gets maximum size in bytes for data to store on disk.
     *
     * @return Maximum size in bytes for data to stored on disk.
     */
    @Override public long getMaximumSwapSize() {
        return maxSwapSize;
    }

    /**
     * Sets maximum size in bytes for data to store on disk.
     * If not provided, default value is {@link #DFLT_MAX_SWAP_SIZE} means unlimited.
     *
     * @param maxSwapSize Maximum size in bytes for data to store on disk.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaximumSwapSize(long maxSwapSize) {
        this.maxSwapSize = maxSwapSize;
    }

    /**
     * Gets maximum count of all swap spaces.
     *
     * @return Maximum count of all swap spaces.
     */
    @Override public int getMaximumSwapCount() {
        return maxSwapCnt;
    }

    /**
     * Sets maximum count of all swap spaces.
     *
     * If not provided, default value is {@link #DFLT_MAX_SWAP_CNT} means unlimited .
     *
     * @param maxSwapCnt Maximum count of all swap spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaximumSwapCount(int maxSwapCnt) {
        this.maxSwapCnt = maxSwapCnt;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        if (dirPath == null) {
            dirPath = DFLT_BASE_DIRECTORY_PATH + gridName;
        }

        if (log.isDebugEnabled()) {
            log.debug("Swap directory path: " + dirPath);
        }

        initFolder();
        initLocks();

        if (persist) {
            if (deleteCorrupted) {
                deleteCorruptedFiles();
            }

            readPersistentData();
        }
        else {
            // There should not be any swap files if persist flag is false.
            clearSwapDir();
        }

        registerMBean(gridName, this, GridFileSwapSpaceSpiMBean.class);

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("folder", folder));
            log.debug(configInfo("persist", persist));
            log.debug(configInfo("maximumSwapSize", maxSwapSize));
            log.debug(configInfo("maximumSwapCount", maxSwapCnt));
        }

        // Ack ok start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /**
     * Initializes consistent locks hash.
     */
    private void initLocks() {
        for (int i = 0; i < LOCKS_CNT; i++) {
            lockHash.addNode(new Lock(i), REPLICAS_CNT);
        }
    }

    /**
     * Initializes work folder.
     *
     * @throws GridSpiException If failed.
     */
    private void initFolder() throws GridSpiException {
        File dir = new File(dirPath);

        if (dir.exists()) {
            folder = dir;
        }
        else {
            if (getGridGainHome() != null && getGridGainHome().length() > 0) {
                // Create relative by default.
                folder = new File(getGridGainHome(), dirPath);

                if (!folder.mkdirs() && !folder.exists()) {
                    throw new GridSpiException("Swap space directory does not exist and cannot be created: " +
                        folder);
                }
            }
            else {
                String tmpDirPath = System.getProperty("java.io.tmpdir");

                if (tmpDirPath == null) {
                    throw new GridSpiException("Failed to initialize swap space directory " +
                        "with unknown GRIDGAIN_HOME (system property 'java.io.tmpdir' does not exist).");
                }

                folder = new File(tmpDirPath, DFLT_TMP_DIRECTORY);

                if (!folder.mkdirs() && !folder.exists()) {
                    throw new GridSpiException("Failed to initialize swap space directory " +
                        "with unknown GRIDGAIN_HOME: " + folder);
                }
            }

            if (log.isInfoEnabled()) {
                log.info("Created file swap space folder: " + folder.getAbsolutePath());
            }
        }

        assert folder != null;

        if (!folder.isDirectory()) {
            throw new GridSpiException("Swap space directory path is not a valid directory: " + dirPath);
        }
    }

    /**
     * Read swap space data from file system.
     *
     * @throws GridSpiException In case of error.
     */
    private void readPersistentData() throws GridSpiException {
        File[] files = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(FILE_EXT);
            }
        });

        if (files != null) {
            for (File file : files) {
                GridTuple2<String, String> fileNameTuple = parseFileName(file.getName());

                String space = fileNameTuple.get1();

                GridSwapByteArray val = readFromFile(file, log);

                long addSize = (val == null ? 0L : val.getLength()) + Integer.SIZE >> 3;

                checkLimits(addSize, 1);

                updateCounters(space, addSize, 1);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Remove all data on disk.
        if (!persist) {
            clearSwapDir();
        }

        // Clean resources.
        folder = null;

        spaceSizes.clear();
        spaceCnts.clear();

        totalCnt.set(0);
        totalSize.set(0);

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void clear(String space) throws GridSpiException {
        File[] files = folder.listFiles();

        if (files != null) {
            String filePref = getFilePrefix(space);

            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(filePref) && file.getName().endsWith(FILE_EXT)) {
                    Lock lock = lock(file.getName());

                    lock.writeLock().lock();

                    try {
                        if (!file.delete() && log.isDebugEnabled()) {
                            log.debug("Failed to delete file (safely ignoring) [space name=" + space +
                                ", file=" + file + ']');
                        }
                    }
                    finally {
                        lock.writeLock().unlock();
                    }
                }
            }
        }

        AtomicLong size = spaceSizes.remove(space);
        AtomicInteger cnt = spaceCnts.remove(space);

        totalSize.addAndGet(size == null ? 0 : -size.get());
        totalCnt.addAndGet(cnt == null ? 0 : -cnt.get());
    }

    /**
     * @return Collection of all swap files in swap directory.
     */
    private Iterable<File> getAllSwapFiles() {
        Collection<File> res = new LinkedList<File>();

        if (folder == null) {
            return res;
        }

        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(FILE_EXT)) {
                    res.add(file);
                }
            }
        }

        return res;
    }

    /**
     * Removes all swap files from swap directory.
     */
    private void clearSwapDir() {
        if (log.isInfoEnabled())
            log.info("Clearing swap storage...");

        for (File file : getAllSwapFiles()) {
            Lock lock = lock(file.getName());

            lock.writeLock().lock();

            try {
                if (file.exists() && !file.delete()) {
                    U.error(log, "Failed to delete swap file: " + file);
                }
            }
            finally {
                lock.writeLock().unlock();
            }
        }
    }

    /**
     * Finds and removes corrupted files in swap directory.
     *
     * @throws GridSpiException If operation failed.
     */
    private void deleteCorruptedFiles() throws GridSpiException {
        for (File file : getAllSwapFiles()) {
            long fileSize = file.length();

            try {
                if (fileSize != readValueLength(file)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found corrupted swap file: " + file + ". Will try to delete it.");
                    }

                    if (!file.delete()) {
                        U.error(log, "Failed to delete swap file: " + file);
                    }
                }
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to read data from file: " + file, e);
            }
        }

    }

    /**
     * Reads value length from given swap file.
     *
     * @param file File to read value length from.
     * @return Value length read from file.
     * @throws IOException If file could not be read.
     */
    private int readValueLength(File file) throws IOException {
        assert file != null;

        FileChannel inCh = null;
        WritableByteChannel outCh = null;

        try {
            inCh = new FileInputStream(file).getChannel();

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            outCh = Channels.newChannel(outStream);

            inCh.transferTo(0, inCh.size(), outCh);

            byte[] data = outStream.toByteArray();

            return U.bytesToInt(data, 0);
        }
        finally {
            U.close(inCh, log);
            U.close(outCh, log);
        }
    }

    /** {@inheritDoc} */
    @Override public long size(String space) throws GridSpiException {
        AtomicLong size = spaceSizes.get(space);

        return size == null ? 0 : size.get();
    }

    /** {@inheritDoc} */
    @Override public int count(String space) throws GridSpiException {
        AtomicInteger cnt = spaceCnts.get(space);

        return cnt == null ? 0 : cnt.get();
    }

    /** {@inheritDoc} */
    @Override public long totalSize() {
        return totalSize.get();
    }

    /** {@inheritDoc} */
    @Override public long totalCount() {
        return totalCnt.get();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public GridSwapByteArray read(String space, GridSwapByteArray key) throws GridSpiException {
        A.notNull(key, "key");

        String fileName = getFileName(space, key);

        Lock lock = lock(fileName);

        lock.readLock().lock();

        try {
            File file = new File(folder, fileName);

            return file.exists() ? readFromFile(file, log) : null;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public Map<GridSwapByteArray, GridSwapByteArray> readAll(String space, Iterable<GridSwapByteArray> keys)
        throws GridSpiException {
        A.notNull(keys, "keys");

        Map<GridSwapByteArray, GridSwapByteArray> res = new HashMap<GridSwapByteArray, GridSwapByteArray>();

        for (GridSwapByteArray key : keys) {
            res.put(key, read(space, key));
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public boolean remove(String space, GridSwapByteArray key, @Nullable GridInClosure<GridSwapByteArray> c)
        throws GridSpiException {
        A.notNull(key, "key");

        String fileName = getFileName(space, key);

        Lock lock = lock(fileName);

        lock.writeLock().lock();

        try {
            File file = new File(folder, fileName);

            if (file.exists()) {
                GridSwapByteArray val = null;

                if (c != null) {
                    val = readFromFile(file, log);
                }

                long fileSize = file.length();

                // Update only if deleted.
                if (file.delete()) {
                    updateCounters(space, -fileSize, -1);

                    if (c != null) {
                        c.apply(val);
                    }

                    return true;
                }
            }

            return false;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void removeAll(String space, Collection<GridSwapByteArray> keys,
        @Nullable final GridInClosure2<GridSwapByteArray, GridSwapByteArray> c) throws GridSpiException {
        A.notNull(keys, "keys");

        for (final GridSwapByteArray key : keys) {
            CI1<GridSwapByteArray> c1 = null;

            if (c != null) {
                c1 = new CI1<GridSwapByteArray>() {
                    @Override public void apply(GridSwapByteArray val) {
                        c.apply(key, val);
                    }
                };
            }

            remove(space, key, c1);
        }
    }

    /** {@inheritDoc} */
    @Override public void store(String space, GridSwapByteArray key, GridSwapByteArray val) throws GridSpiException {
        A.notNull(key, "key");

        String fileName = getFileName(space, key);

        Lock lock = lock(fileName);

        lock.writeLock().lock();

        try {
            File file = new File(folder, fileName);

            // Size for key, value and size for length for both.
            Long addSize = (val == null ? 0L : val.getLength()) + (Integer.SIZE >> 3);

            int addCnt = 1;

            // Need to update counters for overwritten file.
            if (file.exists()) {
                addSize -= file.length();

                addCnt = 0;
            }

            // Check limitations before store file.
            checkLimits(addSize, addCnt);

            writeToFile(file, val, log);

            updateCounters(space, addSize, addCnt);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void storeAll(String space,
        Map<GridSwapByteArray, GridSwapByteArray> pairs) throws GridSpiException {
        A.notNull(pairs, "pairs");

        for (Map.Entry<GridSwapByteArray, GridSwapByteArray> entry : pairs.entrySet()) {
            if (entry.getKey() != null) {
                store(space, entry.getKey(), entry.getValue());
            }
            else {
                throw new GridSpiException("Failed to store data with null key.");
            }
        }
    }

    /**
     * @param fileName File name.
     * @return Lock hash.
     */
    private Lock lock(String fileName) {
        Lock lock = lockHash.node(fileName);

        assert lock != null;

        return lock;
    }

    /**
     * Update counters.
     *
     * @param space Name of the space to update counters for.
     * @param addSize Size of data in bytes added into space. Can be negative.
     * @param addCnt Counter to be added for space. Can be negative.
     */
    private void updateCounters(String space, long addSize, int addCnt) {
        AtomicLong spaceSize = F.addIfAbsent(spaceSizes, space, new CO<AtomicLong>() {
            @Override public AtomicLong apply() { return new AtomicLong(0); }
        });

        assert spaceSize != null;

        long newSize = spaceSize.addAndGet(addSize);

        if (log.isDebugEnabled()) {
            log.debug("New space size was calculated: [newSize=" + newSize + ", addSize=" + addSize + "]");
        }

        assert newSize >= 0;

        AtomicInteger spaceCnt = F.addIfAbsent(spaceCnts, space, new CO<AtomicInteger>() {
            @Override public AtomicInteger apply() { return new AtomicInteger(0); }
        });

        assert spaceCnt != null;

        int newCnt = spaceCnt.addAndGet(addCnt);

        assert newCnt >= 0;

        totalSize.addAndGet(addSize);
        totalCnt.addAndGet(addCnt);
    }

    /**
     * Check max size and max counter limitations.
     *
     * @param addSize Size of data in bytes added into space. Can be negative.
     * @param addCnt Counter to be added.
     * @throws GridSpiException In case of error.
     */
    private void checkLimits(long addSize, long addCnt) throws GridSpiException {
        if (maxSwapSize >= 0) {
            long size = totalSize() + addSize;

            if (size > maxSwapSize) {
                throw new GridSpiException("Exceed the limit for maximum allowed size " +
                    "[maxAllowed=" + maxSwapSize + ", exceedSize=" + size + ']');
            }
        }

        if (maxSwapCnt >= 0) {
            long cnt = totalCount() + addCnt;

            if (cnt > maxSwapCnt) {
                throw new GridSpiException("Exceed the limit for maximum allowed space counts " +
                    "[maxAllowed=" + maxSwapCnt + ", exceedCnt=" + cnt + ']');
            }
        }
    }

    /**
     * Write data to file.
     *
     * @param file File to write data to.
     * @param val Value to be written.
     * @param log Logger.
     * @throws GridSpiException In case of any errors.
     */
    private static void writeToFile(File file, GridSwapByteArray val, GridLogger log)
        throws GridSpiException {
        assert file != null;
        assert log != null;

        FileChannel outCh = null;

        try {
            outCh = new FileOutputStream(file).getChannel();

            if (val != null) {
                ByteBuffer buf = ByteBuffer.wrap(U.intToBytes(val.getLength()));

                outCh.write(buf);

                buf = ByteBuffer.wrap(val.getArray(), val.getOffset(), val.getLength());

                outCh.write(buf);
            }
            else {
                ByteBuffer buf = ByteBuffer.wrap(U.intToBytes(NULL_VAL_LEN));

                outCh.write(buf);
            }
        }
        catch (IOException e) {
            throw new GridSpiException("Failed to write data in file: " + file, e);
        }
        finally {
            U.close(outCh, log);
        }
    }

    /**
     * Read data from file.
     *
     * @param file File to read data from.
     * @param log Logger.
     * @return Key-value pair.
     * @throws GridSpiException In case of any errors.
     */
    @Nullable
    private static GridSwapByteArray readFromFile(File file, GridLogger log) throws GridSpiException {
        assert file != null;

        FileChannel inCh = null;
        WritableByteChannel outCh = null;

        try {
            inCh = new FileInputStream(file).getChannel();

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            outCh = Channels.newChannel(outStream);

            inCh.transferTo(0, inCh.size(), outCh);

            byte[] data = outStream.toByteArray();

            int valLen = U.bytesToInt(data, 0);

            GridSwapByteArray val = null;

            if (valLen != NULL_VAL_LEN) {
                int off = Integer.SIZE >> 3;

                byte[] valData = new byte[valLen];

                System.arraycopy(data, off, valData, 0, valLen);

                val = new GridSwapByteArray(valData);
            }

            return val;
        }
        catch (IOException e) {
            throw new GridSpiException("Failed to read data from file: " + file, e);
        }
        finally {
            U.close(inCh, log);
            U.close(outCh, log);
        }
    }

    /**
     * File name prefix.
     *
     * @param space Space name.
     * @param key Key.
     * @return Prefix for file name for this space.
     * @throws GridSpiException If failed to get file name for key.
     */
    private String getFileName(@Nullable String space, GridSwapByteArray key) throws GridSpiException {
        assert key != null;

        return getFilePrefix(space) + '_' + getHashCode(key) + '.' + FILE_EXT;
    }

    /**
     * File name prefix.
     *
     * @param space Space name.
     * @return Prefix for file name for this space.
     */
    private String getFilePrefix(@Nullable String space) {
        String s = space == null ? NULL_SPACE_NAME : space.replace("\"", "").replace("?", "");

        if (!persist) {
            s += getLocalNodeId().toString().replace("-", "");
        }

        return s;
    }

    /**
     * Parse file name and return values (space name and hash code).
     *
     * @param fileName File name.
     * @return Tuple with values (space name and hash code).
     * @throws GridSpiException In case of any errors.
     */
    private GridTuple2<String, String> parseFileName(String fileName) throws GridSpiException {
        assert fileName != null;

        int idx = fileName.lastIndexOf('_');
        int idx2 = fileName.lastIndexOf('.');

        if (idx == -1 || idx2 == -1 || !fileName.endsWith(FILE_EXT)) {
            throw new GridSpiException("Failed to parse file name: " + fileName);
        }

        String space = fileName.substring(0, idx);

        if (NULL_SPACE_NAME.equals(space)) {
            space = null;
        }

        String hashCode = fileName.substring(idx + 1, idx2);

        return F.t(space, hashCode);
    }

    /**
     * Calculates hash code for given key (actually for any array of bytes).
     *
     * @param key Key.
     * @return Calculated hash code.
     * @throws GridSpiException If failed to get hash value.
     */
    private String getHashCode(GridSwapByteArray key) throws GridSpiException {
        assert key != null;

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            byte[] bytes = md5.digest(key.getAsEntire());

            StringBuilder buf = new StringBuilder();

            for (byte b : bytes)
                buf.append(Integer.toHexString(0xFF & b));

            return buf.toString();
        }
        catch (Exception e) {
            throw new GridSpiException("Failed to get hash value for key: " + key, e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFileSwapSpaceSpi.class, this);
    }

    /**
     *
     */
    private static class Lock extends ReentrantReadWriteLock {
        /** Hash. */
        private final int hash;

        /**
         * @param hash Hash.
         */
        private Lock(int hash) {
            this.hash = hash;
        }

        /**
         * @return Hash.
         */
        int hash() {
            return hash;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return hash;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null) {
                return false;
            }

            Lock lock = (Lock)o;

            return hash == lock.hash;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Lock.class, this);
        }
    }
}
