package com.termux.app.chrome.sheets;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.notice.TerminalDress;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.folder.FolderRenameController;
import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.view.KeyboardUtils;
import com.termux.view.TerminalView;

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

        // -------------------------------------------------------------- folder rename

        /** The launcher's config store a dock folder's rename commits into; null before it is made. */
        @Nullable LauncherConfigRepository launcherConfigRepository();
        /** Hands the in-app keyboard's interceptor slot back to whoever owns it now. */
        void restoreInAppKeyboardTypingClaim();

        // -------------------------------------------------------------- keyboards and focus

        /** The in-app keyboard; null before it is built. */
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        /** Shows the in-app keyboard for typing if it is the keyboard in use; true when it is. */
        boolean ensureInAppTypingKeyboard();
        /** Gives the focus and the keys to the active pane's terminal. */
        boolean handKeysToActivePane();
        /** The current terminal view; null before it is inflated. */
        @Nullable TerminalView terminalView();
        /** The system IME is about to be asked for. */
        void onSystemImeRequested();
        /** Whether a screen point is on a key of the in-app keyboard. */
        boolean isPointOnInAppKeyboard(float rawX, float rawY);
        /** Whether a shown {@code view} contains the screen point. */
        boolean viewContainsScreenPoint(@Nullable View view, float x, float y);

        // -------------------------------------------------------------- the display's keyboard frame

        /** Whether the display on screen is in its touchpad frame. */
        boolean isDisplayTouchpadFrame();
        /** The display frame's content policy (keyboard or pad). */
        @NonNull com.termux.app.x11.DisplayFrameContentPolicy frameContent();
        /** Applies a display frame decision. */
        void applyDisplayFrameDecision(@NonNull com.termux.app.x11.DisplayFrameContentPolicy.Decision decision);
        /** Raises and lowers the display frame's keyboard. */
        void raiseDisplayFrameKeyboard();
        void lowerDisplayFrameKeyboard();
        /** Holds the keyboard's visibility listener off while a raise is nobody's pin. */
        void setRaisingDisplayFrameKeyboard(boolean raising);

        // -------------------------------------------------------------- the sheets still built in the activity

        /** The app drawer's controller if it has been built; null otherwise (never builds one). */
        @Nullable com.termux.app.launcher.drawer.AppDrawerController appDrawerControllerIfBuilt();
    }

    @NonNull private final Host mHost;
    @NonNull private final FolderRenameController mFolderRenameController;

    public SheetsController(@NonNull Host host) {
        mHost = host;
        mFolderRenameController = new FolderRenameController(ClipboardText.forContext(host.activity()));
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

    // ================================================================== folder rename

    /** The focusless folder rename; it holds the in-app keyboard's interceptor while active. */
    @NonNull
    public FolderRenameController folderRenameController() {
        return mFolderRenameController;
    }

    /** Installs focusless rename ahead of drawer search while TerminalView retains InputConnection. */
    public void beginFolderRename(long revision, @NonNull String folderId, @NonNull String title,
                                  @NonNull FolderRenameTitleView titleView) {
        mFolderRenameController.begin(revision, folderId, title,
            new FolderRenameController.Host() {
                @NonNull
                @Override public LauncherConfigRepository.MutationResult commit(long expected,
                    @NonNull String id, @NonNull String value) {
                    LauncherConfigRepository repository = mHost.launcherConfigRepository();
                    if (repository == null)
                        return LauncherConfigRepository.MutationResult.MISSING;
                    return repository.renameFolder(expected, id, value);
                }

                @Override public void onDraftChanged(@NonNull FolderRenameModel model) {
                    titleView.bind(model, true);
                }

                @Override public void onRenameEnded(boolean committed) {
                    LauncherConfigRepository repository = mHost.launcherConfigRepository();
                    com.termux.app.launcher.model.PinnedFolderItem latest =
                        repository == null ? null
                            : repository.loadSnapshot().folder(folderId);
                    titleView.bind(new FolderRenameModel(latest == null ? title : latest.title), false);
                    mHost.restoreInAppKeyboardTypingClaim();
                }
            });
        TermuxInAppKeyboard keyboard = mHost.inAppKeyboard();
        if (keyboard != null)
            keyboard.setKeyValueInterceptor(mFolderRenameController);
        raiseFolderRenameKeyboard();
    }

    /**
     * The same focusless rename for a folder on a Home page: the dock's intake and title view,
     * committing into the Home store instead of the dock's.
     */
    public void beginHomeFolderRename(@NonNull String name, @NonNull FolderRenameTitleView titleView,
            @NonNull com.termux.app.launcher.widget.WidgetPaneController.HomeFolderRenameTarget target) {
        mFolderRenameController.begin(0L, "home", name, new FolderRenameController.Host() {
            @NonNull
            @Override public LauncherConfigRepository.MutationResult commit(long expected,
                @NonNull String id, @NonNull String value) {
                return target.rename(value) ? LauncherConfigRepository.MutationResult.APPLIED
                    : LauncherConfigRepository.MutationResult.MISSING;
            }

            @Override public void onDraftChanged(@NonNull FolderRenameModel model) {
                titleView.bind(model, true);
            }

            @Override public void onRenameEnded(boolean committed) {
                titleView.bind(new FolderRenameModel(target.currentName()), false);
                // Whoever owns the slot now: on Home that can be the palette or a sheet too.
                mHost.restoreInAppKeyboardTypingClaim();
            }
        });
        TermuxInAppKeyboard keyboard = mHost.inAppKeyboard();
        if (keyboard != null)
            keyboard.setKeyValueInterceptor(mFolderRenameController);
        raiseFolderRenameKeyboard();
    }

    /**
     * The keyboard a folder rename is typed with. The in-app keyboard when it is on, shown if it
     * is down: the rename takes its keys through the interceptor slot, and on Home it may well
     * be down (Home rests without it unless Keyboard on Home is on), where asking the system IME
     * for itself raised nothing, since the in-app keyboard holds the system IME suppressed.
     * Otherwise the system IME, through a terminal view that holds the focus: the rename has no
     * text field, and the IME's text and keys reach the rename only by the terminal's overlay
     * channel. On Home the focus may sit elsewhere, and the IME then typed into nothing.
     */
    private void raiseFolderRenameKeyboard() {
        if (mHost.ensureInAppTypingKeyboard()) return;
        Activity activity = mHost.activity();
        if (!(activity.getCurrentFocus() instanceof TerminalView)) mHost.handKeysToActivePane();
        View target = activity.getCurrentFocus() instanceof TerminalView
            ? activity.getCurrentFocus() : mHost.terminalView();
        mHost.onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(activity, target);
    }

    /**
     * Whether a touch at this screen point is a key of the keyboard a folder rename is typed with:
     * the in-app keyboard's keys or the extra-keys row beside them. The folder popup stays open
     * for those, where any other touch outside it closes it.
     */
    public boolean isFolderRenameTypingTouch(float rawX, float rawY) {
        return isFolderRenameActive()
            && (mHost.isPointOnInAppKeyboard(rawX, rawY)
                || mHost.viewContainsScreenPoint(mHost.activity().findViewById(R.id.inapp_keyboard_container), rawX, rawY)
                || mHost.viewContainsScreenPoint(mHost.activity().findViewById(R.id.terminal_toolbar_host), rawX, rawY));
    }

    public void cancelFolderRename() { mFolderRenameController.cancel(); }

    public boolean isFolderRenameActive() { return mFolderRenameController.isActive(); }

    // ================================================================== the drawer's keyboard

    /** The display keyboard the drawer's search raised, and whether the user has claimed it since. */
    private boolean mDrawerRaisedKeyboard;
    private boolean mDrawerRaisedKeyboardClaimed;

    /** Whether the drawer's search raised the display keyboard now up. */
    public boolean drawerRaisedKeyboard() {
        return mDrawerRaisedKeyboard;
    }

    /** Whether the user has claimed the keyboard the drawer's search raised. */
    public boolean drawerRaisedKeyboardClaimed() {
        return mDrawerRaisedKeyboardClaimed;
    }

    /** The user asked for the keyboard themselves: the drawer's close leaves it up. */
    public void claimDrawerRaisedKeyboard() {
        mDrawerRaisedKeyboardClaimed = true;
    }

    /** The drawer closed: put back down what its search raised over the display, quietly. */
    public void lowerDrawerSearchKeyboard() {
        boolean raised = mDrawerRaisedKeyboard;
        mDrawerRaisedKeyboard = false;
        TermuxInAppKeyboard keyboard = mHost.inAppKeyboard();
        if (!com.termux.app.x11.DisplayKeyboardRoute.drawerCloseLowers(raised,
                mDrawerRaisedKeyboardClaimed, keyboard != null && keyboard.isVisible()))
            return;
        com.termux.app.x11.DisplayFrameContentPolicy frameContent = mHost.frameContent();
        if (mHost.isDisplayTouchpadFrame() && frameContent.isKeyboardContent()) {
            mHost.applyDisplayFrameDecision(frameContent.onTextFocus(false));
        } else {
            mHost.lowerDisplayFrameKeyboard();
        }
    }

    /**
     * Summons the system IME for the drawer's search, with <b>no focus change</b>.
     *
     * <p>The obvious call here would be {@code beginExternalTextInput()}, and it is exactly wrong:
     * it runs {@code requestAccessoryGeometrySync()}, and the accessory stack's geometry is frozen
     * for the life of the drawer transition. Thawing it mid-reveal relayouts the stack under an open
     * plane and the dock visibly jumps on close. Focus stays on the terminal view, which keeps
     * owning the input connection; the committed text reaches the drawer through
     * {@code handleAppDrawerCodePoint}.
     */
    public void requestAppDrawerSearchKeyboard() {
        TermuxInAppKeyboard keyboard = mHost.inAppKeyboard();
        // With the built-in keyboard selected the search raises it, on every place: the drawer
        // types through it and reveals it through the plane. Only the Android keyboard gets the
        // system ask below (over a running display that ask would not even be served).
        com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch route = keyboard == null
            ? com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch.SYSTEM_ASK
            : com.termux.app.x11.DisplayKeyboardRoute.drawerSearch(keyboard.isEnabled(),
                keyboard.isTurnedOff(), keyboard.yieldsToHardwareKeyboard());
        // A keyboard that stands aside for a physical one raises nothing; the keys type into the
        // search through the layer's own key route.
        if (route == com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch.NONE) return;
        if (route != com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch.SYSTEM_ASK) {
            boolean wasUp = keyboard.isVisible();
            if (route == com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch.TURN_ON_AND_RAISE) {
                // Switched off with the keyboard key, and tapping the search asks for it back.
                // Nobody's pin either: held off from the visibility listener like the raise below.
                mHost.setRaisingDisplayFrameKeyboard(true);
                try {
                    keyboard.setTurnedOff(false);
                } finally {
                    mHost.setRaisingDisplayFrameKeyboard(false);
                }
            }
            // The drawer's raise is nobody's pin: it goes down again with the drawer
            // (lowerDrawerSearchKeyboard) unless the keyboard was up first or the user claims it.
            if (mHost.isDisplayTouchpadFrame()) {
                com.termux.app.x11.DisplayFrameContentPolicy frameContent = mHost.frameContent();
                if (frameContent.isKeyboardContent()) return;
                mHost.applyDisplayFrameDecision(frameContent.onTextFocus(true));
                mDrawerRaisedKeyboard = true;
                mDrawerRaisedKeyboardClaimed = false;
            } else if (!wasUp) {
                mHost.raiseDisplayFrameKeyboard();
                mDrawerRaisedKeyboard = true;
                mDrawerRaisedKeyboardClaimed = false;
            }
            // Raised after the pull, the keyboard was not among the bands the drawer measured;
            // it measures them again so the reveal can bring it up through the plane.
            com.termux.app.launcher.drawer.AppDrawerController drawer = mHost.appDrawerControllerIfBuilt();
            if (drawer != null) drawer.onBuiltInKeyboardRaised();
            return;
        }
        mHost.onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(mHost.activity(), mHost.terminalView());
    }
}
