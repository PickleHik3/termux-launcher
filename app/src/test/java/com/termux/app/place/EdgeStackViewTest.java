package com.termux.app.place;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The stack is given its bars outermost first, the way {@link EdgeStackPolicy#stack} counts them,
 * and lays them out so that index 0 really is the band against the glass on every edge.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EdgeStackViewTest {

    private Activity mActivity;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
    }

    private EdgeStackView stack(Edge edge) {
        EdgeStackView stack = new EdgeStackView(mActivity);
        stack.setEdge(edge);
        return stack;
    }

    private List<View> bars(int count) {
        List<View> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) bars.add(new View(mActivity));
        return bars;
    }

    private List<View> children(LinearLayout group) {
        List<View> out = new ArrayList<>(group.getChildCount());
        for (int i = 0; i < group.getChildCount(); i++) out.add(group.getChildAt(i));
        return out;
    }

    @Test public void theEdgeDecidesTheAxis() {
        assertEquals(LinearLayout.VERTICAL, stack(Edge.TOP).getOrientation());
        assertEquals(LinearLayout.VERTICAL, stack(Edge.BOTTOM).getOrientation());
        assertEquals(LinearLayout.HORIZONTAL, stack(Edge.LEFT).getOrientation());
        assertEquals(LinearLayout.HORIZONTAL, stack(Edge.RIGHT).getOrientation());
    }

    @Test public void theTopAndLeftEdgesDrawOutermostFirst() {
        for (Edge edge : new Edge[] {Edge.TOP, Edge.LEFT}) {
            EdgeStackView stack = stack(edge);
            List<View> given = bars(3);
            assertTrue(edge.toString(), stack.setStack(given));
            assertEquals(edge.toString(), given, children(stack));
        }
    }

    @Test public void theBottomAndRightEdgesDrawOutermostLast() {
        // The outermost band is the one against the glass, which on these two edges is the last
        // child a LinearLayout lays out. Callers count from the glass on every edge regardless.
        for (Edge edge : new Edge[] {Edge.BOTTOM, Edge.RIGHT}) {
            EdgeStackView stack = stack(edge);
            List<View> given = bars(3);
            stack.setStack(given);
            assertEquals(edge.toString(),
                Arrays.asList(given.get(2), given.get(1), given.get(0)), children(stack));
        }
    }

    @Test public void aStackAlreadyHoldingTheOrderIsLeftAlone() {
        EdgeStackView stack = stack(Edge.LEFT);
        List<View> given = bars(3);
        assertTrue(stack.setStack(given));
        assertFalse("nothing moved the second time", stack.setStack(given));
        assertEquals(given, children(stack));
    }

    @Test public void aReorderMovesTheBandsWithinTheStack() {
        EdgeStackView stack = stack(Edge.LEFT);
        List<View> given = bars(3);
        stack.setStack(given);
        List<View> reordered = Arrays.asList(given.get(2), given.get(0), given.get(1));
        assertTrue(stack.setStack(reordered));
        assertEquals(reordered, children(stack));
    }

    @Test public void aBarIsAdoptedFromWhicheverStackHeldIt() {
        EdgeStackView left = stack(Edge.LEFT);
        EdgeStackView right = stack(Edge.RIGHT);
        List<View> given = bars(2);
        left.setStack(given);

        // The same view, not a new one: everything bound to it by id follows it across.
        View moved = given.get(0);
        right.setStack(Arrays.asList(moved));
        assertSame(right, moved.getParent());
        assertEquals("being put into its new stack is what takes it out of the old one",
            Arrays.asList(given.get(1)), children(left));
    }

    @Test public void nothingUnlistedIsEverTakenOut() {
        // A host no arrangement asks for — a hidden bar's, say — stays where it last stood with
        // its visibility off, rather than being orphaned by a pass that did not mention it.
        EdgeStackView stack = stack(Edge.TOP);
        List<View> given = bars(2);
        stack.setStack(given);
        given.get(1).setVisibility(View.GONE);
        stack.setStack(Arrays.asList(given.get(0)));
        assertEquals(given, children(stack));
    }

    // ------------------------------------------------ the hairline between two adjacent bands

    /** A band: the host the stack holds, with one bar inside it keeping the air of its own. */
    private android.widget.FrameLayout band(int airStartPx, int airEndPx, boolean column) {
        android.widget.FrameLayout host = new android.widget.FrameLayout(mActivity);
        View bar = new View(mActivity);
        if (column) bar.setPadding(airStartPx, 0, airEndPx, 0);
        else bar.setPadding(0, airStartPx, 0, airEndPx);
        host.addView(bar, new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        return host;
    }

    /** Where the band's content starts and ends along the axis, read off the bar it holds. */
    private int[] content(View band, boolean column) {
        View bar = ((android.view.ViewGroup) band).getChildAt(0);
        return column
            ? new int[] {band.getLeft() + bar.getLeft() + bar.getPaddingLeft(),
                band.getLeft() + bar.getRight() - bar.getPaddingRight()}
            : new int[] {band.getTop() + bar.getTop() + bar.getPaddingTop(),
                band.getTop() + bar.getBottom() - bar.getPaddingBottom()};
    }

    @Test public void theHairlineSplitsTheGapBetweenTwoBandsContent() {
        // The complaint: the apps row keeps its icons off its own rims and the extra keys do not,
        // so a line on the child boundary sat hard against the keys. It belongs to the gap, so it
        // goes in the middle of it, whatever air each side happens to keep.
        int thickness = 64;
        for (Edge edge : Edge.values()) {
            boolean column = edge.isOnSide();
            EdgeStackView stack = stack(edge);
            List<View> given = Arrays.asList(
                band(0, 0, column), band(30, 6, column), band(12, 0, column));
            stack.setStack(given);
            stack.setSeparatorCount(2);
            stack.setSeparatorAppearance(0xFF000000, 1, 0);
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                child.setLayoutParams(new LinearLayout.LayoutParams(
                    column ? thickness : android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    column ? android.view.ViewGroup.LayoutParams.MATCH_PARENT : thickness));
            }
            int along = thickness * given.size();
            stack.measure(
                View.MeasureSpec.makeMeasureSpec(column ? along : 480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(column ? 480 : along, View.MeasureSpec.EXACTLY));
            stack.layout(0, 0, column ? along : 480, column ? 480 : along);

            int[] centers = stack.separatorCenters();
            assertEquals(edge + ": one hairline per gap", 2, centers.length);
            for (int gap = 0; gap < 2; gap++) {
                int[] outer = content(stack.getChildAt(gap), column);
                int[] inner = content(stack.getChildAt(gap + 1), column);
                assertEquals(edge + " gap " + gap + ": the middle of the visible gap",
                    (outer[1] + inner[0]) / 2, centers[gap]);
            }
        }
    }

    @Test public void twoBandsWithNoAirBetweenThemKeepTheBoundaryTheyAlwaysHad() {
        // The shipped stacks: nothing to split, so the line lands exactly where it always did.
        EdgeStackView stack = stack(Edge.BOTTOM);
        List<View> given = Arrays.asList(band(0, 0, false), band(0, 0, false));
        stack.setStack(given);
        stack.setSeparatorCount(1);
        for (int i = 0; i < stack.getChildCount(); i++) {
            stack.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 40));
        }
        stack.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY));
        stack.layout(0, 0, 480, 80);
        assertEquals(1, stack.separatorCenters().length);
        assertEquals(stack.getChildAt(0).getBottom(), stack.separatorCenters()[0]);
    }

    @Test public void aStackCarriesNoPaddingOfItsOwn() {
        // The cutout used to be each side stack's own padding, from the days when the two stood
        // outside the padded content root; they stand inside the canvas band now, so the root keeps
        // the camera hole once and a stack is nothing but its bands.
        for (Edge edge : Edge.values()) {
            EdgeStackView stack = stack(edge);
            assertEquals(edge + " left", 0, stack.getPaddingLeft());
            assertEquals(edge + " right", 0, stack.getPaddingRight());
        }
    }
}
