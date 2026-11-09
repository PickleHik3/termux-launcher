package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AssistantMarkdownTest {

    private static final AssistantMarkdown.Style STYLE = new AssistantMarkdown.Style(0x11000000, 0xFF000000, 0xFF0000FF, null);

    @Test
    public void paragraphsAreSeparatedByBlankLines() {
        List<AssistantMarkdown.Block> blocks = AssistantMarkdown.parse("One line\nsame paragraph\n\nSecond");
        assertEquals(2, blocks.size());
        assertEquals("One line\nsame paragraph", blocks.get(0).text);
        assertEquals("Second", blocks.get(1).text);
    }

    @Test
    public void bulletedAndNumberedListsKeepTheirMarkersAndNesting() {
        List<AssistantMarkdown.Block> blocks = AssistantMarkdown.parse("- one\n  - nested\n1. first\n2) second");
        assertEquals(4, blocks.size());
        assertEquals(AssistantMarkdown.Kind.LIST_ITEM, blocks.get(0).kind);
        assertEquals("•", blocks.get(0).marker);
        assertEquals(1, blocks.get(1).level);
        assertEquals("1.", blocks.get(2).marker);
        assertEquals("2.", blocks.get(3).marker);
        assertEquals("second", blocks.get(3).text);
    }

    @Test
    public void fencedCodeKeepsItsLanguageAndLines() {
        List<AssistantMarkdown.Block> blocks = AssistantMarkdown.parse("Run:\n```sh\nls -l\ncd ..\n```\nDone");
        assertEquals(3, blocks.size());
        AssistantMarkdown.Block code = blocks.get(1);
        assertEquals(AssistantMarkdown.Kind.CODE, code.kind);
        assertEquals("sh", code.lang);
        assertEquals("ls -l\ncd ..", code.text);
        assertEquals("Done", blocks.get(2).text);
    }

    @Test
    public void anUnclosedFenceIsACodeBlockOfWhatHasArrived() {
        List<AssistantMarkdown.Block> blocks = AssistantMarkdown.parse("Use this:\n```python\nprint('hi')\nfor x");
        assertEquals(2, blocks.size());
        assertEquals(AssistantMarkdown.Kind.CODE, blocks.get(1).kind);
        assertEquals("print('hi')\nfor x", blocks.get(1).text);
        // A fence just opened, nothing in it yet.
        List<AssistantMarkdown.Block> opened = AssistantMarkdown.parse("```");
        assertEquals(1, opened.size());
        assertEquals("", opened.get(0).text);
    }

    @Test
    public void headingsBecomeHeadingBlocks() {
        List<AssistantMarkdown.Block> blocks = AssistantMarkdown.parse("## Steps\ntext");
        assertEquals(AssistantMarkdown.Kind.HEADING, blocks.get(0).kind);
        assertEquals("Steps", blocks.get(0).text);
    }

    @Test
    public void boldItalicAndCodeAreStyled() {
        Spannable s = AssistantMarkdown.inline("a **bold** and *it* and `code` end", STYLE);
        assertEquals("a bold and it and code end", s.toString());
        StyleSpan[] styles = s.getSpans(0, s.length(), StyleSpan.class);
        assertEquals(2, styles.length);
        boolean bold = false;
        boolean italic = false;
        for (StyleSpan span : styles) {
            bold |= span.getStyle() == Typeface.BOLD;
            italic |= span.getStyle() == Typeface.ITALIC;
        }
        assertTrue(bold && italic);
    }

    @Test
    public void unclosedMarkersStayAsTyped() {
        assertEquals("start **bold never closed", AssistantMarkdown.inline("start **bold never closed", STYLE).toString());
        assertEquals("a `code", AssistantMarkdown.inline("a `code", STYLE).toString());
        assertEquals("2 * 3 = 6", AssistantMarkdown.inline("2 * 3 = 6", STYLE).toString());
        assertEquals("snake_case_name", AssistantMarkdown.inline("snake_case_name", STYLE).toString());
        assertEquals("**", AssistantMarkdown.inline("**", STYLE).toString());
    }

    @Test
    public void everyPrefixOfAnAnswerRendersWithoutThrowing() {
        String answer = "## Title\nUse **exiftool** and `rename`:\n\n1. Install it\n- *item* [link](https://example.com)\n"
            + "```sh\nexiftool -d '%Y' .\n```\nDone.";
        for (int i = 0; i <= answer.length(); i++) {
            for (AssistantMarkdown.Block block : AssistantMarkdown.parse(answer.substring(0, i))) {
                AssistantMarkdown.inline(block.text, STYLE);
            }
        }
    }

    @Test
    public void linksKeepTheirAddress() {
        Spannable s = AssistantMarkdown.inline("see [docs](https://example.com/a) now", STYLE);
        assertEquals("see docs now", s.toString());
        URLSpan[] links = s.getSpans(0, s.length(), URLSpan.class);
        assertEquals(1, links.length);
        assertEquals("https://example.com/a", links[0].getURL());
    }

    @Test
    public void plainStripsTheMarks() {
        assertEquals("Use exiftool now. Then run it.",
            AssistantMarkdown.plain("Use **exiftool** now.\n\nThen `run` it."));
        assertEquals("one two", AssistantMarkdown.plain("- one\n- two"));
    }
}
