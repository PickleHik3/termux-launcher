package com.termux.app.launcher.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.material.M3;

import java.util.Collections;
import java.util.List;

/**
 * Modal child sheet that never creates a window of its own: a {@link BottomSheetBehavior} sheet in
 * a {@link CoordinatorLayout} that fills the widget pane, so the stock bottom-sheet drag, settle
 * and nested scroll apply without the sheet taking a window (and with it, the keyboard).
 *
 * <p>It carries one text field, for searching the catalog, and it refuses to be the thing that
 * takes the keyboard: the field is not focusable until it is tapped, and when it is, the focus is
 * announced through {@link SearchFocusListener} so the activity can hand the system IME over the
 * way it does for a text input inside a widget. Closing the sheet gives that focus straight back.
 */
public final class WidgetPickerSheetView extends CoordinatorLayout {
    /** Told when the search field takes the keyboard, and told again — with null — when it lets go. */
    public interface SearchFocusListener { void onSearchFocusChanged(@Nullable View editor); }

    private final View scrim;
    private final LinearLayout sheet;
    private final BottomSheetBehavior<LinearLayout> behavior;
    private final TextView title;
    private final EditText search;
    private final TextView notice;
    private final RecyclerView list;
    private final WidgetPickerAdapter adapter;
    private final int slop;
    /** A hint lasts as long as the pane's own notice does, then the sheet's standing notice returns. */
    private static final long HINT_MS = 3500L;
    private final Runnable expireHint = this::updateNotice;
    private boolean open;
    @Nullable private SearchFocusListener searchFocusListener;
    private boolean searchFocused;
    private boolean catalogEmpty;
    private boolean loading;
    private float downX, downY;
    private boolean scrimCandidate;
    @Nullable private java.lang.ref.WeakReference<View> previousFocus;

