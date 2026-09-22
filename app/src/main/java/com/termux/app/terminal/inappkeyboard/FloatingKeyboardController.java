package com.termux.app.terminal.inappkeyboard;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

/**
 * Hosts the in-app keyboard in its floating frame, and hands it back to the dock when the place
 * asks for a docked one.
 *
 * <p>The same keyboard is used either way. Docked, its container is a bottom-aligned child of the
 * accessory stack, and its height is what the stack reserves. Floating, the very same container is
 * moved — children, glass, suggestion strip and height controls intact — into a
 * {@link FloatingKeyboardFrame} in the full-content {@code floating_keyboard_host}, where it is
 * measured against the frame's width and drawn over the place instead of beside it. Nothing is
 * rebuilt on the way through, so switching type with the keyboard open re-hosts it without closing
 * it, and the mouse-mode touchpad, which lives inside the keyboard's own host, floats along with it.
 *
 * <p>The frame's width is a share of the content and its place a pair of fractions of the travel,
 * both re-resolved whenever the content bounds change — which is what makes a rotation, a font-scale
 * change and a taller keyboard land the frame the same distance along the new room. A drag writes
 * the fractions back per place and orientation, so every place remembers its own.
 */
public final class FloatingKeyboardController {

    /** What the frame needs from the activity: two slots, three settings and three notifications. */
    public interface Host {

        /** The full-content region the frame is placed in, or null before inflation. */
        @Nullable View floatingHost();

        /** The keyboard's own container, the thing that is moved in and out. */
        @Nullable View keyboardContainer();

        /** The user's floating width, as a share of the content width, for this orientation. */
        float floatingKeyboardWidthScale();

        /** The user's floating row height, as a multiplier, for this orientation. */
        float floatingKeyboardHeightScale();

        @Nullable PlaceLayoutStore placeLayoutStore();

        @NonNull PaneWallPage place();

        @NonNull PlaceOrientation orientation();

        /** The keyboard changed hosts: what the stack reserves and what it paints both moved. */
        void onFloatingHostingChanged();

        /** The frame moved; the position itself is already stored by the controller. */
        void onFloatingFrameMoved(boolean committed);

        /**
         * The grip is being dragged. Uncommitted frames are a preview and must not be written —
         * the card sizes itself from the width, and the host only has to push the row height at
         * the keyboard. The committed one is where both scales are stored.
         */
        void onFloatingFrameResized(float widthScale, float heightScale, boolean committed);
    }

    @NonNull private final Host mHost;

    @Nullable private FloatingKeyboardFrame mFrame;
    private boolean mFloating;
    private boolean mKeyboardShown;

    /** Where the container came from, so it can be put back exactly there. */
    @Nullable private ViewGroup mDockedParent;
    private int mDockedIndex = -1;
    @Nullable private ViewGroup.LayoutParams mDockedLayoutParams;

    private float mXFraction = FloatingKeyboardGeometry.DEFAULT_X_FRACTION;
    private float mYFraction = FloatingKeyboardGeometry.DEFAULT_Y_FRACTION;

    public FloatingKeyboardController(@NonNull Host host) {
        mHost = host;
    }

    /** True while the keyboard is hosted in the frame rather than in the accessory stack. */
    public boolean isFloating() {
        return mFloating;
    }

    /**
     * The bounds the hosted keyboard is measured against, or null while it is docked. Handed to
     * {@link KeyboardGeometryChoreographer} so the fractional height cap resolves against the frame.
     */
    @Nullable
    public KeyboardGeometryChoreographer.HostReference reference() {
        FloatingKeyboardFrame frame = mFrame;
        View host = mHost.floatingHost();
        if (!mFloating || frame == null || host == null) return null;
        return new KeyboardGeometryChoreographer.HostReference(
            frame.frameWidthPx(host.getWidth()), host.getHeight());
    }

