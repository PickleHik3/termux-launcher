package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.termux.R;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.List;

/**
 * The Workspace list on screen (#429): a glass card standing above the control bar (beside the
 * rail), one row per Workspace with its number, its main window's icon and its name. It is a sheet
 * in the SHEETS layer; the clear scrim around the card takes a touch outside as "close".
 *
 * <p>Two ways of being up. While a finger slides up from the bar the bar keeps the touch and feeds
 * it here ({@link #track}, {@link #release}): the row under the finger is lit, and letting go on one
 * goes there. A finger held still on a row renames it. Opened without a finger (the bar's
 * accessibility action) the card stays until a row is chosen or it is dismissed; a tap on a row goes
 * there and a long press renames it. A rename is an inline field in the row.
 */
public final class WorkspaceListView extends FrameLayout {

    static final float WIDTH_DP = 280f;
    private static final float GAP_DP = 8f;
    private static final float PAD_DP = 8f;
    static final float ROW_DP = 48f;
    private static final float NUMBER_DP = 28f;
    private static final float ICON_DP = 20f;
    private static final float TOP_CLEAR_DP = 72f;

    /** What the card needs of the bar: its glass, ink, accent and corner. */
    public interface Style {
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();

        int accent();

        /** A Linux app's own icon by its app id; null for its kind's glyph. */
        @Nullable Drawable appIcon(@NonNull String appId);
    }

    /** What the card asks of its owner. */
    public interface Listener {
        /** Letting go on, or tapping, a row: go to its Workspace. */
        void onGo(@NonNull Place place);

        /** A name was typed for Workspace {@code number}; blank clears it. */
        void onRename(int number, @NonNull String name);

        /** A touch outside the card, or the end of a rename: put the card away. */
        void onDismiss();

        /** The inline field wants the keyboard. */
        void onTextInput(@NonNull EditText field, boolean begin);
    }

    @NonNull private final Style mStyle;
    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mCard;
    @NonNull private final LinearLayout mRowsView;
    @NonNull private final ScrollView mScroll;
    @NonNull private final List<WorkspaceListRows.Row> mRows = new ArrayList<>();
    @NonNull private final List<View> mRowViews = new ArrayList<>();
    @NonNull private final Rect mScratch = new Rect();
    @NonNull private final Runnable mRenameHold = this::renameLit;
    private final float mDensity;
    private final int mTouchSlop;
    /** Follows the bar and the keyboards once each layout settles (set only on change, in fit()). */
    @NonNull private final ViewTreeObserver.OnGlobalLayoutListener mFollow = this::fit;
    @Nullable private View mBar;
    @Nullable private ViewGroup mLayer;
    private boolean mRail;
    private int mAppliedLeft = Integer.MIN_VALUE;
    private int mAppliedBottom = Integer.MIN_VALUE;
    private boolean mFitPosted;
    private float mArmX;
    private float mArmY;
    private int mLit = -1;
    private int mEditing = -1;
    @Nullable private EditText mField;
    private boolean mFinger;

