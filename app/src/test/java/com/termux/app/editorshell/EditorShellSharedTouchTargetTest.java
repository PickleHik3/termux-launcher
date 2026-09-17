package com.termux.app.editorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * The header stands five actions in one parent and a view holds one {@link android.view.TouchDelegate}.
 * Setting them one at a time left only the last one grown, so four of the five sat below the
 * platform's minimum target. These pin that every one of them keeps its own.
 */
@RunWith(RobolectricTestRunner.class)
public class EditorShellSharedTouchTargetTest {

    /** Three small buttons in a row, each drawn smaller than a finger. */
    private static LinearLayout headerWith(Activity activity, List<View> actions, int drawnPx) {
        LinearLayout parent = new LinearLayout(activity);
        parent.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            View action = new View(activity);
            action.setLayoutParams(new LinearLayout.LayoutParams(drawnPx, drawnPx));
            action.setClickable(true);
            parent.addView(action);
            actions.add(action);
        }
        FrameLayout root = new FrameLayout(activity);
        root.addView(parent);
        activity.setContentView(root);
        parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(drawnPx, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 600, drawnPx);
        return parent;
    }

    @Test
    public void everyActionInTheHeaderKeepsItsOwnGrownTarget() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<View> actions = new ArrayList<>();
        int drawn = 40;
        int floor = 48;
        LinearLayout header = headerWith(activity, actions, drawn);

        for (View action : actions)
            EditorShellRows.expandTouchTarget(action, floor);
        Robolectric.flushForegroundThreadScheduler();

        assertNotNull("the parent should carry a delegate", header.getTouchDelegate());

        // A down in the grown band of each action — 4px outside its drawn box, inside its 48px
        // target — must reach that action and no other.
        for (int i = 0; i < actions.size(); i++) {
            View action = actions.get(i);
            float x = action.getLeft() - 2f;
            float y = drawn / 2f;
            MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
            boolean handled = header.getTouchDelegate().onTouchEvent(down);
            down.recycle();
            assertTrue("action " + i + " should own the strip just outside its drawn box", handled);
        }
    }

    @Test
    public void aDownOutsideEveryTargetIsNotClaimed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<View> actions = new ArrayList<>();
        LinearLayout header = headerWith(activity, actions, 40);

        for (View action : actions)
            EditorShellRows.expandTouchTarget(action, 48);
        Robolectric.flushForegroundThreadScheduler();

        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 580f, 20f, 0);
        boolean handled = header.getTouchDelegate().onTouchEvent(down);
        down.recycle();
        assertEquals("empty header space is not a control", false, handled);
    }
}
