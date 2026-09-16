package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * One place's arrangement, already resolved for one orientation: what stands on which edge, whether
 * the alphabets row rides along, how the keyboard behaves and how big the widget grid is.
 *
 * <p>Immutable and value-equal, so a caller can hold the last one it applied and compare rather
 * than re-deriving the chrome on every pass. Everything that decides <em>what is on screen and
 * where</em> for a place lives here; {@link PlaceLayoutStore} is the only thing that builds one.
 */
public final class PlaceLayout {

    /** A screen edge. The status bar always stands on one of them — it is never hidden. */
    public enum Edge {
        TOP, BOTTOM, LEFT, RIGHT;

        /** A column down one side of the screen rather than a row along the top or the bottom. */
        public boolean isOnSide() {
            return this == LEFT || this == RIGHT;
        }

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static Edge parse(@Nullable String value, @NonNull Edge fallback) {
            if (value != null) {
                for (Edge edge : values()) {
                    if (edge.storageValue().equals(value)) return edge;
                }
            }
            return fallback;
        }
    }

    /** Where a chrome row stands: along the bottom, as a column on one edge, or nowhere at all. */
    public enum RowPlacement {
        BOTTOM, LEFT, RIGHT, HIDDEN;

        /** A column on a screen edge rather than a row along the bottom. */
        public boolean isOnSide() {
            return this == LEFT || this == RIGHT;
        }

        public boolean isOnRight() {
            return this == RIGHT;
        }

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static RowPlacement parse(@Nullable String value, @NonNull RowPlacement fallback) {
            if (value != null) {
                for (RowPlacement placement : values()) {
                    if (placement.storageValue().equals(value)) return placement;
                }
            }
            return fallback;
        }
    }

    /** What an open keyboard does to the place under it. */
    public enum KeyboardMode {
        /** The place shrinks to whatever the keyboard leaves. */
        RESIZE,
        /** The keyboard floats over the place, which keeps its size. */
        OVERLAY;

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static KeyboardMode parse(@Nullable String value, @NonNull KeyboardMode fallback) {
            if (value != null) {
                for (KeyboardMode mode : values()) {
                    if (mode.storageValue().equals(value)) return mode;
                }
            }
            return fallback;
        }
    }

    /**
     * The shape the on-screen keyboard takes on this place: the full-width row along the bottom it
     * has always been, a narrower frame floating over the content, or that same bottom row parted
     * in the middle for two thumbs.
     *
     * <p>Not the same question as {@link KeyboardMode}, which says what an open keyboard does to
     * the place under it, and not a keyboard <em>layout</em>, which is how the keys are arranged.
     */
    public enum KeyboardForm {
        /** Full width along the bottom edge. */
        DOCKED,
        /** A narrower frame the user can move, always over the content. */
        FLOATING,
        /** Along the bottom, with every row parted at its midpoint. */
        SPLIT;

        @NonNull
        public String storageValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The type {@code delta} steps along the cycle — docked, floating, split, and round again.
         * A backward cycle key is the same call with {@code -1}.
         */
        @NonNull
        public KeyboardForm cycled(int delta) {
            KeyboardForm[] forms = values();
            int step = ((ordinal() + delta) % forms.length + forms.length) % forms.length;
            return forms[step];
        }

        @NonNull
        public static KeyboardForm parse(@Nullable String value, @NonNull KeyboardForm fallback) {
            if (value != null) {
                for (KeyboardForm form : values()) {
                    if (form.storageValue().equals(value)) return form;
                }
            }
            return fallback;
        }
    }

    /** Every element's slot: the whole truth about what stands where on this place. */
    @NonNull private final Map<Element, Slot> mSlots;

    @NonNull public final KeyboardMode keyboardMode;
    @NonNull public final KeyboardForm keyboardForm;
    public final int widgetColumns;
    public final int widgetRows;

    /**
     * The arrangement spelled the terse way: one edge or row placement per element, each landing
     * in the position that element has always been drawn at ({@link Element#defaultOrder}). It
     * cannot say "the top edge" for the pinned apps or the extra keys — the slots constructor is
     * the one that can — so it is a convenience, not the model.
     */
    public PlaceLayout(@NonNull Edge statusBarEdge, @NonNull RowPlacement appsRow,
                       boolean azRowShown, @NonNull Edge azBarEdge, @NonNull RowPlacement extraKeys,
                       @NonNull KeyboardMode keyboardMode, @NonNull KeyboardForm keyboardForm,
                       int widgetColumns, int widgetRows) {
        this(slotsOf(statusBarEdge, appsRow, azRowShown, azBarEdge, extraKeys), keyboardMode,
            keyboardForm, widgetColumns, widgetRows);
    }

