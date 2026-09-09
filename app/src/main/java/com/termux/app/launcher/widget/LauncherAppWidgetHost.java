package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Process;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Package-stable framework host. IDs survive activity recreation and app upgrades. */
public final class LauncherAppWidgetHost extends AppWidgetHost {
    public static final int APPWIDGET_HOST_ID = 0x544C;

    /**
     * Where provider RemoteViews are inflated. Inflation is the provider's whole layout tree and
     * cost hundreds of milliseconds per widget on the main thread every time the Widgets page was
     * entered and the providers re-pushed. {@link AppWidgetHostView#setExecutor} moves that work
     * here and still applies the finished view on the UI thread.
     *
     * <p>One shared background thread for every widget: the queue is unbounded but shallow, since
     * a host view has at most one apply in flight — the framework cancels the previous one through
     * its {@code CancellationSignal} when a newer RemoteViews arrives.
     */
    static final Executor INFLATE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, "widget-inflate");
        thread.setDaemon(true);
        return thread;
    });

    public interface Callback extends SafeLauncherAppWidgetHostView.FailureListener {
        void onProviderChanged(int appWidgetId, @NonNull AppWidgetProviderInfo info);
        void onProvidersChanged();
        void onAppWidgetRemoved(int appWidgetId);
    }

    @Nullable private Callback callback;

    public LauncherAppWidgetHost(@NonNull Context context) {
        super(context, APPWIDGET_HOST_ID);
    }

    public void setCallback(@Nullable Callback callback) { this.callback = callback; }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                             AppWidgetProviderInfo appWidget) {
        // AppCompat installs its view factory on the activity inflater. RemoteViews validates
        // actions against the inflated class, so substituting AppCompatImageView for the provider's
        // framework ImageView makes ordinary setImageBitmap actions illegal. Base this wrapper on
        // the application inflater (which has no activity factory) while retaining the activity's
        // exact theme for host sizing and colours. This affects provider inflation only.
        Context remoteViewsContext = new ContextThemeWrapper(
            context.getApplicationContext(), context.getTheme());
        SafeLauncherAppWidgetHostView view =
            new SafeLauncherAppWidgetHostView(remoteViewsContext, callback);
        view.setExecutor(INFLATE_EXECUTOR);
        return view;
    }

    @Override
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        super.onProviderChanged(appWidgetId, appWidget);
        if (callback != null) callback.onProviderChanged(appWidgetId, appWidget);
    }

    @Override
    protected void onProvidersChanged() {
        super.onProvidersChanged();
        if (callback != null) callback.onProvidersChanged();
    }

    @Override
    public void onAppWidgetRemoved(int appWidgetId) {
        super.onAppWidgetRemoved(appWidgetId);
        if (callback != null) callback.onAppWidgetRemoved(appWidgetId);
    }
}
