package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * What to do with {@code ~/.config/terl/terl.conf} each time it may have changed (a save, the next
 * start, a resume): apply it once, or leave it. The settings are the truth (ADR 0041), so the
 * file applies only when its content differs from the last file Terl applied; an unchanged file
 * never replays over edits made in the screens, and a file changed while Terl was off applies at
 * the next start. Terl never writes the file. The watcher and the notice are the caller's.
 */
public final class TerlConf {

    private TerlConf() {}

    /** Where Terl remembers the last file it applied: a fingerprint of its content. */
    public interface Record {
        /** The fingerprint remembered, or null when no file was ever applied. */
        @Nullable
        String applied();

        void remember(@NonNull String fingerprint);
    }

    /** What {@link #sync} found. */
    public enum Kind {
        /** There is no file (or it could not be read): nothing to do. */
        ABSENT,
        /** It is the file already applied. */
        UNCHANGED,
        /** Every line landed. */
        APPLIED,
        /** A line was unreadable or refused; nothing changed. */
        FAILED
    }

    /** The answer of one {@link #sync}. */
    public static final class Outcome {
        @NonNull public final Kind kind;
        /** The saves of an {@link Kind#APPLIED} file, already handed to the applier. */
        @NonNull public final List<SettingsRegistry.Change> changes;
        /** The {@code line N: ...} messages of a {@link Kind#FAILED} file, for the notice. */
        @NonNull public final List<String> errors;

        Outcome(@NonNull Kind kind, @NonNull List<SettingsRegistry.Change> changes,
                @NonNull List<String> errors) {
            this.kind = kind;
            this.changes = Collections.unmodifiableList(changes);
            this.errors = Collections.unmodifiableList(errors);
        }
    }

    /**
     * A file read, fingerprinted and checked: everything that may run off the main thread. When
     * {@link #early()} is non-null there is nothing to save; otherwise {@link #commit} saves the
     * whole batch, so a caller can post it as one task and roll back inside that task.
     */
    public static final class Prepared {
        @Nullable private final Outcome mEarly;
        @Nullable private final String mFingerprint;
        @Nullable private final SettingsFile.Result mChecked;

        private Prepared(@Nullable Outcome early, @Nullable String fingerprint,
                         @Nullable SettingsFile.Result checked) {
            mEarly = early;
            mFingerprint = fingerprint;
            mChecked = checked;
        }

        /** The answer when nothing is left to save (no file, unchanged, unreadable), else null. */
        @Nullable
        public Outcome early() {
            return mEarly;
        }

        /** Saves every line or none, hands the changes to {@code applier} and remembers the file. */
        @NonNull
        public Outcome commit(@NonNull Record record, @NonNull SettingsRegistry registry,
                              @NonNull SettingsApplier applier) {
            if (mEarly != null) return mEarly;
            SettingsFile.Applied applied = SettingsFile.apply(mChecked, registry);
            if (!applied.ok) return new Outcome(Kind.FAILED, Collections.emptyList(), applied.errors);
            for (SettingsRegistry.Change change : applied.changes) applier.apply(change);
            record.remember(mFingerprint);
            return new Outcome(Kind.APPLIED, applied.changes, Collections.emptyList());
        }
    }

    /** Fingerprints and checks {@code text} (null: no file) without saving anything. */
    @NonNull
    public static Prepared prepare(@Nullable String text, @NonNull Record record,
                                   @NonNull SettingsRegistry registry) {
        if (text == null) return new Prepared(new Outcome(Kind.ABSENT, Collections.emptyList(),
            Collections.emptyList()), null, null);
        String fingerprint = fingerprint(text);
        if (fingerprint.equals(record.applied())) {
            return new Prepared(new Outcome(Kind.UNCHANGED, Collections.emptyList(),
                Collections.emptyList()), null, null);
        }
        SettingsFile.Result checked = SettingsFile.check(text, registry);
        if (!checked.errors.isEmpty()) {
            return new Prepared(new Outcome(Kind.FAILED, Collections.emptyList(), checked.errors),
                null, null);
        }
        return new Prepared(null, fingerprint, checked);
    }

    /**
     * Applies {@code text} (null: no file) when it is not the file last applied, all of it or none,
     * handing each change to {@code applier}. A file that failed is not remembered, so saving it
     * again, or the next start, tells the user again.
     */
    @NonNull
    public static Outcome sync(@Nullable String text, @NonNull Record record,
                               @NonNull SettingsRegistry registry,
                               @NonNull SettingsApplier applier) {
        return prepare(text, record, registry).commit(record, registry, applier);
    }

    /** The content's SHA-256 as hex: content, not time, so a touch is not a change. */
    @NonNull
    public static String fingerprint(@NonNull String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte each : digest) hex.append(String.format("%02x", each));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
