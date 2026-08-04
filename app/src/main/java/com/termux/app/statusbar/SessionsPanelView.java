package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.SessionBrowserModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compact accordion tree used by the status-row sessions dropdown. */
public final class SessionsPanelView extends LinearLayout {

    public interface Listener {
        void onWindowSelected(long sessionId, long windowId);
        void onSessionClosed(long sessionId);
        void onSessionRenameRequested(long sessionId);
        void onNewSession();
        void onNewSessionPrompt();
    }

    private static final int MENU_RENAME = 1;
    private static final int MENU_CLOSE = 2;

    private final int mPrimary;
    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mSecondary;
    private final int mOutlineVariant;
    private final LinearLayout mRows;
    private final ScrollView mScroll;
    private final TextView mEmpty;
    private final Map<Long, HeaderHolder> mHeaders = new HashMap<>();
    private final Map<Long, WindowHolder> mWindows = new HashMap<>();

    @Nullable private Listener mListener;
    @NonNull private List<SessionBrowserModel.Session> mSessions = new ArrayList<>();
    @Nullable private Long mExpandedSessionId;
    @Nullable private String mStructureKey;
    private boolean mGestureActive;
    private boolean mDeferredStructuralRebuild;
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    @NonNull private SessionsPanelMetrics.Layout mLayout =
        new SessionsPanelMetrics.Layout(SessionsPanelMetrics.MIN_WIDTH_DP, 0f);

