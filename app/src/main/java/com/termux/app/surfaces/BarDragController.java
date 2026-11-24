package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.layouteditor.BarDragGeometry;
import com.termux.app.layouteditor.BarDrop;
import com.termux.app.layouteditor.BarLandingPolicy;
import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.statusbar.StatusPlace;

/**
 * Drag a bar on the editor's frame (#338). Each bar on the frame wears a chip, its name and a
 * drag handle ({@link BarHandleDrawable}); holding a bar for the long-press timeout lifts it, a
 * line on the frame shows the slot {@link BarLandingPolicy#land} gives the finger, and letting go
 * writes the editor's draft through {@link LayoutEditorController#writeSlots} (Undo and Discard
 * take it back; Done stores it). A landing the policy refuses leaves the layout as it was.
 *
 * <pre>
 *   // the frame's tap groups, before their own tap:
 *   if (drag.onTouch(group, event)) return true;
 * </pre>
 *
 * <p>Nothing lays out while a bar is held: the chips and the two lines are views added to the tap
 * overlay once, and a move only sets a translation. The held bar stays where it stands until the
 * drop, so the frame keeps its size. A tap is not a hold: the group's own click opens the card.
 *
 * <p>The Hidden column (#339) is {@link Frame#setHidden} fed from {@link Host#hiddenRect}; the pane
 * (#342) is {@link Host#paneRect}, held beyond an edge's reach, and a drop there writes the status
 * place through {@link Host#setStatusPlace}. {@link #commit} is the one write a drop and a tapped
 * place ({@link BarPlacesController}) share.
 */
final class BarDragController {

    /** The editor's half: where bars stand, the session, and the colours. */
    interface Host {
        @NonNull Context context();

        /** The tap overlay the chips and lines join, or null while it is not there. */
        @Nullable ViewGroup overlay();

        /** The frame's scale, so what is drawn keeps its on-screen size. */
        float scale();

        boolean open();

        /** Where {@code bar} stands in the overlay (l, t, r, b); false when it is not on screen. */
        boolean rectOf(@NonNull Bar bar, @NonNull float[] out);

        /** The Hidden column's rect in the overlay (#339); false until it exists. */
        default boolean hiddenRect(@NonNull float[] out) {
            return false;
        }

        /** The pane's rect in the overlay (#342); false until the status bar may stand there. */
        default boolean paneRect(@NonNull float[] out) {
            return false;
        }

        @Nullable LayoutEditorController layout();

        @NonNull StatusPlace statusPlace();

        /** The status bar's one place for every page, written through the editor's preferences. */
        default void setStatusPlace(@NonNull StatusPlace place) {}

        int primaryColor();

        int surfaceColor();

        int onSurfaceColor();

        /** The held bar is over the Hidden column (letting go hides it), or left it. */
        default void onHiddenTarget(boolean over) {}

        /** A drop wrote the draft: the targets are laid over the bars again. */
        void onWritten();
    }

    private static final Bar[] BARS = {
        Bar.STATUS_BAR, Bar.APPS_ROW, Bar.AZ_INDEX, Bar.EXTRA_KEYS, Bar.KEYBOARD};
    private static final int CHIP_HEIGHT_DP = 28;
    private static final int LINE_DP = 3;

    @NonNull private final Host mHost;
    @NonNull private final float[] mRect = new float[4];
    @NonNull private final float[] mLine = new float[4];
    @NonNull private final float[] mScratch = new float[4];
    private final View[] mChips = new View[BARS.length];
    private final TextView[] mLabels = new TextView[BARS.length];
    @Nullable private View mHLine;
    @Nullable private View mVLine;

    // The hold in progress.
    /** The bar a finger last went down on, for the tap that follows (it selects). */
    @Nullable private Bar mTouched;
    @Nullable private Bar mHeld;
    @Nullable private View mGroup;
    private boolean mArmed;
    private boolean mDragging;
    private float mDownRawX;
    private float mDownRawY;
    private float mX;
    private float mY;
    @Nullable private BarDragGeometry.Frame mFrame;
    @Nullable private FrameState mState;
    @Nullable private Landing mLanding;

    private final Runnable mLift = this::lift;

    BarDragController(@NonNull Host host) {
        mHost = host;
    }

    // ------------------------------------------------------------------------------- touches

