package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Group;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The order rows stand in inside a card's container (developer, 2026-11-04): Look, sliders,
 * toggles, colours, links ({@link EditorRows#ordered}). Containers, headings and tiers stay where
 * the card lists them; rows of one group keep their listed order.
 */
public class EditorRowOrderTest {

    private static final Offers[] OFFERS = {new Offers(true, true, true, false, true),
        new Offers(false, false, false, true, false)};

    @Test
    public void everyContainerRunsLookSlidersTogglesColoursLinks() {
        for (Offers offers : OFFERS)
            for (EditorCard card : EditorCard.values()) {
                Group last = null;
                int tier = 0;
                for (Spec spec : EditorRows.rows(card, offers)) {
                    if (EditorRows.isSectionHeading(spec.row) || spec.tier != tier) {
                        last = null;
                        tier = spec.tier;
                        if (EditorRows.isSectionHeading(spec.row)) continue;
                    }
                    Group group = EditorRows.groupOf(spec.row);
                    assertTrue(card + ": " + spec.row + " (" + group + ") after " + last,
                        last == null || group.ordinal() >= last.ordinal());
                    last = group;
                }
            }
    }

    @Test
    public void structuralChoicesStandAtTheTopWithTheLook() {
        for (Row row : new Row[] {Row.DOCK_TYPE, Row.STATUS_POSITION, Row.FORM, Row.STYLE,
                Row.DESIGN, Row.DOCK_STYLE, Row.CLOCK_FACE, Row.CLOCK_POSITION, Row.THEME})
            assertEquals(row.name(), Group.LOOK, EditorRows.groupOf(row));
        // The Dock's Type and the Status bar's Position are the Global card's (ADR 0023 §1).
        assertEquals("the Dock opens on its icon size", Row.HOME_ICON_SIZE,
            EditorRows.rows(EditorCard.DOCK, OFFERS[0]).get(0).row);
        assertEquals("the Status bar opens on its indicators", Row.STATUS_INDICATORS,
            EditorRows.rows(EditorCard.STATUS, OFFERS[0]).get(0).row);
        assertEquals("the Keyboard opens on its Form", Row.FORM,
            EditorRows.rows(EditorCard.KEYBOARD, OFFERS[0]).get(0).row);
        for (Row row : new Row[] {Row.RIM, Row.CONTRAST, Row.HOME_ICON_SIZE, Row.LABEL_SIZE,
                Row.PAGE_TRANSITION})
            assertEquals(row + " is a small choice", Group.TOGGLES, EditorRows.groupOf(row));
    }

    @Test
    public void theGroupsAreTheRowKinds() {
        assertEquals(Group.LOOK, EditorRows.groupOf(Row.LOOK));
        assertEquals(Group.SLIDERS, EditorRows.groupOf(Row.MARGIN));
        assertEquals("the opacity link stands with the sliders it links", Group.SLIDERS,
            EditorRows.groupOf(Row.OPACITY_LINK));
        assertEquals(Group.TOGGLES, EditorRows.groupOf(Row.RIM));
        assertEquals(Group.TOGGLES, EditorRows.groupOf(Row.CONTRAST));
        assertEquals(Group.TOGGLES, EditorRows.groupOf(Row.MULTIPLEXER));
        assertEquals(Group.COLOURS, EditorRows.groupOf(Row.TINT_HUE));
        assertEquals(Group.LINKS, EditorRows.groupOf(Row.PANE_BORDERS));
    }

    @Test
    public void headingsTiersAndAGroupsOwnOrderStay() {
        List<Spec> listed = new EditorRows.CardRows()
            .tier(1, Row.MULTIPLEXER, Row.MARGIN, Row.TINT_HUE, Row.CORNERS, Row.LOOK)
            .tier(3, Row.SECTION_LINKS, Row.LINK_SHEETS, Row.RIM)
            .tier(1, Row.HEIGHT)
            .build();
        List<Row> got = new ArrayList<>();
        for (Spec spec : EditorRows.ordered(listed))
            got.add(spec.row);
        assertEquals(Arrays.asList(Row.LOOK, Row.MARGIN, Row.CORNERS, Row.MULTIPLEXER, Row.TINT_HUE,
            Row.SECTION_LINKS, Row.RIM, Row.LINK_SHEETS, Row.HEIGHT), got);
    }

    @Test
    public void theOpacityLinkStillStandsBetweenTheTwoOpacities() {
        List<Row> rows = new ArrayList<>();
        for (Spec spec : EditorRows.rows(EditorCard.KEYBOARD, OFFERS[0]))
            rows.add(spec.row);
        int link = rows.indexOf(Row.OPACITY_LINK);
        assertEquals(Row.OPACITY, rows.get(link - 1));
        assertEquals(Row.KEY_OPACITY, rows.get(link + 1));
    }
}
