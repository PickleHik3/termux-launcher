package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The faces the font configuration resolves to, kept while nothing they were read from changed.
 *
 * <p>Every new pane needs the faces, and resolving them re-reads kitty.conf, {@code fonts.d} and
 * {@code fonts.conf}, parses them and rescans the font folders. A new pane now costs a stat of
 * those inputs instead: the config files the load read or looked for, the font files a
 * {@code path=} line or the legacy {@code font.ttf} names, and — when a line names a family —
 * every entry of the folders a family is looked for in. Any change there loads afresh, so the face
 * chosen is always the one a fresh load would choose. Main thread only.
 */
final class TerminalFontMemo {

    static final class Entry {
        @NonNull final TerminalFontConfig.Result config;
        @NonNull final TerminalFontLoader.Faces faces;
        @Nullable final Long stamp;

        Entry(@NonNull TerminalFontConfig.Result config, @NonNull TerminalFontLoader.Faces faces,
              @Nullable Long stamp) {
            this.config = config;
            this.faces = faces;
            this.stamp = stamp;
        }
    }

    @Nullable private Entry mEntry;

    /** A fresh load, remembered for {@link #current()}. */
    @NonNull
    Entry reload() {
        TerminalFontConfig.Result config = TerminalFontConfig.load();
        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);
        mEntry = new Entry(config, faces, stamp(config));
        return mEntry;
    }

    /** The remembered load while its inputs are unchanged, otherwise a fresh one. */
    @NonNull
    Entry current() {
        Entry entry = mEntry;
        if (entry != null && entry.stamp != null && entry.stamp.equals(stamp(entry.config)))
            return entry;
        return reload();
    }

    /** Null when the inputs cannot be stamped, which never matches. */
    @Nullable
    static Long stamp(@NonNull TerminalFontConfig.Result config) {
        List<File> files = new ArrayList<>(config.inputs());
        files.add(TermuxConstants.TERMUX_FONT_FILE);
        files.add(TermuxConstants.TERMUX_ITALIC_FONT_FILE);
        addPath(files, config.faces.values());
        for (TerminalFontConfig.SymbolMapSpec map : config.symbolMaps) addPath(files, map.font);
        addPath(files, config.fallbackFonts);
        long stamp = stampFiles(files);
        if (!TerminalFontLoader.namesAFamily(config)) return stamp;
        Long tree = FontFamilyIndex.treeStamp(TerminalFontLoader.familyRoots());
        return tree == null ? null : stamp * 31 + tree;
    }

    static long stampFiles(@NonNull List<File> files) {
        long stamp = 1125899906842597L;
        for (File file : files) {
            stamp = stamp * 31 + file.getPath().hashCode();
            stamp = stamp * 31 + (file.exists() ? (file.isDirectory() ? 2 : 1) : 0);
            stamp = (stamp * 31 + file.lastModified()) * 31 + file.length();
        }
        return stamp;
    }

    private static void addPath(@NonNull List<File> files,
                                @NonNull Iterable<TerminalFontConfig.FaceSpec> specs) {
        for (TerminalFontConfig.FaceSpec spec : specs) addPath(files, spec);
    }

    private static void addPath(@NonNull List<File> files, @NonNull TerminalFontConfig.FaceSpec spec) {
        if (spec.type == TerminalFontConfig.SourceType.PATH)
            files.add(new File(TerminalFontConfig.expandPath(spec.value)));
    }
}
