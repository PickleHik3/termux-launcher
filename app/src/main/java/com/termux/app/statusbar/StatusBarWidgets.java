package com.termux.app.statusbar;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.drawable.Drawable;
import com.google.android.material.color.MaterialColors;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.R;

/**
 * The status bar's ink, its widgets and its notifications mark, moved out of the activity line
 * for line (ADR 0018 wave B step one, D17). {@link StatusBarController} is its only caller and
 * hands it the bar's {@link StatusBarController.Host}.
 */
final class StatusBarWidgets {

    @NonNull private final StatusBarController.Host mHost;

    StatusBarWidgets(@NonNull StatusBarController.Host host) {
        mHost = host;
    }

    /** The top stack's renderer writes a crossing's blended inks through this bar's views. */
    void bindTopStackInks() {
        mHost.topStack().setInks(this::applyStatusInks);
    }

    /**
     * Hears the notifications the dock badges its icons with, while the status bar's
     * notifications indicator is on; null while it is off.
     */
    @Nullable private com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.Listener
        mStatusNotificationsListener;

    /**
     * The status bar's notifications mark ({@code status_widget_notifications}): a bell and the
     * count while notifications wait, the same ones the dock badges ({@link
     * com.termux.app.statusbar.StatusNotificationsMark}); gone with none, or with the indicator
     * off. Listens to the badge store only while the indicator is on.
     */
    private void syncStatusNotificationsMark() {
        boolean enabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetNotificationsEnabled();
        if (enabled && mStatusNotificationsListener == null) {
            mStatusNotificationsListener = packages -> mHost.runOnUiThread(this::showStatusNotificationsMark);
            // Adding it reports the packages at once, which draws the mark.
            com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.addListener(
                mStatusNotificationsListener);
            return;
        }
        if (!enabled && mStatusNotificationsListener != null) {
            com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.removeListener(
                mStatusNotificationsListener);
            mStatusNotificationsListener = null;
        }
        showStatusNotificationsMark();
    }

    private void showStatusNotificationsMark() {
        com.termux.app.statusbar.StatusBarWidgetView mark =
            mHost.findViewById(R.id.terminal_status_widget_notifications);
        if (mark == null) return;
        boolean enabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetNotificationsEnabled();
        int count = enabled ? com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
            .countFor(com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
                .getActivePackages()) : 0;
        boolean shown = com.termux.app.statusbar.StatusNotificationsMark.shown(enabled, count);
        if (shown && mark.getTag() == null) {
            mark.setTag("wired");
            mark.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            mark.setIconResource(R.drawable.ic_rl_bell);
        }
        if (shown)
            mark.setValue(com.termux.app.statusbar.StatusNotificationsMark.label(count));
        int visibility = shown ? View.VISIBLE : View.GONE;
        boolean changed = mark.getVisibility() != visibility;
        if (changed) {
            mark.setVisibility(visibility);
            syncRowDots();
            syncStatusBarInk();
        }
    }

    /**
     * Shows or hides the font toggle's pill, with the mark for the focused pane's state. Only a
     * change is written, and the row's dots follow it. True when its visibility changed.
     */
    boolean syncFontToggle(boolean shown, boolean compact) {
        com.termux.app.statusbar.StatusBarWidgetView pill =
            mHost.findViewById(R.id.terminal_status_widget_font_toggle);
        if (pill == null) return false;
        if (shown) pill.dressAsFontToggle(compact);
        int visibility = shown ? View.VISIBLE : View.GONE;
        boolean changed = pill.getVisibility() != visibility;
        if (changed) {
            pill.setVisibility(visibility);
            syncRowDots();
            syncStatusBarInk();
        }
        return changed;
    }

    // ---- The row's order and the dots between its items ----

    /** The order the row's group holds its items in now; null before the first sync. */
    @Nullable private StatusRowItem[] mRowOrder;
    /** Reused by {@link #syncRowDots}: each item shown, in row order, and its dot. */
    private final boolean[] mRowShown = new boolean[StatusRowItem.values().length];
    private final boolean[] mRowDots = new boolean[StatusRowItem.values().length];

