// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.lang.annotation.*;
import java.util.*;

/**
 * This annotation disables caching of task results when attached to {@link GridTask} class
 * being executed. By default all results are cached and passed into
 * {@link GridTask#result(GridJobResult,List) GridTask.result(GridJobResult, List&lt;GridJobResult&gt;)}
 * method or {@link GridTask#reduce(List) GridTask.reduce(List&lt;GridJobResult&gt;)} method.
 * When this annotation is attached to a task class, then {@link GridJobResult#getData()} always
 * will return {@code null} for all jobs cached in result list.
 * <p>
 * Use this annotation when job results are too large to hold in memory and can be discarded
 * after being processed in
 * {@link GridTask#result(GridJobResult, List) GridTask.result(GridJobResult, List&lt;GridJobResult&gt;)}
 * method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GridTaskNoResultCache {
    // No-op.
}