    /**
     * Feeds one touch of a tap group. True when the drag has the stream (a lifted bar or its
     * drop), so the group neither moves on nor taps; false for everything else, the tap included.
     */
    boolean onTouch(@NonNull View group, @NonNull MotionEvent event) {
        ViewGroup overlay = mHost.overlay();
        if (overlay == null || !mHost.open())
            return false;
        float x = event.getX() + group.getLeft();
        float y = event.getY() + group.getTop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                cancelHold();
                Bar bar = barAt(x, y);
                mTouched = bar;
                if (bar == null || mHost.layout() == null || mHost.layout().layoutKind() == null)
                    return false;
                mHeld = bar;
                mGroup = group;
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                mX = x;
                mY = y;
                mArmed = true;
                overlay.postDelayed(mLift, ViewConfiguration.getLongPressTimeout());
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mDragging) {
                    mX = x;
                    mY = y;
                    update();
                    return true;
                }
                if (mArmed) {
                    float slop = ViewConfiguration.get(mHost.context()).getScaledTouchSlop();
                    if (Math.abs(event.getRawX() - mDownRawX) > slop
                        || Math.abs(event.getRawY() - mDownRawY) > slop)
                        cancelHold();
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
                if (mDragging) {
                    mX = x;
                    mY = y;
                    update();
                    finish();
                    return true;
                }
                cancelHold();
                return false;
            case MotionEvent.ACTION_CANCEL: {
                boolean was = mDragging;
                cancelHold();
                return was;
            }
            default:
                return mDragging;
        }
    }

    /** The bar the last touch went down on, or null when it went down on none. */
    @Nullable
    Bar touchedBar() {
        return mTouched;
    }

    /** Whether a bar is lifted. */
    boolean dragging() {
        return mDragging;
    }

    private void cancelHold() {
        ViewGroup overlay = mHost.overlay();
        if (overlay != null)
            overlay.removeCallbacks(mLift);
        if (mDragging) {
            hideLines();
            if (mGroup != null && mGroup.getParent() != null)
                mGroup.getParent().requestDisallowInterceptTouchEvent(false);
        }
        mHost.onHiddenTarget(false);
        mArmed = false;
        mDragging = false;
        mHeld = null;
        mGroup = null;
        mFrame = null;
        mState = null;
        mLanding = null;
        setHeldLook(null);
    }

    /** The hold timed out on a still finger: the bar is lifted. */
    private void lift() {
        ViewGroup overlay = mHost.overlay();
        LayoutEditorController layout = mHost.layout();
        Bar held = mHeld;
        if (!mArmed || overlay == null || layout == null || held == null || !mHost.open())
            return;
        PlaceLayout arranged = layout.arrangedLayout();
        PlaceOrientation orientation = layout.shownOrientation();
        LayoutKind kind = layout.layoutKind();
        if (arranged == null || orientation == null || kind == null) {
            cancelHold();
            return;
        }
        BarDragGeometry.Frame frame = buildFrame(overlay, arranged, kind);
        mFrame = frame;
        mState = new FrameState(arranged, orientation, kind, mHost.statusPlace());
        mArmed = false;
        mDragging = true;
        if (mGroup != null && mGroup.getParent() != null)
            mGroup.getParent().requestDisallowInterceptTouchEvent(true);
        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setHeldLook(held);
        update();
    }

    /**
     * The frame as it stands now: every bar's band, the keyboard, the Hidden column and the pane
     * (not on Home). Built when a bar lifts and when one is selected, never per move.
     */
    @NonNull
    BarDragGeometry.Frame buildFrame(@NonNull ViewGroup overlay, @NonNull PlaceLayout arranged,
                                     @NonNull LayoutKind kind) {
        BarDragGeometry.Frame frame = new BarDragGeometry.Frame(overlay.getWidth(),
            overlay.getHeight());
        for (Bar bar : BARS) {
            if (!mHost.rectOf(bar, mRect))
                continue;
            if (bar == Bar.KEYBOARD) {
                frame.setKeyboard(mRect[0], mRect[1], mRect[2], mRect[3]);
            } else if (bar.element() != null && EdgeStackPolicy.claimsBand(arranged, bar.element())) {
                frame.addBand(bar, EdgeStackPolicy.edgeOf(arranged, bar.element()),
                    EdgeStackPolicy.standsUnderKeyboard(arranged, bar.element()),
                    mRect[0], mRect[1], mRect[2], mRect[3]);
            }
        }
        if (mHost.hiddenRect(mScratch))
            frame.setHidden(mScratch[0], mScratch[1], mScratch[2], mScratch[3]);
        if (kind.live() != LayoutKind.HOME && mHost.paneRect(mScratch))
            frame.setPane(mScratch[0], mScratch[1], mScratch[2], mScratch[3],
                dpToPx(CHIP_HEIGHT_DP * 2));
        return frame;
    }

    /**
     * Writes what an allowed landing says into the draft: a slot, hiding, or the status bar's
     * place. False when nothing changed. Both the drag's drop and a tapped place come here.
     */
    boolean commit(@NonNull Bar held, @NonNull Landing landing) {
        LayoutEditorController layout = mHost.layout();
        if (!landing.allowed || layout == null)
            return false;
        boolean wrote = false;
        if (landing.zone == Zone.EDGE) {
            PlaceLayout arranged = layout.arrangedLayout();
            PlaceLayout next = arranged == null ? null
                : BarDrop.layoutAfter(arranged, held, landing);
            if (next != null && !next.equals(arranged)) {
                layout.writeSlots(next);
                wrote = true;
            }
        }
        if (landing.zone == Zone.HIDDEN) {
            // Hiding is the layout's own: a status bar put away keeps its edge.
            if (held.element() != null) {
                layout.setShown(held.element(), false);
                wrote = true;
            } else if (held == Bar.KEYBOARD) {
                layout.setKeyboardShown(false);
                wrote = true;
            }
        }
        StatusPlace place = BarDrop.statusPlaceAfter(held, landing, mHost.statusPlace());
        if (place != null) {
            mHost.setStatusPlace(place);
            wrote = true;
        }
        return wrote;
    }

    private void update() {
        BarDragGeometry.Frame frame = mFrame;
        FrameState state = mState;
        Bar held = mHeld;
        if (frame == null || state == null || held == null)
            return;
        BarLandingPolicy.Hold hold = BarDragGeometry.holdAt(frame, held, mX, mY);
        Landing landing = BarLandingPolicy.land(state, held, hold);
        mLanding = landing;
        mHost.onHiddenTarget(landing.allowed && landing.zone == Zone.HIDDEN);
        if (landing.zone != Zone.HIDDEN && BarDragGeometry.slotLine(frame, held, landing, mLine))
            showLine(landing.edge != null && landing.edge.isOnSide());
        else
            hideLines();
    }

    /** Letting go: the draft takes an allowed landing; a refused one changes nothing. */
    private void finish() {
        Bar held = mHeld;
        Landing landing = mLanding;
        boolean wrote = held != null && landing != null && commit(held, landing);
        cancelHold();
        if (wrote)
            mHost.onWritten();
    }

    @Nullable
    private Bar barAt(float x, float y) {
        // The smallest bar under the finger wins, so a row inside another's rect is reachable.
        Bar best = null;
        float bestArea = Float.MAX_VALUE;
        for (Bar bar : BARS) {
            if (!mHost.rectOf(bar, mRect))
                continue;
            if (x < mRect[0] || x > mRect[2] || y < mRect[1] || y > mRect[3])
                continue;
            float area = (mRect[2] - mRect[0]) * (mRect[3] - mRect[1]);
            if (area < bestArea) {
                best = bar;
                bestArea = area;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------- what is drawn

    /** Lays each bar's chip over it: translation and scale only, no layout request. */
    void placeChips() {
        ViewGroup overlay = mHost.overlay();
        if (overlay == null || !mHost.open())
            return;
        float scale = Math.max(0.01f, mHost.scale());
        for (int i = 0; i < BARS.length; i++) {
            boolean on = mHost.rectOf(BARS[i], mRect)
                && mRect[2] - mRect[0] > dpToPx(CHIP_HEIGHT_DP);
            if (!on) {
                if (mChips[i] != null) EditorViews.setVisible(mChips[i], false);
                continue;
            }
            View chip = chipFor(i, overlay);
            // The chip keeps its on-screen size at any frame scale: scaled up by the frame's
            // inverse about its own corner.
            float inverse = 1f / scale;
            if (chip.getScaleX() != inverse) {
                chip.setScaleX(inverse);
                chip.setScaleY(inverse);
            }
            chip.setPivotX(0f);
            chip.setPivotY(0f);
            float width = chip.getMeasuredWidth() * inverse;
            float height = chip.getMeasuredHeight() * inverse;
            float left = mRect[0] + Math.max(0f, (mRect[2] - mRect[0] - width) / 2f);
            float top = mRect[1] + Math.max(0f, (mRect[3] - mRect[1] - height) / 2f);
            chip.setTranslationX(left);
            chip.setTranslationY(top);
            EditorViews.setVisible(chip, true);
        }
    }

    /** The chips and lines are taken off the overlay with the session. */
    void release() {
        cancelHold();
        ViewGroup overlay = mHost.overlay();
        for (int i = 0; i < mChips.length; i++) {
            if (mChips[i] != null && overlay != null)
                overlay.removeView(mChips[i]);
            mChips[i] = null;
            mLabels[i] = null;
        }
        if (overlay != null) {
            if (mHLine != null) overlay.removeView(mHLine);
            if (mVLine != null) overlay.removeView(mVLine);
        }
        mHLine = null;
        mVLine = null;
    }

    @NonNull
    private View chipFor(int index, @NonNull ViewGroup overlay) {
        View existing = mChips[index];
        if (existing != null)
            return existing;
        Context context = mHost.context();
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setClickable(false);
        chip.setFocusable(false);
        int pad = Math.round(dpToPx(8));
        chip.setPadding(pad, 0, pad, 0);
        GradientDrawable back = new GradientDrawable();
        int surface = mHost.surfaceColor();
        back.setColor((0xCC << 24) | (surface & 0x00FFFFFF));
        back.setCornerRadius(dpToPx(CHIP_HEIGHT_DP / 2f));
        chip.setBackground(back);

        TextView label = new TextView(context);
        label.setText(labelOf(BARS[index]));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setTextColor(mHost.onSurfaceColor());
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chip.addView(label, labelParams);

        ImageView handle = new ImageView(context);
        float radius = dpToPx(1.25f);
        float pitch = dpToPx(4.5f);
        handle.setImageDrawable(new BarHandleDrawable(mHost.onSurfaceColor(), radius, pitch));
        handle.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
            Math.round(BarHandleDrawable.widthFor(radius, pitch)),
            Math.round(BarHandleDrawable.heightFor(radius, pitch)));
        handleParams.setMarginStart(Math.round(dpToPx(8)));
        chip.addView(handle, handleParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(dpToPx(CHIP_HEIGHT_DP)),
            Gravity.TOP | Gravity.START);
        overlay.addView(chip, params);
        chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(Math.round(dpToPx(CHIP_HEIGHT_DP)),
                View.MeasureSpec.EXACTLY));
        mChips[index] = chip;
        mLabels[index] = label;
        return chip;
    }

    /** The lifted bar's chip lights; null puts every chip back. */
    private void setHeldLook(@Nullable Bar held) {
        for (int i = 0; i < BARS.length; i++) {
            View chip = mChips[i];
            if (chip == null) continue;
            float alpha = held == null || BARS[i] == held ? 1f : 0.5f;
            if (chip.getAlpha() != alpha) chip.setAlpha(alpha);
        }
    }

    private void showLine(boolean vertical) {
        ViewGroup overlay = mHost.overlay();
        if (overlay == null)
            return;
        float thickness = Math.max(1f, dpToPx(LINE_DP) / Math.max(0.01f, mHost.scale()));
        View line = lineFor(overlay, vertical, thickness);
        View other = vertical ? mHLine : mVLine;
        if (other != null) EditorViews.setVisible(other, false);
        if (vertical) line.setTranslationX(mLine[0] - thickness / 2f);
        else line.setTranslationY(mLine[1] - thickness / 2f);
        EditorViews.setVisible(line, true);
    }

    private void hideLines() {
        if (mHLine != null) EditorViews.setVisible(mHLine, false);
        if (mVLine != null) EditorViews.setVisible(mVLine, false);
    }

    /** One line view per direction, sized once for the overlay; a move only translates it. */
    @NonNull
    private View lineFor(@NonNull ViewGroup overlay, boolean vertical, float thickness) {
        View existing = vertical ? mVLine : mHLine;
        int size = Math.round(thickness);
        if (existing != null) {
            ViewGroup.LayoutParams params = existing.getLayoutParams();
            int wanted = vertical ? overlay.getHeight() : overlay.getWidth();
            if ((vertical ? params.width : params.height) == size
                && (vertical ? params.height : params.width) == wanted)
                return existing;
            params.width = vertical ? size : wanted;
            params.height = vertical ? wanted : size;
            existing.setLayoutParams(params);
            return existing;
        }
        View line = new View(mHost.context());
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(mHost.primaryColor());
        fill.setCornerRadius(thickness / 2f);
        line.setBackground(fill);
        line.setClickable(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            vertical ? size : overlay.getWidth(), vertical ? overlay.getHeight() : size,
            Gravity.TOP | Gravity.START);
        overlay.addView(line, params);
        if (vertical) mVLine = line;
        else mHLine = line;
        return line;
    }

    @StringRes
    private static int labelOf(@NonNull Bar bar) {
        switch (bar) {
            case STATUS_BAR: return R.string.editor_bar_status;
            case APPS_ROW: return R.string.editor_bar_apps;
            case AZ_INDEX: return R.string.editor_bar_az;
            case EXTRA_KEYS: return R.string.editor_bar_extra_keys;
            default: return R.string.editor_bar_keyboard;
        }
    }

    private float dpToPx(float dp) {
        return dp * mHost.context().getResources().getDisplayMetrics().density;
    }
}
