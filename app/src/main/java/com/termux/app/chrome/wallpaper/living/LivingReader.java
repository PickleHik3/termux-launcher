package com.termux.app.chrome.wallpaper.living;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiRemoteSettings;

import java.net.URI;

/**
 * Who reads the photo for a living still, as the WALLPAPER_READER function resolves it
 * ({@link TaiFunctionModels}): an on-device vision model, the remote provider's model, or nobody,
 * which is the rules-only path ({@link LivingStillBuilder} then builds the recipe without the
 * director). Replaces the reader's own model rule ({@code SceneReader.modelId}).
 */
public final class LivingReader {
    /** The vision model id ({@code <id>-vision}) or {@code remote/<id>}; {@code null} for rules only. */
    @Nullable public final String model;
    /** {@code gpu} or {@code cpu} for an on-device model, else {@code null}. */
    @Nullable public final String accelerator;
    public final boolean remote;
    /** The reader's model file is a quarter of the RAM or more: "May close apps running in the background". */
    public final boolean warnBackground;
    /** The remote server's host, for the consent line; empty for an on-device reader. */
    @NonNull public final String remoteHost;

    public static final LivingReader RULES_ONLY = new LivingReader(null, null, false, false, "");

    LivingReader(@Nullable String model, @Nullable String accelerator, boolean remote, boolean warnBackground,
                 @NonNull String remoteHost) {
        this.model = model;
        this.accelerator = accelerator;
        this.remote = remote;
        this.warnBackground = warnBackground;
        this.remoteHost = remoteHost;
    }

    /** True when a model reads the photo; false for the rules-only path. */
    public boolean usesModel() {
        return model != null;
    }

    /**
     * The pure mapping from a resolution. A remote pick reads as {@code remote/<id>}; any
     * resolution with no model ({@code without == RULES_ONLY}, or nothing installed) is rules only.
     */
    @NonNull
    public static LivingReader of(@NonNull TaiFunctionModels.Resolution resolution, @Nullable String remoteBaseUrl) {
        if (resolution.isRemote()) {
            return new LivingReader(resolution.remoteModel, null, true, false, hostOf(remoteBaseUrl));
        }
        if (resolution.modelId != null) {
            return new LivingReader(resolution.modelId, resolution.accelerator, false, resolution.warnBackground, "");
        }
        return RULES_ONLY;
    }

    /**
     * The reader for this phone now. Reads the model store and settings: not for the main thread.
     */
    @NonNull
    public static LivingReader resolve(@NonNull Context context) {
        Context app = context.getApplicationContext();
        TaiFunctionModels.Resolution resolution = TaiFunctionModels.forContext(app).resolve(TaiFunction.WALLPAPER_READER);
        return of(resolution, resolution.isRemote() ? new TaiRemoteSettings(app).baseUrl() : null);
    }

    /** The host of a server address ({@code https://api.example.com/v1} gives {@code api.example.com}); empty when none. */
    @NonNull
    static String hostOf(@Nullable String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) return "";
        try {
            String host = new URI(baseUrl.trim()).getHost();
            return host == null ? "" : host;
        } catch (java.net.URISyntaxException e) {
            return "";
        }
    }

    /** The one-time consent asks the first time a remote reader would see a photo. */
    public static boolean needsConsent(boolean remote, boolean consentShown) {
        return remote && !consentShown;
    }

    /** The name shown as "Asking &lt;model&gt;…": the remote model's own id. */
    @NonNull
    public String remoteDisplayName() {
        if (model == null) return "";
        return model.startsWith(TaiFunctionModels.REMOTE_PREFIX)
            ? model.substring(TaiFunctionModels.REMOTE_PREFIX.length()) : model;
    }
}
