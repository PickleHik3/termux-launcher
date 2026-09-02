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
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

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

    /**
     * A notice about a specific shell: tapping it takes the user to that pane or window, and an
     * {@code attention} notice is drawn in its own accent because the shell is waiting on them.
     */
    public static void shell(@Nullable Context context, @Nullable CharSequence title,
                             @Nullable CharSequence sub, @Nullable String glyph,
                             boolean attention, @Nullable Runnable onActivate) {
        raise(context, attention ? AppNoticeItem.Kind.WARNING : AppNoticeItem.Kind.INFO,
            title, sub, glyph, false, onActivate, attention);
    }

    /**
     * A notice whose tap is the way back: what just happened, undone by touching the chip.
     *
     * <p>This is what a snackbar with an Undo action used to be. The snackbar landed bottom-centre,
     * so it sat on the soft keyboard and ran into the display cutouts, it drew in Material's own
     * palette rather than the app's, and it could not be swiped away. The chip is themed, is pinned
     * to the one corner nothing else competes for, and dismisses on a swipe like every other notice
     * — and it holds for {@link AppNoticeHostView#HOLD_UNDO_MS}, long enough to see what the write
     * did and change one's mind.
     *
     * @param hint what the tap does, shown as the subtitle and announced to TalkBack.
     */
    public static void undoable(@Nullable Context context, @Nullable CharSequence title,
                                @Nullable CharSequence hint, @NonNull Runnable undo) {
        raise(context, AppNoticeItem.Kind.SUCCESS, title, hint, "↺",
            AppNoticeHostView.HOLD_UNDO_MS, undo, false, hint);
    }

    /**
     * Names the action a key or a palette entry just ran, for a beat. The same pill as every other
     * notice — it used to be its own chip in the terminal's top-trailing corner, the one message in
     * the app that landed somewhere else — but fleeting: it replaces a hint already showing rather
     * than queueing behind it, and it yields to any real notice.
     */
    public static void hint(@Nullable Context context, @Nullable CharSequence label) {
        if (context == null || TextUtils.isEmpty(label)) return;
        AppNoticeItem item = new AppNoticeItem(AppNoticeItem.Kind.INFO, label, null, null,
            AppNoticeHostView.HOLD_HINT_MS, null, false, null, true);
        Context appContext = context.getApplicationContext();
        Activity fromContext = activityOf(context);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver(appContext, fromContext, item);
        } else {
            MAIN.post(() -> deliver(appContext, fromContext, item));
        }
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
        raise(context, kind, title, sub, glyph, longDuration, null, false);
    }

    private static void raise(@Nullable Context context, @NonNull AppNoticeItem.Kind kind,
                              @Nullable CharSequence title, @Nullable CharSequence sub,
                              @Nullable String glyph, boolean longDuration,
                              @Nullable Runnable onActivate, boolean attention) {
        raise(context, kind, title, sub, glyph,
            longDuration ? AppNoticeHostView.HOLD_LONG_MS : AppNoticeHostView.HOLD_SHORT_MS,
            onActivate, attention, null);
    }

    private static void raise(@Nullable Context context, @NonNull AppNoticeItem.Kind kind,
                              @Nullable CharSequence title, @Nullable CharSequence sub,
                              @Nullable String glyph, long holdMs,
                              @Nullable Runnable onActivate, boolean attention,
                              @Nullable CharSequence actionHint) {
        if (context == null || TextUtils.isEmpty(title)) return;
        Context appContext = context.getApplicationContext();
        AppNoticeItem item = new AppNoticeItem(kind, title, sub, glyph, holdMs,
            onActivate, attention, actionHint);
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
            // A read-out of what a key did is meaningless once the window it happened in is gone.
            if (item.fleeting) return;
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
        anchor.addView(host, AppNoticeHostView.buildHostLayoutParams(activity));
        anchor.setTag(HOST_TAG_KEY, host);
        // Where the chip hangs from is derived and kept current rather than measured once here:
        // the bar it hangs off may not be laid out yet, and it moves on rotation, on a resize and
        // when a screen shows or hides it.
        AppNoticePlacement.attach(anchor, host);
        return host;
    }

    /**
     * The window's own content root, on every screen. The terminal used to get the chip inside its
     * surface host instead, to hang it off the window bar — but that made it a sibling of anything
     * the terminal opens in there, and the surface editor, added later, drew straight over the
     * notice it had just raised. {@link AppNoticePlacement} lines the chip up with the chrome
     * without parenting it to the same box.
     */
    @Nullable
    private static ViewGroup anchorFor(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
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
