package com.termux.app.launcher.popup;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.termux.app.material.M3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the App menu's card: groups of rows, each row a 216 x 52 dp slab with a 24 dp outlined
 * glyph and a 14 sp label, in its group's shade, joined to its neighbours by small radii and to the
 * card's outside by the dialog radius. {@link AnchoredMenu#showAppMenu} puts it on screen.
 */
public final class AppMenuCard {

    /** One row as the host describes it. */
    public static final class Item {
        @NonNull final String label;
        @DrawableRes final int glyph;
        @NonNull final Runnable action;
        final boolean opensSubmenu;
        /** A group's caption: a shorter slab with no action, never highlighted. */
        final boolean header;
        /** A row the host draws itself (a segmented choice), in the group's shade; no action. */
        @Nullable final View custom;

        public Item(@NonNull String label, @DrawableRes int glyph, @NonNull Runnable action) {
            this(label, glyph, action, false);
        }

        public Item(@NonNull String label, @DrawableRes int glyph, @NonNull Runnable action,
                    boolean opensSubmenu) {
            this(label, glyph, action, opensSubmenu, false, null);
        }

        private Item(@NonNull String label, @DrawableRes int glyph, @NonNull Runnable action,
                     boolean opensSubmenu, boolean header, @Nullable View custom) {
            this.label = label;
            this.glyph = glyph;
            this.action = action;
            this.opensSubmenu = opensSubmenu;
            this.header = header;
            this.custom = custom;
        }

        /** A caption over the group it leads, such as the name of what was pressed. */
        @NonNull
        public static Item header(@NonNull String label) {
            return new Item(label, 0, () -> { }, false, true, null);
        }

        /** {@code view} as a row of its own, a row's height, in the group's shade. */
        @NonNull
        public static Item custom(@NonNull View view) {
            return new Item("", 0, () -> { }, false, false, view);
        }
    }

