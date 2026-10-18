package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * What a frame's border is made of. A lone pane wears the shared rim, so none of its pixels may
 * be the Material active colour; the focused pane of a split wears exactly that colour; a pane
 * asking for the user wears the attention colour. Under Docked the insert's line is the
 * opening's: the theme's outline role at the divider's strength, round the insert's corners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaneRimBorderTest {

    private static final int SIZE = 200;
    private static final float RADIUS = 24f;

    private static Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return activity;
    }

    private static Bitmap draw(FrameLayout frame) {
        Drawable border = frame.getForeground();
        assertNotNull(border);
        border.setBounds(0, 0, SIZE, SIZE);
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        border.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** Whether any drawn pixel carries this colour's RGB. */
    private static boolean hasColour(Bitmap bitmap, int colour) {
        int rgb = colour & 0xFFFFFF;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = bitmap.getPixel(x, y);
                if ((p >>> 24) > 0 && (p & 0xFFFFFF) == rgb) return true;
            }
        }
        return false;
    }

    @Test
    public void aLonePaneFrameNoLongerUsesTheActiveColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        for (boolean glass : new boolean[] {false, true}) {
            FrameLayout frame = new FrameLayout(activity);
            new PaneRim().apply(frame, glass, RADIUS,
                PaneBorderStyle.decide(1, true, false, palette), null);
            assertFalse("glass=" + glass, hasColour(draw(frame), palette.focus));
        }
    }

    @Test
    public void theFocusedPaneOfASplitWearsTheActiveColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        // Plain only: the lit glass rim shades its tint, so its pixels are not exact.
        FrameLayout frame = new FrameLayout(activity);
        new PaneRim().apply(frame, false, RADIUS,
            PaneBorderStyle.decide(2, true, false, palette), null);
        assertTrue(hasColour(draw(frame), palette.focus));
    }

    @Test
    public void aPaneAskingForAttentionWearsTheAttentionColour() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        FrameLayout frame = new FrameLayout(activity);
        new PaneRim().apply(frame, false, RADIUS,
            PaneBorderStyle.decide(1, true, true, palette), null);
        assertTrue(hasColour(draw(frame), palette.attention));
        assertFalse(palette.attention == palette.focus);
    }

    /** A colour no theme role resolves to, standing in for the glass's gradient rim. */
    private static final int GLASS_RIM = 0xFFFF00FF;

    /** The line's expected colour: the theme's own outline role at the divider's alpha. */
    private static int outlineLine(Activity activity) {
        return MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOutline, Color.MAGENTA);
    }

    private static void assertIsTheLine(Bitmap bitmap, Activity activity, String where) {
        int outline = outlineLine(activity);
        // The middle of the top edge, well clear of the arcs: the line's own pixel.
        int p = bitmap.getPixel(SIZE / 2, 0);
        assertEquals(where + ": the divider's strength", PaneRim.lineColour(activity) >>> 24,
            Color.alpha(p), 2);
        assertEquals(where + ": red", Color.red(outline), Color.red(p), 3);
        assertEquals(where + ": green", Color.green(outline), Color.green(p), 3);
        assertEquals(where + ": blue", Color.blue(outline), Color.blue(p), 3);
        // The screen-side corner pixel lies outside the arc: the line follows the corner.
        assertEquals(where + ": the corner is round", 0, Color.alpha(bitmap.getPixel(0, 0)));
        assertFalse(where + ": no glass rim", hasColour(bitmap, GLASS_RIM));
    }

    @Test
    public void theDockedInsertWearsTheOpeningLineInTheOutlineRole() {
        Activity activity = activity();
        assertTrue("an outline, not white", (outlineLine(activity) & 0xFFFFFF) != 0xFFFFFF);
        for (boolean glass : new boolean[] {false, true}) {
            FrameLayout frame = new FrameLayout(activity);
            new PaneRim().apply(frame, glass, RADIUS, PaneBorderStyle.lone(), new Style(true));
            assertIsTheLine(draw(frame), activity, "glass=" + glass);
        }
    }

    @Test
    public void theLoneHostLineIsTheSameLine() {
        Activity activity = activity();
        Drawable line = PaneRim.openingLine(activity, RADIUS);
        line.setBounds(0, 0, SIZE, SIZE);
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        line.draw(new Canvas(bitmap));
        assertIsTheLine(bitmap, activity, "host");
    }

    @Test
    public void theLineResolvesPerTheme() {
        int nightOutline;
        RuntimeEnvironment.setQualifiers("+night");
        try {
            Activity night = activity();
            nightOutline = outlineLine(night);
            FrameLayout frame = new FrameLayout(night);
            new PaneRim().apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(true));
            assertIsTheLine(draw(frame), night, "night");
        } finally {
            RuntimeEnvironment.setQualifiers("+notnight");
        }
        assertTrue("light and dark resolve their own outline",
            outlineLine(activity()) != nightOutline);
    }

    @Test
    public void floatingCardsKeepTheGlassRim() {
        Activity activity = activity();
        FrameLayout frame = new FrameLayout(activity);
        new PaneRim().apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(false));
        assertTrue(hasColour(draw(frame), GLASS_RIM));
    }

    @Test
    public void aStyleFlipAtTheSameCornersChangesTheLine() {
        Activity activity = activity();
        FrameLayout frame = new FrameLayout(activity);
        PaneRim rim = new PaneRim();
        rim.apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(true));
        Drawable docked = frame.getForeground();
        rim.apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(false));
        assertTrue("Floating brings the glass rim back", hasColour(draw(frame), GLASS_RIM));
        assertNotSame(docked, frame.getForeground());
        rim.apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(true));
        assertFalse("and Docked takes it away again", hasColour(draw(frame), GLASS_RIM));
    }

    @Test
    public void theStyleKeyTellsTheTwoStylesApart() {
        assertFalse(PaneStyleKey.of(new Style(true)).equals(PaneStyleKey.of(new Style(false))));
        assertEquals(PaneStyleKey.of(new Style(true)), PaneStyleKey.of(new Style(true)));
    }

    /** A pane's slab as the border sees it, its program's edge switched by the test. */
    private static final class FakeSlab extends android.view.View implements PaneRim.SlabEdge {
        boolean edge;
        @Nullable Runnable told;

        FakeSlab(@NonNull android.content.Context context, boolean edge) {
            super(context);
            this.edge = edge;
        }

        @Override public boolean drawsEdge() { return edge; }
        @Override public void setOnEdgeChanged(@Nullable Runnable onEdgeChanged) { told = onEdgeChanged; }

        void flip(boolean drawsEdge) {
            edge = drawsEdge;
            if (told != null) told.run();
        }
    }

    /** A glass pane's frame holding {@code slab}, as the pane leaf nests it. */
    private static FrameLayout frameWith(@NonNull Activity activity, @Nullable FakeSlab slab) {
        FrameLayout frame = new FrameLayout(activity);
        if (slab != null) frame.addView(slab);
        return frame;
    }

    @Test
    public void aSlabWhoseProgramDrawsTheEdgeWearsNoRimOverIt() {
        // Floating panes wore the gradient rim over the program's gathered light: two edges. The
        // resting border asks the slab itself, so it yields only while that slab draws its edge.
        Activity activity = activity();
        FakeSlab slab = new FakeSlab(activity, true);
        FrameLayout frame = frameWith(activity, slab);
        new PaneRim().apply(frame, true, RADIUS, PaneBorderStyle.lone(), new Style(false));
        Bitmap drawn = draw(frame);
        assertFalse(hasColour(drawn, GLASS_RIM));
        assertFalse("not the fallback line either", hasColour(drawn, outlineLine(activity)));
        assertNotNull("the slab is listened to", slab.told);

        // The slab goes plain (its frame lost, the program refused, the look gone): the rim
        // comes back on the next draw, and the border is told to redraw.
        boolean[] invalidated = {false};
        frame.getForeground().setCallback(new Drawable.Callback() {
            @Override public void invalidateDrawable(@NonNull Drawable who) { invalidated[0] = true; }
            @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {}
            @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {}
        });
        slab.flip(false);
        assertTrue(invalidated[0]);
        assertTrue(hasColour(draw(frame), GLASS_RIM));
        slab.flip(true);
        assertFalse(hasColour(draw(frame), GLASS_RIM));

        // A slab that never drew its edge keeps the rim from the first draw.
        FrameLayout plainSlab = frameWith(activity, new FakeSlab(activity, false));
        new PaneRim().apply(plainSlab, true, RADIUS, PaneBorderStyle.lone(), new Style(false));
        assertTrue(hasColour(draw(plainSlab), GLASS_RIM));

        // A frame that is not glass has no program under it and keeps its line, slab or none.
        FrameLayout plain = frameWith(activity, new FakeSlab(activity, true));
        new PaneRim().apply(plain, false, RADIUS, PaneBorderStyle.lone(), new Style(false));
        assertTrue(hasColour(draw(plain), GLASS_RIM));
    }

    @Test
    public void theOpeningLineAndTheDockedEdgesNeverYieldToTheSlab() {
        Activity activity = activity();
        // The Docked insert's opening line is the insert's, not the glass's rim, and stays.
        FrameLayout opening = frameWith(activity, new FakeSlab(activity, true));
        new PaneRim().apply(opening, true, RADIUS, PaneBorderStyle.lone(), new Style(true));
        assertIsTheLine(draw(opening), activity, "opening line");
        // A docked pane (paneDocked) wears its divider edges, not the shared rim, whatever the
        // slab does.
        Style docked = new Style(false, true);
        FrameLayout dockedFrame = frameWith(activity, new FakeSlab(activity, true));
        new PaneRim().apply(dockedFrame, true, RADIUS, PaneBorderStyle.lone(), docked);
        assertTrue(dockedFrame.getForeground() instanceof PaneDividerEdges);
        assertEquals("the shared rim was never asked for", 0, docked.rimsBuilt);
    }

    @Test
    public void theFocusedPaneStillWearsTheActiveColourOverARefractingSlab() {
        Activity activity = activity();
        PaneBorderStyle.Palette palette = PaneRim.palette(activity);
        FakeSlab slab = new FakeSlab(activity, true);
        FrameLayout frame = frameWith(activity, slab);
        new PaneRim().apply(frame, true, RADIUS,
            PaneBorderStyle.decide(2, true, false, palette), new Style(false));
        assertTrue("focus draws its own border, never yielding to the slab",
            frame.getForeground() instanceof com.termux.app.GlassRimDrawable);
        assertTrue(PaneRim.slabEdgeOf(frame) == slab);
        assertTrue(PaneRim.slabEdgeOf(new FrameLayout(activity)) == null);
    }

    /** A glass style whose shared rim is {@link #GLASS_RIM}, Docked or Floating. */
    private static final class Style implements PaneSurfaceStyle {
        private final boolean mOpeningLine;
        private final boolean mDocked;
        /** How many times the shared rim was asked for. */
        int rimsBuilt;

        /** @param openingLine the Docked insert, whose edge is the opening's line */
        Style(boolean openingLine) {
            this(openingLine, false);
        }

        /** @param docked the retired divider style ({@link PaneSurfaceStyle#paneDocked}) */
        Style(boolean openingLine, boolean docked) {
            mOpeningLine = openingLine;
            mDocked = docked;
        }

        @Override public boolean paneOpeningLine() { return mOpeningLine; }
        @Override public boolean paneDocked() { return mDocked; }
        @Override @Nullable public Drawable paneRimDrawable(float radiusPx) {
            rimsBuilt++;
            GradientDrawable rim = new GradientDrawable();
            rim.setColor(Color.TRANSPARENT);
            rim.setCornerRadius(radiusPx);
            rim.setStroke(1, GLASS_RIM);
            return rim;
        }
        @Override public boolean isPaneGlassActive() { return true; }
        @Override @Nullable public Bitmap paneGlassBlurFrame() { return null; }
        @Override @NonNull public Rect paneGlassBlurFrameRect() { return new Rect(); }
        @Override @Nullable public android.graphics.ColorFilter paneGlassFrostFilter() {
            return null;
        }
        @Override public int paneGlassTintColor() { return 0x40000000; }
        @Override @Nullable public Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return RADIUS; }
        @Override public int paneGapDp() { return 4; }
    }

    @Test
    public void clearingTheBorderTakesTheForegroundOff() {
        Activity activity = activity();
        FrameLayout frame = new FrameLayout(activity);
        PaneRim rim = new PaneRim();
        rim.apply(frame, false, RADIUS, PaneBorderStyle.lone(), null);
        assertNotNull(frame.getForeground());
        rim.clear(frame);
        assertEquals(null, frame.getForeground());
    }
}
