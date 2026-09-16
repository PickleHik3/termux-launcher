package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.app.launcher.paging.PageTickStrip;
import com.termux.app.place.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccessoryStackLayoutPolicy {

    private AccessoryStackLayoutPolicy() {}

    // ---------------------------------------------------------------- the dock's own bands

    /**
     * Whether a bottom status bar wears a sheet of glass of its own instead of standing on the
     * dock's. It does exactly when it is the band touching the canvas — the <em>last</em> of
     * {@code EdgeStackPolicy.stack(layout, BOTTOM)}, which counts outermost (against the screen's
     * rim) first — because there its sheet meets the terminal with no dock glass above it to
     * double. Ordered anywhere else it is a band between dock rows, and the dock's sheet is
     * already under it.
     *
     * <p>The innermost band is also the only place a bottom status bar has ever rendered, so that
     * arrangement comes out at the pixels it always had.
     */
    public static boolean statusKeepsOwnGlass(@NonNull List<Element> bottomStack) {
        int status = bottomStack.indexOf(Element.STATUS);
        return status >= 0 && status == bottomStack.size() - 1;
    }

    /**
     * The bands standing on the dock's own sheet of glass, outermost (against the dock's rim)
     * first — {@code EdgeStackPolicy.stack(layout, BOTTOM)}'s order, less a status bar that kept a
     * sheet of its own ({@link #statusKeepsOwnGlass}). It is what the hairlines are counted over,
     * what decides the letters' crown and chin, and what "the apps row stands alone" is read off.
     */
    @NonNull
    public static List<Element> plankBands(@NonNull List<Element> bottomStack) {
        boolean ownGlass = statusKeepsOwnGlass(bottomStack);
        List<Element> bands = new ArrayList<>(bottomStack.size());
        for (Element element : bottomStack) {
            if (ownGlass && element == Element.STATUS) continue;
            bands.add(element);
        }
        return Collections.unmodifiableList(bands);
    }

    /**
     * Whether another band stands over the letters on the dock's own sheet, which is what takes
     * their crown away. Read off the resolved order rather than off "the apps row is shown":
     * re-ordered it can be the extra keys standing over the letters, or the status bar, or nothing
     * at all with the apps row under them.
     *
     * <p>This pair is about the letters' <em>air</em> — which side of them already has a neighbour
     * to keep them off the dock's rim — and not about where the scrub's matches go. That is
     * {@code AzPreviewTargetPolicy}, which names the apps row wherever the stack put it; a band
     * over the letters here is as likely to be the extra keys as the row.
     */
    public static boolean rowOverAz(@NonNull List<Element> bottomStack) {
        List<Element> rows = plankBands(bottomStack);
        int az = rows.indexOf(Element.AZ);
        return az >= 0 && az < rows.size() - 1;
    }

    /** Whether a band stands under the letters, which is what takes their chin away. */
    public static boolean rowUnderAz(@NonNull List<Element> bottomStack) {
        return plankBands(bottomStack).indexOf(Element.AZ) > 0;
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

    public static int computePageIndicatorBandHeightPx(boolean appsRowEnabled, float density) {
        if (!appsRowEnabled)
            return 0;
        // The band the page ticks stand in, which is what it was always named for: the dock's row
        // carries the same strip every other edge does instead of the FX layer painting a second
        // set over the glass. It is the row's own furniture, so it goes when the row does.
        return PageTickStrip.bandPx(density);
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
