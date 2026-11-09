package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClockFaceRenderer;

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
 * The Flip Clock widget's meta column: the seconds with the period over them, sized like the
 * expanded status bar's at the widget's scale (the Label role grows with the face past scale 1),
 * the period at the bar's share of the seconds, and the whole column inside the face's box.
 */
@RunWith(RobolectricTestRunner.class)
// Native graphics: real text metrics and real pixels to probe.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockWidgetFlipMetaTest {
    private static final float DENSITY = 2.625f;
    /** The bar's period over its seconds, as a share of them (ClockFaceRenderer.PERIOD_SCALE). */
    private static final float PERIOD_SHARE = .64f;
    /** The FULL flip band's height, in dp. */
    private static final float FLIP_BAND_DP = 35.5f;
    /** Ink at least this opaque counts; the cards' shadow and antialiased fringes stay under it. */
    private static final int INK_ALPHA = 96;
    /** Room around the face's box, so ink that strays out of it is still caught. */
    private static final int MARGIN = 200;
    private static final long FRAME = 10_000L;
    /**
     * The card is laid out at this share of its span's size: a span's own size holds the flip at
     * about the bar's scale (1.1 at 2×1), where a held and a grown column barely differ.
     */
    private static final float LARGE = 1.5f;

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

    @Test public void theTwoByOnesMetaColumnIsTheBarsAtItsScale() {
        checkMetaColumn(BuiltinWidgetSpan.TWO_BY_ONE);
    }

    @Test public void theTwoByTwosMetaColumnIsTheBarsAtItsScale() {
        checkMetaColumn(BuiltinWidgetSpan.TWO_BY_TWO);
    }

    private void checkMetaColumn(@NonNull BuiltinWidgetSpan span) {
        Bundle config = new Bundle();
        config.putString(ClockWidgetSettings.KEY_FACE, ClockFace.FLIP.key);
        config.putString(ClockWidgetSettings.KEY_SECONDS, "on");
        config.putString(ClockWidgetSettings.KEY_24_HOUR, "off");
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        ClockFaceView faceView = laidOut(services, span, config).faceView();
        services.destroy();
        assertNotNull(span + ": face view", faceView);
        ClockFaceRenderer renderer = faceView.renderer();
        assertNotNull(span + ": renderer", renderer);
        int w = faceView.getWidth(), h = faceView.getHeight();
        assertTrue(span + ": a laid-out face", w > 0 && h > 0);
        assertTrue(span + ": keeps its seconds", renderer.isShowingSeconds());
        assertTrue(span + ": keeps its period", renderer.isShowingPeriod());
        renderer.setFitBudget(w, h, false);
        float scale = renderer.fittedScale(w, h);
        assertTrue(span + ": the face is drawn past the bar's size, at " + scale, scale > 1.2f);

        // The period alone: the face with and without it, at one scale (it is narrower than the
        // seconds, so it costs the column no width).
        Bitmap both = render(renderer, w, h);
        renderer.setPeriodShown(false);
        assertEquals(span + ": the period moves nothing", scale, renderer.fittedScale(w, h), 1e-4f);
        Bitmap seconds = render(renderer, w, h);
        renderer.setPeriodShown(true);
        Rect period = inkBounds(both, seconds, 0);
        assertTrue(span + ": a period is drawn", !period.isEmpty());
        // The seconds: the ink at and after the column's left edge, which the period starts at.
        Rect secs = inkBounds(seconds, null, period.left - 2);
        assertTrue(span + ": seconds are drawn", !secs.isEmpty());
        both.recycle();
        seconds.recycle();

        Rect box = new Rect(MARGIN, MARGIN, MARGIN + w, MARGIN + h);
        assertTrue(span + ": the period " + period + " stays in the box " + box,
            box.contains(period));
        assertTrue(span + ": the seconds " + secs + " stay in the box " + box, box.contains(secs));
        assertTrue(span + ": the period " + period + " stands over the seconds " + secs,
            period.bottom <= secs.top);

        // The seconds at the bar's Label size grown with the face: their share of the band is
        // the bar's own.
        String[] parts = String.valueOf(renderer.contentDescription()).split("[: ]");
        String ss = parts[2];
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(ClockFaceRenderer.labelRoleSp() * DENSITY);
        Rect bar = new Rect();
        paint.getTextBounds(ss, 0, ss.length(), bar);
        float barShare = bar.height() / (FLIP_BAND_DP * DENSITY);
        float share = secs.height() / renderer.fullBandHeightPx(h);
        assertEquals(span + ": the seconds' share of the band, " + share + ", is the bar's "
            + barShare, barShare, share, barShare * .08f);
        float periodShare = period.height() / (float) secs.height();
        assertEquals(span + ": the period at the bar's share of the seconds", PERIOD_SHARE,
            periodShare, .09f);
    }

    /** The face drawn by its renderer into a bitmap with {@link #MARGIN} of room on every side. */
    @NonNull private static Bitmap render(@NonNull ClockFaceRenderer renderer, int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w + 2 * MARGIN, h + 2 * MARGIN,
            Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(MARGIN, MARGIN);
        renderer.draw(canvas, w, h, FRAME);
        return bitmap;
    }

    /**
     * The bounds of the ink in {@code bitmap} from column {@code fromX} on, counting only pixels
     * that differ from {@code minus} where one is given.
     */
    @NonNull private static Rect inkBounds(@NonNull Bitmap bitmap, @Nullable Bitmap minus,
                                           int fromX) {
        Rect bounds = new Rect();
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = Math.max(0, fromX); x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y);
                if (minus != null) {
                    int other = minus.getPixel(x, y);
                    if (Math.abs(Color.alpha(pixel) - Color.alpha(other)) < INK_ALPHA) continue;
                } else if (Color.alpha(pixel) < INK_ALPHA) {
                    continue;
                }
                if (bounds.isEmpty()) bounds.set(x, y, x + 1, y + 1);
                else bounds.union(x, y);
            }
        }
        return bounds;
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
        int w = (int) Math.ceil(span.widthDp * LARGE * DENSITY);
        int h = (int) Math.ceil(span.heightDp * LARGE * DENSITY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        return view;
    }
}
