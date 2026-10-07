package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FitStackTest {
    private static final int WIDTH = 100;

    private final Context context = RuntimeEnvironment.getApplication();

    @NonNull private View block(int heightPx) {
        View view = new View(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx));
        return view;
    }

    private static void layOut(@NonNull FitStack stack, int heightPx) {
        stack.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        stack.layout(0, 0, WIDTH, heightPx);
    }

    @Test public void everythingShowsWhenItFits() {
        FitStack stack = FitStack.column(context)
            .add(block(100), 30, 0).add(block(100), 20, 0).add(block(100), 10, 0);
        layOut(stack, 300);
        assertEquals(3, stack.getChildCount());
    }

    @Test public void theLowestRankIsLeftOutFirstAndDetached() {
        View first = block(100);
        View second = block(100);
        View third = block(100);
        FitStack stack = FitStack.column(context)
            .add(first, 30, 0).add(second, 20, 0).add(third, 10, 0);
        layOut(stack, 250);
        assertEquals(2, stack.getChildCount());
        assertSame(first, stack.getChildAt(0));
        assertSame(second, stack.getChildAt(1));
    }

    @Test public void aChildComesBackWhenTheRoomDoes() {
        FitStack stack = FitStack.column(context)
            .add(block(100), 30, 0).add(block(100), 20, 0).add(block(100), 10, 0);
        layOut(stack, 250);
        assertEquals(2, stack.getChildCount());
        layOut(stack, 300);
        assertEquals(3, stack.getChildCount());
    }

    @Test public void childrenOfOneRankAreLeftOutTogether() {
        FitStack stack = FitStack.column(context)
            .add(block(100), FitStack.ESSENTIAL, 0).add(block(100), 5, 0).add(block(100), 5, 0);
        layOut(stack, 250);
        assertEquals(1, stack.getChildCount());
    }

    @Test public void anEssentialChildIsNeverLeftOut() {
        FitStack stack = FitStack.column(context)
            .add(block(100), FitStack.ESSENTIAL, 0).add(block(100), FitStack.ESSENTIAL, 0);
        layOut(stack, 150);
        assertEquals(2, stack.getChildCount());
    }

    @Test public void anElasticGapTakesTheRoomLeftOver() {
        FitStack stack = FitStack.column(context)
            .add(block(50), FitStack.ESSENTIAL, 0).addElastic(block(50), FitStack.ESSENTIAL, 0);
        layOut(stack, 200);
        assertEquals(150, stack.getChildAt(1).getTop());
    }

    @Test public void aFlexChildTakesTheRoomLeftOver() {
        FitStack stack = FitStack.column(context)
            .add(block(50), FitStack.ESSENTIAL, 0).addFlex(block(10), FitStack.ESSENTIAL, 0, 20);
        layOut(stack, 200);
        assertEquals(150, stack.getChildAt(1).getHeight());
    }

    @Test public void aFlexChildThatCannotHaveItsMinimumIsLeftOut() {
        FitStack stack = FitStack.column(context)
            .add(block(100), FitStack.ESSENTIAL, 0).addFlex(block(10), 5, 0, 60);
        layOut(stack, 130);
        assertEquals(1, stack.getChildCount());
    }

    @Test public void aShrinkingChildGivesWayBeforeAnythingIsLeftOut() {
        FitStack stack = FitStack.column(context)
            .addShrink(block(10), 5, 0, 100, 40).add(block(100), FitStack.ESSENTIAL, 0);
        layOut(stack, 150);
        assertEquals(2, stack.getChildCount());
        assertEquals(50, stack.getChildAt(0).getHeight());
    }

    @Test public void listRowsKeepTheFirstThatFitWhole() {
        FitStack stack = FitStack.column(context)
            .addRow(block(60), 0).addRow(block(60), 10).addRow(block(60), 10);
        layOut(stack, 150);
        assertEquals(2, stack.getChildCount());
    }
}
