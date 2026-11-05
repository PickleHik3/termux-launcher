package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the thread shows, worked out from the stored messages and the engine's state (sheet 01):
 * your messages, answers with their actions, the "Working on it" line, the question waiting for the
 * model, error rows, and the divider where the model stops seeing the chat. Pure: no views, so the
 * states are tested here and the adapter only draws a row.
 */
public final class AssistantRows {

    public enum Type { USER, ANSWER, WORKING, WAITING, ERROR, CUTOFF }

    /** Why an answer failed, as the row words it. */
    public enum ErrorKind { MODEL_STOPPED, TIMEOUT, EMPTY, OTHER, NOT_LOADED, NOT_DOWNLOADED, MEMORY }

    /** The one action an error row offers beside the standard ones. */
    public enum ErrorAction { TRY_AGAIN, CHOOSE_MODEL }

    public static final class Row {
        @NonNull public final Type type;
        /** The message the row is for; 0 for a held question or the divider. */
        public final long messageId;
        @NonNull public final String text;
        /** The answer is still arriving: no actions, and it is the engine's live text. */
        public final boolean streaming;
        /** The "Stopped" mark sits at the start of the actions. */
        public final boolean stopped;
        /** Copy and Share are offered (the answer has text). */
        public final boolean copyShare;
        /** Try again is offered: only under the newest answer. */
        public final boolean retry;
        @Nullable public final ErrorKind error;
        /** An error row offers "Use Gemma E2B" (memory, E2B on the phone). */
        public final boolean offerE2b;
        @NonNull public final String key;

