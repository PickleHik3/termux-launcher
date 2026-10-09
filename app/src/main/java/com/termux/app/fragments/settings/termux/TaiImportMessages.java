package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiModelImporter;

import org.json.JSONObject;

import java.util.Locale;

/**
 * The plain words for an import failure. The importer, downloader and manager keep their
 * machine-stable codes and developer messages for the CLI and API; this maps a result to a
 * string resource the settings screen shows, and keeps the technical message as the detail behind
 * "Show details" for bug reports.
 */
final class TaiImportMessages {
    static final class Message {
        final int resId;
        @NonNull final Object[] args;
        /** The technical message, when it says more than the plain one; {@code null} otherwise. */
        @Nullable final String detail;

        Message(int resId, @Nullable String detail, @NonNull Object... args) {
            this.resId = resId;
            this.args = args;
            this.detail = detail == null || detail.trim().isEmpty() ? null : detail.trim();
        }
    }

    private TaiImportMessages() {
    }

    /** The message for a failed importer, downloader or manager result; a null result is a crash. */
    @NonNull
    static Message forResult(@Nullable JSONObject result, @Nullable String fileName) {
        if (result == null) return new Message(R.string.termux_ai_import_err_generic, null);
        String code = result.optString("error", "");
        String technical = result.optString("message", "");
        String reason = result.optString("reason", "");
        switch (code) {
            case TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_raw_weights, technical);
            case TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_native_library, technical);
            case TaiModelImporter.ERROR_UNSUPPORTED_MODEL_FILE:
                return forExtension(fileName, technical);
            case TaiModelImporter.ERROR_INSECURE_URL:
                return new Message(R.string.termux_ai_import_link_invalid, technical);
            case "insufficient_storage": {
                long needed = result.optLong("neededBytes", 0L);
                long free = result.optLong("freeBytes", 0L);
                return needed > 0L
                    ? new Message(R.string.termux_ai_import_err_storage, technical, formatBytes(needed), formatBytes(free))
                    : new Message(R.string.termux_ai_import_err_storage_unknown, technical);
            }
            case "model_exists":
                return new Message(R.string.termux_ai_import_err_exists, technical);
            case "hf_resolve_failed":
                return new Message(R.string.termux_ai_import_err_no_file_in_repo, technical);
            case "runtime_update_required":
                return new Message(R.string.termux_ai_import_err_runtime_update, technical);
            case "missing_config":
                return new Message(R.string.termux_ai_import_err_mnn_folder, technical);
            case "model_import_failed":
                if (TaiModelImporter.REASON_UNREADABLE.equals(reason)) {
                    return new Message(R.string.termux_ai_import_err_unreadable, technical);
                }
                if (TaiModelImporter.REASON_CANCELLED.equals(reason)) {
                    return new Message(R.string.termux_ai_import_cancelled, null);
                }
                return new Message(R.string.termux_ai_import_err_generic, technical);
            default:
                if (looksLikeNetwork(technical)) return new Message(R.string.termux_ai_import_err_offline, technical);
                return new Message(R.string.termux_ai_import_err_generic, technical);
        }
    }

    /** The message for a file the importer's name check refused. */
    @NonNull
    static Message forValidation(@NonNull TaiModelImporter.ValidationResult validation, @Nullable String fileName) {
        switch (validation.errorCode) {
            case TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_raw_weights, validation.message);
            case TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_native_library, validation.message);
            default:
                return forExtension(fileName, validation.message);
        }
    }

    /** The message for a link the flow could not make sense of: always the one example. */
    @NonNull
    static Message forLink(@NonNull TaiModelImporter.ValidationResult validation) {
        switch (validation.errorCode) {
            case TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_raw_weights, validation.message);
            case TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN:
                return new Message(R.string.termux_ai_import_err_native_library, validation.message);
            default:
                return new Message(R.string.termux_ai_import_link_invalid, validation.message);
        }
    }

    /** The message for a download the store recorded as failed; its error is the transfer's own text. */
    @NonNull
    static Message forDownloadError(@Nullable String error) {
        String value = error == null ? "" : error.trim();
        if (value.isEmpty()) return new Message(R.string.termux_ai_import_err_generic, null);
        if (looksLikeNetwork(value)) return new Message(R.string.termux_ai_import_err_offline, value);
        if (value.toLowerCase(Locale.ROOT).contains("space") || value.toLowerCase(Locale.ROOT).contains("storage")) {
            return new Message(R.string.termux_ai_import_err_storage_unknown, value);
        }
        if (value.toLowerCase(Locale.ROOT).contains("sha") || value.toLowerCase(Locale.ROOT).contains("checksum")) {
            return new Message(R.string.termux_ai_import_err_unreadable, value);
        }
        return new Message(R.string.termux_ai_import_err_generic, value);
    }

    @NonNull
    private static Message forExtension(@Nullable String fileName, @Nullable String technical) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1 && !name.substring(dot + 1).contains("/")) {
            return new Message(R.string.termux_ai_import_err_extension, technical, name.substring(dot + 1));
        }
        return new Message(R.string.termux_ai_import_err_no_extension, technical);
    }

    private static boolean looksLikeNetwork(@NonNull String technical) {
        String lower = technical.toLowerCase(Locale.ROOT);
        return lower.contains("unable to resolve host") || lower.contains("timed out") || lower.contains("timeout")
            || lower.contains("connection") || lower.contains("network") || lower.contains("unreachable")
            || lower.contains("http 5");
    }

    @NonNull
    static String formatBytes(long bytes) {
        if (bytes <= 0) return "?";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
