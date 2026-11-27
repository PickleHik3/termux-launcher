package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardLayoutSource;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;
import com.termux.app.terminal.inappkeyboard.KeyboardLayoutFiles;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;

import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.Theme;

/**
 * Builds the settings pages' keyboard preview: a {@link Keyboard2View} drawn with the user's
 * colour scheme, font and shape on the layout they type on. The Keyboard theme page and the
 * Layouts page both build it here, so the two always look the same.
 */
public final class KeyboardPreviewFactory {

    private KeyboardPreviewFactory() {
    }

    /**
     * A preview of {@code layoutId} (the user's file first, else the bundled layout). Key taps
     * do nothing until the caller sets a listener. Config is immutable, so a new font needs a
     * new view.
     */
    @NonNull
    static Keyboard2View create(@NonNull Context context,
                                @NonNull TermuxAppSharedPreferences preferences,
                                @NonNull InAppKeyboardColorScheme scheme,
                                @Nullable String layoutId) {
        return build(context, preferences, scheme, layoutId, new Config.IKeyEventHandler() {
            @Override public void key_down(KeyValue value, boolean isSwipe) {}
            @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
            @Override public void mods_changed(Pointers.Modifiers modifiers) {}
            @Override public void suggestion_entered(String text) {}
        }, false);
    }

    /**
     * The same keyboard for a screen of its own to type with (the Linux windows screen): its keys
     * go to {@code handler}, with the user's haptics and key sound.
     */
    @NonNull
    public static Keyboard2View createTyping(@NonNull Context context,
                                             @NonNull TermuxAppSharedPreferences preferences,
                                             @NonNull InAppKeyboardColorScheme scheme,
                                             @Nullable String layoutId,
                                             @NonNull Config.IKeyEventHandler handler) {
        return build(context, preferences, scheme, layoutId, handler, true);
    }

    @NonNull
    private static Keyboard2View build(@NonNull Context context,
                                       @NonNull TermuxAppSharedPreferences preferences,
                                       @NonNull InAppKeyboardColorScheme scheme,
                                       @Nullable String layoutId,
                                       @NonNull Config.IKeyEventHandler handler,
                                       boolean feedback) {
        Config.Builder config = new Config.Builder(context.getResources(), handler);
        config.hapticEnabled = feedback && preferences.isInAppKeyboardHapticsEnabled()
            && preferences.isAppHapticsEnabled();
        config.keySoundEnabled = feedback && preferences.isInAppKeyboardKeySoundEnabled();
        config.labelFont = labelFont(context, preferences.getInAppKeyboardFontPath());
        config.symbolFont = com.termux.shared.termux.font.NerdFontSpans.typeface(context);
        Keyboard2View keyboard = new Keyboard2View(context, config.build(),
            palette(context, preferences, scheme));
        // The layout the user types on, so every key can be painted.
        KeyboardData layout = InAppKeyboardLayoutSource.load(context,
            KeyboardLayoutFiles.defaultHome(), layoutId,
            InAppKeyboardLayoutSource.textLayoutOptions(preferences.getInAppKeyboardExtraKeys()));
        if (layout != null)
            keyboard.setKeyboard(layout);
        // The height is the layout's, in the orientation the phone is being held in.
        keyboard.setHeightScale(new PlaceLayoutStore(preferences).keyboardHeightScale(
            PlaceOrientation.of(context.getResources().getConfiguration())));
        keyboard.setKeyMarginScale(preferences.getInAppKeyboardKeyMarginScale());
        float radiusDp = preferences.getInAppKeyboardKeyCornerRadiusDp();
        keyboard.setKeyCornerRadiusOverride(radiusDp < 0f ? -1f
            : radiusDp * context.getResources().getDisplayMetrics().density);
        keyboard.setKeyColorOverrides(scheme.resolvedOverrides());
        return keyboard;
    }

    /** Glass preview palette: the imported palette when active, then the background override. */
    @NonNull
    static Theme.Palette palette(@NonNull Context context,
                                 @NonNull TermuxAppSharedPreferences preferences,
                                 @NonNull InAppKeyboardColorScheme scheme) {
        String theme = preferences.getInAppKeyboardTheme();
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, theme,
            com.termux.app.chrome.GlassLook.of(preferences));
        if (scheme.shouldApplyImportedPalette(theme))
            palette = scheme.applyToPalette(palette);
        // Production paints this color into the activity's glass backdrop behind a transparent
        // keyboard; the preview has no backdrop, so its palette carries the color itself.
        Integer background = scheme.resolvedKeyboardBackground();
        return background == null ? palette
            : KeyboardColorSchemeFragment.withKeyboardBackground(palette, background);
    }

    /** The face the keyboard itself would use: the picked file, else the bundled symbols font. */
    @Nullable
    private static Typeface labelFont(@NonNull Context context, @Nullable String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            File file = new File(fontPath);
            if (file.isFile()) {
                try {
                    Typeface typeface = com.termux.shared.termux.font.FileTypefaces.load(file);
                    if (typeface != null && !Typeface.DEFAULT.equals(typeface)) return typeface;
                } catch (RuntimeException ignored) {
                    // Falls through to the bundled face, like the keyboard does.
                }
            }
        }
        return com.termux.shared.termux.font.NerdFontSpans.typeface(context);
    }
}
