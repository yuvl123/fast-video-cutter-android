package com.example.fastvideocutter.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"

    private var firebaseAuth: FirebaseAuth? = null
    private var isInitialized = false

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyCHvHc3J1PhdlzAPd2NwwnKgNu21WmtmCQ")
                    .setApplicationId("1:930896391580:web:189823ba2e8950d6ab6c59")
                    .setProjectId("certain-drake-446320-v8")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            firebaseAuth = FirebaseAuth.getInstance()
            isInitialized = true
            Log.d(TAG, "Firebase initialized programmatically successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase programmatic initialization failed", e)
        }
    }

    fun isLoggedIn(context: Context): Boolean {
        ensureInitialized(context)
        return firebaseAuth?.currentUser != null || getLoggedInEmail(context) != null
    }

    fun getLoggedInEmail(context: Context): String? {
        ensureInitialized(context)
        val firebaseEmail = firebaseAuth?.currentUser?.email
        if (firebaseEmail != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LOGGED_IN_EMAIL, firebaseEmail).apply()
            return firebaseEmail
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGGED_IN_EMAIL, null)
    }

    fun register(context: Context, email: String, pass: String): Boolean {
        ensureInitialized(context)
        val auth = firebaseAuth
        if (auth == null) {
            return localRegister(context, email, pass)
        }

        return try {
            val task = auth.createUserWithEmailAndPassword(email, pass)
            Tasks.await(task)
            if (task.isSuccessful) {
                val user = task.result?.user
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_LOGGED_IN_EMAIL, user?.email).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase registration failed, trying local fallback", e)
            localRegister(context, email, pass)
        }
    }

    fun login(context: Context, email: String, pass: String): Boolean {
        ensureInitialized(context)
        val auth = firebaseAuth
        if (auth == null) {
            return localLogin(context, email, pass)
        }

        return try {
            val task = auth.signInWithEmailAndPassword(email, pass)
            Tasks.await(task)
            if (task.isSuccessful) {
                val user = task.result?.user
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_LOGGED_IN_EMAIL, user?.email).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase login failed, trying local fallback", e)
            localLogin(context, email, pass)
        }
    }

    fun logout(context: Context) {
        ensureInitialized(context)
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signout failed", e)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGGED_IN_EMAIL).apply()
    }

    // Local Fallbacks
    private const val KEY_REGISTERED_USERS = "registered_users"

    private fun localRegister(context: Context, email: String, pass: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val users = prefs.getStringSet(KEY_REGISTERED_USERS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val exists = users.any { it.startsWith("$email:") }
        if (exists) return false
        users.add("$email:$pass")
        prefs.edit().putStringSet(KEY_REGISTERED_USERS, users).putString(KEY_LOGGED_IN_EMAIL, email).apply()
        return true
    }

    private fun localLogin(context: Context, email: String, pass: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val users = prefs.getStringSet(KEY_REGISTERED_USERS, emptySet()) ?: emptySet()
        val isValid = users.any { it == "$email:$pass" }
        if (isValid) {
            prefs.edit().putString(KEY_LOGGED_IN_EMAIL, email).apply()
            return true
        }
        return false
    }

    fun loginOrRegisterGoogleUser(context: Context, email: String): Boolean {
        val pass = "GoogleUserPass123!"
        val loginSuccess = login(context, email, pass)
        if (loginSuccess) return true
        return register(context, email, pass)
    }
}