    @SuppressLint("ClickableViewAccessibility")
    public WorkspaceListView(@NonNull Context context, @NonNull Style style, @NonNull Listener listener) {
        super(context);
        mStyle = style;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setContentDescription(context.getString(R.string.control_bar_workspace_list));
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(PAD_DP * mDensity);
        mCard.setPadding(pad, pad, pad, pad);
        // A touch on the card is the card's; only the scrim around it closes.
        mCard.setClickable(true);
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mRowsView = new LinearLayout(context);
        mRowsView.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mRowsView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mCard.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams cardParams = new LayoutParams(Math.round(WIDTH_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        cardParams.topMargin = Math.round(TOP_CLEAR_DP * mDensity);
        addView(mCard, cardParams);
        mCard.setVisibility(INVISIBLE);
        mCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::dress);
        });
        setOnTouchListener((v, event) -> {
            // While a finger is sliding the bar owns the touch; this only sees a card opened without one.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !mFinger) dismissFromScrim();
            return true;
        });
    }

    // ---- showing ---------------------------------------------------------------------------------

    /**
     * Puts the card in {@code host} above {@code bar} (beside it on a rail); the card shows once placed.
     * False when the host has no size yet.
     */
    boolean placeOver(@NonNull View bar, @NonNull ViewGroup host, boolean rail) {
        if (host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        mBar = bar;
        mLayer = host;
        mRail = rail;
        int[] spot = spot(bar, host, rail);
        mAppliedLeft = spot[0];
        mAppliedBottom = spot[1];
        apply(spot);
        mCard.setVisibility(VISIBLE);
        return true;
    }

    /**
     * Where the card stands in {@code host}: {left margin, bottom margin}. Over the bar, its start
     * level with the bar's (above the keyboards when they stand over it); beside it for a rail.
     * Left is meant: the margins are absolute, so the card does not flip in a right-to-left layout.
     */
    @NonNull
    private int[] spot(@NonNull View bar, @NonNull ViewGroup host, boolean rail) {
        int[] bl = new int[2];
        int[] hl = new int[2];
        bar.getLocationInWindow(bl);
        host.getLocationInWindow(hl);
        int left = bl[0] - hl[0];
        int top = bl[1] - hl[1];
        int right = left + bar.getWidth();
        int bottom = top + bar.getHeight();
        int gap = Math.round(GAP_DP * mDensity);
        int width = Math.round(WIDTH_DP * mDensity);
        // The keyboards and extra keys cap what is free, as the bar search's panel does.
        int keysTop = host.getHeight();
        View root = host.getRootView();
        int[] at = new int[2];
        for (int id : new int[] {R.id.inapp_keyboard_container, R.id.terminal_toolbar_host}) {
            View keys = root.findViewById(id);
            if (keys == null || !keys.isShown() || keys.getHeight() <= 0) continue;
            keys.getLocationInWindow(at);
            keysTop = Math.min(keysTop, at[1] - hl[1]);
        }
        int leftMargin;
        int bottomMargin;
        if (rail) {
            boolean railOnRight = (left + right) / 2 > host.getWidth() / 2;
            leftMargin = railOnRight ? Math.max(gap, left - gap - width) : Math.min(
                Math.max(gap, host.getWidth() - width - gap), right + gap);
            bottomMargin = Math.max(gap, host.getHeight() - Math.min(bottom, keysTop));
        } else {
            leftMargin = Math.max(gap, Math.min(host.getWidth() - width - gap, left));
            bottomMargin = Math.max(gap, host.getHeight() - Math.min(top, keysTop) + gap);
        }
        return new int[] {leftMargin, bottomMargin};
    }

    private void apply(@NonNull int[] spot) {
        LayoutParams params = (LayoutParams) mCard.getLayoutParams();
        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        params.leftMargin = spot[0];
        params.bottomMargin = spot[1];
        mCard.setLayoutParams(params);
    }

    /** Follows the bar and the keyboards: moves the card only when its place changed, never in the pass. */
    private void fit() {
        View bar = mBar;
        ViewGroup layer = mLayer;
        if (mFitPosted || bar == null || layer == null || layer.getWidth() <= 0) return;
        int[] spot = spot(bar, layer, mRail);
        if (spot[0] == mAppliedLeft && spot[1] == mAppliedBottom) return;
        mFitPosted = true;
        post(() -> {
            mFitPosted = false;
            if (getParent() == null || mBar == null || mLayer == null) return;
            int[] now = spot(mBar, mLayer, mRail);
            mAppliedLeft = now[0];
            mAppliedBottom = now[1];
            apply(now);
        });
    }

    /** The card is up with no finger: say so and put accessibility focus on the Workspace in front. */
    void announceOpen() {
        View target = mRowViews.isEmpty() ? mCard : mRowViews.get(Math.max(0, frontIndex()));
        post(() -> {
            announceForAccessibility(getContext().getString(R.string.control_bar_workspace_list));
            target.performAccessibilityAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
    }

    private int frontIndex() {
        for (int i = 0; i < mRows.size(); i++) if (mRows.get(i).front) return i;
        return 0;
    }

    /** The rows to show; the one with focus on its Workspace is marked. */
    public void bind(@NonNull List<WorkspaceListRows.Row> rows) {
        mRows.clear();
        mRows.addAll(rows);
        mRowViews.clear();
        mRowsView.removeAllViews();
        mLit = -1;
        mEditing = -1;
        mField = null;
        for (int i = 0; i < mRows.size(); i++) {
            View row = buildRow(i, mRows.get(i));
            mRowViews.add(row);
            mRowsView.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(ROW_DP * mDensity)));
        }
    }

    @NonNull
    private View buildRow(int index, @NonNull WorkspaceListRows.Row row) {
        Context context = getContext();
        int ink = mStyle.ink();
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int side = Math.round(10f * mDensity);
        view.setPadding(side, 0, side, 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setContentDescription(describe(row));
        view.setOnClickListener(v -> {
            if (mEditing < 0) mListener.onGo(row.place());
        });
        if (row.renamable()) {
            view.setOnLongClickListener(v -> {
                startRename(index);
                return true;
            });
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.workspace_list_rename),
                (v, args) -> {
                    startRename(index);
                    return true;
                });
        }
        TextView number = new TextView(context);
        number.setText(row.waiting ? "+" : Integer.toString(row.number));
        number.setTextColor(row.front ? mStyle.accent() : ColorUtils.setAlphaComponent(ink, 200));
        number.setTextSize(15f);
        number.setTypeface(number.getTypeface(), android.graphics.Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        number.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.addView(number, new LinearLayout.LayoutParams(Math.round(NUMBER_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView icon = new ImageView(context);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Drawable own = row.appId == null ? null : mStyle.appIcon(row.appId);
        if (own != null) icon.setImageDrawable(own);
        else if (row.kind != null) icon.setImageResource(kindIcon(row.kind));
        icon.setColorFilter(row.front ? mStyle.accent() : ColorUtils.setAlphaComponent(ink, 220));
        int size = Math.round(ICON_DP * mDensity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        iconParams.setMarginStart(Math.round(6f * mDensity));
        view.addView(icon, iconParams);
        TextView name = new TextView(context);
        name.setText(label(row));
        name.setTextColor(row.front ? mStyle.accent() : ink);
        name.setTextSize(15f);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(Math.round(10f * mDensity));
        view.addView(name, nameParams);
        return view;
    }

    /** What a row reads: its name, "New Workspace" for the waiting one, else "Workspace N". */
    @NonNull
    private String label(@NonNull WorkspaceListRows.Row row) {
        Context context = getContext();
        if (row.waiting) return context.getString(R.string.workspace_list_new);
        return row.name != null ? row.name : context.getString(R.string.workspace_list_unnamed, row.number);
    }

    @NonNull
    private String describe(@NonNull WorkspaceListRows.Row row) {
        Context context = getContext();
        if (row.waiting) return context.getString(R.string.workspace_list_new);
        return row.name != null
            ? context.getString(R.string.workspace_list_row_named, row.number, row.name)
            : context.getString(R.string.workspace_list_unnamed, row.number);
    }

    static int kindIcon(@NonNull WindowKind kind) {
        switch (kind) {
            case LINUX_APP: return R.drawable.ic_distro_linux;
            case DISPLAY: return R.drawable.ic_ms_desktop_windows;
            case ASSISTANT: return R.drawable.ic_ms_auto_awesome;
            case SHELL:
            default: return R.drawable.ic_ms_terminal;
        }
    }

    // ---- a finger from the bar ---------------------------------------------------------------------

    /** A finger is sliding up from the bar: the card is its, until {@link #release} or {@link #cancel}. */
    void beginFinger() {
        mFinger = true;
    }

    /** The finger is at ({@code rawX}, {@code rawY}) on screen: light the row it is over. */
    void track(float rawX, float rawY) {
        if (mEditing >= 0) return;
        int row = rowAt(rawX, rawY);
        if (row == mLit) {
            // Still on the row but moving: "held still" starts over from here.
            if (row >= 0 && Math.hypot(rawX - mArmX, rawY - mArmY) > mTouchSlop) arm(row, rawX, rawY);
            return;
        }
        removeCallbacks(mRenameHold);
        light(row);
        if (row >= 0) arm(row, rawX, rawY);
    }

    private void arm(int row, float rawX, float rawY) {
        removeCallbacks(mRenameHold);
        mArmX = rawX;
        mArmY = rawY;
        if (mRows.get(row).renamable()) postDelayed(mRenameHold, ViewConfiguration.getLongPressTimeout());
    }

    /** The finger lifted at ({@code rawX}, {@code rawY}): the Workspace to go to, or null for none. */
    @Nullable
    Place release(float rawX, float rawY) {
        removeCallbacks(mRenameHold);
        mFinger = false;
        if (mEditing >= 0) return null;
        int row = rowAt(rawX, rawY);
        return row < 0 ? null : mRows.get(row).place();
    }

    /** The finger was taken away (cancel): nothing is chosen. */
    void endFinger() {
        removeCallbacks(mRenameHold);
        mFinger = false;
    }

    /** Whether a finger from the bar is on the card, or it is held up by a rename. */
    boolean holdsOpen() {
        return mEditing >= 0;
    }

    private void renameLit() {
        if (mLit >= 0) startRename(mLit);
    }

    /** The row under ({@code rawX}, {@code rawY}) on screen; -1 for none. */
    int rowAt(float rawX, float rawY) {
        int x = Math.round(rawX);
        int y = Math.round(rawY);
        // Screen coordinates on both sides: the window need not start at the screen's corner.
        int[] at = new int[2];
        mScroll.getLocationOnScreen(at);
        Rect clip = new Rect(at[0], at[1], at[0] + mScroll.getWidth(), at[1] + mScroll.getHeight());
        for (int i = 0; i < mRowViews.size(); i++) {
            View row = mRowViews.get(i);
            row.getLocationOnScreen(at);
            mScratch.set(at[0], at[1], at[0] + row.getWidth(), at[1] + row.getHeight());
            if (mScratch.intersect(clip) && mScratch.contains(x, y)) return i;
        }
        return -1;
    }

    private void light(int row) {
        if (mLit >= 0 && mLit < mRowViews.size()) mRowViews.get(mLit).setBackground(null);
        mLit = row;
        if (row >= 0 && row < mRowViews.size()) {
            GradientDrawable plate = new GradientDrawable();
            plate.setCornerRadius(10f * mDensity);
            plate.setColor(ColorUtils.setAlphaComponent(mStyle.ink(), 0x33));
            mRowViews.get(row).setBackground(plate);
        }
    }

    // ---- renaming ----------------------------------------------------------------------------------

    /** Opens row {@code index}'s name as an inline field; the card stays up until it is done. */
    void startRename(int index) {
        if (index < 0 || index >= mRows.size() || !mRows.get(index).renamable() || mEditing >= 0) return;
        removeCallbacks(mRenameHold);
        mFinger = false;
        mEditing = index;
        WorkspaceListRows.Row row = mRows.get(index);
        LinearLayout view = (LinearLayout) mRowViews.get(index);
        View text = view.getChildAt(2);
        EditText field = new EditText(getContext());
        field.setText(row.name == null ? "" : row.name);
        field.setSelectAllOnFocus(true);
        field.setHint(R.string.workspace_list_name_hint);
        field.setHintTextColor(ColorUtils.setAlphaComponent(mStyle.ink(), 140));
        field.setTextColor(mStyle.ink());
        field.setTextSize(15f);
        field.setSingleLine(true);
        field.setMinWidth(0);
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setFocusableInTouchMode(true);
        // The keyboard comes by the activity's seam, not by the view.
        field.setShowSoftInputOnFocus(false);
        // The keyboard may have been closed by its own key: a tap on the field asks for it again.
        field.setOnClickListener(v -> mListener.onTextInput(field, true));
        field.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            commitRename();
            return true;
        });
        view.removeViewAt(2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(Math.round(10f * mDensity));
        view.addView(field, 2, params);
        mField = field;
        mListener.onTextInput(field, true);
        if (text != null) text.setVisibility(GONE);
    }

    /** The rename is done: the typed name is kept, and the card goes. */
    void commitRename() {
        EditText field = mField;
        int index = mEditing;
        if (field == null || index < 0 || index >= mRows.size()) return;
        String typed = field.getText().toString();
        mEditing = -1;
        mField = null;
        mListener.onTextInput(field, false);
        mListener.onRename(mRows.get(index).number, typed);
        mListener.onDismiss();
    }

    /** Back or a touch outside while a rename is open: a touch outside keeps the name, Back drops it. */
    void abandonRename() {
        EditText field = mField;
        mEditing = -1;
        mField = null;
        if (field != null) mListener.onTextInput(field, false);
    }

    private void dismissFromScrim() {
        if (mEditing >= 0) {
            commitRename();
            return;
        }
        mListener.onDismiss();
    }

    // ---- dress -------------------------------------------------------------------------------------

    /** The glass is cut for the card's size, never from inside the layout pass. */
    private void dress() {
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0 || getParent() == null) return;
        float radius = mStyle.cornerRadiusPx();
        Drawable glass = mStyle.glass(radius, mCard);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6));
            glass = plain;
        }
        mCard.setBackground(glass);
        mCard.setClipToOutline(true);
        mCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(mFollow);
        mCard.post(this::dress);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mRenameHold);
        getViewTreeObserver().removeOnGlobalLayoutListener(mFollow);
        super.onDetachedFromWindow();
    }

    /** The row lit now, for tests; -1 for none. */
    int lit() {
        return mLit;
    }

    /** The rows' views are the card's own; the card's bounds on screen, for tests. */
    @NonNull
    Rect cardBounds() {
        Rect out = new Rect();
        mCard.getGlobalVisibleRect(out);
        return out;
    }
}
