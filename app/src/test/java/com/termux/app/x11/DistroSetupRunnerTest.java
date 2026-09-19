package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * The pure part of where D9's work runs: the pane it is opened in, and finding that pane again on
 * a second tap. {@link DistroSetupRunner#run} itself needs a live {@code TerminalActionDispatcher}
 * and a real pane behind it, which is the device gate's job.
 */
public class DistroSetupRunnerTest {

    @Test public void theRunGoesIntoATaggedPaneOfItsOwn() throws JSONException {
        JSONObject arguments = DistroSetupRunner.openArguments("set -u\necho hello\n", "Setting up");

        assertEquals("set -u\necho hello\n", arguments.getString("command"));
        assertEquals("Setting up", arguments.getString("title"));
        assertEquals("distro-setup", arguments.getString("tag"));
        assertTrue("the user is meant to watch it", arguments.getBoolean("focus"));
    }

    @Test public void aSecondTapFindsTheSetupPaneAgain() throws JSONException {
        JSONObject list = paneList(new JSONObject()
            .put("id", "p1").put("running", true).put("pid", 4242)
            .put("agent", new JSONObject().put("tag", DistroSetupRunner.TAG)));

        LinuxTerminalAppRunner.ExistingPane found =
            LinuxTerminalAppRunner.findExistingPane(list, DistroSetupRunner.TAG);

        assertNotNull(found);
        assertEquals("p1", found.id);
        assertEquals(4242, found.pid);
    }

    @Test public void aPaneRunningSomethingElseIsNotTheSetupPane() throws JSONException {
        JSONObject list = paneList(new JSONObject()
            .put("id", "p1").put("running", true).put("pid", 7)
            .put("agent", new JSONObject().put("tag", "linux-terminal-app:distro:debian:htop")));

        assertNull(LinuxTerminalAppRunner.findExistingPane(list, DistroSetupRunner.TAG));
    }

    private JSONObject paneList(JSONObject pane) throws JSONException {
        return new JSONObject().put("ok", true).put("windows", new JSONArray()
            .put(new JSONObject().put("panes", new JSONArray().put(pane))));
    }
}
