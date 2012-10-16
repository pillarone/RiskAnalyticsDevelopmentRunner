// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.benchmarks;

import org.gridgain.grid.typedef.internal.*;
import java.beans.*;
import java.io.*;
import java.net.*;

/**
 * Auto-benchmarking facility for local node. This class represent Spring-friendly JavaBean that performs
 * requested benchmarks and exposes their
 * results (scores) via getters. This bean can be used by Spring configuration
 * to initialize local node attributes with benchmark values that can be later utilized by ForkJoin
 * logic in heterogeneous environment to perform a weighted split. Constructor of the JavaBean allows
 * to specify which benchmarks need to be executed, and method {@link #start()} actually
 * executes them or loads them from local file store if they were previously saved (in Spring this
 * method would be an <i>initialization method</i> for this bean).
 * <p>
 * Note that benchmark scores don't depend directly on the size of the specific benchmark (since its
 * score is weighted by the time). However, as a rule of the thumb a single benchmark that takes
 * less than several seconds is likely to be inaccurate, and a single benchmark that takes more than a
 * several minutes to complete is likely to be excessive.
 * <p>
 * Using default benchmark sizes will result in approximately 4 minutes running total for all 5
 * benchmarks on Pentium 4 3.0 GHz. It is recommended that benchmark caching is turned on (basically
 * flags {@code loadResults} and {@code saveResults} are set to {@code true}.
 * <p>
 * You can initialize local node benchmarks by adding/uncommenting the following section
 * in GridGain Spring XML file:
 * <pre name="code" class="xml">
 * &lt;property name="userAttributes"&gt;
 *     &lt;map&gt;
 *         &lt;entry key="grid.node.benchmark"&gt;
 *             &lt;bean class="org.gridgain.grid.benchmarks.GridLocalNodeBenchmark" init-method="start"/&gt;
 *         &lt;/entry&gt;
 *     &lt;/map&gt;
 * &lt;/property&gt;
 * </pre>
 * You can configure load balancing to automatically use node benchmarks to distribute
 * jobs on the grid. Here is an example of how Spring XML configuration would look like:
 * <pre name="code" class="xml">
 * &lt;property name="loadBalancingSpi"&gt;
 *     &lt;bean class="org.gridgain.grid.spi.loadBalancing.adaptive.GridAdaptiveLoadBalancingSpi"&gt;
 *         &lt;property name="loadProbe"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.loadBalancing.adaptive.GridAdaptiveBenchmarkLoadProbe"&gt;
 *                 &lt;!-- Specify name of benchmark node attribute (the same as above). --&gt;
 *                 &lt;property name="benchmarkAttributeName" value="grid.node.benchmark"/&gt;
 *
 *                 &lt;!-- Benchmarks scores to use. --&gt;
 *                 &lt;property name="useIntegerScore" value="true"/&gt;
 *                 &lt;property name="useLongScore" value="true"/&gt;
 *                 &lt;property name="useDoubleScore" value="true"/&gt;
 *                 &lt;property name="useIoScore" value="false"/&gt;
 *                 &lt;property name="useTrigonometryScore" value="false"/&gt;
 *             &lt;/bean&gt;
 *         &lt;/property&gt;
 *     &lt;/bean&gt;
 * &lt;/property&gt;
 * </pre>
 * <p>
 * Please make sure to properly initialize this probe to use exactly the scores you
 * need in your grid. For example, if your jobs don't do any I/O, then you probably
 * should disable I/O score. If you are not doing any trigonometry calculations,
 * then you should disable trigonometry score.
 * <p>
 * Original idea of these benchmarks is by Christopher W. Cowell-Shah.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLocalNodeBenchmark implements Serializable {
    /** Default benchmark size. */
    public static final long DFLT_INT_SIZE = 150000000;

    /** Default benchmark size. */
    public static final long DFLT_LONG_SIZE = 150000000;

    /** Default benchmark size. */
    public static final long DFLT_TRIG_SIZE = 15000000;

    /** Default benchmark size. */
    public static final long DFLT_DOUBLE_SIZE = 30000000;

    /** Default benchmark size. */
    public static final long DFLT_IO_SIZE = 10000000;

    /** Default file path where benchmark results placed (value is {@code work/benchmarks/localnode.xml}). */
    public static final String DFLT_PATH = "work/benchmarks/localnode.xml";

    /** Integer score. */
    private long intScore;

    /** Long score. */
    private long longScore;

    /** Double score. */
    private long doubleScore;

    /** Trigonometry score. */
    private long trigScore;

    /** I/O score. */
    private long ioScore;

    /** Whether or not to run given benchmark. */
    private boolean runInt = true;

    /** Whether or not to run given benchmark. */
    private boolean runLong = true;

    /** Whether or not to run given benchmark. */
    private boolean runDouble = true;

    /** Whether or not to run given benchmark. */
    private boolean runTrig = true;

    /** Whether or not to run given benchmark. */
    private boolean runIo = true;

    /** Given benchmark size. */
    private long intSize = DFLT_INT_SIZE;

    /** Given benchmark size. */
    private long longSize = DFLT_LONG_SIZE;

    /** Given benchmark size. */
    private long doubleSize = DFLT_DOUBLE_SIZE;

    /** Given benchmark size. */
    private long trigSize = DFLT_TRIG_SIZE;

    /** Given benchmark size. */
    private long ioSize = DFLT_IO_SIZE;

    /** Load benchmark results during start from file. */
    private boolean loadResults;

    /** Save benchmark results in file. */
    private boolean saveResults;

    /** File path where benchmark results placed. */
    private String filePath = DFLT_PATH;

    /**
     * Creates new bean with all benchmarks turned on.
     * <p>
     * Caching of benchmark results is turned on which means that every time an instance
     * of this class is created, it will try to upload stored benchmark results from file
     * system, if if not found, it will calculate benchmarks and then store them on the
     * file system.
     */
    public GridLocalNodeBenchmark() {
        this(true, true, true, true, true, true , true);
    }

    /**
     * Creates new bean with specific benchmarks turned on and off.
     * <p>
     * Caching of benchmark results is turned on which means that every time an instance
     * of this class is created, it will try to upload stored benchmark results from file
     * system, if if not found, it will calculate benchmarks and then store them on the
     * file system.
     *
     * @param runInt {@code true} to turn {@code int} benchmark on, {@code false} otherwise.
     * @param runLong {@code true} to turn {@code long} benchmark on, {@code false} otherwise.
     * @param runDouble {@code true} to turn {@code double} benchmark on, {@code false} otherwise.
     * @param runTrig {@code true} to turn trigonometry benchmark on, {@code false} otherwise.
     * @param runIo {@code true} to turn I/O benchmark on, {@code false} otherwise.
     */
    public GridLocalNodeBenchmark(boolean runInt, boolean runLong, boolean runDouble, boolean runTrig, boolean runIo) {
        this(runInt, runLong, runDouble, runTrig, runIo, true, true, DFLT_PATH);
    }

    /**
     * Creates new bean with specific benchmarks turned on and off.
     *
     * @param runInt {@code true} to turn {@code int} benchmark on, {@code false} otherwise.
     * @param runLong {@code true} to turn {@code long} benchmark on, {@code false} otherwise.
     * @param runDouble {@code true} to turn {@code double} benchmark on, {@code false} otherwise.
     * @param runTrig {@code true} to turn trigonometry benchmark on, {@code false} otherwise.
     * @param runIo {@code true} to turn I/O benchmark on, {@code false} otherwise.
     * @param loadResults {@code true} to load benchmark results from file, {@code false} ignore file.
     * @param saveResults {@code true} to save benchmark result in file, {@code false} otherwise.
     */
    public GridLocalNodeBenchmark(boolean runInt, boolean runLong, boolean runDouble, boolean runTrig, boolean runIo,
        boolean loadResults, boolean saveResults) {
        this(runInt, runLong, runDouble, runTrig, runIo, loadResults, saveResults, DFLT_PATH);
    }

    /**
     * Creates new bean with specific benchmarks turned on and off.
     *
     * @param runInt {@code true} to turn {@code int} benchmark on, {@code false} otherwise.
     * @param runLong {@code true} to turn {@code long} benchmark on, {@code false} otherwise.
     * @param runDouble {@code true} to turn {@code double} benchmark on, {@code false} otherwise.
     * @param runTrig {@code true} to turn trigonometry benchmark on, {@code false} otherwise.
     * @param runIo {@code true} to turn I/O benchmark on, {@code false} otherwise.
     * @param loadResults {@code true} to load benchmark results from file, {@code false} ignore file.
     * @param saveResults {@code true} to save benchmark result in file, {@code false} otherwise.
     * @param filePath Benchmark results file path. This should be either absolute or relative path within
     *      {@code GRIDGAIN_HOME}. Benchmarks results will be loader and saved into this
     *      file depending on flags {@code loadResults} and {@code saveResults}. Note that
     *      default value is {@link #DFLT_PATH}.
     */
    public GridLocalNodeBenchmark(boolean runInt, boolean runLong, boolean runDouble, boolean runTrig, boolean runIo,
        boolean loadResults, boolean saveResults, String filePath) {
        assert filePath != null;

        this.runInt = runInt;
        this.runLong = runLong;
        this.runDouble = runDouble;
        this.runTrig = runTrig;
        this.runIo = runIo;
        this.loadResults = loadResults;
        this.saveResults = saveResults;
        this.filePath = filePath;
    }

    /**
     * Runs all requested benchmarks or load if benchmark results exist.
     * In Spring context it should be a initializer method.
     *
     * @throws IllegalArgumentException If loading of results is turned on and
     *      results could not be loaded for whatever reason.
     */
    public void start() throws IllegalArgumentException {
        boolean isLoaded = false;

        if (loadResults) {
            URL fileUrl = U.resolveGridGainUrl(filePath);

            if (fileUrl != null) {
                InputStream in = null;

                try {
                    in = new BufferedInputStream(fileUrl.openConnection().getInputStream());

                    XMLDecoder decoder = new XMLDecoder(in);

                    intScore = (Long)decoder.readObject();
                    longScore = (Long)decoder.readObject();
                    doubleScore = (Long)decoder.readObject();
                    trigScore = (Long)decoder.readObject();
                    ioScore = (Long)decoder.readObject();
                }
                catch (IOException e) {
                    throw new IllegalArgumentException("Failed to read benchmark results from file: " + fileUrl, e);
                }
                finally {
                    U.close(in, null);
                }

                isLoaded = true;
            }
        }

        if (!isLoaded) {
            runBenchmarks();
        }
    }

    /**
     * Runs all requested benchmarks. In Spring context it should be a initializer method.
     */
    public void runBenchmarks() {
        if (runInt)
            intScore = runIntBenchmark();

        if (runLong)
            longScore = runLongBenchmark();

        if (runDouble)
            doubleScore = runDoubleBenchmark();

        if (runTrig)
            trigScore = runTrigonometryBenchmark();

        if (runIo)
            ioScore = runIoBenchmark();

        if (saveResults) {
            File file = new File(filePath);

            File dir = file.getParentFile();

            // First, see if directory exists.
            if (dir == null || !dir.exists()) {
                String ggHome = U.getGridGainHome();

                if (ggHome != null && ggHome.length() > 0) {
                    file = new File(ggHome, filePath);

                    dir = file.getParentFile();
                }
            }

            if (dir == null || !dir.exists())
                throw new IllegalArgumentException("Invalid file path: " + filePath);

            OutputStream out = null;

            try {
                out = new BufferedOutputStream(new FileOutputStream(file));

                XMLEncoder encoder = new XMLEncoder(out);

                encoder.writeObject(intScore);
                encoder.writeObject(longScore);
                encoder.writeObject(doubleScore);
                encoder.writeObject(trigScore);
                encoder.writeObject(ioScore);

                encoder.flush();
                encoder.close();
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Failed to write benchmark results into file: " + filePath, e);
            }
            finally {
                U.close(out, null);
            }
        }
    }

    /**
     * Sets {@code int} benchmark size.
     *
     * @param intSize {@code int} benchmark size.
     */
    public void setIntegerBenchmarkSize(long intSize) {
        A.ensure(intSize > 0, "intSize > 0");

        this.intSize = intSize;
    }

    /**
     * Sets {@code long} benchmark size.
     *
     * @param longSize {@code long} benchmark size.
     */
    public void setLongBenchmarkSize(long longSize) {
        A.ensure(longSize > 0, "longSize > 0");

        this.longSize = longSize;
    }

    /**
     * Sets {@code double} benchmark size.
     *
     * @param doubleSize {@code double} benchmark size.
     */
    public void setDoubleBenchmarkSize(long doubleSize) {
        A.ensure(doubleSize > 0, "doubleSize > 0");

        this.doubleSize = doubleSize;
    }

    /**
     * Sets trigonometry benchmark size.
     *
     * @param trigSize Trigonometry benchmark size.
     */
    public void setTrigonometryBenchmarkSize(long trigSize) {
        A.ensure(trigSize > 0, "trigSize > 0");

        this.trigSize = trigSize;
    }

    /**
     * Sets I/O benchmark size.
     *
     * @param ioSize I/O benchmark size.
     */
    public void setIoBenchmarkSize(long ioSize) {
        A.ensure(ioSize > 0, "ioSize > 0");

        this.ioSize = ioSize;
    }

    /**
     * Runs {@code int} benchmark.
     *
     * @return {@code int} benchmark score.
     */
    private long runIntBenchmark() {
        int r = 1;

        long start = System.currentTimeMillis();

        for (long i = 1; i < intSize; i++) {
            // Benchmark iteration.
            r -= i;
            r *= i;
            r += i;
            r /= i;

            // Make sure we reference this variable.
            r = -r;
        }

        long time = System.currentTimeMillis() - start;

        return time == 0 ? intSize : intSize / time;
    }

    /**
     * Runs {@code long} benchmark.
     *
     * @return {@code long} benchmark score.
     */
    private long runLongBenchmark() {
        long r = 1;

        long start = System.currentTimeMillis();

        for (long i = Long.MAX_VALUE - longSize - 1; i < Long.MAX_VALUE; i++) {
            r -= i;
            r *= i;
            r += i;
            r /= i;

            // Make sure we reference this variable.
            r = -r;
        }

        long time = System.currentTimeMillis() - start;

        return time == 0 ? longSize : longSize / time;
    }

    /**
     * Runs {@code double} benchmark.
     *
     * @return {@code double} benchmark score.
     */
    private long runDoubleBenchmark() {
        double r = 1;

        long start = System.currentTimeMillis();

        double d = Double.MAX_VALUE - doubleSize;

        for (long i = Long.MAX_VALUE - doubleSize - 1; i < Long.MAX_VALUE; i++) {
            // Benchmark iteration.
            r -= d;
            r *= d;
            r += d;
            r /= d;

            // Make sure we reference this variable.
            r = -r;

            d++;
        }

        long time = System.currentTimeMillis() - start;

        return time == 0 ? doubleSize : doubleSize / time;
    }

    /**
     * Runs trigonometry benchmark.
     *
     * @return Trigonometry benchmark score.
     */
    @SuppressWarnings({"UnusedAssignment"})
    private long runTrigonometryBenchmark() {
        double r = 1;

        long start = System.currentTimeMillis();

        for (double i = 0.0f; i < trigSize; i++) {
            // Benchmark iteration.
            r = Math.cos(i);
            r = Math.sin(i);
            r = Math.tan(i);
            r = Math.log(i);
            r = Math.sqrt(i);

            // Make sure we reference this variable.
            r = -r;
        }

        long time = System.currentTimeMillis() - start;

        return time == 0 ? trigSize : trigSize / time;
    }

    /**
     * Runs I/O benchmark.
     *
     * @return I/O benchmark score.
     */
    private long runIoBenchmark() {
        try {
            File tmp = File.createTempFile("gridgain", ".dat");

            String payload = "abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz" +
                "1234567890abcdefgh" + System.getProperty("line.separator");

            long start = System.currentTimeMillis();

            FileWriter out = new FileWriter(tmp);
            BufferedWriter bout = new BufferedWriter(out);

            for (long i = 0; i < ioSize; i++)
                bout.write(payload);

            bout.close();
            out.close();

            FileReader in = new FileReader(tmp);
            BufferedReader bin = new BufferedReader(in);

            for (long i = 0; i < ioSize; i++)
                bin.readLine();

            in.close();
            bin.close();

            long time = System.currentTimeMillis() - start;

            tmp.delete();

            return time == 0 ? trigSize : trigSize / time;
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to run IO benchmark due to IO error.", e);
        }
    }

    /**
     * Gets relative score of {@code int} operations for this benchmark on the local node. Note
     * that this score is only relative to other {@code int} scores obtained from this benchmark on
     * this or other nodes. Note that scores for this benchmark can vary even on the same node
     * from run to run as they are executed in the same VM as grid instance itself and are thus
     * subject to all VM optimization configurations.
     * <p>
     * Higher score means better performance (faster execution of the benchmark). Minimum value is zero.
     *
     * @return Relative score of {@code int} operations for this benchmark. Higher score means
     *      better performance (faster execution of the benchmark). Minimum value is zero.
     */
    public long getIntegerScore() {
        return intScore;
    }

    /**
     * Gets relative score of {@code long} operations for this benchmark on the local node. Note
     * that this score is only relative to other {@code long} scores obtained from this benchmark on
     * this or other nodes. Note that scores for this benchmark can vary even on the same node
     * from run to run as they are executed in the same VM as grid instance itself and are thus
     * subject to all VM optimization configurations.
     * <p>
     * Higher score means better performance (faster execution of the benchmark). Minimum value is zero.
     *
     * @return Relative score of {@code long} operations for this benchmark. Higher score means
     *      better performance (faster execution of the benchmark). Minimum value is zero.
     */
    public long getLongScore() {
        return longScore;
    }

    /**
     * Gets relative score of {@code double} operations for this benchmark on the local node. Note
     * that this score is only relative to other {@code double} scores obtained from this benchmark on
     * this or other nodes. Note that scores for this benchmark can vary even on the same node
     * from run to run as they are executed in the same VM as grid instance itself and are thus
     * subject to all VM optimization configurations.
     * <p>
     * Higher score means better performance (faster execution of the benchmark). Minimum value is zero.
     *
     * @return Relative score of {@code double} operations for this benchmark. Higher score means
     *      better performance (faster execution of the benchmark). Minimum value is zero.
     */
    public long getDoubleScore() {
        return doubleScore;
    }

    /**
     * Gets relative score of trigonometry operations for this benchmark on the local node. Note
     * that this score is only relative to other trigonometry scores obtained from this benchmark on
     * this or other nodes. Note that scores for this benchmark can vary even on the same node
     * from run to run as they are executed in the same VM as grid instance itself and are thus
     * subject to all VM optimization configurations.
     * <p>
     * Higher score means better performance (faster execution of the benchmark). Minimum value is zero.
     *
     * @return Relative score of trigonometry operations for this benchmark. Higher score means
     *      better performance (faster execution of the benchmark). Minimum value is zero.
     */
    public long getTrigonometryScore() {
        return trigScore;
    }

    /**
     * Gets relative score of I/O operations for this benchmark on the local node. Note
     * that this score is only relative to other I/O scores obtained from this benchmark on
     * this or other nodes. Note that scores for this benchmark can vary even on the same node
     * from run to run as they are executed in the same VM as grid instance itself and are thus
     * subject to all VM optimization configurations.
     * <p>
     * Higher score means better performance (faster execution of the benchmark). Minimum value is zero.
     *
     * @return Relative score of I/O operations for this benchmark. Higher score means
     *      better performance (faster execution of the benchmark). Minimum value is zero.
     */
    public long getIoScore() {
        return ioScore;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLocalNodeBenchmark.class, this);
    }
}
