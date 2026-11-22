package com.termux.app.assistant;

import android.content.Context;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.List;

/**
 * Draws an answer's markdown blocks as views in a column (sheet 01.6). The column's children are
 * reused block by block while an answer streams, so a new word costs a text update rather than a
 * rebuild. Each code block is its own view so it scrolls sideways and has its own Copy.
 */
final class AssistantMarkdownViews {

    /** What a code block's Copy does. */
    interface CodeCopy {
        void copy(@NonNull String code);
    }

    private static final int GAP_BLOCK_DP = 12;
    private static final int GAP_ITEM_DP = 4;
    private static final int INDENT_DP = 24;
    private static final long COPIED_MS = 2000L;

    private AssistantMarkdownViews() {
    }

    /** Typeface for code: Maple Mono if the launcher has loaded it, else the system monospace. */
    @NonNull
    static Typeface mono() {
        return Typeface.MONOSPACE;
    }

    /** Shows {@code blocks} in {@code column}, reusing its children where the kinds line up. */
    static void bind(@NonNull LinearLayout column, @NonNull List<AssistantMarkdown.Block> blocks,
                     @NonNull AssistantUi.Palette palette, @NonNull CodeCopy copy) {
        Context context = column.getContext();
        AssistantMarkdown.Style style = new AssistantMarkdown.Style(palette.surfaceHighest, palette.onSurface,
            palette.primary, mono());
        for (int i = 0; i < blocks.size(); i++) {
            AssistantMarkdown.Block block = blocks.get(i);
            View existing = i < column.getChildCount() ? column.getChildAt(i) : null;
            Object tag = existing == null ? null : existing.getTag(R.id.assistant_block_kind);
            if (existing == null || tag != block.kind) {
                View made = make(context, block, palette);
                made.setTag(R.id.assistant_block_kind, block.kind);
                if (existing != null) column.removeViewAt(i);
                column.addView(made, i, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                existing = made;
            }
            int gap = i == 0 ? 0 : AssistantUi.dp(context,
                block.kind == AssistantMarkdown.Kind.LIST_ITEM
                    && blocks.get(i - 1).kind == AssistantMarkdown.Kind.LIST_ITEM ? GAP_ITEM_DP : GAP_BLOCK_DP);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) existing.getLayoutParams();
            if (params.topMargin != gap) {
                params.topMargin = gap;
                existing.setLayoutParams(params);
            }
            update(existing, block, palette, style, copy);
        }
        while (column.getChildCount() > blocks.size()) column.removeViewAt(column.getChildCount() - 1);
    }