        Row(@NonNull Type type, long messageId, @NonNull String text, boolean streaming, boolean stopped,
            boolean copyShare, boolean retry, @Nullable ErrorKind error, boolean offerE2b, @NonNull String key) {
            this.type = type;
            this.messageId = messageId;
            this.text = text;
            this.streaming = streaming;
            this.stopped = stopped;
            this.copyShare = copyShare;
            this.retry = retry;
            this.error = error;
            this.offerE2b = offerE2b;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Row)) return false;
            Row r = (Row) o;
            return type == r.type && messageId == r.messageId && text.equals(r.text)
                && streaming == r.streaming && stopped == r.stopped && copyShare == r.copyShare
                && retry == r.retry && error == r.error && offerE2b == r.offerE2b && key.equals(r.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, text);
        }

        @Override
        public String toString() {
            return type + "(" + key + ")";
        }
    }

    /** The engine and model facts a build reads. */
    public static final class Context {
        /** The answer being written, or 0. */
        public long generatingId;
        /** The engine's text for that answer right now, or null. */
        @Nullable public String liveText;
        /** Questions held for the model, oldest first. */
        @NonNull public List<String> pending = Collections.emptyList();
        /** Questions just handed to the engine that the store does not hold yet, oldest first. */
        @NonNull public List<String> sending = Collections.emptyList();
        /** Gemma E2B is on the phone and the chat model is not it: memory errors offer it. */
        public boolean offerE2b;
    }

    private AssistantRows() {
    }

    /**
     * @param cutIndex the index of the first message the model sees when older ones are left out;
     *     -1 for none ({@link #cutIndex})
     */
    @NonNull
    public static List<Row> build(@NonNull List<AssistantMessage> messages, int cutIndex, @NonNull Context context) {
        List<Row> rows = new ArrayList<>(messages.size() + 2);
        int last = messages.size() - 1;
        for (int i = 0; i <= last; i++) {
            AssistantMessage m = messages.get(i);
            if (i == cutIndex && cutIndex >= 0) {
                rows.add(new Row(Type.CUTOFF, 0, "", false, false, false, false, null, false, "cutoff"));
            }
            if (m.isUser()) {
                rows.add(new Row(Type.USER, m.id, m.text, false, false, false, false, null, false, "u" + m.id));
                continue;
            }
            boolean live = m.id == context.generatingId && context.generatingId != 0L;
            String text = live && context.liveText != null ? context.liveText : m.text;
            String state = m.state;
            // Still "streaming" in the store with nobody writing it: the process went away mid-answer.
            if (AssistantMessage.STATE_STREAMING.equals(state) && !live) state = AssistantMessage.STATE_STOPPED;
            boolean newest = i == last;
            String key = "a" + m.id;
            switch (state) {
                case AssistantMessage.STATE_STREAMING:
                    if (text.isEmpty()) {
                        rows.add(new Row(Type.WORKING, m.id, "", true, false, false, false, null, false, "w" + m.id));
                    } else {
                        rows.add(new Row(Type.ANSWER, m.id, text, true, false, false, false, null, false, key));
                    }
                    break;
                case AssistantMessage.STATE_STOPPED:
                    rows.add(new Row(Type.ANSWER, m.id, text, false, true, !text.isEmpty(), newest, null, false, key));
                    break;
                case AssistantMessage.STATE_ERROR:
                    if (!text.isEmpty()) {
                        rows.add(new Row(Type.ANSWER, m.id, text, false, false, false, false, null, false, key));
                    }
                    ErrorKind kind = errorKind(m.errorCode);
                    rows.add(new Row(Type.ERROR, m.id, "", false, false, false, newest, kind,
                        kind == ErrorKind.MEMORY && context.offerE2b, "e" + m.id));
                    break;
                case AssistantMessage.STATE_WAITING:
                    rows.add(new Row(Type.WAITING, m.id, "", false, false, false, false, null, false, "q" + m.id));
                    break;
                default:
                    rows.add(new Row(Type.ANSWER, m.id, text, false, false, !text.isEmpty(), newest, null, false, key));
                    break;
            }
        }
        for (int i = 0; i < context.sending.size(); i++) {
            rows.add(new Row(Type.USER, 0, context.sending.get(i), false, false, false, false, null, false, "s" + i));
        }
        if (!context.sending.isEmpty()) {
            rows.add(new Row(Type.WORKING, 0, "", true, false, false, false, null, false, "sw"));
        }
        for (int i = 0; i < context.pending.size(); i++) {
            rows.add(new Row(Type.USER, 0, context.pending.get(i), false, false, false, false, null, false, "p" + i));
        }
        if (!context.pending.isEmpty()) {
            rows.add(new Row(Type.WAITING, 0, "", false, false, false, false, null, false, "pw"));
        }
        return rows;
    }

    @NonNull
    public static ErrorKind errorKind(@Nullable String code) {
        if (code == null) return ErrorKind.OTHER;
        switch (code) {
            case AssistantMessage.ERROR_RUNTIME_DIED: return ErrorKind.MODEL_STOPPED;
            case AssistantMessage.ERROR_TIMEOUT: return ErrorKind.TIMEOUT;
            case AssistantMessage.ERROR_EMPTY_ANSWER: return ErrorKind.EMPTY;
            case AssistantMessage.ERROR_MODEL_NOT_LOADED: return ErrorKind.NOT_LOADED;
            case AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED: return ErrorKind.NOT_DOWNLOADED;
            case AssistantMessage.ERROR_NOT_ENOUGH_MEMORY: return ErrorKind.MEMORY;
            default: return ErrorKind.OTHER;
        }
    }

    /** The row's one sentence. */
    @StringRes
    public static int errorTitle(@NonNull ErrorKind kind) {
        switch (kind) {
            case MODEL_STOPPED: return R.string.assistant_error_stopped;
            case TIMEOUT: return R.string.assistant_error_timeout;
            case EMPTY: return R.string.assistant_error_empty;
            case NOT_LOADED: return R.string.assistant_error_not_loaded;
            case NOT_DOWNLOADED: return R.string.assistant_error_not_downloaded;
            case MEMORY: return R.string.assistant_error_memory;
            default: return R.string.assistant_error_other;
        }
    }

    /** A second line for the errors that have one, or 0. */
    @StringRes
    public static int errorBody(@NonNull ErrorKind kind, boolean offerE2b) {
        if (kind != ErrorKind.MEMORY) return 0;
        return offerE2b ? R.string.assistant_error_memory_body : R.string.assistant_error_memory_body_plain;
    }

    /** What the row's main button does: choosing a model when there is none, else another try. */
    @NonNull
    public static ErrorAction errorAction(@NonNull ErrorKind kind) {
        return kind == ErrorKind.NOT_DOWNLOADED ? ErrorAction.CHOOSE_MODEL : ErrorAction.TRY_AGAIN;
    }

    /**
     * Where the model stops seeing the chat: the index of the first message it is sent, or -1 when
     * it sees everything. {@code messages} is the whole chat; the fit works over the part up to the
     * newest question, as the engine's does.
     */
    public static int cutIndex(@NonNull List<AssistantMessage> messages, int windowTokens) {
        int newest = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isUser()) {
                newest = i;
                break;
            }
        }
        if (newest < 0 || windowTokens <= 0) return -1;
        return ContextFitter.fit(messages.subList(0, newest + 1), windowTokens).cutIndex;
    }
}
