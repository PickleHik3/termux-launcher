package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.notice.TerminalDress;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.List;

/**
 * The one sessions surface: what is running, what was saved, and every action either of them has.
 *
 * <p>There used to be two of these. A browser on the terminal's foot did sessions, rename, close and
 * workspaces, each answer opening another panel on top of the last; a dropdown under the status chip
 * did a shorter version of the same list in a pop-up window with a {@code PopupMenu} hanging off
 * each row. Both bound the same projection and called the same activity methods, and they disagreed
 * about what a session row says, how deep it goes and what happens when you tap it.
 *
 * <p>Everything a row can do happens in the row. A stacked panel hid the list it was about — you
 * confirmed closing "Session 2" over a card that no longer showed you Session 2 — and a pop-up menu
 * over a panel over the terminal was three windows deep to close one shell. Unfolding the answer
 * underneath the question instead keeps the thing being acted on on screen the whole time, and the
 * way out of every state is the same tap that opened it.
 */
public final class SessionsDrawerView extends LinearLayout
    implements TerminalSheetController.TextSink {

    /** Which half of the drawer is showing. */
    public enum Tab { LIVE, SAVED }

    /** What a row has unfolded beneath itself. */
    public enum RowState { IDLE, ACTIONS, RENAME, CONFIRM_CLOSE }

    /** What a saved row has unfolded beneath itself. */
    public enum SavedState { IDLE, LOAD, CONFIRM_DELETE }

    /** One saved workspace, as the drawer shows it. */
    public static final class SavedWorkspace {
        @NonNull public final String name;
        public final int paneCount;
        public final long savedAtEpochMs;
        /** Whether it captured what was running, which is the only reason to offer to run it. */
        public final boolean hasCommands;

        public SavedWorkspace(@NonNull String name, int paneCount, long savedAtEpochMs,
                              boolean hasCommands) {
            this.name = name;
            this.paneCount = paneCount;
            this.savedAtEpochMs = savedAtEpochMs;
            this.hasCommands = hasCommands;
        }
    }

    /** Everything the drawer asks the activity for. It owns no shells and reads no files. */
    public interface Listener {
        void onNewSession();

        /** The long press on +: the named-session prompt the panel's + also carried. */
        void onNewSessionPrompt();

        void onActivateSession(long sessionId);

        void onActivateWindow(long sessionId, long windowId);

        void onCloneSession(long sessionId);

        void onRenameSession(long sessionId, @NonNull String name);

        void onCloseSession(long sessionId);

        void onSaveWorkspace(@NonNull String name, boolean captureCommands);

        void onLoadWorkspace(@NonNull String name, boolean replace, boolean runCommands);

        void onDeleteWorkspace(@NonNull String name);

        /**
         * A field has unfolded and the keys have to be reachable. The drawer never takes focus, so
         * nothing summons a keyboard on its behalf until there is somewhere for one to type.
         */
        void onTypingStarted();
    }

    private static final float TITLE_SP = 14f;
    private static final float SUBTITLE_SP = 10.5f;
    private static final float ACTION_SP = 12.5f;
    private static final int ROW_HEIGHT_DP = 44;
    private static final int WINDOW_ROW_HEIGHT_DP = 36;
    /** The tint a row's own surface carries, over whatever the drawer is painted with. */
    private static final int ROW_FILL_ALPHA = 14;
    private static final int ROW_CURRENT_FILL_ALPHA = 34;

    @Nullable private Listener mListener;
    @NonNull private TerminalDress mDress;
    @NonNull private Tab mTab = Tab.LIVE;
    @NonNull private List<SessionBrowserModel.Session> mSessions = new ArrayList<>();
    @NonNull private List<SavedWorkspace> mSaved = new ArrayList<>();

    @Nullable private Long mExpandedSessionId;
    @Nullable private Long mActiveRowId;
    @NonNull private RowState mRowState = RowState.IDLE;
    @Nullable private String mActiveSavedName;
    @NonNull private SavedState mSavedState = SavedState.IDLE;
    private boolean mSaveFieldOpen;

    /** Where keystrokes aimed at the drawer land, or null while nothing is being typed into. */
    @Nullable private TerminalSheetController.TextField mActiveField;
    /** The draft a field is holding, so a rebind while typing does not throw it away. */
    @NonNull private String mDraft = "";
    private boolean mCaptureCommands;
    private boolean mRunCommands;

    private final TextView mTitle;
    private final TextView mLiveTab;
    private final TextView mSavedTab;
    private final LinearLayout mRows;
    private final ScrollView mScroll;
    private final TextView mEmpty;
    private final LinearLayout mFooter;

    private boolean mGestureActive;
    private boolean mDeferredRebuild;

    public SessionsDrawerView(@NonNull Context context) {
        super(context);
        mDress = TerminalDress.stored(context);
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mTitle = label(TITLE_SP, mDress.textColor);
        mTitle.setText(R.string.session_browser_title);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(mTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView add = label(20f, mDress.textColor);
        add.setText("+");
        add.setGravity(Gravity.CENTER);
        add.setContentDescription(context.getString(R.string.sessions_panel_new));
        add.setOnClickListener(view -> {
            if (mListener != null) mListener.onNewSession();
        });
        add.setOnLongClickListener(view -> {
            if (mListener == null) return false;
            mListener.onNewSessionPrompt();
            return true;
        });
        header.addView(add, new LayoutParams(dp(36), dp(36)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));

        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(HORIZONTAL);
        mLiveTab = segment(R.string.sessions_drawer_live, Tab.LIVE);
        mSavedTab = segment(R.string.sessions_drawer_saved, Tab.SAVED);
        segments.addView(mLiveTab, new LayoutParams(0, dp(32), 1f));
        LayoutParams savedParams = new LayoutParams(0, dp(32), 1f);
        savedParams.leftMargin = dp(4);
        segments.addView(mSavedTab, savedParams);
        LayoutParams segmentRow = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        segmentRow.topMargin = dp(6);
        segmentRow.bottomMargin = dp(6);
        addView(segments, segmentRow);

        mRows = new LinearLayout(context);
        mRows.setOrientation(VERTICAL);
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        mScroll.addView(mRows, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        // Weighted: the drawer has the terminal's whole height and the list is what fills it, so a
        // long list scrolls inside the panel rather than pushing the footer off the bottom.
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        mEmpty = label(SUBTITLE_SP + 1f, mDress.subTextColor);
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setVisibility(GONE);
        addView(mEmpty, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        mFooter = new LinearLayout(context);
        mFooter.setOrientation(VERTICAL);
        addView(mFooter, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        rebuild();
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Colours everything from what the terminal is wearing; there is no second palette here. */
    public void setDress(@NonNull TerminalDress dress) {
        mDress = dress;
        rebuild();
    }

    @NonNull
    public Tab tab() {
        return mTab;
    }

    public void setTab(@NonNull Tab tab) {
        if (mTab == tab) return;
        mTab = tab;
        collapseAll();
        rebuild();
    }

    /** Which session has unfolded its actions, and what it unfolded. */
    @NonNull
    public RowState rowState(long sessionId) {
        return mActiveRowId != null && mActiveRowId == sessionId ? mRowState : RowState.IDLE;
    }

    @NonNull
    public SavedState savedState(@NonNull String name) {
        return name.equals(mActiveSavedName) ? mSavedState : SavedState.IDLE;
    }

    public boolean isSaveFieldOpen() {
        return mSaveFieldOpen;
    }

    /** The Save-workspace entry point: the drawer opens with its name field already taking keys. */
    public void openSaveField() {
        setTab(Tab.LIVE);
        collapseAll();
        mSaveFieldOpen = true;
        beginTyping("");
        rebuild();
    }

    /**
     * Binds the live half. Non-structural rebinds land while a finger is down; a rebuild waits for
     * it to lift, or a foreground poll would pull the row out from under the tap.
     */
    public void bindLive(@NonNull List<SessionBrowserModel.Session> sessions) {
        mSessions = new ArrayList<>(sessions);
        if (mExpandedSessionId != null && findSession(mExpandedSessionId) == null)
            mExpandedSessionId = null;
        if (mActiveRowId != null && findSession(mActiveRowId) == null) collapseRow();
        if (mGestureActive) {
            mDeferredRebuild = true;
            return;
        }
        rebuild();
    }

    public void bindSaved(@NonNull List<SavedWorkspace> workspaces) {
        mSaved = new ArrayList<>(workspaces);
        if (mActiveSavedName != null && findSaved(mActiveSavedName) == null) collapseSaved();
        if (mGestureActive) {
            mDeferredRebuild = true;
            return;
        }
        rebuild();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mGestureActive = true;
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mGestureActive = false;
            if (mDeferredRebuild) {
                mDeferredRebuild = false;
                post(this::rebuild);
            }
        }
        return handled;
    }

    // ------------------------------------------------------------------ typing

    @Override
    public void onText(@NonNull String text) {
        if (mActiveField != null) mActiveField.onText(text);
    }

    @Override
    public void onBackspace() {
        if (mActiveField != null) mActiveField.onBackspace();
    }

    @Override
    public boolean onCommit() {
        return mActiveField != null && mActiveField.onCommit();
    }

    /** True while a field is open, so the drawer's Back can close the field before the drawer. */
    public boolean isTyping() {
        return mActiveField != null;
    }

    /** Closes whatever is unfolded, one level at a time. @return false when nothing was open. */
    public boolean collapseOne() {
        if (mActiveField == null && mActiveRowId == null && mActiveSavedName == null
            && !mSaveFieldOpen) return false;
        collapseAll();
        rebuild();
        return true;
    }

    private void beginTyping(@NonNull String initial) {
        mDraft = initial;
        if (mListener != null) mListener.onTypingStarted();
    }

    private void endTyping() {
        mActiveField = null;
        mDraft = "";
    }

    private void collapseRow() {
        mActiveRowId = null;
        mRowState = RowState.IDLE;
        endTyping();
    }

    private void collapseSaved() {
        mActiveSavedName = null;
        mSavedState = SavedState.IDLE;
        mRunCommands = false;
    }

    private void collapseAll() {
        collapseRow();
        collapseSaved();
        mSaveFieldOpen = false;
        endTyping();
    }

    // ------------------------------------------------------------------ building

    private void rebuild() {
        // Dropped before the rows are rebuilt and reclaimed by whichever field is rebuilt with
        // them: the draft outlives the view that was showing it, the TextView never does.
        mActiveField = null;
        mTitle.setTextColor(mDress.textColor);
        styleSegment(mLiveTab, mTab == Tab.LIVE);
        styleSegment(mSavedTab, mTab == Tab.SAVED);
        mEmpty.setTextColor(mDress.subTextColor);

        mRows.removeAllViews();
        mFooter.removeAllViews();
        // Rebuilt from scratch every pass: the rows carry unfolded state under them, and a row that
        // changed shape cannot be rebound into the view that held the old shape.
        if (mTab == Tab.LIVE) buildLiveRows();
        else buildSavedRows();

        boolean empty = mTab == Tab.LIVE ? mSessions.isEmpty() : mSaved.isEmpty();
        mEmpty.setText(mTab == Tab.LIVE ? R.string.sessions_panel_empty
            : R.string.workspace_picker_empty);
        mEmpty.setVisibility(empty ? VISIBLE : GONE);
        mScroll.setVisibility(empty ? GONE : VISIBLE);

        if (mTab == Tab.LIVE) buildSaveFooter();
    }

    private void buildLiveRows() {
        for (SessionBrowserModel.Session session : mSessions) {
            mRows.addView(buildSessionRow(session), rowParams(ROW_HEIGHT_DP));
            if (rowState(session.id) != RowState.IDLE) mRows.addView(buildRowUnfold(session),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            if (mExpandedSessionId == null || mExpandedSessionId != session.id) continue;
            for (int i = 0; i < session.windows.size(); i++) {
                mRows.addView(buildWindowRow(session, session.windows.get(i),
                    i == session.windows.size() - 1), rowParams(WINDOW_ROW_HEIGHT_DP));
            }
        }
    }

    @NonNull
    private View buildSessionRow(@NonNull SessionBrowserModel.Session session) {
        LinearLayout row = baseRow(session.current);
        row.setPaddingRelative(dp(8), dp(2), dp(2), dp(2));

        TextView chevron = label(14f, mDress.subTextColor);
        chevron.setGravity(Gravity.CENTER);
        boolean expanded = mExpandedSessionId != null && mExpandedSessionId == session.id;
        chevron.setText(expanded ? "⌄" : "›");
        chevron.setOnClickListener(view -> {
            mExpandedSessionId = expanded ? null : session.id;
            rebuild();
        });
        row.addView(chevron, new LayoutParams(dp(22), LayoutParams.MATCH_PARENT));

        // The dot does two jobs, because they never collide: it marks the session you are in, and
        // takes the agent's colour when a pane in it is running one — a blocked agent is findable
        // without opening the session it is in.
        TextView dot = label(12f, dotColor(session));
        dot.setText(session.current || session.agentState != null ? "●" : "");
        dot.setGravity(Gravity.CENTER);
        row.addView(dot, new LayoutParams(dp(14), LayoutParams.MATCH_PARENT));

        LinearLayout text = textBlock();
        TextView title = label(TITLE_SP, mDress.textColor);
        title.setText(NerdFontSpans.span(getContext(), sessionTitle(session)));
        title.setTypeface(Typeface.create("sans-serif-medium",
            session.current ? Typeface.BOLD : Typeface.NORMAL));
        TextView subtitle = label(SUBTITLE_SP, mDress.subTextColor);
        subtitle.setText(counts(session));
        text.addView(title);
        text.addView(subtitle);
        row.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView more = label(18f, mDress.subTextColor);
        more.setText("⋯");
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getContext().getString(
            R.string.sessions_panel_more, session.index + 1));
        more.setOnClickListener(view -> {
            boolean open = rowState(session.id) != RowState.IDLE;
            collapseAll();
            if (!open) {
                mActiveRowId = session.id;
                mRowState = RowState.ACTIONS;
            }
            rebuild();
        });
        row.addView(more, new LayoutParams(dp(34), LayoutParams.MATCH_PARENT));

        row.setContentDescription(sessionTitle(session) + " · " + counts(session));
        row.setOnClickListener(view -> {
            if (mListener != null) mListener.onActivateSession(session.id);
        });
        return row;
    }

    /** The action row, the rename field or the close confirmation, whichever the row asked for. */
    @NonNull
    private View buildRowUnfold(@NonNull SessionBrowserModel.Session session) {
        switch (mRowState) {
            case RENAME:
                return buildFieldRow(sessionTitle(session),
                    getContext().getString(R.string.sessions_drawer_session_name),
                    getContext().getString(R.string.sessions_drawer_done),
                    value -> {
                        if (mListener != null) mListener.onRenameSession(session.id, value);
                        collapseAll();
                        rebuild();
                    });
            case CONFIRM_CLOSE:
                return buildConfirmRow(
                    getContext().getString(R.string.sessions_drawer_close_question),
                    getContext().getString(R.string.session_browser_close),
                    getContext().getString(R.string.sessions_drawer_keep),
                    () -> {
                        if (mListener != null) mListener.onCloseSession(session.id);
                        collapseAll();
                        rebuild();
                    });
            default:
                LinearLayout actions = actionStrip();
                addAction(actions, getContext().getString(R.string.session_browser_clone), () -> {
                    if (mListener != null) mListener.onCloneSession(session.id);
                    collapseAll();
                    rebuild();
                });
                addAction(actions, getContext().getString(R.string.session_browser_rename), () -> {
                    mRowState = RowState.RENAME;
                    beginTyping(session.name == null ? "" : session.name);
                    rebuild();
                });
                addAction(actions, getContext().getString(R.string.session_browser_close), () -> {
                    mRowState = RowState.CONFIRM_CLOSE;
                    rebuild();
                });
                return actions;
        }
    }

    @NonNull
    private View buildWindowRow(@NonNull SessionBrowserModel.Session session,
                                @NonNull SessionBrowserModel.Window window, boolean last) {
        LinearLayout row = baseRow(false);
        row.setPaddingRelative(dp(24), dp(1), dp(6), dp(1));
        TextView connector = label(12f, mDress.subTextColor);
        connector.setText(last ? "└" : "├");
        connector.setGravity(Gravity.CENTER);
        row.addView(connector, new LayoutParams(dp(18), LayoutParams.MATCH_PARENT));

        boolean focused = session.current && window.current;
        LinearLayout text = textBlock();
        TextView title = label(SUBTITLE_SP + 1.5f, mDress.textColor);
        title.setText(NerdFontSpans.span(getContext(), windowTitle(window)));
        title.setTypeface(Typeface.create("sans-serif-medium",
            focused ? Typeface.BOLD : Typeface.NORMAL));
        TextView subtitle = label(SUBTITLE_SP, mDress.subTextColor);
        subtitle.setText(getResources().getQuantityString(R.plurals.session_browser_pane_count,
            window.panes.size(), window.panes.size()));
        text.addView(title);
        text.addView(subtitle);
        row.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        row.setContentDescription(windowTitle(window) + " · " + subtitle.getText());
        row.setOnClickListener(view -> {
            if (mListener != null) mListener.onActivateWindow(session.id, window.id);
        });
        return row;
    }

    private void buildSavedRows() {
        for (SavedWorkspace workspace : mSaved) {
            mRows.addView(buildSavedRow(workspace), rowParams(ROW_HEIGHT_DP));
            if (savedState(workspace.name) == SavedState.IDLE) continue;
            mRows.addView(buildSavedUnfold(workspace),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    @NonNull
    private View buildSavedRow(@NonNull SavedWorkspace workspace) {
        LinearLayout row = baseRow(false);
        row.setPaddingRelative(dp(12), dp(2), dp(2), dp(2));
        LinearLayout text = textBlock();
        TextView title = label(TITLE_SP, mDress.textColor);
        title.setText(workspace.name);
        TextView subtitle = label(SUBTITLE_SP, mDress.subTextColor);
        subtitle.setText(savedSubtitle(workspace));
        text.addView(title);
        text.addView(subtitle);
        row.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView more = label(18f, mDress.subTextColor);
        more.setText("⋯");
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getContext().getString(
            R.string.workspace_delete_description, workspace.name));
        more.setOnClickListener(view -> {
            boolean open = savedState(workspace.name) == SavedState.CONFIRM_DELETE;
            collapseAll();
            if (!open) {
                mActiveSavedName = workspace.name;
                mSavedState = SavedState.CONFIRM_DELETE;
            }
            rebuild();
        });
        row.addView(more, new LayoutParams(dp(34), LayoutParams.MATCH_PARENT));

        row.setContentDescription(workspace.name + " · " + subtitle.getText());
        row.setOnClickListener(view -> {
            boolean open = savedState(workspace.name) == SavedState.LOAD;
            collapseAll();
            if (!open) {
                mActiveSavedName = workspace.name;
                mSavedState = SavedState.LOAD;
            }
            rebuild();
        });
        return row;
    }

    @NonNull
    private View buildSavedUnfold(@NonNull SavedWorkspace workspace) {
        if (mSavedState == SavedState.CONFIRM_DELETE) {
            return buildConfirmRow(getContext().getString(R.string.workspace_delete_message),
                getContext().getString(R.string.workspace_delete_confirm),
                getContext().getString(R.string.sessions_drawer_keep),
                () -> {
                    if (mListener != null) mListener.onDeleteWorkspace(workspace.name);
                    collapseAll();
                    rebuild();
                });
        }
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        // Only offered when the workspace actually carries commands, so the question is never about
        // something the file cannot do.
        if (workspace.hasCommands) {
            column.addView(checkBox(R.string.sessions_drawer_run_commands, mRunCommands,
                checked -> mRunCommands = checked));
        }
        LinearLayout actions = actionStrip();
        addAction(actions, getContext().getString(R.string.sessions_drawer_add_to_current), () -> {
            String name = workspace.name;
            boolean run = mRunCommands;
            collapseAll();
            rebuild();
            if (mListener != null) mListener.onLoadWorkspace(name, false, run);
        });
        addAction(actions, getContext().getString(R.string.workspace_picker_replace), () -> {
            String name = workspace.name;
            boolean run = mRunCommands;
            collapseAll();
            rebuild();
            if (mListener != null) mListener.onLoadWorkspace(name, true, run);
        });
        column.addView(actions, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return column;
    }

    /** The Live footer: one line that becomes a name field when it is asked to. */
    private void buildSaveFooter() {
        if (!mSaveFieldOpen) {
            TextView entry = label(ACTION_SP, mDress.textColor);
            entry.setText(R.string.sessions_drawer_save_workspace);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setMinHeight(dp(40));
            entry.setPaddingRelative(dp(8), 0, dp(8), 0);
            entry.setBackground(rowSurface(false));
            entry.setOnClickListener(view -> {
                collapseAll();
                mSaveFieldOpen = true;
                beginTyping("");
                rebuild();
            });
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            mFooter.addView(entry, params);
            return;
        }
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        column.addView(buildFieldRow(getContext().getString(R.string.sessions_drawer_save_workspace),
            getContext().getString(R.string.session_browser_workspace_name),
            getContext().getString(R.string.session_browser_save_workspace),
            value -> {
                boolean capture = mCaptureCommands;
                collapseAll();
                rebuild();
                if (mListener != null) mListener.onSaveWorkspace(value, capture);
            }), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        column.addView(checkBox(R.string.workspace_save_capture_commands, mCaptureCommands,
            checked -> mCaptureCommands = checked));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        mFooter.addView(column, params);
    }

    // ------------------------------------------------------------------ pieces

    private interface OnValue {
        void onValue(@NonNull String value);
    }

    private interface OnChecked {
        void onChecked(boolean checked);
    }

    /**
     * A label, a caret and the word that commits it.
     *
     * <p>Not an {@code EditText}: the drawer rides the sheet plane, which exists so nothing here
     * takes focus off {@code TerminalView} and summons the system IME. The field is driven from the
     * same key channel the palette and the rename chip are typed with.
     */
    @NonNull
    private View buildFieldRow(@NonNull CharSequence heading, @NonNull String hint,
                               @NonNull String commitLabel, @NonNull OnValue onValue) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        column.setBackground(rowSurface(false));
        column.setPaddingRelative(dp(8), dp(4), dp(8), dp(6));

        TextView caption = label(SUBTITLE_SP, mDress.subTextColor);
        caption.setText(heading);
        column.addView(caption, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView field = label(TITLE_SP, mDress.textColor);
        field.setMinHeight(dp(34));
        field.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(field, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout actions = actionStrip();
        addAction(actions, getContext().getString(android.R.string.cancel), () -> {
            collapseAll();
            rebuild();
        });
        addAction(actions, commitLabel, () -> onValue.onValue(mDraft));
        column.addView(actions, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Built after the commit action so ⏎ and the button spend the same draft.
        TerminalSheetController.TextField typed = new TerminalSheetController.TextField(
            field, hint, value -> mDraft = value, () -> onValue.onValue(mDraft));
        for (int i = 0; i < mDraft.length(); ) {
            int codePoint = mDraft.codePointAt(i);
            typed.onText(new String(Character.toChars(codePoint)));
            i += Character.charCount(codePoint);
        }
        mActiveField = typed;
        return column;
    }

    /** A question and its two answers, where the question used to be a card of its own. */
    @NonNull
    private View buildConfirmRow(@NonNull CharSequence question, @NonNull String confirmLabel,
                                 @NonNull String cancelLabel, @NonNull Runnable onConfirm) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        column.setBackground(rowSurface(false));
        column.setPaddingRelative(dp(8), dp(6), dp(8), dp(6));
        TextView message = label(SUBTITLE_SP + 1f, mDress.textColor);
        message.setSingleLine(false);
        message.setText(question);
        column.addView(message, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout actions = actionStrip();
        addAction(actions, cancelLabel, () -> {
            collapseAll();
            rebuild();
        });
        addAction(actions, confirmLabel, onConfirm);
        column.addView(actions, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return column;
    }

    @NonNull
    private LinearLayout actionStrip() {
        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(HORIZONTAL);
        strip.setGravity(Gravity.END);
        strip.setBackground(rowSurface(false));
        strip.setPaddingRelative(dp(4), dp(2), dp(4), dp(2));
        return strip;
    }

    private void addAction(@NonNull LinearLayout strip, @NonNull CharSequence text,
                           @NonNull Runnable action) {
        TextView button = label(ACTION_SP, mDress.textColor);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(36));
        // No minimum width, and one ellipsized line: three actions across a 340dp drawer at a 1.3×
        // font scale is exactly where a fixed-width button clips.
        button.setMinWidth(0);
        button.setPaddingRelative(dp(10), 0, dp(10), 0);
        button.setOnClickListener(view -> action.run());
        LayoutParams params = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(2);
        strip.addView(button, params);
    }

    @NonNull
    private View checkBox(int labelRes, boolean checked, @NonNull OnChecked onChecked) {
        CheckBox box = new CheckBox(getContext());
        box.setText(labelRes);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUBTITLE_SP + 1f);
        box.setTextColor(mDress.textColor);
        box.setChecked(checked);
        box.setMinHeight(dp(36));
        box.setPaddingRelative(box.getPaddingStart(), 0, 0, 0);
        box.setOnCheckedChangeListener((view, value) -> onChecked.onChecked(value));
        return box;
    }

    @NonNull
    private TextView segment(int labelRes, @NonNull Tab tab) {
        TextView view = label(ACTION_SP, mDress.textColor);
        view.setText(labelRes);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(0);
        // A segment carries one word in a space the font scale can halve, so it shrinks its text
        // rather than clipping it.
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            view, 9, Math.round(ACTION_SP), 1, TypedValue.COMPLEX_UNIT_SP);
        view.setOnClickListener(ignored -> setTab(tab));
        return view;
    }

    private void styleSegment(@NonNull TextView view, boolean selected) {
        view.setTextColor(selected ? mDress.textColor : mDress.subTextColor);
        view.setTypeface(Typeface.create("sans-serif-medium",
            selected ? Typeface.BOLD : Typeface.NORMAL));
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(dp(10));
        surface.setColor(ColorUtils.setAlphaComponent(mDress.textColor,
            selected ? ROW_CURRENT_FILL_ALPHA : 0));
        surface.setStroke(Math.max(1, Math.round(mDress.strokeWidthPx)),
            selected ? mDress.strokeColor : android.graphics.Color.TRANSPARENT);
        view.setBackground(surface);
    }

    @NonNull
    private LinearLayout baseRow(boolean current) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setBackground(rowSurface(current));
        return row;
    }

    @NonNull
    private GradientDrawable rowSurface(boolean current) {
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(dp(10));
        surface.setColor(ColorUtils.setAlphaComponent(mDress.textColor,
            current ? ROW_CURRENT_FILL_ALPHA : ROW_FILL_ALPHA));
        return surface;
    }

    @NonNull
    private LinearLayout textBlock() {
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    @NonNull
    private LayoutParams rowParams(int heightDp) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(2);
        return params;
    }

    @NonNull
    private TextView label(float sp, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    // ------------------------------------------------------------------ text

    @NonNull
    private String sessionTitle(@NonNull SessionBrowserModel.Session session) {
        return TextUtils.isEmpty(session.name)
            ? getContext().getString(R.string.sessions_panel_session_fallback, session.index + 1)
            : session.name;
    }

    @NonNull
    private String windowTitle(@NonNull SessionBrowserModel.Window window) {
        String label = TextUtils.isEmpty(window.label)
            ? getContext().getString(R.string.sessions_panel_window_fallback) : window.label;
        return getContext().getString(R.string.sessions_panel_window_title,
            window.index + 1, label);
    }

    @NonNull
    private String counts(@NonNull SessionBrowserModel.Session session) {
        return getResources().getQuantityString(R.plurals.session_browser_window_count,
            session.windows.size(), session.windows.size()) + " · "
            + getResources().getQuantityString(R.plurals.session_browser_pane_count,
            session.paneCount(), session.paneCount());
    }

    @NonNull
    private CharSequence savedSubtitle(@NonNull SavedWorkspace workspace) {
        String panes = getResources().getQuantityString(R.plurals.session_browser_pane_count,
            workspace.paneCount, workspace.paneCount);
        if (workspace.savedAtEpochMs <= 0L) return panes;
        return panes + " · " + DateUtils.getRelativeTimeSpanString(workspace.savedAtEpochMs,
            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    private int dotColor(@NonNull SessionBrowserModel.Session session) {
        Context context = getContext();
        if (session.agentState == AgentStatus.State.BLOCKED) {
            return MaterialColors.getColor(context, com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(context, R.color.termux_error));
        }
        if (session.agentState == AgentStatus.State.WORKING) {
            return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorTertiary,
                ContextCompat.getColor(context, R.color.termux_primary));
        }
        return session.current ? mDress.textColor : mDress.subTextColor;
    }

    @Nullable
    private SessionBrowserModel.Session findSession(long id) {
        for (SessionBrowserModel.Session session : mSessions) {
            if (session.id == id) return session;
        }
        return null;
    }

    @Nullable
    private SavedWorkspace findSaved(@NonNull String name) {
        for (SavedWorkspace workspace : mSaved) {
            if (workspace.name.equals(name)) return workspace;
        }
        return null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** The rows a test walks, without reaching into the view hierarchy by index. */
    @NonNull
    public ViewGroup rowsForTest() {
        return mRows;
    }
}
