package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/** One classified package: which category, which pipeline stage decided it, and how surely. */
public final class AppDrawerCategoryAssignment {

    /** Pipeline stages in precedence order; higher stages always beat lower ones. */
    public enum Source { USER, CURATED_FORCE, PLATFORM, CURATED_FILL, ROLE, HEURISTIC, DEFAULT }

    @NonNull public final AppDrawerCategory category;
    @NonNull public final Source source;
    public final float confidence;

    public AppDrawerCategoryAssignment(@NonNull AppDrawerCategory category,
                                       @NonNull Source source, float confidence) {
        this.category = category;
        this.source = source;
        this.confidence = confidence;
    }

    @NonNull @Override public String toString() {
        return category.slug + "[" + source + " " + confidence + "]";
    }
}
