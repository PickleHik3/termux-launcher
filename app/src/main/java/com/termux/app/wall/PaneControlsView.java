package com.termux.app.wall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.CornerTabGeometry;
import com.termux.app.chrome.CornerZones;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The tab a wall page drops from the corner that was tapped: the same fill, stroke and motion a
 * terminal pane's tab has, so every place on the wall answers a corner tap the same way. It slides
 * out of whichever edge that corner is on — down from a top corner, up from a bottom one — and
 * lines up with whichever side it is on.
 *
 * <p>What the tab holds is the page's own business — the Display page offers power and its
 * settings, the Widgets page offers its settings and an edit pencil, and while a widget is being
 * edited the same corner reads out the grid's size — so the buttons are handed in as
 * {@link Action}s and this view knows only how to lay them out, draw them and say which one a
 * finger landed on. The page keeps the touches: the view is never clickable, so it cannot stand
 * between a finger and whatever the page is drawing underneath.
 *
 * <p>A button is a 30dp square with a Nerd Font glyph or a mark drawn by hand, or a strip as wide
 * as its own text. Both are roomier than a pane's tab on purpose — these sit over a live picture
 * with no other chrome to steady the thumb, and the 22dp pair was missed as often as hit.
 *
 * <p>The frame the tab hangs off is the view's own bounds by default, which is what a page wants:
 * the view is laid over the page and the tab lands on the page's own corner. A terminal pane is
 * not a view of its own — one overlay draws the tabs of every pane on the wall — so that caller
 * hands in a {@link FrameSource} instead, and the tab is laid out and drawn inside whichever pane
 * it names, in the overlay's own coordinates.
 */
public final class PaneControlsView extends View {

    /**
     * The tab's one material, the same on every screen: the theme's panel colour as a scrim,
     * over the app's shared wallpaper blur when it has one. Two strengths, both fixed — the tab
     * does not follow the page's own tint, blur or grain. Following them put a pane's film grain
     * on a 40dp tab, where it read as static (pong, 2026-09-20), and a pane's faint tint left the
     * buttons on bare terminal text.
     */
    /** The scrim's alpha, out of 255, over the wallpaper blur: enough to read on any picture. */
    public static final int SCRIM_ON_FROST_ALPHA = 184;
    /** The scrim's alpha, out of 255, standing alone: what the tab was always filled with. */
    public static final int SCRIM_ALPHA = 232;

    /** Told which button was run; the ids are the page's own. */
    public interface Listener {
        void onPaneControlAction(int id);
    }

    public static final int ACTION_NONE = -1;

    /** How deep the tab is once it is fully out. */
    private static final float TAB_HEIGHT_DP = 32f;

    /** Drawn in the theme's primary colour, as all but two of the buttons are. */
    public static final int TINT_PRIMARY = 0;
    /** Drawn in the tertiary colour: the pane-move grip, which is a handle rather than an action. */
    public static final int TINT_TERTIARY = 1;
    /** Drawn in the error colour, as a close is. */
    public static final int TINT_ERROR = 2;

    /**
     * A button's mark, for the few no font carries: the pane-move grip, the maximise box and the
     * close cross. The paint arrives already stroked and coloured for how far out the tab is, so a
     * mark draws its lines and leaves the colour alone.
     */
    public interface Mark {
        void draw(@NonNull Canvas canvas, @NonNull RectF button, @NonNull Paint paint,
                  float density);
    }

    /**
     * One button of the tab: an id the page knows, and either a glyph, a short label, or a mark it
     * draws itself.
     */
    public static final class Action {
        final int id;
        @NonNull final String text;
        final boolean isGlyph;
        @Nullable final Mark mark;
        final int tint;

        private Action(int id, @NonNull String text, boolean isGlyph, @Nullable Mark mark,
                       int tint) {
            this.id = id;
            this.text = text;
            this.isGlyph = isGlyph;
            this.mark = mark;
            this.tint = tint;
        }

        /** A Nerd Font glyph in a square button. */
        @NonNull
        public static Action glyph(int id, @NonNull String glyph) {
            return new Action(id, glyph, true, null, TINT_PRIMARY);
        }

