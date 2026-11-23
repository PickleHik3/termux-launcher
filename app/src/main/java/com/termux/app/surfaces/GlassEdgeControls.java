package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.GlassMaterial;

/**
 * The Depth slider over the stored thickness, as pure functions: {@link GlassMaterial} owns the
 * numbers (position 60 is the realistic 27 dp slab; the top is a 45 dp slab of a higher index).
 *
 * <p>Depth reads the stored thickness back as the position nearest it; where several positions
 * write one thickness, the one the user left the thumb on is kept while it still writes exactly
 * the stored thickness ({@link #read}). Reading never writes.</p>
 */
public final class GlassEdgeControls {

    private GlassEdgeControls() {}

    /** The slider's range. */
    public static final int MIN = GlassMaterial.MIN;
    public static final int MAX = GlassMaterial.MAX;

    /** No remembered position: the slider reads where its stored thickness puts it. */
    public static final int NONE = -1;

    /** The thickness a Depth position writes, in dp. */
    public static int bendForDepth(int depth) {
        return GlassMaterial.bendForDepth(depth);
    }

    /** Where Depth stands for a stored thickness: the lowest position that writes it. */
    public static int depthFor(int bend) {
        return GlassMaterial.depthForBend(bend);
    }

    /**
     * Where Depth stands, keeping a {@code remembered} position while it still writes exactly
     * {@code bend}: a flat stretch of the curve then reads back where the thumb was left.
     */
    public static int read(int bend, int remembered) {
        return remembered >= MIN && remembered <= MAX && bendForDepth(remembered) == bend
            ? remembered : depthFor(bend);
    }
}
