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
import com.termux.app.surfaces.LegendSlider;

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
import android.app.Application;

/**
 * The editor's row kinds (editor-redesign §1, §2, §8, §9) laid out on pong's width at the default
 * text size and at Large (1.3): every control inside the 16dp gutter, every touch target 48dp,
 * no word cut, 16 sp titles, and each glyph's name on long-press. Native graphics, so text is
 * measured with real font metrics. Renders land in {@code build/reports/editor-row-kinds}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi", application = Application.class)
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
    public void theRowTitleIsNeverCutBesideWordsWithGlyphs() {
        // tl16, 411dp, 1.0x: "Label ...", "Defau...", "Clock po..." stood cut beside their groups.
        // The build is told a width twice the real one: the layout decides with what it gets.
        Object[][] rows = {
            {"Label size", new int[] {EditorGlyphs.LABEL_SMALL, EditorGlyphs.LABEL_MEDIUM,
                EditorGlyphs.LABEL_LARGE}, new String[] {"Small", "Medium", "Large"}},
            {"Default type", new int[] {EditorGlyphs.KB_DOCKED, EditorGlyphs.KB_FLOATING,
                EditorGlyphs.KB_SPLIT}, new String[] {"Docked", "Floating", "Split"}},
            {"Clock position", new int[] {EditorGlyphs.CLOCK_ALIGN_LEFT,
                EditorGlyphs.CLOCK_ALIGN_CENTER, EditorGlyphs.CLOCK_ALIGN_RIGHT},
                new String[] {"Left", "Centre", "Right"}}};
        for (float scale : new float[] {1f, 1.3f}) {
            Fixture f = new Fixture(scale);
            for (Object[] spec : rows) {
                String title = (String) spec[0];
                int[] glyphs = (int[]) spec[1];
                String[] words = (String[]) spec[2];
                java.util.List<SegmentRow.Segment> segments = new java.util.ArrayList<>();
                for (int i = 0; i < words.length; i++)
                    segments.add(SegmentRow.Segment.both(glyphs[i], words[i]));
                SegmentRow row = SegmentRow.build(f.themed, R.drawable.ic_symbol_tune, title,
                    segments, 0, f.width * 2, index -> {});
                f.layOut(row.root);
                String at = title + " at " + scale;
                android.text.Layout layout = row.title.getLayout();
                assertNotNull(at, layout);
                for (int line = 0; line < layout.getLineCount(); line++)
                    assertEquals(at + ": the title is cut", 0, layout.getEllipsisCount(line));
                assertEquals(at + ": the title reads on one line", 1, layout.getLineCount());
                assertTrue(at + ": the title stands whole", row.title.getWidth() + 1
                    >= row.title.getPaint().measureText(title));
                for (MaterialButton button : row.buttons) {
                    assertNotCut(button, at);
                    assertInsideGutter(f, button, at);
                }
                if (row.inline)
                    assertTrue(at + ": the pills start after the title",
                        row.group.getLeft() >= row.title.getRight());
                else
                    assertTrue(at + ": stacked pills stand under the title",
                        top(f, row.group) >= top(f, row.title) + row.title.getHeight() - 1);
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
                // Expressive toggle buttons (overhaul §4): the word alone, no glyph, no mark.
                assertTrue(which + ": the word alone", tile.getIcon() == null);
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
    public void sliderRowsStandOnOneLineWithTheValueInsideTheTrack() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            SliderRow row = SliderRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
                new NoStrip());
            row.setSource(1, 0xFF4285F4, "Opacity, from Home page");
            f.layOut(row.root);
            String at = "at " + scale;
            float density = f.themed.getResources().getDisplayMetrics().density;
            int previousBottom = -1;
            for (SliderRow.Item item : row.items) {
                String which = at + ", " + item.name.getText();
                assertTrue(which + ": one line", item.root.isOneLine());
                assertTrue(which + ": the track prints the value", item.slider.isInsetShown());
                assertFalse(which + ": lies along its row", item.slider.isVertical());
                assertEquals(which + ": a 48dp hit area", f.dp(48), item.slider.getHeight());
                int nameTop = top(f, item.name);
                int sliderTop = top(f, item.slider);
                assertTrue(which + ": the name and the track share the line",
                    nameTop >= sliderTop - 1 && nameTop + item.name.getHeight()
                        <= sliderTop + item.slider.getHeight() + 1);
                assertTrue(which + ": the name ends before the track",
                    left(f, item.name) + item.name.getWidth() <= left(f, item.slider) + 1);
                assertEquals(which + ": the track ends at the row's margin",
                    f.width - f.dp(16), left(f, item.slider) + item.slider.getWidth(), 1);
                for (View part : new View[] {item.slider, item.glyph, item.name})
                    assertInsideGutter(f, part, which);
                assertNotCut(item.name, which);
                assertTrue(which + ": the widest value fits inside the track",
                    SliderRow.widestReadoutPx(glassStrips().get(row.items.indexOf(item)),
                        item.slider.insetValuePaint()) + 2 * f.dp(LegendSlider.INSET_VALUE_PAD_DP)
                        <= item.slider.getWidth());
                int rowTop = top(f, item.root);
                assertTrue(which + ": under the row before it", rowTop >= previousBottom - 1);
                previousBottom = rowTop + item.root.getHeight();
                assertTrue(which + ": the row holds its track",
                    item.root.getHeight() >= item.slider.getHeight());
                System.out.println("slider row " + which + ": " + item.root.getHeight() + " px, "
                    + Math.round(item.root.getHeight() / density) + " dp, one line");
            }
            assertEquals("20%", row.items.get(0).value.getText().toString());
            row.setValue(3, 40);
            assertEquals("40 dp", row.items.get(3).value.getText().toString());
            SliderRow.Item opacity = row.items.get(1);
            assertNotNull("the source dot rides after the name",
                opacity.name.getCompoundDrawablesRelative()[2]);
            assertEquals("Opacity, from Home page",
                String.valueOf(opacity.name.getContentDescription()));
            assertTrue("the track says where its value comes from", opacity.slider.isMarked());
            assertEquals("no dot on a shared value", null,
                row.items.get(0).name.getCompoundDrawablesRelative()[2]);
        }
    }

    @Test
    public void aLongNameAtLargeTextKeepsTheTwoLineRowAndNothingIsCut() {
        Fixture f = new Fixture(1.3f);
        SliderRow.Readout percent = v -> v + "%";
        List<SliderRow.Track> tracks = Arrays.asList(
            new SliderRow.Track(R.drawable.ic_editor_opacity, "Background opacity behind the keys",
                0, 100, 1, percent),
            new SliderRow.Track(R.drawable.ic_editor_blur, "Frost", 0, 100, 1, percent));
        SliderRow row = SliderRow.build(f.themed, tracks, new int[] {64, 20}, new NoStrip());
        f.layOut(row.root);
        SliderRow.Item item = row.items.get(0);
        String which = "at 1.3, the long name";
        assertFalse(which + ": two lines", item.root.isOneLine());
        assertFalse(which + ": the value stands over the track", item.slider.isInsetShown());
        View head = item.name;
        assertTrue(which + ": the name line stands over the track",
            top(f, head) + head.getHeight() <= top(f, item.slider) + 1);
        assertEquals(which + ": a 48dp hit area", f.dp(48), item.slider.getHeight());
        assertEquals(which + ": the track spans the row inside its margins",
            f.width - 2 * f.dp(16), item.slider.getWidth(), 1);
        for (View part : new View[] {item.slider, item.glyph, item.name, item.value})
            assertInsideGutter(f, part, which);
        assertNotCut(item.value, which);
        assertEquals("64%", item.value.getText().toString());
        assertTrue(which + ": the name ends before the value",
            left(f, item.name) + item.name.getWidth() <= left(f, item.value) + 1);
        assertTrue(which + ": the row holds its name line and its track",
            item.root.getHeight() >= item.name.getHeight() + item.slider.getHeight());
        assertTrue("the decision is per row: Frost stands on one line",
            row.items.get(1).root.isOneLine());
    }

    @Test
    public void theLinkStandsBetweenTheTwoRowsItLinksAndGoesWithEither() {
        Fixture f = new Fixture(1f);
        List<Boolean> linked = new ArrayList<>();
        SliderRow row = SliderRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
            new NoStrip(), 1, "Link opacities", true, linked::add, null);
        f.layOut(row.root);
        assertNotNull(row.link);
        assertTrue(row.isLinked());
        assertTrue("under the first",
            top(f, row.link) >= top(f, row.items.get(1).root) + row.items.get(1).root.getHeight() - 1);
        assertTrue("over the second",
            top(f, row.link) + row.link.getHeight() <= top(f, row.items.get(2).root) + 1);
        assertEquals("Linked", String.valueOf(row.link.getText()));
        assertTrue("the Linked toggle is checkable", row.link.isCheckable());
        assertTrue("about half the row wide",
            Math.abs(row.link.getWidth() - row.root.getWidth() / 2) <= row.root.getWidth() / 8);
        row.link.performClick();
        assertEquals(java.util.Collections.singletonList(false), linked);
        assertEquals("Not linked", String.valueOf(row.link.getText()));
        row.setLinked(true);
        assertEquals("restated without a report", 1, linked.size());
        assertEquals("Linked", String.valueOf(row.link.getText()));
        row.setShown(2, false);
        assertEquals(View.GONE, row.link.getVisibility());
        row.setShown(2, true);
        assertEquals(View.VISIBLE, row.link.getVisibility());
    }

    @Test
    public void aDragAlongATrackTakesTheSliderAndOneAcrossItScrollsTheCard() {
        Fixture f = new Fixture(1f);
        List<String> heard = new ArrayList<>();
        List<Integer> named = new ArrayList<>();
        SliderRow row = SliderRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
            new SliderRow.Listener() {
                @Override public void onValue(int index, int value, boolean dragging) {
                    heard.add(index + "=" + value);
                }

                @Override public void onReleased(int index) {
                    heard.add("up " + index);
                }
            }, -1, "", false, null, (index, anchor) -> named.add(index));
        ScrollView scroll = new ScrollView(f.themed);
        scroll.addView(row.root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        f.layOut(scroll);
        SliderRow.Item frost = row.items.get(0);
        com.termux.app.surfaces.LegendSlider slider = frost.slider;
        float y = slider.getHeight() / 2f;
        float w = slider.getWidth();

        // Across the track: the card's scroll; nothing moves.
        long down = SystemClock.uptimeMillis();
        assertTrue(slider.dispatchTouchEvent(event(down, down, MotionEvent.ACTION_DOWN, w * 0.5f,
            y)));
        assertFalse("nothing is decided on the way down", frost.isDragging());
        slider.dispatchTouchEvent(event(down, down + 16, MotionEvent.ACTION_MOVE, w * 0.5f,
            y + f.dp(40)));
        slider.dispatchTouchEvent(event(down, down + 32, MotionEvent.ACTION_UP, w * 0.5f,
            y + f.dp(60)));
        assertFalse(frost.isDragging());
        assertTrue("a vertical swipe changes nothing: " + heard, heard.isEmpty());

        // Along the track: the slider's at once, and the value follows the finger.
        long along = SystemClock.uptimeMillis();
        slider.dispatchTouchEvent(event(along, along, MotionEvent.ACTION_DOWN, w * 0.2f, y));
        slider.dispatchTouchEvent(event(along, along + 16, MotionEvent.ACTION_MOVE, w * 0.9f,
            y + f.dp(2)));
        assertTrue("a horizontal drag takes the slider", frost.isDragging());
        slider.dispatchTouchEvent(event(along, along + 32, MotionEvent.ACTION_UP, w * 0.95f, y));
        assertFalse(frost.isDragging());
        assertTrue("the drag set Frost high: " + slider.getValue(), slider.getValue() > 85f);
        assertEquals("up 0", heard.get(heard.size() - 1));
        heard.clear();

        // A tap: the handle jumps where it lands.
        long tap = SystemClock.uptimeMillis();
        slider.dispatchTouchEvent(event(tap, tap, MotionEvent.ACTION_DOWN, w * 0.5f, y));
        slider.dispatchTouchEvent(event(tap, tap + 40, MotionEvent.ACTION_UP, w * 0.5f, y));
        assertEquals("a tap in the middle", 50f, slider.getValue(), 2f);
        assertTrue("reported: " + heard, heard.contains("up 0"));
        assertEquals("50%", frost.value.getText().toString());

        // A long press on the name is the row's menu.
        frost.name.performLongClick();
        assertEquals(java.util.Collections.singletonList(0), named);
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
        com.termux.app.testing.ReportRenders.assumeRequested();
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
            column.addView(SliderRow.build(f.themed, glassStrips(), new int[] {20, 10, 0, 24, 8},
                new NoStrip()).root);
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
    private static List<SliderRow.Track> glassStrips() {
        SliderRow.Readout percent = v -> v + "%";
        SliderRow.Readout dp = v -> v + " dp";
        return Arrays.asList(
            new SliderRow.Track(R.drawable.ic_editor_blur, "Frost", 0, 100, 1, percent),
            new SliderRow.Track(R.drawable.ic_editor_opacity, "Opacity", 0, 100, 1, percent),
            new SliderRow.Track(R.drawable.ic_editor_tint, "Tint", 0, 100, 1, percent),
            new SliderRow.Track(R.drawable.ic_editor_corners, "Corners", 0, 40, 1, dp),
            new SliderRow.Track(R.drawable.ic_editor_margin, "Margin", 0, 24, 1, dp));
    }

    private static final class NoStrip implements SliderRow.Listener {
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
