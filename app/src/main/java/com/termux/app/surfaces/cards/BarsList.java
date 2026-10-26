package com.termux.app.surfaces.cards;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.place.Element;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.GlyphNames;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SectionHeader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Dock card's Bars list (editor-redesign §2 Dock, §8): one rounded list of the dock's bars,
 * each row the extra-keys editor's handle row (the handle, the row height and the "Hold the
 * handle to reorder" hint) with the bar's glyph and name, a sliders glyph that opens the bar's
 * settings in place, and an eye where the extra-keys editor has its ⋮. A hidden bar greys out.
 * A row moves by its handle, or by a hold anywhere on it; the order itself is
 * {@link BarOrder}'s.
 */
public final class BarsList {

    /** Where the list reads its bars and writes what is done to them. */
    public interface Host {
        /** Every bar, top to bottom as the list reads. */
        @NonNull List<Element> order();
        /** Whether the page being edited wears {@code bar}; its row stands only if it does. */
        boolean offers(@NonNull Element bar);
        boolean shown(@NonNull Element bar);
        void setShown(@NonNull Element bar, boolean shown);
        /** The rows were dragged into {@code order}. */
        void reorder(@NonNull List<Element> order);
        /** {@code bar}'s settings, for a list row {@code widthPx} wide. */
        @NonNull View settings(@NonNull Element bar, int widthPx);
        /** {@code bar}'s open settings read again. */
        void restateSettings(@NonNull Element bar);
        /** A sliders glyph opened {@code bar}'s settings, or closed them (null). */
        void expanded(@Nullable Element bar);
    }

    /** A row's header: the extra-keys editor's 48dp handle row. */
    static final int ROW_DP = 48;
    static final int HANDLE_PAD_DP = 12;
    static final int CORNER_DP = 16;
    /** A hidden bar's glyph and name. */
    static final float HIDDEN_ALPHA = 0.38f;
    static final int DRAG_ELEVATION_DP = 6;
    static final long SHIFT_MS = 120L;

    @NonNull public final LinearLayout root;
    @NonNull private final LinearLayout mList;
    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    private final int mRowWidthPx;
    @NonNull private final Map<Element, Item> mItems = new EnumMap<>(Element.class);
    @Nullable private Element mExpanded;

    // The drag in progress.
    @Nullable private Item mDragged;
    @NonNull private List<Element> mDragVisible = new ArrayList<>();
    @NonNull private float[] mDragCentres = new float[0];
    private int mDragFrom;
    private int mDragTarget;
    private float mDragDownY;
    private float mLastRawY;

    /** One bar's row: its header and the panel its settings open in. */
    private static final class Item {
        @NonNull final Element bar;
        @NonNull final LinearLayout container;
        @NonNull final LinearLayout header;
        @NonNull final ImageView glyph;
        @NonNull final TextView title;
        @NonNull final MaterialButton sliders;
        @NonNull final MaterialButton eye;
        @NonNull final FrameLayout panel;
        int eyeIcon;

        Item(@NonNull Element bar, @NonNull LinearLayout container, @NonNull LinearLayout header,
             @NonNull ImageView glyph, @NonNull TextView title, @NonNull MaterialButton sliders,
             @NonNull MaterialButton eye, @NonNull FrameLayout panel) {
            this.bar = bar;
            this.container = container;
            this.header = header;
            this.glyph = glyph;
            this.title = title;
            this.sliders = sliders;
            this.eye = eye;
            this.panel = panel;
        }
    }

    private BarsList(@NonNull Context context, @NonNull Host host, int widthPx) {
        mContext = context;
        mHost = host;
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        mRowWidthPx = Math.max(1, widthPx - 2 * edge);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, RowKit.dp(context, 8));
        SectionHeader header = SectionHeader.build(context,
            context.getString(R.string.editor_dock_row_bars),
            context.getString(R.string.editor_dock_bars_hint), true);
        root.addView(header.root);
        mList = new LinearLayout(context);
        mList.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(RowKit.color(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh));
        background.setCornerRadius(RowKit.dp(context, CORNER_DP));
        mList.setBackground(background);
        mList.setClipChildren(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(edge);
        params.setMarginEnd(edge);
        params.topMargin = RowKit.dp(context, 4);
        root.addView(mList, params);
    }

