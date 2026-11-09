package com.termux.app.assistant;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The thread's rows (sheet 01): your message, an answer with its actions, the status lines, the
 * error row and the divider. An answer streaming in changes only its own row's text; rows that did
 * not change are not touched, so earlier answers are never rebuilt.
 */
final class AssistantThreadAdapter extends RecyclerView.Adapter<AssistantThreadAdapter.Holder> {

    /** What the row's buttons do. */
    interface Actions {
        void copy(@NonNull String text);

        void share(@NonNull String text);

        void retry(long messageId);

        void errorAction(@NonNull AssistantRows.Row row);

        void useE2b(@NonNull AssistantRows.Row row);
    }

    private static final float USER_MAX_FRACTION = 0.84f;

    private final Actions actions;
    private AssistantUi.Palette palette;
    private List<AssistantRows.Row> rows = new ArrayList<>();

    AssistantThreadAdapter(@NonNull Actions actions, @NonNull AssistantUi.Palette palette) {
        this.actions = actions;
        this.palette = palette;
        setHasStableIds(true);
    }

    void setPalette(@NonNull AssistantUi.Palette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    @NonNull
    List<AssistantRows.Row> rows() {
        return Collections.unmodifiableList(rows);
    }

    /** Shows {@code next}; only the rows that differ are told. */
    void submit(@NonNull List<AssistantRows.Row> next) {
        List<AssistantRows.Row> old = rows;
        rows = next;
        boolean sameShape = old.size() == next.size();
        for (int i = 0; sameShape && i < next.size(); i++) {
            if (!old.get(i).key.equals(next.get(i).key)) sameShape = false;
        }
        if (!sameShape) {
            notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < next.size(); i++) {
            if (!old.get(i).equals(next.get(i))) notifyItemChanged(i);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).key.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type.ordinal();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        AssistantRows.Type type = AssistantRows.Type.values()[viewType];
        switch (type) {
            case USER: return new UserHolder(context);
            case ANSWER: return new AnswerHolder(context, actions);
            case WORKING: return new StatusHolder(context, false);
            case WAITING: return new StatusHolder(context, true);
            case ERROR: return new ErrorHolder(context, actions);
            default: return new CutoffHolder(context);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position), palette);
    }

