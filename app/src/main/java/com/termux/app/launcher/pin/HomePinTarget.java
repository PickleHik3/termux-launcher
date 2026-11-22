package com.termux.app.launcher.pin;

import android.appwidget.AppWidgetProviderInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.WidgetGridDefinition;

/**
 * Home, as the confirm card sees it: the thing that puts an item
 * on a page through the Home model. The Home pane implements it; the activity lends it to
 * {@link HomePinHandoff} while it is started.
 */
public interface HomePinTarget {

    /** How a widget add ended. */
    interface WidgetResult {
        /** The widget is on Home, bound as {@code appWidgetId}; {@code newPage} when it made a page. */
        void onPlaced(int appWidgetId, boolean newPage);

        /** Nothing was added: declined, failed, or no room. The pane has already said why. */
        void onFailed();
    }

    /** Home's grid, for the card's size line and its fit check. */
    @NonNull WidgetGridDefinition grid();

    /** The cells {@code info} takes on Home's grid, as {columns, rows}. */
    @NonNull int[] spanFor(@NonNull AppWidgetProviderInfo info);

    /**
     * Puts a shortcut on Home: the page in front when it has room, else the first that has, else
     * a new page. Returns {key, page, newPage as 0 or 1}, the page counting from 0, or null when
     * it could not be written.
     */
    @Nullable int[] placeShortcut(@NonNull String owner, long profileSerial,
                                  @NonNull Bundle options);

    /** Takes back a shortcut {@link #placeShortcut} just placed, in one commit. */
    boolean removeShortcut(int key);

    /**
     * Binds {@code info} and puts it on Home in a free area of its size, through the widget add
     * path (bind consent and configuration included, which leave for other apps' screens);
     * {@code result} is told when that ends.
     */
    void placeWidget(@NonNull AppWidgetProviderInfo info, @NonNull WidgetResult result);

    /** Whether Home's layout is locked ({@code HomeSettings.KEY_LAYOUT_LOCKED}). */
    boolean layoutLocked();

    /** Unlocks Home's layout, as the card's Unlock does. */
    void unlockLayout();

    /** Says {@code text} on Home, the way Home's own notices are. */
    void notice(@Nullable String text);
}
