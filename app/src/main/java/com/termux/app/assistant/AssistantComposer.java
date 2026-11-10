package com.termux.app.assistant;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The composer (sheet 02): attach (wave 2, kept out of the layout until then), the field, the mic
 * and Send, or Stop while an answer streams. It grows to six lines (three in landscape) and then
 * scrolls inside. While dictating, the Listening row takes its place at the same height.
 */
final class AssistantComposer extends FrameLayout {

    interface Listener {
        void onSend(@NonNull String text);

        void onStop();

        void onMic();

        void onCancelDictation();

        void onDoneDictation();

        /** The field took (true) or lost (false) focus, or was tapped: the launcher's keyboard follows. */
        void onFieldFocused(boolean focused);
    }

    private static final int MAX_LINES = 6;
    private static final int MAX_LINES_LANDSCAPE = 3;

    private final LinearLayout idleRow;
    private final LinearLayout listeningRow;
    private final ImageButton attach;
    private final EditText field;
    private final ImageButton mic;
    private final ImageButton send;
    private final ImageButton cancel;
    private final LevelBars bars;
    private final TextView listeningText;
    private final TextView done;
    private final GradientDrawable shape = new GradientDrawable();
    private AssistantUi.Palette palette;
    private boolean generating;
    private boolean listening;
    @Nullable private Listener listener;

