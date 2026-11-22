package com.termux.app.statusbar;

/**
 * Where each colour of the status bar's content sits in the int array
 * {@link StatusBarWidgets#resolveStatusInks} fills and {@link StatusBarWidgets#applyStatusInks}
 * writes, which is also the slot layout of {@link TopStackScene.End#inks}: a crossing blends the
 * two ends' arrays slot by slot, so a slot is one colour and nothing else.
 */
public final class StatusInkSlots {

    private StatusInkSlots() {}

    /** The status widgets, each three slots: body ink, large (icon) ink, muted ink. */
    public static final int WIDGETS = 8;
    public static final int PER_WIDGET = 3;
    public static final int WIDGET_BODY = 0;
    public static final int WIDGET_LARGE = 1;
    public static final int WIDGET_MUTED = 2;
    /** The separator dots, one slot each. */
    public static final int DOTS = 8;

    public static final int WIDGET_FIRST = 0;
    public static final int DOT_FIRST = WIDGET_FIRST + WIDGETS * PER_WIDGET;
    public static final int LENS_SURFACE = DOT_FIRST + DOTS;
    public static final int PINNED_SURFACE = LENS_SURFACE + 1;
    public static final int CLOCK_SURFACE = PINNED_SURFACE + 1;
    public static final int CLOCK_INK = CLOCK_SURFACE + 1;
    public static final int COUNT = CLOCK_INK + 1;

    /** The first slot of widget {@code index} in the row's order. */
    public static int widget(int index) {
        return WIDGET_FIRST + index * PER_WIDGET;
    }

    /** The slot of dot {@code index} in the row's order. */
    public static int dot(int index) {
        return DOT_FIRST + index;
    }
}
