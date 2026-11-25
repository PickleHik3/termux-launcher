package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.termux.app.chrome.ChromeEdgeRule;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.place.LayoutFixtures;
import com.termux.app.place.RestCards;
import com.termux.app.chrome.RoundCut;
import com.termux.app.wall.PageRef;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * The Terminal's own Peek card wears the Peek dress (#307 views A): its host is clipped to the
 * Peek cut, top corners rounded and the bottom off the screen, and the program's rim is aimed at
 * that cut with the bottom left open. Off Peek the host keeps its own rect.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalHostPeekDressTest {

    private DressSources.Terminal mSource;
    private TerminalHostDress mDress;
    private View mHost;
    private final PeekGlassGeometry.Card mPeek =
        new PeekGlassGeometry.Card(21f, 40f, 1059f, 420f, 68f, 460f, 40f, 120f);

    @Before
    public void setUp() {
        DockControllerTest.FakeHost host = new DockControllerTest.FakeHost();
        mSource = new DressSources.Terminal();
        mDress = new TerminalHostDress(new DockController(host), mSource);
        mHost = new View(RuntimeEnvironment.getApplication());
        mHost.layout(0, 0, 1080, 460);
    }

    /** A Terminal card of {@code id} on its own host, from the card fixtures. */
    private static RestCards.Card card(RestCards.CardId id) {
        for (LayoutFixtures.Fixture base : LayoutFixtures.cardFixtures())
            for (DockStyle design : DockStyle.CHOICES)
                for (DockStyle homeDesign : DockStyle.CHOICES)
                for (boolean homeUp : new boolean[] {false, true})
                    for (boolean floating : new boolean[] {false, true}) {
                        LayoutFixtures.Fixture f = base.withDesign(design)
                            .withHomeDesign(homeDesign);
                        RestCards cards = RestCards.of(f.cardsRest(PageRef.TERMINAL, false,
                            homeUp, floating));
                        RestCards.Card card = cards.cardOn(RestCards.StackView.TERMINAL_HOST);
                        if (card != null && card.id == id) return card;
                    }
        throw new AssertionError("no fixture stands a " + id + " card on the Terminal's host");
    }

    private ViewOutlineProvider outline() {
        return ReflectionHelpers.getField(mDress, "mPeekOutline");
    }

    private GlassBackdropDress.Owner owner() {
        GlassBackdropDress backdrop = ReflectionHelpers.getField(mDress, "mBackdrop");
        return ReflectionHelpers.getField(backdrop, "mOwner");
    }

    /** A Peek card: the outline is the cut's round rect at the cut's radius. */
    @Test public void aPeekCardClipsTheHostToTheCut() {
        mSource.card = card(RestCards.CardId.PEEK);
        mSource.peek = mPeek;
        float[] cut = new float[RoundCut.SIZE];
        PeekCardAim.cut(mPeek, cut);
        Outline outline = new Outline();
        outline().getOutline(mHost, outline);
        Rect rect = new Rect();
        assertTrue(outline.getRect(rect));
        assertEquals(new Rect(Math.round(cut[RoundCut.LEFT]), Math.round(cut[RoundCut.TOP]),
            Math.round(cut[RoundCut.RIGHT]), Math.round(cut[RoundCut.BOTTOM])), rect);
        assertEquals(cut[RoundCut.RADIUS], outline.getRadius(), 0.01f);
        assertTrue("the cut's top runs through the icons, below the host's top",
            rect.top >= 0 && cut[RoundCut.RADIUS] > 0f);
    }

    /** The program's rim follows the cut, its bottom pushed past the screen's edge. */
    @Test public void thePeekRimIsAimedAtTheCutWithItsBottomOpen() {
        mSource.card = card(RestCards.CardId.PEEK);
        mSource.peek = mPeek;
        float[] cut = new float[RoundCut.SIZE];
        PeekCardAim.cut(mPeek, cut);
        float[] got = new float[4];
        assertTrue(owner().cutRect(got, 12, 30f));
        float[] want = new float[4];
        GlassRefraction.rimRect(want, 12 + cut[RoundCut.LEFT], cut[RoundCut.TOP],
            12 + cut[RoundCut.RIGHT], cut[RoundCut.BOTTOM], 30f, ChromeEdgeRule.BOTTOM);
        assertArrayEquals(want, got, 0.001f);
    }

    /** Off Peek (a Docked slice, or no cut handed in) the host keeps its own rect and rim. */
    @Test public void offPeekTheHostKeepsItsOwnRect() {
        mSource.card = card(RestCards.CardId.DOCK);
        mSource.peek = mPeek;
        assertFalse("a Docked slice wears no Peek cut", owner().cutRect(new float[4], 0, 0f));
        Outline outline = new Outline();
        outline().getOutline(mHost, outline);
        Rect rect = new Rect();
        assertTrue(outline.getRect(rect));
        assertEquals(new Rect(0, 0, 1080, 460), rect);

        mSource.card = card(RestCards.CardId.PEEK);
        mSource.peek = null;
        assertFalse("no cut handed in: no Peek dress", owner().cutRect(new float[4], 0, 0f));
    }
}
