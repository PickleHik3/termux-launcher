package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The desktop-notification escape, {@code OSC 99}.
 *
 * <p>A program says "tell the user this" and the terminal passes it to whatever the system uses
 * for notifications. One notification can arrive in pieces — the title in one escape, the body in
 * the next, a last piece saying it is finished — so this keeps the half-built ones by the name the
 * program gave them and hands out only whole ones.
 *
 * <p>The wire form is {@code ESC ] 99 ; key=value:key=value ; payload ST}. Both semicolons are
 * always there, even with nothing between them.
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/desktop-notifications/">the protocol</a>
 */
public final class KittyNotifications {

    /** What the terminal does with a request once it is whole. */
    public interface Handler {

        /** Show this notification. */
        void show(@NonNull KittyNotification notification);

        /** Take down the notification with this name, if it is still up. */
        void close(@NonNull String id);

        /** Answer the program, with a whole escape sequence. */
        void write(@NonNull String escapeSequence);
    }

    /** Payload types this terminal understands. */
    private static final String SUPPORTED_PAYLOAD_TYPES = "title,body,close,?,alive";

    /**
     * What the terminal answers a capability query with: the parts of the protocol it really
     * honours. Icons and buttons are left out on purpose — the notification always wears the
     * launcher's own icon and carries no buttons, so a program should not plan around them.
     */
    private static final String CAPABILITIES = "p=" + SUPPORTED_PAYLOAD_TYPES
        + ":a=focus,report:o=always,unfocused,invisible:u=0,1,2:c=1:w=1";

    /** Longest name a program may give a notification. */
    private static final int MAX_ID_LENGTH = 256;

    /** How many half-built notifications may be waiting at once. */
    private static final int MAX_PENDING = 16;

    /** How many shown notifications are remembered for closing and reporting. */
    private static final int MAX_LIVE = 64;

    /** Ceiling on one assembled title or body, so a runaway program cannot eat the heap. */
    private static final int MAX_TEXT_LENGTH = 64 * 1024;

    /** What kitty separates button labels with. */
    private static final char BUTTON_SEPARATOR = ' ';

    /** Half-built requests, by the name the program gave them. */
    private final LinkedHashMap<String, Pending> mPending = new LinkedHashMap<>();

    /** Notifications handed to the client and not yet taken down again, by name. */
    private final LinkedHashMap<String, KittyNotification> mLive = new LinkedHashMap<>();

    /** Forget everything: a reset, or a new program taking over the terminal. */
    public void reset() {
        mPending.clear();
        mLive.clear();
    }

