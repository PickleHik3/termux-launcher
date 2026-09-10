package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every place's arrangement, in both orientations, as one immutable value: what the surface editor
 * took a copy of on entry, what its revert puts back, and — folded to a string — how it knows a bar
 * has been moved since.
 *
 * <p>Read and written through {@link PlaceLayoutStore}'s own accessors rather than the raw keys, so
 * a restore writes the value the place was resolving to whether or not it had a scoped key of its
 * own. That materialises a key the shared layer was answering for, at the value it was answering
 * with: the same arrangement, spelled out.
 *
 * <p>Pure: a store in, a value out, no views, so revert and dirtiness are testable without a window.
 */
public final class PlaceArrangeSnapshot {

    /** One place and orientation's arrangement, exactly as the store answers for it. */
    private static final class Entry {
        final PaneWallPage place;
        final PlaceOrientation orientation;
        final PlaceLayout.Edge statusBarEdge;
        final PlaceLayout.RowPlacement appsRow;
        final boolean azRowShown;
        final PlaceLayout.Edge azBarEdge;
        final PlaceLayout.RowPlacement extraKeys;
        final PlaceLayout.KeyboardMode keyboardMode;
        final PlaceLayout.KeyboardForm keyboardForm;
        final int widgetColumns;
        final int widgetRows;

        Entry(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
              @NonNull PlaceOrientation orientation) {
            this.place = place;
            this.orientation = orientation;
            statusBarEdge = places.statusBarEdge(place, orientation);
            appsRow = places.appsRow(place, orientation);
            azRowShown = places.azRowShown(place, orientation);
            azBarEdge = places.azBarEdge(place, orientation);
            extraKeys = places.extraKeys(place, orientation);
            keyboardMode = places.keyboardMode(place, orientation);
            keyboardForm = places.keyboardForm(place, orientation);
            widgetColumns = places.widgetColumns(place, orientation);
            widgetRows = places.widgetRows(place, orientation);
        }

        void restore(@NonNull PlaceLayoutStore places) {
            places.setStatusBarEdge(place, orientation, statusBarEdge);
            places.setAppsRow(place, orientation, appsRow);
            places.setAzRowShown(place, orientation, azRowShown);
            places.setAzBarEdge(place, orientation, azBarEdge);
            places.setExtraKeys(place, orientation, extraKeys);
            places.setKeyboardMode(place, orientation, keyboardMode);
            places.setKeyboardForm(place, orientation, keyboardForm);
            places.setWidgetColumns(place, orientation, widgetColumns);
            places.setWidgetRows(place, orientation, widgetRows);
        }

        void appendTo(@NonNull StringBuilder out) {
            out.append(place.name()).append('.').append(orientation.storageValue()).append(':')
                .append(statusBarEdge).append(',')
                .append(appsRow).append(',')
                .append(azRowShown).append(',')
                .append(azBarEdge).append(',')
                .append(extraKeys).append(',')
                .append(keyboardMode).append(',')
                .append(keyboardForm).append(',')
                .append(widgetColumns).append('x').append(widgetRows).append('|');
        }
    }

    @NonNull private final List<Entry> mEntries;
    /** What each place remembers once, rather than per orientation: how it wants its keyboard. */
    @NonNull private final List<PaneWallPage> mPlaces;
    @NonNull private final List<KeyboardOnEnter> mKeyboardOnEnter;

    private PlaceArrangeSnapshot(@NonNull List<Entry> entries, @NonNull List<PaneWallPage> places,
                                 @NonNull List<KeyboardOnEnter> keyboardOnEnter) {
        mEntries = Collections.unmodifiableList(entries);
        mPlaces = Collections.unmodifiableList(places);
        mKeyboardOnEnter = Collections.unmodifiableList(keyboardOnEnter);
    }

    /** Every place, both orientations: a rotation mid-edit moves which one the editor writes. */
    @NonNull
    public static PlaceArrangeSnapshot capture(@NonNull PlaceLayoutStore places) {
        List<Entry> entries = new ArrayList<>();
        List<PaneWallPage> pages = new ArrayList<>();
        List<KeyboardOnEnter> onEnter = new ArrayList<>();
        for (PaneWallPage place : PaneWallPage.values()) {
            pages.add(place);
            onEnter.add(places.keyboardOnEnter(place));
            for (PlaceOrientation orientation : PlaceOrientation.values())
                entries.add(new Entry(places, place, orientation));
        }
        return new PlaceArrangeSnapshot(entries, pages, onEnter);
    }

    /** Puts every place's arrangement back the way {@link #capture} found it. */
    public void restore(@NonNull PlaceLayoutStore places) {
        for (Entry entry : mEntries) entry.restore(places);
        for (int i = 0; i < mPlaces.size(); i++)
            places.setKeyboardOnEnter(mPlaces.get(i), mKeyboardOnEnter.get(i));
    }

    /** The whole arrangement as one string, for the editor's unsaved-changes comparison. */
    @NonNull
    public String signature() {
        StringBuilder out = new StringBuilder(256);
        for (Entry entry : mEntries) entry.appendTo(out);
        for (int i = 0; i < mPlaces.size(); i++)
            out.append(mPlaces.get(i).name()).append('=')
                .append(mKeyboardOnEnter.get(i).storageValue()).append('|');
        return out.toString();
    }
}
