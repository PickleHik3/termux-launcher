package com.termux.app.assistant;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneBorderStyle;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * The Assistant page (ADR 0022): one glass pane holding the chat and, over it, the Chats list. The
 * pane is dressed like the Display page: the launcher's glass slab and rim from the surface style,
 * and with glass off a solid surfaceContainerHigh. Everything inside is opaque and takes its colour
 * roles from the theme. The wall owns this view; it builds it through {@link AssistantPageHost}.
 */
public class AssistantPageView extends PaneContentFrame {

    private final PaneGlassBackdropView glass;
    /**
     * The pane's veil over its glass ({@link AssistantInk}): its own colour at the alpha the
     * legibility contract asks for what is under it. Gone when there is no glass to veil.
     */
    private final View veil;
    @Nullable private AssistantUi.Palette themePalette;
    private int veilColor = AssistantInk.NO_VEIL;
    private float veilRadiusPx = -1f;
    private final int[] inkLocation = new int[2];
    private final int[] inkRootLocation = new int[2];
    private final Rect inkRect = new Rect();
    private final int[] hitLocation = new int[2];
    private final PaneRim rim = new PaneRim();
    private final AssistantChatView chat;
    private final AssistantChatsView chats;
    private final AssistantPageController controller;
    private AssistantUi.Palette palette;
    @Nullable private PaneSurfaceStyle style;

