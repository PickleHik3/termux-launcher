package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.chrome.GrabHandle;
import com.termux.app.terminal.inappkeyboard.FloatingKeyboardGeometry;

/**
 * The dictation pill (agreed design, 2026-09-27): by default top right, under the status bar,
 * 36 dp tall with 6 dp above and below its contents, and a panel that grows down from it, toward
 * the thumb, over the pane's right side. It lives in the window's content frame rather than the
 * accessory stack, so it never takes part in the keyboard's geometry passes; the room it may sit
 * in is re-read from the place viewport ({@code terminal_surface_host}, the frame the pane wall
 * slides inside, so the same room on Home, Terminal and Display — ADR 0003, and above the
 * keyboard and under the status bar and the top bars) on every level update, which is cheap and
 * follows bars that move.
 *
 * <p>The pill row hugs its contents: the scrolling waveform ({@link VoiceWaveformView}) and the
 * state ("Listening…", "Transcribing…", "Cleaning up…", then what became of the text), a small gap,
 * then a "Warming up" chip with a ring while the speech model, the voiced decision or the cleanup
 * model is still loading — recording has already started — and pause/resume and the ×. The panel
 * ({@link VoiceTranscriptPanel}) holds the whole dictation's text and its undo, Copy and ✓, and is
 * as wide as the room allows up to {@link #MAX_WIDTH_DP}; with it up, the pill row keeps to the
 * side the card hangs from.
 *
 * <p>Under the panel sits the floating keyboard's grab handle ({@link GrabHandle}): dragged, it
 * moves the card anywhere in the room above; double-tapped, it puts the card back in the top right
 * corner. Where the card was left is a pair of fractions of the travel, as the floating keyboard's
 * is ({@link FloatingKeyboardGeometry}) — 0 against the left or top edge, 1 against the right or
 * bottom — kept per orientation by the host ({@link PositionMemory}) and read again on the next
 * dictation. A fraction also decides which way the card grows: from the top it grows down, from
 * the bottom up, from the middle both ways, so a card left low keeps its handle where it was left
 * and the panel never runs off the screen; the panel's lines shrink to what the room holds.
 *
 * <p>Pause stops listening: every phrase still transcribing arrives and the automatic cleanup
 * runs; the button then turns into resume, which carries on the same text (it waits, disabled,
 * while phrases are still transcribing). The × only ever discards: it stops listening if need
 * be, throws the text away and closes, and a sideways swipe of the card is the same. The waveform
 * rests while the microphone is closed. All of them are the host's to act on through
 * {@link Callbacks}.
 */
public final class VoiceListeningIndicator {

    /** Main thread. */
    public interface Callbacks {
        /** Pause: close the microphone; the phrases still transcribing arrive, then the cleanup runs. */
        void onPause();

        /** Resume: listen again, carrying on the text in the panel. */
        void onResume();

        /** The pill's ×, or a swipe of the card: stop if need be, discard the text and close. */
        void onClose();

        /** The panel's undo (redo once undone): the cleanup taken back, or put again. */
        void onUndo();

        /** The panel's Copy: the text to the clipboard (stopping first when still listening). */
        void onCopy();

        /** The panel's ✓: insert the text at the cursor, once (stopping first when still listening). */
        void onInsert();

        /** A finger has come down anywhere on the pill or its panel: the user is still there. */
        void onTouched();
    }

    /**
     * Where the card was left in the current orientation, as fractions of its travel. The host
     * keeps it in {@code PlaceLayoutStore}, beside the floating keyboard's.
     */
    public interface PositionMemory {
        /** 0..1, or anything outside that when the card has never been moved in this orientation. */
        float x();

        float y();

        /** Remembers a dragged place; a fraction outside 0..1 forgets it (back to the corner). */
        void set(float x, float y);
    }

    /** An unmoved card sits in the top right corner. */
    static final float DEFAULT_X_FRACTION = 1f;
    static final float DEFAULT_Y_FRACTION = 0f;