    /**
     * Puts the row's items (each its dot, then its widget) in the order the Status bar card
     * stores. Compared first: the group is only rebuilt when the stored order changed, never
     * from measure, layout or draw.
     */
    void syncRowOrder() {
        StatusRowItem[] order = StatusRowOrder.parse(
            mHost.preferences() == null ? null : mHost.preferences().getStatusRowOrder());
        if (java.util.Arrays.equals(order, mRowOrder)) return;
        android.view.ViewGroup group = mHost.findViewById(R.id.terminal_status_stats_cluster);
        if (group == null) return;
        mRowOrder = order;
        if (holdsInOrder(group, order)) return;
        View[] pairs = new View[order.length * 2];
        for (int i = 0; i < order.length; i++) {
            pairs[2 * i] = group.findViewById(order[i].dotId);
            pairs[2 * i + 1] = group.findViewById(order[i].widgetId);
        }
        for (View view : pairs) {
            if (view != null) group.removeView(view);
        }
        // Each view keeps its own layout params; anything else in the group stays after them.
        int at = 0;
        for (View view : pairs) {
            if (view != null) group.addView(view, at++);
        }
        syncRowDots();
    }

    /** Whether {@code group}'s children already read {@code order}'s dots and widgets. */
    private static boolean holdsInOrder(@NonNull android.view.ViewGroup group,
                                        @NonNull StatusRowItem[] order) {
        int at = 0;
        for (StatusRowItem item : order) {
            for (int id : new int[] {item.dotId, item.widgetId}) {
                if (group.findViewById(id) == null) continue;
                if (at >= group.getChildCount() || group.getChildAt(at).getId() != id) return false;
                at++;
            }
        }
        return true;
    }

    /**
     * The dots between the row's items, from which items show right now ({@link
     * StatusRowOrder#dots}): one between two shown neighbours, none at either end. The one place
     * a dot's visibility is written; every item that shows or hides calls it.
     */
    void syncRowDots() {
        StatusRowItem[] order = mRowOrder != null ? mRowOrder : StatusRowOrder.defaults();
        for (int i = 0; i < order.length; i++) {
            View widget = mHost.findViewById(order[i].widgetId);
            mRowShown[i] = widget != null && widget.getVisibility() == View.VISIBLE;
        }
        StatusRowOrder.dots(mRowShown, mRowDots);
        for (int i = 0; i < order.length; i++) {
            com.termux.app.statusbar.MaterialDotSeparatorView dot =
                mHost.findViewById(order[i].dotId);
            if (dot == null) continue;
            dot.setColorRole(order[i].dotRole);
            int visibility = mRowDots[i] ? View.VISIBLE : View.GONE;
            if (dot.getVisibility() != visibility) dot.setVisibility(visibility);
        }
    }

    // ---- What the status bar's content is drawn in, measured against what it is standing on ----

    /** The row's items, whose ink slots are keyed by item ({@link StatusInkSlots}), not place. */
    private static final StatusRowItem[] INK_ITEMS = StatusRowItem.values();

    /** The bar's own rect on screen: one measurement, and every ink in the bar comes out of it. */
    private final android.graphics.Rect mStatusInkRect = new android.graphics.Rect();

    /**
     * What every piece of the status bar's own content is drawn in, resolved from one measurement
     * of what the bar is actually standing on.
     *
     * <p>The bar carries four contrast tiers at once — the stats' labels are body text, their icons
     * and the place marks are graphics, the separator dots are decoration — and a band can only
     * carry one veil. So the band is resolved <em>once</em> here, at its strictest tier and in the
     * role colour of its most saturated content, and every other ink on it comes from
     * {@link com.termux.app.chrome.ChromeInk#inkOn} at its own looser tier, which asks for no more
     * veil. Anything else in this activity that stands on the status bar should read the resolution
     * this method takes rather than call {@code onGlass} for the band a second time: the last
     * caller in a frame would otherwise decide the veil for all of them.</p>
     *
     * <p>The band is measured on the pane this content is laid out in — the top pane's glass,
     * shared with the window chips — and not on the strip that continues that glass under the
     * system status bar, which nothing stands on. {@code ChromeInk} owns that mapping; this method
     * names the band and asks its question.</p>
     *
     * <p>Silently does nothing until the bar has been laid out and a wallpaper sample exists; the
     * accessory render pass runs this again, and the chrome asks for a pass of its own whenever a
     * band's veil moves.</p>
     */
    void syncStatusBarInk() {
        // A crossing's renderer owns the content's inks: it wrote both ends' blend (resolved once
        // at begin) and lands on the end's; the settle's sync then measures at rest.
        TopStackRenderer top = mHost.topStack();
        if (top.crossing()) {
            top.requestApply();
            return;
        }
        if (!resolveStatusInks(mRestInks)) return;
        applyStatusInks(mRestInks);
        top.restInks(mRestInks);
        applySessionsBand(mResolvedBand);
        com.termux.app.statusbar.MediaWidgetView media = mHost.findViewById(R.id.terminal_media_widget);
        if (media != null) media.onThemeChanged();
        com.termux.app.terminal.TerminalClockWidget clock = mHost.findViewById(R.id.terminal_clock_widget);
        if (clock != null) clock.onThemeChanged();
    }

