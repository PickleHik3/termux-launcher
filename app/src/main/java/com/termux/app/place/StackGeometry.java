package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * The bottom stack as it is laid out, in the stack container's own pixels: the container, the
 * layers the rows sink with ({@link #HOST} .. {@link #AZ_LABEL}), the keyboard in its column, the
 * bands under it and the foot under the system pill. An end's rest reads it once
 * ({@link StackEnds#read}); the renderer fills one from the views before each frame, since a
 * clip is cut at what a view is about to be laid out at. Mutable and reused; nothing here
 * allocates after construction.
 */
public final class StackGeometry {

    /** The layers the rows sink into the keyboard with, in {@code CrossingViews}' order. */
    public static final int HOST = 0;
    public static final int AZ_UNDERLAY = 1;
    public static final int ROWS = 2;
    public static final int AZ_OVERLAY = 3;
    public static final int AZ_LABEL = 4;
    public static final int LAYER_COUNT = 5;

    public int displayWidthPx;
    public int displayHeightPx;

    /** The container's width, and the height it is about to be laid out at. */
    public int containerWidthPx;
    public int containerHeightPx;
    /** Padding under the keyboard's column. */
    public int stackPaddingBottomPx;

    /** Whether each sinking layer exists, its top in the container and its laid-out size. */
    public final boolean[] layerPresent = new boolean[LAYER_COUNT];
    public final int[] layerTopPx = new int[LAYER_COUNT];
    public final int[] layerWidthPx = new int[LAYER_COUNT];
    public final int[] layerHeightPx = new int[LAYER_COUNT];

    /** Whether the keyboard is in the tree and not GONE; whether it stands in the stack's column. */
    public boolean keyboardPresent;
    public boolean keyboardInColumn;
    /** The keyboard's top in the container, its laid-out width, and the height it is about to have. */
    public int keyboardTopPx;
    public int keyboardWidthPx;
    public int keyboardHeightPx;
    /** How far the column's bottom stands under the keyboard: bands, their air, its padding. */
    public int belowKeyboardInColumnPx;

    /** Whether bands stand under the keyboard, and where they are laid out in the container. */
    public boolean underShown;
    public int underTopPx;
    public int underBottomPx;

    /** The foot under the system pill, in the container (empty when the stack has none). */
    public int footTopPx;
    public int footBottomPx;

    /**
     * The flat body surface drawn on every page: its width, the height it is about to be laid out
     * at, and the room its layout stands above the window's bottom (the smallest of the pages'),
     * which the scene cuts it down from to the front page's.
     */
    public int bodyWidthPx;
    public int bodyHeightPx;
    public int bodyLaidOutRoomPx;

    public void set(@NonNull StackGeometry o) {
        displayWidthPx = o.displayWidthPx;
        displayHeightPx = o.displayHeightPx;
        containerWidthPx = o.containerWidthPx;
        containerHeightPx = o.containerHeightPx;
        stackPaddingBottomPx = o.stackPaddingBottomPx;
        for (int i = 0; i < LAYER_COUNT; i++) {
            layerPresent[i] = o.layerPresent[i];
            layerTopPx[i] = o.layerTopPx[i];
            layerWidthPx[i] = o.layerWidthPx[i];
            layerHeightPx[i] = o.layerHeightPx[i];
        }
        keyboardPresent = o.keyboardPresent;
        keyboardInColumn = o.keyboardInColumn;
        keyboardTopPx = o.keyboardTopPx;
        keyboardWidthPx = o.keyboardWidthPx;
        keyboardHeightPx = o.keyboardHeightPx;
        belowKeyboardInColumnPx = o.belowKeyboardInColumnPx;
        underShown = o.underShown;
        underTopPx = o.underTopPx;
        underBottomPx = o.underBottomPx;
        footTopPx = o.footTopPx;
        footBottomPx = o.footBottomPx;
        bodyWidthPx = o.bodyWidthPx;
        bodyHeightPx = o.bodyHeightPx;
        bodyLaidOutRoomPx = o.bodyLaidOutRoomPx;
    }
}