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

    /** The card's width in {@code roomPx}: wider with the font scale ({@link SheetWidth}). */
    int widthPx(int roomPx) {
        return SheetWidth.px(WIDTH_DP, mDensity, getResources().getConfiguration().fontScale, roomPx);
    }
    private static final float GAP_DP = 8f;
    private static final float PAD_DP = 6f;
    private static final float ROW_DP = 44f;
    private static final float ROW_SIDE_DP = 14f;
    private static final float ICON_DP = 20f;
    private static final float ICON_GAP_DP = 12f;
    private static final float CHIP_DP = 34f;
    /** Marks the card's title (the window's name), which is no row. */
    private static final Object HEADER = new Object();

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
        addView(mScroll, new LayoutParams(widthPx(context.getResources().getDisplayMetrics().widthPixels),
            ViewGroup.LayoutParams.WRAP_CONTENT,
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
        int right = bl[0] - hl[0] + cell.right;
        int bottom = bl[1] - hl[1] + cell.bottom;
        // A card at the bottom stands from the host's content bottom, inside its padding.
        int floor = host.getHeight() - host.getPaddingBottom();
        int gap = Math.round(GAP_DP * mDensity);
        int width = widthPx(host.getWidth() - 2 * gap);
        LayoutParams params = (LayoutParams) mScroll.getLayoutParams();
        params.width = width;
        params.gravity = Gravity.BOTTOM | Gravity.START;
        if (rail) {
            boolean railOnRight = (left + right) / 2 > host.getWidth() / 2;
            params.leftMargin = railOnRight ? Math.max(gap, left - gap - width)
                : Math.min(Math.max(gap, host.getWidth() - width - gap), right + gap);
            params.bottomMargin = Math.max(gap, floor - bottom);
            params.topMargin = gap;
        } else {
            // Above the whole bar (never over it), its start over the held mark's, kept on screen.
            int centre = (left + right) / 2;
            params.leftMargin = Math.max(gap, Math.min(host.getWidth() - width - gap, centre - width / 2));
            params.bottomMargin = Math.max(gap, floor - (bl[1] - hl[1]) + gap);
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

    /** The rows on the card now, for tests: their words in order (the Width row's choices joined). */
    @NonNull
    java.util.List<CharSequence> rowLabels() {
        java.util.List<CharSequence> labels = new java.util.ArrayList<>();
        for (View row : rowViews()) {
            if (row instanceof TextView) {
                labels.add(((TextView) row).getText());
            } else {
                StringBuilder joined = new StringBuilder();
                ViewGroup chips = (ViewGroup) row;
                for (int i = 0; i < chips.getChildCount(); i++) {
                    if (i > 0) joined.append(' ');
                    joined.append(((TextView) chips.getChildAt(i)).getText());
                }
                labels.add(joined.toString());
            }
        }
        return labels;
    }

    /** Taps row {@code index} on the card, for tests. */
    void clickRow(int index) {
        rowViews().get(index).performClick();
    }

    /** The card's title, or null while a list shows. */
    @Nullable
    CharSequence title() {
        View first = mRows.getChildCount() == 0 ? null : mRows.getChildAt(0);
        return first != null && first.getTag() == HEADER ? ((TextView) first).getText() : null;
    }

    /** Row {@code index}'s icon, for tests; null for a row without one. */
    @Nullable
    Drawable rowIcon(int index) {
        View row = rowViews().get(index);
        return row instanceof TextView ? ((TextView) row).getCompoundDrawablesRelative()[0] : null;
    }

    /** Row {@code index}'s end glyph (a list's chevron), for tests; null for none. */
    @Nullable
    Drawable rowEnd(int index) {
        View row = rowViews().get(index);
        return row instanceof TextView ? ((TextView) row).getCompoundDrawablesRelative()[2] : null;
    }

    /** Taps the Width row's choice {@code index}, for tests. */
    void clickWidth(int index) {
        for (View row : rowViews()) {
            if (row instanceof ViewGroup) {
                ((ViewGroup) row).getChildAt(index).performClick();
                return;
            }
        }
    }

    @NonNull
    private List<View> rowViews() {
        List<View> rows = new java.util.ArrayList<>();
        for (int i = 0; i < mRows.getChildCount(); i++) {
            View child = mRows.getChildAt(i);
            if (child.getTag() != HEADER) rows.add(child);
        }
        return rows;
    }

    private void showRows(@NonNull List<WindowMenu.Row> rows, boolean list) {
        mRows.removeAllViews();
        int ink = mStyle.ink();
        if (list) {
            mRows.addView(row(getContext().getString(R.string.window_menu_back), ink, true, 0,
                v -> {
                    WindowMenu.Subject subject = mSubject;
                    if (subject != null) showRows(subject.rows, false);
                }));
        } else if (!mWindowName.isEmpty()) {
            mRows.addView(header(mWindowName, ink));
        }
        for (WindowMenu.Row row : rows) {
            if (row.action == WindowMenu.Action.WIDTH) {
                WindowMenu.Subject subject = mSubject;
                if (subject != null && !subject.widthRows.isEmpty()) mRows.addView(widthRow(subject.widthRows, ink));
                continue;
            }
            String label = label(getContext(), row, false);
            int rowInk = row.action == WindowMenu.Action.CLOSE ? closeInk(ink) : ink;
            TextView view = row(label, rowInk, false, iconOf(row.action), v -> choose(row));
            // Move to Workspace opens a list: a chevron at its end, as the prototype's.
            if (row.action == WindowMenu.Action.MOVE_LIST) addChevron(view, ink);
            mRows.addView(view);
        }
        mScroll.scrollTo(0, 0);
    }

    /** The window's name over its rows, small and quiet, as the prototype's card is titled. */
    @NonNull
    private TextView header(@NonNull String name, int ink) {
        TextView text = new TextView(getContext());
        text.setTag(HEADER);
        text.setText(name.toUpperCase(java.util.Locale.getDefault()));
        text.setTextColor(ColorUtils.setAlphaComponent(ink, 170));
        text.setTextSize(11f);
        text.setLetterSpacing(0.08f);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int side = Math.round(ROW_SIDE_DP * mDensity);
        text.setPadding(side, Math.round(PAD_DP * mDensity), side, Math.round(PAD_DP * mDensity));
        return text;
    }

    /** The Width row: a chip per column width, narrowest first. */
    @NonNull
    private View widthRow(@NonNull List<WindowMenu.Row> widths, int ink) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int side = Math.round(ROW_SIDE_DP * mDensity);
        int gap = Math.round(PAD_DP * mDensity);
        row.setPadding(side - gap, gap, side - gap, gap);
        for (WindowMenu.Row width : widths) {
            TextView chip = new TextView(getContext());
            chip.setText(label(getContext(), width, false));
            chip.setContentDescription(label(getContext(), width, true));
            chip.setTextColor(ink);
            chip.setTextSize(13f);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setMinHeight(Math.round(CHIP_DP * mDensity));
            GradientDrawable face = new GradientDrawable();
            face.setCornerRadius(CHIP_DP * mDensity / 3f);
            face.setColor(ColorUtils.setAlphaComponent(ink, 0x1F));
            chip.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 0x33)), face, null));
            chip.setOnClickListener(v -> choose(width));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = gap;
            params.rightMargin = gap;
            row.addView(chip, params);
        }
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Close reads as the one row that takes something away: the theme's error colour. */
    private int closeInk(int ink) {
        return com.google.android.material.color.MaterialColors.getColor(getContext(),
            androidx.appcompat.R.attr.colorError, ink);
    }

    /** Each row's icon, as the prototype's card has one; 0 for none. */
    private static int iconOf(@NonNull WindowMenu.Action action) {
        switch (action) {
            case FLOAT: return R.drawable.ic_ms_layers;
            case TILE: return R.drawable.ic_ms_view_quilt;
            case PIN:
            case UNPIN:
            case FLOAT_AND_PIN: return R.drawable.ic_ms_push_pin;
            case FULL_SCREEN: return R.drawable.ic_ms_fullscreen;
            case LEAVE_FULL_SCREEN: return R.drawable.ic_ms_fit_screen;
            case MINIMISE: return R.drawable.ic_ms_vertical_align_bottom;
            case RESTORE: return R.drawable.ic_ms_visibility;
            case MAXIMISE: return R.drawable.ic_ms_open_in_full;
            case UNMAXIMISE: return R.drawable.ic_ms_fit_screen;
            case SCALE: return R.drawable.ic_ms_zoom_in;
            case MOVE_LIST: return R.drawable.ic_ms_swap_horiz;
            case TAB_LIST: return R.drawable.ic_ms_view_carousel;
            case STACK_LIST: return R.drawable.ic_ms_splitscreen;
            case SEPARATE: return R.drawable.ic_ms_link_off;
            case EDIT_LOOK: return R.drawable.ic_ms_brush;
            case CLOSE: return R.drawable.ic_rl_close;
            default: return 0;
        }
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
    private TextView row(@NonNull String label, int ink, boolean back, int icon, @NonNull OnClickListener click) {
        TextView text = new TextView(getContext());
        if (icon != 0) {
            Drawable glyph = androidx.core.content.ContextCompat.getDrawable(getContext(), icon);
            if (glyph != null) {
                glyph = glyph.mutate();
                int size = Math.round(ICON_DP * mDensity);
                glyph.setBounds(0, 0, size, size);
                glyph.setTint(ink);
                text.setCompoundDrawablesRelative(glyph, null, null, null);
                text.setCompoundDrawablePadding(Math.round(ICON_GAP_DP * mDensity));
            }
        }
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

    /** A small chevron at the row's end, in the ink at its quieter strength. */
    private void addChevron(@NonNull TextView row, int ink) {
        Drawable chevron = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ic_editor_chevron_right);
        if (chevron == null) return;
        chevron = chevron.mutate();
        int size = Math.round(CHEVRON_DP * mDensity);
        chevron.setBounds(0, 0, size, size);
        chevron.setTint(ColorUtils.setAlphaComponent(ink, 170));
        Drawable[] now = row.getCompoundDrawablesRelative();
        row.setCompoundDrawablesRelative(now[0], now[1], chevron, now[3]);
    }

    /** The Move to Workspace row's chevron, in dp. */
    private static final float CHEVRON_DP = 16f;

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
            case FLOAT_AND_PIN: return context.getString(R.string.window_menu_float_and_pin);
            case WIDTH: return context.getString(R.string.window_menu_width);
            case SET_WIDTH: {
                String width = widthName(context, row.width);
                return flat ? context.getString(R.string.window_menu_set_width, width) : width;
            }
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

    /** A column width's name: Full, or the fraction (1/3, 1/2, 2/3). */
    @NonNull
    static String widthName(@NonNull Context context, float fraction) {
        if (Math.abs(fraction - 1f) < 0.01f) return context.getString(R.string.window_menu_width_full);
        if (Math.abs(fraction - 0.5f) < 0.01f) return context.getString(R.string.window_menu_width_half);
        if (Math.abs(fraction - 2f / 3f) < 0.01f) return context.getString(R.string.window_menu_width_two_thirds);
        return context.getString(R.string.window_menu_width_third);
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
