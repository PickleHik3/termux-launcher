package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Terl's launcher layouts (ADR 0042) stand in for the bundled launcher layout while the Terl
 * desktop switch is on: the same keys with the Terl key on Ctrl's north-east corner, the clipboard
 * key on Alt's and the switch-keyboard key on Alt's south-west, in the layout Settings chose
 * ({@code keyboard.layout}: QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, or Custom). Custom is the
 * user's own {@code layout.xml}; until there is one it shows the layout that was chosen before.
 * With the switch off the keyboard is today's. Every place that loads a bundled layout asks
 * {@link #forTerl} as it loads, so the switch and the choice are read then.
 */
public final class TerlKeyboardLayout {

    /** The preference {@code keyboard.layout} is kept under. */
    public static final String KEY = "terl_keyboard_layout";
    /** The layout in use before Custom was chosen: what Custom shows until its file exists. */
    public static final String BASE_KEY = "terl_keyboard_layout_base";

    public static final String QWERTY = "qwerty";
    public static final String CUSTOM = "custom";

    /** The choices, in the order the declaration lists them. */
    public static final List<String> CHOICES =
        Collections.unmodifiableList(Arrays.asList(QWERTY, "qwertz", "azerty", "dvorak", "colemak", CUSTOM));

    /** Today's launcher layout. */
    public static final int LAUNCHER = juloo.keyboard2.R.xml.termux_launcher_qwerty;
    /** The launcher layout under Terl (QWERTY). */
    public static final int TERL = juloo.keyboard2.R.xml.terl_launcher_qwerty;

    private TerlKeyboardLayout() {}

    /** Terl's layout for {@code choice}; QWERTY for Custom and for anything unknown. */
    public static int resFor(@Nullable String choice) {
        if (choice == null) return TERL;
        switch (choice) {
            case "qwertz": return juloo.keyboard2.R.xml.terl_launcher_qwertz;
            case "azerty": return juloo.keyboard2.R.xml.terl_launcher_azerty;
            case "dvorak": return juloo.keyboard2.R.xml.terl_launcher_dvorak;
            case "colemak": return juloo.keyboard2.R.xml.terl_launcher_colemak;
            default: return TERL;
        }
    }

    /** {@code stored} when it is a choice, else QWERTY. */
    @NonNull
    public static String normalize(@Nullable String stored) {
        return stored != null && CHOICES.contains(stored) ? stored : QWERTY;
    }

    /** The layout Terl's launcher keyboard shows for {@code choice} and {@code base}. */
    public static int resFor(@Nullable String choice, @Nullable String base) {
        String chosen = normalize(choice);
        return resFor(CUSTOM.equals(chosen) ? normalize(base) : chosen);
    }

    /** {@code resId}, or Terl's QWERTY in place of the launcher layout when {@code terl}. */
    public static int forTerl(int resId, boolean terl) {
        return forTerl(resId, terl, QWERTY, QWERTY);
    }

    /** {@code resId}, or Terl's layout for {@code choice} in place of the launcher layout. */
    public static int forTerl(int resId, boolean terl, @Nullable String choice,
                              @Nullable String base) {
        return terl && resId == LAUNCHER ? resFor(choice, base) : resId;
    }

    /** {@link #forTerl} with the switch and choice as stored now; today's when off or unreadable. */
    public static int forTerl(int resId, @Nullable Context context) {
        if (resId != LAUNCHER || !isOn(context)) return resId;
        return forTerl(resId, true, choice(context), base(context));
    }

    /** Whether the Terl desktop switch is on. */
    public static boolean isOn(@Nullable Context context) {
        if (context == null) return false;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        return preferences != null && preferences.isTerlDesktopEnabled();
    }

    /** The stored {@code keyboard.layout}; QWERTY when unset or unreadable. */
    @NonNull
    public static String choice(@Nullable Context context) {
        return normalize(read(context, KEY));
    }

    /** The layout in use before Custom was chosen. */
    @NonNull
    public static String base(@Nullable Context context) {
        return normalize(read(context, BASE_KEY));
    }

    /**
     * Whether the user's own {@code layout.xml} is ignored: under Terl it is the layout only when
     * Custom is chosen, so choosing another layout takes effect at once.
     */
    public static boolean ignoresMainFile(@Nullable Context context) {
        return isOn(context) && !CUSTOM.equals(choice(context));
    }

    /** Chooses Custom, remembering the layout it starts from; an unchanged Custom keeps its base. */
    public static void chooseCustom(@Nullable Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs == null) return;
        String current = normalize(prefs.getString(KEY, QWERTY));
        SharedPreferences.Editor edit = prefs.edit();
        if (!CUSTOM.equals(current)) edit.putString(BASE_KEY, current);
        edit.putString(KEY, CUSTOM).apply();
    }

    @Nullable
    private static String read(@Nullable Context context, @NonNull String key) {
        SharedPreferences prefs = prefs(context);
        return prefs == null ? null : prefs.getString(key, null);
    }

    @Nullable
    private static SharedPreferences prefs(@Nullable Context context) {
        if (context == null) return null;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        return preferences == null ? null : preferences.getSharedPreferences();
    }
}
