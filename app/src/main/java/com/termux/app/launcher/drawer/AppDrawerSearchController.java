package com.termux.app.launcher.drawer;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.terminal.CommandPaletteSoftKeyDecision;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import juloo.keyboard2.KeyValue;

/**
 * The drawer's search: one {@link AppDrawerSearchModel}, three intake channels and the ranking that
 * turns a query into the grid's contents.
 *
 * <p>The three channels are the palette's, wired the same way and for the same reason — the drawer
 * has no focused text field, so nothing arrives here by itself:
 *
 * <ul>
 *   <li><b>The in-app keyboard</b>, through {@link TerminalKeyEventHandler.KeyValueInterceptor}.
 *       That slot holds exactly one interceptor; sharing it with the palette is safe because
 *       {@code TerminalCommandPaletteController.show()} closes the drawer before installing its own.
 *   <li><b>Hardware keys</b>, through {@link #handleKeyDown} on the view client's key path.
 *   <li><b>System-IME text</b>, through {@link #handleCodePoint}. A third-party keyboard sends an
 *       unfocused overlay no key events at all — only committed text — so without this channel the
 *       drawer would open and then ignore everything typed into it.
 * </ul>
 *
 * <p>The IME decision table is {@link CommandPaletteSoftKeyDecision#decide} <em>verbatim</em>, not a
 * twin: which code points are text, which are Enter, which are a backspace an IME chose to commit
 * rather than send, and the rule that nothing leaks to the shell behind an open overlay are one set
 * of answers for this app. {@code capturing} is passed {@code false} — the drawer has no capture
 * mode — and that is the only difference from the palette's call.
 *
 * <p>Ranking runs on the calling (main) thread. {@link LauncherRankingEngine#filterAndRank} is pure
 * and already run per keystroke by the dock's own search over the same catalogue, so the work is
 * known and a thread hop would only buy the grid a frame of stale results. An empty query is not
 * ranked at all: it is the catalogue in provider order, which is what "no filter" has to mean if
 * the grid is to look the same every time it opens.
 */
