// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.*;
import org.junit.runners.model.*;
import java.util.*;

/**
 * JUnit 4 enclosed runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4EnclosedRunner extends GridJunit4SuiteRunner {
    /**
     * @param cls Suite class.
     */
    GridJunit4EnclosedRunner(Class<?> cls) {
        super(cls);
    }

    /** {@inheritDoc} */
    @Override protected List<GridJunit4Runner> createChildren() {
        List<GridJunit4Runner> children = new ArrayList<GridJunit4Runner>();

        try {
            for (Class<?> cls : getTestClass().getClasses()) {
                children.add(getRunner(cls));
            }
        }
        catch (InitializationError e) {
            throw new GridRuntimeException("Failed to create children.", e);
        }

        return children;
    }
}
