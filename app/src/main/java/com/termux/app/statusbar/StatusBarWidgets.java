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
        com.termux.app.statusbar.MaterialDotSeparatorView dot =
            mHost.findViewById(R.id.terminal_status_dot_notifications);
        if (mark == null || dot == null) return;
        boolean enabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetNotificationsEnabled();
        int count = enabled ? com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
            .countFor(com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
                .getActivePackages()) : 0;
        boolean shown = com.termux.app.statusbar.StatusNotificationsMark.shown(enabled, count);
        if (shown && mark.getTag() == null) {
            mark.setTag("wired");
            mark.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            mark.setIconGlyph(com.termux.app.statusbar.StatusNotificationsMark.GLYPH);
        }
        if (shown)
            mark.setValue(com.termux.app.statusbar.StatusNotificationsMark.label(count));
        boolean anyStatBefore = mHost.preferences() != null && (mHost.preferences().isStatusWidgetCpuEnabled()
            || mHost.preferences().isStatusWidgetRamEnabled() || mHost.preferences().isStatusWidgetWeatherEnabled());
        int visibility = shown ? View.VISIBLE : View.GONE;
        boolean changed = mark.getVisibility() != visibility;
        mark.setVisibility(visibility);
        dot.setVisibility(shown && anyStatBefore ? View.VISIBLE : View.GONE);
        dot.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        if (changed) syncStatusBarInk();
    }

    // ---- What the status bar's content is drawn in, measured against what it is standing on ----

    /** The status widgets and their dots, in the order the row lays them out. */
    private static final int[] STATUS_INK_WIDGET_IDS = {
        R.id.terminal_status_widget_cpu, R.id.terminal_status_widget_ram,
        R.id.terminal_status_widget_weather, R.id.terminal_status_widget_notifications,
        R.id.terminal_status_widget_ai, R.id.terminal_status_widget_mouse,
    };

    private static final int[] STATUS_INK_DOT_IDS = {
        R.id.terminal_status_dot_cpu_ram, R.id.terminal_status_dot_ram_weather,
        R.id.terminal_status_dot_notifications, R.id.terminal_status_dot_ai,
        R.id.terminal_status_dot_mouse,
    };

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
        com.termux.app.chrome.ChromeInk ink = mHost.chrome().ink();
        if (!ink.bandRect(com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR, mStatusInkRect))
            return;
        int primary = statusInkSeed(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.PRIMARY);
        // A real ink pair: the primary role toned dark (about 30) for pale glass and pale (about
        // 85) for dark glass, so the band can flip with the chrome's polarity rather than buying
        // a veil to keep one ink legible.
        double primaryTone = com.termux.app.theme.SchemeTone.tone(primary);
        int darkPrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 30d - primaryTone);
        int palePrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 85d - primaryTone);
        com.termux.app.chrome.OnGlass.Resolution band = ink.onGlass(
            com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR, mStatusInkRect,
            darkPrimary, palePrimary, com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT);

        // A muted widget is the AI glyph's few seconds of afterlife: the state is said by the
        // neutral role, and the fade is allowed only as far as the backdrop can carry it.
        int muted = com.termux.app.statusbar.StatusBarInk.inkAtAlpha(band.surface,
            MaterialColors.getColor(mHost.context(), com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                primary),
            com.termux.app.statusbar.StatusBarInk.MUTED_ALPHA,
            com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT);

        for (int widgetId : STATUS_INK_WIDGET_IDS) {
            com.termux.app.statusbar.StatusBarWidgetView widget = mHost.findViewById(widgetId);
            if (widget == null) continue;
            int seed = statusInkSeed(widget.colorRole());
            widget.setInk(
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT),
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT),
                muted);
        }
        for (int dotId : STATUS_INK_DOT_IDS) {
            com.termux.app.statusbar.MaterialDotSeparatorView dot = mHost.findViewById(dotId);
            if (dot == null) continue;
            int seed = statusInkSeed(dot.colorRole());
            dot.setInk(ink.inkOn(band, seed, seed,
                com.termux.app.chrome.OnGlass.TARGET_DECORATION));
        }
        com.termux.app.statusbar.StatusBarLensView lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setBandSurface(band.surface);
        com.termux.app.statusbar.SessionsIndicatorView sessions =
            mHost.findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null) sessions.setBandInk(ink, band);
        // What stands on the same band takes its ink from the same resolution.
        com.termux.app.statusbar.PinnedNotificationsView pinned =
            mHost.findViewById(R.id.terminal_pinned_notifications);
        if (pinned != null) pinned.setBandSurface(band.surface);
        com.termux.app.statusbar.MediaWidgetView media = mHost.findViewById(R.id.terminal_media_widget);
        if (media != null) media.onThemeChanged();
        com.termux.app.terminal.TerminalClockWidget clock = mHost.findViewById(R.id.terminal_clock_widget);
        if (clock != null) {
            clock.setBandInk(band.surface, band.ink);
            clock.onThemeChanged();
        }
    }

    /** The role colour a status tier is written in; its hue is the tier, and the toning keeps it. */
    private int statusInkSeed(@NonNull com.termux.app.statusbar.StatusBarWidgetView.ColorRole role) {
        return com.termux.app.statusbar.StatusBarWidgetView.seedFor(mHost.context(), role);
    }

    // ---- Trailing status widgets (CPU / RAM / weather) + their anchored detail cards ----

    /** Apply widget visibility from preferences, wire taps once, and drive the data controllers. */
    void updateStatusWidgets() {
        com.termux.app.statusbar.StatusBarWidgetView cpu = mHost.findViewById(R.id.terminal_status_widget_cpu);
        com.termux.app.statusbar.StatusBarWidgetView ram = mHost.findViewById(R.id.terminal_status_widget_ram);
        com.termux.app.statusbar.StatusBarWidgetView weather = mHost.findViewById(R.id.terminal_status_widget_weather);
        com.termux.app.statusbar.MaterialDotSeparatorView cpuRamDot =
            mHost.findViewById(R.id.terminal_status_dot_cpu_ram);
        com.termux.app.statusbar.MaterialDotSeparatorView ramWeatherDot =
            mHost.findViewById(R.id.terminal_status_dot_ram_weather);
        // The CPU reading needs privileged access; without it the widget hides rather than show a
        // dash, and comes back through mPrivilegedAvailabilityListener. The backend itself is
        // brought up (and retried) by refreshPrivilegedBackendIfNeeded() on every resume.
        boolean cpuOn = com.termux.app.statusbar.StatusStatsClusterPolicy.cpuShown(
            mHost.preferences() != null && mHost.preferences().isStatusWidgetCpuEnabled(),
            PrivilegedBackendManager.getInstance().isPrivilegedAvailableForUi());
        boolean ramOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetRamEnabled();
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
        if (ram != null) {
            ram.setVisibility(ramOn ? View.VISIBLE : View.GONE);
            ram.dressAsRam();
            if (ramOn) ram.setValue(mHost.barMemorySmoother().text());
            if (ram.getTag() == null) {
                ram.setTag("wired");
                ram.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (weather != null) {
            weather.setVisibility(weatherOn ? View.VISIBLE : View.GONE);
            weather.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            if (weather.getTag() == null) {
                weather.setTag("wired");
                weather.setIconAnimation(
                    com.termux.app.statusbar.WeatherController.animationAssetFor(0, true));
                weather.setValue("--");
                weather.setOnClickListener(v -> mHost.toggleWeatherCard(v));
            }
        }

        if (cpuRamDot != null) {
            boolean show = com.termux.app.statusbar.StatusStatsClusterPolicy
                .cpuRamDotVisible(cpuOn, ramOn, weatherOn);
            cpuRamDot.setVisibility(show ? View.VISIBLE : View.GONE);
            // The dot takes the tier it leads into: RAM, or the weather when RAM is off. Both are
            // secondary now, so AI alone carries the tertiary role.
            cpuRamDot.setColorRole(
                com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        }
        if (ramWeatherDot != null) {
            boolean show = com.termux.app.statusbar.StatusStatsClusterPolicy
                .ramWeatherDotVisible(ramOn, weatherOn);
            ramWeatherDot.setVisibility(show ? View.VISIBLE : View.GONE);
            ramWeatherDot.setColorRole(
                com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        }

        if (cpuOn || ramOn) {
            boolean cardShowing = mHost.statusCardHost().isShowing() && mHost.statsCardView() != null;
            mHost.ensureStatsController().start(
                mHost.statsInterval(cardShowing ? mHost.statsCardIntervalMs() : mHost.statsBarIntervalMs()),
                cardShowing);
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
