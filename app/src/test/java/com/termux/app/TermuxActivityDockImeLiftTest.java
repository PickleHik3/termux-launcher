package com.termux.app;

import static org.junit.Assert.assertEquals;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The inset lift stands the dock on the keyboard only for the band of it the root's fit does not
 * cover. A root that fits system windows pads itself by the keyboard when the window resizes, so
 * lifting by the inset as well stacked two keyboard heights under the dock (fullscreen on an
 * Android 10 phone; on an Android 15 Motorola g15 the lift read the root's padding before the
 * root's own listener had set it, and the extra-keys bar floated a keyboard above the keyboard).
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxActivityDockImeLiftTest {

    private static final int KEYBOARD = 800;
    private static final int NAV = 60;

    private static WindowInsetsCompat hiddenBars(int imeBottom) {
        return new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.NONE)
            .setVisible(Type.statusBars(), false)
            .setInsets(Type.navigationBars(), Insets.NONE)
            .setVisible(Type.navigationBars(), false)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .setVisible(Type.ime(), imeBottom > 0)
            .build();
    }

    @Test
    public void fullscreenWithoutAResizeLiftsByTheKeyboard() {
        assertEquals(KEYBOARD, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), 0, 0));
    }

    @Test
    public void aWindowResizedForTheKeyboardIsNotLiftedAgain() {
        assertEquals(0, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), KEYBOARD, 0));
    }

    @Test
    public void aRootFitByPartOfTheKeyboardIsLiftedForTheRest() {
        assertEquals(KEYBOARD - 300, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), 300, 0));
    }

    @Test
    public void noKeyboardLiftsNothing() {
        assertEquals(0, TermuxActivity.dockImeLiftPx(hiddenBars(0), 0, 0));
    }

    /**
     * The lift is read from the insets alone: the root's listener runs after the activity's, so
     * the padding a view carries then is the previous dispatch's, and must not be an input.
     */
    @Test
    public void theLiftIsNotReadOffTheRootsPadding() throws Exception {
        java.nio.file.Path source = java.nio.file.Paths.get("src/main/java/com/termux/app/TermuxActivity.java");
        if (!java.nio.file.Files.exists(source))
            source = java.nio.file.Paths.get("app/src/main/java/com/termux/app/TermuxActivity.java");
        String activity = new String(java.nio.file.Files.readAllBytes(source),
            java.nio.charset.StandardCharsets.UTF_8);
        int start = activity.indexOf("private int computeDockImeLiftPx(");
        String body = activity.substring(start, activity.indexOf("\n    }\n", start));
        org.junit.Assert.assertFalse(body.contains("getPaddingBottom"));
        org.junit.Assert.assertTrue(body.contains("insets.getSystemWindowInsetBottom()"));
    }

    @Test
    public void aVisibleBarHandsTheLiftToTheRootProbe() {
        WindowInsetsCompat statusShown = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, 90, 0, 0))
            .setVisible(Type.statusBars(), true)
            .setInsets(Type.navigationBars(), Insets.NONE)
            .setVisible(Type.navigationBars(), false)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, KEYBOARD))
            .setVisible(Type.ime(), true)
            .build();
        assertEquals(0, TermuxActivity.dockImeLiftPx(statusShown, 0, 0));
        WindowInsetsCompat navShown = new WindowInsetsCompat.Builder()
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, NAV))
            .setVisible(Type.navigationBars(), true)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, KEYBOARD))
            .setVisible(Type.ime(), true)
            .build();
        assertEquals(0, TermuxActivity.dockImeLiftPx(navShown, NAV, NAV));
    }

    /** #309: the geometry pass hands the renderer whether Home's keyboard covers its rows. */
    @Test
    public void theGeometryPassTellsTheRendererWhetherHomesKeyboardCoversTheDock() throws Exception {
        java.nio.file.Path source = java.nio.file.Paths.get("src/main/java/com/termux/app/TermuxActivity.java");
        if (!java.nio.file.Files.exists(source))
            source = java.nio.file.Paths.get("app/src/main/java/com/termux/app/TermuxActivity.java");
        String activity = new String(java.nio.file.Files.readAllBytes(source),
            java.nio.charset.StandardCharsets.UTF_8);
        org.junit.Assert.assertTrue(activity.contains("mStackRenderer.setHomeCover(homeRowsCovered(), systemImeTakePx())"));
    }
}
