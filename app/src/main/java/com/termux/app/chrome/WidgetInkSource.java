package com.termux.app.chrome;

import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * What a Home widget asks the chrome ink engine, through the Look it is fed
 * ({@link LookGlassStyle}): the page's one ink polarity, whether the shared frame its glass is cut
 * from is ready, and its own answer on the {@link GlassBackdropCache.Band#WIDGET} band. Asked on a
 * dress pass, never per frame; every answer is memoised in {@link ChromeInk}.
 */
public interface WidgetInkSource {

    /** The polarity the dock and the bars are drawn in; the widget's card follows it. */
    @NonNull ChromeInk.Polarity widgetPolarity();

    /**
     * Whether the shared blur frame the Look's glass is drawn from is resident. A widget whose
     * frame is not ready wears its solid card until the frame lands.
     */
    boolean widgetFrameReady();

    /** {@link ChromeInk#widget}. */
    @NonNull OnGlass.Resolution widgetInk(@NonNull Rect rootRect, @ColorInt int body,
                                          @ColorInt int veilColor, @ColorInt int textInk,
                                          @ColorInt int graphicInk);

    /** {@link ChromeInk#widgetWallpaper}. */
    @ColorInt int widgetWallpaper(@NonNull Rect rootRect);

    /**
     * {@link ChromeInk#wallpaperSample}: the wallpaper under a small rect on the page (a Home
     * label, the page dots), read afresh, {@link android.graphics.Color#TRANSPARENT} while
     * nothing can be read. Not memoised, so the widgets' answers are not crowded out; the caller
     * keeps its own answer against {@link #wallpaperGeneration}.
     */
    @ColorInt
    default int wallpaperSample(@NonNull Rect rootRect) {
        return android.graphics.Color.TRANSPARENT;
    }

    /** {@link ChromeInk#wallpaperFallback}: what to assume where {@link #wallpaperSample} read nothing. */
    @ColorInt
    default int wallpaperFallback() {
        return android.graphics.Color.TRANSPARENT;
    }

    /** {@link ChromeInk#wallpaperGeneration}: moves whenever every sample is stale. */
    default int wallpaperGeneration() {
        return 0;
    }
}
