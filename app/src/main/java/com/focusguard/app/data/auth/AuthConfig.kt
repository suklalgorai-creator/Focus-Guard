package com.focusguard.app.data.auth

import com.focusguard.app.BuildConfig

data class AuthConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val googleWebClientId: String
) {
    val isHttpsSupabaseUrl: Boolean
        get() = normalizedSupabaseUrl.startsWith("https://", ignoreCase = true)

    val isConfigured: Boolean
        get() = isHttpsSupabaseUrl &&
            supabaseAnonKey.isNotBlank() &&
            googleWebClientId.isNotBlank()

    val normalizedSupabaseUrl: String
        get() = supabaseUrl.trim().trimEnd('/')

    companion object {
        fun fromBuildConfig(): AuthConfig {
            return AuthConfig(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            )
        }
    }
}
