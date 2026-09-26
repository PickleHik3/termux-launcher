package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiModelStore;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a download row in the Model centre says and offers, decided from one record alone. Kept
 * apart from the fragment and its views so the mapping (the part that is easy to get subtly
 * wrong: which state offers "Start now", what a paused-for-space row says) is tested on its own,
 * and so the TAI main screen's summary row can count records the same way the centre shows them.
 */
final class TaiModelCentreRows {
    private TaiModelCentreRows() {}

    /** The visual phase of a download row; each has its own bar treatment. */
    enum Phase { DOWNLOADING, WAITING, CHECKING, PAUSED, FAILED, HIDDEN }

    /** The buttons a row carries, in the order they are laid out. */
    enum Action { START_NOW, RETRY, PAUSE, RESUME, CANCEL }

    /** The pill's colour role; mapped onto the theme's container colours by the adapter. */
    enum Tone { NEUTRAL, ACCENT, WARN, ERROR }

    /** How the bar draws: a value, a sweep while the size or the hash progress is unknown, or none. */
    enum Bar { DETERMINATE, INDETERMINATE, NONE }

    /**
     * The fields of a {@link TaiDownloadHub.Snapshot} the mapping reads. A snapshot can only be
     * built inside {@code com.termux.ai}, so tests (and anything else outside it) fill one of
     * these instead; {@link #of(TaiDownloadHub.Snapshot)} copies a real one.
     */
    static final class Input {
        String status = "";
        String pausedReason = "";
        String error = "";
        long bytesRead;
        long totalBytes = -1L;
        double bytesPerSecond;
        long etaSeconds = -1L;
        long verifiedBytes;
        long requiredBytes;
        long freeBytes;
        int queuePosition;

        @NonNull
        static Input of(@NonNull TaiDownloadHub.Snapshot snapshot) {
            Input input = new Input();
            input.status = snapshot.status;
            input.pausedReason = snapshot.pausedReason;
            input.error = snapshot.error;
            input.bytesRead = snapshot.bytesRead;
            input.totalBytes = snapshot.totalBytes;
            input.bytesPerSecond = snapshot.bytesPerSecond;
            input.etaSeconds = snapshot.etaSeconds;
            input.verifiedBytes = snapshot.verifiedBytes;
            input.requiredBytes = snapshot.requiredBytes;
            input.freeBytes = snapshot.freeBytes;
            input.queuePosition = snapshot.queuePosition;
            return input;
        }
    }

    /** Everything a download row binds: the pill, the two halves of the meta line, the bar and the buttons. */
    static final class State {
        @NonNull final Phase phase;
        /** Empty while downloading: the bar and the meta line say it all, a pill would only repeat it. */
        @NonNull final String pill;
        @NonNull final Tone tone;
        @NonNull final String metaStart;
        @NonNull final String metaEnd;
        @NonNull final Bar bar;
        /** 0..10000 when {@link #bar} is determinate. */
        final int progress;
        @NonNull final Set<Action> actions;

        State(@NonNull Phase phase, @NonNull String pill, @NonNull Tone tone, @NonNull String metaStart,
              @NonNull String metaEnd, @NonNull Bar bar, int progress, @NonNull Set<Action> actions) {
            this.phase = phase;
            this.pill = pill;
            this.tone = tone;
            this.metaStart = metaStart;
            this.metaEnd = metaEnd;
            this.bar = bar;
            this.progress = progress;
            this.actions = Collections.unmodifiableSet(actions);
        }
    }

    // ---- the Android-free core ----

    /** Which phase a record shows as. Installed and cancelled records leave the Downloads section. */
    @NonNull
    static Phase phaseOf(@NonNull Input input) {
        switch (input.status) {
            case TaiModelStore.STATE_DOWNLOADING: return Phase.DOWNLOADING;
            case TaiModelStore.STATE_QUEUED: return Phase.WAITING;
            case TaiModelStore.STATE_VERIFYING: return Phase.CHECKING;
            case TaiModelStore.STATE_PAUSED:
                // A swap-out pause lasts only until the engine re-queues the record, a moment
                // later; showing it as "Paused" would flash a state the person never chose.
                return "swap_out".equals(input.pausedReason) ? Phase.WAITING : Phase.PAUSED;
            case TaiModelStore.STATE_FAILED: return Phase.FAILED;
            default: return Phase.HIDDEN;
        }
    }

    /**
     * The buttons for a phase. A waiting row gets "Start now" rather than Pause, because the one
     * thing a person wants from a queued item is for it to go first; a failed row gets Retry,
     * which resumes from the bytes it has. Every visible row can be cancelled, which for a failed
     * one discards the partial file.
     */
    @NonNull
    static Set<Action> actionsFor(@NonNull Phase phase) {
        switch (phase) {
            case DOWNLOADING: return EnumSet.of(Action.PAUSE, Action.CANCEL);
            case WAITING: return EnumSet.of(Action.START_NOW, Action.CANCEL);
            // Pausing mid-hash would throw the hash away; only cancel is offered while checking.
            case CHECKING: return EnumSet.of(Action.CANCEL);
            case PAUSED: return EnumSet.of(Action.RESUME, Action.CANCEL);
            case FAILED: return EnumSet.of(Action.RETRY, Action.CANCEL);
            default: return EnumSet.noneOf(Action.class);
        }
    }

