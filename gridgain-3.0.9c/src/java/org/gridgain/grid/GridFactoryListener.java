// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Listener for gird state change notifications. Use
 * {@link GridFactory#addListener(GridFactoryListener)} to register this
 * listener with grid factory.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridFactoryListener extends EventListener {
    /**
     * Listener for grid factory state change notifications.
     *
     * @param name Grid name ({@code null} for default un-named grid).
     * @param state New state.
     */
    public void onStateChange(@Nullable String name, GridFactoryState state);
}
