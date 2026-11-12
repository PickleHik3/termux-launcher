package com.termux.app.terminal;

import com.termux.terminal.KittyNotification;
import com.termux.terminal.KittyNotifications;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** What a terminal program's message carries from its wire form. */
public class ShellNotificationsTest {

    /** Builds one finished message the way the terminal does, from its wire form. */
    private static KittyNotification notification(String metadataAndPayload) {
        KittyNotification[] built = new KittyNotification[1];
        new KittyNotifications().handle(metadataAndPayload, new KittyNotifications.Handler() {
            @Override
            public void show(KittyNotification notification) {
                built[0] = notification;
            }

            @Override
            public void close(String id) {
            }

            @Override
            public void write(String escapeSequence) {
            }
        });
        assertNotNull("nothing was built from: " + metadataAndPayload, built[0]);
        return built[0];
    }

    @Test
    public void urgencyAndTapWishesSurviveTheWireForm() {
        KittyNotification message = notification("u=2:a=report,-focus:w=3000;Deploy failed");
        assertEquals(KittyNotification.URGENCY_CRITICAL, message.getUrgency());
        assertTrue(message.isReportOnActivate());
        assertFalse(message.isFocusOnActivate());
        assertEquals(3000, message.getTimeoutMillis());
    }
}
