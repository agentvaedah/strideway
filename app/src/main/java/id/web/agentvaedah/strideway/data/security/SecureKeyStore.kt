package id.web.agentvaedah.strideway.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.data.remote.llm.LlmProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secretStore: DataStore<Preferences> by preferencesDataStore(name = "byok_keys")

/**
 * BYOK API keys encrypted with an AES-256-GCM key that never leaves Android Keystore
 * (same parameters as the deprecated security-crypto MasterKeys.AES256_GCM_SPEC).
 * Only ciphertext and IV reach disk; backups exclude this store because allowBackup is off.
 */
@Singleton
class SecureKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.secretStore

    private fun prefKey(p: LlmProviderId) = stringPreferencesKey("key_${p.name}")

    suspend fun put(provider: LlmProviderId, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        store.edit { it[prefKey(provider)] = packed }
    }

    /** Null when no key is stored or it can no longer be decrypted (e.g. Keystore was reset). */
    suspend fun get(provider: LlmProviderId): String? {
        val packed = store.data.first()[prefKey(provider)] ?: return null
        val parts = packed.split(':')
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun remove(provider: LlmProviderId) {
        store.edit { it.remove(prefKey(provider)) }
    }

    fun hasKey(provider: LlmProviderId): Flow<Boolean> = store.data.map { it[prefKey(provider)] != null }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "strideway_byok_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
