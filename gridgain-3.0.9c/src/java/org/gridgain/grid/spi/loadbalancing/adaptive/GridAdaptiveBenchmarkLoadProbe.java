// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.adaptive;

import org.gridgain.grid.*;
import org.gridgain.grid.benchmarks.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * Probe that uses {@link GridLocalNodeBenchmark} for specifying load for every node.
 * In order to use this probe, use should configure every grid node to start with
 * {@link GridLocalNodeBenchmark} as attribute (see {@link GridConfiguration#getUserAttributes()}
 * for more information).
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
 * Here is an example of how load balancing SPI would be configured from Spring XML configuration:
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
 *                 &lt;property name="useDoulbeScore" value="true"/&gt;
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
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridAdaptiveBenchmarkLoadProbe implements GridAdaptiveLoadProbe {
    /**
     * Default node attribute name for storing node benchmarks (value is
     * {@code 'grid.node.benchmark'}). See {@link GridNode#getAttribute(String)}
     * for more information.
     */
    public static final String DFLT_BENCHMARK_ATTR = "grid.node.benchmark";

    /** Flag indicating whether to use {@code integer} score. */
    private boolean useIntScore = true;

    /** Flag indicating whether to use {@code long} score. */
    private boolean useLongScore = true;

    /** Flag indicating whether to use {@code double} score. */
    private boolean useDoubleScore = true;

    /** Flag indicating whether to use {@code trigonometry} score. */
    private boolean useTrigScore = true;

    /** Flag indicating whether to use {@code I/O} score. */
    private boolean useIoScore = true;

    /** Name of node benchmark attribute. */
    private String benchmarkAttr = DFLT_BENCHMARK_ATTR;

    /**
     * Creates benchmark probe with all defaults. By default, all scores
     * provided in {@link GridLocalNodeBenchmark} class will be used.
     */
    public GridAdaptiveBenchmarkLoadProbe() {
        // No-op.
    }

    /**
     * Creates benchmark probe which allows use to specify which scores to use.
     * See {@link GridLocalNodeBenchmark} for more information on which scores
     * are available and how they are calculated.
     *
     * @param useIntScore Flag indicating whether to use {@code integer} score.
     * @param useLongScore Flag indicating whether to use {@code long} score.
     * @param useDoubleScore Flag indicating whether to use {@code double} score.
     * @param useTrigScore Flag indicating whether to use {@code trigonometry} score.
     * @param useIoScore Flag indicating whether to use {@code I/O} score.
     */
    public GridAdaptiveBenchmarkLoadProbe(boolean useIntScore, boolean useLongScore, boolean useDoubleScore,
        boolean useTrigScore, boolean useIoScore) {
        this.useIntScore = useIntScore;
        this.useLongScore = useLongScore;
        this.useDoubleScore = useDoubleScore;
        this.useTrigScore = useTrigScore;
        this.useIoScore = useIoScore;
    }

    /**
     * Creates benchmark probe which allows use to specify which scores to use.
     * See {@link GridLocalNodeBenchmark} for more information on which scores
     * are available and how they are calculated.
     * <p>
     * This constructor also allows to specify the name of node attribute by
     * which node benchmarks should be accessed.
     *
     * @param useIntScore Flag indicating whether to use {@code integer} score.
     * @param useLongScore Flag indicating whether to use {@code long} score.
     * @param useDoubleScore Flag indicating whether to use {@code double} score.
     * @param useTrigScore Flag indicating whether to use {@code trigonometry} score.
     * @param useIoScore Flag indicating whether to use {@code I/O} score.
     * @param benchmarkAttr Name of node attribute by which node benchmarks should be accessed.
     */
    public GridAdaptiveBenchmarkLoadProbe(boolean useIntScore, boolean useLongScore, boolean useDoubleScore,
        boolean useTrigScore, boolean useIoScore, String benchmarkAttr) {
        this.useIntScore = useIntScore;
        this.useLongScore = useLongScore;
        this.useDoubleScore = useDoubleScore;
        this.useTrigScore = useTrigScore;
        this.useIoScore = useIoScore;
        this.benchmarkAttr = benchmarkAttr;
    }

    /**
     * Gets name of node attribute by which node benchmarks should be accessed.
     * By default {@link #DFLT_BENCHMARK_ATTR} name is used.
     *
     * @return Name of node attribute by which node benchmarks should be accessed.
     */
    public String getBenchmarkAttributeName() {
        return benchmarkAttr;
    }

    /**
     * Sets name of node attribute by which node benchmarks should be accessed.
     * By default {@link #DFLT_BENCHMARK_ATTR} name is used.
     *
     * @param benchmarkAttr Name of node attribute by which node benchmarks should be accessed.
     */
    public void setBenchmarkAttributeName(String benchmarkAttr) {
        A.notNull(benchmarkAttr, "benchmarkAttr");

        this.benchmarkAttr = benchmarkAttr;
    }

    /**
     * Gets flag indicating whether {@code integer} score should be used
     * for calculation of node load.
     *
     * @return Flag indicating whether {@code integer} score should be used
     *      for calculation of node load.
     */
    public boolean isUseIntegerScore() {
        return useIntScore;
    }

    /**
     * Sets flag indicating whether {@code integer} score should be used
     * for calculation of node load.
     *
     * @param useIntScore Flag indicating whether {@code integer} score should be used
     *      for calculation of node load.
     */
    public void setUseIntegerScore(boolean useIntScore) {
        this.useIntScore = useIntScore;
    }

    /**
     * Gets flag indicating whether {@code long} score should be used
     * for calculation of node load.
     *
     * @return Flag indicating whether {@code long} score should be used
     *      for calculation of node load.
     */
    public boolean isUseLongScore() {
        return useLongScore;
    }

    /**
     * Sets flag indicating whether {@code long} score should be used
     * for calculation of node load.
     *
     * @param useLongScore Flag indicating whether {@code long} score should be used
     *      for calculation of node load.
     */
    public void setUseLongScore(boolean useLongScore) {
        this.useLongScore = useLongScore;
    }

    /**
     * Gets flag indicating whether {@code double} score should be used
     * for calculation of node load.
     *
     * @return Flag indicating whether {@code double} score should be used
     *      for calculation of node load.
     */
    public boolean isUseDoubleScore() {
        return useDoubleScore;
    }

    /**
     * Sets flag indicating whether {@code double} score should be used
     * for calculation of node load.
     *
     * @param useDoubleScore Flag indicating whether {@code double} score should be used
     *      for calculation of node load.
     */
    public void setUseDoubleScore(boolean useDoubleScore) {
        this.useDoubleScore = useDoubleScore;
    }

    /**
     * Gets flag indicating whether {@code trigonometry} score should be used
     * for calculation of node load.
     *
     * @return Flag indicating whether {@code trigonometry} score should be used
     * for calculation of node load.
     */
    public boolean isUseTrigonometryScore() {
        return useTrigScore;
    }

    /**
     * Sets flag indicating whether {@code trigonometry} score should be used
     * for calculation of node load.
     *
     * @param useTrigScore Flag indicating whether {@code trigonometry} score should be used
     *      for calculation of node load.
     */
    public void setUseTrigonometryScore(boolean useTrigScore) {
        this.useTrigScore = useTrigScore;
    }

    /**
     * Gets flag indicating whether {@code I/O} score should be used
     * for calculation of node load.
     *
     * @return Flag indicating whether {@code I/O} score should be used
     *      for calculation of node load.
     */
    public boolean isUseIoScore() {
        return useIoScore;
    }

    /**
     * Sets flag indicating whether {@code I/O} score should be used
     * for calculation of node load.
     *
     * @param useIoScore Flag indicating whether {@code I/O} score should be used
     *      for calculation of node load.
     */
    public void setUseIoScore(boolean useIoScore) {
        this.useIoScore = useIoScore;
    }

    /** {@inheritDoc} */
    @Override public double getLoad(GridNode node, int jobsSentSinceLastUpdate) {
        GridLocalNodeBenchmark benchmark = (GridLocalNodeBenchmark)node.getAttribute(benchmarkAttr);

        if (benchmark == null) {
            return 0.0d;
        }

        double score = 0;

        score += useIntScore ? benchmark.getIntegerScore() : 0;
        score += useLongScore ? benchmark.getLongScore() : 0;
        score += useDoubleScore ? benchmark.getDoubleScore() : 0;
        score += useIoScore ? benchmark.getIoScore() : 0;
        score += useTrigScore ? benchmark.getTrigonometryScore() : 0;

        // Return 1 over score to get load vs. weight.
        return score == 0 ? 0.0d : 1.0d / score;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridAdaptiveBenchmarkLoadProbe.class, this);
    }
}
