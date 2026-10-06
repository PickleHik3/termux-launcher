package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/**
 * A new pane reuses the resolved faces only while nothing they were read from has changed, so
 * the memo must see every input: each config file, a drop-in appearing, an include that does not
 * exist yet, and the font folders a family name is looked for in.
 */
public class TerminalFontMemoTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private File write(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private TerminalFontConfig.Result load() {
        File root = temporary.getRoot();
        return TerminalFontConfig.load(new File(root, "kitty/kitty.conf"), new File(root, "fonts.d"),
            new File(root, "fonts.conf"));
    }

    @Test
    public void everyConfigInputIsRecordedEvenWhenMissing() throws Exception {
        File root = temporary.getRoot();
        write(new File(root, "kitty/kitty.conf"), "include extra.conf\n");
        File dropIn = write(new File(root, "fonts.d/10-a.conf"), "bold_font family=ten\n");

        TerminalFontConfig.Result result = load();

        assertTrue(result.inputs().containsAll(Arrays.asList(new File(root, "kitty/kitty.conf"),
            new File(root, "fonts.d"), dropIn, new File(root, "fonts.conf"),
            new File(root, "kitty/extra.conf"))));
    }

    @Test
    public void theStampMovesWhenAnInputIsWrittenOrAppears() throws Exception {
        File root = temporary.getRoot();
        File conf = write(new File(root, "fonts.conf"), "bold_font family=one\n");
        long before = TerminalFontMemo.stampFiles(load().inputs());
        assertEquals(before, TerminalFontMemo.stampFiles(load().inputs()));

        write(conf, "bold_font family=one-and-more\n");
        long rewritten = TerminalFontMemo.stampFiles(load().inputs());
        assertNotEquals(before, rewritten);

        write(new File(root, "fonts.d/10-a.conf"), "italic_font family=two\n");
        assertNotEquals(rewritten, TerminalFontMemo.stampFiles(load().inputs()));
    }

    @Test
    public void aFontAddedToAFamilyFolderMovesTheTreeStamp() throws Exception {
        File fonts = temporary.newFolder("fonts");
        write(new File(fonts, "a/Alpha-Regular.ttf"), "x");
        Long before = FontFamilyIndex.treeStamp(Collections.singletonList(fonts));
        assertEquals(before, FontFamilyIndex.treeStamp(Collections.singletonList(fonts)));

        write(new File(fonts, "a/Beta-Regular.ttf"), "y");
        assertNotEquals(before, FontFamilyIndex.treeStamp(Collections.singletonList(fonts)));
    }
}
