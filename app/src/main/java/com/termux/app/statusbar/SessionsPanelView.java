package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.SessionBrowserModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fork-native sessions list, dropped beneath the status row by the session chip. Rows carry the
 * one-based index pill plus the session name or its focused shell, and the current session keeps a
 * primary-tinted wash, a leading bar, and the brighter label. Rebuilt in place from a
 * {@link SessionBrowserModel.Session} projection, so anything that rebuilds the drawer list also
 * refreshes this panel while it is open.
 */
public final class SessionsPanelView extends LinearLayout {

    /** Row and header actions. Index values are positions in the last bound projection. */
    public interface Listener {
        void onSessionSelected(int index);
        void onSessionClosed(int index);
        void onSessionRenameRequested(int index);
        void onNewSession();
        void onNewSessionPrompt();
    }

    private final int mPrimary;
    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mSecondary;
    private final int mTertiary;
    private final int mTertiaryContainer;
    private final int mOnTertiaryContainer;
    private final int mOutlineVariant;

    private final LinearLayout mRows;
    private final ScrollView mScroll;
    private final TextView mEmpty;

    @Nullable private Listener mListener;
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    @NonNull private List<SessionBrowserModel.Session> mSessions = new ArrayList<>();

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
        mTertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, mPrimary);
        mTertiaryContainer = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiaryContainer, mSecondary);
        mOnTertiaryContainer = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnTertiaryContainer, mOnSurface);
        mOutlineVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));

        addView(buildHeader(context), new LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));

        View hairline = new View(context);
        hairline.setBackgroundColor(ColorUtils.setAlphaComponent(mOutlineVariant, 96));
        LayoutParams hairlineParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            Math.max(1, dp(1)));
        hairlineParams.topMargin = dp(6);
        hairlineParams.bottomMargin = dp(4);
        addView(hairline, hairlineParams);

        mRows = new LinearLayout(context);
        mRows.setOrientation(VERTICAL);
        mScroll = new ScrollView(context);
        mScroll.setFillViewport(false);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        mScroll.addView(mRows, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mEmpty = label(10.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 168));
        mEmpty.setText(R.string.sessions_panel_empty);
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setVisibility(GONE);
        LayoutParams emptyParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(40));
        addView(mEmpty, emptyParams);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Match the row capsules to the status-bar surface geometry that opened the panel. */
    public void setSurfaceStyle(boolean capsule, float statusBarRadiusPx) {
        float radius = Math.max(0f, statusBarRadiusPx);
        if (mCapsuleSurface == capsule && mStatusBarRadiusPx == radius) return;
        mCapsuleSurface = capsule;
        mStatusBarRadiusPx = radius;
        rebuild();
    }

    /** Rebuild every row from a fresh projection. Safe to call while the panel is open. */
    public void bind(@NonNull List<SessionBrowserModel.Session> sessions) {
        mSessions = new ArrayList<>(sessions);
        rebuild();
    }

    private LinearLayout buildHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = label(10.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 190));
        title.setText(R.string.session_browser_title);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        AppCompatImageButton add = new AppCompatImageButton(context);
        add.setImageResource(R.drawable.ic_status_bar_add_window);
        ImageViewCompat.setImageTintList(add, ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(mTertiary, 184)));
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setBackground(null);
        add.setPadding(0, 0, 0, 0);
        add.setContentDescription(getResources().getString(R.string.sessions_panel_new));
        add.setOnClickListener(v -> {
            if (mListener != null) mListener.onNewSession();
        });
        add.setOnLongClickListener(v -> {
            if (mListener == null) return false;
            mListener.onNewSessionPrompt();
            return true;
        });
        header.addView(add, new LayoutParams(dp(24), LayoutParams.MATCH_PARENT));
        return header;
    }

    private void rebuild() {
        mRows.removeAllViews();
        for (SessionBrowserModel.Session session : mSessions) {
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
            if (mRows.getChildCount() > 0) params.topMargin = dp(3);
            mRows.addView(buildRow(session), params);
        }
        boolean empty = mSessions.isEmpty();
        mEmpty.setVisibility(empty ? VISIBLE : GONE);
        mScroll.setVisibility(empty ? GONE : VISIBLE);
        // Cap the list at roughly six rows; anything beyond that scrolls inside the panel.
        LayoutParams scrollParams = (LayoutParams) mScroll.getLayoutParams();
        int maxHeight = dp(6 * 47);
        int target = mSessions.size() > 6 ? maxHeight : LayoutParams.WRAP_CONTENT;
        if (scrollParams.height != target) {
            scrollParams.height = target;
            mScroll.setLayoutParams(scrollParams);
        }
    }

    private View buildRow(@NonNull SessionBrowserModel.Session session) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setClickable(true);
        row.setFocusable(true);
        row.setPaddingRelative(dp(4), dp(3), dp(4), dp(3));
        row.setBackground(rowSurface(session.current));

        View selectionBar = new View(context);
        GradientDrawable bar = new GradientDrawable();
        bar.setCornerRadius(dp(1.5f));
        bar.setColor(session.current ? mPrimary : 0);
        selectionBar.setBackground(bar);
        selectionBar.setVisibility(session.current ? VISIBLE : INVISIBLE);
        LayoutParams barParams = new LayoutParams(dp(3), dp(22));
        barParams.gravity = Gravity.CENTER_VERTICAL;
        barParams.setMarginEnd(dp(7));
        row.addView(selectionBar, barParams);

        TextView pill = label(10.5f, mOnTertiaryContainer);
        pill.setGravity(Gravity.CENTER);
        pill.setText(Integer.toString(session.index + 1));
        pill.setBackground(pillSurface());
        LayoutParams pillParams = new LayoutParams(dp(22), dp(22));
        pillParams.gravity = Gravity.CENTER_VERTICAL;
        pillParams.setMarginEnd(dp(9));
        row.addView(pill, pillParams);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        TextView title = label(13f, session.current
            ? mOnSurface : ColorUtils.setAlphaComponent(mOnSurfaceVariant, 214));
        title.setText(rowTitle(session));
        text.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
        TextView subtitle = label(10.5f, ColorUtils.setAlphaComponent(mOnSurfaceVariant, 148));
        subtitle.setTypeface(Typeface.SANS_SERIF);
        subtitle.setText(rowSubtitle(session));
        LayoutParams subtitleParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(2);
        text.addView(subtitle, subtitleParams);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(text, textParams);

        AppCompatImageButton close = new AppCompatImageButton(context);
        close.setImageResource(R.drawable.ic_sessions_panel_close);
        ImageViewCompat.setImageTintList(close, ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(mOnSurfaceVariant, 168)));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackground(null);
        close.setPadding(0, 0, 0, 0);
        close.setContentDescription(getResources().getString(
            R.string.sessions_panel_close_session, session.index + 1));
        close.setOnClickListener(v -> {
            if (mListener != null) mListener.onSessionClosed(session.index);
        });
        LayoutParams closeParams = new LayoutParams(dp(32), dp(32));
        closeParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(close, closeParams);

        row.setContentDescription(rowContentDescription(session));
        row.setOnClickListener(v -> {
            if (mListener != null) mListener.onSessionSelected(session.index);
        });
        row.setOnLongClickListener(v -> {
            if (mListener == null) return false;
            mListener.onSessionRenameRequested(session.index);
            return true;
        });
        return row;
    }

    private GradientDrawable rowSurface(boolean current) {
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(rowCornerRadiusPx());
        surface.setColor(current
            ? ColorUtils.setAlphaComponent(mPrimary, 58)
            : ColorUtils.setAlphaComponent(mSecondary, 14));
        surface.setStroke(Math.max(1, dp(1)), current
            ? ColorUtils.setAlphaComponent(mPrimary, 112)
            : ColorUtils.setAlphaComponent(mSecondary, 26));
        return surface;
    }

    private GradientDrawable pillSurface() {
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(mCapsuleSurface ? dp(11) : 0f);
        pill.setColor(ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(mTertiaryContainer, mTertiary, .22f), 198));
        pill.setStroke(Math.max(1, dp(1)), mTertiary);
        return pill;
    }

    private float rowCornerRadiusPx() {
        return mCapsuleSurface ? Math.min(mStatusBarRadiusPx, dp(44) / 2f) : 0f;
    }

    @NonNull
    private String rowTitle(@NonNull SessionBrowserModel.Session session) {
        if (!TextUtils.isEmpty(session.name)) return session.name;
        String foreground = focusedForeground(session);
        return foreground == null ? getResources().getString(R.string.sessions_panel_shell)
            : foreground;
    }

    @NonNull
    private String rowSubtitle(@NonNull SessionBrowserModel.Session session) {
        StringBuilder out = new StringBuilder();
        String cwd = focusedCwd(session);
        if (cwd != null) out.append(SessionBrowserModel.displayCwd(cwd)).append(" · ");
        out.append(getResources().getQuantityString(R.plurals.session_browser_window_count,
            session.windows.size(), session.windows.size()));
        out.append(" · ");
        out.append(getResources().getQuantityString(R.plurals.session_browser_pane_count,
            session.paneCount(), session.paneCount()));
        return out.toString();
    }

    @NonNull
    private String rowContentDescription(@NonNull SessionBrowserModel.Session session) {
        String name = TextUtils.isEmpty(session.name)
            ? getResources().getString(R.string.session_browser_unnamed, session.index + 1)
            : getResources().getString(R.string.session_browser_named, session.index + 1,
                session.name);
        return session.current
            ? name + " · " + getResources().getString(R.string.session_browser_current) : name;
    }

    @Nullable
    private static String focusedForeground(@NonNull SessionBrowserModel.Session session) {
        SessionBrowserModel.Pane pane = focusedPane(session);
        return pane == null ? null : pane.foreground;
    }

    @Nullable
    private static String focusedCwd(@NonNull SessionBrowserModel.Session session) {
        SessionBrowserModel.Pane pane = focusedPane(session);
        return pane == null ? null : pane.cwd;
    }

    @Nullable
    private static SessionBrowserModel.Pane focusedPane(
            @NonNull SessionBrowserModel.Session session) {
        for (SessionBrowserModel.Window window : session.windows) {
            if (!window.current || window.panes.isEmpty()) continue;
            int index = Math.max(0, Math.min(window.activePane, window.panes.size() - 1));
            return window.panes.get(index);
        }
        for (SessionBrowserModel.Window window : session.windows) {
            if (!window.panes.isEmpty()) return window.panes.get(0);
        }
        return null;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
