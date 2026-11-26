package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * What the Desktops page's Remove does for one desktop: which packages go ({@link
 * #packagesToRemove}) and the script that removes them ({@link #script}). Pure.
 *
 * <p>The rule for which packages go, written once:
 * <ul>
 *   <li>The desktop's own package (the first piece, its proof of install) always goes, so the row
 *       really flips to Not installed.
 *   <li>With an install manifest ({@link DesktopCatalogue#MANIFEST_DIR}): the other packages the
 *       manifest names, which are the ones that were absent before the page installed the desktop.
 *       Anything the user had installed beforehand is not in it, so it stays.
 *   <li>With no manifest (an XFCE from before records): the catalogue's list for the desktop.
 *   <li>Less everything another installed desktop (its catalogue list and its manifest) and the
 *       set-up guide's starter apps ({@link GuiAppsSetup#termuxSetupPackages}) still claim.
 * </ul>
 * The script removes those, then what nothing needs any more. On apt a package is removed only if
 * a dry run shows it takes nothing outside the plan with it (apt removes reverse dependents, which
 * could be the user's own packages) and {@code autoremove} only takes automatically installed
 * packages, so a dependency the user installed by name stays. On pacman {@code -Rs} stops at a
 * package something else requires and keeps dependencies that were installed explicitly. The
 * desktop's settings (folders and loose files) go only when asked.
 */
final class DesktopRemoveScript {
    private DesktopRemoveScript() {}

    /** A plain package name; anything else never reaches a shell. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9+._-]*");

    /** The package names in manifest text: one per line, blanks ignored. */
    @NonNull
    static List<String> parseManifest(@Nullable String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String name = line.trim();
            if (!name.isEmpty()) out.add(name);
        }
        return out;
    }

    /**
     * The packages some other part of the launcher's set-up still needs: {@code setupPackages},
     * and for every other desktop that is {@code installed}, its catalogue packages and its
     * manifest's ({@code manifestOf} answers null for no manifest).
     */
    @NonNull
    static Set<String> claimed(@NonNull DesktopCatalogue.Desktop target,
                               @NonNull Function<DesktopCatalogue.Desktop, List<String>> manifestOf,
                               @NonNull Predicate<DesktopCatalogue.Desktop> installed,
                               @NonNull Collection<String> setupPackages) {
        Set<String> out = new HashSet<>(setupPackages);
        for (DesktopCatalogue.Desktop other : DesktopCatalogue.all()) {
            if (other.id.equals(target.id) || !installed.test(other)) continue;
            for (String name : other.packages()) out.add(name);
            List<String> manifest = manifestOf.apply(other);
            if (manifest != null) out.addAll(manifest);
        }
        return out;
    }

    /** The packages to remove, in order; see the class comment. {@code manifest} null = no file. */
    @NonNull
    static List<String> packagesToRemove(@NonNull DesktopCatalogue.Desktop desktop,
                                         @Nullable List<String> manifest,
                                         @NonNull Set<String> claimed) {
        String[] own = desktop.packages();
        Set<String> wanted = new LinkedHashSet<>();
        if (own.length > 0) wanted.add(own[0]);
        if (manifest == null) {
            for (String name : own) wanted.add(name);
        } else {
            wanted.addAll(manifest);
        }
        List<String> out = new ArrayList<>();
        for (String name : wanted) {
            if (NAME.matcher(name).matches() && !claimed.contains(name)) out.add(name);
        }
        return out;
    }

    /** The script file's text: the shebang, no package question, then {@link #body}. */
    @NonNull
    static String script(@NonNull DesktopCatalogue.Desktop desktop, @NonNull List<String> packages,
                         boolean deleteSettings) {
        return "#!/bin/bash\nexport DEBIAN_FRONTEND=noninteractive\n"
            + body(desktop, packages, deleteSettings);
    }

    /** The commands: stop a running desktop, remove, autoremove, settings, manifest. */
    @NonNull
    static String body(@NonNull DesktopCatalogue.Desktop desktop, @NonNull List<String> packages,
                       boolean deleteSettings) {
        StringBuilder out = new StringBuilder();
        // A desktop on the display is stopped first, in a subshell because that script exits.
        out.append("(\n").append(X11LinuxAppRunner.stopSessionScript()).append(")\n");
        if (!packages.isEmpty()) {
            out.append("PLAN=\"").append(String.join(" ", packages)).append("\"\n");
            out.append("HAVE=\"\"\n");
            out.append("if [ -x \"$PREFIX/bin/pacman\" ]; then\n");
            out.append("  for p in $PLAN; do pacman -Qq \"$p\" >/dev/null 2>&1 && HAVE=\"$HAVE $p\"; done\n");
            out.append("  if [ -n \"$HAVE\" ]; then\n");
            // All at once; if something outside the plan needs one, pacman refuses, so one by one.
            out.append("    pacman -Rs --noconfirm $HAVE || {\n");
            out.append("      for p in $HAVE; do\n");
            out.append("        pacman -Rs --noconfirm \"$p\" || echo \"Keeping $p: something else needs it\"\n");
            out.append("      done\n");
            out.append("    }\n");
            out.append("  fi\n");
            out.append("else\n");
            out.append("  for p in $PLAN; do dpkg -s \"$p\" >/dev/null 2>&1 && HAVE=\"$HAVE $p\"; done\n");
            out.append("  PLANFILE=$(mktemp) || exit 1\n");
            out.append("  printf '%s\\n' $PLAN > \"$PLANFILE\"\n");
            out.append("  SAFE=\"\"\n");
            out.append("  for p in $HAVE; do\n");
            // A dry run: any package it would remove that is not in the plan is somebody's own.
            out.append("    OUTSIDE=$(apt-get -s remove \"$p\" 2>/dev/null"
                + " | sed -n 's/^Remv \\([^ ]*\\).*/\\1/p' | grep -vxFf \"$PLANFILE\")\n");
            out.append("    if [ -n \"$OUTSIDE\" ]; then echo \"Keeping $p: something else needs it\";"
                + " else SAFE=\"$SAFE $p\"; fi\n");
            out.append("  done\n");
            out.append("  rm -f \"$PLANFILE\"\n");
            out.append("  if [ -n \"$SAFE\" ]; then apt-get remove -y $SAFE || exit 1; fi\n");
            out.append("  apt-get autoremove -y || exit 1\n");
            out.append("fi\n");
        }
        if (deleteSettings) {
            for (String path : desktop.settingsPaths()) {
                out.append("rm -rf \"$HOME/").append(path).append("\" || exit 1\n");
            }
        }
        out.append("DIR=\"$PREFIX/").append(DesktopCatalogue.MANIFEST_DIR).append("\"\n");
        out.append("MANIFEST=\"$DIR/").append(DesktopCatalogue.manifestFileName(desktop)).append("\"\n");
        out.append("rm -f \"$MANIFEST\" || exit 1\n");
        return out.toString();
    }

    /** A run counts as done when it exited 0 and the desktop's own files are gone from the prefix. */
    static boolean succeeded(int exitStatus, boolean desktopPresent) {
        return exitStatus == 0 && !desktopPresent;
    }
}
