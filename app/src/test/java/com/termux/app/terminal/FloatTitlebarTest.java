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
        assertArrayEquals(new int[]{FloatTitlebarPolicy.ACTION_DOCK,
                FloatTitlebarPolicy.ACTION_MAXIMISE, FloatTitlebarPolicy.ACTION_CLOSE},
            FloatTitlebarPolicy.buttons(false));
        assertArrayEquals(new int[]{FloatTitlebarPolicy.ACTION_DOCK,
                FloatTitlebarPolicy.ACTION_MINIMISE, FloatTitlebarPolicy.ACTION_MAXIMISE,
                FloatTitlebarPolicy.ACTION_CLOSE},
            FloatTitlebarPolicy.buttons(true));
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
        assertNotNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_DOCK));
        assertNotNull(strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE));
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
        assertEquals(close.getLeft(),
            strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE).getRight());
        assertEquals(1000 - 4 * slot, strip.buttonFor(FloatTitlebarPolicy.ACTION_DOCK).getLeft());
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
    public void narrowStrip_stillFitsEveryButton() {
        FloatTitlebarView strip = laidOut(strip(true, new ArrayList<>()), 120);
        ImageButton dock = strip.buttonFor(FloatTitlebarPolicy.ACTION_DOCK);
        assertTrue(dock.getLeft() >= 0);
        assertEquals(120, strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE).getRight());
    }

    @Test
    public void tappingAButtonReportsItsAction() {
        List<Integer> actions = new ArrayList<>();
        FloatTitlebarView strip = strip(true, actions);
        strip.buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE).performClick();
        strip.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE).performClick();
        strip.buttonFor(FloatTitlebarPolicy.ACTION_DOCK).performClick();
        strip.buttonFor(FloatTitlebarPolicy.ACTION_CLOSE).performClick();
        assertEquals(java.util.Arrays.asList(FloatTitlebarPolicy.ACTION_MAXIMISE,
            FloatTitlebarPolicy.ACTION_MINIMISE, FloatTitlebarPolicy.ACTION_DOCK,
            FloatTitlebarPolicy.ACTION_CLOSE), actions);
    }

    @Test
    public void withoutGlass_theStripIsThePanesPanelAndHasNoBackdrop() {
        FloatTitlebarView strip = laidOut(strip(false, new ArrayList<>()), 600);
        strip.dress(null, 40f);
        assertNotNull(strip.getBackground());
        // The glass backdrop is the first child and stays hidden while there is no glass.
        assertEquals(View.GONE, strip.getChildAt(0).getVisibility());
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
}
