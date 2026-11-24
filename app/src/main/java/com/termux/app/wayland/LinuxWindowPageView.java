package com.termux.app.wayland;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
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
 * its dialogs, centred over it) below a band at the top where the rim tab sits, outside the app's
 * pixels. A tap on the rim tab shows Close, Minimise and Scale; Scale brings out the scale rail.
 * The wall stands it in the Terminal page's place while a Linux window is in front of the row
 * (#392); {@link LinuxWindowsController} fills its window slot.
 */
public final class LinuxWindowPageView extends PaneContentFrame implements Page, TypingPage {

    /** What the page asks of the controller. */
    public interface Host extends DisplayScaleRail.Host {
        boolean hasWindow();
        /** Whether the shown window takes a scale of its own (an X window shares the server's). */
        boolean windowScales();
        void closeWindow();
        void minimiseWindow();
        /** The page came in front, or left it. */
        void frontChanged(boolean front);
        @NonNull TerminalKeyEventHandler.KeyValueInterceptor keys();
    }

    private final PaneGlassBackdropView glass;
    private final PaneRim rim = new PaneRim();
    /** The bound window's views; the controller adds and removes them. */
    private final FrameLayout slot;
    private final ImageView tab;
    private final GradientDrawable tabFill = new GradientDrawable();
    private final DisplayScaleRailView railView;
    private final DisplayScaleRail rail;
    private final Rect tabRect = new Rect();
    @NonNull private final Host host;
    @Nullable private PaneSurfaceStyle style;
    private float radiusPx;
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
        slotParams.topMargin = LinuxWindowRow.slotTopPx(getResources().getDisplayMetrics().density,
            topInsetPx);
        addView(slot, slotParams);
        tab = new ImageView(context);
        tab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tab.setImageResource(R.drawable.ic_ms_more_horiz);
        tab.setContentDescription(context.getString(R.string.linux_window_menu));
        tab.setBackground(tabFill);
        tab.setOnClickListener(v -> showMenu());
        addView(tab, new LayoutParams(0, 0));
        railView = new DisplayScaleRailView(context);
        rail = new DisplayScaleRail(railView, host);
        addView(railView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        applyStyle(null);
    }

    /**
     * Holds the band, the tab and the app's slot {@code px} below the top, where the status bar
     * stands as the row in the pane (the room the Terminal's top pane holds: PaneRowPolicy
     * .paneTopInsetPx); 0 gives it back. Compares first; lays out only on a change.
     */
    public void setTopInsetPx(int px) {
        int inset = Math.max(0, px);
        if (inset == topInsetPx) return;
        topInsetPx = inset;
        LayoutParams lp = (LayoutParams) slot.getLayoutParams();
        int top = LinuxWindowRow.slotTopPx(getResources().getDisplayMetrics().density, inset);
        if (lp.topMargin != top) {
            lp.topMargin = top;
            slot.setLayoutParams(lp);
        }
        placeTab();
    }

    /** Where the controller puts the bound window's views. */
    @NonNull
    FrameLayout slot() {
        return slot;
    }

    /** Dress the page from the surface style; run again on every style change. */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        this.style = style;
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
        setPaneShape(radiusPx, true);
        if (slab || PaneRim.plainBorderWanted(style)) rim.apply(this, slab, radiusPx, PaneBorderStyle.lone(), style);
        else rim.clear(this);
        // The tab is glass over the rim band: the panel colour, see-through, with the rim's glyph colour.
        tabFill.setColor((surface & 0x00FFFFFF) | 0xB3000000);
        tabFill.setCornerRadius(LinuxWindowRow.TAB_HEIGHT_DP * density / 2f);
        tab.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFFFFFFF)));
        placeTab();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        placeTab();
    }

    /** Sizes the tab from {@link LinuxWindowRow#tabRect}; compares first, so no layout loop. */
    private void placeTab() {
        if (getWidth() <= 0) return;
        LinuxWindowRow.tabRect(getWidth(), radiusPx, getResources().getDisplayMetrics().density,
            topInsetPx, tabRect);
        LayoutParams lp = (LayoutParams) tab.getLayoutParams();
        if (lp.width == tabRect.width() && lp.height == tabRect.height()
            && lp.leftMargin == tabRect.left && lp.topMargin == tabRect.top) return;
        lp.width = tabRect.width();
        lp.height = tabRect.height();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = tabRect.left;
        lp.topMargin = tabRect.top;
        // Posted: onSizeChanged runs inside layout, which must not ask for another.
        post(() -> tab.setLayoutParams(lp));
    }

    /** The rim tab's menu: Close, Minimise, Scale. Move comes with carry (#386) and goes here. */
    private void showMenu() {
        if (!host.hasWindow()) return;
        PopupMenu menu = new PopupMenu(getContext(), tab, Gravity.END);
        menu.getMenu().add(0, 1, 0, R.string.linux_windows_close);
        menu.getMenu().add(0, 2, 1, R.string.linux_window_minimise);
        menu.getMenu().add(0, 3, 2, R.string.linux_window_scale).setEnabled(host.windowScales());
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: host.closeWindow(); return true;
                case 2: host.minimiseWindow(); return true;
                case 3: rail.show(); return true;
                default: return false;
            }
        });
        menu.show();
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
