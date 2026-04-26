package com.focusguard.app.domain

import android.graphics.drawable.Drawable

/**
 * Lightweight data class representing an installed app on the device.
 * Used by the Blacklist picker screen.
 */
data class InstalledAppInfo(
    /** The package name, e.g. "com.instagram.android" */
    val packageName: String,

    /** Human-readable app name, e.g. "Instagram" */
    val appName: String,

    /** App icon loaded from PackageManager */
    val icon: Drawable?,

    /** Whether this app is currently blacklisted */
    val isBlacklisted: Boolean = false
)
