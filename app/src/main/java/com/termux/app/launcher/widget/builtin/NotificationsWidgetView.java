package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.NotificationHistoryFragment;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.launcherctl.LauncherCtlNotificationEvent;
import com.termux.launcherctl.LauncherCtlNotificationListener;
import com.termux.launcherctl.LauncherCtlNotificationStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

/**
 * The notifications in the shade, newest first: a bell with a count at 1×1, the newest message
 * at 2×1 and 4×1, a short list at 2×2 and 4×2.
 *
 * <p>The rows come from the notification listener's view of the shade, cut the way the history
 * cuts them (no ongoing, progress, media or bare group summaries), for every app that has a
 * notification showing; nothing is written anywhere. When the listener is not connected the
 * widget falls back to the history store's rows that are still posted. Without notification
 * access the card asks for it and a tap opens the system screen that grants it. App icons are
 * rasterised small, once per package, into a twelve-entry cache.</p>
 */
public class NotificationsWidgetView extends BuiltinWidgetView {
    private static final int LIMIT = 50;
    private static final long DEBOUNCE_MS = 300L;
    private static final int ICON_ENTRIES = 12;
    /** The largest an app icon is drawn (the one-row layout's); icons are kept at this size. */
    private static final int ICON_DP = 36;

    private final LruCache<String, Drawable> icons = new LruCache<>(ICON_ENTRIES);
    private final Runnable reload = this::reload;
    private final LauncherNotificationBadgeStore.Listener badgeListener = packages -> {
        services.main().removeCallbacks(reload);
        services.main().postDelayed(reload, DEBOUNCE_MS);
    };

    @Nullable private FrameLayout frame;
    /** The rows the card was last drawn from, for the icons and taps laid over its plan. */
    @NonNull private List<NotificationsWidgetData.Group> shownGroups = new ArrayList<>();
    private boolean access;
    /** Null until the first read lands. */
    @Nullable private NotificationsWidgetData.Snapshot snapshot;
    private int generation;

