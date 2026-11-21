package com.termux.app.assistant;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The Chats list (sheet 05): back, title, New chat, a search field, and the chats in day groups. It
 * is a second child of the page's pane and slides over the chat, so it keeps the pane's glass.
 */
final class AssistantChatsView extends FrameLayout {

    interface Listener {
        void onBack();

        void onNewChat();

        void onOpen(long chatId);

        void onDelete(long chatId, @NonNull String title);

        void onQuery(@NonNull String query);
    }

    /** What the list shows; built off the main thread. */
    static final class Data {
        @NonNull final List<AssistantChatGroups.Item> items;
        @NonNull final Map<Long, CharSequence> previews;
        final boolean anyChats;
        @NonNull final String query;

        Data(@NonNull List<AssistantChatGroups.Item> items, @NonNull Map<Long, CharSequence> previews,
             boolean anyChats, @NonNull String query) {
            this.items = items;
            this.previews = previews;
            this.anyChats = anyChats;
            this.query = query;
        }
    }

    private static final long SEARCH_DEBOUNCE_MS = 150L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ImageButton back;
    private final TextView title;
    private final ImageButton newChat;
    private final LinearLayout searchBox;
    private final EditText search;
    private final ImageButton clear;
    private final RecyclerView list;
    private final LinearLayout empty;
    private final TextView emptyTitle;
    private final TextView emptyBody;
    private final MaterialButton emptyNew;
    private final ListAdapter adapter = new ListAdapter();
    private AssistantUi.Palette palette;
    @Nullable private Listener listener;
    @Nullable private Data data;
    private long currentChatId = -1;
    private boolean suppressQuery;
    private final Runnable queryRunnable = new Runnable() {
        @Override public void run() {
            if (listener != null) listener.onQuery(search.getText().toString().trim());
        }
    };

    AssistantChatsView(@NonNull Context context, @NonNull AssistantUi.Palette palette) {
        super(context);
        this.palette = palette;
        setClickable(true);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int size = AssistantUi.dp(context, 48);
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int barPad = AssistantUi.dp(context, 4);
        bar.setPadding(barPad, barPad, barPad, barPad);
        back = AssistantUi.iconButton(context, R.drawable.ic_assistant_back, R.string.assistant_back, 0, 24f);
        bar.addView(back, new LinearLayout.LayoutParams(size, size));
        title = AssistantUi.text(context);
        title.setText(R.string.assistant_chats_title);
        title.setSingleLine(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(AssistantUi.dp(context, 4));
        bar.addView(title, tp);
        newChat = AssistantUi.iconButton(context, R.drawable.ic_assistant_add, R.string.assistant_new_chat, 0, 24f);
        bar.addView(newChat, new LinearLayout.LayoutParams(size, size));
        column.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            AssistantUi.dp(context, 56)));

        searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPaddingRelative(AssistantUi.dp(context, 16), 0, AssistantUi.dp(context, 4), 0);
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_assistant_search);
        searchIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(AssistantUi.dp(context, 24), AssistantUi.dp(context, 24)));
        search = new EditText(context);
        search.setBackground(null);
        search.setHint(R.string.assistant_search_hint);
        search.setSingleLine(true);
        search.setShowSoftInputOnFocus(false);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setPaddingRelative(AssistantUi.dp(context, 12), 0, 0, 0);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        clear = AssistantUi.iconButton(context, R.drawable.ic_assistant_close, R.string.assistant_search_clear, 0, 20f);
        clear.setVisibility(GONE);
        searchBox.addView(clear, new LinearLayout.LayoutParams(size, size));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
        int inset = AssistantUi.dp(context, 12);
        sp.setMargins(inset, AssistantUi.dp(context, 8), inset, 0);
        column.addView(searchBox, sp);

        FrameLayout body = new FrameLayout(context);
        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AssistantUi.dp(context, 8));
        body.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(AssistantUi.dp(context, 32), 0, AssistantUi.dp(context, 32), 0);
        emptyTitle = AssistantUi.text(context);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyBody = AssistantUi.text(context);
        emptyBody.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = AssistantUi.dp(context, 8);
        empty.addView(emptyBody, bp);
        emptyNew = new MaterialButton(context);
        emptyNew.setText(R.string.assistant_new_chat);
        emptyNew.setMinHeight(AssistantUi.dp(context, 40));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = AssistantUi.dp(context, 16);
        empty.addView(emptyNew, np);
        empty.setVisibility(GONE);
        body.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        back.setOnClickListener(v -> {
            if (listener != null) listener.onBack();
        });
        newChat.setOnClickListener(v -> {
            if (listener != null) listener.onNewChat();
        });
        emptyNew.setOnClickListener(v -> {
            if (listener != null) listener.onNewChat();
        });
        clear.setOnClickListener(v -> search.setText(""));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                if (suppressQuery) return;
                main.removeCallbacks(queryRunnable);
                main.postDelayed(queryRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
        search.setOnFocusChangeListener((v, focused) -> {
            if (listener instanceof ImeListener) ((ImeListener) listener).onSearchFocused(focused);
        });
        applyPalette(palette);
    }

    /** A listener that also hears of the search field taking focus, for the keyboard gate. */
    interface ImeListener extends Listener {
        void onSearchFocused(boolean focused);
    }

    /** The search field. It is typed into with the launcher's keyboard, never the phone's. */
    @NonNull EditText search() { return search; }

    /** Whether the search field is the one holding focus on screen. */
    boolean isSearchTyping() {
        return search.hasFocus() && isShown();
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    void applyPalette(@NonNull AssistantUi.Palette p) {
        palette = p;
        AssistantUi.tint(back, p.onSurface);
        AssistantUi.tint(newChat, p.onSurface);
        AssistantUi.tint(clear, p.onSurfaceVariant);
        AssistantUi.type(title, 22f, 28f, false, p.onSurface);
        AssistantUi.body(search, p.onSurface);
        search.setHintTextColor(p.onSurfaceVariant);
        searchBox.setBackground(AssistantUi.fill(p.surfaceHigh, AssistantUi.dp(this, 24)));
        ((ImageView) searchBox.getChildAt(0)).setColorFilter(p.onSurfaceVariant);
        emptyNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(p.secondaryContainer));
        emptyNew.setTextColor(p.onSecondaryContainer);
        adapter.notifyDataSetChanged();
        if (data != null) bind(data, currentChatId);
    }

    void resetSearch() {
        suppressQuery = true;
        search.setText("");
        suppressQuery = false;
        search.clearFocus();
    }

    @NonNull
    String query() {
        return search.getText().toString().trim();
    }

    void bind(@NonNull Data next, long currentChat) {
        data = next;
        currentChatId = currentChat;
        adapter.items = next.items;
        adapter.notifyDataSetChanged();
        boolean hasRows = !next.items.isEmpty();
        boolean searching = !next.query.isEmpty();
        AssistantUi.setVisible(searchBox, next.anyChats);
        AssistantUi.setVisible(list, hasRows);
        AssistantUi.setVisible(empty, !hasRows);
        if (!hasRows) {
            Context context = getContext();
            AssistantUi.label(emptyTitle, palette.onSurface);
            AssistantUi.bodySmall(emptyBody, palette.onSurfaceVariant);
            if (searching) {
                String q = next.query.length() > 24 ? next.query.substring(0, 24) + "…" : next.query;
                emptyTitle.setText(context.getString(R.string.assistant_no_results_title, q));
                emptyBody.setText(R.string.assistant_no_results_body);
            } else {
                emptyTitle.setText(R.string.assistant_no_chats_title);
                emptyBody.setText(R.string.assistant_no_chats_body);
            }
            AssistantUi.setVisible(emptyNew, !searching);
        }
    }

    // ---------------------------------------------------------------------------- adapter

    private final class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<AssistantChatGroups.Item> items = Collections.emptyList();

        @Override public int getItemCount() {
            return items.size();
        }

        @Override public int getItemViewType(int position) {
            return items.get(position).header != null ? 0 : 1;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == 0) {
                TextView header = AssistantUi.text(context);
                header.setGravity(Gravity.CENTER_VERTICAL);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    AssistantUi.dp(context, 40));
                lp.setMargins(AssistantUi.dp(context, 20), AssistantUi.dp(context, 8), AssistantUi.dp(context, 20), 0);
                header.setLayoutParams(lp);
                return new RecyclerView.ViewHolder(header) { };
            }
            return new RowHolder(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AssistantChatGroups.Item item = items.get(position);
            if (item.header != null) {
                TextView header = (TextView) holder.itemView;
                AssistantUi.labelSmall(header, palette.primary);
                header.setText(item.header.label);
            } else if (item.chat != null && holder instanceof RowHolder) {
                ((RowHolder) holder).bind(item.chat);
            }
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView time;
        private final TextView preview;
        private AssistantChat chat;

        RowHolder(@NonNull Context context) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.VERTICAL);
            int h = AssistantUi.dp(context, 16);
            int v = AssistantUi.dp(context, 12);
            row.setPadding(h, v, h, v);
            row.setMinimumHeight(AssistantUi.dp(context, 72));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            int side = AssistantUi.dp(context, 4);
            lp.setMargins(side, 0, side, 0);
            row.setLayoutParams(lp);
            LinearLayout top = new LinearLayout(context);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            titleView = AssistantUi.text(context);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            time = AssistantUi.text(context);
            time.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMarginStart(AssistantUi.dp(context, 12));
            top.addView(time, tp);
            row.addView(top);
            preview = AssistantUi.text(context);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v1 -> {
                if (chat != null && listener != null) listener.onOpen(chat.id);
            });
            row.setOnLongClickListener(v1 -> {
                if (chat == null) return false;
                v1.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                PopupMenu menu = new PopupMenu(v1.getContext(), v1, Gravity.END);
                menu.getMenu().add(R.string.assistant_delete);
                final AssistantChat target = chat;
                menu.setOnMenuItemClickListener(item -> {
                    if (listener != null) listener.onDelete(target.id, displayTitle(target));
                    return true;
                });
                menu.show();
                return true;
            });
        }

        void bind(@NonNull AssistantChat c) {
            chat = c;
            Data d = data;
            boolean current = c.id == currentChatId;
            Context context = itemView.getContext();
            itemView.setBackground(current ? AssistantUi.fill(palette.secondaryContainer, AssistantUi.dp(context, 16)) : null);
            int ink = current ? palette.onSecondaryContainer : palette.onSurface;
            int inkVariant = current ? palette.onSecondaryContainer : palette.onSurfaceVariant;
            AssistantUi.label(titleView, ink);
            AssistantUi.meta(time, inkVariant);
            AssistantUi.bodySmall(preview, inkVariant);
            String query = d == null ? "" : d.query;
            titleView.setText(highlight(displayTitle(c), query));
            time.setText(AssistantChatGroups.timeLabel(c.updatedMs, System.currentTimeMillis(), TimeZone.getDefault(),
                Locale.getDefault(), android.text.format.DateFormat.is24HourFormat(context)));
            CharSequence text = d == null ? null : d.previews.get(c.id);
            preview.setText(text == null ? "" : text);
            String description = displayTitle(c) + ", " + time.getText() + (text == null ? "" : ", " + text);
            itemView.setContentDescription(current
                ? description + ", " + context.getString(R.string.assistant_current_chat) : description);
        }

        @NonNull
        private CharSequence highlight(@NonNull String text, @NonNull String query) {
            return AssistantChatsView.highlight(text, query, palette.primary);
        }
    }

    @NonNull
    private String displayTitle(@NonNull AssistantChat chat) {
        return chat.title.isEmpty() ? getContext().getString(R.string.assistant_chat_title_new) : chat.title;
    }

    /** The first match of {@code query} in {@code text} in primary and semibold; case-insensitive. */
    @NonNull
    static CharSequence highlight(@NonNull String text, @NonNull String query, int color) {
        if (query.isEmpty()) return text;
        int at = text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (at < 0) return text;
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(color), at, at + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), at, at + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** A preview line for a search result: the line holding the match, with "…" before it when cut. */
    @NonNull
    static String matchLine(@NonNull String text, @NonNull String query) {
        String plain = AssistantMarkdown.plain(text);
        int at = plain.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (at < 0) return plain;
        int start = Math.max(0, at - 24);
        return (start > 0 ? "…" : "") + plain.substring(start);
    }

    @NonNull
    static List<AssistantChat> copy(@NonNull List<AssistantChat> chats) {
        return new ArrayList<>(chats);
    }
}
