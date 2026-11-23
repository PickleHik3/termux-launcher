package com.termux.app.terminal.inappkeyboard;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/**
 * How a text layout reaches the keys: which id is active in a ring, the options every text layout
 * is modified with, and the layout data for an id (the user's file first, else the bundled
 * resource). The live keyboard and the Keyboard theme preview both resolve through here, so the
 * rows and keys per row the user paints are the rows and keys they type on.
 */
public final class InAppKeyboardLayoutSource {

    private InAppKeyboardLayoutSource() {
    }

    /** The options every text layout is modified with, for the stored extra-keys value. */
    @NonNull
    public static LayoutModifier.LayoutOptions textLayoutOptions(
        @Nullable String extraKeysStoredValue) {
        return new LayoutModifier.LayoutOptions(true, false, true,
            InAppKeyboardExtraKeys.resolve(extraKeysStoredValue));
    }

    /** The stored active id when the ring still carries it, else the ring's first entry. */
    @NonNull
    public static String activeId(@NonNull List<String> ring, @Nullable String storedActive) {
        return storedActive != null && ring.contains(storedActive) ? storedActive : ring.get(0);
    }

    /** The bundled resource behind {@code id}; the launcher QWERTY for ids with none. */
    public static int bundledResId(@NonNull Resources resources, @Nullable String id) {
        LauncherKeyboardLayouts.Layout layout = LauncherKeyboardLayouts.find(resources, id);
        return layout == null || layout.xmlResId == 0
            ? juloo.keyboard2.R.xml.termux_launcher_qwerty : layout.xmlResId;
    }

    /** A bundled layout resource with the text-layout options applied, or null if unparsable. */
    @Nullable
    public static KeyboardData loadBundled(@NonNull Resources resources, int resourceId,
                                           @NonNull LayoutModifier.LayoutOptions options) {
        KeyboardData keyboard = KeyboardData.load(resources, resourceId);
        return keyboard == null ? null : LayoutModifier.modify(keyboard, options, resources);
    }

    /** Layout XML text with the text-layout options applied; throws when it does not parse. */
    @NonNull
    public static KeyboardData parseText(@NonNull Resources resources, @NonNull String xml,
                                         @NonNull LayoutModifier.LayoutOptions options)
        throws Exception {
        return LayoutModifier.modify(KeyboardData.load_string_exn(xml), options, resources);
    }

    /**
     * The layout {@code id} would show: the user's file under {@code home} when it exists and
     * parses, else the bundled layout. Null only when nothing parses at all.
     */
    @Nullable
    public static KeyboardData load(@NonNull Resources resources, @NonNull File home,
                                    @Nullable String id,
                                    @NonNull LayoutModifier.LayoutOptions options) {
        File file = KeyboardLayoutFiles.overrideFile(home, id);
        if (file != null && file.isFile()
            && file.length() <= TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES) {
            try {
                return parseText(resources,
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), options);
            } catch (Exception ignored) {
                // A broken file falls back to the bundled layout, as the live loader does.
            }
        }
        return loadBundled(resources, bundledResId(resources, id), options);
    }
}
