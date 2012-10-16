// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.gridify;

import org.gridgain.grid.*;

/**
 * Convenience adapter for tasks that work with {@link Gridify} annotation
 * for grid-enabling methods. It enhances the regular {@link GridTaskSplitAdapter}
 * by enforcing the argument type of {@link GridifyArgument}. All tasks
 * that work with {@link Gridify} annotation receive an argument of this type.
 * <p>
 * Please refer to {@link GridTaskSplitAdapter} documentation for more information
 * on additional functionality this adapter provides.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <R> Return value of the task (see {@link GridTask#reduce(java.util.List)} method).
 */
public abstract class GridifyTaskSplitAdapter<R> extends GridTaskSplitAdapter<GridifyArgument, R> {
    // No-op.
}
