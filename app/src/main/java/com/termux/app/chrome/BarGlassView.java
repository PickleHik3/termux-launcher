package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.window.WindowLayer;

/**
 * A bar's glass: the status strip, the window bar's main card and its second card. It draws the
 * stored pre-blurred wallpaper frame (the frost, {@link WallpaperFrostPainter}) at its own bounds
 * first, then its background, which is the bar's tint and rim laid over the frost. It used to
 * stand beside an ImageView that drew the frost; now one view is both, and registers one entry
 * with the window's glass service ({@link SlabAim}), whose frame hook redraws it when its place or
 * the wallpaper's pan moved.
 *
 * <p>The frost draws only while this view draws, so hiding the view hides it, and its alpha
 * fades both.</p>
 */
public class BarGlassView extends View {

    @Nullable private Drawable mFrost;
    /** The frost a crossing arrives at when it differs from {@link #mFrost}; null otherwise. */
    @Nullable private Drawable mArrivingFrost;
    /** How much of the arriving frost shows, 0..1. */
    private float mFrostShare;
    @Nullable private ColorFilter mFrostFilter;
    private boolean mFrostShown;
    /** Made on each attach, once the id that names the entry is set. */
    @Nullable private SlabAim mAim;
    @Nullable private WallpaperParallax mParallax;

    public BarGlassView(@NonNull Context context) {
        this(context, null);
    }

    public BarGlassView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** The id's resource entry name, or a name of the view's own when it has none. */
    @NonNull
    private String elementName() {
        int id = getId();
        if (id != View.NO_ID) {
            try {
                return getContext().getResources().getResourceEntryName(id);
            } catch (android.content.res.Resources.NotFoundException ignored) {
                // Fall through to the identity name.
            }
        }
        return "bar@" + Integer.toHexString(System.identityHashCode(this));
    }

    /** The wallpaper pan the frost follows; null for none. */
    public void setParallax(@Nullable WallpaperParallax parallax) {
        mParallax = parallax;
        if (mAim != null) mAim.setParallax(parallax);
    }

    /** Sets the frost drawable, or null for none. */
    public void setFrost(@Nullable Drawable frost) {
        if (frost == mFrost) return;
        if (mFrost != null) mFrost.setCallback(null);
        mFrost = frost;
        if (frost != null) {
            frost.setCallback(this);
            frost.setColorFilter(mFrostFilter);
        }
        invalidate();
    }

    @Nullable
    public Drawable getFrost() {
        return mFrost;
    }

    /** The filter the frost is drawn through, restated on the drawable now and on a new frost. */
    public void setFrostColorFilter(@Nullable ColorFilter filter) {
        if (filter == mFrostFilter) return;
        mFrostFilter = filter;
        if (mFrost != null) mFrost.setColorFilter(filter);
        invalidate();
    }

    /** Whether the frost draws; a frost that is set but not shown is kept, not drawn. */
    public void setFrostShown(boolean shown) {
        if (shown == mFrostShown) return;
        mFrostShown = shown;
        invalidate();
    }

    public boolean frostShown() {
        return mFrostShown;
    }

    /**
     * The frost the other half's look blurs to, laid over the frost as {@link #setFrostShare}
     * rises; null for none. Dropped, not promoted, by {@link #settleFrost} below half.
     */
    public void setArrivingFrost(@Nullable Drawable frost) {
        if (frost == mArrivingFrost) return;
        if (mArrivingFrost != null) mArrivingFrost.setCallback(null);
        mArrivingFrost = frost;
        if (frost != null) {
            frost.setCallback(this);
            frost.setColorFilter(mFrostFilter);
        }
        invalidate();
    }

    @Nullable
    public Drawable getArrivingFrost() {
        return mArrivingFrost;
    }

    /** How much of the arriving frost shows; invalidates only on a change. */
    public void setFrostShare(float share) {
        float clamped = share < 0f ? 0f : Math.min(share, 1f);
        if (clamped == mFrostShare) return;
        mFrostShare = clamped;
        if (mArrivingFrost != null) invalidate();
    }

    public float frostShare() {
        return mFrostShare;
    }

    /**
     * The crossing is over: the arriving frost becomes the frost at half or more, else it is
     * dropped. No frost is left holding two. Whether the arriving one was promoted.
     */
    public boolean settleFrost() {
        Drawable arriving = mArrivingFrost;
        float share = mFrostShare;
        mFrostShare = 0f;
        if (arriving == null) return false;
        mArrivingFrost = null;
        if (share >= 0.5f) {
            Drawable leaving = mFrost;
            if (leaving != null) leaving.setCallback(null);
            mFrost = arriving;
            arriving.setAlpha(255);
            invalidate();
            return true;
        }
        arriving.setCallback(null);
        invalidate();
        return false;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mFrost || who == mArrivingFrost || super.verifyDrawable(who);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Drawable frost = mFrost;
        Drawable arriving = mArrivingFrost;
        float share = mFrostShare;
        if (mFrostShown) {
            // The leaving frost stays whole under the arriving one, which comes in at the share:
            // an opaque frame keeps full coverage all the way (leaving*(1-s) + arriving*s), where
            // two fades over each other would dip to 1 - s(1-s) and show the wall through.
            if (frost != null && (arriving == null || share < 1f)) {
                frost.setBounds(0, 0, getWidth(), getHeight());
                frost.draw(canvas);
            }
            if (arriving != null && share > 0f) {
                arriving.setBounds(0, 0, getWidth(), getHeight());
                arriving.setAlpha(Math.round(255f * share));
                arriving.draw(canvas);
            }
        }
        super.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAim = new SlabAim(this, elementName(), WindowLayer.CHROME, GlassEntry.PRIORITY_DOCK);
        mAim.setParallax(mParallax);
        mAim.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAim != null) mAim.detach();
        super.onDetachedFromWindow();
    }
}