    public NotificationsWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                                   @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.NOTIFICATIONS, services, style);
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        this.frame = frame;
        if (isPreview()) {
            access = true;
            snapshot = sample(ui.style);
        } else {
            access = LauncherNotificationAccess.isEnabled(getContext());
        }
        render();
    }

    @Override protected void onStart() {
        generation++;
        LauncherNotificationBadgeStore.addListener(badgeListener);
        reload();
    }

    @Override protected void onStop() {
        generation++;
        LauncherNotificationBadgeStore.removeListener(badgeListener);
        services.main().removeCallbacks(reload);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        Context context = getContext();
        if (!access) {
            if (!SignalsWidgetKit.start(context, LauncherNotificationAccess.detailSettingsIntent(context))) {
                SignalsWidgetKit.start(context, LauncherNotificationAccess.listSettingsIntent());
            }
            return;
        }
        SignalsWidgetKit.start(context, SettingsActivity.createFragmentIntent(context,
            NotificationHistoryFragment.class, R.string.notif_history_title));
    }

    // ----- data -----------------------------------------------------------------------------

    private void reload() {
        services.main().removeCallbacks(reload);
        if (!isStarted() || isPreview()) return;
        if (!LauncherNotificationAccess.isEnabled(getContext())) {
            if (access) {
                access = false;
                snapshot = null;
                render();
            }
            return;
        }
        final int ticket = generation;
        final Context context = services.context();
        final int iconPx = style().dp(ICON_DP);
        try {
            services.io().execute(() -> {
                NotificationsWidgetData.Snapshot next =
                    NotificationsWidgetData.select(readRows(context), System.currentTimeMillis(), LIMIT,
                        LauncherCtlNotificationListener.activeSilentKeys());
                loadIcons(context, next.iconPackages(6), icons, iconPx);
                services.main().post(() -> apply(ticket, next));
            });
        } catch (RejectedExecutionException ignored) {
            // The page is going away.
        }
    }

    private void apply(int ticket, @NonNull NotificationsWidgetData.Snapshot next) {
        if (ticket != generation || !isStarted()) return;
        boolean changed = !access || !next.sameAs(snapshot);
        access = true;
        snapshot = next;
        if (changed) render();
    }

    /** What is in the shade now; the history's still-posted rows when the listener is not bound. */
    @WorkerThread @NonNull
    private static List<LauncherCtlNotificationEvent> readRows(@NonNull Context context) {
        boolean maskCodes = true;
        try {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
            if (preferences != null) maskCodes = preferences.isNotificationHistoryMaskCodesEnabled();
        } catch (RuntimeException ignored) { }
        List<LauncherCtlNotificationEvent> live = LauncherCtlNotificationListener.captureActive(
            LauncherNotificationBadgeStore.getActivePackages(), maskCodes);
        if (live != null) return live;
        LauncherCtlNotificationStore.Filter filter = new LauncherCtlNotificationStore.Filter();
        filter.sinceMs = System.currentTimeMillis() - NotificationsWidgetData.WINDOW_MS;
        filter.limit = LIMIT;
        return LauncherCtlNotificationStore.getInstance().query(filter);
    }

    /** Rasterises each package's icon at {@code px} square into {@code cache}, once. */
    @WorkerThread
    private static void loadIcons(@NonNull Context context, @NonNull List<String> packages,
                                  @NonNull LruCache<String, Drawable> cache, int px) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : packages) {
            if (cache.get(pkg) != null) continue;
            try {
                Drawable icon = pm.getApplicationIcon(pkg);
                Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                icon.setBounds(0, 0, px, px);
                icon.draw(new Canvas(bitmap));
                cache.put(pkg, new BitmapDrawable(context.getResources(), bitmap));
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // The bell stands in for an app we cannot draw.
            }
        }
    }

    private void clearAll() {
        NotificationsWidgetData.Snapshot data = snapshot;
        if (data == null || data.items.isEmpty()) return;
        List<String> keys = new ArrayList<>();
        for (NotificationsWidgetData.Item item : data.items) keys.add(item.key);
        try {
            services.io().execute(() -> {
                for (String key : keys) LauncherCtlNotificationListener.dismissNotification(key);
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private void openApp(@NonNull NotificationsWidgetData.Item item) {
        if (isPreview()) return;
        Context context = getContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(item.packageName);
        SignalsWidgetKit.start(context, launch);
    }

    // ----- layouts --------------------------------------------------------------------------

    /** The plan is laid out at the card's own size never zoomed. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    private void render() {
        FrameLayout target = frame;
        if (target == null) return;
        target.removeAllViews();
        BuiltinWidgetUi ui = ui();
        NotificationsWidgetData.Snapshot data = snapshot == null
            ? NotificationsWidgetData.Snapshot.EMPTY : snapshot;
        Resources res = getResources();
        NotificationsLayoutPlanner.Notice notice = null;
        List<NotificationsWidgetData.Group> groups = data.groups;
        if (!access) {
            notice = new NotificationsLayoutPlanner.Notice(
                res.getString(R.string.bw_signals_notifications_allow),
                res.getString(R.string.bw_signals_notifications_allow_hint));
            groups = new ArrayList<>();
        } else if (data.count() == 0) {
            notice = new NotificationsLayoutPlanner.Notice(snapshot == null ? ""
                : res.getString(R.string.bw_signals_notifications_empty), "");
            groups = new ArrayList<>();
        }
        shownGroups = groups;
        BuiltinWidgetSpan span = span();
        float fontScale = res.getConfiguration().fontScale;
        boolean canClear = isPreview() || LauncherCtlNotificationListener.isListenerConnected();
        NotificationsLayoutPlanner.Words words = words();
        List<NotificationsWidgetData.Group> shown = groups;
        NotificationsLayoutPlanner.Notice message = notice;
        Card card = new Card(getContext(), ui);
        card.setPlanView(new WidgetPlanView(getContext(), ui.style, (widthDp, heightDp, measure) ->
            NotificationsLayoutPlanner.plan(span, widthDp, heightDp, fontScale, shown, message,
                canClear, words, measure)));
        target.addView(card, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentDescription(describe(data));
    }

    /** The plan's words, from the app's strings. */
    @NonNull private NotificationsLayoutPlanner.Words words() {
        Resources res = getResources();
        return new NotificationsLayoutPlanner.Words() {
            @NonNull @Override public String headerMessages() {
                return res.getString(R.string.bw_signals_notifications_header_messages);
            }
            @NonNull @Override public String headerNotifications() {
                return res.getString(R.string.bw_signals_notifications_caption);
            }
            @NonNull @Override public String clearAll() {
                return res.getString(R.string.bw_signals_notifications_clear_all);
            }
            @NonNull @Override public String count(int count) { return SignalsWidgetFormats.badge(count); }
        };
    }

    /**
     * The card: the plan's view, with what a plan cannot draw laid over it from its parts' boxes —
     * the apps' icons, the badges' pills (under the numbers) and the rows' taps.
     */
    private final class Card extends FrameLayout {
        private final BuiltinWidgetUi ui;
        private final FrameLayout logical;
        private final FrameLayout under;
        private final FrameLayout over;
        @Nullable private WidgetPlanView planView;
        @Nullable private WidgetLayoutPlan synced;

        Card(@NonNull Context context, @NonNull BuiltinWidgetUi ui) {
            super(context);
            this.ui = ui;
            logical = new FrameLayout(context);
            under = new FrameLayout(context);
            over = new FrameLayout(context);
            addView(logical);
            logical.addView(under, matchParent());
        }

        void setPlanView(@NonNull WidgetPlanView view) {
            planView = view;
            logical.addView(view, matchParent());
            logical.addView(over, matchParent());
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l, height = b - t;
            logical.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            logical.layout(0, 0, width, height);
            post(this::sync);
        }

        /** Lays the overlays out for the plan the view last drew, once per plan. */
        private void sync() {
            WidgetPlanView view = planView;
            WidgetLayoutPlan plan = view == null ? null : view.plan();
            if (plan == null || plan == synced) return;
            synced = plan;
            under.removeAllViews();
            over.removeAllViews();
            float density = getResources().getDisplayMetrics().density;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int widthPx = logical.getWidth();
            for (WidgetLayoutPlan.Part part : plan.parts) {
                int n = indexOf(part.id);
                if (part.id.startsWith("badge.")) {
                    View pill = new View(getContext());
                    pill.setBackground(ui.rounded(
                        ColorUtils.setAlphaComponent(ui.style.primary, 48),
                        part.box.height * density / 2f));
                    under.addView(pill, place(part.box, density, rtl, widthPx));
                } else if (part.id.startsWith("icon.") && n >= 0 && n < shownGroups.size()) {
                    float side = Math.min(part.box.width, part.box.height);
                    over.addView(icon(shownGroups.get(n).newest, Math.round(side),
                        side * 0.7f, ui), place(part.box, density, rtl, widthPx));
                } else if (part.id.startsWith("row.") && n >= 0 && n < shownGroups.size()) {
                    if (!isPreview()) {
                        View tap = new View(getContext());
                        clickable(tap, shownGroups.get(n).newest);
                        over.addView(tap, place(part.box, density, rtl, widthPx));
                    }
                } else if (part.id.equals("clear.touch") && !isPreview()) {
                    View tap = new View(getContext());
                    tap.setClickable(true);
                    tap.setFocusable(true);
                    tap.setContentDescription(getResources().getString(
                        R.string.bw_signals_notifications_clear_all_description));
                    tap.setOnClickListener(v -> clearAll());
                    over.addView(tap, place(part.box, density, rtl, widthPx));
                }
            }
        }

        private int indexOf(@NonNull String id) {
            int dot = id.indexOf('.');
            if (dot < 0) return -1;
            String rest = id.substring(dot + 1);
            int next = rest.indexOf('.');
            try {
                return Integer.parseInt(next < 0 ? rest : rest.substring(0, next));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @NonNull private FrameLayout.LayoutParams place(@NonNull WidgetLayoutPlan.Box box,
                                                        float density, boolean rtl, int widthPx) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.round(box.width * density), Math.round(box.height * density));
            params.leftMargin = Math.round(rtl ? widthPx - (box.left + box.width) * density
                : box.left * density);
            params.topMargin = Math.round(box.top * density);
            return params;
        }
    }

    @NonNull private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * The app's icon in a {@code boxDp} square: the cached raster, the sample's glyph in the
     * picker, or the bell while an icon is missing.
     */
    @NonNull
    private View icon(@NonNull NotificationsWidgetData.Item item, int boxDp, float glyphDp,
                      @NonNull BuiltinWidgetUi ui) {
        View view;
        Drawable cached = item.glyph == null ? icons.get(item.packageName) : null;
        if (cached != null) {
            ImageView image = new ImageView(getContext());
            Drawable.ConstantState state = cached.getConstantState();
            image.setImageDrawable(state != null ? state.newDrawable(getResources()) : cached);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            view = image;
        } else if (item.glyph != null) {
            view = ui.glyph(item.glyph, glyphDp, item.glyphColor);
        } else {
            view = ui.glyph(NotificationsLayoutPlanner.GLYPH_BELL, glyphDp,
                ui.style.onSurfaceVariant);
        }
        return view;
    }

    /** A row that opens its app; inert in the picker, whose card takes the tap. */
    private void clickable(@NonNull View view, @NonNull NotificationsWidgetData.Item item) {
        if (isPreview()) return;
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(getResources().getString(
            R.string.bw_signals_notifications_open_app, item.app) + ", " + item.who + ", " + item.text);
        view.setOnClickListener(v -> openApp(item));
    }

    @NonNull
    private String appsLabel(int apps) {
        return getResources().getQuantityString(R.plurals.bw_signals_notifications_apps, apps, apps);
    }

    @NonNull
    private CharSequence describe(@NonNull NotificationsWidgetData.Snapshot data) {
        Resources res = getResources();
        if (!access) return res.getString(R.string.bw_signals_notifications_description_denied);
        NotificationsWidgetData.Item newest = data.newest();
        if (newest == null) return res.getString(R.string.bw_signals_notifications_description_empty);
        String count = res.getQuantityString(R.plurals.bw_signals_notifications_count_spoken,
            data.count(), data.count());
        return res.getString(R.string.bw_signals_notifications_description, count,
            appsLabel(data.apps), newest.who, newest.app);
    }

    // ----- picker sample --------------------------------------------------------------------

    /**
     * The design's sample: five messages, three from Mum on WhatsApp and two from Alex on
     * Messages, newest first.
     */
    @NonNull
    private static NotificationsWidgetData.Snapshot sample(@NonNull BuiltinWidgetStyle s) {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        List<NotificationsWidgetData.Item> items = new ArrayList<>();
        Object[][] rows = {
            {"WhatsApp", "Mum", "Are you coming for dinner tonight? Let me know", 14, 10, "", s.done},
            {"Messages", "Alex", "I've sent the updated plans. See you tomorrow.", 13, 40, "", s.primary},
            {"WhatsApp", "Mum", "Dinner is at eight", 13, 32, "", s.done},
            {"Messages", "Alex", "Running late", 12, 10, "", s.primary},
            {"WhatsApp", "Mum", "Call me when you can", 9, 15, "", s.done},
        };
        for (int i = 0; i < rows.length; i++) {
            Object[] row = rows[i];
            long time = today.atTime((Integer) row[3], (Integer) row[4]).atZone(zone).toInstant().toEpochMilli();
            items.add(new NotificationsWidgetData.Item("sample" + i, "sample." + row[0], (String) row[0],
                (String) row[1], (String) row[2], time, (String) row[5], (Integer) row[6], true));
        }
        return new NotificationsWidgetData.Snapshot(items, 2);
    }
}
