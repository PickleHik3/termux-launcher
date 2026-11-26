package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.surfaces.EditorRows.Form;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.rows.PairFit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The condensed row forms (spec #326, #345): which rows share a widget, which rows a form may
 * hold, the Home card still holding every knob it had, and when two cells share a line.
 */
public class RowFormsTest {

    private static final Offers ALL = new Offers(true, true);

    @Test
    public void aPairAndASteppersRowHoldTwoRowsAndALoneRowStandsAlone() {
        List<Spec> specs = new EditorRows.CardRows()
            .pair(1, Row.OPACITY, Row.FROST).pair(1, Row.CORNERS, Row.MARGIN)
            .steppers(1, Row.HOME_COLUMNS, Row.HOME_ROWS)
            .build();
        assertEquals(2, RowForms.groupEnd(specs, 0));
        assertEquals(4, RowForms.groupEnd(specs, 2));
        assertEquals(6, RowForms.groupEnd(specs, 4));
    }

    @Test
    public void chipsAndLinksTakeEveryFollowingRowOfTheirFormAndTier() {
        List<Spec> specs = new EditorRows.CardRows()
            .chips(1, Row.TWO_LINE, Row.LABEL_SHADOW, Row.GLASS_WIDGETS)
            .chips(3, Row.WALLPAPER_SCROLL)
            .links(3, Row.ICON_PACK, Row.LINK_GESTURES)
            .build();
        assertEquals("a tier change ends the chips", 3, RowForms.groupEnd(specs, 0));
        assertEquals(4, RowForms.groupEnd(specs, 3));
        assertEquals("another form ends the chips", 6, RowForms.groupEnd(specs, 4));
    }

    @Test
    public void aChoiceAndAFoldAreOneRowEach() {
        List<Spec> specs = new EditorRows.CardRows()
            .choice(1, Row.LABEL_SIZE, Row.FOLDERS).fold(1, Row.CURSOR_TRAIL, Row.CLOCK_FACE)
            .build();
        for (int i = 0; i < specs.size(); i++)
            assertEquals(i + 1, RowForms.groupEnd(specs, i));
    }

    @Test
    public void aPlainRowStandsAloneHereAndIsGroupedByItsKindInTheCardBody() {
        List<Spec> specs = new EditorRows.CardRows()
            .tier(1, Row.OPACITY, Row.FROST).build();
        assertEquals(1, RowForms.groupEnd(specs, 0));
        assertEquals(Form.PLAIN, specs.get(0).form);
    }

    @Test
    public void aFormRefusesARowItCannotDraw() {
        assertRefused(() -> new EditorRows.CardRows().pair(1, Row.OPACITY, Row.THEME));
        assertRefused(() -> new EditorRows.CardRows().chips(1, Row.LABEL_SIZE));
        assertRefused(() -> new EditorRows.CardRows().choice(1, Row.TWO_LINE));
        assertRefused(() -> new EditorRows.CardRows().links(1, Row.OPACITY));
        assertRefused(() -> new EditorRows.CardRows().fold(1, Row.TWO_LINE));
        assertRefused(() -> new EditorRows.CardRows().steppers(1, Row.TWO_LINE, Row.LABEL_SIZE));
    }

    private static void assertRefused(Runnable build) {
        try {
            build.run();
            fail("the form took a row it cannot draw");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void theSpecsOfOneRowInTwoFormsAreNotEqual() {
        assertFalse(new EditorRows.CardRows().tier(1, Row.TWO_LINE).build()
            .equals(new EditorRows.CardRows().chips(1, Row.TWO_LINE).build()));
    }

    // ------------------------------------------------------------------------------------ Home

    /** The rows the Home card listed before the condensed kinds: none may be lost. */
    private static final Set<Row> HOME_KNOBS = EnumSet.of(Row.HOME_COLUMNS, Row.HOME_ROWS,
        Row.LABEL_SIZE, Row.ICON_PACK, Row.TWO_LINE, Row.LABEL_SHADOW, Row.FOLDERS,
        Row.WALLPAPER_SCROLL, Row.GLASS_WIDGETS, Row.NUMERALS, Row.SEARCH_FIELD, Row.DRAWER_ICON, Row.LINK_GESTURES);

    @Test
    public void everyHomeKnobIsStillThere() {
        Set<Row> held = EnumSet.noneOf(Row.class);
        for (Spec spec : EditorRows.rows(EditorCard.HOME, ALL))
            held.add(spec.row);
        assertEquals(HOME_KNOBS, held);
    }

    @Test
    public void homeUnderTheOverviewHasNoPagesLinkAndKeepsTheRest() {
        Set<Row> held = EnumSet.noneOf(Row.class);
        for (Spec spec : EditorRows.rows(EditorCard.HOME, new Offers(true, true, true, true)))
            held.add(spec.row);
        Set<Row> expected = EnumSet.copyOf(HOME_KNOBS);
        assertEquals(expected, held);
    }

    @Test
    public void homeIsSixWidgetsWithNoHeadingAndTheLinksAtTheEnd() {
        List<Spec> specs = EditorRows.rows(EditorCard.HOME, ALL);
        for (Spec spec : specs)
            assertFalse(spec.row + " is a heading", EditorRows.isSectionHeading(spec.row));
        List<Form> widgets = new ArrayList<>();
        int i = 0;
        while (i < specs.size()) {
            widgets.add(specs.get(i).form);
            i = Math.max(i + 1, RowForms.groupEnd(specs, i));
        }
        // Grid, three short choices, the switches, the links.
        assertEquals(Arrays.asList(Form.STEPPERS, Form.CHOICE, Form.CHOICE, Form.CHOICE,
            Form.CHIPS, Form.LINKS), widgets);
        assertEquals(Form.LINKS, specs.get(specs.size() - 1).form);
        assertEquals("the grid shares one row", 2, RowForms.groupEnd(specs, 0));
    }

    @Test
    public void homesSwitchesAreChipsAndItsGridAndLinksShareARow() {
        List<Spec> specs = EditorRows.rows(EditorCard.HOME, ALL);
        List<Row> chips = new ArrayList<>();
        List<Row> links = new ArrayList<>();
        List<Row> steppers = new ArrayList<>();
        for (Spec spec : specs) {
            if (spec.form == Form.CHIPS) chips.add(spec.row);
            if (spec.form == Form.LINKS) links.add(spec.row);
            if (spec.form == Form.STEPPERS) steppers.add(spec.row);
        }
        assertEquals(Arrays.asList(Row.TWO_LINE, Row.LABEL_SHADOW, Row.WALLPAPER_SCROLL,
            Row.GLASS_WIDGETS, Row.SEARCH_FIELD, Row.DRAWER_ICON), chips);
        assertEquals(Arrays.asList(Row.ICON_PACK, Row.LINK_GESTURES), links);
        assertEquals(Arrays.asList(Row.HOME_COLUMNS, Row.HOME_ROWS), steppers);
    }

    // --------------------------------------------------------------------------------- the fit

    @Test
    public void twoCellsShareALineWhereEachHoldsItsLeastWidth() {
        // 379dp of room, 16dp between: 181dp each, over a 128dp least.
        assertTrue(PairFit.sideBySide(379, 16, 0, 128));
        // A phone half that wide, or a card with a wide mark between them, stacks them.
        assertFalse(PairFit.sideBySide(250, 16, 0, 128));
        assertFalse(PairFit.sideBySide(379, 16, 120, 128));
        assertFalse("no room measured yet", PairFit.sideBySide(0, 16, 0, 0));
    }

    @Test
    public void aMarkBetweenTheCellsTakesAGapOnEachSide() {
        assertEquals(PairFit.cellPx(379, 16, 0), 181);
        assertEquals(PairFit.cellPx(379, 16, 20), (379 - 32 - 20) / 2);
    }
}
