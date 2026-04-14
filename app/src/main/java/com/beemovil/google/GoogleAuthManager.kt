package com.beemovil.google

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.beemovil.security.SecurePrefs

/**
 * GoogleAuthManager — handles Google Sign-In via Credential Manager
 * and stores OAuth tokens in SecurePrefs.
 *
 * Phase 24-B: Single sign-in for Drive, Gmail, Calendar.
 *
 * Flow:
 *   1. User taps "Connect Google" in Settings
 *   2. CredentialManager shows one-tap bottom sheet
 *   3. User selects account
 *   4. We get idToken + user info (email, name, photo)
 *   5. Stored in SecurePrefs for persistent session
 *
 * Note: Additional scopes (Drive, Gmail, Calendar) are requested
 *       incrementally via AuthorizationClient when first needed.
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleAuth"

        // ═══════════════════════════════════════
        // REPLACE THIS with your Web Client ID from Google Cloud Console
        // Go to: console.cloud.google.com → Credentials → OAuth 2.0 Client IDs → Web Client
        // ═══════════════════════════════════════
        const val WEB_CLIENT_ID = "714791487968-mo9kd08nln2tbf6t3cg1jeije0dejttr.apps.googleusercontent.com"

        // SecurePrefs keys
        private const val KEY_SIGNED_IN = "google_signed_in"
        private const val KEY_ID_TOKEN = "google_id_token"
        private const val KEY_EMAIL = "google_email"
        private const val KEY_DISPLAY_NAME = "google_display_name"
        private const val KEY_PHOTO_URL = "google_photo_url"
        private const val KEY_ACCESS_TOKEN = "google_access_token"
        private const val KEY_GRANTED_SCOPES = "google_granted_scopes"
    }

    data class GoogleUser(
        val email: String,
        val displayName: String,
        val photoUrl: String?,
        val idToken: String
    )

    private val credentialManager = CredentialManager.create(context)
    private val securePrefs = SecurePrefs.get(context)

    // ═══════════════════════════════════════
    // SIGN IN
    // ═══════════════════════════════════════

    /**
     * Initiate Google Sign-In via Credential Manager.
     * Must be called from a coroutine (suspend function).
     * Returns GoogleUser on success, null on failure.
     */
    suspend fun signIn(activityContext: Context): GoogleUser? {
        return try {
            // Try authorized accounts first (returning user)
            val user = trySignIn(activityContext, filterByAuthorized = true)
            if (user != null) return user

            // If no authorized account, show all accounts
            trySignIn(activityContext, filterByAuthorized = false)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed: ${e.message}", e)
            null
        }
    }

    private suspend fun trySignIn(activityContext: Context, filterByAuthorized: Boolean): GoogleUser? {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorized)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(filterByAuthorized) // Auto-select for returning users
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            handleSignInResult(result)
        } catch (e: Exception) {
            if (filterByAuthorized) {
                Log.d(TAG, "No authorized accounts, will try all: ${e.message}")
                null
            } else {
                Log.e(TAG, "Sign-in error: ${e.message}", e)
                null
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): GoogleUser? {
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val user = GoogleUser(
                email = googleCredential.id, // email
                displayName = googleCredential.displayName ?: googleCredential.id,
                photoUrl = googleCredential.profilePictureUri?.toString(),
                idToken = googleCredential.idToken
            )

            // Persist session
            saveUser(user)
            Log.i(TAG, "Signed in as: ${user.email}")
            return user
        }

        Log.w(TAG, "Unexpected credential type: ${credential.javaClass.simpleName}")
        return null
    }

    // ═══════════════════════════════════════
    // SIGN OUT
    // ═══════════════════════════════════════

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Clear credential state error: ${e.message}")
        }
        clearUser()
        Log.i(TAG, "Signed out")
    }

    // ═══════════════════════════════════════
    // SESSION STATE
    // ═══════════════════════════════════════

    fun isSignedIn(): Boolean = securePrefs.getString(KEY_SIGNED_IN, null) == "true"

    fun getEmail(): String? = securePrefs.getString(KEY_EMAIL, null)

    fun getDisplayName(): String? = securePrefs.getString(KEY_DISPLAY_NAME, null)

    fun getPhotoUrl(): String? = securePrefs.getString(KEY_PHOTO_URL, null)

    fun getIdToken(): String? = securePrefs.getString(KEY_ID_TOKEN, null)

    fun getAccessToken(): String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    fun setAccessToken(token: String) {
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getGrantedScopes(): Set<String> {
        val raw = securePrefs.getString(KEY_GRANTED_SCOPES, "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    fun addGrantedScope(scope: String) {
        val current = getGrantedScopes().toMutableSet()
        current.add(scope)
        securePrefs.edit().putString(KEY_GRANTED_SCOPES, current.joinToString(",")).apply()
    }

    fun hasDriveScope(): Boolean = getGrantedScopes().any { it.contains("drive") }
    fun hasGmailScope(): Boolean = getGrantedScopes().any { it.contains("mail.google.com") || it.contains("gmail") }
    fun hasCalendarScope(): Boolean = getGrantedScopes().any { it.contains("calendar") }
    fun hasTasksScope(): Boolean = getGrantedScopes().any { it.contains("tasks") }

    fun getCurrentUser(): GoogleUser? {
        if (!isSignedIn()) return null
        return GoogleUser(
            email = getEmail() ?: return null,
            displayName = getDisplayName() ?: "",
            photoUrl = getPhotoUrl(),
            idToken = getIdToken() ?: ""
        )
    }

    // ═══════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════

    private fun saveUser(user: GoogleUser) {
        securePrefs.edit().apply {
            putString(KEY_SIGNED_IN, "true")
            putString(KEY_ID_TOKEN, user.idToken)
            putString(KEY_EMAIL, user.email)
            putString(KEY_DISPLAY_NAME, user.displayName)
            putString(KEY_PHOTO_URL, user.photoUrl ?: "")
            apply()
        }
    }

    private fun clearUser() {
        securePrefs.edit()
            .remove(KEY_SIGNED_IN)
            .remove(KEY_ID_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PHOTO_URL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_GRANTED_SCOPES)
            .apply()
    }
}
