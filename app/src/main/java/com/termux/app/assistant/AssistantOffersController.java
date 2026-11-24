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
import com.termux.shared.termux.shell.TermuxShellUtils;

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

    /**
     * One offer kind in one place: its threshold, where Not now is kept, the value it reads, the
     * words and the button. Indexed by {@link AssistantOffersPolicy.Kind#ordinal()}; a new kind
     * adds its entry here and its due rule to the policy.
     */
    private abstract class Entry {
        final String key;
        final long threshold;
        final int actionRes;
        final Runnable action;
        long dismissed = AssistantOffersPolicy.NONE;

        Entry(String key, long threshold, int actionRes, Runnable action) {
            this.key = key;
            this.threshold = threshold;
            this.actionRes = actionRes;
            this.action = action;
        }

        abstract long value();

        abstract String message(long value);
    }

    private final Context context;
    private final AssistantOffersView view;
    private final SharedPreferences prefs;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Host host;
    private final AssistantTmpHousekeeping.Clearer clearer;
    private final Entry[] entries;
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
        this.entries = new Entry[AssistantOffersPolicy.Kind.values().length];
        entries[AssistantOffersPolicy.Kind.PENDING.ordinal()] = new Entry("offer_dismissed_unsorted",
            AssistantOffersPolicy.PENDING_THRESHOLD, R.string.assistant_offer_unsorted_action, host::openDrawerSettings) {
            @Override long value() { return pendingApps; }

            @Override String message(long value) {
                int n = (int) value;
                return AssistantOffersController.this.context.getResources()
                    .getQuantityString(R.plurals.assistant_offer_unsorted, n, n);
            }
        };
        entries[AssistantOffersPolicy.Kind.TMP.ordinal()] = new Entry("offer_dismissed_tmp",
            AssistantOffersPolicy.TMP_THRESHOLD_BYTES, R.string.assistant_offer_tmp_action, this::clearOldFiles) {
            @Override long value() { return tmpBytes; }

            @Override String message(long value) {
                Context c = AssistantOffersController.this.context;
                return c.getString(R.string.assistant_offer_tmp, Formatter.formatFileSize(c, value));
            }
        };
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
                long bytes = TermuxShellUtils.getTermuxTMPDIRSize();
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
        for (Entry e : entries) {
            long d = prefs.getLong(e.key, AssistantOffersPolicy.NONE);
            if (AssistantOffersPolicy.forgetDismissal(e.value(), e.threshold, d)) {
                prefs.edit().remove(e.key).apply();
                d = AssistantOffersPolicy.NONE;
            }
            e.dismissed = d;
        }
        contents.clear();
        for (AssistantOffersPolicy.Offer offer : AssistantOffersPolicy.offers(pendingApps, tmpBytes,
            entry(AssistantOffersPolicy.Kind.PENDING).dismissed, entry(AssistantOffersPolicy.Kind.TMP).dismissed)) {
            contents.add(contentFor(entry(offer.kind), offer.value));
        }
        view.show(contents);
    }

    @NonNull
    private Entry entry(@NonNull AssistantOffersPolicy.Kind kind) {
        return entries[kind.ordinal()];
    }

    @NonNull
    private AssistantNoticeView.Content contentFor(@NonNull Entry e, long value) {
        Runnable dismiss = () -> {
            prefs.edit().putLong(e.key, value).apply();
            apply();
        };
        return new AssistantNoticeView.Content(e.message(value), "", -1, false,
            context.getString(R.string.assistant_not_now), dismiss, context.getString(e.actionRes), e.action, false);
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
