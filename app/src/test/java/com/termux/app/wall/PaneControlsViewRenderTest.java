package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.CornerTabGeometry;
import com.termux.app.chrome.CornerZones;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * What a pane's frame and its open tab actually rasterise to, together. The tab is part of the
 * frame's outline now: its outer edge <em>is</em> the frame's side, so the frame's own stroke is the
 * only line there — the pane once drew its border straight under the tab and the pair read as a
 * double line along the bottom of every tab.
 *
 * <p>Drawn for real, not reasoned about: the frame's stroke goes down first, exactly as the pane's
 * foreground drawable paints it, then the tab over it, and the pixels are read back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaneControlsViewRenderTest {

    private static final int WIDTH = 420;
    private static final int HEIGHT = 340;
    /** The wall behind the pane: nothing of the frame or the tab is this colour. */
    private static final int BEHIND = 0xFF101010;
    /** The pane's own glass tint, which the tab fills itself with. */
    private static final int TINT = 0x99204058;
    private static final float RADIUS = 12f;
    private static final float BORDER = 2f;
    /** How far a channel may drift before two colours are different colours. */
    private static final int TOLERANCE = 10;

    private static final RectF PANE = new RectF(30f, 25f, 390f, 315f);

    private android.content.Context mContext;
    private int mPrimary;

    @Before
    public void setUp() {
        mContext = new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight);
        mPrimary = MaterialColors.getColor(mContext, com.termux.shared.R.attr.termuxColorPrimary,
            androidx.core.content.ContextCompat.getColor(mContext, R.color.termux_primary));
    }

    @Test
    public void theFramesStrokeNeverRunsUnderTheTabAtAnyCorner() {
        for (int corner : new int[]{CornerZones.TOP_LEFT, CornerZones.TOP_RIGHT,
            CornerZones.BOTTOM_LEFT, CornerZones.BOTTOM_RIGHT}) {
            Bitmap bitmap = render(corner);
            RectF tab = new RectF();
            PaneControlsView view = view(corner);
            view.tabBounds(tab);
            RectF inner = new RectF();
            CornerTabGeometry.innerBounds(PANE, BORDER, inner);
            float arc = CornerTabGeometry.innerRadiusPx(RADIUS, BORDER);
            int expected = tabFill();

            // The body of the tab: from the frame's own edge to a hair short of the tab's own
            // line, clear of both arcs — the frame's, which rounds the tab's outer corner, and the
            // tab's own. A stroke left running under the tab would be somewhere in here.
            float r = CornerTabGeometry.tabCornerRadiusPx(arc, tab.height(), tab.width());
            boolean left = CornerZones.isLeft(corner);
            boolean top = CornerZones.isTop(corner);
            int fromX = Math.round(left ? inner.left + arc + r : tab.left + r + 2f);
            int toX = Math.round(left ? tab.right - r - 2f : inner.right - arc - r);
            int fromY = Math.round(top ? inner.top + 1f : tab.top + 3f);
            int toY = Math.round(top ? tab.bottom - 3f : inner.bottom - 1f);
            assertTrue("corner " + corner + ": nothing left of the tab to look at",
                toX > fromX && toY > fromY);
            for (int x = fromX; x <= toX; x++) {
                for (int y = fromY; y <= toY; y++) {
                    int pixel = bitmap.getPixel(x, y);
                    assertTrue("corner " + corner + ": the frame's line runs under the tab at ("
                            + x + ", " + y + "), " + hex(pixel),
                        far(pixel, mPrimary));
                    assertTrue("corner " + corner + ": the tab is not its own fill at ("
                            + x + ", " + y + "), " + hex(pixel) + " for " + hex(expected),
                        near(pixel, expected));
                }
            }
        }
    }

    /**
     * And the tab's outer edge is the frame's edge, to the pixel: the fill starts where the border's
     * stroke ends, with nothing of the wall showing between them.
     */
    @Test
    public void theTabsOuterEdgeIsTheFramesOwnEdge() {
        for (int corner : new int[]{CornerZones.TOP_LEFT, CornerZones.TOP_RIGHT,
            CornerZones.BOTTOM_LEFT, CornerZones.BOTTOM_RIGHT}) {
            Bitmap bitmap = render(corner);
            RectF tab = new RectF();
            view(corner).tabBounds(tab);
            RectF inner = new RectF();
            CornerTabGeometry.innerBounds(PANE, BORDER, inner);
            int expected = tabFill();
            boolean left = CornerZones.isLeft(corner);
            // Across the tab, halfway down it: past the frame's corner arc, on the straight run.
            int row = Math.round((tab.top + tab.bottom) / 2f);
            int edge = -1;
            if (left) {
                for (int x = Math.round(PANE.left); x < Math.round(tab.right) && edge < 0; x++) {
                    if (near(bitmap.getPixel(x, row), expected)) edge = x;
                }
                assertEquals("corner " + corner + ": the fill starts at the border's inner edge",
                    (double) Math.round(inner.left), (double) edge, 1d);
            } else {
                for (int x = Math.round(PANE.right) - 1; x > Math.round(tab.left) && edge < 0; x--) {
                    if (near(bitmap.getPixel(x, row), expected)) edge = x;
                }
                assertEquals("corner " + corner + ": the fill starts at the border's inner edge",
                    (double) (Math.round(inner.right) - 1), (double) edge, 1d);
            }
            assertTrue("corner " + corner + ": no fill found along the frame's side at all",
                edge >= 0);
        }
    }

    /**
     * The join where the tab meets the frame side is a straight T-junction now, not an arc: a pixel
     * just inside the frame's edge, right at the tab's own top, is the tab's own stroke — the line
     * it draws for itself starts flush against the frame rather than curving away from it.
     */
    @Test
    public void theStraightJoinIsDrawnAtTheTabsTop() {
        for (int corner : new int[]{CornerZones.TOP_LEFT, CornerZones.TOP_RIGHT,
            CornerZones.BOTTOM_LEFT, CornerZones.BOTTOM_RIGHT}) {
            Bitmap bitmap = render(corner);
            RectF tab = new RectF();
            view(corner).tabBounds(tab);
            boolean left = CornerZones.isLeft(corner);
            boolean top = CornerZones.isTop(corner);
            float topY = top ? tab.bottom : tab.top;
            int fillComposite = tabFill();
            int x = Math.round(left ? tab.left + 3f : tab.right - 3f);
            // The row fully inside the tab's own fill, adjacent to the join line: the fill runs
            // from the join up to the frame edge for a top corner, so that is the row just above
            // it; for a bottom corner the fill starts exactly at the join and runs down, so it is
            // the join's own row. A stroke line sitting on that join, even split across the pixel
            // grid by anti-aliasing, visibly darkens or lightens this row away from the plain fill
            // colour a flat, un-stroked join would leave it.
            int y = Math.round(topY) + (top ? -1 : 0);
            int pixel = bitmap.getPixel(x, y);
            assertTrue("corner " + corner + ": the straight join at the tab's top is not drawn at ("
                    + x + ", " + y + "), " + hex(pixel) + " reads as the plain fill "
                    + hex(fillComposite),
                far(pixel, fillComposite));
        }
    }

    /** The pane as the wall paints it — wall, border stroke, tab — into a bitmap. */
    private Bitmap render(int corner) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BEHIND);
        // pane_active_border: a stroke of its own width painted just inside the pane's box.
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(BORDER);
        paint.setColor(mPrimary);
        RectF stroke = new RectF(PANE.left + BORDER / 2f, PANE.top + BORDER / 2f,
            PANE.right - BORDER / 2f, PANE.bottom - BORDER / 2f);
        canvas.drawRoundRect(stroke, RADIUS - BORDER / 2f, RADIUS - BORDER / 2f, paint);
        view(corner).draw(canvas);
        return bitmap;
    }

    /** The tab, out at one corner of {@link #PANE}, laid out over the whole bitmap. */
    private PaneControlsView view(int corner) {
        return view(corner, TINT, null);
    }

    /** As above, with the page's tint and frost frame chosen; the frame covers the whole bitmap. */
    private PaneControlsView view(int corner, final int tint, Bitmap frost) {
        PaneControlsView view = new PaneControlsView(mContext);
        if (frost != null) {
            view.setPaneGlass(frost, new android.graphics.Rect(0, 0, WIDTH, HEIGHT), null, null, 0);
        }
        // Marks that draw nothing: the buttons' own glyphs are painted in the same colour as the
        // frame's stroke, and this test is about the line around the tab, not what is in it.
        view.setActions(
            PaneControlsView.Action.drawn(0, (canvas, button, paint, density) -> { }),
            PaneControlsView.Action.drawn(1, (canvas, button, paint, density) -> { }));
        view.setFrameSource(frame -> {
            frame.bounds.set(PANE);
            frame.radiusPx = RADIUS;
            frame.borderPx = BORDER;
            frame.fillColor = tint;
            return true;
        });
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        view.showNow(corner);
        return view;
    }

    /**
     * What a tab with no frost is filled with: the page's tint standing on the theme's panel veil
     * over the wall. The tint alone used to be the whole fill, and a faint one left the buttons on
     * bare terminal text.
     */
    private int tabFill() {
        int panel = MaterialColors.getColor(mContext, com.termux.shared.R.attr.termuxColorSurfacePanel,
            androidx.core.content.ContextCompat.getColor(mContext, R.color.termux_surface_panel));
        int veil = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(panel, PaneControlsView.VEIL_ALPHA), BEHIND);
        return ColorUtils.compositeColors(TINT, veil);
    }

    /**
     * A page with a faint tint and no frost: the tab is still nearly opaque, because the veil
     * stands under the tint. This is the case the user could not see the buttons in.
     */
    @Test
    public void aFaintTintStillGetsAnOpaqueTab() {
        PaneControlsView view = view(CornerZones.TOP_RIGHT, 0x14204058, null);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        RectF tab = new RectF();
        view.tabBounds(tab);
        int pixel = bitmap.getPixel(Math.round(tab.centerX()), Math.round(tab.centerY()));
        assertTrue("the tab is see-through: " + hex(pixel),
            Color.alpha(pixel) >= PaneControlsView.VEIL_ALPHA);
    }

    /**
     * A page wearing frost: the tab is cut from the same frame, at its own place on screen, and
     * the tint goes over it — no veil, since the frost already stands between the buttons and
     * whatever is under the tab.
     */
    @Test
    public void aFrostedPageGivesTheTabItsOwnFrost() {
        final int frostColor = 0xFF3060A0;
        Bitmap frost = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        frost.eraseColor(frostColor);
        PaneControlsView view = view(CornerZones.TOP_RIGHT, TINT, frost);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BEHIND);
        view.draw(canvas);
        RectF tab = new RectF();
        view.tabBounds(tab);
        int pixel = bitmap.getPixel(Math.round(tab.centerX()), Math.round(tab.centerY()));
        int expected = ColorUtils.compositeColors(TINT, frostColor);
        assertTrue("the tab is not the page's frost under its tint: " + hex(pixel) + " for "
            + hex(expected), near(pixel, expected));
        // And the frost stays inside the tab: the wall beside it is untouched.
        int outside = bitmap.getPixel(Math.round(tab.left) - 6, Math.round(tab.bottom) + 6);
        assertTrue("frost leaked past the tab: " + hex(outside), near(outside, BEHIND));
    }

    private static boolean near(int pixel, int expected) {
        return Math.abs(Color.red(pixel) - Color.red(expected)) <= TOLERANCE
            && Math.abs(Color.green(pixel) - Color.green(expected)) <= TOLERANCE
            && Math.abs(Color.blue(pixel) - Color.blue(expected)) <= TOLERANCE
            && Math.abs(Color.alpha(pixel) - Color.alpha(expected)) <= TOLERANCE;
    }

    private static boolean far(int pixel, int expected) {
        return !near(pixel, expected);
    }

    private static String hex(int color) {
        return "#" + Integer.toHexString(color);
    }
}
