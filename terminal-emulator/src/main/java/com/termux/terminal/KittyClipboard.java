package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Kitty's extended clipboard protocol, {@code OSC 5522}, for text.
 *
 * <p>Spec: <a href="https://sw.kovidgoyal.net/kitty/clipboard/">kitty clipboard protocol</a> and
 * the {@code kitten clipboard} page under kittens. A packet is
 * {@code OSC 5522 ; key=value:key=value ; payload ST}: metadata keys are joined by {@code :}, the
 * payload is standard padded base64, and the answer goes back down the pty in the same shape.
 * <ul>
 *   <li>{@code type=read;base64(mimes)}: answered {@code status=OK}, one {@code status=DATA:mime=..}
 *       packet per chunk of each available type, then {@code status=DONE}. The payload {@code .}
 *       lists the available types instead of their data.</li>
 *   <li>{@code type=write}, then {@code type=wdata:mime=..;chunk} packets, then a bare
 *       {@code type=wdata} to end: answered {@code type=write:status=DONE}.</li>
 *   <li>{@code type=walias:mime=..;aliases} lets one blob answer to several MIME types.</li>
 * </ul>
 *
 * <p>Only the text types, {@code text/plain} and {@code text/plain;charset=utf-8}, are carried;
 * the Android clipboard bridge here is text only. Anything else is answered {@code ENOSYS} rather
 * than pretended, and so is {@code loc=primary}, which Android does not have. The paste-events
 * mode ({@code CSI ? 5522 h}) and the {@code pw}/{@code name} permission cache are not
 * implemented: every read is put through the same launcher gates as {@code OSC 52} (see
 * {@link Handler#readText()}), and {@code pw} and {@code name} are ignored.
 *
 * <p>The class only parses and answers; the actual clipboard belongs to the {@link Handler}.
 */
public final class KittyClipboard {

    /** What the protocol needs from the terminal around it. */
    public interface Handler {
        /**
         * The clipboard text for a read. Null when the read is refused (the same gates as an
         * {@code OSC 52} query: launcher on screen, "Let programs read the clipboard" on) or the
         * clipboard holds no text; the empty string when it is readable and empty.
         */
        String readText();

        /** Put the text of a finished write on the clipboard. */
        void writeText(String text);

        /** Send an escape sequence to the program: the answer to a request. */
        void write(String escapeSequence);
    }

    /** Bytes of clipboard data in one {@code DATA} packet; a multiple of three so each pads alone. */
    static final int READ_CHUNK_BYTES = 3072;

    /** Most decoded text a write may build up. The spec asks terminals for at least 64 MiB. */
    static final int MAX_WRITE_BYTES = 64 * 1024 * 1024;

    private static final String TEXT_PLAIN = "text/plain";
    private static final String TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8";

    /** True between {@code type=write} and its closing {@code wdata}. */
    private boolean mWriting;

    /** True after a write error: packets are dropped until the next {@code type=write}. */
    private boolean mWriteFailed;

    /** The write's id, echoed on its answer. */
    private String mWriteId = "";

    /** Decoded text bytes of the write, or null while none has arrived. */
    private ByteArrayOutputStream mWriteText;

    /** Base64 characters received but not yet a whole group of four. */
    private final StringBuilder mWriteCarry = new StringBuilder();

    /** The MIME type wdata packets are currently for, lower-cased. */
    private String mWriteMime = "";

    /** Whether any data for a non-text type was seen in this write. */
    private boolean mWriteSawOther;

    /** Pairs of target MIME type and the space-separated aliases that also name it. */
    private final List<String[]> mAliases = new ArrayList<>();

    public void reset() {
        mWriting = false;
        mWriteFailed = false;
        mWriteText = null;
        mWriteCarry.setLength(0);
        mAliases.clear();
        mWriteSawOther = false;
    }

    /** Handle the text after {@code 5522;}; {@code terminator} is the ST or BEL the request ended with. */
    public void handle(String textParameter, String terminator, Handler handler) {
        int split = textParameter.indexOf(';');
        String metadata = split < 0 ? textParameter : textParameter.substring(0, split);
        String payload = split < 0 ? "" : textParameter.substring(split + 1);

        String type = "", mime = "", id = "", loc = "";
        for (String pair : metadata.split(":")) {
            int equals = pair.indexOf('=');
            if (equals < 0) continue;
            String key = pair.substring(0, equals), value = pair.substring(equals + 1);
            switch (key) {
                case "type": type = value; break;
                case "mime": mime = value; break;
                case "id": id = sanitizeId(value); break;
                case "loc": loc = value; break;
                default: break; // pw, name and unknown keys: ignored.
            }
        }
        switch (type) {
            case "read":
                read(payload, loc, new Reply(handler, terminator, id), handler);
                break;
            case "write":
                mWriting = true;
                mWriteFailed = false;
                mWriteText = null;
                mWriteCarry.setLength(0);
                mWriteMime = "";
                mWriteSawOther = false;
                mWriteId = id;
                mAliases.clear();
                if ("primary".equals(loc)) {
                    writeError("ENOSYS", terminator, handler); // Android has no primary selection.
                } else if (!loc.isEmpty() && !"clipboard".equals(loc)) {
                    writeError("EINVAL", terminator, handler);
                }
                break;
            case "wdata":
                writeData(mime, payload, terminator, handler);
                break;
            case "walias":
                writeAlias(mime, payload, terminator, handler);
                break;
            default:
                break; // Unknown type: nothing waits on it.
        }
    }

    // --- Reads ---

    private void read(String payload, String loc, Reply reply, Handler handler) {
        if (!loc.isEmpty() && !"clipboard".equals(loc)) {
            reply.status("read", "ENOSYS"); // The primary selection: Android has none.
            return;
        }
        String requested;
        try {
            requested = new String(Base64.getDecoder().decode(payload.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return; // "ignore invalid reads"
        }
        String text = handler.readText();
        if (text == null) {
            reply.status("read", "EPERM");
            return;
        }
        boolean listing = false;
        List<String> wanted = new ArrayList<>();
        for (String token : requested.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (".".equals(token)) listing = true;
            else wanted.add(token);
        }
        if (listing) {
            reply.status("read", "OK");
            if (!text.isEmpty()) {
                String types = TEXT_PLAIN + " " + TEXT_PLAIN_UTF8;
                reply.data(".", types.getBytes(StandardCharsets.UTF_8));
            }
            reply.status("read", "DONE");
            return;
        }
        List<String> matched = new ArrayList<>();
        for (String token : wanted) if (isText(token)) matched.add(token);
        if (matched.isEmpty()) {
            reply.status("read", "ENOSYS"); // Only text lives on the Android clipboard here.
            return;
        }
        reply.status("read", "OK");
        if (!text.isEmpty()) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            for (String token : matched) {
                for (int from = 0; from < bytes.length; from += READ_CHUNK_BYTES) {
                    int to = Math.min(bytes.length, from + READ_CHUNK_BYTES);
                    reply.data(token, Arrays.copyOfRange(bytes, from, to));
                }
            }
        }
        reply.status("read", "DONE");
    }

    // --- Writes ---

    private void writeData(String mimeB64, String payload, String terminator, Handler handler) {
        if (!mWriting || mWriteFailed) return;
        if (mimeB64.isEmpty() && payload.isEmpty()) {
            finishWrite(terminator, handler);
            return;
        }
        String mime;
        try {
            mime = decodeMime(mimeB64);
        } catch (IllegalArgumentException e) {
            writeError("EINVAL", terminator, handler);
            return;
        }
        if (mime.isEmpty()) {
            writeError("EINVAL", terminator, handler);
            return;
        }
        if (!mime.equals(mWriteMime)) {
            // A new type starts: the previous one's base64 must have been whole.
            if (mWriteCarry.length() != 0) {
                writeError("EINVAL", terminator, handler);
                return;
            }
            mWriteMime = mime;
        }
        if (!isText(mime) && !aliasIsText(mime)) {
            mWriteSawOther = true; // Not kept: there is nowhere to put it.
            return;
        }
        // Only the whole of a type's chunks has to be padded, not each chunk: decode in groups of four.
        mWriteCarry.append(payload.trim());
        int whole = mWriteCarry.length() / 4 * 4;
        if (whole == 0) return;
        try {
            byte[] decoded = Base64.getDecoder().decode(mWriteCarry.substring(0, whole));
            mWriteCarry.delete(0, whole);
            if (mWriteText == null) mWriteText = new ByteArrayOutputStream();
            if ((long) mWriteText.size() + decoded.length > MAX_WRITE_BYTES) {
                writeError("EFBIG", terminator, handler);
                return;
            }
            mWriteText.write(decoded, 0, decoded.length);
        } catch (IllegalArgumentException e) {
            writeError("EINVAL", terminator, handler);
        }
    }

    private void writeAlias(String mimeB64, String payload, String terminator, Handler handler) {
        if (!mWriting || mWriteFailed) return;
        try {
            String target = decodeMime(mimeB64);
            String aliases = new String(Base64.getDecoder().decode(payload.trim()), StandardCharsets.UTF_8);
            mAliases.add(new String[] {target, aliases.trim().toLowerCase(Locale.ROOT)});
        } catch (IllegalArgumentException e) {
            writeError("EINVAL", terminator, handler);
        }
    }

    /** True when {@code mime} is a target whose alias list names a text type: its data is text. */
    private boolean aliasIsText(String mime) {
        for (String[] alias : mAliases) {
            if (!alias[0].equals(mime)) continue;
            for (String name : alias[1].split("\\s+")) if (isText(name)) return true;
        }
        return false;
    }

    private void finishWrite(String terminator, Handler handler) {
        mWriting = false;
        if (mWriteCarry.length() != 0) {
            writeError("EINVAL", terminator, handler);
            return;
        }
        if (mWriteText == null) {
            // Nothing this clipboard can hold arrived (or nothing at all).
            writeError(mWriteSawOther ? "ENOSYS" : "EINVAL", terminator, handler);
            return;
        }
        String text = new String(mWriteText.toByteArray(), StandardCharsets.UTF_8);
        mWriteText = null;
        handler.writeText(text);
        new Reply(handler, terminator, mWriteId).status("write", "DONE");
    }

    private void writeError(String status, String terminator, Handler handler) {
        mWriteFailed = true;
        mWriting = false;
        mWriteText = null;
        mWriteCarry.setLength(0);
        new Reply(handler, terminator, mWriteId).status("write", status);
    }

    // --- Helpers ---

    /** Whether {@code mime} names the plain text this clipboard carries. */
    static boolean isText(String mime) {
        String normal = mime.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return TEXT_PLAIN.equals(normal) || TEXT_PLAIN_UTF8.equals(normal);
    }

    private static String decodeMime(String mimeB64) {
        if (mimeB64.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(mimeB64), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
    }

    /** The spec's multiplexer id: only {@code [a-zA-Z0-9-_+.]} survive. */
    static String sanitizeId(String id) {
        StringBuilder out = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '+' || c == '.') out.append(c);
        }
        return out.toString();
    }

    /** Builds answer packets, echoing the request's id. */
    private static final class Reply {
        private final Handler mHandler;
        private final String mTerminator;
        private final String mIdSuffix;

        Reply(Handler handler, String terminator, String id) {
            mHandler = handler;
            mTerminator = terminator;
            mIdSuffix = id.isEmpty() ? "" : ":id=" + id;
        }

        void status(String type, String status) {
            mHandler.write("\033]5522;type=" + type + ":status=" + status + mIdSuffix + mTerminator);
        }

        void data(String mime, byte[] bytes) {
            String mimeB64 = Base64.getEncoder().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
            mHandler.write("\033]5522;type=read:status=DATA:mime=" + mimeB64 + mIdSuffix + ";"
                + Base64.getEncoder().encodeToString(bytes) + mTerminator);
        }
    }
}