public final class AppDrawerSearchController
    implements TerminalKeyEventHandler.KeyValueInterceptor {

    /**
     * Fuzzy-match floor. The dock's own app search and the palette's Apps section both rank at 70;
     * a drawer that disagreed would answer the same query two ways in one launcher.
     */
    public static final int SEARCH_TOLERANCE = 70;

    /** The drawer around the search: whether typing may reach it, and what Enter and Esc mean. */
    public interface Host {

        /** True while the drawer is open, i.e. while the three channels may claim anything. */
        boolean isSearchActive();

        /** Enter, on any channel: act on the first result. */
        void onSearchCommitRequested();

        /** Esc, or Back with an empty query: put the drawer away. */
        void onSearchDismissRequested();
    }

    /** The grid's half: a new ranked list, and whether the query behind it changed. */
    public interface ResultsListener {

        /**
         * @param results      the ranked list, or the whole catalogue when the query is empty
         * @param queryChanged true when the query itself moved, which is what resets the scroll,
         *                     disarms the close gesture and invalidates any anchored popup
         */
        void onSearchResultsChanged(@NonNull List<LauncherAppEntry> results, boolean queryChanged);
    }

    private final AppDrawerSearchModel mModel = new AppDrawerSearchModel();

    @NonNull private List<LauncherAppEntry> mCatalogue = Collections.emptyList();
    @NonNull private List<LauncherAppEntry> mResults = Collections.emptyList();
    @Nullable private Host mHost;
    @Nullable private ResultsListener mListener;
    private boolean mTextFieldOwnsInput;

    public void setHost(@Nullable Host host) {
        mHost = host;
    }

    public void setResultsListener(@Nullable ResultsListener listener) {
        mListener = listener;
    }

    /**
     * The Android-keyboard search: a focused text field owns typing, deleting and caret movement,
     * and reports the result through {@link #replaceQuery}. The hardware-key channel then keeps
     * only the strokes that mean something to the drawer rather than to the text — Enter and Esc —
     * and the in-app keyboard's interceptor is left to the yielded keyboard.
     */
    public void setTextFieldOwnsInput(boolean owns) {
        mTextFieldOwnsInput = owns;
    }

    public boolean textFieldOwnsInput() {
        return mTextFieldOwnsInput;
    }

    /** Esc on the text field: the same press the key channel would have spent inside the drawer. */
    public void requestDismiss() {
        dismiss();
    }

    /** The text field's whole query after an edit; ranked only when the text itself moved. */
    public void replaceQuery(@NonNull String text, int caret) {
        boolean textChanged = !text.contentEquals(mModel.query());
        if (!mModel.replace(text, caret)) return;
        if (textChanged) rank(true);
        else notifyCaretOnly();
    }

    // ------------------------------------------------------------------ state

    @NonNull
    public String query() {
        return mModel.query();
    }

    public int caret() {
        return mModel.caret();
    }

    public boolean hasQuery() {
        return !mModel.isEmpty();
    }

    @NonNull
    public List<LauncherAppEntry> results() {
        return mResults;
    }

    @Nullable
    public LauncherAppEntry firstResult() {
        return mResults.isEmpty() ? null : mResults.get(0);
    }

    /**
     * The catalogue to rank against, pushed in by the grid whenever the provider has loaded or
     * changed. Re-ranks the current query against it, so a package install lands in a filtered grid
     * without the user retyping anything.
     */
    public void setCatalogue(@NonNull List<LauncherAppEntry> catalogue) {
        mCatalogue = new ArrayList<>(catalogue);
        rank(false);
    }

    /**
     * Empties the query.
     *
     * @return true when there was a query to empty — which is what makes Back clear the search
     *     before it closes the drawer, rather than doing both at once
     */
    public boolean clearQuery() {
        if (!mModel.clear()) return false;
        rank(true);
        return true;
    }

    /**
     * Back to a fresh search: no query, the whole catalogue, reported as a change whether or not
     * there was a query to drop. A drawer that has closed reopens showing its list from the top, and
     * that is the grid's cue to put it there.
     */
    public void reset() {
        mModel.clear();
        rank(true);
    }

    private boolean isActive() {
        Host host = mHost;
        return host != null && host.isSearchActive();
    }

    private void rank(boolean queryChanged) {
        // filterAndRank already answers an empty query with a copy of the input list, in order, so
        // the "no filter" case needs no branch of its own here.
        mResults = LauncherRankingEngine.filterAndRank(mCatalogue, mModel.query(), SEARCH_TOLERANCE);
        notifyResults(queryChanged);
    }

    /** The query is unchanged and so is the ranking; only the pill has something new to draw. */
    private void notifyCaretOnly() {
        notifyResults(false);
    }

    private void notifyResults(boolean queryChanged) {
        ResultsListener listener = mListener;
        if (listener != null) listener.onSearchResultsChanged(mResults, queryChanged);
    }

    // ------------------------------------------------------------------ in-app keyboard

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        if (!isActive()) return false;
        switch (value.getKind()) {
            case Char:
                if (!ctrl && !alt) insert(String.valueOf(value.getChar()));
                return true;
            case String:
                if (!ctrl && !alt) insert(value.getString());
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: insert(" "); break;
                    case BACKSPACE: backspace(); break;
                    default: break;
                }
                return true;
            case Keyevent:
                handleKeyCode(value.getKeyevent());
                return true;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) commit();
                return true;
            case Slider:
                switch (value.getSlider()) {
                    case Cursor_left:
                        moveCaret(-Math.max(1, value.getSliderRepeat()));
                        break;
                    case Cursor_right:
                        moveCaret(Math.max(1, value.getSliderRepeat()));
                        break;
                    default:
                        break;
                }
                return true;
            default:
                // Everything else is swallowed: while a full-screen drawer is up nothing typed may
                // reach the shell behind it.
                return true;
        }
    }

    // ------------------------------------------------------------------ hardware keys

    /**
     * A hardware or external-keyboard stroke, claimed before the terminal writes it.
     *
     * @return true when the drawer consumed the stroke
     */
    public boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (!isActive()) return false;
        if (mTextFieldOwnsInput) {
            // Letters, backspace and the arrows are the field's; only the strokes that act on the
            // drawer are claimed, and on the down stroke alone so the field never sees a stray up.
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    commit();
                    return true;
                case KeyEvent.KEYCODE_ESCAPE:
                    dismiss();
                    return true;
                default:
                    return false;
            }
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (handleKeyCode(keyCode)) return true;
        // A chord is a shortcut, not a letter; swallowed rather than typed, as the palette does.
        if (event.isCtrlPressed() || event.isAltPressed()) return true;
        int unicode = event.getUnicodeChar();
        if (unicode >= ' ') insert(new String(Character.toChars(unicode)));
        return true;
    }

    // ------------------------------------------------------------------ system IME

    /**
     * Text committed by a system IME, claimed before the terminal writes it.
     *
     * @param ctrlDown a latched or held Ctrl already applied to the code point
     * @return true when the drawer consumed the code point
     */
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        CommandPaletteSoftKeyDecision.Action action =
            CommandPaletteSoftKeyDecision.decide(isActive(), false, codePoint, ctrlDown);
        switch (action) {
            case IGNORE:
                return false;
            case APPEND:
                insert(new String(Character.toChars(codePoint)));
                return true;
            case COMMIT:
                commit();
                return true;
            case BACKSPACE:
                backspace();
                return true;
            case COLLAPSE:
                dismiss();
                return true;
            case SWALLOW:
            default:
                return true;
        }
    }

    // ------------------------------------------------------------------ editing

    private boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveCaret(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveCaret(1);
                return true;
            case KeyEvent.KEYCODE_DEL:
                backspace();
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                commit();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                dismiss();
                return true;
            default:
                return false;
        }
    }

    private void insert(@NonNull String text) {
        // A leading space matches nothing and is almost always the tail of the gesture that opened
        // the drawer; the palette drops it for the same reason.
        if (mModel.isEmpty() && text.trim().isEmpty()) return;
        if (mModel.insert(text)) rank(true);
    }

    private void backspace() {
        // A backspace with nothing left to delete is swallowed rather than closing the drawer: the
        // key that empties the query is the one most likely to be held down, and a drawer that
        // vanished on the repeat after the last character would be unusable.
        if (mModel.backspace()) rank(true);
    }

    private void moveCaret(int steps) {
        // The caret moved but the ranking did not: redraw the pill, re-rank nothing.
        if (mModel.moveCursor(steps)) notifyCaretOnly();
    }

    private void commit() {
        Host host = mHost;
        if (host != null) host.onSearchCommitRequested();
    }

    private void dismiss() {
        Host host = mHost;
        if (host != null) host.onSearchDismissRequested();
    }
}
