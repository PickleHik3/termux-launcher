package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A small {@code multipart/form-data} reader for {@code POST /v1/audio/transcriptions}, the one
 * route that takes a file: the boundary from the Content-Type, then each part's headers
 * ({@code Content-Disposition: form-data; name="…"; filename="…"}, an optional Content-Type) and
 * its bytes up to the next delimiter, CRLF-exact so binary audio comes through untouched. Nested
 * multipart and transfer encodings are not handled; OpenAI clients and curl send neither.
 */
final class MultipartFormData {

    /** One form field: its bytes, and a filename and content type when it was a file upload. */
    static final class Part {
        @NonNull final String name;
        @Nullable final String filename;
        @Nullable final String contentType;
        @NonNull final byte[] data;

        Part(@NonNull String name, @Nullable String filename, @Nullable String contentType, @NonNull byte[] data) {
            this.name = name;
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }

        /** The field as UTF-8 text, for the plain (non-file) fields. */
        @NonNull
        String text() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private MultipartFormData() {
    }

    static boolean isMultipart(@Nullable String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    /** The boundary parameter of a multipart Content-Type, unquoted, or {@code null} when absent. */
    @Nullable
    static String boundary(@Nullable String contentType) {
        if (contentType == null) return null;
        for (String parameter : contentType.split(";")) {
            String trimmed = parameter.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) continue;
            String value = trimmed.substring("boundary=".length()).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * The parts of {@code body}, keyed by field name (a repeated name keeps the first). Throws
     * {@link IllegalArgumentException} when the Content-Type carries no boundary or the body is
     * not delimited by it.
     */
    @NonNull
    static Map<String, Part> parse(@NonNull byte[] body, @Nullable String contentType) {
        String boundary = boundary(contentType);
        if (boundary == null) throw new IllegalArgumentException("multipart/form-data without a boundary");
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        LinkedHashMap<String, Part> parts = new LinkedHashMap<>();
        int position = indexOf(body, delimiter, 0);
        if (position < 0) throw new IllegalArgumentException("multipart body does not start with its boundary");
        while (true) {
            position += delimiter.length;
            // "--" right after a delimiter closes the body; otherwise a CRLF and the part's headers follow.
            if (position + 1 < body.length && body[position] == '-' && body[position + 1] == '-') break;
            position = skipLineBreak(body, position);
            int headersEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), position);
            if (headersEnd < 0) throw new IllegalArgumentException("multipart part without a header block");
            String headers = new String(body, position, headersEnd - position, StandardCharsets.UTF_8);
            int dataStart = headersEnd + 4;
            int next = indexOf(body, delimiter, dataStart);
            if (next < 0) throw new IllegalArgumentException("multipart part is not closed by the boundary");
            // The CRLF before the delimiter belongs to the delimiter, not to the data.
            int dataEnd = next;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') dataEnd -= 2;
            byte[] data = new byte[Math.max(0, dataEnd - dataStart)];
            System.arraycopy(body, dataStart, data, 0, data.length);
            String name = headerParameter(headers, "content-disposition", "name");
            if (name != null && !parts.containsKey(name)) {
                parts.put(name, new Part(name, headerParameter(headers, "content-disposition", "filename"),
                    headerValue(headers, "content-type"), data));
            }
            position = next;
        }
        return parts;
    }

    private static int skipLineBreak(@NonNull byte[] body, int position) {
        if (position + 1 < body.length && body[position] == '\r' && body[position + 1] == '\n') return position + 2;
        if (position < body.length && body[position] == '\n') return position + 1;
        return position;
    }

    @Nullable
    private static String headerValue(@NonNull String headers, @NonNull String header) {
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            if (line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals(header)) return line.substring(colon + 1).trim();
        }
        return null;
    }

    /** {@code parameter="value"} (or unquoted) from a header such as Content-Disposition. */
    @Nullable
    private static String headerParameter(@NonNull String headers, @NonNull String header, @NonNull String parameter) {
        String value = headerValue(headers, header);
        if (value == null) return null;
        for (String piece : value.split(";")) {
            String trimmed = piece.trim();
            int equals = trimmed.indexOf('=');
            if (equals <= 0) continue;
            if (!trimmed.substring(0, equals).trim().toLowerCase(Locale.ROOT).equals(parameter)) continue;
            String parameterValue = trimmed.substring(equals + 1).trim();
            if (parameterValue.length() >= 2 && parameterValue.startsWith("\"") && parameterValue.endsWith("\"")) {
                parameterValue = parameterValue.substring(1, parameterValue.length() - 1);
            }
            return parameterValue;
        }
        return null;
    }

    static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle, int from) {
        if (needle.length == 0) return from;
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
