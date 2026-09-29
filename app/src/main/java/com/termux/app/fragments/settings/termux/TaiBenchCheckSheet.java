package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiDeviceConditions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Check (spec Screen 3), a bottom sheet with one line per condition: battery, heat, the "will
 * get warm" warning, keep the screen open, and the downloads with the "remove afterwards"
 * toggle. Start stays disabled while a blocking line fails (the same
 * {@link TaiBenchGuardRules#startCheck} refusal the runtime would answer with), and the failing
 * line says what to do. The phone is re-read every {@link #POLL_MS} while the sheet is up, so
 * plugging in or letting it cool enables Start without reopening.
 */
final class TaiBenchCheckSheet {
    interface OnStart {
        void start(@NonNull TaiBenchSession.Plan plan);
    }

    static final long POLL_MS = 3_000L;

    private TaiBenchCheckSheet() {
    }

    static void show(@NonNull Context context, @NonNull TaiBenchSuite.Preset preset, boolean compare,
                     @NonNull List<TaiBenchSession.Model> models, @NonNull OnStart onStart) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = TaiBenchViews.dp(context, 20);
        content.setPadding(pad, TaiBenchViews.dp(context, 18), pad, pad);

        TextView title = TaiBenchViews.figure(context, context.getString(R.string.tai_bench_check_title), 18f);
        content.addView(title);
        content.addView(TaiBenchViews.body(context, context.getString(R.string.tai_bench_check_summary,
            models.size(), TaiBenchViews.presetLabel(context, preset))), TaiBenchViews.block(context, 4));

        Line battery = new Line(context, R.drawable.ic_symbol_battery_saver);
        Line heat = new Line(context, R.drawable.ic_symbol_info);
        Line warm = new Line(context, R.drawable.ic_symbol_info);
        Line screen = new Line(context, R.drawable.ic_symbol_phone_portrait);
        content.addView(battery.view, TaiBenchViews.block(context, 16));
        content.addView(heat.view, TaiBenchViews.block(context, 10));
        content.addView(warm.view, TaiBenchViews.block(context, 10));
        content.addView(screen.view, TaiBenchViews.block(context, 10));
        warm.set(context.getString(R.string.tai_bench_check_warm_title), context.getString(R.string.tai_bench_check_warm_summary), Line.INFO);
        screen.set(context.getString(R.string.tai_bench_check_screen_title), context.getString(R.string.tai_bench_check_screen_summary), Line.INFO);

        int downloadCount = 0;
        long downloadBytes = 0L;
        for (TaiBenchSession.Model model : models) {
            if (model.installed) continue;
            downloadCount++;
            downloadBytes += Math.max(0L, model.sizeBytes);
        }
        final boolean[] remove = {true};
        if (downloadCount > 0) {
            Line downloads = new Line(context, R.drawable.ic_symbol_save);
            downloads.set(context.getResources().getQuantityString(R.plurals.tai_bench_check_downloads_title, downloadCount, downloadCount),
                context.getString(R.string.tai_bench_check_downloads_summary, TaiModelCentreRows.formatBytes(downloadBytes)), Line.INFO);
            content.addView(downloads.view, TaiBenchViews.block(context, 10));
            LinearLayout toggleRow = new LinearLayout(context);
            toggleRow.setOrientation(LinearLayout.HORIZONTAL);
            toggleRow.setGravity(Gravity.CENTER_VERTICAL);
            toggleRow.setPadding(TaiBenchViews.dp(context, 34), 0, 0, 0);
            TextView toggleText = TaiBenchViews.body(context, context.getString(R.string.tai_bench_check_remove_afterwards));
            toggleRow.addView(toggleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialSwitch toggle = new MaterialSwitch(context);
            toggle.setChecked(true);
            toggle.setContentDescription(context.getString(R.string.tai_bench_check_remove_afterwards));
            toggle.setOnCheckedChangeListener((button, checked) -> remove[0] = checked);
            toggleRow.addView(toggle);
            content.addView(toggleRow, TaiBenchViews.block(context, 4));
        }

        TextView start = TaiBenchViews.goButton(context, context.getString(R.string.tai_bench_check_start));
        start.setMinHeight(TaiBenchViews.dp(context, 44));
        content.addView(start, TaiBenchViews.block(context, 20));
        TaiBenchViews.setEnabled(start, false);

        Handler handler = new Handler(Looper.getMainLooper());
        ExecutorService poller = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tai-bench-check");
            thread.setDaemon(true);
            return thread;
        });
        TaiDeviceConditions reader = new TaiDeviceConditions(context);
        final boolean[] blocked = {true};
        Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (!sheet.isShowing() || poller.isShutdown()) return;
                poller.execute(() -> {
                    TaiBenchGuardRules.Snapshot snapshot;
                    try {
                        snapshot = reader.snapshot();
                    } catch (RuntimeException e) {
                        snapshot = TaiBenchGuardRules.Snapshot.UNKNOWN;
                    }
                    TaiBenchGuardRules.Snapshot seen = snapshot;
                    handler.post(() -> {
                        if (!sheet.isShowing()) return;
                        String reason = TaiBenchGuardRules.startCheck(seen);
                        bindBattery(context, battery, seen, "battery_low".equals(reason));
                        bindHeat(context, heat, seen, "too_hot".equals(reason));
                        blocked[0] = reason != null;
                        TaiBenchViews.setEnabled(start, !blocked[0]);
                        handler.postDelayed(this, POLL_MS);
                    });
                });
            }
        };
        start.setOnClickListener(v -> {
            if (blocked[0]) return;
            TaiMotion.tick(v);
            sheet.dismiss();
            onStart.start(new TaiBenchSession.Plan(preset.id, compare, models, remove[0]));
        });
        sheet.setOnDismissListener(dialog -> {
            handler.removeCallbacksAndMessages(null);
            poller.shutdownNow();
        });
        sheet.setContentView(content);
        sheet.show();
        poll.run();
    }

    private static void bindBattery(@NonNull Context context, @NonNull Line line, @NonNull TaiBenchGuardRules.Snapshot snapshot, boolean failing) {
        String title = context.getString(R.string.tai_bench_check_battery_title, TaiBenchViews.batteryLabel(context, snapshot.batteryPercent, snapshot.charging));
        if (failing) {
            line.set(title, context.getString(R.string.tai_bench_check_battery_fix, TaiBenchGuardRules.START_BATTERY_MIN_PERCENT), Line.FAIL);
        } else if (snapshot.batteryPercent < 0) {
            line.set(title, context.getString(R.string.tai_bench_check_battery_unknown), Line.OK);
        } else {
            line.set(title, context.getString(snapshot.charging ? R.string.tai_bench_check_battery_charging_note : R.string.tai_bench_check_battery_ok), Line.OK);
        }
    }

    private static void bindHeat(@NonNull Context context, @NonNull Line line, @NonNull TaiBenchGuardRules.Snapshot snapshot, boolean failing) {
        String name = TaiBenchGuardRules.thermalStatusName(snapshot.thermalStatus);
        String title = context.getString(R.string.tai_bench_check_heat_title, TaiBenchViews.heatLabel(context, name));
        if (failing) {
            line.set(title, context.getString(R.string.tai_bench_check_heat_fix), Line.FAIL);
        } else if (name == null) {
            line.set(title, context.getString(R.string.tai_bench_check_heat_unknown), Line.OK);
        } else {
            line.set(title, context.getString(R.string.tai_bench_check_heat_ok), Line.OK);
        }
    }

    /** One condition: an icon, a title and a line under it, in the OK, INFO or FAIL colour. */
    private static final class Line {
        static final int OK = 0;
        static final int INFO = 1;
        static final int FAIL = 2;

        @NonNull final LinearLayout view;
        @NonNull private final ImageView icon;
        @NonNull private final TextView title;
        @NonNull private final TextView summary;

        Line(@NonNull Context context, int iconRes) {
            view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.TOP);
            icon = new ImageView(context);
            icon.setImageResource(iconRes);
            int size = TaiBenchViews.dp(context, 20);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
            iconParams.topMargin = TaiBenchViews.dp(context, 1);
            iconParams.setMarginEnd(TaiBenchViews.dp(context, 14));
            view.addView(icon, iconParams);
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            title = TaiBenchViews.title(context, "");
            column.addView(title);
            summary = TaiBenchViews.body(context, "");
            column.addView(summary, TaiBenchViews.block(context, 2));
            view.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        void set(@NonNull String titleText, @Nullable String summaryText, int tone) {
            Context context = view.getContext();
            title.setText(titleText);
            summary.setText(summaryText == null ? "" : summaryText);
            summary.setVisibility(summaryText == null || summaryText.isEmpty() ? View.GONE : View.VISIBLE);
            int color;
            switch (tone) {
                case FAIL: color = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorError); break;
                case INFO: color = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant); break;
                default: color = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary); break;
            }
            icon.setImageTintList(ColorStateList.valueOf(color));
            summary.setTextColor(tone == FAIL ? color : TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        }
    }
}
