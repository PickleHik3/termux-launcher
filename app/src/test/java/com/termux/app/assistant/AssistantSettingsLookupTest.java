package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The settings shortlist by embeddings, with a fake embedder, and the keyword lookup as the fallback. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class AssistantSettingsLookupTest {
    private static final String[] VOCAB = {"text", "size", "dark", "night", "motion", "animation", "contrast", "theme"};

    private static final String CORPUS =
        "text.size\tText size. How large text is. People say: bigger text\n"
        + "theme.mode\tTheme. Light or dark. People say: dark at night\n"
        + "terminal.contrast\tContrast. Text contrast. People say: hard to read\n";

    /** A bag-of-words embedder over {@link #VOCAB}; counts what it was asked. */
    private static final class Fake implements Embedder {
        @Nullable String model = "fake-1";
        boolean fail;
        int documentCalls;
        int queryCalls;
        int unloads;
        @Nullable java.util.concurrent.CountDownLatch entered;
        @Nullable java.util.concurrent.CountDownLatch gate;

        @Nullable @Override public String modelId() {
            return model;
        }

        @NonNull @Override public float[][] embed(@NonNull List<String> texts, boolean query) {
            if (fail) throw new IllegalStateException("no model");
            if (gate != null) {
                entered.countDown();
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (query) queryCalls++; else documentCalls++;
            float[][] out = new float[texts.size()][VOCAB.length];
            for (int i = 0; i < out.length; i++) {
                String lower = texts.get(i).toLowerCase(Locale.ROOT);
                for (int j = 0; j < VOCAB.length; j++) out[i][j] = lower.contains(VOCAB[j]) ? 1f : 0f;
                out[i][VOCAB.length - 1] += 0.01f;
            }
            return out;
        }

        @Override public void unload() {
            unloads++;
        }
    }

    private File cache;
    private SettingsRegistry registry;
    private final Fake fake = new Fake();
    private String corpus = CORPUS;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        cache = new File(context.getCacheDir(), "lookup_" + System.nanoTime() + "/vectors.bin");
        SettingStore memory = new InMemorySettingStore();
        registry = new SettingsRegistry(which -> memory, SettingDeclarations.ALL);
    }

    private AssistantSettingsLookup lookup() {
        return new AssistantSettingsLookup(fake, () -> corpus, cache);
    }

    private List<String> ids(AssistantSettingsLookup lookup, String message) {
        List<String> ids = new ArrayList<>();
        for (AssistantSettingsTurn.Candidate c : lookup.lookup(registry, message).offered) {
            ids.add(c.declaration.id);
        }
        return ids;
    }

    @Test
    public void vectorsAreUsedWhenAnEmbeddingModelAnswers() {
        List<String> ids = ids(lookup(), "night");
        assertEquals("theme.mode", ids.get(0));
        assertEquals(1, fake.queryCalls);
    }

    @Test
    public void noEmbeddingModelMeansTheKeywordLookup() {
        fake.model = null;
        List<String> ids = ids(lookup(), "make the text bigger");
        assertEquals("text.size", ids.get(0));
        assertEquals(0, fake.queryCalls);
        assertEquals(0, fake.documentCalls);
        assertFalse(cache.exists());
    }

    @Test
    public void aModelThatFailsMeansTheKeywordLookup() {
        fake.fail = true;
        List<String> ids = ids(lookup(), "make the text bigger");
        assertEquals("text.size", ids.get(0));
        assertEquals("closed even after a failure", 1, fake.unloads);
    }

    @Test
    public void theCacheIsBuiltOnceAndKeptAcrossTurnsAndPages() {
        AssistantSettingsLookup first = lookup();
        ids(first, "night");
        ids(first, "motion");
        assertEquals(1, first.rebuilds());
        assertEquals(1, fake.documentCalls);
        assertTrue(cache.isFile());

        first.release();
        ids(first, "night");
        assertEquals("a released page reads the file, it does not embed again", 1, first.rebuilds());

        AssistantSettingsLookup restarted = lookup();
        ids(restarted, "night");
        assertEquals(0, restarted.rebuilds());
        assertEquals(1, fake.documentCalls);
    }

    @Test
    public void theCacheRebuildsWhenTheCorpusChanges() {
        ids(lookup(), "night");
        corpus = CORPUS + "terminal.font\tFont. Terminal font. People say: font\n";
        AssistantSettingsLookup changed = lookup();
        ids(changed, "night");
        assertEquals(1, changed.rebuilds());
        assertEquals(2, fake.documentCalls);
        ids(lookup(), "night");
        assertEquals("the rebuilt file is the one now kept", 2, fake.documentCalls);
    }

    @Test
    public void theCacheRebuildsWhenTheModelChanges() {
        ids(lookup(), "night");
        fake.model = "fake-2";
        AssistantSettingsLookup changed = lookup();
        ids(changed, "night");
        assertEquals(1, changed.rebuilds());
        assertEquals(2, fake.documentCalls);
    }

    @Test
    public void aModelChangeInOnePageVisitRebuildsToo() {
        AssistantSettingsLookup lookup = lookup();
        ids(lookup, "night");
        fake.model = "fake-2";
        ids(lookup, "night");
        assertEquals(2, lookup.rebuilds());
    }

    @Test
    public void theEmbeddingModelIsClosedAfterEveryTurn() {
        AssistantSettingsLookup lookup = lookup();
        ids(lookup, "night");
        assertEquals(1, fake.unloads);
        ids(lookup, "motion");
        assertEquals(2, fake.unloads);
    }

    @Test
    public void anEmptyCorpusFallsBackToKeywords() {
        corpus = "";
        List<String> ids = ids(lookup(), "make the text bigger");
        assertEquals("text.size", ids.get(0));
        assertEquals(0, fake.documentCalls);
    }

    @Test
    public void releaseDuringAnEmbedDoesNotWaitAndTheEmbedDoesNotRefillTheCache() throws Exception {
        AssistantSettingsLookup lookup = lookup();
        fake.entered = new java.util.concurrent.CountDownLatch(1);
        fake.gate = new java.util.concurrent.CountDownLatch(1);
        Thread turn = new Thread(() -> ids(lookup, "night"));
        turn.start();
        assertTrue(fake.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));

        Thread leave = new Thread(lookup::release);
        leave.start();
        leave.join(2000);
        boolean blocked = leave.isAlive();
        fake.gate.countDown();
        turn.join(5000);
        assertFalse("release waited for the embed", blocked);
        assertFalse("a finished embed refilled a released cache", lookup.holdsVectors());
    }
}
