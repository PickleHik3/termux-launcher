package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * No swap at the half: a crossing whose two ends dress the bottom
 * stack apart in structure (Style, Corners, Margin, the layout's dock and keyboard sizes, the
 * chin) and in look, on every layout pair, with the keyboard up on both, up to down and down to
 * up, in both directions. The stack is never laid out or dressed again after the crossing
 * begins, the keyboard's reveal moves by no more than the seam's curve allows (never a jump),
 * the look blends from the leaving end's to the arriving end's, and the last frame is the
 * arriving rest's numbers. The status bar's own ride is left out ({@code splitPanes} false, so no
 * bar is laid out over Home): it is another concern.
 */
public class SeamNoSwapTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;
    private static final float STEP = 0.05f;
    /** The steepest the look's curve climbs per unit of the wall's way, as the seam's own tests. */
    private static final float MAX_CURVE_SLOPE = 1.5f / (SeamFade.END - SeamFade.START);

    private static final List<PageKind> ROW =
        Arrays.asList(PageKind.HOME, PageKind.TERMINAL);

    private static final WallShape DOCKED = WallShape.of(LayoutStyle.DOCKED, 12, 8);
    private static final WallShape FLOATING = WallShape.of(LayoutStyle.FLOATING, 26, 4);

    // ---- Layouts ----

    private static PlaceLayout layout(Map<Element, Slot> slots) {
        Map<Element, Slot> all = new EnumMap<>(Element.class);
        for (Element element : Element.values())
            all.put(element, Slot.on(Edge.BOTTOM, element).withHidden(true));
        all.putAll(slots);
        return new PlaceLayout(all, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    private static PlaceLayout rows(Element... shown) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        for (Element element : shown) slots.put(element, Slot.on(Edge.BOTTOM, element));
        return layout(slots);
    }

    /** One pair of layouts: Home's and the window's. */
    private static final class Pair {
        final String name;
        final PlaceLayout home, window;

        Pair(String name, PlaceLayout home, PlaceLayout window) {
            this.name = name;
            this.home = home;
            this.window = window;
        }
    }

    private static Pair[] pairs() {
        return new Pair[] {
            // Different rows on each: the old dip.
            new Pair("apps vs apps, index and extra keys", rows(Element.APPS),
                rows(Element.APPS, Element.AZ, Element.EXTRA_KEYS)),
            // The window's less the extra keys: the old fold.
            new Pair("one layout, extra keys on the window", rows(Element.APPS, Element.AZ),
                rows(Element.APPS, Element.AZ, Element.EXTRA_KEYS)),
            new Pair("pong", LayoutFixtures.PONG.home, LayoutFixtures.PONG.window),
            new Pair("tl16", LayoutFixtures.TL16.home, LayoutFixtures.TL16.window),
        };
    }

    // ---- Dresses ----

    private static final class Stored implements DressResolver.Source {
        final WallShape home, window;

        Stored(WallShape home, WallShape window) {
            this.home = home;
            this.window = window;
        }

        @Override public boolean minimal() { return false; }
        @Override public int pageIdOf(@NonNull PageKind page) {
            return page == PageKind.HOME ? 2 : 1;
        }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return DockStyle.GLASS_TRAY;
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) { return false; }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) { return false; }
        @Override public boolean shows(@NonNull PageKind kind, @NonNull Element element) {
            return true;
        }
        @NonNull @Override public WallShape shape() { return window; }
        @NonNull @Override public WallShape shape(@NonNull PageKind kind) {
            return kind == PageKind.HOME ? home : window;
        }
    }

    /** How the two ends dress the stack apart. */
    private static final class Dressing {
        final String name;
        final WallShape homeShape, windowShape;
        final float homeDock, windowDock, homeKeyboard, windowKeyboard;
        final int homeChin, windowChin;
        final HalfLook homeLook, windowLook;

        Dressing(String name, WallShape homeShape, WallShape windowShape, float homeDock,
                 float windowDock, float homeKeyboard, float windowKeyboard, int homeChin,
                 int windowChin, HalfLook homeLook, HalfLook windowLook) {
            this.name = name;
            this.homeShape = homeShape;
            this.windowShape = windowShape;
            this.homeDock = homeDock;
            this.windowDock = windowDock;
            this.homeKeyboard = homeKeyboard;
            this.windowKeyboard = windowKeyboard;
            this.homeChin = homeChin;
            this.windowChin = windowChin;
            this.homeLook = homeLook;
            this.windowLook = windowLook;
        }
    }

    private static Dressing[] dressings() {
        HalfLook clear = HalfLook.ofDockOpacity(100);
        HalfLook faint = HalfLook.ofDockOpacity(40);
        return new Dressing[] {
            // Docked on Home, Floating on the window, looks apart too.
            new Dressing("docked vs floating, look apart", DOCKED, FLOATING, 1f, 1f, 1f, 1f, 0, 0,
                clear, faint),
            new Dressing("floating vs docked, look apart", FLOATING, DOCKED, 1f, 1f, 1f, 1f, 0, 0,
                faint, clear),
            // The layouts' sizes and chin apart, one shape.
            new Dressing("sizes and chin apart", DOCKED, DOCKED, 1f, 0.8f, 1f, 1.2f, 0, 6, clear,
                faint),
            // Shape apart alone, the look the same on both: nothing to blend.
            new Dressing("shape apart, one look", DOCKED, FLOATING, 1f, 1f, 1f, 1f, 0, 0, clear,
                clear),
        };
    }

    private static SeamCrossing.Endpoint end(Dressing d, Pair pair, PageKind page,
                                             boolean keyboardUp) {
        DressResolver dresses = new DressResolver(new Stored(d.homeShape, d.windowShape));
        Dress dress = dresses.dressOf(page);
        boolean home = page == PageKind.HOME;
        SeamDress.StackDress stack = SeamDress.StackDress.of(dress.shape(),
            home ? d.homeDock : d.windowDock, home ? d.homeKeyboard : d.windowKeyboard,
            home ? d.homeChin : d.windowChin, home ? d.homeLook : d.windowLook);
        return SeamCrossing.Endpoint.of(PageRef.of(page), dress, home ? pair.home : pair.window,
            stack, keyboardUp);
    }

    // ---- The drive ----

    private static final class Sample {
        float fraction, keyboardReveal, endShare, dockReveal;
        boolean relayout, restyle, hasLayout;
    }

    private static Sample[] run(Dressing d, Pair pair, PageKind from, boolean fromKeyboard,
                                boolean toKeyboard) {
        PageKind to = from == PageKind.HOME ? PageKind.TERMINAL
            : PageKind.HOME;
        SeamCrossing.Endpoint here = end(d, pair, from, fromKeyboard);
        SeamCrossing.Endpoint there = end(d, pair, to, toKeyboard);
        SeamCrossing crossing = new SeamCrossing();
        PlaceChromeTravel.States states = place -> new Rest(
            place == from ? fromKeyboard : toKeyboard, false, true);
        int n = Math.round(1f / STEP);
        Sample[] samples = new Sample[n + 1];
        boolean begun = false;
        for (int i = 0; i <= n; i++) {
            float fraction = i * STEP;
            float offset = from == PageKind.HOME ? -fraction * WIDTH : fraction * WIDTH;
            Frame frame = PlaceChromeTravel.at(ROW, from, offset, WIDTH, states);
            PageKind other = SeamTravel.otherHalfPage(frame, from);
            boolean beginsNow = other != null && !begun;
            if (beginsNow) {
                crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, false));
                begun = true;
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            Sample s = new Sample();
            s.fraction = fraction;
            s.keyboardReveal = answers.keyboardReveal();
            s.dockReveal = answers.dockReveal();
            s.endShare = answers.endShare();
            // The frame that begins the crossing is the first the seam answers; every one after
            // it, and that one, must stand the stack as it is.
            s.relayout = begun && answers.relayout();
            s.restyle = begun && answers.restyle();
            if (answers.relayout()) crossing.standLayout();
            s.hasLayout = crossing.layout() != null;
            samples[i] = s;
        }
        return samples;
    }

    private interface Each {
        void at(String what, Dressing d, Pair pair, PageKind from, boolean fromKeyboard,
                boolean toKeyboard);
    }

    private static void forEveryCase(Each each) {
        boolean[][] keyboards = {{true, true}, {true, false}, {false, true}};
        for (Dressing d : dressings()) for (Pair pair : pairs())
            for (PageKind from : new PageKind[] {PageKind.HOME,
                PageKind.TERMINAL})
                for (boolean[] kb : keyboards)
                    each.at(d.name + " / " + pair.name + " / from " + from + " keyboard "
                        + kb[0] + "->" + kb[1], d, pair, from, kb[0], kb[1]);
    }

    @Test
    public void theStackIsNeverLaidOutOrDressedAgainOnTheWay() {
        forEveryCase((what, d, pair, from, fromKb, toKb) -> {
            // Only where the stack itself changes rows is the seam's own layout ever stood; with
            // the dock's rows alone apart, both sides' rows are standing already (the lanes).
            for (Sample s : run(d, pair, from, fromKb, toKb)) {
                assertFalse(what + " relayout at " + s.fraction, s.relayout);
                assertFalse(what + " restyle at " + s.fraction, s.restyle);
                assertFalse(what + " layout stood at " + s.fraction, s.hasLayout);
            }
        });
    }

    @Test
    public void theKeyboardNeverJumps() {
        forEveryCase((what, d, pair, from, fromKb, toKb) -> {
            Sample[] samples = run(d, pair, from, fromKb, toKb);
            float bound = STEP * MAX_CURVE_SLOPE + EPSILON;
            for (int i = 1; i < samples.length; i++) {
                float jump = Math.abs(samples[i].keyboardReveal - samples[i - 1].keyboardReveal);
                assertTrue(what + " keyboard jumps by " + jump + " at " + samples[i].fraction,
                    jump <= bound);
            }
            // Up on both stays up; up to down sinks and down to up rises, never both ways.
            for (int i = 1; i < samples.length; i++) {
                float step = samples[i].keyboardReveal - samples[i - 1].keyboardReveal;
                if (fromKb && toKb) assertEquals(what + " stays up", 1f,
                    samples[i].keyboardReveal, EPSILON);
                if (fromKb && !toKb) assertTrue(what + " sinks at " + samples[i].fraction,
                    step <= EPSILON);
                if (!fromKb && toKb) assertTrue(what + " rises at " + samples[i].fraction,
                    step >= -EPSILON);
            }
        });
    }

    @Test
    public void theEndShareRunsFromTheLeavingEndToTheArrivingOne() {
        forEveryCase((what, d, pair, from, fromKb, toKb) -> {
            Sample[] samples = run(d, pair, from, fromKb, toKb);
            float last = 0f;
            for (Sample s : samples) {
                if (s.fraction == 0f) continue;
                assertFalse(what + " no end share at " + s.fraction, Float.isNaN(s.endShare));
                assertTrue(what + " end share goes back at " + s.fraction,
                    s.endShare >= last - EPSILON);
                last = s.endShare;
            }
            assertEquals(what + " arrives on the arriving end", 1f,
                samples[samples.length - 1].endShare, EPSILON);
        });
    }

    @Test
    public void theLastFrameIsTheArrivingRest() {
        forEveryCase((what, d, pair, from, fromKb, toKb) -> {
            Sample[] samples = run(d, pair, from, fromKb, toKb);
            Sample end = samples[samples.length - 1];
            assertEquals(what + " keyboard at the end", toKb ? 1f : 0f, end.keyboardReveal,
                EPSILON);
            assertEquals(what + " dock at the end", 1f, end.dockReveal, EPSILON);
        });
    }
}
