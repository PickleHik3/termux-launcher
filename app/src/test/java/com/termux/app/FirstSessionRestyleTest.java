package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * The first shell's styling broadcast is marked, and the activity only acts on the marked one
 * after a bootstrap install: on every other cold start it styled itself from the same files a
 * moment earlier, and the reload was ~240 ms of main thread right after the first frame.
 */
public class FirstSessionRestyleTest {

    @Test
    public void serviceMarksTheFirstSessionBroadcast() throws Exception {
        String service = source("app/src/main/java/com/termux/app/TermuxService.java");
        int first = service.indexOf("if (firstSession) {");
        assertTrue(first > 0);
        String block = service.substring(first, service.indexOf("return newTermuxSession;", first));
        assertTrue(block.contains("EXTRA_FIRST_SESSION_RESTYLE, true"));
        assertTrue(block.contains("EXTRA_RECREATE_ACTIVITY, false"));
        assertTrue(block.contains("setPackage(getPackageName())"));
        assertFalse(service.contains("TermuxActivity.updateTermuxActivityStyling(this, false)"));
    }

    @Test
    public void activitySkipsTheMarkedReloadUnlessABootstrapRan() throws Exception {
        String activity = source("app/src/main/java/com/termux/app/TermuxActivity.java");
        int action = activity.indexOf("case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:");
        String handler = activity.substring(action,
            activity.indexOf("case TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER", action));
        int skip = handler.indexOf("if (!mFirstSessionRestyleWanted)");
        assertTrue(skip > 0);
        assertTrue(skip < handler.indexOf("reloadActivityStyling("));

        int start = activity.indexOf("private void startBootstrapAndSession(");
        String bootstrap = activity.substring(start,
            activity.indexOf("private void maybeRecoverFromEmptySession", start));
        // Only an asynchronous answer (an install) wants the restyle; the synchronous fast path
        // for an existing prefix does not.
        assertTrue(bootstrap.contains("if (bootstrapReturned[0]) mFirstSessionRestyleWanted = true;"));
        assertTrue(bootstrap.lastIndexOf("bootstrapReturned[0] = true;")
            > bootstrap.indexOf("addNewSession("));
    }

    private static String source(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relative);
        if (!Files.exists(path)) path = root.getParent().resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
