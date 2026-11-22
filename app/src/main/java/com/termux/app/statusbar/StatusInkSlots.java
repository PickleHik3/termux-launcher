package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * Where each colour of the status bar's content sits in the int array
 * {@link StatusBarWidgets#resolveStatusInks} fills and {@link StatusBarWidgets#applyStatusInks}
 * writes, which is also the slot layout of {@link TopStackScene.End#inks}: a crossing blends the
 * two ends' arrays slot by slot, so a slot is one colour and nothing else. The row's widgets and
 * dots are keyed by {@link StatusRowItem}, never by where the user's order stands them, so a
 * blend never runs between two items' colours.
 */
public final class StatusInkSlots {

    private StatusInkSlots() {}

    /** The status widgets, each three slots: body ink, large (icon) ink, muted ink. */
    public static final int WIDGETS = StatusRowItem.values().length;
    public static final int PER_WIDGET = 3;
    public static final int WIDGET_BODY = 0;
    public static final int WIDGET_LARGE = 1;
    public static final int WIDGET_MUTED = 2;
    /** The separator dots, one slot each: the dot before each item. */
    public static final int DOTS = StatusRowItem.values().length;

    public static final int WIDGET_FIRST = 0;
    public static final int DOT_FIRST = WIDGET_FIRST + WIDGETS * PER_WIDGET;
    public static final int LENS_SURFACE = DOT_FIRST + DOTS;
    public static final int PINNED_SURFACE = LENS_SURFACE + 1;
    public static final int CLOCK_SURFACE = PINNED_SURFACE + 1;
    public static final int CLOCK_INK = CLOCK_SURFACE + 1;
    public static final int COUNT = CLOCK_INK + 1;

    /** The first slot of the widget of the item whose ordinal is {@code index}. */
    public static int widget(int index) {
        return WIDGET_FIRST + index * PER_WIDGET;
    }

    /** The first slot of {@code item}'s widget. */
    public static int widget(@NonNull StatusRowItem item) {
        return widget(item.ordinal());
    }

    /** The slot of the dot before the item whose ordinal is {@code index}. */
    public static int dot(int index) {
        return DOT_FIRST + index;
    }

    /** The slot of the dot before {@code item}. */
    public static int dot(@NonNull StatusRowItem item) {
        return dot(item.ordinal());
    }
}
