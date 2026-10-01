package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarEdgeGeometry;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The layout canvas's measurements, as pure functions. The canvas is a to-scale picture of the
 * screen: every band's thickness is the launcher's own figure in dp, from the same policies the
 * launcher sizes itself with, times one scale (canvas pixels per real dp). Nothing here touches a
 * view, a context or a preference, so the mapping can be tested on the JVM.
 *
 * <p>It also owns the key rows the keyboard is drawn from, docked and split, so a split keyboard is
 * the real rows parted at their midpoint rather than a second, schematic drawing.
 */
final class LayoutCanvasGeometry {

    private LayoutCanvasGeometry() {}

    // ---- Bands, in dp --------------------------------------------------------------------------

    /**
     * The pinned apps' band at a dock height scale, in dp: {@link DockLayoutPolicy}'s own answer
     * at a density of 1, for the row standing alone on its edge. {@code floating} is the capsule
     * style (rounded, with its own padding), otherwise the square docked one.
     */
    static int dockBandHeightDp(float dockHeightScale, boolean floating) {
        DockLayout layout = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(floating)
            .appsRowAlone(true)
            .appsRowEnabledPref(true)
            .density(1f)
            .barHeightScale(dockHeightScale)
            .baseToolbarHeightPx(Math.round(BASE_TOOLBAR_DP))
            .build());
        return layout.appsRowBandPx;
    }

    /** The width of a rail the pinned apps stand in when they lie on a side edge, in dp. */
    static int dockRailWidthDp() {
        return Math.round(Math.max(52f, DockLayoutPolicy.DOCK_RAIL_ICON_SIZE_DP
            + 2f * DockLayoutPolicy.LONE_ROW_AIR_DP));
    }

    /** The shipped height of one extra-keys row, in dp. */
    static final float BASE_TOOLBAR_DP = 37.5f;

    /** The extra keys' band, in dp: one row at the shipped height factor. */
    static int toolbarHeightDp() {
        return AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(
            Math.round(BASE_TOOLBAR_DP), 1,
            TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);
    }

    /** The A–Z index's band, in dp: the letters' band with its chin, alone on its edge. */
    static int azBandHeightDp() {
        return AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, false, 1f);
    }

    /** The status bar's thickness on an edge, in dp, collapsed or expanded. */
    static int statusBandDp(@NonNull Edge edge, boolean compact, boolean floating) {
        return Math.round(StatusBarEdgeGeometry.thicknessDp(edge, floating, compact));
    }

    /** Rows of keys the keyboard stacks; its height is this many key rows. */
    static final int KEYBOARD_ROWS = 4;
    /** The row height the keyboard is drawn at before its scale, in dp (portrait, landscape). */
    private static final float KEY_ROW_DP_PORTRAIT = 52f;
    private static final float KEY_ROW_DP_LANDSCAPE = 44f;
    /** The keyboard's own air above its first row and under its last (before the chin), in dp. */
    private static final float KEYBOARD_MARGIN_TOP_DP = 3f;
    private static final float KEYBOARD_MARGIN_BOTTOM_DP = 7f;
    /** The most of the screen's height the keyboard may take (portrait, landscape). */
    private static final float MAX_HEIGHT_FRACTION_PORTRAIT = 0.42f;
    private static final float MAX_HEIGHT_FRACTION_LANDSCAPE = 0.40f;

    /**
     * The keyboard's height in dp at its height scale, without the chin: its rows times the scale
     * plus its margins, and never more than the keyboard's share of the screen. A screen height of
     * zero or less means "no screen to ask" and skips the cap.
     */
    static float keyboardHeightDp(float heightScale, boolean landscape, float screenHeightDp) {
        float row = landscape ? KEY_ROW_DP_LANDSCAPE : KEY_ROW_DP_PORTRAIT;
        float scale = Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE, heightScale));
        float height = row * scale * KEYBOARD_ROWS + KEYBOARD_MARGIN_TOP_DP
            + KEYBOARD_MARGIN_BOTTOM_DP;
        if (screenHeightDp <= 0f) return height;
        float fraction = landscape ? MAX_HEIGHT_FRACTION_LANDSCAPE : MAX_HEIGHT_FRACTION_PORTRAIT;
        return Math.min(height, screenHeightDp * fraction);
    }

    /** The chin under the keyboard's last row, in dp, clamped to the store's range. */
    static int chinDp(int chinDp) {
        return Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING, chinDp));
    }

    // ---- Fit -----------------------------------------------------------------------------------

    /** The most of one axis the bands and the air between cards may take, so the opening keeps the rest. */
    static final float MAX_BANDS_SHARE = 0.8f;
    /** How far a stack of bands is squeezed at the most to keep it. */
    static final float MIN_FIT = 0.25f;

    /**
     * The factor to scale one axis's bands by so the pane's opening keeps a share of the frame. The
     * shape model caps nothing, and a short landscape screen with every bar and the keyboard on it
     * would leave the opening no height at all, so the canvas squeezes the bands before it asks.
     * One for any stack that already fits.
     *
     * @param frameExtent the frame's size along the axis
     * @param bandsTotal  every band's thickness along it, added
     * @param airTotal    the air between and around the cards along it, added
     */
    static float fitFactor(float frameExtent, float bandsTotal, float airTotal) {
        if (bandsTotal <= 0f || frameExtent <= 0f) return 1f;
        float room = MAX_BANDS_SHARE * frameExtent - Math.max(0f, airTotal);
        return Math.max(MIN_FIT, Math.min(1f, room / bandsTotal));
    }

    // ---- Scale ---------------------------------------------------------------------------------

    /**
     * Canvas pixels per real dp: the canvas's content width over the real screen's width in the
     * orientation it shows. The one factor every dp figure above is multiplied by.
     */
    static float canvasScale(float contentWidthPx, float screenWidthDp) {
        if (screenWidthDp <= 0f || contentWidthPx <= 0f) return 1f;
        return contentWidthPx / screenWidthDp;
    }

    /** A dp figure on the canvas, in pixels. */
    static float toCanvasPx(float dp, float canvasScale) {
        return dp * canvasScale;
    }

    // ---- Key rows ------------------------------------------------------------------------------

    /** What a key is, which is what it is drawn as. */
    enum KeyRole { KEY, SHIFT, BACKSPACE, SYMBOLS, FN, SPACE, ENTER }

    /** One key: its left edge and width in key units, where an ordinary key is 1 wide. */
    static final class KeyCell {
        final KeyRole role;
        final float x;
        final float w;

        KeyCell(@NonNull KeyRole role, float x, float w) {
            this.role = role;
            this.x = x;
            this.w = w;
        }
    }

    /** The width of a docked keyboard's rows, in key units. */
    static final float ROW_UNITS = 10f;

    /**
     * The docked keyboard's rows, top to bottom, across {@value #ROW_UNITS} key units: ten keys,
     * nine stepped in by half a key, seven between a shift and a backspace, and the bottom row
     * with its symbols, fn, wide space and enter.
     */
    @NonNull
    static List<List<KeyCell>> dockedKeyRows() {
        List<List<KeyCell>> rows = new ArrayList<>(KEYBOARD_ROWS);
        List<KeyCell> row = new ArrayList<>();
        for (int i = 0; i < 10; i++) row.add(new KeyCell(KeyRole.KEY, i, 1f));
        rows.add(row);
        row = new ArrayList<>();
        for (int i = 0; i < 9; i++) row.add(new KeyCell(KeyRole.KEY, 0.5f + i, 1f));
        rows.add(row);
        row = new ArrayList<>();
        row.add(new KeyCell(KeyRole.SHIFT, 0f, 1.5f));
        for (int i = 0; i < 7; i++) row.add(new KeyCell(KeyRole.KEY, 1.5f + i, 1f));
        row.add(new KeyCell(KeyRole.BACKSPACE, 8.5f, 1.5f));
        rows.add(row);
        row = new ArrayList<>();
        row.add(new KeyCell(KeyRole.SYMBOLS, 0f, 1.5f));
        row.add(new KeyCell(KeyRole.FN, 1.5f, 1f));
        row.add(new KeyCell(KeyRole.SPACE, 2.5f, 5f));
        row.add(new KeyCell(KeyRole.ENTER, 7.5f, 2.5f));
        rows.add(row);
        return rows;
    }

    /** A split keyboard: the same rows, parted at their midpoint into two halves. */
    static final class SplitRows {
        final List<List<KeyCell>> left;
        final List<List<KeyCell>> right;

        SplitRows(@NonNull List<List<KeyCell>> left, @NonNull List<List<KeyCell>> right) {
            this.left = left;
            this.right = right;
        }
    }

    /** The width of one half of a split keyboard, in key units. */
    static final float HALF_UNITS = ROW_UNITS / 2f;

    /**
     * The docked rows parted at the midpoint. A key wholly on one side goes with that side; a wide
     * key across the middle (the space bar) is cut into a part for each half; a narrow key across
     * it goes to the leading half. Each half's rows start from their outer edge (the left half's
     * at its left, the right half's ending at its right) with the half-key step of the second row
     * dropped, and a row that comes to more than a half's width is scaled down to fit it, so every
     * cell of every half lies inside {@code [0, HALF_UNITS]}.
     */
    @NonNull
    static SplitRows splitKeyRows() {
        float mid = ROW_UNITS / 2f;
        List<List<KeyCell>> left = new ArrayList<>();
        List<List<KeyCell>> right = new ArrayList<>();
        for (List<KeyCell> row : dockedKeyRows()) {
            List<KeyCell> l = new ArrayList<>();
            List<KeyCell> r = new ArrayList<>();
            for (KeyCell cell : row) {
                float end = cell.x + cell.w;
                if (end <= mid + EPS) {
                    l.add(cell);
                } else if (cell.x >= mid - EPS) {
                    r.add(cell);
                } else if (cell.w >= 2f) {
                    l.add(new KeyCell(cell.role, cell.x, mid - cell.x));
                    r.add(new KeyCell(cell.role, mid, end - mid));
                } else if (cell.x + cell.w / 2f <= mid + EPS) {
                    l.add(cell);
                } else {
                    r.add(cell);
                }
            }
            left.add(fitHalf(l, false));
            right.add(fitHalf(r, true));
        }
        return new SplitRows(left, right);
    }

    private static final float EPS = 0.001f;

    /** One half's row, moved to its outer edge and scaled down if it is wider than a half. */
    @NonNull
    private static List<KeyCell> fitHalf(@NonNull List<KeyCell> cells, boolean trailingHalf) {
        List<KeyCell> out = new ArrayList<>(cells.size());
        if (cells.isEmpty()) return out;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (KeyCell cell : cells) {
            min = Math.min(min, cell.x);
            max = Math.max(max, cell.x + cell.w);
        }
        float extent = max - min;
        float factor = extent > HALF_UNITS ? HALF_UNITS / extent : 1f;
        float fitted = extent * factor;
        float origin = trailingHalf ? HALF_UNITS - fitted : 0f;
        for (KeyCell cell : cells) {
            out.add(new KeyCell(cell.role, origin + (cell.x - min) * factor, cell.w * factor));
        }
        return out;
    }
}
