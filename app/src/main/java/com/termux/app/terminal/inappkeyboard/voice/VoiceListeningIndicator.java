package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;

/**
 * The dictation pill (agreed design, 2026-09-27): top right, under the status bar, 36 dp tall
 * with 6 dp above and below its contents, and a panel that grows down from it over the pane's
 * right side. It lives in the window's content frame rather than the accessory stack, so it never
 * takes part in the keyboard's geometry passes; its position is re-read from the pane area
 * ({@code terminal_surface_host}) on every level update, which is cheap and follows bars that move.
 *
 * <p>The pill row holds the scrolling waveform ({@link VoiceWaveformView}), the state
 * ("Listening…", "Transcribing…", "Cleaning up…"), a "Warming up" chip with a ring while the
 * speech model, the voiced decision or the cleanup model is still loading — recording has already
 * started — and the ×. The panel ({@link VoiceTranscriptPanel}) holds the whole session's text.
 *
 * <p>× stops listening and still delivers every phrase waiting to transcribe; swiping the pill
 * sideways discards them. Both are the host's to act on through {@link Callbacks}.
 */
public final class VoiceListeningIndicator {

    /** Main thread. */
    public interface Callbacks {
        /** The ×: stop listening, deliver what is waiting (or close a finished pill). */
        void onClose();

        /** Swiped away: discard what has not been delivered, close at once. */
        void onSwipedAway();

        /** The panel's Replace, offered when the cleanup could not swap the line itself. */
        void onReplace();

        /** The panel's Copy. */
        void onCopy();
    }

    private static final int PILL_HEIGHT_DP = 36;
    private static final int MAX_WIDTH_DP = 300;
    private static final int GAP_DP = 8;

    private final Activity activity;
    /** The pane area: the pill sits at its top right, under whatever bars are above it. */
    private final View anchor;
    @Nullable private SwipeCard card;
    @Nullable private TextView status;
    @Nullable private View chip;
    @Nullable private VoiceWaveformView wave;
    @Nullable private VoiceTranscriptPanel panel;
    private int lastTop = -1;
    private int lastEnd = -1;
    /** Captured segments still transcribing; the shimmer line shows while any are. */
    private int pending;
    private boolean cleaningUp;

    public VoiceListeningIndicator(@NonNull Activity activity, @NonNull View anchor) {
        this.activity = activity;
        this.anchor = anchor;
    }

    /** @param dimRaw whether a cleanup pass will follow, so the raw text shows dim until it lands. */
    public void show(@NonNull Callbacks callbacks, boolean dimRaw) {
        if (card != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        Context context = activity;
        int onSurface = themeColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
        int accent = themeColor(context, com.termux.shared.R.attr.termuxColorPrimary);
        int surface = themeColor(context, com.termux.shared.R.attr.termuxColorSurfaceBase);

        SwipeCard view = new SwipeCard(context, () -> callbacks.onSwipedAway());
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(PILL_HEIGHT_DP / 2));
        view.setBackground(background);
        view.setElevation(dp(4));
        view.setClipToOutline(true);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(12), 0, 0, 0);

        VoiceWaveformView levels = new VoiceWaveformView(context, accent, onSurface);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(dp(56), dp(16));
        waveParams.setMarginEnd(dp(10));
        row.addView(levels, waveParams);

        TextView label = new TextView(context);
        label.setTextColor(onSurface);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setText(R.string.voice_input_listening);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View warming = warmingChip(context, onSurface, accent);
        warming.setVisibility(View.GONE);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMarginStart(dp(6));
        row.addView(warming, chipParams);

