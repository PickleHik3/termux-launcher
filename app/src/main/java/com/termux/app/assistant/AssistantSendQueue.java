package com.termux.app.assistant;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The questions held while the model is not ready (sheet 04.3): a question sent while a model is
 * downloading or loading stays in the thread as "Waiting for the model" and goes to the engine when
 * the model can answer, oldest first, one at a time (the engine runs one answer at a time). They
 * live with the page's process; the engine and the chat store know nothing of them, so no model
 * work and no schema change is involved.
 */
public final class AssistantSendQueue {

    /** What a held question is handed to. */
    public interface Sender {
        /** True when the question was taken. */
        boolean send(long chatId, @NonNull String text);
    }

    private static final class Held {
        final long chatId;
        final String text;

        Held(long chatId, String text) {
            this.chatId = chatId;
            this.text = text;
        }
    }

    private final List<Held> held = new ArrayList<>();

    public void add(long chatId, @NonNull String text) {
        held.add(new Held(chatId, text));
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    /** The questions held for {@code chatId}, oldest first. */
    @NonNull
    public List<String> pending(long chatId) {
        List<String> out = null;
        for (Held h : held) {
            if (h.chatId != chatId) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(h.text);
        }
        return out == null ? Collections.<String>emptyList() : out;
    }

    public void clear(long chatId) {
        for (int i = held.size() - 1; i >= 0; i--) {
            if (held.get(i).chatId == chatId) held.remove(i);
        }
    }

    /**
     * Hands the oldest held question over when the model can answer and no answer is running.
     * Call it whenever either changes. Returns whether one was sent; a question the sender refuses
     * stays held.
     */
    public boolean drain(boolean modelCanAnswer, boolean generating, @NonNull Sender sender) {
        if (!modelCanAnswer || generating || held.isEmpty()) return false;
        Held first = held.get(0);
        if (!sender.send(first.chatId, first.text)) return false;
        held.remove(0);
        return true;
    }
}
