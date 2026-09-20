package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Configuration;
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
        SafeLauncherAppWidgetHostView view =
            new SafeLauncherAppWidgetHostView(widgetContext(context), callback);
        view.setExecutor(INFLATE_EXECUTOR);
        return view;
    }

    /**
     * The context a provider's RemoteViews are inflated against.
     *
     * <p>Two separate things are being fixed here, and they pull in opposite directions.
     *
     * <p>The <b>inflater</b> must not be the activity's. AppCompat installs its view factory
     * there, and RemoteViews validates each action against the class that was actually inflated,
     * so substituting AppCompatImageView for the provider's framework ImageView makes an ordinary
     * setImageBitmap action illegal. The application context has no activity factory, so it is the
     * base; the activity's own theme is carried over for host sizing and colours.
     *
     * <p>The <b>day/night</b> a widget renders in is decided by this context's Configuration, not
     * by the provider's: RemoteViews resolves the provider's resources through
     * {@code createConfigurationContext(hostContext.getResources().getConfiguration())}. The
     * application context does not carry the activity's night mode, so widgets hosted against it
     * could resolve their day resources while the launcher — and the phone — were dark. The night
     * bits are taken from the host context and everything else is left to the application's own
     * configuration, so a widget is dark exactly when the launcher around it is.
     *
     * <p>Nothing re-inflates a widget by itself when night mode changes — AppWidgetHostView has no
     * configuration hook. The activity does not list {@code uiMode} in its configChanges, so the
     * system recreates it and these views are built again against the new configuration.
     */
    @NonNull
    static Context widgetContext(@NonNull Context context) {
        Context base = context.getApplicationContext();
        int hostNight = context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
        int applicationUiMode = base.getResources().getConfiguration().uiMode;
        if (hostNight != (applicationUiMode & Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration override = new Configuration();
            // Sparse override: every field left at its default is taken from the base.
            override.fontScale = 0;
            override.uiMode = hostNight | (applicationUiMode & ~Configuration.UI_MODE_NIGHT_MASK);
            base = base.createConfigurationContext(override);
        }
        return new ContextThemeWrapper(base, context.getTheme());
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