    /** True for the records the Downloads section lists, and the main screen counts as downloading. */
    static boolean isShown(@NonNull Input input) {
        return phaseOf(input) != Phase.HIDDEN;
    }

    /** The bar's value out of 10000 for the bytes a phase reports, or -1 when it is unknown. */
    static int progressOf(@NonNull Input input, @NonNull Phase phase) {
        if (input.totalBytes <= 0L) return -1;
        long done = phase == Phase.CHECKING ? input.verifiedBytes : input.bytesRead;
        if (phase == Phase.CHECKING && done <= 0L) return -1;
        return (int) Math.max(0L, Math.min(10000L, done * 10000L / input.totalBytes));
    }

    /** A short failure reason in plain words, for the pill; the full error goes to the meta line. */
    enum Failure { NETWORK, TOKEN, EXPIRED, SPACE, CHECK, GENERIC }

    @NonNull
    static Failure failureOf(@Nullable String error) {
        String value = error == null ? "" : error.toLowerCase(Locale.ROOT);
        if (value.contains("401") || value.contains("gated") || value.contains("auth") || value.contains("token")) {
            return Failure.TOKEN;
        }
        if (value.contains("403") || value.contains("404") || value.contains("410") || value.contains("expired")) {
            return Failure.EXPIRED;
        }
        if (value.contains("space") || value.contains("storage") || value.contains("enospc")) return Failure.SPACE;
        if (value.contains("sha") || value.contains("checksum") || value.contains("hash")) return Failure.CHECK;
        if (value.contains("resolve host") || value.contains("timed out") || value.contains("timeout")
            || value.contains("connection") || value.contains("network") || value.contains("unreachable")
            || value.contains("http 5")) {
            return Failure.NETWORK;
        }
        return Failure.GENERIC;
    }

    /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th, 21st: the English ordinal the "in line" pill uses. */
    @NonNull
    static String ordinal(int value) {
        int mod100 = value % 100;
        String suffix;
        if (mod100 >= 11 && mod100 <= 13) suffix = "th";
        else if (value % 10 == 1) suffix = "st";
        else if (value % 10 == 2) suffix = "nd";
        else if (value % 10 == 3) suffix = "rd";
        else suffix = "th";
        return value + suffix;
    }

    /** What the TAI main screen's Model centre row says about the downloads, in one pass. */
    static final class Summary {
        /** Downloading, checking or waiting: everything still on its way by itself. */
        final int downloading;
        final int paused;
        final int failed;
        /** 0..10000 over the rows on their way whose size is known; -1 when none is. */
        final int progress;

        Summary(int downloading, int paused, int failed, int progress) {
            this.downloading = downloading;
            this.paused = paused;
            this.failed = failed;
            this.progress = progress;
        }
    }

    @NonNull
    static Summary summarize(@NonNull Iterable<Input> inputs) {
        int downloading = 0;
        int paused = 0;
        int failed = 0;
        long done = 0L;
        long total = 0L;
        for (Input input : inputs) {
            switch (phaseOf(input)) {
                case DOWNLOADING:
                case WAITING:
                case CHECKING:
                    downloading++;
                    // One bar for all of them, weighted by size, so a 97 MB file finishing does
                    // not make a 3.7 GB one look nearly done.
                    if (input.totalBytes > 0L) {
                        total += input.totalBytes;
                        done += Math.min(input.bytesRead, input.totalBytes);
                    }
                    break;
                case PAUSED:
                    paused++;
                    break;
                case FAILED:
                    failed++;
                    break;
                default:
                    break;
            }
        }
        int progress = total > 0L ? (int) Math.min(10000L, done * 10000L / total) : -1;
        return new Summary(downloading, paused, failed, progress);
    }

    // ---- with strings ----

    @NonNull
    static State stateFor(@NonNull Context context, @NonNull TaiDownloadHub.Snapshot snapshot) {
        return stateFor(context, Input.of(snapshot));
    }

