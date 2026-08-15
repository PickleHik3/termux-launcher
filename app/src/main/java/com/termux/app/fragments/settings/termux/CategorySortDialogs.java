package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryFile;
import com.termux.app.launcher.data.LauncherCategorySortPrompt;
import com.termux.app.launcher.data.LauncherCategorySortState;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialogs for the app-categorization chooser, kept out of {@link AppDrawerPreferencesFragment} so
 * the fragment stays a wiring file. Everything here is static and stateless: a dialog owns no
 * lifecycle, and the caller re-resolves the model and the app list every time it opens one.
 */
final class CategorySortDialogs {

    private static final String CATEGORY_FILE_NAME = "app-categories.conf";
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
     * multi-minute run is information the user needs before choosing, not after.
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

        String onDeviceRow;
        if (unavailable == null && model != null) {
            onDeviceRow = context.getString(R.string.settings_app_drawer_category_sort_on_device)
                + "\n" + context.getString(
                    R.string.settings_app_drawer_category_sort_on_device_summary, model.displayName)
                + "\n" + context.getString(
                    R.string.settings_app_drawer_category_sort_on_device_warning,
                    estimatedMinutes(model, apps.size()));
        } else {
            onDeviceRow = context.getString(R.string.settings_app_drawer_category_sort_on_device)
                + "\n" + unavailable;
        }
        String pasteRow = context.getString(R.string.settings_app_drawer_category_sort_paste)
            + "\n" + context.getString(R.string.settings_app_drawer_category_sort_paste_summary)
            + "\n" + context.getString(R.string.settings_app_drawer_category_sort_paste_warning);

