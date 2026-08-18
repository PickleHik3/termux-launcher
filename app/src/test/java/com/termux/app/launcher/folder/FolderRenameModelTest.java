package com.termux.app.launcher.folder;

import static org.junit.Assert.*;

import org.junit.Test;

public class FolderRenameModelTest {
    @Test public void unicodeCaretEditingLimitTrimAndDefault() {
        FolderRenameModel model = new FolderRenameModel("A😀B");
        assertEquals(3, model.codePointCount());
        model.moveCaret(-1); model.backspace();
        assertEquals("AB", model.text());
        model.moveCaret(-1); model.insert("中");
        assertEquals("中AB", model.text());
        model.delete();
        assertEquals("中B", model.text());
        model.insert("12345678901234567890123456789012345678901234567890");
        assertEquals(FolderRenameModel.MAX_CODE_POINTS, model.codePointCount());
        assertEquals("Folder", new FolderRenameModel(" \t ").committedTitle());
        assertEquals("Work", new FolderRenameModel("  Work  ").committedTitle());
    }
}
