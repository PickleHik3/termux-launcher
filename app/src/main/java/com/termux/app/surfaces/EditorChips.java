package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two chips at the top of every card (ADR 0017): Where it applies (Everywhere or one page)
 * and What it applies to (All glass or one element). Together they name the grid cell a card's
 * rows read and write. Pure: the caller says which page the editor shows and which elements it has.
 */
public final class EditorChips {

    /** The page the Where chip stands on, or {@link LookGrid#NO_PAGE} for Everywhere. */
    public final int page;
    /** The element the What chip stands on, or null for All glass. */
    @Nullable public final LookGrid.Element element;

    public EditorChips(int page, @Nullable LookGrid.Element element) {
        this.page = page < 0 ? LookGrid.NO_PAGE : page;
        this.element = element;
    }

    /** The element a card tunes, or null for the page cards and the Appearance card. */
    @Nullable
    public static LookGrid.Element elementOf(@Nullable EditorCard card) {
        return card == null || card.slot == null ? null : card.slot.element;
    }

    /** Whether a card is a page card (Home, Terminal page): it opens on its page. */
    public static boolean isPageCard(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /**
     * Where a card opens: an element's card on (Everywhere, its element), a page card on (its
     * page, All glass), the Appearance card on (Everywhere, All glass).
     */
    @NonNull
    public static EditorChips openFor(@Nullable EditorCard card, int pageId) {
        if (isPageCard(card)) return new EditorChips(pageId, null);
        return new EditorChips(LookGrid.NO_PAGE, elementOf(card));
    }

    /** Whether the card offers the What chip; the Appearance card does not. */
    public static boolean offersWhat(@Nullable EditorCard card) {
        return card != null && card != EditorCard.APPEARANCE;
    }

    /** The Where chip's options: Everywhere, then the page the editor shows when one is known. */
    @NonNull
    public static List<Integer> whereOptions(int pageId) {
        List<Integer> out = new ArrayList<>();
        out.add(LookGrid.NO_PAGE);
        if (pageId >= 0) out.add(pageId);
        return out;
    }

    /**
     * The What chip's options: All glass (null), then the card's element; a page card offers
     * every element its page shows ({@code pageElements}); the Appearance card none.
     */
    @NonNull
    public static List<LookGrid.Element> whatOptions(@Nullable EditorCard card,
                                                     @NonNull List<LookGrid.Element> pageElements) {
        if (!offersWhat(card)) return Collections.emptyList();
        List<LookGrid.Element> out = new ArrayList<>();
        out.add(null);
        LookGrid.Element own = elementOf(card);
        if (own != null) out.add(own);
        else
            for (LookGrid.Element element : pageElements)
                if (!out.contains(element)) out.add(element);
        return out;
    }

    @NonNull
    public EditorChips withPage(int pageId) {
        return new EditorChips(pageId, element);
    }

    @NonNull
    public EditorChips withElement(@Nullable LookGrid.Element value) {
        return new EditorChips(page, value);
    }

    /** Whether Where stands on a page rather than Everywhere. */
    public boolean onPage() {
        return page >= 0;
    }

    /**
     * The cell the chips name for {@code property}: a property without an element column (Margin,
     * Vibrancy) drops the element, as the grid stores it.
     */
    @NonNull
    public LookGrid.Key cell(@NonNull LookGrid.Property property) {
        return new LookGrid.Key(property, property.perElement ? element : null, page);
    }

    /**
     * Whether the What chip greys {@code property}'s row: a property with no element column while
     * the chip names an element (the row then writes All glass and says so).
     */
    public boolean elementIgnored(@NonNull LookGrid.Property property) {
        return element != null && !property.perElement;
    }

    /** Whether these chips stand where {@code card} opens: the editor's plain writes apply. */
    public boolean isOpening(@Nullable EditorCard card) {
        EditorChips open = openFor(card, page);
        return open.page == page && open.element == element;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EditorChips)) return false;
        EditorChips that = (EditorChips) other;
        return page == that.page && element == that.element;
    }

    @Override public int hashCode() {
        return page * 31 + (element == null ? 0 : element.hashCode());
    }

    @NonNull @Override public String toString() {
        return (page < 0 ? "everywhere" : "page " + page) + " · "
            + (element == null ? "all glass" : element.key);
    }
}
