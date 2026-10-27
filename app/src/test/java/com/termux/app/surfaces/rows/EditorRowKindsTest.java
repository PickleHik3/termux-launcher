package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.termux.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The editor's row kinds (editor-redesign §1, §2, §8, §9) laid out on pong's width at the default
 * text size and at Large (1.3): every control inside the 16dp gutter, every touch target 48dp,
 * no word cut, 16 sp titles, and each glyph's name on long-press. Native graphics, so text is
 * measured with real font metrics. Renders land in {@code build/reports/editor-row-kinds}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorRowKindsTest {

    private static final float[] SCALES = {1f, 1.3f};

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    // ---------------------------------------------------------------------------- segment row

    @Test
    public void aGlyphSegmentRowStandsBesideItsTitleAndNamesEachGlyph() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            SegmentRow row = SegmentRow.build(f.themed, R.drawable.ic_symbol_tune, "Position",
                Arrays.asList(SegmentRow.Segment.glyph(EditorGlyphs.POS_TOP, "Top"),
                    SegmentRow.Segment.glyph(EditorGlyphs.POS_BOTTOM, "Bottom")),
                0, f.width, index -> {});
            f.layOut(row.root);
            String at = "at " + scale;
            assertTrue(at + ": two glyphs stand beside the title", row.inline);
            assertTitleIsSixteenSp(f, row.title);
            assertEquals("Top", String.valueOf(GlyphNames.of(row.buttons.get(0))));
            assertEquals("Bottom", String.valueOf(row.buttons.get(1).getContentDescription()));
            for (MaterialButton button : row.buttons) {
                assertTrue(at + ": a 48dp target", button.getHeight() >= f.dp(48) - 1);
                assertTrue(at + ": a 48dp target", button.getWidth() >= f.dp(48) - 1);
                assertInsideGutter(f, button, at);
            }
        }
    }

    @Test
    public void aWordSegmentRowNeverCutsAWordAndStacksWhenItMust() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            for (String title : new String[] {"Type", "Contrast of the grey text"}) {
                SegmentRow row = SegmentRow.build(f.themed, R.drawable.ic_symbol_tune, title,
                    Arrays.asList(SegmentRow.Segment.word("Docked"),
                        SegmentRow.Segment.word("Floating"), SegmentRow.Segment.word("Split")),
                    1, f.width, index -> {});
                f.layOut(row.root);
                String at = title + " at " + scale;
                for (MaterialButton button : row.buttons) {
                    assertNotCut(button, at);
                    assertInsideGutter(f, button, at);
                    assertTrue(at + ": a 48dp target", button.getHeight() >= f.dp(48) - 1);
                }
                if (!row.inline)
                    assertTrue(at + ": stacked pills stand under the title",
                        top(f, row.group) >= top(f, row.title) + row.title.getHeight() - 1);
            }
        }
        Fixture f = new Fixture(1f);
        SegmentRow narrow = SegmentRow.build(f.themed, R.drawable.ic_symbol_tune,
            "Contrast of the grey text", Arrays.asList(SegmentRow.Segment.word("Softer"),
                SegmentRow.Segment.word("Default"), SegmentRow.Segment.word("Harder")),
            1, f.dp(300), index -> {});
        assertFalse("a long title on a narrow card stacks the pills", narrow.inline);
    }

    @Test
    public void aSegmentRowReportsTouchesOnly() {
        Fixture f = new Fixture(1f);
        List<Integer> picks = new ArrayList<>();
        SegmentRow row = SegmentRow.build(f.themed, R.drawable.ic_symbol_tune, "Type",
            Arrays.asList(SegmentRow.Segment.word("Docked"), SegmentRow.Segment.word("Floating")),
            0, f.width, picks::add);
        f.layOut(row.root);
        assertEquals(0, row.selected());
        row.select(1);
        assertEquals(1, row.selected());
        assertTrue("select() is silent", picks.isEmpty());
        row.buttons.get(0).performClick();
        assertEquals(Arrays.asList(0), picks);
        assertTrue(SegmentRow.fitsInline(400, 56, 16, 100f, 16, 200));
        assertFalse(SegmentRow.fitsInline(400, 56, 16, 120f, 16, 200));
    }

    // ------------------------------------------------------------------------------ tile grid

    @Test
    public void tilesWrapIntoColumnsAndKeepEveryWordWhole() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            TileGrid grid = TileGrid.build(f.themed, indicatorTiles(),
                new boolean[] {true, true, true, false, true}, f.width, (i, on) -> {});
            f.layOut(grid.root);
            String at = "at " + scale;
            assertTrue(at + ": at most four to a line", grid.columns <= TileGrid.MAX_COLUMNS);
            assertTrue(at + ": more than one to a line", grid.columns >= 2);
            int width = grid.tiles.get(0).getWidth();
            for (MaterialButton tile : grid.tiles) {
                String which = tile.getText() + " " + at;
                assertInsideGutter(f, tile, which);
                assertNotCut(tile, which);
                Layout layout = tile.getLayout();
                assertTrue(which + ": no word is broken", layout.getLineCount()
                    <= tile.getText().toString().split(" ").length);
                Drawable[] sides = tile.getCompoundDrawablesRelative();
                assertTrue(which + ": the glyph stands on top", sides[0] == null
                    && sides[1] != null);
                int textBottom = tile.getTotalPaddingTop() + layout.getHeight();
                assertTrue(which + ": the word ends inside the tile",
                    textBottom <= tile.getHeight() - tile.getPaddingBottom() + 1);
                assertEquals(which + ": the tiles keep one grid", width, tile.getWidth(), 2);
                assertTrue(which + ": a 48dp target", tile.getHeight() >= f.dp(48) - 1);
            }
        }
    }

    @Test
    public void aTileTogglesByTouchAndSetOnIsSilent() {
        Fixture f = new Fixture(1f);
        List<String> heard = new ArrayList<>();
        TileGrid grid = TileGrid.build(f.themed, indicatorTiles(),
            new boolean[] {true, false}, f.width, (i, on) -> heard.add(i + "=" + on));
        f.layOut(grid.root);
        assertTrue(grid.isOn(0));
        assertFalse(grid.isOn(1));
        grid.setOn(1, true);
        assertTrue(heard.isEmpty());
        grid.tiles.get(0).performClick();
        assertEquals(Arrays.asList("0=false"), heard);
        assertEquals("CPU", String.valueOf(GlyphNames.of(grid.tiles.get(0))));
        assertEquals(4, TileGrid.columns(379, 80, 8, 5, 4));
        assertEquals(2, TileGrid.columns(379, 80, 8, 2, 4));
        assertEquals(1, TileGrid.columns(60, 80, 8, 5, 4));
        assertEquals(3, TileGrid.columns(379, 110, 8, 5, 4));
    }

    // ---------------------------------------------------------------------------------- strip

    @Test
    public void stripsStandGlyphOverTrackOverValueOnOneLineWhenTheyFit() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            StripRow row = StripRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
                f.width, new NoStrip());
            f.layOut(row.root);
            String at = "at " + scale;
            java.util.Set<Object> lines = new java.util.HashSet<>();
            for (StripRow.Column column : row.columns) {
                lines.add(column.root.getParent());
                assertInsideGutter(f, column.root, at);
                assertTrue(at + ": glyph over track", top(f, column.glyph) + column.glyph.getHeight()
                    <= top(f, column.slider) + 1);
                assertTrue(at + ": track over value", top(f, column.slider)
                    + column.slider.getHeight() <= top(f, column.value) + 1);
                assertNotCut(column.value, at);
                assertTrue(at + ": wide enough for its track and the name beside it",
                    column.root.getWidth() >= column.slider.minColumnWidthPx() - 1);
                assertEquals(at + ": no name at rest", "", column.slider.legendText().toString());
            }
            assertEquals(at + ": five strips on one line", 1, lines.size());
            assertEquals("20%", row.columns.get(0).value.getText().toString());
            row.setValue(3, 40);
            assertEquals("40 dp", row.columns.get(3).value.getText().toString());
        }
        org.junit.Assert.assertArrayEquals(new int[] {5, 4}, StripRow.lines(9, 7));
        org.junit.Assert.assertArrayEquals(new int[] {4, 3, 3}, StripRow.lines(10, 4));
        assertEquals(7, StripRow.capacity(379, 42, 12));
    }

    @Test
    public void aSwipeThatStartsOnAStripScrollsTheCardAndARestTakesTheStrip() {
        Fixture f = new Fixture(1f);
        List<String> heard = new ArrayList<>();
        StripRow row = StripRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
            f.width, new StripRow.Listener() {
                @Override public void onValue(int index, int value, boolean dragging) {
                    heard.add(index + "=" + value);
                }

                @Override public void onReleased(int index) {
                    heard.add("up " + index);
                }
            });
        ScrollView scroll = new ScrollView(f.themed);
        scroll.addView(row.root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        f.layOut(scroll);
        StripRow.Column frost = row.columns.get(0);
        float x = frost.slider.getWidth() / 2f;
        float handleY = frost.slider.getHeight() * 0.8f;

        // A quick swipe from the handle: the card's scroll, the value untouched.
        long down = SystemClock.uptimeMillis();
        assertTrue(frost.slider.dispatchTouchEvent(event(down, down, MotionEvent.ACTION_DOWN, x,
            handleY)));
        frost.slider.dispatchTouchEvent(event(down, down + 16, MotionEvent.ACTION_MOVE, x,
            handleY - f.dp(40)));
        frost.slider.dispatchTouchEvent(event(down, down + 32, MotionEvent.ACTION_UP, x,
            handleY - f.dp(60)));
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
        assertFalse(frost.isDragging());
        assertTrue("a swipe changes nothing: " + heard, heard.isEmpty());
        assertEquals(20f, frost.slider.getValue(), 0f);

        // A rest, then a drag: the strip's, its name beside the track while it drags.
        long next = SystemClock.uptimeMillis();
        frost.slider.dispatchTouchEvent(event(next, next, MotionEvent.ACTION_DOWN, x, handleY));
        ShadowLooper.idleMainLooper(StripTouchPolicy.HOLD_TO_CLAIM_MS + 10,
            TimeUnit.MILLISECONDS);
        assertTrue("a rest takes the strip", frost.isDragging());
        assertEquals("Frost", frost.slider.legendText().toString());
        long later = SystemClock.uptimeMillis();
        frost.slider.dispatchTouchEvent(event(next, later, MotionEvent.ACTION_MOVE, x,
            f.dp(4)));
        frost.slider.dispatchTouchEvent(event(next, later + 16, MotionEvent.ACTION_UP, x,
            f.dp(4)));
        assertFalse(frost.isDragging());
        assertEquals("", frost.slider.legendText().toString());
        assertTrue("the drag set Frost high: " + heard, frost.slider.getValue() > 80f);
        assertEquals("up 0", heard.get(heard.size() - 1));
    }

    // -------------------------------------------------------------------------------- stepper

    @Test
    public void aStepperKeepsItsButtonsInsideTheGutterAndStopsAtItsEnds() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            List<Integer> heard = new ArrayList<>();
            StepperRow row = StepperRow.build(f.themed, R.drawable.ic_symbol_apps, "App icons",
                3, 8, 1, 7, v -> String.valueOf(v), heard::add);
            f.layOut(row.root);
            String at = "at " + scale;
            assertTitleIsSixteenSp(f, row.title);
            assertNotCut(row.title, at);
            assertNotCut(row.value, at);
            for (View button : new View[] {row.minus, row.plus}) {
                assertInsideGutter(f, button, at);
                assertTrue(at + ": a 48dp target", button.getHeight() >= f.dp(48) - 1);
            }
            row.plus.performClick();
            assertEquals(Arrays.asList(8), heard);
            assertFalse("the end greys plus out", row.plus.isEnabled());
            row.plus.performClick();
            assertEquals(Arrays.asList(8), heard);
            row.setValue(3);
            assertFalse(row.minus.isEnabled());
            assertEquals(Arrays.asList(8), heard);
            assertEquals("Decrease App icons", String.valueOf(GlyphNames.of(row.minus)));
        }
        assertEquals(8, StepperRow.stepped(7, 4, 3, 8));
        assertEquals(3, StepperRow.stepped(4, -4, 3, 8));
    }

    // -------------------------------------------------------------------------- preview tiles

    @Test
    public void previewTilesKeepTheirNamesWholeAndMarkTheChoice() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            List<PreviewTilesRow.Tile> tiles = new ArrayList<>();
            for (String name : new String[] {"Off", "Default", "Motion", "Railgun", "Torpedo",
                "Pixie", "Comet"}) {
                TextView preview = new TextView(f.themed);
                preview.setText("07:48");
                tiles.add(new PreviewTilesRow.Tile(name, preview));
            }
            List<Integer> picks = new ArrayList<>();
            PreviewTilesRow row = PreviewTilesRow.build(f.themed, tiles, 1, picks::add);
            f.layOut(row.root);
            String at = "at " + scale;
            for (int i = 0; i < row.tiles.size(); i++) {
                assertNotCut(row.names.get(i), row.names.get(i).getText() + " " + at);
                assertTrue(at + ": at least 64dp", row.tiles.get(i).getWidth() >= f.dp(64) - 1);
                assertTrue(at + ": a 48dp target", row.tiles.get(i).getHeight() >= f.dp(48) - 1);
            }
            assertTrue(row.tiles.get(1).isSelected());
            row.select(3);
            assertFalse(row.tiles.get(1).isSelected());
            assertTrue(picks.isEmpty());
            row.tiles.get(5).performClick();
            assertEquals(Arrays.asList(5), picks);
            assertEquals(5, row.selected());
            assertEquals("Pixie", String.valueOf(GlyphNames.of(row.tiles.get(5))));
        }
    }

    // ------------------------------------------------------------------------------ chips row

    @Test
    public void chipsWrapInsideTheGutterWithTheirWordsWhole() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            List<Integer> opened = new ArrayList<>();
            ChipsRow row = ChipsRow.build(f.themed, Arrays.asList(
                new ChipsRow.Link(R.drawable.ic_symbol_palette, "Global"),
                new ChipsRow.Link(R.drawable.ic_symbol_tune, "Gestures"),
                new ChipsRow.Link(R.drawable.ic_symbol_grid_view, "Edit pages"),
                new ChipsRow.Link(R.drawable.ic_symbol_icon_pack, "Icon pack"),
                new ChipsRow.Link(R.drawable.ic_symbol_terminal, "Tool colours")), opened::add);
            f.layOut(row.root);
            String at = "at " + scale;
            for (Chip chip : row.chips) {
                assertInsideGutter(f, chip, chip.getText() + " " + at);
                assertTrue(chip.getText() + " " + at + ": the word fits its chip",
                    chip.getWidth() >= chip.getPaint().measureText(chip.getText().toString()));
                assertTrue(at + ": a 48dp target", chip.getHeight() >= f.dp(48) - 1);
            }
            row.chips.get(2).performClick();
            assertEquals(Arrays.asList(2), opened);
        }
    }

    // ----------------------------------------------------------------------------- action row

    @Test
    public void anActionRowReadsItsWordAndActsFromAnywhereOnIt() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            int[] taps = {0};
            ActionRow row = ActionRow.build(f.themed, R.drawable.ic_ms_swap_horiz,
                "Match Workspace styling", "Copy", v -> taps[0]++);
            f.layOut(row.root);
            String at = "at " + scale;
            assertTitleIsSixteenSp(f, row.title);
            assertInsideGutter(f, row.action, at);
            assertNotCut(row.action, at);
            row.root.performClick();
            row.action.performClick();
            assertEquals(2, taps[0]);
            row.setAction("Undo");
            assertEquals("Undo", row.action.getText().toString());
        }
    }

    // ------------------------------------------------------------------------- section header

    @Test
    public void aSectionHeaderCarriesItsCaptionAtItsEnd() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            SectionHeader header = SectionHeader.build(f.themed, "Glass",
                "widgets, status bar, dock", true);
            f.layOut(header.root);
            String at = "at " + scale;
            assertEquals(View.VISIBLE, header.caption.getVisibility());
            assertNotCut(header.title, at);
            assertInsideGutter(f, header.caption, at);
            assertTrue(at + ": the caption stands after the title",
                left(f, header.caption) >= left(f, header.title) + header.title.getWidth());
            SectionHeader bare = SectionHeader.build(f.themed, "Options", null, false);
            f.layOut(bare.root);
            assertEquals(View.GONE, bare.caption.getVisibility());
        }
    }

    // --------------------------------------------------------------------------------- glyphs

    @Test
    public void everyEditorGlyphInflatesAtTwentyFourDp() {
        Fixture f = new Fixture(1f);
        for (int glyph : EditorGlyphs.all()) {
            Drawable drawable = AppCompatResources.getDrawable(f.themed, glyph);
            String name = f.themed.getResources().getResourceEntryName(glyph);
            assertNotNull(name, drawable);
            assertEquals(name, f.dp(24), drawable.getIntrinsicWidth());
        }
        assertEquals(40, EditorGlyphs.all().length);
    }

    @Test
    public void rendersOfEveryRowKindAtDefaultAndLargeText() throws java.io.IOException {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            LinearLayout column = new LinearLayout(f.themed);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setBackgroundColor(RowKit.color(f.themed,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
            column.addView(SectionHeader.build(f.themed, "Position", null, false).root);
            column.addView(SegmentRow.build(f.themed, R.drawable.ic_symbol_tune, "Position",
                Arrays.asList(SegmentRow.Segment.glyph(EditorGlyphs.POS_TOP, "Top"),
                    SegmentRow.Segment.glyph(EditorGlyphs.POS_BOTTOM, "Bottom")),
                0, f.width, i -> {}).root);
            column.addView(SegmentRow.build(f.themed, R.drawable.ic_symbol_keyboard, "Type",
                Arrays.asList(SegmentRow.Segment.word("Docked"),
                    SegmentRow.Segment.word("Floating"), SegmentRow.Segment.word("Split")),
                0, f.width, i -> {}).root);
            column.addView(SectionHeader.build(f.themed, "Glass", "Shape", true).root);
            column.addView(StripRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
                f.width, new NoStrip()).root);
            column.addView(SectionHeader.build(f.themed, "Options", null, true).root);
            column.addView(TileGrid.build(f.themed, indicatorTiles(),
                new boolean[] {true, true, true, false, true}, f.width, (i, on) -> {}).root);
            column.addView(StepperRow.build(f.themed, R.drawable.ic_symbol_apps, "App icons", 3,
                8, 1, 7, String::valueOf, v -> {}).root);
            List<PreviewTilesRow.Tile> tiles = new ArrayList<>();
            for (String name : new String[] {"Flip", "LCD", "Minimal", "LED", "Tape", "Slab"}) {
                TextView preview = new TextView(f.themed);
                preview.setText("07:48");
                preview.setGravity(android.view.Gravity.CENTER);
                tiles.add(new PreviewTilesRow.Tile(name, preview));
            }
            column.addView(PreviewTilesRow.build(f.themed, tiles, 5, i -> {}).root);
            column.addView(ActionRow.build(f.themed, R.drawable.ic_ms_swap_horiz,
                "Match Home styling", "Copy", v -> {}).root);
            column.addView(ChipsRow.build(f.themed, Arrays.asList(
                new ChipsRow.Link(R.drawable.ic_symbol_palette, "Theme"),
                new ChipsRow.Link(R.drawable.ic_symbol_keyboard, "Layouts")), i -> {}).root);
            f.layOut(column);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                f.width, Math.max(1, column.getHeight()), android.graphics.Bitmap.Config.ARGB_8888);
            column.draw(new android.graphics.Canvas(bitmap));
            java.io.File dir = new java.io.File("build/reports/editor-row-kinds");
            assertTrue(dir.isDirectory() || dir.mkdirs());
            java.io.File png = new java.io.File(dir, "row-kinds-font-" + scale + ".png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            System.out.println("editor-row-kinds render: " + png.getAbsolutePath());
        }
    }

    // -------------------------------------------------------------------------------- helpers

    @NonNull
    private static List<TileGrid.Tile> indicatorTiles() {
        return Arrays.asList(new TileGrid.Tile(R.drawable.ic_symbol_tune, "CPU"),
            new TileGrid.Tile(R.drawable.ic_symbol_tune, "RAM"),
            new TileGrid.Tile(R.drawable.ic_symbol_weather, "Weather"),
            new TileGrid.Tile(R.drawable.ic_symbol_tune, "Notifications"),
            new TileGrid.Tile(R.drawable.ic_symbol_tune, "Media"));
    }

    @NonNull
    private static List<StripRow.Strip> glassStrips() {
        StripRow.Readout percent = v -> v + "%";
        StripRow.Readout dp = v -> v + " dp";
        return Arrays.asList(
            new StripRow.Strip(R.drawable.ic_editor_blur, "Frost", 0, 100, 1, percent),
            new StripRow.Strip(R.drawable.ic_editor_opacity, "Opacity", 0, 100, 1, percent),
            new StripRow.Strip(R.drawable.ic_editor_tint, "Tint", 0, 100, 1, percent),
            new StripRow.Strip(R.drawable.ic_editor_corners, "Corners", 0, 40, 1, dp),
            new StripRow.Strip(R.drawable.ic_editor_margin, "Margin", 0, 24, 1, dp));
    }

    private static final class NoStrip implements StripRow.Listener {
        @Override public void onValue(int index, int value, boolean dragging) {}

        @Override public void onReleased(int index) {}
    }

    @NonNull
    private static MotionEvent event(long down, long at, int action, float x, float y) {
        return MotionEvent.obtain(down, at, action, x, y, 0);
    }

    private static void assertTitleIsSixteenSp(@NonNull Fixture f, @NonNull TextView title) {
        float sp = 16f * f.themed.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("a 16 sp title", sp, title.getTextSize(), 0.5f);
    }

    private static void assertNotCut(@NonNull TextView view, @NonNull String at) {
        Layout layout = view.getLayout();
        assertNotNull(at + ": laid out", layout);
        for (int i = 0; i < layout.getLineCount(); i++)
            assertEquals(at + ": \"" + view.getText() + "\" is cut", 0, layout.getEllipsisCount(i));
    }

    private static void assertInsideGutter(@NonNull Fixture f, @NonNull View view,
                                           @NonNull String at) {
        int left = left(f, view);
        assertTrue(at + ": starts inside the gutter (" + left + ")", left >= f.dp(16) - 1);
        assertTrue(at + ": ends inside the gutter (" + (left + view.getWidth()) + " of "
            + f.width + ")", left + view.getWidth() <= f.width - f.dp(16) + 1);
    }

    private static int left(@NonNull Fixture f, @NonNull View view) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        int[] parent = new int[2];
        f.parent.getLocationInWindow(parent);
        return at[0] - parent[0];
    }

    private static int top(@NonNull Fixture f, @NonNull View view) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        int[] parent = new int[2];
        f.parent.getLocationInWindow(parent);
        return at[1] - parent[1];
    }

    /** Pong's window at a text size: a themed context and a parent to lay a row out in. */
    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final int width;
        final int height;

        Fixture(float fontScale) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            width = Math.round(411 * density);
            height = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, height));
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        void layOut(@NonNull View row) {
            parent.removeAllViews();
            parent.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, height);
        }
    }
}
