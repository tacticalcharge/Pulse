package com.pulse.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal-device key storage; not a substitute for a backend for distributable apps. */
object KeyVault {
    private const val alias = "pulse_personal_openai_key"
    private const val storeName = "secrets"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    fun save(context: Context, secret: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val p = context.getSharedPreferences(storeName, Context.MODE_PRIVATE)
        p.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
    }
    fun read(context: Context): String? {
        return try {
        val p = context.getSharedPreferences(storeName, Context.MODE_PRIVATE)
        val iv = p.getString("iv", null) ?: return null
        val value = p.getString("cipher", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv,Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
    fun clear(context: Context) = context.getSharedPreferences(storeName, Context.MODE_PRIVATE).edit().clear().apply()
    fun exists(context: Context): Boolean = read(context) != null
}
