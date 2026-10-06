package com.termux.app.firstrun;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the "What runs on this phone" welcome card shows and decides (tai-device-tiers spec §5).
 *
 * <p>Built on {@link TaiTierPolicy#welcomeRows}, which owns the rows and the fixed preselection;
 * this adds what the card needs around them: names, sizes, the one plain line, what is already
 * installed, the background-app warning, and the footer's storage rule. Like the first-run
 * permissions card it is pure, so each tier, the platform rule, the margin and the rounding are
 * unit tests rather than a run on a phone. Image generation never appears: the policy has no row
 * for it.
 *
 * <p>Nothing here depends on how full the phone is, except {@link #footer}: storage can disable
 * Download but never changes a tick.
 */
public final class TaiWelcomeCard {
    private TaiWelcomeCard() {}

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    /** Download stays disabled unless the selection leaves this much free (spec §5.2). */
    public static final long STORAGE_MARGIN_BYTES = GIB;

    /** The size of one catalogue download; the view reads {@code TaiModelCatalog}, tests a map. */
    public interface Sizes {
        long bytesOf(@NonNull String modelId);
    }

    /** One row as drawn. */
    public static final class Row {
        /** The policy's key: {@code voice_typing}, {@code read_aloud}, {@code assistant}, ... */
        @NonNull public final String id;
        @StringRes public final int titleRes;
        /** The leading glyph: the settings pages' mark for what the row does. */
        @DrawableRes public final int glyphRes;
        /** The plain line under the title. */
        @StringRes public final int lineRes;
        /** The model names, e.g. "Gemma 4 E2B"; not localised, they are product names. */
        @NonNull public final String modelNames;
        /** Every download the row stands for, installed or not. */
        @NonNull public final List<String> modelIds;
        /** The ones still to download; empty when the row is installed. */
        @NonNull public final List<String> missingIds;
        /** What ticking it downloads, 0 when installed. */
        public final long downloadBytes;
        /** {@link #downloadBytes} to two significant figures, "" when installed. */
        @NonNull public final String sizeText;
        public final boolean installed;
        /** Ticked when the card opens: the policy's preselection, never for an installed row. */
        public final boolean ticked;
        /** The row's largest file would take a quarter of the RAM class or more (spec §4.4). */
        public final boolean warnsBackground;

        Row(@NonNull String id, int titleRes, int glyphRes, int lineRes, @NonNull String modelNames,
            @NonNull List<String> modelIds, @NonNull List<String> missingIds, long downloadBytes,
            boolean ticked, boolean warnsBackground) {
            this.id = id;
            this.titleRes = titleRes;
            this.glyphRes = glyphRes;
            this.lineRes = lineRes;
            this.modelNames = modelNames;
            this.modelIds = Collections.unmodifiableList(modelIds);
            this.missingIds = Collections.unmodifiableList(missingIds);
            this.downloadBytes = downloadBytes;
            this.installed = missingIds.isEmpty();
            this.sizeText = installed ? "" : formatSize(downloadBytes);
            this.ticked = !installed && ticked;
            this.warnsBackground = !installed && warnsBackground;
        }

        /** An installed row is shown as installed and cannot be ticked. */
        public boolean tickable() {
            return !installed;
        }
    }

    /** The card's header, as parts the view puts into its own sentence. */
    public static final class Header {
        public final int tierNumber;
        public final int ramGb;
        /** The chip name, "" when the phone does not say. */
        @NonNull public final String chip;
        @NonNull public final String androidVersion;

        Header(int tierNumber, int ramGb, @NonNull String chip, @NonNull String androidVersion) {
            this.tierNumber = tierNumber;
            this.ramGb = ramGb;
            this.chip = chip;
            this.androidVersion = androidVersion;
        }
    }

    /** The footer: what is selected against what is free, and whether Download may run. */
    public static final class Footer {
        public final long selectedBytes;
        public final long freeBytes;
        @NonNull public final String selectedText;
        @NonNull public final String freeText;
        public final boolean downloadEnabled;
        /** Why Download is disabled, 0 when it is enabled. */
        @StringRes public final int disabledReasonRes;

        Footer(long selectedBytes, long freeBytes, boolean downloadEnabled, int disabledReasonRes) {
            this.selectedBytes = selectedBytes;
            this.freeBytes = freeBytes;
            this.selectedText = formatSize(selectedBytes);
            this.freeText = formatSize(freeBytes);
            this.downloadEnabled = downloadEnabled;
            this.disabledReasonRes = disabledReasonRes;
        }
    }

    // ------------------------------------------------------------------------------------ header

    /** Tier, RAM class, chip and Android version: the one place the card says "tier". */
    @NonNull
    public static Header header(@NonNull TaiTierPolicy.Env env, @Nullable String chip, @Nullable String androidVersion) {
        int ramGb = (int) Math.max(0L, Math.round(env.ramClassBytes / (double) GIB));
        return new Header(env.tier.number(), ramGb, chip == null ? "" : chip.trim(),
            androidVersion == null ? "" : androidVersion.trim());
    }

    /**
     * The chip's name from what Android reports: the SoC model when it says one (prefixed by the
     * maker unless it already starts with it), else the hardware name, else "".
     */
    @NonNull
    public static String chipName(@Nullable String socManufacturer, @Nullable String socModel, @Nullable String hardware) {
        String model = clean(socModel);
        String maker = clean(socManufacturer);
        if (!model.isEmpty()) {
            if (maker.isEmpty() || model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT))) return model;
            return maker + " " + model;
        }
        return clean(hardware);
    }

    private static String clean(@Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.equalsIgnoreCase("unknown") ? "" : trimmed;
    }

    // -------------------------------------------------------------------------------------- rows

    /** Whether the card has anything to offer: a phone with no local backend gets no card. */
    public static boolean hasRows(@NonNull TaiTierPolicy.Env env) {
        return !TaiTierPolicy.welcomeRows(env).isEmpty();
    }

    /** Tier 1 has no assistant rows, so the card adds the line pointing at the Model Centre. */
    public static boolean showsModelCentreLine(@NonNull TaiTierPolicy.Env env) {
        return env.tier == TaiDeviceTier.TIER_1 && env.localModelsSupported();
    }

    /**
     * The card's rows for this phone, in display order. A removed row is absent (the policy drops
     * it); an installed row is shown as installed, unticked and not tickable.
     *
     * @param installedIds the catalogue ids already on the phone
     */
    @NonNull
    public static List<Row> rows(@NonNull TaiTierPolicy.Env env, @NonNull Set<String> installedIds, @NonNull Sizes sizes) {
        boolean t3 = env.tier == TaiDeviceTier.TIER_3;
        List<Row> rows = new ArrayList<>();
        for (TaiTierPolicy.WelcomeRow policyRow : TaiTierPolicy.welcomeRows(env)) {
            List<String> missing = new ArrayList<>();
            long bytes = 0L;
            long largest = 0L;
            for (String id : policyRow.modelIds) {
                long size = Math.max(0L, sizes.bytesOf(id));
                largest = Math.max(largest, size);
                if (!installedIds.contains(id)) {
                    missing.add(id);
                    bytes += size;
                }
            }
            int titleRes;
            int glyphRes;
            int lineRes;
            switch (policyRow.id) {
                case "voice_typing":
                    titleRes = R.string.tai_welcome_row_voice_title;
                    glyphRes = R.drawable.ic_symbol_mic;
                    lineRes = R.string.tai_welcome_row_voice_line;
                    break;
                case "read_aloud":
                    titleRes = R.string.tai_welcome_row_read_aloud_title;
                    glyphRes = R.drawable.ic_symbol_read_aloud;
                    lineRes = R.string.tai_welcome_row_read_aloud_line;
                    break;
                case "assistant":
                    // Tier 3's E2B is only the tidy-dictation helper; its assistant is E4B.
                    titleRes = t3 ? R.string.tai_welcome_row_tidy_title : R.string.tai_welcome_row_assistant_title;
                    glyphRes = t3 ? R.drawable.ic_symbol_edit : R.drawable.ic_symbol_ai_star;
                    lineRes = t3 ? R.string.tai_welcome_row_tidy_line : R.string.tai_welcome_row_assistant_line;
                    break;
                case "e4b_assistant":
                    titleRes = R.string.tai_welcome_row_e4b_assistant_title;
                    glyphRes = R.drawable.ic_symbol_ai_star;
                    lineRes = R.string.tai_welcome_row_e4b_assistant_line;
                    break;
                default: // dawn_notes
                    titleRes = R.string.tai_welcome_row_dawn_title;
                    glyphRes = R.drawable.ic_symbol_search;
                    lineRes = R.string.tai_welcome_row_dawn_line;
                    break;
            }
            rows.add(new Row(policyRow.id, titleRes, glyphRes, lineRes, modelNames(policyRow),
                policyRow.modelIds, missing, bytes, policyRow.preselected,
                TaiTierPolicy.warnsBackground(env, largest)));
        }
        return rows;
    }

    /** Product names only: no parameter counts, no file names. */
    @NonNull
    private static String modelNames(@NonNull TaiTierPolicy.WelcomeRow row) {
        switch (row.id) {
            case "voice_typing":
                return row.modelIds.get(0).contains("small") ? "Whisper Small" : "Whisper Base";
            case "read_aloud":
                return "KittenTTS";
            case "assistant":
                return "Gemma 4 E2B";
            case "e4b_assistant":
                return "Gemma 4 E4B";
            default: // dawn_notes
                return "EmbeddingGemma";
        }
    }

    // ------------------------------------------------------------------------------------ footer

    /**
     * The footer for the ticked rows. Download is enabled only when something is selected and the
     * selection plus {@link #STORAGE_MARGIN_BYTES} fits in the free storage.
     *
     * @param tickedRowIds the ids of the rows ticked right now
     */
    @NonNull
    public static Footer footer(@NonNull List<Row> rows, @NonNull Set<String> tickedRowIds, long freeBytes) {
        long selected = 0L;
        for (Row row : rows) {
            if (row.tickable() && tickedRowIds.contains(row.id)) selected += row.downloadBytes;
        }
        if (selected <= 0L) return new Footer(selected, freeBytes, false, R.string.tai_welcome_reason_nothing);
        if (selected + STORAGE_MARGIN_BYTES > freeBytes) {
            return new Footer(selected, freeBytes, false, R.string.tai_welcome_reason_storage);
        }
        return new Footer(selected, freeBytes, true, 0);
    }

    /** The ids of the rows ticked when the card opens. */
    @NonNull
    public static LinkedHashSet<String> initialTicks(@NonNull List<Row> rows) {
        LinkedHashSet<String> ticked = new LinkedHashSet<>();
        for (Row row : rows) if (row.ticked) ticked.add(row.id);
        return ticked;
    }

    /**
     * The model ids to queue for the ticked rows, smallest download first so voice works within a
     * minute. Installed models are not queued again, and an id shared by two rows is queued once.
     */
    @NonNull
    public static List<String> downloadOrder(@NonNull List<Row> rows, @NonNull Set<String> tickedRowIds, @NonNull Sizes sizes) {
        List<String> ids = new ArrayList<>();
        for (Row row : rows) {
            if (!row.tickable() || !tickedRowIds.contains(row.id)) continue;
            for (String id : row.missingIds) if (!ids.contains(id)) ids.add(id);
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return Long.compare(sizes.bytesOf(a), sizes.bytesOf(b));
            }
        });
        return ids;
    }

    // ----------------------------------------------------------------------------------- showing

    /**
     * Whether the card is raised on the home screen now (spec §5.1): once ever, never while the
     * tour is running or still to be offered, never over chrome or another card, and only when
     * the phone has something to offer. "Later" counts as shown, so it never nags.
     */
    public static boolean shouldShowOnHome(boolean alreadyShown, boolean tourPending, boolean chromeUp,
                                           boolean otherCardUp, boolean hasRows) {
        return !alreadyShown && !tourPending && !chromeUp && !otherCardUp && hasRows;
    }

    // --------------------------------------------------------------------------------------- size

    /**
     * A size to two significant figures, in binary units as the Model Centre counts: 2.6 GB,
     * 94 MB, 100 MB, 12 GB. Under a megabyte it says "&lt;1 MB"; zero is "0 MB".
     */
    @NonNull
    public static String formatSize(long bytes) {
        if (bytes <= 0L) return "0 MB";
        if (bytes < MIB) return "<1 MB";
        double rounded = roundTwoSignificant(bytes / (double) MIB);
        if (rounded >= 1000d) return unit(roundTwoSignificant(bytes / (double) GIB), "GB");
        return unit(rounded, "MB");
    }

    private static double roundTwoSignificant(double value) {
        if (value < 10d) return Math.round(value * 10d) / 10d;
        double scale = Math.pow(10d, Math.floor(Math.log10(value)) - 1d);
        return Math.round(value / scale) * scale;
    }

    private static String unit(double value, String unit) {
        if (value < 10d) return String.format(Locale.US, "%.1f %s", value, unit);
        return String.format(Locale.US, "%d %s", Math.round(value), unit);
    }
}