    @NonNull
    private static View make(@NonNull Context context, @NonNull AssistantMarkdown.Block block,
                             @NonNull AssistantUi.Palette p) {
        switch (block.kind) {
            case LIST_ITEM: {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView marker = AssistantUi.text(context);
                AssistantUi.body(marker, p.onSurfaceVariant);
                marker.setGravity(Gravity.END);
                row.addView(marker, new LinearLayout.LayoutParams(AssistantUi.dp(context, 20),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView text = AssistantUi.text(context);
                AssistantUi.body(text, p.onSurface);
                text.setMovementMethod(LinkMovementMethod.getInstance());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart(AssistantUi.dp(context, 8));
                row.addView(text, lp);
                return row;
            }
            case CODE:
                return makeCode(context, p);
            default: {
                TextView text = AssistantUi.text(context);
                if (block.kind == AssistantMarkdown.Kind.HEADING) AssistantUi.label(text, p.onSurface);
                else AssistantUi.body(text, p.onSurface);
                text.setMovementMethod(LinkMovementMethod.getInstance());
                return text;
            }
        }
    }

    private static void update(@NonNull View view, @NonNull AssistantMarkdown.Block block,
                               @NonNull AssistantUi.Palette p, @NonNull AssistantMarkdown.Style style,
                               @NonNull CodeCopy copy) {
        switch (block.kind) {
            case LIST_ITEM: {
                LinearLayout row = (LinearLayout) view;
                int indent = AssistantUi.dp(view, INDENT_DP * block.level);
                if (row.getPaddingStart() != indent) row.setPaddingRelative(indent, 0, 0, 0);
                TextView marker = (TextView) row.getChildAt(0);
                marker.setTextColor(p.onSurfaceVariant);
                AssistantUi.setText(marker, block.marker);
                TextView text = (TextView) row.getChildAt(1);
                text.setTextColor(p.onSurface);
                text.setText(AssistantMarkdown.inline(block.text, style));
                break;
            }
            case CODE:
                updateCode((LinearLayout) view, block, p, copy);
                break;
            default: {
                TextView text = (TextView) view;
                text.setTextColor(p.onSurface);
                text.setText(AssistantMarkdown.inline(block.text, style));
                break;
            }
        }
    }

    // ---------------------------------------------------------------------------- code block

    private static final class CodeState {
        String code = "";
        Runnable restore;
    }

    @NonNull
    private static LinearLayout makeCode(@NonNull Context context, @NonNull AssistantUi.Palette p) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        CodeState state = new CodeState();
        box.setTag(R.id.assistant_code_state, state);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(AssistantUi.dp(context, 16), 0, AssistantUi.dp(context, 4), 0);
        TextView lang = AssistantUi.text(context);
        AssistantUi.meta(lang, p.onSurfaceVariant);
        lang.setSingleLine(true);
        header.addView(lang, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copy = AssistantUi.text(context);
        AssistantUi.labelSmall(copy, p.onSurface);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_assistant_copy, 0, 0, 0);
        copy.setCompoundDrawablePadding(AssistantUi.dp(context, 6));
        copy.setMinHeight(AssistantUi.dp(context, 48));
        copy.setPaddingRelative(AssistantUi.dp(context, 8), 0, AssistantUi.dp(context, 8), 0);
        copy.setBackground(AssistantUi.rippleBackground(context));
        copy.setContentDescription(context.getString(R.string.assistant_copy));
        header.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            AssistantUi.dp(context, 48)));
        box.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            AssistantUi.dp(context, 40)));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(AssistantUi.dp(context, 28));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        TextView code = AssistantUi.text(context);
        AssistantUi.type(code, 13f, 20f, false, p.onSurface);
        code.setTypeface(mono());
        code.setHorizontallyScrolling(true);
        code.setPaddingRelative(AssistantUi.dp(context, 16), 0, AssistantUi.dp(context, 16), AssistantUi.dp(context, 14));
        scroll.addView(code, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static void updateCode(@NonNull LinearLayout box, @NonNull AssistantMarkdown.Block block,
                                   @NonNull AssistantUi.Palette p, @NonNull CodeCopy copyAction) {
        CodeState state = (CodeState) box.getTag(R.id.assistant_code_state);
        Context context = box.getContext();
        box.setBackground(AssistantUi.fill(p.surfaceHighest, AssistantUi.dp(context, 12)));
        LinearLayout header = (LinearLayout) box.getChildAt(0);
        TextView lang = (TextView) header.getChildAt(0);
        lang.setTextColor(p.onSurfaceVariant);
        AssistantUi.setText(lang, block.lang.isEmpty() ? context.getString(R.string.assistant_code) : block.lang);
        TextView copy = (TextView) header.getChildAt(1);
        copy.setTextColor(p.onSurface);
        state.code = block.text;
        if (copy.getTag(R.id.assistant_code_state) == null) {
            copy.setTag(R.id.assistant_code_state, state);
            AssistantUi.setText(copy, context.getString(R.string.assistant_copy));
            copy.setOnClickListener(v -> {
                copyAction.copy(state.code);
                copy.setText(R.string.assistant_copied);
                if (state.restore != null) copy.removeCallbacks(state.restore);
                state.restore = () -> copy.setText(R.string.assistant_copy);
                copy.postDelayed(state.restore, COPIED_MS);
            });
        }
        HorizontalScrollView scroll = (HorizontalScrollView) box.getChildAt(1);
        TextView code = (TextView) scroll.getChildAt(0);
        code.setTextColor(p.onSurface);
        if (!code.getText().toString().equals(block.text)) code.setText(block.text);
    }

    /** The view of a code block's copy label, for tests and the row that rebinds it. */
    @Nullable
    static TextView copyLabel(@NonNull View codeBlock) {
        if (!(codeBlock instanceof LinearLayout)) return null;
        View header = ((LinearLayout) codeBlock).getChildAt(0);
        return header instanceof LinearLayout ? (TextView) ((LinearLayout) header).getChildAt(1) : null;
    }
}
