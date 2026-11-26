package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class DistroGlyphTest {

    @Test
    public void knownNamesMapToTheirLogo() {
        assertEquals(DistroGlyph.DEBIAN, DistroGlyph.forContainer("debian"));
        assertEquals(DistroGlyph.UBUNTU, DistroGlyph.forContainer("ubuntu"));
        assertEquals(DistroGlyph.ALPINE, DistroGlyph.forContainer("alpine"));
        assertEquals(DistroGlyph.FEDORA, DistroGlyph.forContainer("fedora"));
        assertEquals(DistroGlyph.OPENSUSE, DistroGlyph.forContainer("opensuse"));
        assertEquals(DistroGlyph.VOID, DistroGlyph.forContainer("void"));
        assertEquals(DistroGlyph.MANJARO, DistroGlyph.forContainer("manjaro"));
        assertEquals(DistroGlyph.KALI, DistroGlyph.forContainer("kali"));
        assertEquals(DistroGlyph.ROCKY, DistroGlyph.forContainer("rocky"));
        assertEquals(DistroGlyph.ALMALINUX, DistroGlyph.forContainer("almalinux"));
        assertEquals(DistroGlyph.GENTOO, DistroGlyph.forContainer("gentoo"));
        assertEquals(DistroGlyph.NIXOS, DistroGlyph.forContainer("nixos"));
    }

    @Test
    public void aliasesFoldToTheSameLogo() {
        assertEquals(DistroGlyph.ARCH, DistroGlyph.forContainer("archlinux"));
        assertEquals(DistroGlyph.ARCH, DistroGlyph.forContainer("arch"));
        assertEquals(DistroGlyph.ARCH, DistroGlyph.forContainer("menci/archlinuxarm"));
        assertEquals(DistroGlyph.UBUNTU, DistroGlyph.forContainer("ubuntu-lts"));
        assertEquals(DistroGlyph.DEBIAN, DistroGlyph.forContainer("debian-bookworm"));
    }

    @Test
    public void caseAndPaddingAreIgnored() {
        assertEquals(DistroGlyph.DEBIAN, DistroGlyph.forContainer("  Debian "));
        assertEquals(DistroGlyph.ARCH, DistroGlyph.forContainer("ArchLinux"));
    }

    @Test
    public void unknownNamesGetTheGenericLogo() {
        assertEquals(DistroGlyph.GENERIC, DistroGlyph.forContainer("slackware"));
        assertNotEquals(DistroGlyph.DEBIAN, DistroGlyph.forContainer("devuan"));
    }

    @Test
    public void displayNameCapitalisesTheLastSegment() {
        assertEquals("Debian", DistroGlyph.displayName("debian"));
        assertEquals("Archlinuxarm", DistroGlyph.displayName("menci/archlinuxarm"));
        assertEquals("", DistroGlyph.displayName(""));
        assertEquals("", DistroGlyph.displayName(null));
    }

    @Test
    public void thePrefixWearsNone() {
        assertNull(DistroGlyph.forContainer(""));
        assertNull(DistroGlyph.forContainer(null));
    }
}
