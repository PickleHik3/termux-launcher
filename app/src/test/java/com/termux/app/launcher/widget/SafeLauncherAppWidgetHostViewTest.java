package com.termux.app.launcher.widget;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class SafeLauncherAppWidgetHostViewTest {
    @Test public void allRuntimeBoundariesProduceOneAccessibleBitmapFreeError() {
        for (String phase : new String[] {"update", "measure", "layout", "draw", "touch"}) {
            List<String> failures = new ArrayList<>();
            SafeLauncherAppWidgetHostView view = view(failures);
            view.setBoundaryProbeForTests(value -> { if (phase.equals(value)) throw new RuntimeException(); });
            exercise(view, phase);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue("phase=" + phase, view.isShowingLocalError());
            assertEquals("phase=" + phase, 1, failures.size());
            assertNotNull(view.getChildAt(0).getContentDescription());
            assertFalse(containsBitmap(view.getChildAt(0)));
        }
    }

    @Test public void refreshRecoversAndOutOfMemoryIsNotSwallowed() {
        List<String> failures = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = view(failures);
        view.setBoundaryProbeForTests(phase -> { if ("update".equals(phase)) throw new RuntimeException(); });
        view.updateAppWidget(null);
        assertTrue(view.isShowingLocalError());
        view.setBoundaryProbeForTests(null);
        view.updateAppWidget(new RemoteViews(view.getContext().getPackageName(),
            android.R.layout.simple_list_item_1));
        assertFalse(view.isShowingLocalError());

        view.setBoundaryProbeForTests(phase -> { throw new OutOfMemoryError("fatal"); });
        try {
            view.updateAppWidget(null);
            throw new AssertionError("OutOfMemoryError was swallowed");
        } catch (OutOfMemoryError expected) { }
    }

    @Test public void recoveryReplacesTrackedErrorChildInsteadOfAppendingBesideIt() {
        List<String> failures = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = view(failures);
        RemoteViews valid = new RemoteViews(view.getContext().getPackageName(),
            android.R.layout.simple_list_item_1);
        view.updateAppWidget(valid);
        View originalProviderChild = view.getChildAt(0);
        view.setBoundaryProbeForTests(phase -> {
            if ("update".equals(phase)) throw new RuntimeException("provider");
        });
        view.updateAppWidget(valid);
        View errorChild = view.getChildAt(0);
        assertEquals(1, view.getChildCount());
        assertNotSame(originalProviderChild, errorChild);
        view.setBoundaryProbeForTests(null);
        view.updateAppWidget(valid);
        assertEquals(1, view.getChildCount());
        assertNotSame(errorChild, view.getChildAt(0));
        assertFalse(view.isShowingLocalError());
    }

    @Test public void detachedDrawAndTouchFailuresAreAlwaysDeferredPastDispatch() {
        for (String phase : new String[] {"draw", "touch"}) {
            List<String> failures = new ArrayList<>();
            SafeLauncherAppWidgetHostView view = view(failures);
            view.setBoundaryProbeForTests(value -> {
                if (phase.equals(value)) throw new RuntimeException(phase);
            });
            exercise(view, phase);
            assertFalse("phase=" + phase, view.isShowingLocalError());
            assertEquals("phase=" + phase, 0, failures.size());
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue("phase=" + phase, view.isShowingLocalError());
            assertEquals("phase=" + phase, 1, failures.size());
        }
    }

    private static SafeLauncherAppWidgetHostView view(List<String> failures) {
        return new SafeLauncherAppWidgetHostView(ApplicationProvider.getApplicationContext(),
            new SafeLauncherAppWidgetHostView.FailureListener() {
                @Override public void onRenderFailure(int id, String phase) { failures.add(phase); }
                @Override public void onRenderRecovered(int id) { }
            });
    }
    private static void exercise(SafeLauncherAppWidgetHostView view, String phase) {
        int exact = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY);
        if ("update".equals(phase)) view.updateAppWidget(null);
        else if ("measure".equals(phase)) view.measure(exact, exact);
        else if ("layout".equals(phase)) { view.measure(exact, exact); view.layout(0, 0, 120, 120); }
        else if ("draw".equals(phase)) {
            view.measure(exact, exact); view.layout(0, 0, 120, 120);
            view.dispatchDraw(new Canvas(Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)));
        } else view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5, 5, 0));
    }
    private static boolean containsBitmap(View view) {
        if (view instanceof ImageView && ((ImageView) view).getDrawable() instanceof BitmapDrawable) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsBitmap(group.getChildAt(i))) return true;
        }
        return false;
    }
}
