package com.termux.app.launcher.widget.builtin;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.app.testing.ReportRenders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Pictures of the Clock's Flip face as glass tiles, for a person to look at: each span, 12- and
 * 24-hour, Seconds on and off, dark and light, on the Look's glass over a painted wallpaper, at
 * 12:43:32 PM. The 4×2 also at the design's own 366×154 dp, with and without the date. Written to
 * {@code app/build/reports/clock-tiles}; runs only with {@code -Preports.renders}.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockTilesPicturesTest {
    private static final float DENSITY = 2.625f;
    /** Room around the widget, so the wallpaper shows on every side. */
    private static final int MARGIN_DP = 16;

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public Typeface monoTypeface() { return null; }
    };

    /** A page that hands out the Look's glass over its wallpaper. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        @Nullable PaneSurfaceStyle glass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return true; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) { }
    }

    @NonNull private static PaneSurfaceStyle glass(@NonNull Bitmap frame, int tint, int cornersDp) {
        return new PaneSurfaceStyle() {
            @Override public boolean isPaneGlassActive() { return true; }
            @Override public Bitmap paneGlassBlurFrame() { return frame; }
            @NonNull @Override public Rect paneGlassBlurFrameRect() {
                return new Rect(0, 0, frame.getWidth(), frame.getHeight());
            }
            @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
            @Override public int paneGlassTintColor() { return tint; }
            @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
            @Override public int paneGlassGrainStrength() { return 0; }
            @Override public float paneGlassCornerRadiusPx() { return 0f; }
            @Override public int paneCornerRadiusDp() { return cornersDp; }
            @Override public int paneGapDp() { return 0; }
        };
    }

    @Test public void writeTheTiles() throws IOException {
        ReportRenders.assumeRequested();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;
        File dir = new File("build/reports/clock-tiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (boolean dark : new boolean[] {true, false}) {
            for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
                for (boolean is24 : new boolean[] {false, true}) {
                    for (boolean seconds : new boolean[] {true, false}) {
                        write(activity, dir, span, span.widthDp, span.heightDp, dark, is24,
                            seconds, true);
                    }
                }
            }
            for (boolean date : new boolean[] {false, true}) {
                write(activity, dir, BuiltinWidgetSpan.FOUR_BY_TWO, 366, 154, dark, false, true,
                    date);
            }
        }
    }

    private void write(@NonNull Activity activity, @NonNull File dir,
                       @NonNull BuiltinWidgetSpan span, int widthDp, int heightDp, boolean dark,
                       boolean is24, boolean seconds, boolean date) throws IOException {
        int margin = Math.round(MARGIN_DP * DENSITY);
        int w = Math.round(widthDp * DENSITY), h = Math.round(heightDp * DENSITY);
        int pageW = w + 2 * margin, pageH = h + 2 * margin;
        Bitmap wallpaper = wallpaper(pageW, pageH);
        Bitmap frame = blurred(wallpaper);
        Page page = new Page(activity);
        page.glass = glass(frame, dark ? 0x40100808 : 0x59FFFFFF, 26);
        page.setBackground(new android.graphics.drawable.BitmapDrawable(activity.getResources(),
            wallpaper));
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(
            new android.view.ContextThemeWrapper(activity, dark
                ? com.google.android.material.R.style.Theme_Material3_Dark
                : com.google.android.material.R.style.Theme_Material3_Light),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        BuiltinWidgetServices services = new BuiltinWidgetServices(activity, HOST);
        ClockWidgetView view = new ClockWidgetView(activity, services, style);
        Bundle config = new Bundle();
        config.putString(ClockWidgetSettings.KEY_FACE, ClockFace.FLIP.key);
        config.putString(ClockWidgetSettings.KEY_SECONDS, seconds ? "on" : "off");
        config.putString(ClockWidgetSettings.KEY_24_HOUR, is24 ? "on" : "off");
        config.putString(ClockWidgetSettings.KEY_SHOW_DATE, date ? "on" : "off");
        config.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        view.setCells(span.columns, span.rows);
        view.bind(config);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(w, h);
        params.leftMargin = margin;
        params.topMargin = margin;
        page.addView(view, params);
        activity.setContentView(page);
        page.measure(View.MeasureSpec.makeMeasureSpec(pageW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(pageH, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, pageW, pageH);
        view.onGlassStyle(page.glass);
        page.measure(View.MeasureSpec.makeMeasureSpec(pageW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(pageH, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, pageW, pageH);
        ClockFaceView face = view.faceView();
        if (face != null) {
            face.setTime(ZonedDateTime.now().withHour(12).withMinute(43).withSecond(32));
            // Past the fold the new time started.
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2));
        }
        Bitmap out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888);
        page.draw(new Canvas(out));
        String name = (widthDp == span.widthDp ? span.name().toLowerCase(java.util.Locale.ROOT)
            : "design-" + widthDp + "x" + heightDp + (date ? "-date" : ""))
            + (is24 ? "-24h" : "-12h") + (seconds ? "-seconds" : "") + (dark ? "-dark" : "-light")
            + ".png";
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name))) {
            out.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        services.destroy();
    }

    /** A wallpaper with something to blur: a warm-to-cool ground under bright bars. */
    @NonNull private static Bitmap wallpaper(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, w, h, new int[] {0xFF2A1712, 0xFF1F3FA8,
            0xFFF08A2C, 0xFF140A07}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        int[] bars = {0xFFC22B2B, 0xFF2E6BE0, 0xFFF08A2C, 0xFF3B7BF0, 0xFFB2245E};
        int bar = Math.max(1, w / 14);
        for (int i = 0; i * bar * 2 < w; i++) {
            paint.setColor(bars[i % bars.length]);
            canvas.drawRect(i * bar * 2, h / 5f, i * bar * 2 + bar, h * 4f / 5f, paint);
        }
        return bitmap;
    }

    /** A cheap blur for the glass's frame: down to a sixteenth and back up, filtered. */
    @NonNull private static Bitmap blurred(@NonNull Bitmap source) {
        Bitmap small = Bitmap.createScaledBitmap(source, Math.max(1, source.getWidth() / 16),
            Math.max(1, source.getHeight() / 16), true);
        return Bitmap.createScaledBitmap(small, source.getWidth(), source.getHeight(), true);
    }
}
