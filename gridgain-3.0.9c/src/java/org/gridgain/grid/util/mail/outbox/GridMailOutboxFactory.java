// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.outbox;

import org.gridgain.grid.util.mail.outbox.smtp.*;

/**
 * This class provides factory for creating {@link GridMailOutbox} instances.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridMailOutboxFactory {
    /**
     * Enforces singleton.
     */
    private GridMailOutboxFactory() {
        // No-op.
    }

    /**
     * Creates mail outbox with specified configuration.
     *
     * @param cfg Configuration of a creating outbox.
     * @return Mail outbox.
     */
    public static GridMailOutbox createOutbox(GridMailOutboxConfiguration cfg) {
        assert cfg != null;

        // Outbox configuration must have mail protocol set.
        assert cfg.getProtocol() != null;

        // Unsupported outbox mail protocol.
        assert cfg.getProtocol() == GridMailOutboxProtocol.SMTP || cfg.getProtocol() == GridMailOutboxProtocol.SMTPS;

        return new GridSmtpOutbox(cfg);
    }
}
