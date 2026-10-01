package com.termux.app.launcher.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
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
    private boolean open;
    @Nullable private SearchFocusListener searchFocusListener;
    private boolean searchFocused;
    private boolean catalogEmpty;
    private boolean loading;
    private float downX, downY;
    private boolean scrimCandidate;

    public WidgetPickerSheetView(@NonNull Context context,
                                 @NonNull WidgetPickerAdapter.Listener listener) {
        super(context);
        setClipChildren(true); setClipToPadding(true); setFocusable(false);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrim = new View(context); scrim.setBackgroundColor(M3.scrim(context));
        scrim.setContentDescription("Close widget picker");
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
        title = new TextView(context); title.setText("Add widget");
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleLarge);
        title.setTextColor(M3.onSurface(context));
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton close = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        close.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("Close widget picker"); close.setFocusable(false);
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
        notice = new TextView(context); notice.setPadding(pad, dp(4), pad, dp(8));
        M3.textAppearance(notice, com.google.android.material.R.attr.textAppearanceBodyMedium);
        notice.setTextColor(M3.onSurfaceVariant(context));
        notice.setVisibility(GONE); sheet.addView(notice);
        list = new RecyclerView(context); list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(true); list.setFocusable(false);
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
        field.setFocusable(false); field.setFocusableInTouchMode(false);
        field.setOnClickListener(view -> {
            field.setFocusableInTouchMode(true); field.setFocusable(true); field.requestFocus();
        });
        field.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                searchFocused = true;
                if (searchFocusListener != null) searchFocusListener.onSearchFocusChanged(view);
            } else releaseSearchFocus();
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

    /** Hands the keyboard back, whether the field lost focus on its own or the sheet closed. */
    private void releaseSearchFocus() {
        search.setFocusable(false); search.setFocusableInTouchMode(false);
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
        loading = true; catalogEmpty = false; title.setText("Add widget");
        showNotice("Loading widgets…");
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
        if (loading) return;
        if (catalogEmpty) { title.setText("Add widget"); showNotice("No widgets available"); return; }
        if (adapter.searchFoundNothing()) {
            title.setText("Add widget");
            showNotice(getContext().getString(R.string.widget_picker_no_matches));
            return;
        }
        if (!adapter.anyProviderFits()) {
            title.setText("Grid is full"); showNotice("No widget fits the grid."); return;
        }
        title.setText("Add widget"); notice.setVisibility(GONE);
    }
    public void showNoSpace(int columns, int rows, WidgetGridDefinition grid) {
        showNotice(getContext().getString(R.string.widget_picker_no_space, columns, rows,
            grid.columns, grid.rows));
    }
    public void showNotice(@NonNull String message) {
        notice.setText(message); notice.setContentDescription(message); notice.setVisibility(VISIBLE);
    }

    public void open() {
        if (open) return; open = true; setVisibility(VISIBLE); bringToFront();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
    public void close() {
        if (!open) return; open = false;
        clearSearch();
        adapter.submit(Collections.emptyList());
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
    public void closeImmediate() {
        open = false;
        clearSearch();
        adapter.submit(Collections.emptyList());
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        scrim.setAlpha(0f); setVisibility(GONE);
    }

    /** The sheet has settled hidden, whether by {@link #close()} or by being dragged down. */
    private void finishClosing() {
        if (open) {
            open = false;
            clearSearch();
            adapter.submit(Collections.emptyList());
        }
        scrim.setAlpha(0f); setVisibility(GONE);
    }

    /** A closing picker keeps nothing: not the query, and not the keyboard it borrowed. */
    private void clearSearch() {
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
