package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A deliberately bounded Tier-1 implementation of kitty's APC graphics protocol. */
final class KittyGraphicsProtocol {

    static final int MAX_TRANSMITTED_BYTES = 16 * 1024 * 1024;
    static final int MAX_DECODED_BYTES = 32 * 1024 * 1024;
    private static final int MAX_CONTROL_BYTES = 2048;
    private static final int MAX_CONTROLS = 64;

    private static final ExecutorService PNG_DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TermuxKittyPngDecoder");
        thread.setDaemon(true);
        return thread;
    });

    private final TerminalEmulator emulator;
    private final TerminalOutput output;
    private Upload upload;
    private long generation;
    private long decodeBytesInFlight;

    KittyGraphicsProtocol(TerminalEmulator emulator, TerminalOutput output) {
        this.emulator = emulator;
        this.output = output;
    }

    void accept(String apc) {
        if (apc == null || apc.length() < 2 || apc.charAt(0) != 'G') return;
        // The payload, and therefore the ';' separator, is optional: a control-only command is well
        // formed. Requiring the separator made the canonical delete form (ESC _ G a=d ESC \) and the
        // control-only header that real clients send before chunked data fail with EINVAL.
        int separator = apc.indexOf(';', 1);
        String controlData = separator < 0 ? apc.substring(1) : apc.substring(1, separator);
        String payload = separator < 0 ? "" : apc.substring(separator + 1);
        Command command;
        try {
            command = Command.parse(controlData);
        } catch (IllegalArgumentException e) {
            reply(null, "EINVAL:" + printable(e.getMessage()), true, true);
            return;
        }

        if (command.action == 'd') {
            resetUploadAndDecodes();
            emulator.deleteKittyGraphics(command.imageId, Character.isUpperCase(command.deleteMode));
            reply(command, "OK", false, false);
            return;
        }
        if (command.action == 'q') {
            handleQuery(command, payload);
            return;
        }
        if (upload != null) {
            if (!command.isContinuation()) {
                Upload abandoned = upload;
                upload = null;
                reply(abandoned.command, "EINVAL:chunk upload interrupted", true, false);
            } else {
                appendChunk(upload, command, payload);
                return;
            }
        }
        if (command.action != 'T' && command.action != 't') {
            reply(command, "ENOSYS:action is outside Tier 1", true, false);
            return;
        }
        if (command.action != 'T') {
            reply(command, "ENOSYS:stored images require Tier 2", true, false);
            return;
        }
        if (command.medium != 'd') {
            reply(command, "ENOSYS:only direct transmission is supported", true, false);
            return;
        }
        if (command.format != 100) {
            reply(command, "ENOSYS:Tier 1 display accepts PNG only", true, false);
            return;
        }
        Upload next = new Upload(command);
        appendChunk(next, command, payload);
    }

    private void appendChunk(Upload target, Command chunkCommand, String payload) {
        byte[] decoded;
        try {
            decoded = decodeBase64(payload);
        } catch (IllegalArgumentException e) {
            if (upload == target) upload = null;
            reply(target.command, "EINVAL:invalid base64 payload", true, false);
            return;
        }
        if (decoded.length > MAX_TRANSMITTED_BYTES - target.data.size()) {
            if (upload == target) upload = null;
            reply(target.command, "ENOSPC:image exceeds transmission limit", true, false);
            return;
        }
        target.data.write(decoded, 0, decoded.length);
        if (chunkCommand.more) {
            upload = target;
            return;
        }
        upload = null;
        submitPng(target.command, target.data.toByteArray());
    }

    private void handleQuery(Command command, String payload) {
        if (command.medium != 'd') {
            reply(command, "ENOSYS:unsupported transmission medium", true, true);
            return;
        }
        byte[] decoded;
        try {
            decoded = decodeBase64(payload);
        } catch (IllegalArgumentException e) {
            reply(command, "EINVAL:invalid base64 payload", true, true);
            return;
        }
        boolean valid;
        if (command.format == 24 || command.format == 32) {
            long required = (long) command.width * command.height * (command.format / 8);
            valid = command.width > 0 && command.height > 0 && required == decoded.length;
        } else if (command.format == 100) {
            valid = pngDimensions(decoded) != null;
        } else {
            reply(command, "ENOSYS:unsupported image format", true, true);
            return;
        }
        reply(command, valid ? "OK" : "EINVAL:invalid image data", !valid, true);
    }

    private void submitPng(Command command, byte[] png) {
        int[] dimensions = pngDimensions(png);
        if (dimensions == null) {
            reply(command, "EINVAL:invalid PNG", true, false);
            return;
        }
        long pixelBytes = (long) dimensions[0] * dimensions[1] * 4L;
        if (pixelBytes <= 0 || pixelBytes > MAX_DECODED_BYTES) {
            reply(command, "ENOSPC:decoded image exceeds session limit", true, false);
            return;
        }
        if (decodeBytesInFlight + png.length > MAX_TRANSMITTED_BYTES) {
            reply(command, "ENOSPC:too much image data pending", true, false);
            return;
        }
        final long acceptedGeneration = generation;
        final int row = emulator.getCursorRow();
        final int col = emulator.getCursorCol();
        final int cellWidth = Math.max(1, emulator.getCellWidthPixels());
        final int cellHeight = Math.max(1, emulator.getCellHeightPixels());
        final int[] displaySize = displaySize(command, dimensions[0], dimensions[1], cellWidth, cellHeight);
        if ((long) displaySize[0] * displaySize[1] * 4L > MAX_DECODED_BYTES) {
            reply(command, "ENOSPC:display image exceeds session limit", true, false);
            return;
        }
        decodeBytesInFlight += png.length;
        emulator.advanceKittyGraphicsCursor(command, displaySize[0], displaySize[1], row, col,
            cellWidth, cellHeight);

        PNG_DECODER.execute(() -> {
            Bitmap bitmap = null;
            String error = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeByteArray(png, 0, png.length, options);
                if (bitmap == null) {
                    error = "EINVAL:PNG decode failed";
                } else if (bitmap.getAllocationByteCount() > MAX_DECODED_BYTES) {
                    bitmap.recycle();
                    bitmap = null;
                    error = "ENOSPC:decoded image exceeds session limit";
                } else if (bitmap.getWidth() != displaySize[0] || bitmap.getHeight() != displaySize[1]) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, displaySize[0], displaySize[1], true);
                    if (scaled != bitmap) bitmap.recycle();
                    bitmap = scaled;
                }
            } catch (RuntimeException | OutOfMemoryError e) {
                error = "ENOMEM:PNG decode failed";
                if (bitmap != null) bitmap.recycle();
                bitmap = null;
            }
            final Bitmap result = bitmap;
            final String decodeError = error;
            output.postTerminalUpdate(() -> {
                decodeBytesInFlight = Math.max(0, decodeBytesInFlight - png.length);
                if (acceptedGeneration != generation) {
                    if (result != null) result.recycle();
                    return;
                }
                if (decodeError != null) {
                    reply(command, decodeError, true, false);
                    return;
                }
                if (!emulator.placeKittyGraphics(result, command, row, col, cellWidth, cellHeight)) {
                    result.recycle();
                    reply(command, "EINVAL:image placement failed", true, false);
                    return;
                }
                reply(command, "OK", false, false);
            });
        });
    }

    void reset() {
        resetUploadAndDecodes();
        emulator.deleteAllKittyGraphics();
    }

    void screenCleared() {
        resetUploadAndDecodes();
        emulator.deleteVisibleKittyGraphics();
    }

    private void resetUploadAndDecodes() {
        upload = null;
        generation++;
    }

    private void reply(Command command, String status, boolean error, boolean always) {
        int quiet = command == null ? 0 : command.quiet;
        // q=1 suppresses success, q=2 suppresses everything. The old check compared q to 1 and 2
        // exactly, so q=2 still emitted OK — which lands in the tty and, with no application reading
        // it, corrupts the shell's input line.
        if (!error && quiet >= 1) return;
        if (error && quiet >= 2) return;
        if (!always && command != null && command.imageId == 0) return;
        StringBuilder response = new StringBuilder("\033_G");
        if (command != null && command.imageId != 0) {
            response.append("i=").append(Long.toUnsignedString(command.imageId));
            if (command.placementId != 0)
                response.append(",p=").append(Long.toUnsignedString(command.placementId));
        }
        response.append(';').append(status).append("\033\\");
        output.write(response.toString());
    }

    private static int[] pngDimensions(byte[] png) {
        if (png.length < 24 || (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N'
            || png[3] != 'G' || png[4] != 13 || png[5] != 10 || png[6] != 26 || png[7] != 10
            || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return null;
        int width = readPositiveInt(png, 16);
        int height = readPositiveInt(png, 20);
        if (width <= 0 || height <= 0) return null;
        return new int[] { width, height };
    }

    /** Small strict decoder kept here so protocol tests do not depend on Android framework stubs. */
    private static byte[] decodeBase64(String encoded) {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(encoded.length() * 3 / 4);
        int accumulator = 0;
        int bits = 0;
        int useful = 0;
        boolean padding = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
            if (c == '=') {
                padding = true;
                continue;
            }
            if (padding) throw new IllegalArgumentException("data after base64 padding");
            int value = base64Value(c);
            if (value < 0) throw new IllegalArgumentException("invalid base64 character");
            accumulator = (accumulator << 6) | value;
            bits += 6;
            useful++;
            if (bits >= 8) {
                bits -= 8;
                decoded.write((accumulator >> bits) & 0xff);
            }
        }
        if ((useful & 3) == 1 || (bits > 0 && (accumulator & ((1 << bits) - 1)) != 0))
            throw new IllegalArgumentException("invalid base64 length");
        return decoded.toByteArray();
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }

    private static int[] displaySize(Command command, int sourceWidth, int sourceHeight,
                                     int cellWidth, int cellHeight) {
        long requestedWidth = command.displayColumns > 0 ? (long) command.displayColumns * cellWidth : 0;
        long requestedHeight = command.displayRows > 0 ? (long) command.displayRows * cellHeight : 0;
        if (requestedWidth > Integer.MAX_VALUE || requestedHeight > Integer.MAX_VALUE)
            return new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE };
        int width = (int) requestedWidth;
        int height = (int) requestedHeight;
        if (width <= 0 && height <= 0) return new int[] { sourceWidth, sourceHeight };
        if (width <= 0) width = Math.max(1, (int) Math.round((double) sourceWidth * height / sourceHeight));
        if (height <= 0) height = Math.max(1, (int) Math.round((double) sourceHeight * width / sourceWidth));
        return new int[] { width, height };
    }

    private static int readPositiveInt(byte[] value, int offset) {
        long result = ((long) (value[offset] & 0xff) << 24) | ((long) (value[offset + 1] & 0xff) << 16)
            | ((long) (value[offset + 2] & 0xff) << 8) | (value[offset + 3] & 0xffL);
        return result > Integer.MAX_VALUE ? -1 : (int) result;
    }

    private static String printable(String message) {
        if (message == null || message.isEmpty()) return "invalid control data";
        return message.replaceAll("[^ -~]", "?");
    }

    private static final class Upload {
        final Command command;
        final ByteArrayOutputStream data = new ByteArrayOutputStream(8192);

        Upload(Command command) {
            this.command = command;
        }
    }

    static final class Command {
        final Map<Character, String> values;
        final char action;
        final char medium;
        final char deleteMode;
        final int format;
        final int width;
        final int height;
        final int quiet;
        final int displayColumns;
        final int displayRows;
        final boolean more;
        final boolean noCursorMovement;
        final long imageId;
        final long placementId;

        private Command(Map<Character, String> values) {
            this.values = values;
            action = character(values, 'a', 't');
            medium = character(values, 't', 'd');
            deleteMode = character(values, 'd', 'a');
            format = integer(values, 'f', 32);
            width = integer(values, 's', 0);
            height = integer(values, 'v', 0);
            quiet = integer(values, 'q', 0);
            displayColumns = integer(values, 'c', 0);
            displayRows = integer(values, 'r', 0);
            more = integer(values, 'm', 0) == 1;
            noCursorMovement = integer(values, 'C', 0) == 1;
            imageId = unsigned(values, 'i');
            placementId = unsigned(values, 'p');
            if (quiet < 0 || quiet > 2) throw new IllegalArgumentException("invalid q value");
        }

        static Command parse(String control) {
            if (control.length() > MAX_CONTROL_BYTES) throw new IllegalArgumentException("control data too long");
            Map<Character, String> values = new HashMap<>();
            if (!control.isEmpty()) {
                String[] entries = control.split(",", -1);
                if (entries.length > MAX_CONTROLS) throw new IllegalArgumentException("too many control keys");
                for (String entry : entries) {
                    if (entry.length() < 3 || entry.charAt(1) != '=')
                        throw new IllegalArgumentException("malformed control key");
                    char key = entry.charAt(0);
                    String value = entry.substring(2);
                    if (value.isEmpty()) throw new IllegalArgumentException("empty control value");
                    values.put(key, value);
                }
            }
            return new Command(values);
        }

        boolean isContinuation() {
            for (Character key : values.keySet()) {
                if (key != 'm' && key != 'q') return false;
            }
            return values.containsKey('m');
        }

        private static char character(Map<Character, String> values, char key, char fallback) {
            String value = values.get(key);
            if (value == null) return fallback;
            if (value.length() != 1) throw new IllegalArgumentException("invalid " + key + " value");
            return value.charAt(0);
        }

        private static int integer(Map<Character, String> values, char key, int fallback) {
            String value = values.get(key);
            if (value == null) return fallback;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + key + " value");
            }
        }

        private static long unsigned(Map<Character, String> values, char key) {
            String value = values.get(key);
            if (value == null) return 0;
            try {
                long result = Long.parseLong(value);
                if (result < 0 || result > 0xffffffffL) throw new NumberFormatException();
                return result;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + key + " value");
            }
        }
    }
}
