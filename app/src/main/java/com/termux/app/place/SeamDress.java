package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Objects;

/**
 * What each surface wears while the wall slides across the seam between its halves (ADR 0008):
 * Home's Style, Corners and Margin and the Home layout's sizes on the Home pages, the Workspace's
 * on the windows. The chrome used to be dressed for the page it was committed to and dressed again
 * at settle ({@code restyleChromeForHalfIfChanged}), so a half's surfaces arriving on screen wore
 * the other half's shape for the whole slide and snapped into their own as it landed. Here every
 * surface wears the half it belongs to on every frame it can be seen:
 *
 * <ul>
 *   <li><b>The stack</b> (the dock's rows, the A-Z index, the extra keys and the keyboard) is
 *       dressed for the committed half all the way across and for the arriving half from the
 *       landing; it is never laid out or dressed again on the way. Where the halves dress it apart
 *       in look its glass blends with the wall ({@link StackDress#structureEquals}), and where
 *       they dress it apart in structure the slab and the keyboard blend their numbers
 *       ({@link StackScene}).</li>
 *   <li><b>The status bar</b> is the Workspace's alone (Home wears none), so it wears the
 *       Workspace's dress through any slide across the seam, laid out over Home or not.</li>
 *   <li><b>The frame</b> (the window's border, its panes and Home's floats, which share their
 *       pane style) stays with the committed half and switches at settle.</li>
 * </ul>
 *
 * <p>Pure, so which shape each surface wears per frame is read and tested as arithmetic.
 */
public final class SeamDress {

    private SeamDress() {}

    /** The surfaces that wear a half's dress, grouped by the half they belong to mid-slide. */
    public enum Surface {
        /** The dock's rows, the index, the extra keys and the keyboard. */
        STACK,
        /** The status bar, the Workspace's alone. */
        STATUS,
        /** The window's frame, its panes and Home's floats: the committed half's. */
        FRAME
    }

    /**
     * What the stack wears on one half: the half's Style, Corners and Margin, the sizes its
     * layout gives the dock and the keyboard, and the dock's and keyboard's look values there
     * (ADR 0013). Two halves that agree on all of it dress the stack alike, and a slide between
     * them changes nothing the stack shows.
     */
    public static final class StackDress {
        @NonNull public final WallShape shape;
        public final float dockHeightScale;
        public final float keyboardHeightScale;
        public final int keyboardChinDp;
        /** The half's look; only its stack part (the dock and the keyboard) is compared. */
        @NonNull public final HalfLook look;

        private StackDress(@NonNull WallShape shape, float dockHeightScale,
                           float keyboardHeightScale, int keyboardChinDp,
                           @NonNull HalfLook look) {
            this.shape = shape;
            this.dockHeightScale = dockHeightScale;
            this.keyboardHeightScale = keyboardHeightScale;
            this.keyboardChinDp = keyboardChinDp;
            this.look = look;
        }

        @NonNull
        public static StackDress of(@NonNull WallShape shape, float dockHeightScale,
                                    float keyboardHeightScale, int keyboardChinDp) {
            return of(shape, dockHeightScale, keyboardHeightScale, keyboardChinDp, HalfLook.NONE);
        }

