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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.LauncherWidgetRepository;
import com.termux.app.launcher.widget.WidgetCellRect;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.terminal.ClockFaceRenderer;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Clock widget (#112): old ids open as it with the matching face and settings, a new one copies
 * the bar's face and 12/24-hour setting once, every face fits its card at every span (no overlap,
 * the 11 sp floor, the 1 dp stroke floor), the 1×1 station dial sets the four cardinal numerals,
 * and Minimal's Weight row shows only for Minimal, in the gear and in the Look's picker.
 */
@RunWith(RobolectricTestRunner.class)
// Native graphics: real text metrics, so the faces measure as they draw.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockWidgetTest {
    private static final float DENSITY = 2.625f;
    private static final float[] FONT_SCALES = {1f, 1.3f, 2f};

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
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

    @Test public void theOldClocksOpenAsTheClockWithTheirFace() {
        assertEquals(BuiltinWidgetKind.CLOCK, BuiltinWidgetKind.fromId("clock"));
        assertEquals(BuiltinWidgetKind.CLOCK, BuiltinWidgetKind.fromId("clock.analog"));
        assertEquals(BuiltinWidgetKind.CLOCK, BuiltinWidgetKind.fromId("clock.digital"));
        assertNull(BuiltinWidgetKind.fromId("clock.sundial"));
        assertTrue(BuiltinWidgetAliases.isAlias("clock.analog"));
        assertFalse(BuiltinWidgetAliases.isAlias("clock"));

        // The picker lists the kinds: one Clock, and neither old clock.
        int clocks = 0;
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            assertFalse(kind.id, kind.id.equals("clock.analog") || kind.id.equals("clock.digital"));
            if (kind.id.startsWith("clock")) clocks++;
        }
        assertEquals("one Clock in the picker", 1, clocks);

        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetHost host = new BuiltinWidgetHost(context, services,
            () -> BuiltinWidgetStyle.Numerals.SANS);
        try {
            LauncherWidgetRecord analog = LauncherWidgetRecord.builtin(-1, "clock.analog",
                new WidgetCellRect(0, 0, 1, 1), 0, null);
            View view = host.viewFor(analog);
            assertTrue("an analog clock opens as the Clock", view instanceof ClockWidgetView);
            Bundle settings = ((ClockWidgetView) view).config();
            assertEquals(ClockFace.ANALOG, ClockWidgetSettings.face(settings));
            assertTrue("the analog clock keeps its second hand",
                ClockWidgetSettings.seconds(settings));
            assertEquals("its cities are kept for the time zones", "Europe/London",
                settings.getString(ClockWidgetSettings.KEY_ZONE1));
            assertEquals("the stored id never changes", "clock.analog", analog.builtinKind);

            LauncherWidgetRecord digital = LauncherWidgetRecord.builtin(-2, "clock.digital",
                new WidgetCellRect(0, 0, 2, 1), 0, null);
            ClockWidgetView digitalView = (ClockWidgetView) host.viewFor(digital);
            assertNotNull(digitalView);
            assertEquals(ClockFace.NUMERALS, ClockWidgetSettings.face(digitalView.config()));
            assertFalse(ClockWidgetSettings.seconds(digitalView.config()));
            assertNull("no 24-hour choice: it follows the phone, as it did",
                digitalView.config().getString(ClockWidgetSettings.KEY_24_HOUR));

            // What a record stores itself wins over what its old kind implied.
            Bundle own = new Bundle();
            own.putString(ClockWidgetSettings.KEY_FACE, ClockFace.LCD.key);
            own.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
            Bundle opened = host.settingsOf(LauncherWidgetRecord.builtin(-3, "clock.analog",
                new WidgetCellRect(0, 0, 1, 1), 0, own));
            assertEquals(ClockFace.LCD.key, opened.getString(ClockWidgetSettings.KEY_FACE));
            assertEquals(BuiltinWidgetGlass.ON, opened.getString(BuiltinWidgetGlass.CONFIG_KEY));
            assertEquals(ClockWidgetSettings.ON, opened.getString(ClockWidgetSettings.KEY_SECONDS));
        } finally {
            host.destroy();
        }
    }

    @Test public void anOldClockKeepsItsIdThroughTheStore() {
        Memory memory = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "clock.analog",
            new WidgetCellRect(0, 0, 1, 1), 0, null)));
        LauncherWidgetRecord reloaded = new LauncherWidgetRepository(memory).get(-1);
        assertNotNull("an old clock is not dropped as unknown", reloaded);
        assertEquals("clock.analog", reloaded.builtinKind);
        assertEquals(BuiltinWidgetKind.CLOCK, BuiltinWidgetKind.fromId(reloaded.builtinKind));
    }

    // ----- copy once ------------------------------------------------------------------------

    @Test public void aNewClockCopiesTheBarsFaceAndHourOnce() {
        Map<String, String> lcd12 = ClockWidgetSettings.initial("lcd", true);
        assertEquals(ClockFace.LCD.key, lcd12.get(ClockWidgetSettings.KEY_FACE));
        assertEquals(ClockWidgetSettings.OFF, lcd12.get(ClockWidgetSettings.KEY_24_HOUR));
        assertEquals("seconds start off at widget size", ClockWidgetSettings.OFF,
            lcd12.get(ClockWidgetSettings.KEY_SECONDS));
        Map<String, String> medium24 = ClockWidgetSettings.initial("minimal_medium", false);
        assertEquals(ClockFace.MINIMAL.key, medium24.get(ClockWidgetSettings.KEY_FACE));
        assertEquals(ClockWidgetSettings.WEIGHT_MEDIUM, medium24.get(ClockWidgetSettings.KEY_WEIGHT));
        assertEquals(ClockWidgetSettings.ON, medium24.get(ClockWidgetSettings.KEY_24_HOUR));
        assertEquals("an unknown bar style is the Flip the bar draws", ClockFace.FLIP.key,
            ClockWidgetSettings.initial("sundial", false).get(ClockWidgetSettings.KEY_FACE));

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        Assume.assumeNotNull(preferences);
        String barStyle = preferences.getTopPaneClockStyle();
        boolean barAmPm = preferences.isTopPaneClockAmPmEnabled();
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetHost host = new BuiltinWidgetHost(context, services,
            () -> BuiltinWidgetStyle.Numerals.SANS);
        try {
            preferences.setTopPaneClockStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE);
            preferences.setTopPaneClockAmPmEnabled(true);
            Bundle placed = host.initialConfig("clock");
            assertNotNull(placed);
            assertNull("only the Clock is placed with settings", host.initialConfig("weather"));
            assertEquals(ClockFace.TAPE.key, placed.getString(ClockWidgetSettings.KEY_FACE));
            assertEquals(ClockWidgetSettings.OFF, placed.getString(ClockWidgetSettings.KEY_24_HOUR));

            // The bar moves on: the placed Clock keeps what it copied.
            preferences.setTopPaneClockStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
            preferences.setTopPaneClockAmPmEnabled(false);
            ClockWidgetView clock = (ClockWidgetView) host.viewFor(LauncherWidgetRecord.builtin(
                -1, "clock", new WidgetCellRect(0, 0, 2, 1), 0, placed));
            assertNotNull(clock);
            assertEquals(ClockFace.TAPE, clock.face());
            assertFalse(ClockWidgetSettings.is24Hour(clock.config(), true));

            // The Clock moves on: the bar keeps its own.
            Bundle changed = clock.config();
            changed.putString(ClockWidgetSettings.KEY_FACE, ClockFace.LED.key);
            clock.bind(changed);
            assertEquals(ClockFace.LED, clock.face());
            assertEquals(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
                preferences.getTopPaneClockStyle());
            assertFalse(preferences.isTopPaneClockAmPmEnabled());
        } finally {
            preferences.setTopPaneClockStyle(barStyle);
            preferences.setTopPaneClockAmPmEnabled(barAmPm);
            host.destroy();
        }
    }

    // ----- fit --------------------------------------------------------------------------------

    @Test public void everyFaceFitsItsCardAtEverySpan() {
        List<String> failures = new ArrayList<>();
        for (float scale : FONT_SCALES) {
            fontScale(scale);
            for (ClockFace face : ClockFace.values()) {
                for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
                    for (int[] size : sizesFor(span)) {
                        for (boolean seconds : new boolean[] {false, true}) {
                            for (boolean is24 : new boolean[] {true, false}) {
                                checkFit(face, span, size, scale, seconds, is24, failures);
                            }
                        }
                    }
                }
            }
        }
        assertTrue("faces that do not fit:\n" + String.join("\n", failures), failures.isEmpty());
    }

    private void checkFit(@NonNull ClockFace face, @NonNull BuiltinWidgetSpan span,
                          @NonNull int[] size, float scale, boolean seconds, boolean is24,
                          @NonNull List<String> failures) {
        Bundle config = new Bundle();
        config.putString(ClockWidgetSettings.KEY_FACE, face.key);
        config.putString(ClockWidgetSettings.KEY_SECONDS, seconds ? "on" : "off");
        config.putString(ClockWidgetSettings.KEY_24_HOUR, is24 ? "on" : "off");
        String where = face + " " + span + " " + size[0] + "x" + size[1] + "dp font " + scale
            + (seconds ? " seconds" : "") + (is24 ? " 24h" : " 12h");
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        ClockWidgetView view = laidOut(services, span, size[0], size[1], config);
        services.destroy();
        if (view.span() != span) {
            failures.add(where + ": drew " + view.span());
            return;
        }
        WidgetLayoutPlan plan = view.plan();
        ClockFaceView faceView = view.faceView();
        if (plan == null || faceView == null) {
            failures.add(where + ": no plan or no face");
            return;
        }
        WidgetLayoutPlan.Part facePart = plan.part(ClockLayoutPlanner.FACE);
        if (facePart == null) {
            failures.add(where + ": no face part");
            return;
        }
        List<WidgetLayoutPlan.Part> drawn = new ArrayList<>();
        for (WidgetLayoutPlan.Part part : plan.parts) {
            if (part.type == WidgetLayoutPlan.Type.TOUCH) continue;
            drawn.add(part);
            if (!plan.inset.contains(part.box)) {
                failures.add(where + ": " + part + " leaves the inset " + plan.inset);
            }
            if (part.type == WidgetLayoutPlan.Type.TEXT && part.sizeDp
                < WidgetTokens.TEXT_FLOOR_SP * scale - 0.01f) {
                failures.add(where + ": " + part.id + " at " + part.sizeDp + "dp");
            }
        }
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                if (drawn.get(i).box.overlaps(drawn.get(j).box)) {
                    failures.add(where + ": " + drawn.get(i) + " overlaps " + drawn.get(j));
                }
            }
        }
        // The face is laid out in its part's box, and its ink stays inside it.
        float w = faceView.getWidth(), h = faceView.getHeight();
        if (Math.abs(w - facePart.box.width * DENSITY) > 1.5f
            || Math.abs(h - facePart.box.height * DENSITY) > 1.5f) {
            failures.add(where + ": face view " + w + "x" + h + " is not its box " + facePart.box);
        }
        if (span == BuiltinWidgetSpan.ONE_BY_ONE && plan.has(ClockLayoutPlanner.DATE)) {
            failures.add(where + ": a 1×1 shows the date");
        }
        float spPx = DENSITY * scale;
        switch (face) {
            case ANALOG: {
                StationDial.Geometry dial = faceView.dialGeometry();
                checkDial(dial, where, spPx, span == BuiltinWidgetSpan.ONE_BY_ONE, failures);
                if (Math.abs(w - h) > 1.5f) failures.add(where + ": the dial is not round");
                break;
            }
            case NUMERALS: {
                float[] sizes = faceView.numeralsSizesPx();
                if (sizes[2] > w + 0.5f) failures.add(where + ": numerals run " + sizes[2] + "px");
                if (sizes[3] > h + 0.5f) failures.add(where + ": numerals stand " + sizes[3] + "px");
                if ((seconds || !is24) && sizes[1] < WidgetTokens.TEXT_FLOOR_SP * spPx - 0.5f) {
                    failures.add(where + ": seconds at " + sizes[1] / spPx + "sp");
                }
                break;
            }
            default: {
                ClockFaceRenderer renderer = faceView.renderer();
                if (renderer == null) {
                    failures.add(where + ": no renderer");
                    break;
                }
                // Seconds asked for may be shed where they cannot fit at the Label role; never
                // shown where they were not asked for.
                if (!seconds && renderer.isShowingSeconds()) failures.add(where + ": seconds");
                if (seconds && scale == 1f && span == BuiltinWidgetSpan.TWO_BY_ONE
                    && size[0] == span.widthDp && !renderer.isShowingSeconds()) {
                    failures.add(where + ": a 2×1 sheds its seconds");
                }
                if (renderer.isUsingAmPm() == is24) failures.add(where + ": 12/24 hour");
                float painted = renderer.fittedWidthPx(w, h);
                float column = renderer.fittedColumnPx(w, h);
                if (painted > w + 0.5f) failures.add(where + ": runs " + painted + "px of " + w);
                if (column > h + 0.5f) failures.add(where + ": stands " + column + "px of " + h);
                if (renderer.fitCeiling() <= 1.35f) failures.add(where + ": the bar's ceiling");
                break;
            }
        }
    }

    private static void checkDial(@NonNull StationDial.Geometry dial, @NonNull String where,
                                  float spPx, boolean oneByOne, @NonNull List<String> failures) {
        float floor = StationDial.STROKE_FLOOR_DP * DENSITY - 0.01f;
        if (dial.minorWidth < floor || dial.majorWidth < floor || dial.secondWidth < floor
            || dial.minuteWidth < floor || dial.hourWidth < floor) {
            failures.add(where + ": a stroke under 1dp");
        }
        if (dial.numeralPx < WidgetTokens.TEXT_FLOOR_SP * spPx - 0.01f) {
            failures.add(where + ": numerals at " + dial.numeralPx / spPx + "sp");
        }
        if (oneByOne && !dial.cardinal) failures.add(where + ": a 1×1 sets all twelve");
        if (StationDial.crowded(dial.numerals)) failures.add(where + ": numerals crowd");
        float track = dial.trackInner();
        for (StationDial.Numeral numeral : dial.numerals) {
            // Along its own direction a numeral's box ends short of the five-minute bars.
            float centre = (float) Math.hypot(numeral.cx, numeral.cy);
            float outer = 2f * centre - numeral.innerRadius();
            if (outer > track + 0.5f) {
                failures.add(where + ": " + numeral.text + " runs into the track");
            }
            if (dial.hourLength >= numeral.innerRadius()) {
                failures.add(where + ": the hour hand reaches " + numeral.text);
            }
        }
    }

    // ----- the 1×1 dial -----------------------------------------------------------------------

    @Test public void theOneByOneDialSetsTheFourCardinalNumeralsAtTheFloor() {
        for (float scale : FONT_SCALES) {
            fontScale(scale);
            for (int[] card : new int[][] {{57, 57}, {88, 92}, {92, 120}, {132, 138}}) {
                Bundle analog = new Bundle();
                analog.putString(ClockWidgetSettings.KEY_FACE, ClockFace.ANALOG.key);
                BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
                ClockWidgetView view = laidOut(services, BuiltinWidgetSpan.ONE_BY_ONE, card[0],
                    card[1], analog);
                services.destroy();
                ClockFaceView face = view.faceView();
                assertNotNull(face);
                StationDial.Geometry dial = face.dialGeometry();
                String where = card[0] + "x" + card[1] + "dp at font " + scale;
                List<String> texts = new ArrayList<>();
                for (StationDial.Numeral numeral : dial.numerals) texts.add(numeral.text);
                assertTrue(where, dial.cardinal);
                if (card[0] >= 88) {
                    assertEquals(where, Arrays.asList("12", "3", "6", "9"), texts);
                } else {
                    // A dense grid's 57 dp cell at a large font has no room for four at 11 sp:
                    // it sets none rather than shrink or crowd them.
                    assertTrue(where + ": " + texts, texts.isEmpty()
                        || texts.equals(Arrays.asList("12", "3", "6", "9")));
                }
                assertFalse(where + ": crowded", StationDial.crowded(dial.numerals));
                assertTrue(where + ": at " + dial.numeralPx / (DENSITY * scale) + "sp",
                    dial.numeralPx >= 11f * DENSITY * scale - 0.01f);
                int side = Math.round(OneByOneFit.artSide(card[0], card[1],
                    OneByOneFit.INSET_DP) * DENSITY);
                assertEquals(where + ": the dial fills the card to the inset", side,
                    face.getWidth(), 2);
            }
        }
        // A roomy 2×2 sets all twelve, with the hour hand well inside them.
        fontScale(1f);
        StationDial.Geometry roomy = StationDial.layout(140f * DENSITY, DENSITY, DENSITY, false,
            measureOf(context));
        assertFalse(roomy.cardinal);
        assertEquals(12, roomy.numerals.size());
        assertTrue("the hour hand is shorter than the drawing's",
            roomy.hourLength < 137.5f / 250f * roomy.radius);
    }

    // ----- Minimal's weight -------------------------------------------------------------------

    @Test public void minimalsWeightRowShowsOnlyForMinimal() {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        Bundle config = new Bundle();
        config.putString(ClockWidgetSettings.KEY_FACE, ClockFace.SLAB.key);
        ClockWidgetView view = laidOut(services, BuiltinWidgetSpan.TWO_BY_ONE, 184, 92, config);
        services.destroy();
        Map<String, BuiltinWidgetView.ConfigField> fields = new HashMap<>();
        for (BuiltinWidgetView.ConfigField field : view.configFields()) fields.put(field.key, field);

        BuiltinWidgetView.ConfigField faces = fields.get(ClockWidgetSettings.KEY_FACE);
        assertNotNull(faces);
        assertEquals(BuiltinWidgetView.ConfigField.Type.CHOICE, faces.type);
        assertNotNull("the faces are a strip of live previews", faces.tiles);
        assertEquals(ClockFace.values().length, faces.options.size());
        assertEquals(ClockFace.SLAB.key, faces.fallback);

        BuiltinWidgetView.ConfigField weight = fields.get(ClockWidgetSettings.KEY_WEIGHT);
        assertNotNull(weight);
        Map<String, String> values = new HashMap<>();
        values.put(ClockWidgetSettings.KEY_FACE, ClockFace.SLAB.key);
        assertFalse("no Weight row for Slab", weight.shownFor(values));
        values.put(ClockWidgetSettings.KEY_FACE, ClockFace.MINIMAL.key);
        assertTrue("the Weight row for Minimal", weight.shownFor(values));
        assertEquals(2, weight.options.size());

        BuiltinWidgetView.ConfigField seconds = fields.get(ClockWidgetSettings.KEY_SECONDS);
        assertNotNull(seconds);
        assertEquals(BuiltinWidgetView.ConfigField.Type.SWITCH, seconds.type);
        assertEquals("seconds default off", BuiltinWidgetView.ConfigField.OFF, seconds.fallback);
        assertNotNull(fields.get(ClockWidgetSettings.KEY_24_HOUR));

        // Medium draws Minimal in its medium weight.
        Bundle medium = new Bundle();
        medium.putString(ClockWidgetSettings.KEY_FACE, ClockFace.MINIMAL.key);
        medium.putString(ClockWidgetSettings.KEY_WEIGHT, ClockWidgetSettings.WEIGHT_MEDIUM);
        services = new BuiltinWidgetServices(context, HOST);
        ClockWidgetView heavy = laidOut(services, BuiltinWidgetSpan.TWO_BY_ONE, 184, 92, medium);
        services.destroy();
        assertNotNull(heavy.faceView());
        assertNotNull(heavy.faceView().renderer());
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM,
            heavy.faceView().renderer().getStyle());

        // The Look's bar-clock picker: the same row, only while Minimal is the bar's face.
        assertTrue(Arrays.asList(SurfaceEditorController.clockDropdownRows("minimal"))
            .contains(SurfaceEditorController.CLOCK_ROW_WEIGHT));
        assertTrue(Arrays.asList(SurfaceEditorController.clockDropdownRows("minimal_medium"))
            .contains(SurfaceEditorController.CLOCK_ROW_WEIGHT));
        assertFalse(Arrays.asList(SurfaceEditorController.clockDropdownRows("slab"))
            .contains(SurfaceEditorController.CLOCK_ROW_WEIGHT));
        assertFalse("Minimal's medium weight is not a face row of its own",
            Arrays.asList(SurfaceEditorController.clockDropdownRows("minimal_medium"))
                .contains("minimal_medium"));
    }

    // ----- helpers --------------------------------------------------------------------------

    /** The sizes, in dp, a span is checked at: its minimum, its design and a roomy end. */
    @NonNull private static int[][] sizesFor(@NonNull BuiltinWidgetSpan span) {
        int minW = span.columns == 1 ? 57 : span.minWidthDp;
        int minH = span.columns == 1 ? 57 : span.minHeightDp;
        return new int[][] {
            {minW, minH}, {span.widthDp, span.heightDp},
            {Math.round(span.widthDp * 1.5f), Math.round(span.heightDp * 1.5f)}
        };
    }

    @NonNull private ClockWidgetView laidOut(@NonNull BuiltinWidgetServices services,
                                             @NonNull BuiltinWidgetSpan span, int widthDp,
                                             int heightDp, @Nullable Bundle config) {
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        ClockWidgetView view = new ClockWidgetView(context, services, style);
        view.setPreview(true);
        view.setCells(span.columns, span.rows);
        view.bind(config);
        int w = (int) Math.ceil(widthDp * DENSITY);
        int h = (int) Math.ceil(heightDp * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        return view;
    }

    @NonNull private static StationDial.TextMeasure measureOf(@NonNull Context context) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTypeface(ClockTypefaces.libronBold(context));
        return new StationDial.TextMeasure() {
            @Override public float widthPx(@NonNull String text, float sizePx) {
                paint.setTextSize(sizePx);
                return paint.measureText(text);
            }
            @Override public float figureHeightPx(float sizePx) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                paint.setTextSize(100f);
                paint.getTextBounds("0123456789", 0, 10, bounds);
                return bounds.height() / 100f * sizePx;
            }
        };
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