    private static final int PILL_HEIGHT_DP = 36;
    private static final int MAX_WIDTH_DP = 300;
    private static final int GAP_DP = 8;
    /** Between the waveform-and-state group and the chip-and-buttons group. */
    private static final int GROUP_GAP_DP = 4;
    /** The row's padding, the waveform and the two 36 dp buttons: all of the row but the state. */
    private static final int ROW_FIXED_DP = 12 + 56 + 10 + GROUP_GAP_DP + 2 * PILL_HEIGHT_DP + 4;
    /** How far a finger may wander on the handle and still be a tap. */
    private static final int HANDLE_SLOP_DP = 6;

    private final Activity activity;
    /** The place viewport: the room the card sits in, under whatever bars are above it and above the keyboard. */
    private final View anchor;
    @NonNull private final PositionMemory memory;
    @Nullable private SwipeCard card;
    @Nullable private LinearLayout row;
    @Nullable private TextView status;
    @Nullable private View chip;
    /** Pause while listening, resume once the microphone has closed; gone once the text is used. */
    @Nullable private ImageView toggle;
    private boolean toggleListening;
    @Nullable private VoiceWaveformView wave;
    @Nullable private VoiceTranscriptPanel panel;
    @Nullable private View handle;
    /** The panel's width for this session, from the room at show time; the state's widest from it. */
    private int panelWidth;
    /** Captured segments still transcribing; the shimmer line shows while any are. */
    private int pending;
    private boolean cleaningUp;
    /** What the state says for the cleaned text, for redo to put back. */
    @StringRes private int cleanedStatus = R.string.voice_input_cleaned_up;

    /** Where the card is, as fractions of its travel in {@link #room}. */
    private float xFraction = DEFAULT_X_FRACTION;
    private float yFraction = DEFAULT_Y_FRACTION;
    /** The configuration orientation the fractions were read for. */
    private int fractionsOrientation;
    /** The room the card may sit in, in the content frame's coordinates; refreshed on every placement. */
    private final Rect room = new Rect();
    private int rowGravity = Gravity.END;

    private final GrabHandle.Drag drag = new GrabHandle.Drag();
    private boolean dragMoved;
    private long lastHandleTapMs;

    /** Keeps the card in place once the level ticks have stopped and the text waits: the keyboard, the bars. */
    private final View.OnLayoutChangeListener anchorMoved =
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> v.post(this::reposition);

