// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * Deployment metadata.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDeploymentMetadata {
    /** Deployment mode. */
    private GridDeploymentMode depMode;

    /** */
    private String alias;

    /** */
    private String clsName;

    /** */
    private long seqNum;

    /** */
    private String userVer;

    /** */
    private UUID senderNodeId;

    /** */
    private UUID clsLdrId;

    /** Class loader. */
    private ClassLoader clsLdr;

    /** Master node participants. */
    @GridToStringInclude
    private Map<UUID, GridTuple2<UUID, Long>> participants;

    /** */
    private ClassLoader parentLdr;

    /** */
    private boolean record;

    private GridPredicate<GridNode> nodeFilter;

    /**
     * Gets property depMode.
     *
     * @return Property depMode.
     */
    GridDeploymentMode deploymentMode() {
        return depMode;
    }

    /**
     * Sets property depMode.
     *
     * @param depMode Property depMode.
     */
    void deploymentMode(GridDeploymentMode depMode) {
        this.depMode = depMode;
    }

    /**
     * Gets property alias.
     *
     * @return Property alias.
     */
    String alias() {
        return alias;
    }

    /**
     * Sets property alias.
     *
     * @param alias Property alias.
     */
    void alias(String alias) {
        this.alias = alias;
    }

    /**
     * Gets property clsName.
     *
     * @return Property clsName.
     */
    String className() {
        return clsName;
    }

    /**
     * Sets property clsName.
     *
     * @param clsName Property clsName.
     */
    void className(String clsName) {
        this.clsName = clsName;
    }

    /**
     * Gets property seqNum.
     *
     * @return Property seqNum.
     */
    long sequenceNumber() {
        return seqNum;
    }

    /**
     * Sets property seqNum.
     *
     * @param seqNum Property seqNum.
     */
    void sequenceNumber(long seqNum) {
        this.seqNum = seqNum;
    }

    /**
     * Gets property userVer.
     *
     * @return Property userVer.
     */
    String userVersion() {
        return userVer;
    }

    /**
     * Sets property userVer.
     *
     * @param userVer Property userVer.
     */
    void userVersion(String userVer) {
        this.userVer = userVer;
    }

    /**
     * Gets property senderNodeId.
     *
     * @return Property senderNodeId.
     */
    UUID senderNodeId() {
        return senderNodeId;
    }

    /**
     * Sets property senderNodeId.
     *
     * @param senderNodeId Property senderNodeId.
     */
    void senderNodeId(UUID senderNodeId) {
        this.senderNodeId = senderNodeId;
    }

    /**
     * Gets property clsLdrId.
     *
     * @return Property clsLdrId.
     */
    UUID classLoaderId() {
        return clsLdrId;
    }

    /**
     * Sets property clsLdrId.
     *
     * @param clsLdrId Property clsLdrId.
     */
    void classLoaderId(UUID clsLdrId) {
        this.clsLdrId = clsLdrId;
    }

    /**
     * Gets parent loader.
     *
     * @return Parent loader.
     */
    public ClassLoader parentLoader() {
        return parentLdr;
    }

    /**
     * Sets parent loader.
     *
     * @param parentLdr Parent loader.
     */
    public void parentLoader(ClassLoader parentLdr) {
        this.parentLdr = parentLdr;
    }

    /**
     * Gets property record.
     *
     * @return Property record.
     */
    boolean isRecord() {
        return record;
    }

    /**
     * Sets property record.
     *
     * @param record Property record.
     */
    void record(boolean record) {
        this.record = record;
    }

    /**
     * @return Node participants.
     */
    public Map<UUID, GridTuple2<UUID, Long>> participants() {
        return participants;
    }

    /**
     * @param participants Node participants.
     */
    public void participants(Map<UUID, GridTuple2<UUID, Long>> participants) {
        this.participants = participants;
    }

    /**
     * @return Class loader.
     */
    public ClassLoader classLoader() {
        return clsLdr;
    }

    /**
     * @param clsLdr Class loader.
     */
    public void classLoader(ClassLoader clsLdr) {
        this.clsLdr = clsLdr;
    }

    /**
     * @param nodeFilter Node filter.
     */
    public void nodeFilter(GridPredicate<GridNode> nodeFilter) {
        this.nodeFilter = nodeFilter;
    }

    /**
     * @return Node filter.
     */
    public GridPredicate<GridNode> nodeFilter() {
        return nodeFilter;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentMetadata.class, this);
    }
}
