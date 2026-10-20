package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.dock.TaskbarHomeTilePolicy;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Taskbar's Home tile on the dock's row (spec "Taskbar"): it takes one of the row's slots, so
 * a page holds one app fewer; it stands at the start, the middle or the end with the apps in
 * their own order around it; a tap is the drawer's; and every other dock has none.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SuggestionBarHomeTileTest {

    private Activity activity;
    private SuggestionBarView bar;
    private int taps;
    private final List<TaskbarHomeTilePolicy.Position> moves = new ArrayList<>();

    private final SuggestionBarView.HomeTileListener listener =
        new SuggestionBarView.HomeTileListener() {
            @Override public void onHomeTileTapped() { taps++; }
            @Override public void onHomeTileMoved(@NonNull TaskbarHomeTilePolicy.Position p) {
                moves.add(p);
            }
        };

    @Before public void setUp() {
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, 140));
        bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        idle();
        bar.setConfigRepository(storeHoldingTerminal());
        bar.reload();
        idle();
    }

    @Test public void everyOtherDockHasNoHomeTile() {
        assertNull(homeTile());
        assertEquals(7, bar.computePinnedItemsPerPage());
    }

    @Test public void theHomeTileTakesOneOfTheRowsSlots() {
        bar.setHomeTile(TaskbarHomeTilePolicy.Position.START, listener);
        idle();
        assertNotNull(homeTile());
        assertEquals("a page holds one app fewer", 6, bar.computePinnedItemsPerPage());
        assertEquals("the tile and the page's six app slots", 7, bar.getColumnCount());

        bar.setHomeTile(null, null);
        idle();
        assertNull(homeTile());
        assertEquals(7, bar.computePinnedItemsPerPage());
    }

    @Test public void theAppsKeepTheirOrderAroundTheTile() {
        bar.setHomeTile(TaskbarHomeTilePolicy.Position.START, listener);
        idle();
        assertTrue("at the start the tile stands before the apps",
            centreX(homeTile()) < centreX(terminal()));

        bar.setHomeTile(TaskbarHomeTilePolicy.Position.END, listener);
        idle();
        assertTrue("at the end it stands after them", centreX(homeTile()) > centreX(terminal()));
        assertTrue("in the row's last slot", centreX(homeTile()) > bar.getWidth() * 6f / 7f);
    }

    @Test public void aTapOpensTheDrawer() {
        bar.setHomeTile(TaskbarHomeTilePolicy.Position.MIDDLE, listener);
        idle();
        View tile = homeTile();
        assertNotNull(tile);
        tile.performClick();
        assertEquals(1, taps);
        assertTrue("a tap is not a move", moves.isEmpty());
    }

    private View homeTile() {
        return findNamed(bar, activity.getString(R.string.dock_taskbar_home_tile));
    }

    private View terminal() {
        return findNamed(bar, activity.getString(R.string.launcher_entry_terminal));
    }

    private static float centreX(View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return at[0] + view.getWidth() / 2f;
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
        store.savePinnedItems(Collections.<PinnedItem>singletonList(
            new PinnedAppItem(LauncherEntries.terminal())));
        return store;
    }

    private static void idle() {
        for (int i = 0; i < 4; i++) Shadows.shadowOf(Looper.getMainLooper()).idle();
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
