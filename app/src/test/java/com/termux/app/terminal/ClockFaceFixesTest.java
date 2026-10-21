package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.chrome.OnGlass;
import com.termux.app.statusbar.TopPaneClockForm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The face fixes of #111, held at the renderer the status bar and the Clock widget share: what
 * each face actually paints, recorded off the canvas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClockFaceFixesTest {

    /** 2026-07-23 04:05:06 UTC, a Thursday. "6" is drawn by the seconds alone. */
    private static final long WALL = 1_784_779_506_000L;
    private static final String[] FACES = {"flip", "lcd", "minimal", "minimal_medium", "led",
        "tape", "slab"};
    /** A purple glass by night and by day, and the bar's ink on each: pale, then dark. */
    private static final int DARK_GLASS = 0xFF3B2763;
    private static final int PALE_INK = 0xFFEDE4FF;
    private static final int PALE_GLASS = 0xFFE6D9F5;
    private static final int DARK_INK = 0xFF2A1450;
    /** A dark theme whose primary is a mid violet: on the purple glass it is the old LCD's ink. */
    private static final ClockFaceRenderer.Palette PALETTE = new ClockFaceRenderer.Palette(
        0xFFE6E1E5, 0xFF958DA5, 0xFF6750A4, 0xFF1C1B1F, 0xFF2B2930, 0xFF36343B, 0xFF48464F,
        0xFF49454F, 0xFFCAC4D0);

    private Context mContext;
    private float mDensity;
    private TimeZone mSavedZone;
    private Locale mSavedLocale;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mDensity = mContext.getResources().getDisplayMetrics().density;
        mSavedZone = TimeZone.getDefault();
        mSavedLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Locale.setDefault(Locale.UK);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mSavedZone);
        Locale.setDefault(mSavedLocale);
    }

    @Test
    public void secondsAndDatePaintAtTheLabelRoleOnEveryFaceAndForm() {
        String date = ClockFaceRenderer.dateText(WALL, TimeZone.getTimeZone("UTC"), Locale.UK);
        for (String face : FACES) {
            for (boolean shared : new boolean[] {false, true}) {
                for (TopPaneClockForm form : TopPaneClockForm.values()) {
                    Recorder full = draw(face, form, shared ? 190f : 340f, shared, 0, 0);
                    String label = face + " " + form + (shared ? " beside media" : " alone");
                    float floor = form == TopPaneClockForm.MONO_CHIP ? 11f : 12f;
                    float seconds = form == TopPaneClockForm.MONO_CHIP
                        ? full.sizeOf("06") : full.sizeOf("6");
                    assertTrue(label + " seconds at " + seconds / sp(1f) + "sp",
                        seconds >= sp(floor) - .01f);
                    if (form == TopPaneClockForm.COMPACT) continue; // compact draws no date
                    float dated = full.sizeOf(date);
                    assertTrue(label + " date at " + dated / sp(1f) + "sp",
                        dated >= sp(floor) - .01f);
                }
            }
        }
    }

    @Test
    public void theDateIsSentenceCaseInTheLocalesOwnOrder() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals("Thu 23 Jul", ClockFaceRenderer.dateText(WALL, utc, Locale.UK));
        // The JVM's ICU may lack the locale's skeleton data (it hands "EEEdMMM" back); on a
        // device US English reads "Thu, Jul 23". Either way: the parts, apart, sentence case.
        String american = ClockFaceRenderer.dateText(WALL, utc, Locale.US);
        assertTrue(american, american.startsWith("Thu") && american.contains("23")
            && american.contains("Jul") && !american.contains("Thu23")
            && !american.contains("JUL"));
        String french = ClockFaceRenderer.dateText(WALL, utc, Locale.FRANCE);
        assertTrue(french, Character.isUpperCase(french.charAt(0)));
    }

    @Test
    public void noStrokeSegmentOrCellPaintsUnderOneDp() {
        for (String face : FACES) {
            for (boolean shared : new boolean[] {false, true}) {
                for (TopPaneClockForm form : TopPaneClockForm.values()) {
                    // The text faces' compact form draws no shapes at all; nothing to hold there.
                    Recorder recorder = draw(face, form, shared ? 190f : 340f, shared, 0, 0);
                    for (Shape shape : recorder.shapes) {
                        assertTrue(face + " " + form + " " + shape.kind + " at "
                                + shape.thickness / mDensity + "dp",
                            shape.thickness >= mDensity - .01f);
                    }
                }
            }
        }
    }

    @Test
    public void lcdSegmentsAndLedCellsScaleWithTheBand() {
        for (String face : new String[] {"lcd", "led"}) {
            ClockFaceRenderer small = renderer(face);
            ClockFaceRenderer large = renderer(face);
            float smallScale = small.fitPlan(400f * mDensity, 40f * mDensity, false)[0];
            float largeScale = large.fitPlan(400f * mDensity, 60f * mDensity, false)[0];
            assertTrue(face, largeScale > smallScale * 1.2f);
            float smallStroke = thinnest(drawFull(small, 400f, 40f), face);
            float largeStroke = thinnest(drawFull(large, 400f, 60f), face);
            assertEquals(face + " segment or cell follows the scale", largeScale / smallScale,
                largeStroke / smallStroke, .02f);
        }
    }

    @Test
    public void lcdLitSegmentsHoldThreeToOneOnTheMeasuredGlass_andTheUnlitOnesAreFaint() {
        for (boolean darkGlass : new boolean[] {true, false}) {
            int glass = darkGlass ? DARK_GLASS : PALE_GLASS;
            Recorder recorder = draw("lcd", TopPaneClockForm.FULL, 340f, false,
                glass, darkGlass ? PALE_INK : DARK_INK);
            int lit = 0;
            int ghosts = 0;
            for (Shape shape : recorder.shapes) {
                if (!"roundRect".equals(shape.kind) || shape.stroke) continue;
                int alpha = Color.alpha(shape.color);
                if (alpha == 255) {
                    lit++;
                    assertTrue("lit segment " + Integer.toHexString(shape.color) + " on "
                            + Integer.toHexString(glass),
                        OnGlass.ratio(shape.color, glass) >= OnGlass.TARGET_LARGE_TEXT - .01);
                    assertEquals("on " + (darkGlass ? "dark" : "pale") + " glass the segments go "
                            + (darkGlass ? "pale" : "dark"), darkGlass,
                        ColorUtils.calculateLuminance(shape.color)
                            > ColorUtils.calculateLuminance(glass));
                } else if (alpha <= Math.round(255f * ClockFaceRenderer.LCD_GHOST_ALPHA)) {
                    ghosts++;
                }
            }
            assertTrue("04:05 lights segments", lit > 0);
            assertTrue("the unlit segments are still there, faintly", ghosts > 0);
        }
        // Fainter than the .08 they were.
        assertTrue(ClockFaceRenderer.LCD_GHOST_ALPHA < .08f / 2f);
    }

    @Test
    public void digitsHoldThreeToOneAndLabelsFourAndAHalfOnTheMeasuredGlass() {
        String date = ClockFaceRenderer.dateText(WALL, TimeZone.getTimeZone("UTC"), Locale.UK);
        for (String face : FACES) {
            for (boolean darkGlass : new boolean[] {true, false}) {
                int glass = darkGlass ? DARK_GLASS : PALE_GLASS;
                Recorder recorder = draw(face, TopPaneClockForm.FULL, 340f, false, glass,
                    darkGlass ? PALE_INK : DARK_INK);
                String label = face + " on " + (darkGlass ? "dark" : "pale") + " glass ";
                assertTrue(label + "date", ratio(recorder.colorOf(date), glass)
                    >= OnGlass.TARGET_BODY_TEXT - .01);
                assertTrue(label + "seconds", ratio(recorder.colorOf("6"), glass)
                    >= OnGlass.TARGET_BODY_TEXT - .01);
                if (face.equals("minimal") || face.equals("minimal_medium")
                    || face.equals("slab") || face.equals("tape")) {
                    assertTrue(label + "digits", ratio(recorder.colorOf("4"), glass)
                        >= OnGlass.TARGET_LARGE_TEXT - .01);
                }
            }
        }
    }

    @Test
    public void flipDrawsOneSeamPerCardAndNoHingeClips() {
        ClockFaceRenderer renderer = renderer("flip");
        Recorder recorder = drawFull(renderer, 340f, 49f);
        float scale = renderer.fitPlan(340f * mDensity, 49f * mDensity, false)[0];
        float card = 26f * mDensity * scale;
        int seams = 0;
        for (Shape shape : recorder.shapes) {
            if ("rect".equals(shape.kind) && Math.abs(shape.width - card) < .5f
                && shape.thickness < 2f * mDensity) {
                seams++;
            }
        }
        assertEquals("one seam on each of the four cards", 4, seams);
        assertEquals("no hinge clips blitted over the seams", 0, recorder.bitmaps);
    }

    @Test
    public void minimalMediumIsMinimalInTheMediumWeight() {
        ClockFaceRenderer renderer = renderer("minimal");
        assertTrue(renderer.setStyle("minimal_medium"));
        assertEquals("minimal_medium", renderer.getStyle());
        assertFalse(renderer.setStyle("minimal_medium"));
        Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        assertSame(medium, drawFull(renderer, 340f, 49f).typefaceOf("4"));
        assertSame(thin, drawFull(renderer("minimal"), 340f, 49f).typefaceOf("4"));
    }

    @Test
    public void tapeSetsItsDateUnderTheTimeAndNoMinuteLabel() {
        String date = ClockFaceRenderer.dateText(WALL, TimeZone.getTimeZone("UTC"), Locale.UK);
        Recorder recorder = drawFull(renderer("tape"), 340f, 49f);
        Text time = recorder.text("4");
        Text dated = recorder.text(date);
        assertTrue("the date is under the time", dated.y > time.y);
        Text first = recorder.texts.get(0);
        assertEquals("the date starts where the time does", first.x, dated.x, 1f);
        for (Text text : recorder.texts) {
            assertFalse("no minute label: " + text.text, text.text.contains("MINUTE")
                || text.text.contains("%"));
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
            value, mContext.getResources().getDisplayMetrics());
    }

    private static double ratio(int color, int glass) {
        return OnGlass.ratio(ColorUtils.compositeColors(color, glass), glass);
    }

    private ClockFaceRenderer renderer(String face) {
        ClockFaceRenderer renderer = new ClockFaceRenderer(mContext.getResources());
        renderer.setPalette(PALETTE);
        renderer.setStyle(face);
        renderer.setLazyMode(true);
        renderer.updateTime(WALL, 1_000L);
        return renderer;
    }

    /** The face in the bar: a 65dp slot, its 49dp budget, alone or floored beside media. */
    private Recorder draw(String face, TopPaneClockForm form, float widthDp, boolean shared,
                          int glass, int ink) {
        ClockFaceRenderer renderer = renderer(face);
        if (glass != 0) renderer.setBandInk(glass, ink);
        renderer.setForm(form);
        int width = Math.round(widthDp * mDensity);
        int height = Math.round(65f * mDensity);
        renderer.setFitBudget(width, 49f * mDensity, shared);
        Recorder recorder = new Recorder(Bitmap.createBitmap(width, height,
            Bitmap.Config.ARGB_8888));
        renderer.draw(recorder, width, height, 2_000L);
        return recorder;
    }

    private Recorder drawFull(ClockFaceRenderer renderer, float widthDp, float budgetDp) {
        int width = Math.round(widthDp * mDensity);
        int height = Math.round((budgetDp + 16f) * mDensity);
        renderer.setFitBudget(width, budgetDp * mDensity, false);
        Recorder recorder = new Recorder(Bitmap.createBitmap(width, height,
            Bitmap.Config.ARGB_8888));
        renderer.draw(recorder, width, height, 2_000L);
        return recorder;
    }

    /** The thinnest lit LCD segment, or the LED's smallest dot, as painted. */
    private static float thinnest(Recorder recorder, String face) {
        float thinnest = Float.MAX_VALUE;
        for (Shape shape : recorder.shapes) {
            boolean counts = face.equals("lcd")
                ? "roundRect".equals(shape.kind) && !shape.stroke
                    && Color.alpha(shape.color) == 255
                : "circle".equals(shape.kind);
            if (counts) thinnest = Math.min(thinnest, shape.thickness);
        }
        return thinnest;
    }

    private static final class Text {
        final String text;
        final float size;
        final int color;
        final Typeface typeface;
        final float x;
        final float y;

        Text(String text, float size, int color, Typeface typeface, float x, float y) {
            this.text = text;
            this.size = size;
            this.color = color;
            this.typeface = typeface;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Shape {
        final String kind;
        final float thickness;
        final float width;
        final int color;
        final boolean stroke;

        Shape(String kind, float thickness, float width, int color, boolean stroke) {
            this.kind = kind;
            this.thickness = thickness;
            this.width = width;
            this.color = color;
            this.stroke = stroke;
        }
    }

    /** A canvas that notes every text and shape a face paints, in painted pixels. */
    private static final class Recorder extends Canvas {
        final List<Text> texts = new ArrayList<>();
        final List<Shape> shapes = new ArrayList<>();
        int bitmaps;
        private final Matrix mMatrix = new Matrix();
        private final float[] mValues = new float[9];
        private final RectF mMapped = new RectF();

        Recorder(Bitmap bitmap) {
            super(bitmap);
        }

        @SuppressWarnings("deprecation")
        private float scale() {
            getMatrix(mMatrix);
            mMatrix.getValues(mValues);
            return mValues[Matrix.MSCALE_Y];
        }

        @SuppressWarnings("deprecation")
        private void map(float left, float top, float right, float bottom) {
            getMatrix(mMatrix);
            mMapped.set(left, top, right, bottom);
            mMatrix.mapRect(mMapped);
        }

        @Override
        public void drawText(@NonNull String text, float x, float y, @NonNull Paint paint) {
            map(x, y, x, y);
            texts.add(new Text(text, paint.getTextSize() * scale(), paint.getColor(),
                paint.getTypeface(), mMapped.left, mMapped.top));
            super.drawText(text, x, y, paint);
        }

        @Override
        public void drawRect(float left, float top, float right, float bottom,
                             @NonNull Paint paint) {
            record("rect", left, top, right, bottom, paint);
            super.drawRect(left, top, right, bottom, paint);
        }

        @Override
        public void drawRoundRect(@NonNull RectF rect, float rx, float ry, @NonNull Paint paint) {
            record("roundRect", rect.left, rect.top, rect.right, rect.bottom, paint);
            super.drawRoundRect(rect, rx, ry, paint);
        }

        @Override
        public void drawCircle(float cx, float cy, float radius, @NonNull Paint paint) {
            shapes.add(new Shape("circle", 2f * radius * scale(), 2f * radius * scale(),
                paint.getColor(), false));
            super.drawCircle(cx, cy, radius, paint);
        }

        @Override
        public void drawBitmap(@NonNull Bitmap bitmap, float left, float top,
                               android.graphics.Paint paint) {
            bitmaps++;
            super.drawBitmap(bitmap, left, top, paint);
        }

        private void record(String kind, float left, float top, float right, float bottom,
                            Paint paint) {
            boolean stroke = paint.getStyle() == Paint.Style.STROKE;
            map(left, top, right, bottom);
            float thickness = stroke ? paint.getStrokeWidth() * scale()
                : Math.min(mMapped.width(), mMapped.height());
            shapes.add(new Shape(kind, thickness, mMapped.width(), paint.getColor(), stroke));
        }

        Text text(String wanted) {
            for (Text text : texts) {
                if (text.text.equals(wanted)) return text;
            }
            throw new AssertionError("never drew \"" + wanted + "\"");
        }

        float sizeOf(String wanted) {
            return text(wanted).size;
        }

        int colorOf(String wanted) {
            return text(wanted).color;
        }

        Typeface typefaceOf(String wanted) {
            return text(wanted).typeface;
        }
    }
}
