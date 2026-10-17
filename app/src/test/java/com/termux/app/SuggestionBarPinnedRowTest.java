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
