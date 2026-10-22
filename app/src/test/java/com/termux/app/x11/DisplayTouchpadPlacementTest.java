package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Where the touchpad stands in the keyboard frame: the whole of it, or the seat a key would take
 * in a split keyboard's parting — and the switch on it that flips this orientation's keyboard
 * mode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DisplayTouchpadPlacementTest {

    /** 160dp at 2x. */
    private static final int MIN_GAP_PX = 320;

    /** A key's seat in [gap]: inset by a key margin at the sides, the caps' rows top to bottom. */
    private static Rect seatIn(Rect gap) {
        return new Rect(gap.left + 8, gap.top + 6, gap.right - 8, gap.bottom - 10);
    }

    @Test
    public void aGapWideEnoughToPointInSeatsThePadAsOneMoreKey() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX, 500);
        Rect seat = seatIn(gap);

        FrameLayout.LayoutParams params = DisplayTouchpadPlacement.padParams(gap, seat, 500, 2f);

        assertTrue(DisplayTouchpadPlacement.fitsGap(gap, 2f));
        assertTrue(DisplayTouchpadPlacement.standsInGap(gap, seat, 2f));
        assertEquals(seat.width(), params.width);
        assertEquals(seat.height(), params.height);
        assertEquals(seat.left, params.leftMargin);
        assertEquals(seat.top, params.topMargin);
        assertEquals(Gravity.TOP | Gravity.START, params.gravity);
    }

    @Test
    public void aGapTooNarrowToPointInLeavesThePadOnTheWholeFrame() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX - 1, 500);

        FrameLayout.LayoutParams params =
            DisplayTouchpadPlacement.padParams(gap, seatIn(gap), 500, 2f);

        assertFalse(DisplayTouchpadPlacement.fitsGap(gap, 2f));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(500, params.height);
        assertEquals(0, params.leftMargin);
        assertEquals(0, params.topMargin);
        assertEquals(Gravity.TOP, params.gravity);
    }

    @Test
    public void thatSameGapIsWideEnoughOnADenserScreensDp() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX - 1, 500);

        // 319px is 212dp at 1.5x, well past the minimum.
        assertTrue(DisplayTouchpadPlacement.fitsGap(gap, 1.5f));
        assertEquals(seatIn(gap).width(),
            DisplayTouchpadPlacement.padParams(gap, seatIn(gap), 500, 1.5f).width);
    }

    @Test
    public void noGapAtAllIsTheWholeFrame() {
        FrameLayout.LayoutParams params = DisplayTouchpadPlacement.padParams(null, null, 500, 2f);

        assertFalse(DisplayTouchpadPlacement.fitsGap(null, 2f));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(500, params.height);
    }

    @Test
    public void aGapWithNoSeatInItIsTheWholeFrame() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX, 500);

        assertFalse(DisplayTouchpadPlacement.standsInGap(gap, null, 2f));
        assertFalse(DisplayTouchpadPlacement.standsInGap(gap, new Rect(), 2f));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
            DisplayTouchpadPlacement.padParams(gap, null, 500, 2f).width);
    }

    @Test
    public void aFrameThatHasNotBeenMeasuredLeavesThePadWrappingItsContent() {
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
            DisplayTouchpadPlacement.padParams(null, null, 0, 2f).height);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
            DisplayTouchpadPlacement.padParams(new Rect(0, 0, MIN_GAP_PX, 0), null, -1, 2f)
                .height);
    }

    @Test
    public void theMinimumIsWhatASplitKeyboardIsAskedToPartBy() {
        assertEquals(MIN_GAP_PX, DisplayTouchpadPlacement.minimumGapPx(2f));
        assertEquals(480, DisplayTouchpadPlacement.minimumGapPx(3f));
        assertEquals("an unmeasured screen asks for nothing",
            0, DisplayTouchpadPlacement.minimumGapPx(0f));
    }

    @Test
    public void aPartingWidenedToTheMinimumFitsAndSeatsThePadInsideIt() {
        int minimum = DisplayTouchpadPlacement.minimumGapPx(3f);
        Rect widened = new Rect(300, 0, 300 + minimum, 700);
        Rect seat = seatIn(widened);

        FrameLayout.LayoutParams params =
            DisplayTouchpadPlacement.padParams(widened, seat, 700, 3f);

        // The ask is the strip between the key runs; the pad keeps a key's spacing inside it.
        assertTrue(DisplayTouchpadPlacement.fitsGap(widened, 3f));
        assertEquals(seat.width(), params.width);
        assertEquals(seat.left, params.leftMargin);
        assertEquals(Gravity.TOP | Gravity.START, params.gravity);
    }

    @Test
    public void anUnmeasuredScreenDensityIsNoGap() {
        assertFalse(DisplayTouchpadPlacement.fitsGap(new Rect(0, 0, MIN_GAP_PX, 500), 0f));
    }

    @Test
    public void layoutIsOnlyReappliedWhenSomethingMoved() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX, 500);
        FrameLayout.LayoutParams gapParams =
            DisplayTouchpadPlacement.padParams(gap, seatIn(gap), 500, 2f);

        assertTrue(DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(gap, seatIn(gap), 500, 2f), gapParams));
        Rect wider = new Rect(390, 0, 410 + MIN_GAP_PX, 500);
        assertFalse("a wider gap is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(wider, seatIn(wider), 500, 2f), gapParams));
        Rect taller = new Rect(400, 0, 400 + MIN_GAP_PX, 600);
        assertFalse("a taller keyboard is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(taller, seatIn(taller), 600, 2f), gapParams));
        Rect lower = seatIn(gap);
        lower.offset(0, 4);
        assertFalse("a seat that moved down is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(gap, lower, 500, 2f), gapParams));
        assertFalse("the whole frame is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(null, null, 500, 2f), gapParams));
        assertFalse(DisplayTouchpadPlacement.describes(null, gapParams));
        assertFalse(DisplayTouchpadPlacement.describes(
            new ViewGroup.LayoutParams(MIN_GAP_PX, 500), gapParams));
    }

    // ------------------------------------------------------------------ the keyboard mode switch

    private static PlaceLayoutStore store() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("display-touchpad-placement-test",
            Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        return new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    /**
     * The switch writes the one stored mode the Layout editor reads, for the orientation on
     * screen only, and a second tap puts it back.
     */
    @Test
    public void theSwitchFlipsAndSavesThisOrientationsKeyboardModeOnly() {
        PlaceLayoutStore store = store();
        // Set both explicitly: what an unset orientation falls back to is not this switch's call.
        store.setKeyboardMode(PlaceOrientation.PORTRAIT, KeyboardMode.RESIZE);
        store.setKeyboardMode(PlaceOrientation.LANDSCAPE, KeyboardMode.RESIZE);

        assertEquals(KeyboardMode.OVERLAY,
            DisplayTouchpadPlacement.switchKeyboardMode(store, PlaceOrientation.PORTRAIT));
        assertEquals(KeyboardMode.OVERLAY, store.keyboardMode(PlaceOrientation.PORTRAIT));
        assertEquals("what the Layout editor resolves", KeyboardMode.OVERLAY,
            store.resolve(PlaceOrientation.PORTRAIT).keyboardMode);
        assertEquals("the other orientation keeps its own", KeyboardMode.RESIZE,
            store.keyboardMode(PlaceOrientation.LANDSCAPE));

        assertEquals(KeyboardMode.RESIZE,
            DisplayTouchpadPlacement.switchKeyboardMode(store, PlaceOrientation.PORTRAIT));
        assertEquals(KeyboardMode.RESIZE, store.keyboardMode(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void theSwitchFlipsAStoredOverlayToResize() {
        PlaceLayoutStore store = store();
        store.setKeyboardMode(PlaceOrientation.LANDSCAPE, KeyboardMode.OVERLAY);

        assertEquals(KeyboardMode.RESIZE,
            DisplayTouchpadPlacement.switchKeyboardMode(store, PlaceOrientation.LANDSCAPE));
        assertEquals(KeyboardMode.RESIZE, store.keyboardMode(PlaceOrientation.LANDSCAPE));
        // The switch saves what it shows: a fresh store over the same preferences reads it back.
        assertEquals(KeyboardMode.RESIZE,
            new PlaceLayoutStore(new TermuxAppSharedPreferences(RuntimeEnvironment.getApplication(),
                RuntimeEnvironment.getApplication().getSharedPreferences(
                    "display-touchpad-placement-test", Context.MODE_PRIVATE), null))
                .keyboardMode(PlaceOrientation.LANDSCAPE));
    }

    /** A floating keyboard floats over the display whatever is stored, so there is nothing to switch. */
    @Test
    public void theSwitchIsOfferedWhereverTheModeDecidesSomething() {
        assertTrue(DisplayTouchpadPlacement.offersModeSwitch(KeyboardForm.DOCKED));
        assertTrue(DisplayTouchpadPlacement.offersModeSwitch(KeyboardForm.SPLIT));
        assertFalse(DisplayTouchpadPlacement.offersModeSwitch(KeyboardForm.FLOATING));
    }
}
