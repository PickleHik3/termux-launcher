package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.RectF;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.LayoutCanvasView.Block;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The hidden-element tiles in the sheet (layout editor v2, DECISIONS item 3): one tile per hidden
 * element, in the canvas's order, each with the restore arrow; a tap restores that element.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class HiddenTilesTest {

    private static PlaceLayout layout(RowPlacement apps, RowPlacement keys) {
        return new PlaceLayout(Edge.TOP, apps, true, Edge.BOTTOM, keys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    private ContextThemeWrapper themed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        return new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
    }

    private LayoutCanvasView canvas(ContextThemeWrapper context, PlaceLayout layout) {
        FrameLayout parent = new FrameLayout(context);
        LayoutCanvasView view = new LayoutCanvasView(context);
        parent.addView(view, new FrameLayout.LayoutParams(400, 800));
        parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 400, 800);
        view.setLegendVisible(false);
        view.setFillsView(true);
        view.setExternalTrayRect(new RectF(300f, 820f, 364f, 884f));
        view.setLayout(layout, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        return view;
    }

    @Test
    public void oneTilePerHiddenElementInOrderAndNoneWhenNothingIsHidden() {
        ContextThemeWrapper context = themed();
        LayoutCanvasView view = canvas(context, layout(RowPlacement.BOTTOM, RowPlacement.BOTTOM));
        ChipGroup group = new ChipGroup(context);
        HiddenTiles tiles = new HiddenTiles(view, group, block -> { });
        tiles.update();
        assertTrue(tiles.isEmpty());
        assertEquals(0, group.getChildCount());

        view.setLayout(layout(RowPlacement.HIDDEN, RowPlacement.HIDDEN), PlaceOrientation.PORTRAIT,
            PaneWallPage.TERMINAL);
        tiles.update();
        assertFalse(tiles.isEmpty());
        assertEquals(Arrays.asList(Block.APPS_ROW, Block.EXTRA_KEYS), tiles.shown());
        assertEquals(2, group.getChildCount());
    }

    @Test
    public void aTileCarriesTheRestoreArrowAndSaysWhatItRestores() {
        ContextThemeWrapper context = themed();
        LayoutCanvasView view = canvas(context, layout(RowPlacement.HIDDEN, RowPlacement.BOTTOM));
        ChipGroup group = new ChipGroup(context);
        HiddenTiles tiles = new HiddenTiles(view, group, block -> { });
        tiles.update();
        Chip tile = tiles.tileOf(Block.APPS_ROW);
        assertNotNull(tile);
        assertNotNull("the restore arrow", tile.getChipIcon());
        assertTrue(tile.getContentDescription().toString()
            .contains(view.chipName(Block.APPS_ROW)));
        assertFalse(tile.isCheckable());
    }

    @Test
    public void tappingATileRestoresThatElement() {
        ContextThemeWrapper context = themed();
        LayoutCanvasView view = canvas(context, layout(RowPlacement.HIDDEN, RowPlacement.HIDDEN));
        ChipGroup group = new ChipGroup(context);
        List<Block> restored = new ArrayList<>();
        HiddenTiles tiles = new HiddenTiles(view, group, restored::add);
        tiles.update();
        Chip tile = tiles.tileOf(Block.EXTRA_KEYS);
        assertNotNull(tile);
        tile.performClick();
        assertEquals(Collections.singletonList(Block.EXTRA_KEYS), restored);
        assertEquals("the tile's element maps to the plan's restore",
            LayoutEditorPlan.TrayItem.EXTRA_KEYS, LayoutEditorController.trayItemOf(Block.EXTRA_KEYS));
    }

    /** Key radius shows in Layout mode with the keyboard selected, and only then (item 6). */
    @Test
    public void keyRadiusShowsOnlyWithTheKeyboardSelected() {
        assertTrue(LayoutEditorController.showsKeyRadius(Block.KEYBOARD));
        for (Block block : Block.values()) {
            if (block != Block.KEYBOARD)
                assertFalse(block.name(), LayoutEditorController.showsKeyRadius(block));
        }
        assertFalse(LayoutEditorController.showsKeyRadius(null));
    }

    /** The move menu is exactly the canvas's destinations, in its order (item 5). */
    @Test
    public void theMoveMenuIsTheCanvasesDestinations() {
        ContextThemeWrapper context = themed();
        LayoutCanvasView view = canvas(context, layout(RowPlacement.BOTTOM, RowPlacement.BOTTOM));
        for (Block block : Block.values()) {
            assertEquals(block.name(), view.moveDestinations(block),
                LayoutEditorController.moveMenuItems(view, block));
        }
    }
}
