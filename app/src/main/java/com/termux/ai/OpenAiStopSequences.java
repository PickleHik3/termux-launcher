package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses and applies OpenAI stop sequences without leaking a partial match to a stream. */
final class OpenAiStopSequences {
    private static final int MAX_STOP_SEQUENCES = 4;

    private OpenAiStopSequences() {
    }

    @NonNull
    static List<String> fromRequest(@NonNull JSONObject request) throws JSONException {
        if (!request.has("stop") || request.isNull("stop")) return Collections.emptyList();
        Object value = request.get("stop");
        ArrayList<String> stops = new ArrayList<>();
        if (value instanceof String) {
            addStop(stops, (String) value);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() > MAX_STOP_SEQUENCES) {
                throw new JSONException("stop must contain at most " + MAX_STOP_SEQUENCES + " sequences");
            }
            for (int i = 0; i < array.length(); i++) {
                Object stop = array.get(i);
                if (!(stop instanceof String)) throw new JSONException("stop entries must be strings");
                addStop(stops, (String) stop);
            }
        } else {
            throw new JSONException("stop must be a string or an array of strings");
        }
        return Collections.unmodifiableList(stops);
    }

    private static void addStop(@NonNull List<String> stops, @NonNull String stop) throws JSONException {
        if (stop.isEmpty()) throw new JSONException("stop sequences must not be empty");
        stops.add(stop);
    }

    @NonNull
    static Match truncate(@NonNull String text, @NonNull List<String> stops) {
        int earliest = -1;
        for (String stop : stops) {
            int index = text.indexOf(stop);
            if (index >= 0 && (earliest < 0 || index < earliest)) earliest = index;
        }
        return earliest < 0 ? new Match(text, false) : new Match(text.substring(0, earliest), true);
    }

    static final class Match {
        @NonNull final String text;
        final boolean stopped;

        Match(@NonNull String text, boolean stopped) {
            this.text = text;
            this.stopped = stopped;
        }
    }

    /** Stateful matcher for token streams; {@link #append(String)} returns only safe-to-emit text. */
    static final class StreamMatcher {
        @NonNull private final List<String> stops;
        @NonNull private final StringBuilder pending = new StringBuilder();
        private boolean stopped;

        StreamMatcher(@NonNull List<String> stops) {
            this.stops = stops;
        }

        @NonNull
        synchronized String append(@Nullable String text) {
            if (stopped || text == null || text.isEmpty()) return "";
            pending.append(text);
            Match match = truncate(pending.toString(), stops);
            if (match.stopped) {
                stopped = true;
                pending.setLength(0);
                return match.text;
            }

            int holdBack = longestPossibleStopPrefix();
            int emitLength = pending.length() - holdBack;
            if (emitLength <= 0) return "";
            String safe = pending.substring(0, emitLength);
            pending.delete(0, emitLength);
            return safe;
        }

        @NonNull
        synchronized String finish() {
            if (stopped || pending.length() == 0) return "";
            String remaining = pending.toString();
            pending.setLength(0);
            return remaining;
        }

        synchronized boolean isStopped() {
            return stopped;
        }

        private int longestPossibleStopPrefix() {
            int longest = 0;
            for (String stop : stops) {
                int candidate = Math.min(pending.length(), stop.length() - 1);
                for (; candidate > longest; candidate--) {
                    if (suffixMatchesPrefix(stop, candidate)) {
                        longest = candidate;
                        break;
                    }
                }
            }
            return longest;
        }

        private boolean suffixMatchesPrefix(@NonNull String stop, int length) {
            int pendingStart = pending.length() - length;
            for (int i = 0; i < length; i++) {
                if (pending.charAt(pendingStart + i) != stop.charAt(i)) return false;
            }
            return true;
        }
    }
}