        /** A short read-out — the grid's size, or the help question mark — as wide as its text. */
        @NonNull
        public static Action label(int id, @NonNull String text) {
            return new Action(id, text, false, null, TINT_PRIMARY);
        }

        /** A hand-drawn mark, in a square button the size a glyph's would be. */
        @NonNull
        public static Action drawn(int id, @NonNull Mark mark) {
            return drawn(id, mark, TINT_PRIMARY);
        }

        /** As above, in one of the tab's other colours. */
        @NonNull
        public static Action drawn(int id, @NonNull Mark mark, int tint) {
            return new Action(id, "", false, mark, tint);
        }
    }

    /**
     * The frame a tab hangs off when it is not this view's own bounds: where it is, and the shape
     * it is drawn with. One instance is filled over and over, so nothing reads it after the call
     * that filled it.
     */
    public static final class Frame {
        /** The frame's bounds, in this view's coordinates. */
        public final RectF bounds = new RectF();
        /** The radius its corners are drawn at, as the user set it; 0 for a square frame. */
        public float radiusPx;
        /** The border it paints — the line the tab lines up inside, 0 when it paints none. */
        public float borderPx;
    }

    /** Where the tab's frame is now; asked afresh every time the tab is laid out or drawn. */
    public interface FrameSource {
        /** Fill {@code out}; false when there is no frame to hang a tab off any more. */
        boolean fillFrame(@NonNull Frame out);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The marks' own paint, so a hand-drawn button cannot disturb the tab's fill and stroke. */
    private final Paint mMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mTab = new RectF();
    private final List<Action> mActions = new ArrayList<>();
    /** The frame the tab is laid out inside: this view's bounds, or what a frame source says. */
    private final RectF mBounds = new RectF();
    /** Scratch for the shape the tab is painted through; never allocated per frame. */
    private final RectF mClip = new RectF();
    @Nullable private FrameSource mFrameSource;
    private final Frame mFrame = new Frame();
    /** One hit rectangle per action, in this view's coordinates; recomputed with the geometry. */
    private RectF[] mButtons = new RectF[0];
    /** Each action's asked-for width, in the order they are drawn. */
    private float[] mWidths = new float[0];
    /** The ids drawn in the error colour rather than the primary one. */
    private final List<Integer> mAlerted = new ArrayList<>();
    @Nullable private ValueAnimator mAnimator;
    /** The radius the page is drawn at; 0 until a page says otherwise. */
    private float mPaneRadiusPx;
    /** The border the page paints — the line the tab lines up inside, 0 when it paints none. */
    private float mPaneBorderPx;
    /**
     * The tab's glass: the app's shared pre-blurred wallpaper frame, shown through the tab's
     * shape at the tab's position on screen, under the scrim. Null while the app has no blur
     * frame, and the scrim then stands alone.
     */
    @Nullable private Bitmap mFrostFrame;
    @Nullable private BitmapShader mFrostShader;
    private final Paint mFrostPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix mFrostMatrix = new Matrix();
    private final Rect mFrostRect = new Rect();
    private final int[] mLocation = new int[2];
    /** The wallpaper's live x-offset the frost follows, or null while nothing pans. */
    @Nullable private com.termux.app.chrome.WallpaperParallax mParallax;
    private final int[] mRootLocation = new int[2];
    @Nullable private ColorFilter mFrostFilter;
    /** Scratch for the tab's path points; never allocated per frame. */
    private final float[] mPathPoints = new float[CornerTabGeometry.PATH_POINTS * 2];
    /** The corner it comes out of; {@link CornerZones#NONE} until a page or the default says. */
    private int mCorner = CornerZones.NONE;
    private float mProgress;
    private boolean mShown;
    /** Sliding back in; cleared when the slide lands or a show() turns it round. */
    private boolean mRetracting;
    @Nullable private Listener mListener;

