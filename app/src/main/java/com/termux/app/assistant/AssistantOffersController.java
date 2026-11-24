package com.termux.app.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryPendingApps;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Runs the offers strip (#383): reads the two values, asks the pure {@link AssistantOffersPolicy}
 * what to show, remembers Not now and does what the buttons say. The pending-apps count is the
 * drawer nudge's own and the tmp size is read at most once an hour, both off the main thread and
 * only while the page is shown. Nothing here touches a chat or the model.
 */
final class AssistantOffersController {

    /** What the strip asks of the page around it. */
    interface Host {
        /** Opens the drawer's settings, the action the drawer's own nudge runs. */
        void openDrawerSettings();

        /** Tells the user plainly what a button did. */
        void say(@NonNull String message);
    }

    private static final String KEY_DISMISSED_PENDING = "offer_dismissed_unsorted";
    private static final String KEY_DISMISSED_TMP = "offer_dismissed_tmp";

    private final Context context;
    private final AssistantOffersView view;
    private final SharedPreferences prefs;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Host host;
    private final AssistantTmpHousekeeping.Clearer clearer;
    private final List<AssistantNoticeView.Content> contents = new ArrayList<>(AssistantOffersView.MAX_ROWS);

    private long pendingApps = AssistantOffersPolicy.NONE;
    private long tmpBytes = AssistantOffersPolicy.NONE;
    private long measuredAt;
    private boolean measuring;
    private boolean clearing;
    private boolean shown;

    AssistantOffersController(@NonNull Context context, @NonNull AssistantOffersView view,
                              @NonNull SharedPreferences prefs, @NonNull Executor io, @NonNull Host host) {
        this.context = context.getApplicationContext();
        this.view = view;
        this.prefs = prefs;
        this.io = io;
        this.host = host;
        this.clearer = TermuxShellUtils::clearTermuxTMPDIROlderThan;
    }

    /** The page is on screen: count the waiting apps and, if an hour has passed, measure tmp. */
    void onShown() {
        shown = true;
        apply();
        countPending();
        measureTmp(false);
    }

    void onHidden() {
        shown = false;
    }

    // ------------------------------------------------------------------------ measuring

    private void countPending() {
        try {
            LauncherCategoryPendingApps.countWaitingAsync(context,
                LauncherAppDataProvider.getInstance(context).getAllApps(), waiting -> {
                    pendingApps = waiting < 0 ? AssistantOffersPolicy.NONE : waiting;
                    apply();
                });
        } catch (RuntimeException ignored) {
            // An offer is a nicety; no count says nothing.
        }
    }

    private void measureTmp(boolean force) {
        if (measuring || clearing) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && !AssistantOffersPolicy.measureDue(measuredAt, now)) return;
        measuring = true;
        measuredAt = now;
        try {
            io.execute(() -> {
                long bytes = AssistantTmpHousekeeping.sizeOf(new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH));
                main.post(() -> {
                    measuring = false;
                    tmpBytes = bytes;
                    apply();
                });
            });
        } catch (RuntimeException e) {
            measuring = false;
        }
    }

    // --------------------------------------------------------------------------- strip

    private void apply() {
        long dismissedPending = prefs.getLong(KEY_DISMISSED_PENDING, AssistantOffersPolicy.NONE);
        long dismissedTmp = prefs.getLong(KEY_DISMISSED_TMP, AssistantOffersPolicy.NONE);
        if (AssistantOffersPolicy.forgetDismissal(pendingApps, AssistantOffersPolicy.UNSORTED_THRESHOLD, dismissedPending)) {
            prefs.edit().remove(KEY_DISMISSED_PENDING).apply();
            dismissedPending = AssistantOffersPolicy.NONE;
        }
        if (AssistantOffersPolicy.forgetDismissal(tmpBytes, AssistantOffersPolicy.TMP_THRESHOLD_BYTES, dismissedTmp)) {
            prefs.edit().remove(KEY_DISMISSED_TMP).apply();
            dismissedTmp = AssistantOffersPolicy.NONE;
        }
        contents.clear();
        for (AssistantOffersPolicy.Offer offer : AssistantOffersPolicy.offers(pendingApps, tmpBytes,
            dismissedPending, dismissedTmp)) {
            contents.add(contentFor(offer));
        }
        view.show(contents);
    }

    @NonNull
    private AssistantNoticeView.Content contentFor(@NonNull AssistantOffersPolicy.Offer offer) {
        String notNow = context.getString(R.string.assistant_not_now);
        Runnable dismiss = () -> {
            prefs.edit().putLong(offer.kind == AssistantOffersPolicy.Kind.UNSORTED
                ? KEY_DISMISSED_PENDING : KEY_DISMISSED_TMP, offer.value).apply();
            apply();
        };
        if (offer.kind == AssistantOffersPolicy.Kind.UNSORTED) {
            int n = (int) offer.value;
            return new AssistantNoticeView.Content(
                context.getResources().getQuantityString(R.plurals.assistant_offer_unsorted, n, n), "", -1, false,
                notNow, dismiss, context.getString(R.string.assistant_offer_unsorted_action), host::openDrawerSettings, false);
        }
        return new AssistantNoticeView.Content(
            context.getString(R.string.assistant_offer_tmp, Formatter.formatFileSize(context, offer.value)), "", -1, false,
            notNow, dismiss, context.getString(R.string.assistant_offer_tmp_action), this::clearOldFiles, false);
    }

    // ----------------------------------------------------------------------- clearing

    private void clearOldFiles() {
        if (clearing) return;
        clearing = true;
        try {
            io.execute(() -> {
                long freed = AssistantTmpHousekeeping.clearOld(clearer);
                main.post(() -> {
                    clearing = false;
                    host.say(freed > 0
                        ? context.getString(R.string.assistant_offer_freed, Formatter.formatFileSize(context, freed))
                        : context.getString(R.string.assistant_offer_nothing_to_clear));
                    if (shown) measureTmp(true);
                    else measuredAt = 0L;
                });
            });
        } catch (RuntimeException e) {
            clearing = false;
        }
    }
}
