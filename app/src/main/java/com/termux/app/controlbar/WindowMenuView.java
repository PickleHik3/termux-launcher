package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.tiling.WindowKind;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.List;

/**
 * A held mark's window menu on screen (#425): a glass card in the SHEETS layer standing over the
 * held mark (beside the rail), one row per {@link WindowMenu.Row}. A row that opens a list (Move to
 * Workspace, Tab with, Stack with) shows that list in the card's place with Back at its top. A
 * clear scrim takes a touch outside the card as "close"; Back closes it through the bar
 * ({@link ControlBar#closeSheet}).
 */
public final class WindowMenuView extends FrameLayout {

    static final float WIDTH_DP = 232f;
    private static final float GAP_DP = 8f;
    private static final float PAD_DP = 6f;
    private static final float ROW_DP = 44f;
    private static final float ROW_SIDE_DP = 14f;

    /** What the card asks of its owner. */
    public interface Listener {
        /** Row {@code row} was chosen; a list's row opens the list here and is not reported. */
        void onRow(@NonNull WindowMenu.Row row);

        /** A touch outside the card. */
        void onDismiss();
    }

    @NonNull private final ReadingCardView.Style mStyle;
    @NonNull private final Listener mListener;
    @NonNull private final ScrollView mScroll;
    @NonNull private final LinearLayout mRows;
    private final float mDensity;
    @Nullable private WindowMenu.Subject mSubject;
    @NonNull private String mWindowName = "";

