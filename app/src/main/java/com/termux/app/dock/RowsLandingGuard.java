package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Side;
import com.termux.app.place.SideRows;
import com.termux.app.window.FrameGuards;

/**
 * The landing guard over rows (ADR 0030 section 4): a crossing's landing changes no row's
 * visibility or parent, except between pages of one side whose layouts differ, where the landing
 * may move rows to their new edges ({@link SideRows#landingMayMoveRows}). Each row's visibility
 * and parent are owned by the writer {@code rows} on the pipeline's guards; the landing's chrome
 * commit is that writer's write, and with the motion log on a change it makes on a Home and
 * Terminal landing counts {@code landing-wrote rows ...}. The status bar is not a row: standing
 * among the bottom rows it keeps the landing of the shared stack.
 */
public final class RowsLandingGuard {

    /** The rows read off the window. */
    public interface Rows {
        int count();
        @NonNull String name(int row);
        /** The row's visibility ({@code View.VISIBLE} ..), -1 while it has no view. */
        float visibility(int row);
        /** The id of the row's parent, -1 while it has none. */
        float parent(int row);
    }

    @NonNull private final Rows mRows;
    @Nullable private FrameGuards mGuards;
    @Nullable private FrameGuards.Writer mWriter;
    private int mSlots;
    private boolean mPending;
    private boolean mAllowed;

    public RowsLandingGuard(@NonNull Rows rows) {
        mRows = rows;
    }

    /** Registers every row's visibility and parent with {@code guards}. Once. */
    public void attach(@NonNull FrameGuards guards) {
        if (mGuards == guards) return;
        mGuards = guards;
        FrameGuards.Writer writer = guards.writer("rows");
        for (int i = 0; i < mRows.count(); i++) {
            final int row = i;
            writer.own(mRows.name(row) + ".visibility", () -> mRows.visibility(row));
            writer.own(mRows.name(row) + ".parent", () -> mRows.parent(row));
        }
        mSlots = 2 * mRows.count();
        mWriter = writer;
    }

    /**
     * A crossing from {@code from}'s side lands on {@code to}'s: the next chrome commit is its
     * landing's. {@code layoutsDiffer} says whether the two pages' layouts differ.
     */
    public void onLanding(@NonNull Side from, @NonNull Side to, boolean layoutsDiffer) {
        FrameGuards.Writer writer = mWriter;
        if (writer == null) return;
        writer.check();
        mAllowed = SideRows.landingMayMoveRows(from, to, layoutsDiffer);
        mPending = true;
    }

    /**
     * A chrome commit ended: what it wrote to the rows is this writer's (a crossing's commits
     * switch rows before the landing), and a landing's commit is checked once.
     */
    public void afterCommit() {
        FrameGuards guards = mGuards;
        FrameGuards.Writer writer = mWriter;
        if (guards == null || writer == null) return;
        if (!mPending) {
            for (int slot = 0; slot < mSlots; slot++) writer.wrote(slot);
            return;
        }
        mPending = false;
        if (!mAllowed) guards.setLanding(true);
        for (int slot = 0; slot < mSlots; slot++) writer.wrote(slot);
        if (!mAllowed) guards.setLanding(false);
    }
}
