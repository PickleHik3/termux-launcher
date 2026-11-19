package com.termux.app.statusbar;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.terminal.TerminalWindowBar;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import java.util.List;

/**
 * Home's float chips on the status bar (ADR 0030 section 2): the window chips' own views, a row of
 * pills and a column for a bar standing on a side, in a strip of their own beside the
 * Workspace's. This only dresses them and hands them their chips; which chips they are is
 * {@link com.termux.app.chrome.HomeFloatChips}'s answer, and where the strip stands each frame is
 * the top stack's ({@link TopStackRenderer}). A tap is handed to the listener by its index.
 */
public final class HomeFloatChipBar {

    /** A tap on the chip at {@code index}. */
    public interface Listener {
        void onHomeFloatChipTapped(int index);
    }

    @Nullable private final TerminalWindowBar mRow;
    @Nullable private final StatusBarWindowColumn mColumn;
    private boolean mVertical;
    private boolean mEmpty = true;

    public HomeFloatChipBar(@Nullable TerminalWindowBar row, @Nullable StatusBarWindowColumn column,
                            @NonNull Listener listener) {
        mRow = row;
        mColumn = column;
        if (row != null) {
            row.setOnWindowSelectedListener(listener::onHomeFloatChipTapped);
            // A float's chip only brings it forward: no close, no new window, no reorder.
            row.setSelectedChipOffersClose(false);
            row.setCreateButtonShown(false);
            row.setMovableChipCount(0);
        }
        if (column != null) column.setListener(listener::onHomeFloatChipTapped);
    }

    /** What the chips stand on and their accent, as the window chips are dressed. */
    public void dress(@Nullable ChromeInk ink, int accent, boolean vertical) {
        mVertical = vertical;
        if (mRow != null) {
            mRow.setChromeInk(ink);
            mRow.setPlaceAccent(accent);
        }
        if (mColumn != null) {
            mColumn.setChromeInk(ink);
            mColumn.setPlaceAccent(accent);
        }
        applyVisibility();
    }

    /** The chips, dressed as the window chips' items are, and the one in front (-1 for none). */
    public void setChips(@NonNull List<WindowItem> items, int selected) {
        mEmpty = items.isEmpty();
        if (mRow != null) mRow.setWindows(items, selected);
        if (mColumn != null) mColumn.setWindows(items, selected);
        applyVisibility();
    }

    /** The row's styles the window chips' row wears too. */
    public void setLazyMode(boolean lazy) {
        if (mRow != null) mRow.setLazyMode(lazy);
    }

    public void setDeckLook(@Nullable com.termux.app.terminal.DeckLook deck) {
        if (mRow != null) mRow.setDeckLook(deck);
    }

    public void setScreenKeepOut(@Nullable StatusRowKeepOut.Screen screen) {
        if (mRow != null) mRow.setScreenKeepOut(screen);
    }

    /** Where the chip at {@code index} stands on screen: where a minimised float flies to. */
    public boolean chipBoundsOnScreen(int index, @NonNull Rect out) {
        if (mVertical) return mColumn != null && mColumn.chipBoundsOnScreen(index, out);
        return mRow != null && mRow.chipBoundsOnScreen(index, out);
    }

    /** The row along a row, the column down a side; neither while there is no float to show. */
    private void applyVisibility() {
        show(mRow, !mEmpty && !mVertical);
        show(mColumn, !mEmpty && mVertical);
    }

    private static void show(@Nullable View view, boolean shown) {
        if (view == null) return;
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }
}
