package com.termux.app.assistant;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.List;

/**
 * The chat half of the page (sheet 01.0): the top bar, the thread with its empty state and Jump to
 * latest, the notice and the composer, top to bottom. The composer is pinned to the bottom; the
 * thread takes what is left and keeps its newest message at the bottom.
 */
final class AssistantChatView extends LinearLayout {

    interface Listener {
        void onChats();

        void onNewChat();

        void onChip();

        void onOpenRecent(long chatId);
    }

    /** A card of the empty state's Recent chats rail. */
    static final class Recent {
        final long id;
        @NonNull final String title;
        @NonNull final String when;

        Recent(long id, @NonNull String title, @NonNull String when) {
            this.id = id;
            this.title = title;
            this.when = when;
        }
    }

    private final ImageButton chatsButton;
    private final TextView title;
    private final TextView chip;
    private final ImageButton newChat;
    private final RecyclerView thread;
    private final LinearLayoutManager layout;
    private final AssistantThreadAdapter adapter;
    private final FrameLayout threadFrame;
    private final LinearLayout empty;
    private final TextView heading;
    private final TextView railLabel;
    private final HorizontalScrollView rail;
    private final LinearLayout railRow;
    private final ImageButton jump;
    private final AssistantNoticeView notice;
    private final AssistantComposer composer;
    private final LinearLayout answerStatus;
    private final ProgressBar answerSpinner;
    private final TextView answerStatusText;
    private AssistantModelPresenter.Stage answerStage = AssistantModelPresenter.Stage.NONE;
    private String answerModelName = "";
    private AssistantUi.Palette palette;
    @Nullable private Listener listener;
    private AssistantModelPresenter.Chip chipState = AssistantModelPresenter.Chip.NO_MODEL;
    private String chipName = "";
    private List<Recent> recent = java.util.Collections.emptyList();

