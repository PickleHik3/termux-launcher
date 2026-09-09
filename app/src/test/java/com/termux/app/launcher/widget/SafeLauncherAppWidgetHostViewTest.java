package com.termux.app.launcher.widget;

import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
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
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowAppWidgetHostView;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.reflector.Reflector.reflector;

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

    @Test public void errorTileDoesNotRetryOnFramesAndOnlyProviderUpdateRetries() {
        List<String> failures = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = view(failures);
        int[] updateAttempts = {0};
        view.setBoundaryProbeForTests(phase -> {
            if ("update".equals(phase)) {
                updateAttempts[0]++;
                throw new RuntimeException("provider");
            }
        });
        view.updateAppWidget(null);
        assertEquals(1, updateAttempts[0]);

        int exact = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY);
        for (int frame = 0; frame < 5; frame++) {
            view.measure(exact, exact);
            view.layout(0, 0, 120, 120);
            view.dispatchDraw(new Canvas(
                Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)));
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("layout/draw frames must not retry RemoteViews inflation", 1,
            updateAttempts[0]);

        view.setBoundaryProbeForTests(phase -> {
            if ("update".equals(phase)) updateAttempts[0]++;
        });
        view.updateAppWidget(new RemoteViews(view.getContext().getPackageName(),
            android.R.layout.simple_list_item_1));
        assertEquals("a genuine provider update gets one new attempt", 2, updateAttempts[0]);
        assertFalse(view.isShowingLocalError());
    }

    /**
     * Robolectric replaces {@link AppWidgetHostView#updateAppWidget} with a plain inflate that
     * knows nothing about executors, error views or content tracking, so the executor-backed
     * apply path is only reachable by calling the real framework method. {@code setAppWidget} is
     * called through as well, because the real path reads the provider info it stores.
     */
    @Implements(AppWidgetHostView.class)
    public static class FrameworkApplyShadow extends ShadowAppWidgetHostView {
        @ForType(AppWidgetHostView.class)
        interface Direct1 {
            @Direct void updateAppWidget(RemoteViews views);
            @Direct void setAppWidget(int appWidgetId, AppWidgetProviderInfo info);
        }

        @RealObject AppWidgetHostView real;

        @Implementation
        @Override protected void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
            super.setAppWidget(appWidgetId, info);
            reflector(Direct1.class, real).setAppWidget(appWidgetId, info);
        }

        @Implementation
        @Override protected void updateAppWidget(RemoteViews remoteViews) {
            reflector(Direct1.class, real).updateAppWidget(remoteViews);
        }
    }

    @Test
    @Config(shadows = FrameworkApplyShadow.class)
    public void executorDefersProviderInflationPastTheUpdateCall() {
        List<String> failures = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = boundView(failures);
        ArrayDeque<Runnable> inflations = new ArrayDeque<>();
        view.setExecutor(inflations::add);

        view.updateAppWidget(provider(view));
        assertEquals("inflation must not run on the caller's thread", 0, view.getChildCount());
        assertEquals(1, inflations.size());

        pump(inflations);
        assertEquals(1, view.getChildCount());
        assertFalse(view.isShowingLocalError());
        assertEquals(0, failures.size());
    }

    @Test
    @Config(shadows = FrameworkApplyShadow.class)
    public void asyncInflationFailureThenTheSameLayoutAgainFailsAndRecovers() {
        List<String> events = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = new SafeLauncherAppWidgetHostView(
            ApplicationProvider.getApplicationContext(),
            new SafeLauncherAppWidgetHostView.FailureListener() {
                @Override public void onRenderFailure(int id, String phase) { events.add(phase); }
                @Override public void onRenderRecovered(int id) { events.add("recovered"); }
            });
        view.setAppWidget(7, WidgetTestFixtures.info(false));
        ArrayDeque<Runnable> inflations = new ArrayDeque<>();
        view.setExecutor(inflations::add);

        view.updateAppWidget(provider(view));
        pump(inflations);
        View providerChild = view.getChildAt(0);

        // Layout id 0 inflates on the executor and fails there, not in updateAppWidget.
        view.updateAppWidget(new RemoteViews(view.getContext().getPackageName(), 0));
        assertFalse("the failure is only known once the executor has run",
            view.isShowingLocalError());
        pump(inflations);
        assertTrue(view.isShowingLocalError());
        assertEquals(Collections.singletonList("framework"), events);
        assertEquals(1, view.getChildCount());
        assertNotSame(providerChild, view.getChildAt(0));
        assertNotNull(view.getChildAt(0).getContentDescription());

        // Same layout id as the render that worked: the framework would otherwise reapply it onto
        // the error tile and the widget would never come back.
        view.updateAppWidget(provider(view));
        pump(inflations);
        assertFalse(view.isShowingLocalError());
        assertEquals(Arrays.asList("framework", "recovered"), events);
        assertEquals(1, view.getChildCount());
        assertNotSame(providerChild, view.getChildAt(0));
        assertNull(view.getChildAt(0).getTag());
    }

    @Test
    @Config(shadows = FrameworkApplyShadow.class)
    public void measureFailureReplacesTheProviderViewInThatFrameDespiteTheExecutor() {
        List<String> failures = new ArrayList<>();
        SafeLauncherAppWidgetHostView view = boundView(failures);
        ArrayDeque<Runnable> inflations = new ArrayDeque<>();
        view.setExecutor(inflations::add);
        view.updateAppWidget(provider(view));
        pump(inflations);
        View providerChild = view.getChildAt(0);

        view.setBoundaryProbeForTests(phase -> {
            if ("measure".equals(phase)) throw new RuntimeException("provider");
        });
        int exact = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY);
        view.measure(exact, exact);

        assertTrue(view.isShowingLocalError());
        assertEquals(1, view.getChildCount());
        assertNotSame(providerChild, view.getChildAt(0));
        assertEquals(1, failures.size());
        assertTrue("the tile must not be queued on the executor", inflations.isEmpty());

        view.setBoundaryProbeForTests(null);
        view.updateAppWidget(provider(view));
        assertEquals("later provider updates still go to the executor", 1, inflations.size());
        pump(inflations);
        assertFalse(view.isShowingLocalError());
    }

    private static SafeLauncherAppWidgetHostView boundView(List<String> failures) {
        SafeLauncherAppWidgetHostView view = view(failures);
        view.setAppWidget(7, WidgetTestFixtures.info(false));
        return view;
    }

    private static RemoteViews provider(View view) {
        return new RemoteViews(view.getContext().getPackageName(),
            android.R.layout.simple_list_item_1);
    }

    /** Runs the queued inflations, then the main looper the framework applies them on. */
    private static void pump(ArrayDeque<Runnable> inflations) {
        while (!inflations.isEmpty()) {
            inflations.poll().run();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
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
