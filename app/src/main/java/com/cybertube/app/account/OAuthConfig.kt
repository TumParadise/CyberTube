package com.cybertube.app.account

/**
 * Central OAuth configuration for CyberTube.
 *
 * No client secret, access token, refresh token, or user credential belongs
 * in source control. The Android OAuth client ID will be connected through
 * build configuration after the Google Cloud project is configured.
 */
object OAuthConfig {
    const val YOUTUBE_READONLY_SCOPE =
        "https://<scrubbed-url-domain>/auth/youtube.readonly"

    /**
     * Intentionally empty until the real Android OAuth client for
     * applicationId com.cybertube.app is configured.
     */
    const val ANDROID_CLIENT_ID = ""

    val isConfigured: Boolean
        get() = ANDROID_CLIENT_ID.isNotBlank()
}
