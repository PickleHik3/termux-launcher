package com.termux.app.assistant;

import android.content.Context;
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
        palette = AssistantUi.Palette.read(this);
        // Glass off: a solid pane in surfaceContainerHigh.
        setBackground(slab ? null : AssistantUi.fill(palette.surfaceHigh, radiusPx));
        setPaneShape(radiusPx, true);
        if (slab || PaneRim.plainBorderWanted(style)) rim.apply(this, slab, radiusPx, PaneBorderStyle.lone(), style);
        else rim.clear(this);
        controller.applyPalette(palette);
    }

    /** The wall moved this page, or the wallpaper panned under it: the slab re-aims. */
    public void onWallMoved() {
        glass.invalidateGlassPosition();
    }

    /** How much of the page's rim the wall's slide leaves showing. */
    public void setOutlineTravelAlpha(float alpha) {
        rim.setTravelAlpha(alpha);
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