    @NonNull
    static State stateFor(@NonNull Context context, @NonNull Input input) {
        Phase phase = phaseOf(input);
        Set<Action> actions = actionsFor(phase);
        int progress = progressOf(input, phase);
        String bytes = bytesLine(context, input);
        switch (phase) {
            case DOWNLOADING: {
                String start = bytes;
                if (input.bytesPerSecond >= 1024.0) {
                    start = start + " · " + context.getString(R.string.tai_centre_meta_speed,
                        formatBytes((long) input.bytesPerSecond));
                } else if (input.bytesRead <= 0L) {
                    start = context.getString(R.string.tai_centre_meta_starting);
                }
                return new State(phase, "", Tone.NEUTRAL, start, eta(context, input.etaSeconds),
                    progress < 0 ? Bar.INDETERMINATE : Bar.DETERMINATE, Math.max(0, progress), actions);
            }
            case WAITING: {
                String pill = input.queuePosition > 0
                    ? context.getString(R.string.tai_centre_pill_waiting_at, ordinal(input.queuePosition))
                    : context.getString(R.string.tai_centre_pill_waiting);
                return new State(phase, pill, Tone.NEUTRAL, input.bytesRead > 0L ? bytes : "", "",
                    Bar.DETERMINATE, Math.max(0, progress), actions);
            }
            case CHECKING:
                return new State(phase, context.getString(R.string.tai_centre_pill_checking), Tone.ACCENT,
                    context.getString(R.string.tai_centre_meta_checking), "",
                    progress < 0 ? Bar.INDETERMINATE : Bar.DETERMINATE, Math.max(0, progress), actions);
            case PAUSED: {
                String pill;
                String start;
                switch (input.pausedReason) {
                    case TaiModelStore.PAUSED_APP_CLOSED:
                        pill = context.getString(R.string.tai_centre_pill_paused_reason,
                            context.getString(R.string.tai_centre_reason_app_closed));
                        start = join(bytes, context.getString(R.string.tai_centre_meta_paused_app_closed));
                        break;
                    case TaiModelStore.PAUSED_NETWORK:
                        pill = context.getString(R.string.tai_centre_pill_paused_reason,
                            context.getString(R.string.tai_centre_reason_network));
                        start = join(bytes, context.getString(R.string.tai_centre_meta_paused_network));
                        break;
                    case TaiModelStore.PAUSED_NO_SPACE:
                        pill = context.getString(R.string.tai_centre_pill_paused_reason,
                            context.getString(R.string.tai_centre_reason_no_space));
                        start = context.getString(R.string.tai_centre_needs_space,
                            formatBytes(input.requiredBytes), formatBytes(input.freeBytes));
                        break;
                    default:
                        pill = context.getString(R.string.tai_centre_pill_paused);
                        start = join(bytes, context.getString(R.string.tai_centre_meta_paused_user));
                        break;
                }
                return new State(phase, pill, Tone.WARN, start, "", Bar.DETERMINATE, Math.max(0, progress), actions);
            }
            case FAILED: {
                String reason = context.getString(failureLabel(failureOf(input.error)));
                String detail = input.error == null || input.error.trim().isEmpty() ? bytes : input.error.trim();
                return new State(phase, context.getString(R.string.tai_centre_pill_failed, reason), Tone.ERROR,
                    detail, "", Bar.NONE, 0, actions);
            }
            default:
                return new State(phase, "", Tone.NEUTRAL, "", "", Bar.NONE, 0, actions);
        }
    }

    static int failureLabel(@NonNull Failure failure) {
        switch (failure) {
            case NETWORK: return R.string.tai_centre_fail_network;
            case TOKEN: return R.string.tai_centre_fail_token;
            case EXPIRED: return R.string.tai_centre_fail_expired;
            case SPACE: return R.string.tai_centre_fail_space;
            case CHECK: return R.string.tai_centre_fail_check;
            default: return R.string.tai_centre_fail_generic;
        }
    }

    /** "2.3 / 3.7 GB", or just what has arrived when the size is not known. */
    @NonNull
    static String bytesLine(@NonNull Context context, @NonNull Input input) {
        if (input.totalBytes > 0L) {
            return context.getString(R.string.tai_centre_meta_progress,
                formatBytes(input.bytesRead), formatBytes(input.totalBytes));
        }
        return input.bytesRead > 0L ? formatBytes(input.bytesRead) : "";
    }

    /** "~3 min", "~40 s", "~1 h 5 min"; empty when the rate has not settled yet. */
    @NonNull
    static String eta(@NonNull Context context, long seconds) {
        if (seconds <= 0L) return "";
        if (seconds < 60L) return context.getString(R.string.tai_centre_meta_eta_seconds, (int) seconds);
        long minutes = (seconds + 30L) / 60L;
        if (minutes < 60L) return context.getString(R.string.tai_centre_meta_eta_minutes, (int) minutes);
        return context.getString(R.string.tai_centre_meta_eta_hours, (int) (minutes / 60L), (int) (minutes % 60L));
    }

    @NonNull
    private static String join(@NonNull String first, @NonNull String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + " · " + second;
    }

    /** Sizes the way every TAI screen writes them: binary units, one decimal from KB up. */
    @NonNull
    static String formatBytes(long bytes) {
        if (bytes <= 0L) return "0 B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
