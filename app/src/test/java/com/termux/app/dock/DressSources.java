package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.SharedFrameDrawable;
import com.termux.app.place.RestCards;

/** A card source that answers nothing: the dress tests fill in the parts they read. */
abstract class DressSources implements KeysCardDress.Source {

    boolean crossing;
    @Nullable RestCards.Card card;

    @Override public boolean crossing() { return crossing; }
    @Override public boolean blurWanted(@NonNull ChromeSpec state) { return false; }
    @Override public boolean pictureUp(@NonNull ImageView backdrop) { return false; }
    @Nullable @Override public Drawable attachLive(@NonNull ImageView backdrop, int radiusDp) {
        return null;
    }
    @Override public void releaseLive(@NonNull ImageView backdrop) { }
    @Nullable @Override public Bitmap blurFrame(int radiusDp, @NonNull Rect outRect) { return null; }
    @Nullable @Override public ColorFilter frostFilter(@Nullable View surface) { return null; }
    @Override public boolean managedSource() { return false; }
    @Override public boolean glassDragPreview() { return false; }
    @Nullable @Override public GlassRefraction.Look fancierLook() { return null; }
    @Override public int footPx() { return 0; }
    @Nullable @Override public Drawable plate(float barAlpha) { return null; }
    @Override public int outlineColor() { return 0; }
    @Nullable @Override public GroundUnderGlass ground(@Nullable GroundUnderGlass held,
                                                       @NonNull ImageView backdrop) {
        return null;
    }
    @Override public void installPicture(@NonNull ImageView backdrop,
                                         @Nullable SharedFrameDrawable installed,
                                         @NonNull Bitmap frame, @NonNull Rect frameRect,
                                         int radiusDp, @Nullable Drawable live) { }

    public boolean standsDownOnFrame() { return false; }

    @Nullable public RestCards.Card card() { return card; }

    /** The foot's source. */
    static final class Foot extends DressSources implements FootDress.Source { }

    /** The Terminal host's source, with its Peek cut. */
    static final class Terminal extends DressSources implements TerminalHostDress.Source {
        @Nullable PeekGlassGeometry.Card peek;

        @Nullable @Override public PeekGlassGeometry.Card peekCut() { return peek; }
        @Nullable @Override public PeekGlassReflection.Row peekRow() { return null; }
        @Override public float peekReflectionAlpha() { return 0f; }
    }
}