    /** What the bar's content wears at rest, in {@link StatusInkSlots} order; reused. */
    private final int[] mRestInks = new int[StatusInkSlots.COUNT];
    /** The band the last {@link #resolveStatusInks} measured: what the sessions chip is inked on. */
    @Nullable private com.termux.app.chrome.OnGlass.Resolution mResolvedBand;

    /** The band the last {@link #resolveStatusInks} measured; null before the first. */
    @Nullable com.termux.app.chrome.OnGlass.Resolution resolvedBand() {
        return mResolvedBand;
    }

    /**
     * Measures every ink the bar's content is drawn in into {@code out} ({@link StatusInkSlots}),
     * writing nothing to a view. False, with {@code out} untouched, until the bar has been laid
     * out and a wallpaper sample exists.
     */
    boolean resolveStatusInks(@NonNull int[] out) {
        return resolveStatusInks(out, false, 0);
    }

    /**
     * {@link #resolveStatusInks(int[])} over {@code pageGround}, another page's ground, recording
     * nothing ({@link com.termux.app.chrome.ChromeInk#probeOnGlass}): the inks the bar lands on at
     * that page, read as a crossing toward it begins (#283).
     */
    boolean probeStatusInks(@NonNull int[] out, int pageGround) {
        return resolveStatusInks(out, true, pageGround);
    }

