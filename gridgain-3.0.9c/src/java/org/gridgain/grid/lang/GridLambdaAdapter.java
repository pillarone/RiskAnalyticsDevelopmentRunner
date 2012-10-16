// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;

/**
 * Adapter for common interfaces in closures, reducers and predicates.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridLambdaAdapter extends GridMetadataAwareAdapter implements GridLambda {
    /** Peer deploy aware class. */
    private transient volatile GridPeerDeployAware pda;

    /**
     * Sets object that from which peer deployment information 
     * will be copied, i.e. this lambda object will be peer deployed
     * using the same class loader as given object.
     * <p>
     * Note that in most cases GridGain attempts to automatically call this
     * method whenever lambda classes like closures and predicates are created that
     * wrap user object (the peer deploy information in such cases will be copied
     * from the user object).
     * <p>
     * In general, if user gets class not found exception during peer loading it is
     * very likely that peer deploy information was lost during wrapping of one object
     * into another.  
     *  
     * @param obj Peer deploy aware.
     */
    public void peerDeployLike(Object obj) {
        assert obj != null;

        pda = U.peerDeployAware(obj);
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        if (pda == null) {
            pda = U.detectPeerDeployAware(this);
        }

        return pda.deployClass();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        if (pda == null) {
            pda = U.detectPeerDeployAware(this);
        }

        return pda.classLoader();
    }

    /**
     * Copies metadata from this lambda function into the passed in metadata aware instance.
     *
     * @param t Passed in metadata aware instance.
     * @param <T> Type of the metadata aware instance.
     * @return Metadata aware instance with metadata copied.
     */
    protected <T extends GridMetadataAware> T withMeta(T t) {
        assert t != null;

        t.copyMeta(this);

        return t;
    }
}
