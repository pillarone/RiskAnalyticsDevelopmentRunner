// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.query;

import java.lang.annotation.*;

/**
 * Annotates fields to SQL query on. All fields that will be involved in SQL where clauses must have
 * this annotation. For more information about cache queries see {@link GridCacheQuery},
 * {@link GridCacheReduceQuery}, or {@link GridCacheTransformQuery} documentation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCacheQuery
 * @see GridCacheReduceQuery
 * @see GridCacheTransformQuery
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GridCacheQuerySqlField {
    /**
     * Specifies whether cache should maintain an index for this field or not.
     * Just like with databases, field indexing may require additional overhead
     * during updates, but makes select operations faster.
     *
     * @return {@code True} if index must be created for this field in database.
     */
    boolean index() default true;

    /**
     * Specifies whether index should be unique or not. This property only
     * makes sense if {@link #index()} property is set to {@code true}.
     *
     * @return {@code True} if field index should be unique.
     */
    boolean unique() default false;

    /**
     * Array of index groups this field belongs to. Groups are used for compound indexes,
     * whenever index should be created on more than one field. All fields within the same
     * group will belong to the same index.
     * <p>
     * Most of the times user will not specify any group, which means that cache should
     * simply create a single field index.
     *
     * @return Array of group names.
     */
    String[] group() default {};

    /**
     * Property name. If not provided then field name will be used.
     * 
     * @return Name of property.
     */
    String name() default "";
}