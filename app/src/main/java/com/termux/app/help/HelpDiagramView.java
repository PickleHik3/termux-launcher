package com.termux.app.help;

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.VisibleForTesting;
import com.termux.app.ReducedMotion;

/**
 * One topic's drawn diagram, shown on its page under the instruction.
 *
 * <p>A card the width of the page and as tall as the drawable's own shape, with the panel's corner
 * radius. The drawable is a vector 360dp wide whose colours are theme attributes, so it is dressed
 * by the theme the page is drawn in and sharp at any size.
 *
 * <p>Only one diagram moves: Mouse mode's is an animated vector that loops while the page is on
 * screen. It starts when the view is attached and visible, stops the moment it is not, and is
 * never started when the phone is set to play no animations, when the card holds the first frame.
 *
 * <p>A drawable that cannot be inflated, such as one whose theme attribute the current theme does
 * not define, takes the card away with it, and the page reads as it would without a diagram.
 */
final class HelpDiagramView extends FrameLayout {

    private final String topicId;
    private final ImageView image;
    private final boolean animated;
    private final boolean reducedMotion;
    /** The drawable's own shape, which the card keeps at any width. */
    private int sourceWidth;
    private int sourceHeight;
    /** True between a start and a stop: the loop restarts itself only while this holds. */
    private boolean playing;
    private final Animatable2.AnimationCallback loop = new Animatable2.AnimationCallback() {
        @Override public void onAnimationEnd(Drawable drawable) {
            if (!playing) return;
            // Posted, so the restart does not happen inside the animator's own end callback.
            post(HelpDiagramView.this::restart);
        }
    };

    HelpDiagramView(Context context, HelpStyle style, String topicId, CharSequence description) {
        super(context);
        this.topicId = topicId;
        this.animated = HelpDiagrams.isAnimated(topicId);
        this.reducedMotion = ReducedMotion.isEnabled(context);
        setBackground(style.diagramCard());
        setClipToOutline(true);
        setContentDescription(description == null || description.length() == 0 ? null : description);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int resource = HelpDiagrams.forTopic(topicId);
        Drawable drawable = null;
        if (resource != 0) {
            try {
                drawable = context.getDrawable(resource);
            } catch (RuntimeException unresolved) {
                HelpLog.d("help diagram " + topicId + ": no diagram on the page (" + unresolved + ")");
            }
        }
        if (drawable == null) {
            setVisibility(GONE);
            return;
        }
        sourceWidth = drawable.getIntrinsicWidth();
        sourceHeight = drawable.getIntrinsicHeight();
        image.setImageDrawable(drawable);
        if (animated && drawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) drawable).registerAnimationCallback(loop);
        }
    }

    /** The card is the page's width and the drawing's shape, so the page never reflows. */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        int height = Math.max(1, Math.round(width * (float) sourceHeight / sourceWidth));
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshPlayback();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        refreshPlayback();
    }

    private void refreshPlayback() {
        if (animated && !reducedMotion && isAttachedToWindow() && isShown()) start();
        else stop();
    }

    private void start() {
        Drawable drawable = image.getDrawable();
        if (!(drawable instanceof AnimatedVectorDrawable) || playing) return;
        playing = true;
        ((AnimatedVectorDrawable) drawable).start();
    }

    private void restart() {
        Drawable drawable = image.getDrawable();
        if (!playing || !(drawable instanceof AnimatedVectorDrawable)) return;
        ((AnimatedVectorDrawable) drawable).start();
    }

    private void stop() {
        playing = false;
        Drawable drawable = image.getDrawable();
        if (drawable instanceof AnimatedVectorDrawable) {
            AnimatedVectorDrawable vector = (AnimatedVectorDrawable) drawable;
            if (vector.isRunning()) vector.stop();
        }
    }

    /** Stop the loop. Safe at any time, and again afterwards; showing the page starts it again. */
    void release() {
        stop();
    }

    @VisibleForTesting
    boolean isPlaying() {
        return playing;
    }

    @VisibleForTesting
    boolean isAnimated() {
        return animated;
    }

    @VisibleForTesting
    boolean hasDiagram() {
        return image.getDrawable() != null;
    }

    @VisibleForTesting
    String topicId() {
        return topicId;
    }
}
