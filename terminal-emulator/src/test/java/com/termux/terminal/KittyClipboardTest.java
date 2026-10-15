package com.termux.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code OSC 5522}, kitty's extended clipboard protocol: text, pictures and paste events. */
public class KittyClipboardTest extends TerminalTestCase {

	/** A clipboard holding one picture, which answers its data or fails as told. */
	private static final class ImageOffer implements ClipboardOffer {
		final String text;
		final List<String> types;
		final byte[] data;
		final IOException failure;
		final List<String> asked = new ArrayList<>();

		ImageOffer(String text, List<String> types, byte[] data, IOException failure) {
			this.text = text;
			this.types = types;
			this.data = data;
			this.failure = failure;
		}

		@Override public String text() {
			return text;
		}

		@Override public List<String> types() {
			return types;
		}

		@Override public byte[] data(String mime) throws IOException {
			asked.add(mime);
			if (failure != null) throw failure;
			return data;
		}
	}

	private static byte[] bytes(int count) {
		byte[] out = new byte[count];
		for (int i = 0; i < count; i++) out[i] = (byte) (i * 31);
		return out;
	}

	/** The DATA payloads of {@code out} for {@code mime}, decoded and joined. */
	private static byte[] dataOf(String out, String mime) {
		java.io.ByteArrayOutputStream joined = new java.io.ByteArrayOutputStream();
		for (String packet : out.split("\033\\\\")) {
			if (!packet.contains("status=DATA:mime=" + b64(mime) + ";")) continue;
			byte[] chunk = Base64.getDecoder().decode(packet.substring(packet.indexOf(';', packet.indexOf("status=DATA")) + 1));
			joined.write(chunk, 0, chunk.length);
		}
		return joined.toByteArray();
	}

	private static String b64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	private static final String TEXT = b64("text/plain");

	private void send(String metadata, String payload) {
		enterString("\033]5522;" + metadata + ";" + payload + "\033\\");
	}

