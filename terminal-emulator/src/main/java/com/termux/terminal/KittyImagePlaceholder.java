package com.termux.terminal;

import android.graphics.Bitmap;

/** Reusable renderer result for one stored kitty Unicode-placeholder virtual placement. */
public final class KittyImagePlaceholder {
    public Bitmap bitmap;
    public int sourceX;
    public int sourceY;
    public int sourceWidth;
    public int sourceHeight;
    public int columns;
    public int rows;
    /**
     * Which pixels this answer was drawn from, monotonically increasing per image and never 0.
     *
     * <p>Everything above can be identical across two animation frames — a frame flip replaces the
     * bitmap object, but a frame composed in place does not, and the crop never moves — so this is
     * the only field a renderer can compare to decide whether the cell has to be drawn again. Ask
     * {@link TerminalEmulator#getKittyImageGeneration} for the same number without a lookup that
     * fills this struct; it answers 0 for an image that is not stored.</p>
     */
    public long generation;
}
