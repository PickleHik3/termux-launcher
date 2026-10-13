package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kitty's extended clipboard protocol, {@code OSC 5522}, for text and pictures.
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
 * <p>Text travels as {@code text/plain} and {@code text/plain;charset=utf-8}. Pictures travel
 * under their own {@code image/*} type: a read offers whatever {@link ClipboardOffer#types()} the
 * clipboard holds, and a write keeps the {@code image/*} data it carries for the {@link Handler}
 * to put on the clipboard. Other types are answered {@code ENOSYS} rather than pretended, and so
 * is {@code loc=primary}, which Android does not have.
 *
 * <p>Paste events ({@code CSI ? 5522 h}, kept by the emulator): when the user pastes, the program
 * is sent {@link #pasteEvent} — the types on offer and a single-use password — instead of the
 * text. A read that brings the password back with a {@code name} is the program fetching that
 * paste, and is passed to {@link Handler#read} as consented. Every other read goes through the
 * same launcher gates as {@code OSC 52}; the {@code pw}/{@code name} permission cache beyond that
 * is not implemented.
 *
 * <p>The class only parses and answers; the actual clipboard belongs to the {@link Handler}.
 */
public final class KittyClipboard {

    /** What the protocol needs from the terminal around it. */
    public interface Handler {
        /**
         * Read the clipboard for a program, answering {@code callback} exactly once with what it
         * holds, or with null when the read is refused (the same gates as an {@code OSC 52} query:
         * launcher on screen, "Let programs read the clipboard" on) or the clipboard holds
         * nothing. {@code pasted} is true when the read redeemed a paste event's password: the
         * user pasted, which is consent enough without the setting.
         *
         * <p>The callback may run later and on another thread; the answer is written from there,
         * and {@link ClipboardOffer#data} is only called there.
         */
        void read(boolean pasted, ReadCallback callback);

        /** Put the text of a finished write on the clipboard. */
        void writeText(String text);

        /**
         * Put the pictures of a finished write on the clipboard: data by {@code image/*} type, in
         * the order the program sent them, with the write's text, or null when it carried none.
         */
        void writeData(Map<String, byte[]> images, String text);

        /** Send an escape sequence to the program: the answer to a request. */
        void write(String escapeSequence);
    }

    /** Where a {@link Handler#read} delivers the clipboard. */
    public interface ReadCallback {
        /** The clipboard as it stands, or null when the read is refused. */
        void onOffer(ClipboardOffer offer);
    }

    /** Bytes of clipboard data in one {@code DATA} packet; a multiple of three so each pads alone. */
    static final int READ_CHUNK_BYTES = 3072;

    /** Most decoded data a write may build up. The spec asks terminals for at least 64 MiB. */
    static final int MAX_WRITE_BYTES = 64 * 1024 * 1024;

    /** The type a read asks with to list the types instead; the paste event lists under it too. */
    private static final String LISTING = ".";

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

    /** Decoded picture bytes of the write by {@code image/*} type, in arrival order. */
    private final Map<String, ByteArrayOutputStream> mWriteImages = new LinkedHashMap<>();

    /** Decoded bytes of every type in the write, against {@link #MAX_WRITE_BYTES}. */
    private long mWriteTotal;

    /** Whether any data for a type this clipboard cannot hold was seen in this write. */
    private boolean mWriteSawOther;

    /** The password the last paste event handed out. */
    private final PasteEventToken mPasteToken = new PasteEventToken();

    /** Pairs of target MIME type and the space-separated aliases that also name it. */
    private final List<String[]> mAliases = new ArrayList<>();

    public void reset() {
        mWriting = false;
        mWriteFailed = false;
        clearWriteData();
        mAliases.clear();
        mWriteSawOther = false;
        mPasteToken.clear();
    }

    /**
     * Tell the program the user pasted: {@code OK}, one {@code DATA} packet listing
     * {@code mimeTypes}, {@code DONE}, each carrying a fresh password the program reads the
     * paste with.
     */
    public void pasteEvent(List<String> mimeTypes, long now, Handler handler) {
        String pw = b64(mPasteToken.issue(now));
        handler.write("\033]5522;type=read:status=OK:pw=" + pw + "\033\\");
        handler.write("\033]5522;type=read:status=DATA:mime=" + b64(LISTING) + ":pw=" + pw + ";"
            + b64(String.join(" ", mimeTypes)) + "\033\\");
        handler.write("\033]5522;type=read:status=DONE:pw=" + pw + "\033\\");
    }

    /** Handle the text after {@code 5522;}; {@code terminator} is the ST or BEL the request ended with. */
    public void handle(String textParameter, String terminator, Handler handler) {
        int split = textParameter.indexOf(';');
        String metadata = split < 0 ? textParameter : textParameter.substring(0, split);
        String payload = split < 0 ? "" : textParameter.substring(split + 1);

        String type = "", mime = "", id = "", loc = "", pw = "", name = "";
        for (String pair : metadata.split(":")) {
            int equals = pair.indexOf('=');
            if (equals < 0) continue;
            String key = pair.substring(0, equals), value = pair.substring(equals + 1);
            switch (key) {
                case "type": type = value; break;
                case "mime": mime = value; break;
                case "id": id = sanitizeId(value); break;
                case "loc": loc = value; break;
                case "pw": pw = value; break;
                case "name": name = value; break;
                default: break; // Unknown keys: ignored.
            }
        }
        switch (type) {
            case "read":
                // Only a read that names itself can redeem a paste event's password.
                boolean pasted = !pw.isEmpty() && !name.isEmpty()
                    && mPasteToken.redeem(decodeOrNull(pw), System.currentTimeMillis());
                read(payload, loc, pasted, new Reply(handler, terminator, id), handler);
                break;
            case "write":
                mWriting = true;
                mWriteFailed = false;
                clearWriteData();
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

    private void read(String payload, String loc, boolean pasted, Reply reply, Handler handler) {
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
        boolean listing = false;
        List<String> wanted = new ArrayList<>();
        for (String token : requested.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (LISTING.equals(token)) listing = true;
            else wanted.add(token);
        }
        boolean listingOnly = listing;
        handler.read(pasted, offer -> answer(offer, listingOnly, wanted, reply));
    }

    /** Answer a read from what the clipboard offers; runs wherever the handler called back. */
    private static void answer(ClipboardOffer offer, boolean listing, List<String> wanted, Reply reply) {
        if (offer == null) {
            reply.status("read", "EPERM");
            return;
        }
        String text = offer.text() == null ? "" : offer.text();
        List<String> types = offer.types();
        if (listing) {
            List<String> names = new ArrayList<>();
            if (!text.isEmpty()) {
                names.add(TEXT_PLAIN);
                names.add(TEXT_PLAIN_UTF8);
            }
            names.addAll(types);
            reply.status("read", "OK");
            if (!names.isEmpty()) reply.data(LISTING, String.join(" ", names).getBytes(StandardCharsets.UTF_8));
            reply.status("read", "DONE");
            return;
        }
        // Everything asked for is fetched before OK, so a failure is one status, not half an answer.
        List<String> matched = new ArrayList<>();
        List<byte[]> blobs = new ArrayList<>();
        byte[] textBytes = null;
        for (String token : wanted) {
            if (isText(token)) {
                if (textBytes == null) textBytes = text.getBytes(StandardCharsets.UTF_8);
                matched.add(token);
                blobs.add(textBytes);
                continue;
            }
            String mime = token.trim().toLowerCase(Locale.ROOT);
            if (!types.contains(mime)) continue;
            byte[] data;
            try {
                data = offer.data(mime);
            } catch (ClipboardOffer.TooLargeException e) {
                reply.status("read", "EFBIG");
                return;
            } catch (IOException | RuntimeException e) {
                reply.status("read", "EIO");
                return;
            }
            if (data == null) {
                reply.status("read", "EIO");
                return;
            }
            matched.add(token);
            blobs.add(data);
        }
        if (matched.isEmpty()) {
            reply.status("read", "ENOSYS"); // Nothing asked for is on the clipboard in that type.
            return;
        }
        reply.status("read", "OK");
        for (int i = 0; i < matched.size(); i++) {
            byte[] bytes = blobs.get(i);
            for (int from = 0; from < bytes.length; from += READ_CHUNK_BYTES) {
                int to = Math.min(bytes.length, from + READ_CHUNK_BYTES);
                reply.data(matched.get(i), Arrays.copyOfRange(bytes, from, to));
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
        boolean text = isText(mime) || aliasIsText(mime);
        String image = text ? null : imageType(mime);
        if (!text && image == null) {
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
            if (mWriteTotal + decoded.length > MAX_WRITE_BYTES) {
                writeError("EFBIG", terminator, handler);
                return;
            }
            ByteArrayOutputStream into;
            if (text) {
                if (mWriteText == null) mWriteText = new ByteArrayOutputStream();
                into = mWriteText;
            } else {
                into = mWriteImages.get(image);
                if (into == null) mWriteImages.put(image, into = new ByteArrayOutputStream());
            }
            into.write(decoded, 0, decoded.length);
            mWriteTotal += decoded.length;
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

    /**
     * The {@code image/*} type data sent as {@code mime} is kept under: the type itself, or the
     * first picture type its alias list names. Null when it is no picture.
     */
    private String imageType(String mime) {
        if (isImage(mime)) return mime;
        for (String[] alias : mAliases) {
            if (!alias[0].equals(mime)) continue;
            for (String name : alias[1].split("\\s+")) if (isImage(name)) return name;
        }
        return null;
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
        if (mWriteText == null && mWriteImages.isEmpty()) {
            // Nothing this clipboard can hold arrived (or nothing at all).
            writeError(mWriteSawOther ? "ENOSYS" : "EINVAL", terminator, handler);
            return;
        }
        String text = mWriteText == null ? null : new String(mWriteText.toByteArray(), StandardCharsets.UTF_8);
        Map<String, byte[]> images = new LinkedHashMap<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : mWriteImages.entrySet())
            images.put(entry.getKey(), entry.getValue().toByteArray());
        clearWriteData();
        if (images.isEmpty()) handler.writeText(text);
        else handler.writeData(images, text);
        new Reply(handler, terminator, mWriteId).status("write", "DONE");
    }

    private void writeError(String status, String terminator, Handler handler) {
        mWriteFailed = true;
        mWriting = false;
        clearWriteData();
        new Reply(handler, terminator, mWriteId).status("write", status);
    }

    /** Drop what a write has gathered, so a big picture is not held past its write. */
    private void clearWriteData() {
        mWriteText = null;
        mWriteImages.clear();
        mWriteTotal = 0;
        mWriteCarry.setLength(0);
    }

    // --- Helpers ---

    /** Whether {@code mime} names the plain text this clipboard carries. */
    static boolean isText(String mime) {
        String normal = mime.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return TEXT_PLAIN.equals(normal) || TEXT_PLAIN_UTF8.equals(normal);
    }

    /** Whether {@code mime} names a picture this clipboard can carry. */
    static boolean isImage(String mime) {
        String normal = mime.trim().toLowerCase(Locale.ROOT);
        return normal.startsWith("image/") && normal.length() > "image/".length();
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code value} base64-decoded, or null when it is not base64. */
    private static String decodeOrNull(String value) {
        try {
            return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
