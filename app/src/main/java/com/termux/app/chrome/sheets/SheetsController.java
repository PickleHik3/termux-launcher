package com.termux.app.chrome.sheets;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.notice.TerminalDress;
import com.termux.app.surfaces.SurfaceEditorController;

/**
 * The sheets' own code (ADR 0018 §1, wave H step one, D17), moved out of the activity line for
 * line: the notices first. It reaches the activity only through {@link Host}.
 */
public final class SheetsController {

    /** What only the activity gives the sheets. */
    public interface Host {
        // -------------------------------------------------------------- window

        /** The activity, as the Activity the notice pill is hosted in and its strings come from. */
        @NonNull Activity activity();
        /** Whether the activity is visible ({@code mIsVisible}). */
        boolean isActivityVisible();

        // -------------------------------------------------------------- notices

        /** What the terminal is wearing, which the notice pill reads through. */
        @NonNull TerminalDress.Source terminalDressSource();
        /** The background-process stack below the pill; null until it is made. */
        @Nullable com.termux.app.statusbar.BackgroundProcessStackView backgroundProcessStack();
        /** The surface editor, whose being open holds Always on's notice back. */
        SurfaceEditorController surfaceEditor();
        /** Raises a transient notice on the pill (quiet while notices are suppressed). */
        void showToast(String text, boolean longDuration);
    }

    @NonNull private final Host mHost;

    public SheetsController(@NonNull Host host) {
        mHost = host;
    }

    // ================================================================== notices

    /** Always on's notice, held while the editor that turned it on is still up. */
    private final com.termux.app.place.HomeAlwaysOnNotice mAlwaysOnNotice =
        new com.termux.app.place.HomeAlwaysOnNotice();
    /** Height the in-app notice chip is occupying above the background stack; 0 when not showing. */
    private int mAppNoticeOccupancyPx;
    /**
     * Nesting depth of {@link #runWithoutNotices}. Creating a window or a pane touches the focused
     * shell, which several unrelated listeners read as a session change worth announcing; the user
     * sees the result of the split on screen already, so the whole operation runs silent.
     */
    private int mNoticeSuppressionDepth;
    /** A rebuild put the arrangement back and its notice waits for the window to be in front. */
    private boolean mWindowsRestoredNoticeOwed;

    /** Always on's notice. */
    @NonNull
    public com.termux.app.place.HomeAlwaysOnNotice alwaysOnNotice() {
        return mAlwaysOnNotice;
    }

    /** Height the notice pill occupies now, px; 0 while none is up. */
    public int appNoticeOccupancyPx() {
        return mAppNoticeOccupancyPx;
    }

    /** A rebuild put the arrangement back: its notice is owed. */
    public void oweWindowsRestoredNotice() {
        mWindowsRestoredNoticeOwed = true;
    }

    /**
     * Creates the notice pill up front, hands it the terminal it will be floating over, and joins
     * it to the column below, so the background-process stack moves down under it while a notice is
     * up rather than being drawn over.
     */
    public void ensureAppNoticeHost() {
        com.termux.app.notice.AppNoticeHostView host = AppNotice.hostFor(mHost.activity());
        if (host == null) return;
        // Read through, never copied: the corner knob, the opacity slider and the glass tint all
        // move while the surface editor is open, and the pill has to be wearing what the terminal
        // is wearing at the moment it is raised.
        host.setTerminalDressSource(mHost.terminalDressSource());
        host.setOccupancyListener(height -> {
            mAppNoticeOccupancyPx = height;
            applyNoticeColumnOffsets();
        });
    }

    /** The column under the pill: the background stack sits directly below whatever it is showing. */
    private void applyNoticeColumnOffsets() {
        com.termux.app.statusbar.BackgroundProcessStackView stack = mHost.backgroundProcessStack();
        if (stack != null)
            stack.setNoticeOccupancyPx(mAppNoticeOccupancyPx);
    }

    /**
     * Shows the "Windows restored" notice a rebuild owes, once the activity is in front
     * ({@link com.termux.app.terminal.ArrangementRecovery#restoredNoticeDue}); called again when
     * the window gains the focus. Posted, so it goes up after the frame the wall's start page and
     * the rebuilt row are laid out in, on whichever half is in front, for its ordinary hold.
     */
    public void showWindowsRestoredNoticeIfDue() {
        Activity activity = mHost.activity();
        if (!com.termux.app.terminal.ArrangementRecovery.restoredNoticeDue(
                mWindowsRestoredNoticeOwed, mHost.isActivityVisible(), activity.hasWindowFocus())) return;
        mWindowsRestoredNoticeOwed = false;
        View content = activity.findViewById(android.R.id.content);
        Runnable show = () -> com.termux.app.notice.AppNotice.show(activity,
            R.string.notice_windows_restored);
        if (content != null) content.post(show);
        else show.run();
    }

    /** The one line always on says about the grid: what moved, or that nothing could. */
    public void showAlwaysOnNotice(
            @Nullable com.termux.app.launcher.widget.WidgetReflowReport report) {
        Activity activity = mHost.activity();
        String text;
        if (report == null) {
            text = activity.getString(R.string.home_always_on_no_rows);
        } else if (report.isEmpty()) {
            text = activity.getString(R.string.home_always_on_nothing_moved);
        } else {
            text = activity.getResources().getQuantityString(R.plurals.home_always_on_moved,
                report.movedCount(), report.movedCount());
        }
        // Turned on from the Layout editor, it waits for the editor to close (onClosed), so it
        // never stands over the editor's controls.
        String now = mAlwaysOnNotice.offer(text, mHost.surfaceEditor().isActive());
        if (now != null) mHost.showToast(now, false);
    }

    /**
     * Raises a notice the terminal is the subject of, on the same pill as everything else, held for
     * as long as its kind is worth reading.
     *
     * <p>These used to have a chip of their own in the terminal's top-leading corner, with one hold
     * for all of them: a refusal the user has to act on and a window number they have already seen
     * both left after 1400ms. On the pill each says how long it is worth.
     */
    public void showTerminalNotice(@Nullable String text, @NonNull AppNoticeItem.Hold hold) {
        Activity activity = mHost.activity();
        if (text == null || text.isEmpty() || activity.isFinishing() || mNoticeSuppressionDepth > 0)
            return;
        AppNotice.held(activity, hold == AppNoticeItem.Hold.REFUSAL
            ? AppNoticeItem.Kind.WARNING
            : AppNoticeItem.Kind.INFO, text, hold);
    }

    /**
     * Runs {@code body} with every transient notice swallowed.
     *
     * <p>Window and pane creation are visible in themselves — a new pane appears, the window bar
     * grows a pill — so announcing them was pure noise, and worse, the new shell also tripped the
     * session-change notice, so one keypress raised two notices at once. Only session switches and
     * new sessions are worth a notice, and neither goes through here.
     */
    public void runWithoutNotices(@NonNull Runnable body) {
        mNoticeSuppressionDepth++;
        try {
            body.run();
        } finally {
            mNoticeSuppressionDepth--;
        }
    }

    /** True while a window/pane operation is deliberately running silent. */
    public boolean areNoticesSuppressed() {
        return mNoticeSuppressionDepth > 0;
    }
}
