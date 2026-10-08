package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
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
 * The dictation card (agreed design 1b, "Single control bar"): by default top right, under the
 * status bar, as wide as the room allows up to {@link #MAX_WIDTH_DP}, growing down from there,
 * toward the thumb, over the pane's right side. It lives in the window's content frame rather
 * than the accessory stack, so it never takes part in the keyboard's geometry passes; the room it
 * may sit in is re-read from the place viewport ({@code terminal_surface_host}, the frame the
 * pane wall slides inside, so the same room on Home, Terminal and Display - ADR 0003, and above
 * the keyboard and under the status bar and the top bars) on every level update, which is cheap
 * and follows bars that move.
 *
 * <p>The card is whole from the moment it shows: a header strip, the text, the controls. The
 * strip says what is happening on the left ("Listening…", "Transcribing…", "Cleaning up…", then
 * what became of the text, with a small ring while something is loading or running and a few
 * words of meta such as "· at the cursor"), carries a small handle in the middle and the × on the
 * right. The text and the controls are {@link VoiceTranscriptPanel}: Pause (with the scrolling
 * waveform inside it) or Resume, undo, Copy and Insert.
 *
 * <p>The whole strip is the drag surface, as the floating keyboard's grab handle is: dragged, it
 * moves the card anywhere in the room; double-tapped, it puts the card back in the top right
 * corner. Where the card was left is a pair of fractions of the travel, as the floating keyboard's
 * is ({@link FloatingKeyboardGeometry}) - 0 against the left or top edge, 1 against the right or
 * bottom - kept per orientation by the host ({@link PositionMemory}) and read again on the next
 * dictation. A fraction also decides which way the card grows: from the top it grows down, from
 * the bottom up, from the middle both ways, so a card left low keeps its strip where it was left
 * and the text never runs off the screen; its lines shrink to what the room holds.
 *
 * <p>Pause stops listening: every phrase still transcribing arrives and the automatic cleanup
 * runs; the button then turns into resume, which carries on the same text (it waits, dimmed,
 * while phrases are still transcribing). The × only ever discards: it stops listening if need
 * be, throws the text away and closes, and a sideways swipe of the card is the same. The waveform
 * rests while the microphone is closed. All of them are the host's to act on through
 * {@link Callbacks}.
 *
 * <p>The same card reads aloud ({@link #showReading}): the shell, the place and every gesture
 * are the dictation's, the body is {@link ReadAloudPanel} (the selection with the sentence being
 * heard marked; Pause, the voice and Stop), the strip says "Reading", "Paused" or "Done", and the
 * × and the swipe stop the reading. The host gives reading an indicator of its own, so a
 * dictation card that is up is left exactly as it is.
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

    /** The reading card's controls ({@link #showReading}); main thread. */
    public interface ReadingCallbacks {
        /** Pause: hold the reading mid-word. */
        void onPause();

        /** Resume: carry on from where it was held. */
        void onResume();

        /** Stop, the ×, or a swipe of the card: stop reading and close. */
        void onClose();

        /** A voice was picked on the card: keep it, and read on in it from the next sentence. */
        void onVoice(@NonNull String voice);

        /** A finger has come down anywhere on the card. */
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

    /** The header strip: 4 dp above, then 32 dp of content. */
    private static final int HEADER_DP = 36;
    private static final int MAX_WIDTH_DP = 300;
    private static final int GAP_DP = 8;
    /** How far a finger may wander on the strip and still be a tap. */
    private static final int HANDLE_SLOP_DP = 6;
    /** The state's share of the strip, out of two, the × taking the rest. */
    private static final float LEFT_SHARE = 1.3f;

    private final Activity activity;
    /** The place viewport: the room the card sits in, under whatever bars are above it and above the keyboard. */
    private final View anchor;
    @NonNull private final PositionMemory memory;
    @Nullable private SwipeCard card;
    @Nullable private View ring;
    @Nullable private TextView status;
    @Nullable private TextView meta;
    @Nullable private VoiceTranscriptPanel panel;
    /** The card's body while it reads aloud ({@link #showReading}); {@link #panel} is null then. */
    @Nullable private ReadAloudPanel readingPanel;
    /** Reading: the first sound is not out yet, so the ring turns. */
    private boolean readingPreparing;
    /** Reading: "· 3 of 12", or empty for a single sentence. */
    @NonNull private CharSequence readingProgress = "";
    /** The panel's width for this session, from the room at show time. */
    private int panelWidth;
    /** Captured segments still transcribing; the ghost bar shows while any are. */
    private int pending;
    private boolean cleaningUp;
    /** A model or the voiced decision is still loading while the microphone is open. */
    private boolean warming;
    /** What the header's state says now, and for the cleaned text, for redo to put back. */
    @StringRes private int statusRes = R.string.voice_input_listening;
    @StringRes private int cleanedStatus = R.string.voice_input_cleaned_up;
    private int accent;
    private int onSurfaceVariant;
    private int onSurface;
    private int onAccent;
    private int surface;
    private int raised;

    /** Where the card is, as fractions of its travel in {@link #room}. */
    private float xFraction = DEFAULT_X_FRACTION;
    private float yFraction = DEFAULT_Y_FRACTION;
    /** The configuration orientation the fractions were read for. */
    private int fractionsOrientation;
    /** The room the card may sit in, in the content frame's coordinates; refreshed on every placement. */
    private final Rect room = new Rect();

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
        readColors(context);
        readRoom(content);
        readFractions();
        panelWidth = Math.max(dp(160), Math.min(dp(MAX_WIDTH_DP), room.width()));

        VoiceTranscriptPanel transcript = new VoiceTranscriptPanel(context, onSurface, onSurfaceVariant,
            accent, onAccent, raised, new VoiceTranscriptPanel.Actions() {
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

                @Override
                public void onPause() {
                    callbacks.onPause();
                }

                @Override
                public void onResume() {
                    callbacks.onResume();
                }
            });
        transcript.setDimRaw(dimRaw);
        if (!base.isEmpty()) transcript.resetTo(base);
        attach(content, transcript, callbacks::onClose, callbacks::onTouched,
            R.string.voice_input_move_handle, R.string.voice_input_close);
        panel = transcript;
        pending = 0;
        cleaningUp = false;
        warming = false;
        cleanedStatus = R.string.voice_input_cleaned_up;
        statusRes = R.string.voice_input_listening;
        setToggle(true, true);
        refreshHeader();
        fitPanel();
    }

    /**
     * Puts the card up for Read aloud ({@link ReadAloudPanel}): the same shell, place, drag, double
     * tap and swipe as the dictation's, with {@code text} in it and Pause, the voice and Stop under
     * it. The header says "Reading" with the ring until {@link #setReadingSounding}. A card this
     * indicator already has up is replaced; the host keeps reading on an indicator of its own, so a
     * dictation card stays as it is.
     */
    public void showReading(@NonNull ReadingCallbacks callbacks, @NonNull String text, @NonNull String voice) {
        if (card != null) hide();
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        Context context = activity;
        readColors(context);
        readRoom(content);
        readFractions();
        panelWidth = Math.max(dp(160), Math.min(dp(MAX_WIDTH_DP), room.width()));
        ReadAloudPanel body = new ReadAloudPanel(context, onSurface, onSurfaceVariant, accent, onAccent, raised,
            text, voice, new ReadAloudPanel.Actions() {
                @Override
                public void onPause() {
                    callbacks.onPause();
                }

                @Override
                public void onResume() {
                    callbacks.onResume();
                }

                @Override
                public void onStop() {
                    callbacks.onClose();
                }

                @Override
                public void onVoice(@NonNull String picked) {
                    callbacks.onVoice(picked);
                }
            });
        attach(content, body, callbacks::onClose, callbacks::onTouched,
            R.string.read_aloud_move_handle, R.string.read_aloud_close);
        readingPanel = body;
        readingPreparing = true;
        readingProgress = "";
        statusRes = R.string.read_aloud_reading;
        refreshHeader();
        fitPanel();
    }

    /** Sentence {@code index} of {@code count}, {@code text[start, end)}, is being read: marked, and counted in the header. */
    public void setReadingSentence(int index, int count, int start, int end) {
        ReadAloudPanel view = readingPanel;
        if (view == null) return;
        view.markSentence(start, end);
        readingProgress = count > 1 ? activity.getString(R.string.read_aloud_meta_progress, index + 1, count) : "";
        refreshHeader();
    }

    /** The first sound is out: the ring goes and the waveform moves. */
    public void setReadingSounding() {
        ReadAloudPanel view = readingPanel;
        if (view == null) return;
        readingPreparing = false;
        view.setSounding();
        refreshHeader();
    }

    /** "Paused" with Resume, or "Reading" with Pause again. */
    public void setReadingPaused(boolean paused) {
        ReadAloudPanel view = readingPanel;
        if (view == null) return;
        view.setPaused(paused);
        setStatus(paused ? R.string.read_aloud_paused : R.string.read_aloud_reading);
    }

    /** Heard to the end: "Done", the controls gone, the text all read. */
    public void showReadingDone() {
        ReadAloudPanel view = readingPanel;
        if (view == null) return;
        readingPreparing = false;
        readingProgress = "";
        view.showDone();
        setStatus(R.string.read_aloud_done);
    }

    private void readColors(@NonNull Context context) {
        onSurface = themeColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
        onSurfaceVariant = themeColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        accent = themeColor(context, com.termux.shared.R.attr.termuxColorPrimary);
        onAccent = themeColor(context, com.termux.shared.R.attr.termuxColorOnPrimary);
        // The card floats over the terminal and the keyboard, both of which sit on the base
        // tone, so it takes the raised container tone the other floating cards use; its buttons
        // go one step higher again so they still stand off the card.
        surface = themeColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh);
        raised = themeColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHighest);
    }

    /**
     * Builds the card around {@code body} and puts it in {@code content}: the header strip, then
     * the body, unseen until its first layout has placed it.
     */
    private void attach(@NonNull ViewGroup content, @NonNull View body, @NonNull Runnable onClose,
                        @NonNull Runnable onTouched, @StringRes int handleDescription, @StringRes int closeDescription) {
        Context context = activity;
        SwipeCard view = new SwipeCard(context, onClose, onTouched);
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(24));
        view.setBackground(background);
        view.setElevation(com.termux.app.chrome.ShapeTokens.elevationPx(context, 3));
        view.setClipToOutline(true);

        // The header strip: what is happening and what became of it on the left, the handle in
        // the middle (the two sides share the room equally), the x on the right. The whole strip
        // is the drag surface; the x is a child with its own click, so its taps still land.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(16), dp(4), dp(4), 0);
        header.setContentDescription(context.getString(handleDescription));
        header.setOnTouchListener((v, event) -> onHandleTouch(v, event));

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        com.google.android.material.progressindicator.CircularProgressIndicator busy =
            new com.google.android.material.progressindicator.CircularProgressIndicator(context);
        busy.setIndeterminate(true);
        busy.setIndicatorSize(dp(8));
        busy.setIndicatorInset(0);
        busy.setTrackThickness(dp(2));
        busy.setIndicatorColor(accent);
        busy.setVisibility(View.GONE);
        LinearLayout.LayoutParams busyParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        busyParams.setMarginEnd(dp(6));
        left.addView(busy, busyParams);

        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.02f);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(accent);
        label.setText(R.string.voice_input_listening);
        left.addView(label, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(context);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(onSurfaceVariant);
        note.setSingleLine();
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setVisibility(View.GONE);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.setMarginStart(dp(6));
        left.addView(note, noteParams);
        // The state's side gets a little more of the strip than the ×'s (the handle sits a touch
        // right of centre, which the eye does not catch), and a long state ("Inserted · at the
        // cursor") stops short of the handle rather than touching it.
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, LEFT_SHARE);
        leftParams.setMarginEnd(dp(8));
        header.addView(left, leftParams);

        View grip = new View(context);
        GradientDrawable gripShape = new GradientDrawable();
        gripShape.setColor((onSurfaceVariant & 0x00FFFFFF) | 0x8C000000);
        gripShape.setCornerRadius(dp(1.5f));
        grip.setBackground(gripShape);
        header.addView(grip, new LinearLayout.LayoutParams(dp(28), dp(3)));

        // The x: 32 dp around a 16 dp glyph. It stays for the card's whole life, and only ever discards.
        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ImageView closeButton = closeButton(context, onSurfaceVariant, closeDescription);
        closeButton.setOnClickListener(v -> onClose.run());
        right.addView(closeButton, new LinearLayout.LayoutParams(dp(32), dp(32)));
        header.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f - LEFT_SHARE));
        view.addView(header, new LinearLayout.LayoutParams(panelWidth, dp(HEADER_DP)));

        view.addView(body, new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Placed by translation from the frame's top left, so the frame measures it at its own
        // size wherever it sits; seen from its first layout, once it is in its place.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        view.setVisibility(View.INVISIBLE);
        view.addOnLayoutChangeListener(cardResized);
        content.addView(view, params);
        anchor.addOnLayoutChangeListener(anchorMoved);
        card = view;
        ring = busy;
        status = label;
        meta = note;
    }

    /** The card was up with its text waiting, and a new dictation carries on from it. */
    private void resume(boolean dimRaw, @NonNull String base) {
        pending = 0;
        cleaningUp = false;
        setToggle(true, true);
        VoiceTranscriptPanel view = panel;
        if (view != null) {
            view.setWaveResting(false);
            view.setDimRaw(dimRaw);
            view.resetTo(base);
        }
        setStatus(R.string.voice_input_listening);
        updateGhost();
    }

    public void hide() {
        anchor.removeOnLayoutChangeListener(anchorMoved);
        SwipeCard view = card;
        if (panel != null) panel.release();
        if (readingPanel != null) readingPanel.release();
        card = null;
        ring = null;
        status = null;
        meta = null;
        panel = null;
        readingPanel = null;
        readingPreparing = false;
        readingProgress = "";
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

    /** A level sample with the VAD's noise floor at that moment; also keeps the card in place as bars move. */
    public void setLevel(float rms, boolean voiced, float noiseFloor) {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.pushLevel(rms, voiced, noiseFloor);
        reposition();
    }

    /** The ring and "· warming up" beside "Listening…" while a model or the voiced decision is still loading. */
    public void setWarmingUp(boolean warmingNow) {
        if (warming == warmingNow) return;
        warming = warmingNow;
        refreshHeader();
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
        VoiceTranscriptPanel view = panel;
        if (view != null) view.setWaveResting(true);
    }

    private void setToggle(boolean listening, boolean enabled) {
        VoiceTranscriptPanel view = panel;
        if (view != null) view.setToggle(listening, enabled);
    }

    public void setStatus(@StringRes int text) {
        statusRes = text;
        refreshHeader();
    }

    /**
     * The header from what is known: the state's words, accent only while the microphone is open,
     * the ring while something loads or runs, and the meta that goes with the state.
     */
    private void refreshHeader() {
        TextView label = status;
        TextView note = meta;
        View busy = ring;
        if (label == null || note == null || busy == null) return;
        if (readingPanel != null) {
            refreshReadingHeader(label, note, busy);
            return;
        }
        boolean listening = statusRes == R.string.voice_input_listening;
        label.setText(statusRes);
        label.setTextColor(listening ? accent : onSurfaceVariant);
        boolean running = statusRes == R.string.voice_input_transcribing
            || statusRes == R.string.voice_input_cleaning_up;
        int busyVisibility = running || (listening && warming) ? View.VISIBLE : View.GONE;
        if (busy.getVisibility() != busyVisibility) busy.setVisibility(busyVisibility);
        CharSequence text = "";
        if (listening) {
            if (warming) text = activity.getString(R.string.voice_input_meta_warming_up);
        } else if (statusRes == R.string.voice_input_as_heard) {
            text = activity.getString(R.string.voice_input_meta_undone);
        } else if (statusRes == R.string.voice_input_inserted) {
            text = activity.getString(R.string.voice_input_meta_inserted);
        } else if (statusRes == R.string.voice_input_cleanup_copied) {
            text = activity.getString(R.string.voice_input_meta_copied);
        }
        note.setText(text);
        note.setVisibility(text.length() > 0 && metaFits(label, note, busyVisibility == View.VISIBLE)
            ? View.VISIBLE : View.GONE);
    }

    /**
     * The reading card's header: "Reading" in the accent while the voice reads, "Paused" and
     * "Done" in the secondary colour; the ring with "· preparing" until the first sound, then
     * which sentence of how many.
     */
    private void refreshReadingHeader(@NonNull TextView label, @NonNull TextView note, @NonNull View busy) {
        boolean reading = statusRes == R.string.read_aloud_reading;
        label.setText(statusRes);
        label.setTextColor(reading ? accent : onSurfaceVariant);
        boolean preparing = readingPreparing && statusRes != R.string.read_aloud_done;
        int busyVisibility = preparing ? View.VISIBLE : View.GONE;
        if (busy.getVisibility() != busyVisibility) busy.setVisibility(busyVisibility);
        CharSequence text = statusRes == R.string.read_aloud_done ? ""
            : preparing ? activity.getString(R.string.read_aloud_meta_preparing) : readingProgress;
        note.setText(text);
        note.setVisibility(text.length() > 0 && metaFits(label, note, preparing) ? View.VISIBLE : View.GONE);
    }

    /**
     * Whether the meta fits beside the whole state in the strip's left group. The state is the
     * one thing the strip must say; where the two together would be cut, the meta goes rather
     * than either being cut short.
     */
    private boolean metaFits(@NonNull TextView label, @NonNull TextView note, boolean ringShown) {
        View group = (View) label.getParent();
        int available = group != null && group.getWidth() > 0 ? group.getWidth()
            : Math.round((panelWidth - dp(16) - dp(4) - dp(28) - dp(8)) * (LEFT_SHARE / 2f));
        float needed = label.getPaint().measureText(label.getText().toString()) + dp(6)
            + note.getPaint().measureText(note.getText().toString());
        if (ringShown) needed += dp(8) + dp(6);
        return needed <= available;
    }

    /** The VAD has closed a segment: a ghost bar stands in for it until it transcribes. */
    public void onSegmentCaptured() {
        pending++;
        updateGhost();
    }

    /** A captured segment has come back, with text or without. */
    public void onSegmentSettled() {
        pending = Math.max(0, pending - 1);
        updateGhost();
    }

    /** One phrase, as it joins on, into the panel. */
    public void appendTranscript(@NonNull String typed) {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.append(typed);
    }

    /** The session has ended and the one cleanup pass is running. */
    public void setCleaningUp() {
        cleaningUp = true;
        warming = false;
        onMicrophoneClosed(true);
        setStatus(R.string.voice_input_cleaning_up);
        updateGhost();
    }

    /**
     * The cleanup has landed and waits with its changes marked; see
     * {@link VoiceTranscriptPanel#showCleaned}. {@code status} is what the strip says for it:
     * "Cleaned up", for the model's pass and the command formatter's alike.
     */
    public void showCleaned(@NonNull String cleaned, @StringRes int status) {
        cleaningUp = false;
        onMicrophoneClosed(true);
        updateGhost();
        cleanedStatus = status;
        VoiceTranscriptPanel view = panel;
        if (view != null) view.showCleaned(cleaned);
        setStatus(status);
    }

    /**
     * The text is final as heard and waits: no cleanup was asked for ("Ready"), or it came back
     * with nothing better or failed ("Kept as heard").
     */
    public void showAsHeard(@StringRes int text) {
        cleaningUp = false;
        onMicrophoneClosed(true);
        updateGhost();
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

    /** Insert or Copy has used the text: the controls go (resume too) and the header says what happened. */
    public void onActionDone(@StringRes int text) {
        VoiceTranscriptPanel view = panel;
        if (view != null) view.hideActions();
        setStatus(text);
    }

    /** The ghost bar stands in after the text while a phrase is still transcribing. */
    private void updateGhost() {
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        view.setPending(pending > 0);
    }

    /**
     * Seven lines where they fit; where the room is short (landscape, the keyboard up) the panel
     * keeps as many as fit in it with the header and the controls, and the top truncates.
     */
    private void fitPanel() {
        if (card == null) return;
        ReadAloudPanel reading = readingPanel;
        if (reading != null) {
            int line = reading.lineHeightPx();
            reading.setMaxVisibleLines(line <= 0 || room.isEmpty() ? VoiceTranscriptPanel.VISIBLE_LINES
                : (room.height() - dp(HEADER_DP) - reading.chromeHeightPx()) / line);
            return;
        }
        VoiceTranscriptPanel view = panel;
        if (view == null) return;
        int line = view.lineHeightPx();
        if (line <= 0 || room.isEmpty()) {
            view.setMaxVisibleLines(VoiceTranscriptPanel.VISIBLE_LINES);
            return;
        }
        int space = room.height() - dp(HEADER_DP) - view.chromeHeightPx();
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

    /** The x: a 16 dp glyph centred in its 32 dp, tinted {@code tint}, with a borderless ripple. */
    @NonNull
    private ImageView closeButton(@NonNull Context context, int tint, @StringRes int description) {
        ImageView button = new ImageView(context);
        button.setImageResource(R.drawable.ic_symbol_close);
        button.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(context.getString(description));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            button.setBackgroundResource(ripple.resourceId);
        }
        return button;
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
     * fades, and past 60% of the width, or on a fast fling that has covered a third of it, it leaves and reports the swipe. Touches
     * that land on the card never fall through to the terminal under it. Its place is a
     * translation from the frame's top left ({@link #setRest}); the swipe moves it from there.
     */
    static final class SwipeCard extends LinearLayout {
        private static final float DISMISS_FRACTION = 0.6f;
        /** A fling dismisses only after travelling this much of the width. */
        private static final float FLING_FRACTION = 0.35f;
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
            minFling = configuration.getScaledMinimumFlingVelocity() * 8;
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
            boolean flung = Math.abs(dx) > getWidth() * FLING_FRACTION
                && Math.abs(vx) > minFling && Math.signum(vx) == Math.signum(dx);
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
