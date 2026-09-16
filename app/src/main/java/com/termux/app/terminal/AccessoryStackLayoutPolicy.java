package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.app.place.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccessoryStackLayoutPolicy {

    private AccessoryStackLayoutPolicy() {}

    // ---------------------------------------------------------------- the dock's own rows

    /**
     * The dock's own rows within one bottom-edge stack, outermost (against the dock's rim) first —
     * the order {@code EdgeStackPolicy.stack(layout, BOTTOM)} gives, with anything that is not a
     * dock row left out. Today that is the status bar, which stands above the whole dock on the
     * terminal's own height rather than on its glass.
     */
    @NonNull
    public static List<Element> dockRows(@NonNull List<Element> bottomStack) {
        List<Element> rows = new ArrayList<>(3);
        for (Element element : bottomStack) {
            if (element == Element.APPS || element == Element.AZ
                || element == Element.EXTRA_KEYS) rows.add(element);
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * Whether a dock row stands over the letters, which is what takes their crown away. Read off
     * the resolved order rather than off "the apps row is shown": re-ordered, it can be the extra
     * keys standing over the letters, or nothing at all with the apps row under them.
     */
    public static boolean rowOverAz(@NonNull List<Element> bottomStack) {
        List<Element> rows = dockRows(bottomStack);
        int az = rows.indexOf(Element.AZ);
        return az >= 0 && az < rows.size() - 1;
    }

    /** Whether a dock row stands under the letters, which is what takes their chin away. */
    public static boolean rowUnderAz(@NonNull List<Element> bottomStack) {
        return dockRows(bottomStack).indexOf(Element.AZ) > 0;
    }

    public static int computeCombinedHeight(int toolbarHeightPx, int appsBarHeightPx, int azRowHeightPx, int appsBarGapPx) {
        int toolbar = Math.max(0, toolbarHeightPx);
        int apps = Math.max(0, appsBarHeightPx);
        int az = Math.max(0, azRowHeightPx);
        int gap = Math.max(0, appsBarGapPx);
        return toolbar + apps + az + gap;
    }

    /** Applies the three independent row switches before summing the explicitly-sized stack. */
    public static int computeCombinedHeight(boolean appsRowEnabled, boolean azRowEnabled,
                                            boolean extraKeysRowEnabled, int appsBarHeightPx,
                                            int azRowHeightPx, int extraKeysRowHeightPx,
                                            int appsAzGapPx) {
        int apps = appsRowEnabled ? appsBarHeightPx : 0;
        int az = azRowEnabled ? azRowHeightPx : 0;
        int extraKeys = extraKeysRowEnabled ? extraKeysRowHeightPx : 0;
        int gap = appsRowEnabled && azRowEnabled ? appsAzGapPx : 0;
        return computeCombinedHeight(extraKeys, apps, az, gap);
    }

    public static int computeAppsBarInterRowGapPx(boolean azEnabled, float density, float iconScale) {
        if (!azEnabled)
            return 0;
        float safeDensity = Math.max(0f, density);
        float safeIconScale = Math.max(0f, iconScale);
        return Math.round(safeDensity * (3f + (Math.max(0f, safeIconScale - 1f) * 2f)));
    }

    /** Mirrors SuggestionBarView's icon-to-row fill curve so row sizing can grow without clipping. */
    public static float computeDockIconFillRatio(float iconScale) {
        float normalized = Math.max(0f, Math.min(1f, (iconScale - 1f) / 0.8f));
        return 0.68f + (normalized * 0.16f);
    }

    /** Piecewise-linear preset curve with endpoint extrapolation for legacy free-form values. */
    public static float interpolatePresetCurve(float progress, float[] progressPoints,
                                               float[] valuePoints) {
        if (progressPoints == null || valuePoints == null || progressPoints.length == 0
            || progressPoints.length != valuePoints.length) {
            throw new IllegalArgumentException("Preset progress/value arrays must have equal non-zero length");
        }
        if (progressPoints.length == 1) return valuePoints[0];
        int segment = 0;
        if (progress >= progressPoints[progressPoints.length - 1]) {
            segment = progressPoints.length - 2;
        } else {
            while (segment < progressPoints.length - 2 && progress > progressPoints[segment + 1]) {
                segment++;
            }
        }
        float startProgress = progressPoints[segment];
        float endProgress = progressPoints[segment + 1];
        if (Math.abs(endProgress - startProgress) < 0.000001f) return valuePoints[segment];
        float fraction = (progress - startProgress) / (endProgress - startProgress);
        return valuePoints[segment] + ((valuePoints[segment + 1] - valuePoints[segment]) * fraction);
    }

    public static int computePageIndicatorBandHeightPx(boolean azEnabled, float density) {
        if (!azEnabled)
            return 0;
        // Pure spacing between the icon row and the A-Z row (the page-indicator ticks draw at the
        // dock's top rim, not in this band) — kept tight so the gap matches the other inter-row gaps.
        return Math.round(Math.max(0f, density) * 3f);
    }

    /** The letters' own band: the glyphs and the 1dp of air the row draws them in. */
    private static final float AZ_ROW_LETTER_BAND_DP = 19f;

    /**
     * Dead space the A-Z row carries under its letters when it is the row on the dock's bottom rim,
     * so the letters are not hard against it and the row is not a 19dp strip to hit. With another
     * dock row under it that row is the one on the rim, and the A-Z row keeps its band alone.
     *
     * <p>{@code rowUnderAz} is read off the resolved bottom stack ({@link #rowUnderAz}), not off
     * the extra keys: whichever band the user put under the letters does the same job.
     */
    private static final float AZ_ROW_CHIN_DP = 10f;

    public static int computeAzRowChinPaddingPx(boolean azEnabled, boolean rowUnderAz,
                                                float density) {
        if (!azEnabled || rowUnderAz)
            return 0;
        return Math.round(Math.max(0f, density) * AZ_ROW_CHIN_DP);
    }

    /**
     * Air above the letters when the A-Z row is the dock's top row. With another dock row over it
     * that row's own bottom padding keeps the letters off the dock's top rim; without one the 19dp
     * band would stand 1dp under the rim, so the row carries the air itself.
     */
    private static final float AZ_ROW_CROWN_DP = 6f;

    public static int computeAzRowCrownPaddingPx(boolean azEnabled, boolean rowOverAz,
                                                 float density) {
        if (!azEnabled || rowOverAz)
            return 0;
        return Math.round(Math.max(0f, density) * AZ_ROW_CROWN_DP);
    }

    /**
     * The A-Z row's full height: the letter band, plus the crown over it when no dock row stands
     * above and the chin under it when the row sits on the dock's bottom rim. Both are drawn as
     * padding, so the letters keep their place in the band and the extra height is touchable air.
     */
    public static int computeAzRowHeightPx(boolean azEnabled, boolean rowOverAz,
                                           boolean rowUnderAz, float density) {
        if (!azEnabled)
            return 0;
        return Math.round(Math.max(0f, density) * AZ_ROW_LETTER_BAND_DP)
            + computeAzRowCrownPaddingPx(azEnabled, rowOverAz, density)
            + computeAzRowChinPaddingPx(azEnabled, rowUnderAz, density);
    }

    public static int computeTerminalToolbarHeightPx(int baseHeightPx, int rowCount, float scaleFactor) {
        int safeBaseHeight = Math.max(0, baseHeightPx);
        int safeRows = Math.max(0, rowCount);
        float safeScale = Math.max(0f, scaleFactor);
        return Math.round(safeBaseHeight * safeRows * safeScale);
    }
}
