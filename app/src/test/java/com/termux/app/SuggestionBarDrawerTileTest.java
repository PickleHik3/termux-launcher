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
import com.termux.app.dock.DrawerTilePolicy;
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
 * The Drawer tile on the dock's row (#97, once the Taskbar's): it takes one of the row's slots, so
 * a page holds one app fewer; it stands at the start, the middle or the end with the apps in
 * their own order around it; a tap is the drawer's; and a row with the toggle off has none.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SuggestionBarDrawerTileTest {

    private Activity activity;
    private SuggestionBarView bar;
    private int taps;
    private final List<DrawerTilePolicy.Position> moves = new ArrayList<>();

    private final SuggestionBarView.DrawerTileListener listener =
        new SuggestionBarView.DrawerTileListener() {
            @Override public void onDrawerTileTapped() { taps++; }
            @Override public void onDrawerTileMoved(@NonNull DrawerTilePolicy.Position p) {
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

    @Test public void everyOtherDockHasNoDrawerTile() {
        assertNull(drawerTile());
        assertEquals(7, bar.computePinnedItemsPerPage());
    }

    @Test public void theDrawerTileTakesOneOfTheRowsSlots() {
        bar.setDrawerTile(DrawerTilePolicy.Position.START, listener);
        idle();
        assertNotNull(drawerTile());
        assertEquals("a page holds one app fewer", 6, bar.computePinnedItemsPerPage());
        assertEquals("the tile and the page's six app slots", 7, bar.getColumnCount());

        bar.setDrawerTile(null, null);
        idle();
        assertNull(drawerTile());
        assertEquals(7, bar.computePinnedItemsPerPage());
    }

    @Test public void theAppsKeepTheirOrderAroundTheTile() {
        bar.setDrawerTile(DrawerTilePolicy.Position.START, listener);
        idle();
        assertTrue("at the start the tile stands before the apps",
            centreX(drawerTile()) < centreX(terminal()));

        bar.setDrawerTile(DrawerTilePolicy.Position.END, listener);
        idle();
        assertTrue("at the end it stands after them", centreX(drawerTile()) > centreX(terminal()));
        assertTrue("in the row's last slot", centreX(drawerTile()) > bar.getWidth() * 6f / 7f);
    }

    @Test public void aTapOpensTheDrawer() {
        bar.setDrawerTile(DrawerTilePolicy.Position.MIDDLE, listener);
        idle();
        View tile = drawerTile();
        assertNotNull(tile);
        tile.performClick();
        assertEquals(1, taps);
        assertTrue("a tap is not a move", moves.isEmpty());
    }

    /**
     * On Lens beads the tile stands on a bead of its own (#97): its box is one of the row's icon
     * boxes the beads are cut from, and the centre the beads keep still mid-swipe is that box's.
     */
    @Test public void theTileIsOneOfTheBeadedBoxesAndNamesItsCentre() {
        assertTrue("no tile, no still centre", Float.isNaN(bar.drawerTileCenterX()));
        float[] boxes = new float[4 * 16];
        int without = bar.layoutIconBoxes(boxes);

        bar.setDrawerTile(DrawerTilePolicy.Position.END, listener);
        idle();
        int with = bar.layoutIconBoxes(boxes);
        assertEquals("the tile is one more box", without + 1, with);
        float tile = bar.drawerTileCenterX();
        assertTrue(!Float.isNaN(tile));
        boolean matched = false;
        for (int i = 0; i < with; i++)
            matched |= Math.abs((boxes[i * 4] + boxes[i * 4 + 2]) * 0.5f - tile) < 0.5f;
        assertTrue("the still centre is the tile's box's", matched);
        int[] row = new int[2];
        bar.getLocationOnScreen(row);
        assertEquals(centreX(drawerTile()) - row[0], tile, 1f);
    }

    private View drawerTile() {
        return findNamed(bar, activity.getString(R.string.dock_drawer_tile));
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