    /** The list for a card {@code widthPx} wide, {@code expanded}'s settings open. */
    @NonNull
    public static BarsList build(@NonNull Context context, @NonNull Host host, int widthPx,
                                 @Nullable Element expanded) {
        BarsList list = new BarsList(context, host, widthPx);
        for (Element bar : BarOrder.DEFAULT)
            list.mItems.put(bar, list.item(bar));
        list.mExpanded = expanded;
        list.restate();
        return list;
    }

    /** The glyph a bar's row shows. */
    @DrawableRes
    static int glyphOf(@NonNull Element bar) {
        switch (bar) {
            case AZ: return R.drawable.ic_ms_sort_by_alpha;
            case EXTRA_KEYS: return R.drawable.ic_ms_keyboard_command_key;
            case APPS:
            default: return R.drawable.ic_symbol_apps;
        }
    }

    /** A bar's name in the list. */
    @StringRes
    public static int nameOf(@NonNull Element bar) {
        switch (bar) {
            case AZ: return R.string.editor_dock_bar_az;
            case EXTRA_KEYS: return R.string.editor_dock_bar_extra_keys;
            case APPS:
            default: return R.string.editor_dock_bar_apps;
        }
    }

    /** The bar whose settings are open, or null. */
    @Nullable
    public Element expanded() {
        return mExpanded;
    }

    /** Opens {@code bar}'s settings (closing any other), or closes them all for null. */
    public void expand(@Nullable Element bar) {
        if (mExpanded == bar)
            return;
        mExpanded = bar;
        restate();
    }

    /** The rows read again: their order, which stand, which are hidden, whose settings are open. */
    public void restate() {
        if (mDragged != null)
            return;
        List<Element> order = BarOrder.normalized(mHost.order());
        boolean inOrder = mList.getChildCount() == order.size();
        for (int i = 0; inOrder && i < order.size(); i++)
            inOrder = mList.getChildAt(i) == itemOf(order.get(i)).container;
        if (!inOrder) {
            mList.removeAllViews();
            for (Element bar : order)
                mList.addView(itemOf(bar).container);
        }
        for (Element bar : order)
            restateItem(itemOf(bar));
    }

    @NonNull
    private Item itemOf(@NonNull Element bar) {
        Item item = mItems.get(bar);
        if (item == null) {
            item = item(bar);
            mItems.put(bar, item);
        }
        return item;
    }

