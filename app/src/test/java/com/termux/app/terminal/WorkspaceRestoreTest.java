package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.WindowForegroundResolver;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Save keeps what runs in every pane and restore starts it again (#366). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WorkspaceRestoreTest {

    /** A fake /proc: shell pids with their foreground group and its argv. */
    private static final class FakeProc implements WindowForegroundResolver.ProcSource {
        final Map<String, String> files = new HashMap<>();

        FakeProc idle(int shell) {
            files.put("/proc/" + shell + "/stat", stat(shell, shell));
            return this;
        }

        FakeProc running(int shell, int group, String... argv) {
            files.put("/proc/" + shell + "/stat", stat(shell, group));
            files.put("/proc/" + group + "/cmdline", String.join("\0", argv) + "\0");
            files.put("/proc/" + group + "/stat", stat(group, group));
            return this;
        }

        private static String stat(int pid, int tpgid) {
            return pid + " (x) S 1 " + pid + " " + pid + " 0 " + tpgid + " 0 0 0 0 0 0 0 0 0 0";
        }

        @Override public String read(String path) { return files.get(path); }

        @Override public int[] pids() { return new int[0]; }
    }

    private static final WorkspaceRestore.Notices NOTICES = new WorkspaceRestore.Notices() {
        @Override public String programMissing(String program) { return program + " is not installed"; }
        @Override public String agentStartedFresh(String agent) {
            return agent + " started a new conversation";
        }
        @Override public String folderMissing(String folder, String program) {
            return program == null ? "folder " + folder + " is gone"
                : program + " did not start, folder " + folder + " is gone";
        }
    };

    private static TerminalWorkspace.Pane save(FakeProc proc, int pid, String cwd) {
        return new TerminalWorkspace.Pane(cwd, null,
            WindowForegroundResolver.captureCommand(pid, proc));
    }

    @Test
    public void aRunningProgramIsReadFreshAtSaveTime() {
        TerminalWorkspace.Pane pane = save(new FakeProc().running(10, 500, "nvim", "/h/foo.md"), 10, "/h");
        assertEquals(Arrays.asList("nvim", "/h/foo.md"), pane.command);
    }

    @Test
    public void aWholeSessionSavesAndRestoresPanesFoldersAndPrograms() throws Exception {
        FakeProc proc = new FakeProc()
            .running(10, 500, "clock")
            .running(11, 501, "nvim", "/h/foo.md")
            .idle(12);
        TerminalWorkspace.Node tree = new TerminalWorkspace.Split(TerminalWorkspace.Split.VERTICAL,
            1f, 1f, save(proc, 10, "/h"),
            new TerminalWorkspace.Split(TerminalWorkspace.Split.HORIZONTAL, 1f, 1f,
                save(proc, 11, "/h/docs"), save(proc, 12, "/h/gone")));
        TerminalWorkspace saved = new TerminalWorkspace("dev", 1L, 0,
            Collections.singletonList(new TerminalWorkspace.Session("main", 0,
                Collections.singletonList(new TerminalWorkspace.Window(1, tree)))));
        saved.validate();
        TerminalWorkspace loaded = TerminalWorkspace.fromJson(saved.toJson());

        List<TerminalWorkspace.Pane> panes = new ArrayList<>();
        collect(loaded.sessions.get(0).windows.get(0).root, panes);
        assertEquals(3, panes.size());

        List<WorkspaceRestore.PanePlan> plans = new ArrayList<>();
        for (TerminalWorkspace.Pane pane : panes)
            plans.add(WorkspaceRestore.plan(pane, dir -> !dir.equals("/h/gone"), "/home", NOTICES));

        assertEquals("/h", plans.get(0).cwd);
        assertEquals(Collections.singletonList("clock"), plans.get(0).command);
        assertNull(plans.get(0).note);
        assertEquals("/h/docs", plans.get(1).cwd);
        assertEquals(Arrays.asList("nvim", "/h/foo.md"), plans.get(1).command);
        // The idle pane in a vanished folder: a shell at home with one line saying so.
        assertEquals("/home", plans.get(2).cwd);
        assertTrue(plans.get(2).command.isEmpty());
        assertEquals("folder /h/gone is gone", plans.get(2).note);
    }

    @Test
    public void aMissingFolderDoesNotStartTheProgramAndSaysSo() {
        WorkspaceRestore.PanePlan plan = WorkspaceRestore.plan(
            new TerminalWorkspace.Pane("/h/gone", null, Arrays.asList("nvim", "x")),
            dir -> false, "/home", NOTICES);
        assertEquals("/home", plan.cwd);
        assertTrue(plan.command.isEmpty());
        assertEquals("nvim did not start, folder /h/gone is gone", plan.note);
    }

    @Test
    public void aMissingProgramLeavesAShellWithOneLine() {
        WorkspaceRestore.PanePlan plan = WorkspaceRestore.plan(
            new TerminalWorkspace.Pane("/h", null, Arrays.asList("/usr/bin/nvim", "a b")),
            dir -> true, "/home", NOTICES);
        String posix = WorkspaceRestore.script(plan, "/bin/bash", false, NOTICES);
        assertEquals("if command -v '/usr/bin/nvim' >/dev/null 2>&1; then '/usr/bin/nvim' 'a b'; "
            + "else printf '%s\\n' '/usr/bin/nvim is not installed'; fi; exec '/bin/bash' -l", posix);
        String fish = WorkspaceRestore.script(plan, "/bin/fish", true, NOTICES);
        assertEquals("if command -v '/usr/bin/nvim' >/dev/null 2>&1; '/usr/bin/nvim' 'a b'; "
            + "else; printf '%s\\n' '/usr/bin/nvim is not installed'; end; exec '/bin/fish' -l", fish);
    }

    @Test
    public void anIdlePaneIsAPlainShellAndANoteAloneIsPrinted() {
        WorkspaceRestore.PanePlan idle = WorkspaceRestore.plan(
            new TerminalWorkspace.Pane("/h", null, null), dir -> true, "/home", NOTICES);
        assertNull(WorkspaceRestore.script(idle, "/bin/bash", false, NOTICES));
        WorkspaceRestore.PanePlan noted = WorkspaceRestore.plan(
            new TerminalWorkspace.Pane("/h/gone", null, null), dir -> false, "/home", NOTICES);
        assertEquals("printf '%s\\n' 'folder /h/gone is gone'; exec '/bin/bash' -l",
            WorkspaceRestore.script(noted, "/bin/bash", false, NOTICES));
    }

    @Test
    public void aMissingCwdFallsBackToTheDefaultWithoutANote() {
        WorkspaceRestore.PanePlan plan = WorkspaceRestore.plan(
            new TerminalWorkspace.Pane(null, null, null), dir -> false, "/home", NOTICES);
        assertEquals("/home", plan.cwd);
        assertNull(plan.note);
    }

    @Test
    public void everyOlderFormatStillLoads() throws Exception {
        for (int version = 1; version <= TerminalWorkspace.VERSION; version++) {
            org.json.JSONObject json = new TerminalWorkspace("w", 1L, 0,
                Collections.singletonList(new TerminalWorkspace.Session(null, 0,
                    Collections.singletonList(new TerminalWorkspace.Window(0,
                        new TerminalWorkspace.Pane("/h", null, Arrays.asList("nvim")))))))
                .toJson();
            json.put("version", version);
            TerminalWorkspace loaded = TerminalWorkspace.fromJson(json);
            assertEquals(1, loaded.commandCount());
        }
    }

    private static TerminalWorkspace.Pane agentPane(String cwd, String agent, String id, String... argv) {
        return new TerminalWorkspace.Pane(cwd, null, Arrays.asList(argv), agent, id);
    }

    private static WorkspaceRestore.PanePlan planOf(TerminalWorkspace.Pane pane) {
        return WorkspaceRestore.plan(pane, dir -> true, "/home", NOTICES);
    }

    @Test
    public void aSavedIdRewritesTheCommandToItsResumeAndKeepsTheOriginalAsFallback() {
        WorkspaceRestore.PanePlan plan = planOf(agentPane("/h", "claude", "id-1", "claude", "--model", "x"));
        assertEquals(Arrays.asList("claude", "--resume", "id-1"), plan.command);
        assertEquals(Arrays.asList("claude", "--model", "x"), plan.fallback);
        assertEquals("Claude started a new conversation", plan.freshLine);
        WorkspaceRestore.PanePlan codex = planOf(agentPane("/h", "codex", "c-9", "codex"));
        assertEquals(Arrays.asList("codex", "resume", "c-9"), codex.command);
        WorkspaceRestore.PanePlan open = planOf(agentPane("/h", "opencode", "ses_1", "opencode"));
        assertEquals(Arrays.asList("opencode", "--session", "ses_1"), open.command);
    }

    @Test
    public void anAgentWithNoIdStartsFreshWithOneLine() {
        WorkspaceRestore.PanePlan plan = planOf(agentPane("/h", null, null, "claude"));
        assertEquals(Arrays.asList("claude"), plan.command);
        assertTrue(plan.fallback.isEmpty());
        assertEquals("Claude started a new conversation", plan.freshLine);
        assertEquals("if command -v 'claude' >/dev/null 2>&1; then "
                + "printf '%s\\n' 'Claude started a new conversation'; 'claude'; "
                + "else printf '%s\\n' 'claude is not installed'; fi; exec '/bin/bash' -l",
            WorkspaceRestore.script(plan, "/bin/bash", false, NOTICES));
    }

    @Test
    public void anIdFromAnotherAgentOrAnOrdinaryProgramIsIgnored() {
        WorkspaceRestore.PanePlan other = planOf(agentPane("/h", "codex", "id", "claude"));
        assertEquals(Arrays.asList("claude"), other.command);
        WorkspaceRestore.PanePlan nvim = planOf(agentPane("/h", "claude", "id", "nvim", "a"));
        assertEquals(Arrays.asList("nvim", "a"), nvim.command);
        assertNull(nvim.freshLine);
    }

    @Test
    public void aStaleIdFallsBackToAFreshStartInBothShellFamilies() {
        WorkspaceRestore.PanePlan plan = planOf(agentPane("/h", "claude", "id-1", "claude", "-c"));
        assertEquals("if command -v 'claude' >/dev/null 2>&1; then t=$(date +%s); "
                + "'claude' '--resume' 'id-1' || { [ $(( $(date +%s) - t )) -lt "
                + WorkspaceRestore.RESUME_FAILS_WITHIN_S + " ] && { "
                + "printf '%s\\n' 'Claude started a new conversation'; 'claude' '-c'; }; }; "
                + "else printf '%s\\n' 'claude is not installed'; fi; exec '/bin/bash' -l",
            WorkspaceRestore.script(plan, "/bin/bash", false, NOTICES));
        assertEquals("if command -v 'claude' >/dev/null 2>&1; set -l t (date +%s); "
                + "'claude' '--resume' 'id-1'; or begin; if test (math (date +%s) - $t) -lt "
                + WorkspaceRestore.RESUME_FAILS_WITHIN_S + "; "
                + "printf '%s\\n' 'Claude started a new conversation'; 'claude' '-c'; end; end; "
                + "else; printf '%s\\n' 'claude is not installed'; end; exec '/bin/fish' -l",
            WorkspaceRestore.script(plan, "/bin/fish", true, NOTICES));
    }

    @Test
    public void twoClaudePanesInOneFolderEachResumeTheirOwnIdThroughSaveAndRestore() throws Exception {
        TerminalWorkspace.Node tree = new TerminalWorkspace.Split(TerminalWorkspace.Split.VERTICAL,
            1f, 1f, agentPane("/h/proj", "claude", "aaaa-1111", "claude"),
            agentPane("/h/proj", "claude", "bbbb-2222", "claude"));
        TerminalWorkspace saved = new TerminalWorkspace("w", 1L, 0,
            Collections.singletonList(new TerminalWorkspace.Session(null, 0,
                Collections.singletonList(new TerminalWorkspace.Window(0, tree)))));
        saved.validate();
        TerminalWorkspace loaded = TerminalWorkspace.fromJson(saved.toJson());
        loaded.validate();
        List<TerminalWorkspace.Pane> panes = new ArrayList<>();
        collect(loaded.sessions.get(0).windows.get(0).root, panes);
        assertEquals(Arrays.asList("claude", "--resume", "aaaa-1111"), planOf(panes.get(0)).command);
        assertEquals(Arrays.asList("claude", "--resume", "bbbb-2222"), planOf(panes.get(1)).command);
    }

    @Test
    public void aWorkspaceWithAnAgentIdIsWrittenAsVersionSixAndAV5FileStillLoads() throws Exception {
        assertEquals(6, TerminalWorkspace.VERSION);
        TerminalWorkspace with = new TerminalWorkspace("w", 1L, 0,
            Collections.singletonList(new TerminalWorkspace.Session(null, 0,
                Collections.singletonList(new TerminalWorkspace.Window(0,
                    agentPane("/h", "claude", "id-1", "claude"))))));
        org.json.JSONObject json = with.toJson();
        assertEquals(6, json.getInt("version"));
        TerminalWorkspace.Pane back = (TerminalWorkspace.Pane)
            TerminalWorkspace.fromJson(json).sessions.get(0).windows.get(0).root;
        assertEquals("claude", back.agent);
        assertEquals("id-1", back.agentSessionId);
        // A v5 file has neither key.
        json.put("version", 5);
        org.json.JSONObject node = json.getJSONArray("sessions").getJSONObject(0)
            .getJSONArray("windows").getJSONObject(0).getJSONObject("root");
        node.remove("agent");
        node.remove("agentSessionId");
        TerminalWorkspace.Pane old = (TerminalWorkspace.Pane)
            TerminalWorkspace.fromJson(json).sessions.get(0).windows.get(0).root;
        assertNull(old.agentSessionId);
        assertEquals(Arrays.asList("claude"), old.command);
    }

    private static void collect(TerminalWorkspace.Node node, List<TerminalWorkspace.Pane> out) {
        if (node instanceof TerminalWorkspace.Pane) {
            out.add((TerminalWorkspace.Pane) node);
            return;
        }
        TerminalWorkspace.Split split = (TerminalWorkspace.Split) node;
        collect(split.a, out);
        collect(split.b, out);
    }
}
