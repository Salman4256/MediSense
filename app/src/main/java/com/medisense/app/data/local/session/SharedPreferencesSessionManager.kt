package com.medisense.app.data.local.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SessionManager {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun saveSession(session: UserSession): Unit = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(UserSession.serializer(), session)
            prefs.edit()
                .putString(KEY_SESSION_JSON, jsonString)
                .putString(KEY_USER_ID, session.user?.id)
                .putString(KEY_USER_EMAIL, session.user?.email)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply()
        } catch (e: Exception) {
            // Save basic logged in flag
            prefs.edit()
                .putString(KEY_USER_ID, session.user?.id)
                .putString(KEY_USER_EMAIL, session.user?.email)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply()
        }
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        try {
            val jsonString = prefs.getString(KEY_SESSION_JSON, null) ?: return@withContext null
            json.decodeFromString(UserSession.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession(): Unit = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_SESSION_JSON)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }

    fun isUserLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) || prefs.getString(KEY_SESSION_JSON, null) != null
    }

    fun getSavedUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getSavedUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    companion object {
        private const val PREFS_NAME = "medisense_auth_session_prefs"
        private const val KEY_SESSION_JSON = "supabase_user_session_json"
        private const val KEY_USER_ID = "saved_user_id"
        private const val KEY_USER_EMAIL = "saved_user_email"
        private const val KEY_IS_LOGGED_IN = "saved_is_logged_in"
    }
}