    /** The arrangement as slots: an edge and a position for each of the four elements. */
    public PlaceLayout(@NonNull Map<Element, Slot> slots, @NonNull KeyboardMode keyboardMode,
                       @NonNull KeyboardForm keyboardForm, int widgetColumns, int widgetRows) {
        EnumMap<Element, Slot> copy = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            Slot slot = slots.get(element);
            if (slot == null) slot = Slot.on(Edge.BOTTOM, element);
            // The status bar is never hidden; the wall's pager needs something to ride.
            if (element == Element.STATUS && slot.hidden) slot = slot.withHidden(false);
            copy.put(element, slot);
        }
        mSlots = Collections.unmodifiableMap(copy);

        this.keyboardMode = keyboardMode;
        this.keyboardForm = keyboardForm;
        this.widgetColumns = widgetColumns;
        this.widgetRows = widgetRows;
    }

    /** Where one element stands on this place. Never null: every element always has a slot. */
    @NonNull
    public Slot slot(@NonNull Element element) {
        Slot slot = mSlots.get(element);
        return slot == null ? Slot.on(Edge.BOTTOM, element) : slot;
    }

    /** Every element's slot, in enum order. */
    @NonNull
    public Map<Element, Slot> slots() {
        return mSlots;
    }

    /** The same arrangement with one element moved — what a drag in progress reads against. */
    @NonNull
    public PlaceLayout withSlot(@NonNull Element element, @NonNull Slot slot) {
        EnumMap<Element, Slot> next = new EnumMap<>(mSlots);
        next.put(element, slot);
        return new PlaceLayout(next, keyboardMode, keyboardForm, widgetColumns, widgetRows);
    }

    @NonNull
    private static EnumMap<Element, Slot> slotsOf(@NonNull Edge statusBarEdge,
                                                  @NonNull RowPlacement appsRow, boolean azRowShown,
                                                  @NonNull Edge azBarEdge,
                                                  @NonNull RowPlacement extraKeys) {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(statusBarEdge, Element.STATUS));
        slots.put(Element.APPS, slotOf(appsRow, Element.APPS));
        slots.put(Element.AZ, azRowShown
            ? Slot.on(azBarEdge, Element.AZ)
            : Slot.hiddenFrom(azBarEdge, Element.AZ.defaultOrder(azBarEdge)));
        slots.put(Element.EXTRA_KEYS, slotOf(extraKeys, Element.EXTRA_KEYS));
        return slots;
    }

    @NonNull
    private static Slot slotOf(@NonNull RowPlacement placement, @NonNull Element element) {
        if (placement == RowPlacement.HIDDEN) {
            return Slot.hiddenFrom(Edge.BOTTOM, element.defaultOrder(Edge.BOTTOM));
        }
        Edge edge = placement == RowPlacement.LEFT ? Edge.LEFT
            : placement == RowPlacement.RIGHT ? Edge.RIGHT : Edge.BOTTOM;
        return Slot.on(edge, element);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof PlaceLayout)) return false;
        PlaceLayout that = (PlaceLayout) other;
        return widgetColumns == that.widgetColumns
            && widgetRows == that.widgetRows
            && keyboardMode == that.keyboardMode
            && keyboardForm == that.keyboardForm
            && mSlots.equals(that.mSlots);
    }

    @Override
    public int hashCode() {
        int result = mSlots.hashCode();
        result = 31 * result + keyboardMode.hashCode();
        result = 31 * result + keyboardForm.hashCode();
        result = 31 * result + widgetColumns;
        result = 31 * result + widgetRows;
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "PlaceLayout{status=" + slot(Element.STATUS)
            + ", apps=" + slot(Element.APPS)
            + ", az=" + slot(Element.AZ)
            + ", keys=" + slot(Element.EXTRA_KEYS)
            + ", keyboard=" + keyboardMode
            + ", form=" + keyboardForm
            + ", grid=" + widgetColumns + "x" + widgetRows
            + "}";
    }
}
