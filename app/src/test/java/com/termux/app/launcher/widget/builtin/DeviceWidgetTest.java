package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.theme.SchemeTone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * The Device widget (#117): the old Battery and System widgets open as it and keep their stored
 * ids, the picker lists it once, every span lays out inside its card with the real fonts at
 * font scales 1.0, 1.3 and 2.0, the CPU is absent without privileged access, and every bar, the
 * ring's arc and the charts hold 3:1 against what they sit on, on the light and the dark card.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DeviceWidgetTest {
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

    @Test public void theBatteryAndSystemWidgetsOpenAsDevice() {
        assertEquals(BuiltinWidgetKind.DEVICE, BuiltinWidgetKind.fromId("device"));
        assertEquals(BuiltinWidgetKind.DEVICE, BuiltinWidgetKind.fromId("battery"));
        assertEquals(BuiltinWidgetKind.DEVICE, BuiltinWidgetKind.fromId("system"));
        assertTrue(BuiltinWidgetAliases.isAlias("battery"));
        assertTrue(BuiltinWidgetAliases.isAlias("system"));
        assertFalse(BuiltinWidgetAliases.isAlias("device"));

        int devices = 0;
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            assertFalse("the picker leaves out " + kind.id,
                kind.id.equals("battery") || kind.id.equals("system"));
            if (kind == BuiltinWidgetKind.DEVICE) devices++;
        }
        assertEquals("one Device in the picker", 1, devices);

        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetHost host = new BuiltinWidgetHost(context, services,
            () -> BuiltinWidgetStyle.Numerals.SANS);
        try {
            for (String id : new String[] {"battery", "system"}) {
                LauncherWidgetRecord record = LauncherWidgetRecord.builtin(-1, id,
                    new WidgetCellRect(0, 0, 2, 1), 0, null);
                View view = host.viewFor(record);
                assertTrue(id + " opens as Device", view instanceof DeviceWidgetView);
                assertEquals(BuiltinWidgetKind.DEVICE, ((DeviceWidgetView) view).kind());
                assertEquals("the stored id never changes", id, record.builtinKind);

                // Its own Glass choice comes along.
                Bundle own = new Bundle();
                own.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
                Bundle opened = host.settingsOf(LauncherWidgetRecord.builtin(-2, id,
                    new WidgetCellRect(0, 0, 2, 1), 0, own));
                assertEquals(BuiltinWidgetGlass.ON, opened.getString(BuiltinWidgetGlass.CONFIG_KEY));
            }
        } finally {
            host.destroy();
        }
    }

    @Test public void anOldBatteryOrSystemKeepsItsIdThroughTheStore() {
        for (String id : new String[] {"battery", "system"}) {
            Memory memory = new Memory();
            LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
            assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, id,
                new WidgetCellRect(0, 0, 2, 1), 0, null)));
            LauncherWidgetRecord reloaded = new LauncherWidgetRepository(memory).get(-1);
            assertNotNull(id + " is not dropped as unknown", reloaded);
            assertEquals(id, reloaded.builtinKind);
            assertEquals(BuiltinWidgetKind.DEVICE, BuiltinWidgetKind.fromId(reloaded.builtinKind));
        }
    }

    // ----- layout with the real fonts -------------------------------------------------------

    @Test public void everySpanFitsItsCardWithTheRealFonts() {
        for (float scale : FONT_SCALES) {
            fontScale(scale);
            for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
                String where = span + " at font " + scale;
                WidgetLayoutPlan plan = plan(laidOut(span, true), where);
                for (Part part : plan.parts) {
                    assertTrue(where + ": " + part + " inside " + plan.inset,
                        plan.inset.contains(part.box));
                    if (part.role == WidgetTokens.Role.HERO) {
                        assertTrue(where + ": hero " + part.sizeDp,
                            part.sizeDp <= WidgetTokens.heroMaxDp() + 0.01f);
                    }
                }
                for (int i = 0; i < plan.parts.size(); i++) {
                    for (int j = i + 1; j < plan.parts.size(); j++) {
                        Part a = plan.parts.get(i), b = plan.parts.get(j);
                        assertFalse(where + ": " + a + " overlaps " + b, a.box.overlaps(b.box));
                    }
                }
                if (span != BuiltinWidgetSpan.ONE_BY_ONE) {
                    assertTrue(where + ": the level shows", plan.has("hero"));
                }
            }
        }
    }

    @Test public void theTwoByOneAtFontTwoShowsTheLevelAndTheTimeToFull() {
        fontScale(2f);
        WidgetLayoutPlan plan = plan(laidOut(BuiltinWidgetSpan.TWO_BY_ONE, true), "2x1 font 2");
        assertEquals("84%", plan.part("hero").text);
        assertNotNull("a line under the level", plan.part("detail"));
        assertTrue(plan.part("detail").text.contains("1 h 10 m"));
    }

    @Test public void withoutAccessTheCpuIsAbsent() {
        // The privileged backend is never initialized here, so it reads as unavailable.
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            WidgetLayoutPlan plan = plan(laidOut(span, false), span + " without access");
            assertTrue(span + ": no CPU", plan.partsStartingWith("cpu").isEmpty());
            for (Part part : plan.parts) {
                assertFalse(span + ": " + part + " is never a dash", part.text.contains("—"));
            }
        }
        WidgetLayoutPlan preview = plan(laidOut(BuiltinWidgetSpan.FOUR_BY_TWO, true), "preview");
        assertTrue("the picker's card shows the CPU's history", preview.has("cpu"));
    }

    // ----- colour ---------------------------------------------------------------------------

    @Test public void barsTheRingAndTheChartsHoldThreeToOne() {
        BuiltinWidgetStyle light = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        for (BuiltinWidgetStyle s : new BuiltinWidgetStyle[] {light, light.onInk(true),
            light.glassVariant(context), light.glassVariant(context).onInk(true)}) {
            for (int accent : new int[] {s.primary, s.done, s.error, s.onSurfaceVariant, s.warm}) {
                for (int track : new int[] {s.containerHigh, s.card}) {
                    int fill = DeviceWidgetView.onTrack(accent, track);
                    double ratio = SchemeTone.contrastRatio(fill, track);
                    assertTrue(String.format("%08x on %08x (dark=%b): %.2f", fill, track, s.dark,
                        ratio), ratio >= DeviceWidgetView.GRAPHIC_CONTRAST - 0.01);
                }
            }
        }
    }

    @Test public void theRingWarnsOnlyWhenLowOrCharging() {
        BuiltinWidgetStyle s = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        assertEquals(s.done, DeviceWidgetView.ringAccent(state(60, true, false, true), s));
        assertEquals(s.done, DeviceWidgetView.ringAccent(state(100, false, true, true), s));
        assertEquals(s.error, DeviceWidgetView.ringAccent(state(12, false, false, false), s));
        assertEquals(s.onSurfaceVariant,
            DeviceWidgetView.ringAccent(state(60, false, false, false), s));
        assertEquals("plugged but not charging is not low", s.onSurfaceVariant,
            DeviceWidgetView.ringAccent(state(12, false, false, true), s));
    }

    // ----- helpers --------------------------------------------------------------------------

    @NonNull private static BatterySource.State state(int level, boolean charging, boolean full,
                                                      boolean plugged) {
        return new BatterySource.State(level, charging, full, plugged, 300, 4000, Double.NaN, -1);
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

    @NonNull private BuiltinWidgetView laidOut(@NonNull BuiltinWidgetSpan span, boolean preview) {
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        BuiltinWidgetView view = BuiltinWidgetFactory.create(context, BuiltinWidgetKind.DEVICE,
            services, style);
        view.setPreview(preview);
        view.setCells(span.columns, span.rows);
        view.bind(null);
        int w = Math.round(span.widthDp * DENSITY);
        int h = Math.round(span.heightDp * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        services.destroy();
        assertEquals(span + ": the size picks the span", span, view.span());
        return view;
    }

    @NonNull private static WidgetLayoutPlan plan(@NonNull View view, @NonNull String where) {
        WidgetPlanHost host = find(view);
        assertNotNull(where + ": a plan host", host);
        WidgetLayoutPlan plan = host.plan();
        assertNotNull(where + ": planned", plan);
        return plan;
    }

    @Nullable private static WidgetPlanHost find(@NonNull View view) {
        if (view instanceof WidgetPlanHost) return (WidgetPlanHost) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            WidgetPlanHost found = find(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static final class Memory implements LauncherWidgetRepository.Storage {
        String value;
        @Override public String read() { return value; }
        @Override public boolean write(@NonNull String next) { value = next; return true; }
    }
}
