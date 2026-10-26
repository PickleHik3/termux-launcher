package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Every card fits pong at the default text size, at Large (1.3) and at 2.0, at both detents: the
 * last row scrolls clear of the navigation inset by 16dp (a partly visible control is a bug), no
 * choice's word is cut (segments turn into a list instead), every row is there without a More,
 * and every slider but the Look's is a strip column whose legend stands whole. Native graphics,
 * so text is measured with real font metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorCardSheetFitTest {

    private static final int NAV_DP = 24;

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void everyCardsLastRowClearsTheNavigationInsetAtBothDetents() {
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            for (EditorCard card : EditorCard.values())
                assertFits(card, scale);
        }
    }

    @Test
    public void noChoiceIsCutAtAnyTextSize() {
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.layOut();
                for (MaterialButton button : buttons(fixture.sheet.rows())) {
                    Layout layout = button.getLayout();
                    if (layout == null || layout.getLineCount() == 0)
                        continue;
                    assertEquals(card + " at " + scale + ": \"" + button.getText() + "\" is cut",
                        0, layout.getEllipsisCount(layout.getLineCount() - 1));
                }
            }
        }
    }

    @Test
    public void theHeaderIsSixtyFourAndTheRowsAtLeastTheirHeights() {
        Fixture fixture = new Fixture(EditorCard.DOCK, 1f);
        fixture.layOut();
        float density = fixture.themed.getResources().getDisplayMetrics().density;
        LinearLayout column = (LinearLayout) ((ViewGroup) fixture.sheet.view()).getChildAt(0);
        assertEquals(Math.round(64 * density), column.getChildAt(0).getHeight());
        View strip = fixture.sheet.rows().getChildAt(0);
        assertTrue("the glass strip is at least 72dp", strip.getHeight() >= Math.round(72 * density));
    }

    @Test
    public void aSlidersThumbAtEitherEndStaysInsideTheMargins() {
        Fixture fixture = new Fixture(EditorCard.APPEARANCE, 1f);
        fixture.layOut();
        int width = fixture.parent.getWidth();
        int checked = 0;
        for (com.google.android.material.slider.Slider slider : sliders(fixture.sheet.rows())) {
            if (slider.getWidth() <= 0)
                continue;
            int[] at = new int[2];
            slider.getLocationInWindow(at);
            int[] parentAt = new int[2];
            fixture.parent.getLocationInWindow(parentAt);
            int left = at[0] - parentAt[0];
            int trackStart = left + slider.getTrackSidePadding();
            int trackEnd = left + slider.getWidth() - slider.getTrackSidePadding();
            int radius = slider.getThumbWidth() / 2;
            assertTrue("the thumb at the end clears the 16dp margin: " + (trackEnd + radius)
                + " vs " + (width - fixture.dp(16)), trackEnd + radius <= width - fixture.dp(16) + 1);
            assertEquals("the track starts at the text column", fixture.dp(56), trackStart, 1);
            checked++;
        }
        assertTrue(checked > 0);
    }

    @Test
    public void theDesignTilesHoldTheirGlyphAndWordInsideTheirOutline() {
        for (float scale : new float[] {1f, 1.3f}) {
            Fixture fixture = new Fixture(EditorCard.DOCK, scale);
            fixture.layOut();
            int tiles = 0;
            for (MaterialButton tile : buttons(fixture.sheet.rows())) {
                if (tile.getIcon() == null || tile.getLayout() == null)
                    continue;
                tiles++;
                assertEquals("no inset under the outline", 0, tile.getInsetBottom());
                int textBottom = tile.getTotalPaddingTop() + tile.getLayout().getHeight();
                assertTrue(tile.getText() + " at " + scale + ": the word ends at " + textBottom
                        + " inside " + tile.getHeight(),
                    textBottom <= tile.getHeight() - tile.getPaddingBottom() + 1);
            }
            assertEquals(3, tiles);
        }
    }

    @Test
    public void theDesignTilesCentreTheirWordUnderTheGlyph() {
        for (float scale : new float[] {1f, 1.3f}) {
            Fixture fixture = new Fixture(EditorCard.DOCK, scale);
            fixture.layOut();
            int tiles = 0;
            for (MaterialButton tile : buttons(fixture.sheet.rows())) {
                if (tile.getIcon() == null || tile.getLayout() == null)
                    continue;
                tiles++;
                android.graphics.drawable.Drawable[] sides = tile.getCompoundDrawablesRelative();
                assertTrue("the glyph stands on top, not at the start", sides[0] == null
                    && sides[1] != null);
                assertEquals(tile.getText() + " at " + scale + ": the word is laid out across the"
                        + " whole tile", tile.getWidth() - tile.getCompoundPaddingLeft()
                        - tile.getCompoundPaddingRight(), tile.getLayout().getWidth());
                float wordCentre = tile.getCompoundPaddingLeft()
                    + (tile.getLayout().getLineLeft(0) + tile.getLayout().getLineRight(0)) / 2f;
                assertEquals(tile.getText() + " at " + scale + ": under the glyph's centre",
                    tile.getWidth() / 2f, wordCentre, 2f);
            }
            assertEquals(3, tiles);
        }
    }

    @Test
    public void anInlineSegmentRowKeepsItsIconOffTheEdgeAndNoCheckMark() {
        Fixture fixture = new Fixture(EditorCard.DOCK, 1f);
        fixture.layOut();
        com.google.android.material.button.MaterialButtonToggleGroup style = null;
        for (com.google.android.material.button.MaterialButtonToggleGroup group
                : toggleGroups(fixture.sheet.rows())) {
            if (group.getParent() instanceof LinearLayout && ((LinearLayout) group.getParent())
                    .getOrientation() == LinearLayout.HORIZONTAL) {
                style = group;
                break;
            }
        }
        assertTrue("the Dock card has a row of segments beside its label", style != null);
        ViewGroup row = (ViewGroup) style.getParent();
        View icon = row.getChildAt(0);
        int[] at = new int[2];
        icon.getLocationInWindow(at);
        int[] parentAt = new int[2];
        fixture.parent.getLocationInWindow(parentAt);
        assertTrue("the icon stands 16dp in", at[0] - parentAt[0] >= fixture.dp(16) - 1);
        for (int i = 0; i < style.getChildCount(); i++)
            assertEquals(null, ((MaterialButton) style.getChildAt(i)).getIcon());
    }

    @Test
    public void everySegmentRowStandsOnOneLineAtTheDefaultTextSize() {
        for (EditorCard card : EditorCard.values()) {
            Fixture fixture = new Fixture(card, 1f);
            fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
            fixture.layOut();
            for (com.google.android.material.button.MaterialButtonToggleGroup group
                    : toggleGroups(fixture.sheet.rows())) {
                assertEquals(card + ": a segment row stands label and control on one line",
                    LinearLayout.HORIZONTAL, ((LinearLayout) group.getParent()).getOrientation());
                for (MaterialButton button : buttons(group))
                    assertTrue("the touch target is 48dp", button.getHeight() >= fixture.dp(48) - 1);
            }
        }
    }

    @Test
    public void aSegmentRowNeverClipsAtLargeTextAndStacksOnlyWhenItMust() {
        Fixture fixture = new Fixture(EditorCard.HOME, 1.3f);
        fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
        fixture.layOut();
        for (com.google.android.material.button.MaterialButtonToggleGroup group
                : toggleGroups(fixture.sheet.rows())) {
            ViewGroup row = (ViewGroup) group.getParent();
            int[] at = new int[2];
            group.getLocationInWindow(at);
            int[] parentAt = new int[2];
            fixture.parent.getLocationInWindow(parentAt);
            assertTrue("the control ends inside the 16dp margin",
                at[0] - parentAt[0] + group.getWidth() <= fixture.parent.getWidth() - fixture.dp(16) + 1);
            assertTrue("the row holds its control", row.getHeight() >= group.getHeight());
        }
    }

    @Test
    public void everyStripStandsOnOneLineWhenItsColumnsFit() {
        for (float scale : new float[] {1f, 1.3f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
                fixture.layOut();
                List<LegendSlider> legends = legends(fixture.sheet.rows());
                java.util.Set<Object> lines = new java.util.HashSet<>();
                for (LegendSlider legend : legends)
                    lines.add(legend.getParent());
                int min = legends.get(0).minColumnWidthPx();
                int room = fixture.parent.getWidth() - 2 * fixture.dp(16);
                boolean fits = legends.size() * min <= room;
                System.out.println("strip " + card + " at " + scale + ": " + legends.size()
                    + " columns, " + lines.size() + " lines, column " + legends.get(0).getWidth()
                    + "px, min " + min + "px");
                if (fits)
                    assertEquals(card + " at " + scale + " is one line", 1, lines.size());
            }
        }
    }

    @Test
    public void rendersOfTheCardsAtDefaultAndLargeText() throws java.io.IOException {
        for (float scale : new float[] {1f, 1.3f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
                fixture.layOut();
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    fixture.parent.getWidth(), fixture.parent.getHeight(),
                    android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                canvas.drawColor(0xFF202124);
                fixture.parent.draw(canvas);
                java.io.File dir = new java.io.File("build/reports/editor-rows");
                assertTrue(dir.isDirectory() || dir.mkdirs());
                java.io.File png = new java.io.File(dir, card.name().toLowerCase(java.util.Locale.ROOT)
                    + "-font-" + scale + ".png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                }
                System.out.println("editor-rows render: " + png.getAbsolutePath());
            }
        }
    }

    @Test
    public void everyGlassStripsLegendsStandWholeInTheirColumnsAtAnyTextSize() {
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.layOut();
                List<LegendSlider> legends = legends(fixture.sheet.rows());
                assertFalse(card + " has a strip", legends.isEmpty());
                for (LegendSlider slider : legends) {
                    assertTrue(card + ": a strip column is laid out", slider.getWidth() > 0
                        && slider.getHeight() > 0);
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        slider.getWidth(), slider.getHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
                    slider.draw(new android.graphics.Canvas(bitmap));
                    String at = card + " at " + scale + ", \"" + slider.legendText() + "\"";
                    assertTrue(at + " overruns its track", slider.legendFits());
                    assertTrue(at + " leaves its column", slider.legendInsideColumn());
                    assertEquals(at + " is cut", slider.legendText().toString(),
                        slider.shownLegend().toString());
                }
            }
        }
    }

    @Test
    public void everySliderButTheLooksIsAStripColumnAndEveryRowIsThere() {
        for (EditorCard card : EditorCard.values()) {
            Fixture fixture = new Fixture(card, 1f);
            fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
            fixture.layOut();
            int strips = 0;
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true))) {
                if (spec.row.kind == EditorRows.Kind.STRIP)
                    strips++;
            }
            List<LegendSlider> legends = legends(fixture.sheet.rows());
            assertEquals(card + ": a column for every value slider", strips, legends.size());
            for (LegendSlider legend : legends)
                assertTrue(card + ": " + legend.getContentDescription() + " stands open, no More",
                    legend.isShown() && legend.getWidth() > 0);
            List<com.google.android.material.slider.Slider> sliders = sliders(fixture.sheet.rows());
            int looks = 0;
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true))) {
                if (spec.row == EditorRows.Row.LOOK)
                    looks++;
            }
            assertEquals(card + ": the Look's is the one horizontal slider", looks,
                sliders.size());
            for (TextView text : texts(fixture.sheet.rows()))
                assertTrue(card + " has no More row", !"More".contentEquals(text.getText()));
        }
    }

    @Test
    public void aStripWrapsOntoBalancedLinesAndKeepsItsColumnsInOneGrid() {
        assertArrayEquals(new int[] {5, 4}, EditorCardSheet.stripLines(9, 7));
        assertArrayEquals(new int[] {4, 3, 3}, EditorCardSheet.stripLines(10, 4));
        assertArrayEquals(new int[] {3}, EditorCardSheet.stripLines(3, 7));
        assertArrayEquals(new int[0], EditorCardSheet.stripLines(0, 7));
        assertEquals(7, EditorCardSheet.stripCapacity(379, 42, 12));
        assertEquals(1, EditorCardSheet.stripCapacity(30, 42, 12));
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
                fixture.layOut();
                List<LegendSlider> legends = legends(fixture.sheet.rows());
                int width = legends.get(0).getWidth();
                for (LegendSlider legend : legends) {
                    String at = card + " at " + scale + ", " + legend.getContentDescription();
                    assertTrue(at + " is narrower than its legend needs: " + legend.getWidth()
                        + " < " + legend.minColumnWidthPx(),
                        legend.getWidth() >= legend.minColumnWidthPx() - 1);
                    if (legend.getParent() == legends.get(0).getParent()
                        || isStripOf(legend, legends.get(0)))
                        assertEquals(at + " leaves the grid", width, legend.getWidth(), 2);
                }
            }
        }
    }

    /** Whether two columns stand in the same strip (lines under one root). */
    private static boolean isStripOf(@NonNull View a, @NonNull View b) {
        return a.getParent() != null && b.getParent() != null
            && ((View) a.getParent()).getParent() == ((View) b.getParent()).getParent();
    }

    @NonNull
    private static List<TextView> texts(@NonNull View view) {
        List<TextView> out = new ArrayList<>();
        if (view instanceof TextView) {
            out.add((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(texts(group.getChildAt(i)));
        }
        return out;
    }

    @Test
    public void theSetHereDotRidesOnTheRotatedLegend() {
        Fixture fixture = new Fixture(EditorCard.KEYBOARD, 1f);
        fixture.layOut();
        int marked = 0;
        for (LegendSlider slider : legends(fixture.sheet.rows())) {
            boolean opacity = slider.getContentDescription().toString().equals(
                fixture.themed.getString(R.string.appearance_row_opacity));
            assertEquals(String.valueOf(slider.getContentDescription()), opacity,
                slider.isMarked());
            if (slider.isMarked())
                marked++;
        }
        assertEquals(1, marked);
    }

    @NonNull
    private static List<LegendSlider> legends(@NonNull View view) {
        List<LegendSlider> out = new ArrayList<>();
        if (view instanceof LegendSlider) {
            out.add((LegendSlider) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(legends(group.getChildAt(i)));
        }
        return out;
    }

    @NonNull
    private static List<com.google.android.material.slider.Slider> sliders(@NonNull View view) {
        List<com.google.android.material.slider.Slider> out = new ArrayList<>();
        if (view instanceof com.google.android.material.slider.Slider) {
            out.add((com.google.android.material.slider.Slider) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(sliders(group.getChildAt(i)));
        }
        return out;
    }

    @NonNull
    private static List<com.google.android.material.button.MaterialButtonToggleGroup> toggleGroups(
            @NonNull View view) {
        List<com.google.android.material.button.MaterialButtonToggleGroup> out = new ArrayList<>();
        if (view instanceof com.google.android.material.button.MaterialButtonToggleGroup) {
            out.add((com.google.android.material.button.MaterialButtonToggleGroup) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(toggleGroups(group.getChildAt(i)));
        }
        return out;
    }

    // ------------------------------------------------------------------------------- the drive

    private static void assertFits(@NonNull EditorCard card, float scale) {
        Fixture fixture = new Fixture(card, scale);
        for (int detent : new int[] {EditorCardSheet.DETENT_HALF, EditorCardSheet.DETENT_FULL}) {
            fixture.sheet.setDetent(detent, false);
            fixture.layOut();
            ScrollView scroll = fixture.sheet.scroll();
            View rows = fixture.sheet.rows();
            scroll.scrollTo(0, Math.max(0, rows.getHeight() - scroll.getHeight()));
            View last = lastRow(fixture.sheet.rows());
            int[] at = new int[2];
            last.getLocationInWindow(at);
            int[] parentAt = new int[2];
            fixture.parent.getLocationInWindow(parentAt);
            int lastBottom = at[1] - parentAt[1] + last.getHeight();
            int limit = fixture.parent.getHeight() - fixture.navPx - fixture.dp(16);
            assertTrue(card + " at " + scale + ", detent " + detent + ": last row ends at "
                + lastBottom + ", past " + limit, lastBottom <= limit + 1);
        }
    }

    /** The card's last row: tier 3's last, every row shown. */
    @NonNull
    private static View lastRow(@NonNull LinearLayout rows) {
        return rows.getChildAt(rows.getChildCount() - 1);
    }

    @NonNull
    private static List<MaterialButton> buttons(@NonNull View view) {
        List<MaterialButton> out = new ArrayList<>();
        if (view instanceof MaterialButton) {
            out.add((MaterialButton) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(buttons(group.getChildAt(i)));
        }
        return out;
    }

    /** A sheet on pong's window with a card up, its values standing in from a fake model. */
    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final EditorCardSheet sheet;
        final int navPx;
        private final int mWidth;
        private final int mHeight;

        Fixture(@NonNull EditorCard card, float fontScale) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            mWidth = Math.round(411 * density);
            mHeight = Math.round(914 * density);
            navPx = Math.round(NAV_DP * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(mWidth, mHeight));
            sheet = new EditorCardSheet(themed, new FakeModel(themed), new EditorCardSheet.Listener() {
                @Override public void onClose() {}
                @Override public void onReset() {}
                @Override public void onSheetMoved(int detent, float topPx) {}
            });
            int full = Math.round(mHeight * 0.55f);
            sheet.attach(parent);
            sheet.setGeometry(full, full, navPx);
            sheet.show(card, EditorRows.rows(card, new EditorRows.Offers(true, true)), "Reset",
                false);
            layOut();
            EditorFramePose.Window window = new EditorFramePose.Window(mHeight,
                Math.round(41 * density), navPx, density, fontScale, Math.round(41 * density),
                mHeight, 0, Math.round(48 * density), Math.round(8 * density));
            int fullPx = EditorFramePose.fullSheetPx(window);
            int half = EditorFramePose.halfSheetPx(window, sheet.minHalfHeightPx(mWidth), fullPx);
            sheet.setGeometry(half, fullPx, navPx);
            layOut();
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        void layOut() {
            parent.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, mWidth, mHeight);
        }
    }

    /** Values a card would show on a phone in its first days: mid-range, choices in place. */
    private static final class FakeModel implements EditorCardSheet.Model {
        private final Context mContext;

        FakeModel(@NonNull Context context) {
            mContext = context;
        }

        @Override public int value(@NonNull Row row) {
            return row.kind == EditorRows.Kind.STRIP ? 50 : 0;
        }

        @Override public int from(@NonNull Row row) {
            return 0;
        }

        @Override public int to(@NonNull Row row) {
            return row == Row.LOOK ? AppearanceLooks.CUSTOM_STOP : 100;
        }

        @Override public int step(@NonNull Row row) {
            return 1;
        }

        @NonNull @Override public String readout(@NonNull Row row, int value) {
            return mContext.getString(R.string.appearance_readout_percent, value);
        }

        @Override public boolean ownValue(@NonNull Row row) {
            return row == Row.OPACITY;
        }

        @Override public boolean enabled(@NonNull Row row) {
            return true;
        }

        @Nullable @Override public CharSequence summary(@NonNull Row row) {
            return row.kind == EditorRows.Kind.MENU ? "Default" : null;
        }

        @NonNull @Override public String[] choices(@NonNull Row row) {
            switch (row) {
                case STYLE: return strings(R.string.appearance_editor_style_docked,
                    R.string.appearance_editor_style_floating);
                case DESIGN: return strings(R.string.layout_editor_dock_style_glass_tray,
                    R.string.layout_editor_dock_style_lens_beads,
                    R.string.layout_editor_dock_style_wall_strip);
                case CONTRAST: return strings(R.string.appearance_contrast_softer,
                    R.string.appearance_contrast_default, R.string.appearance_contrast_harder);
                case LABEL_SIZE: return strings(R.string.appearance_size_small,
                    R.string.appearance_size_medium, R.string.appearance_size_large);
                case FOLDERS: return strings(R.string.appearance_folders_compact,
                    R.string.appearance_folders_default, R.string.appearance_folders_roomy);
                case NUMERALS: return strings(R.string.appearance_numerals_sans,
                    R.string.appearance_numerals_mono);
                case FORM: return strings(R.string.appearance_form_docked,
                    R.string.appearance_form_floating, R.string.appearance_form_split);
                case THEME: return strings(R.string.appearance_theme_system,
                    R.string.appearance_theme_light, R.string.appearance_theme_dark);
                case LOOK: return strings(R.string.termux_surface_preset_minimal,
                    R.string.termux_surface_preset_frost, R.string.termux_surface_preset_stock,
                    R.string.termux_surface_preset_solid, R.string.termux_surface_preset_custom);
                default: return new String[0];
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            return row == Row.DESIGN ? new int[] {R.drawable.ic_symbol_dock_glass_tray,
                R.drawable.ic_symbol_dock_lens_beads, R.drawable.ic_symbol_dock_wall_strip}
                : null;
        }

        @Nullable @Override public View expanderContent(@NonNull Row row) {
            TextView content = new TextView(mContext);
            content.setText("Packs");
            return content;
        }

        @Override public void onValue(@NonNull Row row, int value, boolean dragging) {}

        @Override public void onReleased(@NonNull Row row) {}

        @Override public void onAction(@NonNull Row row, @NonNull View anchor) {}

        @NonNull
        private String[] strings(int... res) {
            String[] out = new String[res.length];
            for (int i = 0; i < res.length; i++)
                out[i] = mContext.getString(res[i]);
            return out;
        }
    }
}
