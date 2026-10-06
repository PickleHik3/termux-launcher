package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

/**
 * Picks one font file through the system document picker, with the device's whole shared storage
 * on offer rather than only Downloads.
 *
 * <p>The picker lists only the storage roots that can serve the requested MIME types. With font
 * types, the media roots (Images, Audio, Documents, Recent) drop out, which is fine, but the
 * primary "Internal storage" root is also an <em>advanced</em> root
 * ({@code DocumentsContract.Root.FLAG_ADVANCED}) that the picker hides unless the caller asks for
 * it. That left Downloads as the only place a font could come from. {@link #EXTRA_SHOW_ADVANCED}
 * (a hidden {@code DocumentsContract} extra every file manager-style app sends) asks for it; both
 * spellings it has had are sent.
 *
 * <p>No persistable grant is requested: the caller copies the font into its own files directory
 * inside the result callback, so the keyboard never reads the content URI again, and a persisted
 * grant would only use up one of the app's limited slots on every pick.
 *
 * <p>{@link Intent#CATEGORY_OPENABLE} keeps virtual documents (ones that cannot be opened as a
 * byte stream) out of the list, since the copy step needs the bytes.
 */
public final class OpenFontDocument extends ActivityResultContracts.OpenDocument {

    /** {@code DocumentsContract.EXTRA_SHOW_ADVANCED} as current releases name it. */
    static final String EXTRA_SHOW_ADVANCED = "android.provider.extra.SHOW_ADVANCED";
    /** The name the same extra had in earlier releases, which some picker builds still read. */
    static final String EXTRA_SHOW_ADVANCED_LEGACY = "android.content.extra.SHOW_ADVANCED";

    /**
     * Font types as providers report them. The external-storage provider maps .ttf/.otf to
     * {@code font/ttf} / {@code font/otf}; older providers and file managers use the
     * {@code application/x-font-*} names, and some have no mapping at all and say
     * {@code application/octet-stream}. The copy step rejects anything that is not a font.
     */
    static final String[] FONT_MIME_TYPES = {
        "font/ttf", "font/otf", "font/*",
        "application/x-font-ttf", "application/x-font-otf",
        "application/font-sfnt", "application/vnd.ms-opentype",
        "application/octet-stream"
    };

    /** The types to launch this contract with. */
    @NonNull
    static String[] mimeTypes() {
        return FONT_MIME_TYPES.clone();
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
        Intent intent = super.createIntent(context, input);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(EXTRA_SHOW_ADVANCED, true);
        intent.putExtra(EXTRA_SHOW_ADVANCED_LEGACY, true);
        return intent;
    }
}
