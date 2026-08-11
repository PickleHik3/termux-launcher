package com.termux.app.launcher.folder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class LauncherFolderPopupFreshStateTest {
    @Test public void popupActionsRetainIdsAndResolveLatestFolderBeforeMutation() throws Exception {
        String source = read("app/src/main/java/com/termux/app/SuggestionBarView.java");
        int contextStart = source.indexOf("private static final class AppMenuContext");
        int contextEnd = source.indexOf("private static final class", contextStart + 1);
        String context = source.substring(contextStart, contextEnd);
        assertTrue(context.contains("String sourceFolderId"));
        assertFalse(context.contains("PinnedFolderItem sourceFolder"));
        assertTrue(source.contains("refreshPinnedItemsFromRepository();\n            PinnedFolderItem folder = resolveLatestFolder(context.sourceFolderId)"));
        assertTrue(source.contains("latest.folder(popupFolderId)"));
    }

    private static String read(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relative);
        if (!Files.exists(path)) path = root.getParent().resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
