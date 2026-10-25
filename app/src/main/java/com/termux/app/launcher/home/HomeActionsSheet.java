package com.termux.app.launcher.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.termux.R;
import com.termux.app.launcher.home.HomeActionsSheetPolicy.Group;
import com.termux.app.launcher.home.HomeActionsSheetPolicy.Item;
import com.termux.app.material.M3;

import java.util.List;
import java.util.function.Function;

/**
 * Home's long-press sheet: a Material 3 modal bottom sheet that slides up from the bottom of the
 * screen over a dim, with the launcher's name, then the Home page's actions and the launcher's in
 * two rounded groups of two-column rows ({@link HomeActionsSheetPolicy} says which rows).
 *
 * <p>Like the widget picker it never takes a window of its own: it is a {@link BottomSheetBehavior}
 * card in a {@link CoordinatorLayout} the activity lays over everything, so the stock drag, settle
 * and swipe-down-to-dismiss apply, and Back reaches it through the activity's overlay registry
 * rather than through a dialog. It wears the app's Material surface, never glass, so it follows the
 * app's light or dark theme. A tap on the dim, a swipe down or Back puts it away; a row puts it away
 * and tells the {@link Listener} which action was chosen.
 *
 * <p>The card runs down under the navigation bar and pads its rows above it, as the Appearance
 * editor's sheet and the drawer plane do. The activity hands it the window's insets ({@link
 * #applyInsets}): the shell's root fits the system windows and consumes them, so a dispatch never
 * reaches an overlay standing after it in the content frame. The under-pill strip a Docked dock
 * continues into is the decor's topmost child and would paint over the card's foot, so the sheet
 * reports how much of the band under the bar it covers ({@link Listener#onNavBandCovered}) and the
 * activity fades the strip out by as much.
 */
public final class HomeActionsSheet extends CoordinatorLayout {

    /** Told which row was tapped; the sheet is already on its way down. */
    public interface Listener {
        void onHomeAction(@NonNull Item item);

        /**
         * How much of the band under the navigation bar the card now covers, 0 to 1
         * ({@link HomeActionsSheetPolicy#navBandCover}); back to 0 once the sheet is put away.
         */
        default void onNavBandCovered(float covered) { }

        /** The Lock layout switch was flipped; the sheet stays up and redraws its rows. */
        default void onLayoutLockChanged(boolean locked) { }
    }

    /** The card's top corners: the M3 extra-large corner. */
    private static final float CARD_CORNER_DP = 28f;
    /** The widest an M3 bottom sheet stands; wider screens centre it. */
    private static final float CARD_MAX_WIDTH_DP = 640f;

    @NonNull private final Listener listener;
    @NonNull private final View scrim;
    @NonNull private final LinearLayout card;
    @NonNull private final TextView lockedNote;
    @NonNull private final View pageGroup;
    @NonNull private final LinearLayout pageRows;
    @NonNull private final View launcherGroup;
    @NonNull private final LinearLayout launcherRows;
    @NonNull private final BottomSheetBehavior<LinearLayout> behavior;
    private final int cardPadLeft, cardPadRight, cardPadBottom;
    /** The navigation bar's height under the card, from the last {@link #applyInsets}. */
    private int navInsetPx;
    /** The last cover told to the listener, so a settle that changes nothing tells it nothing. */
    private float reportedCover;
    private boolean open;
    /** The rows for a layout that is locked (true) or not, so the switch can redraw in place. */
    @NonNull private Function<Boolean, List<Item>> source = locked -> java.util.Collections.emptyList();
    private boolean locked;

    public HomeActionsSheet(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        LayoutInflater.from(context).inflate(R.layout.home_actions_sheet, this, true);
        scrim = findViewById(R.id.home_actions_scrim);
        card = findViewById(R.id.home_actions_card);
        lockedNote = findViewById(R.id.home_actions_locked_note);
        pageGroup = findViewById(R.id.home_actions_group_page);
        pageRows = findViewById(R.id.home_actions_group_page_rows);
        launcherGroup = findViewById(R.id.home_actions_group_launcher);
        launcherRows = findViewById(R.id.home_actions_group_launcher_rows);
        cardPadLeft = card.getPaddingLeft();
        cardPadRight = card.getPaddingRight();
        cardPadBottom = card.getPaddingBottom();

        scrim.setBackgroundColor(M3.scrim(context));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(view -> close());

        float corner = dp(CARD_CORNER_DP);
        ShapeAppearanceModel topCorners = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, corner)
            .setTopRightCorner(CornerFamily.ROUNDED, corner)
            .build();
        MaterialShapeDrawable surface = new MaterialShapeDrawable(topCorners);
        surface.initializeElevationOverlay(context);
        surface.setFillColor(ColorStateList.valueOf(M3.surfaceContainerLow(context)));
        card.setBackground(surface);
        // The rows' ripples stay inside each group's rounded corners.
        pageGroup.setClipToOutline(true);
        launcherGroup.setClipToOutline(true);
        TextView title = findViewById(R.id.home_actions_title);
        ViewCompat.setAccessibilityHeading(title, true);
        ViewCompat.setAccessibilityPaneTitle(card, title.getText());

