package com.example.kaptus.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class ProviderCredentials(
    val apiKey: String = "",
    val username: String = "",
    val password: String = ""
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): ProviderCredentials {
        val encoded = preferences.getString(CREDENTIALS, null) ?: return ProviderCredentials()
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(payload)
            val ivLength = buffer.int
            require(ivLength in 12..32)
            val iv = ByteArray(ivLength).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            json.decodeFromString<ProviderCredentials>(
                cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            )
        }.getOrElse {
            preferences.edit { remove(CREDENTIALS) }
            ProviderCredentials()
        }
    }

    @Synchronized
    fun save(credentials: ProviderCredentials) {
        if (!credentials.isConfigured) {
            preferences.edit { remove(CREDENTIALS) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.encodeToString(credentials).toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit {
            putString(CREDENTIALS, Base64.encodeToString(payload, Base64.NO_WRAP))
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "kaptus_secure_credentials"
        const val CREDENTIALS = "provider_credentials"
        const val KEY_ALIAS = "kaptus_provider_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
