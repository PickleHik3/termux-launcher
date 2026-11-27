package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel.Entry;
import com.termux.app.wall.pageswitch.PageBarModel.Input;
import com.termux.app.wall.pageswitch.PageBarModel.Page;
import com.termux.app.wall.pageswitch.PageBarModel.Action;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PageBarModelTest {

    /**
     * A wall from a kind string, ids 1.. in order: H Home, T Terminal, D Display, A Assistant.
     * Windows are named after their index unless {@code windows} is empty.
     */
    static Input wall(String kinds, int windows) {
        Input in = new Input();
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < kinds.length(); i++) {
            PageKind kind;
            switch (kinds.charAt(i)) {
                case 'H': kind = PageKind.HOME; break;
                case 'T': kind = PageKind.TERMINAL; break;
                case 'D': kind = PageKind.DISPLAY; break;
                default: kind = PageKind.ASSISTANT; break;
            }
            pages.add(new Page(i + 1, kind, null));
        }
        in.pages = pages;
        List<String> names = new ArrayList<>();
        for (int w = 0; w < windows; w++) names.add(null);
        in.windowNames = names;
        in.displayRunning = true;
        return in;
    }

    private static String labels(Input in) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : PageBarModel.build(in).entries()) {
            sb.append(sb.length() == 0 ? "" : "|").append(e.label);
        }
        return sb.toString();
    }

    @Test public void stopOrderInterleavesHomeWithTerminalAndDisplay() {
        Input in = wall("HTHD", 2);
        assertEquals("Home 1|Terminal 1|Terminal 2|Home 2|Display", labels(in));
    }

    @Test public void terminalWithNoWindowsIsOneEntryThatStartsAShell() {
        Input in = wall("HT", 0);
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals(2, e.size());
        assertEquals(0, e.get(1).number);
        assertEquals("Terminal", e.get(1).label);
        assertEquals(Action.GO_TERMINAL, e.get(1).target.action);
        assertEquals(-1, e.get(1).target.index);
        assertEquals(2, e.get(1).target.pageId);
    }

    @Test public void terminalWithThreeWindowsHasThreeEntriesWithWindowTargets() {
        Input in = wall("HT", 3);
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals(4, e.size());
        for (int w = 0; w < 3; w++) {
            assertEquals(w + 1, e.get(1 + w).number);
            assertEquals(Action.GO_TERMINAL, e.get(1 + w).target.action);
            assertEquals(w, e.get(1 + w).target.index);
        }
    }

    @Test public void displayIsLeftOutWhileNotRunning() {
        Input in = wall("HD", 0);
        in.displayRunning = false;
        assertEquals("Home 1", labels(in));
    }

    @Test public void twoDisplayPagesAreNumbered() {
        assertEquals("Display 1|Display 2", labels(wall("DD", 0)));
        assertEquals("Display", labels(wall("D", 0)));
    }

    @Test public void terminalOnlyOffersNoHome() {
        Input in = wall("HTH", 2);
        in.homeOffered = false;
        assertEquals("Terminal 1|Terminal 2", labels(in));
    }

    @Test public void ownNamesWinOverKindNames() {
        Input in = wall("HHT", 2);
        in.pages = Arrays.asList(new Page(1, PageKind.HOME, null), new Page(2, PageKind.HOME, "Work"),
            new Page(3, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList("htop", null);
        assertEquals("Home 1|Work|htop|Terminal 2", labels(in));
    }

    @Test public void linuxWindowAmongTerminalWindowsWearsTheLinuxGlyphKind() {
        Input in = wall("T", 1);
        in.windowNames = Arrays.asList("htop", "xeyes", null);
        in.linuxWindows = new boolean[] {false, true, true};
        PageBarModel m = PageBarModel.build(in);
        assertEquals(PageKind.TERMINAL, m.entries().get(0).kind);
        assertEquals(PageKind.LINUX, m.entries().get(1).kind);
        assertEquals(PageKind.LINUX, m.entries().get(2).kind);
        assertEquals("htop|xeyes|Display", labels(in));
        assertEquals(Action.GO_TERMINAL, m.entries().get(1).target.action);
        assertEquals(1, m.entries().get(1).target.index);
    }

    @Test public void windowLabelsLoseIconCodePoints() {
        Input in = wall("T", 1);
        in.windowNames = Arrays.asList(" vim");
        assertEquals("vim", labels(in));
    }

    @Test public void currentWindowIsMarkedAndCloses() {
        Input in = wall("HT", 3);
        in.currentPageId = 2;
        in.currentWindow = 1;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(2, m.currentIndex());
        assertTrue(m.entries().get(2).current);
        assertEquals(Action.CLOSE, m.entries().get(2).target.action);
        assertFalse(m.entries().get(1).current);
        assertEquals(Action.GO_TERMINAL, m.entries().get(1).target.action);
    }

    @Test public void windowToReturnToIsMarkedWhileOnAnotherPage() {
        Input in = wall("HT", 3);
        in.currentPageId = 1;
        in.currentWindow = 2;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(0, m.currentIndex());
        assertFalse(m.entries().get(3).current);
        assertTrue(m.entries().get(3).returnTo);
        assertFalse(m.entries().get(1).returnTo);
        assertEquals(Action.CLOSE, m.entries().get(0).target.action);
        assertEquals(Action.GO_TERMINAL, m.entries().get(3).target.action);
    }

    @Test public void homeAndAssistantTargetTheirPages() {
        Input in = wall("HA", 0);
        in.currentPageId = 2;
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals(Action.GO_PAGE, e.get(0).target.action);
        assertEquals(1, e.get(0).target.pageId);
        assertEquals(Action.CLOSE, e.get(1).target.action);
        assertEquals("Assistant", e.get(1).label);
    }

    @Test public void noCurrentEntryIsMinusOne() {
        Input in = wall("HT", 1);
        assertEquals(-1, PageBarModel.build(in).currentIndex());
    }

    @Test public void capShowsTheCurrentHomePageAndItsNumber() {
        Input in = wall("HHT", 1);
        in.currentPageId = 2;
        in.currentWindow = 0;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(PageKind.HOME, m.capKind());
        assertEquals(2, m.capNumber());
    }

    @Test public void capShowsTheWindowYouWouldReturnToWhenOnAnotherPage() {
        Input in = wall("HT", 3);
        in.currentPageId = 1;
        in.currentWindow = 1;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(PageKind.HOME, m.capKind());
        in.currentPageId = 2;
        m = PageBarModel.build(in);
        assertEquals(PageKind.TERMINAL, m.capKind());
        assertEquals(2, m.capNumber());
    }

    @Test public void capOfAssistantAndDisplayHasNoNumber() {
        Input in = wall("HDA", 0);
        in.currentPageId = 3;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(PageKind.ASSISTANT, m.capKind());
        assertEquals(0, m.capNumber());
    }

    @Test public void capFallsBackToTheTerminalWhenTheWallIsOnNoEntry() {
        Input in = wall("HT", 2);
        in.currentPageId = 99;
        in.currentWindow = 1;
        PageBarModel m = PageBarModel.build(in);
        assertEquals(PageKind.TERMINAL, m.capKind());
        assertEquals(2, m.capNumber());
    }

    @Test public void plainLabelDropsIconCodePointsButNeverEmpties() {
        assertEquals("htop", PageBarModel.plainLabel("\uE000 htop"));
        assertEquals("", PageBarModel.plainLabel(""));
        assertEquals("\uE000", PageBarModel.plainLabel("\uE000"));
    }

    @Test public void aLinuxWindowCarriesItsAppIdAsIconKey() {
        Input in = wall("HT", 3);
        in.linuxWindows = new boolean[] {false, true, false};
        in.windowAppIds = Arrays.asList(null, "org.gnome.gedit", null);
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals(null, e.get(1).iconKey);
        assertEquals("org.gnome.gedit", e.get(2).iconKey);
        assertEquals(null, e.get(3).iconKey);
        assertEquals(null, e.get(2).glyph);
    }

    @Test public void aBoundDisplayPageCarriesItsAppAnUnboundOneNone() {
        Input in = wall("DD", 0);
        in.pages = Arrays.asList(new Page(1, PageKind.DISPLAY, null, "firefox"),
            new Page(2, PageKind.DISPLAY, null));
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals("firefox", e.get(0).iconKey);
        assertEquals(null, e.get(1).iconKey);
        assertEquals(null, e.get(0).glyph);
    }

    @Test public void aTerminalWindowCarriesItsProgramGlyphOnlyWhenGiven() {
        Input in = wall("T", 3);
        in.windowGlyphs = Arrays.asList(null, "\uE7C5", null);
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals(null, e.get(0).glyph);
        assertEquals("\uE7C5", e.get(1).glyph);
        assertEquals(null, e.get(2).glyph);
        assertEquals(null, e.get(1).iconKey);
    }

    @Test public void aGlyphOnALinuxWindowAndAnAppIdOnATerminalWindowAreIgnored() {
        Input in = wall("T", 2);
        in.linuxWindows = new boolean[] {true, false};
        in.windowAppIds = Arrays.asList("app", "app");
        in.windowGlyphs = Arrays.asList("\uE7C5", "\uE7C5");
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals("app", e.get(0).iconKey);
        assertEquals(null, e.get(0).glyph);
        assertEquals(null, e.get(1).iconKey);
        assertEquals("\uE7C5", e.get(1).glyph);
    }

    @Test public void shortAndEmptyListsMeanNullForTheRest() {
        Input in = wall("T", 3);
        in.linuxWindows = new boolean[] {true, true, false};
        in.windowAppIds = Arrays.asList("a");
        in.windowGlyphs = new ArrayList<>();
        List<Entry> e = PageBarModel.build(in).entries();
        assertEquals("a", e.get(0).iconKey);
        assertEquals(null, e.get(1).iconKey);
        assertEquals(null, e.get(2).glyph);
    }

    @Test public void theNewFieldsChangeNoOrderNumberLabelOrTarget() {
        Input plain = wall("HTHD", 2);
        Input marked = wall("HTHD", 2);
        marked.windowGlyphs = Arrays.asList("\uE7C5", "\uE7C5");
        marked.windowAppIds = Arrays.asList("a", "b");
        List<Entry> a = PageBarModel.build(plain).entries();
        List<Entry> b = PageBarModel.build(marked).entries();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString());
            assertEquals(a.get(i).number, b.get(i).number);
            assertEquals(a.get(i).kind, b.get(i).kind);
        }
    }
}