    /**
     * The place on screen resolved to a keyboard type. Re-hosts the keyboard if that moved it, and
     * re-reads the width and the remembered place either way — the same call carries a rotation and
     * a move to another place, both of which have their own values.
     */
    public void onKeyboardFormResolved(@NonNull PlaceLayout.KeyboardForm form) {
        boolean floating = form == PlaceLayout.KeyboardForm.FLOATING;
        if (floating) readRememberedPosition();
        if (floating == mFloating) {
            if (floating) applyFrameGeometry();
            return;
        }
        mFloating = floating;
        if (floating) hostInFrame();
        else hostInDock();
        mHost.onFloatingHostingChanged();
    }

    /**
     * The keyboard was asked to show or hide. A frame with nothing in it is a handle floating on its
     * own, so the host goes with the keyboard.
     */
    public void onKeyboardVisibilityRequested(boolean visible) {
        mKeyboardShown = visible;
        applyHostVisibility();
        if (mFloating && visible) applyFrameGeometry();
    }

    /** Drops the frame and returns the keyboard to the dock; the activity is going away. */
    public void onDestroy() {
        if (mFloating) {
            mFloating = false;
            hostInDock();
        }
        mFrame = null;
    }

    // ------------------------------------------------------------------- hosting

    private void hostInFrame() {
        View host = mHost.floatingHost();
        View container = mHost.keyboardContainer();
        if (!(host instanceof ViewGroup) || container == null) {
            mFloating = false;
            return;
        }
        FloatingKeyboardFrame frame = ensureFrame((ViewGroup) host);
        if (container.getParent() == frame.contentHost()) {
            applyHostVisibility();
            applyFrameGeometry();
            return;
        }
        if (container.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) container.getParent();
            mDockedParent = parent;
            mDockedIndex = parent.indexOfChild(container);
            mDockedLayoutParams = container.getLayoutParams();
            parent.removeView(container);
        }
        frame.contentHost().addView(container, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyHostVisibility();
        applyFrameGeometry();
    }

    private void hostInDock() {
        View container = mHost.keyboardContainer();
        FloatingKeyboardFrame frame = mFrame;
        if (container != null && frame != null && container.getParent() == frame.contentHost()) {
            frame.contentHost().removeView(container);
            ViewGroup parent = mDockedParent;
            if (parent != null) {
                int index = mDockedIndex >= 0 && mDockedIndex <= parent.getChildCount()
                    ? mDockedIndex : parent.getChildCount();
                if (mDockedLayoutParams != null)
                    parent.addView(container, index, mDockedLayoutParams);
                else
                    parent.addView(container, index);
            }
        }
        applyHostVisibility();
    }

