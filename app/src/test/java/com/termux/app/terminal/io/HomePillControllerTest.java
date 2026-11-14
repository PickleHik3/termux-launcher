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

import com.termux.app.terminal.extrakeys.HomePillSpot;
import com.termux.app.wall.PaneWallPage;
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
            h -> h.place = PaneWallPage.TERMINAL,
            h -> h.place = PaneWallPage.DISPLAY,
            h -> h.place = PaneWallPage.ASSISTANT,
            h -> h.target = ExtraKeysTarget.TERMINAL,
            h -> h.target = ExtraKeysTarget.GLOBAL,
            h -> h.target = null,
            h -> h.keyboardUp = true,
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
        host.keyboardUp = true;
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

    private static final class FakeHost implements HomePillController.Host {
        boolean switchOn = true;
        PaneWallPage place = PaneWallPage.WIDGETS;
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
        @NonNull @Override public PaneWallPage settledPlace() { return place; }
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
        @Override public void openKeysEditor() { editorOpened++; }
    }
}
