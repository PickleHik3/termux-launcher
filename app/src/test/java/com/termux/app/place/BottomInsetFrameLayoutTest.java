package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The container keeps its bottom band clear for every child but the ones that reach it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BottomInsetFrameLayoutTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 800;
    private static final int KEPT = 24;

    @Test
    public void aChildKeepsTheBandClearUnlessItReaches() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View plain = fill(container, context);
        layout(container);
        assertEquals(HEIGHT - KEPT, plain.getBottom());
    }

    @Test
    public void theSheetsLayerReachesTheContainersBottomEdge() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View plain = fill(container, context);
        View layer = fill(container, context);
        container.setReachingLayer(layer);
        layout(container);
        assertEquals(HEIGHT, layer.getBottom());
        assertEquals(HEIGHT, layer.getMeasuredHeight() + layer.getTop());
        assertEquals(HEIGHT - KEPT, plain.getBottom());
    }

    @Test
    public void theStacksLayoutAndTheSheetsLayerReachTogether() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View stack = fill(container, context);
        View layer = fill(container, context);
        container.setReachingChild(stack);
        container.setReachingLayer(layer);
        layout(container);
        assertEquals(HEIGHT, stack.getBottom());
        assertEquals(HEIGHT, layer.getBottom());
    }

    @Test
    public void aLayerThatIsNotShownDoesNotReach() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View layer = fill(container, context);
        layer.setVisibility(View.GONE);
        container.setReachingLayer(layer);
        layout(container);
        assertEquals(0, layer.getBottom());
    }

    @Test
    public void theColumnReachesTaller_withAndWithoutBottomPadding() {
        for (int padding : new int[]{KEPT, 0}) {
            Context context = RuntimeEnvironment.getApplication();
            BottomInsetFrameLayout container = container(context);
            container.setPadding(0, 0, 0, padding);
            View column = columnUnderReachingChild(container, context);
            ViewGroup parent = (ViewGroup) column.getParent();
            container.setReachingColumn(column, 120);
            layout(container);
            assertEquals(parent.getHeight() + 120, column.getHeight());
            assertEquals(0, column.getTop());
            assertEquals(0, ((ViewGroup.MarginLayoutParams) column.getLayoutParams()).bottomMargin);
        }
    }

    @Test
    public void aNegativeReachShortensTheColumn() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View column = columnUnderReachingChild(container, context);
        container.setReachingColumn(column, -50);
        layout(container);
        assertEquals(((ViewGroup) column.getParent()).getHeight() - 50, column.getHeight());
        assertEquals(-50, container.reachingColumnPx());
    }

    @Test
    public void theSameReachTwiceDoesNotDouble() {
        Context context = RuntimeEnvironment.getApplication();
        BottomInsetFrameLayout container = container(context);
        View column = columnUnderReachingChild(container, context);
        container.setReachingColumn(column, 120);
        layout(container);
        int first = column.getHeight();
        container.setReachingColumn(column, 120);
        layout(container);
        layout(container);
        assertEquals(first, column.getHeight());
    }

    private static View columnUnderReachingChild(BottomInsetFrameLayout container, Context context) {
        android.widget.RelativeLayout reaching = new android.widget.RelativeLayout(context);
        container.addView(reaching, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        container.setReachingChild(reaching);
        android.widget.LinearLayout column = new android.widget.LinearLayout(context);
        reaching.addView(column, new android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return column;
    }

    private static BottomInsetFrameLayout container(Context context) {
        BottomInsetFrameLayout container = new BottomInsetFrameLayout(context);
        container.setPadding(0, 0, 0, KEPT);
        return container;
    }

    private static View fill(BottomInsetFrameLayout container, Context context) {
        View view = new View(context);
        container.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    private static void layout(BottomInsetFrameLayout container) {
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, WIDTH, HEIGHT);
    }
}
