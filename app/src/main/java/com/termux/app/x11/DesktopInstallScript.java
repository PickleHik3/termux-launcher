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
        return script(desktop, 1f);
    }

    /** {@link #script(DesktopCatalogue.Desktop)} with the output scale a starter config gets. */
    @NonNull
    static String script(@NonNull DesktopCatalogue.Desktop desktop, float scale) {
        return "#!/bin/bash\nexport DEBIAN_FRONTEND=noninteractive\n" + body(desktop, scale);
    }

    @NonNull
    static String body(@NonNull DesktopCatalogue.Desktop desktop) {
        return body(desktop, 1f);
    }

    /** The commands: record, add the x11 repository, refresh, install, then the starter config. */
    @NonNull
    static String body(@NonNull DesktopCatalogue.Desktop desktop, float scale) {
        String packages = String.join(" ", desktop.packages());
        StringBuilder out = new StringBuilder(record(desktop));
        // The x11 repository carries the desktops; an install without one (the VAJ edition) has them already.
        out.append("pkg install -y x11-repo || true\n");
        out.append("if [ -x \"$PREFIX/bin/pacman\" ]; then\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.PACMAN.refresh()).append(" || exit 1\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.PACMAN.install(packages)).append(" || exit 1\n");
        out.append("else\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.APT.refresh()).append(" || exit 1\n");
        out.append("  ").append(GuiAppsSetup.PackageManager.APT.install(packages)).append(" || exit 1\n");
        out.append("fi\n");
        out.append(starterConfig(desktop, scale));
        return out.toString();
    }

    /**
     * The starter config of a Wayland desktop, written after the install (the stock file is the
     * package's) and only when the user's file does not exist: the stock file, then the scale
     * line. Nothing for a desktop with no starter.
     */
    @NonNull
    static String starterConfig(@NonNull DesktopCatalogue.Desktop desktop, float scale) {
        DesktopCatalogue.Starter starter = desktop.starterConfig;
        if (starter == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("CONFIG=\"$HOME/").append(starter.configPath).append("\"\n");
        out.append("STOCK=\"$PREFIX/").append(starter.stockPath).append("\"\n");
        out.append("if [ ! -e \"$CONFIG\" ] && [ ! -L \"$CONFIG\" ]; then\n");
        out.append("  mkdir -p \"$(dirname \"$CONFIG\")\" || exit 1\n");
        out.append("  {\n");
        out.append("    if [ -f \"$STOCK\" ]; then cat \"$STOCK\"; fi\n");
        out.append("    printf '\\n# %s\\n%s\\n' ")
            .append(ProotDistro.singleQuote(starter.scaleComment)).append(' ')
            .append(ProotDistro.singleQuote(starter.scaleLine(scale))).append('\n');
        out.append("  } > \"$CONFIG.new\" && mv \"$CONFIG.new\" \"$CONFIG\" || exit 1\n");
        out.append("fi\n");
        return out.toString();
    }

    /**
     * The record alone: the manifest of the desktop's packages that are absent now. It must run
     * before anything installs; the set-up guide's command runs it ahead of its own install so
     * the page can remove what the guide installed.
     */
    @NonNull
    static String record(@NonNull DesktopCatalogue.Desktop desktop) {
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
        return out.toString();
    }

    /** A run counts as done when it exited 0 and the desktop's own files are in the prefix. */
    static boolean succeeded(int exitStatus, boolean desktopPresent) {
        return exitStatus == 0 && desktopPresent;
    }
}
