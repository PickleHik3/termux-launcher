package com.termux.app.launcher.popup;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything {@link AnchoredMenu} needs to put one popup on screen.
 *
 * <p>{@link #content} is the panel's body, already built by the consumer. A text menu builds it out
 * of {@link MenuRowFactory} and declares its rows so the drag-to-highlight interaction can find
 * them; a bespoke surface (a folder's icon grid, a stack of notification cards) passes its own view
 * and no rows at all. Everything else here is sizing and material policy the module applies.
 */
public final class MenuSpec {

    /** The panel body. */
    @NonNull public final View content;
    /** Hue the glass panel and row highlights are tinted from (RGB; alpha ignored). */
    public final int tintBase;
    /** True to let the panel shrink to its content; false to pad it out to the minimum width. */
    public final boolean tightWrap;
    /** Exact panel width in px, clamped into [min, max]; -1 to measure the content. */
    public final int requestedWidth;
    /** Opacity floor in percent, for content that must stay readable over a transparent dock. */
    public final int minimumOpacityPercent;
    /** Whether the panel's scroll view shows its scrollbar. */
    public final boolean showVerticalScrollbar;
    /** Whether the panel fades and slides in when shown. */
    public final boolean animateEntry;
    /** Highlightable rows, in top-to-bottom order; empty for bespoke content. */
    @NonNull public final List<MenuRow> rows;
    /** Run when the window is dismissed, whatever dismissed it. */
    @Nullable public final Runnable onDismiss;

    private MenuSpec(@NonNull Builder builder) {
        this.content = builder.content;
        this.tintBase = builder.tintBase;
        this.tightWrap = builder.tightWrap;
        this.requestedWidth = builder.requestedWidth;
        this.minimumOpacityPercent = builder.minimumOpacityPercent;
        this.showVerticalScrollbar = builder.showVerticalScrollbar;
        this.animateEntry = builder.animateEntry;
        this.rows = builder.rows.isEmpty()
            ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.rows));
        this.onDismiss = builder.onDismiss;
    }

    @NonNull
    public static Builder of(@NonNull View content, int tintBase) {
        return new Builder(content, tintBase);
    }

    public static final class Builder {
        @NonNull private final View content;
        private final int tintBase;
        private boolean tightWrap = true;
        private int requestedWidth = -1;
        private int minimumOpacityPercent = 0;
        private boolean showVerticalScrollbar = true;
        private boolean animateEntry = true;
        @NonNull private List<MenuRow> rows = Collections.emptyList();
        @Nullable private Runnable onDismiss;

        private Builder(@NonNull View content, int tintBase) {
            this.content = content;
            this.tintBase = tintBase;
        }

        @NonNull public Builder tightWrap(boolean value) { this.tightWrap = value; return this; }
        @NonNull public Builder width(int px) { this.requestedWidth = px; return this; }
        @NonNull public Builder minimumOpacityPercent(int p) { this.minimumOpacityPercent = p; return this; }
        @NonNull public Builder verticalScrollbar(boolean v) { this.showVerticalScrollbar = v; return this; }
        @NonNull public Builder animateEntry(boolean v) { this.animateEntry = v; return this; }
        @NonNull public Builder rows(@NonNull List<MenuRow> v) { this.rows = v; return this; }
        @NonNull public Builder onDismiss(@Nullable Runnable v) { this.onDismiss = v; return this; }

        @NonNull public MenuSpec build() { return new MenuSpec(this); }
    }
}
