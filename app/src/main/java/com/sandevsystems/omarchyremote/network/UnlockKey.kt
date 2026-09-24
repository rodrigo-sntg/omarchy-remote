package com.sandevsystems.omarchyremote.network

import android.app.Activity
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The phone's key for unlocking the PC: an EC P-256 key in the Android Keystore that signs only
 * right after a strong biometric (each use), and is gone if a new fingerprint is added. The PC keeps
 * the public half (host unlock.py) and checks each signed challenge with it.
 */
object UnlockKey {
    private const val ALIAS = "pc_unlock"

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun exists(): Boolean = runCatching { store().containsAlias(ALIAS) }.getOrDefault(false)

    /** The public key (base64 SPKI DER), making the key the first time. */
    fun publicKey(): String {
        if (!exists()) create()
        val cert = store().getCertificate(ALIAS)
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    fun delete() = runCatching { store().deleteEntry(ALIAS) }

    private fun create() {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
    }

    /**
     * Asks for the fingerprint, then signs [nonce] (base64). [done] gets the signature (base64), or
     * null with why (cancelled, key gone after a new fingerprint, no biometric).
     */
    fun sign(activity: Activity, pcName: String, nonce: String, done: (String?, String?) -> Unit) {
        val signature = runCatching {
            Signature.getInstance("SHA256withECDSA").apply { initSign(store().getKey(ALIAS, null) as java.security.PrivateKey) }
        }.getOrElse {
            // KeyPermanentlyInvalidatedException: a fingerprint was added since; the phone must enroll again.
            delete()
            return done(null, "invalidated")
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(com.sandevsystems.omarchyremote.ui.tr("Desbloquear $pcName", "Unlock $pcName"))
            .setSubtitle(com.sandevsystems.omarchyremote.ui.tr("Com a sua digital", "With your fingerprint"))
            .setNegativeButton(com.sandevsystems.omarchyremote.ui.tr("Cancelar", "Cancel"), activity.mainExecutor) { _, _ -> done(null, "cancelled") }
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG) }
            .build()
        prompt.authenticate(BiometricPrompt.CryptoObject(signature), CancellationSignal(), activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val s = result.cryptoObject?.signature ?: return done(null, "failed")
                    val signed = runCatching { s.update(Base64.decode(nonce, Base64.NO_WRAP)); s.sign() }.getOrNull()
                    done(signed?.let { Base64.encodeToString(it, Base64.NO_WRAP) }, if (signed == null) "failed" else null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    done(null, if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED) "cancelled" else errString.toString())
                }
            })
    }
}
