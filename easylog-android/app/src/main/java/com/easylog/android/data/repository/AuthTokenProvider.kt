package com.easylog.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Provides access to authentication tokens and engineer info stored in DataStore.
 * Used for local caching of engineer information.
 */
class AuthTokenProvider(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val ENGINEER_ID_KEY = stringPreferencesKey("engineer_id")
        private val ENGINEER_NAME_KEY = stringPreferencesKey("engineer_name")
        private val TOKEN_EXPIRY_KEY = stringPreferencesKey("token_expiry")
    }

    fun getToken(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[TOKEN_KEY]
        }
    }

    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun saveEngineerInfo(engineerId: String, engineerName: String) {
        dataStore.edit { preferences ->
            preferences[ENGINEER_ID_KEY] = engineerId
            preferences[ENGINEER_NAME_KEY] = engineerName
        }
    }

    suspend fun saveTokenExpiry(expiry: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN_EXPIRY_KEY] = expiry
        }
    }

    fun getEngineerId(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[ENGINEER_ID_KEY]
        }
    }

    fun getEngineerName(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[ENGINEER_NAME_KEY]
        }
    }

    suspend fun clearAuth() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(ENGINEER_ID_KEY)
            preferences.remove(ENGINEER_NAME_KEY)
            preferences.remove(TOKEN_EXPIRY_KEY)
        }
    }
}