    @NonNull
    private FloatingKeyboardFrame ensureFrame(@NonNull ViewGroup host) {
        FloatingKeyboardFrame frame = mFrame;
        if (frame == null) {
            frame = new FloatingKeyboardFrame(host.getContext());
            frame.setWidthScaleSource(mHost::floatingKeyboardWidthScale);
            frame.setHeightScaleSource(mHost::floatingKeyboardHeightScale);
            frame.setOnFrameMovedListener(this::onFrameMoved);
            frame.setOnFrameResizedListener(this::onFrameResized);
            mFrame = frame;
            // Content bounds moved: a rotation, a window inset, a font scale. The frame re-measures
            // itself out of that same pass, so all that is left here is where it sits in the new
            // room — translation, which needs no layout of its own.
            host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol || b - t != ob - ot) applyTravelAndPosition();
            });
            // The frame's own height is the keyboard's, which the height slider, a rotation and a
            // layout change all move. Its travel follows.
            frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot || r - l != or - ol) applyTravelAndPosition();
            });
        }
        if (frame.getParent() != host) {
            if (frame.getParent() instanceof ViewGroup)
                ((ViewGroup) frame.getParent()).removeView(frame);
            host.addView(frame, FloatingKeyboardFrame.hostLayoutParams());
        }
        return frame;
    }

    private void applyHostVisibility() {
        View host = mHost.floatingHost();
        if (host == null) return;
        int visibility = mFloating && mKeyboardShown ? View.VISIBLE : View.GONE;
        if (host.getVisibility() != visibility) host.setVisibility(visibility);
    }

    // ------------------------------------------------------------------ geometry

    private void readRememberedPosition() {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null) return;
        PaneWallPage place = mHost.place();
        PlaceOrientation orientation = mHost.orientation();
        mXFraction = FloatingKeyboardGeometry.xFractionOr(
            store.floatingKeyboardX(place, orientation));
        mYFraction = FloatingKeyboardGeometry.yFractionOr(
            store.floatingKeyboardY(place, orientation));
    }

    /** Re-measures the frame for the settings it reads, then puts it back at its remembered place. */
    private void applyFrameGeometry() {
        FloatingKeyboardFrame frame = mFrame;
        if (frame == null || !mFloating) return;
        frame.requestLayout();
        applyTravelAndPosition();
    }

    /**
     * The travel the frame has in the content it floats over, and the place its fractions put it
     * at. Both are translation only: a size the frame resolves for itself is never written back to
     * it from a layout pass it is already inside.
     */
    private void applyTravelAndPosition() {
        FloatingKeyboardFrame frame = mFrame;
        if (frame == null || !mFloating) return;
        int travelXPx = travelXPx(frame);
        int travelYPx = travelYPx(frame);
        frame.setTravelPx(travelXPx, travelYPx);
        if (frame.ownsPosition()) {
            // A grip drag holds the card by its right and bottom edges, which is not a place any
            // fraction describes while the height is still catching up. Read where the card put
            // itself instead of putting it back where it was parked.
            mXFraction = FloatingKeyboardGeometry.fractionFor(frame.positionXPx(), travelXPx);
            mYFraction = FloatingKeyboardGeometry.fractionFor(frame.positionYPx(), travelYPx);
            // The finger's own commit wrote where the card was before the keyboard answered with
            // its new height; this is where the card actually came to rest.
            if (!frame.isResizing()) rememberPosition();
            return;
        }
        frame.setPositionPx(
            FloatingKeyboardGeometry.positionPx(mXFraction, travelXPx),
            FloatingKeyboardGeometry.positionPx(mYFraction, travelYPx));
    }

    private int travelXPx(@NonNull FloatingKeyboardFrame frame) {
        View host = mHost.floatingHost();
        int contentWidthPx = host == null ? 0 : host.getWidth();
        int frameWidthPx = frame.getWidth() > 0 ? frame.getWidth()
            : frame.frameWidthPx(contentWidthPx);
        return FloatingKeyboardGeometry.travelPx(contentWidthPx, frameWidthPx);
    }

    private int travelYPx(@NonNull FloatingKeyboardFrame frame) {
        View host = mHost.floatingHost();
        return FloatingKeyboardGeometry.travelPx(host == null ? 0 : host.getHeight(),
            frame.getHeight());
    }

    private void onFrameMoved(int xPx, int yPx, boolean committed) {
        FloatingKeyboardFrame frame = mFrame;
        if (frame == null) return;
        mXFraction = FloatingKeyboardGeometry.fractionFor(xPx, travelXPx(frame));
        mYFraction = FloatingKeyboardGeometry.fractionFor(yPx, travelYPx(frame));
        if (committed) rememberPosition();
        mHost.onFloatingFrameMoved(committed);
    }

    /** The place the card is in now, kept for this place and orientation. */
    private void rememberPosition() {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null) return;
        store.setFloatingKeyboardPosition(mHost.place(), mHost.orientation(),
            mXFraction, mYFraction);
    }

    /**
     * The grip drag. The card has already taken its new width and moved its left edge to keep its
     * right one still, so what is left is to remember where that put it and to hand the two scales
     * on — a preview while the finger is down, a write when it lifts.
     */
    private void onFrameResized(float widthScale, float heightScale, int xPx, boolean committed) {
        FloatingKeyboardFrame frame = mFrame;
        if (frame == null) return;
        mXFraction = FloatingKeyboardGeometry.fractionFor(xPx, frame.travelXPx());
        if (committed) rememberPosition();
        mHost.onFloatingFrameResized(widthScale, heightScale, committed);
    }

    /** The frame, for tests. Null until the keyboard has floated once. */
    @Nullable
    FloatingKeyboardFrame frame() {
        return mFrame;
    }
}
