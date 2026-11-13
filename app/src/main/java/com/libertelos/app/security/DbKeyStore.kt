package com.libertelos.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 混合密钥：
 * - 随机 32 字节 DB key，用 Android Keystore AES-GCM 包装后落盘（设备绑定，防直接拷库）
 * - 若用户开启应用密码，再额外用密码派生密钥包装一份（可作恢复/加强）
 */
class DbKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreateDbKey(): ByteArray {
        unwrapWithKeystore()?.let { return it }
        val fresh = ByteArray(KEY_LEN).also { SecureRandom().nextBytes(it) }
        wrapWithKeystore(fresh)
        return fresh
    }

    fun unwrapWithKeystore(): ByteArray? {
        val blob = prefs.getString(KEY_WRAPPED, null) ?: return null
        return runCatching {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            require(raw.size > IV_LEN)
            val iv = raw.copyOfRange(0, IV_LEN)
            val cipherBytes = raw.copyOfRange(IV_LEN, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherBytes)
        }.getOrNull()
    }

    fun wrapWithKeystore(dbKey: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(dbKey)
        val out = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
        prefs.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(out, Base64.NO_WRAP))
            .commit()
    }

    val hasPasswordWrap: Boolean
        get() = !prefs.getString(KEY_PWD_WRAPPED, null).isNullOrBlank() &&
            !prefs.getString(KEY_PWD_SALT, null).isNullOrBlank()

    fun wrapWithPassword(password: String, dbKey: ByteArray) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, KeyProperties.KEY_ALGORITHM_AES))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(dbKey)
        val out = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
        prefs.edit()
            .putString(KEY_PWD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PWD_WRAPPED, Base64.encodeToString(out, Base64.NO_WRAP))
            .commit()
    }

    fun unwrapWithPassword(password: String): ByteArray? {
        val saltB64 = prefs.getString(KEY_PWD_SALT, null) ?: return null
        val blob = prefs.getString(KEY_PWD_WRAPPED, null) ?: return null
        return runCatching {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            require(raw.size > IV_LEN)
            val iv = raw.copyOfRange(0, IV_LEN)
            val cipherBytes = raw.copyOfRange(IV_LEN, raw.size)
            val derived = deriveKey(password, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derived, KeyProperties.KEY_ALGORITHM_AES),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.doFinal(cipherBytes)
        }.getOrNull()
    }

    fun clearPasswordWrap() {
        prefs.edit()
            .remove(KEY_PWD_SALT)
            .remove(KEY_PWD_WRAPPED)
            .apply()
    }

    var databaseEncrypted: Boolean
        get() = prefs.getBoolean(KEY_DB_ENCRYPTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DB_ENCRYPTED, value).commit()
        }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF_ITERATIONS, KEY_LEN * 8)
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS = "db_key_store"
        private const val KEY_WRAPPED = "db_key_wrapped"
        private const val KEY_PWD_WRAPPED = "db_key_pwd_wrapped"
        private const val KEY_PWD_SALT = "db_key_pwd_salt"
        private const val KEY_DB_ENCRYPTED = "db_encrypted"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "liber_telos_db_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_LEN = 32
        private const val IV_LEN = 12
        private const val SALT_LEN = 16
        private const val GCM_TAG_BITS = 128
        private const val PBKDF_ITERATIONS = 120_000
    }
}
