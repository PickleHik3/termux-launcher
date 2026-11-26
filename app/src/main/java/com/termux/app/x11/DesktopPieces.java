package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which pieces of a whole Termux XFCE desktop ({@link GuiAppsSetup#DESKTOP_TERMUX_PACKAGES}) a
 * prefix lacks, read off the files each package provides. An install that predates the full list
 * has only the {@code xfce4} metapackage, which starts a desktop with no wallpaper. Pure: the
 * caller supplies the file test.
 */
final class DesktopPieces {
    private DesktopPieces() {}

    /**
     * The files, relative to the prefix, that show {@code packageName} is installed: any one of
     * them is enough. Empty for a package this does not know, which is then never reported.
     */
    @NonNull
    static String[] filesFor(@Nullable String packageName) {
        if (packageName == null) return new String[0];
        switch (packageName) {
            case "xfce4": return new String[]{"bin/startxfce4", "bin/xfce4-session"};
            case "xfdesktop": return new String[]{"bin/xfdesktop"};
            case "xfce4-pulseaudio-plugin": return new String[]{
                "lib/xfce4/panel/plugins/libpulseaudio-plugin.so",
                "share/xfce4/panel/plugins/pulseaudio.desktop"};
            case "pavucontrol": return new String[]{"bin/pavucontrol"};
            case "xfce4-whiskermenu-plugin": return new String[]{
                "lib/xfce4/panel/plugins/libwhiskermenu.so",
                "share/xfce4/panel/plugins/whiskermenu.desktop"};
            case "tumbler": return new String[]{"lib/tumbler-1", "libexec/tumbler-1",
                "share/dbus-1/services/org.xfce.Tumbler.Thumbnailer1.service"};
            case "thunar-archive-plugin": return new String[]{
                "lib/thunarx-3/thunar-archive-plugin.so"};
            case "xarchiver": return new String[]{"bin/xarchiver"};
            case "xfce4-screenshooter": return new String[]{"bin/xfce4-screenshooter"};
            case "xfce4-clipman-plugin": return new String[]{"bin/xfce4-clipman",
                "lib/xfce4/panel/plugins/libclipman.so"};
            case "ristretto": return new String[]{"bin/ristretto"};
            case "mousepad": return new String[]{"bin/mousepad"};
            case "papirus-icon-theme": return new String[]{"share/icons/Papirus"};
            default: return new String[0];
        }
    }

    /**
     * The packages of {@code wanted} whose files {@code exists} finds none of, in order. A null
     * list, or a package with no known file, reports nothing.
     */
    @NonNull
    static List<String> missing(@Nullable String[] wanted, @NonNull Predicate<String> exists) {
        if (wanted == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String name : wanted) {
            String[] files = filesFor(name);
            if (files.length == 0) continue;
            boolean found = false;
            for (String file : files) {
                if (exists.test(file)) { found = true; break; }
            }
            if (!found) out.add(name);
        }
        return out;
    }

    /** The command that installs {@code packages} on either package manager; null for none. */
    @Nullable
    static String installCommand(@NonNull List<String> packages) {
        if (packages.isEmpty()) return null;
        return "pkg install -y x11-repo && pkg install -y " + String.join(" ", packages);
    }
}
