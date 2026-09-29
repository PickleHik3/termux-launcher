package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** {@code OSC 5522}, kitty's extended clipboard protocol, as far as text goes. */
public class KittyClipboardTest extends TerminalTestCase {

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

	/** Types that are not text are not pretended. */
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

	/** Text among other types is taken; the rest is dropped. */
	public void testTextAmongOtherTypes() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("image/png"), b64("pngbytes"));
		send("type=wdata:mime=" + TEXT, b64("words"));
		send("type=wdata", "");
		assertEquals(1, mOutput.clipboardPuts.size());
		assertEquals("words", mOutput.clipboardPuts.get(0));
	}

	/** A write of only non-text types is ENOSYS and puts nothing on the clipboard. */
	public void testNonTextWriteIsEnosys() {
		withTerminalSized(20, 5);
		send("type=write", "");
		send("type=wdata:mime=" + b64("image/png"), b64("pngbytes"));
		send("type=wdata", "");
		assertTrue(mOutput.clipboardPuts.isEmpty());
		assertEquals("\033]5522;type=write:status=ENOSYS\033\\", mOutput.getOutputAndClear());
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
