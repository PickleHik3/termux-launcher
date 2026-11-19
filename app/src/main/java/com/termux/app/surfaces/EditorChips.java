package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * The grid cell a card's glass rows read and write: the card's element's own cell, or Global's
 * for a card with no element (the Global card). The chrome wears one look on every page
 * (ADR 0031), so no card names a page or a kind of page. Pure.
 */
public final class EditorChips {

    /** The element the card tunes, or null for all glass (the Global card). */
    @Nullable public final LookGrid.Element element;

    public EditorChips(@Nullable LookGrid.Element element) {
        this.element = element;
    }

    /** The element a card tunes, or null for a card with no element. */
    @Nullable
    public static LookGrid.Element elementOf(@Nullable EditorCard card) {
        return card == null || card.slot == null ? null : card.slot.element;
    }

    /** Whether a card is a page card (Home, Workspace). */
    public static boolean isPageCard(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /** The cell {@code card}'s glass rows stand on. */
    @NonNull
    public static EditorChips openFor(@Nullable EditorCard card) {
        return new EditorChips(elementOf(card));
    }

    /**
     * The cell a row of {@code property} reads and writes: the element's own, or Global's for a
     * property with no element column (Margin, Vibrancy) or a card with no element.
     */
    @NonNull
    public LookGrid.Key cell(@NonNull LookGrid.Property property) {
        return new LookGrid.Key(property, property.perElement ? element : null, LookGrid.NO_PAGE);
    }

    /**
     * Whether the chips grey {@code property}'s row: a property with no element column while the
     * chips name an element (the row then writes Global and says so).
     */
    public boolean elementIgnored(@NonNull LookGrid.Property property) {
        return element != null && !property.perElement;
    }

    @Override public boolean equals(Object other) {
        return other instanceof EditorChips && element == ((EditorChips) other).element;
    }

    @Override public int hashCode() {
        return element == null ? 0 : element.hashCode();
    }

    @NonNull @Override public String toString() {
        return element == null ? "all glass" : element.key;
    }
}
