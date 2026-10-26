package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The chrome keeping the wall's clock through a settle with no finger on it, under a page
 * transition that turns the pages: it starts where it stood, and the status content (and the dock
 * with it) lands on the wall's last frame rather than ahead of it, opening a window from Home and
 * closing it back. The keyboard reads the wall's own offset throughout, so it goes as the page goes.
 */
public class ChromeSettleTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;

    private static final List<PaneWallPage> RING =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    /** Home with its keyboard down and no status bar, the window with its keyboard up. */
    private static final PlaceChromeTravel.States STATES = new PlaceChromeTravel.States() {
        private final Map<PaneWallPage, Rest> mRest = new EnumMap<>(PaneWallPage.class);
        {
            mRest.put(PaneWallPage.WIDGETS, new Rest(false, false, false));
            mRest.put(PaneWallPage.TERMINAL, new Rest(true, false));
            mRest.put(PaneWallPage.DISPLAY, new Rest(false, false));
        }
        @NonNull @Override public Rest restOf(@NonNull PaneWallPage place) {
            return mRest.get(place);
        }
    };

    /** The chrome's frame for the settle's answer, as the activity reads it. */
    private static Frame chrome(ChromeSettle settle, float wallPx, PaneWallPage current) {
        return PlaceChromeTravel.at(RING, current, settle.chromeOffsetPx(), wallPx, WIDTH, STATES,
            null);
    }

    /** The chrome as the wall's own position puts it, which a finger reads. */
    private static Frame wall(float offsetPx, PaneWallPage current) {
        return PlaceChromeTravel.at(RING, current, offsetPx, WIDTH, STATES);
    }

    @Test
    public void tapOpenLandsTheChromeOnTheWallsLastFrame() {
        // A tap on Home slides the window in from one width to the right.
        ChromeSettle settle = new ChromeSettle();
        float from = WIDTH;
        settle.frame(from, from, WIDTH, true);
        Frame first = chrome(settle, from, PaneWallPage.TERMINAL);
        Frame unwarped = wall(from, PaneWallPage.TERMINAL);
        assertEquals(unwarped.statusReveal, first.statusReveal, EPSILON);
        assertEquals(unwarped.keyboardReveal, first.keyboardReveal, EPSILON);
        // On the wall's own way the status content is in with 5% of the slide still to go; on the
        // settle's clock it is still coming then, and in only as the wall rests.
        assertEquals(1f, wall(0.05f * WIDTH, PaneWallPage.TERMINAL).statusReveal, EPSILON);
        float last = 0f;
        for (int step = 19; step > 0; step--) {
            float share = step * 0.05f;
            settle.frame(share * WIDTH, from, WIDTH, true);
            Frame frame = chrome(settle, share * WIDTH, PaneWallPage.TERMINAL);
            assertTrue("comes in every frame at " + share, frame.statusReveal >= last);
            assertTrue("still coming at " + share, frame.statusReveal < 1f);
            last = frame.statusReveal;
            // The keyboard shows as much as the page does.
            assertEquals("keyboard at " + share, 1f - share, frame.keyboardReveal, EPSILON);
        }
        settle.frame(0.001f * WIDTH, from, WIDTH, true);
        assertEquals(1f, chrome(settle, 0.001f * WIDTH, PaneWallPage.TERMINAL).statusReveal,
            0.01f);
    }

    @Test
    public void tapCloseTakesTheChromeAwayInStepWithTheWall() {
        // Back to Home: the window leaves to the right, its status content going with it.
        ChromeSettle settle = new ChromeSettle();
        float from = -WIDTH;
        settle.frame(from, from, WIDTH, true);
        assertEquals(1f, chrome(settle, from, PaneWallPage.WIDGETS).statusReveal, EPSILON);
        for (int step = 9; step > 0; step--) {
            float share = step * 0.1f;
            settle.frame(share * from, from, WIDTH, true);
            Frame frame = chrome(settle, share * from, PaneWallPage.WIDGETS);
            // The status content goes in step with the slide left, from its first stretch, where
            // it rests, to the wall's rest; the keyboard shows as much as there is slide left.
            float landing = PlaceChromeTravel.LANDING;
            assertEquals("status at " + share,
                PlaceChromeTravel.chromeFraction(landing + (1f - landing) * share),
                frame.statusReveal, EPSILON);
            assertEquals("keyboard at " + share, share, frame.keyboardReveal, EPSILON);
        }
    }

    @Test
    public void flingCarriesOnFromWhereTheFingerLeftTheChrome() {
        // A drag from Home brought the window 40% in; the release commits and slides the rest.
        ChromeSettle settle = new ChromeSettle();
        settle.frame(-0.4f * WIDTH, Float.NaN, WIDTH, true);
        Frame underFinger = chrome(settle, -0.4f * WIDTH, PaneWallPage.WIDGETS);
        assertEquals(wall(-0.4f * WIDTH, PaneWallPage.WIDGETS).statusReveal,
            underFinger.statusReveal, EPSILON);
        float from = 0.6f * WIDTH;
        settle.frame(from, from, WIDTH, true);
        Frame released = chrome(settle, from, PaneWallPage.TERMINAL);
        assertEquals(underFinger.statusReveal, released.statusReveal, EPSILON);
        assertEquals(underFinger.keyboardReveal, released.keyboardReveal, EPSILON);
        settle.frame(0.02f * from, from, WIDTH, true);
        assertTrue(chrome(settle, 0.02f * from, PaneWallPage.TERMINAL).statusReveal < 1f);
    }

    @Test
    public void springBackBringsTheChromeBackAsTheWallRests() {
        // From the window toward Home, let go 30% across without committing: it comes back.
        ChromeSettle settle = new ChromeSettle();
        float from = 0.3f * WIDTH;
        float released = wall(from, PaneWallPage.TERMINAL).statusReveal;
        settle.frame(from, from, WIDTH, true);
        assertEquals(released, chrome(settle, from, PaneWallPage.TERMINAL).statusReveal, EPSILON);
        settle.frame(0.1f * from, from, WIDTH, true);
        assertTrue(chrome(settle, 0.1f * from, PaneWallPage.TERMINAL).statusReveal < 1f);
        settle.frame(0f, Float.NaN, WIDTH, true);
        assertEquals(1f, chrome(settle, 0f, PaneWallPage.TERMINAL).statusReveal, EPSILON);
    }

    @Test
    public void aNewSlideCarriesOnFromWhereTheLastLeftTheChrome() {
        ChromeSettle settle = new ChromeSettle();
        settle.frame(0.5f * WIDTH, WIDTH, WIDTH, true);
        float chrome = settle.chromeOffsetPx();
        assertTrue(chrome != 0.5f * WIDTH);
        // The wall slides again from where it stands, toward the same page.
        settle.frame(0.5f * WIDTH, 0.5f * WIDTH, WIDTH, true);
        assertEquals(chrome, settle.chromeOffsetPx(), EPSILON);
    }

    @Test
    public void theSlideTransitionAFingerAndALongSlideReadTheWall() {
        ChromeSettle settle = new ChromeSettle();
        settle.frame(0.5f * WIDTH, WIDTH, WIDTH, false);
        assertEquals(0.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
        settle.frame(0.5f * WIDTH, Float.NaN, WIDTH, true);
        assertEquals(0.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
        settle.frame(1.5f * WIDTH, 2f * WIDTH, WIDTH, true);
        assertEquals(1.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
    }

    @Test
    public void aWayAlreadyInItsRestGoesTheRestOfTheWayWithTheWall() {
        assertEquals(30f, ChromeSettle.toward(0.5f, 60f, 80f), EPSILON);
        assertEquals(80f + (500f - 80f) * 0.5f, ChromeSettle.toward(0.5f, 500f, 80f), EPSILON);
    }
}
