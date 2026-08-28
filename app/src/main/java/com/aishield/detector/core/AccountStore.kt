package com.aishield.detector.core

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Minimal account layer: guest sessions (offline, always available) or
 * email sign-in against the config backend. Swappable for Firebase Auth
 * later without touching call sites.
 */
object AccountStore {

    private const val PREFS = "aishield_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"
    private const val KEY_GUEST = "guest"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    data class Account(val token: String, val email: String, val guest: Boolean)

    fun current(): Account? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Account(
            token = token,
            email = prefs.getString(KEY_EMAIL, "Guest") ?: "Guest",
            guest = prefs.getBoolean(KEY_GUEST, true)
        )
    }

    fun isSignedIn(): Boolean = prefs.getString(KEY_TOKEN, null) != null

    fun save(token: String, email: String, guest: Boolean) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_GUEST, guest)
            .apply()
    }

    fun signInAsGuest() {
        save("guest-" + UUID.randomUUID().toString(), "Guest", guest = true)
    }

    fun signOut() = prefs.edit().clear().apply()
}
