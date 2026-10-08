package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.app.material.M3;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryCatalogue;
import com.termux.app.launcher.data.LauncherCategoryPasteImporter;
import com.termux.app.launcher.data.LauncherCategoryPasteNotification;
import com.termux.app.launcher.data.LauncherCategorySortPlan;
import com.termux.app.launcher.data.LauncherCategorySortPrompt;
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
     * @return what a sort would ask: app sorting's feature load plan ({@link TaiFeaturePlans}), a local
     *     model on an accelerator or the remote provider's model. Reads the model store, settings and
     *     the evidence files: not for the main thread. Availability is a separate question, see
     *     {@link #unavailableReason}.
     */
    @NonNull
    static LauncherCategorySortPlan resolvePlan(@NonNull Context context) {
        return LauncherCategorySortPlan.of(TaiFeaturePlans.forContext(context).plan(TaiFunction.APP_CATEGORIES));
    }

    /** Everything the chooser shows, read together off the main thread by {@link #preview}. */
    static final class Preview {
        @NonNull final List<LauncherCategorySortPrompt.AppEntry> apps;
        @NonNull final LauncherCategorySortPlan plan;
        /** The installed spec of a local plan's model; null for a remote plan or a missing model. */
        @Nullable final TaiModelSpec model;
        /** Why the on-device row is disabled, or null when it can run. */
        @Nullable final String unavailable;

        Preview(@NonNull List<LauncherCategorySortPrompt.AppEntry> apps, @NonNull LauncherCategorySortPlan plan,
                @Nullable TaiModelSpec model, @Nullable String unavailable) {
            this.apps = apps;
            this.plan = plan;
            this.model = model;
            this.unavailable = unavailable;
        }
    }

    /** The apps, the plan and whether it can run here. Blocking: the package manager, the model store, the device. */
    @NonNull
    static Preview preview(@NonNull Context context) {
        LauncherCategorySortPlan plan = resolvePlan(context);
        return new Preview(loadApps(context), plan, specFor(context, plan), unavailableReason(context, plan));
    }

    /** The installed spec for a local plan's model, or null (and always null for a remote plan). */
    @Nullable
    private static TaiModelSpec specFor(@NonNull Context context, @NonNull LauncherCategorySortPlan plan) {
        if (plan.model == null || plan.remote) return null;
        TaiModelStore store = new TaiModelStore(context);
        TaiModelSpec spec = store.getDownloadedReadableModels().get(plan.model);
        return spec != null ? spec : store.getInstalledUserModels().get(plan.model);
    }

    /**
     * @return null when the model can run here, otherwise the user-facing reason the on-device row
     *     is disabled. The row is disabled with this reason rather than hidden, so the user learns
     *     what to install or why their device cannot do it.
     */
    @Nullable
    static String unavailableReason(@NonNull Context context, @NonNull LauncherCategorySortPlan plan) {
        if (plan.remote) return null; // the remote provider runs it: nothing to download or fit
        TaiModelSpec model = specFor(context, plan);
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
        // Excludes x11:linux too; see LauncherCategoryCatalogue for why.
        LinkedHashMap<String, String> labelByPackage = LauncherCategoryCatalogue.labelByPackage(
            LauncherAppDataProvider.getInstance(context).getAllAppsBlocking());
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
     * @param onChangeModel run when the Model row is picked: the caller opens the function picker
     *     sheet for APP_CATEGORIES. Null hides the row.
     * @param onPasteApplied run after a pasted reply has been written, so the caller can refresh.
     */
    static void showChooser(@NonNull Context context,
                            @NonNull Preview preview,
                            @NonNull Runnable onDeviceChosen,
                            @Nullable Runnable onChangeModel,
                            @Nullable Runnable onPasteApplied) {
        List<LauncherCategorySortPrompt.AppEntry> apps = preview.apps;
        LauncherCategorySortPlan plan = preview.plan;
        TaiModelSpec model = preview.model;
        String unavailable = preview.unavailable;
        boolean onDeviceEnabled = unavailable == null && plan.hasModel();

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

        String modelName = plan.remote ? plan.displayId() : model != null ? model.displayName : "";
        String onDeviceSummary = onDeviceEnabled
            ? context.getString(plan.remote
                ? R.string.tai_callers_category_remote_summary
                : R.string.settings_app_drawer_category_sort_on_device_summary, modelName)
            : unavailable;
        // What the sort will really load with: the plan's accelerator, the one the load is told.
        String onDeviceNote = onDeviceEnabled
            ? context.getString(R.string.settings_app_drawer_category_sort_on_device_warning,
                plan.estimatedMinutes(apps.size()))
            : null;
        // A large model may make Android close cached background apps (tai-device-tiers spec section 4.4).
        if (onDeviceNote != null && plan.warnBackground) {
            onDeviceNote += "\n" + context.getString(R.string.tai_warn_background_apps);
        }
        // The row's one way forward, when it has one: the Model centre when there is no model to
        // run. A device that cannot run the model gets none.
        boolean missingModel = !plan.remote && model == null;
        String suggestion = null;
        String link = null;
        Runnable onSuggestion = null;
        if (missingModel) {
            link = context.getString(R.string.tai_model_centre_title);
            suggestion = context.getString(R.string.settings_app_drawer_category_sort_get_model_hint, link);
            onSuggestion = () -> {
                dialog.dismiss();
                TaiModelCentreFragment.open(activityOf(context), TaiModelCentreFragment.SEGMENT_GET);
            };
        }
        container.addView(buildRow(context,
            R.drawable.ic_symbol_smart_toy,
            context.getString(R.string.settings_app_drawer_category_sort_on_device),
            onDeviceSummary,
            onDeviceNote,
            onDeviceEnabled,
            () -> {
                dialog.dismiss();
                onDeviceChosen.run();
            },
            suggestion, link, onSuggestion));

        if (onChangeModel != null) {
            container.addView(buildRow(context,
                R.drawable.ic_symbol_smart_toy,
                context.getString(R.string.tai_callers_category_model_row),
                plan.hasModel() ? modelName : context.getString(R.string.cleanup_model_automatic_title),
                null,
                true,
                () -> {
                    dialog.dismiss();
                    onChangeModel.run();
                }));
        }

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
        return buildRow(context, iconRes, title, summary, note, enabled, onClick, null, null, null);
    }

    /**
     * As above, plus an optional suggestion line under the note: {@code suggestion} with {@code link}
     * underlined inside it, the whole line a tap target of its own that runs {@code onSuggestion}
     * instead of the card's click. It wraps, so a large font scale never clips it.
     */
    @NonNull
    private static View buildRow(@NonNull Context context,
                                 @DrawableRes int iconRes,
                                 @NonNull String title,
                                 @Nullable String summary,
                                 @Nullable String note,
                                 boolean enabled,
                                 @NonNull Runnable onClick,
                                 @Nullable String suggestion,
                                 @Nullable String link,
                                 @Nullable Runnable onSuggestion) {
        float density = context.getResources().getDisplayMetrics().density;
        int titleColor = enabled ? M3.onSurface(context) : M3.onSurfaceVariant(context);
        int summaryColor = M3.onSurfaceVariant(context);
        int accent = M3.primary(context);

        // A clickable filled card: the ripple, shape and container colour are the card's own. A
        // disabled card keeps its text, because the summary is the reason it is disabled.
        MaterialCardView card = M3.clickableCard(context, false);
        card.setEnabled(enabled);
        card.setClickable(enabled);
        card.setFocusable(enabled);
        if (enabled) card.setOnClickListener(v -> onClick.run());

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * density);
        row.setPadding(padding, padding, padding, padding);
        card.addView(row);

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
        M3.textAppearance(titleView, com.google.android.material.R.attr.textAppearanceTitleMedium);
        titleView.setTextColor(titleColor);
        texts.addView(titleView);

        if (summary != null && !summary.isEmpty()) {
            TextView summaryView = new TextView(context);
            summaryView.setText(summary);
            M3.textAppearance(summaryView, com.google.android.material.R.attr.textAppearanceBodyMedium);
            summaryView.setTextColor(summaryColor);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(2 * density);
            texts.addView(summaryView, params);
        }

        if (note != null && !note.isEmpty()) {
            TextView noteView = new TextView(context);
            noteView.setText(note);
            M3.textAppearance(noteView, com.google.android.material.R.attr.textAppearanceBodySmall);
            noteView.setTextColor(summaryColor);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(6 * density);
            texts.addView(noteView, params);
        }

        if (suggestion != null && !suggestion.isEmpty() && onSuggestion != null) {
            SpannableString text = new SpannableString(suggestion);
            int at = link == null ? -1 : suggestion.indexOf(link);
            if (at >= 0) text.setSpan(new UnderlineSpan(), at, at + link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView suggestionView = new TextView(context);
            suggestionView.setText(text);
            M3.textAppearance(suggestionView, com.google.android.material.R.attr.textAppearanceBodySmall);
            suggestionView.setTextColor(accent);
            suggestionView.setMinHeight(Math.round(48 * density));
            suggestionView.setGravity(Gravity.CENTER_VERTICAL);
            suggestionView.setClickable(true);
            suggestionView.setOnClickListener(v -> onSuggestion.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            texts.addView(suggestionView, params);
        }

        row.addView(texts, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Math.round(6 * density);
        rowParams.bottomMargin = Math.round(6 * density);
        card.setLayoutParams(rowParams);
        return card;
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
        if (toast) AppNotice.show(context,
            R.string.settings_app_drawer_category_sort_paste_copied, false);
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
        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint(context.getString(
            R.string.settings_app_drawer_category_sort_paste_input_hint));
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        inputLayout.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setMaxLines(12);
        input.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        layout.addView(inputLayout, new LinearLayout.LayoutParams(
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
                AppNotice.show(context, message, true);
                if (result.applied > 0 && !result.isFailure()) {
                    // The notification is the other half of this same round trip; once the answer
                    // landed there is nothing left to reply to.
                    LauncherCategoryPasteNotification.cancel(context);
                    if (onApplied != null) onApplied.run();
                }
            });
        });
    }

    /** The Activity under a (possibly wrapped) context, or null. */
    @Nullable
    private static Activity activityOf(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
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

}
