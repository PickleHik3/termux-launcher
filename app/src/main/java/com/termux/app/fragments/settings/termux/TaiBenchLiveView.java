package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * The Run screen's live view (spec Screen 4): the prompt in grey, the streaming reply in
 * monospace with the newest line kept at the bottom, and a token counter with the running
 * tok/s. It is the voice pill's {@code VoiceTranscriptPanel} in shape, without that panel's
 * as-heard/cleaned versions, typewriter reveal and Undo/Copy/Insert pill, none of which a
 * benchmark reply has a use for; the reply is set whole on every token event, at the harness's
 * 20 Hz, so a typewriter would only lag it.
 */
final class TaiBenchLiveView extends LinearLayout {
    private static final int REPLY_LINES = 6;

    private final TextView prompt;
    private final TextView reply;
    private final TextView counter;

    TaiBenchLiveView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        int variant = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);

        prompt = new TextView(context);
        prompt.setTextColor(variant);
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        prompt.setMaxLines(3);
        prompt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(prompt, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        reply = new TextView(context);
        reply.setTypeface(Typeface.MONOSPACE);
        reply.setTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        reply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        reply.setLineSpacing(0f, 1.1f);
        reply.setMaxLines(REPLY_LINES);
        // Bottom gravity so the newest line sits at the bottom and older ones scroll up under the fade.
        reply.setGravity(Gravity.BOTTOM | Gravity.START);
        reply.setVerticalScrollBarEnabled(false);
        reply.setVerticalFadingEdgeEnabled(true);
        reply.setFadingEdgeLength(TaiBenchViews.dp(context, 14));
        LayoutParams replyParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        replyParams.topMargin = TaiBenchViews.dp(context, 8);
        addView(reply, replyParams);

        counter = new TextView(context);
        counter.setTypeface(Typeface.MONOSPACE);
        counter.setTextColor(variant);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        LayoutParams counterParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        counterParams.topMargin = TaiBenchViews.dp(context, 6);
        addView(counter, counterParams);
    }

    /** Draws the live state; an inactive one shows the last reply dimmed with a still counter. */
    void bind(@NonNull TaiBenchRunState.Live live) {
        Context context = getContext();
        String promptText = live.prompt.isEmpty() ? context.getString(R.string.tai_bench_live_idle) : live.prompt;
        if (!promptText.contentEquals(prompt.getText())) prompt.setText(promptText);
        String replyText = live.reply.toString();
        if (!replyText.contentEquals(reply.getText())) {
            reply.setText(replyText);
            scrollToEnd();
        }
        reply.setAlpha(live.active ? 1f : 0.6f);
        reply.setVisibility(replyText.isEmpty() ? GONE : VISIBLE);
        String count = live.tokens <= 0 ? ""
            : live.tps > 0.0 ? context.getString(R.string.tai_bench_live_counter_tps, live.tokens, TaiBenchViews.tps(context, live.tps))
            : context.getString(R.string.tai_bench_live_counter, live.tokens);
        if (live.runs > 1 && !count.isEmpty()) count = context.getString(R.string.tai_bench_live_run, live.run, live.runs) + " · " + count;
        if (!count.contentEquals(counter.getText())) counter.setText(count);
        counter.setVisibility(count.isEmpty() ? GONE : VISIBLE);
    }

    private void scrollToEnd() {
        reply.post(() -> {
            Layout layout = reply.getLayout();
            if (layout == null) return;
            int box = reply.getHeight() - reply.getCompoundPaddingTop() - reply.getCompoundPaddingBottom();
            reply.scrollTo(0, Math.max(0, layout.getHeight() - box));
        });
    }

    /** Whether anything is on show; an empty view folds to nothing. */
    boolean hasContent() {
        return reply.getVisibility() == View.VISIBLE || counter.getVisibility() == View.VISIBLE;
    }
}