        // The pill's full height as the tap target around an 18 dp glyph.
        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.ic_symbol_close);
        close.setColorFilter(onSurface, PorterDuff.Mode.SRC_IN);
        close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        close.setContentDescription(context.getString(R.string.voice_input_close));
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            close.setBackgroundResource(ripple.resourceId);
        }
        close.setOnClickListener(v -> callbacks.onClose());
        row.addView(close, new LinearLayout.LayoutParams(dp(PILL_HEIGHT_DP), dp(PILL_HEIGHT_DP)));

        view.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(PILL_HEIGHT_DP)));

        VoiceTranscriptPanel transcript = new VoiceTranscriptPanel(context, onSurface, accent,
            new VoiceTranscriptPanel.Actions() {
                @Override
                public void onReplace() {
                    callbacks.onReplace();
                }

                @Override
                public void onCopy() {
                    callbacks.onCopy();
                }
            });
        transcript.setDimRaw(dimRaw);
        transcript.setPaddingRelative(dp(14), 0, dp(12), dp(10));
        transcript.setVisibility(View.GONE);
        view.addView(transcript, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width(content),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        int[] place = place(content);
        params.topMargin = place[0];
        params.setMarginEnd(place[1]);
        lastTop = place[0];
        lastEnd = place[1];
        content.addView(view, params);
        card = view;
        status = label;
        chip = warming;
        wave = levels;
        panel = transcript;
        pending = 0;
        cleaningUp = false;
    }

    public void hide() {
        SwipeCard view = card;
        if (panel != null) panel.release();
        card = null;
        status = null;
        chip = null;
        wave = null;
        panel = null;
        lastTop = -1;
        lastEnd = -1;
        pending = 0;
        cleaningUp = false;
        if (view == null) return;
        view.animate().cancel();
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
    }

    public boolean isShowing() {
        return card != null;
    }

    /** A level sample with the VAD's noise floor at that moment; also keeps the pill in place as bars move. */
    public void setLevel(float rms, boolean voiced, float noiseFloor) {
        VoiceWaveformView levels = wave;
        if (levels == null) return;
        levels.push(rms, voiced, noiseFloor);
        reposition();
    }

    /** The "Warming up" chip, with its ring, while a model or the voiced decision is still loading. */
    public void setWarmingUp(boolean warming) {
        View view = chip;
        if (view != null) view.setVisibility(warming ? View.VISIBLE : View.GONE);
    }

    public void setListening() {
        setStatus(R.string.voice_input_listening);
    }

    /** The mic has closed but a captured segment is still transcribing: "Listening…" no longer fits. */
    public void setTranscribing() {
        setStatus(R.string.voice_input_transcribing);
    }

    public void setStatus(@StringRes int text) {
        TextView view = status;
        if (view != null) view.setText(text);
    }

    /** The VAD has closed a segment: a shimmer line stands in for it until it transcribes. */
    public void onSegmentCaptured() {
        pending++;
        updateShimmer();
    }

    /** A captured segment has come back, with text or without. */
    public void onSegmentSettled() {
        pending = Math.max(0, pending - 1);
        updateShimmer();
    }

    /** One phrase, exactly as typed, into the panel. */
    public void appendTranscript(@NonNull String typed) {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.append(typed);
        updatePanelVisibility();
    }

    /** The session has ended and the one cleanup pass is running. */
    public void setCleaningUp() {
        cleaningUp = true;
        setWarmingUp(false);
        setStatus(R.string.voice_input_cleaning_up);
        updateShimmer();
    }

    /**
     * The cleanup has landed; see {@link VoiceTranscriptPanel#showCleaned}.
     *
     * @param swapped whether the line in the terminal was already swapped for {@code cleaned}
     */
    public void showCleaned(@NonNull String cleaned, boolean swapped, boolean canReplace) {
        cleaningUp = false;
        updateShimmer();
        setStatus(swapped ? R.string.voice_input_cleaned_up : R.string.voice_input_cleaned_up_not_swapped);
        VoiceTranscriptPanel view = panel;
        if (view != null) view.showCleaned(cleaned, !swapped, canReplace);
        updatePanelVisibility();
    }

    /** The cleanup came back with nothing better (or failed): the text stays as heard. */
    public void showKeptAsHeard() {
        cleaningUp = false;
        updateShimmer();
        setStatus(R.string.voice_input_kept_as_heard);
    }

    /** Replace or Copy was used: the actions go and the state says what happened. */
    public void onActionDone(@StringRes int text) {
        VoiceTranscriptPanel view = panel;
        if (view != null) view.hideActions();
        setStatus(text);
    }

    private void updateShimmer() {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.setShimmering(pending > 0 || cleaningUp);
        updatePanelVisibility();
    }

    private void updatePanelVisibility() {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        int visibility = view.hasContent() ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    // ------------------------------------------------------------------ placement

    private void reposition() {
        SwipeCard view = card;
        if (view == null) return;
        ViewGroup content = (ViewGroup) view.getParent();
        if (content == null) return;
        int[] place = place(content);
        if (place[0] == lastTop && place[1] == lastEnd) return;
        lastTop = place[0];
        lastEnd = place[1];
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = place[0];
        params.setMarginEnd(place[1]);
        view.setLayoutParams(params);
    }

    /**
     * {top margin, end margin} in the content frame: just inside the pane area's top right
     * corner, which is under the status bar and whatever top bars the layout puts above the panes;
     * under the system status bar when the pane area is not laid out.
     */
    @NonNull
    private int[] place(@NonNull ViewGroup content) {
        int gap = dp(GAP_DP);
        if (anchor.isShown() && anchor.getHeight() > 0 && anchor.getWidth() > 0) {
            int[] panes = new int[2];
            int[] frame = new int[2];
            anchor.getLocationInWindow(panes);
            content.getLocationInWindow(frame);
            int top = panes[1] - frame[1] + gap;
            int end = (frame[0] + content.getWidth()) - (panes[0] + anchor.getWidth()) + gap;
            return new int[] {Math.max(gap, top), Math.max(gap, end)};
        }
        int statusBar = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        if (insets != null) statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        return new int[] {statusBar + gap, gap};
    }

    /** Fixed for the session, so the pill does not jump as the panel fills: at most 300 dp, and never wider than the frame allows. */
    private int width(@NonNull ViewGroup content) {
        int frame = content.getWidth() > 0 ? content.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        return Math.min(dp(MAX_WIDTH_DP), frame - 2 * dp(GAP_DP) - dp(40));
    }

    // ------------------------------------------------------------------ pieces

    @NonNull
    private View warmingChip(@NonNull Context context, int onSurface, int accent) {
        LinearLayout chipView = new LinearLayout(context);
        chipView.setOrientation(LinearLayout.HORIZONTAL);
        chipView.setGravity(Gravity.CENTER_VERTICAL);
        chipView.setPaddingRelative(dp(6), dp(2), dp(8), dp(2));
        GradientDrawable background = new GradientDrawable();
        background.setColor((onSurface & 0x00FFFFFF) | 0x1A000000);
        background.setCornerRadius(dp(10));
        chipView.setBackground(background);

        ProgressBar ring = new ProgressBar(context);
        ring.setIndeterminate(true);
        ring.setIndeterminateTintList(ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        ringParams.setMarginEnd(dp(4));
        chipView.addView(ring, ringParams);

        TextView text = new TextView(context);
        text.setTextColor(onSurface);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        text.setSingleLine();
        text.setText(R.string.voice_input_warming_up);
        chipView.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return chipView;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics()));
    }

    private static int themeColor(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            return value.data;
        }
        return 0xFF888888;
    }

    /**
     * The pill and its panel, swipeable sideways: a horizontal drag past the touch slop is taken
     * from the children (so the × and the actions still get their taps), follows the finger and
     * fades, and past a third of the width or on a fling it leaves and reports the swipe. Touches
     * that land on the card never fall through to the terminal under it.
     */
    static final class SwipeCard extends LinearLayout {
        private static final float DISMISS_FRACTION = 0.35f;
        private static final long SETTLE_MS = 180L;

        private final Runnable onSwiped;
        private final int touchSlop;
        private final int minFling;
        private float downX;
        private float downY;
        private boolean dragging;
        private boolean gone;
        @Nullable private VelocityTracker velocity;

        SwipeCard(@NonNull Context context, @NonNull Runnable onSwiped) {
            super(context);
            this.onSwiped = onSwiped;
            ViewConfiguration configuration = ViewConfiguration.get(context);
            touchSlop = configuration.getScaledTouchSlop();
            minFling = configuration.getScaledMinimumFlingVelocity() * 4;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            track(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    return startDragIfHorizontal(event);
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    recycleVelocity();
                    return false;
                default:
                    return dragging;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (gone) return true;
            // Down reaches here only when no child took it; the card claims it so the drag can follow.
            track(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) startDragIfHorizontal(event);
                    if (dragging) follow(event.getRawX() - downX);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) release(event.getRawX() - downX);
                    dragging = false;
                    recycleVelocity();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) settleBack();
                    dragging = false;
                    recycleVelocity();
                    return true;
                default:
                    return true;
            }
        }

        private boolean startDragIfHorizontal(@NonNull MotionEvent event) {
            if (dragging) return true;
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
            return dragging;
        }

        private void follow(float dx) {
            setTranslationX(dx);
            float width = Math.max(1f, getWidth());
            setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / width));
        }

        private void release(float dx) {
            float vx = 0f;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                vx = velocity.getXVelocity();
            }
            boolean far = Math.abs(dx) > getWidth() * DISMISS_FRACTION;
            boolean flung = Math.abs(vx) > minFling && Math.signum(vx) == Math.signum(dx);
            if (!far && !flung) {
                settleBack();
                return;
            }
            gone = true;
            float target = Math.signum(dx == 0f ? vx : dx) * (getWidth() + getRight());
            animate().translationX(target).alpha(0f).setDuration(SETTLE_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onSwiped.run();
                    }
                }).start();
        }

        private void settleBack() {
            animate().translationX(0f).alpha(1f).setDuration(SETTLE_MS).setListener(null).start();
        }

        private void track(@NonNull MotionEvent event) {
            if (velocity == null) velocity = VelocityTracker.obtain();
            // Raw coordinates: the card itself moves under the finger.
            MotionEvent copy = MotionEvent.obtain(event);
            copy.setLocation(event.getRawX(), event.getRawY());
            velocity.addMovement(copy);
            copy.recycle();
        }

        private void recycleVelocity() {
            if (velocity != null) {
                velocity.recycle();
                velocity = null;
            }
        }
    }
}
