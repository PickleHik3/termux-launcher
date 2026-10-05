package com.termux.ai;

import androidx.annotation.NonNull;

import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiFunctionModels.Source;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiTierPolicy.Env;
import com.termux.ai.TaiTierPolicy.WithoutModel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The pick, Automatic, chain order of tai-device-tiers §4.5, with a fake store and a fake installed set. */
public class TaiFunctionModelsTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
    private static final long E2B_BYTES = 2_588_147_712L;
    private static final long E4B_BYTES = 3_659_530_240L;

    private final Map<String, String> prefs = new HashMap<>();
    private final Map<String, ModelInfo> installed = new LinkedHashMap<>();

    private static Set<String> caps(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private void install(String id, long bytes, String... capabilities) {
        installed.put(id, new ModelInfo(id, bytes, caps(capabilities), TaiModelSpec.BACKEND_LITERT_LM));
    }

    private void installGemma() {
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        install(E4B, E4B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
    }

    private boolean remoteConfigured;
    private boolean remoteImages;
    private boolean remotePrefers;

    private final TaiFunctionModels.Remote fakeRemote = new TaiFunctionModels.Remote() {
        @Override public boolean configured() { return remoteConfigured; }
        @Override public String modelId() { return remoteConfigured ? "gpt-x" : ""; }
        @Override public boolean understandsImages() { return remoteImages; }
        @Override public boolean prefersRemote() { return remotePrefers; }
    };

    private TaiFunctionModels models(Env env) {
        TaiFunctionModels.Store store = new TaiFunctionModels.Store() {
            @Override public String get(String key) {
                String value = prefs.get(key);
                return value == null ? "" : value;
            }

            @Override public void put(String key, String value) {
                if (value.isEmpty()) prefs.remove(key);
                else prefs.put(key, value);
            }
        };
        return new TaiFunctionModels(env, store, () -> installed, fakeRemote);
    }

    private static Env env(int gb, int sdk, GpuPath gpu) {
        return new Env(TaiDeviceTier.from(gb * GIB), gb * GIB, sdk, true, true, gpu, false);
    }

    private TaiFunctionModels pong() {
        return models(env(12, 34, GpuPath.YES));
    }

    // ------------------------------------------------------------------------------------ keys

    @Test
    public void existingKeysAreKept() {
        assertEquals("tai_role_default_assistant", TaiFunction.ASSISTANT.modelKey);
        assertEquals("tai_stt_model_id", TaiFunction.VOICE_TYPING.modelKey);
        assertEquals("keyboard_voice_polish_model", TaiFunction.TIDY_DICTATION.modelKey);
        assertEquals("wallpaper_depth_model", TaiFunction.WALLPAPER_DEPTH.modelKey);
        assertEquals("tai_fn_read_aloud_model", TaiFunction.READ_ALOUD.modelKey);
        assertEquals("tai_fn_app_categories_model", TaiFunction.APP_CATEGORIES.modelKey);
        assertEquals("tai_fn_wallpaper_reader_model", TaiFunction.WALLPAPER_READER.modelKey);
        assertEquals("tai_fn_embeddings_model", TaiFunction.EMBEDDINGS.modelKey);
        assertEquals("tai_fn_assistant_accel", TaiFunction.ASSISTANT.accelKey);
    }

    // --------------------------------------------------------------------------------- order

    @Test
    public void aPickWinsWhenInstalled() {
        installGemma();
        TaiFunctionModels models = pong();
        models.set(TaiFunction.ASSISTANT, E4B);
        Resolution resolution = models.resolve(TaiFunction.ASSISTANT);
        assertEquals(E4B, resolution.modelId);
        assertEquals(Source.PICK, resolution.source);
        assertEquals("gpu", resolution.accelerator);
        assertTrue(resolution.warnBackground);
    }

    @Test
    public void aPickThatIsNotInstalledFallsToAutomatic() {
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFunctionModels models = pong();
        models.set(TaiFunction.ASSISTANT, E4B);
        Resolution resolution = models.resolve(TaiFunction.ASSISTANT);
        assertEquals(E2B, resolution.modelId);
        assertEquals(Source.AUTOMATIC, resolution.source);
        assertFalse(resolution.warnBackground);
    }

    @Test
    public void automaticThatIsMissingFallsToTheFirstInstalledLinkOfTheChain() {
        // Tier 2 at 12 GB reads wallpapers with E4B; with only E2B installed the chain's E2B vision serves.
        install(E2B, E2B_BYTES, TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        Resolution resolution = pong().resolve(TaiFunction.WALLPAPER_READER);
        assertEquals(E2B + "-vision", resolution.modelId);
        assertEquals(Source.FALLBACK, resolution.source);
        assertEquals(2, resolution.chain.size());
    }

    @Test
    public void theReaderResolvesToTheVisionVariantOfTheInstalledFile() {
        installGemma();
        Resolution resolution = pong().resolve(TaiFunction.WALLPAPER_READER);
        assertEquals(E4B + "-vision", resolution.modelId);
        assertEquals(Source.AUTOMATIC, resolution.source);
    }

    @Test
    public void aReaderPickIsStoredWithoutTheVisionSuffix() {
        installGemma();
        TaiFunctionModels models = pong();
        models.set(TaiFunction.WALLPAPER_READER, E2B + "-vision");
        assertEquals(E2B, prefs.get("tai_fn_wallpaper_reader_model"));
        assertEquals(E2B + "-vision", models.resolve(TaiFunction.WALLPAPER_READER).modelId);
    }

    @Test
    public void nothingInstalledEndsInTheWithoutModelChoice() {
        Resolution reader = pong().resolve(TaiFunction.WALLPAPER_READER);
        assertNull(reader.modelId);
        assertEquals(Source.FALLBACK, reader.source);
        assertEquals(WithoutModel.RULES_ONLY, reader.without);
        Resolution tidy = pong().resolve(TaiFunction.TIDY_DICTATION);
        assertEquals(WithoutModel.RAW_TEXT, tidy.without);
        Resolution assistant = pong().resolve(TaiFunction.ASSISTANT);
        assertNull(assistant.modelId);
        assertEquals(Source.NONE, assistant.source);
        assertEquals(WithoutModel.NONE, assistant.without);
    }

    @Test
    public void tier1AutomaticIsTheWithoutModelChoiceEvenWithAnLlmInstalled() {
        installGemma();
        TaiFunctionModels tier1 = models(env(6, 34, GpuPath.YES));
        Resolution reader = tier1.resolve(TaiFunction.WALLPAPER_READER);
        assertNull(reader.modelId);
        assertEquals(Source.AUTOMATIC, reader.source);
        assertEquals(WithoutModel.RULES_ONLY, reader.without);
        // ...until the user picks the model they added.
        tier1.set(TaiFunction.WALLPAPER_READER, E2B);
        assertEquals(E2B + "-vision", tier1.resolve(TaiFunction.WALLPAPER_READER).modelId);
        assertEquals(Source.PICK, tier1.resolve(TaiFunction.WALLPAPER_READER).source);
    }

    // ------------------------------------------------------------------------- off and remote

    @Test
    public void offTurnsAFunctionIntoItsWithoutModelChoice() {
        installGemma();
        TaiFunctionModels models = pong();
        models.set(TaiFunction.APP_CATEGORIES, TaiFunctionModels.VALUE_OFF);
        Resolution categories = models.resolve(TaiFunction.APP_CATEGORIES);
        assertNull(categories.modelId);
        assertEquals(Source.PICK, categories.source);
        assertEquals(WithoutModel.OFF, categories.without);
        models.set(TaiFunction.WALLPAPER_READER, "off");
        assertEquals(WithoutModel.RULES_ONLY, models.resolve(TaiFunction.WALLPAPER_READER).without);
        models.set(TaiFunction.TIDY_DICTATION, "off");
        assertEquals(WithoutModel.RAW_TEXT, models.resolve(TaiFunction.TIDY_DICTATION).without);
        models.set(TaiFunction.ASSISTANT, "off");
        assertEquals(WithoutModel.OFF, models.resolve(TaiFunction.ASSISTANT).without);
    }

    @Test
    public void aRemotePickIsStoredButUnavailableWhileNoProviderIsSetUp() {
        installGemma();
        TaiFunctionModels models = pong();
        models.set(TaiFunction.ASSISTANT, "remote/some-model");
        assertEquals("remote/some-model", prefs.get("tai_role_default_assistant"));
        assertEquals("remote/some-model", models.pick(TaiFunction.ASSISTANT));
        Resolution resolution = models.resolve(TaiFunction.ASSISTANT);
        assertEquals(E2B, resolution.modelId);
        assertEquals(Source.AUTOMATIC, resolution.source);
        assertTrue(TaiFunctionModels.isRemote("remote/some-model"));
        assertFalse(TaiFunctionModels.isRemote("remote:old"));
        assertFalse(TaiFunctionModels.isRemote(E2B));
        assertFalse(TaiFunctionModels.isRemote(null));
    }

    @Test
    public void aRemotePickResolvesWhenTheProviderIsConfigured() {
        installGemma();
        remoteConfigured = true;
        TaiFunctionModels models = pong();
        models.set(TaiFunction.ASSISTANT, "remote/gpt-x");
        Resolution resolution = models.resolve(TaiFunction.ASSISTANT);
        assertEquals("remote/gpt-x", resolution.modelId);
        assertEquals(Source.PICK, resolution.source);
        assertNull(resolution.accelerator);
        assertFalse(resolution.warnBackground);
        // The remote model uses no local file, so nothing local is "used by" it.
        assertFalse(models.usedBy(E2B).contains(TaiFunction.ASSISTANT));
    }

    @Test
    public void remoteNeverServesVoiceTypingReadAloudOrEmbeddings() {
        installGemma();
        install("kitten", 90L * 1024 * 1024, TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
        remoteConfigured = true;
        TaiFunctionModels models = pong();
        models.set(TaiFunction.READ_ALOUD, "remote/gpt-x");
        models.set(TaiFunction.EMBEDDINGS, "remote/gpt-x");
        assertFalse(TaiFunctionModels.remoteAllowed(TaiFunction.VOICE_TYPING, true));
        assertFalse(TaiFunctionModels.remoteAllowed(TaiFunction.READ_ALOUD, true));
        assertFalse(TaiFunctionModels.remoteAllowed(TaiFunction.EMBEDDINGS, true));
        assertFalse(TaiFunctionModels.remoteAllowed(TaiFunction.WALLPAPER_DEPTH, true));
        assertTrue(TaiFunctionModels.remoteAllowed(TaiFunction.TIDY_DICTATION, false));
        assertFalse(TaiFunctionModels.isRemote(models.resolve(TaiFunction.READ_ALOUD).modelId));
        assertFalse(TaiFunctionModels.isRemote(models.resolve(TaiFunction.EMBEDDINGS).modelId));
    }

    @Test
    public void theReaderUsesARemoteModelOnlyWhenItUnderstandsImages() {
        installGemma();
        remoteConfigured = true;
        TaiFunctionModels models = pong();
        models.set(TaiFunction.WALLPAPER_READER, "remote/gpt-x");
        // Text-only remote model: the pick falls through to Automatic.
        assertEquals(E4B + "-vision", models.resolve(TaiFunction.WALLPAPER_READER).modelId);
        remoteImages = true;
        Resolution resolution = models.resolve(TaiFunction.WALLPAPER_READER);
        assertEquals("remote/gpt-x", resolution.modelId);
        assertEquals(Source.PICK, resolution.source);
    }

    @Test
    public void preferRemoteMakesAutomaticTheRemoteModelButKeepsLocalPicks() {
        installGemma();
        remoteConfigured = true;
        remotePrefers = true;
        TaiFunctionModels models = pong();
        Resolution automatic = models.resolve(TaiFunction.ASSISTANT);
        assertEquals("remote/gpt-x", automatic.modelId);
        assertEquals(Source.AUTOMATIC, automatic.source);
        assertNull(automatic.accelerator);
        // A local pick is the user's word and wins.
        models.set(TaiFunction.ASSISTANT, E4B);
        assertEquals(E4B, models.resolve(TaiFunction.ASSISTANT).modelId);
        // The reader needs images; the audio functions stay local.
        assertEquals(E4B + "-vision", models.resolve(TaiFunction.WALLPAPER_READER).modelId);
        assertFalse(TaiFunctionModels.isRemote(models.resolve(TaiFunction.EMBEDDINGS).modelId));
        // Not configured: the preference means nothing.
        remoteConfigured = false;
        models.set(TaiFunction.ASSISTANT, "");
        assertEquals(E2B, models.resolve(TaiFunction.ASSISTANT).modelId);
    }

    @Test
    public void settingAutomaticClearsThePick() {
        TaiFunctionModels models = pong();
        models.set(TaiFunction.READ_ALOUD, "some-voice");
        assertEquals("some-voice", prefs.get("tai_fn_read_aloud_model"));
        models.set(TaiFunction.READ_ALOUD, "");
        assertFalse(prefs.containsKey("tai_fn_read_aloud_model"));
        assertEquals("", models.pick(TaiFunction.READ_ALOUD));
    }

    // -------------------------------------------------------------------------------- platform

    @Test
    public void belowApi34TheWallpaperFunctionsResolveToNothing() {
        installGemma();
        install(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, 100L, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        TaiFunctionModels old = models(env(12, 33, GpuPath.YES));
        Resolution reader = old.resolve(TaiFunction.WALLPAPER_READER);
        assertNull(reader.modelId);
        assertEquals(Source.NONE, reader.source);
        assertEquals(WithoutModel.NONE, reader.without);
        assertNull(old.resolve(TaiFunction.WALLPAPER_DEPTH).modelId);
        assertTrue(old.candidates(TaiFunction.WALLPAPER_DEPTH).isEmpty());
    }

    @Test
    public void aGpuPickOnAPhoneWithNoGpuPathRunsOnTheCpu() {
        installGemma();
        TaiFunctionModels models = models(env(12, 34, GpuPath.NO));
        models.setAcceleratorPick(TaiFunction.ASSISTANT, "gpu");
        models.set(TaiFunction.ASSISTANT, E4B);
        assertEquals("cpu", models.resolve(TaiFunction.ASSISTANT).accelerator);
        TaiFunctionModels yes = pong();
        yes.setAcceleratorPick(TaiFunction.ASSISTANT, "cpu");
        yes.set(TaiFunction.ASSISTANT, E4B);
        assertEquals("cpu", yes.resolve(TaiFunction.ASSISTANT).accelerator);
    }

    @Test
    public void withNoUsableAbiNoModelResolves() {
        installGemma();
        TaiFunctionModels models = models(new Env(TaiDeviceTier.TIER_2, 12 * GIB, 34, false, false, GpuPath.YES, false));
        assertNull(models.resolve(TaiFunction.ASSISTANT).modelId);
        assertEquals(WithoutModel.RAW_TEXT, models.resolve(TaiFunction.TIDY_DICTATION).without);
        assertTrue(models.candidates(TaiFunction.ASSISTANT).isEmpty());
    }

    // ------------------------------------------------------------------------ usedBy / candidates

    @Test
    public void usedByNamesTheFunctionsWhoseResolvedModelItIs() {
        installGemma();
        TaiFunctionModels models = pong();
        List<TaiFunction> e4b = models.usedBy(E4B);
        assertEquals(Collections.singletonList(TaiFunction.WALLPAPER_READER), e4b);
        assertEquals(e4b, models.usedBy(E4B + "-vision"));
        assertEquals(Arrays.asList(TaiFunction.ASSISTANT, TaiFunction.TIDY_DICTATION, TaiFunction.APP_CATEGORIES),
            models.usedBy(E2B));
        models.set(TaiFunction.ASSISTANT, E4B);
        assertTrue(models.usedBy(E4B).contains(TaiFunction.ASSISTANT));
        assertFalse(models.usedBy(E2B).contains(TaiFunction.ASSISTANT));
        assertTrue(models.usedBy("not-installed").isEmpty());
    }

    @Test
    public void candidatesAreTheInstalledModelsThatCanServe() {
        installGemma();
        install("whisper-acft-small", 286L * 1024 * 1024, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        install(TaiModelCatalog.KITTEN_TTS_NANO_ID, 94L * 1024 * 1024, TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
        install(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 183L * 1024 * 1024, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        install(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, 100L, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        install("some-diffusion", 1L, TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
        install("text-only", 1L, TaiModelSpec.CAPABILITY_TEXT_CHAT);
        TaiFunctionModels models = pong();

        assertEquals(Arrays.asList("whisper-acft-small"), ids(models.candidates(TaiFunction.VOICE_TYPING)));
        assertEquals(Arrays.asList(TaiModelCatalog.KITTEN_TTS_NANO_ID), ids(models.candidates(TaiFunction.READ_ALOUD)));
        assertEquals(Arrays.asList(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID), ids(models.candidates(TaiFunction.EMBEDDINGS)));
        assertEquals(Arrays.asList(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID), ids(models.candidates(TaiFunction.WALLPAPER_DEPTH)));
        // The reader takes vision variants only; the chat functions take every chat model, never an image model.
        assertEquals(Arrays.asList(E2B + "-vision", E4B + "-vision"), ids(models.candidates(TaiFunction.WALLPAPER_READER)));
        assertEquals(Arrays.asList(E2B, E4B, "text-only"), ids(models.candidates(TaiFunction.ASSISTANT)));
        assertEquals(ids(models.candidates(TaiFunction.ASSISTANT)), ids(models.candidates(TaiFunction.APP_CATEGORIES)));
        assertEquals(ids(models.candidates(TaiFunction.ASSISTANT)), ids(models.candidates(TaiFunction.TIDY_DICTATION)));
    }

    @Test
    public void mnnModelsNeedTheMnnAbi() {
        installed.put("mnn-chat", new ModelInfo("mnn-chat", 1L, caps(TaiModelSpec.CAPABILITY_TEXT_CHAT),
            TaiModelSpec.BACKEND_MNN_LLM));
        Env noMnn = new Env(TaiDeviceTier.TIER_2, 12 * GIB, 29, true, false, GpuPath.YES, false);
        assertTrue(models(noMnn).candidates(TaiFunction.ASSISTANT).isEmpty());
        assertEquals(1, pong().candidates(TaiFunction.ASSISTANT).size());
    }

    private static List<String> ids(List<ModelInfo> infos) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (ModelInfo info : infos) out.add(info.id);
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------- callers: the resolutions they map from

    private TaiFunctionModels withRemote(Env env, boolean configured, boolean images) {
        TaiFunctionModels.Store store = new TaiFunctionModels.Store() {
            @Override public String get(String key) {
                String value = prefs.get(key);
                return value == null ? "" : value;
            }

            @Override public void put(String key, String value) {
                if (value.isEmpty()) prefs.remove(key);
                else prefs.put(key, value);
            }
        };
        return new TaiFunctionModels(env, store, () -> installed, new TaiFunctionModels.Remote() {
            @Override public boolean configured() { return configured; }
            @NonNull @Override public String modelId() { return "big-model"; }
            @Override public boolean understandsImages() { return images; }
            @Override public boolean prefersRemote() { return false; }
        });
    }

    @Test
    public void aRemotePickResolvesToTheRemoteModelWhenTheProviderIsSetUp() {
        installGemma();
        TaiFunctionModels models = withRemote(env(12, 34, GpuPath.YES), true, true);
        models.set(TaiFunction.APP_CATEGORIES, "remote/big-model");
        Resolution resolution = models.resolve(TaiFunction.APP_CATEGORIES);
        assertTrue(resolution.isRemote());
        assertEquals("remote/big-model", resolution.remoteModel);
        assertEquals("remote/big-model", resolution.requestModel());
        assertEquals("remote/big-model", resolution.modelId);
        assertNull(resolution.accelerator);
        assertFalse(resolution.warnBackground);
    }

    @Test
    public void aRemoteReaderNeedsAModelThatTakesImages() {
        installGemma();
        TaiFunctionModels models = withRemote(env(12, 34, GpuPath.YES), true, false);
        models.set(TaiFunction.WALLPAPER_READER, "remote/text-only");
        Resolution resolution = models.resolve(TaiFunction.WALLPAPER_READER);
        assertFalse(resolution.isRemote());
        assertEquals(E4B + "-vision", resolution.modelId);
        TaiFunctionModels seeing = withRemote(env(12, 34, GpuPath.YES), true, true);
        seeing.set(TaiFunction.WALLPAPER_READER, "remote/vision-1");
        assertTrue(seeing.resolve(TaiFunction.WALLPAPER_READER).isRemote());
    }

    @Test
    public void aRemotePickWithNoProviderFallsToAutomatic() {
        installGemma();
        TaiFunctionModels models = withRemote(env(12, 34, GpuPath.YES), false, true);
        models.set(TaiFunction.TIDY_DICTATION, "remote/gone");
        Resolution resolution = models.resolve(TaiFunction.TIDY_DICTATION);
        assertFalse(resolution.isRemote());
        assertEquals(E2B, resolution.modelId);
        assertEquals(Source.AUTOMATIC, resolution.source);
    }

    @Test
    public void aRemotePickIsIgnoredByTheFunctionsThatRunNoChatModel() {
        install("whisper-acft-small", 100L, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        TaiFunctionModels models = withRemote(env(12, 34, GpuPath.YES), true, true);
        models.set(TaiFunction.VOICE_TYPING, "remote/x");
        assertFalse(models.resolve(TaiFunction.VOICE_TYPING).isRemote());
        assertEquals("whisper-acft-small", models.resolve(TaiFunction.VOICE_TYPING).modelId);
    }

    @Test
    public void categoriesResolveToE2bOnTierTwoAndE4bOnTierThree() {
        installGemma();
        Resolution tier2 = models(env(12, 34, GpuPath.YES)).resolve(TaiFunction.APP_CATEGORIES);
        assertEquals(E2B, tier2.modelId);
        assertEquals("gpu", tier2.accelerator);
        assertEquals(E4B, models(env(16, 34, GpuPath.YES)).resolve(TaiFunction.APP_CATEGORIES).modelId);
    }

    @Test
    public void rulesOnlyAndRawTextAreTheWithoutModelChoices() {
        Env tier1 = env(4, 34, GpuPath.YES);
        Resolution reader = models(tier1).resolve(TaiFunction.WALLPAPER_READER);
        assertNull(reader.modelId);
        assertFalse(reader.isRemote());
        assertEquals(WithoutModel.RULES_ONLY, reader.without);
        assertEquals(WithoutModel.RAW_TEXT, models(tier1).resolve(TaiFunction.TIDY_DICTATION).without);
    }

    @Test
    public void theEmbedderResolvesApartFromTheChatAssistant() {
        installGemma();
        install(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 200L, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        TaiFunctionModels models = pong();
        models.set(TaiFunction.ASSISTANT, E4B);
        assertEquals(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, models.resolve(TaiFunction.EMBEDDINGS).modelId);
        assertEquals(E4B, models.resolve(TaiFunction.ASSISTANT).modelId);
    }

    @Test
    public void speechVoiceAndDepthResolveFromTheirPicksThenAutomatic() {
        install("whisper-acft-small", 100L, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        install("whisper-acft-base", 50L, TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        install(TaiModelCatalog.KITTEN_TTS_NANO_ID, 30L, TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
        install(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, 30L, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        install(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, 30L, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        TaiFunctionModels models = pong();
        assertEquals("whisper-acft-small", models.resolve(TaiFunction.VOICE_TYPING).modelId);
        models.set(TaiFunction.VOICE_TYPING, "whisper-acft-base");
        assertEquals("whisper-acft-base", models.resolve(TaiFunction.VOICE_TYPING).modelId);
        assertEquals(TaiModelCatalog.KITTEN_TTS_NANO_ID, models.resolve(TaiFunction.READ_ALOUD).modelId);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, models.resolve(TaiFunction.WALLPAPER_DEPTH).modelId);
        models.set(TaiFunction.WALLPAPER_DEPTH, TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, models.resolve(TaiFunction.WALLPAPER_DEPTH).modelId);
    }
}
