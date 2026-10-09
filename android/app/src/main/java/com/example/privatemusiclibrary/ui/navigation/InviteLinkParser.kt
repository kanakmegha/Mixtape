package com.example.privatemusiclibrary.ui.navigation

/**
 * Extracts an invitation token from an incoming deep link.
 *
 * Accepted forms:
 *  - https://mixtape-web-eight.vercel.app/?token=TOKEN        (landing page / App Link)
 *  - https://mixtape-web-eight.vercel.app/invite?token=TOKEN
 *  - mixtape://invite?token=TOKEN                             (landing page "Open Mixtape" button)
 *
 * Pure function (no android.net.Uri) so it can be unit-tested on the JVM.
 * The token is only parsed here; it is validated and redeemed by the backend at registration.
 */
object InviteLinkParser {
    const val LANDING_HOST = "mixtape-web-eight.vercel.app"
    const val APP_SCHEME = "mixtape"
    private const val MAX_TOKEN_LENGTH = 256

    fun parse(scheme: String?, host: String?, path: String?, token: String?): String? {
        val s = scheme?.lowercase()
        val h = host?.lowercase()
        val p = path.orEmpty()

        val recognised = when (s) {
            "https" -> h == LANDING_HOST && (p.isEmpty() || p == "/" || p == "/invite" || p.startsWith("/invite/"))
            APP_SCHEME -> h == "invite"
            else -> false
        }
        if (!recognised) return null

        val t = token?.trim().orEmpty()
        if (t.isEmpty() || t.length > MAX_TOKEN_LENGTH) return null
        // Opaque token: reject whitespace/control characters (malformed or tampered links).
        if (t.any { it.isWhitespace() || it.isISOControl() }) return null
        return t
    }
}
