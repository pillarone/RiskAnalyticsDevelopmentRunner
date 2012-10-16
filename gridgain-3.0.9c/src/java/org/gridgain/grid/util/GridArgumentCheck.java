// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

/**
 * This class encapsulates argument check (null and range) for public facing APIs. Unlike asserts
 * it throws "normal" exceptions with standardized messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see A
 */
public class GridArgumentCheck {
    /**
     * Checks if given argument value is not {@code null}. Otherwise - throws {@link NullPointerException}.
     *
     * @param val Argument value to check.
     * @param name Name of the argument in the code (used in error message).
     */
    @SuppressWarnings({"ProhibitedExceptionThrown"})
    public static void notNull(Object val, String name) {
        if (val == null) {
            throw new NullPointerException("Ouch! Argument cannot be null: " + name);
        }
    }

    /**
     * Checks that none of the given values are {@code null}. Otherwise - throws {@link NullPointerException}.
     *
     * @param val1 1st argument value to check.
     * @param name1 Name of the 1st argument in the code (used in error message).
     * @param val2 2nd argument value to check.
     * @param name2 Name of the 2nd argument in the code (used in error message).
     */
    @SuppressWarnings({"ProhibitedExceptionThrown"})
    public static void notNull(Object val1, String name1, Object val2, String name2) {
        notNull(val1, name1);
        notNull(val2, name2);
    }

    /**
     * Checks that none of the given values are {@code null}. Otherwise - throws {@link NullPointerException}.
     *
     * @param val1 1st argument value to check.
     * @param name1 Name of the 1st argument in the code (used in error message).
     * @param val2 2nd argument value to check.
     * @param name2 Name of the 2nd argument in the code (used in error message).
     * @param val3 3rd argument value to check.
     * @param name3 Name of the 3rd argument in the code (used in error message).
     */
    @SuppressWarnings({"ProhibitedExceptionThrown"})
    public static void notNull(Object val1, String name1, Object val2, String name2, Object val3, String name3) {
        notNull(val1, name1);
        notNull(val2, name2);
        notNull(val3, name3);
    }

    /**
     * Checks that none of the given values are {@code null}. Otherwise - throws {@link NullPointerException}.
     *
     * @param val1 1st argument value to check.
     * @param name1 Name of the 1st argument in the code (used in error message).
     * @param val2 2nd argument value to check.
     * @param name2 Name of the 2nd argument in the code (used in error message).
     * @param val3 3rd argument value to check.
     * @param name3 Name of the 3rd argument in the code (used in error message).
     * @param val4 4th argument value to check.
     * @param name4 Name of the 4th argument in the code (used in error message).
     */
    @SuppressWarnings({"ProhibitedExceptionThrown"})
    public static void notNull(Object val1, String name1, Object val2, String name2, Object val3, String name3,
        Object val4, String name4) {
        notNull(val1, name1);
        notNull(val2, name2);
        notNull(val3, name3);
        notNull(val4, name4);
    }

    /**
     * Checks if given argument's condition is equal to {@code true}, otherwise
     * throws {@link IllegalArgumentException} exception.
     *
     * @param cond Argument's value condition to check.
     * @param desc Description of the condition to be used in error message.
     */
    public static void ensure(boolean cond, String desc) {
        if (!cond) {
            throw new IllegalArgumentException("Ouch! Argument is invalid: " + desc);
        }
    }
}