    public PaneControlsView(@NonNull Context context) {
        super(context);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mGlyphPaint.setTextSize(dp(14));
        mLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTextSize(dp(12));
        mMarkPaint.setStyle(Paint.Style.STROKE);
        mMarkPaint.setStrokeCap(Paint.Cap.ROUND);
        mMarkPaint.setStrokeWidth(dp(1.35f));
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * The shape the page draws itself with: the radius of its corners and the width of the border
     * it paints on them. Everything the tab lines up against comes from these two — it sits inside
     * the border's own line rather than against the bounding box behind it, and starts far enough
     * along that edge that the corner arc never cuts it. A page that paints no border passes 0 and
     * the tab lands on the bounding box, as a square page's always has.
     */
    public void setPaneBorder(float radiusPx, float strokePx) {
        float radius = Math.max(0f, radiusPx);
        float stroke = Math.max(0f, strokePx);
        if (mPaneRadiusPx == radius && mPaneBorderPx == stroke) return;
        mPaneRadiusPx = radius;
        mPaneBorderPx = stroke;
        invalidate();
    }

    /**
     * The blur the tab shows through its scrim: the app's shared pre-blurred wallpaper frame
     * ({@code frameRect} says where it lies on screen) and the frost's vibrancy filter. Pass a
     * null frame while the app has none — blur off, or no still picture — and the scrim stands
     * alone. Compared by identity, as the pane's own slab does: a re-dress with what the tab
     * already wears costs nothing.
     */
    public void setPaneGlass(@Nullable Bitmap frame, @NonNull Rect frameRect,
                             @Nullable ColorFilter frostFilter) {
        setPaneGlass(frame, frameRect, frostFilter, null);
    }

    /**
     * {@link #setPaneGlass(Bitmap, Rect, ColorFilter)} that also follows the wallpaper's
     * parallax, sampling the frame {@code parallax.offsetPx()} further along on every draw; null
     * while nothing pans.
     */
    public void setPaneGlass(@Nullable Bitmap frame, @NonNull Rect frameRect,
                             @Nullable ColorFilter frostFilter,
                             @Nullable com.termux.app.chrome.WallpaperParallax parallax) {
        Bitmap live = frame != null && !frame.isRecycled() ? frame : null;
        if (live == mFrostFrame && mFrostFilter == frostFilter && mFrostRect.equals(frameRect)
            && mParallax == parallax) {
            return;
        }
        mParallax = parallax;
        mFrostFrame = live;
        // CLAMP and a shader, as every other glass surface here draws the same frame: it does not
        // always reach the screen's full width, and a plain drawBitmap left a sharp strip.
        mFrostShader = live == null
            ? null : new BitmapShader(live, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mFrostPaint.setShader(mFrostShader);
        mFrostPaint.setColorFilter(frostFilter);
        mFrostFilter = frostFilter;
        mFrostRect.set(frameRect);
        invalidate();
    }

    /** True while the tab shows the wallpaper blur under its scrim. */
    public boolean hasPaneGlass() {
        return mFrostShader != null;
    }

    /**
     * Where the tab's frame is, for a caller whose frame is not this view. The source is asked
     * afresh on every layout and every frame drawn, so a pane that moves under a tab already out
     * carries it along; it also answers the shape, which {@link #setPaneBorder} would otherwise
     * have to be told again every time the pane changed. Null puts the tab back on this view's
     * own bounds, which is where a page wants it.
     */
    public void setFrameSource(@Nullable FrameSource source) {
        mFrameSource = source;
        invalidate();
    }

    /**
     * Read where the tab's frame is now into {@link #mBounds}; false when there is none, which is
     * a pane that has gone while its tab was out.
     */
    private boolean readFrame() {
        if (mFrameSource == null) {
            mBounds.set(0f, 0f, getWidth(), getHeight());
            return getWidth() > 0 && getHeight() > 0;
        }
        if (!mFrameSource.fillFrame(mFrame)) {
            mBounds.setEmpty();
            return false;
        }
        mBounds.set(mFrame.bounds);
        mPaneRadiusPx = Math.max(0f, mFrame.radiusPx);
        mPaneBorderPx = Math.max(0f, mFrame.borderPx);
        return mBounds.width() > 0f && mBounds.height() > 0f;
    }

    /** The corner the tab is out of, or coming out of. */
    public int corner() {
        return mCorner == CornerZones.NONE ? defaultCorner() : mCorner;
    }

    /**
     * Where a tab nobody aimed goes: the top-trailing corner, which is where every place on the
     * wall has always carried its controls.
     */
    private int defaultCorner() {
        return CornerZones.corner(true, false, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    /**
     * The buttons this tab carries, in reading order. Replacing them keeps whatever the tab is
     * doing — a page that swaps the pair for a read-out while the tab is out does not have to put
     * it away first — and an id that is no longer here loses its alert with it.
     */
    public void setActions(@NonNull Action... actions) {
        setActions(Arrays.asList(actions));
    }

    /** As above, for a caller that builds its buttons from the state it is in. */
    public void setActions(@NonNull Collection<Action> actions) {
        mActions.clear();
        mActions.addAll(actions);
        mButtons = new RectF[mActions.size()];
        mWidths = new float[mActions.size()];
        for (int i = 0; i < mActions.size(); i++) mButtons[i] = new RectF();
        for (int i = mAlerted.size() - 1; i >= 0; i--) {
            if (indexOf(mAlerted.get(i)) < 0) mAlerted.remove(i);
        }
        invalidate();
    }

    /**
     * Whether one button reads as an alert: the error colour, as a close does. The Display page's
     * power glyph turns while a display is running.
     */
    public void setActionAlert(int id, boolean alert) {
        boolean was = mAlerted.contains(id);
        if (was == alert) return;
        if (alert) mAlerted.add(id);
        else mAlerted.remove(Integer.valueOf(id));
        invalidate();
    }

    /** Out, or on its way out: a retracting tab is already gone to a tap, and show() brings it back. */
    public boolean isControlsShown() {
        return mShown && !mRetracting;
    }

    /**
     * Slide out at the top-trailing corner, for a tab nobody aimed — a mode's own chrome coming
     * out on its own, rather than a finger asking for it somewhere in particular.
     */
    public void show() {
        show(defaultCorner());
    }

    /**
     * Slide out at one corner. A tab still retracting turns round here rather than staying put -
     * the pencil puts the pair away and, in the same touch, editing asks for the grid's size in
     * its place. A tab already out at another corner starts again from the new one, so it always
     * comes out of the corner the finger asked at.
     */
    public void show(int corner) {
        boolean moved = corner() != corner;
        mCorner = corner;
        if (mShown && !mRetracting) {
            if (!moved) return;
            mProgress = 0f;
        }
        animateTo(1f, false);
        mShown = true;
        mRetracting = false;
    }

    /**
     * Out at once, with no motion: chrome that belongs to a mode the user is already in, rather
     * than a tab a finger asked for. A maximised pane's tab is re-asserted this way on every
     * render, and animating it there would make it flicker out and back on each one.
     */
    public void showNow(int corner) {
        if (mAnimator != null) mAnimator.cancel();
        mCorner = corner;
        mProgress = 1f;
        mShown = true;
        mRetracting = false;
        invalidate();
    }

    public void dismiss() {
        if (!mShown || mRetracting) return;
        animateTo(0f, true);
        mRetracting = true;
    }

    /**
     * Gone at once: the tab is making way for something that covers it whole, so sliding it out
     * from under the new thing would only be seen as a glitch at its edge.
     */
    public void dismissNow() {
        if (mAnimator != null) mAnimator.cancel();
        mProgress = 0f;
        mShown = false;
        mRetracting = false;
        mCorner = CornerZones.NONE;
        invalidate();
    }

    /** Run one button; false when the id is not on this tab. */
    public boolean activate(int id) {
        if (mListener == null || indexOf(id) < 0) return false;
        mListener.onPaneControlAction(id);
        return true;
    }

    /** The button at {@code (x, y)}, or {@link #ACTION_NONE}; nothing answers while half shown. */
    public int actionAt(float x, float y) {
        if (!mShown || mProgress < .35f || mActions.isEmpty()) return ACTION_NONE;
        computeGeometry();
        for (int i = 0; i < mButtons.length; i++) {
            if (mButtons[i].contains(x, y)) return mActions.get(i).id;
        }
        return ACTION_NONE;
    }

    /**
     * The bounds of one button while the tab is up, in this view's coordinates; false when the tab
     * is down, half shown, or carries no such action.
     */
    public boolean actionBounds(int id, @NonNull RectF out) {
        if (!isControlsShown() || mProgress < .35f) return false;
        int index = indexOf(id);
        if (index < 0) return false;
        computeGeometry();
        out.set(mButtons[index]);
        return !out.isEmpty();
    }

    /** Where the tab sits on screen, for a popup that has to hang off it. */
    public void tabBounds(@NonNull RectF out) {
        computeGeometry();
        out.set(mTab);
    }

    private int indexOf(int id) {
        for (int i = 0; i < mActions.size(); i++) {
            if (mActions.get(i).id == id) return i;
        }
        return -1;
    }

    private void animateTo(float target, boolean clearOnEnd) {
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = ValueAnimator.ofFloat(mProgress, target);
        mAnimator.setDuration(190L);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (clearOnEnd && mProgress <= 0f) {
                    mShown = false;
                    mRetracting = false;
                }
            }
        });
        mAnimator.start();
    }

    /** How wide one button is: a glyph's square, or the room its own text asks for. */
    private float buttonWidth(@NonNull Action action) {
        float square = dp(30);
        if (action.isGlyph || action.mark != null) return square;
        return Math.max(square, mLabelPaint.measureText(action.text) + dp(20));
    }

    /**
     * The tab at its corner: the buttons a finger's width apart in a 32dp tab. Where that lands is
     * {@link CornerTabGeometry}'s to say — the same rule a terminal pane's tab follows — and this
     * view brings only the sizes its own buttons wear.
     */
    private void computeGeometry() {
        if (mActions.isEmpty() || !readFrame()) {
            mTab.setEmpty();
            for (RectF button : mButtons) button.setEmpty();
            return;
        }
        for (int i = 0; i < mActions.size(); i++) mWidths[i] = buttonWidth(mActions.get(i));
        CornerTabGeometry.layout(corner(), mBounds, mWidths, mActions.size(), dp(8), dp(5),
            dp(TAB_HEIGHT_DP), mPaneBorderPx, dp(3),
            dp(CornerTabGeometry.TAB_CORNER_HOLD_DP), mProgress, mTab, mButtons);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!mShown || mProgress <= 0f || mActions.isEmpty()) return;
        computeGeometry();
        if (mTab.isEmpty()) return;
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        int error = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError, Color.RED);
        int save = canvas.save();
        // Revealed through the page's own border, and clipped to the shape that border traces on
        // its inside: that clip is what rounds the tab's outer corner to the frame's own arc, and
        // what keeps everything the tab paints off the line. A page with no border clips to its
        // plain bounding box, which is what this always was.
        float arc = CornerTabGeometry.innerRadiusPx(mPaneRadiusPx, mPaneBorderPx);
        CornerTabGeometry.innerBounds(mBounds, mPaneBorderPx, mClip);
        if (arc > 0f) {
            mPath.reset();
            mPath.addRoundRect(mClip, arc, arc, Path.Direction.CW);
            canvas.clipPath(mPath);
        } else {
            canvas.clipRect(mClip);
        }

