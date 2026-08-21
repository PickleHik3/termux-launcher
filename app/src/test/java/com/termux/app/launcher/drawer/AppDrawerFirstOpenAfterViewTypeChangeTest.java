package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerFirstOpenAfterViewTypeChangeTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    @Test public void firstClaimingMoveAfterColdViewTypeChangeOpensProductionDrawer() {
        Robolectric.getForegroundThreadScheduler().pause();
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("first-open-view-type",
            Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(activity, raw, null);
        preferences.setAppLauncherDrawerViewType("vertical");
        preferences.setAppLauncherDrawerViewType("horizontal");
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        FrameLayout host = activity.findViewById(R.id.app_drawer_host);
        View dock = activity.findViewById(R.id.accessory_surface_host);
        host.layout(0, 0, WIDTH, HEIGHT);
        dock.layout(0, HEIGHT - 180, WIDTH, HEIGHT);

        AppDrawerController controller = activity.getAppDrawerController();
        SuggestionBarView row = new SuggestionBarView(activity, null);
        row.addView(new ConsumingView(activity), new FrameLayout.LayoutParams(WIDTH, 160));
        row.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, WIDTH, 160);
        row.setAppDrawerGestureListener(new SuggestionBarView.AppDrawerGestureListener() {
            @Override public boolean isAppDrawerEnabled() { return true; }
            @Override public boolean isDockTuningActive() { return false; }
            @Override public boolean isCommandPaletteOpen() { return false; }
            @Override public boolean isAppDrawerEngaged() { return controller.isEngaged(); }
            @Override public void onDrawerDragBegin(float downRawY) {
                controller.beginDrag(downRawY);
            }
            @Override public void onDrawerDrag(float rawY) { controller.updateDrag(rawY); }
            @Override public void onDrawerDragEnd(float velocityPxPerSec) {
                controller.endDrag(velocityPxPerSec);
            }
            @Override public void onDrawerDragCancel() { controller.cancelDrag(); }
        });

        dispatch(row, MotionEvent.ACTION_DOWN, 100f, 20f);
        // Deliberately one MOVE: the cold content build must not discard the distance that claimed
        // the drawer even when UP is the next event delivered by the device.
        dispatch(row, MotionEvent.ACTION_MOVE, 100f, 320f);
        dispatch(row, MotionEvent.ACTION_UP, 100f, 320f);

        assertTrue(controller.isOpen());
        AppDrawerContentView content = ReflectionHelpers.getField(controller, "mContent");
        assertEquals(AppDrawerViewType.HORIZONTAL, content.getViewType());
    }

    private static void dispatch(SuggestionBarView row, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            row.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static final class ConsumingView extends View {
        ConsumingView(Context context) { super(context); }
        @Override public boolean onTouchEvent(MotionEvent event) { return true; }
    }
}