    AssistantComposer(@NonNull Context context, @NonNull AssistantUi.Palette palette) {
        super(context);
        this.palette = palette;
        setMinimumHeight(AssistantUi.dp(context, 56));
        setBackground(shape);
        setClipToOutline(false);
        int pad = AssistantUi.dp(context, 4);
        setPadding(pad, pad, pad, pad);

        idleRow = new LinearLayout(context);
        idleRow.setOrientation(LinearLayout.HORIZONTAL);
        idleRow.setGravity(Gravity.BOTTOM);
        int size = AssistantUi.dp(context, 48);

        // Wave 2 puts the attach button first; until then it is not in the way.
        attach = AssistantUi.iconButton(context, R.drawable.ic_assistant_attach, R.string.assistant_attach, 0, 24f);
        attach.setVisibility(GONE);
        idleRow.addView(attach, new LinearLayout.LayoutParams(size, size));

        field = new EditText(context);
        field.setBackground(null);
        field.setHint(R.string.assistant_composer_hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        // The launcher's own keyboard types into the field (AssistantFieldKeys); the phone's
        // never opens for it. The caret, selection handles and long-press stay the field's own.
        field.setShowSoftInputOnFocus(false);
        field.setMinHeight(size);
        field.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        field.setVerticalScrollBarEnabled(true);
        field.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        field.setHorizontallyScrolling(false);
        int fieldH = AssistantUi.dp(context, 12);
        int fieldV = AssistantUi.dp(context, 12);
        field.setPadding(fieldH + AssistantUi.dp(context, 4), fieldV, fieldH, fieldV);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        idleRow.addView(field, fp);

        mic = AssistantUi.iconButton(context, R.drawable.ic_assistant_mic, R.string.assistant_dictate, 0, 24f);
        idleRow.addView(mic, new LinearLayout.LayoutParams(size, size));
        send = AssistantUi.iconButton(context, R.drawable.ic_assistant_send, R.string.assistant_send, 0, 20f);
        send.setBackground(null);
        idleRow.addView(send, new LinearLayout.LayoutParams(size, size));
        addView(idleRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        listeningRow = new LinearLayout(context);
        listeningRow.setOrientation(LinearLayout.HORIZONTAL);
        listeningRow.setGravity(Gravity.CENTER_VERTICAL);
        listeningRow.setVisibility(GONE);
        cancel = AssistantUi.iconButton(context, R.drawable.ic_assistant_close, R.string.assistant_cancel_dictation, 0, 24f);
        listeningRow.addView(cancel, new LinearLayout.LayoutParams(size, size));
        bars = new LevelBars(context);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(AssistantUi.dp(context, 27),
            AssistantUi.dp(context, 24));
        bp.setMarginEnd(AssistantUi.dp(context, 12));
        listeningRow.addView(bars, bp);
        listeningText = AssistantUi.text(context);
        listeningText.setSingleLine(true);
        listeningText.setEllipsize(android.text.TextUtils.TruncateAt.START);
        listeningRow.addView(listeningText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        done = AssistantUi.text(context);
        done.setText(R.string.assistant_done);
        done.setGravity(Gravity.CENTER);
        done.setMinHeight(AssistantUi.dp(context, 40));
        done.setPadding(AssistantUi.dp(context, 16), 0, AssistantUi.dp(context, 16), 0);
        done.setContentDescription(context.getString(R.string.assistant_done_description));
        done.setClickable(true);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            AssistantUi.dp(context, 40));
        dp.setMarginEnd(AssistantUi.dp(context, 4));
        listeningRow.addView(done, dp);
        addView(listeningRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size));

        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                refreshSend();
            }
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            refreshShape(hasFocus);
            if (listener != null) listener.onFieldFocused(hasFocus);
        });
        field.setOnClickListener(v -> {
            if (listener != null) listener.onFieldFocused(true);
        });
        send.setOnClickListener(v -> {
            if (listener == null) return;
            if (generating) {
                listener.onStop();
                return;
            }
            submit();
        });
        mic.setOnClickListener(v -> {
            if (listener != null) listener.onMic();
        });
        cancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancelDictation();
        });
        done.setOnClickListener(v -> {
            if (listener != null) listener.onDoneDictation();
        });
        applyPalette(palette);
        applyLineCap(context.getResources().getConfiguration());
    }

    /** Sends the field's text when it is not blank and no answer streams: what Send does, and Enter. */
    void submit() {
        if (listener == null || generating) return;
        String text = text();
        if (!text.trim().isEmpty()) listener.onSend(text);
    }

    /** Whether the field holds the focus, which is when the launcher's keyboard types into it. */
    boolean isTyping() {
        return field.hasFocus();
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    void applyPalette(@NonNull AssistantUi.Palette p) {
        palette = p;
        AssistantUi.body(field, p.onSurface);
        field.setHintTextColor(p.onSurfaceVariant);
        field.setHighlightColor(androidx.core.graphics.ColorUtils.setAlphaComponent(p.primary, 90));
        AssistantUi.tint(attach, p.onSurfaceVariant);
        AssistantUi.tint(mic, p.onSurfaceVariant);
        AssistantUi.tint(cancel, p.onSurface);
        AssistantUi.body(listeningText, p.onSurfaceVariant);
        AssistantUi.labelSmall(done, p.onSecondaryContainer);
        done.setBackground(AssistantUi.fill(p.secondaryContainer, AssistantUi.dp(this, 20)));
        bars.setColor(p.primary);
        refreshShape(field.hasFocus());
        refreshSend();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLineCap(newConfig);
    }

    private void applyLineCap(@NonNull Configuration config) {
        field.setMaxLines(config.orientation == Configuration.ORIENTATION_LANDSCAPE
            ? MAX_LINES_LANDSCAPE : MAX_LINES);
    }

    /** Corner 28; the focus ring is drawn inside the 56 so nothing shifts. */
    private void refreshShape(boolean focused) {
        shape.setColor(palette.surfaceHigh);
        shape.setCornerRadius(AssistantUi.dp(this, 28));
        shape.setStroke(focused ? AssistantUi.dp(this, 2) : 0, palette.primary);
    }

    private void refreshSend() {
        boolean active = generating || !text().trim().isEmpty();
        send.setEnabled(active);
        int fill = active ? palette.primary : AssistantUi.disabledFill(palette);
        int ink = active ? palette.onPrimary : AssistantUi.disabledInk(palette);
        int disc = AssistantUi.dp(this, 40);
        android.graphics.drawable.GradientDrawable d = AssistantUi.disc(fill);
        d.setSize(disc, disc);
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(d,
            AssistantUi.dp(this, 4));
        send.setBackground(inset);
        AssistantUi.tint(send, ink);
        send.setImageResource(generating ? R.drawable.ic_assistant_stop : R.drawable.ic_assistant_send);
        send.setContentDescription(getContext().getString(generating ? R.string.assistant_stop
            : R.string.assistant_send));
    }

    /** Send becomes Stop while an answer streams. */
    void setGenerating(boolean generating) {
        if (this.generating == generating) return;
        this.generating = generating;
        refreshSend();
    }

    @NonNull
    String text() {
        return field.getText().toString();
    }

    void clearText() {
        field.setText("");
    }

    void setText(@NonNull String text) {
        field.setText(text);
        field.setSelection(field.getText().length());
    }

    @NonNull
    EditText field() {
        return field;
    }

    /** Dictated text goes in at the caret, or after the text with one space; the caret ends after it. */
    void insertDictation(@NonNull String dictated) {
        String text = dictated.trim();
        if (text.isEmpty()) return;
        Editable current = field.getText();
        int start = Math.max(0, field.getSelectionStart());
        int end = Math.max(start, field.getSelectionEnd());
        boolean atEnd = start >= current.length();
        if (current.length() > 0 && atEnd && !Character.isWhitespace(current.charAt(current.length() - 1))) {
            text = " " + text;
        }
        current.replace(start, end, text);
        field.setSelection(Math.min(current.length(), start + text.length()));
        field.requestFocus();
    }

    // -------------------------------------------------------------------------- listening

    boolean isListening() {
        return listening;
    }

    void setListening(boolean on) {
        if (listening == on) return;
        listening = on;
        idleRow.setVisibility(on ? INVISIBLE : VISIBLE);
        listeningRow.setVisibility(on ? VISIBLE : GONE);
        if (on) {
            listeningText.setText(R.string.assistant_listening);
            bars.setLevel(0f);
        }
        // Same height both ways, so nothing moves under the thread.
        if (on) setMinimumHeight(Math.max(getMinimumHeight(), AssistantUi.dp(this, 56)));
    }

    void setListeningText(@NonNull String text) {
        listeningText.setText(text.isEmpty() ? getContext().getString(R.string.assistant_listening) : text);
    }

    /** The microphone level in [0, 1]; bars are redrawn only when their heights change. */
    void setLevel(float level) {
        bars.setLevel(level);
    }

    // ------------------------------------------------------------------------- level bars

    /** Five bars, 3 wide with 3 between, 4 to 24 tall, following the level. No idle motion. */
    private static final class LevelBars extends View {
        private static final int COUNT = 5;
        private static final float[] SHAPE = {0.6f, 0.85f, 1f, 0.8f, 0.55f};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] heights = new int[COUNT];
        private float smoothed;
        private final float density;

        LevelBars(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            for (int i = 0; i < COUNT; i++) heights[i] = Math.round(4 * density);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        void setLevel(float level) {
            float target = Math.max(0f, Math.min(1f, level));
            // Rises quickly and settles slowly, so speech reads as movement and silence as rest.
            smoothed = target > smoothed ? smoothed + (target - smoothed) * 0.6f : smoothed + (target - smoothed) * 0.25f;
            boolean changed = false;
            for (int i = 0; i < COUNT; i++) {
                int h = Math.round((4f + 20f * smoothed * SHAPE[i]) * density);
                if (h != heights[i]) {
                    heights[i] = h;
                    changed = true;
                }
            }
            if (changed) invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float barW = 3 * density;
            float gap = 3 * density;
            float total = COUNT * barW + (COUNT - 1) * gap;
            float x = (getWidth() - total) / 2f;
            float mid = getHeight() / 2f;
            for (int i = 0; i < COUNT; i++) {
                float h = heights[i];
                rect.set(x, mid - h / 2f, x + barW, mid + h / 2f);
                canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
                x += barW + gap;
            }
        }
    }
}
