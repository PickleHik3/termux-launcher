package com.termux.app.terminal.io;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * Every page's extra-keys row is its own or follows Global. Assistant used to show a built-in row
 * (keyboard, dictate, paste) while it had none; this writes that row as Assistant's own, once, so
 * it stays. Gated by a preference so a user who later makes Assistant follow Global is not
 * written over.
 */
public final class AssistantKeysMigration {

    private static final String LOG_TAG = "AssistantKeysMigration";

    private AssistantKeysMigration() {}

    /** Writes one property line to termux.properties. */
    public interface Writer {
        void write(@NonNull String key, @NonNull String value);
    }

    /** Start-up entry: the real properties file and the launcher preferences; never throws. */
    public static void runAtStartUp(@NonNull Context context, @Nullable TermuxAppSharedPreferences preferences) {
        if (preferences == null) return;
        try {
            run(preferences.getSharedPreferences(),
                TermuxPropertiesFile.load(context)::getProperty, TermuxPropertiesFile::write);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not write Assistant's key row", e);
        }
    }

    /**
     * Writes Assistant's row when it has none, unless this already ran. The flag is set only after
     * the write, so a failed write is tried again next start.
     *
     * @return whether a row was written
     */
    public static boolean run(@Nullable SharedPreferences prefs, @NonNull ExtraKeySetResolver.Lines lines,
                              @NonNull Writer writer) {
        if (prefs == null || prefs.getBoolean(TERMUX_APP.KEY_EXTRA_KEYS_ASSISTANT_ROW_WRITTEN, false)) return false;
        String own = lines.get(TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT);
        boolean write = own == null || own.trim().isEmpty();
        if (write) {
            writer.write(TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT,
                TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT);
        }
        prefs.edit().putBoolean(TERMUX_APP.KEY_EXTRA_KEYS_ASSISTANT_ROW_WRITTEN, true).apply();
        return write;
    }
}
