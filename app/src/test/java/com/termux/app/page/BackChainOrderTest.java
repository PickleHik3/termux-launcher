package com.termux.app.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.chrome.OverlayRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** ADR 0030 section 3: raised overlays, then the front page, then Home page 1. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BackChainOrderTest {

    private static final class Raised implements OverlayRegistry.Overlay {
        final String name;
        final List<String> log;
        boolean open = true;

        Raised(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override public boolean onBack() {
            if (!open) return false;
            open = false;
            log.add(name);
            return true;
        }
    }

    private static final class Assistant implements Page {
        final List<String> log;
        boolean open = true;

        Assistant(List<String> log) { this.log = log; }

        @NonNull @Override public PageKind kind() { return PageKind.ASSISTANT; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
        @NonNull @Override public Rest rest(@NonNull Rest.Inputs in) { throw new UnsupportedOperationException(); }
        @Override public void onFront() { }
        @Override public void offFront() { }
        @Override public boolean onBack() {
            if (!open) return false;
            open = false;
            log.add("assistant");
            return true;
        }
    }

    @Test
    public void backClosesRaisedThenTheFrontPageThenGoesHome() {
        List<String> log = new ArrayList<>();
        Pages pages = new Pages(PageHost.NONE, () -> { });
        pages.setHomeDefault(() -> { log.add("home1"); return true; });
        Assistant assistant = new Assistant(log);
        pages.put(3, assistant);
        pages.landed(3, PageKind.ASSISTANT);

        OverlayRegistry registry = new OverlayRegistry();
        registry.register(new Raised("sheet", log));
        registry.register(new Raised("menu", log));
        registry.register(new Raised("editor", log));
        registry.register(new Raised("switchPanel", log));
        registry.register(pages::onBack);

        for (int i = 0; i < 4; i++) assertTrue(registry.onBackPressed());
        assertEquals(List.of("sheet", "menu", "editor", "switchPanel"), log);
        assertTrue(assistant.open);

        assertTrue(registry.onBackPressed());
        assertEquals("assistant", log.get(4));
        assertFalse(assistant.open);

        assertTrue(registry.onBackPressed());
        assertEquals(List.of("sheet", "menu", "editor", "switchPanel", "assistant", "home1"), log);
    }
}
