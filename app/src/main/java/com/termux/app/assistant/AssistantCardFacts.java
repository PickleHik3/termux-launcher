package com.termux.app.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.app.overview.CardFacts;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The shape of the chat the Assistant page shows, for Overview's card: who spoke and how much, with
 * none of the text kept. Read off the main thread (the chat is a database), delivered on it.
 */
public final class AssistantCardFacts {

    /** A message of this many characters fills a bubble's full width. */
    static final int FULL_LINE_CHARS = 160;

    private static final Executor IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "overview-assistant-card");
        t.setDaemon(true);
        return t;
    });

    private AssistantCardFacts() { }

    /**
     * Reads the saved chat's last bubbles and hands them to {@code onFacts} on the main thread;
     * nothing is delivered for no chat or one that cannot be read.
     */
    public static void read(@NonNull Context context, @NonNull Consumer<CardFacts> onFacts) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            CardFacts.Assistant facts = null;
            try {
                long chatId = app.getSharedPreferences(AssistantPageController.PREFS,
                    Context.MODE_PRIVATE).getLong(AssistantPageController.KEY_CHAT, -1L);
                if (chatId > 0) {
                    facts = of(AssistantChatStore.getInstance(app).messages(chatId));
                }
            } catch (RuntimeException ignored) {
                // An unreadable chat leaves the card a plain face.
            }
            CardFacts.Assistant result = facts;
            if (result != null) main.post(() -> onFacts.accept(result));
        });
    }

    /** The last {@link CardFacts#MAX_BUBBLES} messages as bubbles; null when there are none. */
    static CardFacts.Assistant of(@NonNull List<AssistantMessage> messages) {
        int count = Math.min(messages.size(), CardFacts.MAX_BUBBLES);
        if (count == 0) return null;
        boolean[] fromUser = new boolean[count];
        float[] fill = new float[count];
        int first = messages.size() - count;
        for (int i = 0; i < count; i++) {
            AssistantMessage m = messages.get(first + i);
            fromUser[i] = AssistantMessage.ROLE_USER.equals(m.role);
            fill[i] = Math.min(1f, m.text.length() / (float) FULL_LINE_CHARS);
        }
        return new CardFacts.Assistant(fromUser, fill);
    }
}
