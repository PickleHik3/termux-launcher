package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Content;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Item;
import com.termux.app.launcher.widget.builtin.GlanceLayoutPlanner.Kind;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Glance (ticket #116, spec #101) and the Bare path it is drawn in: the layout plan with each part
 * present or absent at 4×1 and 2×1, line two's priority order, and Bare's measured ink and dim.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlanceWidgetTest {

    // ----- the layout plan --------------------------------------------------------------------

    /** A deterministic width: every character half again as wide as half its size. */
    private static final WidgetLayoutPlan.Measure MEASURE =
        (text, role, numeric, sizeDp) -> text.length() * sizeDp * 0.55f;

    private static final float[] FONT_SCALES = {1.0f, 1.3f, 2.0f};

    private static final String DATE = "Wed, Oct 21";
    private static final String SKY = "";

    private static Item event(@NonNull String text) {
        return new Item(Kind.EVENT_SOON, text, GlanceLayoutPlanner.GLYPH_EVENT, Ink.STRONG, null);
    }

    private static Content full() {
        return new Content(DATE, "21°", SKY, Ink.SUN, event("Design review · 15:00 in 40 min"));
    }

    private static List<Content> contents() {
        Item battery = new Item(Kind.BATTERY_LOW, "Battery 18% · Plug in soon",
            GlanceLayoutPlanner.GLYPH_BATTERY_LOW, Ink.CAUTION, null);
        return Arrays.asList(
            full(),
            new Content(DATE, "", "", Ink.CLOUD, event("Design review · 15:00 in 40 min")),
            new Content(DATE, "21°", SKY, Ink.SUN, null),
            new Content(DATE, "", "", Ink.CLOUD, null),
            new Content(DATE, "21°", "", Ink.CLOUD, battery),
            new Content("Wednesday, 21 October", "-12°", SKY, Ink.MOON,
                event("Quarterly planning with the whole design and research group · 15:00 in 1 h 20 min")));
    }

    /** The rooms each span is drawn at: its design, and Android's minimum for its cells. */
    private static float[][] rooms(@NonNull BuiltinWidgetSpan span) {
        return new float[][] {{span.widthDp, span.heightDp},
            {Math.max(span.minWidthDp, 64), Math.max(span.minHeightDp, 56)}};
    }

    @Test
    public void everyPlanKeepsItsPartsApartInsideTheInsetAndOverTheTextFloor() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] room : rooms(span)) {
                for (float scale : FONT_SCALES) {
                    for (Content content : contents()) {
                        WidgetLayoutPlan plan = GlanceLayoutPlanner.plan(span, room[0], room[1],
                            scale, content, MEASURE);
                        String where = span + " " + room[0] + "x" + room[1] + " @" + scale + " "
                            + plan.parts;
                        assertTrue("the date always shows: " + where,
                            plan.has(GlanceLayoutPlanner.DATE));
                        List<Part> drawn = new ArrayList<>();
                        for (Part part : plan.parts) {
                            if (part.type == WidgetLayoutPlan.Type.TOUCH) continue;
                            drawn.add(part);
                            assertTrue(part + " inside the inset: " + where,
                                plan.inset.contains(part.box));
                            if (part.type == WidgetLayoutPlan.Type.TEXT) {
                                assertTrue(part + " at 11 sp or more: " + where,
                                    part.sizeDp / scale >= WidgetTokens.TEXT_FLOOR_SP - 0.01f);
                            }
                        }
                        for (int i = 0; i < drawn.size(); i++) {
                            for (int j = i + 1; j < drawn.size(); j++) {
                                assertFalse(drawn.get(i) + " overlaps " + drawn.get(j) + ": "
                                    + where, drawn.get(i).box.overlaps(drawn.get(j).box));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void eachPartShowsOnlyWhenItHasSomethingToSay() {
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.FOUR_BY_ONE,
                BuiltinWidgetSpan.TWO_BY_ONE}) {
            WidgetLayoutPlan all = plan(span, full());
            for (String id : new String[] {GlanceLayoutPlanner.DATE,
                    GlanceLayoutPlanner.WEATHER_GLYPH, GlanceLayoutPlanner.TEMP,
                    GlanceLayoutPlanner.ITEM_GLYPH, GlanceLayoutPlanner.ITEM_LINE + 0}) {
                assertTrue(span + " shows " + id + ": " + all.parts, all.has(id));
            }

            WidgetLayoutPlan noWeather = plan(span, new Content(DATE, "", "", Ink.CLOUD,
                event("Design review · 15:00")));
            assertFalse(noWeather.has(GlanceLayoutPlanner.TEMP));
            assertFalse(noWeather.has(GlanceLayoutPlanner.WEATHER_GLYPH));
            assertFalse("no weather, no separator", noWeather.has(GlanceLayoutPlanner.DOT));
            assertTrue(noWeather.has(GlanceLayoutPlanner.ITEM_LINE + 0));

            WidgetLayoutPlan noSky = plan(span, new Content(DATE, "21°", "", Ink.CLOUD, null));
            assertTrue(noSky.has(GlanceLayoutPlanner.TEMP));
            assertFalse(noSky.has(GlanceLayoutPlanner.WEATHER_GLYPH));

            WidgetLayoutPlan noItem = plan(span, new Content(DATE, "21°", SKY, Ink.SUN, null));
            assertFalse("nothing to say: line two is hidden",
                noItem.has(GlanceLayoutPlanner.ITEM_GLYPH));
            assertTrue(noItem.partsStartingWith(GlanceLayoutPlanner.ITEM_LINE).isEmpty());
            assertTrue(noItem.has(GlanceLayoutPlanner.TEMP));
        }
    }

    @Test
    public void itNeverShowsTheTime() {
        Set<String> known = new HashSet<>(Arrays.asList(GlanceLayoutPlanner.DATE,
            GlanceLayoutPlanner.DOT, GlanceLayoutPlanner.WEATHER_GLYPH, GlanceLayoutPlanner.TEMP,
            GlanceLayoutPlanner.ITEM_GLYPH, GlanceLayoutPlanner.ITEM_LINE + 0,
            GlanceLayoutPlanner.ITEM_LINE + 1, GlanceLayoutPlanner.TOUCH));
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (Content content : contents()) {
                for (Part part : plan(span, content).parts) {
                    assertTrue("no part beyond the date, weather and item: " + part,
                        known.contains(part.id));
                }
            }
            for (Part part : plan(span, new Content(DATE, "21°", SKY, Ink.SUN, null)).parts) {
                assertFalse("no clock time: " + part, part.text.matches(".*\\d{1,2}:\\d{2}.*"));
            }
        }
    }

    @Test
    public void lineTwoIsOneLineAtFourByOneAndWrapsToTwoAtTwoByOne() {
        Content content = new Content(DATE, "21°", SKY, Ink.SUN,
            event("Quarterly planning with the whole design and research group · 15:00 in 1 h 20 min"));
        WidgetLayoutPlan wide = plan(BuiltinWidgetSpan.FOUR_BY_ONE, content);
        assertEquals(1, wide.partsStartingWith(GlanceLayoutPlanner.ITEM_LINE).size());
        WidgetLayoutPlan narrow = plan(BuiltinWidgetSpan.TWO_BY_ONE, content);
        assertEquals("at most two lines", 2,
            narrow.partsStartingWith(GlanceLayoutPlanner.ITEM_LINE).size());
        WidgetLayoutPlan shortItem = plan(BuiltinWidgetSpan.TWO_BY_ONE,
            new Content(DATE, "21°", SKY, Ink.SUN, event("Alarm 07:00")));
        assertEquals("a short item keeps one", 1,
            shortItem.partsStartingWith(GlanceLayoutPlanner.ITEM_LINE).size());
    }

    @Test
    public void theDateIsTwentyTwoSpRegularAndLineTwoIsBody() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_ONE, full());
        Part date = plan.part(GlanceLayoutPlanner.DATE);
        assertNotNull(date);
        assertEquals(GlanceLayoutPlanner.DATE_SP, date.sizeDp, 0.01f);
        assertFalse("regular, not emphasised", date.role.emphasised);
        Part temp = plan.part(GlanceLayoutPlanner.TEMP);
        assertNotNull(temp);
        assertEquals("the temperature rides line one", date.sizeDp, temp.sizeDp, 0.01f);
        assertTrue(temp.numeric);
        Part line = plan.part(GlanceLayoutPlanner.ITEM_LINE + 0);
        assertNotNull(line);
        assertEquals(WidgetTokens.Role.BODY, line.role);
        assertEquals(14f, line.sizeDp, 0.01f);
        assertTrue("line two sits under line one", line.box.top >= date.box.bottom());
        assertEquals("Glance adds nothing past its 4×1", BuiltinWidgetSpan.FOUR_BY_ONE,
            plan.maxSpan);
        assertSame(BuiltinWidgetKind.GLANCE, plan.kind);
    }

    @Test
    public void theBatteryItemIsDrawnInTheCautionInk() {
        Item battery = GlanceLayoutPlanner.pick(NOW, END_OF_DAY, Collections.emptyList(), 18,
            false, 0L, WORDS);
        assertNotNull(battery);
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_ONE,
            new Content(DATE, "21°", SKY, Ink.SUN, battery));
        assertEquals(Ink.CAUTION, plan.part(GlanceLayoutPlanner.ITEM_LINE + 0).ink);
        assertEquals(Ink.CAUTION, plan.part(GlanceLayoutPlanner.ITEM_GLYPH).ink);
        assertEquals("Battery 18% · Plug in soon", plan.part(GlanceLayoutPlanner.ITEM_LINE + 0).text);
    }

    @NonNull private static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span,
                                                  @NonNull Content content) {
        return GlanceLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f, content, MEASURE);
    }

    // ----- line two's priority ----------------------------------------------------------------

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    /** 2026-10-21 12:00 UTC. */
    private static final long NOW = 1_792_584_000_000L;
    private static final long END_OF_DAY = NOW + 12L * HOUR;

    private static final GlanceLayoutPlanner.Words WORDS = new GlanceLayoutPlanner.Words() {
        @NonNull @Override public String time(long epochMs) {
            long minutes = Math.floorMod(epochMs / MINUTE, 24L * 60L);
            return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        }
        @NonNull @Override public String untitled() { return "(No title)"; }
        @NonNull @Override public String eventSoon(@NonNull String title, @NonNull String time,
                                                   long minutesAway) {
            return title + " · " + time + " in " + minutesAway + " min";
        }
        @NonNull @Override public String eventToday(@NonNull String title, @NonNull String time) {
            return title + " · " + time;
        }
        @NonNull @Override public String alarm(@NonNull String time) { return "Alarm " + time; }
        @NonNull @Override public String batteryLow(int percent) {
            return "Battery " + percent + "% · Plug in soon";
        }
    };

    private static CalendarEvent at(long startMs, @NonNull String title) {
        return CalendarEvent.of(startMs, startMs, startMs + HOUR, false, title, "", "", 0, ZONE);
    }

    private static CalendarEvent allDay(@NonNull String title) {
        long midnight = NOW - 12L * HOUR;
        return CalendarEvent.of(1L, midnight, midnight + 24L * HOUR, true, title, "", "", 0, ZONE);
    }

    @Nullable private static Item pick(@NonNull List<CalendarEvent> events, int battery,
                                       boolean charging, long alarm) {
        return GlanceLayoutPlanner.pick(NOW, END_OF_DAY, events, battery, charging, alarm, WORDS);
    }

    @Test
    public void anEventWithinTwoHoursComesFirst() {
        List<CalendarEvent> events = Arrays.asList(at(NOW + 40L * MINUTE, "Design review"),
            at(NOW + 5L * HOUR, "Dinner"));
        Item item = pick(events, 12, false, NOW + HOUR);
        assertNotNull(item);
        assertEquals("ahead of a low battery and an alarm", Kind.EVENT_SOON, item.kind);
        assertEquals("Design review · 12:40 in 40 min", item.text);
        assertEquals(GlanceLayoutPlanner.GLYPH_EVENT, item.glyph);
        assertSame(events.get(0), item.event);
        Item edge = pick(Collections.singletonList(at(NOW + 2L * HOUR, "Edge")), 50, false, 0L);
        assertEquals("two hours is still soon", Kind.EVENT_SOON, edge.kind);
    }

    @Test
    public void aLowBatteryNotChargingComesSecond() {
        List<CalendarEvent> later = Collections.singletonList(at(NOW + 3L * HOUR, "Dinner"));
        Item item = pick(later, 18, false, NOW + HOUR);
        assertNotNull(item);
        assertEquals("ahead of an alarm and the next event", Kind.BATTERY_LOW, item.kind);
        assertEquals(Ink.CAUTION, item.ink);
        assertEquals("Battery 18% · Plug in soon", item.text);
        assertEquals("charging: no warning", Kind.ALARM, pick(later, 18, true, NOW + HOUR).kind);
        assertEquals("20% is not low", Kind.ALARM, pick(later, 20, false, NOW + HOUR).kind);
        assertEquals("unknown is not low", Kind.ALARM, pick(later, -1, false, NOW + HOUR).kind);
    }

    @Test
    public void anAlarmWithinTwelveHoursComesThird() {
        List<CalendarEvent> later = Collections.singletonList(at(NOW + 3L * HOUR, "Dinner"));
        Item item = pick(later, 80, false, NOW + 11L * HOUR);
        assertNotNull(item);
        assertEquals("ahead of the next event", Kind.ALARM, item.kind);
        assertEquals("Alarm 23:00", item.text);
        assertEquals(GlanceLayoutPlanner.GLYPH_ALARM, item.glyph);
        assertEquals("thirteen hours off: the event instead", Kind.EVENT_TODAY,
            pick(later, 80, false, NOW + 13L * HOUR).kind);
        assertEquals("a past alarm is none", Kind.EVENT_TODAY,
            pick(later, 80, false, NOW - HOUR).kind);
    }

    @Test
    public void theNextEventTodayComesLastAndNothingHidesTheLine() {
        Item item = pick(Arrays.asList(at(NOW + 6L * HOUR, "Dinner"),
            at(NOW + 3L * HOUR, "")), 80, false, 0L);
        assertNotNull(item);
        assertEquals(Kind.EVENT_TODAY, item.kind);
        assertEquals("the earliest, untitled named", "(No title) · 15:00", item.text);
        assertNull("tomorrow's event is not today's",
            pick(Collections.singletonList(at(END_OF_DAY + HOUR, "Tomorrow")), 80, false, 0L));
        assertNull("an event already begun and an all-day one are not items",
            pick(Arrays.asList(at(NOW - 10L * MINUTE, "Running"), allDay("Holiday")), 80, false,
                0L));
        assertNull("nothing at all", pick(Collections.emptyList(), 80, false, 0L));
    }

    // ----- Bare -------------------------------------------------------------------------------

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** Any widget that asks to be Bare: the path is BuiltinWidgetView's, not Glance's. */
    private static final class Probe extends BuiltinWidgetView {
        final boolean bare;

        Probe(@NonNull Context context, @NonNull BuiltinWidgetServices services,
              @NonNull BuiltinWidgetStyle style, boolean bare) {
            super(context, BuiltinWidgetKind.WEATHER, services, style);
            this.bare = bare;
        }

        @Override protected boolean asksBare() { return bare; }

        @Override protected void onBuild(@NonNull FrameLayout frame,
                                         @NonNull BuiltinWidgetSpan span,
                                         @NonNull BuiltinWidgetUi ui) { }

        int groundAlpha() { return cardAlpha(); }
        int ground() { return groundSurface(); }
    }

    /** A page that hands out the Look. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        @Nullable PaneSurfaceStyle glass;
        boolean onGlass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return onGlass; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) { }
    }

    /** The Look on the wallpaper, with the widget band answering for {@link #wallpaper}. */
    private static final class InkLook implements PaneSurfaceStyle, WidgetInkSource {
        @NonNull ChromeInk.Polarity polarity = ChromeInk.Polarity.PALE_INK;
        int wallpaper = 0xFF101114;
        int tint = 0x4DE1E7F2;
        @NonNull private final Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);

        @NonNull @Override public ChromeInk.Polarity widgetPolarity() { return polarity; }
        @Override public boolean widgetFrameReady() { return true; }
        @NonNull @Override public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                               int veilColor, int textInk,
                                                               int graphicInk) {
            return OnGlass.resolveWidget(wallpaper, body, textInk, OnGlass.TARGET_BODY_TEXT,
                graphicInk, OnGlass.TARGET_LARGE_TEXT, veilColor);
        }
        @Override public int widgetWallpaper(@NonNull Rect rootRect) { return wallpaper; }

        @Override public boolean isPaneGlassActive() { return true; }
        @Override public Bitmap paneGlassBlurFrame() { return frame; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
        @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return tint; }
        @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneCornerRadiusDp() {
            return TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
        }
        @Override public int paneGapDp() { return 0; }
    }

    private Context context;
    private BuiltinWidgetServices services;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        services = new BuiltinWidgetServices(context, HOST);
    }

    private BuiltinWidgetStyle dayStyle() {
        return BuiltinWidgetStyle.resolve(new android.view.ContextThemeWrapper(context,
                com.google.android.material.R.style.Theme_Material3_Light),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
    }

    /** {@code view} live on a page fed {@code look}, the page's Widgets on glass on. */
    private static void live(@NonNull Activity activity, @NonNull InkLook look,
                             @NonNull BuiltinWidgetView... views) {
        Page page = new Page(activity);
        page.glass = look;
        page.onGlass = true;
        for (BuiltinWidgetView view : views) {
            view.bind(null);
            page.addView(view, new FrameLayout.LayoutParams(400, 200));
        }
        activity.setContentView(page);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        for (BuiltinWidgetView view : views) view.onGlassStyle(look);
    }

    @Nullable private static PaneGlassBackdropView slabOf(@NonNull BuiltinWidgetView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            if (view.getChildAt(i) instanceof PaneGlassBackdropView)
                return (PaneGlassBackdropView) view.getChildAt(i);
        }
        return null;
    }

    @NonNull private static View cardOf(@NonNull BuiltinWidgetView view) {
        return view.findViewById(android.R.id.background);
    }

    @Test
    public void glanceIsAlwaysBareAndOnThePicker() {
        assertTrue(new GlanceWidgetView(context, services, dayStyle()).asksBare());
        assertSame(BuiltinWidgetKind.GLANCE, BuiltinWidgetKind.fromId("glance"));
        assertTrue("the picker lists it",
            new LinkedHashSet<>(Arrays.asList(BuiltinWidgetKind.values()))
                .contains(BuiltinWidgetKind.GLANCE));
        assertEquals("placed at 4×1", BuiltinWidgetSpan.FOUR_BY_ONE,
            BuiltinWidgetHost.defaultSpan(BuiltinWidgetKind.GLANCE));
        assertTrue(BuiltinWidgetFactory.create(context, BuiltinWidgetKind.GLANCE, services,
            dayStyle()) instanceof GlanceWidgetView);
    }

    /** Over a dark wallpaper the page's pale ink reads as it is: no card, no rim, no slab, no dim. */
    @Test
    public void bareDrawsNoCardRimSlabOrDimWhereTheInkReads() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        InkLook look = new InkLook();
        Probe view = new Probe(activity, services, dayStyle(), true);
        live(activity, look, view);
        assertTrue(view.isBare());
        assertTrue(view.style().bare);
        assertFalse("no glass for a Bare widget, whatever the page says", view.isGlassOn());
        PaneGlassBackdropView slab = slabOf(view);
        assertTrue("no slab", slab == null || slab.getVisibility() == View.GONE);
        assertNull("no card fill and no rim", cardOf(view).getBackground());
        assertEquals("nothing laid under the ink", 0, view.groundAlpha());
        assertEquals("the ink stands on the wallpaper itself", look.wallpaper, view.ground());
        BuiltinWidgetStyle pale = dayStyle().onInk(true);
        assertEquals("the page's own ink, unmoved", pale.onSurfaceVariant,
            view.style().onSurfaceVariant);
        assertTrue(OnGlass.ratio(view.style().onSurface, look.wallpaper) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(view.style().primary, look.wallpaper)
            >= OnGlass.TARGET_LARGE_TEXT);
    }

    /**
     * Over a mid-grey wallpaper the page's pale ink is short, and Bare lays nothing behind itself
     * (decided 2026-10-27): no dim, no card. The ink moves on its side until it reads instead.
     */
    @Test
    public void bareLaysNoDimAndMovesItsInkInstead() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        InkLook look = new InkLook();
        look.wallpaper = 0xFF606060;
        Probe view = new Probe(activity, services, dayStyle(), true);
        live(activity, look, view);
        BuiltinWidgetStyle pale = dayStyle().onInk(true);
        assertTrue("the premise: the pale ink is short on the bare wallpaper",
            OnGlass.ratio(pale.onSurfaceVariant, look.wallpaper) < OnGlass.TARGET_BODY_TEXT);
        assertTrue(view.isBare());
        assertNull("nothing is laid behind a Bare widget", cardOf(view).getBackground());
        assertEquals("no dim", 0, view.groundAlpha());
        assertTrue(OnGlass.ratio(view.style().onSurfaceVariant, look.wallpaper)
            >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(view.style().onSurface, look.wallpaper) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(view.style().primary, look.wallpaper) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue("the caution ink reads as text",
            OnGlass.ratio(view.style().warm, look.wallpaper) >= OnGlass.TARGET_BODY_TEXT);
    }

    /** Where 35% is not enough the ink moves instead, and still holds 4.5:1 and 3:1. */
    @Test
    public void bareMovesTheInkRatherThanDimPastThirtyFivePercent() {
        BuiltinWidgetStyle pale = dayStyle().onInk(true);
        for (int wallpaper : new int[] {0xFFF5F5F5, 0xFFFFFFFF, 0xFFB0B0B0, 0xFF8090A0}) {
            OnGlass.Resolution answer = OnGlass.resolveBareWidget(wallpaper,
                pale.onSurfaceVariant, OnGlass.TARGET_BODY_TEXT, pale.primary,
                OnGlass.TARGET_LARGE_TEXT, pale.card, true);
            assertTrue("never past 35%: " + answer,
                Color.alpha(answer.veil) <= OnGlass.BARE_MAX_DIM_255);
            int text = OnGlass.bareInk(answer, pale.onSurfaceVariant, OnGlass.TARGET_BODY_TEXT, true);
            int graphic = OnGlass.bareInk(answer, pale.primary, OnGlass.TARGET_LARGE_TEXT, true);
            assertTrue(Integer.toHexString(wallpaper) + " text " + answer,
                OnGlass.ratio(text, answer.surface) >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(Integer.toHexString(wallpaper) + " graphics " + answer,
                OnGlass.ratio(graphic, answer.surface) >= OnGlass.TARGET_LARGE_TEXT);
        }
    }

    /** A dark theme over a light wallpaper: every Bare ink still holds its ratio on the wallpaper. */
    @Test
    public void bareHoldsContrastForADarkThemeOnALightWallpaper() {
        BuiltinWidgetStyle night = BuiltinWidgetStyle.resolve(new android.view.ContextThemeWrapper(
                context, com.google.android.material.R.style.Theme_Material3_Dark),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        for (int wallpaper : new int[] {0xFFF4EFE4, 0xFFECECEC, 0xFFFFFFFF, 0xFFD0D4DC}) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            InkLook look = new InkLook();
            look.polarity = ChromeInk.Polarity.DARK_INK;
            look.wallpaper = wallpaper;
            Probe view = new Probe(activity, services, night, true);
            live(activity, look, view);
            String at = Integer.toHexString(wallpaper) + " ";
            assertTrue(at + "bare", view.isBare());
            assertTrue(at + "onSurface " + OnGlass.ratio(view.style().onSurface, view.ground()),
                OnGlass.ratio(view.style().onSurface, view.ground()) >= OnGlass.TARGET_BODY_TEXT);
            // The sample is a mean: the text may stand on a part 4% darker than it.
            int local = OnGlass.opaque(OnGlass.composite(OnGlass.withAlpha(0xFF000000, 10),
                view.ground()));
            assertTrue(at + "onSurface on the darker part",
                OnGlass.ratio(view.style().onSurface, local) >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(at + "onSurfaceVariant on the darker part",
                OnGlass.ratio(view.style().onSurfaceVariant, local) >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(at + "onSurfaceVariant",
                OnGlass.ratio(view.style().onSurfaceVariant, view.ground())
                    >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(at + "primary", OnGlass.ratio(view.style().primary, view.ground())
                >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue(at + "warm", OnGlass.ratio(view.style().warm, view.ground())
                >= OnGlass.TARGET_BODY_TEXT);
        }
    }

    /** Bare is the widget's ask only: the Look's Clear stop leaves a card a card. */
    @Test
    public void theLooksClearStopDoesNotTurnBareOn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        InkLook clear = new InkLook();
        clear.tint = 0x1AE1E7F2;
        Probe card = new Probe(activity, services, dayStyle(), false);
        live(activity, clear, card);
        assertFalse(card.isBare());
        assertFalse(card.style().bare);
        assertTrue("on the Look's glass", card.isGlassOn());
        assertNotNull("the card's own fill stays", cardOf(card).getBackground());
        assertTrue(card.groundAlpha() > Color.alpha(clear.tint));
    }

    /** No wallpaper to measure, or the system asks for contrast: the solid card. */
    @Test
    public void bareFallsBackToTheCardWhereLegibilityAsks() {
        assertTrue(BuiltinWidgetGlass.bareWorn(true, false, true, false, false));
        assertFalse("not asked", BuiltinWidgetGlass.bareWorn(false, false, true, false, false));
        assertFalse("off the wallpaper",
            BuiltinWidgetGlass.bareWorn(true, false, false, false, false));
        assertTrue("a picker card", BuiltinWidgetGlass.bareWorn(true, true, false, false, false));
        assertFalse("outline text", BuiltinWidgetGlass.bareWorn(true, false, true, true, false));
        assertFalse("high contrast", BuiltinWidgetGlass.bareWorn(true, false, true, false, true));

        Probe unplaced = new Probe(context, services, dayStyle(), true);
        unplaced.bind(new Bundle());
        assertFalse("no page, no wallpaper: the card", unplaced.isBare());
        assertNotNull(cardOf(unplaced).getBackground());
    }
}
