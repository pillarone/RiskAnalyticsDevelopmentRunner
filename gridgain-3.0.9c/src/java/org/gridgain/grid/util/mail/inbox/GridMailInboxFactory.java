// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.util.mail.inbox.imap.*;
import org.gridgain.grid.util.mail.inbox.pop3.*;

/**
 * This class provides factory for creating {@link GridMailInbox}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridMailInboxFactory {
    /**
     * Enforces singleton.
     */
    private GridMailInboxFactory() {
        // No-op.
    }

    /**
     * Creates a mail outbox with specified configuration.
     *
     * @param cfg Inbox configuration.
     * @param matcher Object containing rules for messages filtering.
     * @param marshaller Marshaller to marshal and unmarshal objects.
     * @return Newly created mail inbox.
     */
    public static GridMailInbox createInbox(GridMailInboxConfiguration cfg, GridMailInboxMatcher matcher,
        GridMarshaller marshaller) {
        assert cfg != null;
        assert matcher != null;

        // Inbox configuration must have mail protocol set and logger.
        assert cfg.getProtocol() != null;
        assert cfg.getLogger() != null;

        switch (cfg.getProtocol()) {
            case POP3:
            case POP3S: { return new GridPop3Inbox(cfg, matcher, marshaller); }

            case IMAP:
            case IMAPS: { return new GridImapInbox(cfg, matcher); }

            default: {
                // Unsupported inbox mail protocol.
                assert false;

                // Never reached.
                return null;
            }
        }
    }
}
