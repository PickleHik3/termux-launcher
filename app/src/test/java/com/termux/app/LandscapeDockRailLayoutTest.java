package com.termux.app;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.launcher.drawer.DockRailScrollView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Production landscape rail geometry: layout bounds and touch ownership use the same split. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LandscapeDockRailLayoutTest {

    @Before
    public void useLandscapeResources() {
        RuntimeEnvironment.setQualifiers("+land");
    }

    @Test
    public void leftDockKeepsIconsAboveTouchableAlphabet() {
        assertMeasuredSplitAndTouchOwnership(Gravity.START);
    }

    @Test
    public void rightDockKeepsIconsAboveTouchableAlphabet() {
        assertMeasuredSplitAndTouchOwnership(Gravity.END);
    }

    private void assertMeasuredSplitAndTouchOwnership(int edgeGravity) {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        LinearLayout container = activity.findViewById(R.id.dock_rail_container);
        DockRailScrollView iconViewport = activity.findViewById(R.id.dock_rail_scroll);
        LinearLayout iconList = activity.findViewById(R.id.dock_rail_list);
        AzScrubRowView az = activity.findViewById(R.id.apps_bar_az_row);

        android.widget.FrameLayout.LayoutParams containerParams =
            (android.widget.FrameLayout.LayoutParams) container.getLayoutParams();
        containerParams.gravity = edgeGravity | Gravity.TOP;
        container.setLayoutParams(containerParams);
        container.setVisibility(View.VISIBLE);
        iconViewport.setVisibility(View.VISIBLE);

        if (az.getParent() instanceof ViewGroup)
            ((ViewGroup) az.getParent()).removeView(az);
        az.setVertical(true);
        az.setVisibility(View.VISIBLE);
        container.addView(az, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 14; i++) {
            View icon = new View(activity);
            iconList.addView(icon, new LinearLayout.LayoutParams(32, 32));
        }

        View root = activity.findViewById(R.id.activity_termux_root_view);
        int exactWidth = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY);
        int exactHeight = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY);
        root.measure(exactWidth, exactHeight);
        root.layout(0, 0, 1000, 480);

        assertTrue("vertical A-Z must measure to a non-zero intrinsic height", az.getHeight() > 0);
        assertTrue("icon viewport must stop at or above A-Z",
            iconViewport.getBottom() <= az.getTop());
        assertTrue("A-Z must remain inside the rail",
            az.getBottom() <= container.getHeight() - container.getPaddingBottom());

        final char[] touched = {'?'};
        az.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override public void onScrub(char letter, int selectionIndex, float touchX,
                                          float touchY, float rawX, float rawY,
                                          long eventTimeMs,
                                          AzScrubRowView.GesturePhase phase) {
                touched[0] = letter;
            }

            @Override public void onCancel() {}
        });

        float x = az.getLeft() + az.getWidth() * .5f;
        float y = az.getTop() + az.getHeight() * .5f;
        MotionEvent down = MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, x, y, 0);
        assertTrue("the A-Z band must consume DOWN", container.dispatchTouchEvent(down));
        assertTrue("the A-Z band must consume UP", container.dispatchTouchEvent(up));
        down.recycle();
        up.recycle();
        assertTrue("touch in the A-Z band must reach A-Z", touched[0] != '?');
    }
}
