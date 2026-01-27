package com.easylog.android.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Handles Google Sign-in using Credential Manager API and Firebase Auth.
 */
class GoogleAuthClient(
    private val context: Context,
    private val webClientId: String
) {
    private val credentialManager = CredentialManager.create(context)
    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Get the currently signed-in Firebase user, or null if not signed in.
     */
    fun getCurrentUser() = firebaseAuth.currentUser

    /**
     * Check if a user is currently signed in.
     */
    fun isSignedIn() = firebaseAuth.currentUser != null

    /**
     * Sign in with Google using Credential Manager.
     * Returns the Firebase user's UID on success, or throws an exception on failure.
     */
    suspend fun signIn(): GoogleSignInResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleSignInResult(result)
        } catch (e: Exception) {
            GoogleSignInResult.Error(e.message ?: "Sign-in failed")
        }
    }

    /**
     * Handle the credential result and authenticate with Firebase.
     */
    private suspend fun handleSignInResult(result: GetCredentialResponse): GoogleSignInResult {
        val credential = result.credential

        return when {
            credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    // Authenticate with Firebase
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                    val user = authResult.user

                    if (user != null) {
                        GoogleSignInResult.Success(
                            uid = user.uid,
                            email = user.email ?: "",
                            displayName = user.displayName ?: ""
                        )
                    } else {
                        GoogleSignInResult.Error("Firebase authentication failed")
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    GoogleSignInResult.Error("Invalid Google ID token: ${e.message}")
                }
            }
            else -> {
                GoogleSignInResult.Error("Unexpected credential type")
            }
        }
    }

    /**
     * Sign out from both Firebase and Google.
     */
    suspend fun signOut() {
        firebaseAuth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Ignore errors during sign-out
        }
    }
}

/**
 * Result of a Google Sign-in attempt.
 */
sealed class GoogleSignInResult {
    data class Success(
        val uid: String,
        val email: String,
        val displayName: String
    ) : GoogleSignInResult()

    data class Error(val message: String) : GoogleSignInResult()
}
