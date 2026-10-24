package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.terminal.ClockFaceRenderer;
import com.termux.app.terminal.ClockFlipEndPinsTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * The Flip Clock widget's cards carry their end pins at both ends of the split line, and the
 * pins are all the renderer adds: probed at 2×1 and 2×2, 12- and 24-hour, with and without
 * seconds ({@link ClockFlipEndPinsTest#checkEndPins}).
 */
@RunWith(RobolectricTestRunner.class)
// Native graphics: real pixels to probe.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockWidgetFlipPinsTest {
    private static final float DENSITY = 2.625f;

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public Typeface monoTypeface() { return null; }
    };

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = 1f;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
        context.getResources().updateConfiguration(configuration, metrics);
        metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
    }

    @After public void resetTextScale() {
        com.termux.app.theme.TextScale.set(1f);
    }

    @Test public void theTwoByOnesCardsCarryTheirEndPins() {
        checkPins(BuiltinWidgetSpan.TWO_BY_ONE);
    }

    @Test public void theTwoByTwosCardsCarryTheirEndPins() {
        checkPins(BuiltinWidgetSpan.TWO_BY_TWO);
    }

    private void checkPins(@NonNull BuiltinWidgetSpan span) {
        for (boolean twelveHour : new boolean[] {true, false}) {
            for (boolean seconds : new boolean[] {true, false}) {
                Bundle config = new Bundle();
                config.putString(ClockWidgetSettings.KEY_FACE, ClockFace.FLIP.key);
                config.putString(ClockWidgetSettings.KEY_SECONDS, seconds ? "on" : "off");
                config.putString(ClockWidgetSettings.KEY_24_HOUR, twelveHour ? "off" : "on");
                String where = span + (twelveHour ? " 12h" : " 24h")
                    + (seconds ? " seconds" : "");
                BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
                ClockFaceView faceView = laidOut(services, span, config).faceView();
                services.destroy();
                assertNotNull(where + ": face view", faceView);
                ClockFaceRenderer renderer = faceView.renderer();
                assertNotNull(where + ": renderer", renderer);
                int w = faceView.getWidth(), h = faceView.getHeight();
                assertTrue(where + ": a laid-out face", w > 0 && h > 0);
                renderer.setFitBudget(w, h, false);
                ClockFlipEndPinsTest.checkEndPins(renderer, w, h, where);
            }
        }
    }

    @NonNull private ClockWidgetView laidOut(@NonNull BuiltinWidgetServices services,
                                             @NonNull BuiltinWidgetSpan span,
                                             @NonNull Bundle config) {
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        ClockWidgetView view = new ClockWidgetView(context, services, style);
        view.setPreview(true);
        view.setCells(span.columns, span.rows);
        view.bind(config);
        int w = (int) Math.ceil(span.widthDp * DENSITY);
        int h = (int) Math.ceil(span.heightDp * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        return view;
    }
}
