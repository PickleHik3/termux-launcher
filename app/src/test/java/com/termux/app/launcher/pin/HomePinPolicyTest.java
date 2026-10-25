package com.termux.app.launcher.pin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.widget.HomeItemPlacementPolicy;
import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.LauncherWidgetRepository;
import com.termux.app.launcher.widget.WidgetCellRect;
import com.termux.app.launcher.widget.WidgetGridDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pin requests: the card's state, where an item lands (the page in front, else the first with
 * room, else a new page), a shortcut kept in the durable store and read back, the legacy intent
 * filter, and the handoff that holds work until Home is there to take it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class HomePinPolicyTest {
    private static final WidgetGridDefinition GRID = new WidgetGridDefinition(2, 2);

    private static final class Memory implements LauncherWidgetRepository.Storage {
        String value;
        @Override public String read() { return value; }
        @Override public boolean write(String next) { value = next; return true; }
    }

    private static LauncherWidgetRecord icon(int id, int page, int column, int row) {
        return LauncherWidgetRecord.shortcut(id, "app.pkg", 0L,
            new WidgetCellRect(column, row, column + 1, row + 1), page, new Bundle());
    }

    private static HomeItemPlacementPolicy.Pages pages(List<List<LauncherWidgetRecord>> held) {
        return page -> page < held.size() ? held.get(page)
            : Collections.<LauncherWidgetRecord>emptyList();
    }

    private static List<LauncherWidgetRecord> full(int page, int firstId) {
        List<LauncherWidgetRecord> out = new ArrayList<>();
        int id = firstId;
        for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) {
            out.add(icon(id--, page, column, row));
        }
        return out;
    }

    @Test public void theCardIsLockedBeforeItIsTooBigBeforeItIsReady() {
        assertEquals(HomePinPolicy.Card.LOCKED, HomePinPolicy.card(true, GRID, 5, 5));
        assertEquals(HomePinPolicy.Card.TOO_BIG, HomePinPolicy.card(false, GRID, 3, 1));
        assertEquals(HomePinPolicy.Card.TOO_BIG, HomePinPolicy.card(false, GRID, 1, 3));
        assertEquals(HomePinPolicy.Card.READY, HomePinPolicy.card(false, GRID, 2, 2));
        assertEquals(HomePinPolicy.Card.READY, HomePinPolicy.card(false, GRID, 1, 1));
    }

    @Test public void thePageInFrontWinsWhenItHasRoom() {
        List<List<LauncherWidgetRecord>> held = new ArrayList<>();
        held.add(Collections.<LauncherWidgetRecord>emptyList());
        held.add(Collections.singletonList(icon(-5, 1, 0, 0)));
        HomeItemPlacementPolicy.Choice choice = HomePinPolicy.place(GRID, 2, 1, pages(held), 1, 1);
        assertNotNull(choice);
        assertEquals(1, choice.page);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), choice.cell);
        assertFalse(choice.appendsPage);
    }

    @Test public void aFullFrontPageSendsItToTheFirstPageWithRoom() {
        List<List<LauncherWidgetRecord>> held = new ArrayList<>();
        held.add(Collections.singletonList(icon(-1, 0, 0, 0)));
        held.add(full(1, -10));
        HomeItemPlacementPolicy.Choice choice = HomePinPolicy.place(GRID, 2, 1, pages(held), 1, 1);
        assertNotNull(choice);
        assertEquals(0, choice.page);
        assertFalse(choice.appendsPage);
    }

    @Test public void everyPageFullMakesANewPageAfterTheLast() {
        List<List<LauncherWidgetRecord>> held = new ArrayList<>();
        held.add(full(0, -1));
        held.add(full(1, -10));
        HomeItemPlacementPolicy.Choice choice = HomePinPolicy.place(GRID, 2, 0, pages(held), 1, 1);
        assertNotNull(choice);
        assertEquals(2, choice.page);
        assertTrue(choice.appendsPage);
        assertEquals(HomePinPolicy.Landed.ON_NEW_PAGE, HomePinPolicy.landed(choice.page, 2));
    }

    @Test public void aWidgetNeedsAFreeAreaOfItsSize() {
        List<List<LauncherWidgetRecord>> held = new ArrayList<>();
        held.add(Collections.singletonList(icon(-1, 0, 0, 0)));
        // 2x2 cannot go on a page holding one icon, so it takes a page of its own.
        HomeItemPlacementPolicy.Choice choice = HomePinPolicy.place(GRID, 1, 0, pages(held), 2, 2);
        assertNotNull(choice);
        assertEquals(1, choice.page);
        assertTrue(choice.appendsPage);
        assertNull(HomePinPolicy.place(GRID, 1, 0, pages(held), 3, 1));
    }

    @Test public void landedNamesTheOutcome() {
        assertEquals(HomePinPolicy.Landed.NOWHERE, HomePinPolicy.landed(-1, 2));
        assertEquals(HomePinPolicy.Landed.ON_PAGE, HomePinPolicy.landed(1, 2));
        assertEquals(HomePinPolicy.Landed.ON_NEW_PAGE, HomePinPolicy.landed(2, 2));
    }

    @Test public void classicCellsFollowTheSeventyDpRule() {
        assertEquals(1, HomePinPolicy.classicCells(0));
        assertEquals(1, HomePinPolicy.classicCells(40));
        assertEquals(2, HomePinPolicy.classicCells(110));
        assertEquals(4, HomePinPolicy.classicCells(250));
    }

    @Test public void aLegacyLabelIsTrimmedAndCapped() {
        assertEquals("", HomePinPolicy.legacyLabel(null));
        assertEquals("", HomePinPolicy.legacyLabel("   "));
        assertEquals("Maps", HomePinPolicy.legacyLabel("  Maps "));
        assertEquals(HomePinPolicy.MAX_LABEL,
            HomePinPolicy.legacyLabel(new String(new char[200]).replace('\0', 'x')).length());
    }

    @Test public void aLegacyIntentAtThisAppIsRefusedOnEveryEdition() {
        assertFalse(HomePinPolicy.legacyIntentAllowed("com.edition.one", true, "com.edition.one"));
        assertFalse(HomePinPolicy.legacyIntentAllowed("com.edition.two", false, "com.edition.two"));
        assertTrue(HomePinPolicy.legacyIntentAllowed("com.edition.one", true, "com.edition.two"));
        assertTrue(HomePinPolicy.legacyIntentAllowed(null, true, "com.edition.one"));
        assertFalse(HomePinPolicy.legacyIntentAllowed(null, false, "com.edition.one"));
    }

    @Test public void aShortcutIsPlacedOnTheFrontPageAndSurvivesAReload() {
        Memory memory = new Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, "Docs");
        options.putString(LauncherWidgetRecord.SHORTCUT_ID, "doc-1");
        LauncherWidgetRecord placed = repository.addShortcut("docs.pkg", 7L, 0, options);
        assertNotNull(placed);
        assertEquals(0, placed.page);
        assertTrue(placed.isShortcut());

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        LauncherWidgetRecord back = reloaded.get(placed.appWidgetId);
        assertNotNull(back);
        assertTrue(back.isShortcut());
        assertEquals("doc-1", back.shortcutId());
        assertEquals("Docs", back.appLabel());
        assertEquals(new ComponentName("docs.pkg", "shortcut"), back.provider);
        assertEquals(7L, back.profileSerial);
    }

    @Test public void shortcutsFillAPageThenMakeANewOne() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new Memory());
        int cells = repository.gridDefinition().columns * repository.gridDefinition().rows;
        for (int i = 0; i < cells; i++) {
            LauncherWidgetRecord placed = repository.addShortcut("a.pkg", 0L, 0, new Bundle());
            assertNotNull(placed);
            assertEquals(0, placed.page);
        }
        assertEquals(1, repository.pageCount());
        LauncherWidgetRecord overflow = repository.addShortcut("a.pkg", 0L, 0, new Bundle());
        assertNotNull(overflow);
        assertEquals(1, overflow.page);
        assertEquals(2, repository.pageCount());
    }

    @Test public void handoffHoldsWorkUntilHomeIsAttachedThenRunsItOnce() {
        HomePinTarget home = (HomePinTarget) java.lang.reflect.Proxy.newProxyInstance(
            HomePinTarget.class.getClassLoader(), new Class<?>[] {HomePinTarget.class},
            (proxy, method, args) -> null);
        AtomicInteger runs = new AtomicInteger();
        assertFalse(HomePinHandoff.deliver(t -> runs.incrementAndGet()));
        assertEquals(0, runs.get());
        HomePinHandoff.attach(home);
        assertEquals(1, runs.get());
        assertTrue(HomePinHandoff.deliver(t -> runs.incrementAndGet()));
        assertEquals(2, runs.get());
        HomePinHandoff.detach(home);
        assertNull(HomePinHandoff.target());
        HomePinHandoff.attach(home);
        assertEquals(2, runs.get());
        HomePinHandoff.detach(home);
    }

    @Test public void handoffKeepsOnlyTheNewestFewWhileNothingIsAttached() {
        HomePinTarget home = (HomePinTarget) java.lang.reflect.Proxy.newProxyInstance(
            HomePinTarget.class.getClassLoader(), new Class<?>[] {HomePinTarget.class},
            (proxy, method, args) -> null);
        AtomicInteger runs = new AtomicInteger();
        for (int i = 0; i < HomePinHandoff.MAX_WAITING + 5; i++) {
            HomePinHandoff.deliver(t -> runs.incrementAndGet());
        }
        HomePinHandoff.attach(home);
        assertEquals(HomePinHandoff.MAX_WAITING, runs.get());
        HomePinHandoff.detach(home);
    }
}
