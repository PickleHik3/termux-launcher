package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The Home card's Options grid as pong shows it (420 dpi): one-line and two-line words in one
 * row make tiles of one height and one top, and the off tile's outline closes inside the grid.
 * Native graphics, so the words wrap on real font metrics. Renders land in
 * {@code build/reports/tile-grid}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileGridLayoutTest {

    private static final float[] SCALES = {1f, 1.3f, 2f};
    private static final int[] WIDTHS_DP = {411, 360};
    /** The tile that reads off, so it is the one drawn as an outline. */
    private static final int OFF = 1;

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void everyTileHasOneHeightAndEachLineOneTop() {
        for (float scale : SCALES) {
            for (int widthDp : WIDTHS_DP) {
                Fixture f = new Fixture(scale, widthDp);
                TileGrid grid = f.homeOptions();
                String at = "at " + scale + " / " + widthDp + "dp";
                boolean mixed = false;
                int lines = -1;
                for (MaterialButton tile : grid.tiles) {
                    assertNotNull(at, tile.getLayout());
                    int count = tile.getLayout().getLineCount();
                    mixed |= lines >= 0 && lines != count;
                    lines = count;
                }
                if (widthDp == 411 && scale <= 1.3f)
                    assertTrue(at + ": the grid mixes one- and two-line words", mixed);
                int height = grid.tiles.get(0).getHeight();
                for (int i = 0; i < grid.tiles.size(); i++) {
                    MaterialButton tile = grid.tiles.get(i);
                    String which = tile.getText() + " " + at;
                    assertEquals(which + ": one height", height, tile.getHeight());
                    MaterialButton first = grid.tiles.get(i - i % grid.columns);
                    assertEquals(which + ": one top in its line", first.getTop(), tile.getTop());
                    assertEquals(which + ": one width", grid.tiles.get(0).getWidth(),
                        tile.getWidth());
                    int textBottom = tile.getTotalPaddingTop() + tile.getLayout().getHeight();
                    assertTrue(which + ": the word ends inside the tile",
                        textBottom <= tile.getHeight() - tile.getPaddingBottom() + 1);
                }
            }
        }
    }

    @Test
    public void theOutlineStaysInsideTheGrid() {
        for (float scale : SCALES) {
            for (int widthDp : WIDTHS_DP) {
                Fixture f = new Fixture(scale, widthDp);
                TileGrid grid = f.homeOptions();
                String at = "at " + scale + " / " + widthDp + "dp";
                ViewGroup root = grid.root;
                for (MaterialButton tile : grid.tiles) {
                    String which = tile.getText() + " " + at;
                    assertTrue(which + ": inside the grid's padding",
                        tile.getLeft() >= root.getPaddingLeft()
                            && tile.getTop() >= root.getPaddingTop()
                            && tile.getRight() <= root.getWidth() - root.getPaddingRight()
                            && tile.getBottom() <= root.getHeight() - root.getPaddingBottom());
                    Drawable background = tile.getBackground();
                    assertNotNull(which, background);
                    Rect bounds = background.getBounds();
                    assertTrue(which + ": the outline is drawn inside the tile " + bounds,
                        bounds.left >= 0 && bounds.top >= 0
                            && bounds.right <= tile.getWidth()
                            && bounds.bottom <= tile.getHeight());
                }
                // The off tile's outline closes: its bottom edge is drawn, inside the grid.
                MaterialButton off = grid.tiles.get(OFF);
                assertTrue(at + ": the off tile reads off", !off.isChecked());
                Bitmap bitmap = f.render(root, Fixture.GROUND);
                int x = off.getLeft() + off.getWidth() / 2;
                boolean bottomEdge = false;
                for (int y = off.getBottom() - 1; y >= off.getBottom() - f.dp(3); y--)
                    bottomEdge |= distinct(bitmap.getPixel(x, y), Fixture.GROUND);
                assertTrue(at + ": the outline's bottom edge is drawn", bottomEdge);
                boolean topEdge = false;
                for (int y = off.getTop(); y <= off.getTop() + f.dp(3); y++)
                    topEdge |= distinct(bitmap.getPixel(x, y), Fixture.GROUND);
                assertTrue(at + ": the outline's top edge is drawn", topEdge);
            }
        }
    }

    @Test
    public void rendersAtDefaultAndLargeText() throws IOException {
        renderAll("light");
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-night-420dpi")
    public void rendersInTheDarkAtDefaultAndLargeText() throws IOException {
        renderAll("dark");
    }

    private static void renderAll(@NonNull String theme) throws IOException {
        for (float scale : new float[] {1f, 1.3f}) {
            Fixture f = new Fixture(scale, 411);
            TileGrid grid = f.homeOptions();
            Bitmap bitmap = f.render(grid.root, RowKit.color(f.themed,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
            File dir = new File("build/reports/tile-grid");
            assertTrue(dir.isDirectory() || dir.mkdirs());
            File png = new File(dir, "tiles-" + theme + "-font-" + scale + ".png");
            try (FileOutputStream out = new FileOutputStream(png)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            System.out.println("tile-grid render: " + png.getAbsolutePath());
        }
    }

    private static boolean distinct(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b))
            + Math.abs(Color.blue(a) - Color.blue(b)) > 24;
    }

    /** Pong's density at a text size and a window width, with the Home Options tiles. */
    private static final class Fixture {
        /** A ground no theme's outline or fill matches, so a drawn edge always shows on it. */
        static final int GROUND = Color.rgb(0x00, 0xC0, 0x00);

        final Context themed;
        final FrameLayout parent;
        final int width;
        final int height;

        Fixture(float fontScale, int widthDp) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            width = Math.round(widthDp * density);
            height = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, height));
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        @NonNull
        TileGrid homeOptions() {
            List<TileGrid.Tile> tiles = Arrays.asList(
                new TileGrid.Tile(R.drawable.ic_ms_grid_view, "Glass widgets"),
                new TileGrid.Tile(R.drawable.ic_ms_wrap_text, "Two-line labels"),
                new TileGrid.Tile(R.drawable.ic_ms_blur_on, "Label shadow"),
                new TileGrid.Tile(R.drawable.ic_ms_more_horiz, "Page dots"),
                new TileGrid.Tile(R.drawable.ic_ms_wallpaper, "Wallpaper scroll"));
            boolean[] on = {true, false, true, true, true};
            TileGrid grid = TileGrid.build(themed, tiles, on, width, (i, checked) -> {});
            parent.removeAllViews();
            parent.addView(grid.root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, height);
            return grid;
        }

        /** The grid drawn on {@code ground}. */
        @NonNull
        Bitmap render(@NonNull View root, int ground) {
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, root.getWidth()),
                Math.max(1, root.getHeight()), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ground);
            root.draw(canvas);
            return bitmap;
        }
    }
}
