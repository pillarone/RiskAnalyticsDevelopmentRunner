// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.typedef;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;

/**
 * Defines {@code alias} for <tt>GridPredicate&lt;GridRichNode&gt;</tt> by extending
 * {@link GridPredicate}. Since Java doesn't provide type aliases (like Scala, for example) we resort
 * to these types of measures. This is intended to provide for more concise code without sacrificing
 * readability. For more information see {@link GridPredicate} and {@link GridRichNode}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridPredicate
 * @see GridRichNode
 * @see GridFunc
 */
public abstract class PN extends GridPredicate<GridRichNode> { /* No-op. */ }
