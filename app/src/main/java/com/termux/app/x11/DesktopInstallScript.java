package com.termux.app.x11;

import androidx.annotation.NonNull;

/**
 * The script the Desktops page's Install runs for one desktop. Pure: a catalogue entry in, text
 * out. The run never asks a question, takes the package manager the prefix has (apt or pacman),
 * records which of the desktop's packages were absent before it installs anything (the manifest,
 * {@link DesktopCatalogue#MANIFEST_DIR}) and exits non-zero the moment an install step fails.
 */
final class DesktopInstallScript {
    private DesktopInstallScript() {}

    /** The script file's text: the shebang, no package question, then {@link #body}. */
    @NonNull
    static String script(@NonNull DesktopCatalogue.Desktop desktop) {
        return "#!/bin/bash\nexport DEBIAN_FRONTEND=noninteractive\n" + body(desktop);
    }

    /** The commands: record, add the x11 repository, refresh, install. */
    @NonNull
    static String body(@NonNull DesktopCatalogue.Desktop desktop) {
        String packages = String.join(" ", desktop.packages());
        StringBuilder out = new StringBuilder();
        out.append("DIR=\"$PREFIX/").append(DesktopCatalogue.MANIFEST_DIR).append("\"\n");
        out.append("MANIFEST=\"$DIR/").append(DesktopCatalogue.manifestFileName(desktop)).append("\"\n");
        out.append("mkdir -p \"$DIR\" || exit 1\n");
        out.append("touch \"$MANIFEST\" || exit 1\n");
        // A name is only ever added, so a retry after a half install keeps what the first run saw.
        out.append("note() { grep -qxF \"$1\" \"$MANIFEST\" 2>/dev/null || printf '%s\\n' \"$1\" >> \"$MANIFEST\"; }\n");
        for (DesktopCatalogue.Piece piece : desktop.pieces()) {
            out.append("{ ");
            String[] files = piece.files();
            for (int i = 0; i < files.length; i++) {
                if (i > 0) out.append(" || ");
                out.append("[ -e \"$PREFIX/").append(files[i]).append("\" ]");
            }
            out.append("; } || note ").append(piece.packageName).append('\n');
        }
        // The x11 repository carries the desktops; an install without one (the VAJ edition) has them already.
        out.append("pkg install -y x11-repo || true\n");
        out.append("if [ -x \"$PREFIX/bin/pacman\" ]; then\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.PACMAN.refresh()).append(" || exit 1\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.PACMAN.install(packages)).append(" || exit 1\n");
        out.append("else\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.APT.refresh()).append(" || exit 1\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.APT.install(packages)).append(" || exit 1\n");
        out.append("fi\n");
        return out.toString();
    }

    /** A run counts as done when it exited 0 and the desktop's own files are in the prefix. */
    static boolean succeeded(int exitStatus, boolean desktopPresent) {
        return exitStatus == 0 && desktopPresent;
    }
}
