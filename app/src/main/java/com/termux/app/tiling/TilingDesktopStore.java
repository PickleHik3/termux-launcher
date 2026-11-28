package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The tiling desktop's saved form on disk (#418): the stack ({@link TilingStack#toJson}, each
 * Workspace's layout state inside it) and, per shell window, the handle of the shell it showed, so
 * a launcher restart reattaches a shell that is still running and starts a new one in the saved
 * directory for one that is not. Per Linux window it keeps the Linux windows host's id for the
 * window it showed (#419), so the app, which outlives the launcher, slots back into it
 * ({@link LinuxWindowReturn}). One file of its own beside v1's arrangement autosave
 * ({@code ~/.termux/workspaces}), written whole through a temporary file.
 *
 * <p>No Android views; the caller picks the thread (the desktop writes off the main thread).
 */
public final class TilingDesktopStore {

    /** The file's name: hidden, like the arrangement autosave beside it. */
    public static final String FILE_NAME = ".tiling-desktop.json";
    /** The largest file read back. */
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int VERSION = 1;

    /** What a load brings back. */
    public static final class Saved {
        @NonNull public final TilingStack stack;
        /** Shell window id to the handle of the shell it showed. */
        @NonNull public final Map<Long, String> shells;
        /** Linux window id to the Linux windows host's id for the window it showed. */
        @NonNull public final Map<Long, Long> linux;
        /** Linux windows of a loaded saved Workspace still waiting for their apps (#431). */
        @NonNull public final LinuxRelaunch relaunch;

        Saved(@NonNull TilingStack stack, @NonNull Map<Long, String> shells, @NonNull Map<Long, Long> linux,
              @NonNull LinuxRelaunch relaunch) {
            this.stack = stack;
            this.shells = Collections.unmodifiableMap(shells);
            this.linux = Collections.unmodifiableMap(linux);
            this.relaunch = relaunch;
        }
    }

    @NonNull private final File file;

    public TilingDesktopStore(@NonNull File file) {
        this.file = file;
    }

    /** The store under the Termux home directory {@code homeDir}. */
    @NonNull
    public static TilingDesktopStore inHome(@NonNull File homeDir) {
        return new TilingDesktopStore(new File(new File(new File(homeDir, ".termux"), "workspaces"),
            FILE_NAME));
    }

    @NonNull
    public File file() {
        return file;
    }

    /** The saved form of {@code stack} and its shell windows' handles, with no Linux windows' ids. */
    @NonNull
    public static String encode(@NonNull TilingStack stack, @NonNull Map<Long, String> shells) {
        return encode(stack, shells, Collections.emptyMap());
    }

    /**
     * The saved form of {@code stack}, its shell windows' handles and its Linux windows' host ids
     * ({@code linux}, window id to the host's window id).
     */
    @NonNull
    public static String encode(@NonNull TilingStack stack, @NonNull Map<Long, String> shells,
                                @NonNull Map<Long, Long> linux) {
        return encode(stack, shells, linux, new LinuxRelaunch());
    }

    /**
     * The saved form, with the Linux windows of loaded saved Workspaces still waiting for their
     * apps ({@code relaunch}, #431): after a restart they keep waiting rather than closing as gone.
     */
    @NonNull
    public static String encode(@NonNull TilingStack stack, @NonNull Map<Long, String> shells,
                                @NonNull Map<Long, Long> linux, @NonNull LinuxRelaunch relaunch) {
        try {
            JSONObject json = new JSONObject();
            json.put("version", VERSION);
            json.put("stack", stack.toJson());
            JSONObject handles = new JSONObject();
            for (Map.Entry<Long, String> entry : shells.entrySet()) {
                if (entry.getValue() != null) handles.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            json.put("shells", handles);
            JSONObject linuxIds = new JSONObject();
            for (Map.Entry<Long, Long> entry : linux.entrySet()) {
                if (entry.getValue() != null) linuxIds.put(String.valueOf(entry.getKey()), entry.getValue().longValue());
            }
            json.put("linux", linuxIds);
            json.put("relaunch", relaunch.toJson());
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads {@link #encode}'s form back, or null for anything unreadable (the desktop then starts
     * as a first start). A handle or host id named for a window the stack does not hold, or for a
     * window of another kind, is dropped.
     */
    @Nullable
    public static Saved decode(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(text);
            JSONObject stackJson = json.optJSONObject("stack");
            if (stackJson == null) return null;
            TilingStack stack = TilingStack.fromJson(stackJson);
            Map<Long, String> shells = new HashMap<>();
            JSONObject handles = json.optJSONObject("shells");
            if (handles != null) {
                for (Iterator<String> keys = handles.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    long id;
                    try {
                        id = Long.parseLong(key);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String handle = handles.optString(key, "");
                    TilingWindow window = stack.window(id);
                    if (!handle.isEmpty() && window != null && window.kind() == WindowKind.SHELL) {
                        shells.put(id, handle);
                    }
                }
            }
            Map<Long, Long> linux = new HashMap<>();
            JSONObject linuxIds = json.optJSONObject("linux");
            if (linuxIds != null) {
                for (Iterator<String> keys = linuxIds.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    long id;
                    try {
                        id = Long.parseLong(key);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    long linuxId = linuxIds.optLong(key, LinuxWindowReturn.NO_LINUX_ID);
                    TilingWindow window = stack.window(id);
                    if (linuxId != LinuxWindowReturn.NO_LINUX_ID && window != null
                        && window.kind() == WindowKind.LINUX_APP) {
                        linux.put(id, linuxId);
                    }
                }
            }
            LinuxRelaunch relaunch = LinuxRelaunch.fromJson(json.optJSONArray("relaunch"), id -> {
                TilingWindow window = stack.window(id);
                return window != null && window.kind() == WindowKind.LINUX_APP && !linux.containsKey(id);
            });
            return new Saved(stack, shells, linux, relaunch);
        } catch (JSONException | TilingStack.SavedFormException e) {
            return null;
        }
    }

    /** The saved form in the file, or null when there is none or it cannot be read. */
    @Nullable
    public Saved load() {
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) return null;
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAX_FILE_BYTES) return null;
            }
            return decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes {@code encoded} as the whole file, through a temporary file in the same directory. */
    public void save(@NonNull String encoded) throws IOException {
        File directory = file.getParentFile();
        if (directory == null) throw new IOException("No directory for " + file);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create " + directory);
        File temporary = File.createTempFile(FILE_NAME + ".", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(encoded.getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            ownerOnly(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void ownerOnly(@NonNull File target) {
        target.setReadable(false, false);
        target.setWritable(false, false);
        target.setReadable(true, true);
        target.setWritable(true, true);
    }
}
