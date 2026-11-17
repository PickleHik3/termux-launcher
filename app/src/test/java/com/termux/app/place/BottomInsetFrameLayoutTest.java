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
