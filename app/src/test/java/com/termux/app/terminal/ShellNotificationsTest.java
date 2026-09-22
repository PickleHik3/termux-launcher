package com.termux.app.terminal;

import com.termux.terminal.KittyNotification;
import com.termux.terminal.KittyNotifications;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** When a message a terminal program sent is worth putting in the shade. */
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
    public void plainMessageIsAlwaysShown() {
        KittyNotification message = notification(";Build finished");
        assertEquals(KittyNotification.OCCASION_ALWAYS, message.getOccasion());
        assertTrue(ShellNotifications.shouldShow(message, true, true));
        assertTrue(ShellNotifications.shouldShow(message, false, false));
    }

    @Test
    public void unfocusedMessageIsHeldBackOnlyWhileItsPaneIsInFront() {
        KittyNotification message = notification("o=unfocused;Build finished");
        assertFalse(ShellNotifications.shouldShow(message, true, true));
        assertTrue(ShellNotifications.shouldShow(message, true, false));
        assertTrue(ShellNotifications.shouldShow(message, false, true));
    }

    @Test
    public void invisibleMessageWaitsForTheLauncherToBePutAway() {
        KittyNotification message = notification("o=invisible;Build finished");
        assertFalse(ShellNotifications.shouldShow(message, true, true));
        assertFalse(ShellNotifications.shouldShow(message, true, false));
        assertTrue(ShellNotifications.shouldShow(message, false, false));
    }

    @Test
    public void anUnknownOccasionFallsBackToShowingIt() {
        KittyNotification message = notification("o=whenever;Build finished");
        assertEquals(KittyNotification.OCCASION_ALWAYS, message.getOccasion());
        assertTrue(ShellNotifications.shouldShow(message, true, true));
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
