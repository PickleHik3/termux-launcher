package com.termux.app.terminal.find;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalFindOverlay;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs scrollback search as a strip above the dock with the matches lit in the transcript itself.
 *
 * <p>Search used to open on the sheet plane: a full-screen blurred backdrop with a list of snippets
 * on it, which took the transcript away from the reader at the exact moment they were trying to
 * read it. Here the terminal stays where it is, every hit is highlighted in place, and the strip
 * costs one text line at the bottom. Once a query is committed the session is vim's normal mode
 * over the transcript — n/N between matches, motions, charwise, linewise and block selection, and a
 * yank that ends the session with the text on the clipboard.</p>
 *
 * <p>The strip never takes focus, so the terminal keeps its {@code InputConnection} and no system
 * IME is summoned. Where there is no keyboard at all to type into, the host falls back to the
 * compact sheet surface instead.</p>
 */
public final class TerminalFindCoordinator implements TerminalFindController.Host {

    /** How much transcript to keep around the current match when scrolling it into view. */
    private static final int REVEAL_MARGIN_ROWS = 2;
    private static final long ENTER_DURATION_MS = 160L;
    private static final long EXIT_DURATION_MS = 100L;

    public interface Host {
        /** Full-width container above the dock the strip is added to, or null before layout. */
        @Nullable ViewGroup findBarHost();

        /** The pane being searched, or null when there is none. */
        @Nullable TerminalView terminalView();

        /** Glass background for the strip, from the same builder as the other dock surfaces. */
        @NonNull Drawable barBackground();

        /** Query, dim and caret colours, in that order. */
        @NonNull int[] barColors();

        /**
         * Make a keyboard available for typing into the strip, and report whether one is. False
         * sends the session to {@link #showFallbackSearch()}.
         */
        boolean ensureTypingKeyboard();

        /** Installs (or with null restores) the in-app keyboard's interceptor slot. */
        void installFindInterceptor(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor);

        /** The compact sheet search, for when no keyboard can be raised to type into the strip. */
        void showFallbackSearch();

        void copyToClipboard(@NonNull String text);

        /** Told what was yanked, so the host can say so however it says things. */
        void onYanked(@NonNull String text);

        boolean isReducedMotionEnabled();
    }

    @NonNull private final Host host;
    @NonNull private final TerminalFindController controller = new TerminalFindController();
    @NonNull private final TerminalFindOverlay overlay = new TerminalFindOverlay();
    @Nullable private TerminalFindBarView bar;
    @Nullable private TerminalView pane;

    public TerminalFindCoordinator(@NonNull Host host) {
        this.host = host;
    }

    public boolean isActive() {
        return controller.isActive();
    }

    /** Opens a session on the given pane, or falls back when there is nothing to type with. */
    public boolean begin(@Nullable TerminalView terminalView) {
        if (terminalView == null || terminalView.mEmulator == null) return false;
        if (controller.isActive()) {
            controller.cancel();
            return true;
        }
        if (!host.ensureTypingKeyboard()) {
            host.showFallbackSearch();
            return true;
        }
        ViewGroup container = host.findBarHost();
        if (container == null) {
            host.showFallbackSearch();
            return true;
        }
        pane = terminalView;
        showBar(container, terminalView);
        host.installFindInterceptor(controller);
        return controller.begin(snapshot(terminalView.mEmulator), this);
    }

    /** Ends any running session; safe to call from pause, teardown and pane switches. */
    public void cancel() {
        controller.cancel();
    }