        final boolean onDeviceEnabled = unavailable == null && model != null;
        List<CharSequence> rows = new ArrayList<>();
        rows.add(onDeviceRow);
        rows.add(pasteRow);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                context, android.R.layout.simple_list_item_1, rows) {
            @Override
            public boolean areAllItemsEnabled() {
                return onDeviceEnabled;
            }

            @Override
            public boolean isEnabled(int position) {
                return position != 0 || onDeviceEnabled;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (text != null) {
                    // The rows are title + summary + warning, and the platform row layout is sized
                    // for one line; unlock it or the warning is the part that gets cut.
                    text.setSingleLine(false);
                    text.setMaxLines(4);
                    text.setEnabled(isEnabled(position));
                }
                return view;
            }
        };

        new AlertDialog.Builder(context)
            .setTitle(R.string.settings_app_drawer_category_sort_dialog_title)
            .setAdapter(adapter, (dialog, which) -> {
                if (which == 0) {
                    onDeviceChosen.run();
                    return;
                }
                ShareUtils.copyTextToClipboard(context, "Termux Launcher app categories",
                    LauncherCategorySortPrompt.pasteablePrompt(apps), null);
                showPasteBack(context, apps, onPasteApplied);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Takes the AI chat's answer back. The prompt is already on the clipboard when this opens, so
     * the dialog is only the return leg.
     */
    static void showPasteBack(@NonNull Context context,
                              @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                              @Nullable Runnable onApplied) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setMaxLines(12);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, 0, padding, 0);
        layout.addView(input);

        new AlertDialog.Builder(context)
            .setTitle(R.string.settings_app_drawer_category_sort_paste)
            .setMessage(R.string.settings_app_drawer_category_sort_paste_summary)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, (dialog, which) ->
                applyPastedReply(context, apps, input.getText().toString(), onApplied))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Merges a pasted reply into {@code app-categories.conf}. Pasted assignments win over what the
     * file already says for the same package — the user just asked for this answer — but packages
     * the reply never mentions keep their section.
     */
    private static void applyPastedReply(@NonNull Context context,
                                         @NonNull List<LauncherCategorySortPrompt.AppEntry> apps,
                                         @NonNull String reply,
                                         @Nullable Runnable onApplied) {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (LauncherCategorySortPrompt.AppEntry app : apps) known.add(app.packageName);
        Map<String, String> slugByPackage = LauncherCategorySortPrompt.parsePastedReply(reply, known);
        int applied = slugByPackage.size();
        // Every package line the reply's grammar yielded, minus the ones that survived the
        // known-package filter: dropping hallucinated packages silently would read as the feature
        // failing, so the count is reported.
        int ignored = Math.max(0, countPackageLines(reply) - applied);

        if (applied == 0) {
            Toast.makeText(context, context.getString(
                R.string.settings_app_drawer_category_sort_done, 0, 0)
                + ignoredSuffix(ignored), Toast.LENGTH_LONG).show();
            return;
        }

        LinkedHashSet<String> reassigned = new LinkedHashSet<>();
        for (String packageName : slugByPackage.keySet())
            reassigned.add(packageName.toLowerCase(Locale.US));

        FILE_EXECUTOR.execute(() -> {
            File file = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/" + CATEGORY_FILE_NAME);
            LauncherCategoryFile existing;
            try {
                existing = LauncherCategoryFile.parse(file);
            } catch (Exception ignoredError) {
                existing = LauncherCategoryFile.empty();
            }

            LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> section : existing.sections().entrySet()) {
                List<String> packages = new ArrayList<>();
                for (String packageName : section.getValue()) {
                    if (reassigned.contains(packageName.toLowerCase(Locale.US))) continue;
                    packages.add(packageName);
                }
                merged.put(section.getKey(), packages);
            }
            for (Map.Entry<String, String> assignment : slugByPackage.entrySet()) {
                List<String> packages = merged.get(assignment.getValue());
                if (packages == null) {
                    packages = new ArrayList<>();
                    merged.put(assignment.getValue(), packages);
                }
                packages.add(assignment.getKey());
            }

            LinkedHashMap<String, List<String>> written = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> section : merged.entrySet()) {
                if (section.getValue().isEmpty()) continue;
                written.put(section.getKey(), section.getValue());
            }

            try {
                LauncherCategoryFile.of(written).write(file);
            } catch (Exception error) {
                String message = context.getString(
                    R.string.settings_app_drawer_category_sort_failed,
                    error.getMessage() == null ? error.toString() : error.getMessage());
                MAIN_HANDLER.post(() -> {
                    if (!isContextAlive(context)) return;
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                });
                return;
            }

            LinkedHashSet<String> allPackages = new LinkedHashSet<>();
            for (List<String> packages : written.values()) allPackages.addAll(packages);
            new LauncherCategorySortState(context).recordRun(System.currentTimeMillis(),
                allPackages.size(), LauncherCategorySortState.SOURCE_PASTED, null);
            LauncherAppDataProvider.getInstance(context).invalidate();

            String message = context.getString(
                R.string.settings_app_drawer_category_sort_done, applied, written.size())
                + ignoredSuffix(ignored);
            MAIN_HANDLER.post(() -> {
                if (!isContextAlive(context)) return;
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                if (onApplied != null) onApplied.run();
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

    /** @return how many package lines the reply contained, before unknown packages are dropped. */
    private static int countPackageLines(@NonNull String reply) {
        try {
            int lines = 0;
            for (List<String> packages : LauncherCategoryFile.parse(new StringReader(reply))
                    .sections().values())
                lines += packages.size();
            return lines;
        } catch (Exception ignored) {
            return 0;
        }
    }

    @NonNull
    private static String ignoredSuffix(int ignored) {
        return ignored <= 0 ? "" : " · " + ignored + " lines ignored";
    }

    /** Rounded up and never zero: "about 0 minutes" would read as instant. */
    private static int estimatedMinutes(@NonNull TaiModelSpec model, int appCount) {
        int secondsPerApp = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT.equals(model.id)
            ? SECONDS_PER_APP_E2B : SECONDS_PER_APP_E4B;
        return Math.max(1, (int) Math.ceil(appCount * secondsPerApp / 60.0));
    }
}
