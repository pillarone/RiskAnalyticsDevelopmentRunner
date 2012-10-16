// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

/**
 * Interface similar in purpose to Scala's {@code Product} trait.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTypedProduct<T> extends Iterable<T> {
    /**
     * Gets size of this product.
     *
     * @return Size of this product.
     */
    public int arity();

    /**
     * Gets n{@code th} part or element for this product.
     *
     * @param n Zero-based index of the part.
     * @return N{@code th} part or element for this product.
     * @throws IndexOutOfBoundsException In case of invalid index.
     */
    public T part(int n);
}
