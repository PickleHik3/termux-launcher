package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.app.activities.SettingsActivity;
import com.termux.app.launcher.data.LauncherCategorySortPlan;
import com.termux.app.launcher.data.LauncherCategorySortService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Sort now" from anywhere: the drawer's nudge, the Assistant page's offer and the Drawer layout
 * page's refresh row all share this one rule. A model that can run starts the sort service, which
 * shows its own progress notification; anything else (no model, a RAM warning, an unavailable
 * runtime) opens the drawer's settings, where the model chooser explains it.
 */
public final class CategorySortNow {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "category-sort-now");
        t.setDaemon(true);
        return t;
    });

    private CategorySortNow() {}

    /**
     * The sort may start without asking: a model is chosen, its runtime is available, and — for an
     * on-device one — the RAM check does not advise against it (that one waits for a tap in the chooser).
     */
    static boolean isRunnable(boolean hasModel, boolean unavailable, boolean remote, boolean ramAdvisesAgainst) {
        return hasModel && !unavailable && (remote || !ramAdvisesAgainst);
    }

    /** Resolves the plan's runnability; call off the main thread. */
    static boolean isRunnable(@NonNull Context context, @NonNull LauncherCategorySortPlan plan) {
        if (!plan.hasModel()) return false;
        return isRunnable(true, CategorySortDialogs.unavailableReason(context, plan) != null, plan.remote,
            !plan.remote && CategorySortDialogs.ramAdvisesAgainst(context, plan.model));
    }

    /** The service intent for a plan with a model. {@code userAsked} adds the user's own accelerator choice. */
    @NonNull
    static Intent sortIntent(@NonNull Context context, @NonNull LauncherCategorySortPlan plan, boolean userAsked) {
        Intent intent = new Intent(context, LauncherCategorySortService.class);
        intent.setAction(LauncherCategorySortService.ACTION_SORT);
        intent.putExtra(LauncherCategorySortService.EXTRA_MODEL_ID, plan.model);
        if (userAsked && !plan.remote) {
            intent.putExtra(LauncherCategorySortService.EXTRA_USER_ASKED, true);
            intent.putExtra(LauncherCategorySortService.EXTRA_ACCELERATOR, plan.accelerator);
        }
        return intent;
    }

    /**
     * Starts the sort, or opens the drawer's settings when none can run. The plan is read off the
     * main thread. The auto path (not user-asked) is used: the same as the settings page's refresh
     * row, which never overrides the accelerator the sort resolved for itself.
     */
    public static void start(@NonNull Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            boolean runnable = false;
            LauncherCategorySortPlan plan = null;
            try {
                plan = CategorySortDialogs.resolvePlan(app);
                runnable = isRunnable(app, plan);
            } catch (RuntimeException ignored) {
                // A plan that cannot be read is no runnable plan: the settings page explains it.
            }
            final LauncherCategorySortPlan resolved = plan;
            final boolean go = runnable;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (go && resolved != null) {
                    try {
                        ContextCompat.startForegroundService(app, sortIntent(app, resolved, false));
                        return;
                    } catch (RuntimeException ignored) {
                        // Offered from Home: a service that may not start (background limits) must not
                        // crash it; the settings page is the way to run it by hand.
                    }
                }
                SettingsActivity.openAppDrawerSettings(app);
            });
        });
    }
}
