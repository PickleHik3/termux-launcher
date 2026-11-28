package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The saved Workspaces on disk (#431): one file each, {@code <name>.json}, in a folder of their own
 * under v1's saved sessions ({@code ~/.termux/workspaces/terl}). v1's sessions are the user's
 * files and stay where they are: v1 lists every {@code .json} in {@code workspaces} itself, so a
 * Terl file beside them would show there as a session it cannot open. Names follow v1's rule: a
 * letter or digit first, then letters, digits, spaces, {@code _ - .}, at most
 * {@link #MAX_NAME_CODE_POINTS}.
 *
 * <p>No Android views; blocking file work, so the caller keeps it off the main thread.
 */
public final class SavedWorkspaceStore {

    /** The folder's name, inside v1's {@code ~/.termux/workspaces}. */
    public static final String DIRECTORY = "terl";
    public static final int MAX_NAME_CODE_POINTS = 64;
    /** The largest file read back. */
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final String SUFFIX = ".json";

    /** How a change went. */
    public enum Result { OK, INVALID_NAME, NAME_TAKEN, NOT_FOUND, FAILED }

    /** One saved Workspace: its name and what it holds, null when its file would not read. */
    public static final class Entry {
        @NonNull public final String name;
        @Nullable public final SavedWorkspace workspace;

        Entry(@NonNull String name, @Nullable SavedWorkspace workspace) {
            this.name = name;
            this.workspace = workspace;
        }
    }

    @NonNull private final File directory;

    public SavedWorkspaceStore(@NonNull File directory) {
        this.directory = directory;
    }

    /** The store under the Termux home directory {@code homeDir}. */
    @NonNull
    public static SavedWorkspaceStore inHome(@NonNull File homeDir) {
        return new SavedWorkspaceStore(new File(new File(new File(homeDir, ".termux"), "workspaces"), DIRECTORY));
    }

    @NonNull
    public File directory() {
        return directory;
    }

    /** {@code name} trimmed, or null when it cannot be a saved Workspace's name. */
    @Nullable
    public static String cleanName(@Nullable String name) {
        if (name == null) return null;
        String value = name.trim();
        int points = value.codePointCount(0, value.length());
        if (points < 1 || points > MAX_NAME_CODE_POINTS) return null;
        int offset = 0;
        int position = 0;
        while (offset < value.length()) {
            int cp = value.codePointAt(offset);
            boolean valid = Character.isLetterOrDigit(cp)
                || (position > 0 && (cp == ' ' || cp == '_' || cp == '-' || cp == '.'));
            if (!valid) return null;
            offset += Character.charCount(cp);
            position++;
        }
        return value;
    }

    /** Every saved Workspace, by name (letters' case aside). */
    @NonNull
    public List<Entry> list() {
        File[] files = directory.listFiles((dir, filename) -> filename.endsWith(SUFFIX) && !filename.startsWith("."));
        if (files == null) return Collections.emptyList();
        List<Entry> entries = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            String filename = file.getName();
            String name = filename.substring(0, filename.length() - SUFFIX.length());
            if (!name.equals(cleanName(name))) continue;
            entries.add(new Entry(name, read(file)));
        }
        entries.sort((a, b) -> {
            int byCase = a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
            return byCase != 0 ? byCase : a.name.compareTo(b.name);
        });
        return entries;
    }

    /** Saved Workspace {@code name}, or null when there is none or it would not read. */
    @Nullable
    public SavedWorkspace load(@NonNull String name) {
        String clean = cleanName(name);
        return clean == null ? null : read(fileOf(clean));
    }

    /** Saves {@code workspace} as {@code name}; a name already saved is replaced only with {@code replace}. */
    @NonNull
    public Result save(@NonNull String name, @NonNull SavedWorkspace workspace, boolean replace) {
        String clean = cleanName(name);
        if (clean == null) return Result.INVALID_NAME;
        File file = fileOf(clean);
        if (file.exists() && !replace) return Result.NAME_TAKEN;
        return write(file, workspace.encode());
    }

    /** Renames saved Workspace {@code from} to {@code to}; a name already saved is refused. */
    @NonNull
    public Result rename(@NonNull String from, @NonNull String to) {
        String source = cleanName(from);
        String target = cleanName(to);
        if (source == null || target == null) return Result.INVALID_NAME;
        File sourceFile = fileOf(source);
        if (!sourceFile.isFile()) return Result.NOT_FOUND;
        if (source.equals(target)) return Result.OK;
        File targetFile = fileOf(target);
        // A name differing only in case is the same file where the file system ignores case.
        boolean sameFile = source.equalsIgnoreCase(target) && sameFile(sourceFile, targetFile);
        if (targetFile.exists() && !sameFile) return Result.NAME_TAKEN;
        try {
            Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return Result.OK;
        } catch (IOException | RuntimeException e) {
            return Result.FAILED;
        }
    }

    @NonNull
    public Result delete(@NonNull String name) {
        String clean = cleanName(name);
        if (clean == null) return Result.INVALID_NAME;
        File file = fileOf(clean);
        if (!file.isFile()) return Result.NOT_FOUND;
        return file.delete() ? Result.OK : Result.FAILED;
    }

    /** Marks saved Workspace {@code name} to open, or not, when the launcher starts. */
    @NonNull
    public Result setOpenAtStart(@NonNull String name, boolean on) {
        String clean = cleanName(name);
        if (clean == null) return Result.INVALID_NAME;
        File file = fileOf(clean);
        if (!file.isFile()) return Result.NOT_FOUND;
        SavedWorkspace workspace = read(file);
        if (workspace == null) return Result.FAILED;
        if (workspace.openAtStart() == on) return Result.OK;
        return write(file, workspace.withOpenAtStart(on).encode());
    }

    @NonNull
    private File fileOf(@NonNull String cleanName) {
        return new File(directory, cleanName + SUFFIX);
    }

    @Nullable
    private static SavedWorkspace read(@NonNull File file) {
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) return null;
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAX_FILE_BYTES) return null;
            }
            return SavedWorkspace.decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes {@code text} as the whole of {@code file}, through a temporary file beside it. */
    @NonNull
    private Result write(@NonNull File file, @NonNull String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) return Result.FAILED;
        if (!directory.isDirectory() && !directory.mkdirs()) return Result.FAILED;
        File temporary = null;
        try {
            temporary = File.createTempFile("." + file.getName() + ".", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            temporary.setReadable(false, false);
            temporary.setWritable(false, false);
            temporary.setReadable(true, true);
            temporary.setWritable(true, true);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return Result.OK;
        } catch (IOException | RuntimeException e) {
            return Result.FAILED;
        } finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    private static boolean sameFile(@NonNull File a, @NonNull File b) {
        try {
            return b.exists() && Files.isSameFile(a.toPath(), b.toPath());
        } catch (IOException e) {
            return false;
        }
    }
}
