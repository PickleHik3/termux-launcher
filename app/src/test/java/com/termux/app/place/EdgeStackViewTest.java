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