    /** The card grew or shrank (the panel came, a line more): its place in the room follows. */
    private final View.OnLayoutChangeListener cardResized =
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) reposition();
            // Laid out once, in its place: now it may be seen.
            if (v.getVisibility() == View.INVISIBLE && right > left) v.setVisibility(View.VISIBLE);
        };

    public VoiceListeningIndicator(@NonNull Activity activity, @NonNull View anchor,
                                   @NonNull PositionMemory memory) {
        this.activity = activity;
        this.anchor = anchor;
        this.memory = memory;
    }

    /**
     * Puts the pill up listening; a pill already up (its text waiting) goes back to listening
     * and carries on from {@code base}.
     *
     * @param dimRaw whether a cleanup pass will follow, so the raw text shows as heard until it lands
     * @param base the text the dictation carries on from, {@code ""} for a fresh one
     */
    public void show(@NonNull Callbacks callbacks, boolean dimRaw, @NonNull String base) {
        if (card != null) {
            resume(dimRaw, base);
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        Context context = activity;
        int onSurface = themeColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
        int onSurfaceVariant = themeColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        int accent = themeColor(context, com.termux.shared.R.attr.termuxColorPrimary);
        int surface = themeColor(context, com.termux.shared.R.attr.termuxColorSurfaceBase);
        int raised = themeColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh);
        readRoom(content);
        readFractions();
        panelWidth = Math.max(dp(160), Math.min(dp(MAX_WIDTH_DP), room.width()));

        SwipeCard view = new SwipeCard(context, callbacks::onClose, callbacks::onTouched);
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(PILL_HEIGHT_DP / 2));
        view.setBackground(background);
        view.setElevation(com.termux.app.chrome.ShapeTokens.elevationPx(context, 2));
        view.setClipToOutline(true);

        // The pill row wraps its contents: no stretch between the state and the buttons, so the
        // pill is as long as what it says and no longer.
        LinearLayout pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);

        VoiceWaveformView levels = new VoiceWaveformView(context, accent, onSurface);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(dp(56), dp(16));
        waveParams.setMarginEnd(dp(10));
        pillRow.addView(levels, waveParams);

        TextView label = new TextView(context);
        label.setTextColor(onSurface);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setText(R.string.voice_input_listening);
        label.setMaxWidth(Math.max(dp(48), panelWidth - dp(ROW_FIXED_DP)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginEnd(dp(GROUP_GAP_DP));
        pillRow.addView(label, labelParams);

        View warming = warmingChip(context, onSurface, accent);
        warming.setVisibility(View.GONE);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMarginStart(dp(2));
        pillRow.addView(warming, chipParams);

        // Pause/resume, then the ×: each the pill's full height as the tap target around an 18 dp
        // glyph. The × stays for the pill's whole life, and only ever discards.
        ImageView toggleButton = pillButton(context, R.drawable.ic_symbol_pause, onSurface, R.string.voice_input_pause);
        toggleButton.setOnClickListener(v -> {
            if (!v.isEnabled()) return;
            if (toggleListening) callbacks.onPause();
            else callbacks.onResume();
        });
        pillRow.addView(toggleButton, new LinearLayout.LayoutParams(dp(PILL_HEIGHT_DP), dp(PILL_HEIGHT_DP)));
        ImageView closeButton = pillButton(context, R.drawable.ic_symbol_close, onSurface, R.string.voice_input_close);
        closeButton.setOnClickListener(v -> callbacks.onClose());
        pillRow.addView(closeButton, new LinearLayout.LayoutParams(dp(PILL_HEIGHT_DP), dp(PILL_HEIGHT_DP)));
        pillRow.setPaddingRelative(dp(12), 0, dp(4), 0);

        rowGravity = rowGravityFor(xFraction);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT_DP));
        rowParams.gravity = rowGravity;
        view.addView(pillRow, rowParams);

        VoiceTranscriptPanel transcript = new VoiceTranscriptPanel(context, onSurface, onSurfaceVariant,
            accent, raised, new VoiceTranscriptPanel.Actions() {
                @Override
                public void onUndo() {
                    callbacks.onUndo();
                }

                @Override
                public void onCopy() {
                    callbacks.onCopy();
                }

                @Override
                public void onInsert() {
                    callbacks.onInsert();
                }
            });
        transcript.setDimRaw(dimRaw);
        if (!base.isEmpty()) transcript.resetTo(base);
        transcript.setPaddingRelative(dp(12), 0, dp(12), dp(2));
        transcript.setVisibility(View.GONE);
        view.addView(transcript, new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The floating keyboard's handle, under the panel: drag to move the card, double-tap for
        // the corner. It comes and goes with the panel, so the bare pill stays 36 dp.
        FrameLayout grab = new FrameLayout(context);
        grab.setContentDescription(context.getString(R.string.voice_input_move_handle));
        grab.addView(GrabHandle.newPill(context), GrabHandle.pillParams(context));
        grab.setOnTouchListener((v, event) -> onHandleTouch(v, event));
        grab.setVisibility(View.GONE);
        view.addView(grab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(dp(GrabHandle.ROW_DP))));

        // Placed by translation from the frame's top left, so the frame measures it at its own
        // size wherever it sits; seen from its first layout, once it is in its place.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        view.setVisibility(View.INVISIBLE);
        view.addOnLayoutChangeListener(cardResized);
        content.addView(view, params);
        anchor.addOnLayoutChangeListener(anchorMoved);
        card = view;
        row = pillRow;
        status = label;
        chip = warming;
        toggle = toggleButton;
        wave = levels;
        panel = transcript;
        handle = grab;
        pending = 0;
        cleaningUp = false;
        cleanedStatus = R.string.voice_input_cleaned_up;
        setToggle(true, true);
        updatePanelVisibility();
        fitPanel();
    }

    /** The pill was up with its text waiting, and a new dictation carries on from it. */
    private void resume(boolean dimRaw, @NonNull String base) {
        pending = 0;
        cleaningUp = false;
        setStatus(R.string.voice_input_listening);
        setToggle(true, true);
        if (wave != null) wave.setResting(false);
        VoiceTranscriptPanel view = panel;
        if (view != null) {
            view.setDimRaw(dimRaw);
            view.resetTo(base);
        }
        updateShimmer();
    }

    public void hide() {
        anchor.removeOnLayoutChangeListener(anchorMoved);
        SwipeCard view = card;
        if (panel != null) panel.release();
        card = null;
        row = null;
        status = null;
        chip = null;
        toggle = null;
        wave = null;
        panel = null;
        handle = null;
        pending = 0;
        cleaningUp = false;
        drag.end();
        dragMoved = false;
        lastHandleTapMs = 0L;
        if (view == null) return;
        view.removeOnLayoutChangeListener(cardResized);
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
        TextView label = status;
        if (view == null || label == null) return;
        int visibility = warming ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == visibility) return;
        view.setVisibility(visibility);
        // The chip comes out of the state's room, so the pill never grows past the panel's width.
        int chipWidth = 0;
        if (warming) {
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            chipWidth = view.getMeasuredWidth() + dp(2);
        }
        label.setMaxWidth(Math.max(dp(48), panelWidth - dp(ROW_FIXED_DP) - chipWidth));
    }

    public void setListening() {
        setStatus(R.string.voice_input_listening);
    }

    /**
     * The mic has closed but a captured segment is still transcribing: "Listening…" no longer
     * fits, and resume waits until the phrases are in.
     */
    public void setTranscribing() {
        onMicrophoneClosed(false);
        setStatus(R.string.voice_input_transcribing);
    }

    /**
     * The microphone has closed: the waveform rests and pause turns into resume, enabled once
     * there is nothing left to transcribe.
     */
    private void onMicrophoneClosed(boolean canResume) {
        setToggle(false, canResume);
        VoiceWaveformView levels = wave;
        if (levels != null) levels.setResting(true);
    }

    private void setToggle(boolean listening, boolean enabled) {
        ImageView view = toggle;
        if (view == null) return;
        toggleListening = listening;
        view.setImageResource(listening ? R.drawable.ic_symbol_pause : R.drawable.ic_symbol_mic);
        view.setContentDescription(activity.getString(listening ? R.string.voice_input_pause : R.string.voice_input_resume));
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.38f);
        view.setVisibility(View.VISIBLE);
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

    /** One phrase, as it joins on, into the panel. */
    public void appendTranscript(@NonNull String typed) {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.append(typed);
        updatePanelVisibility();
    }

    /** The session has ended and the one cleanup pass is running. */
    public void setCleaningUp() {
        cleaningUp = true;
        onMicrophoneClosed(true);
        setWarmingUp(false);
        setStatus(R.string.voice_input_cleaning_up);
        updateShimmer();
    }

    /**
     * The cleanup has landed and waits with its changes marked; see
     * {@link VoiceTranscriptPanel#showCleaned}. {@code status} says which: "Cleaned up", or
     * "Formatted as a command".
     */
    public void showCleaned(@NonNull String cleaned, @StringRes int status) {
        cleaningUp = false;
        onMicrophoneClosed(true);
        updateShimmer();
        cleanedStatus = status;
        setStatus(status);
        VoiceTranscriptPanel view = panel;
        if (view != null) view.showCleaned(cleaned);
        updatePanelVisibility();
    }

    /**
     * The text is final as heard and waits: no cleanup was asked for ("Ready"), or it came back
     * with nothing better or failed ("Kept as heard").
     */
    public void showAsHeard(@StringRes int text) {
        cleaningUp = false;
        onMicrophoneClosed(true);
        updateShimmer();
        setStatus(text);
        VoiceTranscriptPanel view = panel;
        if (view != null) view.showFinalRaw();
    }

    /** Undo ({@code true}) or redo: the panel shows the text as it went into the cleanup, or cleaned again. */
    public void setUndone(boolean undone) {
        VoiceTranscriptPanel view = panel;
        if (view != null) view.setUndone(undone);
        setStatus(undone ? R.string.voice_input_as_heard : cleanedStatus);
    }

    /** ✓ or Copy has used the text: the buttons go (resume too) and the state says what happened. */
    public void onActionDone(@StringRes int text) {
        VoiceTranscriptPanel view = panel;
        if (view != null) view.hideActions();
        if (toggle != null) toggle.setVisibility(View.GONE);
        setStatus(text);
    }

    private void updateShimmer() {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.setShimmering(pending > 0 || cleaningUp);
        updatePanelVisibility();
    }

    /** The panel, and the handle under it, show once there is text or a phrase on its way. */
    private void updatePanelVisibility() {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        int visibility = view.hasContent() ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
        View grab = handle;
        if (grab != null && grab.getVisibility() != visibility) grab.setVisibility(visibility);
    }

    /**
     * Seven lines where they fit; where the room is short (landscape, the keyboard up) the panel
     * keeps as many as fit in it with the pill and the handle, and the top truncates.
     */
    private void fitPanel() {
        VoiceTranscriptPanel view = panel;
        if (view == null || card == null) return;
        int line = view.lineHeightPx();
        if (line <= 0 || room.isEmpty()) {
            view.setMaxVisibleLines(VoiceTranscriptPanel.VISIBLE_LINES);
            return;
        }
        int space = room.height() - dp(PILL_HEIGHT_DP) - view.chromeHeightPx() - Math.round(dp(GrabHandle.ROW_DP));
        view.setMaxVisibleLines(space / line);
    }

    // ------------------------------------------------------------------ placement

    /** The card at its fractions of the room, which is read again first; the panel refits to it. */
    private void reposition() {
        SwipeCard view = card;
        if (view == null) return;
        ViewGroup content = (ViewGroup) view.getParent();
        if (content == null) return;
        readRoom(content);
        if (activity.getResources().getConfiguration().orientation != fractionsOrientation) {
            // Turned: the other orientation's place, as the floating keyboard does.
            if (!drag.isActive()) readFractions();
        }
        place(view);
        // The keyboard coming up shortens the room without moving its top.
        fitPanel();
    }

    private void place(@NonNull SwipeCard view) {
        int width = view.getWidth();
        int height = view.getHeight();
        int x = room.left + FloatingKeyboardGeometry.positionPx(xFraction,
            FloatingKeyboardGeometry.travelPx(room.width(), width));
        int y = room.top + FloatingKeyboardGeometry.positionPx(yFraction,
            FloatingKeyboardGeometry.travelPx(room.height(), height));
        view.setRest(x, y);
        int gravity = rowGravityFor(xFraction);
        LinearLayout pillRow = row;
        if (gravity != rowGravity && pillRow != null) {
            rowGravity = gravity;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) pillRow.getLayoutParams();
            params.gravity = gravity;
            pillRow.setLayoutParams(params);
        }
    }

    /** With the panel up, the pill row keeps to the side the card is nearer: the × stays put as it grows. */
    private static int rowGravityFor(float xFraction) {
        return xFraction >= 0.5f ? Gravity.END : Gravity.START;
    }

    /**
     * The room the card may sit in, in the content frame's coordinates: the place viewport, a gap
     * in from each side, which is under the status bar and whatever top bars the layout puts above
     * the panes, and above the keyboard, on every place alike. Without a laid-out viewport, the
     * frame under the status bar and above the navigation bar and the keyboard.
     */
    private void readRoom(@NonNull ViewGroup content) {
        int gap = dp(GAP_DP);
        if (anchor.isShown() && anchor.getHeight() > 0 && anchor.getWidth() > 0) {
            int[] panes = new int[2];
            int[] frame = new int[2];
            anchor.getLocationInWindow(panes);
            content.getLocationInWindow(frame);
            int left = panes[0] - frame[0];
            int top = panes[1] - frame[1];
            room.set(Math.max(gap, left + gap), Math.max(gap, top + gap),
                left + anchor.getWidth() - gap, top + anchor.getHeight() - gap);
        } else {
            int statusBar = 0;
            int bottomBars = 0;
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
            if (insets != null) {
                statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                bottomBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()
                    | WindowInsetsCompat.Type.ime()).bottom;
            }
            int width = content.getWidth() > 0 ? content.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            int height = content.getHeight() > 0 ? content.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
            room.set(gap, statusBar + gap, width - gap, height - bottomBars - gap);
        }
        if (room.right < room.left) room.right = room.left;
        if (room.bottom < room.top) room.bottom = room.top;
    }

    /** This orientation's remembered place, or the top right corner. */
    private void readFractions() {
        float x = memory.x();
        float y = memory.y();
        boolean set = FloatingKeyboardGeometry.isPositionSet(x) && FloatingKeyboardGeometry.isPositionSet(y);
        xFraction = set ? x : DEFAULT_X_FRACTION;
        yFraction = set ? y : DEFAULT_Y_FRACTION;
        fractionsOrientation = activity.getResources().getConfiguration().orientation;
    }

    /**
     * The handle: a drag past the slop moves the card, held inside the room, and is remembered when
     * the finger lifts; a tap does nothing, two quick ones put the card back in the corner. The
     * card's own sideways swipe is kept out of it for the whole gesture.
     */
    private boolean onHandleTouch(@NonNull View grab, @NonNull MotionEvent event) {
        SwipeCard view = card;
        if (view == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (grab.getParent() != null) grab.getParent().requestDisallowInterceptTouchEvent(true);
                drag.begin(event.getRawX(), event.getRawY(), Math.round(view.restX()), Math.round(view.restY()));
                dragMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!drag.isActive()) return false;
                if (!dragMoved && !drag.isPast(event.getRawX(), event.getRawY(), dp(HANDLE_SLOP_DP))) return true;
                dragMoved = true;
                moveTo(view, drag.x(event.getRawX()), drag.y(event.getRawY()));
                return true;
            case MotionEvent.ACTION_UP:
                if (!drag.isActive()) return false;
                if (dragMoved) {
                    moveTo(view, drag.x(event.getRawX()), drag.y(event.getRawY()));
                    memory.set(xFraction, yFraction);
                    lastHandleTapMs = 0L;
                } else {
                    long now = SystemClock.uptimeMillis();
                    if (lastHandleTapMs != 0L && now - lastHandleTapMs <= ViewConfiguration.getDoubleTapTimeout()) {
                        lastHandleTapMs = 0L;
                        resetPosition();
                    } else {
                        lastHandleTapMs = now;
                    }
                }
                drag.end();
                dragMoved = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!drag.isActive()) return false;
                if (dragMoved) memory.set(xFraction, yFraction);
                drag.end();
                dragMoved = false;
                return true;
            default:
                return false;
        }
    }

    /** The card's top left at {@code x, y} in the frame, held inside the room, kept as fractions. */
    private void moveTo(@NonNull SwipeCard view, int x, int y) {
        readRoom((ViewGroup) view.getParent());
        int travelX = FloatingKeyboardGeometry.travelPx(room.width(), view.getWidth());
        int travelY = FloatingKeyboardGeometry.travelPx(room.height(), view.getHeight());
        xFraction = FloatingKeyboardGeometry.fractionFor(x - room.left, travelX);
        yFraction = FloatingKeyboardGeometry.fractionFor(y - room.top, travelY);
        place(view);
    }

    /** The handle's double tap: the top right corner again, and the remembered place forgotten. */
    private void resetPosition() {
        xFraction = DEFAULT_X_FRACTION;
        yFraction = DEFAULT_Y_FRACTION;
        memory.set(-1f, -1f);
        reposition();
    }

    // ------------------------------------------------------------------ pieces

    @NonNull
    private ImageView pillButton(@NonNull Context context, int icon, int tint, @StringRes int description) {
        ImageView button = new ImageView(context);
        button.setImageResource(icon);
        button.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(context.getString(description));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            button.setBackgroundResource(ripple.resourceId);
        }
        return button;
    }

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

        com.google.android.material.progressindicator.CircularProgressIndicator ring =
            new com.google.android.material.progressindicator.CircularProgressIndicator(context);
        ring.setIndeterminate(true);
        ring.setIndicatorSize(dp(12));
        ring.setTrackThickness(dp(2));
        ring.setIndicatorColor(accent);
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
        return Math.round(dp((float) value));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics());
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
     * from the children (so the pill's buttons and the actions still get their taps), follows the finger and
     * fades, and past a third of the width or on a fling it leaves and reports the swipe. Touches
     * that land on the card never fall through to the terminal under it. Its place is a
     * translation from the frame's top left ({@link #setRest}); the swipe moves it from there.
     */
    static final class SwipeCard extends LinearLayout {
        private static final float DISMISS_FRACTION = 0.35f;
        private static final long SETTLE_MS = 180L;

        private final Runnable onSwiped;
        private final Runnable onTouched;
        private final int touchSlop;
        private final int minFling;
        private float downX;
        private float downY;
        private boolean dragging;
        private boolean gone;
        /** Where the card rests, as a translation in its frame; the swipe is an offset from it. */
        private float restX;
        private float restY;
        @Nullable private VelocityTracker velocity;

        SwipeCard(@NonNull Context context, @NonNull Runnable onSwiped, @NonNull Runnable onTouched) {
            super(context);
            this.onSwiped = onSwiped;
            this.onTouched = onTouched;
            ViewConfiguration configuration = ViewConfiguration.get(context);
            touchSlop = configuration.getScaledTouchSlop();
            minFling = configuration.getScaledMinimumFlingVelocity() * 4;
        }

        /** Puts the card at rest at {@code x, y}; a swipe under way keeps its offset from the new place. */
        void setRest(float x, float y) {
            if (x == restX && y == restY) return;
            float swipe = dragging || gone ? getTranslationX() - restX : 0f;
            restX = x;
            restY = y;
            if (!dragging && !gone) {
                // A settle still running would carry the card back to where it rested before.
                animate().cancel();
                setAlpha(1f);
            }
            setTranslationX(x + swipe);
            setTranslationY(y);
        }

        float restX() {
            return restX;
        }

        float restY() {
            return restY;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            // Seen before any child takes it: a tap on a button counts as much as one on the text.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) onTouched.run();
            return super.dispatchTouchEvent(event);
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
            setTranslationX(restX + dx);
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
            int frame = getParent() instanceof View ? ((View) getParent()).getWidth() : getRight();
            float target = restX + Math.signum(dx == 0f ? vx : dx) * (getWidth() + frame);
            animate().translationX(target).alpha(0f).setDuration(SETTLE_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onSwiped.run();
                    }
                }).start();
        }

        private void settleBack() {
            animate().translationX(restX).alpha(1f).setDuration(SETTLE_MS).setListener(null).start();
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
