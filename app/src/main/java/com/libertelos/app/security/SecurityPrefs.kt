package com.libertelos.app.security

import android.content.Context
import java.security.MessageDigest

class SecurityPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val passwordEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASSWORD_ENABLED, false) &&
            !prefs.getString(KEY_PASSWORD_HASH, null).isNullOrBlank()

    val patternEnabled: Boolean
        get() = prefs.getBoolean(KEY_PATTERN_ENABLED, false) &&
            !prefs.getString(KEY_PATTERN_HASH, null).isNullOrBlank()

    val fingerprintEnabled: Boolean
        get() = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)

    val faceEnabled: Boolean
        get() = prefs.getBoolean(KEY_FACE_ENABLED, false)

    val anyEnabled: Boolean
        get() = passwordEnabled || patternEnabled || fingerprintEnabled || faceEnabled

    val lastUnlockMethod: UnlockMethod?
        get() {
            val raw = prefs.getString(KEY_LAST_UNLOCK, null) ?: return null
            return runCatching { UnlockMethod.valueOf(raw) }.getOrNull()
        }

    fun setLastUnlockMethod(method: UnlockMethod) {
        prefs.edit().putString(KEY_LAST_UNLOCK, method.name).apply()
    }

    fun enablePassword(rawPassword: String) {
        prefs.edit()
            .putBoolean(KEY_PASSWORD_ENABLED, true)
            .putString(KEY_PASSWORD_HASH, hash(rawPassword))
            .apply()
        // 混合加密：密码额外包装一份 DB key
        runCatching {
            val keys = DbKeyStore(appContext)
            val dbKey = keys.getOrCreateDbKey()
            keys.wrapWithPassword(rawPassword, dbKey)
        }
    }

    fun disablePassword() {
        prefs.edit()
            .putBoolean(KEY_PASSWORD_ENABLED, false)
            .remove(KEY_PASSWORD_HASH)
            .apply()
        runCatching { DbKeyStore(appContext).clearPasswordWrap() }
    }

    fun verifyPassword(rawPassword: String): Boolean {
        val stored = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        return stored == hash(rawPassword)
    }

    fun enablePattern(dots: List<Int>) {
        prefs.edit()
            .putBoolean(KEY_PATTERN_ENABLED, true)
            .putString(KEY_PATTERN_HASH, hash(patternKey(dots)))
            .apply()
    }

    fun disablePattern() {
        prefs.edit()
            .putBoolean(KEY_PATTERN_ENABLED, false)
            .remove(KEY_PATTERN_HASH)
            .apply()
    }

    fun verifyPattern(dots: List<Int>): Boolean {
        val stored = prefs.getString(KEY_PATTERN_HASH, null) ?: return false
        return stored == hash(patternKey(dots))
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    fun setFaceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FACE_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS = "security_prefs"
        private const val KEY_PASSWORD_ENABLED = "password_enabled"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PATTERN_ENABLED = "pattern_enabled"
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_FACE_ENABLED = "face_enabled"
        private const val KEY_LAST_UNLOCK = "last_unlock_method"

        fun patternKey(dots: List<Int>): String = dots.joinToString("-")

        fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