    private void restateItem(@NonNull Item item) {
        Element bar = item.bar;
        setVisible(item.container, mHost.offers(bar));
        boolean shown = mHost.shown(bar);
        float alpha = shown ? 1f : HIDDEN_ALPHA;
        if (item.glyph.getAlpha() != alpha) {
            item.glyph.setAlpha(alpha);
            item.title.setAlpha(alpha);
        }
        int icon = shown ? R.drawable.ic_ms_visibility : R.drawable.ic_ms_visibility_off;
        if (item.eyeIcon != icon) {
            item.eyeIcon = icon;
            item.eye.setIconResource(icon);
            String name = mContext.getString(nameOf(bar));
            CharSequence action = mContext.getString(shown ? R.string.editor_dock_bar_hide
                : R.string.editor_dock_bar_show, name);
            item.eye.setContentDescription(action);
            GlyphNames.name(item.eye, action);
        }
        boolean open = bar == mExpanded;
        if (item.sliders.isChecked() != open)
            item.sliders.setChecked(open);
        if (open && item.panel.getChildCount() == 0) {
            View settings = mHost.settings(bar, mRowWidthPx);
            if (settings.getParent() instanceof ViewGroup)
                ((ViewGroup) settings.getParent()).removeView(settings);
            item.panel.addView(settings, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (!open && item.panel.getChildCount() > 0) {
            item.panel.removeAllViews();
        }
        setVisible(item.panel, open);
        if (open)
            mHost.restateSettings(bar);
    }

    // ---------------------------------------------------------------------------------- a row

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private Item item(@NonNull Element bar) {
        Context context = mContext;
        String name = context.getString(nameOf(bar));
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(RowKit.dp(context, ROW_DP));
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);

        ImageView handle = new ImageView(context);
        handle.setImageResource(EditorGlyphs.BARS_REORDER);
        handle.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        int pad = RowKit.dp(context, HANDLE_PAD_DP);
        handle.setPadding(pad, pad, pad, pad);
        handle.setContentDescription(context.getString(R.string.editor_dock_bar_handle, name));
        header.addView(handle, new LinearLayout.LayoutParams(touch, touch));

        ImageView glyph = RowKit.glyph(context, glyphOf(bar), name);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            RowKit.dp(context, RowKit.GLYPH_DP), RowKit.dp(context, RowKit.GLYPH_DP));
        glyphParams.setMarginEnd(RowKit.dp(context, 12));
        header.addView(glyph, glyphParams);

        TextView title = RowKit.title(context, name);
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton sliders = iconButton(context, EditorGlyphs.BAR_SETTINGS,
            context.getString(R.string.editor_dock_bar_settings, name));
        sliders.setCheckable(true);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sliders.setBackgroundTintList(new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorSecondaryContainer), 0}));
        sliders.setIconTint(new ColorStateList(states, new int[] {
            RowKit.color(context, androidx.appcompat.R.attr.colorPrimary),
            RowKit.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant)}));
        header.addView(sliders, new LinearLayout.LayoutParams(touch, touch));

        MaterialButton eye = iconButton(context, R.drawable.ic_ms_visibility,
            context.getString(R.string.editor_dock_bar_hide, name));
        header.addView(eye, new LinearLayout.LayoutParams(touch, touch));
        container.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout panel = new FrameLayout(context);
        panel.setVisibility(View.GONE);
        container.addView(panel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Item item = new Item(bar, container, header, glyph, title, sliders, eye, panel);
        item.eyeIcon = R.drawable.ic_ms_visibility;

        // The sliders glyph, or a tap on the row, opens the bar's settings in place.
        View.OnClickListener toggle = v -> {
            Element next = BarOrder.toggled(mExpanded, bar);
            mExpanded = next;
            mHost.expanded(next);
            restate();
        };
        sliders.setOnClickListener(toggle);
        header.setOnClickListener(toggle);
        eye.setOnClickListener(v -> mHost.setShown(bar, !mHost.shown(bar)));

        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startDrag(item, event.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    moveDrag(event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    endDrag(true);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    endDrag(false);
                    return true;
                default:
                    return true;
            }
        });
        // A hold anywhere on the row lifts it too; the finger then drags it as the handle does.
        header.setOnLongClickListener(v -> {
            startDrag(item, mLastRawY);
            header.setPressed(false);
            return true;
        });
        header.setOnTouchListener((v, event) -> {
            mLastRawY = event.getRawY();
            if (mDragged != item)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    moveDrag(event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    endDrag(true);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    endDrag(false);
                    return true;
                default:
                    return true;
            }
        });
        ViewCompat.addAccessibilityAction(header,
            context.getString(R.string.editor_dock_bar_move_up), (v, args) -> step(bar, -1));
        ViewCompat.addAccessibilityAction(header,
            context.getString(R.string.editor_dock_bar_move_down), (v, args) -> step(bar, 1));
        return item;
    }

    /** Moves {@code bar} one shown row up or down: the drag's spoken twin. */
    private boolean step(@NonNull Element bar, int delta) {
        List<Element> visible = visibleOrder();
        int from = visible.indexOf(bar);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= visible.size())
            return false;
        mHost.reorder(BarOrder.mergeVisible(BarOrder.normalized(mHost.order()),
            BarOrder.moved(visible, from, to)));
        restate();
        return true;
    }

    @NonNull
    private List<Element> visibleOrder() {
        List<Element> out = new ArrayList<>();
        for (Element bar : BarOrder.normalized(mHost.order())) {
            if (itemOf(bar).container.getVisibility() == View.VISIBLE)
                out.add(bar);
        }
        return out;
    }

    // ------------------------------------------------------------------------------- the drag

    private void startDrag(@NonNull Item item, float rawY) {
        if (mDragged != null)
            return;
        List<Element> visible = visibleOrder();
        int from = visible.indexOf(item.bar);
        if (from < 0)
            return;
        mDragged = item;
        mDragVisible = visible;
        mDragFrom = from;
        mDragTarget = from;
        mDragDownY = rawY;
        mDragCentres = new float[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            View row = itemOf(visible.get(i)).container;
            mDragCentres[i] = row.getTop() + row.getHeight() / 2f;
        }
        ViewParent parent = mList.getParent();
        if (parent != null)
            parent.requestDisallowInterceptTouchEvent(true);
        item.container.setElevation(RowKit.dp(mContext, DRAG_ELEVATION_DP));
        item.container.setBackground(dragBackground());
        item.container.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void moveDrag(float rawY) {
        Item item = mDragged;
        if (item == null)
            return;
        View row = item.container;
        float dy = rawY - mDragDownY;
        // Kept inside the list, so the row never leaves the rounded card.
        dy = Math.max(-row.getTop(), Math.min(mList.getHeight() - row.getBottom(), dy));
        row.setTranslationY(dy);
        int target = BarOrder.dropIndex(mDragCentres, mDragFrom, mDragCentres[mDragFrom] + dy);
        if (target == mDragTarget)
            return;
        mDragTarget = target;
        int height = row.getHeight();
        for (int i = 0; i < mDragVisible.size(); i++) {
            if (i == mDragFrom)
                continue;
            float shift = 0f;
            if (mDragFrom < target && i > mDragFrom && i <= target)
                shift = -height;
            else if (mDragFrom > target && i >= target && i < mDragFrom)
                shift = height;
            itemOf(mDragVisible.get(i)).container.animate().translationY(shift)
                .setDuration(SHIFT_MS).start();
        }
    }

    private void endDrag(boolean commit) {
        Item item = mDragged;
        if (item == null)
            return;
        mDragged = null;
        for (Element bar : mDragVisible) {
            View row = itemOf(bar).container;
            row.animate().cancel();
            row.setTranslationY(0f);
        }
        item.container.setElevation(0f);
        item.container.setBackground(null);
        ViewParent parent = mList.getParent();
        if (parent != null)
            parent.requestDisallowInterceptTouchEvent(false);
        if (commit && mDragTarget != mDragFrom)
            mHost.reorder(BarOrder.mergeVisible(BarOrder.normalized(mHost.order()),
                BarOrder.moved(mDragVisible, mDragFrom, mDragTarget)));
        restate();
    }

    /** The lifted row's own card, so it reads above the rows it passes. */
    @NonNull
    private GradientDrawable dragBackground() {
        GradientDrawable lifted = new GradientDrawable();
        lifted.setColor(RowKit.color(mContext,
            com.google.android.material.R.attr.colorSurfaceContainerHighest));
        lifted.setCornerRadius(RowKit.dp(mContext, CORNER_DP));
        return lifted;
    }

    // ---------------------------------------------------------------------------- the helpers

    @NonNull
    private static MaterialButton iconButton(@NonNull Context context, @DrawableRes int icon,
                                             @NonNull CharSequence description) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        button.setContentDescription(description);
        GlyphNames.name(button, description);
        return button;
    }

    private static void setVisible(@NonNull View view, boolean visible) {
        int next = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != next)
            view.setVisibility(next);
    }
}