    @SuppressLint("ClickableViewAccessibility")
    public WindowMenuView(@NonNull Context context, @NonNull ReadingCardView.Style style,
                          @NonNull Listener listener) {
        super(context);
        mStyle = style;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        mScroll = new ScrollView(context);
        mScroll.setClickable(true);
        mScroll.setVerticalScrollBarEnabled(false);
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(PAD_DP * mDensity);
        mRows.setPadding(0, pad, 0, pad);
        mScroll.addView(mRows, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(Math.round(WIDTH_DP * mDensity), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.START));
        mScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::dress);
        });
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mListener.onDismiss();
            return true;
        });
    }

    /**
     * Opens the card for {@code subject} in the SHEETS layer above {@code bar}, at the held cell
     * {@code cell} (bar coordinates). False when there is no sheets layer (nothing shows).
     */
    public boolean show(@NonNull View bar, @NonNull Rect cell, boolean rail, @NonNull WindowMenu.Subject subject,
                        @NonNull String windowName) {
        mSubject = subject;
        mWindowName = windowName;
        setContentDescription(getContext().getString(R.string.window_menu_description, windowName));
        showRows(subject.rows, false);
        // Open already (a screen reader can long-click another mark through the scrim): it moves
        // over the new mark.
        ViewGroup host = getParent() instanceof ViewGroup ? (ViewGroup) getParent()
            : WindowLayers.host(id -> bar.getRootView().findViewById(id), WindowLayer.SHEETS);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        int[] bl = new int[2];
        int[] hl = new int[2];
        bar.getLocationInWindow(bl);
        host.getLocationInWindow(hl);
        int left = bl[0] - hl[0] + cell.left;
        int top = bl[1] - hl[1] + cell.top;
        int right = bl[0] - hl[0] + cell.right;
        int bottom = bl[1] - hl[1] + cell.bottom;
        int gap = Math.round(GAP_DP * mDensity);
        int width = Math.round(WIDTH_DP * mDensity);
        LayoutParams params = (LayoutParams) mScroll.getLayoutParams();
        params.gravity = Gravity.BOTTOM | Gravity.START;
        if (rail) {
            boolean railOnRight = (left + right) / 2 > host.getWidth() / 2;
            params.leftMargin = railOnRight ? Math.max(gap, left - gap - width)
                : Math.min(Math.max(gap, host.getWidth() - width - gap), right + gap);
            params.bottomMargin = Math.max(gap, host.getHeight() - bottom);
            params.topMargin = gap;
        } else {
            // Over the bar, its start over the held mark's, kept on screen.
            int centre = (left + right) / 2;
            params.leftMargin = Math.max(gap, Math.min(host.getWidth() - width - gap, centre - width / 2));
            params.bottomMargin = Math.max(gap, host.getHeight() - top + gap);
            params.topMargin = gap;
        }
        mScroll.setLayoutParams(params);
        if (getParent() == null) {
            host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return true;
    }

    /** What the card was drawn from; null while it is closed. */
    @Nullable
    public WindowMenu.Subject subject() {
        return mSubject;
    }

    /** The stack changed under the open card: its rows are drawn again from {@code subject}, from the top. */
    public void rebind(@NonNull WindowMenu.Subject subject) {
        mSubject = subject;
        showRows(subject.rows, false);
    }

    /** Whether the card is on screen. */
    public boolean isOpen() {
        return getParent() != null;
    }

    /** Takes the card off the screen. */
    public void close() {
        mSubject = null;
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null) parent.removeView(this);
    }

    /** The rows on the card now, for tests: their spoken labels in order. */
    @NonNull
    java.util.List<CharSequence> rowLabels() {
        java.util.List<CharSequence> labels = new java.util.ArrayList<>();
        for (int i = 0; i < mRows.getChildCount(); i++) labels.add(((TextView) mRows.getChildAt(i)).getText());
        return labels;
    }

    /** Taps row {@code index} on the card, for tests. */
    void clickRow(int index) {
        mRows.getChildAt(index).performClick();
    }

    private void showRows(@NonNull List<WindowMenu.Row> rows, boolean list) {
        mRows.removeAllViews();
        int ink = mStyle.ink();
        if (list) mRows.addView(row(getContext().getString(R.string.window_menu_back), ink, true,
            v -> {
                WindowMenu.Subject subject = mSubject;
                if (subject != null) showRows(subject.rows, false);
            }));
        for (WindowMenu.Row row : rows) {
            String label = label(getContext(), row, false);
            mRows.addView(row(label, ink, false, v -> choose(row)));
        }
        mScroll.scrollTo(0, 0);
    }

    private void choose(@NonNull WindowMenu.Row row) {
        WindowMenu.Subject subject = mSubject;
        if (subject == null) return;
        if (row.action.opensList()) {
            showRows(subject.listOf(row.action), true);
            return;
        }
        mListener.onRow(row);
    }

    @NonNull
    private TextView row(@NonNull String label, int ink, boolean back, @NonNull OnClickListener click) {
        TextView text = new TextView(getContext());
        text.setText(back ? "‹  " + label : label);
        text.setContentDescription(label);
        text.setTextColor(back ? ColorUtils.setAlphaComponent(ink, 190) : ink);
        text.setTextSize(15f);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int side = Math.round(ROW_SIDE_DP * mDensity);
        text.setPadding(side, 0, side, 0);
        text.setMinHeight(Math.round(ROW_DP * mDensity));
        text.setBackgroundResource(rippleBackground());
        text.setOnClickListener(click);
        text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return text;
    }

    private int rippleBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    /**
     * What row {@code row} reads: in the card a list's row is the place or the window alone; in a
     * screen reader's flat list ({@code flat}) it says what it does ("Move to Workspace 2").
     */
    @NonNull
    static String label(@NonNull Context context, @NonNull WindowMenu.Row row, boolean flat) {
        switch (row.action) {
            case FLOAT: return context.getString(R.string.window_menu_float);
            case TILE: return context.getString(R.string.window_menu_tile);
            case PIN: return context.getString(R.string.window_menu_pin);
            case UNPIN: return context.getString(R.string.window_menu_unpin);
            case FULL_SCREEN: return context.getString(R.string.window_menu_full_screen);
            case LEAVE_FULL_SCREEN: return context.getString(R.string.window_menu_leave_full_screen);
            case MINIMISE: return context.getString(R.string.window_menu_minimise);
            case RESTORE: return context.getString(R.string.window_menu_restore);
            case MAXIMISE: return context.getString(R.string.window_menu_maximise);
            case UNMAXIMISE: return context.getString(R.string.window_menu_unmaximise);
            case SCALE: return context.getString(R.string.window_menu_scale);
            case MOVE_LIST: return context.getString(R.string.window_menu_move_list);
            case TAB_LIST: return context.getString(R.string.window_menu_tab_list);
            case STACK_LIST: return context.getString(R.string.window_menu_stack_list);
            case SEPARATE:
                return context.getString(row.tabs ? R.string.window_menu_take_out_tabs
                    : R.string.window_menu_take_out_stack);
            case MOVE: {
                String place = placeName(context, row);
                return flat ? context.getString(R.string.window_menu_move_to, place) : place;
            }
            case TAB: {
                String name = windowName(context, row.name, row.kind);
                return flat ? context.getString(R.string.window_menu_tab_with, name) : name;
            }
            case STACK: {
                String name = windowName(context, row.name, row.kind);
                return flat ? context.getString(R.string.window_menu_stack_with, name) : name;
            }
            case EDIT_LOOK: return context.getString(R.string.window_menu_edit_look);
            case CLOSE:
            default:
                return context.getString(R.string.window_menu_close);
        }
    }

    @NonNull
    private static String placeName(@NonNull Context context, @NonNull WindowMenu.Row row) {
        if (row.place == null || row.place.isHome()) return context.getString(R.string.window_menu_home);
        if (row.newWorkspace) return context.getString(R.string.window_menu_new_workspace);
        int number = row.place.workspaceNumber();
        return row.name == null || row.name.isEmpty()
            ? context.getString(R.string.window_menu_workspace, number)
            : context.getString(R.string.window_menu_named_workspace, number, row.name);
    }

    /** A window's name, or its kind's when it has none. */
    @NonNull
    static String windowName(@NonNull Context context, @Nullable String name, @Nullable WindowKind kind) {
        if (name != null && !name.isEmpty()) return name;
        if (kind == null) return context.getString(R.string.control_bar_kind_terminal);
        switch (kind) {
            case LINUX_APP: return context.getString(R.string.control_bar_kind_linux_app);
            case DISPLAY: return context.getString(R.string.control_bar_kind_display);
            case ASSISTANT: return context.getString(R.string.control_bar_kind_assistant);
            case SHELL:
            default: return context.getString(R.string.control_bar_kind_terminal);
        }
    }

    /** The glass is cut for the card's size, never from inside the layout pass. */
    private void dress() {
        if (mScroll.getWidth() <= 0 || mScroll.getHeight() <= 0 || !isOpen()) return;
        float radius = mStyle.cornerRadiusPx();
        Drawable glass = mStyle.glass(radius, mScroll);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6));
            glass = plain;
        }
        mScroll.setBackground(glass);
        mScroll.setClipToOutline(true);
        mScroll.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mScroll.post(this::dress);
    }
}