    public WidgetPickerSheetView(@NonNull Context context,
                                 @NonNull WidgetPickerAdapter.Listener listener) {
        super(context);
        setClipChildren(true); setClipToPadding(true); setFocusable(false);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrim = new View(context); scrim.setBackgroundColor(M3.scrim(context));
        scrim.setContentDescription(context.getString(R.string.widget_picker_close));
        scrim.setOnTouchListener(this::onScrimTouch);
        addView(scrim, new CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT));
        sheet = new LinearLayout(context); sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setFocusable(false); sheet.setClickable(true);
        ShapeAppearanceModel topCorners = M3.shape(context,
            com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge).toBuilder()
            .setBottomLeftCornerSize(0f).setBottomRightCornerSize(0f).build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(topCorners);
        background.initializeElevationOverlay(context);
        background.setFillColor(ColorStateList.valueOf(M3.surfaceContainerLow(context)));
        sheet.setBackground(background);
        sheet.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16); header.setPadding(pad, 0, dp(8), 0);
        title = new TextView(context); title.setText(R.string.widget_picker_title);
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleLarge);
        title.setTextColor(M3.onSurface(context));
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton close = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        close.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription(context.getString(R.string.widget_picker_close));
        close.setFocusable(true); close.setFocusableInTouchMode(false);
        close.setOnClickListener(view -> close());
        header.addView(close, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sheet.addView(header);
        TextInputLayout searchLayout = new TextInputLayout(context, null,
            com.google.android.material.R.attr.textInputFilledStyle);
        search = buildSearchField(searchLayout);
        searchLayout.addView(search, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(pad, dp(4), pad, dp(4));
        sheet.addView(searchLayout, searchParams);
        notice = new TextView(context); notice.setTag("notice");
        notice.setPadding(pad, dp(4), pad, dp(8));
        M3.textAppearance(notice, com.google.android.material.R.attr.textAppearanceBodyMedium);
        notice.setTextColor(M3.onSurfaceVariant(context));
        notice.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.setVisibility(GONE); sheet.addView(notice);
        list = new RecyclerView(context); list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(true); list.setFocusable(false);
        list.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        adapter = new WidgetPickerAdapter(listener); list.setAdapter(adapter);
        sheet.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        // Body-modal: the picker owns the pane's entire corrected body rectangle, including the
        // action strip beneath it. It never creates a focusable window or an InputConnection.
        CoordinatorLayout.LayoutParams sheetParams = new CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT);
        behavior = new BottomSheetBehavior<>();
        behavior.setHideable(true); behavior.setSkipCollapsed(true);
        behavior.setFitToContents(false); behavior.setDraggable(true);
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) finishClosing();
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                scrim.setAlpha(Math.max(0f, Math.min(1f, 1f + slideOffset)));
            }
        });
        sheetParams.setBehavior(behavior);
        addView(sheet, sheetParams);
        scrim.setAlpha(0f); setVisibility(GONE);
    }

    /**
     * The field is inert until it is touched: no focus, no keyboard, nothing taken from the
     * terminal by the picker merely being on screen. A tap makes it focusable and asks for focus,
     * and the focus change is what tells the host to hand over the system IME.
     */
    @NonNull private EditText buildSearchField(@NonNull TextInputLayout layout) {
        Context context = layout.getContext();
        EditText field = new TextInputEditText(context);
        field.setTag("search");
        field.setHint(R.string.widget_picker_search_hint);
        field.setContentDescription(context.getString(R.string.widget_picker_search_hint));
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        // Reachable by Tab/D-pad, never by touch alone: a tap still goes through the click below.
        field.setFocusable(true); field.setFocusableInTouchMode(false);
        field.setOnClickListener(view -> {
            field.setFocusableInTouchMode(true); field.requestFocus(); activateSearch();
        });
        // Landing on the field from the keyboard is not asking for the soft keyboard; Enter is.
        field.setOnKeyListener((view, keyCode, event) -> {
            if (searchFocused || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            activateSearch(); return true;
        });
        field.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) releaseSearchFocus();
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                adapter.setQuery(value.toString());
                updateNotice();
            }
        });
        return field;
    }

    /** The user asked for the field (tap or Enter): hand the system IME over through the host. */
    private void activateSearch() {
        if (searchFocused) return;
        searchFocused = true;
        if (searchFocusListener != null) searchFocusListener.onSearchFocusChanged(search);
    }

    /** Hands the keyboard back, whether the field lost focus on its own or the sheet closed. */
    private void releaseSearchFocus() {
        search.setFocusable(true); search.setFocusableInTouchMode(false);
        if (!searchFocused) return;
        searchFocused = false;
        if (searchFocusListener != null) searchFocusListener.onSearchFocusChanged(null);
    }

    public void setSearchFocusListener(@Nullable SearchFocusListener value) {
        searchFocusListener = value;
    }
    /** Kept for callers; the stock sheet's settle follows the system animation scale. */
    public void setReducedMotion(boolean value) { }
    public boolean isOpen() { return open; }
    @NonNull public WidgetPickerAdapter adapter() { return adapter; }
    @NonNull public RecyclerView list() { return list; }
    @NonNull public EditText searchField() { return search; }

    public void showLoading() {
        loading = true; catalogEmpty = false; title.setText(R.string.widget_picker_title);
        showNotice(getContext().getString(R.string.widget_picker_loading));
    }

    /** The app rows, before the widgets inside them are known; the loading notice stays up. */
    public void showSections(@NonNull List<WidgetAppGroup> sections) {
        if (!loading || sections.isEmpty()) return;
        adapter.submit(sections);
    }

    public void showCatalog(@NonNull List<WidgetAppGroup> groups) {
        loading = false; catalogEmpty = groups.isEmpty();
        adapter.submit(groups);
        updateNotice();
    }

    private void updateNotice() {
        notice.removeCallbacks(expireHint);
        if (loading) return;
        if (catalogEmpty) { title.setText(R.string.widget_picker_title); showNotice(getContext().getString(R.string.widget_picker_empty)); return; }
        if (adapter.searchFoundNothing()) {
            title.setText(R.string.widget_picker_title);
            showNotice(getContext().getString(R.string.widget_picker_no_matches));
            return;
        }
        if (!adapter.anyProviderFits()) {
            title.setText(R.string.widget_picker_grid_full_title);
            showNotice(getContext().getString(R.string.widget_picker_grid_full)); return;
        }
        title.setText(R.string.widget_picker_title); notice.setVisibility(GONE);
    }
    public void showNoSpace(int columns, int rows, WidgetGridDefinition grid) {
        showNotice(getContext().getString(R.string.widget_picker_no_space, columns, rows,
            grid.columns, grid.rows));
    }
    /**
     * The widget has no room on the page on screen, though it fits the grid: say how to put it on
     * another page. The sheet stays up, and the hint goes after a while.
     */
    public void showNoRoomOnPage() {
        showNotice(getContext().getString(R.string.widget_picker_no_room_on_page));
        notice.postDelayed(expireHint, HINT_MS);
    }
    public void showNotice(@NonNull String message) {
        notice.removeCallbacks(expireHint);
        notice.setText(message); notice.setContentDescription(message); notice.setVisibility(VISIBLE);
    }

    /**
     * While the picker is open it is the whole focus world: Tab and the D-pad wrap inside it rather
     * than escaping to the terminal or controls behind the overlay.
     */
    @Override public View focusSearch(View focused, int direction) {
        if (!open) return super.focusSearch(focused, direction);
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (next == null && (direction == View.FOCUS_FORWARD || direction == View.FOCUS_BACKWARD)) {
            next = FocusFinder.getInstance().findNextFocus(this, null, direction);
        }
        return next;
    }

    /** Opening takes no focus; it only remembers who had it so closing can hand it back. */
    public void open() {
        if (open) return;
        View current = getRootView().findFocus();
        previousFocus = current != null && !isInside(current)
            ? new java.lang.ref.WeakReference<>(current) : null;
        open = true; setVisibility(VISIBLE); bringToFront();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
    public void close() {
        if (!open) return; open = false;
        boolean hadFocus = findFocus() != null;
        clearSearch();
        restoreFocus(hadFocus);
        adapter.submit(Collections.emptyList());
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
    public void closeImmediate() {
        boolean hadFocus = open && findFocus() != null;
        open = false;
        clearSearch();
        restoreFocus(hadFocus);
        adapter.submit(Collections.emptyList());
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        scrim.setAlpha(0f); setVisibility(GONE);
    }

    /** The sheet has settled hidden, whether by {@link #close()} or by being dragged down. */
    private void finishClosing() {
        if (open) {
            open = false;
            boolean hadFocus = findFocus() != null;
            clearSearch();
            restoreFocus(hadFocus);
            adapter.submit(Collections.emptyList());
        }
        scrim.setAlpha(0f); setVisibility(GONE);
    }

    private boolean isInside(@NonNull View view) {
        for (android.view.ViewParent p = view.getParent(); p != null; p = p.getParent()) {
            if (p == this) return true;
        }
        return false;
    }

    /**
     * Gives keyboard focus back to what had it before the picker opened, but only when the picker
     * itself held it at close: a picker closed from a touch never takes focus anywhere.
     */
    private void restoreFocus(boolean pickerHadFocus) {
        View target = previousFocus != null ? previousFocus.get() : null;
        previousFocus = null;
        if (!pickerHadFocus) return;
        if (target != null && target.isAttachedToWindow() && target.isShown()
            && target.requestFocus()) return;
        clearFocus();
    }

    /** A closing picker keeps nothing: not the query, and not the keyboard it borrowed. */
    private void clearSearch() {
        notice.removeCallbacks(expireHint);
        loading = false; catalogEmpty = false;
        if (search.getText().length() > 0) search.setText("");
        if (search.hasFocus()) search.clearFocus();
        releaseSearchFocus();
    }

    private boolean onScrimTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); scrimCandidate = true; return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) scrimCandidate = false;
                return true;
            case MotionEvent.ACTION_UP:
                if (scrimCandidate) close(); scrimCandidate = false; return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                scrimCandidate = false; return true;
            default: return true;
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
