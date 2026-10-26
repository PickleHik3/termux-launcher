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
 * transition that turns the pages: it starts where it stood, and the dock and the keyboard land on
 * the wall's last frame rather than ahead of it, opening a window from Home and closing it back.
 */
public class ChromeSettleTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;

    private static final List<PaneWallPage> RING =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    /** Home with its keyboard down, the window with its keyboard up. */
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

    /** The chrome's frame for the settle's answers, as the activity reads it. */
    private static Frame chrome(ChromeSettle settle, PaneWallPage current,
                                PaneWallPage committed) {
        return PlaceChromeTravel.at(RING, current, settle.chromeOffsetPx(),
            settle.keyboardOffsetPx(), WIDTH, STATES, committed, null);
    }

    /** The chrome as the wall's own position puts it, which a finger reads. */
    private static Frame wall(float offsetPx, PaneWallPage current, PaneWallPage committed) {
        return PlaceChromeTravel.at(RING, current, offsetPx, WIDTH, STATES, committed);
    }

    @Test
    public void tapOpenLandsTheKeyboardAndDockOnTheWallsLastFrame() {
        // A tap on Home slides the window in from one width to the right.
        ChromeSettle settle = new ChromeSettle();
        float from = WIDTH;
        settle.frame(from, from, WIDTH, true, false);
        Frame first = chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS);
        Frame unwarped = wall(from, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS);
        assertEquals(unwarped.keyboardReveal, first.keyboardReveal, EPSILON);
        assertEquals(unwarped.dockReveal, first.dockReveal, EPSILON);
        // On the wall's own way the keyboard is up with 5% of the slide still to go; on the
        // settle's clock it is still rising then, and up only as the wall rests.
        assertEquals(1f, wall(0.05f * WIDTH, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS)
            .keyboardReveal, EPSILON);
        float last = 0f;
        for (int step = 19; step > 0; step--) {
            float share = step * 0.05f;
            settle.frame(share * WIDTH, from, WIDTH, true, false);
            float reveal = chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS)
                .keyboardReveal;
            assertTrue("rises every frame at " + share, reveal >= last);
            assertTrue("still rising at " + share, reveal < 1f);
            last = reveal;
        }
        settle.frame(0.001f * WIDTH, from, WIDTH, true, false);
        assertEquals(1f, chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS)
            .keyboardReveal, 0.01f);
    }

    @Test
    public void tapCloseHidesTheKeyboardInStepWithTheWall() {
        // Back to Home: the window leaves to the right, its keyboard going down with it.
        ChromeSettle settle = new ChromeSettle();
        float from = -WIDTH;
        // On the wall's own way it is gone with 30% of the slide still to go.
        assertEquals(0f, wall(-0.3f * WIDTH, PaneWallPage.WIDGETS, PaneWallPage.TERMINAL)
            .keyboardReveal, EPSILON);
        settle.frame(from, from, WIDTH, true, false);
        assertEquals(1f, chrome(settle, PaneWallPage.WIDGETS, PaneWallPage.TERMINAL)
            .keyboardReveal, EPSILON);
        for (int step = 9; step > 0; step--) {
            float share = step * 0.1f;
            settle.frame(share * from, from, WIDTH, true, false);
            Frame frame = chrome(settle, PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
            // As much keyboard shows as there is slide left: it goes as the window does.
            assertEquals("keyboard at " + share, share, frame.keyboardReveal, EPSILON);
        }
    }

    @Test
    public void flingCarriesOnFromWhereTheFingerLeftTheChrome() {
        // A drag from Home brought the window 40% in; the release commits and slides the rest.
        ChromeSettle settle = new ChromeSettle();
        settle.frame(-0.4f * WIDTH, Float.NaN, WIDTH, true, true);
        Frame underFinger = chrome(settle, PaneWallPage.WIDGETS, PaneWallPage.WIDGETS);
        assertEquals(wall(-0.4f * WIDTH, PaneWallPage.WIDGETS, PaneWallPage.WIDGETS).keyboardReveal,
            underFinger.keyboardReveal, EPSILON);
        float from = 0.6f * WIDTH;
        settle.frame(from, from, WIDTH, true, false);
        Frame released = chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS);
        assertEquals(underFinger.keyboardReveal, released.keyboardReveal, EPSILON);
        assertEquals(underFinger.dockReveal, released.dockReveal, EPSILON);
        settle.frame(0.02f * from, from, WIDTH, true, false);
        assertTrue(chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.WIDGETS).keyboardReveal < 1f);
    }

    @Test
    public void springBackBringsTheKeyboardBackAsTheWallRests() {
        // From the window toward Home, let go 30% across without committing: it comes back.
        ChromeSettle settle = new ChromeSettle();
        float from = 0.3f * WIDTH;
        float released = wall(from, PaneWallPage.TERMINAL, PaneWallPage.TERMINAL).keyboardReveal;
        settle.frame(from, from, WIDTH, true, true);
        assertEquals(released, chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.TERMINAL)
            .keyboardReveal, EPSILON);
        settle.frame(0.1f * from, from, WIDTH, true, true);
        assertTrue(chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.TERMINAL).keyboardReveal < 1f);
        settle.frame(0f, Float.NaN, WIDTH, true, true);
        assertEquals(1f, chrome(settle, PaneWallPage.TERMINAL, PaneWallPage.TERMINAL)
            .keyboardReveal, EPSILON);
    }

    @Test
    public void aNewSlideCarriesOnFromWhereTheLastLeftTheChrome() {
        ChromeSettle settle = new ChromeSettle();
        settle.frame(0.5f * WIDTH, WIDTH, WIDTH, true, false);
        float chrome = settle.chromeOffsetPx();
        float keyboard = settle.keyboardOffsetPx();
        assertTrue(chrome != 0.5f * WIDTH);
        // The wall slides again from where it stands, toward the same page.
        settle.frame(0.5f * WIDTH, 0.5f * WIDTH, WIDTH, true, false);
        assertEquals(chrome, settle.chromeOffsetPx(), EPSILON);
        assertEquals(keyboard, settle.keyboardOffsetPx(), EPSILON);
    }

    @Test
    public void theSlideTransitionAFingerAndALongSlideReadTheWall() {
        ChromeSettle settle = new ChromeSettle();
        settle.frame(0.5f * WIDTH, WIDTH, WIDTH, false, false);
        assertEquals(0.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
        assertEquals(0.5f * WIDTH, settle.keyboardOffsetPx(), EPSILON);
        settle.frame(0.5f * WIDTH, Float.NaN, WIDTH, true, false);
        assertEquals(0.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
        settle.frame(1.5f * WIDTH, 2f * WIDTH, WIDTH, true, false);
        assertEquals(1.5f * WIDTH, settle.chromeOffsetPx(), EPSILON);
        assertEquals(1.5f * WIDTH, settle.keyboardOffsetPx(), EPSILON);
    }

    @Test
    public void aWayAlreadyInItsRestGoesTheRestOfTheWayWithTheWall() {
        assertEquals(30f, ChromeSettle.toward(0.5f, 60f, 80f), EPSILON);
        assertEquals(80f + (500f - 80f) * 0.5f, ChromeSettle.toward(0.5f, 500f, 80f), EPSILON);
        assertEquals(-920f, ChromeSettle.startAt(-1000f, -920f, WIDTH), EPSILON);
        assertEquals(-900f, ChromeSettle.startAt(-900f, -920f, WIDTH), EPSILON);
    }
}
