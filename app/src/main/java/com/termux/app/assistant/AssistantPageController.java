package com.termux.app.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.TaiModelCentreFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the Assistant page: which chat is open, what the thread shows, what is sent and held, the
 * model chip, notice and sheet, dictation and the Chats list. All model and chat work goes through
 * {@link AssistantEngine}, {@link AssistantChatStore} and {@link AssistantModelStatus}; reads of the
 * store happen on one background thread. It listens only while the page is on screen; an answer
 * that kept going meanwhile is read again (its live text included) when the page returns.
 */
final class AssistantPageController implements AssistantEngine.Listener, AssistantModelStatus.Observer,
    AssistantChatView.Listener, AssistantComposer.Listener, AssistantThreadAdapter.Actions,
    AssistantChatsView.ImeListener, AssistantModelSheet.Host {

    private static final String PREFS = "assistant_page";
    private static final String KEY_CHAT = "chat";
    private static final String KEY_MIC_ASKED = "mic_asked";
    private static final int REQUEST_MIC = 0x4153;
    private static final long SNACKBAR_MS = 4000L;

    private final Context context;
    private final View page;
    private final AssistantChatView chatView;
    private final AssistantChatsView chatsView;
    private final AssistantEngine engine;
    private final AssistantChatStore store;
    private final AssistantModelStatus status;
    private final AssistantDictation dictation;
    private final AssistantContextWindow window;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assistant-page");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, AssistantModelPresenter.Failure> failures = new HashMap<>();
    private final Set<String> userCancelled = new HashSet<>();
    private AssistantUi.Palette palette;

    private boolean shown;
    private boolean restored;
    private long chatId = -1L;
    private List<AssistantMessage> messages = Collections.emptyList();
    private int cutIndex = -1;
    private String chatTitle = "";
    private int loadSeq;
    private boolean renderScheduled;
    private boolean forceFollow;
    private List<String> sending = Collections.emptyList();
    private int sendingBase;
    @Nullable private AssistantModelStatus.Snapshot snapshot;
    @Nullable private AssistantNoticeView.Content composerNotice;
    private boolean micRequestPending;
    private boolean chatsOpen;
    private long pendingDeleteId = -1L;
    @Nullable private AssistantModelSheet sheet;
    @Nullable private Snackbar deleteSnackbar;

    private final Runnable renderRunnable = () -> {
        renderScheduled = false;
        render();
    };

    AssistantPageController(@NonNull Context context, @NonNull View page, @NonNull AssistantChatView chatView,
                            @NonNull AssistantChatsView chatsView, @NonNull AssistantUi.Palette palette) {
        this.context = context;
        this.page = page;
        this.chatView = chatView;
        this.chatsView = chatsView;
        this.palette = palette;
        Context app = context.getApplicationContext();
        engine = AssistantEngine.getInstance(app);
        store = AssistantChatStore.getInstance(app);
        status = AssistantModelStatus.getInstance(app);
        dictation = new AssistantDictation(app);
        window = new AssistantContextWindow(app);
        chatView.setListener(this);
        chatView.composer().setListener(this);
        chatsView.setListener(this);
        chatsView.setVisibility(View.GONE);
    }

    void applyPalette(@NonNull AssistantUi.Palette p) {
        palette = p;
        chatView.applyPalette(p);
        chatsView.applyPalette(p);
    }

    // ---------------------------------------------------------------------- lifecycle

    void onShown() {
        if (shown) return;
        shown = true;
        if (!restored) {
            restored = true;
            long saved = prefs().getLong(KEY_CHAT, -1L);
            chatId = saved;
        }
        engine.addListener(this);
        status.addObserver(this);
        reload();
        loadRecent();
        render();
    }

    void onHidden() {
        if (!shown) return;
        shown = false;
        engine.removeListener(this);
        status.removeObserver(this);
        if (dictation.isActive()) dictation.cancel();
        if (sheet != null) sheet.dismiss();
        commitPendingDelete();
    }

    void onDetached() {
        onHidden();
        io.shutdown();
    }

    /** The window regained focus: a permission dialog this page raised may have been answered. */
    void onWindowFocusGained() {
        if (!micRequestPending) return;
        micRequestPending = false;
        composerNotice = null;
        if (dictation.hasPermission()) onMic();
        else showMicNotice();
    }

    private SharedPreferences prefs() {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------------ loading

    private void reload() {
        final int seq = ++loadSeq;
        final long id = chatId;
        io.execute(() -> {
            List<AssistantMessage> loaded = id > 0 ? store.messages(id) : Collections.<AssistantMessage>emptyList();
            AssistantChat chat = id > 0 ? store.chat(id) : null;
            int cut = -1;
            if (!loaded.isEmpty()) {
                try {
                    cut = AssistantRows.cutIndex(loaded, window.servingWindowTokens());
                } catch (RuntimeException e) {
                    cut = -1;
                }
            }
            final int cutResult = cut;
            main.post(() -> {
                if (seq != loadSeq) return;
                if (id > 0 && chat == null) {
                    // The chat is gone (deleted elsewhere): back to an empty one.
                    chatId = -1L;
                    prefs().edit().remove(KEY_CHAT).apply();
                    messages = Collections.emptyList();
                    cutIndex = -1;
                    chatTitle = "";
                } else {
                    messages = loaded;
                    cutIndex = cutResult;
                    chatTitle = chat == null ? "" : chat.title;
                }
                if (messages.size() > sendingBase) sending = Collections.emptyList();
                render();
            });
        });
    }

    private void loadRecent() {
        io.execute(() -> {
            List<AssistantChat> latest = store.latestChats(3);
            List<AssistantChatView.Recent> cards = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (AssistantChat chat : latest) {
                if (chat.id == pendingDeleteId) continue;
                cards.add(new AssistantChatView.Recent(chat.id, displayTitle(chat), whenLabel(chat.updatedMs, now)));
            }
            main.post(() -> chatView.setRecent(cards));
        });
    }

    @NonNull
    private String whenLabel(long updatedMs, long now) {
        TimeZone zone = TimeZone.getDefault();
        switch (AssistantChatGroups.groupOf(updatedMs, now, zone)) {
            case TODAY: return context.getString(R.string.assistant_group_today);
            case YESTERDAY: return context.getString(R.string.assistant_group_yesterday);
            case WEEK: return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date(updatedMs));
            default: return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(updatedMs));
        }
    }

    @NonNull
    private String displayTitle(@NonNull AssistantChat chat) {
        return chat.title.isEmpty() ? context.getString(R.string.assistant_chat_title_new) : chat.title;
    }

    // ------------------------------------------------------------------------ render

    private void scheduleRender() {
        if (renderScheduled) return;
        renderScheduled = true;
        page.postOnAnimation(renderRunnable);
    }

    private void render() {
        AssistantRows.Context rc = new AssistantRows.Context();
        long generating = engine.generatingMessageId();
        rc.generatingId = generating;
        rc.liveText = generating == 0L ? null : engine.liveText(generating);
        rc.sending = sending;
        AssistantModelStatus.Snapshot snap = snapshot;
        rc.offerE2b = snap != null && snap.gemmaE2b.state.kind != AssistantModelStatus.Kind.NOT_DOWNLOADED
            && snap.gemmaE2b.state.kind != AssistantModelStatus.Kind.DOWNLOADING
            && !AssistantModelStatus.sameModel(snap.defaultModel.modelId, AssistantModelStatus.MODEL_E2B);
        List<AssistantRows.Row> rows = AssistantRows.build(messages, cutIndex, rc);
        boolean follow = forceFollow || chatView.atBottom();
        forceFollow = false;
        chatView.adapter().submit(rows);
        chatView.showEmpty(rows.isEmpty());
        if (follow) chatView.thread().post(chatView::scrollToEnd);
        else chatView.updateJump();
        chatView.setTitle(chatTitle.isEmpty() ? context.getString(R.string.assistant_chat_title_new) : chatTitle);
        chatView.setNewChatEnabled(!rows.isEmpty());
        chatView.composer().setGenerating(engine.isGenerating());
        updateChip();
        updateNotice();
        updateAnswerStatus();
        // An answer the store still calls streaming that nobody is writing: read it as the store has it.
        if (generating == 0L && !messages.isEmpty() && AssistantMessage.STATE_STREAMING
            .equals(messages.get(messages.size() - 1).state) && !renderScheduled) {
            reload();
        }
    }

    /** The status line above the composer: loading, thinking or responding while an answer is in progress. */
    private void updateAnswerStatus() {
        long id = engine.generatingMessageId();
        String live = id == 0L ? null : engine.liveText(id);
        AssistantModelStatus.Snapshot snap = snapshot;
        AssistantModelStatus.Kind kind = snap == null ? AssistantModelStatus.Kind.NONE : snap.defaultModel.state.kind;
        AssistantModelPresenter.Stage stage = AssistantModelPresenter.stage(engine.isGenerating(),
            live == null || live.isEmpty(), kind);
        String name = snap == null ? "" : context.getString(AssistantModelPresenter.fullName(snap.defaultModel.modelId));
        chatView.setAnswerStatus(stage, name);
    }

    private boolean containsMessage(long id) {
        for (AssistantMessage m : messages) if (m.id == id) return true;
        return false;
    }

    // ------------------------------------------------------------------ engine events

    @Override
    public void onMessageChanged(long changedChat, long messageId) {
        if (changedChat != chatId) return;
        if (messageId == engine.generatingMessageId() && containsMessage(messageId)) scheduleRender();
        else reload();
    }

    @Override
    public void onChatsChanged() {
        loadRecent();
        if (chatsOpen) loadChats();
        reload();
    }

    // ------------------------------------------------------------------------- sending

    @Override
    public void onSend(@NonNull String text) {
        chatView.composer().clearText();
        forceFollow = true;
        if (chatId > 0) {
            submit(chatId, text);
            return;
        }
        io.execute(() -> {
            long id = store.createChat(System.currentTimeMillis());
            main.post(() -> {
                if (id < 0) {
                    chatView.composer().setText(text);
                    return;
                }
                chatId = id;
                prefs().edit().putLong(KEY_CHAT, id).apply();
                submit(id, text);
            });
        });
    }

    private void submit(long id, @NonNull String text) {
        AssistantModelStatus.Snapshot snap = snapshot;
        boolean ready = snap != null && AssistantModelPresenter.canAnswer(snap);
        engine.setModelReady(ready);
        if (ready && !hasWaiting()) {
            if (engine.send(id, text)) noteSending(id, text);
            else chatView.composer().setText(text);
        } else if (!engine.hold(id, text)) {
            chatView.composer().setText(text);
        }
        render();
    }

    private void noteSending(long id, @NonNull String text) {
        if (id != chatId) return;
        List<String> next = new ArrayList<>(sending);
        if (sending.isEmpty()) sendingBase = messages.size();
        next.add(text);
        sending = next;
    }

    /** A question of the open chat is stored as waiting for the model. */
    private boolean hasWaiting() {
        for (AssistantMessage m : messages) {
            if (AssistantMessage.STATE_WAITING.equals(m.state)) return true;
        }
        return false;
    }

    @Override
    public void onStop() {
        engine.stop();
    }

    @Override
    public void retry(long messageId) {
        AssistantMessage m = null;
        for (AssistantMessage candidate : messages) if (candidate.id == messageId) m = candidate;
        AssistantRows.ErrorKind kind = m != null && AssistantMessage.STATE_ERROR.equals(m.state)
            ? AssistantRows.errorKind(m.errorCode) : null;
        boolean needsModel = kind == AssistantRows.ErrorKind.MODEL_STOPPED || kind == AssistantRows.ErrorKind.NOT_LOADED
            || kind == AssistantRows.ErrorKind.MEMORY;
        if (needsModel) {
            status.load(result -> {
                if (!result.ok && isMemory(result)) failures.put(defaultModelId(), AssistantModelPresenter.Failure.NO_MEMORY);
                engine.retry(messageId);
                render();
            });
        } else {
            engine.retry(messageId);
        }
        forceFollow = true;
    }

    @Override
    public void errorAction(@NonNull AssistantRows.Row row) {
        if (row.error != null && AssistantRows.errorAction(row.error) == AssistantRows.ErrorAction.CHOOSE_MODEL) {
            openModelSheet();
        } else {
            retry(row.messageId);
        }
    }

    @Override
    public void useE2b(@NonNull AssistantRows.Row row) {
        status.setDefaultAssistantModel(AssistantModelStatus.MODEL_E2B, r -> {
            failures.remove(AssistantModelStatus.MODEL_E4B);
            status.load(l -> engine.retry(row.messageId));
        });
    }

    @NonNull
    private String defaultModelId() {
        AssistantModelStatus.Snapshot snap = snapshot;
        return snap == null ? AssistantModelStatus.MODEL_E2B : snap.defaultModel.modelId;
    }

    private static boolean isMemory(@NonNull AssistantModelStatus.ActionResult r) {
        String text = (r.code + " " + r.message).toLowerCase(Locale.ROOT);
        return text.contains("memory");
    }

    // -------------------------------------------------------------------- answer actions

    @Override
    public void copy(@NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("", text));
        // Android 13 and later show their own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(page, R.string.assistant_copied, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void share(@NonNull String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(send, null);
        if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooser);
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------------- chats

    @Override
    public void onNewChat() {
        if (chatsOpen) closeChats();
        chatId = -1L;
        messages = Collections.emptyList();
        cutIndex = -1;
        chatTitle = "";
        sending = Collections.emptyList();
        prefs().edit().remove(KEY_CHAT).apply();
        loadSeq++;
        loadRecent();
        render();
    }

    @Override
    public void onOpenRecent(long id) {
        openChat(id);
    }

    @Override
    public void onOpen(long id) {
        if (chatsOpen) closeChats();
        openChat(id);
    }

    private void openChat(long id) {
        chatId = id;
        messages = Collections.emptyList();
        cutIndex = -1;
        sending = Collections.emptyList();
        prefs().edit().putLong(KEY_CHAT, id).apply();
        forceFollow = true;
        reload();
        render();
    }

    @Override
    public void onChats() {
        openChats();
    }

    private void openChats() {
        if (chatsOpen) return;
        chatsOpen = true;
        chatsView.resetSearch();
        loadChats();
        int width = Math.max(page.getWidth(), 1);
        chatsView.setVisibility(View.VISIBLE);
        chatsView.setTranslationX(-width);
        chatsView.setAlpha(0f);
        chatsView.animate().translationX(0f).alpha(1f).setDuration(220L)
            .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                if (chatsOpen) chatView.setVisibility(View.INVISIBLE);
            }).start();
    }

    private void closeChats() {
        if (!chatsOpen) return;
        chatsOpen = false;
        chatView.setVisibility(View.VISIBLE);
        int width = Math.max(page.getWidth(), 1);
        chatsView.animate().translationX(-width).alpha(0f).setDuration(220L)
            .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                if (!chatsOpen) chatsView.setVisibility(View.GONE);
            }).start();
    }

    @Override
    public void onBack() {
        closeChats();
    }

    @Override
    public void onQuery(@NonNull String query) {
        loadChats();
    }

    @Override
    public void onSearchFocused(boolean focused) {
        typingFocusChanged(focused);
    }

    /** Whether back should close the Chats list; the page's host asks first. */
    boolean handleBack() {
        if (!chatsOpen) return false;
        closeChats();
        return true;
    }

    private void loadChats() {
        final String query = chatsView.query();
        final long exclude = pendingDeleteId;
        io.execute(() -> {
            List<AssistantChat> chats = new ArrayList<>(store.listChats(query.isEmpty() ? null : query));
            if (exclude > 0) {
                for (int i = chats.size() - 1; i >= 0; i--) if (chats.get(i).id == exclude) chats.remove(i);
            }
            boolean any = !query.isEmpty() || !chats.isEmpty() ? true : false;
            if (!query.isEmpty()) any = !store.latestChats(1).isEmpty();
            Map<Long, CharSequence> previews = new HashMap<>();
            int limit = Math.min(chats.size(), 200);
            for (int i = 0; i < limit; i++) previews.put(chats.get(i).id, previewOf(chats.get(i).id, query));
            List<AssistantChatGroups.Item> items;
            if (query.isEmpty()) {
                items = AssistantChatGroups.build(chats, System.currentTimeMillis(), TimeZone.getDefault());
            } else {
                items = new ArrayList<>();
                for (AssistantChat chat : chats) items.add(new AssistantChatGroups.Item(null, chat));
            }
            AssistantChatsView.Data data = new AssistantChatsView.Data(items, previews, any, query);
            main.post(() -> {
                if (chatsOpen) chatsView.bind(data, chatId);
            });
        });
    }

    @NonNull
    private CharSequence previewOf(long id, @NonNull String query) {
        List<AssistantMessage> all = store.messages(id);
        if (!query.isEmpty()) {
            for (AssistantMessage m : all) {
                if (m.text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    return AssistantChatsView.highlight(AssistantChatsView.matchLine(m.text, query), query, palette.primary);
                }
            }
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            AssistantMessage m = all.get(i);
            if (!m.isUser() && !m.text.isEmpty()) return AssistantMarkdown.plain(m.text);
        }
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).isUser()) return all.get(i).text;
        return "";
    }

    @Override
    public void onDelete(long id, @NonNull String title) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.assistant_delete_title)
            .setMessage(context.getString(R.string.assistant_delete_body, title))
            .setNegativeButton(R.string.assistant_cancel, null)
            .setPositiveButton(R.string.assistant_delete, (d, w) -> deferDelete(id))
            .show();
    }

    /** The chat leaves the list at once and is removed from storage when the snackbar ends. */
    private void deferDelete(long id) {
        commitPendingDelete();
        pendingDeleteId = id;
        boolean wasCurrent = id == chatId;
        if (wasCurrent) {
            chatId = -1L;
            messages = Collections.emptyList();
            cutIndex = -1;
            chatTitle = "";
            prefs().edit().remove(KEY_CHAT).apply();
            loadSeq++;
        }
        loadChats();
        loadRecent();
        render();
        Snackbar bar = Snackbar.make(page, R.string.assistant_chat_deleted, (int) SNACKBAR_MS);
        bar.setAction(R.string.assistant_undo, v -> {
            if (pendingDeleteId != id) return;
            pendingDeleteId = -1L;
            if (wasCurrent) {
                chatId = id;
                prefs().edit().putLong(KEY_CHAT, id).apply();
                reload();
            }
            loadChats();
            loadRecent();
        });
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION && pendingDeleteId == id) commitPendingDelete();
            }
        });
        deleteSnackbar = bar;
        bar.show();
    }

    private void commitPendingDelete() {
        final long id = pendingDeleteId;
        if (id <= 0) return;
        pendingDeleteId = -1L;
        io.execute(() -> store.deleteChat(id));
        main.post(this::loadRecent);
    }

    // -------------------------------------------------------------------------- model

    @Override
    public void onModelStatus(@NonNull AssistantModelStatus.Snapshot next) {
        AssistantModelStatus.Snapshot previous = snapshot;
        snapshot = next;
        if (previous != null) {
            noteDownloadEnd(previous.gemmaE2b, next.gemmaE2b);
            noteDownloadEnd(previous.gemmaE4b, next.gemmaE4b);
        }
        for (AssistantModelStatus.Entry e : new AssistantModelStatus.Entry[] {next.gemmaE2b, next.gemmaE4b}) {
            if (e.state.kind == AssistantModelStatus.Kind.READY) failures.remove(e.modelId);
        }
        if (sheet != null) sheet.refresh();
        updateChip();
        updateNotice();
        updateAnswerStatus();
        engine.setModelReady(AssistantModelPresenter.canAnswer(next));
    }

    /** A download that stopped without the person cancelling it failed. */
    private void noteDownloadEnd(@NonNull AssistantModelStatus.Entry before, @NonNull AssistantModelStatus.Entry after) {
        if (before.state.kind == AssistantModelStatus.Kind.DOWNLOADING
            && after.state.kind == AssistantModelStatus.Kind.NOT_DOWNLOADED) {
            if (!userCancelled.remove(after.modelId)) {
                failures.put(after.modelId, AssistantModelPresenter.Failure.DOWNLOAD_FAILED);
            }
        }
    }

    private void updateChip() {
        AssistantModelStatus.Snapshot snap = snapshot;
        if (snap == null) {
            chatView.setChip(AssistantModelPresenter.Chip.NO_MODEL, "");
            return;
        }
        chatView.setChip(AssistantModelPresenter.chip(snap),
            context.getString(AssistantModelPresenter.shortName(snap.defaultModel.modelId)));
    }

    private void updateNotice() {
        chatView.notice().show(composerNotice != null ? composerNotice : modelNotice());
    }

    @Nullable
    private AssistantNoticeView.Content modelNotice() {
        AssistantModelStatus.Snapshot snap = snapshot;
        if (snap == null) return null;
        AssistantModelStatus.Entry def = snap.defaultModel;
        AssistantModelPresenter.Failure failure = failures.get(def.modelId);
        if (failure == null) failure = AssistantModelPresenter.Failure.NONE;
        String name = context.getString(AssistantModelPresenter.fullName(def.modelId));
        Runnable choose = this::openModelSheet;
        Runnable tryAgain = () -> {
            failures.remove(def.modelId);
            if (def.state.kind == AssistantModelStatus.Kind.DOWNLOADED) status.load(r -> render());
            else status.download(def.modelId, r -> render());
            updateNotice();
        };
        AssistantModelStatus.Kind kind = def.state.kind;
        if (failure == AssistantModelPresenter.Failure.NO_MEMORY && kind != AssistantModelStatus.Kind.READY) {
            boolean e2bReady = snap.gemmaE2b.state.kind == AssistantModelStatus.Kind.DOWNLOADED
                && !AssistantModelStatus.sameModel(def.modelId, AssistantModelStatus.MODEL_E2B);
            return new AssistantNoticeView.Content(
                context.getString(R.string.assistant_error_memory_named, name),
                context.getString(e2bReady ? R.string.assistant_error_memory_body
                    : R.string.assistant_error_memory_body_plain), -1, true,
                e2bReady ? context.getString(R.string.assistant_use_e2b) : null,
                e2bReady ? () -> {
                    failures.remove(def.modelId);
                    status.setDefaultAssistantModel(AssistantModelStatus.MODEL_E2B, r -> status.load(l -> render()));
                } : null,
                context.getString(R.string.assistant_try_again), tryAgain, false);
        }
        if ((kind == AssistantModelStatus.Kind.NONE || kind == AssistantModelStatus.Kind.NOT_DOWNLOADED)
            && failure == AssistantModelPresenter.Failure.DOWNLOAD_FAILED) {
            return new AssistantNoticeView.Content(context.getString(R.string.assistant_notice_failed_title),
                context.getString(R.string.assistant_notice_failed_body), -1, true,
                context.getString(R.string.assistant_choose_model), choose,
                context.getString(R.string.assistant_try_again), tryAgain, false);
        }
        if ((kind == AssistantModelStatus.Kind.NONE || kind == AssistantModelStatus.Kind.NOT_DOWNLOADED)
            && failure == AssistantModelPresenter.Failure.NO_SPACE) {
            return new AssistantNoticeView.Content(context.getString(R.string.assistant_notice_space_title, name),
                context.getString(R.string.assistant_notice_space_body), -1, true,
                context.getString(R.string.assistant_free_up_space), this::freeUpSpace,
                context.getString(R.string.assistant_try_again), tryAgain, false);
        }
        switch (kind) {
            case NONE:
            case NOT_DOWNLOADED:
                return new AssistantNoticeView.Content(context.getString(R.string.assistant_notice_no_model_title),
                    context.getString(R.string.assistant_notice_no_model_body), -1, false,
                    context.getString(R.string.assistant_choose_model), choose,
                    context.getString(R.string.assistant_download), this::downloadE2b, true);
            case DOWNLOADING: {
                int percent = def.state.percent;
                return new AssistantNoticeView.Content(context.getString(R.string.assistant_notice_downloading_title, name),
                    percent >= 0 ? context.getString(R.string.assistant_notice_percent, percent) : "", Math.max(percent, 0), false,
                    null, null, context.getString(R.string.assistant_cancel), () -> cancelDownload(def.modelId), false);
            }
            case LOADING:
                if (!hasWaiting()) return null;
                return new AssistantNoticeView.Content(context.getString(R.string.assistant_notice_loading_title, name),
                    context.getString(R.string.assistant_notice_loading_body), -1, false, null, null, null, null, false);
            default:
                return null;
        }
    }

    private void downloadE2b() {
        status.setDefaultAssistantModel(AssistantModelStatus.MODEL_E2B,
            r -> status.download(AssistantModelStatus.MODEL_E2B, result -> {
                if (!result.ok) noteFailure(AssistantModelStatus.MODEL_E2B, result);
                render();
            }));
    }

    private void noteFailure(@NonNull String modelId, @NonNull AssistantModelStatus.ActionResult result) {
        String text = (result.code + " " + result.message).toLowerCase(Locale.ROOT);
        failures.put(modelId, text.contains("space") || text.contains("storage") ? AssistantModelPresenter.Failure.NO_SPACE
            : text.contains("memory") ? AssistantModelPresenter.Failure.NO_MEMORY
            : AssistantModelPresenter.Failure.DOWNLOAD_FAILED);
        if (sheet != null) sheet.refresh();
    }

    // ------------------------------------------------------------------- model sheet host

    @Override
    public void onChip() {
        openModelSheet();
    }

    private void openModelSheet() {
        if (sheet != null && sheet.isShowing()) return;
        sheet = new AssistantModelSheet(context, status, palette, this);
        sheet.show();
    }

    @NonNull
    @Override
    public Map<String, AssistantModelPresenter.Failure> failures() {
        return failures;
    }

    @Override
    public void choose(@NonNull String modelId) {
        status.setDefaultAssistantModel(modelId, r -> {
            failures.remove(modelId);
            status.load(l -> {
                if (!l.ok && isMemory(l)) failures.put(modelId, AssistantModelPresenter.Failure.NO_MEMORY);
                if (sheet != null) sheet.refresh();
                render();
            });
        });
    }

    @Override
    public void download(@NonNull String modelId) {
        failures.remove(modelId);
        status.download(modelId, r -> {
            if (!r.ok) noteFailure(modelId, r);
            render();
        });
    }

    @Override
    public void cancelDownload(@NonNull String modelId) {
        userCancelled.add(modelId);
        status.cancelDownload(modelId, null);
    }

    @Override
    public void unload() {
        status.unload(null);
    }

    @Override
    public void loadAgain(@NonNull String modelId) {
        failures.remove(modelId);
        status.load(l -> {
            if (!l.ok && isMemory(l)) failures.put(modelId, AssistantModelPresenter.Failure.NO_MEMORY);
            if (sheet != null) sheet.refresh();
            render();
        });
    }

    @Override
    public void freeUpSpace() {
        Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void dismissed() {
        sheet = null;
    }

    // -------------------------------------------------------------------------- dictation

    @Override
    public void onFieldFocused(boolean focused) {
        typingFocusChanged(focused);
    }

    /**
     * One of the page's fields took or lost focus: the launcher's keyboard comes up for it and
     * takes the keys, or the claim is recomputed without it. The phone's keyboard never opens here.
     */
    private void typingFocusChanged(boolean focused) {
        Activity activity = activity();
        if (activity instanceof TermuxActivity) ((TermuxActivity) activity).onAssistantTypingFocus(focused);
    }

    @Nullable
    private Activity activity() {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    @Override
    public void onMic() {
        if (dictation.isActive()) return;
        composerNotice = null;
        AssistantDictation.Start result = dictation.start(new AssistantDictation.Callback() {
            @Override public void onListening() {
                chatView.composer().setListening(true);
                hideKeyboard();
            }

            @Override public void onLevel(float level) {
                chatView.composer().setLevel(level);
            }

            @Override public void onText(@NonNull String heard) {
                chatView.composer().setListeningText(heard);
            }

            @Override public void onFinished(@NonNull String text, boolean cancelled, boolean failed) {
                chatView.composer().setListening(false);
                if (cancelled) return;
                if (text.isEmpty()) {
                    if (!failed) Snackbar.make(page, R.string.assistant_didnt_catch, Snackbar.LENGTH_SHORT).show();
                    return;
                }
                chatView.composer().insertDictation(text);
            }
        });
        if (result == AssistantDictation.Start.NO_PERMISSION) showMicNotice();
        else if (result == AssistantDictation.Start.NO_SPEECH_MODEL) showSpeechNotice();
        updateNotice();
    }

    private void showMicNotice() {
        Activity activity = activity();
        boolean asked = prefs().getBoolean(KEY_MIC_ASKED, false);
        boolean rationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity,
            Manifest.permission.RECORD_AUDIO);
        Runnable notNow = () -> {
            composerNotice = null;
            updateNotice();
        };
        if (!asked || rationale) {
            composerNotice = new AssistantNoticeView.Content(context.getString(R.string.assistant_mic_title),
                context.getString(R.string.assistant_mic_body), -1, false,
                context.getString(R.string.assistant_not_now), notNow,
                context.getString(R.string.assistant_mic_allow), () -> {
                    prefs().edit().putBoolean(KEY_MIC_ASKED, true).apply();
                    Activity a = activity();
                    if (a == null) return;
                    micRequestPending = true;
                    ActivityCompat.requestPermissions(a, new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
                }, false);
        } else {
            composerNotice = new AssistantNoticeView.Content(context.getString(R.string.assistant_mic_off_title),
                context.getString(R.string.assistant_mic_off_body), -1, false,
                context.getString(R.string.assistant_not_now), notNow,
                context.getString(R.string.assistant_open_settings), () -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.getPackageName(), null));
                    if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                    } catch (RuntimeException ignored) {
                    }
                }, false);
        }
    }

    private void showSpeechNotice() {
        composerNotice = new AssistantNoticeView.Content(context.getString(R.string.assistant_speech_title),
            context.getString(R.string.assistant_speech_body), -1, false,
            context.getString(R.string.assistant_not_now), () -> {
                composerNotice = null;
                updateNotice();
            },
            context.getString(R.string.assistant_speech_get), () -> {
                Intent intent = SettingsActivity.createFragmentIntent(context, TaiModelCentreFragment.class,
                    R.string.tai_model_centre_title);
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (RuntimeException ignored) {
                }
                composerNotice = null;
                updateNotice();
            }, false);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        View field = chatView.composer().field();
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
    }

    @Override
    public void onCancelDictation() {
        dictation.cancel();
    }

    @Override
    public void onDoneDictation() {
        dictation.stop(true);
    }
}
