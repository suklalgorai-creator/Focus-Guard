package com.focusguard.app.data.auth

import com.focusguard.app.domain.auth.AuthSession
import com.focusguard.app.domain.auth.AuthUser
import com.focusguard.app.domain.auth.FocusScheduleSnapshot
import com.focusguard.app.domain.auth.LocalUserSettings
import com.focusguard.app.domain.auth.RemoteUserSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SupabaseAuthApi(
    private val config: AuthConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): AuthSession {
        ensureConfigured()
        val body = JSONObject()
            .put("provider", "google")
            .put("id_token", idToken)
            .put("nonce", nonce)
            .toString()

        val response = try {
            request(
                path = "/auth/v1/token?grant_type=id_token",
                method = "POST",
                bearerToken = config.supabaseAnonKey,
                body = body
            )
        } catch (exception: SupabaseException) {
            // Older GoTrue deployments used "token" instead of "id_token" for this endpoint.
            if (exception.code != HttpURLConnection.HTTP_BAD_REQUEST) throw exception
            val fallbackBody = JSONObject()
                .put("provider", "google")
                .put("token", idToken)
                .put("nonce", nonce)
                .toString()
            request(
                path = "/auth/v1/token?grant_type=id_token",
                method = "POST",
                bearerToken = config.supabaseAnonKey,
                body = fallbackBody
            )
        }

        return parseSession(response)
    }

    suspend fun signInWithEmail(email: String, password: String): AuthSession {
        ensureConfigured()
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val response = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            bearerToken = config.supabaseAnonKey,
            body = body
        )
        return parseSession(response)
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String?
    ): AuthSession {
        ensureConfigured()
        val metadata = JSONObject().apply {
            name?.takeIf { it.isNotBlank() }?.let {
                put("name", it)
                put("full_name", it)
            }
        }
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", metadata)
            .toString()

        val response = request(
            path = "/auth/v1/signup",
            method = "POST",
            bearerToken = config.supabaseAnonKey,
            body = body
        )

        if (!response.contains("access_token")) {
            throw SupabaseException(
                HttpURLConnection.HTTP_ACCEPTED,
                "Account created. Check your email to confirm, then sign in."
            )
        }
        return parseSession(response)
    }

    suspend fun refreshSession(session: AuthSession): AuthSession? {
        val refreshToken = session.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        ensureConfigured()

        val response = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            bearerToken = config.supabaseAnonKey,
            body = JSONObject().put("refresh_token", refreshToken).toString()
        )
        return parseSession(response, fallbackUser = session.user)
    }

    suspend fun logout(accessToken: String) {
        if (!config.isConfigured || accessToken.isBlank()) return
        runCatching {
            request(
                path = "/auth/v1/logout",
                method = "POST",
                bearerToken = accessToken,
                body = "{}"
            )
        }
    }

    suspend fun upsertUserAndSettings(session: AuthSession, settings: LocalUserSettings) {
        ensureConfigured()

        val userBody = JSONObject()
            .put("id", session.user.id)
            .put("email", session.user.email ?: JSONObject.NULL)
            .put("name", session.user.name ?: JSONObject.NULL)
            .toString()

        request(
            path = "/rest/v1/users?on_conflict=id",
            method = "POST",
            bearerToken = session.accessToken,
            body = userBody,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )

        val scheduleJson = JSONObject()
            .put("enabled", settings.focusSchedule.enabled)
            .put("startHour", settings.focusSchedule.startHour)
            .put("startMinute", settings.focusSchedule.startMinute)
            .put("endHour", settings.focusSchedule.endHour)
            .put("endMinute", settings.focusSchedule.endMinute)
            .put("days", JSONArray(settings.focusSchedule.days.sorted()))

        val settingsBody = JSONObject()
            .put("user_id", session.user.id)
            .put("blocked_apps", JSONArray(settings.blockedApps.sorted()))
            .put("focus_schedule", scheduleJson)
            .put("streak", settings.streak)
            .toString()

        request(
            path = "/rest/v1/user_settings?on_conflict=user_id",
            method = "POST",
            bearerToken = session.accessToken,
            body = settingsBody,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }

    suspend fun fetchUserSettings(session: AuthSession): RemoteUserSettings? {
        ensureConfigured()
        val response = request(
            path = "/rest/v1/user_settings?user_id=eq.${session.user.id}&select=blocked_apps,focus_schedule,streak&limit=1",
            method = "GET",
            bearerToken = session.accessToken
        )

        val rows = JSONArray(response)
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)

        return RemoteUserSettings(
            blockedApps = parseStringSet(row.opt("blocked_apps")),
            focusSchedule = parseSchedule(row.opt("focus_schedule")),
            streak = row.takeIf { it.has("streak") && !it.isNull("streak") }?.optInt("streak")
        )
    }

    private fun parseSession(response: String, fallbackUser: AuthUser? = null): AuthSession {
        val root = JSONObject(response)
        val accessToken = root.optNullableString("access_token")
            ?: throw SupabaseException(HttpURLConnection.HTTP_UNAUTHORIZED, "Supabase did not return an access token.")
        val refreshToken = root.optNullableString("refresh_token")
        val expiresInSeconds = root.optLong("expires_in", 3600L).coerceAtLeast(1L)
        val userJson = root.optJSONObject("user")

        val user = parseUser(userJson) ?: fallbackUser
            ?: throw SupabaseException(HttpURLConnection.HTTP_UNAUTHORIZED, "Supabase did not return a user profile.")

        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = clock() + expiresInSeconds * 1_000L,
            user = user
        )
    }

    private fun parseUser(userJson: JSONObject?): AuthUser? {
        if (userJson == null) return null
        val id = userJson.optNullableString("id") ?: return null
        val metadata = userJson.optJSONObject("user_metadata")
        return AuthUser(
            id = id,
            email = userJson.optNullableString("email") ?: metadata?.optNullableString("email"),
            name = userJson.optNullableString("name")
                ?: metadata?.optNullableString("full_name")
                ?: metadata?.optNullableString("name")
        )
    }

    private fun parseStringSet(value: Any?): Set<String>? {
        if (value == null || value == JSONObject.NULL) return null
        val array = when (value) {
            is JSONArray -> value
            is String -> runCatching { JSONArray(value) }.getOrNull()
            else -> null
        } ?: return null

        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun parseSchedule(value: Any?): FocusScheduleSnapshot? {
        if (value == null || value == JSONObject.NULL) return null
        val json = when (value) {
            is JSONObject -> value
            is String -> runCatching { JSONObject(value) }.getOrNull()
            else -> null
        } ?: return null

        return FocusScheduleSnapshot(
            enabled = json.optBoolean("enabled", false),
            startHour = json.optInt("startHour", 9),
            startMinute = json.optInt("startMinute", 0),
            endHour = json.optInt("endHour", 21),
            endMinute = json.optInt("endMinute", 0),
            days = parseStringSet(json.opt("days"))?.mapNotNull { it.toIntOrNull() }?.toSet()
                ?: parseIntSet(json.optJSONArray("days"))
                ?: setOf(2, 3, 4, 5, 6, 7)
        )
    }

    private fun parseIntSet(array: JSONArray?): Set<Int>? {
        if (array == null) return null
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optInt(index, Int.MIN_VALUE)
                if (value != Int.MIN_VALUE) add(value)
            }
        }
    }

    private suspend fun request(
        path: String,
        method: String,
        bearerToken: String,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String = withContext(ioDispatcher) {
        val url = URL(config.normalizedSupabaseUrl + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", config.supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            throw SupabaseException(code, readableError(response))
        }
        response
    }

    private fun readableError(response: String): String {
        if (response.isBlank()) return "Supabase request failed."
        return runCatching {
            val json = JSONObject(response)
            json.optNullableString("msg")
                ?: json.optNullableString("message")
                ?: json.optNullableString("error_description")
                ?: response
        }.getOrElse { response }
    }

    private fun ensureConfigured() {
        if (!config.isConfigured) {
            throw SupabaseException(0, "Supabase auth is not configured for this build.")
        }
    }
}

class SupabaseException(
    val code: Int,
    override val message: String
) : IOException(message)

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}
