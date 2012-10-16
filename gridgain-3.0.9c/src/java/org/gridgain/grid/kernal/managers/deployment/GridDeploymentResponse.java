// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import java.io.*;

/**
 * Grid deployment response containing requested resource bytes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDeploymentResponse implements Externalizable {
    /** Result state. */
    private boolean success;

    /** */
    private String errMsg;

    /** Raw class/resource/task. */
    private GridByteArrayList byteSrc;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    @SuppressWarnings({"RedundantNoArgConstructor"})
    public GridDeploymentResponse() {
        // No-op.
    }

    /**
     * Sets raw class/resource or serialized task as bytes array.
     *
     * @param byteSrc Class/resource/task source.
     */
    void setByteSource(GridByteArrayList byteSrc) {
        this.byteSrc = byteSrc;
    }

    /**
     * Gets raw class/resource or serialized task source as bytes array.
     * @return Class/resource/task source.
     */
    GridByteArrayList getByteSource() {
        return byteSrc;
    }

    /**
     * Tests whether corresponding request was processed successful of not.
     *
     * @return {@code true} if request for the source processed
     *      successfully and {@code false} if not.
     */
    boolean isSuccess() {
        return success;
    }

    /**
     * Sets corresponding request processing status.
     *
     * @param success {@code true} if request processed successfully and
     *      response keeps source inside and {@code false} otherwise.
     */
    void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets request processing error message. If request processed with error,
     * message will be put in response.
     *
     * @return  Request processing error message.
     */
    String getErrorMessage() {
        return errMsg;
    }

    /**
     * Sets request processing error message.
     *
     * @param errMsg Request processing error message.
     */
    void setErrorMessage(String errMsg) {
        this.errMsg = errMsg;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(byteSrc);
        out.writeBoolean(success);

        U.writeString(out, errMsg);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        byteSrc = (GridByteArrayList)in.readObject();
        success = in.readBoolean();

        errMsg = U.readString(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDeploymentResponse.class, this);
    }
}