    public AssistantPageView(@NonNull Context context) {
        super(context);
        setClipChildren(true);
        glass = new PaneGlassBackdropView(context);
        glass.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        glass.setVisibility(GONE);
        addView(glass, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        PaneGlass.followLayout(glass);
        veil = new View(context);
        veil.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        veil.setVisibility(GONE);
        addView(veil, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        palette = AssistantUi.Palette.read(this);
        // The thread's buttons call the controller, which needs the views: made first over a holder.
        final AssistantPageController[] holder = new AssistantPageController[1];
        chat = new AssistantChatView(context, palette, new AssistantThreadAdapter.Actions() {
            @Override public void copy(@NonNull String text) { holder[0].copy(text); }
            @Override public void share(@NonNull String text) { holder[0].share(text); }
            @Override public void retry(long messageId) { holder[0].retry(messageId); }
            @Override public void errorAction(@NonNull AssistantRows.Row row) { holder[0].errorAction(row); }
            @Override public void useE2b(@NonNull AssistantRows.Row row) { holder[0].useE2b(row); }
        });
        chats = new AssistantChatsView(context, palette);
        addView(chat, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        addView(chats, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        controller = new AssistantPageController(context, this, chat, chats, palette);
        holder[0] = controller;
        applyStyle(null);
        // The views were made with the theme's roles; the first dress always reaches them.
        controller.applyPalette(palette);
    }

    /**
     * Dress the page from the surface style, the same values the panes beside it read. Run again on
     * every style change; it also reads the colour roles again, so a scheme change reaches the chat.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        this.style = style;
        float density = getResources().getDisplayMetrics().density;
        float radiusPx = style == null ? 24f * density : PaneGlass.radiusPx(style, density);
        boolean slab = PaneGlass.apply(style, this, glass, radiusPx);
        themePalette = AssistantUi.Palette.read(this);
        // The pane's roles on the page's ink polarity, as a Home widget's card takes them.
        com.termux.app.chrome.WidgetInkSource ink = inkSource(style);
        AssistantUi.Palette dressed = AssistantInk.onSide(themePalette,
            AssistantInk.darkSide(themePalette, ink == null ? null : ink.widgetPolarity()));
        // Glass off: a solid pane in surfaceContainerHigh.
        setBackground(slab ? null : AssistantUi.fill(dressed.surfaceHigh, radiusPx));
        setPaneShape(radiusPx, true);
        if (slab || PaneRim.plainBorderWanted(style)) rim.apply(this, slab, radiusPx, PaneBorderStyle.lone(), style);
        else rim.clear(this);
        measureInk(slab, radiusPx, dressed);
        if (!AssistantInk.same(palette, dressed)) {
            palette = dressed;
            controller.applyPalette(palette);
        }
    }

    /** The chrome's widget band, through the Look the style carries; null with nothing to ask. */
    @Nullable
    private static com.termux.app.chrome.WidgetInkSource inkSource(@Nullable PaneSurfaceStyle style) {
        return style == null ? null : style.lookGlass();
    }

    /**
     * Asks the widget band what veil the pane lays over its glass where it is laid out, so its
     * weakest text holds the body target over the wallpaper under it (ADR 0014;
     * {@link AssistantInk#resolve}). Memoised by the band on the rect; asked on a dress pass and
     * when the pane moves, never per frame.
     */
    private void measureInk(boolean slab, float radiusPx, @NonNull AssistantUi.Palette dressed) {
        com.termux.app.chrome.WidgetInkSource ink = inkSource(style);
        int color = AssistantInk.NO_VEIL;
        if (slab && ink != null && style != null && getWidth() > 0 && getHeight() > 0) {
            com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(this, inkLocation, inkRootLocation);
            inkRect.set(inkLocation[0], inkLocation[1], inkLocation[0] + getWidth(),
                inkLocation[1] + getHeight());
            color = ink.widgetInk(inkRect, style.paneGlassTintColor(),
                AssistantInk.veilColor(dressed), dressed.onSurfaceVariant, dressed.primary).veil;
        }
        if (color == veilColor && radiusPx == veilRadiusPx) return;
        veilColor = color;
        veilRadiusPx = radiusPx;
        boolean shown = android.graphics.Color.alpha(color) > 0;
        veil.setBackground(shown ? AssistantUi.fill(color, radiusPx) : null);
        AssistantUi.setVisible(veil, shown);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The veil belongs to the laid-out rect: measured again once there is one.
        if (w > 0 && h > 0) post(() -> applyStyle(style));
    }

    /**
     * Whether a sideways swipe that starts at the screen point ({@code rawX}, {@code rawY}) pages
     * the wall: anywhere on the page but over a view that scrolls sideways itself (a code block,
     * the recent chats rail), which keeps it.
     */
    public boolean bodySwipePagesWall(float rawX, float rawY) {
        return !scrollsSideways(this, rawX, rawY);
    }

    private boolean scrollsSideways(@NonNull View view, float rawX, float rawY) {
        if (view.getVisibility() != VISIBLE) return false;
        view.getLocationOnScreen(hitLocation);
        if (rawX < hitLocation[0] || rawY < hitLocation[1]
            || rawX >= hitLocation[0] + view.getWidth()
            || rawY >= hitLocation[1] + view.getHeight()) return false;
        if (view != this && (view.canScrollHorizontally(1) || view.canScrollHorizontally(-1))) {
            return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            if (scrollsSideways(group.getChildAt(i), rawX, rawY)) return true;
        }
        return false;
    }

    /** The wall moved this page, or the wallpaper panned under it: the slab re-aims. */
    public void onWallMoved() {
        glass.invalidateGlassPosition();
    }

    /** How much of the page's rim the wall's slide leaves showing. */
    public void setOutlineTravelAlpha(float alpha) {
        rim.setTravelAlpha(alpha);
    }

    @Nullable private AssistantFieldKeys fieldKeys;

    /** Whether one of the page's text fields holds focus, which is when the launcher's keyboard types into it. */
    public boolean isTyping() {
        return chat.composer().isTyping() || chats.isSearchTyping();
    }

    /**
     * The in-app keyboard's interceptor for this page: its keys edit whichever field has focus.
     * Enter sends from the composer and does nothing in the search.
     */
    @NonNull
    public com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler.KeyValueInterceptor typingKeys() {
        if (fieldKeys == null) {
            AssistantComposer composer = chat.composer();
            fieldKeys = new AssistantFieldKeys(
                () -> chats.isSearchTyping() ? chats.search()
                    : composer.isTyping() ? composer.field() : null,
                field -> { if (field == composer.field()) composer.submit(); },
                com.termux.app.terminal.ClipboardText.forContext(getContext()));
        }
        return fieldKeys;
    }

    /** Back closes the Chats list first; false when there is nothing for it to do. */
    public boolean handleBack() {
        return controller.handleBack();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (controller == null) return;
        if (isVisible) controller.onShown();
        else controller.onHidden();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) controller.onWindowFocusGained();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // A dress may have come before there was a window to read the theme from.
        applyStyle(style);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        rim.cancel();
        controller.onHidden();
    }
}
