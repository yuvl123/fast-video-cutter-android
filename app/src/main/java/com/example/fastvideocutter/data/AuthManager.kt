package com.example.fastvideocutter.data

import android.content.Context

object AuthManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"
    private const val KEY_REGISTERED_USERS = "registered_users"

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGGED_IN_EMAIL, null) != null
    }

    fun getLoggedInEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGGED_IN_EMAIL, null)
    }

    fun register(context: Context, email: String, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val users = prefs.getStringSet(KEY_REGISTERED_USERS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        // Check if user already exists
        val userExists = users.any { it.startsWith("$email:") }
        if (userExists) return false
        
        // Save format "email:password"
        users.add("$email:$password")
        prefs.edit().putStringSet(KEY_REGISTERED_USERS, users).apply()
        
        // Log in immediately
        login(context, email, password)
        return true
    }

    fun login(context: Context, email: String, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val users = prefs.getStringSet(KEY_REGISTERED_USERS, emptySet()) ?: emptySet()
        
        // For development convenience: if there are no registered users yet, register them automatically
        if (users.isEmpty()) {
            register(context, email, password)
            return true
        }

        val isValid = users.any { it == "$email:$password" }
        if (isValid) {
            prefs.edit().putString(KEY_LOGGED_IN_EMAIL, email).apply()
            return true
        }
        return false
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGGED_IN_EMAIL).apply()
    }
}
