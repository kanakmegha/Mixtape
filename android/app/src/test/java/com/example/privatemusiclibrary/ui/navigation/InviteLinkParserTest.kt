package com.example.privatemusiclibrary.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkParserTest {
    private val host = InviteLinkParser.LANDING_HOST

    @Test fun landingRootWithToken() =
        assertEquals("abc123", InviteLinkParser.parse("https", host, "/", "abc123"))

    @Test fun landingEmptyPathWithToken() =
        assertEquals("abc123", InviteLinkParser.parse("https", host, "", "abc123"))

    @Test fun landingInvitePath() =
        assertEquals("abc123", InviteLinkParser.parse("https", host, "/invite", "abc123"))

    @Test fun customScheme() =
        assertEquals("abc123", InviteLinkParser.parse("mixtape", "invite", "", "abc123"))

    @Test fun hostIsCaseInsensitive() =
        assertEquals("abc123", InviteLinkParser.parse("HTTPS", host.uppercase(), "/", "abc123"))

    @Test fun tokenIsTrimmed() =
        assertEquals("abc123", InviteLinkParser.parse("https", host, "/", "  abc123 "))

    @Test fun missingToken() = assertNull(InviteLinkParser.parse("https", host, "/", null))

    @Test fun blankToken() = assertNull(InviteLinkParser.parse("https", host, "/", "   "))

    @Test fun wrongHostRejected() =
        assertNull(InviteLinkParser.parse("https", "evil.example.com", "/", "abc123"))

    @Test fun lookalikeHostRejected() =
        assertNull(InviteLinkParser.parse("https", "$host.evil.com", "/", "abc123"))

    @Test fun plainHttpRejected() = assertNull(InviteLinkParser.parse("http", host, "/", "abc123"))

    @Test fun unrelatedPathRejected() =
        assertNull(InviteLinkParser.parse("https", host, "/settings", "abc123"))

    @Test fun customSchemeWrongHostRejected() =
        assertNull(InviteLinkParser.parse("mixtape", "player", "", "abc123"))

    @Test fun tokenWithWhitespaceRejected() =
        assertNull(InviteLinkParser.parse("https", host, "/", "abc 123"))

    @Test fun overlongTokenRejected() =
        assertNull(InviteLinkParser.parse("https", host, "/", "a".repeat(257)))
}
