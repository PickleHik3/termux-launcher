package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The installed {@code launcherctl} script itself, run by the host's {@code sh} against a small
 * fake of the launcher's local server: group help, usage errors that must never reach the
 * launcher, {@code pane current}, {@code --print-id}, the caller's directory as the default, and
 * {@code pane wait}'s arguments. Skipped where there is no {@code sh} or {@code curl}.
 */
public class LauncherCtlCliScriptTest {

    private static final String[] GROUPS = {"launch", "pane", "window", "agent", "notify", "progress",
        "clipboard", "notifications", "keyboard", "vibrate", "torch", "battery", "volume", "toast",
        "wallpaper", "x11"};

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    private File script;
    private File home;
    private File work;
    private FakeServer server;

    @Before
    public void install() throws IOException {
        Assume.assumeTrue("needs sh", new File("/bin/sh").canExecute());
        Assume.assumeTrue("needs curl", onPath("curl"));
        script = folder.newFile("launcherctl");
        Files.write(script.toPath(), LauncherCtlApiServer.launcherctlCliScript("/bin")
            .getBytes(StandardCharsets.UTF_8));
        home = folder.newFolder("home");
        work = folder.newFolder("work");
        server = new FakeServer();
        File dir = new File(home, ".launcherctl");
        assertTrue(dir.mkdirs());
        Files.write(new File(dir, "token").toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "endpoint").toPath(),
            ("http://127.0.0.1:" + server.port() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void stop() throws IOException {
        if (server != null) server.close();
    }

    @Test
    public void everyGroupHasHelpAndTheOverviewNamesThem() throws Exception {
        Run overview = run(null, "--help");
        assertEquals(overview.err, 0, overview.code);
        for (String group : GROUPS) {
            assertTrue(group, overview.out.contains(group));
            Run help = run(null, group, "--help");
            assertEquals(group + help.err, 0, help.code);
            assertTrue(group + ": " + help.out, help.out.startsWith("launcherctl " + group + " - "));
            Run shortHelp = run(null, group, "-h");
            assertEquals(help.out, shortHelp.out);
            Run named = run(null, "help", group);
            assertEquals(help.out, named.out);
        }
        assertTrue(server.requests.isEmpty());
    }

    @Test
    public void aGroupThatNeedsACommandExplainsItselfInsteadOfActing() throws Exception {
        for (String group : new String[]{"launch", "pane", "window", "agent", "progress", "clipboard",
                "keyboard", "torch", "wallpaper", "x11"}) {
            Run bare = run(null, group);
            assertEquals(group, 2, bare.code);
            assertEquals(group, "", bare.out);
            assertTrue(group + ": " + bare.err, bare.err.startsWith("launcherctl " + group + " - "));
        }
        assertTrue(server.requests.toString(), server.requests.isEmpty());
    }

    @Test
    public void missingOrMalformedArgumentsNeverReachTheLauncher() throws Exception {
        String[][] mistakes = {
            {"pane", "get"},
            {"pane", "focus"},
            {"pane", "close", ""},
            {"pane", "close", "../x"},
            {"pane", "read", "p1", "--lines"},
            {"pane", "read", "p1", "--lines", "ten"},
            {"pane", "write"},
            {"pane", "open", "--cwd"},
            {"pane", "open", "--from"},
            {"pane", "open", "--bogus"},
            {"pane", "wait"},
            {"pane", "wait", "p1"},
            {"pane", "wait", "p1", "--match"},
            {"pane", "wait", "p1", "--match", ""},
            {"pane", "wait", "p1", "--match", "a", "--regex", "b"},
            {"pane", "wait", "p1", "--match", "a", "--timeout", "soon"},
            {"pane", "wait", "p1", "--match", "a", "--timeout", "300001"},
            {"pane", "wait", "p1", "--match", "a", "--timeout", "99999999999999999999"},
            {"pane", "wait", "p1", "--match", "a", "--lines", "-3"},
            {"pane", "wait", "--help-me"},
            {"pane", "current"},
            {"window", "open"},
            {"window", "open", "--title"},
            {"window", "focus"},
            {"pane", "nonsense"},
        };
        for (String[] mistake : mistakes) {
            Run result = run(null, mistake);
            assertEquals(Arrays.toString(mistake) + result.err, 2, result.code);
            assertFalse(Arrays.toString(mistake), result.err.isEmpty());
        }
        assertTrue(server.requests.toString(), server.requests.isEmpty());
    }

    @Test
    public void paneCurrentAsksForThisPaneAndExplainsWhenThereIsNone() throws Exception {
        Run outside = run(null, "pane", "current");
        assertEquals(2, outside.code);
        assertTrue(outside.err, outside.err.contains("TERMUX_LAUNCHER_PANE"));

        server.answer(200, "application/json", "{\"ok\":true,\"pane\":{\"id\":\"p7\"}}");
        Run inside = run("p7", "pane", "current");
        assertEquals(inside.err, 0, inside.code);
        assertEquals("{\"ok\":true,\"pane\":{\"id\":\"p7\"}}\n", inside.out);
        assertEquals("GET /v1/panes/p7", server.requests.poll().line);

        server.answer(200, "application/json", "{\"ok\":true}");
        assertEquals(0, run(null, "pane", "get", "abc-1").code);
        assertEquals("GET /v1/panes/abc-1", server.requests.poll().line);
    }

    @Test
    public void printIdAsksForTheIdAndPrintsExactlyThatLine() throws Exception {
        server.answer(200, "text/plain; charset=utf-8", "4f1c-77\n");
        Run opened = run(null, "pane", "open", "--print-id", "--no-focus", "--", "make", "test");
        assertEquals(opened.err, 0, opened.code);
        assertEquals("4f1c-77\n", opened.out);
        Request request = server.requests.poll();
        assertEquals("POST /v1/panes", request.line);
        assertTrue(request.body, request.body.contains("\"print\":\"id\""));
        assertTrue(request.body, request.body.contains("\"focus\":false"));
        assertTrue(request.body, request.body.contains("\"command\":[\"make\",\"test\"]"));
        // No --cwd: the directory this ran in, not the launcher's default.
        assertTrue(request.body, request.body.contains("\"cwd\":\"" + work.getCanonicalPath() + "\""));

        server.answer(200, "text/plain; charset=utf-8", "9a-2\n");
        Run window = run(null, "window", "open", "--print-id", "--cwd", "/tmp", "--", "htop");
        assertEquals(window.err, 0, window.code);
        assertEquals("9a-2\n", window.out);
        Request windowRequest = server.requests.poll();
        assertEquals("POST /v1/windows", windowRequest.line);
        assertTrue(windowRequest.body, windowRequest.body.contains("\"print\":\"id\""));
        assertTrue(windowRequest.body, windowRequest.body.contains("\"cwd\":\"/tmp\""));

        // A refusal is still the JSON answer, and exit 1.
        server.answer(409, "application/json", "{\"ok\":false,\"error\":\"pane_open_failed\"}");
        Run refused = run(null, "pane", "open", "--print-id");
        assertEquals(1, refused.code);
        assertEquals("{\"ok\":false,\"error\":\"pane_open_failed\"}\n", refused.out);
    }

    @Test
    public void paneOpenFromThisPaneFallsBackToTheFocusedOneWithTheSameRequest() throws Exception {
        server.answer(404, "application/json", "{\"ok\":false,\"error\":\"pane_not_found\"}");
        server.answer(200, "text/plain; charset=utf-8", "new-1\n");
        Run opened = run("gone", "pane", "open", "--print-id");
        assertEquals(opened.err, 0, opened.code);
        assertEquals("new-1\n", opened.out);
        assertTrue(server.requests.poll().body.contains("\"from\":\"gone\""));
        String retry = server.requests.poll().body;
        assertFalse(retry, retry.contains("\"from\""));
        assertTrue(retry, retry.contains("\"print\":\"id\""));
    }

    @Test
    public void paneWaitSendsOneRequestWithTheMatchAndTimeout() throws Exception {
        server.answer(200, "application/json", "{\"ok\":true,\"matched\":true,\"text\":\"done\"}");
        Run matched = run(null, "pane", "wait", "p1", "--regex", "do\"ne$", "--timeout", "0120000",
            "--lines", "200");
        assertEquals(matched.err, 0, matched.code);
        Request request = server.requests.poll();
        assertEquals("POST /v1/panes/p1/wait", request.line);
        assertTrue(request.body, request.body.contains("\"regex\":\"do\\\"ne$\""));
        assertTrue(request.body, request.body.contains("\"timeout_ms\":120000"));
        assertTrue(request.body, request.body.contains("\"lines\":\"200\""));

        server.answer(408, "application/json", "{\"ok\":false,\"error\":\"timeout\"}");
        Run timedOut = run(null, "pane", "wait", "p1", "--match", "done");
        assertEquals(1, timedOut.code);
        assertTrue(server.requests.poll().body.contains("\"timeout_ms\":" + PaneWait.DEFAULT_TIMEOUT_MS));
    }

    @Test
    public void theScriptsWaitCeilingIsTheServers() {
        String text = LauncherCtlApiServer.launcherctlCliScript("/bin");
        assertTrue(text.contains("WAIT_MAX_MS=" + PaneWait.MAX_TIMEOUT_MS + "\n"));
        assertTrue(text.contains("WAIT_DEFAULT_MS=" + PaneWait.DEFAULT_TIMEOUT_MS + "\n"));
        assertTrue(text.startsWith("#!/bin/sh\n"));
    }

    private Run run(@Nullable String pane, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("sh");
        command.add(script.getAbsolutePath());
        Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(work);
        Map<String, String> env = builder.environment();
        String path = env.get("PATH");
        env.clear();
        env.put("PATH", path == null ? "/usr/bin:/bin" : path);
        env.put("HOME", home.getAbsolutePath());
        if (pane != null) env.put("TERMUX_LAUNCHER_PANE", pane);
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        Process process = builder.start();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread errReader = new Thread(() -> copy(process.getErrorStream(), err));
        errReader.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(process.getInputStream(), out);
        assertTrue("launcherctl hung: " + Arrays.toString(args), process.waitFor(30, TimeUnit.SECONDS));
        errReader.join();
        return new Run(process.exitValue(), out.toString("UTF-8"), err.toString("UTF-8"));
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buffer = new byte[4096];
        try {
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        } catch (IOException ignored) {
        }
    }

    private static boolean onPath(@NonNull String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, program).canExecute()) return true;
        }
        return false;
    }

    private static final class Run {
        final int code;
        final String out;
        final String err;

        Run(int code, String out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }
    }

    private static final class Request {
        final String line;
        final String body;

        Request(String line, String body) {
            this.line = line;
            this.body = body;
        }

        @Override public String toString() {
            return line + " " + body;
        }
    }

    /**
     * Just enough HTTP/1.1 for curl: one request per connection, each answered with the next
     * queued answer (500 when none is queued), every request recorded.
     */
    private static final class FakeServer {
        final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<String[]> answers = new ConcurrentLinkedQueue<>();
        private final ServerSocket socket;
        private final Thread thread;

        FakeServer() throws IOException {
            socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(this::serve, "fake-launcherctl-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        void answer(int status, String contentType, String body) {
            answers.add(new String[]{String.valueOf(status), contentType, body});
        }

        void close() throws IOException {
            socket.close();
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    InputStream in = client.getInputStream();
                    String requestLine = readLine(in);
                    int length = 0;
                    for (String header; !(header = readLine(in)).isEmpty(); ) {
                        if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                            length = Integer.parseInt(header.substring(15).trim());
                        }
                    }
                    byte[] body = new byte[length];
                    for (int read = 0; read < length; ) {
                        int n = in.read(body, read, length - read);
                        if (n < 0) break;
                        read += n;
                    }
                    String[] parts = requestLine.split(" ");
                    requests.add(new Request(parts[0] + " " + parts[1], new String(body, StandardCharsets.UTF_8)));
                    String[] answer = answers.poll();
                    if (answer == null) answer = new String[]{"500", "application/json", "{\"ok\":false}"};
                    byte[] payload = answer[2].getBytes(StandardCharsets.UTF_8);
                    OutputStream out = client.getOutputStream();
                    out.write(("HTTP/1.1 " + answer[0] + " X\r\nContent-Type: " + answer[1]
                        + "\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                    out.write(payload);
                    out.flush();
                } catch (IOException | RuntimeException e) {
                    if (socket.isClosed()) return;
                }
            }
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            for (int c; (c = in.read()) >= 0 && c != '\n'; ) {
                if (c != '\r') line.write(c);
            }
            return line.toString("UTF-8");
        }
    }
}
