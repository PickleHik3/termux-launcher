package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * The Roundline icons the home widgets and their editing chrome draw. Planners and views still
 * hand around one-character "glyph" strings; each of these is a private plane-16 code point that
 * stands for a drawable, so a plan stays plain text and a test can compare it. Whatever draws a
 * glyph asks {@link #res} first and draws the drawable, tinted with the ink the glyph had, when
 * it is one of these. Android-free except {@link #drawable} and {@link #label}.
 */
public final class WidgetIcons {
    private WidgetIcons() { }

    private static final int BASE = 0x10FE00;

    private static final int[] RES = {
        R.drawable.ic_rl_calendar, R.drawable.ic_rl_chevron_left, R.drawable.ic_rl_chevron_right,
        R.drawable.ic_rl_alarm, R.drawable.ic_rl_clock, R.drawable.ic_rl_sun, R.drawable.ic_rl_moon,
        R.drawable.ic_rl_battery_low, R.drawable.ic_rl_play, R.drawable.ic_rl_pause,
        R.drawable.ic_rl_skip_previous, R.drawable.ic_rl_skip_next, R.drawable.ic_rl_lock,
        R.drawable.ic_rl_note, R.drawable.ic_rl_bell, R.drawable.ic_rl_reply, R.drawable.ic_rl_close,
        R.drawable.ic_rl_message, R.drawable.ic_rl_cpu, R.drawable.ic_rl_memory,
        R.drawable.ic_rl_temperature, R.drawable.ic_rl_battery, R.drawable.ic_rl_disk,
        R.drawable.ic_rl_power, R.drawable.ic_rl_add, R.drawable.ic_rl_checkbox_empty,
        R.drawable.ic_rl_checkbox_checked, R.drawable.ic_rl_settings, R.drawable.ic_rl_chevron_up,
        R.drawable.ic_rl_chevron_down,
    };

    private static String glyph(int index) { return new String(Character.toChars(BASE + index)); }

    public static final String CALENDAR = glyph(0);
    public static final String CHEVRON_LEFT = glyph(1);
    public static final String CHEVRON_RIGHT = glyph(2);
    public static final String ALARM = glyph(3);
    public static final String CLOCK = glyph(4);
    public static final String SUN = glyph(5);
    public static final String MOON = glyph(6);
    public static final String BATTERY_LOW = glyph(7);
    public static final String PLAY = glyph(8);
    public static final String PAUSE = glyph(9);
    public static final String SKIP_PREVIOUS = glyph(10);
    public static final String SKIP_NEXT = glyph(11);
    /** The Media widget's "allow notification access" prompt. */
    public static final String LOCK = glyph(12);
    public static final String NOTE = glyph(13);
    public static final String BELL = glyph(14);
    public static final String REPLY = glyph(15);
    public static final String CLOSE = glyph(16);
    public static final String MESSAGE = glyph(17);
    public static final String CPU = glyph(18);
    public static final String MEMORY = glyph(19);
    public static final String TEMPERATURE = glyph(20);
    public static final String BATTERY = glyph(21);
    public static final String DISK = glyph(22);
    public static final String POWER = glyph(23);
    public static final String ADD = glyph(24);
    public static final String CHECKBOX_EMPTY = glyph(25);
    public static final String CHECKBOX_CHECKED = glyph(26);
    public static final String SETTINGS = glyph(27);
    public static final String CHEVRON_UP = glyph(28);
    public static final String CHEVRON_DOWN = glyph(29);

    /** The drawable {@code glyph} stands for, or 0 when it is ordinary text. */
    @DrawableRes public static int res(@Nullable String glyph) {
        if (glyph == null || glyph.isEmpty()) return 0;
        int codePoint = glyph.codePointAt(0);
        if (Character.charCount(codePoint) != glyph.length()) return 0;
        int index = codePoint - BASE;
        return index >= 0 && index < RES.length ? RES[index] : 0;
    }

    /** A private, tinted copy of the icon {@code glyph} stands for; null for ordinary text. */
    @Nullable public static Drawable drawable(@NonNull Context context, @Nullable String glyph,
                                              @ColorInt int color) {
        int res = res(glyph);
        if (res == 0) return null;
        Drawable drawable = context.getDrawable(res);
        if (drawable == null) return null;
        drawable = drawable.mutate();
        drawable.setTint(color | 0xFF000000);
        drawable.setAlpha(color >>> 24);
        return drawable;
    }

    /**
     * The text a TextView shows for {@code glyph}: a one-character span that draws the icon at
     * the text's size in its colour, or the glyph itself when it is ordinary text.
     */
    @NonNull public static CharSequence label(@NonNull Context context, @NonNull String glyph) {
        int res = res(glyph);
        return res == 0 ? glyph : NerdFontSpans.iconSpan(context, res);
    }
}
