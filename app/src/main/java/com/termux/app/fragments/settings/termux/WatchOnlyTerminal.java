package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;

/**
 * A small terminal a Settings page shows output in and the user can only watch: no cursor, no
 * focus, no keys, no selection. Its text is the largest that fits the column count across the
 * view, between {@link #TEXT_MIN_SP} and {@link #TEXT_MAX_SP}, so a script's lines rarely wrap.
 *
 * <p>The host runs the session and creates it with {@link #TRANSCRIPT_ROWS} rows of history (the
 * bound on the transcript); this view only shows it. A view is made per session, so a new run
 * starts on a clean one. A host that hands its session over ({@code owned} in {@link #watch}) has
 * it ended by {@link #release()}; a host whose session outlives the page (the Graphics benchmark)
 * passes false and only loses the view.
 *
 * <p>To learn when a run ends, forward the session client's {@code onSessionFinished} to
 * {@link #onSessionFinished}: the {@link Listener} then gets the exit status, once per session.
 */
public final class WatchOnlyTerminal extends FrameLayout {
    /** Told once when the watched session ends, on the main thread. */
    public interface Listener {
        /** {@code exitStatus} is the session's: the process's code, or the negated signal. */
        void onFinished(int exitStatus);
    }

    public static final float TEXT_MAX_SP = 11f;
    public static final float TEXT_MIN_SP = 7.5f;
    /** Rows of history a host gives the sessions it shows here. */
    public static final int TRANSCRIPT_ROWS = 600;
    public static final int MIN_TRANSCRIPT_ROWS = 100;
    public static final int MAX_TRANSCRIPT_ROWS = 2000;
    private static final int CORNER_DP = 8;
    private static final int INSET_DP = 6;

    private final int columns;
    @Nullable private TerminalView view;
    @Nullable private TerminalSession watched;
    private boolean owned;
    private boolean finishedSent;
    @Nullable private Listener listener;

    public WatchOnlyTerminal(@NonNull Context context, int columns) {
        super(context);
        this.columns = Math.max(1, columns);
        int inset = TaiBenchViews.dp(context, INSET_DP);
        setPadding(inset, inset, inset, inset);
        GradientDrawable ground = new GradientDrawable();
        ground.setCornerRadius(TaiBenchViews.dp(context, CORNER_DP));
        setBackground(ground);
        setClipToOutline(true);
        // The text size follows the width; set from layout, so it is posted.
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) post(this::fitText);
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Nullable
    public TerminalSession session() {
        return watched;
    }

    /** Redraws the shown session; call from the session client's text-changed callback. */
    public void onScreenUpdated() {
        TerminalView v = view;
        if (v != null) v.onScreenUpdated();
    }

    /**
     * Shows {@code session}, or nothing for null. The same session again changes nothing; another
     * one replaces the view, and the previous session is ended first if it was owned.
     */
    public void watch(@Nullable TerminalSession session, boolean owned, @Nullable CharSequence description) {
        if (view != null && view.getCurrentSession() == session) return;
        release();
        if (session == null) return;
        Context context = getContext();
        TerminalView made = new TerminalView(context, null);
        made.setTerminalViewClient(new ReadOnlyClient());
        made.setTextSize(textSizePx(context, columns, availableWidth()));
        made.setCursorSuppressed(true);
        // Nothing is typed into the run: no focus, so no keyboard and no keys reach it.
        made.setFocusable(false);
        made.setFocusableInTouchMode(false);
        if (description != null) made.setContentDescription(description);
        made.attachSession(session);
        // The ground is the terminal's own, so its colours read the same in a light or dark theme.
        // The view paints it too: it declares itself opaque and leaves default cells unpainted.
        int ground = background(session);
        ((GradientDrawable) getBackground()).setColor(ground);
        made.setBackgroundColor(ground);
        addView(made, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view = made;
        watched = session;
        this.owned = owned;
        finishedSent = false;
    }

    /** Takes the view out, and ends the session when this view owns it. Safe to repeat. */
    public void release() {
        TerminalView v = view;
        TerminalSession s = watched;
        view = null;
        watched = null;
        if (v != null) removeView(v);
        if (owned && s != null) s.finishIfRunning();
        owned = false;
    }

    /** Forward the session client's {@code onSessionFinished} here; tells the listener once. */
    public void onSessionFinished(@NonNull TerminalSession finished) {
        if (finished != watched || finishedSent) return;
        finishedSent = true;
        Listener l = listener;
        if (l != null) l.onFinished(finished.getExitStatus());
    }

    private int availableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private void fitText() {
        TerminalView v = view;
        if (v == null || v.getParent() != this) return;
        v.setTextSize(textSizePx(getContext(), columns, availableWidth()));
    }

    private static int textSizePx(@NonNull Context context, int columns, int width) {
        float max = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_MAX_SP,
            context.getResources().getDisplayMetrics());
        float min = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_MIN_SP,
            context.getResources().getDisplayMetrics());
        if (width <= 0) return Math.round(max);
        Paint paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(100f);
        // The renderer's cell is the width of "X" (TerminalRenderer), which scales with the size.
        return fitTextSize(width, columns, paint.measureText("X") / 100f, min, max);
    }

    /**
     * The text size, in pixels, at which {@code columns} cells fit {@code width} pixels, kept
     * between {@code min} and {@code max}; the largest before there is a width.
     * {@code cellPerPixelOfSize} is one cell's width at text size 1.
     */
    @VisibleForTesting
    static int fitTextSize(int width, int columns, float cellPerPixelOfSize, float min, float max) {
        if (width <= 0) return Math.round(max);
        float fits = (float) Math.floor(width / (columns * cellPerPixelOfSize));
        return Math.round(Math.max(min, Math.min(max, fits)));
    }

    /** The rows of history a session keeps, held between the bounds. */
    @VisibleForTesting
    static int boundedRows(int requested) {
        return Math.max(MIN_TRANSCRIPT_ROWS, Math.min(MAX_TRANSCRIPT_ROWS, requested));
    }

    private static int background(@NonNull TerminalSession session) {
        TerminalEmulator emulator = session.getEmulator();
        int color = emulator != null ? emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
            : TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        return color | 0xFF000000;
    }

    /** Shows output and scrolls; takes no keys, no text and no selection. */
    private static final class ReadOnlyClient extends TermuxTerminalViewClientBase {
        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
            return true;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent e) {
            return true;
        }

        @Override
        public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
            return true;
        }

        @Override
        public boolean onLongPress(MotionEvent event) {
            return true;
        }

        @Override
        public boolean isTerminalViewSelected() {
            return false;
        }
    }
}