        @NonNull
        public static StackDress of(@NonNull WallShape shape, float dockHeightScale,
                                    float keyboardHeightScale, int keyboardChinDp,
                                    @NonNull HalfLook look) {
            return new StackDress(shape, dockHeightScale, keyboardHeightScale, keyboardChinDp,
                look);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof StackDress)) return false;
            StackDress that = (StackDress) other;
            return shape.equals(that.shape)
                && Float.compare(dockHeightScale, that.dockHeightScale) == 0
                && Float.compare(keyboardHeightScale, that.keyboardHeightScale) == 0
                && keyboardChinDp == that.keyboardChinDp
                && look.stackEquals(that.look);
        }

        /**
         * Whether {@code that} dresses the stack's structure as this does, its shape and sizes,
         * whatever its look values: two halves apart only in look keep one stack, whose glass
         * blends from one look to the other with the wall instead of dipping (amendment
         * 2026-11-04).
         */
        public boolean structureEquals(@NonNull StackDress that) {
            return shape.equals(that.shape)
                && Float.compare(dockHeightScale, that.dockHeightScale) == 0
                && Float.compare(keyboardHeightScale, that.keyboardHeightScale) == 0
                && keyboardChinDp == that.keyboardChinDp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(shape, dockHeightScale, keyboardHeightScale, keyboardChinDp,
                look.stackHashCode());
        }

        @NonNull
        @Override
        public String toString() {
            return shape + " dock=" + dockHeightScale + " keyboard=" + keyboardHeightScale
                + " chin=" + keyboardChinDp;
        }
    }

    /**
     * The page whose half the stack wears at {@code progress} into the other half
     * ({@link SeamTravel#otherHalfProgress}): the other side's once the stack stands its rows
     * ({@link SeamTravel#showsOtherDock}), the committed page's before that and at rest. A drag
     * that reverses reads it back through the same swap.
     */
    @NonNull
    public static PageKind stackPage(@NonNull PageKind committed,
                                         @Nullable PageKind other, @NonNull DockPlan plan,
                                         float progress) {
        return other != null && SeamTravel.showsOtherDock(plan, progress) ? other : committed;
    }

    /**
     * Whether {@code surface} wears Home's dress on this frame.
     *
     * @param committed  the page the chrome is committed to
     * @param stackPage  the page whose half the stack wears ({@link #stackPage}); the committed
     *                   page at rest
     * @param seamTravel whether the wall is sliding across the seam: a frame of the gesture has
     *                   reached the other half and the wall has not landed yet
     *                   ({@link SeamCrossing#sliding}). A landed crossing is at rest, so the
     *                   page's commit dresses the status bar in its own half's look, the one
     *                   every later pass reads
     */
    public static boolean wearsHome(@NonNull Surface surface, @NonNull PageKind committed,
                                    @NonNull PageKind stackPage, boolean seamTravel) {
        switch (surface) {
            case STACK: return stackPage == PageKind.HOME;
            case STATUS: return !seamTravel && committed == PageKind.HOME;
            case FRAME:
            default: return committed == PageKind.HOME;
        }
    }

    /**
     * The page whose look values {@code surface} wears on this frame (ADR 0015): the page whose
     * dress it wears ({@link #wearsHome}), {@link PageKind#HOME} for Home's and
     * {@link PageKind#TERMINAL} for the Terminal's. The stack's look so changes with its
     * rows, where none of it shows; the status bar wears the Terminal's through a slide and its
     * own page's at rest; the frame its committed page's.
     */
    @NonNull
    public static PageKind lookPage(@NonNull Surface surface, @NonNull PageKind committed,
                                        @NonNull PageKind stackPage, boolean seamTravel) {
        return wearsHome(surface, committed, stackPage, seamTravel) ? PageKind.HOME
            : PageKind.TERMINAL;
    }

    /** The kind of screen a page is, for its look values: Home's or the Terminal's. */
    @NonNull
    public static PageKind screenOf(@NonNull PageKind page) {
        return page == PageKind.HOME ? PageKind.HOME : PageKind.TERMINAL;
    }

    /**
     * The id a page's look values are read under: the wall (Global) for Home's pages, which hold no
     * look of their own, and the Terminal page's id for any other page.
     */
    public static int lookPageId(@NonNull PageKind page, int terminalPageId) {
        return page == PageKind.HOME ? TermuxAppSharedPreferences.PAGE_WALL
            : terminalPageId;
    }

    /**
     * The id a surface's look values are read under (ADR 0015 §6): the page it stands on. Home's
     * pages read the Home page's own ({@code homePageId}, the wall while none is known); a window
     * reads the bound Display page's ({@code displayPageId}, when the surface stands on Display)
     * or else the Terminal page's. A page holding no overrides reads Global through its id.
     *
     * @param page the kind of page the surface wears ({@link #lookPage})
     */
    public static int lookPageId(@NonNull PageKind page, int homePageId, int displayPageId,
                                 int terminalPageId) {
        if (page == PageKind.HOME)
            return homePageId >= 0 ? homePageId : TermuxAppSharedPreferences.PAGE_WALL;
        return displayPageId >= 0 ? displayPageId : terminalPageId;
    }
}
