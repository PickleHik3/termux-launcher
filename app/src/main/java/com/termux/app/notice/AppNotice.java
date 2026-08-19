package com.termux.app.notice;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;

import java.lang.ref.WeakReference;

/**
 * Every transient notice in the app goes through here.
 *
 * <p>The app used to raise these as stock toasts. A toast lands bottom-centre — over the shell
 * prompt, over the soft keyboard, over the dock — it cannot be themed to match the surface it
 * covers, and from Android 11 onward {@code setGravity} is ignored for text toasts, so it cannot be
 * moved either. {@link AppNoticeHostView} draws them instead, in the top-trailing corner.
 *
 * <p>Callers pass a {@link Context} and nothing else, exactly as {@code Toast.makeText} took one.
 * The host is found from that context when it is an activity, and otherwise from the foreground
 * activity this class tracks — services and preference data stores raise notices too, and the chip
 * should still be the thing that shows them. A stock toast remains the fallback for the genuinely
 * headless case (a notice raised while no activity of ours is up), because a dropped message is
 * worse than a misplaced one.
 */
public final class AppNotice {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int HOST_TAG_KEY = R.id.app_notice_host;

    @Nullable private static WeakReference<Activity> sForegroundActivity;

    private AppNotice() {}

    /**
     * Starts tracking which activity is in front, so notices raised from a service or a background
     * callback can still find a chip to land in. Called once, from the application object.
     */
    public static void install(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                sForegroundActivity = new WeakReference<>(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                if (current() == activity) sForegroundActivity = null;
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {
                if (current() == a) sForegroundActivity = null;
            }
        });
    }

    // ---------------------------------------------------------------- message-only entry points

    public static void show(@Nullable Context context, @Nullable CharSequence message) {
        show(context, message, false);
    }

    public static void show(@Nullable Context context, @StringRes int message) {
        show(context, message, false);
    }

    public static void show(@Nullable Context context, @Nullable CharSequence message,
                            boolean longDuration) {
        raise(context, AppNoticeItem.Kind.INFO, message, null, null, longDuration);
    }

    public static void show(@Nullable Context context, @StringRes int message,
                            boolean longDuration) {
        if (context == null) return;
        raise(context, AppNoticeItem.Kind.INFO, context.getString(message), null, null,
            longDuration);
    }

    // ------------------------------------------------------------------------ richer entry points

    /** A notice with a subtitle: the headline stays readable, the detail is what gets clipped. */
    public static void show(@Nullable Context context, @NonNull AppNoticeItem.Kind kind,
                            @Nullable CharSequence title, @Nullable CharSequence sub,
                            boolean longDuration) {
        raise(context, kind, title, sub, null, longDuration);
    }

    /** As above, with the glyph the action itself suggests instead of the kind's default. */
    public static void show(@Nullable Context context, @NonNull AppNoticeItem.Kind kind,
                            @Nullable String glyph, @Nullable CharSequence title,
                            @Nullable CharSequence sub, boolean longDuration) {
        raise(context, kind, title, sub, glyph, longDuration);
    }

    public static void error(@Nullable Context context, @Nullable CharSequence message) {
        raise(context, AppNoticeItem.Kind.ERROR, message, null, null, true);
    }

    public static void success(@Nullable Context context, @Nullable CharSequence message) {
        raise(context, AppNoticeItem.Kind.SUCCESS, message, null, null, false);
    }

    // ------------------------------------------------------------------------------- plumbing

    private static void raise(@Nullable Context context, @NonNull AppNoticeItem.Kind kind,
                              @Nullable CharSequence title, @Nullable CharSequence sub,
                              @Nullable String glyph, boolean longDuration) {
        if (context == null || TextUtils.isEmpty(title)) return;
        Context appContext = context.getApplicationContext();
        AppNoticeItem item = new AppNoticeItem(kind, title, sub, glyph,
            longDuration ? AppNoticeHostView.HOLD_LONG_MS : AppNoticeHostView.HOLD_SHORT_MS);
        Activity fromContext = activityOf(context);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver(appContext, fromContext, item);
        } else {
            MAIN.post(() -> deliver(appContext, fromContext, item));
        }
    }

    @MainThread
    private static void deliver(@NonNull Context appContext, @Nullable Activity fromContext,
                                @NonNull AppNoticeItem item) {
        Activity activity = usable(fromContext) ? fromContext : current();
        AppNoticeHostView host = usable(activity) ? hostFor(activity) : null;
        if (host == null) {
            // No window of ours to draw into. A stock toast is bottom-centre and unthemed, but it
            // is the only surface left, and losing the message outright would be worse.
            CharSequence text = TextUtils.isEmpty(item.sub)
                ? item.title : item.title + " — " + item.sub;
            Toast.makeText(appContext, text,
                item.durationMs >= AppNoticeHostView.HOLD_LONG_MS
                    ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            return;
        }
        host.enqueue(item);
    }

    /** The chip for this activity, creating and attaching it on first use. */
    @MainThread
    @Nullable
    public static AppNoticeHostView hostFor(@NonNull Activity activity) {
        ViewGroup anchor = anchorFor(activity);
        if (anchor == null) return null;
        Object existing = anchor.getTag(HOST_TAG_KEY);
        if (existing instanceof AppNoticeHostView) {
            AppNoticeHostView host = (AppNoticeHostView) existing;
            if (host.getParent() == anchor) return host;
        }
        AppNoticeHostView host = new AppNoticeHostView(activity);
        FrameLayout.LayoutParams params = AppNoticeHostView.buildHostLayoutParams(activity);
        if (anchor.getId() != R.id.terminal_surface_host) {
            // Outside the terminal the chip has no chrome of ours to hang from, so it hangs from
            // whatever is at the top of that screen instead: the app bar when there is one, and the
            // system status bar otherwise.
            params.topMargin = topChromeOffset(activity, anchor);
        }
        anchor.addView(host, params);
        anchor.setTag(HOST_TAG_KEY, host);
        return host;
    }

    /**
     * The terminal hangs the chip off its window bar, which is what the design is drawn against.
     * Every other screen — settings, the report viewer — gets it in the content root instead.
     */
    @Nullable
    private static ViewGroup anchorFor(@NonNull Activity activity) {
        View surfaceHost = activity.findViewById(R.id.terminal_surface_host);
        if (surfaceHost instanceof FrameLayout) return (FrameLayout) surfaceHost;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }

    /**
     * How far down the chip has to start so it does not land on top of the screen's own chrome.
     * Measured off the real toolbar when one is laid out, because settings screens draw
     * edge-to-edge and the status-bar inset alone would put the chip behind the app bar.
     */
    private static int topChromeOffset(@NonNull Activity activity, @NonNull ViewGroup anchor) {
        View toolbar = activity.findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null && toolbar.getHeight() > 0) {
            int[] toolbarLocation = new int[2];
            int[] anchorLocation = new int[2];
            toolbar.getLocationInWindow(toolbarLocation);
            anchor.getLocationInWindow(anchorLocation);
            int below = toolbarLocation[1] + toolbar.getHeight() - anchorLocation[1];
            if (below > 0) return below;
        }
        WindowInsetsCompat insets =
            ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (insets == null) return 0;
        Insets bars = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
        return bars.top;
    }

    @Nullable
    private static Activity current() {
        return sForegroundActivity == null ? null : sForegroundActivity.get();
    }

    private static boolean usable(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return false;
        }
        return activity.getWindow() != null;
    }

    @Nullable
    private static Activity activityOf(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
