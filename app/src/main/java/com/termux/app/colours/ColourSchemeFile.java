package com.termux.app.colours;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code ~/.termux/colors.properties}, the file the terminal reads its colours from while Wallpaper
 * colours is off, as the app writes it for a built-in scheme. A file the app wrote starts with two
 * comment lines: the scheme's id, and what stood there before the app first wrote it (a backup's
 * name, or {@link #NOTHING}). A file the app did not write (Termux:Styling's, the user's own) is
 * moved to a backup beside it before the first overwrite, never written over, so
 * {@link #restoreOwn} can put it back.
 */
public final class ColourSchemeFile {

    static final String MARK = "# Written by the launcher for the colour scheme: ";
    static final String BEFORE = "# Before it: ";
    /** {@link #BEFORE}'s value when there was no file before the app first wrote one. */
    static final String NOTHING = "nothing";

    @NonNull private final File mFile;

    public ColourSchemeFile(@NonNull File file) {
        mFile = file;
    }

    /** The built-in scheme's id the file holds now, or null when the app did not write it (or there is none). */
    @Nullable
    public String schemeId() {
        String[] head = head();
        return head == null ? null : head[0];
    }

    /** Whether there is a file of the user's own, standing there now or kept in a backup the app made. */
    public boolean hasOwn() {
        String[] head = head();
        if (head == null) return mFile.isFile();
        return !NOTHING.equals(head[1]) && new File(mFile.getParentFile(), head[1]).isFile();
    }

    /**
     * Writes {@code scheme} over the file. A file the app did not write is moved to a backup first,
     * once; a file the app wrote carries on naming what stood there before it. The new file is
     * written in full before the user's is moved, and a failure after the move puts it back, so a
     * full disk never leaves the user's file stranded in its backup.
     */
    public void write(@NonNull TerminalSchemes.Scheme scheme) throws IOException {
        File dir = mFile.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("no " + dir);
        String[] head = head();
        File backup = head == null && mFile.isFile() ? freeBackup() : null;
        String before = head != null ? head[1] : backup != null ? backup.getName() : NOTHING;
        StringBuilder text = new StringBuilder();
        text.append(MARK).append(scheme.id).append('\n');
        text.append(BEFORE).append(before).append('\n');
        text.append("# ").append(scheme.name).append(" (").append(scheme.licence).append(", ")
            .append(scheme.home).append(")\n");
        for (Map.Entry<String, String> colour : scheme.colours.entrySet()) {
            text.append(colour.getKey()).append('=').append(colour.getValue()).append('\n');
        }
        File temp = new File(dir, mFile.getName() + ".tmp");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            out.write(text.toString());
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw e;
        }
        if (backup != null && !mFile.renameTo(backup)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("could not keep " + mFile);
        }
        if (!temp.renameTo(mFile)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            // The user's file goes back where it stood.
            if (backup != null && !mFile.exists() && !backup.renameTo(mFile))
                throw new IOException("could not write " + mFile + "; it is kept as " + backup);
            throw new IOException("could not write " + mFile);
        }
    }

    /**
     * Puts back what stood there before the app first wrote the file: the user's file from its
     * backup, or no file. A file the app did not write is left as it is.
     */
    public void restoreOwn() throws IOException {
        String[] head = head();
        if (head == null) return;
        if (NOTHING.equals(head[1])) {
            if (!mFile.delete() && mFile.exists()) throw new IOException("could not remove " + mFile);
            return;
        }
        File backup = new File(mFile.getParentFile(), head[1]);
        if (!backup.isFile()) return;
        if (!mFile.delete() && mFile.exists()) throw new IOException("could not remove " + mFile);
        if (!backup.renameTo(mFile)) throw new IOException("could not restore " + backup);
    }

    /** The first backup name not taken: colors.properties.bak, then .bak2, .bak3… */
    @NonNull
    private File freeBackup() {
        File dir = mFile.getParentFile();
        File backup = new File(dir, mFile.getName() + ".bak");
        for (int n = 2; backup.exists(); n++) backup = new File(dir, mFile.getName() + ".bak" + n);
        return backup;
    }

    /** The app's two lines {scheme id, before}, or null for a file the app did not write. */
    @Nullable
    private String[] head() {
        if (!mFile.isFile()) return null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(mFile), StandardCharsets.UTF_8))) {
            String first = in.readLine();
            String second = in.readLine();
            if (first == null || second == null || !first.startsWith(MARK)
                || !second.startsWith(BEFORE)) return null;
            String id = first.substring(MARK.length()).trim();
            String before = second.substring(BEFORE.length()).trim();
            // A backup name never leaves the file's folder.
            if (id.isEmpty() || before.isEmpty() || before.contains("/")) return null;
            return new String[] {id, before};
        } catch (IOException e) {
            return null;
        }
    }
}
