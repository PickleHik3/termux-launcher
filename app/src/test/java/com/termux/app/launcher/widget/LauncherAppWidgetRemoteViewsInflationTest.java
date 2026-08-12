package com.termux.app.launcher.widget;

import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherAppWidgetRemoteViewsInflationTest {
    @Test public void productionHostUsesFactoryFreeContextAndAcceptsFrameworkImageBitmapAction() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.getDelegate().installViewFactory();
        assertNotNull("precondition: AppCompat owns the activity inflater",
            LayoutInflater.from(activity).getFactory2());

        LauncherAppWidgetHost host = new LauncherAppWidgetHost(activity);
        AppWidgetProviderInfo info = WidgetTestFixtures.info(false);
        AppWidgetHostView created = ReflectionHelpers.callInstanceMethod(host, "onCreateView",
            from(Context.class, activity), from(int.class, 20),
            from(AppWidgetProviderInfo.class, info));
        assertTrue(created instanceof SafeLauncherAppWidgetHostView);
        SafeLauncherAppWidgetHostView view = (SafeLauncherAppWidgetHostView) created;
        LayoutInflater remoteInflater = LayoutInflater.from(view.getContext());
        assertNull("provider inflater must not inherit AppCompat's Factory",
            remoteInflater.getFactory());
        assertNull("provider inflater must not inherit AppCompat's Factory2",
            remoteInflater.getFactory2());
        assertSame("host context must retain the activity theme", activity.getTheme(),
            view.getContext().getTheme());

        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        RemoteViews remoteViews = new RemoteViews(activity.getPackageName(),
            R.layout.launcher_widget_error_tile);
        remoteViews.setImageViewBitmap(R.id.launcher_widget_error_icon, bitmap);
        View inflated = remoteViews.apply(view.getContext(), view);

        assertFalse(view.isShowingLocalError());
        ImageView image = inflated.findViewById(R.id.launcher_widget_error_icon);
        assertNotNull(image);
        assertEquals("RemoteViews must inflate the exact framework class", ImageView.class,
            image.getClass());
        assertTrue(image.getDrawable() instanceof BitmapDrawable);
    }
}