    AssistantChatView(@NonNull Context context, @NonNull AssistantUi.Palette palette,
                      @NonNull AssistantThreadAdapter.Actions actions) {
        super(context);
        this.palette = palette;
        setOrientation(VERTICAL);
        int size = AssistantUi.dp(context, 48);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int barPad = AssistantUi.dp(context, 4);
        bar.setPadding(barPad, barPad, barPad, barPad);
        chatsButton = AssistantUi.iconButton(context, R.drawable.ic_assistant_chats, R.string.assistant_chats_button, 0, 24f);
        bar.addView(chatsButton, new LayoutParams(size, size));
        title = AssistantUi.text(context);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(AssistantUi.dp(context, 4), 0, AssistantUi.dp(context, 4), 0);
        bar.addView(title, tp);
        FrameLayout chipTouch = new FrameLayout(context);
        chip = AssistantUi.text(context);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setSingleLine(true);
        chip.setCompoundDrawablePadding(AssistantUi.dp(context, 6));
        chip.setPaddingRelative(AssistantUi.dp(context, 12), 0, AssistantUi.dp(context, 6), 0);
        chip.setMinHeight(AssistantUi.dp(context, 32));
        chipTouch.addView(chip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            AssistantUi.dp(context, 32), Gravity.CENTER));
        chipTouch.setMinimumHeight(size);
        chipTouch.setClickable(true);
        bar.addView(chipTouch, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size));
        newChat = AssistantUi.iconButton(context, R.drawable.ic_assistant_add, R.string.assistant_new_chat, 0, 24f);
        bar.addView(newChat, new LayoutParams(size, size));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AssistantUi.dp(context, 56)));

        threadFrame = new FrameLayout(context);
        adapter = new AssistantThreadAdapter(actions, palette);
        thread = new RecyclerView(context);
        layout = new LinearLayoutManager(context);
        layout.setStackFromEnd(true);
        thread.setLayoutManager(layout);
        thread.setAdapter(adapter);
        thread.setItemAnimator(null);
        thread.addItemDecoration(new AssistantThreadAdapter.Gap(AssistantUi.dp(context, 24)));
        thread.setClipToPadding(false);
        thread.setPadding(AssistantUi.dp(context, 16), AssistantUi.dp(context, 8), AssistantUi.dp(context, 16),
            AssistantUi.dp(context, 16));
        thread.setOverScrollMode(OVER_SCROLL_NEVER);
        threadFrame.addView(thread, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        empty = new LinearLayout(context);
        empty.setOrientation(VERTICAL);
        heading = AssistantUi.text(context);
        heading.setText(R.string.assistant_empty_heading);
        int m20 = AssistantUi.dp(context, 20);
        LayoutParams hp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(m20, 0, m20, AssistantUi.dp(context, 24));
        empty.addView(heading, hp);
        railLabel = AssistantUi.text(context);
        railLabel.setText(R.string.assistant_recent_chats);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AssistantUi.dp(context, 20));
        lp.setMargins(m20, 0, m20, AssistantUi.dp(context, 8));
        empty.addView(railLabel, lp);
        rail = new HorizontalScrollView(context);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        railRow = new LinearLayout(context);
        railRow.setOrientation(HORIZONTAL);
        railRow.setPadding(AssistantUi.dp(context, 16), 0, AssistantUi.dp(context, 16), 0);
        rail.addView(railRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        empty.addView(rail, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        ep.bottomMargin = AssistantUi.dp(context, 16);
        threadFrame.addView(empty, ep);
        empty.setVisibility(GONE);

        jump = AssistantUi.iconButton(context, R.drawable.ic_assistant_down, R.string.assistant_jump_latest, 0, 24f);
        jump.setVisibility(GONE);
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        jp.bottomMargin = AssistantUi.dp(context, 8);
        threadFrame.addView(jump, jp);
        addView(threadFrame, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int inset = AssistantUi.dp(context, 8);
        notice = new AssistantNoticeView(context, palette);
        LayoutParams np = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMargins(inset, 0, inset, inset);
        addView(notice, np);
        answerStatus = new LinearLayout(context);
        answerStatus.setOrientation(LinearLayout.HORIZONTAL);
        answerStatus.setGravity(Gravity.CENTER_VERTICAL);
        answerSpinner = new ProgressBar(context);
        answerSpinner.setIndeterminate(true);
        int spin = AssistantUi.dp(context, 14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(spin, spin);
        sp.setMarginEnd(AssistantUi.dp(context, 8));
        answerStatus.addView(answerSpinner, sp);
        answerStatusText = AssistantUi.text(context);
        answerStatusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        answerStatus.addView(answerStatusText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        answerStatus.setVisibility(GONE);
        LayoutParams ap = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(inset * 2, 0, inset * 2, AssistantUi.dp(context, 6));
        addView(answerStatus, ap);
        composer = new AssistantComposer(context, palette);
        LayoutParams cp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(inset, 0, inset, inset);
        addView(composer, cp);

        chatsButton.setOnClickListener(v -> {
            if (listener != null) listener.onChats();
        });
        newChat.setOnClickListener(v -> {
            if (listener != null) listener.onNewChat();
        });
        chipTouch.setOnClickListener(v -> {
            if (listener != null) listener.onChip();
        });
        jump.setOnClickListener(v -> {
            int last = adapter.getItemCount() - 1;
            if (last >= 0) thread.smoothScrollToPosition(last);
            AssistantUi.setVisible(jump, false);
        });
        thread.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateJump();
            }
        });
        applyPalette(palette);
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @NonNull AssistantThreadAdapter adapter() { return adapter; }

    @NonNull RecyclerView thread() { return thread; }

    @NonNull AssistantComposer composer() { return composer; }

    @NonNull AssistantNoticeView notice() { return notice; }

    /** The one line above the composer naming the stage of an answer in progress; gone when there is none. */
    void setAnswerStatus(@NonNull AssistantModelPresenter.Stage stage, @NonNull String modelName) {
        boolean sameName = stage != AssistantModelPresenter.Stage.LOADING_MODEL || answerModelName.equals(modelName);
        if (stage == answerStage && sameName) return;
        answerStage = stage;
        answerModelName = modelName;
        if (stage == AssistantModelPresenter.Stage.NONE) {
            AssistantUi.setVisible(answerStatus, false);
            return;
        }
        Context c = getContext();
        String line;
        switch (stage) {
            case LOADING_MODEL: line = c.getString(R.string.assistant_status_loading, modelName); break;
            case THINKING: line = c.getString(R.string.assistant_status_thinking); break;
            default: line = c.getString(R.string.assistant_status_responding); break;
        }
        AssistantUi.setText(answerStatusText, line);
        AssistantUi.setVisible(answerStatus, true);
    }

    void applyPalette(@NonNull AssistantUi.Palette p) {
        palette = p;
        AssistantUi.tint(chatsButton, p.onSurface);
        AssistantUi.tint(newChat, newChat.isEnabled() ? p.onSurface : AssistantUi.disabledInk(p));
        AssistantUi.type(title, 18f, 24f, true, p.onSurface);
        AssistantUi.type(heading, 28f, 34f, false, p.onSurface);
        AssistantUi.labelSmall(railLabel, p.onSurfaceVariant);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(p.surfaceHighest);
        AssistantUi.tint(jump, p.onSecondaryContainer);
        GradientDrawable disc = AssistantUi.disc(p.secondaryContainer);
        disc.setStroke(AssistantUi.dp(this, 1), p.outlineVariant);
        disc.setSize(AssistantUi.dp(this, 40), AssistantUi.dp(this, 40));
        jump.setBackground(new android.graphics.drawable.InsetDrawable(disc, AssistantUi.dp(this, 4)));
        adapter.setPalette(p);
        composer.applyPalette(p);
        notice.setPalette(p);
        AssistantUi.bodySmall(answerStatusText, p.onSurfaceVariant);
        answerSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(p.primary));
        applyChip();
        setRecent(recent);
    }

    void setTitle(@NonNull String text) {
        AssistantUi.setText(title, text);
    }

    void setNewChatEnabled(boolean enabled) {
        newChat.setEnabled(enabled);
        AssistantUi.tint(newChat, enabled ? palette.onSurface : AssistantUi.disabledInk(palette));
    }

    void showEmpty(boolean show) {
        AssistantUi.setVisible(empty, show);
    }

    // ------------------------------------------------------------------------------- chip

    void setChip(@NonNull AssistantModelPresenter.Chip state, @NonNull String name) {
        if (chipState == state && chipName.equals(name)) return;
        chipState = state;
        chipName = name;
        applyChip();
    }

    private void applyChip() {
        Context context = getContext();
        AssistantUi.labelSmall(chip, palette.onSurface);
        boolean none = chipState == AssistantModelPresenter.Chip.NO_MODEL;
        GradientDrawable bg = AssistantUi.fill(none ? 0 : palette.surfaceHighest, AssistantUi.dp(this, 16));
        if (none) bg.setStroke(AssistantUi.dp(this, 1), palette.outline);
        chip.setBackground(bg);
        chip.setText(none ? context.getString(R.string.assistant_chip_no_model) : chipName);
        chip.setTextColor(none ? palette.onSurfaceVariant : palette.onSurface);
        android.graphics.drawable.Drawable start = null;
        int d8 = AssistantUi.dp(this, 8);
        switch (chipState) {
            case READY: {
                GradientDrawable dot = AssistantUi.disc(palette.success);
                dot.setSize(d8, d8);
                start = dot;
                break;
            }
            case LOADING: {
                GradientDrawable r = new GradientDrawable();
                r.setShape(GradientDrawable.OVAL);
                r.setStroke(AssistantUi.dp(this, 2), palette.primary);
                r.setSize(AssistantUi.dp(this, 14), AssistantUi.dp(this, 14));
                start = r;
                break;
            }
            case NOT_LOADED: {
                GradientDrawable r = new GradientDrawable();
                r.setShape(GradientDrawable.OVAL);
                r.setStroke(Math.max(1, AssistantUi.dp(this, 1)), palette.onSurfaceVariant);
                r.setSize(d8, d8);
                start = r;
                break;
            }
            default:
                break;
        }
        android.graphics.drawable.Drawable chevron = none ? null
            : androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_assistant_chevron);
        if (chevron != null) {
            chevron = chevron.mutate();
            androidx.core.graphics.drawable.DrawableCompat.setTint(chevron, palette.onSurfaceVariant);
            chevron.setBounds(0, 0, AssistantUi.dp(this, 18), AssistantUi.dp(this, 18));
        }
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, chevron, null);
        chip.setPaddingRelative(AssistantUi.dp(this, 12), 0, AssistantUi.dp(this, none ? 12 : 6), 0);
        int descRes;
        switch (chipState) {
            case READY: descRes = R.string.assistant_chip_ready; break;
            case LOADING: descRes = R.string.assistant_chip_loading; break;
            case NOT_LOADED: descRes = R.string.assistant_chip_not_loaded; break;
            default: descRes = 0; break;
        }
        chip.setContentDescription(descRes == 0 ? context.getString(R.string.assistant_chip_none)
            : context.getString(descRes, chipName));
    }

    // ------------------------------------------------------------------------------ recent

    void setRecent(@NonNull List<Recent> cards) {
        recent = cards;
        Context context = getContext();
        railRow.removeAllViews();
        AssistantUi.setVisible(railLabel, !cards.isEmpty());
        AssistantUi.setVisible(rail, !cards.isEmpty());
        for (Recent card : cards) {
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(VERTICAL);
            box.setPadding(AssistantUi.dp(context, 14), AssistantUi.dp(context, 12), AssistantUi.dp(context, 14),
                AssistantUi.dp(context, 12));
            box.setMinimumHeight(AssistantUi.dp(context, 72));
            box.setBackground(AssistantUi.fill(palette.surfaceHigh, AssistantUi.dp(context, 16)));
            TextView t = AssistantUi.text(context);
            AssistantUi.labelSmall(t, palette.onSurface);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setText(card.title);
            TextView w = AssistantUi.text(context);
            AssistantUi.meta(w, palette.onSurfaceVariant);
            w.setText(card.when);
            box.addView(t);
            LayoutParams wp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wp.topMargin = AssistantUi.dp(context, 8);
            box.addView(w, wp);
            box.setContentDescription(card.title + ", " + card.when);
            box.setOnClickListener(v -> {
                if (listener != null) listener.onOpenRecent(card.id);
            });
            LayoutParams bp = new LayoutParams(AssistantUi.dp(context, 152), ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMarginEnd(AssistantUi.dp(context, 8));
            railRow.addView(box, bp);
        }
    }

    // ------------------------------------------------------------------------------ scroll

    /** Whether the thread is showing its newest message (or has nothing to scroll). */
    boolean atBottom() {
        return !thread.canScrollVertically(1);
    }

    void scrollToEnd() {
        int last = adapter.getItemCount() - 1;
        if (last >= 0) thread.scrollToPosition(last);
        AssistantUi.setVisible(jump, false);
    }

    /** Jump to latest shows once the newest message is more than a screen of thread below. */
    void updateJump() {
        int range = thread.computeVerticalScrollRange();
        int offset = thread.computeVerticalScrollOffset();
        int extent = thread.computeVerticalScrollExtent();
        AssistantUi.setVisible(jump, extent > 0 && range - offset - extent > extent);
    }
}
