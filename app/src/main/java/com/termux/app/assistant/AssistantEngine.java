package com.termux.app.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Runs the Assistant's answers. It is process-scoped (it holds only the application context), so an
 * answer keeps streaming and is saved when the page is left.
 *
 * <p>One answer runs at a time: {@link #send} and {@link #retry} are refused while one is running
 * (the page shows Stop). The request carries no model, so the runtime serves the launcher's default
 * assistant model. The answer is saved at most every {@link #PERSIST_INTERVAL_MS} and once more at
 * the end; {@link #liveText} has the text as it is right now. Listeners are called on the main
 * thread.
 */
public final class AssistantEngine {
    private static final String LOG_TAG = "AssistantEngine";
    public static final long PERSIST_INTERVAL_MS = 250L;
    /** Listeners hear of streamed text at most this often. */
    static final long NOTIFY_INTERVAL_MS = 50L;
    /** Used when the serving window cannot be read. */
    static final int FALLBACK_WINDOW_TOKENS = 4096;

    public interface Listener {
        void onMessageChanged(long chatId, long messageId);

        void onChatsChanged();
    }

    /** The window the model serves, in tokens. */
    public interface WindowSource {
        int servingWindowTokens();
    }

    private static AssistantEngine sInstance;

    private final AssistantChatStore store;
    private final ChatTransport transport;
    private final WindowSource windowSource;
    private final Executor streamExecutor;
    private final Executor controlExecutor;
    private final Executor notifier;
    private final LongSupplier clock;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    @Nullable private Generation current;

    private static final class Generation {
        final long chatId;
        /** The user text of a send; null for a retry. */
        @Nullable final String userText;
        /** The answer to redo, for a retry; 0 for a send. */
        final long retryMessageId;
        volatile long messageId;
        final StringBuilder text = new StringBuilder();
        @Nullable String errorCode;
        boolean finishSeen;
        boolean done;
        volatile boolean stopped;
        /** True once the runtime has answered the request: its generation is ours to cancel. */
        volatile boolean started;
        final AtomicBoolean cancelSent = new AtomicBoolean();
        long lastPersistMs;
        long lastNotifyMs;

        Generation(long chatId, @Nullable String userText, long retryMessageId) {
            this.chatId = chatId;
            this.userText = userText;
            this.retryMessageId = retryMessageId;
        }
    }

    AssistantEngine(@NonNull AssistantChatStore store, @NonNull ChatTransport transport,
                    @NonNull WindowSource windowSource, @NonNull Executor streamExecutor,
                    @NonNull Executor controlExecutor, @NonNull Executor notifier,
                    @NonNull LongSupplier clock) {
        this.store = store;
        this.transport = transport;
        this.windowSource = windowSource;
        this.streamExecutor = streamExecutor;
        this.controlExecutor = controlExecutor;
        this.notifier = notifier;
        this.clock = clock;
    }

    public static synchronized AssistantEngine getInstance(@NonNull Context context) {
        if (sInstance == null) {
            Context app = context.getApplicationContext();
            ExecutorService stream = Executors.newSingleThreadExecutor(r -> daemon(r, "assistant-stream"));
            ExecutorService control = Executors.newSingleThreadExecutor(r -> daemon(r, "assistant-control"));
            Handler main = new Handler(Looper.getMainLooper());
            sInstance = new AssistantEngine(AssistantChatStore.getInstance(app), new TaiChatTransport(app),
                new AssistantContextWindow(app), stream, control, main::post, System::currentTimeMillis);
        }
        return sInstance;
    }

    /** Drops the singleton. Intended for unit tests. */
    public static synchronized void resetForTesting() {
        sInstance = null;
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** True while an answer is running and has not been stopped. */
    public boolean isGenerating() {
        synchronized (lock) {
            return current != null && !current.stopped;
        }
    }

    /** The id of the answer being written, or 0. */
    public long generatingMessageId() {
        synchronized (lock) {
            return current == null || current.stopped ? 0L : current.messageId;
        }
    }

    /** The text of an answer being written, exactly as received so far; null when it is not. */
    @Nullable
    public String liveText(long messageId) {
        synchronized (lock) {
            Generation g = current;
            if (g == null || g.messageId != messageId) return null;
            synchronized (g) {
                return g.done ? null : g.text.toString();
            }
        }
    }

    /**
     * Stores the user's message and starts the answer. Returns false, and does nothing, when an
     * answer is already running or the text is empty.
     */
    public boolean send(long chatId, @NonNull String text) {
        if (text.trim().isEmpty()) return false;
        Generation g = new Generation(chatId, text, 0L);
        if (!begin(g)) return false;
        streamExecutor.execute(() -> run(g));
        return true;
    }

    /**
     * Runs the answer again for the user message before it. Only the newest message of a chat can
     * be retried, and only an answer. Returns false when that does not hold or one is running.
     */
    public boolean retry(long messageId) {
        AssistantMessage message = store.message(messageId);
        if (message == null || message.isUser()) return false;
        List<AssistantMessage> all = store.messages(message.chatId);
        if (all.isEmpty() || all.get(all.size() - 1).id != messageId || all.size() < 2
            || !all.get(all.size() - 2).isUser()
            || AssistantMessage.STATE_STREAMING.equals(message.state)) return false;
        Generation g = new Generation(message.chatId, null, messageId);
        if (!begin(g)) return false;
        streamExecutor.execute(() -> run(g));
        return true;
    }

    private boolean begin(Generation g) {
        synchronized (lock) {
            if (current != null && !current.stopped) return false;
            current = g;
            return true;
        }
    }

    /**
     * Stops the running answer. It is marked stopped at once, keeping the text it has; the runtime
     * is told to cancel only if it has begun this answer.
     */
    public void stop() {
        final Generation g;
        synchronized (lock) {
            g = current;
            if (g == null || g.stopped) return;
            g.stopped = true;
        }
        controlExecutor.execute(() -> {
            if (g.started) cancelRuntime(g);
            finish(g, AssistantMessage.STATE_STOPPED, null);
        });
    }

    private void cancelRuntime(Generation g) {
        if (!g.cancelSent.compareAndSet(false, true)) return;
        try {
            transport.cancel();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Cancel failed: " + e.getMessage());
        }
    }

    private void run(Generation g) {
        long now = clock.getAsLong();
        List<AssistantMessage> history;
        if (g.userText != null) {
            long userId = store.appendMessage(g.chatId, AssistantMessage.ROLE_USER, g.userText,
                AssistantMessage.STATE_COMPLETE, now);
            long answerId = userId < 0 ? -1L : store.appendMessage(g.chatId, AssistantMessage.ROLE_ASSISTANT, "",
                AssistantMessage.STATE_STREAMING, now);
            if (answerId < 0) {
                clear(g);
                notifyChats();
                return;
            }
            g.messageId = answerId;
            notifyMessage(g.chatId, userId);
        } else {
            g.messageId = g.retryMessageId;
            store.updateMessage(g.messageId, "", AssistantMessage.STATE_STREAMING, null, now);
        }
        notifyChats();
        notifyMessage(g.chatId, g.messageId);
        if (g.stopped) {
            finish(g, AssistantMessage.STATE_STOPPED, null);
            return;
        }
        history = store.messages(g.chatId);
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).id == g.messageId) {
                history = history.subList(0, i);
                break;
            }
        }
        int window = windowSource.servingWindowTokens();
        if (window <= 0) window = FALLBACK_WINDOW_TOKENS;
        ContextFitter.Result fit = ContextFitter.fit(history, window);
        String body;
        try {
            body = buildBody(fit);
        } catch (JSONException e) {
            finish(g, AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_OTHER);
            return;
        }
        try {
            transport.stream(body, new ChatTransport.Sink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    g.started = true;
                    if (g.stopped) {
                        // The runtime may still be generating for nobody: end it, then stop reading.
                        cancelRuntime(g);
                        throw new IOException("stopped");
                    }
                    StreamEvent parsed = StreamEvent.parse(event);
                    if (parsed.isError()) {
                        g.errorCode = parsed.errorCode;
                        Log.w(LOG_TAG, "Answer failed: " + parsed.rawErrorCode);
                        return;
                    }
                    if (parsed.finished) g.finishSeen = true;
                    if (!parsed.delta.isEmpty()) appendDelta(g, parsed.delta);
                }

                @Override
                public void onDone() {
                }
            });
        } catch (Exception e) {
            if (!g.stopped) {
                Log.w(LOG_TAG, "Answer failed: " + e);
                finish(g, AssistantMessage.STATE_ERROR,
                    g.errorCode != null ? g.errorCode
                        : e instanceof IOException ? AssistantMessage.ERROR_RUNTIME_DIED
                        : AssistantMessage.ERROR_OTHER);
            }
            return;
        }
        if (g.stopped) {
            finish(g, AssistantMessage.STATE_STOPPED, null);
        } else if (g.errorCode != null) {
            finish(g, AssistantMessage.STATE_ERROR, g.errorCode);
        } else if (textOf(g).isEmpty()) {
            finish(g, AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_EMPTY_ANSWER);
        } else {
            finish(g, AssistantMessage.STATE_COMPLETE, null);
        }
    }

    private void appendDelta(Generation g, String delta) {
        long now = clock.getAsLong();
        boolean persist;
        boolean notify;
        String snapshot = null;
        synchronized (g) {
            if (g.done) return;
            g.text.append(delta);
            persist = now - g.lastPersistMs >= PERSIST_INTERVAL_MS;
            notify = now - g.lastNotifyMs >= NOTIFY_INTERVAL_MS;
            if (persist) {
                g.lastPersistMs = now;
                snapshot = g.text.toString();
            }
            if (notify) g.lastNotifyMs = now;
        }
        if (snapshot != null) {
            store.updateMessage(g.messageId, snapshot, AssistantMessage.STATE_STREAMING, null, now);
        }
        if (notify || persist) notifyMessage(g.chatId, g.messageId);
    }

    private static String textOf(Generation g) {
        synchronized (g) {
            return g.text.toString();
        }
    }

    /** Ends the answer once: saves it in its final state and lets the next one start. */
    private void finish(Generation g, String state, @Nullable String errorCode) {
        String text;
        synchronized (g) {
            if (g.done || g.messageId <= 0L) return;
            g.done = true;
            text = g.text.toString();
        }
        store.updateMessage(g.messageId, text, state, errorCode, clock.getAsLong());
        clear(g);
        notifyMessage(g.chatId, g.messageId);
        notifyChats();
    }

    private void clear(Generation g) {
        synchronized (lock) {
            if (current == g) current = null;
        }
    }

    @NonNull
    static String buildBody(@NonNull ContextFitter.Result fit) throws JSONException {
        JSONArray messages = new JSONArray();
        for (ContextFitter.Sent sent : fit.sent) {
            messages.put(new JSONObject().put("role", sent.role).put("content", sent.text));
        }
        // No "model": the runtime falls back to the default assistant model.
        return new JSONObject().put("messages", messages).put("stream", true).toString();
    }

    private void notifyMessage(long chatId, long messageId) {
        notifier.execute(() -> {
            for (Listener l : listeners) l.onMessageChanged(chatId, messageId);
        });
    }

    private void notifyChats() {
        notifier.execute(() -> {
            for (Listener l : listeners) l.onChatsChanged();
        });
    }
}
