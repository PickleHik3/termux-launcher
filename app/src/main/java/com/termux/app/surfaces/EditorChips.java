package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The grid cells a card's rows read and write (appearance-cards-overhaul §1): the card's element
 * (none on Customization, Home and Workspace) and where it applies, Global, Home or Workspace.
 * Customization writes Base; Home the all-glass cell of Home pages; Workspace the all-glass cells
 * of Terminal and Display pages together. The status bar, the dock, the keyboard and Menus and
 * dialogs choose with their Applies to row; Widgets stand on Home alone and the terminal on the
 * Workspace alone. Pure.
 */
public final class EditorChips {

    /** Where a card's values apply: everywhere, Home pages, or Terminal and Display pages. */
    public enum AppliesTo {
        GLOBAL(),
        HOME(LookGrid.Kind.HOME),
        WORKSPACE(LookGrid.Kind.TERMINAL, LookGrid.Kind.DISPLAY);

        /** The kinds a write lands on, the one reads show first; empty for Global. */
        @NonNull public final List<LookGrid.Kind> kinds;

        AppliesTo(@NonNull LookGrid.Kind... kinds) {
            this.kinds = Collections.unmodifiableList(Arrays.asList(kinds));
        }
    }

    /** Where the values apply. */
    @NonNull public final AppliesTo appliesTo;
    /** The element the card tunes, or null for all glass (Customization, Home, Workspace). */
    @Nullable public final LookGrid.Element element;

    public EditorChips(@NonNull AppliesTo appliesTo, @Nullable LookGrid.Element element) {
        this.appliesTo = appliesTo;
        this.element = element;
    }

    /** The element a card tunes, or null for the three main cards. */
    @Nullable
    public static LookGrid.Element elementOf(@Nullable EditorCard card) {
        return card == null || card.slot == null ? null : card.slot.element;
    }

    /** Whether a card is a page card (Home, Workspace). */
    public static boolean isPageCard(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /** Whether the card shows the Applies to row: the status bar, dock, keyboard and sheets. */
    public static boolean offersAppliesTo(@Nullable EditorCard card) {
        return card == EditorCard.STATUS || card == EditorCard.DOCK
            || card == EditorCard.KEYBOARD || card == EditorCard.SHEETS;
    }

    /** Where a card without the Applies to row applies, or null for a card that has it. */
    @Nullable
    public static AppliesTo fixedFor(@Nullable EditorCard card) {
        if (card == null || card == EditorCard.APPEARANCE) return AppliesTo.GLOBAL;
        if (card == EditorCard.HOME || card == EditorCard.WIDGETS) return AppliesTo.HOME;
        if (card == EditorCard.WORKSPACE || card == EditorCard.TERMINAL) return AppliesTo.WORKSPACE;
        return offersAppliesTo(card) ? null : AppliesTo.GLOBAL;
    }

    /**
     * The cells {@code card} stands on: its fixed place, else {@code chosen} (the card's Applies
     * to choice this session; null is Global).
     */
    @NonNull
    public static EditorChips openFor(@Nullable EditorCard card, @Nullable AppliesTo chosen) {
        AppliesTo fixed = fixedFor(card);
        AppliesTo where = fixed != null ? fixed : chosen != null ? chosen : AppliesTo.GLOBAL;
        return new EditorChips(where, elementOf(card));
    }

    /** The Applies to row's options for {@code card}, in order; empty where it has none. */
    @NonNull
    public static List<AppliesTo> appliesToOptions(@Nullable EditorCard card) {
        return offersAppliesTo(card) ? Arrays.asList(AppliesTo.values())
            : Collections.<AppliesTo>emptyList();
    }

    @NonNull
    public EditorChips withAppliesTo(@NonNull AppliesTo value) {
        return new EditorChips(value, element);
    }

    /** Whether the chips stand on a kind of page (Home or Workspace) rather than Global. */
    public boolean onKind() {
        return appliesTo != AppliesTo.GLOBAL;
    }

    /** The kind reads show, or null for Global. */
    @Nullable
    public LookGrid.Kind kind() {
        return appliesTo.kinds.isEmpty() ? null : appliesTo.kinds.get(0);
    }

    /**
     * Every cell a write of {@code property} lands in: one per kind (Workspace writes Terminal and
     * Display pages), or the one everywhere cell. A property with no element column and no kind
     * row (Margin, Vibrancy) has one cell, Base.
     */
    @NonNull
    public List<LookGrid.Key> cells(@NonNull LookGrid.Property property) {
        List<LookGrid.Key> out = new ArrayList<>();
        if (!property.perElement || appliesTo.kinds.isEmpty()) {
            out.add(new LookGrid.Key(property, property.perElement ? element : null,
                LookGrid.NO_PAGE));
            return out;
        }
        for (LookGrid.Kind kind : appliesTo.kinds)
            out.add(new LookGrid.Key(property, element, kind));
        return out;
    }

    /** The cell reads of {@code property} show: the first of {@link #cells}. */
    @NonNull
    public LookGrid.Key cell(@NonNull LookGrid.Property property) {
        return cells(property).get(0);
    }

    /**
     * Whether the chips grey {@code property}'s row: a property with no element column (and no
     * kind row) while the chips name an element or a kind (the row then writes Base and says so).
     */
    public boolean elementIgnored(@NonNull LookGrid.Property property) {
        return (element != null || onKind()) && !property.perElement;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EditorChips)) return false;
        EditorChips that = (EditorChips) other;
        return appliesTo == that.appliesTo && element == that.element;
    }

    @Override public int hashCode() {
        return appliesTo.hashCode() * 31 + (element == null ? 0 : element.hashCode());
    }

    @NonNull @Override public String toString() {
        return appliesTo.name().toLowerCase(java.util.Locale.ROOT) + " · "
            + (element == null ? "all glass" : element.key);
    }
}
