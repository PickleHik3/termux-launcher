package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The environment and Xft DPI that make a toolkit draw one app at a scale. For the launch path to
 * call later; not wired yet.
 */
public final class X11AppScaleEnv {

    /** Follow the display: no variables, no DPI. */
    public static final X11AppScaleEnv FOLLOW = new X11AppScaleEnv(Collections.emptyMap(), 0);

    @NonNull public final Map<String, String> env;
    /** {@code Xft.dpi} for the app, 0 when it follows the display. */
    public final int xftDpi;

    private X11AppScaleEnv(@NonNull Map<String, String> env, int xftDpi) {
        this.env = env;
        this.xftDpi = xftDpi;
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
        return new X11AppScaleEnv(Collections.unmodifiableMap(env), s.multiply(BigDecimal.valueOf(96)).intValue());
    }

    @NonNull
    private static String format(@NonNull BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().toLowerCase(Locale.ROOT);
    }
}
