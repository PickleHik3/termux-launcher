package com.termux.app;

import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.app.launcher.widget.SafeLauncherAppWidgetHostView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Issue #83: a relaunch (a wallpaper change, a Material You or theme recreate) crashed with
 * "Wrong state class, expecting View State but received class android.os.Bundle … id/0x5".
 *
 * <p>The cause: {@link AppWidgetHostView} files its saved subtree as a Bundle under its view id
 * or, with none, under its appWidgetId. Widget ids and {@code View.generateViewId()} ids are both
 * small ints, so a plain view in the relaunched tree that drew a generated id equal to a widget's
 * id was handed that Bundle and threw.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityViewStateRestoreTest {

    private static final int COLLIDING_ID = 5;

    private static SparseArray<Parcelable> saveTreeHolding(AppWidgetHostView host) {
        ReflectionHelpers.setField(AppWidgetHostView.class, host, "mAppWidgetId", COLLIDING_ID);
        FrameLayout parent = new FrameLayout(host.getContext());
        parent.addView(host);
        SparseArray<Parcelable> container = new SparseArray<>();
        parent.saveHierarchyState(container);
        return container;
    }

    private static FrameLayout viewWithGeneratedIdFive(Context context) {
        FrameLayout view = new FrameLayout(context);
        view.setId(COLLIDING_ID);
        return view;
    }

    @Test
    public void aPlainWidgetHostFilesABundleUnderItsWidgetIdAndAPlainViewChokesOnIt() {
        // The framework behaviour the fix works around, pinned so the test below means something.
        Context context = ApplicationProvider.getApplicationContext();
        SparseArray<Parcelable> container = saveTreeHolding(new AppWidgetHostView(context));

        assertTrue(container.get(COLLIDING_ID) instanceof Bundle);
        FrameLayout victim = viewWithGeneratedIdFive(context);
        assertThrows(IllegalArgumentException.class, () -> victim.restoreHierarchyState(container));
    }

    @Test
    public void theLauncherWidgetTileSavesNothingSoTheRelaunchedViewRestoresCleanly() {
        Context context = ApplicationProvider.getApplicationContext();
        SparseArray<Parcelable> container =
            saveTreeHolding(new SafeLauncherAppWidgetHostView(context, null));

        assertNull(container.get(COLLIDING_ID));
        viewWithGeneratedIdFive(context).restoreHierarchyState(container);
    }

    @Test
    public void theActivitySkipsAMismatchedViewStateInsteadOfCrashing() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(viewWithGeneratedIdFive(activity));

        SparseArray<Parcelable> views = new SparseArray<>();
        views.put(COLLIDING_ID, new Bundle());
        Bundle window = new Bundle();
        window.putSparseParcelableArray("android:views", views);
        Bundle saved = new Bundle();
        saved.putBundle("android:viewHierarchyState", window);

        activity.onRestoreInstanceState(saved);
    }
}
