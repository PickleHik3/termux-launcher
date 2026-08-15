package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryPasteImporter;
import com.termux.app.launcher.data.LauncherCategoryPasteNotification;
import com.termux.app.launcher.data.LauncherCategorySortPrompt;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.interact.ShareUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialogs for the app-categorization chooser, kept out of {@link AppDrawerPreferencesFragment} so
 * the fragment stays a wiring file. Everything here is static and stateless: a dialog owns no
 * lifecycle, and the caller re-resolves the model and the app list every time it opens one.
 */
final class CategorySortDialogs {

    /** Measured on-device throughput, used only for the "takes about N minutes" estimate. */
    private static final int SECONDS_PER_APP_E4B = 3;
    private static final int SECONDS_PER_APP_E2B = 1;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    /**
     * The config read/merge/write is disk I/O and must not run on the click. One shared daemon
     * thread serialises those writes and is never per-click, so nothing outlives the screen but the
     * idle thread itself; the class is static and stateless, so there is no lifecycle to close over.
     */
    private static final ExecutorService FILE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "app-drawer-category-write");
        thread.setDaemon(true);
        return thread;
    });

    private CategorySortDialogs() {
    }

    /**
     * @return the downloaded model this feature would use — E4B when present, else E2B — or null
     *     when neither is installed. Availability is a separate question, see
     *     {@link #unavailableReason}.
     */
    @Nullable
    static TaiModelSpec resolveModel(@NonNull Context context) {
        Map<String, TaiModelSpec> installed = new TaiModelStore(context).getDownloadedReadableModels();
        TaiModelSpec preferred = installed.get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        if (preferred != null) return preferred;
        return installed.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
    }

    /**
     * @return null when the model can run here, otherwise the user-facing reason the on-device row
     *     is disabled. The row is disabled with this reason rather than hidden, so the user learns
     *     what to install or why their device cannot do it.
     */
    @Nullable
    static String unavailableReason(@NonNull Context context, @Nullable TaiModelSpec model) {
        if (model == null)
            return context.getString(R.string.settings_app_drawer_category_sort_unavailable_model);
        TaiDeviceCapabilities.ModelCapabilityCheck check =
            TaiDeviceCapabilities.detect(context).checkModelCapability(model);
        if (check.blockingReason != null)
            return context.getString(R.string.settings_app_drawer_category_sort_unavailable_model);
        if (check.warning != null)
            return context.getString(R.string.settings_app_drawer_category_sort_unavailable_memory,
                model.recommendedRamGb + " GB");
        return null;
    }

    /**
     * Collapses the launcher catalogue to one entry per package — a package appears once per
     * work/private profile, and the config file is package-keyed. Blocking: call it off the main
     * thread.
     */
    @NonNull
    static List<LauncherCategorySortPrompt.AppEntry> loadApps(@NonNull Context context) {
        LinkedHashMap<String, String> labelByPackage = new LinkedHashMap<>();
        for (LauncherAppEntry entry : LauncherAppDataProvider.getInstance(context).getAllAppsBlocking()) {
            if (entry == null) continue;
            if (labelByPackage.containsKey(entry.appRef.packageName)) continue;
            labelByPackage.put(entry.appRef.packageName, entry.label);
        }
        List<LauncherCategorySortPrompt.AppEntry> apps = new ArrayList<>();
        for (Map.Entry<String, String> app : labelByPackage.entrySet())
            apps.add(new LauncherCategorySortPrompt.AppEntry(app.getKey(),
                app.getValue() == null ? app.getKey() : app.getValue()));
        return apps;
    }

    /**
     * Presents the two ways to categorize: the on-device model, and copying a prompt into an
     * external AI chat. Both rows carry their warning in the row itself — a privacy cost or a
     * multi-minute run is information the user needs before choosing, not after. The rows are drawn
     * as cards rather than as a platform list, because a three-line row in
     * {@code simple_list_item_1} clips its warning — the very line that must not be missed.
     *
     * @param onDeviceChosen run when the on-device row is picked; the caller owns starting the
     *     service, because only it can keep polling for progress afterwards.
     * @param onPasteApplied run after a pasted reply has been written, so the caller can refresh.
     */
    static void showChooser(@NonNull Context context,
                            @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                            @NonNull Runnable onDeviceChosen,
                            @Nullable Runnable onPasteApplied) {
        TaiModelSpec model = resolveModel(context);
        String unavailable = unavailableReason(context, model);
        boolean onDeviceEnabled = unavailable == null && model != null;

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(20 * density);
        container.setPadding(padH, Math.round(4 * density), padH, Math.round(4 * density));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_app_drawer_category_sort_dialog_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        String onDeviceSummary = onDeviceEnabled && model != null
            ? context.getString(R.string.settings_app_drawer_category_sort_on_device_summary,
                model.displayName)
            : unavailable;
        String onDeviceNote = onDeviceEnabled && model != null
            ? context.getString(R.string.settings_app_drawer_category_sort_on_device_warning,
                estimatedMinutes(model, apps.size()))
            : null;
        container.addView(buildRow(context,
            R.drawable.ic_symbol_smart_toy,
            context.getString(R.string.settings_app_drawer_category_sort_on_device),
            onDeviceSummary,
            onDeviceNote,
            onDeviceEnabled,
            () -> {
                dialog.dismiss();
                onDeviceChosen.run();
            }));

        container.addView(buildRow(context,
            R.drawable.ic_symbol_content_copy,
            context.getString(R.string.settings_app_drawer_category_sort_paste),
            context.getString(R.string.settings_app_drawer_category_sort_paste_summary),
            context.getString(R.string.settings_app_drawer_category_sort_paste_warning),
            true,
            () -> {
                dialog.dismiss();
                startPasteRoute(context, apps, onPasteApplied);
            }));

        dialog.show();
    }

    /**
     * One card in the chooser: icon, title, summary, and the warning as its own dimmer line so it
     * cannot be truncated away. A disabled card keeps its text — the summary is the reason it is
     * disabled — but loses its ripple and its click.
     */
    @NonNull
    private static View buildRow(@NonNull Context context,
                                 @DrawableRes int iconRes,
                                 @NonNull String title,
                                 @Nullable String summary,
                                 @Nullable String note,
                                 boolean enabled,
                                 @NonNull Runnable onClick) {
        float density = context.getResources().getDisplayMetrics().density;
        int titleColor = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFECEFF4);
        int summaryColor = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFF9AA3B2);
        int surfaceColor = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel, 0xFF20242C));
        int accent = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary, 0xFF8AB4F8);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * density);
        row.setPadding(padding, padding, padding, padding);

        GradientDrawable card = new GradientDrawable();
        card.setColor(surfaceColor);
        card.setCornerRadius(20 * density);
        if (enabled) {
            row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(MaterialColors.compositeARGBWithAlpha(accent, 48)), card, null));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> onClick.run());
        } else {
            row.setBackground(card);
            row.setAlpha(0.5f);
        }

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(enabled ? accent : summaryColor));
        int iconSize = Math.round(24 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(Math.round(16 * density));
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(titleColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(titleView);

        if (summary != null && !summary.isEmpty()) {
            TextView summaryView = new TextView(context);
            summaryView.setText(summary);
            summaryView.setTextColor(summaryColor);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            summaryView.setLineSpacing(Math.round(2 * density), 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(2 * density);
            texts.addView(summaryView, params);
        }

        if (note != null && !note.isEmpty()) {
            TextView noteView = new TextView(context);
            noteView.setText(note);
            noteView.setTextColor(MaterialColors.compositeARGBWithAlpha(summaryColor, 200));
            noteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            noteView.setLineSpacing(Math.round(2 * density), 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(6 * density);
            texts.addView(noteView, params);
        }

        row.addView(texts, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Math.round(6 * density);
        rowParams.bottomMargin = Math.round(6 * density);
        row.setLayoutParams(rowParams);
        return row;
    }

    /**
     * Copies the prompt and opens both return legs at once: the paste-back dialog for a user who
     * stays in Settings, and a persistent notification for the far more common case where they
     * leave for a chat app and the dialog does not survive the trip.
     */
    private static void startPasteRoute(@NonNull Context context,
                                        @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                                        @Nullable Runnable onPasteApplied) {
        copyPrompt(context, apps, false);
        LauncherCategoryPasteNotification.post(context);
        showPasteBack(context, apps, onPasteApplied);
    }

    private static void copyPrompt(@NonNull Context context,
                                   @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                                   boolean toast) {
        ShareUtils.copyTextToClipboard(context, "Termux Launcher app categories",
            LauncherCategorySortPrompt.pasteablePrompt(apps), null);
        if (toast) Toast.makeText(context,
            R.string.settings_app_drawer_category_sort_paste_copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * Takes the AI chat's answer back. The prompt is already on the clipboard when this opens, but
     * the copy button stays available: coming back from a chat app usually means the clipboard now
     * holds the answer, and re-opening the chooser to re-copy the prompt would overwrite it.
     */
    static void showPasteBack(@NonNull Context context,
                              @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                              @Nullable Runnable onApplied) {
        float density = context.getResources().getDisplayMetrics().density;
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setMaxLines(12);
        input.setHint(R.string.settings_app_drawer_category_sort_paste_input_hint);
        input.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        layout.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Neutral button rather than a view button: the dialog must not close when the prompt is
        // re-copied, and a neutral button is the one dialog button that leaves it open by default.
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_app_drawer_category_sort_paste)
            .setMessage(R.string.settings_app_drawer_category_sort_paste_dialog_body)
            .setView(layout)
            .setPositiveButton(R.string.settings_app_drawer_category_sort_paste_apply, (d, which) ->
                applyPastedReply(context, apps, input.getText().toString(), onApplied))
            .setNeutralButton(R.string.settings_app_drawer_category_sort_paste_copy_again, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.show();
        View copyButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (copyButton != null) copyButton.setOnClickListener(v -> copyPrompt(context, apps, true));
    }

    /**
     * Merges a pasted reply into {@code app-categories.conf} off the main thread and reports the
     * outcome. The merge itself lives in {@link LauncherCategoryPasteImporter} because the
     * notification reply path applies the same text without any dialog around it.
     */
    private static void applyPastedReply(@NonNull Context context,
                                         @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                                         @NonNull String reply,
                                         @Nullable Runnable onApplied) {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (LauncherCategorySortPrompt.AppEntry app : apps) known.add(app.packageName);

        FILE_EXECUTOR.execute(() -> {
            LauncherCategoryPasteImporter.Result result =
                LauncherCategoryPasteImporter.apply(context, known, reply);
            final String message;
            if (result.isFailure()) {
                message = context.getString(
                    R.string.settings_app_drawer_category_sort_failed, result.errorMessage);
            } else {
                message = context.getString(R.string.settings_app_drawer_category_sort_done,
                    result.applied, result.categories) + ignoredSuffix(context, result.ignored);
            }
            MAIN_HANDLER.post(() -> {
                if (!isContextAlive(context)) return;
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                if (result.applied > 0 && !result.isFailure()) {
                    // The notification is the other half of this same round trip; once the answer
                    // landed there is nothing left to reply to.
                    LauncherCategoryPasteNotification.cancel(context);
                    if (onApplied != null) onApplied.run();
                }
            });
        });
    }

    /**
     * @return false once the hosting activity is gone, so a write that finishes after the user left
     *     the screen reports into nothing instead of touching a dead window.
     */
    private static boolean isContextAlive(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                Activity activity = (Activity) current;
                return !activity.isFinishing() && !activity.isDestroyed();
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return true;
    }

    @NonNull
    private static String ignoredSuffix(@NonNull Context context, int ignored) {
        return ignored <= 0 ? "" : " · " + context.getString(
            R.string.settings_app_drawer_category_sort_ignored_lines, ignored);
    }

    /** Rounded up and never zero: "about 0 minutes" would read as instant. */
    private static int estimatedMinutes(@NonNull TaiModelSpec model, int appCount) {
        int secondsPerApp = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT.equals(model.id)
            ? SECONDS_PER_APP_E2B : SECONDS_PER_APP_E4B;
        return Math.max(1, (int) Math.ceil(appCount * secondsPerApp / 60.0));
    }
}
