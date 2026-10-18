package com.termux.app.terminal;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.ClipboardOffer;
import com.termux.terminal.KittyClipboard;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSession;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The terminal's clipboard beyond text: what a paste into a pane becomes, and the gates on a
 * program's reads and picture writes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalClipboardTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private FakeTerminalHost host;
    private ShellSignals signals;
    private TerminalClipboard clipboard;
    private File pasted;
    private File copied;
    private Recorder output;
    private TerminalEmulator emulator;

    /** Records what the emulator writes to its program. */
    private static final class Recorder extends TerminalOutput {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override public void write(byte[] data, int offset, int count) {
            bytes.write(data, offset, count);
        }

        String take() {
            String out = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            bytes.reset();
            return out;
        }

        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }

    @Before
    public void setUp() throws IOException {
        Context context = FakeTerminalHost.testContext();
        host = new FakeTerminalHost(context, FakeTerminalHost.testProperties());
        signals = new ShellSignals(context, host, new ShellSignals.Notices() {
            @Override @Nullable public String describe(@NonNull TerminalSession session) {
                return null;
            }

            @Override public void bringToFront(@NonNull TerminalSession session) {}
        });
        pasted = new File(tmp.getRoot(), "tmp/clipboard");
        // Where the provider's paths say copied pictures live, or it will not hand out a link.
        copied = new File(context.getCacheDir(), "clipboard");
        // Both threads are the test's own: the worker's work is done by the time a call returns.
        clipboard = new TerminalClipboard(context, signals, Runnable::run, Runnable::run, pasted, copied);
        output = new Recorder();
        emulator = new TerminalEmulator(output, false, 40, 10, 8, 16, 10, null);
    }

    private static ClipboardManager manager() {
        return (ClipboardManager) FakeTerminalHost.testContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Puts a picture link on the clipboard whose content the resolver serves. */
    private static Uri putPicture(String mime, byte[] content) {
        Uri uri = Uri.parse("content://com.example.pictures/shot");
        shadowOf(FakeTerminalHost.testContext().getContentResolver())
            .registerInputStream(uri, new ByteArrayInputStream(content));
        manager().setPrimaryClip(new ClipData(new ClipDescription("shot", new String[] {mime}), new ClipData.Item(uri)));
        return uri;
    }

    // --- Pasting ---

    @Test
    public void textPastesAsItAlwaysHas() {
        manager().setPrimaryClip(ClipData.newPlainText("", "echo hi"));
        clipboard.paste(emulator);
        assertEquals("echo hi", output.take());
    }

    @Test
    public void aPicturePastesAsThePathOfItsSavedFile() throws IOException {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        putPicture("image/png", png);
        clipboard.paste(emulator);
        String path = output.take();
        File file = new File(path);
        assertEquals(pasted.getAbsolutePath(), file.getParent());
        assertTrue(file.getName(), file.getName().matches("clip-\\d{8}-\\d{6}\\.png"));
        assertArrayEquals(png, Files.readAllBytes(file.toPath()));
        assertFalse("never the content link itself", path.contains("content://"));
    }

    @Test
    public void inPasteEventsModeAPasteIsAnEventNotText() {
        emulator.append("\033[?5522h".getBytes(StandardCharsets.UTF_8), 8);
        manager().setPrimaryClip(ClipData.newPlainText("", "secret"));
        clipboard.paste(emulator);
        String out = output.take();
        assertFalse(out, out.contains("secret"));
        assertTrue(out, out.startsWith("\033]5522;type=read:status=OK:pw="));
        assertTrue(out, out.contains(";" + b64("text/plain text/plain;charset=utf-8") + "\033\\"));

        putPicture("image/jpeg", new byte[] {1});
        clipboard.paste(emulator);
        out = output.take();
        assertTrue(out, out.contains(";" + b64("image/jpeg image/png") + "\033\\"));
        assertFalse("no file is made for an event", pasted.exists());
    }

    @Test
    public void pasteEventTypes() {
        assertEquals(Arrays.asList("text/plain", "text/plain;charset=utf-8"),
            TerminalClipboard.pasteEventTypes(true, null));
        assertEquals(Collections.singletonList("image/png"), TerminalClipboard.pasteEventTypes(false, "image/png"));
        assertEquals(Arrays.asList("text/plain", "text/plain;charset=utf-8", "image/webp", "image/png"),
            TerminalClipboard.pasteEventTypes(true, "image/webp"));
        assertNull(TerminalClipboard.pictureType(Arrays.asList("text/uri-list", "image/*")));
        assertEquals("image/gif", TerminalClipboard.pictureType(Arrays.asList("text/uri-list", "IMAGE/GIF")));
    }

    // --- A program reads ---

    private ClipboardOffer readOffer(boolean pasted) {
        ClipboardOffer[] answer = new ClipboardOffer[1];
        boolean[] called = new boolean[1];
        KittyClipboard.ReadCallback callback = offer -> {
            called[0] = true;
            answer[0] = offer;
        };
        clipboard.read(emulator, pasted, callback);
        assertTrue("answered exactly once", called[0]);
        return answer[0];
    }

    @Test
    public void thePastedReadNeedsOnlyTheLauncherOnScreen() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(FakeTerminalHost.testContext(), false);
        Assume.assumeTrue("no termux preferences in this test environment", preferences != null);
        preferences.setOsc52ClipboardReadEnabled(false);
        manager().setPrimaryClip(ClipData.newPlainText("", "pasted words"));

        assertNull("a program reading on its own is refused", readOffer(false));
        ClipboardOffer offer = readOffer(true);
        assertNotNull("the user's paste is consent", offer);
        assertEquals("pasted words", offer.text());
        assertEquals(ShellSignals.REFUSAL_READ_DISABLED, signals.clipboardReadRefusal(false));
        assertNull(signals.clipboardReadRefusal(true));

        host.visible = false;
        assertNull("never while the launcher is away", readOffer(true));
        assertEquals(ShellSignals.REFUSAL_NOT_VISIBLE, signals.clipboardReadRefusal(true));
    }

    @Test
    public void aPictureIsOfferedInItsOwnTypeAndAsPng() throws IOException {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(FakeTerminalHost.testContext(), false);
        Assume.assumeTrue("no termux preferences in this test environment", preferences != null);
        preferences.setOsc52ClipboardReadEnabled(true);
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 9, 9};
        putPicture("image/jpeg", jpeg);

        ClipboardOffer offer = readOffer(false);
        assertNotNull(offer);
        assertEquals("", offer.text());
        assertEquals(Arrays.asList("image/jpeg", "image/png"), offer.types());
        assertArrayEquals(jpeg, offer.data("image/jpeg"));
    }

    // --- A program writes ---

    @Test
    public void aPictureWriteLandsOnTheClipboardAsALink() throws IOException {
        Map<String, byte[]> images = new LinkedHashMap<>();
        images.put("image/png", new byte[] {7, 7, 7});
        clipboard.write(images, "alt text");

        ClipData clip = manager().getPrimaryClip();
        assertNotNull(clip);
        assertEquals("image/png", clip.getDescription().getMimeType(0));
        Uri uri = clip.getItemAt(0).getUri();
        assertNotNull(uri);
        assertEquals("content", uri.getScheme());
        assertEquals(TerminalClipboard.AUTHORITY, uri.getAuthority());
        assertEquals(2, clip.getItemCount());
        assertEquals("alt text", clip.getItemAt(1).getText().toString());
        String[] saved = copied.list();
        assertNotNull(saved);
        assertEquals(1, saved.length);
        assertArrayEquals(new byte[] {7, 7, 7}, Files.readAllBytes(new File(copied, saved[0]).toPath()));
    }

    @Test
    public void aPictureWriteOffScreenIsDropped() {
        manager().setPrimaryClip(ClipData.newPlainText("", "kept"));
        host.visible = false;
        clipboard.write(Collections.singletonMap("image/png", new byte[] {1}), null);
        assertEquals("kept", manager().getPrimaryClip().getItemAt(0).getText().toString());
        assertFalse(copied.exists());
    }
}
