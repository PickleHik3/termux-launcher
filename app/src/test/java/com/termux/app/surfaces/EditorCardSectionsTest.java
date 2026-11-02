package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Card → section titles in order → row ids, against the appearance-cards overhaul spec §2 (wave
 * W2b-1, existing rows only). A section is a SECTION_* heading and the rows up to the next; the
 * rows before the first heading are the section named for the card ({@link #CARD}).
 */
public class EditorCardSectionsTest {

    private static final String CARD = "<card name>";
    private static final Offers ALL = new Offers(true, true, true, false);

    private static final List<Row> BLOCK = Arrays.asList(Row.LOOK, Row.OPACITY, Row.FROST,
        Row.GRAIN, Row.TINT, Row.TINT_HUE, Row.CORNERS, Row.RIM, Row.GLASS_EDGE, Row.DEPTH);

    private static List<Row> block(Row... after) {
        List<Row> out = new ArrayList<>(BLOCK);
        out.addAll(Arrays.asList(after));
        return out;
    }

    /** The card's sections in order, each with its rows. */
    private static Map<String, List<Row>> sections(EditorCard card, Offers offers) {
        Map<String, List<Row>> out = new LinkedHashMap<>();
        String at = null;
        for (EditorRows.Spec spec : EditorRows.rows(card, offers)) {
            if (EditorCardSheet.isSectionHeading(spec.row)) {
                at = spec.row.name();
                assertFalse(card + " repeats " + at, out.containsKey(at));
                out.put(at, new ArrayList<>());
                continue;
            }
            if (at == null) {
                at = CARD;
                out.put(at, new ArrayList<>());
            }
            out.get(at).add(spec.row);
        }
        return out;
    }

    private static Map<String, List<Row>> expect(Object... pairs) {
        Map<String, List<Row>> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<Row> rows = (List<Row>) pairs[i + 1];
            out.put((String) pairs[i], rows);
        }
        return out;
    }

    private static void check(EditorCard card, Map<String, List<Row>> want) {
        Map<String, List<Row>> got = sections(card, ALL);
        assertEquals(card + " sections", new ArrayList<>(want.keySet()),
            new ArrayList<>(got.keySet()));
        for (String section : want.keySet())
            assertEquals(card + " › " + section, want.get(section), got.get(section));
    }

    @Test
    public void customization() {
        check(EditorCard.APPEARANCE, expect(
            "SECTION_APPEARANCE", Arrays.asList(Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN,
                Row.TINT, Row.CORNERS, Row.RIM, Row.VIBRANCY, Row.GLASS_EDGE, Row.DEPTH),
            "SECTION_COLOURS", Arrays.asList(Row.THEME, Row.CONTRAST, Row.TINT_HUE,
                Row.WALLPAPER_COLOURS),
            "SECTION_LAYOUT", Arrays.asList(Row.ELEMENTS),
            "SECTION_MORE_OPTIONS", Arrays.asList(Row.STYLE, Row.FULL_SCREEN, Row.SHOW_WALLPAPER),
            "SECTION_LINKS", Arrays.asList(Row.LINK_SHEETS, Row.TOOL_COLOURS)));
    }

    @Test
    public void home() {
        check(EditorCard.HOME, expect(
            CARD, Arrays.asList(Row.HOME_COLUMNS, Row.HOME_ROWS, Row.ICON_PACK,
                Row.HOME_ICON_SIZE, Row.LABEL_SIZE, Row.PAGE_TRANSITION),
            "SECTION_MORE_OPTIONS", Arrays.asList(Row.TWO_LINE, Row.LABEL_SHADOW, Row.PAGE_DOTS,
                Row.WALLPAPER_SCROLL, Row.FOLDERS, Row.NUMERALS, Row.GLASS_WIDGETS),
            "SECTION_LAYOUT", Arrays.asList(Row.ELEMENTS),
            "SECTION_APPEARANCE", block(),
            "SECTION_LINKS", Arrays.asList(Row.LINK_GLOBAL, Row.LINK_GESTURES, Row.LINK_PAGES,
                Row.LINK_SHEETS)));
    }

    @Test
    public void homeUnderTheOverviewHasNoPagesLink() {
        Map<String, List<Row>> got = sections(EditorCard.HOME, new Offers(true, true, true, true));
        assertEquals(Arrays.asList(Row.LINK_GLOBAL, Row.LINK_GESTURES, Row.LINK_SHEETS),
            got.get("SECTION_LINKS"));
    }

    @Test
    public void workspace() {
        check(EditorCard.WORKSPACE, expect(
            "SECTION_APPEARANCE", block(Row.MINIMAL),
            "SECTION_LAYOUT", Arrays.asList(Row.ELEMENTS)));
    }

    @Test
    public void statusBar() {
        check(EditorCard.STATUS, expect(
            CARD, Arrays.asList(Row.STATUS_POSITION, Row.MARGIN, Row.STATUS_INDICATORS),
            "SECTION_CLOCK_WEATHER", Arrays.asList(Row.CLOCK_FACE, Row.CLOCK_POSITION,
                Row.STATUS_SECONDS, Row.STATUS_HOURS, Row.STATUS_UNIT),
            "SECTION_APPEARANCE", block(),
            "SECTION_LINKS", Arrays.asList(Row.CAMERA_CUTOUT, Row.PINNED_NOTIFICATIONS)));
    }

    @Test
    public void dock() {
        check(EditorCard.DOCK, expect(
            CARD, Arrays.asList(Row.DOCK_TYPE, Row.MARGIN, Row.HOME_ICON_SIZE,
                Row.DOCK_APP_ICONS),
            "SECTION_MORE_OPTIONS", Arrays.asList(Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS,
                Row.DOCK_MONOCHROME),
            "SECTION_LAYOUT", Arrays.asList(Row.DOCK_BARS),
            "SECTION_APPEARANCE", block(Row.REFLECTION),
            "SECTION_LINKS", Arrays.asList(Row.LINK_SHEETS, Row.LINK_GLOBAL)));
    }

    @Test
    public void terminal() {
        check(EditorCard.TERMINAL, expect(
            CARD, Arrays.asList(Row.TEXT_SIZE, Row.CONTRAST, Row.PANE_BORDERS, Row.CURSOR_TRAIL,
                Row.EFFECT, Row.SPLIT_GAP, Row.MARGIN, Row.MULTIPLEXER, Row.AUTO_TILING,
                Row.FOCUS_GROWS),
            "SECTION_LAYOUT", Arrays.asList(Row.ELEMENTS),
            "SECTION_APPEARANCE", block(),
            "SECTION_LINKS", Arrays.asList(Row.LINK_FONT)));
    }

    @Test
    public void keyboard() {
        check(EditorCard.KEYBOARD, expect(
            CARD, Arrays.asList(Row.FORM, Row.HEIGHT, Row.KEY_SPACING, Row.MARGIN,
                Row.KEY_CORNERS, Row.BOTTOM_PADDING),
            "SECTION_APPEARANCE", Arrays.asList(Row.LOOK, Row.OPACITY, Row.OPACITY_LINK,
                Row.KEY_OPACITY, Row.FROST, Row.GRAIN, Row.TINT, Row.TINT_HUE, Row.CORNERS,
                Row.RIM, Row.GLASS_EDGE, Row.DEPTH),
            "SECTION_LINKS", Arrays.asList(Row.KEYBOARD_THEME, Row.LINK_LAYOUTS)));
    }

    @Test
    public void widgetsAndMenusAreAppearanceOnly() {
        check(EditorCard.WIDGETS, expect("SECTION_APPEARANCE", block()));
        check(EditorCard.SHEETS, expect("SECTION_APPEARANCE", block()));
    }

    @Test
    public void noCardListsChangeWallpaperOrTheOldHeadings() {
        for (EditorCard card : EditorCard.values()) {
            for (EditorRows.Spec spec : EditorRows.rows(card, ALL)) {
                assertFalse(card + " " + spec.row, spec.row == Row.CHANGE_WALLPAPER
                    || spec.row == Row.HINT_GLASS_MATERIAL || spec.row == Row.LINK_WIDGETS
                    || spec.row == Row.STATUS_OPTIONS);
                if (EditorCardSheet.isSectionHeading(spec.row))
                    assertFalse(card + " " + spec.row, Arrays.asList(Row.SECTION_GLASS,
                        Row.SECTION_SHAPE, Row.SECTION_MORE, Row.SECTION_GRID, Row.SECTION_LOOK,
                        Row.SECTION_LABELS_FOLDERS, Row.SECTION_OPTIONS, Row.SECTION_WALLPAPER,
                        Row.SECTION_COLOUR, Row.SECTION_GLASS_MATERIAL).contains(spec.row));
            }
        }
    }
}
