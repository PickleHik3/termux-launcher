package com.termux.app.statusbar;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalWindowBar;
import com.termux.app.wall.pageswitch.MarkControls;
import com.termux.app.wall.pageswitch.PageBarModel;

/**
 * The Glyphs look's hold: a window chip held still opens the window's controls over the window
 * bar ({@link ChipControlsView}), as a held mark does; a lift on one acts through the same
 * {@link MarksLineView.WindowControls}, and Move lifts the window into the finger at once.
 */
public final class ChipControlsHold implements TerminalWindowBar.OnWindowChipHeldListener {

    /** What the hold asks of the activity. */
    public interface Host {
        /** The window chip {@code index} stands for; null when it is not a row window (a Display page's apps). */
        @Nullable PageBarModel.Entry heldWindow(int index);

        /** The scheme's accent: the control under the finger. */
        int accent();

        @Nullable MarksLineView.TitleGlass glass();

        @NonNull MarksLineView.WindowControls controls();
    }

    @NonNull private final TerminalWindowBar mBar;
    @NonNull private final Host mHost;
    @Nullable private ChipControlsView mView;
    /** The window the controls are open for; null while none are. */
    @Nullable private PageBarModel.Entry mHeld;

    public ChipControlsHold(@NonNull TerminalWindowBar bar, @NonNull Host host) {
        mBar = bar;
        mHost = host;
    }

    @Override
    public void onChipHeld(int index, @NonNull View chip, float rawX) {
        mHeld = null;
        PageBarModel.Entry entry = mHost.heldWindow(index);
        if (entry == null) return;
        if (mView == null) mView = new ChipControlsView(mBar.getContext());
        if (mView.show(mBar, PageBarModel.plainLabel(entry.label),
            MarkControls.controlsFor(entry.kind, mHost.controls().scales(entry)), rawX, mHost.accent(), mHost.glass())) {
            mHeld = entry;
        }
    }

    /**
     * Opens {@code entry}'s controls with no finger down, as if its chip were held: the window's
     * app asked for its window menu. A press on a control acts; anywhere else puts them away.
     * False when there is no chrome layer to stand in.
     */
    public boolean open(@NonNull PageBarModel.Entry entry) {
        mHeld = null;
        if (mView == null) mView = new ChipControlsView(mBar.getContext());
        return mView.showPinned(mBar, PageBarModel.plainLabel(entry.label),
            MarkControls.controlsFor(entry.kind, mHost.controls().scales(entry)), mHost.accent(),
            mHost.glass(), (control, x, y, pointer) ->
                mBar.post(() -> mHost.controls().onWindowControl(entry, control, x, y, pointer)));
    }

    @Override
    public boolean onHeldMove(float rawX, float rawY, int pointerId) {
        PageBarModel.Entry entry = mHeld;
        ChipControlsView view = mView;
        if (entry == null || view == null) return false;
        view.move(rawX);
        if (view.controlAt(rawX) != MarkControls.Control.MOVE) return false;
        // Move lifts the window into the finger at once: the carry takes the stream.
        view.hide();
        mHeld = null;
        mHost.controls().onWindowControl(entry, MarkControls.Control.MOVE, rawX, rawY, pointerId);
        return true;
    }

    @Override
    public boolean onHeldLift(float rawX) {
        PageBarModel.Entry entry = mHeld;
        ChipControlsView view = mView;
        mHeld = null;
        if (entry == null || view == null) return false;
        MarkControls.Control control = view.controlAt(rawX);
        view.hide();
        if (control == null) return false;
        // After the bar has let go of the chip: the action may hand it a new row.
        mBar.post(() -> mHost.controls().onWindowControl(entry, control, rawX, 0f, -1));
        return true;
    }

    @Override
    public void onHeldEnd() {
        mHeld = null;
        if (mView != null) mView.hide();
    }
}
