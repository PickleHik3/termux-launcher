package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.extrakeys.HomePillSpot;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;


/**
 * The pill's visibility matrix, its key feed, its spot and what its drop and chip do. Robolectric
 * only for org.json, which the key page parser needs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HomePillControllerTest {

    private static final float DENSITY = 2f;

    private final FakeHost host = new FakeHost();
    private HomePillController controller;

    @Before
    public void setUp() {
        controller = new HomePillController(host);
    }

    @Test
    public void showsWhenEveryFactAllowsIt() {
        assertTrue(controller.shows());
        controller.refresh();
        assertTrue(controller.isShown());
        assertEquals(1, host.shownCalls);
        assertTrue(host.shown);
    }

    @Test
    public void eachCoveringFactHidesIt() {
        java.util.List<java.util.function.Consumer<FakeHost>> covers = java.util.Arrays.asList(
            h -> h.switchOn = false,
            h -> h.place = PageKind.TERMINAL,
            h -> h.place = PageKind.DISPLAY,
            h -> h.place = PageKind.ASSISTANT,
            h -> h.target = ExtraKeysTarget.TERMINAL,
            h -> h.target = ExtraKeysTarget.GLOBAL,
            h -> h.target = null,
            h -> h.drawer = true,
            h -> h.palette = true,
            h -> h.layoutEditor = true,
            h -> h.surfaceEditor = true,
            h -> h.appearance = true,
            h -> h.overlay = true,
            h -> h.minimal = true,
            h -> h.keys = null);
        for (int i = 0; i < covers.size(); i++) {
            FakeHost h = new FakeHost();
            covers.get(i).accept(h);
            HomePillController c = new HomePillController(h);
            assertFalse("cover " + i + " hides the pill", c.shows());
            c.refresh();
            assertFalse("cover " + i + " never shows it", h.shown);
            assertEquals("cover " + i + " feeds no keys", 0, h.setKeysCalls);
        }
    }

    @Test
    public void aShownPillHidesWhenACoverComes() {
        controller.refresh();
        assertTrue(host.shown);
        host.drawer = true;
        controller.refresh();
        assertFalse(host.shown);
        assertFalse(controller.isShown());
        host.drawer = false;
        controller.refresh();
        assertTrue(host.shown);
    }

    @Test
    public void staysHiddenUntilItHasASize() {
        host.pillW = 0;
        controller.refresh();
        assertFalse(host.shown);
        assertEquals(0, host.placeCalls);
        host.pillW = 200;
        controller.refresh();
        assertTrue(host.shown);
        assertEquals(1, host.placeCalls);
    }

    @Test
    public void keysAreFedOnlyWhenTheyChange() {
        controller.refresh();
        controller.refresh();
        controller.refresh();
        assertEquals(1, host.setKeysCalls);
        host.keys = keys();
        controller.refresh();
        assertEquals(2, host.setKeysCalls);
        host.scale = 1.5f;
        controller.refresh();
        assertEquals(3, host.setKeysCalls);
        assertEquals(1.5f, host.fedScale, 0f);
        controller.refresh();
        assertEquals(3, host.setKeysCalls);
    }

    @Test
    public void noKeysAreFedWhileItIsHidden() {
        host.drawer = true;
        controller.refresh();
        assertEquals(0, host.setKeysCalls);
    }

    @Test
    public void aRefreshThatFindsNothingNewPlacesNothing() {
        controller.refresh();
        int areas = host.areaCalls;
        int places = host.placeCalls;
        controller.refresh();
        controller.refresh();
        assertEquals(areas, host.areaCalls);
        assertEquals(places, host.placeCalls);
    }

    @Test
    public void aMovedStackReplacesIt() {
        controller.refresh();
        float firstTop = host.placedTop;
        host.geo[HomePillController.GEO_STACK_TOP] = 900f;
        controller.refresh();
        assertEquals(2, host.areaCalls);
        assertEquals(2, host.placeCalls);
        assertTrue(host.placedTop != firstTop || host.placeCalls == 2);
    }

    @Test
    public void theDefaultSpotIsTheRightSideCentred() {
        controller.refresh();
        float[] expected = new float[2];
        HomePillSpot.Area area = HomePillSpot.area(1080f, 2000f, 0f, 0f, 0f, 0f,
            HomePillSpot.GUTTER_DP * DENSITY, 100f, 1800f, null,
            HomePillSpot.DEFAULT_INSET_DP * DENSITY);
        HomePillSpot.place(null, 200f, 92f, area, expected);
        assertEquals(expected[0], host.placedLeft, 0.01f);
        assertEquals(expected[1], host.placedTop, 0.01f);
    }

    @Test
    public void aStoredSpotIsWhereItRests() {
        host.stored = new float[] {0.5f, 0.5f};
        controller.refresh();
        assertEquals(540f - 100f, host.placedLeft, 0.01f);
        assertEquals(1000f - 46f, host.placedTop, 0.01f);
    }

    @Test
    public void aPickedUpPillIsNotMovedFromUnderTheFinger() {
        controller.refresh();
        int places = host.placeCalls;
        host.pickedUp = true;
        host.geo[HomePillController.GEO_STACK_TOP] = 700f;
        controller.refresh();
        assertEquals(places, host.placeCalls);
        assertTrue(host.shown);
    }

    @Test
    public void aDropStoresTheCentreFractions() {
        controller.refresh();
        controller.onPillDropped(440f, 954f);
        assertNotNull(host.storedWrite);
        assertEquals((440f + 100f) / 1080f, host.storedWrite[0], 0.0001f);
        assertEquals((954f + 46f) / 2000f, host.storedWrite[1], 0.0001f);
        // The next refresh finds the pill where the drop left it and moves nothing.
        int places = host.placeCalls;
        controller.refresh();
        assertEquals(places, host.placeCalls);
    }

    @Test
    public void reloadReadsTheStoredSpotAgain() {
        controller.refresh();
        host.stored = new float[] {0.25f, 0.25f};
        controller.reload();
        assertEquals(270f - 100f, host.placedLeft, 0.01f);
    }

    @Test
    public void theKeyboardComingUpDocksThePillInsteadOfHidingIt() {
        controller.refresh();
        assertTrue(host.shown);
        assertFalse(controller.isDocked());
        host.keyboardUp = true;
        assertTrue(controller.shows());
        controller.refresh();
        assertTrue("still on screen", host.shown);
        assertTrue(controller.isShown());
        assertTrue(controller.isDocked());
        assertTrue(host.docked);
        assertEquals(1, host.dockedCalls);
        // A refresh that finds nothing new says nothing again.
        controller.refresh();
        assertEquals(1, host.dockedCalls);
    }

    @Test
    public void theKeyboardGoingAwayLetsTheDockedPillGo() {
        host.keyboardUp = true;
        controller.refresh();
        assertTrue(host.docked);
        host.keyboardUp = false;
        controller.refresh();
        assertTrue(host.shown);
        assertFalse(host.docked);
        assertFalse(controller.isDocked());
        assertEquals(2, host.dockedCalls);
    }

    @Test
    public void aTerminalFloatFocusedOverTheKeyboardHidesItAndDocksNothing() {
        host.keyboardUp = true;
        host.target = ExtraKeysTarget.TERMINAL;
        controller.refresh();
        assertFalse(host.shown);
        assertFalse(controller.isDocked());
        assertFalse(host.docked);
        assertEquals("never told it docked", 0, host.dockedCalls);
    }

    @Test
    public void aDockedPillThatAFactHidesIsNoLongerDocked() {
        host.keyboardUp = true;
        controller.refresh();
        assertTrue(host.docked);
        host.drawer = true;
        controller.refresh();
        assertFalse(host.shown);
        assertFalse(host.docked);
        assertFalse(controller.isDocked());
    }

    @Test
    public void aDockedPillIsNotPlacedOnItsSpot() {
        controller.refresh();
        int places = host.placeCalls;
        host.keyboardUp = true;
        host.pickedUp = true;
        host.geo[HomePillController.GEO_STACK_TOP] = 700f;
        controller.refresh();
        assertEquals("the dock owns its pose", places, host.placeCalls);
    }

    @Test
    public void aFlightHoldsTheShownPillThoughHomeIsGone() {
        controller.refresh();
        assertTrue(host.shown);
        host.place = PageKind.TERMINAL;
        host.target = ExtraKeysTarget.TERMINAL;
        controller.holdForFlight(true);
        int keysFed = host.setKeysCalls;
        controller.refresh();
        assertTrue(host.shown);
        assertTrue(controller.isShown());
        assertEquals("it keeps Home's keys", keysFed, host.setKeysCalls);
        controller.holdForFlight(false);
        assertFalse("letting go rechecks and hides it", host.shown);
        assertFalse(controller.isShown());
    }

    @Test
    public void holdingAPillThatIsNotShownDoesNothing() {
        host.place = PageKind.TERMINAL;
        controller.refresh();
        controller.holdForFlight(true);
        host.place = PageKind.HOME;
        host.target = ExtraKeysTarget.HOME;
        controller.refresh();
        assertTrue("no hold was taken, so Home shows it", host.shown);
    }

    @Test
    public void theChipOpensTheEditor() {
        controller.onEditKeys();
        assertEquals(1, host.editorOpened);
    }

    @Test
    public void noStoredSpotMeansNoWrite() {
        controller.refresh();
        assertNull(host.storedWrite);
    }

    private static ExtraKeysInfo keys() {
        try {
            return new ExtraKeysInfo("[['a', 'b', 'c']]", "default",
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void everyCoverClosingBringsThePillBackWithoutALayoutPass() {
        java.util.List<java.util.function.BiConsumer<FakeHost, Boolean>> covers = java.util.Arrays.asList(
            (h, on) -> h.drawer = on,
            (h, on) -> h.palette = on,
            (h, on) -> h.layoutEditor = on,
            (h, on) -> h.surfaceEditor = on,
            (h, on) -> h.appearance = on,
            (h, on) -> h.overlay = on);
        for (int i = 0; i < covers.size(); i++) {
            FakeHost h = new FakeHost();
            HomePillController c = new HomePillController(h);
            c.refreshIfCoversChanged();
            assertTrue("cover " + i + " starts shown", h.shown);
            covers.get(i).accept(h, true);
            c.refreshIfCoversChanged();
            assertFalse("cover " + i + " opens: hidden", h.shown);
            covers.get(i).accept(h, false);
            c.refreshIfCoversChanged();
            assertTrue("cover " + i + " closes: shown again", h.shown);
            assertTrue(c.isShown());
        }
    }

    @Test
    public void thePaletteOpenedFromThePillsOwnKeyReturnsTheNextFrame() {
        controller.refreshIfCoversChanged();
        assertTrue(host.shown);
        host.palette = true;
        controller.refreshIfCoversChanged();
        assertFalse(host.shown);
        host.palette = false;
        controller.refreshIfCoversChanged();
        assertTrue(host.shown);
        int calls = host.shownCalls;
        controller.refreshIfCoversChanged();
        controller.refreshIfCoversChanged();
        assertEquals("nothing changed, nothing told", calls, host.shownCalls);
    }

    private static final class FakeHost implements HomePillController.Host {
        boolean switchOn = true;
        PageKind place = PageKind.HOME;
        ExtraKeysTarget target = ExtraKeysTarget.HOME;
        boolean keyboardUp;
        boolean drawer;
        boolean palette;
        boolean layoutEditor;
        boolean surfaceEditor;
        boolean appearance;
        boolean overlay;
        boolean minimal;
        ExtraKeysInfo keys = HomePillControllerTest.keys();
        float scale = 1f;
        final float[] geo = new float[HomePillController.GEO_SIZE];
        float[] stored;
        int pillW = 200;
        int pillH = 92;
        boolean pickedUp;
        boolean docked;
        int dockedCalls;

        int setKeysCalls;
        float fedScale;
        int areaCalls;
        int placeCalls;
        float placedLeft;
        float placedTop;
        int shownCalls;
        boolean shown;
        float[] storedWrite;
        int editorOpened;

        FakeHost() {
            reset();
        }

        void reset() {
            geo[HomePillController.GEO_WIDTH] = 1080f;
            geo[HomePillController.GEO_HEIGHT] = 2000f;
            geo[HomePillController.GEO_ROOM_TOP] = 100f;
            geo[HomePillController.GEO_STACK_TOP] = 1800f;
        }

        @Override public boolean switchOn() { return switchOn; }
        @NonNull @Override public PageKind settledPlace() { return place; }
        @Nullable @Override public ExtraKeysTarget keyTarget() { return target; }
        @Override public boolean keyboardUp() { return keyboardUp; }
        @Override public boolean appDrawerOpen() { return drawer; }
        @Override public boolean paletteOpen() { return palette; }
        @Override public boolean layoutEditorActive() { return layoutEditor; }
        @Override public boolean surfaceEditorActive() { return surfaceEditor; }
        @Override public boolean appearanceOpen() { return appearance; }
        @Override public boolean overlayOpen() { return overlay; }
        @Override public boolean minimalMode() { return minimal; }
        @Nullable @Override public ExtraKeysInfo keys() { return keys; }
        @Override public float keysScale() { return scale; }
        @Override public void setKeys(@Nullable ExtraKeysInfo keys, float scale) {
            setKeysCalls++;
            fedScale = scale;
        }
        @Override public float density() { return DENSITY; }
        @Override public boolean readGeometry(@NonNull float[] out) {
            System.arraycopy(geo, 0, out, 0, out.length);
            return true;
        }
        @Nullable @Override public float[] storedPosition() { return stored; }
        @Override public void storePosition(float x, float y) { storedWrite = new float[] {x, y}; }
        @Override public int pillWidth() { return pillW; }
        @Override public int pillHeight() { return pillH; }
        @Override public boolean pillPickedUp() { return pickedUp; }
        @Override public void setArea(@NonNull HomePillSpot.Area area) { areaCalls++; }
        @Override public void placePill(float left, float top) {
            placeCalls++;
            placedLeft = left;
            placedTop = top;
        }
        @Override public void setPillShown(boolean shown) {
            shownCalls++;
            this.shown = shown;
        }
        @Override public void setPillDocked(boolean docked) {
            dockedCalls++;
            this.docked = docked;
        }
        @Override public void openKeysEditor() { editorOpened++; }
    }
}