    public SessionsPanelView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mSecondary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mOutlineVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));

        addView(buildPanelHeader(context), new LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));
        View hairline = new View(context);
        hairline.setBackgroundColor(ColorUtils.setAlphaComponent(mOutlineVariant, 96));
        LayoutParams line = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        line.topMargin = dp(3);
        line.bottomMargin = dp(3);
        addView(hairline, line);

        mRows = new LinearLayout(context);
        mRows.setOrientation(VERTICAL);
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        mScroll.addView(mRows, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mEmpty = label(10.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 168));
        mEmpty.setText(R.string.sessions_panel_empty);
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setVisibility(GONE);
        addView(mEmpty, new LayoutParams(LayoutParams.MATCH_PARENT, dp(40)));
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    public void setSurfaceStyle(boolean capsule, float radiusPx) {
        if (mCapsuleSurface == capsule && mStatusBarRadiusPx == radiusPx) return;
        mCapsuleSurface = capsule;
        mStatusBarRadiusPx = Math.max(0f, radiusPx);
        rebuild();
    }

    /** Non-structural binds update the same views; structural binds wait for an active gesture. */
    public void bind(@NonNull List<SessionBrowserModel.Session> sessions) {
        mSessions = new ArrayList<>(sessions);
        String nextKey = structureKey(mSessions);
        boolean structural = !nextKey.equals(mStructureKey);
        if (mExpandedSessionId != null && findSession(mExpandedSessionId) == null)
            mExpandedSessionId = null;
        if (structural && mGestureActive) {
            mDeferredStructuralRebuild = true;
            return;
        }
        if (structural) {
            mStructureKey = nextKey;
            rebuild();
        } else {
            rebindRows();
        }
    }

    public int desiredWidthDp() { return mLayout.widthDp; }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mGestureActive = true;
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mGestureActive = false;
            if (mDeferredStructuralRebuild) {
                mDeferredStructuralRebuild = false;
                mStructureKey = structureKey(mSessions);
                post(this::rebuild);
            }
        }
        return handled;
    }

    private View buildPanelHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(10.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 190));
        title.setText(R.string.session_browser_title);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView add = label(18f, ColorUtils.setAlphaComponent(mPrimary, 210));
        add.setText("+");
        add.setGravity(Gravity.CENTER);
        add.setContentDescription(getResources().getString(R.string.sessions_panel_new));
        add.setOnClickListener(v -> { if (mListener != null) mListener.onNewSession(); });
        add.setOnLongClickListener(v -> {
            if (mListener == null) return false;
            mListener.onNewSessionPrompt();
            return true;
        });
        header.addView(add, new LayoutParams(dp(32), LayoutParams.MATCH_PARENT));
        return header;
    }

    private void rebuild() {
        if (mRows == null) return;
        mHeaders.clear();
        mWindows.clear();
        mRows.removeAllViews();
        float widest = 0f;
        int visibleRows = 0;
        for (SessionBrowserModel.Session session : mSessions) {
            widest = Math.max(widest, measureText(sessionTitle(session), 12.5f));
            LayoutParams hp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(40));
            if (mRows.getChildCount() > 0) hp.topMargin = dp(1);
            mRows.addView(buildSessionHeader(session), hp);
            visibleRows++;
            if (mExpandedSessionId != null && mExpandedSessionId == session.id) {
                for (int i = 0; i < session.windows.size(); i++) {
                    SessionBrowserModel.Window window = session.windows.get(i);
                    widest = Math.max(widest, measureText(windowTitle(window), 11f));
                    mRows.addView(buildWindowRow(session, window, i == session.windows.size() - 1),
                        new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));
                    visibleRows++;
                }
            }
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        mLayout = SessionsPanelMetrics.calculate(widest, dp(88),
            Math.min(dm.widthPixels, dm.heightPixels), dm.density);
        boolean empty = mSessions.isEmpty();
        mEmpty.setVisibility(empty ? VISIBLE : GONE);
        mScroll.setVisibility(empty ? GONE : VISIBLE);
        LayoutParams params = (LayoutParams) mScroll.getLayoutParams();
        params.height = visibleRows > 7 ? dp(7 * 40) : LayoutParams.WRAP_CONTENT;
        mScroll.setLayoutParams(params);
        rebindRows();
    }

    private View buildSessionHeader(@NonNull SessionBrowserModel.Session session) {
        LinearLayout row = baseRow();
        row.setTag(session.id);
        row.setPaddingRelative(dp(6), dp(2), dp(2), dp(2));
        TextView chevron = label(15f, mOnSurfaceVariant);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LayoutParams(dp(22), LayoutParams.MATCH_PARENT));
        LinearLayout text = textBlock();
        TextView title = label(12.5f, mOnSurface);
        TextView subtitle = label(9.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 170));
        text.addView(title);
        text.addView(subtitle);
        row.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView more = label(18f, mOnSurfaceVariant);
        more.setText("⋮");
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getResources().getString(
            R.string.sessions_panel_more, session.index + 1));
        more.setOnClickListener(v -> showOverflow(v, session.id));
        row.addView(more, new LayoutParams(dp(36), LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> post(() -> {
            mExpandedSessionId = mExpandedSessionId != null && mExpandedSessionId == session.id
                ? null : session.id;
            rebuild();
        }));
        mHeaders.put(session.id, new HeaderHolder(row, title, subtitle, chevron, more));
        return row;
    }

    private View buildWindowRow(@NonNull SessionBrowserModel.Session session,
                                @NonNull SessionBrowserModel.Window window, boolean last) {
        LinearLayout row = baseRow();
        row.setTag(window.id);
        row.setPaddingRelative(dp(18), dp(1), dp(5), dp(1));
        TextView connector = label(12f, ColorUtils.setAlphaComponent(mOutlineVariant, 190));
        connector.setText(last ? "└" : "├");
        connector.setGravity(Gravity.CENTER);
        row.addView(connector, new LayoutParams(dp(18), LayoutParams.MATCH_PARENT));
        LinearLayout text = textBlock();
        TextView title = label(11f, mOnSurface);
        TextView subtitle = label(9.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 160));
        text.addView(title);
        text.addView(subtitle);
        row.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> {
            if (mListener != null) mListener.onWindowSelected(session.id, window.id);
        });
        mWindows.put(window.id, new WindowHolder(row, title, subtitle));
        return row;
    }

    private void rebindRows() {
        for (SessionBrowserModel.Session session : mSessions) {
            HeaderHolder holder = mHeaders.get(session.id);
            if (holder != null) {
                boolean expanded = mExpandedSessionId != null && mExpandedSessionId == session.id;
                holder.title.setText(sessionTitle(session));
                holder.title.setTypeface(null, session.current ? Typeface.BOLD : Typeface.NORMAL);
                holder.subtitle.setText(counts(session));
                holder.chevron.setText(expanded ? "⌄" : "›");
                holder.row.setBackground(rowSurface(session.current));
                holder.row.setContentDescription(sessionContentDescription(session, expanded));
                holder.more.setOnClickListener(v -> showOverflow(v, session.id));
            }
            for (SessionBrowserModel.Window window : session.windows) {
                WindowHolder child = mWindows.get(window.id);
                if (child == null) continue;
                child.title.setText(windowTitle(window));
                child.title.setTypeface(null, window.current ? Typeface.BOLD : Typeface.NORMAL);
                child.subtitle.setText(getResources().getQuantityString(
                    R.plurals.session_browser_pane_count, window.panes.size(), window.panes.size()));
                child.row.setBackground(rowSurface(session.current && window.current));
                child.row.setContentDescription(windowTitle(window) + " · " + child.subtitle.getText());
            }
        }
    }

    private void showOverflow(@NonNull View anchor, long sessionId) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(Menu.NONE, MENU_RENAME, 0, R.string.session_browser_rename);
        popup.getMenu().add(Menu.NONE, MENU_CLOSE, 1, R.string.session_browser_close);
        popup.setOnMenuItemClickListener(item -> {
            if (mListener == null) return false;
            if (item.getItemId() == MENU_RENAME) mListener.onSessionRenameRequested(sessionId);
            else if (item.getItemId() == MENU_CLOSE) mListener.onSessionClosed(sessionId);
            else return false;
            return true;
        });
        popup.show();
    }

    @Nullable private SessionBrowserModel.Session findSession(long id) {
        for (SessionBrowserModel.Session session : mSessions) if (session.id == id) return session;
        return null;
    }

    @NonNull private String sessionTitle(@NonNull SessionBrowserModel.Session session) {
        return TextUtils.isEmpty(session.name)
            ? getResources().getString(R.string.sessions_panel_session_fallback, session.index + 1)
            : session.name;
    }

    @NonNull private String windowTitle(@NonNull SessionBrowserModel.Window window) {
        String label = TextUtils.isEmpty(window.label) ? getResources().getString(
            R.string.sessions_panel_window_fallback) : window.label;
        return getResources().getString(R.string.sessions_panel_window_title,
            window.index + 1, label);
    }

    @NonNull private String counts(@NonNull SessionBrowserModel.Session session) {
        return getResources().getQuantityString(R.plurals.session_browser_window_count,
            session.windows.size(), session.windows.size()) + " · "
            + getResources().getQuantityString(R.plurals.session_browser_pane_count,
            session.paneCount(), session.paneCount());
    }

    @NonNull private String sessionContentDescription(@NonNull SessionBrowserModel.Session session,
                                                      boolean expanded) {
        return sessionTitle(session) + " · " + counts(session) + " · "
            + (expanded ? getResources().getString(R.string.sessions_panel_expanded)
                : getResources().getString(R.string.sessions_panel_collapsed));
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private LinearLayout textBlock() {
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private GradientDrawable rowSurface(boolean current) {
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(mCapsuleSurface ? Math.min(mStatusBarRadiusPx, dp(20)) : 0f);
        surface.setColor(current ? ColorUtils.setAlphaComponent(mPrimary, 52)
            : ColorUtils.setAlphaComponent(mSecondary, 10));
        surface.setStroke(Math.max(1, dp(1)), current
            ? ColorUtils.setAlphaComponent(mPrimary, 100)
            : ColorUtils.setAlphaComponent(mOutlineVariant, 24));
        return surface;
    }

    private TextView label(float sp, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private float measureText(@NonNull String text, float sp) {
        android.text.TextPaint paint = new android.text.TextPaint(android.text.TextPaint.ANTI_ALIAS_FLAG);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        return paint.measureText(text);
    }

    @NonNull private static String structureKey(@NonNull List<SessionBrowserModel.Session> sessions) {
        StringBuilder out = new StringBuilder();
        for (SessionBrowserModel.Session session : sessions) {
            out.append(session.id).append(':');
            for (SessionBrowserModel.Window window : session.windows) out.append(window.id).append(',');
            out.append(';');
        }
        return out.toString();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HeaderHolder {
        final View row;
        final TextView title, subtitle, chevron, more;
        HeaderHolder(View row, TextView title, TextView subtitle, TextView chevron, TextView more) {
            this.row = row; this.title = title; this.subtitle = subtitle;
            this.chevron = chevron; this.more = more;
        }
    }

    private static final class WindowHolder {
        final View row;
        final TextView title, subtitle;
        WindowHolder(View row, TextView title, TextView subtitle) {
            this.row = row; this.title = title; this.subtitle = subtitle;
        }
    }
}