    /** @return true when the stroke belonged to a running session. */
    public boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        return controller.handleKeyDown(keyCode, event);
    }

    /** @return true when the committed code point belonged to a running session. */
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        return controller.handleCodePoint(codePoint, ctrlDown);
    }

    // ------------------------------------------------------------------ TerminalFindController.Host

    @Override
    public void onFindChanged(@NonNull TerminalFindModel model) {
        TerminalFindBarView view = bar;
        if (view != null) {
            view.bind(model.query(), model.counter(), modeTag(model), hint(model),
                model.mode() == TerminalFindModel.Mode.TYPING);
        }
        TerminalView terminalView = pane;
        if (terminalView == null) return;
        applyOverlay(model);
        terminalView.setFindOverlay(overlay);
        Integer focus = model.focusRow();
        if (focus != null) terminalView.revealBufferRow(focus, REVEAL_MARGIN_ROWS);
    }

    @Override
    public void onFindEnded(@Nullable String yankedText) {
        host.installFindInterceptor(null);
        TerminalView terminalView = pane;
        if (terminalView != null) terminalView.setFindOverlay(null);
        pane = null;
        hideBar();
        if (yankedText != null && !yankedText.isEmpty()) {
            host.copyToClipboard(yankedText);
            host.onYanked(yankedText);
        }
    }

    // ------------------------------------------------------------------------------------ drawing

    private void applyOverlay(@NonNull TerminalFindModel model) {
        overlay.clearSpans();
        List<TerminalFindModel.Match> matches = model.matches();
        for (TerminalFindModel.Match match : matches) {
            overlay.spans.add(new TerminalFindOverlay.Span(match.row, match.startColumn,
                match.endColumn));
        }
        overlay.currentSpan = model.currentIndex();
        boolean navigating = model.mode() != TerminalFindModel.Mode.TYPING;
        overlay.cursorVisible = navigating;
        overlay.cursorRow = model.cursorRow();
        overlay.cursorColumn = model.cursorColumn();
        overlay.anchorRow = model.anchorRow();
        overlay.anchorColumn = model.anchorColumn();
        switch (model.selection()) {
            case CHAR: overlay.selectionMode = TerminalFindOverlay.SELECTION_CHAR; break;
            case LINE: overlay.selectionMode = TerminalFindOverlay.SELECTION_LINE; break;
            case BLOCK: overlay.selectionMode = TerminalFindOverlay.SELECTION_BLOCK; break;
            default: overlay.selectionMode = TerminalFindOverlay.SELECTION_NONE; break;
        }
    }

    private void showBar(@NonNull ViewGroup container, @NonNull TerminalView terminalView) {
        TerminalFindBarView view = bar;
        if (view == null) {
            view = new TerminalFindBarView(container.getContext());
            bar = view;
        }
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        int[] colors = host.barColors();
        view.setColors(colors[0], colors[1], colors[2]);
        Typeface typeface = terminalView.getTerminalTypeface();
        view.setTerminalTextAppearance(typeface, terminalView.getTerminalTextSizePixels());
        view.bind("", "", "", "", true);
        container.setBackground(host.barBackground());
        container.addView(view, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL));
        container.setVisibility(View.VISIBLE);
        if (host.isReducedMotionEnabled()) {
            container.setAlpha(1f);
            container.setTranslationY(0f);
            return;
        }
        container.setAlpha(0f);
        container.setTranslationY(container.getResources().getDisplayMetrics().density * 10f);
        container.animate().alpha(1f).translationY(0f).setDuration(ENTER_DURATION_MS).start();
    }

    private void hideBar() {
        TerminalFindBarView view = bar;
        if (view == null) return;
        ViewGroup container = view.getParent() instanceof ViewGroup
            ? (ViewGroup) view.getParent() : null;
        if (container == null) return;
        Runnable detach = () -> {
            container.removeView(view);
            container.setVisibility(View.GONE);
            container.setAlpha(1f);
            container.setTranslationY(0f);
        };
        if (host.isReducedMotionEnabled()) {
            detach.run();
            return;
        }
        container.animate().alpha(0f).setDuration(EXIT_DURATION_MS).withEndAction(detach).start();
    }

    @NonNull
    private String modeTag(@NonNull TerminalFindModel model) {
        switch (model.selection()) {
            case CHAR: return "VISUAL";
            case LINE: return "V-LINE";
            case BLOCK: return "V-BLOCK";
            default: break;
        }
        return model.mode() == TerminalFindModel.Mode.NAVIGATE ? "NAV" : "";
    }

    @NonNull
    private String hint(@NonNull TerminalFindModel model) {
        return model.mode() == TerminalFindModel.Mode.TYPING ? "search scrollback" : "n/N · v · y";
    }

    /**
     * The transcript as rows, in the emulator's own row coordinates, so a match's row can be handed
     * straight back to the view. Taken once per session: a search reads what was on screen when it
     * was asked, and re-reading under a running command would move every match under the cursor.
     */
    @NonNull
    public static List<TerminalFindModel.Line> snapshot(@NonNull TerminalEmulator emulator) {
        TerminalBuffer screen = emulator.getScreen();
        int first = -screen.getActiveTranscriptRows();
        List<TerminalFindModel.Line> lines = new ArrayList<>(
            screen.getActiveTranscriptRows() + emulator.mRows);
        for (int row = first; row < emulator.mRows; row++) {
            lines.add(new TerminalFindModel.Line(row,
                screen.getSelectedText(0, row, emulator.mColumns, row, false)));
        }
        return lines;
    }
}