        // The tab's one free corner; the frame owns the other three, including the arc between the
        // tab and the frame side, and the edge that meets the frame side is a straight T-junction.
        float radius = CornerTabGeometry.tabCornerRadiusPx(arc, mTab.height(), mTab.width());
        // The fill runs a hair past the edge and is trimmed there by the clip, so no anti-aliased
        // seam opens up between the tab and the border it comes out from behind.
        CornerTabGeometry.buildTabFill(corner(), mClip, mTab, radius, dp(1), mPathPoints, mPath);
        drawMaterial(canvas, surface);

        // One line around frame and tab together: the frame's own stroke is the tab's outer edge,
        // so all the tab draws is the boundary it shares with the page's interior. Drawing its
        // outer edge as well is what used to leave a second line beside the border.
        CornerTabGeometry.buildTabOutline(corner(), mClip, mTab, radius, mPathPoints, mPath);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(CornerTabGeometry.TAB_OUTLINE_DP));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(225f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        int alpha = Math.round(255f * mProgress);
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        for (int i = 0; i < mActions.size(); i++) {
            Action action = mActions.get(i);
            int tint = mAlerted.contains(action.id) ? error
                : action.tint == TINT_ERROR ? error
                : action.tint == TINT_TERTIARY ? tertiary : primary;
            int color = ColorUtils.setAlphaComponent(tint, alpha);
            if (action.mark != null) {
                mMarkPaint.setStyle(Paint.Style.STROKE);
                mMarkPaint.setStrokeCap(Paint.Cap.ROUND);
                mMarkPaint.setStrokeWidth(dp(1.35f));
                mMarkPaint.setColor(color);
                action.mark.draw(canvas, mButtons[i], mMarkPaint,
                    getResources().getDisplayMetrics().density);
            } else {
                drawText(canvas, mButtons[i], action, color);
            }
        }
        canvas.restoreToCount(save);
    }

