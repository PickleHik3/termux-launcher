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
    /** The largest an app icon is drawn (inside the 4×1 avatar disc); icons are kept at this size. */
    private static final int ICON_DP = 28;
    private static final String BELL = "\uf0f3";
    private static final String CLEAR_TAG = "clear";

    private final LruCache<String, Drawable> icons = new LruCache<>(ICON_ENTRIES);
    private final Runnable reload = this::reload;
    private final LauncherNotificationBadgeStore.Listener badgeListener = packages -> {
        services.main().removeCallbacks(reload);
        services.main().postDelayed(reload, DEBOUNCE_MS);
    };

    @Nullable private FrameLayout frame;
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
                    NotificationsWidgetData.select(readRows(context), System.currentTimeMillis(), LIMIT);
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

    private void render() {
        FrameLayout target = frame;
        if (target == null) return;
        target.removeAllViews();
        BuiltinWidgetUi ui = ui();
        NotificationsWidgetData.Snapshot data = snapshot == null
            ? NotificationsWidgetData.Snapshot.EMPTY : snapshot;
        View root;
        if (!access) {
            root = buildNotice(span(), ui, true);
        } else if (data.count() == 0) {
            root = buildNotice(span(), ui, false);
        } else {
            switch (span()) {
                case ONE_BY_ONE: root = oneByOne(data, ui); break;
                case TWO_BY_ONE: root = twoByOne(data, ui); break;
                case TWO_BY_TWO: root = twoByTwo(data, ui); break;
                case FOUR_BY_ONE: root = fourByOne(data, ui); break;
                default: root = fourByTwo(data, ui); break;
            }
        }
        target.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentDescription(describe(data));
    }

    /** The no-access ({@code denied}) and nothing-new states: a quiet bell and one line. */
    @NonNull
    private View buildNotice(@NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui, boolean denied) {
        BuiltinWidgetStyle s = ui.style;
        Resources res = getResources();
        boolean loading = !denied && snapshot == null;
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            String line = loading ? "" : res.getString(denied
                ? R.string.bw_signals_notifications_allow_short : R.string.bw_signals_notifications_empty_short);
            TextView label = ui.text(line, 11f, s.sansBold, denied ? s.primary : s.onSurfaceVariant);
            LinearLayout column = ui.column(6, ui.glyph(BELL, 26f, s.onSurfaceVariant), label);
            column.setGravity(Gravity.CENTER);
            inset(column, 8, 0, 8, 0, ui);
            return column;
        }
        String line = loading ? "" : res.getString(denied
            ? R.string.bw_signals_notifications_allow : R.string.bw_signals_notifications_empty);
        TextView title = ui.text(line, 12.5f, s.sansBold, denied ? s.primary : s.onSurfaceVariant);
        View body;
        if (denied) {
            TextView hint = ui.text(res.getString(R.string.bw_signals_notifications_allow_hint), 11f,
                s.sans, s.onSurfaceVariant);
            body = ui.column(3, title, hint);
        } else {
            body = title;
        }
        if (!span.isTall()) {
            LinearLayout row = ui.row(12, ui.glyph(BELL, 22f, s.onSurfaceVariant), BuiltinWidgetUi.flex(body));
            inset(row, span.isWide() ? 16 : 14, 0, 14, 0, ui);
            return row;
        }
        LinearLayout centre = ui.column(8, ui.glyph(BELL, 26f, s.onSurfaceVariant), body);
        centre.setGravity(Gravity.CENTER);
        if (body instanceof LinearLayout) ((LinearLayout) body).setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout column = ui.column(8, wide(header(ui, span, null)), BuiltinWidgetUi.flexTall(centre));
        inset(column, span.isWide() ? 16 : 12, 12, span.isWide() ? 16 : 12, 12, ui);
        return column;
    }

    @NonNull
    private View oneByOne(@NonNull NotificationsWidgetData.Snapshot data, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        FrameLayout bell = new FrameLayout(getContext());
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        // The badge hangs 6dp above and 12dp right of the bell; the same margin on the other
        // sides keeps the bell itself centred.
        glyphParams.setMargins(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        bell.addView(ui.glyph(BELL, 26f, s.onSurface), glyphParams);
        TextView badge = ui.text(SignalsWidgetFormats.badge(data.count()), 10.5f,
            SignalsWidgetKit.monoBold(s), onBadge(s));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(ui.dp(18));
        badge.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        badge.setBackground(ui.rounded(s.error, ui.dp(9)));
        bell.addView(badge, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(18), Gravity.TOP | Gravity.END));
        TextView apps = ui.mono(appsLabel(data.apps), 10.5f);
        LinearLayout column = ui.column(6, bell, apps);
        column.setGravity(Gravity.CENTER);
        return column;
    }

    @NonNull
    private View twoByOne(@NonNull NotificationsWidgetData.Snapshot data, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        NotificationsWidgetData.Item item = data.items.get(0);
        TextView app = ui.text(item.app, 11f, s.sansBold, s.onSurfaceVariant);
        LinearLayout appRow = ui.row(6, icon(item, 16, 11f, ui), BuiltinWidgetUi.flex(app),
            ui.mono(time(item), 10.5f));
        TextView who = ui.sans(item.who, 13f, true);
        TextView text = ui.text(item.text, 11.5f, s.sans, s.onSurfaceVariant);
        LinearLayout column = ui.column(5, wide(appRow), who, text);
        column.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inset(column, 14, 0, 14, 0, ui);
        clickable(column, item);
        return column;
    }

    @NonNull
    private View twoByTwo(@NonNull NotificationsWidgetData.Snapshot data, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        SignalsFitColumn list = new SignalsFitColumn(getContext(), ui.dp(8));
        for (int i = 0; i < Math.min(3, data.count()); i++) {
            NotificationsWidgetData.Item item = data.items.get(i);
            LinearLayout lines = ui.column(0,
                ui.text(item.who, 11.5f, s.sansBold, s.onSurface),
                ui.text(item.text, 10.5f, s.sans, s.onSurfaceVariant));
            View icon = icon(item, 18, 14f, ui);
            icon.setPadding(0, ui.dp(1), 0, 0);
            LinearLayout card = ui.row(8, icon, BuiltinWidgetUi.flex(lines));
            card.setGravity(Gravity.TOP);
            card.setBackground(ui.rounded(s.container, ui.innerRadius(12)));
            card.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
            clickable(card, item);
            list.addView(card);
        }
        LinearLayout column = ui.column(8, wide(header(ui, BuiltinWidgetSpan.TWO_BY_TWO, data)),
            BuiltinWidgetUi.flexTall(list));
        inset(column, 12, 12, 12, 12, ui);
        return column;
    }

    @NonNull
    private View fourByOne(@NonNull NotificationsWidgetData.Snapshot data, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        NotificationsWidgetData.Item item = data.items.get(0);
        SpannableStringBuilder head = new SpannableStringBuilder();
        SignalsWidgetKit.append(head, item.who, s.sansBold, 1f, s.onSurface);
        head.append("  ");
        SignalsWidgetKit.append(head, getResources().getString(R.string.bw_signals_notifications_app_time,
            item.app, time(item)), s.monoMedium, 10.5f / 13f, s.onSurfaceVariant);
        TextView headline = ui.text(head, 13f, s.sansBold, s.onSurface);
        TextView text = ui.text(item.text, 12f, s.sans, s.onSurfaceVariant);
        LinearLayout lines = ui.column(3, headline, text);
        LinearLayout newest = ui.row(12, disc(item, 40, 28, 18f, false, ui), BuiltinWidgetUi.flex(lines));
        clickable(newest, item);
        List<View> right = new ArrayList<>();
        List<NotificationsWidgetData.Item> others = data.otherApps(2);
        if (!others.isEmpty()) {
            LinearLayout stack = ui.row(0);
            for (int i = 0; i < others.size(); i++) {
                FrameLayout small = disc(others.get(i), 22, 14, 10f, true, ui);
                if (i > 0) ((LinearLayout.LayoutParams) small.getLayoutParams()).leftMargin = -ui.dp(6);
                stack.addView(small);
            }
            right.add(stack);
        }
        String more = SignalsWidgetFormats.overflow(data.count() - 1);
        if (!more.isEmpty()) right.add(ui.text(more, 11f, SignalsWidgetKit.monoBold(s), s.onSurfaceVariant));
        LinearLayout row = right.isEmpty()
            ? ui.row(12, BuiltinWidgetUi.flex(newest))
            : ui.row(12, BuiltinWidgetUi.flex(newest), ui.row(6, right.toArray(new View[0])));
        inset(row, 16, 0, 16, 0, ui);
        return row;
    }

    @NonNull
    private View fourByTwo(@NonNull NotificationsWidgetData.Snapshot data, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        SignalsFitColumn list = new SignalsFitColumn(getContext(), ui.dp(2));
        for (int i = 0; i < Math.min(4, data.count()); i++) {
            NotificationsWidgetData.Item item = data.items.get(i);
            SpannableStringBuilder head = new SpannableStringBuilder();
            SignalsWidgetKit.append(head, item.who, s.sansBold, 1f, s.onSurface);
            head.append("  ");
            SignalsWidgetKit.append(head, item.app, s.monoMedium, 10f / 12f, s.onSurfaceVariant);
            LinearLayout lines = ui.column(0, ui.text(head, 12f, s.sansBold, s.onSurface),
                ui.text(item.text, 11f, s.sans, s.onSurfaceVariant));
            LinearLayout line = ui.row(10, icon(item, 18, 14f, ui), BuiltinWidgetUi.flex(lines),
                ui.mono(time(item), 10.5f));
            line.setPadding(0, ui.dp(5), 0, ui.dp(5));
            LinearLayout entry = ui.column(0, ui.divider(false), wide(line));
            clickable(entry, item);
            list.addView(entry);
        }
        LinearLayout header = header(ui, BuiltinWidgetSpan.FOUR_BY_TWO, data);
        header.setPadding(0, 0, 0, ui.dp(4));
        LinearLayout column = ui.column(2, wide(header), BuiltinWidgetUi.flexTall(list));
        inset(column, 16, 12, 16, 12, ui);
        View clear = header.findViewWithTag(CLEAR_TAG);
        if (clear != null) SignalsWidgetKit.expandTouch(column, clear, ui);
        return column;
    }

    /**
     * The caption row of the tall layouts: the count in red on the right at 2×2, "Clear all" at
     * 4×2 when there is something to clear and the listener can clear it.
     */
    @NonNull
    private LinearLayout header(@NonNull BuiltinWidgetUi ui, @NonNull BuiltinWidgetSpan span,
                                @Nullable NotificationsWidgetData.Snapshot data) {
        BuiltinWidgetStyle s = ui.style;
        Resources res = getResources();
        View caption = ui.caption(res.getString(R.string.bw_signals_notifications_caption), BELL,
            res.getString(span.isWide() ? R.string.bw_signals_notifications_pane
                : R.string.bw_signals_notifications_pane_short));
        LinearLayout header;
        if (data == null || data.count() == 0) {
            header = ui.row(8, BuiltinWidgetUi.flex(caption));
        } else if (span.isWide()) {
            boolean canClear = isPreview() || LauncherCtlNotificationListener.isListenerConnected();
            if (canClear) {
                TextView clear = ui.text(res.getString(R.string.bw_signals_notifications_clear_all), 11f,
                    s.sansBold, s.primary);
                if (!isPreview()) {
                    clear.setTag(CLEAR_TAG);
                    clear.setClickable(true);
                    clear.setFocusable(true);
                    clear.setContentDescription(
                        res.getString(R.string.bw_signals_notifications_clear_all_description));
                    clear.setOnClickListener(v -> clearAll());
                }
                header = ui.row(8, BuiltinWidgetUi.flex(caption), clear);
            } else {
                header = ui.row(8, BuiltinWidgetUi.flex(caption));
            }
        } else {
            TextView count = ui.text(SignalsWidgetFormats.badge(data.count()), 10.5f,
                SignalsWidgetKit.monoBold(s), s.error);
            header = ui.row(8, BuiltinWidgetUi.flex(caption), count);
        }
        if (!span.isWide()) header.setPadding(ui.dp(2), 0, ui.dp(2), 0);
        return header;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * The app's icon in a {@code boxDp} square: the cached raster, the sample's glyph in the
     * picker, or the bell while an icon is missing.
     */
    @NonNull
    private View icon(@NonNull NotificationsWidgetData.Item item, int boxDp, float glyphSp,
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
            view = ui.glyph(item.glyph, glyphSp, item.glyphColor);
        } else {
            view = ui.glyph(BELL, glyphSp, ui.style.onSurfaceVariant);
        }
        return BuiltinWidgetUi.size(view, ui.dp(boxDp), ui.dp(boxDp));
    }

    /** A {@code container} disc holding the app icon, with the card-coloured rim when stacked. */
    @NonNull
    private FrameLayout disc(@NonNull NotificationsWidgetData.Item item, int discDp, int iconDp,
                             float glyphSp, boolean rim, @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle s = ui.style;
        FrameLayout disc = new FrameLayout(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(s.container);
        if (rim) shape.setStroke(ui.dp(2), s.card);
        disc.setBackground(shape);
        disc.addView(icon(item, iconDp, glyphSp, ui),
            new FrameLayout.LayoutParams(ui.dp(iconDp), ui.dp(iconDp), Gravity.CENTER));
        disc.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(discDp), ui.dp(discDp)));
        return disc;
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
    private static <V extends View> V wide(@NonNull V view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    @NonNull
    private String time(@NonNull NotificationsWidgetData.Item item) {
        return SignalsWidgetFormats.clockOrDay(item.time, System.currentTimeMillis(),
            ZoneId.systemDefault(), DateFormat.is24HourFormat(getContext()), Locale.getDefault());
    }

    @NonNull
    private String appsLabel(int apps) {
        return getResources().getQuantityString(R.plurals.bw_signals_notifications_apps, apps, apps);
    }

    /** Text on the red badge: the page colour, as the design has it, opaque even on glass. */
    @ColorInt
    private static int onBadge(@NonNull BuiltinWidgetStyle s) {
        return ColorUtils.setAlphaComponent(s.card, 255);
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

    /** The design's sample: seven notifications from four apps, newest from Ahmed on WhatsApp. */
    @NonNull
    private static NotificationsWidgetData.Snapshot sample(@NonNull BuiltinWidgetStyle s) {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        List<NotificationsWidgetData.Item> items = new ArrayList<>();
        Object[][] rows = {
            {"WhatsApp", "Ahmed", "Sent the PO, check mail when you can", 13, 32, "\uf232", s.done},
            {"Gmail", "Elsevier", "Your 2027 renewal quote is ready", 12, 10, "\uf0e0", s.error},
            {"GitHub", "termux-launcher", "PR #412 approved: widget pane rim", 11, 48, "\uf09b", s.onSurface},
            {"Telegram", "Termux Launcher", "v1.0.1 released", 9, 15, "\uf2c6", s.primary},
            {"WhatsApp", "Sara", "On my way", 9, 2, "\uf232", s.done},
            {"Gmail", "Zoho Desk", "Ticket #2291 was updated", 8, 40, "\uf0e0", s.error},
            {"GitHub", "dependabot", "Bump gradle to 8.13", 8, 5, "\uf09b", s.onSurface},
        };
        for (int i = 0; i < rows.length; i++) {
            Object[] row = rows[i];
            long time = today.atTime((Integer) row[3], (Integer) row[4]).atZone(zone).toInstant().toEpochMilli();
            items.add(new NotificationsWidgetData.Item("sample" + i, "sample." + row[0], (String) row[0],
                (String) row[1], (String) row[2], time, (String) row[5], (Integer) row[6]));
        }
        return new NotificationsWidgetData.Snapshot(items, 4);
    }
}
