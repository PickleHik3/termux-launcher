package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.privileged.PrivilegedChoice;

/**
 * What a status stat that may need Shizuku or root does on the bar: show its value, stand in its
 * place greyed with a hint of what is missing, or stay hidden. Pure, so the table is tested.
 *
 * <p>A stat needs a backend only where this phone refuses the app's own read: the CPU where
 * {@code /proc/stat} is refused, the GPU where its sysfs nodes are. A phone with no GPU nodes even
 * for root hides it rather than greying it.</p>
 */
public final class StatusStatNeedsPolicy {

    private StatusStatNeedsPolicy() {}

    /** Whether the app can read the stat itself. */
    public enum Access {
        /** It reads now. */
        DIRECT,
        /** The phone refuses the app's own read; a backend could fill it. */
        NEEDS_BACKEND,
        /** Not yet tried. */
        UNKNOWN,
        /** Nothing to read on this phone, even for root. */
        ABSENT
    }

    /** What is missing, and the copy that says so. */
    public enum Hint {
        START_SHIZUKU(R.string.status_stat_needs_start_shizuku),
        ALLOW_SHIZUKU(R.string.status_stat_needs_allow_shizuku),
        ALLOW_ROOT(R.string.status_stat_needs_allow_root),
        NEEDS_BOTH(R.string.status_stat_needs_both),
        NEEDS_SHIZUKU(R.string.status_stat_needs_shizuku),
        NEEDS_ROOT(R.string.status_stat_needs_root);

        @StringRes public final int label;

        Hint(@StringRes int label) {
            this.label = label;
        }

        /** Whether a tap can ask for the missing permission itself, instead of opening Settings. */
        public boolean asksPermission() {
            return this == ALLOW_SHIZUKU || this == ALLOW_ROOT;
        }
    }

    public static final class Verdict {
        public static final Verdict VALUE = new Verdict(true, null);
        public static final Verdict HIDE = new Verdict(false, null);

        public final boolean shown;
        /** Set when shown greyed in place of its value; null for the value or hidden. */
        @Nullable public final Hint needs;

        private Verdict(boolean shown, @Nullable Hint needs) {
            this.shown = shown;
            this.needs = needs;
        }

        static Verdict needs(@NonNull Hint hint) {
            return new Verdict(true, hint);
        }
    }

    /** The CPU: its reading is {@code access}; a running backend reads it where the app cannot. */
    @NonNull
    public static Verdict cpu(boolean enabled, @NonNull Access access,
                              @NonNull PrivilegedChoice.Status status,
                              boolean shizukuInstalled, boolean suPresent) {
        if (!enabled) return Verdict.HIDE;
        switch (access) {
            case DIRECT:
                return Verdict.VALUE;
            case ABSENT:
                return Verdict.HIDE;
            default:
                break;
        }
        if (backendUp(status)) return Verdict.VALUE;
        if (access == Access.UNKNOWN || status == PrivilegedChoice.Status.CHECKING) return Verdict.HIDE;
        return Verdict.needs(hintFor(status, shizukuInstalled, suPresent));
    }

    /**
     * The GPU: {@code known} is whether a load has been read. With a backend up and no load it
     * stays hidden, as it always has: that is a phone with nothing to read.
     */
    @NonNull
    public static Verdict gpu(boolean enabled, boolean known, @NonNull Access access,
                              @NonNull PrivilegedChoice.Status status,
                              boolean shizukuInstalled, boolean suPresent) {
        if (!enabled) return Verdict.HIDE;
        if (known) return Verdict.VALUE;
        if (backendUp(status)) return Verdict.HIDE;
        return cpu(true, access == Access.DIRECT ? Access.UNKNOWN : access, status,
            shizukuInstalled, suPresent);
    }

    private static boolean backendUp(@NonNull PrivilegedChoice.Status status) {
        return status == PrivilegedChoice.Status.USING_SHIZUKU
            || status == PrivilegedChoice.Status.USING_ROOT;
    }

    @NonNull
    static Hint hintFor(@NonNull PrivilegedChoice.Status status, boolean shizukuInstalled,
                        boolean suPresent) {
        switch (status) {
            case NEEDS_PERMISSION:
                return Hint.ALLOW_SHIZUKU;
            case SHIZUKU_NOT_RUNNING:
                return shizukuInstalled ? Hint.START_SHIZUKU : Hint.NEEDS_SHIZUKU;
            case ROOT_DENIED:
                return suPresent ? Hint.ALLOW_ROOT : Hint.NEEDS_ROOT;
            case NOT_AVAILABLE:
                if (shizukuInstalled) return Hint.START_SHIZUKU;
                return suPresent ? Hint.ALLOW_ROOT : Hint.NEEDS_BOTH;
            default:
                // Off: the tap takes them to the choice.
                return Hint.NEEDS_BOTH;
        }
    }
}
