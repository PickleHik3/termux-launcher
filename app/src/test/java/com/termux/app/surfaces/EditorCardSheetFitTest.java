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
    /** Whether the fake model offers a Where chip, so the sheet shows its chip row. */
    private static boolean sChips;
    /** The row the fake model draws as a 24 dp heading, or null for none. */
    private static EditorRows.Row sShortRow;

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
    public void theLeastHalfHeightCountsTheChipRowWhenItIsShown() {
        for (float scale : new float[] {1f, 1.3f}) {
            sChips = false;
            Fixture plain = new Fixture(EditorCard.DOCK, scale);
            plain.layOut();
            int without = plain.sheet.minHalfHeightPx(plain.parent.getWidth());
            int tierOneWithout = plain.sheet.tierOneHeightPx(plain.parent.getWidth());
            sChips = true;
            try {
                Fixture chips = new Fixture(EditorCard.DOCK, scale);
                chips.layOut();
                int with = chips.sheet.minHalfHeightPx(chips.parent.getWidth());
                int grown = with - without;
                assertTrue("the chip row (56dp) is counted at " + scale + ", grew " + grown,
                    grown >= chips.dp(56));
                assertTrue("tier 1 counts it too", chips.sheet.tierOneHeightPx(
                    chips.parent.getWidth()) - tierOneWithout >= chips.dp(56));
            } finally {
                sChips = false;
            }
        }
    }

    @Test
    public void theLeastHalfHeightCountsAHeadingWithTheFirstControlUnderIt() {
        boolean anyControlFirst = false;
        for (float scale : new float[] {1f, 1.3f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.layOut();
                int width = fixture.parent.getWidth();
                int target = fixture.dp(48);
                int rowsPx = 0;
                int counted = 0;
                int spec = android.view.View.MeasureSpec.makeMeasureSpec(width,
                    android.view.View.MeasureSpec.EXACTLY);
                int free = android.view.View.MeasureSpec.makeMeasureSpec(0,
                    android.view.View.MeasureSpec.UNSPECIFIED);
                // The rows as the sections stand them: a section's title row counts with the
                // rows under it, and is not itself the control the walk stops at.
                for (View child : fixture.sheet.flatRowsForTest()) {
                    if (child.getVisibility() == View.GONE)
                        continue;
                    child.measure(spec, free);
                    rowsPx += child.getMeasuredHeight();
                    boolean title = fixture.sheet.isSectionTitleForTest(child);
                    if (!title)
                        counted++;
                    if (!title && child.getMeasuredHeight() >= target)
                        break;
                }
                if (counted == 1)
                    anyControlFirst = true;
                int chrome = fixture.dp(72) + fixture.navPx + fixture.dp(16);
                int got = fixture.sheet.minHalfHeightPx(width);
                assertTrue(card + " at " + scale + ": header + rows down to the first control, "
                    + (chrome + rowsPx) + " vs " + got, Math.abs(chrome + rowsPx - got) <= 2);
            }
        }
        assertTrue("some card opens on a whole control", anyControlFirst);
    }

    @Test
    public void aShortHeadingFirstIsCountedWithTheControlUnderIt() {
        for (float scale : new float[] {1f, 1.3f}) {
            EditorCard card = EditorCard.HOME;
            // The first drawn row (a section heading is the section's title, not a row).
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true))) {
                if (!EditorCardSheet.isSectionHeading(spec.row)) {
                    sShortRow = spec.row;
                    break;
                }
            }
            try {
                Fixture fixture = new Fixture(card, scale);
                fixture.layOut();
                int width = fixture.parent.getWidth();
                int spec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
                int free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                List<View> flat = fixture.sheet.flatRowsForTest();
                View title = flat.get(0);
                assertTrue("the first section's title row",
                    fixture.sheet.isSectionTitleForTest(title));
                View heading = flat.get(1);
                View control = flat.get(2);
                title.measure(spec, free);
                heading.measure(spec, free);
                control.measure(spec, free);
                assertTrue("a heading under 48dp", heading.getMeasuredHeight() < fixture.dp(48));
                assertTrue("a control of 48dp or more",
                    control.getMeasuredHeight() >= fixture.dp(48));
                int expected = fixture.dp(72) + fixture.navPx + fixture.dp(16)
                    + title.getMeasuredHeight() + heading.getMeasuredHeight()
                    + control.getMeasuredHeight();
                int got = fixture.sheet.minHalfHeightPx(width);
                assertTrue("heading + control + chrome at " + scale + ": " + expected + " vs " + got,
                    Math.abs(expected - got) <= 2);
            } finally {
                sShortRow = null;
            }
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
    public void sliderRowsStandOnOneLineAtDefaultTextAndNoValueIsCut() {
        for (float scale : new float[] {1f, 1.3f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture fixture = new Fixture(card, scale);
                fixture.layOut();
                for (LegendSlider slider : legends(fixture.sheet.rows())) {
                    if (!(slider.getParent() instanceof com.termux.app.surfaces.rows.SliderRow.Line)
                            || slider.getWidth() <= 0)
                        continue;
                    com.termux.app.surfaces.rows.SliderRow.Line line =
                        (com.termux.app.surfaces.rows.SliderRow.Line) slider.getParent();
                    TextView name = (TextView) line.getChildAt(1);
                    TextView value = (TextView) line.getChildAt(2);
                    String which = card + " at " + scale + ", " + name.getText();
                    if (scale == 1f && card == EditorCard.APPEARANCE)
                        assertTrue(which + " stands on one line", line.isOneLine());
                    assertEquals(which + ": the track prints the value on one line alone",
                        line.isOneLine(), slider.isInsetShown());
                    assertEquals(which + ": the name is whole", 0, ellipsized(name));
                    if (line.isOneLine()) {
                        float widest = slider.insetValuePaint().measureText(
                            String.valueOf(value.getText()));
                        assertTrue(which + ": the value fits inside the track",
                            widest + 2 * fixture.dp(LegendSlider.INSET_VALUE_PAD_DP)
                                <= slider.getWidth());
                    } else {
                        assertEquals(which + ": the value is whole", 0, ellipsized(value));
                    }
                }
            }
        }
    }

    private static int ellipsized(@NonNull TextView view) {
        Layout layout = view.getLayout();
        if (layout == null || layout.getLineCount() == 0)
            return 0;
        return layout.getEllipsisCount(layout.getLineCount() - 1);
    }

    @Test
    public void theHeaderIsSeventyTwoAndTheRowsAtLeastTheirHeights() {
        Fixture fixture = new Fixture(EditorCard.DOCK, 1f);
        fixture.layOut();
        float density = fixture.themed.getResources().getDisplayMetrics().density;
        LinearLayout column = (LinearLayout) ((ViewGroup) fixture.sheet.view()).getChildAt(0);
        // A 24dp grab zone over the 48dp row of icon, title and buttons.
        assertEquals(Math.round(72 * density), column.getChildAt(0).getHeight());
        // The Dock's strip follows its Style and Bars rows.
        View strip = legends(fixture.sheet.rows()).get(0);
        while (strip.getParent() != fixture.sheet.rows())
            strip = (View) strip.getParent();
        assertTrue("the glass strip is at least 72dp", strip.getHeight() >= Math.round(72 * density));
    }

    @Test
    public void aSlidersThumbAtEitherEndStaysInsideTheMargins() {
        int checked = 0;
        boolean anyLook = false;
        for (EditorCard card : EditorCard.values()) {
            // The Look slider stands only on a card the sheet still draws itself; the redesigned
            // cards show the Look as tiles (EditorCardBodyTest).
            if (CardBody.redesigned(card))
                continue;
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true)))
                anyLook |= spec.row == EditorRows.Row.LOOK;
            Fixture fixture = new Fixture(card, 1f);
            fixture.layOut();
            int width = fixture.parent.getWidth();
            for (com.google.android.material.slider.Slider slider : sliders(fixture.sheet.rows())) {
                if (slider.getWidth() <= 0)
                    continue;
                int[] at = new int[2];
                slider.getLocationInWindow(at);
                int[] parentAt = new int[2];
                fixture.parent.getLocationInWindow(parentAt);
                int[] box = containerSpan(fixture, slider);
                int left = at[0] - parentAt[0];
                int trackStart = left + slider.getTrackSidePadding();
                int trackEnd = left + slider.getWidth() - slider.getTrackSidePadding();
                int radius = slider.getThumbWidth() / 2;
                assertTrue("the thumb at the end clears the section's 16dp gutter: "
                    + (trackEnd + radius) + " vs " + (box[1] - fixture.dp(16)),
                    trackEnd + radius <= box[1] - fixture.dp(16) + 1);
                assertEquals("the track starts at the text column", box[0] + fixture.dp(56),
                    trackStart, 1);
                checked++;
            }
            // The Presets were this card's slider; they are picture tiles now (overhaul §4),
            // every one inside the 16dp margins, none clipped.
            int[] parentAt = new int[2];
            fixture.parent.getLocationInWindow(parentAt);
            for (ViewGroup grid : presetGrids(fixture.sheet.rows())) {
                int[] box = containerSpan(fixture, grid);
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View tile = grid.getChildAt(i);
                    int[] at = new int[2];
                    tile.getLocationInWindow(at);
                    int left = at[0] - parentAt[0];
                    assertTrue(card + ": a Presets tile inside the section's gutter " + left,
                        left >= box[0] + fixture.dp(16) - 1
                            && left + tile.getWidth() <= box[1] - fixture.dp(16) + 1);
                    assertTrue(card + ": a Presets tile is laid out", tile.getWidth() > 0);
                    // All five on one line at 411dp.
                    assertEquals(card + ": the Presets stand five across",
                        grid.getChildAt(0).getTop(), tile.getTop());
                    checked++;
                }
            }
        }
        assertTrue(!anyLook || checked > 0);
    }

    /**
     * The left and right edges (in the parent's frame) of the section container holding
     * {@code view} (overhaul §3), or the parent's own where none does.
     */
    @NonNull
    private static int[] containerSpan(@NonNull Fixture fixture, @NonNull View view) {
        int[] parentAt = new int[2];
        fixture.parent.getLocationInWindow(parentAt);
        for (EditorCardSheet.Section section : fixture.sheet.sections()) {
            for (View at = view; at != null;
                    at = at.getParent() instanceof View ? (View) at.getParent() : null) {
                if (at == section.root) {
                    int[] box = new int[2];
                    section.root.getLocationInWindow(box);
                    int left = box[0] - parentAt[0];
                    return new int[] {left, left + section.root.getWidth()};
                }
            }
        }
        return new int[] {0, fixture.parent.getWidth()};
    }

    /** The Presets rows' picture grids (PreviewTilesRow's grid). */
    @NonNull
    private static List<ViewGroup> presetGrids(@NonNull View view) {
        List<ViewGroup> out = new ArrayList<>();
        if (!(view instanceof ViewGroup))
            return out;
        ViewGroup group = (ViewGroup) view;
        if (group.getClass().getName().endsWith("PreviewTilesRow$Grid")) {
            out.add(group);
            return out;
        }
        for (int i = 0; i < group.getChildCount(); i++)
            out.addAll(presetGrids(group.getChildAt(i)));
        return out;
    }

    @Test
    public void theDockOptionsAreToggleButtonsHoldingTheirWordWhole() {
        for (float scale : new float[] {1f, 1.3f}) {
            Fixture fixture = new Fixture(EditorCard.DOCK, scale);
            fixture.layOut();
            int tiles = 0;
            for (MaterialButton tile : dockOptions(fixture.sheet.rows())) {
                tiles++;
                String which = tile.getText() + " at " + scale;
                // Expressive toggle buttons (overhaul §4): the word alone, no check mark.
                assertTrue(which + ": no glyph, no check mark", tile.getIcon() == null);
                assertTrue(which + ": a 48dp target", tile.getHeight() >= fixture.dp(48) - 1);
                assertTrue(which + ": laid out", tile.getLayout() != null);
                int textBottom = tile.getTotalPaddingTop() + tile.getLayout().getHeight();
                assertTrue(which + ": the word ends at " + textBottom + " inside "
                        + tile.getHeight(),
                    textBottom <= tile.getHeight() - tile.getPaddingBottom() + 1);
                android.text.Layout layout = tile.getLayout();
                assertTrue(which + ": no word is cut",
                    layout.getEllipsisCount(layout.getLineCount() - 1) == 0);
            }
            assertEquals("four Show/hide tiles, New float terminal among them (Search field and Drawer icon are the Global card's, Monochrome is a switch row)", 4, tiles);
        }
    }

    @Test
    public void theDockOptionsWearThePrimaryWhenOnAndTheTonalWhenOff() {
        Fixture fixture = new Fixture(EditorCard.DOCK, 1f);
        fixture.layOut();
        int tiles = 0;
        for (MaterialButton tile : dockOptions(fixture.sheet.rows())) {
            tiles++;
            int[] state = tile.getDrawableState();
            int fill = tile.getBackgroundTintList().getColorForState(state, 0);
            int ink = tile.getTextColors().getColorForState(state, 0);
            int primary = com.google.android.material.color.MaterialColors.getColor(tile,
                androidx.appcompat.R.attr.colorPrimary);
            int onPrimary = com.google.android.material.color.MaterialColors.getColor(tile,
                com.google.android.material.R.attr.colorOnPrimary);
            int tonal = com.google.android.material.color.MaterialColors.getColor(tile,
                com.google.android.material.R.attr.colorSecondaryContainer);
            int onTonal = com.google.android.material.color.MaterialColors.getColor(tile,
                com.google.android.material.R.attr.colorOnSecondaryContainer);
            String which = tile.getText() + (tile.isChecked() ? " (on)" : " (off)");
            assertEquals(which + ": its container", tile.isChecked() ? primary : tonal, fill);
            assertEquals(which + ": its word", tile.isChecked() ? onPrimary : onTonal, ink);
            assertEquals(which + ": no outline", 0, tile.getStrokeWidth());
        }
        assertEquals("four Show/hide tiles, New float terminal among them (Search field and Drawer icon are the Global card's, Monochrome is a switch row)", 4, tiles);
    }

    @Test
    public void aSheetConnectedGroupShowsEveryButtonWhole() {
        // Emulator W1a: the Status bar's Rim (None / Thin / Bright beside its title) was cut
        // flat at the bottom. Every connected button keeps its 48dp target, inside its row.
        for (EditorCard card : new EditorCard[] {EditorCard.STATUS, EditorCard.DOCK,
                EditorCard.TERMINAL}) {
            Fixture fixture = new Fixture(card, 1f);
            fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
            fixture.layOut();
            for (com.google.android.material.button.MaterialButtonToggleGroup group
                    : toggleGroups(fixture.sheet.rows())) {
                if (!group.isShown())
                    continue;
                View row = (View) group.getParent();
                for (int i = 0; i < group.getChildCount(); i++) {
                    View button = group.getChildAt(i);
                    String which = card + " " + ((MaterialButton) button).getText()
                        + " " + group.getContentDescription();
                    assertTrue(which + ": a 48dp target, " + button.getHeight(),
                        button.getHeight() >= fixture.dp(48) - 1);
                    assertTrue(which + ": the group holds it whole",
                        group.getHeight() >= button.getBottom());
                    assertTrue(which + ": the row holds the group whole",
                        row.getHeight() >= group.getBottom());
                }
            }
        }
    }

    /** The Dock's Show/hide toggles: the buttons of its TileGrid. */
    @NonNull
    private static List<MaterialButton> dockOptions(@NonNull View rows) {
        List<MaterialButton> out = new ArrayList<>();
        for (MaterialButton button : buttons(rows)) {
            if (button.isShown() && button.getParent() != null
                    && button.getParent().getClass().getName().endsWith("TileGrid$Lines"))
                out.add(button);
        }
        return out;
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
        // Style became a picker rail; Rim is the Dock card's segment row beside its label.
        assertTrue("the Dock card has a row of segments beside its label", style != null);
        ViewGroup row = (ViewGroup) style.getParent();
        View icon = row.getChildAt(0);
        int[] at = new int[2];
        icon.getLocationInWindow(at);
        int[] parentAt = new int[2];
        fixture.parent.getLocationInWindow(parentAt);
        int[] box = containerSpan(fixture, icon);
        assertTrue("the icon stands 16dp inside its section",
            at[0] - parentAt[0] >= box[0] + fixture.dp(16) - 1);
        // Rim's segments are words with no check mark.
        for (int i = 0; i < style.getChildCount(); i++) {
            MaterialButton button = (MaterialButton) style.getChildAt(i);
            assertTrue(button.getText() + ": a word, no mark", button.getIcon() == null
                && button.getText().length() > 0);
        }
    }

    @Test
    public void aCardSwapKeepsTheDetentAndRelabelsTheHeader() {
        Fixture fixture = new Fixture(EditorCard.STATUS, 1f);
        int detent = fixture.sheet.detent();
        View firstRow = fixture.sheet.rows().getChildAt(0);
        fixture.sheet.show(EditorCard.DOCK, EditorRows.rows(EditorCard.DOCK,
            new EditorRows.Offers(true, true)), "Reset", true);
        assertEquals("the detent is the one the last card stood at", detent,
            fixture.sheet.detent());
        String dock = fixture.themed.getString(EditorCard.DOCK.titleRes);
        assertEquals(dock, String.valueOf(fixture.sheet.view().getContentDescription()));
        assertTrue("the header reads the new card", hasText(fixture.sheet.view(), dock));
        assertTrue("the old body has left the rows", firstRow.getParent()
            != fixture.sheet.rows());
        assertEquals("the new body waits to fade in", 0f, fixture.sheet.rows().getAlpha(), 0f);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        fixture.layOut();
        assertEquals("the new body is in", 1f, fixture.sheet.rows().getAlpha(), 0.001f);
        assertEquals("risen into place", 0f, fixture.sheet.rows().getTranslationY(), 0.5f);
        assertEquals(detent, fixture.sheet.detent());
    }

    private static boolean hasText(@NonNull View view, @NonNull String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText()))
            return view.getAlpha() >= 0f;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    @Test
    public void aResetSpringsItsValuesOnTheSlidersStepsAndKeepsEveryRow() {
        Fixture fixture = new Fixture(EditorCard.STATUS, 1f);
        java.util.List<Row> rows = fixture.sheet.shownRows();
        assertTrue("the card shows rows in touch order", rows.size() > 1);
        int before = fixture.sheet.rows().getChildCount();
        EditorCardSheet.ResetMotion motion = fixture.sheet.beginReset(rows);
        fixture.sheet.restate();
        fixture.sheet.endReset(motion, EditorCardSheet.RESET_STAGGER_MS);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("a reset keeps every row", before, fixture.sheet.rows().getChildCount());
        com.google.android.material.slider.Slider slider =
            new com.google.android.material.slider.Slider(fixture.themed);
        slider.setValueFrom(0f);
        slider.setValueTo(100f);
        slider.setStepSize(5f);
        assertEquals("an overshoot stays on the range", 100f,
            EditorCardSheet.onSliderSteps(slider, 104f), 0f);
        assertEquals("a value in flight lands on a step", 45f,
            EditorCardSheet.onSliderSteps(slider, 43.6f), 0f);
    }

    @Test
    public void theMainPillAndAppliesToShowEveryWordWhole() {
        // Overhaul §4: "Customiza…" and "Workspa…" no more, at 1.0× and 1.3× on 411dp.
        boolean chips = sChips;
        sChips = true;
        try {
            for (float scale : new float[] {1f, 1.3f}) {
                for (EditorCard card : new EditorCard[] {EditorCard.HOME, EditorCard.STATUS}) {
                    Fixture fixture = new Fixture(card, scale);
                    for (int frame = 0; frame < 4; frame++) {
                        fixture.layOut();
                        org.robolectric.shadows.ShadowLooper.idleMainLooper();
                    }
                    List<MaterialButton> group = new ArrayList<>();
                    if (card == EditorCard.HOME) {
                        group.addAll(fixture.sheet.mainPillButtons());
                    } else {
                        ViewGroup applies = fixture.sheet.appliesToGroup();
                        for (int i = 0; i < applies.getChildCount(); i++)
                            group.add((MaterialButton) applies.getChildAt(i));
                    }
                    assertEquals(card + " at " + scale, 3, group.size());
                    for (MaterialButton button : group) {
                        String which = card + " at " + scale + ": " + button.getText();
                        assertTrue(which + " shows", button.isShown());
                        Layout layout = button.getLayout();
                        assertEquals(which + " is not cut", 0,
                            layout.getEllipsisCount(layout.getLineCount() - 1));
                        assertTrue(which + " stands whole (" + button.getWidth() + "px, needs "
                                + com.termux.app.surfaces.rows.SegmentRow.neededPx(button) + ")",
                            button.getWidth() + 1
                                >= com.termux.app.surfaces.rows.SegmentRow.neededPx(button));
                    }
                }
            }
        } finally {
            sChips = chips;
        }
    }

    @Test
    public void everySegmentRowStandsOnOneLineAtTheDefaultTextSize() {
        for (EditorCard card : EditorCard.values()) {
            if (CardBody.redesigned(card))
                continue;
            Fixture fixture = new Fixture(card, 1f);
            fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
            fixture.layOut();
            for (com.google.android.material.button.MaterialButtonToggleGroup group
                    : toggleGroups(fixture.sheet.rows())) {
                ViewGroup holder = (ViewGroup) group.getParent();
                assertEquals(card + ": " + group.getContentDescription() + " stands label and"
                        + " control on one line (row " + holder.getWidth() + "px, parent "
                        + fixture.parent.getWidth() + "px, control " + group.getWidth() + "px)",
                    true, holder instanceof LinearLayout
                        ? ((LinearLayout) holder).getOrientation() == LinearLayout.HORIZONTAL
                        : com.termux.app.surfaces.rows.SegmentRow.standsInline(group));
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
    public void everySliderButTheLooksIsAStripColumnAndEveryRowIsThere() {
        for (EditorCard card : EditorCard.values()) {
            if (CardBody.redesigned(card))
                continue;
            Fixture fixture = new Fixture(card, 1f);
            fixture.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
            fixture.layOut();
            int strips = 0;
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true))) {
                if (spec.row.kind == EditorRows.Kind.SLIDER)
                    strips++;
            }
            List<LegendSlider> legends = legends(fixture.sheet.rows());
            assertEquals(card + ": a column for every value slider", strips, legends.size());
            for (LegendSlider legend : legends)
                assertTrue(card + ": " + legend.getContentDescription() + " stands open, no More",
                    legend.isShown() && legend.getWidth() > 0);
            // The Presets are picture tiles now (overhaul §4), so no horizontal slider is left.
            List<com.google.android.material.slider.Slider> sliders = sliders(fixture.sheet.rows());
            assertEquals(card + ": no horizontal slider; the Presets are tiles", 0,
                sliders.size());
            // The row set has a More header by the spec (§3), so the old "no More" rule is gone.
        }
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
        Fixture fixture = new Fixture(EditorCard.DOCK, 1f);
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
            int minPx = sheet.minHalfHeightPx(mWidth);
            int fullPx = EditorFramePose.fullSheetPx(window, minPx);
            int half = EditorFramePose.halfSheetPx(window, minPx, fullPx);
            sheet.setGeometry(half, fullPx, navPx);
            layOut();
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        void layOut() {
            // Every section open (overhaul §3), so every row is there to measure.
            sheet.openAllSections();
            parent.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, mWidth, mHeight);
        }
    }

    /** Values a card would show on a phone in its first days: mid-range, choices in place. */
    private static final class FakeModel implements EditorCardSheet.Model {
        private final Context mContext;
        private final com.termux.app.surfaces.cards.DockCard mDock;

        FakeModel(@NonNull Context context) {
            mContext = context;
            mDock = new com.termux.app.surfaces.cards.DockCard(new FakeDockHost(context));
        }

        @Nullable @Override public EditorCardSheet.Chip appliesToChip() {
            return sChips ? new EditorCardSheet.Chip("Applies to",
                new CharSequence[] {"Global", "Home", "Workspace"}, 0)
                : null;
        }

        @Nullable @Override public View rowView(@NonNull Row row, int widthPx) {
            if (row == sShortRow) {
                final int height = Math.round(24 * mContext.getResources().getDisplayMetrics().density);
                return new View(mContext) {
                    @Override protected void onMeasure(int w, int h) {
                        setMeasuredDimension(MeasureSpec.getSize(w), height);
                    }
                };
            }
            // Rim is the card's own word row, built as the editor builds it.
            if (row == Row.RIM)
                return com.termux.app.surfaces.rows.SegmentRow.words(mContext, row.iconRes,
                    mContext.getString(row.labelRes), choices(row), 0, widthPx, index -> {}).root;
            return mDock.view(row, widthPx);
        }

        @Override public void restateRowView(@NonNull Row row, @NonNull View view) {
            mDock.restate(row);
        }

        @Override public int value(@NonNull Row row) {
            return row.kind == EditorRows.Kind.SLIDER ? 50 : 0;
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
                // The one icon size, as the controller names its four steps.
                case HOME_ICON_SIZE: return strings(R.string.editor_icon_size_s,
                    R.string.editor_icon_size_m, R.string.editor_icon_size_l,
                    R.string.editor_icon_size_xl);
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
                case RIM: return strings(R.string.appearance_rim_none,
                    R.string.appearance_rim_hairline, R.string.appearance_rim_bright);
                case LOOK: return strings(R.string.termux_surface_preset_minimal,
                    R.string.termux_surface_preset_frost, R.string.termux_surface_preset_stock,
                    R.string.termux_surface_preset_solid, R.string.termux_surface_preset_custom);
                // The Status bar's pairs, as StatusBarCard words them.
                case STATUS_HOURS: return strings(R.string.editor_st_hours_12,
                    R.string.editor_st_hours_24);
                case STATUS_UNIT: return strings(R.string.editor_st_unit_c,
                    R.string.editor_st_unit_f);
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

    /** The Dock card on a fresh install: Docked, Apps and A–Z shown, Extra keys hidden. */
    private static final class FakeDockHost implements com.termux.app.surfaces.cards.DockCard.Host {
        private final Context mContext;

        FakeDockHost(@NonNull Context context) {
            mContext = context;
        }

        @NonNull @Override public Context context() { return mContext; }
        @Override public boolean floating() { return false; }
        @Override public void setFloating(boolean floating) {}
        @NonNull @Override public List<com.termux.app.place.Element> barOrder() {
            return com.termux.app.surfaces.cards.BarOrder.DEFAULT;
        }
        @Override public void setBarOrder(@NonNull List<com.termux.app.place.Element> order) {}
        @Override public boolean offers(@NonNull com.termux.app.place.Element bar) { return true; }
        @Override public boolean barShown(@NonNull com.termux.app.place.Element bar) {
            return bar != com.termux.app.place.Element.EXTRA_KEYS;
        }
        @Override public void setBarShown(@NonNull com.termux.app.place.Element bar, boolean shown) {}
        @NonNull @Override public com.termux.app.surfaces.cards.BarOrder.Position position(
                @NonNull com.termux.app.place.Element bar) {
            return com.termux.app.surfaces.cards.BarOrder.positionsFor(bar).get(0);
        }
        @Override public void setPosition(@NonNull com.termux.app.place.Element bar,
                @NonNull com.termux.app.surfaces.cards.BarOrder.Position position) {}
        @Override public int appIcons() { return 7; }
        @Override public int appIconsFrom() { return 3; }
        @Override public int appIconsTo() { return 10; }
        @Override public void setAppIcons(int count) {}
        @Override public boolean mostUsed() { return true; }
        @Override public void setMostUsed(boolean on) {}
        @NonNull @Override public CharSequence iconPack() { return "System"; }
        @Override public void pickIconPack() {}
        @Override public boolean azGlass() { return false; }
        @Override public void setAzGlass(boolean on) {}
        @Override public int azShow() { return 0; }
        @Override public void setAzShow(int index) {}
        @Override public void openExtraKeysEditor() {}
        @Override public int dockType() { return 0; }
        @Override public void setDockType(int index) {}
        @Override public boolean option(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option) {
            return option.ordinal() % 2 == 0;
        }
        @Override public void setOption(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option, boolean on) {}
    }

    /** How many runs of strip rows a card's list holds, broken by any other row. */
    private static int stripRuns(EditorCard card) {
        int runs = 0;
        boolean inRun = false;
        for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true))) {
            boolean strip = spec.row.kind == EditorRows.Kind.SLIDER
                || CardBody.shape(spec.row) == CardBody.Shape.SLIDER_LINK;
            if (strip && !inRun) runs++;
            inRun = strip;
        }
        return runs;
    }
}
