package com.termux.app.terminal.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.app.terminal.TerminalKeyBindingResolver.Hint;
import com.termux.app.terminal.inappkeyboard.TerminalModifiers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hint surfaces' choreography, driven through fakes: which surface a prefix raises, what a
 * consumed bind does to it, and what it takes to get it back. Robolectric only because the strip
 * and the legend are real views — every decision under test is taken against the fake
 * {@link KeybindHintPresenter.Surface} and the fake scheduler.
 */
@RunWith(RobolectricTestRunner.class)
public class KeybindHintPresenterTest {

    /** A scheduler with a hand crank instead of a looper. */
    private static final class FakeScheduler implements KeybindHintPresenter.Scheduler {
        private final Map<Runnable, Long> mPending = new LinkedHashMap<>();

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            mPending.put(runnable, delayMs);
        }

        @Override
        public void remove(Runnable runnable) {
            mPending.remove(runnable);
        }

        boolean hasPending() {
            return !mPending.isEmpty();
        }

        Long delayOfOnlyPending() {
            assertEquals(1, mPending.size());
            return mPending.values().iterator().next();
        }

        /** Fires everything due within {@code ms}, oldest first. */
        void advance(long ms) {
            List<Runnable> due = new ArrayList<>();
            for (Map.Entry<Runnable, Long> entry : mPending.entrySet())
                if (entry.getValue() <= ms) due.add(entry.getKey());
            for (Runnable runnable : due) {
                mPending.remove(runnable);
                runnable.run();
            }
        }
    }

    /** An A-Z row that claims its slot without a window behind it. */
    private static final class FakeAzRow extends View {
        boolean shown = true;

        FakeAzRow(Context context) {
            super(context);
        }

        @Override
        public boolean isShown() {
            return shown;
        }
    }

    private static final class FakeSurface implements KeybindHintPresenter.Surface {
        final Context context = ApplicationProvider.getApplicationContext();
        HorizontalScrollView dockRow;
        FakeAzRow azRow;
        boolean reducedMotion = true;
        boolean splitPanes = true;
        boolean showKeyHints = true;
        boolean cardShowing;
        View cardContent;
        Runnable cardOutsideTap;
        int cardShows;
        int cardDismissals;
        Map<String, Integer> highlights;
        int highlightWrites;

        @Override
        public Context context() {
            return context;
        }

        @Override
        public View findView(int viewId) {
            if (viewId == R.id.keybind_hint_dock_row) return dockRow;
            if (viewId == R.id.apps_bar_az_row) return azRow;
            return null;
        }

        @Override
        public int accessoryGlassBaseColor() {
            return 0xFF14171A;
        }

        @Override
        public boolean isReducedMotionEnabled() {
            return reducedMotion;
        }

        @Override
        public boolean isSplitPanesEnabled() {
            return splitPanes;
        }

        @Override
        public boolean isShowKeyHintsEnabled() {
            return showKeyHints;
        }

        @Override
        public boolean isCardShowing() {
            return cardShowing;
        }

        @Override
        public void showCard(View content, Runnable onOutsideTap) {
            cardShowing = true;
            cardContent = content;
            cardOutsideTap = onOutsideTap;
            cardShows++;
        }

        @Override
        public void dismissCard(boolean animated) {
            cardShowing = false;
            cardContent = null;
            cardOutsideTap = null;
            cardDismissals++;
        }

        @Override
        public void setKeyboardHintHighlights(Map<String, Integer> litTokens) {
            highlights = litTokens;
            highlightWrites++;
        }
    }

    /** Stand-in for the resolver: a fixed table per prefix. */
    private static final class FakeHints implements KeybindHintPresenter.Hints {
        final Map<String, Map<String, Hint>> tables = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        FakeHints bind(String prefix, String... tokenToolPairs) {
            Map<String, Hint> hints = new LinkedHashMap<>();
            for (int i = 0; i < tokenToolPairs.length; i += 2)
                hints.put(tokenToolPairs[i], new Hint(tokenToolPairs[i + 1], null));
            tables.put(prefix, hints);
            return this;
        }

        @Override
        public Map<String, Hint> hintsForPrefix(String prefix) {
            asked.add(prefix);
            Map<String, Hint> hints = tables.get(prefix);
            return hints != null ? hints : Collections.emptyMap();
        }
    }

    private FakeSurface mSurface;
    private FakeScheduler mScheduler;
    private FakeHints mHints;
    private KeybindHintPresenter mPresenter;

    @Before
    public void setUp() {
        mSurface = new FakeSurface();
        mSurface.dockRow = new HorizontalScrollView(mSurface.context);
        mSurface.dockRow.setVisibility(View.GONE);
        mSurface.azRow = new FakeAzRow(mSurface.context);
        // A laid-out row: canUseDockRow asks for a slot with height, not just visibility.
        mSurface.azRow.layout(0, 0, 320, 24);
        mScheduler = new FakeScheduler();
        mHints = new FakeHints()
            .bind("ctrl+alt+", "v", "pane.split_vertical", "c", "window.new",
                "x", "window.close", "left", "window.previous", "right", "window.next")
            .bind("ctrl+alt+shift+", "c", "session.new", "p", "view.palette")
            .bind("ctrl+", "f", "pane.focus_next");
        mPresenter = new KeybindHintPresenter(mSurface, mScheduler, mHints,
            (toolName, bindingLabel) -> bindingLabel != null ? bindingLabel : toolName);
    }

    private boolean dockRowUp() {
        return mSurface.dockRow.getVisibility() == View.VISIBLE;
    }

    private void hold() {
        mPresenter.setHardwarePrefix("ctrl+alt+", false);
    }

    private void release() {
        mPresenter.setHardwarePrefix(null, false);
    }

    // ------------------------------------------------------------------ where the strip lands

    @Test
    public void aHeldPrefixPutsTheStripInTheAzRowsSlotAndLightsTheCaps() {
        hold();

        assertTrue(dockRowUp());
        assertEquals(1, mSurface.dockRow.getChildCount());
        assertEquals(View.INVISIBLE, mSurface.azRow.getVisibility());
        assertEquals(0, mSurface.cardShows);
        assertTrue(mPresenter.isVisible());
        // Every bound cap of the held table lights, curated strip or not.
        assertNotNull(mSurface.highlights);
        assertEquals(5, mSurface.highlights.size());
    }

    @Test
    public void withoutTheAzRowsSlotTheStripFallsBackToTheCard() {
        mSurface.azRow = null;

        hold();

        assertFalse(dockRowUp());
        assertEquals(1, mSurface.cardShows);
        assertTrue(mSurface.cardContent instanceof LinearLayout);
        // The strip tracks the hold alone; only the sticky table watches for an outside tap.
        assertNull(mSurface.cardOutsideTap);
        assertTrue(mPresenter.isVisible());
    }

    @Test
    public void aSlotThatIsOffScreenIsNotTheSlot() {
        mSurface.azRow.shown = false;

        hold();

        assertFalse(dockRowUp());
        assertEquals(1, mSurface.cardShows);
    }

    @Test
    public void nothingBoundUnderThePrefixRaisesNothing() {
        mPresenter.setHardwarePrefix("ctrl+space>", false);

        assertFalse(dockRowUp());
        assertEquals(0, mSurface.cardShows);
        assertFalse(mPresenter.isVisible());
        assertNull(mSurface.highlights);
    }

    @Test
    public void withCustomPanesOffTheHintsStayAway() {
        mSurface.splitPanes = false;

        hold();

        assertFalse(dockRowUp());
        assertEquals(0, mSurface.cardShows);
        assertTrue(mHints.asked.isEmpty());
    }

    @Test
    public void withHintsOffOnlyTheQuestionCapGlows() {
        mSurface.showKeyHints = false;

        hold();

        assertFalse(dockRowUp());
        assertEquals(0, mSurface.cardShows);
        assertEquals(Collections.singleton("?"), mSurface.highlights.keySet());
    }

    @Test
    public void repeatedCallbacksForTheSameLatchDoNotRebuild() {
        hold();
        int writes = mSurface.highlightWrites;
        View strip = mSurface.dockRow.getChildAt(0);

        mPresenter.setHardwarePrefix("ctrl+alt+", false);   // identical state: ignored outright
        mPresenter.onInAppModifiersChanged(TerminalModifiers.NONE);   // hardware still outranks

        assertEquals(writes, mSurface.highlightWrites);
        assertEquals(strip, mSurface.dockRow.getChildAt(0));
    }

    @Test
    public void shiftJoiningMidHoldSwapsInTheShiftLayer() {
        hold();
        mPresenter.setHardwarePrefix("ctrl+alt+", true);

        assertTrue(dockRowUp());
        assertEquals(Collections.singletonList("ctrl+alt+"), mHints.asked.subList(0, 1));
        assertEquals("ctrl+alt+shift+", mHints.asked.get(1));
        // The Shift table's two binds are what lights now.
        assertEquals(2, mSurface.highlights.size());
    }

    // ------------------------------------------------------------------ the spend

    @Test
    public void aConsumedBindTakesTheHintsDownAtOnceAndKeepsThemDown() {
        hold();
        assertTrue(dockRowUp());

        mPresenter.onConsumed();

        assertFalse(dockRowUp());
        assertNull(mSurface.highlights);
        assertFalse(mPresenter.isVisible());
        // No lingering hide is left armed: the surface is already gone.
        assertFalse(mScheduler.hasPending());
        assertEquals(View.VISIBLE, mSurface.azRow.getVisibility());

        // Still holding the same prefix: the answer to "what can I press now" is nothing.
        mPresenter.setHardwarePrefix("ctrl+alt+", true);
        assertFalse(dockRowUp());
        assertEquals(0, mSurface.cardShows);
        assertNull(mSurface.highlights);
    }

    @Test
    public void theSpendIsForgivenWhenThePrefixIsTakenUpAfresh() {
        hold();
        mPresenter.onConsumed();
        release();
        assertFalse(dockRowUp());

        hold();

        assertTrue(dockRowUp());
        assertEquals(5, mSurface.highlights.size());
    }

    @Test
    public void aLeaderChordThatRecordsItsSpendAfterTheReleaseStillArmsTheNextPrefix() {
        // The order a leader chord actually produces: release, then the spend, then the new hold.
        mPresenter.setHardwarePrefix("ctrl+alt+", false);
        release();
        mPresenter.onConsumed();

        hold();

        assertTrue(dockRowUp());
    }

    @Test
    public void aConsumedBindLeavesWithinTheExitWindowRatherThanVanishing() {
        mSurface.reducedMotion = false;
        hold();
        assertTrue(dockRowUp());

        mPresenter.onConsumed();

        // The letters are back at once; the strip fades over them instead of blinking out.
        assertEquals(View.VISIBLE, mSurface.azRow.getVisibility());
        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(KeybindHintPresenter.CONSUMED_EXIT_MS + 32));
        assertEquals(View.GONE, mSurface.dockRow.getVisibility());
        assertEquals(0, mSurface.dockRow.getChildCount());
    }

    /** A plain hide is not a spend: only a bind, or a touch on the terminal, spends the prefix. */
    @Test
    public void aHideWhileThePrefixIsStillHeldRedrawsOnTheNextRefresh() {
        hold();

        mPresenter.hideNow(true);
        mPresenter.setHardwarePrefix("ctrl+alt+", true);

        // hideNow alone is not a spend: a still-held prefix redraws.
        assertTrue(dockRowUp());
    }

    // ------------------------------------------------------------------ release and linger

    @Test
    public void releasingThePrefixLingersBeforeTheHintsGo() {
        hold();

        release();

        // Still readable: releasing the prefix is also how a stroke is typed.
        assertTrue(dockRowUp());
        assertEquals(Long.valueOf(KeybindHintPresenter.LINGER_MS),
            mScheduler.delayOfOnlyPending());

        mScheduler.advance(KeybindHintPresenter.LINGER_MS);

        assertFalse(dockRowUp());
        assertNull(mSurface.highlights);
    }

    @Test
    public void takingThePrefixBackUpWithinTheLingerCancelsTheHide() {
        hold();
        release();
        assertTrue(mScheduler.hasPending());

        hold();

        assertFalse(mScheduler.hasPending());
        assertTrue(dockRowUp());
    }

    @Test
    public void aHardwareHoldOutranksTheInAppKeyboardsReleaseCallback() {
        hold();

        // The in-app keyboard reports "no modifiers" on every key it releases.
        mPresenter.onInAppModifiersChanged(TerminalModifiers.NONE);

        assertTrue(dockRowUp());
        assertFalse(mScheduler.hasPending());
    }

    @Test
    public void theInAppLatchRaisesTheSameSurfaceAndItsReleaseLingers() {
        mPresenter.onInAppModifiersChanged(ctrlAlt());

        assertTrue(dockRowUp());

        mPresenter.onInAppModifiersChanged(TerminalModifiers.NONE);

        assertEquals(Long.valueOf(KeybindHintPresenter.LINGER_MS),
            mScheduler.delayOfOnlyPending());
    }

    // ------------------------------------------------------------------ the ? table

    @Test
    public void questionPromotesTheStripToTheStickyFullTable() {
        hold();

        mPresenter.toggleFullPopup();

        // The table is a card, and it evicts the strip from the dock row.
        assertEquals(1, mSurface.cardShows);
        assertFalse(dockRowUp());
        assertNotNull(mSurface.cardOutsideTap);
        // The full table also lists the plain Ctrl strokes alongside the prefixed ones.
        assertTrue(mHints.asked.contains("ctrl+"));
    }

    @Test
    public void theFullTableSurvivesTheReleaseThatTheOneShotLatchImplies() {
        hold();
        mPresenter.toggleFullPopup();

        release();

        assertTrue(mSurface.cardShowing);
        assertFalse(mScheduler.hasPending());
    }

    @Test
    public void anOutsideTapRetiresTheFullTable() {
        hold();
        mPresenter.toggleFullPopup();

        mSurface.cardOutsideTap.run();

        assertFalse(mSurface.cardShowing);
        assertNull(mSurface.highlights);
    }

    @Test
    public void questionAgainDropsBackToTheStrip() {
        hold();
        mPresenter.toggleFullPopup();

        mPresenter.toggleFullPopup();

        assertTrue(dockRowUp());
        assertFalse(mSurface.cardShowing);
    }

    @Test
    public void questionUnderNoPrefixDoesNothing() {
        mPresenter.toggleFullPopup();

        assertFalse(mPresenter.isVisible());
        assertEquals(0, mSurface.cardShows);
    }

    @Test
    public void askingForTheTableForgivesABindThatAlreadyRan() {
        hold();
        mPresenter.onConsumed();

        mPresenter.toggleFullPopup();

        assertEquals(1, mSurface.cardShows);
    }

    // ------------------------------------------------------------------ the readout that shares the slot

    @Test
    public void anExtraKeyReadoutTakesTheSlotAndGivesItBack() {
        mPresenter.showExtraKeyPressReadout("Ctrl+C");

        assertTrue(dockRowUp());
        assertEquals(Long.valueOf(KeybindHintPresenter.EXTRA_KEY_READOUT_HOLD_MS),
            mScheduler.delayOfOnlyPending());

        mScheduler.advance(KeybindHintPresenter.EXTRA_KEY_READOUT_HOLD_MS);

        assertFalse(dockRowUp());
    }

    @Test
    public void theLeadersStripOutranksTheReadoutAndKillsItsPendingHide() {
        mPresenter.showExtraKeyPressReadout("Ctrl+C");

        hold();

        assertTrue(dockRowUp());
        // The readout's hide must die with it or it would take the strip down mid-latch.
        assertFalse(mScheduler.hasPending());
    }

    @Test
    public void theReadoutStaysQuietWhileTheStripAnswersAQuestion() {
        hold();
        View strip = mSurface.dockRow.getChildAt(0);

        mPresenter.showExtraKeyPressReadout("Ctrl+C");

        assertEquals(strip, mSurface.dockRow.getChildAt(0));
        assertFalse(mScheduler.hasPending());
    }

    private static TerminalModifiers ctrlAlt() {
        return TerminalModifiers.from(juloo.keyboard2.Pointers.Modifiers.EMPTY
            .with_extra_mod(juloo.keyboard2.KeyValue.getKeyByName("ctrl"))
            .with_extra_mod(juloo.keyboard2.KeyValue.getKeyByName("alt")));
    }
}
