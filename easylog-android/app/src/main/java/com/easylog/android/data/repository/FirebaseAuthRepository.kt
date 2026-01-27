package com.easylog.android.data.repository

import android.util.Log
import com.easylog.android.data.auth.GoogleAuthClient
import com.easylog.android.data.auth.GoogleSignInResult
import com.easylog.android.data.local.dao.EngineerDao
import com.easylog.android.data.local.entity.EngineerEntity
import com.easylog.android.domain.model.Engineer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Generic result type for auth operations.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

/**
 * Result type for engineer lookup/creation.
 */
private sealed class EngineerResult {
    data class Success(val engineer: Engineer, val isNewUser: Boolean) : EngineerResult()
    data class Deactivated(val email: String) : EngineerResult()
    data class Error(val message: String) : EngineerResult()
}

/**
 * Repository handling Firebase Auth with Google Sign-in and Firestore engineer management.
 */
class FirebaseAuthRepository(
    private val googleAuthClient: GoogleAuthClient,
    private val engineerDao: EngineerDao,
    private val tokenProvider: AuthTokenProvider
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseAuthRepository"
        private const val ENGINEERS_COLLECTION = "engineers"
    }

    /**
     * Sign in with Google and ensure engineer record exists.
     * Automatically registers new engineers on first sign-in.
     */
    suspend fun signInWithGoogle(): AuthResult<Engineer> {
        return try {
            // Step 1: Sign in with Google
            when (val signInResult = googleAuthClient.signIn()) {
                is GoogleSignInResult.Success -> {
                    Log.d(TAG, "Google sign-in successful: ${signInResult.email}")

                    // Step 2: Get or create engineer in Firestore
                    when (val engineerResult = getOrCreateEngineer(
                        uid = signInResult.uid,
                        email = signInResult.email,
                        displayName = signInResult.displayName
                    )) {
                        is EngineerResult.Success -> {
                            val engineer = engineerResult.engineer

                            if (engineerResult.isNewUser) {
                                Log.d(TAG, "New engineer registered: ${engineer.email}")
                            }

                            // Step 3: Save to local cache and DataStore
                            val entity = EngineerEntity(
                                id = engineer.id,
                                name = engineer.name,
                                email = engineer.email,
                                phone = engineer.phone,
                                isActive = engineer.isActive,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            engineerDao.insertEngineer(entity)
                            tokenProvider.saveEngineerInfo(engineer.id, engineer.name)

                            // Use Firebase ID token as the auth token
                            val idToken = firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
                            if (idToken != null) {
                                tokenProvider.saveToken(idToken)
                            }

                            AuthResult.Success(engineer)
                        }

                        is EngineerResult.Deactivated -> {
                            // Sign out since they can't use the app
                            googleAuthClient.signOut()
                            AuthResult.Error("Your account (${engineerResult.email}) has been deactivated. Please contact an administrator.")
                        }

                        is EngineerResult.Error -> {
                            googleAuthClient.signOut()
                            AuthResult.Error(engineerResult.message)
                        }
                    }
                }
                is GoogleSignInResult.Error -> {
                    Log.e(TAG, "Google sign-in failed: ${signInResult.message}")
                    AuthResult.Error(signInResult.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error", e)
            AuthResult.Error(e.message ?: "Sign-in failed")
        }
    }

    /**
     * Get existing engineer from Firestore or create a new one.
     * Returns specific result types for different scenarios.
     */
    private suspend fun getOrCreateEngineer(
        uid: String,
        email: String,
        displayName: String
    ): EngineerResult {
        return try {
            val docRef = firestore.collection(ENGINEERS_COLLECTION).document(uid)
            val doc = try {
                withTimeout(5000) {
                    try {
                        docRef.get(Source.DEFAULT).await()
                    } catch (e: Exception) {
                        docRef.get(Source.CACHE).await()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Try cache as last resort
                docRef.get(Source.CACHE).await()
            }

            if (doc.exists()) {
                // Engineer exists, check if active
                val isActive = doc.getBoolean("isActive") ?: true
                if (!isActive) {
                    Log.w(TAG, "Engineer account is deactivated: $email")
                    return EngineerResult.Deactivated(email)
                }

                val engineer = Engineer(
                    id = uid,
                    name = doc.getString("name") ?: displayName,
                    email = doc.getString("email") ?: email,
                    phone = doc.getString("phone"),
                    isActive = true
                )
                EngineerResult.Success(engineer, isNewUser = false)
            } else {
                // Create new engineer record (auto-registration)
                Log.d(TAG, "Creating new engineer record for $email")
                val now = com.google.firebase.Timestamp.now()
                val engineerData = hashMapOf(
                    "uid" to uid,
                    "email" to email,
                    "name" to displayName,
                    "phone" to null,
                    "isActive" to true,
                    "createdAt" to now,
                    "updatedAt" to now
                )

                withTimeout(10000) {
                    docRef.set(engineerData).await()
                }

                val engineer = Engineer(
                    id = uid,
                    name = displayName,
                    email = email,
                    phone = null,
                    isActive = true
                )
                EngineerResult.Success(engineer, isNewUser = true)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout getting/creating engineer", e)
            EngineerResult.Error("Connection timeout. Please check your internet connection and try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating engineer", e)
            EngineerResult.Error("Failed to set up your account: ${e.localizedMessage}")
        }
    }

    /**
     * Sign out from Firebase and clear local state.
     */
    suspend fun signOut() {
        try {
            googleAuthClient.signOut()
            tokenProvider.clearAuth()
            // Note: Firestore cache is filtered by engineerId in queries,
            // and security rules prevent unauthorized server access
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign-out", e)
        }
    }

    /**
     * Get current engineer info from local cache.
     */
    fun getCurrentEngineer(): Flow<Engineer?> {
        return tokenProvider.getEngineerId().map { engineerId ->
            if (engineerId != null) {
                engineerDao.getEngineerById(engineerId)?.toDomainModel()
            } else {
                null
            }
        }
    }

    /**
     * Refresh the Firebase ID token (for API calls if needed during transition).
     */
    suspend fun refreshToken(): String? {
        return try {
            val token = firebaseAuth.currentUser?.getIdToken(true)?.await()?.token
            if (token != null) {
                tokenProvider.saveToken(token)
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            null
        }
    }
}

private fun EngineerEntity.toDomainModel(): Engineer {
    return Engineer(
        id = id,
        name = name,
        email = email,
        phone = phone,
        isActive = isActive
    )
}
