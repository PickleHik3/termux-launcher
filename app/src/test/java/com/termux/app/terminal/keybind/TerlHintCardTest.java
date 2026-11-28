package com.termux.app.terminal.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.ViewGroup;
import android.view.View;
import android.widget.FrameLayout;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.leader.Leader;
import com.termux.app.leader.LeaderChord;
import com.termux.app.terminal.TerminalBindingConfigTesting;
import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Terl key's hint card (ADR 0042): what it lists comes from the live binding table (Terl's
 * defaults with the user's rebinds), grouped, and when it shows follows the held leader.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TerlHintCardTest {

    private static final LauncherToolRegistry.ActionContext CONTEXT =
        new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return true; }
            @Override public boolean hasCurrentSession() { return true; }
            @Override public boolean hasSelectedText() { return false; }
        };

    private static final KeybindHintModel.Labels LABELS = (tool, label) ->
        label != null ? label : tool;

    @Before public void setUp() { TerminalBindingConfigTesting.reset(); }
    @After public void tearDown() { TerminalBindingConfigTesting.reset(); }

    /** The live resolver under Terl with {@code file} as the user's bindings (null for none). */
    private static KeybindHintPresenter.Hints terl(@Nullable String file) {
        TerminalKeyBindingResolver.getInstance();
        if (file != null) TerminalBindingConfigTesting.install(file);
        TerminalKeyBindingResolver.setLeader(Leader.TERL_KEY, true);
        return new KeybindHintPresenter.Hints() {
            @NonNull @Override
            public Map<String, TerminalKeyBindingResolver.Hint> hintsForPrefix(@NonNull String prefix) {
                return TerminalKeyBindingResolver.getInstance().hintsForPrefix(prefix, CONTEXT);
            }

            @NonNull @Override public Leader leader() {
                return TerminalKeyBindingResolver.getInstance().leader();
            }
        };
    }

    private static List<TerlHintCard.Section> card(KeybindHintPresenter.Hints hints) {
        return TerlHintCard.build(
            hints.hintsForPrefix(TerminalKeyBindingResolver.LEADER_HINT_PREFIX),
            hints.hintsForPrefix(TerminalKeyBindingResolver.LEADER_HINT_PREFIX + "shift+"), LABELS);
    }

    @Nullable
    private static TerlHintCard.Row row(List<TerlHintCard.Section> card, TerlHintCard.Family family) {
        for (TerlHintCard.Section section : card)
            for (TerlHintCard.Row row : section.rows) if (row.family == family) return row;
        return null;
    }

    // ------------------------------------------------------------------ what it lists

    @Test
    public void theDefaultsAreGroupedAsThePrototypeDrawsThem() {
        List<TerlHintCard.Section> card = card(terl(null));
        List<TerlHintCard.Group> groups = new ArrayList<>();
        for (TerlHintCard.Section section : card) groups.add(section.group);
        assertEquals(java.util.Arrays.asList(TerlHintCard.Group.WINDOWS, TerlHintCard.Group.WORKSPACES,
            TerlHintCard.Group.PLACES, TerlHintCard.Group.TOOLS), groups);

        assertEquals("↵", row(card, TerlHintCard.Family.TERMINAL).capsText());
        assertEquals("Q", row(card, TerlHintCard.Family.CLOSE).capsText());
        assertEquals("← ↑ ↓ →", row(card, TerlHintCard.Family.FOCUS).capsText());
        assertEquals("⇧ ← ↑ ↓ →", row(card, TerlHintCard.Family.MOVE).capsText());
        assertEquals("1 2 3 …9", row(card, TerlHintCard.Family.GO).capsText());
        assertEquals("⇧ 1 2 3 …9", row(card, TerlHintCard.Family.SEND).capsText());
        assertEquals("V", row(card, TerlHintCard.Family.FLOAT).capsText());
        assertEquals("F", row(card, TerlHintCard.Family.FULL_SCREEN).capsText());
        assertEquals("W", row(card, TerlHintCard.Family.TAB).capsText());
        assertEquals("Tab", row(card, TerlHintCard.Family.OVERVIEW).capsText());
        assertEquals("Space", row(card, TerlHintCard.Family.SEARCH).capsText());
        assertEquals(",", row(card, TerlHintCard.Family.EDIT_MODE).capsText());
        // Each cap fires its own chord; ⇧ and the elided run fire nothing.
        TerlHintCard.Row move = row(card, TerlHintCard.Family.MOVE);
        assertNull(move.caps.get(0).chord);
        assertEquals("shift+left", move.caps.get(1).chord);
        assertNull(row(card, TerlHintCard.Family.GO).caps.get(3).chord);
    }

    @Test
    public void aRebindShowsWhereTheActionNowIs() {
        // The user moved Close to X and bound G to an app: the card follows the table.
        List<TerlHintCard.Section> card = card(terl(
            "unmap ctrl+alt+q\nmap ctrl+alt+x window.close\n"
                + "map --label Browser ctrl+alt+g app.launch com.example\n"));
        assertEquals("X", row(card, TerlHintCard.Family.CLOSE).capsText());
        TerlHintCard.Row app = null;
        for (TerlHintCard.Section section : card)
            for (TerlHintCard.Row row : section.rows) if ("Browser".equals(row.label)) app = row;
        assertNotNull(app);
        assertEquals(TerlHintCard.Group.TOOLS, app.group);
        assertEquals("G", app.capsText());
    }

    @Test
    public void aStoredSplitLineDoesNotReachTheCard() {
        List<TerlHintCard.Section> card = card(terl("map ctrl+alt+enter pane.split\n"));
        assertEquals("↵", row(card, TerlHintCard.Family.TERMINAL).capsText());
        for (TerlHintCard.Section section : card)
            for (TerlHintCard.Row row : section.rows)
                assertFalse(String.valueOf(row.label), "pane.split".equals(row.label));
    }

    @Test
    public void aGapInTheWorkspacesShowsEveryKeyItHolds() {
        // Workspace 5's key was taken off: the row must not read as 1 2 3 …9.
        List<TerlHintCard.Section> card = card(terl("unmap ctrl+alt+5\n"));
        assertEquals("1 2 3 4 6 7 8 9", row(card, TerlHintCard.Family.GO).capsText());
        assertEquals("⇧ 1 2 3 …9", row(card, TerlHintCard.Family.SEND).capsText());
    }

    @Test
    public void theFallbackFillFollowsTheInk() {
        int pale = 0xFFF0F0F0;
        int dark = 0xFF202020;
        assertTrue("pale ink stands on a dark fill",
            androidx.core.graphics.ColorUtils.calculateLuminance(TerlHintCardView.fallbackFill(pale)) < 0.2d);
        assertTrue("dark ink stands on a pale fill",
            androidx.core.graphics.ColorUtils.calculateLuminance(TerlHintCardView.fallbackFill(dark)) > 0.6d);
    }

    @Test
    public void aCardRaisedWithQuestionHasNoHoldAndResetTakesItDown() {
        FakeSurface surface = new FakeSurface();
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(surface, new FakeClock(), terl(null), LABELS);
        presenter.toggle();
        assertTrue(surface.showing);
        // Nothing to let go: Back (the HINT_CARD overlay) resets it.
        presenter.reset();
        assertFalse(surface.showing);
    }

    @Test
    public void theHeaderNamesTheHeldLeader() {
        assertEquals("Terl", TerlHintCardPresenter.leaderName(
            TerlHintCardPresenter.Source.TERL_KEY, Leader.TERL_KEY));
        assertEquals("Ctrl+Alt", TerlHintCardPresenter.leaderName(
            TerlHintCardPresenter.Source.HARDWARE, Leader.TERL_KEY));
        assertEquals("Ctrl+Shift", TerlHintCardPresenter.leaderName(
            TerlHintCardPresenter.Source.HARDWARE,
            Leader.custom(LeaderChord.parse("ctrl+shift").chord)));
        assertEquals("Super", TerlHintCardPresenter.leaderName(
            TerlHintCardPresenter.Source.SUPER, Leader.TERL_KEY));
    }

    // ------------------------------------------------------------------ when it shows

    private static final class FakeSurface implements TerlHintCardPresenter.Surface {
        String header;
        List<TerlHintCard.Section> sections;
        boolean showing;

        @Override public void show(@NonNull String leaderName, @NonNull List<TerlHintCard.Section> s) {
            header = leaderName;
            sections = s;
            showing = true;
        }

        @Override public void hide() { showing = false; }

        @Override public boolean isShowing() { return showing; }
    }

    private static final class FakeClock implements KeybindHintPresenter.Scheduler {
        final List<Runnable> pending = new ArrayList<>();

        @Override public void postDelayed(@NonNull Runnable runnable, long delayMs) {
            pending.add(runnable);
        }

        @Override public void remove(@NonNull Runnable runnable) {
            pending.remove(runnable);
        }

        void elapse() {
            List<Runnable> due = new ArrayList<>(pending);
            pending.clear();
            for (Runnable runnable : due) runnable.run();
        }
    }

    @Test
    public void theTerlKeyHeldShowsAtOnceAndLettingGoTakesItDown() {
        FakeSurface surface = new FakeSurface();
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(surface, new FakeClock(),
            terl(null), LABELS);
        presenter.setTerlKeyHeld(true);
        assertTrue(surface.showing);
        assertEquals("Terl", surface.header);
        assertEquals(TerlHintCard.Group.WINDOWS, surface.sections.get(0).group);
        presenter.setTerlKeyHeld(false);
        assertFalse(surface.showing);
    }

    @Test
    public void aChordFiredWhileItShowsClosesItForTheRestOfTheHold() {
        FakeSurface surface = new FakeSurface();
        FakeClock clock = new FakeClock();
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(surface, clock, terl(null), LABELS);
        presenter.setHardwareLeaderHeld(true);
        assertFalse("a quick chord never flashes it", surface.showing);
        clock.elapse();
        assertTrue(surface.showing);
        assertEquals("Ctrl+Alt", surface.header);
        presenter.onConsumed();
        assertFalse(surface.showing);
        presenter.setTerlKeyHeld(true);
        assertFalse("spent until every leader is let go", surface.showing);
        presenter.setTerlKeyHeld(false);
        presenter.setHardwareLeaderHeld(false);
        presenter.setHardwareLeaderHeld(true);
        clock.elapse();
        assertTrue(surface.showing);
    }

    @Test
    public void aQuickChordBeforeTheHoldNeverShowsIt() {
        FakeSurface surface = new FakeSurface();
        FakeClock clock = new FakeClock();
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(surface, clock, terl(null), LABELS);
        presenter.setHardwareLeaderHeld(true);
        presenter.onConsumed();
        clock.elapse();
        assertFalse(surface.showing);
    }

    @Test
    public void superHeldShowsItAndItsReleaseIsNoTap() {
        FakeSurface surface = new FakeSurface();
        FakeClock clock = new FakeClock();
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(surface, clock, terl(null), LABELS);
        // A quick tap: no card, so the release is a tap (the search opens).
        presenter.setSuperHeld(true);
        assertFalse(presenter.setSuperHeld(false));
        assertFalse(surface.showing);
        assertTrue("the tap's show was cancelled", clock.pending.isEmpty());
        // Held: the card, and the release is no tap.
        presenter.setSuperHeld(true);
        assertTrue(presenter.isSuperHeld());
        clock.elapse();
        assertTrue(surface.showing);
        assertEquals("Super", surface.header);
        assertTrue(presenter.setSuperHeld(false));
        assertFalse(surface.showing);
    }

    // ------------------------------------------------------------------ on screen

    @Test
    public void theCardStandsAboveTheFloorAndATapFiresTheChord() {
        Application app = ApplicationProvider.getApplicationContext();
        FrameLayout sheets = new FrameLayout(app);
        sheets.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        sheets.layout(0, 0, 1080, 2000);
        List<String> fired = new ArrayList<>();
        TerlHintCardView view = new TerlHintCardView(app, new TerlHintCardView.Host() {
            @Override public ViewGroup sheetsHost() { return sheets; }
            @Override public int floorPx(@NonNull ViewGroup host) { return 1400; }
            @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 20f; }
            @Override public int ink() { return 0xFFFFFFFF; }
            @Override public boolean isReducedMotionEnabled() { return true; }
            @Override public void fire(@NonNull String chord) { fired.add(chord); }
        });
        TerlHintCardPresenter presenter = new TerlHintCardPresenter(view, new FakeClock(),
            terl(null), LABELS);
        presenter.setTerlKeyHeld(true);
        assertTrue(view.isShowing());
        assertEquals(sheets, view.getParent());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        // Its bottom stands a gap above the floor (the bar and the keyboard below it).
        assertTrue(String.valueOf(params.bottomMargin), params.bottomMargin > 2000 - 1400);
        List<String> rows = view.rowTexts();
        assertEquals("TERL +", rows.get(0));
        assertTrue(rows.toString(), rows.contains("WINDOWS"));
        assertTrue(rows.toString(), rows.contains("↵ Terminal"));
        assertTrue(rows.toString(), rows.contains("⇧ ← ↑ ↓ → Move"));
        assertEquals("Enter, Terminal", String.valueOf(view.capDescription("↵")));
        assertEquals("Left arrow, Focus", String.valueOf(view.capDescription("←")));
        assertEquals("⇧ says nothing of its own", null, view.capDescription("⇧"));
        assertEquals("Q, Close", String.valueOf(view.capDescription("Q")));
        assertTrue(view.tapCap("Q"));
        assertEquals(java.util.Collections.singletonList("q"), fired);
        presenter.setTerlKeyHeld(false);
        assertFalse(view.isShowing());
        assertNull(view.getParent());
    }
}
