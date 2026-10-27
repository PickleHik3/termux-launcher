package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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
 *   <li><b>The stack</b> (the dock's rows, the A-Z index, the extra keys and the keyboard) wears
 *       the half whose rows it stands ({@link #stackPage}). It changes its dress only together
 *       with its rows, on the frame the seam lays them out, which is one where none of the stack
 *       shows. When the halves dress it differently and both show some of it, the whole stack,
 *       keyboard included, dips: down over the first half of the way, dressed and laid out for the
 *       other half while it is below the screen, and up over the second ({@link #plan}).</li>
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
        @NonNull public final WallHalfShape shape;
        public final float dockHeightScale;
        public final float keyboardHeightScale;
        public final int keyboardChinDp;
        /** The half's look; only its stack part (the dock and the keyboard) is compared. */
        @NonNull public final HalfLook look;

        private StackDress(@NonNull WallHalfShape shape, float dockHeightScale,
                           float keyboardHeightScale, int keyboardChinDp,
                           @NonNull HalfLook look) {
            this.shape = shape;
            this.dockHeightScale = dockHeightScale;
            this.keyboardHeightScale = keyboardHeightScale;
            this.keyboardChinDp = keyboardChinDp;
            this.look = look;
        }

        @NonNull
        public static StackDress of(@NonNull WallHalfShape shape, float dockHeightScale,
                                    float keyboardHeightScale, int keyboardChinDp) {
            return of(shape, dockHeightScale, keyboardHeightScale, keyboardChinDp, HalfLook.NONE);
        }

        @NonNull
        public static StackDress of(@NonNull WallHalfShape shape, float dockHeightScale,
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
    public static PaneWallPage stackPage(@NonNull PaneWallPage committed,
                                         @Nullable PaneWallPage other, @NonNull DockPlan plan,
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
    public static boolean wearsHome(@NonNull Surface surface, @NonNull PaneWallPage committed,
                                    @NonNull PaneWallPage stackPage, boolean seamTravel) {
        switch (surface) {
            case STACK: return stackPage == PaneWallPage.WIDGETS;
            case STATUS: return !seamTravel && committed == PaneWallPage.WIDGETS;
            case FRAME:
            default: return committed == PaneWallPage.WIDGETS;
        }
    }

    /**
     * The half of the wall whose look values {@code surface} wears on this frame (ADR 0013): the
     * half whose dress it wears ({@link #wearsHome}). The stack's look so changes with its rows,
     * where none of it shows; the status bar wears the Workspace's through a slide and its own
     * half's at rest; the frame its committed half's.
     */
    @NonNull
    public static WallHalf lookHalf(@NonNull Surface surface, @NonNull PaneWallPage committed,
                                    @NonNull PaneWallPage stackPage, boolean seamTravel) {
        return wearsHome(surface, committed, stackPage, seamTravel) ? WallHalf.HOME
            : WallHalf.WORKSPACE;
    }

    /** The half of the wall a page stands on, for its look values. */
    @NonNull
    public static WallHalf halfOf(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? WallHalf.HOME : WallHalf.WORKSPACE;
    }

    /**
     * How the stack crosses the seam, given how its rows would ({@code rowsPlan},
     * {@link SeamTravel#dockPlan}). While the halves dress it alike that is the answer. While they
     * differ, the stack's dress may only change on a frame where none of the stack shows:
     *
     * <ul>
     *   <li>neither half shows any of it: nothing to dress ({@link DockPlan#NONE});</li>
     *   <li>only the other half does: it is laid out and dressed in the first frame, while all of
     *       it is below the screen, and rises ({@link DockPlan#SLIDE_IN});</li>
     *   <li>only the committed half does: it leaves as it would, and is dressed at settle, off
     *       screen ({@code rowsPlan});</li>
     *   <li>both do, the same rows or not, a keyboard or not: the whole stack dips
     *       ({@link DockPlan#DIP}, {@link #wholeStack}).</li>
     * </ul>
     *
     * A stack that cannot be exchanged mid-slide ({@code exchangeable} false: a row on a side or
     * the top edge, under the keyboard, or beside a bottom status bar) keeps its settle-time
     * change.
     *
     * @param committedShows whether the committed half's stack shows anything at rest: a dock row
     *                       on the bottom edge, or a keyboard in the stack
     * @param otherShows     the same, for the other half
     */
    @NonNull
    public static DockPlan plan(@NonNull DockPlan rowsPlan, boolean exchangeable,
                                boolean dressDiffers, boolean committedShows,
                                boolean otherShows) {
        if (!dressDiffers || !exchangeable) return rowsPlan;
        if (!otherShows) return committedShows ? rowsPlan : DockPlan.NONE;
        if (!committedShows) return DockPlan.SLIDE_IN;
        return DockPlan.DIP;
    }

    /**
     * Whether the whole stack, keyboard included, travels by the plan's reveal: a dip between two
     * halves that dress it differently. Otherwise the rows sink into a keyboard in the stack and
     * the keyboard keeps its own travel, as between halves dressed alike.
     */
    public static boolean wholeStack(@NonNull DockPlan plan, boolean dressDiffers) {
        return dressDiffers && plan == DockPlan.DIP;
    }

    /**
     * How much of the keyboard shows in a whole-stack dip at {@code progress} into the other
     * half: the committed half's keyboard goes down with the rows before the swap, the other
     * half's comes up with them after it, and a half without one shows none. Both read 0 at the
     * swap, where the stack is dressed again.
     */
    public static float wholeStackKeyboardReveal(float progress, boolean committedKeyboard,
                                                 boolean otherKeyboard) {
        float reveal = SeamTravel.dockReveal(DockPlan.DIP, progress);
        boolean keyboard = progress < SeamTravel.DIP_SWAP ? committedKeyboard : otherKeyboard;
        return keyboard ? reveal : 0f;
    }
}
