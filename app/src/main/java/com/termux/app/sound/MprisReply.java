package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a player's state out of what {@code dbus-send --print-reply} printed for
 * {@code Properties.GetAll("org.mpris.MediaPlayer2.Player")}. The helper sends that text as it
 * is, so the shell never has to take D-Bus's nesting apart; this does, and is tested against
 * real replies. Pure.
 *
 * <p>dbus-send prints one value per line — {@code string "…"}, {@code int64 5}, {@code array [}
 * … {@code ]}, {@code dict entry(} key value {@code )} — and a variant as {@code variant}
 * followed by its value on the same line.
 */
final class MprisReply {

    static final String MPRIS_PREFIX = "org.mpris.MediaPlayer2.";

    private MprisReply() {}

    /** The player's state, or null when {@code reply} is not a property map. */
    @Nullable
    static PlayerState parse(@NonNull String name, int pid, @Nullable String reply) {
        if (reply == null) return null;
        Object top;
        try {
            top = new Reader(tokens(reply)).value();
        } catch (RuntimeException e) {
            return null;
        }
        if (!(top instanceof Map)) return null;
        Map<?, ?> props = (Map<?, ?>) top;
        Object metaValue = props.get("Metadata");
        Map<?, ?> meta = metaValue instanceof Map ? (Map<?, ?>) metaValue : new LinkedHashMap<>();
        boolean control = !Boolean.FALSE.equals(props.get("CanControl"));
        Number rate = number(props.get("Rate"));
        return new PlayerState(name, pid, status(props.get("PlaybackStatus")),
            text(meta.get("xesam:title")), joined(meta.get("xesam:artist")),
            text(meta.get("xesam:album")), text(meta.get("mpris:artUrl")),
            text(meta.get("mpris:trackid")), microsToMs(number(meta.get("mpris:length"))),
            microsToMs(number(props.get("Position"))),
            rate == null ? 1f : rate.floatValue(),
            control && flag(props.get("CanPlay")), control && flag(props.get("CanPause")),
            control && flag(props.get("CanGoNext")), control && flag(props.get("CanGoPrevious")),
            control && flag(props.get("CanSeek")));
    }

    @NonNull
    static PlayerState.Status status(@Nullable Object value) {
        if ("Playing".equals(value)) return PlayerState.Status.PLAYING;
        if ("Paused".equals(value)) return PlayerState.Status.PAUSED;
        return PlayerState.Status.STOPPED;
    }

    private static boolean flag(@Nullable Object value) {
        return Boolean.TRUE.equals(value);
    }

    @Nullable
    private static String text(@Nullable Object value) {
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    /** {@code xesam:artist} is a list of names; a few players send one string instead. */
    @Nullable
    private static String joined(@Nullable Object value) {
        if (value instanceof String) return text(value);
        if (!(value instanceof List)) return null;
        StringBuilder out = new StringBuilder();
        for (Object item : (List<?>) value) {
            String name = text(item);
            if (name == null) continue;
            if (out.length() > 0) out.append(", ");
            out.append(name);
        }
        return out.length() == 0 ? null : out.toString();
    }

    @Nullable
    private static Number number(@Nullable Object value) {
        return value instanceof Number ? (Number) value : null;
    }

    private static long microsToMs(@Nullable Number micros) {
        return micros == null || micros.longValue() < 0 ? -1 : micros.longValue() / 1000L;
    }

    /**
     * The reply's values, one per entry: header and error lines dropped, every {@code variant}
     * prefix taken off (a variant is only a wrapper), and a string that ran over several lines
     * joined back into one.
     */
    @NonNull
    static List<String> tokens(@NonNull String reply) {
        String[] lines = reply.split("\n", -1);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("method return") || line.startsWith("Error")) {
                continue;
            }
            while (line.startsWith("variant")) line = line.substring("variant".length()).trim();
            if (line.startsWith("string \"")) {
                StringBuilder joined = new StringBuilder(line);
                while (!closesString(joined) && i + 1 < lines.length) {
                    joined.append('\n').append(lines[++i]);
                }
                line = joined.toString();
            }
            tokens.add(line);
        }
        return tokens;
    }

    private static boolean closesString(@NonNull CharSequence token) {
        return token.length() > "string \"".length() && token.charAt(token.length() - 1) == '"';
    }

    /** One pass over the tokens, value by value. */
    private static final class Reader {
        private final List<String> tokens;
        private int next;

        Reader(@NonNull List<String> tokens) {
            this.tokens = tokens;
        }

        @Nullable
        Object value() {
            String token = tokens.get(next++);
            if (token.equals("array [")) {
                List<Object> items = new ArrayList<>();
                boolean entries = true;
                while (!tokens.get(next).equals("]")) {
                    Object item = value();
                    entries &= item instanceof Entry;
                    items.add(item);
                }
                next++;
                if (!entries || items.isEmpty()) return items;
                Map<Object, Object> map = new LinkedHashMap<>();
                for (Object item : items) map.put(((Entry) item).key, ((Entry) item).value);
                return map;
            }
            if (token.equals("dict entry(")) {
                Object key = value();
                Object value = value();
                if (!tokens.get(next++).equals(")")) throw new IllegalStateException("entry");
                return new Entry(key, value);
            }
            if (token.equals("struct {")) {
                List<Object> fields = new ArrayList<>();
                while (!tokens.get(next).equals("}")) fields.add(value());
                next++;
                return fields;
            }
            if (token.startsWith("string \"")) return quoted(token, "string \"".length());
            if (token.startsWith("object path \"")) return quoted(token, "object path \"".length());
            int space = token.indexOf(' ');
            if (space < 0) return null;
            String type = token.substring(0, space);
            String literal = token.substring(space + 1).trim();
            try {
                switch (type) {
                    case "boolean": return "true".equals(literal);
                    case "double": return Double.parseDouble(literal);
                    case "byte": case "int16": case "uint16": case "int32": case "uint32":
                    case "int64": case "uint64":
                        return Long.parseLong(literal);
                    default: return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @NonNull
        private static String quoted(@NonNull String token, int start) {
            int end = token.length() - 1;
            return end >= start && token.charAt(end) == '"' ? token.substring(start, end)
                : token.substring(Math.min(start, token.length()));
        }
    }

    private static final class Entry {
        @Nullable final Object key;
        @Nullable final Object value;

        Entry(@Nullable Object key, @Nullable Object value) {
            this.key = key;
            this.value = value;
        }
    }
}
