// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.editions;

import java.lang.annotation.*;

/**
 * Indicates that feature annotated with this annotation available fully or partially in enterprise
 * edition only. Attempt to use this feature from Community Edition may result in
 * {@link GridEnterpriseFeatureException} exception being thrown. In other cases, the feature may
 * silently no-op. In general, the usage of such feature from Community Edition may results in undefined
 * behavior.
 * <p>
 * Note that this annotation exists for documentation purposes only.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface GridEnterpriseFeature {
    /**
     * @return Description or explanation wherever applicable.
     */
    String value() default "";
}
