package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The preset tiles stand centred in the room they are given (the upgrade nudge's row). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PreviewTilesCentredTest {

    @Test
    public void fourTilesLeaveEqualGapsOnBothSides() {
        Context context = RuntimeEnvironment.getApplication();
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight);
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>();
        for (String name : new String[] {"Clear", "Mist", "Tint", "Solid"})
            tiles.add(new PreviewTilesRow.Tile(name, new View(context)));
        PreviewTilesRow row = PreviewTilesRow.build(context, tiles, 0, index -> {});
        for (int width : new int[] {1000, 1001, 1003, 960}) {
            row.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.root.layout(0, 0, width, row.root.getMeasuredHeight());
            int left = row.tiles.get(0).getLeft();
            int right = width - row.tiles.get(3).getRight();
            assertEquals("width " + width + ": left " + left + ", right " + right, left, right, 1);
        }
    }
}
