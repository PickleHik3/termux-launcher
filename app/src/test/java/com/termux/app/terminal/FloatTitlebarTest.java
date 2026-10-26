package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.widget.ImageButton;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FloatTitlebarTest {

    // --- Policy ---

    @Test
    public void buttons_listMinimiseOnlyWhereTheHostHasATargetForIt() {
        assertArrayEquals(new int[]{
                FloatTitlebarPolicy.ACTION_MAXIMISE, FloatTitlebarPolicy.ACTION_CLOSE},
            FloatTitlebarPolicy.buttons(false));
        assertArrayEquals(new int[]{
                FloatTitlebarPolicy.ACTION_MINIMISE, FloatTitlebarPolicy.ACTION_MAXIMISE,
                FloatTitlebarPolicy.ACTION_CLOSE},
            FloatTitlebarPolicy.buttons(true));
    }

    @Test
    public void glyphInk_onGlassIsTheWindowBarsLabelInkOnTheChromesPolarity() {
        int lightOnSurface = 0xFF171C24;
        int lightSurfaceBase = 0xFFF4F6FB;
        int paleTint = 0x33F4F6FB;
        // The light theme over a dark wallpaper: the chrome reads pale ink, and so does the strip,
        // where solving against the pale tint alone gave near-black glyphs that read as disabled.
        assertEquals(lightSurfaceBase, FloatTitlebarPolicy.glyphInk(true,
            com.termux.app.chrome.ChromeInk.Polarity.PALE_INK, lightOnSurface, lightSurfaceBase,
            paleTint, true));
        assertEquals("full alpha on an unfocused float too", lightSurfaceBase,
            FloatTitlebarPolicy.glyphInk(true, com.termux.app.chrome.ChromeInk.Polarity.PALE_INK,
                lightOnSurface, lightSurfaceBase, paleTint, false));
        assertEquals(lightOnSurface, FloatTitlebarPolicy.glyphInk(true,
            com.termux.app.chrome.ChromeInk.Polarity.DARK_INK, lightOnSurface, lightSurfaceBase,
            paleTint, true));

        int darkOnSurface = 0xFFE8EDF7;
        int darkSurfaceBase = 0xFF0F1218;
        assertEquals(darkOnSurface, FloatTitlebarPolicy.glyphInk(true,
            com.termux.app.chrome.ChromeInk.Polarity.PALE_INK, darkOnSurface, darkSurfaceBase,
            0x33101418, true));
        assertEquals("the dark theme over a light wallpaper", darkSurfaceBase,
            FloatTitlebarPolicy.glyphInk(true, com.termux.app.chrome.ChromeInk.Polarity.DARK_INK,
                darkOnSurface, darkSurfaceBase, 0x33101418, true));
    }

    @Test
    public void glyphInk_onThePanelOrUnmeasuredIsLegibleOnTheStrip() {
        int onSurface = 0xFF171C24;
        int panel = 0xFFFFFFFF;
        assertEquals(com.termux.app.chrome.GlassInk.legible(panel, onSurface,
                com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT, 255),
            FloatTitlebarPolicy.glyphInk(false, com.termux.app.chrome.ChromeInk.Polarity.PALE_INK,
                onSurface, 0xFFF4F6FB, panel, true));
        assertEquals(com.termux.app.chrome.GlassInk.legible(panel, onSurface,
                com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT, 170),
            FloatTitlebarPolicy.glyphInk(true, null, onSurface, 0xFFF4F6FB, panel, false));
    }

    @Test
    public void dock_isInTheHandlesMenuNeverOnTheStrip() {
        // The two-pane Dock glyph read as Split (developer, 2026-10-18).
        for (boolean minimise : new boolean[]{false, true}) {
            for (int action : FloatTitlebarPolicy.buttons(minimise))
                assertTrue(action != FloatTitlebarPolicy.ACTION_DOCK);
        }
        assertArrayEquals(new int[]{FloatTitlebarPolicy.ACTION_DOCK},
            FloatTitlebarPolicy.handleMenu());
    }

    @Test
    public void buttonSlots_keepTheirWidthUntilTheStripRunsOutThenShareIt() {
        assertEquals(40, FloatTitlebarPolicy.buttonSlotPx(400, 4, 40));
        // 120 px for four buttons: each gets a quarter, so Close never falls off the end.
        assertEquals(30, FloatTitlebarPolicy.buttonSlotPx(120, 4, 40));
        assertEquals(0, FloatTitlebarPolicy.buttonSlotPx(0, 4, 40));
        assertEquals(0, FloatTitlebarPolicy.buttonSlotPx(400, 0, 40));
    }

    @Test
    public void maximise_fillsTheRoomAndRemembersThenRestoreForgets() {
        RectF shape = new RectF(.1f, .2f, .6f, .7f);
        FloatTitlebarPolicy.Toggle maximised = FloatTitlebarPolicy.toggleMaximise(shape, null);
        assertEquals(new RectF(0f, 0f, 1f, 1f), maximised.frac);
        assertEquals(shape, maximised.restoreFrac);
        assertTrue(FloatTitlebarPolicy.isMaximised(maximised.restoreFrac));

        FloatTitlebarPolicy.Toggle restored =
            FloatTitlebarPolicy.toggleMaximise(maximised.frac, maximised.restoreFrac);
        assertEquals(shape, restored.frac);
        assertNull(restored.restoreFrac);
        assertFalse(FloatTitlebarPolicy.isMaximised(restored.restoreFrac));
    }

    @Test
    public void maximise_doesNotAliasTheShapesItIsHanded() {
        RectF shape = new RectF(.1f, .2f, .6f, .7f);
        FloatTitlebarPolicy.Toggle maximised = FloatTitlebarPolicy.toggleMaximise(shape, null);
        shape.offset(.1f, .1f);
        assertEquals(new RectF(.1f, .2f, .6f, .7f), maximised.restoreFrac);
    }

    // --- View ---

    @Test
    public void windowFloat_stripHasNoMinimiseButton() {
        FloatTitlebarView strip = strip(false, new ArrayList<>());
        assertNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE));
        assertNotNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE));
        assertNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_DOCK));
        assertNotNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE));
    }

    @Test
    public void theHandleIsNamedForTalkBackAndItsLongPressAsksForTheMenu() {
        int[] asked = {0};
        Context themed = new android.view.ContextThemeWrapper(context(),
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FloatTitlebarView strip = new FloatTitlebarView(themed, true,
            new FloatTitlebarView.Listener() {
                @Override public void onTitlebarAction(int action) {}
                @Override public void onHandleMenu() { asked[0]++; }
            });
        assertEquals(context().getString(R.string.titlebar_handle_description),
            strip.getContentDescription());
        assertTrue(strip.performLongClick());
        assertEquals(1, asked[0]);
    }

    @Test
    public void scratchpad_stripOffersMinimise() {
        FloatTitlebarView strip = strip(true, new ArrayList<>());
        ImageButton minimise = strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE);
        assertNotNull(minimise);
        assertEquals(context().getString(R.string.titlebar_minimise_description),
            minimise.getContentDescription());
    }

    @Test
    public void maximiseButton_readsAsRestoreWhileMaximised() {
        FloatTitlebarView strip = strip(false, new ArrayList<>());
        ImageButton maximise = strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE);
        assertEquals(context().getString(R.string.titlebar_maximise_description),
            maximise.getContentDescription());
        strip.setMaximised(true);
        assertEquals(context().getString(R.string.titlebar_restore_description),
            maximise.getContentDescription());
        strip.setMaximised(false);
        assertEquals(context().getString(R.string.titlebar_maximise_description),
            maximise.getContentDescription());
    }

    @Test
    public void buttonsSitAtTheTrailingEndWithCloseOutermost() {
        FloatTitlebarView strip = laidOut(strip(true, new ArrayList<>()), 1000);
        float density = context().getResources().getDisplayMetrics().density;
        int slot = Math.round(FloatTitlebarPolicy.BUTTON_DP * density);
        ImageButton close = strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE);
        assertEquals(1000, close.getRight());
        assertEquals(slot, close.getWidth());
        ImageButton maximise = strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE);
        assertEquals(close.getLeft(), maximise.getRight());
        ImageButton minimise = strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE);
        assertEquals(maximise.getLeft(), minimise.getRight());
        assertEquals("Minimise leads the three", 1000 - 3 * slot, minimise.getLeft());
    }

    @Test
    public void actionAt_answersButtonsAndLeavesTheRestOfTheStripToTheDrag() {
        FloatTitlebarView strip = laidOut(strip(false, new ArrayList<>()), 1000);
        ImageButton close = strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE);
        float y = strip.getHeight() / 2f;
        assertEquals(FloatTitlebarPolicy.ACTION_CLOSE,
            strip.actionAt((close.getLeft() + close.getRight()) / 2f, y));
        // The leading span is the drag handle.
        assertEquals(FloatTitlebarPolicy.ACTION_NONE, strip.actionAt(10f, y));
        // Nothing below the strip is the strip's: the pane's first row stays the terminal's.
        assertEquals(FloatTitlebarPolicy.ACTION_NONE,
            strip.actionAt((close.getLeft() + close.getRight()) / 2f, strip.getHeight() + 1f));
    }

    @Test
    public void metrics_aThinStripWhoseButtonsStillReachAThumb() {
        // The strip is the glyph and a little air; the target is the reach, not the strip.
        android.graphics.drawable.Drawable glyph = context().getDrawable(R.drawable.ic_titlebar_close);
        float density = context().getResources().getDisplayMetrics().density;
        assertNotNull(glyph);
        assertTrue("the glyph fits the strip with air above and below",
            glyph.getIntrinsicHeight() + 2 * Math.round(2 * density)
                <= Math.round(FloatTitlebarPolicy.HEIGHT_DP * density));
        assertTrue("a thumb-sized target", FloatTitlebarPolicy.BUTTON_REACH_DP >= 40);
        assertTrue("the reach covers the gap under the strip", FloatTitlebarPolicy.BUTTON_REACH_DP
            > FloatTitlebarPolicy.HEIGHT_DP + FloatTitlebarPolicy.GAP_DP);

        assertTrue(FloatTitlebarPolicy.inButtonReach(0f, 24, 40));
        assertTrue(FloatTitlebarPolicy.inButtonReach(39.5f, 24, 40));
        assertFalse(FloatTitlebarPolicy.inButtonReach(40f, 24, 40));
        assertFalse("never above the strip", FloatTitlebarPolicy.inButtonReach(-1f, 24, 40));
        assertTrue("a strip taller than the reach is its own reach",
            FloatTitlebarPolicy.inButtonReach(50f, 60, 40));
    }

    @Test
    public void actionInReach_takesAPressJustUnderAButtonButNotUnderTheHandle() {
        FloatTitlebarView strip = laidOut(strip(false, new ArrayList<>()), 1000);
        ImageButton close = strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE);
        float density = context().getResources().getDisplayMetrics().density;
        float reach = FloatTitlebarPolicy.BUTTON_REACH_DP * density;
        float x = (close.getLeft() + close.getRight()) / 2f;
        assertEquals(FloatTitlebarPolicy.ACTION_CLOSE, strip.actionInReach(x, strip.getHeight() + 1f));
        assertEquals(FloatTitlebarPolicy.ACTION_CLOSE, strip.actionInReach(x, reach - 1f));
        assertEquals(FloatTitlebarPolicy.ACTION_NONE, strip.actionInReach(x, reach + 1f));
        assertEquals("the handle's span reaches nothing",
            FloatTitlebarPolicy.ACTION_NONE, strip.actionInReach(10f, strip.getHeight() + 1f));
    }

    @Test
    public void narrowStrip_stillFitsEveryButton() {
        FloatTitlebarView strip = laidOut(strip(true, new ArrayList<>()), 90);
        ImageButton minimise = strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE);
        assertTrue(minimise.getLeft() >= 0);
        assertEquals(90, strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE).getRight());
    }

    @Test
    public void tappingAButtonReportsItsAction() {
        List<Integer> actions = new ArrayList<>();
        FloatTitlebarView strip = strip(true, actions);
        strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE).performClick();
        strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE).performClick();
        strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE).performClick();
        assertEquals(java.util.Arrays.asList(FloatTitlebarPolicy.ACTION_MAXIMISE,
            FloatTitlebarPolicy.ACTION_MINIMISE, FloatTitlebarPolicy.ACTION_CLOSE), actions);
    }

    @Test
    public void withoutGlass_theStripIsThePanesPanelAndHasNoBackdrop() {
        FloatTitlebarView strip = laidOut(strip(false, new ArrayList<>()), 600);
        strip.dress(null, 40f);
        assertNotNull(strip.getBackground());
        // The glass backdrop is the first child and stays hidden while there is no glass.
        assertEquals(View.GONE, strip.getChildAt(0).getVisibility());
    }

    // --- The handle ---

    @Test
    public void grip_centresInTheSpanTheButtonsLeaveOrStandsDown() {
        // Buttons start at 400 - 120 = 280: the span's middle.
        assertEquals(140f, FloatTitlebarPolicy.gripCenterPx(400, 120, 28, 8), 0f);
        assertEquals("too tight for the grip and its air", -1f,
            FloatTitlebarPolicy.gripCenterPx(160, 120, 28, 8), 0f);
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static FloatTitlebarView strip(boolean minimise, List<Integer> actions) {
        Context themed = new android.view.ContextThemeWrapper(context(), R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return new FloatTitlebarView(themed, minimise, actions::add);
    }

    private static FloatTitlebarView laidOut(FloatTitlebarView strip, int width) {
        int height = Math.round(FloatTitlebarPolicy.HEIGHT_DP
            * context().getResources().getDisplayMetrics().density);
        strip.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        strip.layout(0, 0, width, height);
        return strip;
    }

    // --- The handle's menu ---

    @Test
    public void handleMenuTitle_widensTheMenuRatherThanEllipsizingAShortName() {
        android.widget.LinearLayout shell = handleMenuShell("workspace notes");
        android.widget.TextView title = (android.widget.TextView) shell.getChildAt(0);
        assertNotNull(title.getLayout());
        assertEquals("the whole name shows", 0, title.getLayout().getEllipsisCount(0));
        assertTrue(shell.getMeasuredWidth() >= title.getMeasuredWidth());
    }

    @Test
    public void handleMenuTitle_aNameLongerThanTheMenuStillEllipsizes() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 40; i++) name.append("window ");
        android.widget.LinearLayout shell = handleMenuShell(name.toString().trim());
        android.widget.TextView title = (android.widget.TextView) shell.getChildAt(0);
        assertTrue(title.getLayout().getEllipsisCount(0) > 0);
    }

    /** The menu's content as TerminalPaneController builds it, measured the way AnchoredMenu does. */
    private static android.widget.LinearLayout handleMenuShell(String name) {
        Context themed = new android.view.ContextThemeWrapper(context(), R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        com.termux.app.launcher.popup.AnchoredMenuTheme theme =
            new com.termux.app.launcher.popup.AnchoredMenuTheme() {
                @Override public int textColor() { return 0xFF000000; }
                @Override public int selectedTextColor() { return 0xFF000000; }
                @Override public int opacityPercent() { return 0; }
                @Override public boolean blurEnabled() { return false; }
                @Override public int blurRadiusDp() { return 0; }
            };
        com.termux.app.launcher.popup.MenuRowFactory rows =
            new com.termux.app.launcher.popup.MenuRowFactory(themed, theme);
        android.widget.LinearLayout shell = rows.newShell();
        TerminalPaneController.addHandleMenuTitle(shell, name);
        rows.addDivider(shell);
        rows.addActionRow(shell, "Dock", 0x202020, () -> { });
        shell.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        shell.layout(0, 0, shell.getMeasuredWidth(), shell.getMeasuredHeight());
        return shell;
    }
}
