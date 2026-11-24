package com.termux.app.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.FeatureRequest;
import com.termux.app.settings.LauncherSettings;
import com.termux.app.settings.LauncherSettingsAdapters;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

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
import java.util.function.Supplier;

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
    /** The settings registry, asked for on the first answer; null when settings turns are off. */
    @Nullable private final Supplier<SettingsRegistry> registrySupplier;
    private final AssistantCards cards;
    /** Settings shortlist by embeddings, keywords as the fallback; null means keywords only. */
    @Nullable private volatile AssistantSettingsLookup settingsLookup;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    @Nullable private Generation current;
    private volatile boolean modelReady = true;

    /** Where the running answer is, from the request's events. */
    public enum AnswerPhase { NONE, WAITING, LOADING, STARTED }

    private static final class Generation {
        final long chatId;
        /** The user text of a send; null for a retry. */
        @Nullable final String userText;
        /** The answer to redo, for a retry; 0 for a send. */
        final long retryMessageId;
        volatile long messageId;
        final StringBuilder text = new StringBuilder();
        /** The model's thinking, as streamed; guarded by the generation like {@link #text}. */
        final StringBuilder thinking = new StringBuilder();
        /** First thinking delta and the first answer text after it (or the end); -1 until then. */
        long thinkStartMs = -1L;
        long thinkEndMs = -1L;
        long lastThinkPersistMs;
        @Nullable String errorCode;
        boolean finishSeen;
        boolean done;
        volatile boolean stopped;
        /** This answer's request; null until the transport has started it. */
        @Nullable volatile ChatTransport.Call call;
        volatile AnswerPhase phase = AnswerPhase.NONE;
        final AtomicBoolean cancelSent = new AtomicBoolean();
        long lastPersistMs;
        long lastNotifyMs;
        /** Request start (queue wait and model load excluded), first token and the runtime's token count; -1 when unknown. */
        volatile long startMs = -1L;
        volatile long firstTokenMs = -1L;
        volatile int outputTokens = -1;
        volatile boolean loading;
        /** Settings turn: what the model was offered, and the tool calls it streamed, by index. */
        @Nullable AssistantSettingsTurn.Shortlist shortlist;
        @Nullable SettingsRegistry registry;
        final java.util.TreeMap<Integer, JSONObject> toolCalls = new java.util.TreeMap<>();

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
        this(store, transport, windowSource, streamExecutor, controlExecutor, notifier, clock, null,
            new AssistantCards());
    }

    AssistantEngine(@NonNull AssistantChatStore store, @NonNull ChatTransport transport,
                    @NonNull WindowSource windowSource, @NonNull Executor streamExecutor,
                    @NonNull Executor controlExecutor, @NonNull Executor notifier,
                    @NonNull LongSupplier clock, @Nullable Supplier<SettingsRegistry> registrySupplier,
                    @NonNull AssistantCards cards) {
        this.registrySupplier = registrySupplier;
        this.cards = cards;
        this.store = store;
        cards.attach(store);
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
                new AssistantContextWindow(app), stream, control, main::post, System::currentTimeMillis,
                () -> deviceRegistry(app), AssistantCards.shared());
            sInstance.settingsLookup = new AssistantSettingsLookup(new TaiEmbedder(app),
                () -> SettingsCorpusAsset.read(app),
                new java.io.File(app.getFilesDir(), "assistant/settings-vectors.bin"));
        }
        return sInstance;
    }

    /** Uses {@code lookup} for the settings shortlist. Tests pass one over a fake embedder. */
    void setSettingsLookup(@Nullable AssistantSettingsLookup lookup) {
        settingsLookup = lookup;
    }

    /** The Assistant page was left: the settings vectors leave memory (the file stays). */
    public void releaseSettingsLookup() {
        AssistantSettingsLookup lookup = settingsLookup;
        if (lookup != null) lookup.release();
    }

    /** The launcher's settings registry, the one every door uses. */
    @NonNull
    static SettingsRegistry deviceRegistry(@NonNull Context app) {
        return LauncherSettings.registry(app, c -> LauncherSettingsAdapters.forDevice(c,
            () -> TerminalActionDispatcher.getInstance().redressChrome()));
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

    /** The phase of the answer being written; NONE when there is none. */
    @NonNull
    public AnswerPhase answerPhase() {
        synchronized (lock) {
            return current == null || current.stopped ? AnswerPhase.NONE : current.phase;
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
     * Keeps a question the model cannot answer yet: stores it and a "waiting" answer under it, so
     * it is titled, searchable and survives a restart. It is answered, oldest first, once
     * {@link #setModelReady} says the model can. Returns false when the text is empty or the chat
     * is gone.
     */
    public boolean hold(long chatId, @NonNull String text) {
        if (text.trim().isEmpty()) return false;
        controlExecutor.execute(() -> {
            long now = clock.getAsLong();
            long userId = store.appendMessage(chatId, AssistantMessage.ROLE_USER, text,
                AssistantMessage.STATE_COMPLETE, now);
            long waitId = userId < 0 ? -1L : store.appendMessage(chatId, AssistantMessage.ROLE_ASSISTANT, "",
                AssistantMessage.STATE_WAITING, now);
            if (waitId >= 0) notifyMessage(chatId, waitId);
            notifyChats();
            drainWaiting();
        });
        return true;
    }

    /**
     * Says whether the default model can answer now. When it can, the oldest waiting answer starts,
     * and each finished answer starts the next. It begins true.
     */
    public void setModelReady(boolean ready) {
        modelReady = ready;
        if (ready) drainWaiting();
    }

    private void drainWaiting() {
        if (!modelReady) return;
        controlExecutor.execute(() -> {
            if (!modelReady || isGenerating()) return;
            AssistantMessage w = store.oldestWaiting();
            if (w == null) return;
            Generation g = new Generation(w.chatId, null, w.id);
            if (!begin(g)) return;
            streamExecutor.execute(() -> run(g));
        });
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
     * Stops the running answer. It is marked stopped at once, keeping the text it has; its own
     * request is cancelled (a waiting one leaves the queue), no other.
     */
    public void stop() {
        final Generation g;
        synchronized (lock) {
            g = current;
            if (g == null || g.stopped) return;
            g.stopped = true;
        }
        controlExecutor.execute(() -> {
            cancelCall(g);
            finish(g, AssistantMessage.STATE_STOPPED, null);
        });
    }

    private void cancelCall(Generation g) {
        ChatTransport.Call call = g.call;
        if (call == null || !g.cancelSent.compareAndSet(false, true)) return;
        try {
            call.cancel();
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
            store.setThinking(g.messageId, null, null);
            cards.remove(g.messageId);
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
        history = withoutCards(history);
        int window = windowSource.servingWindowTokens();
        if (window <= 0) window = FALLBACK_WINDOW_TOKENS;
        String system = null;
        JSONArray tools = null;
        String request = lastUserText(history);
        if (registrySupplier != null && !request.isEmpty()) {
            try {
                g.registry = registrySupplier.get();
                AssistantSettingsLookup lookup = settingsLookup;
                g.shortlist = lookup != null ? lookup.lookup(g.registry, request)
                    : AssistantSettingsTurn.lookup(g.registry, request);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Settings lookup failed: " + e);
                g.registry = null;
                g.shortlist = null;
            }
            if (g.shortlist != null && !g.shortlist.offered.isEmpty()) {
                system = AssistantSettingsTurn.instructions(g.shortlist);
                tools = AssistantSettingsTurn.tools(g.shortlist);
                window = Math.max(window / 2, window - AssistantSettingsTurn.reserveTokens(system, tools));
            }
        }
        ContextFitter.Result fit = ContextFitter.fit(history, window);
        String body;
        try {
            body = buildBody(fit, system, tools);
        } catch (JSONException e) {
            finish(g, AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_OTHER);
            return;
        }
        try {
            g.startMs = clock.getAsLong();
            ChatTransport.Call call = transport.start(body, event -> onEvent(g, event));
            g.call = call;
            // A Stop that came before the request existed.
            if (g.stopped) cancelCall(g);
            call.await();
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
        } else if (settleSettingsTurn(g)) {
            finish(g, AssistantMessage.STATE_COMPLETE, null);
        } else if (textOf(g).isEmpty()) {
            finish(g, AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_EMPTY_ANSWER);
        } else {
            finish(g, AssistantMessage.STATE_COMPLETE, null);
        }
    }

    /**
     * A settings card is not sent back to the model: an applied one becomes the one line saying what
     * was applied, any other card leaves its answer and the request before it out.
     */
    @NonNull
    private static List<AssistantMessage> withoutCards(@NonNull List<AssistantMessage> history) {
        List<AssistantMessage> out = null;
        for (int i = 0; i < history.size(); i++) {
            AssistantMessage m = history.get(i);
            SettingsCard card = m.isUser() ? null : SettingsCard.fromJson(m.card);
            if (card == null) {
                if (out != null) out.add(m);
                continue;
            }
            if (out == null) out = new java.util.ArrayList<>(history.subList(0, i));
            String note = card.note();
            if (note != null) {
                out.add(new AssistantMessage(m.id, m.chatId, m.role, note, m.state, m.errorCode, m.createdMs));
            } else if (!out.isEmpty() && out.get(out.size() - 1).isUser()) {
                // Nothing is sent for the card, so its request goes too: turns keep alternating.
                out.remove(out.size() - 1);
            }
        }
        return out == null ? history : out;
    }

    @NonNull
    private static String lastUserText(@NonNull List<AssistantMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).isUser()) return history.get(i).text;
        }
        return "";
    }

    /**
     * Reads the model's reply as a settings turn: a card for the changes it proposed, its question
     * or words, or "No matching setting". Returns false when the turn was not a settings turn or the
     * answer is just text, which then ends as any answer does.
     */
    private boolean settleSettingsTurn(Generation g) {
        AssistantSettingsTurn.Shortlist shortlist = g.shortlist;
        SettingsRegistry registry = g.registry;
        if (shortlist == null || registry == null) return false;
        JSONArray calls;
        synchronized (g) {
            calls = new JSONArray();
            for (JSONObject each : g.toolCalls.values()) calls.put(each);
        }
        AssistantSettingsTurn.Answer answer = AssistantSettingsTurn.interpret(registry, shortlist,
            textOf(g), calls);
        SettingsCard card = AssistantSettingsTurn.card(shortlist, answer);
        String text;
        if (card != null) {
            cards.put(g.messageId, card);
            text = card.summary();
        } else if (answer.question != null) {
            text = answer.question;
        } else if (!answer.text.isEmpty()) {
            text = answer.text;
        } else if (answer.dropped > 0 || calls.length() > 0 || shortlist.offered.isEmpty()) {
            card = SettingsCard.noMatch(lastUserText(store.messages(g.chatId)));
            cards.put(g.messageId, card);
            text = card.summary();
        } else {
            return false;
        }
        synchronized (g) {
            g.text.setLength(0);
            g.text.append(text);
        }
        return true;
    }

    private void collectToolCalls(Generation g, @NonNull JSONObject chunk) {
        JSONArray choices = chunk.optJSONArray("choices");
        JSONObject choice = choices == null ? null : choices.optJSONObject(0);
        JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
        JSONArray calls = delta == null ? null : delta.optJSONArray("tool_calls");
        if (calls == null) return;
        synchronized (g) {
            for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.optJSONObject(i);
                JSONObject function = call == null ? null : call.optJSONObject("function");
                if (function == null) continue;
                int index = call.optInt("index", i);
                JSONObject held = g.toolCalls.get(index);
                try {
                    if (held == null) {
                        g.toolCalls.put(index, new JSONObject(call.toString()));
                    } else {
                        JSONObject heldFunction = held.getJSONObject("function");
                        String name = function.optString("name", "");
                        if (!name.isEmpty()) heldFunction.put("name", name);
                        Object arguments = function.opt("arguments");
                        if (arguments instanceof String) {
                            heldFunction.put("arguments", heldFunction.optString("arguments", "") + arguments);
                        } else if (arguments != null) {
                            heldFunction.put("arguments", arguments);
                        }
                    }
                } catch (JSONException ignored) {
                    // A malformed chunk is not a call.
                }
            }
        }
    }

    private void onEvent(Generation g, FeatureRequest.Event event) throws IOException {
        if (g.loading && event.type != FeatureRequest.Event.Type.LOADING) {
            // The model is up and the queue is clear: load and wait time are not the answer's.
            g.loading = false;
            g.startMs = clock.getAsLong();
        }
        if (event.usage != null && event.usage.has("completion_tokens")) {
            g.outputTokens = Math.max(0, event.usage.optInt("completion_tokens", 0));
        }
        switch (event.type) {
            case WAITING:
                g.loading = true;
                setPhase(g, AnswerPhase.WAITING);
                return;
            case LOADING:
                g.loading = true;
                setPhase(g, AnswerPhase.LOADING);
                return;
            case ERROR:
                g.errorCode = AssistantMessage.errorFor(event.errorCode);
                Log.w(LOG_TAG, "Answer failed: " + event.errorCode);
                return;
            case DONE:
                return;
            default:
                break;
        }
        // TEXT
        setPhase(g, AnswerPhase.STARTED);
        if (g.stopped) {
            // The request may still be generating for nobody: end it, then stop reading.
            cancelCall(g);
            throw new IOException("stopped");
        }
        if (event.finishReason != null) g.finishSeen = true;
        if (g.shortlist != null && event.openAi != null) collectToolCalls(g, event.openAi);
        if (!event.reasoning.isEmpty()) {
            if (g.firstTokenMs < 0L) g.firstTokenMs = clock.getAsLong();
            appendThinking(g, event.reasoning);
        }
        if (!event.text.isEmpty()) {
            long now = clock.getAsLong();
            if (g.firstTokenMs < 0L) g.firstTokenMs = now;
            endThinking(g, now);
            appendDelta(g, event.text);
        }
    }

    private void setPhase(Generation g, AnswerPhase phase) {
        if (g.phase == phase) return;
        g.phase = phase;
        notifyMessage(g.chatId, g.messageId);
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

    private void appendThinking(Generation g, String delta) {
        long now = clock.getAsLong();
        String snapshot = null;
        boolean notify;
        synchronized (g) {
            if (g.done) return;
            if (g.thinkStartMs < 0L) g.thinkStartMs = now;
            g.thinking.append(delta);
            if (now - g.lastThinkPersistMs >= PERSIST_INTERVAL_MS) {
                g.lastThinkPersistMs = now;
                snapshot = g.thinking.toString();
            }
            notify = now - g.lastNotifyMs >= NOTIFY_INTERVAL_MS;
            if (notify) g.lastNotifyMs = now;
        }
        if (snapshot != null) store.setThinking(g.messageId, snapshot, null);
        if (notify || snapshot != null) notifyMessage(g.chatId, g.messageId);
    }

    /** The first answer text after thinking ends it: saves the thinking with its time at once. */
    private void endThinking(Generation g, long now) {
        String snapshot;
        long ms;
        synchronized (g) {
            if (g.done || g.thinkStartMs < 0L || g.thinkEndMs >= 0L) return;
            g.thinkEndMs = now;
            ms = now - g.thinkStartMs;
            snapshot = g.thinking.toString();
        }
        store.setThinking(g.messageId, snapshot, ms);
    }

    private static String textOf(Generation g) {
        synchronized (g) {
            return g.text.toString();
        }
    }

    /** Ends the answer once: saves it in its final state and lets the next one start. */
    private void finish(Generation g, String state, @Nullable String errorCode) {
        String text;
        String thinking = null;
        Long thinkingMs = null;
        long now = clock.getAsLong();
        synchronized (g) {
            if (g.done || g.messageId <= 0L) return;
            g.done = true;
            text = g.text.toString();
            if (g.thinkStartMs >= 0L) {
                if (g.thinkEndMs < 0L) g.thinkEndMs = now;
                thinking = g.thinking.toString();
                thinkingMs = g.thinkEndMs - g.thinkStartMs;
            }
        }
        Long duration = null;
        Long ttft = null;
        Integer tokens = null;
        boolean answered = AssistantMessage.STATE_COMPLETE.equals(state)
            || (AssistantMessage.STATE_STOPPED.equals(state) && !text.isEmpty());
        if (answered && g.startMs >= 0L && g.firstTokenMs >= g.startMs) {
            duration = now - g.startMs;
            ttft = g.firstTokenMs - g.startMs;
            if (g.outputTokens >= 0) tokens = g.outputTokens;
        }
        store.updateMessage(g.messageId, text, state, errorCode, now, duration, ttft, tokens);
        store.setThinking(g.messageId, thinking, thinkingMs);
        clear(g);
        notifyMessage(g.chatId, g.messageId);
        notifyChats();
        drainWaiting();
    }

    private void clear(Generation g) {
        synchronized (lock) {
            if (current == g) current = null;
        }
    }

    @NonNull
    static String buildBody(@NonNull ContextFitter.Result fit) throws JSONException {
        return buildBody(fit, null, null);
    }

    /** The request: {@code system} first when there is one, the fitted chat, and the {@code tools} offered. */
    @NonNull
    static String buildBody(@NonNull ContextFitter.Result fit, @Nullable String system,
                            @Nullable JSONArray tools) throws JSONException {
        JSONArray messages = new JSONArray();
        if (system != null) messages.put(new JSONObject().put("role", "system").put("content", system));
        for (ContextFitter.Sent sent : fit.sent) {
            messages.put(new JSONObject().put("role", sent.role).put("content", sent.text));
        }
        // No "model": Feature request picks the Assistant's model.
        JSONObject body = new JSONObject().put("messages", messages).put("stream", true)
            .put("stream_options", new JSONObject().put("include_usage", true));
        if (tools != null) body.put("tools", tools);
        return body.toString();
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
