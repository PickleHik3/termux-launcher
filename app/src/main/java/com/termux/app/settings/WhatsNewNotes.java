package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tour.TourEdition;

/**
 * Picks this build's release notes out of the two files bundled with the app
 * ({@code project-docs/release-notes.md}, the newest releases, and
 * {@code project-docs/prerelease-notes.md}, the pre-release channel's rolling list; see
 * {@code bundleReleaseNotes} in {@code app/build.gradle}). A pre-release build reads the rolling
 * list, a stable build its own {@code ## vX.Y.Z} section; a nightly's {@code +dev.<sha>} is
 * build metadata and does not change which release it follows.
 */
public final class WhatsNewNotes {

    private static final String RELEASES_URL =
        "https://github.com/PickleHik3/termux-launcher/releases/tag/";

    private WhatsNewNotes() {}

    /** One build's notes: Markdown body (no title) and where the full notes live online. */
    public static final class Notes {
        @NonNull public final String version;
        @NonNull public final String body;
        @NonNull public final String url;

        Notes(@NonNull String version, @NonNull String body, @NonNull String url) {
            this.version = version;
            this.body = body;
            this.url = url;
        }
    }

    /** {@code 1.1.0-pre.6+dev.abc} becomes {@code 1.1.0-pre.6}. */
    @NonNull
    public static String core(@NonNull String versionName) {
        int plus = versionName.indexOf('+');
        return (plus < 0 ? versionName : versionName.substring(0, plus)).trim();
    }

    /** The edition's tag for a version: {@code v1.0.6}, {@code nix-v1.0.6} or {@code vaj-v1.0.6}. */
    @NonNull
    public static String tag(@NonNull String versionName, @NonNull TourEdition edition) {
        String prefix = edition == TourEdition.NIX ? "nix-" : edition == TourEdition.VAJ ? "vaj-" : "";
        return prefix + "v" + core(versionName);
    }

    /**
     * @return the notes for {@code versionName}, or null when neither bundled file has any
     *     (a build ahead of its notes), in which case the screen offers the online link alone
     */
    @Nullable
    public static Notes select(@NonNull String versionName, @Nullable String stableMarkdown,
                               @Nullable String rollingMarkdown, @NonNull TourEdition edition) {
        String core = core(versionName);
        String body = core.contains("-pre")
            ? rollingBody(rollingMarkdown)
            : section(stableMarkdown, "## v" + core);
        if (body == null || body.isEmpty()) return null;
        return new Notes(core, body, RELEASES_URL + tag(versionName, edition));
    }

    /** The release page for a build, for when there are no bundled notes to show. */
    @NonNull
    public static String url(@NonNull String versionName, @NonNull TourEdition edition) {
        return RELEASES_URL + tag(versionName, edition);
    }

    @Nullable
    private static String rollingBody(@Nullable String markdown) {
        if (markdown == null) return null;
        StringBuilder out = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            // The file's own title says "Pre-release channel"; the screen already has a title.
            if (line.startsWith("# ")) continue;
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    /** The lines under {@code heading} up to the next {@code ## v} heading, trimmed; null if absent. */
    @Nullable
    private static String section(@Nullable String markdown, @NonNull String heading) {
        if (markdown == null) return null;
        StringBuilder out = null;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (out == null) {
                if (trimmed.equals(heading)) out = new StringBuilder();
                continue;
            }
            if (line.startsWith("## v") || trimmed.equals("---")) break;
            out.append(line).append('\n');
        }
        return out == null ? null : out.toString().trim();
    }
}
