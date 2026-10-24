package com.termux.app.dock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One surface the Peek glass reflection is drawn on ({@link PeekGlassReflection}): the dock's
 * glass host, over its tint, and the strip under the gesture pill that continues the card. The
 * reflection is held once, in the host's coordinates; each view draws it shifted by where its own
 * origin stands in them ({@link #setOrigin}), and clips to its own bounds as any view does, so the
 * card's cut on the host and the strip's bounds below it are the reflection's too.
 *
 * <p>It draws nothing of its own and takes no touch; it is redrawn only when the reflection
 * changes.</p>
 */
@SuppressLint("ViewConstructor")
public final class PeekReflectionView extends View {

    @NonNull private final PeekGlassReflection mSource;
    private float mOriginX;
    private float mOriginY;

    PeekReflectionView(@NonNull Context context, @NonNull PeekGlassReflection source) {
        super(context);
        mSource = source;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Where this view's origin stands in the glass host's coordinates. Redrawn only on a change. */
    void setOrigin(float x, float y) {
        if (Float.compare(mOriginX, x) == 0 && Float.compare(mOriginY, y) == 0) return;
        mOriginX = x;
        mOriginY = y;
        invalidate();
    }

    float originX() {
        return mOriginX;
    }

    float originY() {
        return mOriginY;
    }

    /** The reflection this view draws. */
    @NonNull
    PeekGlassReflection source() {
        return mSource;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        mSource.drawOn(canvas, mOriginX, mOriginY);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Whether {@code view} is a reflection view of {@code source}. */
    static boolean isOf(@Nullable View view, @NonNull PeekGlassReflection source) {
        return view instanceof PeekReflectionView && ((PeekReflectionView) view).mSource == source;
    }
}
