package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.launcher.drawer.AppDrawerCategoryClassifier;

import java.io.File;
import java.io.IOException;

/**
 * The drawer's USER-stage answer, read from two places at once: the in-app override store and the
 * hand-editable {@code app-categories.conf}.
 *
 * <p><b>Precedence.</b> An explicit in-app drag wins over the file, always. Dragging an icon into a
 * category is a stated intent by a person holding the phone; a generated or scripted file may not
 * overrule it. The file only answers for packages the user never placed by hand.
 *
 * <p><b>Unknown sections.</b> A section name outside the current taxonomy resolves to null — "no
 * opinion" — and the classifier falls through to its later stages. It never degrades to
 * {@code other}, because free-form category titles land later and this class must not start
 * discarding those names now.
 */
public final class LauncherCategoryAssignmentSource
    implements AppDrawerCategoryClassifier.OverrideLookup {

    @NonNull private final LauncherCategoryOverrideStore overrides;
    @NonNull private final File categoryFile;

    @Nullable private LauncherCategoryFile parsed;
    private boolean loaded;
    private long cachedLastModified;
    private long cachedLength;

    public LauncherCategoryAssignmentSource(@NonNull LauncherCategoryOverrideStore overrides) {
        this(overrides, LauncherCategoryFile.defaultFile());
    }

    public LauncherCategoryAssignmentSource(@NonNull LauncherCategoryOverrideStore overrides,
                                            @NonNull File categoryFile) {
        this.overrides = overrides;
        this.categoryFile = categoryFile;
    }

    @Override
    @Nullable
    public AppDrawerCategory categoryForPackage(@NonNull String packageName) {
        AppDrawerCategory dragged = resolve(overrides.get(packageName));
        if (dragged != null) return dragged;
        LauncherCategoryFile file = file();
        if (file == null) return null;
        return resolve(file.categoryForPackage(packageName));
    }

    /** Forces the next read to hit the disk; call after writing the file. */
    public synchronized void invalidate() {
        loaded = false;
        cachedLastModified = 0L;
        cachedLength = 0L;
    }

    /** @return the parsed file, or null when it is absent or has never parsed cleanly. */
    @Nullable
    private synchronized LauncherCategoryFile file() {
        // Two stats per drawer open, not a parse: the cached copy stays valid until the file's
        // mtime or size moves. A missing file caches as a null parse for the same reason, so an
        // unconfigured device does not stat-storm on every classify pass.
        long lastModified = categoryFile.lastModified();
        long length = categoryFile.length();
        if (loaded && lastModified == cachedLastModified && length == cachedLength) return parsed;
        cachedLastModified = lastModified;
        cachedLength = length;
        loaded = true;
        if (!categoryFile.isFile()) {
            parsed = null;
            return null;
        }
        try {
            parsed = LauncherCategoryFile.parse(categoryFile);
        } catch (IOException ignored) {
            // A half-written or unreadable file is not a reason to forget what the user assigned:
            // keep the last good parse and try again once the file changes.
        }
        return parsed;
    }

    @Nullable
    private static AppDrawerCategory resolve(@Nullable String slug) {
        if (slug == null) return null;
        AppDrawerCategory category = AppDrawerCategory.fromSlug(slug.trim());
        return category == null || category.synthetic ? null : category;
    }
}