    /** Rows announce as buttons; a TextView would otherwise read as plain text. */
    private static final AccessibilityDelegateCompat BUTTON_ROLE = new AccessibilityDelegateCompat() {
        @Override
        public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                      @NonNull AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName("android.widget.Button");
        }
    };

    @NonNull public final AppMenuCardView view;
    /** Every row, top to bottom, for the drag-to-highlight tracker. */
    @NonNull public final List<MenuRow> rows;
    /** Each group's shade, top to bottom. */
    @NonNull public final int[] groupShades;
    /** Row width in px, the card's width without its shadow room. */
    public final int width;

    private AppMenuCard(@NonNull AppMenuCardView view, @NonNull List<MenuRow> rows,
                        @NonNull int[] groupShades, int width) {
        this.view = view;
        this.rows = Collections.unmodifiableList(rows);
        this.groupShades = groupShades;
        this.width = width;
    }

    /** The shade the arrow takes: the top group's when it points up, the bottom group's otherwise. */
    public int arrowShade(boolean arrowOnTop) {
        if (groupShades.length == 0) return 0;
        return arrowOnTop ? groupShades[0] : groupShades[groupShades.length - 1];
    }

    @NonNull
    public static AppMenuCard build(@NonNull Context context, @NonNull MenuRowFactory factory,
                                    @NonNull List<List<Item>> groups) {
        float density = context.getResources().getDisplayMetrics().density;
        int width = dp(density, AppMenuGeometry.ROW_WIDTH_DP);
        int height = dp(density, AppMenuGeometry.ROW_HEIGHT_DP);
        int headerHeight = dp(density, AppMenuGeometry.HEADER_HEIGHT_DP);
        int padding = dp(density, AppMenuGeometry.ROW_PADDING_DP);
        int glyphSize = dp(density, AppMenuGeometry.GLYPH_DP);
        int glyphGap = dp(density, AppMenuGeometry.GLYPH_GAP_DP);
        int gap = dp(density, AppMenuGeometry.GROUP_GAP_DP);
        float outer = dp(density, AppMenuGeometry.OUTER_RADIUS_DP);
        float join = dp(density, AppMenuGeometry.JOIN_RADIUS_DP);
        float pill = dp(density, AppMenuGeometry.PILL_RADIUS_DP);
        float elevation = dp(density, AppMenuGeometry.ELEVATION_DP);
        int[] shades = AppMenuShades.resolve(context);
        int ink = M3.onSurface(context);
        int glyphInk = M3.onSurfaceVariant(context);
        int stateLayer = MenuRowFactory.armedFill(ink);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        List<MenuRow> rows = new ArrayList<>();
        List<Integer> usedShades = new ArrayList<>(groups.size());
        float[] radii = new float[2];
        int groupIndex = 0;
        for (List<Item> group : groups) {
            if (group.isEmpty()) continue;
            int shade = shades[AppMenuGeometry.shadeIndex(groupIndex)];
            usedShades.add(shade);
            for (int i = 0; i < group.size(); i++) {
                Item item = group.get(i);
                AppMenuGeometry.rowRadii(i, group.size(), outer, join, pill, radii);
                if (item.header || item.custom != null) {
                    View slab = item.header
                        ? headerView(context, item.label, glyphInk, padding)
                        : customSlab(context, item.custom, padding);
                    slab.setBackground(new AppMenuRowDrawable(shade, 0, radii[0], radii[1]));
                    slab.setElevation(elevation);
                    LinearLayout.LayoutParams slabLp = new LinearLayout.LayoutParams(width,
                        item.header ? headerHeight : height);
                    if (i == 0 && groupIndex > 0) slabLp.topMargin = gap;
                    column.addView(slab, slabLp);
                    continue;
                }
                TextView row = new TextView(context);
                M3.textAppearance(row, com.google.android.material.R.attr.textAppearanceBodyMedium);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppMenuGeometry.LABEL_SP);
                row.setTextColor(ink);
                row.setText(item.label);
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                row.setPaddingRelative(padding, 0, padding, 0);
                Drawable glyph = item.glyph != 0
                    ? factory.loadMenuIcon(item.glyph, glyphSize, glyphInk) : null;
                if (glyph != null) {
                    row.setCompoundDrawablesRelative(glyph, null, null, null);
                    row.setCompoundDrawablePadding(glyphGap);
                }
                row.setBackground(new AppMenuRowDrawable(shade, stateLayer, radii[0], radii[1]));
                row.setElevation(elevation);
                row.setClickable(true);
                // Real views in a window of their own: say outright that they are buttons a
                // screen reader should reach, and which one opens more.
                ViewCompat.setImportantForAccessibility(row,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
                ViewCompat.setAccessibilityDelegate(row, BUTTON_ROLE);
                Runnable action = item.action;
                row.setOnClickListener(v -> factory.runWithFeedback(row, action));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
                if (i == 0 && groupIndex > 0) lp.topMargin = gap;
                column.addView(row, lp);
                rows.add(new MenuRow(row, action, item.opensSubmenu));
            }
            groupIndex++;
        }
        int[] groupShades = new int[usedShades.size()];
        for (int i = 0; i < groupShades.length; i++) groupShades[i] = usedShades.get(i);
        return new AppMenuCard(new AppMenuCardView(context, column), rows, groupShades, width);
    }

    /** A group's caption: the label style, in the glyphs' ink, sitting on the slab's foot. */
    @NonNull
    private static TextView headerView(@NonNull Context context, @NonNull String label,
                                       int ink, int padding) {
        TextView header = new TextView(context);
        M3.textAppearance(header, com.google.android.material.R.attr.textAppearanceLabelMedium);
        header.setTextColor(ink);
        header.setText(label);
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setGravity(Gravity.START | Gravity.BOTTOM);
        header.setPaddingRelative(padding, 0, padding, padding / 4);
        ViewCompat.setAccessibilityHeading(header, true);
        return header;
    }

    /** The host's own row, centred in a row-sized slab with the rows' side padding. */
    @NonNull
    private static View customSlab(@NonNull Context context, @NonNull View custom, int padding) {
        FrameLayout slab = new FrameLayout(context);
        slab.setPaddingRelative(padding, 0, padding, 0);
        if (custom.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) custom.getParent()).removeView(custom);
        slab.addView(custom, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL));
        return slab;
    }

    /** The rows' total height at {@code width}, before any placement clamps it. */
    public int measureContentHeight() {
        LinearLayout column = view.groups();
        column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return column.getMeasuredHeight();
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }
}
