package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockMorph;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * D11 for the Home ⇄ Terminal crossing with the Terminal's keyboard closed
 * ({@code project-docs/active/chrome-elements-2026-10-31.md} section 4): the terminal's page is
 * laid out at the Terminal's rest wherever the wall stands, so its grid never changes on the
 * round trip Terminal → Home → Terminal: not as either crossing begins, not mid-slide, not at
 * either landing. The crossing is driven the way the activity drives it (doSyncChromeTravel,
 * doSettlePlaceChrome): the room given back on the way out, the Terminal's pre-size on the way
 * back, the landings; the rooms are the layout fixtures' own passes and rests. The page's height
 * is the content's room (the hold's while one holds, else the committed page's landing) less the
 * inset its page was last laid out with.
 */
@RunWith(Parameterized.class)
public class TerminalRoundTripParityTest {

    @Parameterized.Parameters(name = "{0}, {1}")
    public static List<Object[]> cases() {
        List<Object[]> out = new ArrayList<>();
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL) {
            out.add(new Object[] {f, "Floating"});
            out.add(new Object[] {f, "Docked"});
        }
        return out;
    }

    private static final int WIDTH = 1000;
    private static final float STEP = 0.05f;
    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);

    private final LayoutFixtures.Fixture f;
    private final boolean floating;

    public TerminalRoundTripParityTest(LayoutFixtures.Fixture fixture, String style) {
        f = fixture;
        floating = style.equals("Floating");
    }

    // ---- The adapter: only the page's inset is read ----

    private static final class Views implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard, Crossing.Dock {
        int insetPx;
        @Override public void translate(float dockPx, float keyboardPx) {}
        @Override public void sink(float px) {}
        @Override public void fold() {}
        @Override public void alpha(float status, float side) {}
        @Override public void rideX(float px) {}
        @Override public void cover(int px) {}
        @Override public void preSize(int px) { insetPx = Math.max(0, px); }
        @Override public void preRoll(@NonNull PaneWallPage target) {}
        @Override public void beginMorph(@NonNull DockLayout leaving, @NonNull DockLayout arriving) {}
        @Override public void dress(@NonNull DockMorph morph) {}
        @Override public void endMorph(@NonNull DockLayout landed) {}
    }

    private static final class Stored implements DressResolver.Source {
        @Override public boolean minimal() { return false; }
        @Override public boolean homeLayout() { return true; }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean shows(@NonNull com.termux.app.wall.WallPages.PageKind kind,
                                       @NonNull Element element) {
            return kind != com.termux.app.wall.WallPages.PageKind.HOME
                || element != Element.EXTRA_KEYS;
        }
        @NonNull @Override public WallShape shape() {
            return WallShape.of(LayoutStyle.DOCKED, 12, 8);
        }
    }

    private final Views views = new Views();
    private final Crossing crossing = new Crossing(views, views, views, views, views);

    private SeamCrossing.Endpoint end(@NonNull PaneWallPage page) {
        Dress dress = new DressResolver(new Stored()).dressOf(page);
        PageRef ref = PageRef.of(page);
        PlaceLayout layout = f.layoutOf(ref);
        LayoutStyle style = floating ? LayoutStyle.FLOATING : LayoutStyle.DOCKED;
        ResolvedArrangement arranged = page == PaneWallPage.WIDGETS
            ? ResolvedArrangement.of(layout, style, f.homeDesign, true, true,
                PlaceOrientation.PORTRAIT, LayoutVariant.HOME, PageRef.HOME)
            : ResolvedArrangement.of(layout, style, DockStyle.GLASS_TRAY, false, false,
                PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL, PageRef.TERMINAL);
        return SeamCrossing.Endpoint.of(ref, dress, null, arranged, layout,
            SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0), false, -1);
    }

    private DockLayout dockRest(@NonNull PaneWallPage page) {
        PageRef ref = PageRef.of(page);
        return DockGeometry.compute(f.snapshot(ref, floating).restOf(ref));
    }

    /** The room the content holds: the hold's while one holds, else the committed page's. */
    private int contentReservationPx(@NonNull PaneWallPage committed) {
        if (crossing.holdsContent()) return crossing.heldReservationPx();
        return f.settle(PageRef.of(committed), false, floating).contentReservationPx;
    }

    /** The terminal's page as it is laid out: the content's height less its page's inset. */
    private int terminalPagePx(@NonNull PaneWallPage committed) {
        return f.heightPx - contentReservationPx(committed) - views.insetPx;
    }

    /** One slide from {@code from}, landed on the other half, asserting the page every frame. */
    private void slide(@NonNull PaneWallPage from, int restPx) {
        PaneWallPage to = from == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL
            : PaneWallPage.WIDGETS;
        String way = f + (floating ? ", Floating" : ", Docked") + ", " + from + " → " + to;
        PlaceChromeTravel.States states = place -> new Rest(false, false, true);
        int terminalRest = f.restReservationPx(PageRef.TERMINAL, false, floating);
        int homeRest = f.restReservationPx(PageRef.HOME, false, floating);
        int committedRest = f.settle(PageRef.of(from), false, floating).contentReservationPx;
        boolean begun = false;
        boolean preRolled = false;
        for (float t = STEP; t <= 1f + 1e-4f; t += STEP) {
            float fraction = Math.min(1f, t);
            float offset = from == PaneWallPage.WIDGETS ? -fraction * WIDTH : fraction * WIDTH;
            Frame frame = PlaceChromeTravel.at(ROW, from, offset, WIDTH, states, from);
            if (!begun) {
                crossing.begin(end(from), end(to), SeamCrossing.Stage.of(false, () -> false,
                    false), dockRest(from), dockRest(to), 1);
                begun = true;
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.seam().standLayout();
            // Toward a place whose chrome takes less room, the content gets it now.
            int roomChange = (from == PaneWallPage.TERMINAL ? committedRest : homeRest)
                - (to == PaneWallPage.TERMINAL ? terminalRest : homeRest);
            if (!preRolled && roomChange > 0) {
                crossing.giveRoomBack(roomChange, committedRest);
                preRolled = true;
            }
            // Toward the Terminal, its page is pre-sized to its rest.
            if (to == PaneWallPage.TERMINAL && crossing.terminalPreSizePx() == 0
                && -roomChange > 0) {
                crossing.preSizeTerminal(-roomChange, committedRest);
            }
            float translation = crossing.stack(answers, answers.dockReveal(), 0, 200, 150);
            crossing.coverAt(translation);
            assertEquals(way + " at " + fraction, restPx, terminalPagePx(from));
        }
        crossing.land(PageRef.of(to), 1);
        crossing.end();
        assertEquals(way + ", landed", restPx, terminalPagePx(to));
    }

    @Test
    public void theTerminalsGridNeverChangesOnARoundTripThroughHome() {
        int restPx = terminalPagePx(PaneWallPage.TERMINAL);
        assertEquals(f.heightPx - f.settle(PageRef.TERMINAL, false, floating).contentReservationPx,
            restPx);
        slide(PaneWallPage.TERMINAL, restPx);
        slide(PaneWallPage.WIDGETS, restPx);
        // And again: the second round trip finds everything as the first left it.
        slide(PaneWallPage.TERMINAL, restPx);
        slide(PaneWallPage.WIDGETS, restPx);
        assertEquals("nothing is laid short on the Terminal", 0, views.insetPx);
    }
}
