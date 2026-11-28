package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edit mode's live labels (round 5, the prototype's {@code .eo}): a dashed outline with a small
 * label on every surface over the live scene, so the reader sees what a tap opens. The screen's
 * own frame (the wallpaper, with a Wallpaper chip at its top right), Home or each window of the
 * Workspace in front (its name), the control bar and the keyboard (their labels ride their top
 * edge). The surface of the card that is open is drawn solid in the accent instead.
 *
 * <p>Pure: rectangles in, outlines out, in the overlay's pixels. It runs once per change of what
 * edit mode measures (a layout, a card opening), never per frame.</p>
 */
public final class EditOutlines {

    private EditOutlines() {}

    /** A rectangle, left/top/right/bottom in px. */
    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        @NonNull
        Box inset(float l, float t, float r, float b) {
            return new Box(left + l, top + t, right - r, bottom - b);
        }

        @NonNull
        Box clippedTo(@NonNull Box room) {
            return new Box(Math.max(left, room.left), Math.max(top, room.top),
                Math.min(right, room.right), Math.min(bottom, room.bottom));
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Box)) return false;
            Box that = (Box) other;
            return left == that.left && top == that.top && right == that.right && bottom == that.bottom;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(left, top, right, bottom);
        }

        @NonNull
        @Override
        public String toString() {
            return "[" + left + "," + top + "," + right + "," + bottom + "]";
        }
    }

    /** A window of the place in front: where it stands, its corner radius and its name. */
    public static final class Window {
        @NonNull public final Box box;
        public final float radiusPx;
        @NonNull public final String name;

        public Window(@NonNull Box box, float radiusPx, @NonNull String name) {
            this.box = box;
            this.radiusPx = radiusPx;
            this.name = name;
        }
    }

    /** Which surface an outline marks. */
    public enum Surface { WALLPAPER, HOME, WINDOW, BAR, KEYBOARD }

    /** Where an outline's label stands. */
    public enum LabelAt {
        /** Inside its top-left corner (Home, a window). */
        INSIDE,
        /** Riding its top edge (the control bar, the keyboard). */
        ON_EDGE,
        /** A chip at the screen's top right (the wallpaper). */
        CHIP
    }

    /** One outline to draw. */
    public static final class Outline {
        @NonNull public final Surface surface;
        @NonNull public final Box box;
        public final float radiusPx;
        /** Its label; empty for none. */
        @NonNull public final String label;
        @NonNull public final LabelAt labelAt;
        /** Its card is open: solid in the accent, not dashed. */
        public final boolean lit;

        Outline(@NonNull Surface surface, @NonNull Box box, float radiusPx, @NonNull String label,
                @NonNull LabelAt labelAt, boolean lit) {
            this.surface = surface;
            this.box = box;
            this.radiusPx = radiusPx;
            this.label = label;
            this.labelAt = labelAt;
            this.lit = lit;
        }
    }

    /** The words on the labels (from resources; held here as text so the arithmetic stays pure). */
    public static final class Labels {
        @NonNull final String wallpaper;
        @NonNull final String home;
        @NonNull final String bar;
        @NonNull final String keyboard;

        public Labels(@NonNull String wallpaper, @NonNull String home, @NonNull String bar,
                      @NonNull String keyboard) {
            this.wallpaper = wallpaper;
            this.home = home;
            this.bar = bar;
            this.keyboard = keyboard;
        }
    }

    /** What edit mode measured, in the overlay's px; {@code density} turns the prototype's dp to px. */
    public static final class Scene {
        @NonNull final Box screen;
        @Nullable final Box home;
        @NonNull final List<Window> windows;
        @Nullable final Box bar;
        final float barRadiusPx;
        @Nullable final Box keyboard;
        final float keyboardRadiusPx;
        final float density;

        public Scene(@NonNull Box screen, @Nullable Box home, @NonNull List<Window> windows,
                     @Nullable Box bar, float barRadiusPx, @Nullable Box keyboard,
                     float keyboardRadiusPx, float density) {
            this.screen = screen;
            this.home = home;
            this.windows = windows;
            this.bar = bar;
            this.barRadiusPx = barRadiusPx;
            this.keyboard = keyboard;
            this.keyboardRadiusPx = keyboardRadiusPx;
            this.density = density;
        }
    }

    /** The screen frame's inset and corner, dp (the prototype's {@code #edlayer .frame}). */
    static final float FRAME_INSET_DP = 3f;
    static final float FRAME_RADIUS_DP = 36f;
    /** Home's outline inset from its page, dp: left/right, top, bottom (the prototype's {@code .homeW .eo}). */
    static final float HOME_INSET_SIDE_DP = 10f;
    static final float HOME_INSET_TOP_DP = 12f;
    static final float HOME_INSET_BOTTOM_DP = 18f;
    static final float HOME_RADIUS_DP = 20f;

    /**
     * The outlines for {@code scene} with {@code open} the card open (null at rest), bottom to top:
     * the screen's frame, Home or the windows, the bar, the keyboard. A surface that is not in
     * sight (no Home in front, no bar, the keyboard down) has none; a window is clipped to the
     * screen and left out when nothing of it shows. The Windows card lights every window.
     */
    @NonNull
    public static List<Outline> of(@NonNull Scene scene, @NonNull Labels labels,
                                   @Nullable EditModeController.Card open) {
        if (scene.screen.isEmpty()) return Collections.emptyList();
        float d = scene.density;
        List<Outline> out = new ArrayList<>(4 + scene.windows.size());
        float frame = FRAME_INSET_DP * d;
        out.add(new Outline(Surface.WALLPAPER, scene.screen.inset(frame, frame, frame, frame),
            FRAME_RADIUS_DP * d, labels.wallpaper, LabelAt.CHIP, open == EditModeController.Card.WALLPAPER));
        if (scene.home != null) {
            Box home = scene.home.inset(HOME_INSET_SIDE_DP * d, HOME_INSET_TOP_DP * d,
                HOME_INSET_SIDE_DP * d, HOME_INSET_BOTTOM_DP * d);
            if (!home.isEmpty()) {
                out.add(new Outline(Surface.HOME, home, HOME_RADIUS_DP * d, labels.home, LabelAt.INSIDE,
                    open == EditModeController.Card.HOME));
            }
        }
        for (Window window : scene.windows) {
            Box box = window.box.clippedTo(scene.screen);
            if (box.isEmpty()) continue;
            out.add(new Outline(Surface.WINDOW, box, window.radiusPx, window.name, LabelAt.INSIDE,
                open == EditModeController.Card.WINDOWS));
        }
        if (scene.bar != null && !scene.bar.isEmpty()) {
            out.add(new Outline(Surface.BAR, scene.bar, scene.barRadiusPx, labels.bar, LabelAt.ON_EDGE,
                open == EditModeController.Card.BAR));
        }
        if (scene.keyboard != null && !scene.keyboard.isEmpty()) {
            out.add(new Outline(Surface.KEYBOARD, scene.keyboard, scene.keyboardRadiusPx, labels.keyboard,
                LabelAt.ON_EDGE, open == EditModeController.Card.KEYBOARD));
        }
        return out;
    }
}
