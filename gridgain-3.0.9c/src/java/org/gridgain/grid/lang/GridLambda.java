// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;

/**
 * Common interface for closures, predicates and related entities. It defines any
 * first-class function or monad as P2P deployment and meta-programming aware.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 */
public interface GridLambda extends GridPeerDeployAware, GridMetadataAware {
    // Marker interface.
}
