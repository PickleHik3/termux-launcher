package com.termux.shared.settings.preferences;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Values shown without being stored: while a preview is up, every read of one store through
 * {@link SharedPreferenceUtils} is answered by the preview's own {@link SharedPreferences} instead,
 * which reads through to the store for anything it does not hold. Nothing is written, no change
 * listener fires and no disk write is queued; ending the preview puts every reader back on the
 * store, and whoever showed it decides whether its values are then written.
 *
 * <p>One preview at a time, for the whole process: the Appearance editor's Look slider uses it
 * while a finger drags across the Looks, so the launcher draws each Look it passes and stores only
 * the one it is let go on. The preview must read through to the store without going back through
 * {@link SharedPreferenceUtils}, and must not change after it is shown (it may be read off the
 * main thread); a new picture is a new {@link #show}.</p>
 *
 * <p>Only reads made through {@link SharedPreferenceUtils} see it. A caller that reads the store's
 * own methods directly sees what is stored.</p>
 */
public final class SharedPreferencesPreview {

    private SharedPreferencesPreview() {}

    private static final class Shown {
        @NonNull final SharedPreferences store;
        @NonNull final SharedPreferences reads;

        Shown(@NonNull SharedPreferences store, @NonNull SharedPreferences reads) {
            this.store = store;
            this.reads = reads;
        }
    }

    @Nullable private static volatile Shown sShown;

    /** Answers reads of {@code store} from {@code reads} until {@link #clear}, or the next show. */
    public static void show(@NonNull SharedPreferences store, @NonNull SharedPreferences reads) {
        sShown = new Shown(store, reads);
    }

    /** Every reader is back on what is stored. */
    public static void clear() {
        sShown = null;
    }

    public static boolean isShowing() {
        return sShown != null;
    }

    /** What a read of {@code store} should ask: the preview over it while one is up, else itself. */
    @Nullable
    public static SharedPreferences readsFor(@Nullable SharedPreferences store) {
        Shown shown = sShown;
        return shown != null && store != null && shown.store == store ? shown.reads : store;
    }
}
