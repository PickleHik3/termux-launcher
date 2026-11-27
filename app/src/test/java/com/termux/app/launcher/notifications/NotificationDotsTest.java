package com.termux.app.launcher.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class NotificationDotsTest {

    private static Set<String> active(String... packages) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, packages);
        return set;
    }

    @Test public void anAppWithANotificationShowsADotWhileDotsAreOn() {
        assertTrue(NotificationDots.shows(true, "a.b", active("a.b", "c.d")));
    }

    @Test public void dotsOffShowNoDotAnywhere() {
        assertFalse(NotificationDots.shows(false, "a.b", active("a.b")));
    }

    @Test public void anAppWithoutNotificationsOrAPackageShowsNoDot() {
        assertFalse(NotificationDots.shows(true, "x.y", active("a.b")));
        assertFalse(NotificationDots.shows(true, null, active("a.b")));
    }

    @Test public void terlIsDotsOnlyWhateverStyleWasChosen() {
        assertEquals(NotificationBadgeStyle.DOT, NotificationDots.styleFor(true, NotificationBadgeStyle.COUNT));
        assertEquals(NotificationBadgeStyle.COUNT, NotificationDots.styleFor(false, NotificationBadgeStyle.COUNT));
    }

    @Test public void theDotSitsInsideTheIconsTopRightCorner() {
        float[] out = new float[2];
        NotificationDots.centre(10f, 20f, 60f, 70f, out);
        assertEquals(50f, out[0], 0.001f);
        assertEquals(30f, out[1], 0.001f);
    }

    @Test public void theRadiusNeverFallsUnderThreeAndAHalfDp() {
        assertEquals(7f, NotificationDots.radiusPx(10f, 2f), 0.001f);
        assertEquals(15f, NotificationDots.radiusPx(200f, 2f), 0.001f);
    }
}
