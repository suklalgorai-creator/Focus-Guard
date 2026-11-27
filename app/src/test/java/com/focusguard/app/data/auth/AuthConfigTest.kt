package com.focusguard.app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthConfigTest {

    @Test
    fun httpsSupabaseUrl_isAccepted() {
        val config = AuthConfig(
            supabaseUrl = "https://example.supabase.co/",
            supabaseAnonKey = "anon-key",
            googleWebClientId = "client-id"
        )

        assertTrue(config.isHttpsSupabaseUrl)
        assertTrue(config.isConfigured)
    }

    @Test
    fun cleartextHttpSupabaseUrl_isRejected() {
        val config = AuthConfig(
            supabaseUrl = "http://example.supabase.co",
            supabaseAnonKey = "anon-key",
            googleWebClientId = "client-id"
        )

        assertFalse(config.isHttpsSupabaseUrl)
        assertFalse(config.isConfigured)
    }
}
