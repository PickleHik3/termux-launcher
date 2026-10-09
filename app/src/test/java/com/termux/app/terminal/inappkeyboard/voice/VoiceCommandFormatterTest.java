package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Dictated commands written as commands (device check B), and prose left alone. */
public class VoiceCommandFormatterTest {

    /** {input as the recognizer wrote it, the command}. */
    private static final String[][] COMMANDS = {
        // The two device-check failures.
        {"ls dash la", "ls -la"},
        {"Ls dash La", "ls -la"},
        {"L S dash La", "ls -la"},
        {"LS dash La.", "ls -la"},
        {"l. s. dash la", "ls -la"},
        {"Ls, dash, la.", "ls -la"},
        // Plain commands: no capital, no full stop.
        {"git status", "git status"},
        {"Git status.", "git status"},
        {"Sudo apt update.", "sudo apt update"},
        {"Um, git status.", "git status"},
        {"git uh status", "git status"},
        {"C D slash home", "cd /home"},
        {"P W D", "pwd"},
        {"G I T log", "git log"},
        // Slashes, dots and the rest.
        {"cd slash home slash user", "cd /home/user"},
        {"Cat file dot txt", "cat file.txt"},
        {"cat readme dot md", "cat readme.md"},
        {"cd dot dot", "cd .."},
        {"cd dot dot slash src", "cd ../src"},
        {"git add dot", "git add ."},
        {"git add dot.", "git add ."},
        {"vim dot bashrc", "vim .bashrc"},
        {"cd tilde slash projects", "cd ~/projects"},
        {"cd tilde", "cd ~"},
        {"cd slash", "cd /"},
        {"cd dash", "cd -"},
        {"git checkout feature slash login", "git checkout feature/login"},
        {"ps aux pipe grep node", "ps aux | grep node"},
        {"rm star dot log", "rm *.log"},
        {"rm asterisk dot log", "rm *.log"},
        {"ls src slash star", "ls src/*"},
        {"touch my underscore notes dot txt", "touch my_notes.txt"},
        {"dot slash gradlew build", "./gradlew build"},
        {"./gradlew assembleDebug.", "./gradlew assembleDebug"},
        // Flags: lower-cased and glued; spelled letters join; a long flag keeps its hyphen.
        {"ls dash dash Help", "ls --help"},
        {"rm dash r f build", "rm -rf build"},
        {"ls dash L A", "ls -la"},
        {"git commit dash a dash m fix", "git commit -a -m fix"},
        {"git log dash dash pretty equals oneline", "git log --pretty=oneline"},
        {"ls dash dash color equals auto", "ls --color=auto"},
        {"npm install dash dash save dash dev", "npm install --save-dev"},
        {"find dot dash name star dot txt", "find . -name *.txt"},
        {"ls dash la slash tmp", "ls -la /tmp"},
        // Everyday words that are also commands: short, or with a symbol, they are commands.
        {"Clear.", "clear"},
        {"Exit.", "exit"},
        {"make build", "make build"},
        {"Make dash j four", "make -j four"},
        {"python script dot py", "python script.py"},
        {"Claude dash dash help", "claude --help"},
    };

    /** Text that does not start with a command, or an everyday word opening a sentence: unchanged. */
    private static final String[] PROSE = {
        "Please run the tests.",
        "I think so.",
        "So the PC is busy, never run Gradle.",
        "A B testing is fun.",
        "Make sure the tests pass before you push.",
        "Find the file I sent you yesterday.",
        "Claude, can you fix the failing test?",
        "Which one of these is faster?",
        "The ls command lists files.",
        "gitlab is down",
        "",
        "um",
    };

    @Test
    public void spokenCommandsAreWrittenAsCommands() {
        for (String[] row : COMMANDS) {
            assertEquals("\"" + row[0] + "\"", row[1], VoiceCommandFormatter.format(row[0]));
            assertTrue(row[0], VoiceCommandFormatter.isCommand(row[0]));
        }
    }

    @Test
    public void proseIsLeftAlone() {
        for (String text : PROSE) {
            assertNull("\"" + text + "\"", VoiceCommandFormatter.format(text));
            assertFalse(text, VoiceCommandFormatter.isCommand(text));
        }
    }

    @Test
    public void formattingItsOwnOutputChangesNothing() {
        for (String[] row : COMMANDS) {
            assertEquals("\"" + row[1] + "\"", row[1], VoiceCommandFormatter.format(row[1]));
        }
    }

    @Test
    public void aResumedDictationFormatsOnlyWhatIsNew() {
        // The text waiting ("ls -la") carries on, and the next pause formats the whole of it.
        assertEquals("ls -la -h", VoiceCommandFormatter.format("ls -la dash h"));
        assertEquals("cd /home/user/src", VoiceCommandFormatter.format("cd /home/user slash src"));
    }

    @Test
    public void wordsThatAreNotSymbolsKeepTheirCase() {
        assertEquals("git checkout Main", VoiceCommandFormatter.format("git checkout Main."));
        assertEquals("cat README.md", VoiceCommandFormatter.format("cat README dot md"));
    }
}
