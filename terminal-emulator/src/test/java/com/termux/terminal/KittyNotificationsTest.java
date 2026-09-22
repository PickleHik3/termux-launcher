package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** {@code OSC 99}, the desktop notification, and {@code OSC 22}, the mouse pointer shape. */
public class KittyNotificationsTest extends TerminalTestCase {

	private static String b64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	/** The plainest form: no metadata at all is a title, shown at once. */
	public void testBareTitleIsShownImmediately() {
		withTerminalSized(20, 5);
		enterString("\033]99;;Build finished\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals("Build finished", notification.getTitle());
		assertEquals("", notification.getBody());
		assertEquals("", notification.getId());
		assertEquals(KittyNotification.URGENCY_UNSET, notification.getUrgency());
		assertTrue(notification.isFocusOnActivate());
		assertFalse(notification.isReportOnActivate());
	}

	/** A title chunk, then a body chunk that says it is done: one notification, not two. */
	public void testChunkedTitleAndBodyAssembleIntoOne() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:d=0:p=title;Build finished\033\\");
		assertTrue(mOutput.kittyNotifications.isEmpty());
		enterString("\033]99;i=1:d=1:p=body;42 tests passed\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals("1", notification.getId());
		assertEquals("Build finished", notification.getTitle());
		assertEquals("42 tests passed", notification.getBody());
	}

	/** Several chunks of the same kind are joined, in order. */
	public void testRepeatedChunksOfOneKindAreConcatenated() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=x:d=0:p=body;one \033\\");
		enterString("\033]99;i=x:d=0:p=body;two \033\\");
		enterString("\033]99;i=x:d=1:p=body;three\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		assertEquals("one two three", mOutput.kittyNotifications.get(0).getBody());
	}

	/** Two half-built notifications with different names do not run into each other. */
	public void testChunksAreKeyedByName() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=a:d=0:p=title;Alpha\033\\");
		enterString("\033]99;i=b:d=0:p=title;Beta\033\\");
		enterString("\033]99;i=b:d=1:p=body;second\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		assertEquals("Beta", mOutput.kittyNotifications.get(0).getTitle());
		enterString("\033]99;i=a:d=1:p=body;first\033\\");
		assertEquals(2, mOutput.kittyNotifications.size());
		assertEquals("Alpha", mOutput.kittyNotifications.get(1).getTitle());
		assertEquals("first", mOutput.kittyNotifications.get(1).getBody());
	}

	/** With e=1 the payload is base64, which is how anything non-ASCII travels. */
	public void testBase64Payload() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:e=1:d=0:p=title;" + b64("Wär's fertig") + "\033\\");
		enterString("\033]99;i=1:e=1:d=1:p=body;" + b64("日本語もどうぞ") + "\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		assertEquals("Wär's fertig", mOutput.kittyNotifications.get(0).getTitle());
		assertEquals("日本語もどうぞ", mOutput.kittyNotifications.get(0).getBody());
	}

	/** A semicolon in the text is text: only the first one separates metadata from payload. */
	public void testSemicolonInPayloadStaysInThePayload() {
		withTerminalSized(20, 5);
		enterString("\033]99;;a; b; c\033\\");
		assertEquals("a; b; c", mOutput.kittyNotifications.get(0).getTitle());
	}

	/** Urgency, occasion, timeout, application name and actions all come off the metadata. */
	public void testMetadataIsRead() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=7:u=2:o=unfocused:w=5000:c=1:a=report,-focus:f=" + b64("ninja")
			+ ":t=" + b64("build") + ":s=" + b64("bell") + ";Broke\033\\");
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals(KittyNotification.URGENCY_CRITICAL, notification.getUrgency());
		assertEquals(KittyNotification.OCCASION_UNFOCUSED, notification.getOccasion());
		assertEquals(5000, notification.getTimeoutMillis());
		assertTrue(notification.isReportOnClose());
		assertTrue(notification.isReportOnActivate());
		assertFalse(notification.isFocusOnActivate());
		assertEquals("ninja", notification.getApplicationName());
		assertEquals(1, notification.getTypes().size());
		assertEquals("build", notification.getTypes().get(0));
		assertEquals("bell", notification.getSound());
	}

	/** Metadata riding on a later chunk still counts. */
	public void testMetadataOnALaterChunkCounts() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:d=0:p=title;Title\033\\");
		enterString("\033]99;i=1:d=1:u=0;\033\\");
		assertEquals(KittyNotification.URGENCY_LOW, mOutput.kittyNotifications.get(0).getUrgency());
	}

	/** p=close takes a notification away by name. */
	public void testCloseByName() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=job;Running\033\\");
		enterString("\033]99;i=job:p=close;\033\\");
		assertEquals(1, mOutput.kittyNotificationCloses.size());
		assertEquals("job", mOutput.kittyNotificationCloses.get(0));
	}

	/** A close with no name has nothing to act on, so it does nothing. */
	public void testCloseWithoutANameDoesNothing() {
		withTerminalSized(20, 5);
		enterString("\033]99;p=close;\033\\");
		assertTrue(mOutput.kittyNotificationCloses.isEmpty());
		assertTrue(mOutput.kittyNotifications.isEmpty());
	}

	/** The capability query is answered with what this terminal really honours. */
	public void testCapabilityQueryIsAnswered() {
		withTerminalSized(20, 5);
		mOutput.getOutputAndClear();
		enterString("\033]99;i=q1:p=?;\033\\");
		String answer = mOutput.getOutputAndClear();
		assertTrue(answer, answer.startsWith("\033]99;i=q1:p=?;"));
		assertTrue(answer, answer.endsWith("\033\\"));
		assertTrue(answer, answer.contains("p=title,body,close,?,alive"));
		assertTrue(answer, answer.contains("a=focus,report"));
		assertTrue(answer, answer.contains("u=0,1,2"));
		// Nothing is shown for a query.
		assertTrue(mOutput.kittyNotifications.isEmpty());
	}

	/** An alive query lists the notifications still up, and closing one takes it off the list. */
	public void testAliveQueryListsLiveNotifications() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=one;First\033\\");
		enterString("\033]99;i=two;Second\033\\");
		mOutput.getOutputAndClear();
		enterString("\033]99;i=q:p=alive;\033\\");
		assertEquals("\033]99;i=q:p=alive;one,two\033\\", mOutput.getOutputAndClear());
		enterString("\033]99;i=one:p=close;\033\\");
		enterString("\033]99;i=q:p=alive;\033\\");
		assertEquals("\033]99;i=q:p=alive;two\033\\", mOutput.getOutputAndClear());
	}

	/** With a=report, a tap is reported back; without it, nothing is sent. */
	public void testActivationIsReportedOnlyWhenAsked() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=silent;No report wanted\033\\");
		mOutput.getOutputAndClear();
		mTerminal.kittyNotificationActivated("silent", 0);
		assertEquals("", mOutput.getOutputAndClear());

		enterString("\033]99;i=loud:a=report;Tell me\033\\");
		mOutput.getOutputAndClear();
		mTerminal.kittyNotificationActivated("loud", 0);
		assertEquals("\033]99;i=loud;\033\\", mOutput.getOutputAndClear());

		// A tap takes it away, so a second one has nothing to report.
		mTerminal.kittyNotificationActivated("loud", 0);
		assertEquals("", mOutput.getOutputAndClear());
	}

	/** A button tap carries the button's number. */
	public void testButtonActivationCarriesItsNumber() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=b:a=report;Choose\033\\");
		mOutput.getOutputAndClear();
		mTerminal.kittyNotificationActivated("b", 2);
		assertEquals("\033]99;i=b;2\033\\", mOutput.getOutputAndClear());
	}

	/** With c=1, a notification going away on its own is reported too. */
	public void testCloseIsReportedWhenAsked() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=z:c=1;Watch me\033\\");
		mOutput.getOutputAndClear();
		mTerminal.kittyNotificationClosed("z");
		assertEquals("\033]99;i=z:p=close;\033\\", mOutput.getOutputAndClear());

		enterString("\033]99;i=y;Quiet\033\\");
		mOutput.getOutputAndClear();
		mTerminal.kittyNotificationClosed("y");
		assertEquals("", mOutput.getOutputAndClear());
	}

	/** Button labels arrive separated by the line-separator character. */
	public void testButtonsArePickedApart() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:d=0:p=title;Deploy?\033\\");
		enterString("\033]99;i=1:d=1:p=buttons;Yes No\033\\");
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals(2, notification.getButtons().size());
		assertEquals("Yes", notification.getButtons().get(0));
		assertEquals("No", notification.getButtons().get(1));
	}

	/** An empty request is not worth waking the user for. */
	public void testEmptyRequestIsDropped() {
		withTerminalSized(20, 5);
		enterString("\033]99;;\033\\");
		assertTrue(mOutput.kittyNotifications.isEmpty());
	}

	/** A hard reset forgets the half-built ones, so the next request starts clean. */
	public void testResetForgetsPendingRequests() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:d=0:p=title;Half\033\\");
		enterString("\033c");
		enterString("\033]99;i=1:d=1:p=body;done\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		assertEquals("", mOutput.kittyNotifications.get(0).getTitle());
		assertEquals("done", mOutput.kittyNotifications.get(0).getBody());
	}

	/** OSC 22 sets the pointer shape, and an empty one asks for the terminal's own back. */
	public void testPointerShapeIsSetAndReset() {
		withTerminalSized(20, 5);
		assertNull(mTerminal.getPointerShape());
		enterString("\033]22;pointer\033\\");
		assertEquals("pointer", mTerminal.getPointerShape());
		assertEquals(1, mOutput.pointerShapes.size());
		assertEquals("pointer", mOutput.pointerShapes.get(0));

		// The same shape again is not a change.
		enterString("\033]22;pointer\033\\");
		assertEquals(1, mOutput.pointerShapes.size());

		enterString("\033]22;\033\\");
		assertNull(mTerminal.getPointerShape());
		assertEquals(2, mOutput.pointerShapes.size());
		assertNull(mOutput.pointerShapes.get(1));
	}

	/** Names are matched without regard to case, and a nonsense name asks for the default. */
	public void testPointerShapeNamesAreNormalized() {
		withTerminalSized(20, 5);
		enterString("\033]22;NOT-ALLOWED\033\\");
		assertEquals("not-allowed", mTerminal.getPointerShape());
		enterString("\033]22;../etc/passwd\033\\");
		assertNull(mTerminal.getPointerShape());
	}

	/** A program can set the shape aside and put it back. */
	public void testPointerShapePushAndPop() {
		withTerminalSized(20, 5);
		enterString("\033]22;text\033\\");
		enterString("\033]22;>wait\033\\");
		assertEquals("wait", mTerminal.getPointerShape());
		enterString("\033]22;<\033\\");
		assertEquals("text", mTerminal.getPointerShape());

		// Popping past the bottom leaves the shape alone rather than throwing it away.
		enterString("\033]22;<\033\\");
		assertEquals("text", mTerminal.getPointerShape());
	}

	/** A hard reset puts the pointer back to the terminal's own. */
	public void testResetClearsPointerShape() {
		withTerminalSized(20, 5);
		enterString("\033]22;wait\033\\");
		assertEquals("wait", mTerminal.getPointerShape());
		enterString("\033c");
		assertNull(mTerminal.getPointerShape());
	}

	/**
	 * An escape a program never closed keeps swallowing, so the terminal's own bytes — here a
	 * shell-integration marker printed straight afterwards — end up inside the title. None of it
	 * may reach the notification.
	 */
	public void testControlCharactersNeverReachTheNotification() {
		withTerminalSized(20, 5);
		// Base64 so the embedded escape arrives as payload rather than ending the sequence.
		enterString("\033]99;i=1:e=1:d=1:p=title;" + b64("Build finished\033]133;D;0") + "\033\\");
		assertEquals(1, mOutput.kittyNotifications.size());
		String title = mOutput.kittyNotifications.get(0).getTitle();
		assertEquals("Build finished]133;D;0", title);
		for (int i = 0; i < title.length(); i++) {
			char c = title.charAt(i);
			assertFalse("control character at " + i, c < ' ' || c == '\u007F');
		}
	}

	/** A body may be several lines; a title is one. */
	public void testBodyKeepsItsLineBreaksAndTheTitleDoesNot() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:e=1:d=0:p=title;" + b64("Build\nfinished") + "\033\\");
		enterString("\033]99;i=1:e=1:d=1:p=body;"
			+ b64("42 tests passed\nall green\007\u0001") + "\033\\");
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals("Build finished", notification.getTitle());
		assertEquals("42 tests passed\nall green", notification.getBody());
	}

	/** Runs of spaces, tabs and line breaks come out as one gap, and the ends are trimmed. */
	public void testWhitespaceIsCollapsedAndTrimmed() {
		assertEquals("a b", KittyNotifications.clean("   a \t  b   ", false));
		assertEquals("a b", KittyNotifications.clean("a\n\n\n\tb", false));
		assertEquals("a\nb", KittyNotifications.clean("\n\n a \n \n b \n\n", true));
		assertEquals("a\tb", KittyNotifications.clean("a \t b", true));
		assertEquals("", KittyNotifications.clean("\u0001\033\u007F", false));
		assertEquals("", KittyNotifications.clean(null, true));
	}

	/** A request with nothing left to say after cleaning is not worth waking the user for. */
	public void testARequestOfNothingButControlBytesIsDropped() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:e=1:d=0:p=title;" + b64("\033\007") + "\033\\");
		enterString("\033]99;i=1:e=1:d=1:p=body;" + b64("\u0001\u007F") + "\033\\");
		assertTrue(mOutput.kittyNotifications.isEmpty());
	}

	/** Button labels are read by the user too, so they are cleaned the same way. */
	public void testButtonLabelsAreCleaned() {
		withTerminalSized(20, 5);
		enterString("\033]99;i=1:d=0:p=title;Deploy?\033\\");
		enterString("\033]99;i=1:e=1:d=1:p=buttons;" + b64("Yes\007  No  now ") + "\033\\");
		KittyNotification notification = mOutput.kittyNotifications.get(0);
		assertEquals(2, notification.getButtons().size());
		assertEquals("Yes", notification.getButtons().get(0));
		assertEquals("No now", notification.getButtons().get(1));
	}

	/** OSC 99 does not disturb the older, plainer notification escapes. */
	public void testOlderNotificationEscapesStillWork() {
		withTerminalSized(20, 5);
		enterString("\033]777;notify;Title;Body\033\\");
		assertEquals(1, mOutput.notifications.size());
		assertEquals("Title", mOutput.notifications.get(0)[0]);
		assertTrue(mOutput.kittyNotifications.isEmpty());
	}
}
