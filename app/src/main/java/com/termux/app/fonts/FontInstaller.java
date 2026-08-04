package com.termux.app.fonts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Installs a downloaded font family under {@code ~/.termux} and writes the app-managed
 * terminal font config.
 *
 * <p>Layout this class owns, and nothing else:
 * <ul>
 *   <li>{@code ~/.termux/fonts/<familyId>/{regular,bold,italic,bold-italic}.ttf} — the faces,
 *       plus a {@code LICENSE.txt} carrying the family's notice so the OFL attribution
 *       travels with the files.</li>
 *   <li>{@code ~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf} — the bundled Nerd Font
 *       symbols face, extracted from assets on first use.</li>
 *   <li>{@code ~/.termux/fonts.d/10-launcher.conf} — the managed config, written atomically.</li>
 * </ul>
 *
 * <p>{@code ~/.termux/font.ttf} and {@code ~/.termux/font-italic.ttf} are emphatically not in
 * that list. They belong to the user (and to Termux:Styling), they are frequently a hand-picked
 * Nerd Font build every other UI surface depends on, and the managed config already names all
 * four faces by path — so mirroring into them would destroy something valuable to gain nothing.
 * No code path here creates, overwrites or deletes them.
 *
 * <p>Precedence contract this is written against: the loader reads
 * {@code ~/.termux/fonts.d/*.conf} in filename order and then the user's own
 * {@code ~/.termux/fonts.conf} last, so the user always wins. Nothing here ever reads, writes
 * or deletes {@code fonts.conf} — not even on uninstall.
 */
public final class FontInstaller {

    /** Directory the loader autoloads managed config fragments from. */
    public static final String FONTS_D_DIR_NAME = "fonts.d";
    /** The one file this class writes there. The {@code 10-} prefix leaves room on both sides. */
    public static final String MANAGED_CONFIG_NAME = "10-launcher.conf";
    /** Directory under {@code ~/.termux} holding the installed families. */
    public static final String FONTS_DIR_NAME = "fonts";
    /** Sub-directory of {@link #FONTS_DIR_NAME} holding the bundled symbols face. */
    public static final String SYMBOLS_DIR_NAME = "symbols";

    /** Ligature policies the config accepts, mirrored here so callers need not import the parser. */
    public static final String LIGATURES_NEVER = "never";
    public static final String LIGATURES_CURSOR = "cursor";
    public static final String LIGATURES_ALWAYS = "always";

    /** Column the {@code path=} value starts in, matching the shipped fonts.conf example. */
    private static final int DIRECTIVE_COLUMN = 16;
    /** Column the feature/axis list starts in, after the face target. */
    private static final int TARGET_COLUMN = 11;

    /** The user-facing toggles that shape the managed config. */
    public static final class Options {
        /** Route the private-use planes to the bundled Nerd Font symbols face. */
        public final boolean nerdIcons;
        /** {@code never}, {@code cursor} or {@code always}. */
        @NonNull public final String ligatures;
        /** Apply the family's recommended {@code font_features}. */
        public final boolean recommendedFeatures;
        /**
         * Regular-face weight for a variable family. {@code 0} means "use the family default".
         * Ignored for static families, which carry no axis.
         */
        public final int weight;

        public Options(boolean nerdIcons, @NonNull String ligatures, boolean recommendedFeatures, int weight) {
            this.nerdIcons = nerdIcons;
            this.ligatures = normalizeLigatures(ligatures);
            this.recommendedFeatures = recommendedFeatures;
            this.weight = Math.max(0, weight);
        }

        /**
         * The defaults a family is installed with when nothing has been tuned for it yet: icons
         * on, the family's own ligature policy and font features, and no weight override.
         */
        @NonNull
        public static Options recommendedFor(@NonNull FontCatalog.Family family) {
            return new Options(true, family.defaultLigatures, true, 0);
        }

        @NonNull
        public Options withNerdIcons(boolean enabled) {
            return new Options(enabled, ligatures, recommendedFeatures, weight);
        }

        @NonNull
        public Options withLigatures(@NonNull String policy) {
            return new Options(nerdIcons, policy, recommendedFeatures, weight);
        }

        @NonNull
        public Options withRecommendedFeatures(boolean enabled) {
            return new Options(nerdIcons, ligatures, enabled, weight);
        }

        @NonNull
        public Options withWeight(int newWeight) {
            return new Options(nerdIcons, ligatures, recommendedFeatures, newWeight);
        }
    }

    @NonNull private final File dataHomeDir;
    @NonNull private final FontDownloader.TypefaceProbe typefaceProbe;

    /** Uses the real Termux data home, {@code /data/data/<package>/files/home/.termux}. */
    public FontInstaller() {
        this(TermuxConstants.TERMUX_DATA_HOME_DIR);
    }

    /** Root override, so the tests can install into a temporary directory. */
    public FontInstaller(@NonNull File dataHomeDir) {
        this(dataHomeDir, FontDownloader.ANDROID_TYPEFACE_PROBE);
    }

    /** Root and font-parse probe override, for JVM tests with no real {@code Typeface}. */
    public FontInstaller(@NonNull File dataHomeDir, @NonNull FontDownloader.TypefaceProbe typefaceProbe) {
        this.dataHomeDir = dataHomeDir;
        this.typefaceProbe = typefaceProbe;
    }

    @NonNull
    public File getManagedConfigFile() {
        return new File(new File(dataHomeDir, FONTS_D_DIR_NAME), MANAGED_CONFIG_NAME);
    }

    @NonNull
    public File getFamilyDir(@NonNull String familyId) {
        return new File(new File(dataHomeDir, FONTS_DIR_NAME), familyId);
    }

    @NonNull
    public File getSymbolsFile(@NonNull FontCatalog.SymbolFont symbolFont) {
        return new File(new File(new File(dataHomeDir, FONTS_DIR_NAME), SYMBOLS_DIR_NAME),
            symbolFont.installName);
    }

    /** Whether every face the catalog lists for this family is present on disk. */
    public boolean isInstalled(@NonNull FontCatalog.Family family) {
        File dir = getFamilyDir(family.id);
        for (FontCatalog.FaceSlot slot : family.faces.keySet()) {
            File face = new File(dir, slot.fileName);
            if (!face.isFile() || face.length() <= 0L) return false;
        }
        return true;
    }

    /** Whether the app-managed config is currently in place. */
    public boolean isManaged() {
        return getManagedConfigFile().isFile();
    }

    /**
     * Moves staged faces into the family directory and then writes the managed config — in that
     * order, so the config never names a file that is not there yet.
     *
     * @param stagedFaces faces produced by {@link FontDownloader#stageFamily}.
     */
    public void install(@NonNull FontCatalog.Family family,
                        @NonNull Map<FontCatalog.FaceSlot, File> stagedFaces,
                        @Nullable FontCatalog.SymbolFont symbolFont,
                        @NonNull Options options) throws IOException {
        File familyDir = getFamilyDir(family.id);
        if (!familyDir.isDirectory() && !familyDir.mkdirs()) {
            throw new IOException("cannot create " + familyDir);
        }
        Map<FontCatalog.FaceSlot, File> installed = new EnumMap<>(FontCatalog.FaceSlot.class);
        for (Map.Entry<FontCatalog.FaceSlot, File> entry : stagedFaces.entrySet()) {
            File source = entry.getValue();
            if (!source.isFile() || source.length() <= 0L) {
                throw new IOException("staged " + entry.getKey().key + " face is missing");
            }
            File target = new File(familyDir, entry.getKey().fileName);
            copyAtomically(source, target);
            installed.put(entry.getKey(), target);
        }
        if (!installed.containsKey(FontCatalog.FaceSlot.REGULAR)) {
            throw new IOException("no regular face was staged for " + family.id);
        }
        writeAtomically(new File(familyDir, "LICENSE.txt"), licenseText(family));

        // Deliberately no ~/.termux/font.ttf mirror: an earlier version wrote one and silently
        // replaced the user's own Nerd Font build, taking every icon glyph on every surface that
        // draws with the regular face down with it. The managed config names the faces by path,
        // which is all the terminal needs.
        writeManagedConfig(family, options, symbolFont);
    }

    /**
     * Writes the managed config for a family, checking the symbols face on disk first.
     *
     * <p>The check matters because the config outlives the install: {@code ensureSymbolsInstalled}
     * is idempotent on size and digest, but nothing stops the user from deleting
     * {@code ~/.termux/fonts/symbols/}, and a future bundled face could arrive under a different
     * name. Either way the stored {@code symbol_map} path would dangle, and the terminal answers a
     * dangling symbol font by silently falling back for every icon cell — the worst kind of
     * failure, because the config still looks correct. So the path is only written when the file
     * is there, the right size, and something Android can actually parse as a font.
     */
    public void writeManagedConfig(@NonNull FontCatalog.Family family, @NonNull Options options,
                                   @Nullable FontCatalog.SymbolFont symbolFont) throws IOException {
        writeAtomically(getManagedConfigFile(),
            buildManagedConfig(family, options, usableSymbolsFileName(symbolFont)));
    }

    /**
     * The symbols file name to write into the config, or null when there is nothing usable to
     * point at.
     *
     * <p>Public so any caller about to promise the user icons can ask the same question this
     * class asks before it writes the path. Repair is deliberately not done here: the callers
     * that can repair — {@link FontInstallCoordinator}'s install and reapply paths — run
     * {@link #ensureSymbolsInstalled} first, and an installer with no {@link Context} has no way
     * to reach the bundled asset.
     */
    @Nullable
    public String usableSymbolsFileName(@Nullable FontCatalog.SymbolFont symbolFont) {
        if (symbolFont == null) return null;
        File file = getSymbolsFile(symbolFont);
        // Size is checked as well as existence: a truncated or replaced file parses on some
        // devices and renders wrong glyphs on others, which is harder to diagnose than no icons.
        if (!file.isFile() || file.length() != symbolFont.sizeBytes) return null;
        if (!typefaceProbe.loads(file)) return null;
        return symbolFont.installName;
    }

    /**
     * Removes the managed config so the terminal falls back to {@code ~/.termux/font.ttf} —
     * i.e. hands control back to Termux:Styling and manual font replacement.
     *
     * <p>Deletes exactly one file. The installed faces stay (re-selecting a family should not
     * re-download it) and {@code ~/.termux/fonts.conf} is never touched.
     *
     * @return true when a managed file was present and is now gone.
     */
    public boolean uninstallManagedConfig() {
        File managed = getManagedConfigFile();
        if (!managed.isFile()) return false;
        return managed.delete();
    }

    /**
     * Copies the bundled symbols face out of assets, idempotently: an existing copy with the
     * right size and digest is left alone, so the common path costs one {@code stat} and one
     * hash of a 2.5 MB file rather than a rewrite.
     */
    @NonNull
    public File ensureSymbolsInstalled(@NonNull Context context,
                                       @NonNull FontCatalog.SymbolFont symbolFont) throws IOException {
        File target = getSymbolsFile(symbolFont);
        if (target.isFile() && target.length() == symbolFont.sizeBytes
            && symbolFont.sha256.equalsIgnoreCase(FontDownloader.sha256(target))) {
            return target;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        File partial = new File(target.getAbsolutePath() + ".part");
        try (InputStream input = context.getApplicationContext().getAssets().open(symbolFont.assetPath);
             OutputStream output = new FileOutputStream(partial, false)) {
            byte[] buffer = new byte[64 * 1024];
            long written = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (written > symbolFont.sizeBytes) {
                    throw new IOException("bundled symbols font is larger than the catalog declares");
                }
                output.write(buffer, 0, read);
            }
            if (written != symbolFont.sizeBytes) {
                throw new IOException("bundled symbols font is " + written + " bytes, expected "
                    + symbolFont.sizeBytes);
            }
        } catch (IOException e) {
            FontDownloader.deleteQuietly(partial);
            throw e;
        }
        // The asset is shipped in the APK, so a mismatch here means a broken build rather than a
        // hostile file — still worth refusing, because the config would then point at garbage.
        if (!symbolFont.sha256.equalsIgnoreCase(FontDownloader.sha256(partial))) {
            FontDownloader.deleteQuietly(partial);
            throw new IOException("bundled symbols font failed SHA-256 verification");
        }
        FontDownloader.commit(partial, target);
        return target;
    }

    /**
     * Renders the managed config for a family and a set of toggles.
     *
     * <p>Pure and static: the exact text is the contract between this class, the loader and the
     * user reading the file, so it is unit-tested character for character.
     *
     * @param symbolsFileName file name of the extracted symbols face, or null when no symbols
     *                        face is available (which forces icons off regardless of the toggle).
     */
    @NonNull
    public static String buildManagedConfig(@NonNull FontCatalog.Family family,
                                            @NonNull Options options,
                                            @Nullable String symbolsFileName) {
        StringBuilder out = new StringBuilder(2048);
        out.append("# ~/.termux/").append(FONTS_D_DIR_NAME).append('/').append(MANAGED_CONFIG_NAME)
            .append(" — generated by Termux Launcher, do not edit.\n");
        out.append("#\n");
        out.append("# This file is written by the app (Settings > Appearance > Terminal fonts).\n");
        out.append("# Every change made there replaces it completely, so edits here are lost.\n");
        out.append("#\n");
        out.append("# Your own ~/.termux/fonts.conf is read after this file, so anything set there\n");
        out.append("# overrides everything below. To take over by hand: copy the lines you want into\n");
        out.append("# ~/.termux/fonts.conf, then choose \"Use font.ttf / Termux:Styling\" in the app\n");
        out.append("# to delete this file.\n");
        out.append("#\n");
        out.append("# Family: ").append(family.displayName).append(" (").append(family.id).append(')');
        if (!family.releaseTag.isEmpty()) out.append(", release ").append(family.releaseTag);
        out.append('\n');
        out.append("# License: ").append(family.license).append('\n');
        out.append("#          ").append(family.licenseUrl).append('\n');

        out.append('\n');
        for (FontCatalog.FaceSlot slot : FontCatalog.FaceSlot.values()) {
            if (!family.hasFace(slot)) continue;
            out.append(pad(slot.directive, DIRECTIVE_COLUMN)).append(' ')
                .append("path=~/.termux/").append(FONTS_DIR_NAME).append('/').append(family.id)
                .append('/').append(slot.fileName).append('\n');
        }

        if (options.nerdIcons && symbolsFileName != null && !symbolsFileName.isEmpty()) {
            // Both private-use planes, matching the shipped ~/.termux/fonts.conf example. The BMP
            // range carries powerline, devicons, codicons, seti and font-awesome; SPUA-A carries
            // the Material Design set (nf-md-*), which real prompts lean on heavily. Mapping only
            // the first range would silently drop those glyphs for anyone moving from the example
            // config to this picker.
            String path = "path=~/.termux/" + FONTS_DIR_NAME + '/' + SYMBOLS_DIR_NAME + '/'
                + symbolsFileName;
            out.append('\n');
            out.append("symbol_map U+E000-U+F8FF   ").append(path).append('\n');
            out.append("symbol_map U+F0000-U+FFFFD ").append(path).append('\n');
        }

        out.append('\n');
        out.append("disable_ligatures ").append(options.ligatures).append('\n');

        String features = options.recommendedFeatures ? family.defaultFontFeatures.trim() : "";
        if (!features.isEmpty()) {
            out.append('\n');
            for (FontCatalog.FaceSlot slot : FontCatalog.FaceSlot.values()) {
                if (!family.hasFace(slot)) continue;
                out.append("font_features ").append(pad(slot.key, TARGET_COLUMN)).append(' ')
                    .append(features).append('\n');
            }
        }

        FontCatalog.WeightAxis axis = family.weightAxis;
        if (axis != null) {
            int regularWeight = options.weight > 0 ? axis.clamp(options.weight) : axis.regularWeight;
            // Moving the slider moves bold with it, keeping the family's own regular-to-bold
            // contrast instead of collapsing it at the top of the axis.
            int boldWeight = axis.clamp(regularWeight + (axis.boldWeight - axis.regularWeight));
            out.append('\n');
            for (FontCatalog.FaceSlot slot : FontCatalog.FaceSlot.values()) {
                if (!family.hasFace(slot)) continue;
                boolean bold = slot == FontCatalog.FaceSlot.BOLD
                    || slot == FontCatalog.FaceSlot.BOLD_ITALIC;
                out.append("font_variations ").append(pad(slot.key, TARGET_COLUMN)).append(' ')
                    .append("wght=").append(bold ? boldWeight : regularWeight).append('\n');
            }
        }
        return out.toString();
    }

    /** Writes {@code content} to {@code target} through a temp file plus rename. */
    public static void writeAtomically(@NonNull File target, @NonNull String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        File partial = new File(target.getAbsolutePath() + ".part");
        try (OutputStream output = new FileOutputStream(partial, false)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            FontDownloader.deleteQuietly(partial);
            throw e;
        }
        FontDownloader.commit(partial, target);
    }

    /** Copies a file through a temp file plus rename, so readers never see a partial font. */
    static void copyAtomically(@NonNull File source, @NonNull File target) throws IOException {
        if (source.getCanonicalPath().equals(target.getCanonicalPath())) return;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        File partial = new File(target.getAbsolutePath() + ".part");
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new FileOutputStream(partial, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        } catch (IOException e) {
            FontDownloader.deleteQuietly(partial);
            throw e;
        }
        FontDownloader.commit(partial, target);
    }

    @NonNull
    private static String licenseText(@NonNull FontCatalog.Family family) {
        StringBuilder out = new StringBuilder(512);
        out.append(family.displayName);
        if (!family.releaseTag.isEmpty()) out.append(" (").append(family.releaseTag).append(')');
        out.append("\n\n");
        out.append(family.license).append('\n');
        if (!family.licenseNotice.isEmpty()) out.append('\n').append(family.licenseNotice).append('\n');
        out.append('\n').append("Full license text: ").append(family.licenseUrl).append('\n');
        if (!family.homepageUrl.isEmpty()) {
            out.append("Upstream project:  ").append(family.homepageUrl).append('\n');
        }
        out.append('\n')
            .append("Installed by Termux Launcher. Deleting this directory removes the family.\n");
        return out.toString();
    }

    @NonNull
    static String normalizeLigatures(@Nullable String policy) {
        if (policy == null) return LIGATURES_NEVER;
        String value = policy.trim().toLowerCase(Locale.US);
        if (LIGATURES_CURSOR.equals(value) || LIGATURES_ALWAYS.equals(value)) return value;
        return LIGATURES_NEVER;
    }

    @NonNull
    private static String pad(@NonNull String value, int width) {
        if (value.length() >= width) return value;
        StringBuilder out = new StringBuilder(width);
        out.append(value);
        while (out.length() < width) out.append(' ');
        return out.toString();
    }
}