    abstract static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
            itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        abstract void bind(@NonNull AssistantRows.Row row, @NonNull AssistantUi.Palette p);
    }

    /** Spacing: 24 between messages. */
    static final class Gap extends RecyclerView.ItemDecoration {
        private final int gapPx;

        Gap(int gapPx) {
            this.gapPx = gapPx;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            outRect.top = parent.getChildAdapterPosition(view) == 0 ? 0 : gapPx;
        }
    }

    // ------------------------------------------------------------------------------- you

    /** A text view that never grows wider than a fraction of the width it is offered. */
    private static final class CappedTextView extends TextView {
        CappedTextView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int offered = MeasureSpec.getSize(widthMeasureSpec);
            int cap = Math.round(offered * USER_MAX_FRACTION);
            int mode = MeasureSpec.getMode(widthMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST), heightMeasureSpec);
        }
    }

    private static final class UserHolder extends Holder {
        private final TextView text;

        UserHolder(@NonNull Context context) {
            super(new FrameLayout(context));
            text = new CappedTextView(context);
            AssistantUi.body(text, 0);
            int h = AssistantUi.dp(context, 16);
            int v = AssistantUi.dp(context, 10);
            text.setPadding(h, v, h, v);
            text.setTextIsSelectable(false);
            ((FrameLayout) itemView).addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));
        }

        @Override
        void bind(@NonNull AssistantRows.Row row, @NonNull AssistantUi.Palette p) {
            GradientDrawable bubble = new GradientDrawable();
            float big = AssistantUi.dp(itemView, 20);
            float small = AssistantUi.dp(itemView, 6);
            // Top-left, top-right, bottom-right, bottom-left: 20 20 6 20.
            bubble.setCornerRadii(new float[] {big, big, big, big, small, small, big, big});
            bubble.setColor(p.primaryContainer);
            text.setBackground(bubble);
            text.setTextColor(p.onPrimaryContainer);
            text.setText(row.text);
            text.setContentDescription(itemView.getContext().getString(R.string.assistant_you, row.text));
        }
    }

    // ----------------------------------------------------------------------------- answer

    private static final class AnswerHolder extends Holder {
        private final LinearLayout column;
        private final LinearLayout actionsRow;
        private final TextView stopped;
        private final ImageButton copy;
        private final ImageButton share;
        private final ImageButton retry;
        private final Actions actions;
        private AssistantRows.Row row;

        AnswerHolder(@NonNull Context context, @NonNull Actions actions) {
            super(new LinearLayout(context));
            this.actions = actions;
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            root.addView(column, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            actionsRow = new LinearLayout(context);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);
            stopped = AssistantUi.text(context);
            stopped.setText(R.string.assistant_stopped);
            stopped.setPaddingRelative(AssistantUi.dp(context, 14), 0, AssistantUi.dp(context, 6), 0);
            actionsRow.addView(stopped, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            copy = AssistantUi.iconButton(context, R.drawable.ic_assistant_copy, R.string.assistant_copy, 0, 20f);
            share = AssistantUi.iconButton(context, R.drawable.ic_assistant_share, R.string.assistant_share, 0, 20f);
            retry = AssistantUi.iconButton(context, R.drawable.ic_assistant_refresh, R.string.assistant_try_again, 0, 20f);
            int size = AssistantUi.dp(context, 48);
            actionsRow.addView(copy, new LinearLayout.LayoutParams(size, size));
            actionsRow.addView(share, new LinearLayout.LayoutParams(size, size));
            actionsRow.addView(retry, new LinearLayout.LayoutParams(size, size));
            root.addView(actionsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            copy.setOnClickListener(v -> {
                if (row != null) actions.copy(row.text);
            });
            share.setOnClickListener(v -> {
                if (row != null) actions.share(row.text);
            });
            retry.setOnClickListener(v -> {
                if (row != null) actions.retry(row.messageId);
            });
        }

        @Override
        void bind(@NonNull AssistantRows.Row r, @NonNull AssistantUi.Palette p) {
            row = r;
            AssistantUi.setVisible(column, !r.text.isEmpty());
            if (!r.text.isEmpty()) {
                AssistantMarkdownViews.bind(column, AssistantMarkdown.parse(r.text), p,
                    code -> actions.copy(code));
            }
            boolean any = !r.streaming && (r.stopped || r.copyShare || r.retry);
            AssistantUi.setVisible(actionsRow, any);
            AssistantUi.meta(stopped, p.onSurfaceVariant);
            AssistantUi.setVisible(stopped, r.stopped);
            AssistantUi.setVisible(copy, r.copyShare);
            AssistantUi.setVisible(share, r.copyShare);
            AssistantUi.setVisible(retry, r.retry);
            AssistantUi.tint(copy, p.onSurfaceVariant);
            AssistantUi.tint(share, p.onSurfaceVariant);
            AssistantUi.tint(retry, p.onSurfaceVariant);
            itemView.setContentDescription(null);
            itemView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        }
    }

    // ------------------------------------------------------------------------------ status

    private static final class StatusHolder extends Holder {
        private final TextView text;
        private final boolean waiting;

        StatusHolder(@NonNull Context context, boolean waiting) {
            super(new FrameLayout(context));
            this.waiting = waiting;
            text = AssistantUi.text(context);
            text.setText(waiting ? R.string.assistant_waiting : R.string.assistant_working);
            ((FrameLayout) itemView).addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                AssistantUi.dp(context, 20), waiting ? Gravity.END : Gravity.START));
        }

        @Override
        void bind(@NonNull AssistantRows.Row row, @NonNull AssistantUi.Palette p) {
            AssistantUi.bodySmall(text, p.onSurfaceVariant);
        }
    }

    // ------------------------------------------------------------------------------- error

    private static final class ErrorHolder extends Holder {
        private final LinearLayout card;
        private final ImageView icon;
        private final TextView title;
        private final TextView body;
        private final MaterialButton main;
        private final MaterialButton alt;
        private final Actions actions;
        private AssistantRows.Row row;

        ErrorHolder(@NonNull Context context, @NonNull Actions actions) {
            super(new FrameLayout(context));
            this.actions = actions;
            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            int h = AssistantUi.dp(context, 16);
            int v = AssistantUi.dp(context, 12);
            card.setPadding(h, v, h, v);
            icon = new ImageView(context);
            icon.setImageResource(R.drawable.ic_assistant_error);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(AssistantUi.dp(context, 20),
                AssistantUi.dp(context, 20));
            ip.setMarginEnd(AssistantUi.dp(context, 10));
            ip.topMargin = AssistantUi.dp(context, 0);
            card.addView(icon, ip);
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            title = AssistantUi.text(context);
            body = AssistantUi.text(context);
            col.addView(title);
            col.addView(body);
            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.START);
            main = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            alt = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            buttons.addView(main);
            LinearLayout.LayoutParams altParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            altParams.setMarginStart(AssistantUi.dp(context, 8));
            buttons.addView(alt, altParams);
            col.addView(buttons);
            card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ((FrameLayout) itemView).addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            main.setOnClickListener(v1 -> {
                if (row != null) actions.errorAction(row);
            });
            alt.setOnClickListener(v1 -> {
                if (row != null) actions.useE2b(row);
            });
        }

        @Override
        void bind(@NonNull AssistantRows.Row r, @NonNull AssistantUi.Palette p) {
            row = r;
            Context context = itemView.getContext();
            AssistantRows.ErrorKind kind = r.error == null ? AssistantRows.ErrorKind.OTHER : r.error;
            card.setBackground(AssistantUi.fill(p.errorContainer, AssistantUi.dp(context, 16)));
            AssistantUi.bodySmall(title, p.onErrorContainer);
            AssistantUi.bodySmall(body, p.onErrorContainer);
            title.setText(AssistantRows.errorTitle(kind));
            int bodyRes = AssistantRows.errorBody(kind, r.offerE2b);
            AssistantUi.setVisible(body, bodyRes != 0);
            if (bodyRes != 0) body.setText(bodyRes);
            icon.setColorFilter(p.onErrorContainer);
            AssistantRows.ErrorAction action = AssistantRows.errorAction(kind);
            main.setText(action == AssistantRows.ErrorAction.CHOOSE_MODEL ? R.string.assistant_choose_model
                : R.string.assistant_try_again);
            main.setTextColor(p.onErrorContainer);
            main.setStrokeColor(android.content.res.ColorStateList.valueOf(p.onErrorContainer));
            AssistantUi.setVisible(main, r.retry || action == AssistantRows.ErrorAction.CHOOSE_MODEL);
            alt.setText(R.string.assistant_use_e2b);
            alt.setTextColor(p.onErrorContainer);
            alt.setStrokeColor(android.content.res.ColorStateList.valueOf(p.onErrorContainer));
            AssistantUi.setVisible(alt, r.offerE2b && r.retry);
        }
    }

    // ------------------------------------------------------------------------------ cut-off

    private static final class CutoffHolder extends Holder {
        private final View left;
        private final View right;
        private final TextView text;

        CutoffHolder(@NonNull Context context) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            left = new View(context);
            right = new View(context);
            text = AssistantUi.text(context);
            text.setText(R.string.assistant_cutoff);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            text.setMaxWidth(AssistantUi.dp(context, 290));
            int gap = AssistantUi.dp(context, 12);
            int min = AssistantUi.dp(context, 12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMarginEnd(gap);
            row.addView(left, lp);
            row.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, 1, 1f);
            rp.setMarginStart(gap);
            row.addView(right, rp);
            left.setMinimumWidth(min);
            right.setMinimumWidth(min);
        }

        @Override
        void bind(@NonNull AssistantRows.Row row, @NonNull AssistantUi.Palette p) {
            AssistantUi.meta(text, p.onSurfaceVariant);
            left.setBackgroundColor(p.outlineVariant);
            right.setBackgroundColor(p.outlineVariant);
        }
    }
}