    /**
     * Take one {@code OSC 99} escape. {@code args} is everything after {@code 99;} — the metadata,
     * a semicolon, then the payload. The payload may itself contain semicolons, so only the first
     * one separates.
     */
    public void handle(@NonNull String args, @NonNull Handler handler) {
        String metadata;
        String payload;
        int separator = args.indexOf(';');
        if (separator < 0) {
            // Malformed: the protocol requires both semicolons. Read what is there anyway rather
            // than dropping a notification the user was meant to see.
            metadata = args;
            payload = "";
        } else {
            metadata = args.substring(0, separator);
            payload = args.substring(separator + 1);
        }

        Map<String, String> keys = parseMetadata(metadata);
        String id = clampId(keys.get("i"));
        String type = keys.containsKey("p") ? keys.get("p") : "title";
        if (type == null || type.isEmpty()) type = "title";

        switch (type) {
            case "?":
                handler.write("\033]99;i=" + id + ":p=?;" + CAPABILITIES + "\033\\");
                return;
            case "alive":
                handler.write("\033]99;i=" + id + ":p=alive;" + liveIds() + "\033\\");
                return;
            case "close":
                // A close needs a name to act on; without one the protocol says it does nothing.
                if (!id.isEmpty()) {
                    mPending.remove(id);
                    mLive.remove(id);
                    handler.close(id);
                }
                return;
            default:
                break;
        }

        boolean base64 = "1".equals(keys.get("e"));
        String text = base64 ? decodeBase64Utf8(payload) : payload;

        Pending pending = mPending.get(id);
        if (pending == null) {
            if (mPending.size() >= MAX_PENDING) {
                // Drop the oldest unfinished one rather than the new one: a program that never
                // sends its final chunk should not wedge the ones that do.
                Iterator<String> oldest = mPending.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            pending = new Pending();
            mPending.put(id, pending);
        }
        pending.absorb(keys);

        switch (type) {
            case "title":
                pending.title = append(pending.title, text);
                break;
            case "body":
                pending.body = append(pending.body, text);
                break;
            case "buttons":
                pending.buttons = append(pending.buttons, text);
                break;
            case "icon":
                // Icon data is accepted and discarded: the notification wears the launcher's own
                // icon, so there is nothing useful to do with a program's picture of itself.
                break;
            default:
                // An unknown payload type still carries metadata worth keeping, but no text.
                break;
        }

        // The done flag defaults to set, so a plain one-shot notification needs no metadata.
        if (!"0".equals(keys.get("d"))) {
            mPending.remove(id);
            KittyNotification notification = pending.build(id);
            if (notification.isEmpty()) return;
            if (!id.isEmpty()) {
                if (mLive.size() >= MAX_LIVE) {
                    Iterator<String> oldest = mLive.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
                mLive.put(id, notification);
            }
            handler.show(notification);
        }
    }

    /**
     * The user tapped a notification. Returns the escape to send the program, or null when it did
     * not ask to be told. {@code button} is 0 for the notification itself, or the button's number.
     *
     * <p>A tap takes the notification away, so it stops being live either way.
     */
    @Nullable
    public String activated(@NonNull String id, int button) {
        KittyNotification notification = mLive.remove(id);
        if (notification == null || !notification.isReportOnActivate()) return null;
        return button > 0
            ? "\033]99;i=" + id + ";" + button + "\033\\"
            : "\033]99;i=" + id + ";\033\\";
    }

    /**
     * A notification went away without being tapped. Returns the escape to send the program, or
     * null when it did not ask to be told.
     */
    @Nullable
    public String closed(@NonNull String id) {
        KittyNotification notification = mLive.remove(id);
        if (notification == null || !notification.isReportOnClose()) return null;
        return "\033]99;i=" + id + ":p=close;\033\\";
    }

    /** The names of the notifications still up, comma separated, for an {@code alive} query. */
    @NonNull
    private String liveIds() {
        StringBuilder builder = new StringBuilder();
        for (String id : mLive.keySet()) {
            if (builder.length() > 0) builder.append(',');
            builder.append(id);
        }
        return builder.toString();
    }

    /** Colon-separated {@code key=value} pairs, single-letter keys. Anything else is skipped. */
    @NonNull
    private static Map<String, String> parseMetadata(@NonNull String metadata) {
        Map<String, String> keys = new LinkedHashMap<>();
        int start = 0;
        while (start <= metadata.length()) {
            int end = metadata.indexOf(':', start);
            if (end < 0) end = metadata.length();
            String pair = metadata.substring(start, end);
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String key = pair.substring(0, equals);
                if (key.length() == 1) {
                    String value = pair.substring(equals + 1);
                    // Keys that may repeat collect; the rest are last-one-wins.
                    if ("n".equals(key) || "t".equals(key)) {
                        String existing = keys.get(key);
                        keys.put(key, existing == null || existing.isEmpty()
                            ? value : existing + '\u0000' + value);
                    } else {
                        keys.put(key, value);
                    }
                }
            }
            start = end + 1;
        }
        return keys;
    }

    @NonNull
    private static String clampId(@Nullable String id) {
        if (id == null) return "";
        return id.length() > MAX_ID_LENGTH ? id.substring(0, MAX_ID_LENGTH) : id;
    }

    @NonNull
    private static String append(@NonNull String existing, @NonNull String addition) {
        if (addition.isEmpty()) return existing;
        if (existing.length() >= MAX_TEXT_LENGTH) return existing;
        String joined = existing + addition;
        return joined.length() > MAX_TEXT_LENGTH ? joined.substring(0, MAX_TEXT_LENGTH) : joined;
    }

    /** The protocol's own fields — application name, icon name, type, sound — are always base64. */
    @NonNull
    private static String decodeBase64Utf8(@NonNull String encoded) {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(encoded.length() * 3 / 4 + 3);
        int accumulator = 0;
        int bits = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int sextet;
            if (c >= 'A' && c <= 'Z') sextet = c - 'A';
            else if (c >= 'a' && c <= 'z') sextet = c - 'a' + 26;
            else if (c >= '0' && c <= '9') sextet = c - '0' + 52;
            else if (c == '+' || c == '-') sextet = 62;
            else if (c == '/' || c == '_') sextet = 63;
            else continue; // Padding, whitespace and anything else is not data.
            accumulator = (accumulator << 6) | sextet;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                decoded.write((accumulator >> bits) & 0xFF);
            }
        }
        return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Ranked whitespace: what survives when several kinds run together between two words. */
    private static final int GAP_NONE = 0;

    private static final int GAP_SPACE = 1;

    private static final int GAP_TAB = 2;

    private static final int GAP_BREAK = 3;

