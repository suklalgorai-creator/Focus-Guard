package com.focusguard.app.data.auth

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.focusguard.app.domain.auth.GoogleIdTokenResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

class GoogleAuthManager(
    private val config: AuthConfig
) {

    suspend fun getGoogleIdToken(activity: Activity): GoogleIdTokenResult {
        if (!config.isConfigured) {
            throw AuthException("Google Sign-In is not configured for this build.")
        }

        val nonce = generateNonce()
        return try {
            requestCredential(
                activity = activity,
                nonce = nonce,
                filterByAuthorizedAccounts = true
            )
        } catch (exception: NoCredentialException) {
            requestCredential(
                activity = activity,
                nonce = nonce,
                filterByAuthorizedAccounts = false
            )
        }
    }

    private suspend fun requestCredential(
        activity: Activity,
        nonce: String,
        filterByAuthorizedAccounts: Boolean
    ): GoogleIdTokenResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(config.googleWebClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(activity)
        val credential = try {
            credentialManager.getCredential(
                context = activity,
                request = request
            ).credential
        } catch (exception: NoCredentialException) {
            throw exception
        } catch (exception: GetCredentialException) {
            throw AuthException(
                message = exception.message ?: "Google Sign-In failed.",
                cause = exception
            )
        }

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthException("Google Sign-In returned an unsupported credential.")
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (exception: GoogleIdTokenParsingException) {
            throw AuthException("Could not read Google ID token.", exception)
        }

        return GoogleIdTokenResult(
            idToken = googleCredential.idToken,
            nonce = nonce,
            email = googleCredential.id,
            name = googleCredential.displayName
        )
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}

class AuthException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
