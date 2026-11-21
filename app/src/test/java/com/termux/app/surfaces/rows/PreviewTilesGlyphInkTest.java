package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** A tile's glyph wears the same ink as its name, selected or not (the Dock type tiles). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PreviewTilesGlyphInkTest {

    private static int tint(PreviewTilesRow row, int index) {
        ImageView glyph = (ImageView) ((ViewGroup) row.tiles.get(index).getChildAt(0)).getChildAt(0);
        return glyph.getImageTintList().getColorForState(
            row.tiles.get(index).getDrawableState(), 0);
    }

    private static int ink(PreviewTilesRow row, int index) {
        return row.names.get(index).getTextColors().getColorForState(
            row.tiles.get(index).getDrawableState(), 0);
    }

    @Test
    public void glyphTintEqualsNameInkSelectedAndNot() {
        Context context = RuntimeEnvironment.getApplication();
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight);
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>();
        for (String name : new String[] {"Tray", "Beads", "Strip"}) {
            ImageView glyph = new ImageView(context);
            glyph.setImageResource(android.R.drawable.ic_menu_add);
            tiles.add(new PreviewTilesRow.Tile(name, glyph));
        }
        PreviewTilesRow row = PreviewTilesRow.build(context, tiles, 1, i -> {});
        for (int i = 0; i < 3; i++)
            assertEquals("tile " + i, ink(row, i), tint(row, i));
        assertNotEquals(ink(row, 0), ink(row, 1));
        row.select(2);
        for (int i = 0; i < 3; i++)
            assertEquals("after select, tile " + i, ink(row, i), tint(row, i));
    }
}
