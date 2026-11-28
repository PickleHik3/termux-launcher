package com.termux.app.wayland;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.terminal.PaneBorderStyle;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.app.terminal.inappkeyboard.TypingPage;
import com.termux.app.x11.DisplayScaleRail;
import com.termux.app.x11.DisplayScaleRailView;

/**
 * The one view every Linux window's page shows (spec #384), modelled on the Assistant page: a
 * glass pane dressed as the panes beside it, holding the bound window's {@code TextureView} (and
 * its dialogs, centred over it) below the status row's room. It has no controls of its own: Close,
 * Minimise, Maximise, Move and Scale are on the window's mark (a pane in a split has them on its
 * corner tab), and Scale brings out the scale rail here ({@link #showRail}). The wall stands it in the Terminal page's place while a Linux window is in front of the row
 * (#392); {@link LinuxWindowsController} fills its window slot.
 */
public final class LinuxWindowPageView extends PaneContentFrame implements Page, TypingPage {

    /** What the page asks of the controller. */
    public interface Host extends DisplayScaleRail.Host {
        boolean hasWindow();
        /** Whether the shown window takes a scale of its own (an X window shares the server's). */
        boolean windowScales();
        /** The page came in front, or left it. */
        void frontChanged(boolean front);
        @NonNull TerminalKeyEventHandler.KeyValueInterceptor keys();
        /** A Float's page was shown or hidden (it came on screen, was minimised, Home turned away). */
        default void shownChanged(boolean shown) {}
        /** Back while the shown window is full screen: it leaves; false when it is not full screen. */
        default boolean leaveFullscreen() { return false; }
        /**
         * The phone keyboard's connection to the shown window's focused text field, filling
         * {@code out}; null while the window has no field served through the phone's keyboard.
         */
        @Nullable default InputConnection textInput(@NonNull View view, @NonNull EditorInfo out) { return null; }
    }

    private final PaneGlassBackdropView glass;
    private final PaneRim rim = new PaneRim();
    /** The bound window's views; the controller adds and removes them. */
    private final FrameLayout slot;
    private final DisplayScaleRailView railView;
    private final DisplayScaleRail rail;
    @NonNull private final Host host;
    @Nullable private PaneSurfaceStyle style;
    private float radiusPx;
    /** The page is a pane of a split: the pane controller shapes and rims it, as it does a terminal's. */
    private boolean embedded;
    /** The page wears no dress of its own ({@link #setBare}). */
    private boolean bare;
    /** The page is a Float on Home (#388): the Float's titlebar is its top, so nothing above the app. */
    private boolean floating;
    /** The status row's room at the top, as the Terminal's top pane holds it. */
    private int topInsetPx;