    private boolean resolveStatusInks(@NonNull int[] out, boolean probe, int pageGround) {
        com.termux.app.chrome.ChromeInk ink = mHost.chrome().ink();
        if (!ink.bandRect(com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR, mStatusInkRect))
            return false;
        int primary = statusInkSeed(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.PRIMARY);
        // A real ink pair: the primary role toned dark (about 30) for pale glass and pale (about
        // 85) for dark glass, so the band can flip with the chrome's polarity rather than buying
        // a veil to keep one ink legible.
        double primaryTone = com.termux.app.theme.SchemeTone.tone(primary);
        int darkPrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 30d - primaryTone);
        int palePrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 85d - primaryTone);
        com.termux.app.chrome.OnGlass.Resolution band = probe
            ? ink.probeOnGlass(com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR,
                mStatusInkRect, darkPrimary, palePrimary,
                com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT, pageGround)
            : ink.onGlass(com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR,
                mStatusInkRect, darkPrimary, palePrimary,
                com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT);
        if (!probe) mResolvedBand = band;

        // A muted widget is the AI glyph's few seconds of afterlife: the state is said by the
        // neutral role, and the fade is allowed only as far as the backdrop can carry it.
        int muted = com.termux.app.statusbar.StatusBarInk.inkAtAlpha(band.surface,
            MaterialColors.getColor(mHost.context(), com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                primary),
            com.termux.app.statusbar.StatusBarInk.MUTED_ALPHA,
            com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT);

        for (int i = 0; i < INK_ITEMS.length; i++) {
            int at = StatusInkSlots.widget(i);
            com.termux.app.statusbar.StatusBarWidgetView widget =
                mHost.findViewById(INK_ITEMS[i].widgetId);
            if (widget == null) {
                out[at + StatusInkSlots.WIDGET_BODY] = 0;
                out[at + StatusInkSlots.WIDGET_LARGE] = 0;
                out[at + StatusInkSlots.WIDGET_MUTED] = 0;
                continue;
            }
            int seed = statusInkSeed(widget.colorRole());
            out[at + StatusInkSlots.WIDGET_BODY] =
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT);
            out[at + StatusInkSlots.WIDGET_LARGE] =
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT);
            out[at + StatusInkSlots.WIDGET_MUTED] = muted;
        }
        for (int i = 0; i < INK_ITEMS.length; i++) {
            com.termux.app.statusbar.MaterialDotSeparatorView dot =
                mHost.findViewById(INK_ITEMS[i].dotId);
            if (dot == null) {
                out[StatusInkSlots.dot(i)] = 0;
                continue;
            }
            int seed = statusInkSeed(dot.colorRole());
            out[StatusInkSlots.dot(i)] = ink.inkOn(band, seed, seed,
                com.termux.app.chrome.OnGlass.TARGET_DECORATION);
        }
        out[StatusInkSlots.LENS_SURFACE] = band.surface;
        out[StatusInkSlots.PINNED_SURFACE] = band.surface;
        out[StatusInkSlots.CLOCK_SURFACE] = band.surface;
        out[StatusInkSlots.CLOCK_INK] = band.ink;
        return true;
    }

    /**
     * Writes {@code inks} ({@link StatusInkSlots}) to the bar's content: each view takes its
     * colour only when it differs from the one it wears (the views compare), so an unchanged
     * array writes nothing. The sessions chip and the window chips are Terminal content that
     * slides with the chips and does not blend ({@link #applySessionsBand}); the media widget and
     * the clock's theme are the scheme's, not the band's.
     */
    void applyStatusInks(@NonNull int[] inks) {
        for (int i = 0; i < INK_ITEMS.length; i++) {
            com.termux.app.statusbar.StatusBarWidgetView widget =
                mHost.findViewById(INK_ITEMS[i].widgetId);
            if (widget == null) continue;
            int at = StatusInkSlots.widget(i);
            widget.setInk(inks[at + StatusInkSlots.WIDGET_BODY],
                inks[at + StatusInkSlots.WIDGET_LARGE], inks[at + StatusInkSlots.WIDGET_MUTED]);
        }
        for (int i = 0; i < INK_ITEMS.length; i++) {
            com.termux.app.statusbar.MaterialDotSeparatorView dot =
                mHost.findViewById(INK_ITEMS[i].dotId);
            if (dot != null) dot.setInk(inks[StatusInkSlots.dot(i)]);
        }
        com.termux.app.statusbar.StatusBarLensView lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setBandSurface(inks[StatusInkSlots.LENS_SURFACE]);
        com.termux.app.statusbar.PinnedNotificationsView pinned =
            mHost.findViewById(R.id.terminal_pinned_notifications);
        if (pinned != null) pinned.setBandSurface(inks[StatusInkSlots.PINNED_SURFACE]);
        com.termux.app.terminal.TerminalClockWidget clock = mHost.findViewById(R.id.terminal_clock_widget);
        if (clock != null) {
            clock.setBandInk(inks[StatusInkSlots.CLOCK_SURFACE], inks[StatusInkSlots.CLOCK_INK]);
        }
    }

    /**
     * The sessions chip is inked on {@code band}, the Terminal end's: it slides with the window
     * chips and does not blend across a crossing. Nothing for a null band.
     */
    void applySessionsBand(@Nullable com.termux.app.chrome.OnGlass.Resolution band) {
        if (band == null) return;
        com.termux.app.statusbar.SessionsIndicatorView sessions =
            mHost.findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null) sessions.setBandInk(mHost.chrome().ink(), band);
    }

    /** The role colour a status tier is written in; its hue is the tier, and the toning keeps it. */
    private int statusInkSeed(@NonNull com.termux.app.statusbar.StatusBarWidgetView.ColorRole role) {
        return com.termux.app.statusbar.StatusBarWidgetView.seedFor(mHost.context(), role);
    }

    // ---- The battery chip: a drawn battery and the percentage, read from the sticky broadcast ----

    /** The picture the battery widget draws; null until the widget has been dressed. */
    @Nullable private com.termux.app.statusbar.BatteryIconDrawable mBatteryIcon;
    @Nullable private com.termux.app.statusbar.BatteryChipController mBattery;
    /** Whether the window is started: the battery is listened to only while it is. */
    private boolean mBatteryWindowStarted;

    /** The window came to the front or left it; the battery listens only while it is in front. */
    void setBatteryWindowStarted(boolean started) {
        if (mBatteryWindowStarted == started) return;
        mBatteryWindowStarted = started;
        com.termux.app.statusbar.StatusBarWidgetView battery =
            mHost.findViewById(R.id.terminal_status_widget_battery);
        boolean on = mHost.preferences() != null && mHost.preferences().isStatusWidgetBatteryEnabled();
        if (battery != null) syncBatteryChip(on);
    }

    /**
     * Listens to the battery while the switch is on and the window is started, and puts the
     * latest reading on the chip (the unknown chip, "--" and an empty outline, before the first).
     */
    private void syncBatteryChip(boolean on) {
        if (mBattery == null) {
            if (!on) return;
            mBattery = new com.termux.app.statusbar.BatteryChipController(mHost.context(),
                this::showBattery);
        }
        mBattery.setListening(on && mBatteryWindowStarted);
        if (on) showBattery(mBattery.latest());
    }

    private void showBattery(@NonNull com.termux.app.statusbar.BatteryChipState state) {
        com.termux.app.statusbar.StatusBarWidgetView battery =
            mHost.findViewById(R.id.terminal_status_widget_battery);
        if (battery == null || mBatteryIcon == null) return;
        mBatteryIcon.setState(state);
        battery.setValue(state.text());
    }

    /** The theme's error colour, as the rest of the chrome reads it (the scheme chrome included). */
    private int batteryErrorColor() {
        android.content.Context context = mHost.context();
        int fallback = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError,
            androidx.core.content.ContextCompat.getColor(context, R.color.termux_error));
        return MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError, fallback);
    }

    /** Opens Android's battery screen; nothing happens where the phone has none. */
    private void openBatteryUsage() {
        android.content.Context context = mHost.context();
        android.content.Intent intent =
            new android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) return;
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
            // No battery screen to open, or it refused: the chip is a glance, not a promise.
        }
    }

    // ---- Trailing status widgets (CPU / RAM / battery / weather) + their anchored detail cards ----

    /** Apply widget visibility from preferences, wire taps once, and drive the data controllers. */
    void updateStatusWidgets() {
        // The user's order first, so the dots below are worked out on the row as it stands.
        syncRowOrder();
        com.termux.app.statusbar.StatusBarWidgetView cpu = mHost.findViewById(R.id.terminal_status_widget_cpu);
        com.termux.app.statusbar.StatusBarWidgetView gpu = mHost.findViewById(R.id.terminal_status_widget_gpu);
        com.termux.app.statusbar.StatusBarWidgetView ram = mHost.findViewById(R.id.terminal_status_widget_ram);
        com.termux.app.statusbar.StatusBarWidgetView weather = mHost.findViewById(R.id.terminal_status_widget_weather);
        com.termux.app.statusbar.StatusBarWidgetView battery =
            mHost.findViewById(R.id.terminal_status_widget_battery);
        // The CPU reading needs privileged access; without it the widget hides rather than show a
        // dash, and comes back through mPrivilegedAvailabilityListener. The backend itself is
        // brought up (and retried) by refreshPrivilegedBackendIfNeeded() on every resume.
        boolean cpuOn = com.termux.app.statusbar.StatusStatsClusterPolicy.cpuShown(
            mHost.preferences() != null && mHost.preferences().isStatusWidgetCpuEnabled(),
            PrivilegedBackendManager.getInstance().isPrivilegedAvailableForUi());
        // The GPU reading needs no switch beyond its own, but a phone that reports none keeps it
        // hidden: it is shown only once a sample has read a load (the smoother holds the last one).
        boolean gpuEnabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetGpuEnabled();
        if (!gpuEnabled) mHost.barGpuSmoother().reset();
        boolean gpuOn = com.termux.app.statusbar.StatusStatsClusterPolicy.gpuShown(
            gpuEnabled, mHost.barGpuSmoother().publishedPercent()
                != com.termux.app.statusbar.StatusBarStatSmoother.UNKNOWN);
        boolean ramOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetRamEnabled();
        boolean batteryOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetBatteryEnabled();
        boolean weatherOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetWeatherEnabled();
        View widgetSlot = mHost.findViewById(R.id.terminal_top_widget_area);
        if (widgetSlot instanceof com.termux.app.statusbar.TopPaneWidgetSlot) {
            ((com.termux.app.statusbar.TopPaneWidgetSlot) widgetSlot).setMediaEnabled(
                mHost.preferences() == null || mHost.preferences().isStatusWidgetMediaEnabled());
        }

        if (cpu != null) {
            cpu.setVisibility(cpuOn ? View.VISIBLE : View.GONE);
            cpu.dressAsCpu();
            // Seeded from the smoother every pass: onStatsUpdated skips hidden widgets, so a value
            // published while this one was off or covered is otherwise only repainted when the
            // reading next changes \u2014 and before the first sample this is what puts "--" on screen
            // instead of an empty slot.
            if (cpuOn) cpu.setValue(mHost.barCpuSmoother().text());
            if (cpu.getTag() == null) {
                cpu.setTag("wired");
                cpu.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (gpu != null) {
            gpu.setVisibility(gpuOn ? View.VISIBLE : View.GONE);
            gpu.dressAsGpu();
            if (gpuOn) gpu.setValue(mHost.barGpuSmoother().text());
            if (gpu.getTag() == null) {
                gpu.setTag("wired");
                gpu.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (ram != null) {
            ram.setVisibility(ramOn ? View.VISIBLE : View.GONE);
            ram.dressAsRam();
            if (ramOn) ram.setValue(mHost.barMemorySmoother().text());
            if (ram.getTag() == null) {
                ram.setTag("wired");
                ram.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (battery != null) {
            battery.setVisibility(batteryOn ? View.VISIBLE : View.GONE);
            mBatteryIcon = battery.dressAsBattery();
            mBatteryIcon.setErrorColor(batteryErrorColor());
            if (battery.getTag() == null) {
                battery.setTag("wired");
                battery.setOnClickListener(v -> openBatteryUsage());
            }
        }
        syncBatteryChip(batteryOn);
        if (weather != null) {
            weather.setVisibility(weatherOn ? View.VISIBLE : View.GONE);
            weather.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            if (weather.getTag() == null) {
                weather.setTag("wired");
                weather.setWeatherIcon(
                    com.termux.app.statusbar.WeatherIcons.NOT_AVAILABLE);
                weather.setValue("--");
                weather.setOnClickListener(v -> mHost.toggleWeatherCard(v));
            }
        }

        // An enabled GPU stat keeps the sampler running (and reading hardware, in the same one
        // poll) even while it is hidden for want of a reading: that is how it learns it can show.
        if (cpuOn || ramOn || gpuEnabled) {
            boolean cardShowing = mHost.statusCardHost().isShowing() && mHost.statsCardView() != null;
            mHost.ensureStatsController().start(
                mHost.statsInterval(cardShowing ? mHost.statsCardIntervalMs() : mHost.statsBarIntervalMs()),
                cardShowing, cardShowing || gpuEnabled);
            // The sampler pauses while nothing shows its numbers (StatsPollCadence).
            mHost.statsController().setConsumerVisible(mHost.statsConsumerVisible());
        } else if (mHost.statsController() != null) {
            mHost.statsController().stop();
        }

        if (weatherOn) {
            mHost.ensureWeatherController().refreshIfStale();
        } else if (mHost.weatherController() != null) {
            mHost.weatherController().stop();
        }

        mHost.ensureAiIndicatorController();
        syncStatusNotificationsMark();
        mHost.syncFontToggle();
        // Every item above may have shown or hidden: the dots follow who stands next to whom.
        syncRowDots();
        // The tiers just moved, and a tier is which hue this widget's ink is toned from.
        syncStatusBarInk();
    }

    @NonNull
    com.termux.app.statusbar.StatusCardHost.StyleProvider statusCardStyleProvider() {
        return new com.termux.app.statusbar.StatusCardHost.StyleProvider() {
            @Override
            public Drawable cardBackground() {
                com.google.android.material.shape.MaterialShapeDrawable card =
                    com.termux.app.material.M3.surface(mHost.context(),
                        com.google.android.material.R.attr.shapeAppearanceCornerLarge,
                        com.termux.app.material.M3.surfaceContainer(mHost.context()));
                card.setStroke(mHost.getResources().getDimension(
                        com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width),
                    com.termux.app.material.M3.outlineVariant(mHost.context()));
                return card;
            }

            @Override
            public float cornerRadiusPx() {
                return com.termux.app.material.M3.shape(mHost.context(),
                    com.google.android.material.R.attr.shapeAppearanceCornerLarge)
                    .getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0, 0, 1, 1));
            }

            @Override
            public float contentInsetPx() {
                return mHost.dpToPx(12);
            }
        };
    }

    /** The window is going away: the process-wide badge store lets go of the bar's mark. */
    void removeStatusNotificationsListener() {
        com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.removeListener(
            mStatusNotificationsListener);
        mStatusNotificationsListener = null;
    }
}
