package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The dock's pinned row draws what the store holds. The "pin your apps" hint is for an empty
 * store, never for a row that missed a write (#77): the Terminal pin once stayed off the row
 * while the store still held it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SuggestionBarPinnedRowTest {

    private Activity activity;
    private FrameLayout host;
    private SuggestionBarView bar;

    @Before public void setUp() {
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, 140));
        bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        idle();
    }

    @Test public void aRowRenderedBeforeTheStoreArrivedRendersItsPinsWhenItDoes() {
        bar.reload();
        idle();
        assertTrue("no store yet: the row invites a pin", showsEmptyHint());

        bar.setConfigRepository(storeHoldingTerminal());
        idle();

        assertFalse("the store holds the Terminal pin", showsEmptyHint());
        assertEquals(1, renderedPins());
    }

    @Test public void anEmptiedCopyNeverHidesThePinsTheStoreHolds() {
        bar.setConfigRepository(storeHoldingTerminal());
        idle();
        ReflectionHelpers.setField(bar, "pinnedItems", new ArrayList<PinnedItem>());

        bar.reload();
        idle();

        assertFalse(showsEmptyHint());
        assertEquals(1, renderedPins());
    }

    @Test public void aPinWrittenWhileTheRowWasDetachedShowsWhenItReturns() {
        LauncherConfigRepository store = new LauncherConfigRepository(new Store());
        bar.setConfigRepository(store);
        bar.reload();
        idle();
        assertTrue(showsEmptyHint());

        host.removeView(bar);
        store.savePinnedItems(Collections.singletonList(new PinnedAppItem(LauncherEntries.terminal())));
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        idle();

        assertFalse(showsEmptyHint());
        assertEquals(1, renderedPins());
    }

    @Test public void theFingerprintFollowsWhatTheRowDraws() {
        List<PinnedItem> terminal = Collections.singletonList(new PinnedAppItem(LauncherEntries.terminal()));
        assertEquals(SuggestionBarView.pinsFingerprint(terminal),
            SuggestionBarView.pinsFingerprint(new ArrayList<>(terminal)));
        assertFalse(SuggestionBarView.pinsFingerprint(terminal)
            .equals(SuggestionBarView.pinsFingerprint(new ArrayList<>())));
    }

    @Test public void aLongPressHeldStillOnThePinnedTerminalEntryKeepsItPinned() {
        LauncherConfigRepository store = storeHoldingTerminal();
        bar.setConfigRepository(store);
        bar.reload();
        idle();
        assertEquals(1, renderedPins());

        View target = findNamed(bar, activity.getString(R.string.launcher_entry_terminal));
        int[] at = new int[2];
        target.getLocationOnScreen(at);
        float x = at[0] + target.getWidth() / 2f;
        float y = at[1] + target.getHeight() / 2f;
        long down = android.os.SystemClock.uptimeMillis();
        int[] barAt = new int[2];
        bar.getLocationOnScreen(barAt);
        dispatch(android.view.MotionEvent.ACTION_DOWN, down, down, x, y, barAt);
        for (int i = 1; i <= 16; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            long now = android.os.SystemClock.uptimeMillis();
            dispatch(android.view.MotionEvent.ACTION_MOVE, down, now, x, y, barAt);
        }
        dispatch(android.view.MotionEvent.ACTION_UP, down, android.os.SystemClock.uptimeMillis(), x, y, barAt);
        idle();

        assertEquals("a press without a drag never unpins", 1, store.loadPinnedItems().size());
    }

    @Test public void aStillPressNeverRunsTheMenuRowUnderIt() {
        int slop = 21;
        assertFalse("held still, even with a row under the finger",
            SuggestionBarView.menuReleaseCommits(true, 0f, slop));
        assertFalse("a tremor inside the arm distance",
            SuggestionBarView.menuReleaseCommits(true, slop * 0.5f, slop));
        assertFalse("travel without a row",
            SuggestionBarView.menuReleaseCommits(false, slop * 4f, slop));
        assertTrue("slid onto a row",
            SuggestionBarView.menuReleaseCommits(true, slop * 4f, slop));
    }

    @Test public void unpinningFromAStaleRowKeepsThePinsTheRowMissed() throws Exception {
        PinnedAppItem terminal = new PinnedAppItem(LauncherEntries.terminal());
        PinnedAppItem alpha = new PinnedAppItem(new AppRef("com.example.alpha", "com.example.alpha.Main"));
        PinnedAppItem beta = new PinnedAppItem(new AppRef("com.example.beta", "com.example.beta.Main"));
        LauncherConfigRepository store = new LauncherConfigRepository(new Store());
        store.savePinnedItems(new ArrayList<PinnedItem>(java.util.Arrays.asList(terminal, alpha, beta)));
        // No idle: a catalogue load would prune the two made-up apps before the unpin runs.
        bar.setConfigRepository(store);
        // The row's copy fell behind: it still holds only the Terminal entry.
        ReflectionHelpers.setField(bar, "pinnedItems",
            new ArrayList<PinnedItem>(Collections.singletonList(terminal)));

        java.lang.reflect.Method removePinnedAt =
            SuggestionBarView.class.getDeclaredMethod("removePinnedAt", int.class);
        removePinnedAt.setAccessible(true);
        removePinnedAt.invoke(bar, 0);

        List<PinnedItem> left = store.loadPinnedItems();
        assertEquals(2, left.size());
        assertEquals(alpha.appRef.stableId(), ((PinnedAppItem) left.get(0)).appRef.stableId());
        assertEquals(beta.appRef.stableId(), ((PinnedAppItem) left.get(1)).appRef.stableId());
    }

    @Test public void aPinIsFoundByWhatItIsNotWhereItSat() {
        PinnedAppItem terminal = new PinnedAppItem(LauncherEntries.terminal());
        PinnedAppItem alpha = new PinnedAppItem(new AppRef("com.example.alpha", "com.example.alpha.Main"));
        List<PinnedItem> items = new ArrayList<PinnedItem>(java.util.Arrays.asList(alpha, terminal));
        assertEquals(1, SuggestionBarView.indexOfSamePin(items,
            new PinnedAppItem(LauncherEntries.terminal())));
        assertEquals(-1, SuggestionBarView.indexOfSamePin(items,
            new PinnedAppItem(LauncherEntries.display())));
    }

    private void dispatch(int action, long down, long time, float rawX, float rawY, int[] barAt) {
        android.view.MotionEvent event = android.view.MotionEvent.obtain(down, time, action, rawX, rawY, 0);
        event.offsetLocation(-barAt[0], -barAt[1]);
        bar.dispatchTouchEvent(event);
        event.recycle();
    }

    private static View findNamed(View view, String name) {
        if (name.contentEquals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findNamed(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static LauncherConfigRepository storeHoldingTerminal() {
        LauncherConfigRepository store = new LauncherConfigRepository(new Store());
        store.savePinnedItems(Collections.singletonList(new PinnedAppItem(LauncherEntries.terminal())));
        return store;
    }

    private static void idle() {
        for (int i = 0; i < 4; i++) Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private boolean showsEmptyHint() {
        String hint = activity.getString(R.string.termux_app_launcher_empty_pinned_hint);
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof TextView && hint.contentEquals(((TextView) child).getText())) return true;
        }
        return false;
    }

    /** Views on the row named for the Terminal entry, the way TalkBack reads its slot. */
    private int renderedPins() {
        return countNamed(bar, activity.getString(R.string.launcher_entry_terminal));
    }

    private static int countNamed(View view, String name) {
        int count = name.contentEquals(String.valueOf(view.getContentDescription())) ? 1 : 0;
        if (count == 0 && view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countNamed(group.getChildAt(i), name);
        }
        return count;
    }

    private static final class Store implements LauncherConfigRepository.PreferencesStore {
        String raw = "{\"schemaVersion\":5,\"items\":[],\"folders\":[]}";
        @Override public String getPinnedItemsV2() { return raw; }
        @Override public int getPinnedItemsSchemaVersion() { return 5; }
        @Override public boolean commitPinnedItems(String value, int version) {
            raw = value;
            return true;
        }
        @Override public String getLegacyDefaultButtons() { return ""; }
    }
}
