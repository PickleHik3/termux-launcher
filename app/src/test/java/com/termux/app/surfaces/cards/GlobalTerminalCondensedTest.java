package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorCard;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Form;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Global and Terminal cards in the condensed row kinds (spec #326, #346): every knob they had
 * is still listed, sliders pair by meaning, the short choices stand beside their labels, Cursor
 * trail is a fold, and Fine-tune is the one section heading left.
 */
public class GlobalTerminalCondensedTest {

    private static final Offers ALL = new Offers(true, true, true, false, true);

    @NonNull
    private static List<Spec> global() {
        return EditorRows.rows(EditorCard.APPEARANCE, ALL);
    }

    @NonNull
    private static List<Spec> terminal() {
        return EditorRows.rows(EditorCard.TERMINAL, new Offers(true, true, false, false, true));
    }

    @NonNull
    private static Set<Row> rowsOf(@NonNull List<Spec> specs) {
        Set<Row> out = EnumSet.noneOf(Row.class);
        for (Spec spec : specs)
            out.add(spec.row);
        return out;
    }

    private static int indexOf(@NonNull List<Spec> specs, @NonNull Row row) {
        for (int i = 0; i < specs.size(); i++)
            if (specs.get(i).row == row) return i;
        return -1;
    }

    private static Form formOf(@NonNull List<Spec> specs, @NonNull Row row) {
        return specs.get(indexOf(specs, row)).form;
    }

    /** Whether {@code a} and {@code b} stand next to each other as one pair. */
    private static void assertPaired(@NonNull List<Spec> specs, @NonNull Row a, @NonNull Row b) {
        int i = indexOf(specs, a);
        assertTrue(a + " is listed", i >= 0);
        assertEquals(a + " with " + b, b, specs.get(i + 1).row);
        assertEquals(Form.PAIR, specs.get(i).form);
        assertEquals(Form.PAIR, specs.get(i + 1).form);
    }

    @Test
    public void globalKeepsEveryKnobItHad() {
        Set<Row> kept = EnumSet.of(Row.WALLPAPER, Row.SHOW_WALLPAPER, Row.LOOK, Row.OPACITY,
            Row.FROST, Row.TINT, Row.DEPTH, Row.SHINE, Row.LINK_GLASS_REFRACTION, Row.STYLE,
            Row.CORNERS, Row.MARGIN, Row.HOME_ICON_SIZE, Row.THEME, Row.CONTRAST,
            Row.ADAPTIVE_LEGIBILITY, Row.TINT_HUE, Row.WALLPAPER_COLOURS, Row.TOOL_COLOURS,
            Row.FULL_SCREEN, Row.TEXT_SIZE, Row.PAGE_TRANSITION);
        Set<Row> have = rowsOf(global());
        for (Row row : kept)
            assertTrue(row + " is still on Global", have.contains(row));
    }

    @Test
    public void globalKeepsWallpaperFirstAndShowWallpaperUnderIt() {
        List<Spec> rows = global();
        assertEquals(Row.WALLPAPER, rows.get(0).row);
        assertEquals(Row.SHOW_WALLPAPER, rows.get(1).row);
    }

    @Test
    public void globalPairsItsSlidersByMeaning() {
        List<Spec> rows = global();
        assertPaired(rows, Row.OPACITY, Row.FROST);
        assertPaired(rows, Row.CORNERS, Row.MARGIN);
        assertPaired(rows, Row.DEPTH, Row.SHINE);
    }

    @Test
    public void globalChoicesStandBesideTheirLabelsAndSwitchesAreChips() {
        List<Spec> rows = global();
        for (Row row : new Row[] {Row.STYLE, Row.THEME, Row.CONTRAST, Row.HOME_ICON_SIZE,
                Row.PAGE_TRANSITION})
            assertEquals(row.name(), Form.CHOICE, formOf(rows, row));
        for (Row row : new Row[] {Row.ADAPTIVE_LEGIBILITY, Row.WALLPAPER_COLOURS, Row.FULL_SCREEN})
            assertEquals(row.name(), Form.CHIPS, formOf(rows, row));
    }

    @Test
    public void globalHasNoHeadingButFineTuneAndFineTuneHoldsTheGlassMaterial() {
        List<Spec> rows = global();
        List<Row> headings = new ArrayList<>();
        for (Spec spec : rows)
            if (EditorRows.isSectionHeading(spec.row)) headings.add(spec.row);
        assertEquals(Arrays.asList(Row.SECTION_FINE_TUNE), headings);
        int fine = indexOf(rows, Row.SECTION_FINE_TUNE);
        for (Row row : new Row[] {Row.DEPTH, Row.SHINE, Row.LINK_GLASS_REFRACTION})
            assertTrue(row + " is under Fine-tune", indexOf(rows, row) > fine);
        assertTrue("Opacity stays out of it", indexOf(rows, Row.OPACITY) < fine);
        assertEquals("Fine-tune is the card's last section", rows.size() - 1 - 3, fine);
    }

    @Test
    public void globalOrderIsWhatItIsThenSlidersChoicesSwitchesLinks() {
        List<Spec> rows = global();
        int look = indexOf(rows, Row.LOOK);
        int opacity = indexOf(rows, Row.OPACITY);
        int corners = indexOf(rows, Row.CORNERS);
        int contrast = indexOf(rows, Row.CONTRAST);
        int chips = indexOf(rows, Row.ADAPTIVE_LEGIBILITY);
        int links = indexOf(rows, Row.TOOL_COLOURS);
        assertTrue(look < opacity);
        assertTrue(corners < contrast);
        assertTrue(contrast < chips);
        assertTrue(chips < links);
        assertTrue("links after the swatches", indexOf(rows, Row.TINT_HUE) < links);
    }

    @Test
    public void globalWithoutTheRefractionOfferHasNoRefractionLink() {
        assertFalse(rowsOf(EditorRows.rows(EditorCard.APPEARANCE, new Offers(true, true)))
            .contains(Row.LINK_GLASS_REFRACTION));
    }

    @Test
    public void terminalKeepsEveryKnobItHad() {
        Set<Row> have = rowsOf(terminal());
        for (Row row : new Row[] {Row.FONT_SIZE, Row.CONTRAST, Row.ADAPTIVE_LEGIBILITY,
                Row.LINK_FONT, Row.TERMINAL_OPACITY, Row.TERMINAL_BLUR, Row.WALLPAPER_DIM,
                Row.PANE_BORDERS, Row.SPLIT_GAP, Row.MULTIPLEXER, Row.AUTO_TILING,
                Row.FOCUS_GROWS, Row.CURSOR_TRAIL, Row.EFFECT, Row.SCREEN_EVENTS,
                Row.COMPOSITE_SIGNAL, Row.SEARCH_FIELD, Row.DRAWER_ICON, Row.MINIMAL, Row.LINK_PAGES})
            assertTrue(row + " is still on Terminal", have.contains(row));
    }

    @Test
    public void terminalPairsItsSlidersAndHasNoHeadings() {
        List<Spec> rows = terminal();
        assertPaired(rows, Row.FONT_SIZE, Row.SPLIT_GAP);
        assertPaired(rows, Row.TERMINAL_OPACITY, Row.TERMINAL_BLUR);
        for (Spec spec : rows)
            assertFalse(spec.row + " is a heading", EditorRows.isSectionHeading(spec.row));
    }

    @Test
    public void terminalFoldsCursorTrailAndPutsContrastBesideItsLabel() {
        List<Spec> rows = terminal();
        assertEquals(Form.FOLD, formOf(rows, Row.CURSOR_TRAIL));
        assertEquals(Form.CHOICE, formOf(rows, Row.CONTRAST));
        assertEquals(Form.CHIPS, formOf(rows, Row.MULTIPLEXER));
        assertEquals(Form.LINKS, formOf(rows, Row.PANE_BORDERS));
        assertEquals(Form.CHIPS, formOf(rows, Row.SEARCH_FIELD));
        assertEquals(Form.CHIPS, formOf(rows, Row.DRAWER_ICON));
    }

    @Test
    public void terminalScreenEffectStandsOnlyWhereItsShaderRuns() {
        Set<Row> none = rowsOf(EditorRows.rows(EditorCard.TERMINAL, new Offers(false, false)));
        assertFalse(none.contains(Row.EFFECT));
        assertFalse(none.contains(Row.SCREEN_EVENTS));
        assertFalse(none.contains(Row.COMPOSITE_SIGNAL));
        assertTrue(none.contains(Row.FONT_SIZE));
    }

    @Test
    public void everyListedFormAcceptsItsRow() {
        // CardRows throws on a row its form cannot draw; building the tables proves it.
        for (EditorCard card : new EditorCard[] {EditorCard.APPEARANCE, EditorCard.TERMINAL})
            assertFalse(card.name(), EditorRows.rows(card, ALL).isEmpty());
    }
}
