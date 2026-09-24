package com.sandevsystems.omarchyremote.network

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The pairing code, encrypted with a key that never leaves the Android Keystore (AES-GCM). A code
 * saved in clear by older versions is moved in on first read.
 */
class Secrets(private val prefs: SharedPreferences) {
    var pairingCode: String
        get() {
            prefs.getString(PLAIN, null)?.let { plain ->
                pairingCode = plain
                prefs.edit().remove(PLAIN).apply()
                return plain
            }
            val stored = prefs.getString(ENCRYPTED, null) ?: return ""
            return runCatching { decrypt(stored) }.getOrDefault("")
        }
        set(value) {
            prefs.edit().putString(ENCRYPTED, encrypt(value)).remove(PLAIN).apply()
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    companion object {
        private const val ALIAS = "keypad-pairing"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PLAIN = "pairing_code"
        private const val ENCRYPTED = "pairing_code_enc"
    }
}
