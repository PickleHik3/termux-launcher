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
}
