package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * The status bar's notifications mark (editor-redesign §2, Status bar > Indicators): a bell and
 * the count of the notifications waiting, the same ones the dock badges its icons with
 * ({@code LauncherNotificationBadgeStore}). Pure, so a JVM test holds it.
 */
public final class StatusNotificationsMark {

    /** The most the count reads before it says "9+": the mark stays one narrow glyph wide. */
    public static final int MAX_SHOWN = 9;

    /** nf-md-bell, from the bar's own symbol font, as the mouse mark's glyph is. */
    public static final String GLYPH = "󰂚";

    private StatusNotificationsMark() {}

    /** Whether the mark stands: the indicator is on and something is waiting. */
    public static boolean shown(boolean enabled, int count) {
        return enabled && count > 0;
    }

    /** What the mark reads beside its bell: the count, "9+" past nine, nothing with none. */
    @NonNull
    public static String label(int count) {
        if (count <= 0)
            return "";
        return count > MAX_SHOWN ? MAX_SHOWN + "+" : String.valueOf(count);
    }
}
