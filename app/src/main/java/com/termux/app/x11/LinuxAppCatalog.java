package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Linux apps installed in the prefix, read from their {@code .desktop} files the way any
 * desktop's menu reads them. The launcher lists these in its app drawer beside Android apps; a
 * tap runs the app on the display.
 *
 * <p>Pure file reading, so it is tested against fixture files. Nothing here touches the display.
 */
public final class LinuxAppCatalog {

    /** One launchable entry: what to show and what to run. */
    public static final class LinuxApp {
        /** The desktop file's name without its extension — stable across reinstalls. */
        @NonNull public final String id;
        @NonNull public final String name;
        /** The Exec line with its field codes ({@code %f %u …}) removed; a shell command line. */
        @NonNull public final String exec;
        /** The Icon key: a theme icon name or an absolute path, or empty. */
        @NonNull public final String icon;
        @NonNull public final String comment;

        LinuxApp(@NonNull String id, @NonNull String name, @NonNull String exec,
                 @NonNull String icon, @NonNull String comment) {
            this.id = id;
            this.name = name;
            this.exec = exec;
            this.icon = icon;
            this.comment = comment;
        }
    }

    private LinuxAppCatalog() {}

    /** Where the prefix keeps desktop files. */
    @NonNull
    public static List<File> applicationDirs() {
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        List<File> dirs = new ArrayList<>(2);
        dirs.add(new File(prefix + "/share/applications"));
        dirs.add(new File(prefix + "/local/share/applications"));
        return dirs;
    }

    /** Every launchable app in {@code dirs}, sorted by name; a later dir does not shadow an earlier id. */
    @NonNull
    public static List<LinuxApp> scan(@NonNull List<File> dirs) {
        List<LinuxApp> apps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".desktop"));
            if (files == null) continue;
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - ".desktop".length());
                if (seen.contains(id)) continue;
                LinuxApp app = parse(id, file);
                if (app == null) continue;
                seen.add(id);
                apps.add(app);
            }
        }
        Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return apps;
    }

    /**
     * A cheap fingerprint of {@code dirs} — their modification times — so a caller can tell that
     * something was installed or removed without reading every file.
     */
    public static long signature(@NonNull List<File> dirs) {
        long signature = 0L;
        for (File dir : dirs) {
            signature = signature * 31 + dir.lastModified();
            String[] names = dir.list();
            signature = signature * 31 + (names == null ? -1 : names.length);
        }
        return signature;
    }

    /**
     * Read one desktop file. Null when it is not an application, asks not to be shown, wants a
     * terminal (a command-line tool with a menu entry, not a window), or names a binary that is
     * not installed.
     */
    @Nullable
    static LinuxApp parse(@NonNull String id, @NonNull File file) {
        String type = "", name = "", exec = "", icon = "", comment = "", tryExec = "";
        boolean noDisplay = false, hidden = false, terminal = false, inEntry = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    inEntry = line.equals("[Desktop Entry]");
                    continue;
                }
                if (!inEntry) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "Type": type = value; break;
                    case "Name": name = value; break;
                    case "Exec": exec = value; break;
                    case "TryExec": tryExec = value; break;
                    case "Icon": icon = value; break;
                    case "Comment": comment = value; break;
                    case "NoDisplay": noDisplay = "true".equalsIgnoreCase(value); break;
                    case "Hidden": hidden = "true".equalsIgnoreCase(value); break;
                    case "Terminal": terminal = "true".equalsIgnoreCase(value); break;
                    default: break;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (!"Application".equals(type) || name.isEmpty() || exec.isEmpty()) return null;
        if (noDisplay || hidden || terminal) return null;
        if (!tryExec.isEmpty() && !executableExists(tryExec, file)) return null;
        return new LinuxApp(id, name, stripFieldCodes(exec), icon, comment);
    }

    private static boolean executableExists(@NonNull String tryExec, @NonNull File desktopFile) {
        if (tryExec.startsWith("/")) return new File(tryExec).canExecute();
        // Relative: the prefix's bin, or — for fixtures — the directory beside the desktop file.
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, tryExec).canExecute()
            || new File(desktopFile.getParentFile(), tryExec).canExecute();
    }

    /**
     * Drop the desktop-entry field codes: {@code %f %F %u %U %d %D %n %N %i %c %k %v %m} and the
     * literal {@code %%}. The launcher opens no files, so every code is an empty expansion.
     */
    @NonNull
    static String stripFieldCodes(@NonNull String exec) {
        StringBuilder out = new StringBuilder(exec.length());
        for (int i = 0; i < exec.length(); i++) {
            char c = exec.charAt(i);
            if (c == '%' && i + 1 < exec.length()) {
                char code = exec.charAt(i + 1);
                if (code == '%') {
                    out.append('%');
                } else if ("fFuUdDnNickvm".indexOf(code) < 0) {
                    out.append(c).append(code);
                }
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }

    /** Case-insensitive lookup by id. */
    @Nullable
    public static LinuxApp find(@NonNull List<LinuxApp> apps, @NonNull String id) {
        for (LinuxApp app : apps) {
            if (app.id.equalsIgnoreCase(id)) return app;
        }
        return null;
    }

    @NonNull
    static String lower(@NonNull String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