        behavior = BottomSheetBehavior.from(card);
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setFitToContents(true);
        behavior.setDraggable(true);
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) finishClosing();
                else if (newState == BottomSheetBehavior.STATE_EXPANDED) reportCover();
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                scrim.setAlpha(Math.max(0f, Math.min(1f, 1f + slideOffset)));
                reportCover();
            }
        });
        setVisibility(GONE);
    }

    /**
     * The window's insets: the card reaches the screen's bottom edge and its rows stay clear of
     * the system bars. Called by the activity on every inset dispatch and read off the window
     * again on {@link #open}.
     */
    public void applyInsets(@NonNull WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
            | WindowInsetsCompat.Type.displayCutout());
        navInsetPx = Math.max(0, bars.bottom);
        int left = cardPadLeft + bars.left;
        int right = cardPadRight + bars.right;
        int bottom = cardPadBottom + navInsetPx;
        if (card.getPaddingLeft() != left || card.getPaddingRight() != right
            || card.getPaddingBottom() != bottom) {
            card.setPadding(left, card.getPaddingTop(), right, bottom);
        }
        if (open) reportCover();
    }

    /** Tells the listener how much of the band under the navigation bar the card covers. */
    private void reportCover() {
        float cover = HomeActionsSheetPolicy.navBandCover(card.getTop(), getHeight(), navInsetPx);
        if (cover == reportedCover) return;
        reportedCover = cover;
        listener.onNavBandCovered(cover);
    }

    /** Whether the sheet is up, or on its way up. */
    public boolean isOpen() {
        return open;
    }

    /** Fill the groups with {@code items} and slide the sheet up. */
    public void open(@NonNull List<Item> items) {
        open(isLocked -> items, false);
    }

    /**
     * Fill the groups from {@code rowsFor} (the rows for a locked or an unlocked layout) and slide
     * the sheet up. Flipping the Lock layout switch asks it again, with the card still open.
     */
    public void open(@NonNull Function<Boolean, List<Item>> rowsFor, boolean layoutLocked) {
        source = rowsFor;
        locked = layoutLocked;
        refill();
        boolean wasOpen = open;
        open = true;
        setVisibility(VISIBLE);
        bringToFront();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) applyInsets(insets);
        if (wasOpen) return;
        // Expanding before the card has been laid out with these rows would jump rather than
        // slide, so the settle starts on the first frame after layout.
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                if (open) behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                return true;
            }
        });
    }

    private void refill() {
        List<Item> items = source.apply(locked);
        fill(pageGroup, pageRows, HomeActionsSheetPolicy.inGroup(items, Group.PAGE));
        fill(launcherGroup, launcherRows, HomeActionsSheetPolicy.inGroup(items, Group.LAUNCHER));
        boolean note = locked && items.contains(Item.LOCK_LAYOUT);
        if (note != (lockedNote.getVisibility() == VISIBLE)) lockedNote.setVisibility(note ? VISIBLE : GONE);
    }

    private void toggleLock(boolean nowLocked) {
        if (!open || nowLocked == locked) return;
        locked = nowLocked;
        refill();
        listener.onLayoutLockChanged(nowLocked);
    }

    /** Slide the sheet away. */
    public void close() {
        if (!open) return;
        open = false;
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    /** Drop the sheet with no slide: the activity is leaving, turning, or going home. */
    public void closeImmediately() {
        open = false;
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        finishClosing();
    }

    /** The sheet has settled hidden, by {@link #close()} or by being dragged down. */
    private void finishClosing() {
        open = false;
        scrim.setAlpha(0f);
        setVisibility(GONE);
        if (reportedCover != 0f) {
            reportedCover = 0f;
            listener.onNavBandCovered(0f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Wider than an M3 sheet may stand, the card keeps that width and its gravity centres it.
        int maxWidth = Math.round(dp(CARD_MAX_WIDTH_DP));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        ViewGroup.LayoutParams params = card.getLayoutParams();
        params.width = width > maxWidth ? maxWidth : ViewGroup.LayoutParams.MATCH_PARENT;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** One group: its rows two to a line, the last line half empty for an odd count. */
    private void fill(@NonNull View group, @NonNull LinearLayout rows, @NonNull List<Item> items) {
        rows.removeAllViews();
        group.setVisibility(items.isEmpty() ? GONE : VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int lines = HomeActionsSheetPolicy.lineCount(items.size());
        for (int line = 0; line < lines; line++) {
            LinearLayout lineView = new LinearLayout(getContext());
            lineView.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = line * 2 + column;
                if (index < items.size()) {
                    lineView.addView(row(inflater, lineView, items.get(index)));
                } else {
                    lineView.addView(new View(getContext()),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                }
            }
            rows.addView(lineView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @NonNull
    private View row(@NonNull LayoutInflater inflater, @NonNull ViewGroup line,
                     @NonNull Item item) {
        View row = inflater.inflate(R.layout.home_actions_sheet_row, line, false);
        Context context = getContext();
        ImageView icon = row.findViewById(R.id.home_actions_row_icon);
        icon.setImageResource(iconFor(item));
        // Terminal is the sheet's primary row, so its glyph wears the accent.
        icon.setImageTintList(ColorStateList.valueOf(item == Item.TERMINAL
            ? M3.primary(context) : M3.onSurfaceVariant(context)));
        TextView label = row.findViewById(R.id.home_actions_row_label);
        label.setText(labelFor(item));
        row.setContentDescription(label.getText());
        row.setTag(item);
        if (item == Item.LOCK_LAYOUT) {
            // A switch row: the whole row flips it and the card stays up.
            MaterialSwitch toggle = row.findViewById(R.id.home_actions_row_switch);
            label.setMaxLines(1);
            toggle.setVisibility(VISIBLE);
            toggle.setChecked(locked);
            toggle.setClickable(false);
            toggle.setFocusable(false);
            row.setOnClickListener(view -> toggleLock(!locked));
            // The switch itself is not focusable, so the row says it is one, and whether it is on.
            ViewCompat.setAccessibilityDelegate(row, new androidx.core.view.AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                        @NonNull androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Switch");
                    info.setCheckable(true);
                    info.setChecked(locked);
                }
            });
            return row;
        }
        row.setOnClickListener(view -> select(item));
        return row;
    }

    private void select(@NonNull Item item) {
        if (!open) return;
        close();
        listener.onHomeAction(item);
    }

    /** The row for {@code item} while the sheet is up, or null. */
    @VisibleForTesting
    View rowFor(@NonNull Item item) {
        return findViewWithTag(item);
    }

    @StringRes
    static int labelFor(@NonNull Item item) {
        switch (item) {
            case TERMINAL: return R.string.home_actions_terminal;
            case ADD_WIDGET: return R.string.home_actions_add_widget;
            case ADD_APP: return R.string.home_actions_add_app;
            case ADD_PAGE: return R.string.home_actions_add_page;
            case EDIT_WIDGETS: return R.string.home_actions_edit_widgets;
            case EDIT_PAGES: return R.string.home_actions_edit_pages;
            case REMOVE_PAGE: return R.string.home_actions_remove_page;
            case LOCK_LAYOUT: return R.string.home_actions_lock_layout;
            case WALLPAPER: return R.string.home_actions_wallpaper;
            case APPEARANCE: return R.string.home_actions_appearance;
            case LAUNCHER_SETTINGS: return R.string.home_actions_launcher_settings;
            case SET_DEFAULT_HOME: return R.string.home_actions_set_default_home;
            case HELP: default: return R.string.home_actions_help;
        }
    }

    /** The glyphs the drawer, the settings and the Layout editor already wear for these. */
    @DrawableRes
    static int iconFor(@NonNull Item item) {
        switch (item) {
            case TERMINAL: return R.drawable.ic_symbol_terminal;
            case ADD_WIDGET: return R.drawable.ic_symbol_grid_view;
            case ADD_APP: return R.drawable.ic_symbol_apps;
            case ADD_PAGE: return R.drawable.ic_symbol_add;
            case EDIT_WIDGETS: return R.drawable.ic_symbol_edit;
            case EDIT_PAGES: return R.drawable.ic_layout_home;
            case REMOVE_PAGE: return R.drawable.ic_symbol_delete;
            case LOCK_LAYOUT: return R.drawable.ic_symbol_lock;
            case WALLPAPER: return R.drawable.ic_symbol_wallpaper;
            case APPEARANCE: return R.drawable.ic_symbol_palette;
            case LAUNCHER_SETTINGS: return R.drawable.ic_settings;
            case SET_DEFAULT_HOME: return R.drawable.ic_symbol_check_circle;
            case HELP: default: return R.drawable.ic_symbol_help;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
