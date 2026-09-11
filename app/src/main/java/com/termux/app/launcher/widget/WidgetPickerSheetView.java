package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.Spring;

import java.util.Collections;
import java.util.List;

/**
 * Modal child sheet that never creates a window of its own.
 *
 * <p>It carries one text field, for searching the catalog, and it refuses to be the thing that
 * takes the keyboard: the field is not focusable until it is tapped, and when it is, the focus is
 * announced through {@link SearchFocusListener} so the activity can hand the system IME over the
 * way it does for a text input inside a widget. Closing the sheet gives that focus straight back.
 */
public final class WidgetPickerSheetView extends FrameLayout {
    /** Told when the search field takes the keyboard, and told again — with null — when it lets go. */
    public interface SearchFocusListener { void onSearchFocusChanged(@Nullable View editor); }

    private final View scrim;
    private final LinearLayout sheet;
    private final TextView title;
    private final EditText search;
    private final TextView notice;
    private final RecyclerView list;
    private final WidgetPickerAdapter adapter;
    private final Spring spring = new Spring(1f, 420f, 41f);
    private final int slop;
    private boolean reducedMotion;
    private boolean open;
    @Nullable private SearchFocusListener searchFocusListener;
    private boolean searchFocused;
    private boolean catalogEmpty;
    private boolean loading;
    private boolean animating;
    private long lastFrame;
    private float downX, downY;
    private boolean scrimCandidate;

    public WidgetPickerSheetView(@NonNull Context context,
                                 @NonNull WidgetPickerAdapter.Listener listener) {
        super(context);
        setClipChildren(true); setClipToPadding(true); setFocusable(false);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrim = new View(context); scrim.setBackgroundColor(0x66000000);
        scrim.setContentDescription("Close widget picker");
        scrim.setOnTouchListener(this::onScrimTouch);
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        sheet = new LinearLayout(context); sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setFocusable(false); sheet.setClickable(true);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xee202124);
        background.setCornerRadii(new float[] {24,24,24,24,0,0,0,0}); sheet.setBackground(background);
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16); header.setPadding(pad, dp(8), dp(8), dp(4));
        title = new TextView(context); title.setText("Add widget"); title.setTextColor(Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton close = new ImageButton(context); close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setBackgroundColor(Color.TRANSPARENT); close.setContentDescription("Close widget picker");
        close.setMinimumWidth(dp(48)); close.setMinimumHeight(dp(48)); close.setFocusable(false);
        close.setOnClickListener(view -> close()); header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sheet.addView(header);
        search = buildSearchField(context);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(44));
        searchParams.setMargins(pad, dp(4), pad, dp(4));
        sheet.addView(search, searchParams);
        notice = new TextView(context); notice.setPadding(pad, dp(4), pad, dp(8)); notice.setTextColor(Color.WHITE);
        notice.setVisibility(GONE); sheet.addView(notice);
        list = new RecyclerView(context); list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(true); list.setFocusable(false);
        adapter = new WidgetPickerAdapter(listener); list.setAdapter(adapter);
        sheet.addView(list, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        // Body-modal: the picker owns the pane's entire corrected body rectangle, including the
        // action strip beneath it. It never creates a focusable window or an InputConnection.
        LayoutParams sheetParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
        addView(sheet, sheetParams); setVisibility(GONE);
    }

    /**
     * The field is inert until it is touched: no focus, no keyboard, nothing taken from the
     * terminal by the picker merely being on screen. A tap makes it focusable and asks for focus,
     * and the focus change is what tells the host to hand over the system IME.
     */
    @NonNull private EditText buildSearchField(@NonNull Context context) {
        EditText field = new EditText(context);
        field.setTag("search");
        field.setHint(R.string.widget_picker_search_hint);
        field.setContentDescription(context.getString(R.string.widget_picker_search_hint));
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setTextColor(Color.WHITE); field.setHintTextColor(0x99FFFFFF);
        field.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x1AFFFFFF); background.setCornerRadius(dp(12));
        field.setBackground(background);
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
    public void setReducedMotion(boolean value) { reducedMotion = value; }
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
        spring.reset(1f); spring.target = 0f; applyProgress(1f); startSpring();
    }
    public void close() {
        if (!open) return; open = false; spring.value = Math.max(0f, spring.value);
        clearSearch();
        adapter.submit(Collections.emptyList());
        spring.target = 1f; spring.vel = 0f; startSpring();
    }
    public void closeImmediate() {
        open = false; animating = false; removeCallbacks(frame); spring.reset(1f);
        clearSearch();
        adapter.submit(Collections.emptyList());
        applyProgress(1f); setVisibility(GONE);
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
    private void startSpring() {
        if (reducedMotion) {
            spring.reset(spring.target); applyProgress(spring.value);
            if (!open) setVisibility(GONE); return;
        }
        if (animating) return; animating = true; lastFrame = 0; postOnAnimation(frame);
    }
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!animating) return;
            long now = System.nanoTime(); float dt = lastFrame == 0 ? Spring.MIN_DT
                : Spring.clampDelta((now - lastFrame) / 1_000_000_000f); lastFrame = now;
            boolean moving = spring.tick(false, dt); applyProgress(spring.value);
            if (moving) postOnAnimation(this); else {
                animating = false; if (!open) setVisibility(GONE);
            }
        }
    };
    private void applyProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        sheet.setTranslationY(p * Math.max(1, sheet.getHeight())); scrim.setAlpha(1f - p);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