    /**
     * The tab's material, inside {@link #mPath}: the app's wallpaper blur at this tab's place
     * on screen under the panel scrim, or the scrim alone, stronger, when there is no blur. One
     * recipe for every screen — a terminal pane, the Display page, the Widgets page — so the
     * buttons read the same wherever the corner is.
     */
    private void drawMaterial(@NonNull Canvas canvas, int surface) {
        int save = canvas.save();
        canvas.clipPath(mPath);
        boolean frosted = mFrostFrame != null && !mFrostFrame.isRecycled() && mFrostShader != null;
        if (frosted) {
            aimFrost();
            mFrostPaint.setAlpha(Math.round(255f * mProgress));
            canvas.drawRect(mTab.left - dp(1), mTab.top - dp(1), mTab.right + dp(1),
                mTab.bottom + dp(1), mFrostPaint);
        }
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(scaleAlpha(ColorUtils.setAlphaComponent(surface,
            frosted ? SCRIM_ON_FROST_ALPHA : SCRIM_ALPHA)));
        canvas.drawRect(mClip, mPaint);
        canvas.restoreToCount(save);
    }

    /** The colour at its own alpha scaled by the slide, so the material fades in with the tab. */
    private int scaleAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * mProgress));
    }

    /**
     * Point the frost shader at the wallpaper under this view. Recomputed on every draw: the tab
     * is out only briefly and the overlay it lives in moves under the keyboard, so caching by
     * position buys nothing here and risks a stale aim.
     */
    private void aimFrost() {
        if (mFrostFrame == null || mFrostShader == null) return;
        float slideX = layoutOriginOnScreen(mLocation);
        // Past the page's slide and the wallpaper's parallax, exactly as the slab aims: the tab
        // is cut from the same glass and has to show the same wallpaper mid-slide.
        float shiftX = slideX + (mParallax == null ? 0f : mParallax.offsetPx());
        float scaleX = mFrostRect.width() / (float) Math.max(1, mFrostFrame.getWidth());
        float scaleY = mFrostRect.height() / (float) Math.max(1, mFrostFrame.getHeight());
        mFrostMatrix.reset();
        mFrostMatrix.setScale(scaleX, scaleY);
        mFrostMatrix.postTranslate(mFrostRect.left - mLocation[0] - shiftX,
            mFrostRect.top - mLocation[1]);
        mFrostShader.setLocalMatrix(mFrostMatrix);
    }

    /**
     * This view's position on screen as laid out, ignoring every transform on the way up — the
     * same anchor the pane's own slab uses, so the tab's frost lines up with the slab it grows
     * out of while the pane tilts or slides under a finger. The one transform that is not
     * ignored is the wall page's slide, returned rather than folded in, since a page travelling
     * over the wallpaper shows the wallpaper it is over.
     *
     * @return the wall page's translation on the way up, in px; 0 off the wall
     */
    private float layoutOriginOnScreen(@NonNull int[] out) {
        float x = 0f;
        float y = 0f;
        float slideX = 0f;
        View view = this;
        while (true) {
            x += view.getLeft();
            y += view.getTop();
            android.view.ViewParent parent = view.getParent();
            if (!(parent instanceof View)) break;
            View parentView = (View) parent;
            if (parentView instanceof PaneWallLayout) slideX += view.getTranslationX();
            x -= parentView.getScrollX();
            y -= parentView.getScrollY();
            view = parentView;
        }
        view.getLocationOnScreen(mRootLocation);
        out[0] = Math.round(x) + mRootLocation[0];
        out[1] = Math.round(y) + mRootLocation[1];
        return slideX;
    }

    private void drawText(@NonNull Canvas canvas, @NonNull RectF button, @NonNull Action action,
                          int color) {
        Paint paint = action.isGlyph ? mGlyphPaint : mLabelPaint;
        paint.setColor(color);
        float baseline = button.centerY() - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(action.text, button.centerX(), baseline, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) mAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
