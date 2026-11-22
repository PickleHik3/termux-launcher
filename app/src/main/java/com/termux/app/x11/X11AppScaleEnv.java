package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The environment that makes a toolkit draw one app at its own scale. Display-wide {@code Xft.dpi}
 * is the display's and is never changed per app.
 *
 * <p>GTK takes whole numbers only for widgets, so the integer part goes in {@code GDK_SCALE} and
 * the rest is text size ({@code GDK_DPI_SCALE}): 1.5 is 1 and 1.5, never 2 and 0.75, which would
 * draw every widget twice as large and only shrink the text. Qt takes the factor as it is.
 */
public final class X11AppScaleEnv {

    /** Follow the display: no variables, no DPI. */
    public static final X11AppScaleEnv FOLLOW = new X11AppScaleEnv(Collections.emptyMap());

    @NonNull public final Map<String, String> env;

    private X11AppScaleEnv(@NonNull Map<String, String> env) {
        this.env = env;
    }

    /** {@code scale} is one of {@link X11AppSettings#SCALES}; null or anything else follows. */
    @NonNull
    public static X11AppScaleEnv of(@Nullable String scale) {
        if (!X11AppSettings.isScale(scale)) return FOLLOW;
        BigDecimal s = new BigDecimal(scale);
        int gdk = Math.max(1, s.intValue());
        BigDecimal dpiScale = s.divide(BigDecimal.valueOf(gdk));
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GDK_SCALE", String.valueOf(gdk));
        env.put("GDK_DPI_SCALE", format(dpiScale));
        env.put("QT_SCALE_FACTOR", format(s));
        // Qt 5 only honours the factor with its automatic detection off; Qt 6 scales by default.
        env.put("QT_AUTO_SCREEN_SCALE_FACTOR", "0");
        env.put("QT_ENABLE_HIGHDPI_SCALING", "1");
        return new X11AppScaleEnv(Collections.unmodifiableMap(env));
    }

    @NonNull
    private static String format(@NonNull BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().toLowerCase(Locale.ROOT);
    }
}