    /**
     * Clean text a program sent, before anybody shows it.
     *
     * <p>What arrives here is whatever the program's output happened to be, and an escape it never
     * closed keeps swallowing until something else ends it — so the terminal's own control bytes,
     * and pieces of whatever the shell printed next, can land inside a title. None of that belongs
     * on a notification: the control characters go, and a run of spaces, tabs and line breaks
     * becomes one gap of the widest kind that was in it.
     *
     * @param multiline true for a body, which may keep its line breaks and tabs; false for a
     *     title, where every gap becomes a single space.
     */
    @NonNull
    static String clean(@Nullable String text, boolean multiline) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        int gap = GAP_NONE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                gap = Math.max(gap, multiline ? GAP_BREAK : GAP_SPACE);
                continue;
            }
            if (c == '\t') {
                gap = Math.max(gap, multiline ? GAP_TAB : GAP_SPACE);
                continue;
            }
            if (c == ' ') {
                gap = Math.max(gap, GAP_SPACE);
                continue;
            }
            // Every other control character is dropped outright rather than turned into a space:
            // it was never meant to be read, and standing in for it would only widen the gap.
            if (c < ' ' || c == '\u007F') continue;
            // Nothing is written before the first word or after the last, so the text comes out
            // trimmed without a second pass.
            if (out.length() > 0 && gap != GAP_NONE)
                out.append(gap == GAP_BREAK ? '\n' : gap == GAP_TAB ? '\t' : ' ');
            gap = GAP_NONE;
            out.append(c);
        }
        return out.toString();
    }

    @NonNull
    private static List<String> splitDecoded(@Nullable String packed) {
        List<String> parts = new ArrayList<>();
        if (packed == null || packed.isEmpty()) return parts;
        for (String part : packed.split("\u0000", -1)) {
            String decoded = decodeBase64Utf8(part);
            if (!decoded.isEmpty()) parts.add(decoded);
        }
        return parts;
    }

    /** A notification being assembled across escapes. */
    private static final class Pending {

        String title = "";

        String body = "";

        String buttons = "";

        int urgency = KittyNotification.URGENCY_UNSET;

        String occasion = KittyNotification.OCCASION_ALWAYS;

        boolean focusOnActivate = true;

        boolean reportOnActivate = false;

        boolean reportOnClose = false;

        int timeoutMillis = KittyNotification.TIMEOUT_DEFAULT;

        String applicationName;

        String iconNames;

        String types;

        String sound;

        /** Metadata may ride on any chunk of the request; the latest wins. */
        void absorb(@NonNull Map<String, String> keys) {
            String urgencyValue = keys.get("u");
            if (urgencyValue != null) {
                int parsed = parseInt(urgencyValue, KittyNotification.URGENCY_UNSET);
                if (parsed >= KittyNotification.URGENCY_LOW
                    && parsed <= KittyNotification.URGENCY_CRITICAL) {
                    urgency = parsed;
                }
            }
            String occasionValue = keys.get("o");
            if (KittyNotification.OCCASION_ALWAYS.equals(occasionValue)
                || KittyNotification.OCCASION_UNFOCUSED.equals(occasionValue)
                || KittyNotification.OCCASION_INVISIBLE.equals(occasionValue)) {
                occasion = occasionValue;
            }
            String actions = keys.get("a");
            if (actions != null) {
                for (String action : actions.split(",", -1)) {
                    boolean off = action.startsWith("-");
                    String name = off ? action.substring(1) : action;
                    if ("focus".equals(name)) focusOnActivate = !off;
                    else if ("report".equals(name)) reportOnActivate = !off;
                }
            }
            String closeReports = keys.get("c");
            if (closeReports != null) reportOnClose = "1".equals(closeReports);
            String timeout = keys.get("w");
            if (timeout != null) {
                int parsed = parseInt(timeout, KittyNotification.TIMEOUT_DEFAULT);
                if (parsed >= KittyNotification.TIMEOUT_DEFAULT) timeoutMillis = parsed;
            }
            if (keys.containsKey("f")) applicationName = keys.get("f");
            if (keys.containsKey("n")) iconNames = keys.get("n");
            if (keys.containsKey("t")) types = keys.get("t");
            if (keys.containsKey("s")) sound = keys.get("s");
        }

        @NonNull
        KittyNotification build(@NonNull String id) {
            List<String> buttonLabels = new ArrayList<>();
            if (!buttons.isEmpty()) {
                for (String label : buttons.split(String.valueOf(BUTTON_SEPARATOR), -1)) {
                    // A button's label is read by the user too, so it is cleaned like a title.
                    String cleaned = clean(label, false);
                    if (!cleaned.isEmpty()) buttonLabels.add(cleaned);
                }
            }
            String application = applicationName == null || applicationName.isEmpty()
                ? null : clean(decodeBase64Utf8(applicationName), false);
            String soundName = sound == null || sound.isEmpty() ? null : decodeBase64Utf8(sound);
            // Cleaned here, once, so no client has to wonder whether what it was handed is safe to
            // show. A request left with nothing to say is dropped by the caller.
            return new KittyNotification(id, clean(title, false), clean(body, true), urgency,
                occasion, focusOnActivate, reportOnActivate, reportOnClose, timeoutMillis,
                application == null || application.isEmpty() ? null : application,
                splitDecoded(iconNames), splitDecoded(types),
                soundName == null || soundName.isEmpty() ? null : soundName, buttonLabels);
        }

        private static int parseInt(@NonNull String value, int fallback) {
            try {
                return Integer.parseInt(value.trim().toLowerCase(Locale.US));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }
}
