package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.LauncherWidgetRepository;
import com.termux.app.launcher.widget.WidgetCellRect;
import com.termux.app.launcher.widget.builtin.CalendarLayoutPlanner.Content;
import com.termux.app.launcher.widget.builtin.CalendarLayoutPlanner.Event;
import com.termux.app.launcher.widget.builtin.CalendarLayoutPlanner.Grid;
import com.termux.app.launcher.widget.builtin.CalendarLayoutPlanner.MonthShape;
import com.termux.app.launcher.widget.builtin.CalendarLayoutPlanner.Result;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The Calendar (#114): the old Agenda and Calendar month open as it, on the events and on the
 * month, and keep their ids; at every span, font scale and content its plan keeps every part in
 * the inset, apart and at 11 sp or more, with 48 dp targets; the grids keep their day cells 2 dp
 * apart with the dot under the digit; the header has its own line, the month title its year,
 * an empty day says "Nothing else today" and shows tomorrow's first; access is asked for on the
 * card.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CalendarWidgetTest {
    private static final float DENSITY = 2.625f;
    private static final float[] FONT_SCALES = {1f, 1.3f, 2f};
    private static final float EPS = 0.02f;

    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) em += Character.isDigit(text.charAt(i)) ? 0.57f : 0.6f;
        return em * size;
    };

    private static final CalendarLayoutPlanner.Words WORDS = new CalendarLayoutPlanner.Words() {
        @NonNull @Override public String today() { return "Today"; }
        @NonNull @Override public String nextUp() { return "Next up"; }
        @NonNull @Override public String nothingElseToday() { return "Nothing else today"; }
        @NonNull @Override public String tomorrow() { return "Tomorrow"; }
        @NonNull @Override public String allowAccess() { return "Allow calendar access"; }
        @NonNull @Override public String allowShort() { return "Allow access"; }
        @NonNull @Override public String joined(@NonNull String first, @NonNull String second) {
            return first.isEmpty() ? second : second.isEmpty() ? first : first + " · " + second;
        }
    };

    private static final String[] NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] LETTERS = {"M", "T", "W", "T", "F", "S", "S"};

    private int permissionAsks;
    private final BuiltinWidgetServices.Host host = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { permissionAsks++; }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        fontScale(1f);
    }

    // ----- kind loading ---------------------------------------------------------------------

    @Test public void theOldCalendarsOpenAsTheCalendarOnTheirView() {
        assertEquals(BuiltinWidgetKind.CALENDAR, BuiltinWidgetKind.fromId("calendar"));
        assertEquals(BuiltinWidgetKind.CALENDAR, BuiltinWidgetKind.fromId("agenda"));
        assertEquals(BuiltinWidgetKind.CALENDAR, BuiltinWidgetKind.fromId("calendar.month"));
        assertTrue(BuiltinWidgetAliases.isAlias("agenda"));
        assertTrue(BuiltinWidgetAliases.isAlias("calendar.month"));
        assertFalse(BuiltinWidgetAliases.isAlias("calendar"));

        int calendars = 0;
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            assertFalse(kind.id, kind.id.equals("agenda") || kind.id.equals("calendar.month"));
            if (kind.id.startsWith("calendar") || kind.id.equals("agenda")) calendars++;
        }
        assertEquals("one Calendar in the picker", 1, calendars);

        BuiltinWidgetServices services = new BuiltinWidgetServices(context, host);
        BuiltinWidgetHost widgets = new BuiltinWidgetHost(context, services,
            () -> BuiltinWidgetStyle.Numerals.SANS);
        try {
            LauncherWidgetRecord agenda = LauncherWidgetRecord.builtin(-1, "agenda",
                new WidgetCellRect(0, 0, 2, 2), 0, null);
            View agendaView = widgets.viewFor(agenda);
            assertTrue("an agenda opens as the Calendar", agendaView instanceof CalendarWidgetView);
            assertFalse("on today's events", ((CalendarWidgetView) agendaView).monthView());
            assertEquals("the stored id never changes", "agenda", agenda.builtinKind);

            LauncherWidgetRecord month = LauncherWidgetRecord.builtin(-2, "calendar.month",
                new WidgetCellRect(0, 0, 2, 2), 0, null);
            View monthView = widgets.viewFor(month);
            assertTrue(monthView instanceof CalendarWidgetView);
            assertTrue("on the month", ((CalendarWidgetView) monthView).monthView());

            LauncherWidgetRecord placed = LauncherWidgetRecord.builtin(-3, "calendar",
                new WidgetCellRect(0, 0, 2, 2), 0, null);
            assertFalse("a new Calendar starts on the events",
                ((CalendarWidgetView) widgets.viewFor(placed)).monthView());

            // What a record stores itself wins over what its old kind implied.
            Bundle own = new Bundle();
            own.putString(CalendarWidgetSupport.KEY_VIEW, CalendarWidgetSupport.VIEW_EVENTS);
            Bundle opened = widgets.settingsOf(LauncherWidgetRecord.builtin(-4, "calendar.month",
                new WidgetCellRect(0, 0, 2, 2), 0, own));
            assertEquals(CalendarWidgetSupport.VIEW_EVENTS,
                opened.getString(CalendarWidgetSupport.KEY_VIEW));
        } finally {
            widgets.destroy();
            services.destroy();
        }
    }

    @Test public void anOldCalendarKeepsItsIdThroughTheStore() {
        Memory memory = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "agenda",
            new WidgetCellRect(0, 0, 2, 1), 0, null)));
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-2, "calendar.month",
            new WidgetCellRect(0, 1, 2, 2), 0, null)));
        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertNotNull("an old agenda is not dropped as unknown", reloaded.get(-1));
        assertEquals("agenda", reloaded.get(-1).builtinKind);
        assertNotNull(reloaded.get(-2));
        assertEquals("calendar.month", reloaded.get(-2).builtinKind);
        assertEquals(BuiltinWidgetKind.CALENDAR, BuiltinWidgetKind.fromId(reloaded.get(-2).builtinKind));
    }

    // ----- the layout plan ------------------------------------------------------------------

    @Test public void everySpanFontScaleAndContentFitsItsInset() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float fs : FONT_SCALES) {
                for (int[] size : sizes(span)) {
                    for (Content content : contents()) {
                        String what = span + " " + size[0] + "x" + size[1] + " at " + fs + " "
                            + describe(content);
                        Result result = CalendarLayoutPlanner.plan(span, size[0], size[1], fs,
                            content, WORDS, MEASURE);
                        check(result, what);
                    }
                }
            }
        }
    }

    @Test public void theGridsKeepTheirCellsApartWithTheDotUnderTheDigit() {
        int grids = 0;
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO}) {
            for (float fs : FONT_SCALES) {
                for (MonthShape month : new MonthShape[] {october(false), august()}) {
                    Content content = content(3, true, true, true, true, false, month);
                    Result result = CalendarLayoutPlanner.plan(span, span.widthDp, span.heightDp,
                        fs, content, WORDS, MEASURE);
                    String what = span + " at " + fs;
                    if (result.month != null) {
                        checkGrid(result.month, result.plan.part(CalendarLayoutPlanner.MONTH), fs,
                            what + " month");
                        grids++;
                    }
                    if (result.strip != null) {
                        checkGrid(result.strip, result.plan.part(CalendarLayoutPlanner.STRIP), fs,
                            what + " strip");
                        grids++;
                    }
                }
            }
        }
        assertTrue("grids drawn: " + grids, grids >= 8);
    }

    @Test public void eachSpanShowsItsDesign() {
        Content typical = content(3, true, true, true, false, false, october(false));
        WidgetLayoutPlan one = plan(BuiltinWidgetSpan.ONE_BY_ONE, typical);
        assertEquals("Wed", one.part(CalendarLayoutPlanner.WEEKDAY).text);
        assertEquals("21", one.part(CalendarLayoutPlanner.DATE).text);
        assertEquals("a dot per event of today", 3,
            one.partsStartingWith(CalendarLayoutPlanner.DOT).size());

        WidgetLayoutPlan two = plan(BuiltinWidgetSpan.TWO_BY_ONE, typical);
        assertEquals("Design review", two.part(CalendarLayoutPlanner.NEXT_TITLE).text);
        assertEquals("15:00 · in 40 min", two.part(CalendarLayoutPlanner.NEXT_DETAIL).text);
        assertTrue(two.has(CalendarLayoutPlanner.NEXT_DISC));
        assertTrue(two.has(CalendarLayoutPlanner.NEXT_TOUCH));

        WidgetLayoutPlan square = plan(BuiltinWidgetSpan.TWO_BY_TWO, typical);
        assertEquals("Today", square.part(CalendarLayoutPlanner.HEADER).text);
        for (int i = 0; i < 3; i++) {
            assertTrue("row " + i, square.has("row." + i + ".title"));
            assertTrue("row " + i + " chip", square.has("row." + i + ".chip"));
        }
        assertEquals("15:00", square.part("row.0.time").text);
        assertFalse("events first: no month", square.has(CalendarLayoutPlanner.MONTH));

        Content monthOption = content(3, true, true, true, true, false, october(false));
        Result squareMonth = CalendarLayoutPlanner.plan(BuiltinWidgetSpan.TWO_BY_TWO, 184, 192,
            1f, monthOption, WORDS, MEASURE);
        assertTrue("the month is a 2×2 option", squareMonth.plan.has(CalendarLayoutPlanner.MONTH));
        assertNotNull(squareMonth.month);
        assertEquals("the whole month fits", 5, squareMonth.month.rows);
        assertTrue(squareMonth.plan.has(CalendarLayoutPlanner.STEP_PREV + ".touch"));
        assertTrue(squareMonth.plan.has(CalendarLayoutPlanner.STEP_NEXT + ".touch"));

        Result wide = CalendarLayoutPlanner.plan(BuiltinWidgetSpan.FOUR_BY_ONE, 376, 92, 1f,
            typical, WORDS, MEASURE);
        assertEquals("Design review", wide.plan.part(CalendarLayoutPlanner.NEXT_TITLE).text);
        assertNotNull("the fortnight", wide.strip);
        assertEquals(14, wide.strip.columns);
        assertNotNull("with its weekday letters", wide.strip.letterRole);

        Result big = CalendarLayoutPlanner.plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376, 192, 1f,
            typical, WORDS, MEASURE);
        assertTrue("the month beside the events", big.plan.has(CalendarLayoutPlanner.MONTH));
        assertEquals("Next up", big.plan.part(CalendarLayoutPlanner.HEADER).text);
        assertTrue(big.plan.part(CalendarLayoutPlanner.HEADER).box.left
            > big.plan.part(CalendarLayoutPlanner.MONTH).box.right());
        assertEquals("in 40 min", big.plan.part("row.0.when").text);
        assertNotNull(big.month);
        assertTrue("short weekday names at 4×2", big.month.shortNames);
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, big.plan.maxSpan);
    }

    @Test public void theHeaderHasItsOwnLine() {
        Content typical = content(3, true, true, true, false, false, october(false));
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_TWO}) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = CalendarLayoutPlanner.plan(span, span.widthDp,
                    span.heightDp, fs, typical, WORDS, MEASURE).plan;
                Part header = plan.part(CalendarLayoutPlanner.HEADER);
                assertNotNull(span + " " + fs, header);
                for (Part part : plan.parts) {
                    if (part == header || part.type == WidgetLayoutPlan.Type.TOUCH) continue;
                    if (part.box.right() <= header.box.left + EPS) continue; // the month's side
                    boolean beside = part.box.top < header.box.bottom() - EPS
                        && header.box.top < part.box.bottom() - EPS;
                    assertFalse(span + " " + fs + ": " + part + " beside the header", beside);
                }
            }
        }
    }

    @Test public void theMonthTitleCarriesTheYear() {
        for (float fs : FONT_SCALES) {
            for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_TWO,
                BuiltinWidgetSpan.FOUR_BY_TWO}) {
                WidgetLayoutPlan plan = CalendarLayoutPlanner.plan(span, span.widthDp,
                    span.heightDp, fs, content(3, true, true, true, true, false, october(false)),
                    WORDS, MEASURE).plan;
                Part title = plan.part(CalendarLayoutPlanner.MONTH_TITLE);
                if (title == null) continue;
                assertTrue(span + " " + fs + ": " + title, title.text.endsWith("2026"));
            }
        }
        Part title = plan(BuiltinWidgetSpan.FOUR_BY_TWO,
            content(3, true, true, true, false, false, october(false)))
            .part(CalendarLayoutPlanner.MONTH_TITLE);
        assertNotNull(title);
        assertTrue(title.text.contains("2026"));
    }

    @Test public void withNothingLeftTodayItSaysSoAndShowsTomorrow() {
        Content empty = content(0, true, true, true, false, false, october(false));
        WidgetLayoutPlan two = plan(BuiltinWidgetSpan.TWO_BY_ONE, empty);
        assertEquals("Nothing else today", two.part(CalendarLayoutPlanner.NEXT_TITLE).text);
        assertEquals("Tomorrow 10:00 · Dentist", two.part(CalendarLayoutPlanner.NEXT_DETAIL).text);

        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_TWO}) {
            WidgetLayoutPlan plan = plan(span, empty);
            assertEquals(span.toString(), "Nothing else today",
                plan.part(CalendarLayoutPlanner.EMPTY).text);
            assertEquals("Tomorrow", plan.part(CalendarLayoutPlanner.TOMORROW_LABEL).text);
            assertEquals("Dentist", plan.part(CalendarLayoutPlanner.TOMORROW + "title").text);
            assertTrue(plan.has(CalendarLayoutPlanner.TOMORROW + "touch"));
        }
        WidgetLayoutPlan wide = plan(BuiltinWidgetSpan.FOUR_BY_ONE, empty);
        assertEquals("Nothing else today", wide.part(CalendarLayoutPlanner.NEXT_TITLE).text);
        assertTrue(wide.part(CalendarLayoutPlanner.NEXT_DETAIL).text.contains("Dentist"));

        // With nothing tomorrow either, it just says so.
        WidgetLayoutPlan bare = plan(BuiltinWidgetSpan.TWO_BY_TWO,
            content(0, false, true, true, false, false, october(false)));
        assertEquals("Nothing else today", bare.part(CalendarLayoutPlanner.EMPTY).text);
        assertFalse(bare.has(CalendarLayoutPlanner.TOMORROW + "title"));
    }

    @Test public void tomorrowsFirstIsTheFirstToStartTomorrow() {
        ZoneId zone = ZoneId.of("UTC");
        LocalDate today = LocalDate.of(2026, 10, 21);
        List<CalendarEvent> events = new ArrayList<>();
        events.add(event(1, today.atTime(18, 0), zone, "Dinner"));
        events.add(event(2, today.plusDays(1).atTime(10, 0), zone, "Dentist"));
        events.add(event(3, today.plusDays(1).atTime(15, 0), zone, "Gym"));
        CalendarEvent first = CalendarWidgetFormats.firstStarting(events, today.plusDays(1), zone);
        assertNotNull(first);
        assertEquals("Dentist", first.title);
        assertNull(CalendarWidgetFormats.firstStarting(events, today.plusDays(2), zone));
    }

    @Test public void calendarAccessIsAskedForOnTheCard() {
        Content denied = content(0, false, false, false, false, false, october(false));
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_ONE,
            BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.FOUR_BY_ONE,
            BuiltinWidgetSpan.FOUR_BY_TWO}) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = CalendarLayoutPlanner.plan(span, span.widthDp,
                    span.heightDp, fs, denied, WORDS, MEASURE).plan;
                assertTrue(span + " " + fs, plan.has(CalendarLayoutPlanner.ALLOW_TOUCH));
            }
        }

        // On the card itself: no access yet, and the card's own target asks for it.
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, host);
        try {
            CalendarWidgetView view = laidOut(services, BuiltinWidgetSpan.TWO_BY_TWO, 184, 192,
                null, false);
            assertNotNull(view.plan());
            assertTrue(view.plan().has(CalendarLayoutPlanner.ALLOW_TOUCH));
            View target = find(view, context.getString(com.termux.R.string.bw_calendar_allow_access));
            assertNotNull("a target for the access line", target);
            permissionAsks = 0;
            assertTrue(target.performClick());
            assertEquals(1, permissionAsks);
        } finally {
            services.destroy();
        }
    }

    // ----- the card -------------------------------------------------------------------------

    @Test public void everySpanLaysOutOnTheCard() {
        List<String> clips = new ArrayList<>();
        for (float fs : FONT_SCALES) {
            fontScale(fs);
            for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
                for (String choice : new String[] {CalendarWidgetSupport.VIEW_EVENTS,
                    CalendarWidgetSupport.VIEW_MONTH}) {
                    Bundle config = new Bundle();
                    config.putString(CalendarWidgetSupport.KEY_VIEW, choice);
                    BuiltinWidgetServices services = new BuiltinWidgetServices(context, host);
                    CalendarWidgetView view = laidOut(services, span, span.widthDp,
                        span.heightDp, config, true);
                    String where = span + "/" + choice + " at " + fs;
                    assertEquals(where, span, view.span());
                    WidgetLayoutPlan plan = view.plan();
                    assertNotNull(where, plan);
                    assertFalse(where + " draws something", plan.parts.isEmpty());
                    collectClips(view, where, clips);
                    services.destroy();
                }
            }
        }
        assertTrue("clipped views:\n" + String.join("\n", clips), clips.isEmpty());
    }

    // ----- checks ---------------------------------------------------------------------------

    private static void check(@NonNull Result result, @NonNull String what) {
        WidgetLayoutPlan plan = result.plan;
        List<Part> parts = plan.parts;
        for (Part part : parts) {
            boolean target = part.type == WidgetLayoutPlan.Type.TOUCH;
            if (target) {
                assertTrue(what + ": " + part + " inside the card " + plan.card,
                    plan.card.contains(part.box));
                if (plan.card.height >= 48f && plan.card.width >= 48f) {
                    assertTrue(what + ": " + part + " is a 48 dp target",
                        part.box.width >= 48f - EPS && part.box.height >= 48f - EPS);
                }
            } else {
                assertTrue(what + ": " + part + " inside " + plan.inset,
                    plan.inset.contains(part.box));
            }
            if (part.role != null && part.role != WidgetTokens.Role.HERO) {
                assertTrue(what + ": " + part + " under the text floor",
                    part.sizeDp >= 11f * plan.fontScale - EPS);
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                Part a = parts.get(i), b = parts.get(j);
                boolean aTouch = a.type == WidgetLayoutPlan.Type.TOUCH;
                boolean bTouch = b.type == WidgetLayoutPlan.Type.TOUCH;
                // A target lies over what it acts on; targets must not share any area.
                if (aTouch != bTouch) continue;
                // A chip holds its time, a disc its glyph.
                if (inside(a, b) || inside(b, a)) continue;
                assertFalse(what + ": " + a + " overlaps " + b, a.box.overlaps(b.box));
            }
        }
        if (result.month != null) checkGrid(result.month, plan.part(CalendarLayoutPlanner.MONTH),
            plan.fontScale, what + " month");
        if (result.strip != null) checkGrid(result.strip, plan.part(CalendarLayoutPlanner.STRIP),
            plan.fontScale, what + " strip");
        // The month steppers' targets end before the first week's days.
        Part month = plan.part(CalendarLayoutPlanner.MONTH);
        if (month != null && result.month != null) {
            float firstDays = month.box.top + result.month.cells.get(0).top;
            for (String id : new String[] {CalendarLayoutPlanner.STEP_PREV + ".touch",
                CalendarLayoutPlanner.STEP_NEXT + ".touch"}) {
                Part step = plan.part(id);
                assertNotNull(what + ": " + id, step);
                assertTrue(what + ": " + step + " reaches the days at " + firstDays,
                    step.box.bottom() <= firstDays + EPS);
            }
        }
    }

    /** Whether {@code inner} is drawn on {@code outer}: a chip's time, a disc's glyph. */
    private static boolean inside(@NonNull Part outer, @NonNull Part inner) {
        if (outer.type != WidgetLayoutPlan.Type.FACE) return false;
        if (outer.id.endsWith(".chip")) {
            String prefix = outer.id.substring(0, outer.id.length() - "chip".length());
            return inner.id.equals(prefix + "time") && outer.box.contains(inner.box);
        }
        return outer.id.equals(CalendarLayoutPlanner.NEXT_DISC)
            && inner.id.equals(CalendarLayoutPlanner.NEXT_ICON) && outer.box.contains(inner.box);
    }

    private static void checkGrid(@NonNull Grid grid, @Nullable Part face, float fs,
                                  @NonNull String what) {
        assertNotNull(what + ": the grid's box", face);
        assertTrue(what + ": " + grid.width + "x" + grid.height + " in " + face,
            grid.width <= face.box.width + EPS && grid.height <= face.box.height + EPS);
        assertTrue(what + ": digits under the floor", grid.digitSizeDp >= 11f * fs - EPS);
        if (grid.letterRole != null) {
            assertTrue(what + ": names under the floor", grid.letterSizeDp >= 11f * fs - EPS);
            float namesBottom = grid.letters.get(0).bottom();
            assertTrue(what + ": names above the days", namesBottom <= grid.cells.get(0).top + EPS);
        }
        Box whole = new Box(0f, 0f, grid.width, grid.height);
        for (int i = 0; i < grid.cells.size(); i++) {
            Box cell = grid.cells.get(i);
            Box digit = grid.digits.get(i);
            Box dot = grid.dots.get(i);
            assertTrue(what + ": cell " + i + " inside", whole.contains(cell));
            assertTrue(what + ": digit " + i + " in its cell", cell.contains(digit));
            assertTrue(what + ": dot " + i + " in its cell", cell.contains(dot));
            assertTrue(what + ": dot " + i + " under the digit", dot.top >= digit.bottom() - EPS);
            assertEquals(what + ": dot " + i + " centred under the digit", digit.centerX(),
                dot.centerX(), EPS);
            assertTrue(what + ": \"28\" fits cell " + i, MEASURE.widthDp("28", grid.digitRole,
                true, grid.digitSizeDp) <= digit.width + EPS);
            int col = i % grid.columns;
            if (col + 1 < grid.columns) {
                float gap = grid.cells.get(i + 1).left - cell.right();
                assertTrue(what + ": cells " + i + " and " + (i + 1) + " " + gap + " dp apart",
                    gap >= CalendarLayoutPlanner.CELL_GAP_DP - EPS);
            }
            if (i + grid.columns < grid.cells.size()) {
                float gap = grid.cells.get(i + grid.columns).top - cell.bottom();
                assertTrue(what + ": rows at cell " + i + " " + gap + " dp apart",
                    gap >= CalendarLayoutPlanner.CELL_GAP_DP - EPS);
            }
        }
    }

    // ----- content --------------------------------------------------------------------------

    @NonNull private static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span,
                                                  @NonNull Content content) {
        return CalendarLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f, content, WORDS,
            MEASURE).plan;
    }

    @NonNull private static int[][] sizes(@NonNull BuiltinWidgetSpan span) {
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            return new int[][] {{57, 57}, {span.widthDp, span.heightDp}, {120, 120}};
        }
        return new int[][] {
            {span.minWidthDp, span.minHeightDp},
            {span.widthDp, span.heightDp},
            {Math.round(span.widthDp * 1.4f), Math.round(span.heightDp * 1.4f)}
        };
    }

    @NonNull private static List<Content> contents() {
        List<Content> out = new ArrayList<>();
        for (MonthShape month : new MonthShape[] {october(false), october(true), august()}) {
            for (boolean monthView : new boolean[] {false, true}) {
                out.add(content(3, true, true, true, monthView, false, month));
                out.add(content(5, true, true, true, monthView, true, month));
                out.add(content(1, false, true, true, monthView, false, month));
                out.add(content(0, true, true, true, monthView, false, month));
                out.add(content(0, true, true, true, monthView, true, month));
                out.add(content(0, false, true, true, monthView, false, month));
                out.add(content(0, false, true, false, monthView, false, month));
                out.add(content(0, false, false, false, monthView, false, month));
            }
        }
        return out;
    }

    @NonNull private static String describe(@NonNull Content content) {
        return "events=" + content.today.size() + " tomorrow=" + (content.tomorrow != null)
            + " permitted=" + content.permitted + " loaded=" + content.loaded
            + " month=" + content.monthView + " rows=" + content.month.rows;
    }

    @NonNull private static Content content(int events, boolean tomorrow, boolean permitted,
                                            boolean loaded, boolean monthView, boolean longText,
                                            @NonNull MonthShape month) {
        String[] titles = {"Design review", "Team sync", "Dinner", "Gym", "Call home"};
        String[] times = {"15:00", "16:30", "18:00", "All day", "21:00"};
        String[] whens = {"in 40 min", "in 2 h", "in 4 h", "All day", "in 7 h"};
        List<Event> today = new ArrayList<>();
        for (int i = 0; i < events && permitted && loaded; i++) {
            String title = longText
                ? titles[i % titles.length] + " with the whole product and design group"
                : titles[i % titles.length];
            today.add(new Event(times[i % times.length], title, whens[i % whens.length]));
        }
        Event next = tomorrow && permitted && loaded && today.isEmpty()
            ? new Event("10:00", longText ? "Dentist appointment on the far side of town"
                : "Dentist", "")
            : null;
        String[] strip = new String[14];
        for (int i = 0; i < 14; i++) strip[i] = LETTERS[i % 7];
        int dots = permitted && loaded ? Math.max(events, 0) : 0;
        return new Content(permitted, loaded, "Wed", "21", today, 0, dots, today.isEmpty() ? -1 : 0,
            next, monthView, month, strip);
    }

    /** October 2026 from Monday: five weeks, today the 21st. */
    @NonNull private static MonthShape october(boolean stepped) {
        return new MonthShape("October 2026", "Oct 2026", 3, 31, 5, stepped ? -1 : 23, stepped,
            NAMES, LETTERS);
    }

    /** August 2026 from Monday: six weeks, today the 31st. */
    @NonNull private static MonthShape august() {
        return new MonthShape("August 2026", "Aug 2026", 5, 31, 6, 35, false, NAMES, LETTERS);
    }

    @NonNull private static CalendarEvent event(long id, @NonNull java.time.LocalDateTime start,
                                                @NonNull ZoneId zone, @NonNull String title) {
        long begin = start.atZone(zone).toInstant().toEpochMilli();
        return CalendarEvent.of(id, begin, begin + 60 * 60_000L, false, title, "", "", 0, zone);
    }

    // ----- views ----------------------------------------------------------------------------

    @NonNull private CalendarWidgetView laidOut(@NonNull BuiltinWidgetServices services,
                                                @NonNull BuiltinWidgetSpan span, int widthDp,
                                                int heightDp, @Nullable Bundle config,
                                                boolean preview) {
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        CalendarWidgetView view = new CalendarWidgetView(context, services, style);
        view.setPreview(preview);
        view.setCells(span.columns, span.rows);
        view.bind(config);
        int w = (int) Math.ceil(widthDp * DENSITY);
        int h = (int) Math.ceil(heightDp * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        // The plan's views are placed in the layout after the plan is made.
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        return view;
    }

    @Nullable private static View find(@NonNull View root, @NonNull CharSequence description) {
        if (description.toString().contentEquals(
            root.getContentDescription() == null ? "" : root.getContentDescription())
            && root.isClickable()) {
            return root;
        }
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }

    private static void collectClips(@NonNull ViewGroup parent, @NonNull String where,
                                     @NonNull List<String> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            if (child.getLeft() < 0 || child.getTop() < 0 || child.getRight() > parent.getWidth()
                || child.getBottom() > parent.getHeight()) {
                out.add(where + ": " + child.getClass().getSimpleName() + " [" + child.getLeft()
                    + "," + child.getTop() + "," + child.getRight() + "," + child.getBottom()
                    + "] outside " + parent.getWidth() + "x" + parent.getHeight());
            }
            if (child instanceof ViewGroup) collectClips((ViewGroup) child, where, out);
        }
    }

    private void fontScale(float scale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = scale;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY * scale;
        metrics.densityDpi = 420;
        context.getResources().updateConfiguration(configuration, metrics);
        metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY * scale;
        metrics.densityDpi = 420;
    }

    private static final class Memory implements LauncherWidgetRepository.Storage {
        String value;
        @Override public String read() { return value; }
        @Override public boolean write(@NonNull String next) { value = next; return true; }
    }
}
