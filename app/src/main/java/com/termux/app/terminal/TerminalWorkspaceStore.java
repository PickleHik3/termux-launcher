package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Owns validated, atomic workspace files under {@code ~/.termux/workspaces}. */
public final class TerminalWorkspaceStore {

    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_NAME_CODE_POINTS = 64;
    private static final String SUFFIX = ".json";

    @NonNull private final File homeDir;

    public TerminalWorkspaceStore() {
        this(TermuxConstants.TERMUX_HOME_DIR);
    }

    public TerminalWorkspaceStore(@NonNull File homeDir) {
        this.homeDir = homeDir;
    }

    public static final class Entry {
        @NonNull public final String name;
        public final long modifiedAtEpochMs;
        public final long sizeBytes;

        private Entry(@NonNull String name, long modifiedAtEpochMs, long sizeBytes) {
            this.name = name;
            this.modifiedAtEpochMs = modifiedAtEpochMs;
            this.sizeBytes = sizeBytes;
        }
    }

    public void save(@NonNull String name, @NonNull TerminalWorkspace workspace, boolean overwrite)
        throws TerminalWorkspace.WorkspaceException {
        String cleanName = validateName(name);
        if (!cleanName.equals(workspace.name)) {
            throw error("invalid_workspace", "Workspace document name does not match file name");
        }
        workspace.validate();
        byte[] payload;
        try {
            payload = (workspace.toJson().toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw error("invalid_workspace", "Could not encode workspace: " + e.getMessage(), e);
        }
        if (payload.length > MAX_FILE_BYTES)
            throw error("workspace_too_large", "Workspace exceeds " + MAX_FILE_BYTES + " bytes");

        File directory = ensureDirectory();
        File target = resolveFile(directory, cleanName);
        if (target.exists() && !overwrite)
            throw error("conflict", "Workspace '" + cleanName + "' already exists");

        File temporary = null;
        try {
            temporary = File.createTempFile("." + cleanName + ".", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(payload);
                output.flush();
                output.getFD().sync();
            }
            ownerOnlyFile(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Same-directory replacement still avoids partial file contents on filesystems that
                // do not advertise atomic moves (notably some host-test temporary filesystems).
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            ownerOnlyFile(target);
        } catch (IOException e) {
            throw error("io_error", "Could not save workspace '" + cleanName + "': " + e.getMessage(), e);
        } finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    @NonNull
    public TerminalWorkspace load(@NonNull String name) throws TerminalWorkspace.WorkspaceException {
        String cleanName = validateName(name);
        File directory = workspaceDirectory(false);
        if (directory.exists()) validateDirectory(directory);
        File target = resolveFile(directory, cleanName);
        if (!target.isFile()) throw error("not_found", "Workspace '" + cleanName + "' does not exist");
        long length = target.length();
        if (length < 1 || length > MAX_FILE_BYTES)
            throw error("workspace_too_large", "Workspace must be between 1 and " + MAX_FILE_BYTES + " bytes");
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(target);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) length)) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES)
                    throw error("workspace_too_large", "Workspace exceeds " + MAX_FILE_BYTES + " bytes");
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        } catch (TerminalWorkspace.WorkspaceException e) {
            throw e;
        } catch (IOException e) {
            throw error("io_error", "Could not read workspace '" + cleanName + "': " + e.getMessage(), e);
        }
        try {
            TerminalWorkspace workspace = TerminalWorkspace.fromJson(
                new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
            if (!cleanName.equals(workspace.name))
                throw error("invalid_workspace", "Workspace document name does not match file name");
            return workspace;
        } catch (JSONException e) {
            throw error("invalid_workspace", "Workspace is not valid JSON: " + e.getMessage(), e);
        }
    }

    @NonNull
    public List<Entry> list() throws TerminalWorkspace.WorkspaceException {
        File directory = workspaceDirectory(false);
        if (!directory.exists()) return Collections.emptyList();
        validateDirectory(directory);
        File[] files = directory.listFiles((dir, filename) -> filename.endsWith(SUFFIX)
            && !filename.startsWith("."));
        if (files == null) throw error("io_error", "Could not list workspace directory");
        List<Entry> entries = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            String filename = file.getName();
            String name = filename.substring(0, filename.length() - SUFFIX.length());
            try {
                validateName(name);
                entries.add(new Entry(name, file.lastModified(), file.length()));
            } catch (TerminalWorkspace.WorkspaceException ignored) {
                // Ignore unrelated or invalid JSON filenames in this user-owned directory.
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.name));
        return entries;
    }

    public void delete(@NonNull String name) throws TerminalWorkspace.WorkspaceException {
        String cleanName = validateName(name);
        File directory = workspaceDirectory(false);
        if (directory.exists()) validateDirectory(directory);
        File target = resolveFile(directory, cleanName);
        if (!target.exists()) throw error("not_found", "Workspace '" + cleanName + "' does not exist");
        if (!target.isFile() || !target.delete())
            throw error("io_error", "Could not delete workspace '" + cleanName + "'");
    }

    @NonNull
    public File fileForTesting(@NonNull String name) throws TerminalWorkspace.WorkspaceException {
        return resolveFile(workspaceDirectory(false), validateName(name));
    }

    @NonNull
    public static String validateName(@NonNull String name) throws TerminalWorkspace.WorkspaceException {
        String value = name.trim();
        int points = value.codePointCount(0, value.length());
        if (points < 1 || points > MAX_NAME_CODE_POINTS)
            throw error("invalid_name", "Workspace name must contain 1 to " + MAX_NAME_CODE_POINTS + " characters");
        if (value.endsWith(SUFFIX))
            throw error("invalid_name", "Workspace name must not include the .json suffix");
        int offset = 0;
        int position = 0;
        while (offset < value.length()) {
            int cp = value.codePointAt(offset);
            boolean valid = Character.isLetterOrDigit(cp)
                || (position > 0 && (cp == ' ' || cp == '_' || cp == '-' || cp == '.'));
            if (!valid)
                throw error("invalid_name", "Workspace names may contain letters, digits, spaces, '_', '-', and '.'");
            offset += Character.charCount(cp);
            position++;
        }
        if (".".equals(value) || "..".equals(value))
            throw error("invalid_name", "Invalid workspace name");
        return value;
    }

    private File ensureDirectory() throws TerminalWorkspace.WorkspaceException {
        File directory = workspaceDirectory(false);
        if (!directory.exists() && !directory.mkdirs())
            throw error("io_error", "Could not create " + directory);
        validateDirectory(directory);
        ownerOnlyDirectory(directory);
        return directory;
    }

    private File workspaceDirectory(boolean requireExisting) throws TerminalWorkspace.WorkspaceException {
        File directory = new File(new File(homeDir, ".termux"), "workspaces");
        if (requireExisting && !directory.isDirectory())
            throw error("not_found", "Workspace directory does not exist");
        return directory;
    }

    private void validateDirectory(File directory) throws TerminalWorkspace.WorkspaceException {
        try {
            File home = homeDir.getCanonicalFile();
            File canonical = directory.getCanonicalFile();
            String prefix = home.getPath() + File.separator;
            if (!canonical.getPath().startsWith(prefix))
                throw error("unsafe_path", "Workspace directory resolves outside the Termux home directory");
            if (!canonical.isDirectory()) throw error("io_error", "Workspace path is not a directory");
        } catch (IOException e) {
            throw error("io_error", "Could not resolve workspace directory: " + e.getMessage(), e);
        }
    }

    private File resolveFile(File directory, String cleanName) throws TerminalWorkspace.WorkspaceException {
        try {
            File target = new File(directory, cleanName + SUFFIX);
            File canonicalDirectory = directory.getCanonicalFile();
            File canonicalTarget = target.getCanonicalFile();
            if (!canonicalDirectory.equals(canonicalTarget.getParentFile()))
                throw error("unsafe_path", "Workspace path escapes its directory");
            return target;
        } catch (IOException e) {
            throw error("io_error", "Could not resolve workspace path: " + e.getMessage(), e);
        }
    }

    private static void ownerOnlyDirectory(File directory) {
        directory.setReadable(false, false);
        directory.setWritable(false, false);
        directory.setExecutable(false, false);
        directory.setReadable(true, true);
        directory.setWritable(true, true);
        directory.setExecutable(true, true);
    }

    private static void ownerOnlyFile(File file) {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private static TerminalWorkspace.WorkspaceException error(String code, String message) {
        return new TerminalWorkspace.WorkspaceException(code, message);
    }

    private static TerminalWorkspace.WorkspaceException error(String code, String message, Throwable cause) {
        return new TerminalWorkspace.WorkspaceException(code, message, cause);
    }
}