	/** {@code type=read} of text/plain: OK, the data, DONE. */
	public void testReadTextAnswersOkDataDone() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "Hello, world";
		send("type=read", b64("text/plain"));
		assertEquals("\033]5522;type=read:status=OK\033\\"
			+ "\033]5522;type=read:status=DATA:mime=" + TEXT + ";" + b64("Hello, world") + "\033\\"
			+ "\033]5522;type=read:status=DONE\033\\", mOutput.getOutputAndClear());
	}

	/** The charset alias is answered under the name it was asked with. */
	public void testReadCharsetAlias() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "hi";
		send("type=read", b64("text/plain;charset=utf-8"));
		String out = mOutput.getOutputAndClear();
		assertTrue(out, out.contains("mime=" + b64("text/plain;charset=utf-8") + ";" + b64("hi")));
	}

	/** The id is echoed on every answer, and stripped of characters the spec does not allow. */
	public void testReadEchoesSanitizedId() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		send("type=read:id=a-1_b!<>", b64("text/plain"));
		String out = mOutput.getOutputAndClear();
		assertTrue(out, out.startsWith("\033]5522;type=read:status=OK:id=a-1_b\033\\"));
		assertTrue(out, out.endsWith("type=read:status=DONE:id=a-1_b\033\\"));
	}

	/** A long clipboard is sent in several DATA packets that join back into the text. */
	public void testReadIsChunked() {
		withTerminalSized(20, 5);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 4000; i++) text.append((char) ('a' + i % 26));
		mOutput.clipboardContents = text.toString();
		send("type=read", b64("text/plain"));
		String out = mOutput.getOutputAndClear();
		StringBuilder joined = new StringBuilder();
		int packets = 0;
		for (String packet : out.split("\033\\\\")) {
			int semi = packet.indexOf(';', packet.indexOf("status=DATA"));
			if (!packet.contains("status=DATA")) continue;
			packets++;
			joined.append(new String(Base64.getDecoder().decode(packet.substring(semi + 1)), StandardCharsets.UTF_8));
		}
		assertEquals(2, packets);
		assertEquals(text.toString(), joined.toString());
	}

	/** {@code .} lists the types on offer. */
	public void testReadListsMimeTypes() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		send("type=read", b64("."));
		String out = mOutput.getOutputAndClear();
		assertTrue(out, out.startsWith("\033]5522;type=read:status=OK\033\\"));
		assertTrue(out, out.contains(b64("text/plain text/plain;charset=utf-8")));
		assertTrue(out, out.endsWith("type=read:status=DONE\033\\"));
	}

	/** An empty clipboard lists nothing, but the read still completes. */
	public void testReadListEmptyClipboard() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "";
		send("type=read", b64("."));
		assertEquals("\033]5522;type=read:status=OK\033\\\033]5522;type=read:status=DONE\033\\",
			mOutput.getOutputAndClear());
	}

	/** A refused read (the client answers null) is EPERM, never silence. */
	public void testReadRefusedIsEperm() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = null;
		send("type=read", b64("text/plain"));
		assertEquals("\033]5522;type=read:status=EPERM\033\\", mOutput.getOutputAndClear());
	}

	/** A type the clipboard does not hold is not pretended. */
	public void testReadNonTextIsEnosys() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		send("type=read", b64("image/png"));
		assertEquals("\033]5522;type=read:status=ENOSYS\033\\", mOutput.getOutputAndClear());
	}

	/** Android has no primary selection. */
	public void testPrimarySelectionIsEnosys() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		send("type=read:loc=primary", b64("text/plain"));
		assertEquals("\033]5522;type=read:status=ENOSYS\033\\", mOutput.getOutputAndClear());
	}

	/** Garbage base64 in a read is ignored, per the spec. */
	public void testInvalidReadIsIgnored() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		send("type=read", "!!!not base64");
		assertEquals("", mOutput.getOutputAndClear());
	}

	/** write, wdata chunks, closing wdata: one clipboard put and a DONE. */
	public void testChunkedWrite() {
		withTerminalSized(20, 5);
		String all = b64("Hello, world!");
		send("type=write", "");
		send("type=wdata:mime=" + TEXT, all.substring(0, 8));
		send("type=wdata:mime=" + TEXT, all.substring(8));
		assertTrue(mOutput.clipboardPuts.isEmpty());
		send("type=wdata", "");
		assertEquals(1, mOutput.clipboardPuts.size());
		assertEquals("Hello, world!", mOutput.clipboardPuts.get(0));
		assertEquals("\033]5522;type=write:status=DONE\033\\", mOutput.getOutputAndClear());
	}

	/** A chunk boundary need not fall on a base64 group. */
	public void testChunkSplitMidGroup() {
		withTerminalSized(20, 5);
		String all = b64("abcdefg");
		send("type=write", "");
		send("type=wdata:mime=" + TEXT, all.substring(0, 5));
		send("type=wdata:mime=" + TEXT, all.substring(5));
		send("type=wdata", "");
		assertEquals("abcdefg", mOutput.clipboardPuts.get(0));
	}

	/** Non-ASCII text survives the trip. */
	public void testWriteUtf8() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("text/plain;charset=utf-8"), b64("héllo ☃"));
		send("type=wdata", "");
		assertEquals("héllo ☃", mOutput.clipboardPuts.get(0));
	}

	/** The write's id comes back on DONE. */
	public void testWriteEchoesId() {
		withTerminalSized(20, 5);
		send("type=write:id=w1", "");
		send("type=wdata:id=w1:mime=" + TEXT, b64("x"));
		send("type=wdata:id=w1", "");
		assertEquals("\033]5522;type=write:status=DONE:id=w1\033\\", mOutput.getOutputAndClear());
	}

	/** An alias makes another target's data text. */
	public void testAliasedTextIsWritten() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=walias:mime=" + b64("application/x-custom"), b64("text/plain"));
		send("type=wdata:mime=" + b64("application/x-custom"), b64("aliased"));
		send("type=wdata", "");
		assertEquals("aliased", mOutput.clipboardPuts.get(0));
	}

	/** Text among types the clipboard cannot hold is taken; the rest is dropped. */
	public void testTextAmongOtherTypes() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("application/x-thing"), b64("blob"));
		send("type=wdata:mime=" + TEXT, b64("words"));
		send("type=wdata", "");
		assertEquals(1, mOutput.clipboardPuts.size());
		assertEquals("words", mOutput.clipboardPuts.get(0));
		assertTrue(mOutput.clipboardImagePuts.isEmpty());
	}

	/** A write of only types the clipboard cannot hold is ENOSYS and puts nothing on it. */
	public void testNonTextWriteIsEnosys() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("application/octet-stream"), b64("bytes"));
		send("type=wdata", "");
		assertTrue(mOutput.clipboardPuts.isEmpty());
		assertTrue(mOutput.clipboardImagePuts.isEmpty());
		assertEquals("\033]5522;type=write:status=ENOSYS\033\\", mOutput.getOutputAndClear());
	}

	/** A picture write reaches the clipboard whole, under its type, and is answered DONE. */
	public void testImageWrite() {
		withTerminalSized(20, 5);
		byte[] png = bytes(5000);
		String all = Base64.getEncoder().encodeToString(png);
		send("type=write", "");
		send("type=wdata:mime=" + b64("image/png"), all.substring(0, 4097));
		send("type=wdata:mime=" + b64("image/png"), all.substring(4097));
		send("type=wdata", "");
		assertEquals("\033]5522;type=write:status=DONE\033\\", mOutput.getOutputAndClear());
		assertEquals(1, mOutput.clipboardImagePuts.size());
		Map<String, byte[]> images = mOutput.clipboardImagePuts.get(0);
		assertEquals(Collections.singleton("image/png"), images.keySet());
		assertTrue(Arrays.equals(png, images.get("image/png")));
		assertTrue(mOutput.clipboardPuts.isEmpty());
	}

	/** A picture written with text carries both: the picture to the clipboard, the text beside it. */
	public void testImageWriteWithText() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("image/jpeg"), b64("jpegbytes"));
		send("type=wdata:mime=" + TEXT, b64("caption"));
		send("type=wdata", "");
		assertEquals(1, mOutput.clipboardImagePuts.size());
		assertEquals("jpegbytes", new String(mOutput.clipboardImagePuts.get(0).get("image/jpeg"), StandardCharsets.UTF_8));
		assertEquals(Collections.singletonList("caption"), mOutput.clipboardPuts);
	}

	/** Data under another name aliased to a picture type is kept as that picture. */
	public void testImageAlias() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=walias:mime=" + b64("application/x-shot"), b64("image/png"));
		send("type=wdata:mime=" + b64("application/x-shot"), b64("png"));
		send("type=wdata", "");
		assertEquals("png", new String(mOutput.clipboardImagePuts.get(0).get("image/png"), StandardCharsets.UTF_8));
	}

	/** A picture on the clipboard is read in chunks under the type asked for. */
	public void testImageRead() {
		withTerminalSized(20, 5);
		byte[] png = bytes(5000);
		ImageOffer offer = new ImageOffer("", Collections.singletonList("image/png"), png, null);
		mOutput.clipboardOffer = offer;
		send("type=read", b64("image/png"));
		String out = mOutput.getOutputAndClear();
		assertTrue(out, out.startsWith("\033]5522;type=read:status=OK\033\\"));
		assertTrue(out, out.endsWith("\033]5522;type=read:status=DONE\033\\"));
		assertTrue(Arrays.equals(png, dataOf(out, "image/png")));
		assertEquals(Collections.singletonList("image/png"), offer.asked);
	}

	/** Listing names the text types only when there is text, then every picture type. */
	public void testListingOffersPictureTypes() {
		withTerminalSized(20, 5);
		mOutput.clipboardOffer = new ImageOffer("", Arrays.asList("image/jpeg", "image/png"), new byte[0], null);
		send("type=read", b64("."));
		assertEquals("image/jpeg image/png", new String(dataOf(mOutput.getOutputAndClear(), "."), StandardCharsets.UTF_8));

		mOutput.clipboardOffer = new ImageOffer("alt", Collections.singletonList("image/png"), new byte[0], null);
		send("type=read", b64("."));
		assertEquals("text/plain text/plain;charset=utf-8 image/png",
			new String(dataOf(mOutput.getOutputAndClear(), "."), StandardCharsets.UTF_8));
	}

	/** A picture over the read limit is EFBIG, one that cannot be read EIO: never half an answer. */
	public void testImageReadFailures() {
		withTerminalSized(20, 5);
		mOutput.clipboardOffer = new ImageOffer("", Collections.singletonList("image/png"), null,
			new ClipboardOffer.TooLargeException("big"));
		send("type=read", b64("image/png"));
		assertEquals("\033]5522;type=read:status=EFBIG\033\\", mOutput.getOutputAndClear());

		mOutput.clipboardOffer = new ImageOffer("", Collections.singletonList("image/png"), null,
			new IOException("gone"));
		send("type=read", b64("image/png"));
		assertEquals("\033]5522;type=read:status=EIO\033\\", mOutput.getOutputAndClear());
	}

	/** A picture clipboard asked for a picture type it does not hold is ENOSYS. */
	public void testImageReadOfOtherTypeIsEnosys() {
		withTerminalSized(20, 5);
		mOutput.clipboardOffer = new ImageOffer("", Collections.singletonList("image/png"), bytes(4), null);
		send("type=read", b64("image/gif"));
		assertEquals("\033]5522;type=read:status=ENOSYS\033\\", mOutput.getOutputAndClear());
	}

	/** The answer may come later, from wherever the clipboard was read; it still carries the id. */
	public void testDeferredAnswer() {
		final KittyClipboard.ReadCallback[] pending = new KittyClipboard.ReadCallback[1];
		mOutput = new MockTerminalOutput() {
			@Override public void onReadClipboard(boolean pasted, KittyClipboard.ReadCallback callback) {
				pending[0] = callback;
			}
		};
		withTerminalSized(20, 5);
		send("type=read:id=r1", b64("text/plain"));
		assertEquals("", mOutput.getOutputAndClear());
		pending[0].onOffer(ClipboardOffer.ofText("later"));
		String out = mOutput.getOutputAndClear();
		assertTrue(out, out.startsWith("\033]5522;type=read:status=OK:id=r1\033\\"));
		assertTrue(out, out.contains("mime=" + TEXT + ":id=r1;" + b64("later")));
	}

	// --- Paste events ---

	/** DECRQM knows mode 5522, and DECSET, save and restore move it. */
	public void testPasteEventsMode() {
		withTerminalSized(20, 5);
		assertEnteringStringGivesResponse("\033[?5522$p", "\033[?5522;2$y");
		assertFalse(mTerminal.isPasteEventsMode());
		enterString("\033[?5522h");
		assertTrue(mTerminal.isPasteEventsMode());
		assertEnteringStringGivesResponse("\033[?5522$p", "\033[?5522;1$y");
		enterString("\033[?5522s\033[?5522l");
		assertFalse(mTerminal.isPasteEventsMode());
		enterString("\033[?5522r");
		assertTrue(mTerminal.isPasteEventsMode());
		enterString("\033c");
		assertFalse(mTerminal.isPasteEventsMode());
	}

	/** The paste event's password, from its OK packet. */
	private String sendPasteEventAndTakePassword(List<String> types) {
		mTerminal.sendPasteEvent(types);
		String out = mOutput.getOutputAndClear();
		String[] packets = out.split("\033\\\\");
		assertEquals(out, 3, packets.length);
		assertTrue(out, packets[0].startsWith("\033]5522;type=read:status=OK:pw="));
		String pw = packets[0].substring(packets[0].indexOf("pw=") + 3);
		assertEquals("\033]5522;type=read:status=DATA:mime=Lg==:pw=" + pw + ";" + b64(String.join(" ", types)), packets[1]);
		assertEquals("\033]5522;type=read:status=DONE:pw=" + pw, packets[2]);
		return pw;
	}

	/** A paste event offers the types under a fresh password; reading with it and a name is the user's paste, once. */
	public void testPasteEventPasswordIsSingleUse() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "pasted";
		String pw = sendPasteEventAndTakePassword(Arrays.asList("text/plain", "image/png"));
		String name = b64("Paste event");

		send("type=read:pw=" + pw, b64("text/plain")); // No name: an ordinary read, password kept.
		send("type=read:pw=" + pw + ":name=" + name, b64("text/plain"));
		send("type=read:pw=" + pw + ":name=" + name, b64("text/plain")); // Used up.
		assertEquals(Arrays.asList(false, true, false), mOutput.clipboardReadsPasted);
	}

	/** A wrong password is an ordinary read, and a newer paste replaces the older password. */
	public void testPasteEventWrongOrStalePassword() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = "x";
		String name = b64("Paste event");
		String first = sendPasteEventAndTakePassword(Collections.singletonList("text/plain"));
		send("type=read:pw=" + b64("guess") + ":name=" + name, b64("text/plain"));
		send("type=read:pw=!!!:name=" + name, b64("text/plain"));
		mOutput.getOutputAndClear();
		String second = sendPasteEventAndTakePassword(Collections.singletonList("text/plain"));
		assertFalse(first.equals(second));
		send("type=read:pw=" + first + ":name=" + name, b64("text/plain"));
		send("type=read:pw=" + second + ":name=" + name, b64("text/plain"));
		assertEquals(Arrays.asList(false, false, false, true), mOutput.clipboardReadsPasted);
	}

	/** Bad base64 fails the write with EINVAL, and later packets are ignored until the next write. */
	public void testInvalidBase64FailsWrite() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + TEXT, "@@@@");
		assertEquals("\033]5522;type=write:status=EINVAL\033\\", mOutput.getOutputAndClear());
		send("type=wdata:mime=" + TEXT, b64("late"));
		send("type=wdata", "");
		assertEquals("", mOutput.getOutputAndClear());
		assertTrue(mOutput.clipboardPuts.isEmpty());
		send("type=write", "");
		send("type=wdata:mime=" + TEXT, b64("ok"));
		send("type=wdata", "");
		assertEquals("ok", mOutput.clipboardPuts.get(0));
	}

	/** A write that ends with a group cut short is malformed. */
	public void testTruncatedBase64IsEinval() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + TEXT, "aGVsb");
		send("type=wdata", "");
		assertEquals("\033]5522;type=write:status=EINVAL\033\\", mOutput.getOutputAndClear());
		assertTrue(mOutput.clipboardPuts.isEmpty());
	}

	/** A write to the primary selection is ENOSYS. */
	public void testPrimaryWriteIsEnosys() {
		withTerminalSized(20, 5);
		send("type=write:loc=primary", "");
		assertEquals("\033]5522;type=write:status=ENOSYS\033\\", mOutput.getOutputAndClear());
		send("type=wdata:mime=" + TEXT, b64("x"));
		send("type=wdata", "");
		assertTrue(mOutput.clipboardPuts.isEmpty());
	}

	/** Data with no write open is ignored. */
	public void testWdataWithoutWriteIsIgnored() {
		withTerminalSized(20, 5);
		send("type=wdata:mime=" + TEXT, b64("x"));
		send("type=wdata", "");
		assertTrue(mOutput.clipboardPuts.isEmpty());
		assertEquals("", mOutput.getOutputAndClear());
	}

	/** The BEL terminator is answered with BEL. */
	public void testBelTerminatorIsEchoed() {
		withTerminalSized(20, 5);
		mOutput.clipboardContents = null;
		enterString("\033]5522;type=read;" + b64("text/plain") + "\007");
		assertEquals("\033]5522;type=read:status=EPERM\007", mOutput.getOutputAndClear());
	}
}
