package com.termux.app.chrome;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Whether the system is on gesture navigation, read at the moment of a Home press so a change in
 * Settings is seen at once. With gestures the system animates the window itself while the finger
 * swipes up, so the launcher lands on its home page without a motion of its own.
 */
public final class GestureNavigation {

    /** {@code Settings.Secure} {@code navigation_mode}: 0 three buttons, 1 two buttons, 2 gestures. */
    static final String SETTING = "navigation_mode";
    static final int MODE_GESTURES = 2;
    static final int UNKNOWN = -1;

    private GestureNavigation() { }

    /**
     * The setting first; when it cannot be read, the insets (a navigation bar that is not
     * tappable is the gesture pill); when neither answers, buttons, which keeps today's motion.
     */
    public static boolean isActive(@NonNull Context context, @Nullable View decor) {
        int mode = UNKNOWN;
        try {
            mode = Settings.Secure.getInt(context.getContentResolver(), SETTING, UNKNOWN);
        } catch (RuntimeException ignored) {
            // Unreadable: fall through to the insets.
        }
        WindowInsetsCompat insets = decor == null ? null : ViewCompat.getRootWindowInsets(decor);
        return resolve(mode,
            insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
            insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom,
            insets != null);
    }

    /** Pure: the setting when it answered, else the insets, else buttons. */
    static boolean resolve(int mode, int navBarBottomPx, int tappableBottomPx, boolean haveInsets) {
        if (mode >= 0) return mode == MODE_GESTURES;
        return haveInsets && navBarBottomPx > 0 && tappableBottomPx == 0;
    }
}