    public LinuxWindowPageView(@NonNull Context context, @NonNull Host host) {
        super(context);
        this.host = host;
        setClipChildren(true);
        glass = new PaneGlassBackdropView(context);
        glass.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        glass.setVisibility(GONE);
        addView(glass, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        PaneGlass.followLayout(glass);
        slot = new FrameLayout(context);
        LayoutParams slotParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        slotParams.topMargin = slotTop();
        addView(slot, slotParams);
        railView = new DisplayScaleRailView(context);
        rail = new DisplayScaleRail(railView, host);
        addView(railView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        applyStyle(null);
    }

    /**
     * Makes this page a pane of a split (#387): the pane controller gives it its shape and its
     * rim like any pane, and the page keeps its glass. Set once, before it shows.
     */
    /**
     * Under the Terl desktop the window frame wears the glass: this page stands bare, so a
     * see-through app shows the frame's glass and nothing of v1's pane dress.
     */
    public void setBare(boolean bare) {
        if (this.bare == bare) return;
        this.bare = bare;
        applyStyle(style);
    }

    public void setEmbedded(boolean embedded) {
        if (this.embedded == embedded) return;
        this.embedded = embedded;
        applyStyle(style);
    }

    /**
     * Makes this page a Float's picture (#388): the Float's titlebar is its top, so the status
     * row's room above the app's pixels goes. Set before it shows, and back with {@code false}
     * when the window returns to a row or a split; compares first.
     */
    public void setFloating(boolean floating) {
        if (this.floating == floating) return;
        this.floating = floating;
        LayoutParams lp = (LayoutParams) slot.getLayoutParams();
        int top = slotTop();
        if (lp.topMargin != top) {
            lp.topMargin = top;
            slot.setLayoutParams(lp);
        }
    }

    /** Where the app's pixels start: below the status row's room, or the top of a Float's picture. */
    private int slotTop() {
        return floating ? 0 : LinuxWindowRow.slotTopPx(topInsetPx);
    }

    /**
     * Holds the app's slot {@code px} below the top, where the status bar
     * stands as the row in the pane (the room the Terminal's top pane holds: PaneRowPolicy
     * .paneTopInsetPx); 0 gives it back. Compares first; lays out only on a change.
     */
    public void setTopInsetPx(int px) {
        int inset = Math.max(0, px);
        if (inset == topInsetPx) return;
        topInsetPx = inset;
        LayoutParams lp = (LayoutParams) slot.getLayoutParams();
        int top = slotTop();
        if (lp.topMargin != top) {
            lp.topMargin = top;
            slot.setLayoutParams(lp);
        }
    }

    /** The slot's height on a page {@code pageHeight} px high: below the status row's room. */
    int slotHeightFor(int pageHeight) {
        return Math.max(0, pageHeight - LinuxWindowRow.slotTopPx(topInsetPx));
    }

    /** Where the controller puts the bound window's views. */
    @NonNull
    FrameLayout slot() {
        return slot;
    }

    /** Dress the page from the surface style; run again on every style change. */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        this.style = style;
        if (bare) {
            PaneGlass.apply(null, this, glass, 0f);
            setBackground(null);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        radiusPx = style == null ? 24f * density : PaneGlass.radiusPx(style, density);
        boolean slab = PaneGlass.apply(style, this, glass, radiusPx);
        int surface = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            0xFF202020);
        // Glass off: a solid pane in the panel colour, as the Assistant page's.
        if (slab) setBackground(null);
        else {
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(surface);
            fill.setCornerRadius(radiusPx);
            setBackground(fill);
        }
        if (!embedded) {
            setPaneShape(radiusPx, true);
            if (slab || PaneRim.plainBorderWanted(style)) rim.apply(this, slab, radiusPx, PaneBorderStyle.lone(), style);
            else rim.clear(this);
        }
    }

    /**
     * Brings out the scale rail for the shown window, as the window's Scale control asks; nothing
     * for a window that takes no scale of its own (an X window shares the server's).
     */
    void showRail() {
        if (host.windowScales()) rail.show();
    }

    /** Puts the rail away: the window behind it changed or went. */
    void dismissRail() {
        rail.dismiss();
    }

    @Override
    public boolean onInterceptTouchEvent(@Nullable MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        if (rail.down(event.getX(), event.getY())) return true;
        if (rail.isShown()) rail.dismiss();
        return false;
    }

    @Override
    public boolean onTouchEvent(@Nullable MotionEvent event) {
        if (rail.isPressed()) return rail.onTouch(event);
        super.onTouchEvent(event);
        // It stands over the Terminal's pane host (#392): the terminal behind takes none of its touches.
        return true;
    }

    /** How much of the page's rim the wall's slide leaves showing. */
    public void setOutlineTravelAlpha(float alpha) {
        rim.setTravelAlpha(alpha);
    }

    // ---- Page and TypingPage ------------------------------------------------------------------

    @NonNull @Override public PageKind kind() { return PageKind.LINUX; }

    @NonNull @Override public View view() { return this; }

    /** A Linux window rests as a terminal window does: the Terminal's keys, look and chips. */
    @NonNull
    @Override
    public Rest rest(@NonNull Rest.Inputs in) {
        boolean keyboardUp = !in.overviewPreviewing && in.keyboardRemembered && !in.minimal;
        return new Rest(keyboardUp, PageKind.TERMINAL, false, in.barShown, true, PageKind.TERMINAL);
    }

    @Override public void onFront() { host.frontChanged(true); }

    @Override public void offFront() {
        rail.dismiss();
        host.frontChanged(false);
    }

    @Override public boolean onBack() {
        if (host.leaveFullscreen()) return true;
        if (!rail.isShown()) return false;
        rail.dismiss();
        return true;
    }

    /** The launcher's keyboard types into the shown window whenever there is one. */
    @Override
    public boolean isTyping() {
        return host.hasWindow();
    }

    @NonNull
    @Override
    public TerminalKeyEventHandler.KeyValueInterceptor typingKeys() {
        return host.keys();
    }

    /** A text editor only while the window's field takes the phone's keyboard. */
    @Override
    public boolean onCheckIsTextEditor() {
        return isFocusable();
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo out) {
        return host.textInput(this, out);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (floating) host.shownChanged(isVisible);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyStyle(style);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        rim.cancel();
        rail.dismiss();
    }
}
